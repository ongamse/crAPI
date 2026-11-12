
import argparse
import csv
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--config", required=True)
    args = parser.parse_args()

    with open(args.config, encoding="utf-8") as f:
        config = json.load(f)

    findings = []

    with open(args.input, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)

        required = {"Issue Name", "Severity", "Issues", "Last Seen"}
        missing = required - set(reader.fieldnames or [])

        if missing:
            raise ValueError(
                f"Missing required CSV columns: {sorted(missing)}"
            )

        for row_number, row in enumerate(reader, start=2):
            findings.append({
                "title": row["Issue Name"],
                "severity": row["Severity"],
                "issue_count": row["Issues"],
                "virtual_patch_eligibility": row.get(
                    "Virtual Patch Eligibility", ""
                ),
                "framework_references": row.get(
                    "Framework References", ""
                ),
                "last_seen": row["Last Seen"],
                "source": "Harness API Testing",
                "source_row": row_number,
                "endpoint": {
                    "method": None,
                    "path": None
                },
                "evidence_status": "summary_only"
            })

    result = {
        "application": config["application_id"],
        "environment": config["environment"],
        "scan": config.get("api_testing_scan"),
        "source_format": "csv_summary",
        "findings": findings
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    output.write_text(
        json.dumps(result, indent=2),
        encoding="utf-8"
    )

    print(f"Imported {len(findings)} issue groups")
    print(f"Saved to: {output}")
    print("Endpoint-level evidence: not included in CSV")


if __name__ == "__main__":
    main()
