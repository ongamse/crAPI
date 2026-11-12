"""Reusable evidence-conservative security correlation report (CSV summary inputs)."""
import argparse
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit


def load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def normalize_method(value):
    return str(value or "").strip().upper()


def normalize_path(value):
    if not value:
        return ""
    raw = str(value).strip()
    path = urlsplit(raw).path if "://" in raw else raw.split("?", 1)[0]
    return "/" + path.strip("/").lower() if path.strip("/") else ""


def endpoint_key(record):
    endpoint = record.get("endpoint") or {}
    method = normalize_method(endpoint.get("method"))
    path = normalize_path(endpoint.get("path"))
    return (method, path) if method and path else None


def verified_service(finding, event, config):
    service = finding.get("service")
    event_service = event.get("service") or (event.get("endpoint") or {}).get("service")
    if not service or not event_service:
        return False
    aliases = config.get("service_mappings", {}).get(service, [])
    return event_service == service or event_service in aliases


def route_hint(finding, route):
    """Weak naming similarity, NEVER evidence of an endpoint mapping."""
    title = finding.get("title") or ""
    view = re.search(r"\b([A-Za-z][A-Za-z0-9_]*View)\.(get|post|put|patch|delete)\b", title, re.I)
    if not view:
        return False
    view_name = view.group(1)[:-4]  # remove View
    words = re.findall(r"[A-Z]?[a-z]+|[A-Z]+(?![a-z])|[0-9]+", view_name)
    token = "".join(words).lower()
    method, path = route
    segments = [s for s in path.split("/") if s]
    # Match an entire terminal route segment, not a substring of another route.
    return method == view.group(2).upper() and bool(token) and bool(segments) and token == re.sub(r"[^a-z0-9]", "", segments[-1])


def runtime_summary(events):
    routes = {}
    for event in events:
        key = endpoint_key(event)
        if not key:
            continue
        item = routes.setdefault(key, {"method": key[0], "path": key[1], "summary_records": 0,
                                       "services": set(), "threat_rules": set(), "reported_event_count_sum": 0})
        item["summary_records"] += 1
        if event.get("service"):
            item["services"].add(str(event["service"]))
        if event.get("threat_rule"):
            item["threat_rules"].add(str(event["threat_rule"]))
        count = event.get("events_count")
        if isinstance(count, (int, float)) and not isinstance(count, bool):
            item["reported_event_count_sum"] += count
    return [{**item, "services": sorted(item["services"]), "threat_rules": sorted(item["threat_rules"])}
            for _, item in sorted(routes.items())]


def evidence_assessment(finding, verified, candidates, events):
    """Evidence tiers are categorical, not fabricated numerical probabilities."""
    route_key = endpoint_key(finding)
    service = finding.get("service")
    if verified:
        tier = "endpoint_service_verified"
    elif candidates:
        tier = "candidate"
    else:
        tier = "insufficient_evidence"
    route_hypotheses = [{"method": c["method"], "path": c["path"], "reason": c["reason"]}
                        for c in candidates]
    return {
        "tier": tier,
        "endpoint_source": "explicit_finding_endpoint" if route_key else "missing",
        "service_identity": "present" if service else "missing",
        "deployment_identity_verified": False,
        "vulnerability_specific_runtime_evidence": False,
        "vulnerable_function_execution": "unknown",
        "successful_exploitation": "unknown",
        "route_hypotheses": route_hypotheses,
        "warning": "A route or gateway threat summary alone does not prove exploitation."
    }


def shared_runtime_evidence(static_results):
    """Count unique routes referenced by candidates, not WAAP summaries per finding."""
    by_route = {}
    for result in static_results:
        finding = result["finding"]
        for route in result["candidate_routes"]:
            key = (route["method"], route["path"])
            item = by_route.setdefault(key, {
                "method": key[0], "path": key[1], "finding_ids": [],
                "waap_summary_records": route["waap_summary_records"],
                "services": route["services"], "threat_rules": route["threat_rules"]
            })
            if finding.get("id") not in item["finding_ids"]:
                item["finding_ids"].append(finding.get("id"))
    return [by_route[key] for key in sorted(by_route)]


