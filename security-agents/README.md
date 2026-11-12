# crAPI Runtime Correlation Starter
This is an offline-first starter, NOT a live Harness API integration.

Run in PowerShell from security-agents:
python .\correlate.py --api .\data\api_finding.json --runtime .\data\runtime_events.json --static .\data\static_findings.json --out .\output\report.json

Initial runtime and static data are empty by design; no fabricated events. Replace only with verified, redacted exports mapped into these schemas. Do not commit live credentials or response bodies.
