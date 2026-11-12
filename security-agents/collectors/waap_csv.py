
import argparse
import csv
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import urlsplit



def parse_endpoint(value):
    """Extract HTTP method, path, and service from Harness WAAP CSV."""
    value = (value or "").strip()

    match = re.match(
        r"^API:\s*(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)"
        r"\s+(.+?)\s+Service:\s*(.+?)\s*$",
        value,
        re.IGNORECASE
    )

    if not match:
        return {
            "method": None,
            "path": None,
            "service": None
        }

    method = match.group(1).upper()
    raw_path = match.group(2).strip()
    service = match.group(3).strip()

    parsed = urlsplit(raw_path)

    return {
        "method": method,
        "path": parsed.path or None,
        "service": service
    }



def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    with open(args.input, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)

        required = {
            "Threat Rule",
            "API Endpoint",
            "Events Count",
            "Last Seen Time"
        }

        missing = required - set(reader.fieldnames or [])
        if missing:
            raise ValueError(
                f"Missing WAAP CSV columns: {sorted(missing)}"
            )

        events = []

        for row in reader:
            endpoint = parse_endpoint(row.get("API Endpoint"))

            identity = json.dumps(
                row, sort_keys=True, ensure_ascii=False
            )
            event_id = hashlib.sha256(
                identity.encode("utf-8")
            ).hexdigest()[:16]

            count_text = row.get("Events Count", "0")
            count_text = count_text.replace(",", "").strip()

            try:
                count = int(count_text)
            except ValueError:
                count = None

            events.append({
                "id": event_id,
                "source": "Harness WAAP CSV",
                "record_type": "threat_summary",
                "endpoint": endpoint,
                "service": endpoint.get("service"),
                "domain": row.get("Domain"),
                "threat_rule": row.get("Threat Rule"),
                "threat_type": row.get("Threat Type"),
                "threat_labels": row.get("Threat Labels"),
                "severity": row.get("Severity"),
                "confidence": row.get("Confidence"),
                "threat_status": row.get("Threat Status"),
                "status_code": row.get("Status Code"),
                "events_count": count,
                "last_seen": row.get("Last Seen Time"),
                "waf_action": None
            })

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    output.write_text(
        json.dumps({
            "source_format": "waap_csv_summary",
            "events": events
        }, indent=2),
        encoding="utf-8"
    )

    print(f"Imported WAAP threat summaries: {len(events)}")
    print(f"Output: {output}")
    print("Service identity and WAF action: not available")
    print("Event counts are aggregated, not individual requests")


if __name__ == "__main__":
    main()
