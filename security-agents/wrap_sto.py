import json
from pathlib import Path

source = Path("data/static_findings_api.json")
output = Path("data/static_findings_api_wrapped.json")

with source.open("r", encoding="utf-8-sig") as f:
    data = json.load(f)

print("JSON type:", type(data).__name__)

if isinstance(data, list):
    findings = data
elif isinstance(data, dict):
    findings = data.get("findings", data.get("issues", []))
else:
    raise ValueError("Unexpected JSON structure")

if not isinstance(findings, list):
    raise ValueError("Findings must be a list")

print("Findings found:", len(findings))

with output.open("w", encoding="utf-8") as f:
    json.dump({"findings": findings}, f, indent=2)

print("Output created:", output)
