"""Export a deterministic UNKNOWN-only review ledger from a candidate manifest.

This is a CLI tool that reads the committed candidate manifest and produces
an incomplete review ledger where every record has UNKNOWN / NOT_VERIFIED
status. No truth is inferred, no approval is granted.

The output is NOT committed — it serves as local validation evidence only.

Usage:
    python -m manual_gate.export_unknown_ledger \\
        --manifest path/to/candidate_2026_s25_manifest.json \\
        --output path/to/unknown_ledger.json
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from manual_gate.ledger_schema import (
    build_unknown_ledger,
    canonical_json_bytes,
    validate_canonical_bytes,
    validate_ledger,
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Export UNKNOWN-only review ledger from candidate manifest."
    )
    parser.add_argument(
        "--manifest",
        required=True,
        type=Path,
        help="Path to the candidate manifest JSON file.",
    )
    parser.add_argument(
        "--output",
        required=True,
        type=Path,
        help="Path for the output ledger JSON file.",
    )
    args = parser.parse_args(argv)

    manifest_path: Path = args.manifest
    output_path: Path = args.output

    if not manifest_path.exists():
        print(f"ERROR: Manifest not found: {manifest_path}", file=sys.stderr)
        return 1

    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    # Build the UNKNOWN-only ledger
    ledger = build_unknown_ledger(manifest)

    # Validate the ledger
    validate_ledger(ledger)

    # Serialize to canonical bytes
    output_bytes = canonical_json_bytes(ledger)
    validate_canonical_bytes(output_bytes)

    # Write output
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(output_bytes)

    # Summary
    print(f"Exported UNKNOWN-only ledger: {output_path}")
    print(f"  Total records: {ledger['totalRecords']}")
    print(f"  Development: {ledger['developmentRecords']}")
    print(f"  Holdout: {ledger['holdoutRecords']}")
    print(f"  Truth completed: {ledger['truthCompletedCount']}")
    print(f"  Privacy approved: {ledger['privacyApprovedCount']}")
    print(f"  Provenance verified: {ledger['provenanceVerifiedCount']}")
    print(f"  Suggestions promoted: {ledger['suggestionsPromotedCount']}")
    print(f"  Holdout truth exposure: {ledger['holdoutTruthExposureCount']}")
    print(f"  Gate status: {ledger['gateStatus']}")
    print(f"  Completion: {ledger['completionStatus']}")
    print(f"  Output bytes: {len(output_bytes)}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
