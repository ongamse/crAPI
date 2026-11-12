#!/usr/bin/env python3
"""Read Harness STO scan issues directly and normalize for correlation.

Usage (PowerShell):
  $env:HARNESS_API_KEY = '<token>'  # prefer prompting, see accompanying instructions
  python collectors/sto_api.py --account-id ... --scan-id ... --out data/static_findings.json
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE = 'https://app.harness.io'


def fetch(base, account, scan, token, page, page_size):
    query = urllib.parse.urlencode({'accountId': account, 'page': page, 'pageSize': page_size})
    url = f'{base.rstrip("/")}/sto/api/v2/scans/{urllib.parse.quote(scan, safe="")}/issues?{query}'
    req = urllib.request.Request(url, headers={'X-Api-Key': token, 'Accept': 'application/json'})
    with urllib.request.urlopen(req, timeout=45) as resp:
        return json.load(resp)


def normalize(issue, scan, execution):
    details = issue.get('details') or {}
    occurrences = issue.get('occurrences') or []
    # Keep structured occurrence evidence, excluding large raw/AI text blobs.
    locations = []
    for occ in occurrences:
        locations.append({k: occ.get(k) for k in ('fileName', 'lineNumber', 'occurrenceId', 'title', 'severityCode') if occ.get(k) is not None})
    reachability = issue.get('reachability') or details.get('reachability') or 'unknown'
    severity = issue.get('severityCode') or details.get('severityCode') or 'Unknown'
    return {
        'id': issue.get('id'),
        'title': issue.get('title'),
        'severity': severity,
        'severityCode': severity,
        'category': issue.get('issueType') or 'unknown',
        'issueType': issue.get('issueType'),
        'reachability': reachability,
        'scan_id': scan,
        'pipeline_execution': execution,
        'source': 'harness_sto_api',
        'evidence_tier': 'scanner',
        'runtime_verified': False,
        'service_identity': 'unknown',
        'endpoint_source': 'missing',
        'locations': locations,
        'occurrences': locations,
        'numOccurrences': issue.get('numOccurrences', len(locations)),
        'details': {
            'category': details.get('category'),
            'inKev': details.get('inKev'),
            'dataflow': details.get('dataflow'),
            'sourceSinkLocs': details.get('sourceSinkLocs'),
            'referenceIdentifiers': details.get('referenceIdentifiers'),
        },
    }


def main():
    p = argparse.ArgumentParser(description='Collect Harness STO findings via API')
    p.add_argument('--account-id', required=True)
    p.add_argument('--scan-id', required=True)
    p.add_argument('--execution-id', default='')
    p.add_argument('--out', default='data/static_findings.json')
    p.add_argument('--base-url', default=BASE)
    p.add_argument('--page-size', type=int, default=100)
    args = p.parse_args()
    token = os.environ.get('HARNESS_API_KEY')
    if not token:
        print('Missing HARNESS_API_KEY environment variable', file=sys.stderr)
        return 2
    if args.page_size < 1:
        p.error('--page-size must be positive')
    all_issues = []
    seen_ids = set()
    page = 0
    while True:
        try:
            response = fetch(args.base_url, args.account_id, args.scan_id, token, page, args.page_size)
        except urllib.error.HTTPError as e:
            print(f'STO API returned HTTP {e.code} on page {page}', file=sys.stderr)
            return 1
        except (urllib.error.URLError, ValueError, TimeoutError) as e:
            print(f'STO API request failed on page {page}: {e}', file=sys.stderr)
            return 1
        if not isinstance(response, dict) or not isinstance(response.get('issues'), list):
            print(f'Unexpected STO response structure on page {page}: expected issues array', file=sys.stderr)
            return 1
        issues = response['issues']
        page_ids = {str(issue.get('id')) for issue in issues if issue.get('id') is not None}
        new_ids = page_ids - seen_ids
        print(f'Page {page}: {len(issues)} issues returned, {len(new_ids)} new IDs', flush=True)
        if page > 0 and page_ids and not new_ids:
            print('Repeated page detected; stopping pagination safely.', flush=True)
            break
        seen_ids.update(page_ids)
        all_issues.extend(issues)
        pagination = response.get('pagination') or {}
        total_pages = pagination.get('totalPages')
        if isinstance(total_pages, int):
            if page + 1 >= total_pages:
                break
        elif len(issues) < args.page_size:
            break
        if not issues:
            break
        page += 1
        if page >= 100:
            print('Pagination safety limit reached', file=sys.stderr)
            return 1
    unique = {str(issue.get('id') or f'no-id-{i}'): issue for i, issue in enumerate(all_issues)}
    normalized = [normalize(issue, args.scan_id, args.execution_id) for issue in unique.values()]
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"findings": normalized}, indent=2, ensure_ascii=False), encoding='utf-8')
    print(f'Collected {len(normalized)} STO findings from {page + 1} page(s)')
    print(f'Wrote {out}')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