def main():
    parser = argparse.ArgumentParser(description="Reusable Runtime Security Correlation Engine")
    for name in ("config", "api", "runtime", "static", "out"):
        parser.add_argument("--" + name, required=True)
    args = parser.parse_args()
    config, api_data, runtime_data, static_data = [load_json(getattr(args, name))
                                                   for name in ("config", "api", "runtime", "static")]
    api_findings = api_data.get("findings")
    if api_findings is None:
        api_findings = [api_data] if api_data else []
    events = runtime_data.get("events", [])
    static_findings = static_data.get("findings", [])
    routes = runtime_summary(events)
    results = []
    for finding in api_findings:
        key = endpoint_key(finding)
        exact = [e for e in events if key and endpoint_key(e) == key and verified_service(finding, e, config)]
        context = [e for e in events if verified_service(finding, e, config) and e not in exact]
        matches = [s for s in static_findings if key and endpoint_key(s) == key]
        results.append({
            "finding": finding,
            "correlation_level": "endpoint" if exact else "service_context" if context else "none",
            "runtime": {"exact_endpoint_events": len(exact), "service_context_events": len(context),
                        "exact_event_ids": [e.get("id") for e in exact]},
            "static": {"matched_findings": len(matches), "finding_ids": [s.get("id") for s in matches]},
            "assessment": {"runtime_endpoint_observed": bool(exact),
                           "vulnerable_function_execution": "unknown", "currently_exploitable": "unknown",
                           "note": "Endpoint/service correlation does not prove exploitation or same deployed version."}
        })

    static_results = []
    for finding in static_findings:
        key = endpoint_key(finding)
        verified = [e for e in events if key and endpoint_key(e) == key and verified_service(finding, e, config)]
        candidates = []
        if not verified:
            for route in routes:
                route_key = (route["method"], route["path"])
                if (key and key == route_key) or (not key and route_hint(finding, route_key)):
                    candidates.append({"method": route["method"], "path": route["path"],
                                       "services": route["services"], "threat_rules": route["threat_rules"],
                                       "waap_summary_records": route["summary_records"],
                                       "reason": "unverified_endpoint_service" if key else "view_name_route_similarity",
                                       "confidence": "unverified"})
        static_results.append({
            "finding": finding,
            "correlation_level": "verified_service_endpoint" if verified else "candidate" if candidates else "none",
            "verified_runtime_summary_records": len(verified),
            "candidate_routes": candidates,
            "evidence": evidence_assessment(finding, verified, candidates, events),
            "assessment": {"vulnerable_function_execution": "unknown", "currently_exploitable": "unknown",
                           "note": "A candidate is a naming/route hypothesis, not proof of shared deployment, vulnerable code execution, or exploitation."}
        })

    shared_routes = shared_runtime_evidence(static_results)
    report = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "application": config.get("application_id"), "environment": config.get("environment"),
        "api_testing_scan": config.get("api_testing_scan"),
        "summary": {"api_issue_groups": len(api_findings), "sto_findings": len(static_findings),
                    "waap_threat_summary_records": len(events), "waap_unique_routes": len(routes),
                    "sto_candidate_findings": sum(bool(r["candidate_routes"]) for r in static_results),
                    "sto_verified_service_endpoint_findings": sum(r["correlation_level"] == "verified_service_endpoint" for r in static_results),
                    "candidate_unique_routes": len(shared_routes),
                    "candidate_route_waap_summary_records_not_double_counted": sum(r["waap_summary_records"] for r in shared_routes)},
        "results": results, "static_results": static_results, "runtime_routes": routes,
        "shared_candidate_routes": shared_routes,
        "limitations": ["CSV summaries do not prove vulnerable function execution or successful exploitation.",
                        "View-name route similarities are unverified hypotheses.",
                        "WAAP gateway identities require explicit verified service mappings.",
                        "WAAP summary records and event-count sums may overlap across gateways.",
                        "No deployment or commit identity verification is performed."]
    }
    output = Path(args.out)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"Report generated: {output}")
    print(f"API findings analyzed: {len(results)}")
    print(f"STO findings analyzed: {len(static_results)}")
    print(f"WAAP threat summaries: {len(events)}")
    print(f"STO findings with candidate routes: {report['summary']['sto_candidate_findings']}")
    print(f"Unique candidate routes: {len(shared_routes)}")


if __name__ == "__main__":
    main()
