"""Normalize a Harness STO issue CSV export into static_findings.json.

Usage:
    python collectors/sto_csv.py --input data/sto_findings.csv --output data/static_findings.json
"""
import argparse
import csv
import json
import re
from pathlib import Path

REQUIRED = {'Issue ID', 'Issue Title', 'Severity', 'Target Name', 'Scanner Name'}
HTTP_METHODS = {'GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'}


def number(value):
    value = (value or '').strip()
    if not value:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def integer(value):
    value = (value or '').strip()
    if not value:
        return None
    try:
        return int(value)
    except ValueError:
        return None


def yes_no(value):
    value = (value or '').strip().lower()
    if value in ('yes', 'true', '1'):
        return True
    if value in ('no', 'false', '0'):
        return False
    return None


def normalize(row):
    title = (row.get('Issue Title') or '').strip()
    purl = title if title.startswith('pkg:') else None
    category = 'SCA' if purl else 'SAST_or_other'
    return {
        'id': (row.get('Issue ID') or '').strip(),
        'title': title,
        'severity': (row.get('Severity') or '').strip(),
        'severity_score': number(row.get('Severity Score')),
        'category': category,
        'package_url': purl,
        'source': 'Harness STO CSV',
        'scanner': (row.get('Scanner Name') or '').strip(),
        'organization': (row.get('Organisation Name') or '').strip(),
        'project': (row.get('Project Name') or '').strip(),
        'pipeline': (row.get('Pipeline Name') or '').strip(),
        'pipeline_execution_id': (row.get('Pipeline Execution ID') or '').strip(),
        'target_type': (row.get('Target Type') or '').strip(),
        'target_name': (row.get('Target Name') or '').strip(),
        'variant': (row.get('Variant') or '').strip(),
        'status': (row.get('Status') or '').strip(),
        'exemption_status': (row.get('Exemption Status') or '').strip(),
        'occurrences': integer(row.get('No. of Occurrences')),
        'only_in_current_scan': yes_no(row.get('Only in Current Scan')),
        'epss_score': number(row.get('EPSS Score')),
        'epss_percentile': number(row.get('EPSS Percentile')),
        'cisa_kev': yes_no(row.get('CISA KEV')),
        'endpoint': {'method': None, 'path': None},
        'service': None,
        'reachability': 'unknown',
        'evidence_status': 'summary_only',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    with open(args.input, newline='', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        missing = REQUIRED - set(reader.fieldnames or [])
        if missing:
            parser.error('CSV missing columns: ' + ', '.join(sorted(missing)))
        findings = [normalize(row) for row in reader if any(row.values())]
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({'source_format': 'harness_sto_csv_summary', 'findings': findings}, indent=2), encoding='utf-8')
    print(f'Imported {len(findings)} STO findings -> {output}')
    print(f'SCA package findings: {sum(f["category"] == "SCA" for f in findings)}')
    print(f'SAST/other findings: {sum(f["category"] != "SCA" for f in findings)}')


if __name__ == '__main__':
    main()
