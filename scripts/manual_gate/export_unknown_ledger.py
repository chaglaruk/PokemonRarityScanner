"""Export a deterministic, incomplete UNKNOWN-only Manual Gate A ledger."""

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


def _find_repo_root(path: Path) -> Path | None:
    current = path.resolve().parent
    for candidate in (current, *current.parents):
        if (candidate / "settings.gradle.kts").is_file():
            return candidate
    return None


def ensure_output_outside_repo(manifest_path: Path, output_path: Path) -> None:
    """Keep session ledgers outside Git when the manifest belongs to this repo."""
    repo_root = _find_repo_root(manifest_path)
    if repo_root is None:
        return
    destination = output_path.resolve()
    if destination == repo_root or repo_root in destination.parents:
        raise ValueError("UNKNOWN-only ledger output must be outside the repository")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(argv)

    manifest_path = args.manifest
    output_path = args.output
    if not manifest_path.is_file():
        print("ERROR: candidate manifest not found", file=sys.stderr)
        return 1

    try:
        ensure_output_outside_repo(manifest_path, output_path)
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        ledger = build_unknown_ledger(manifest)
        validate_ledger(ledger, manifest)
        output_bytes = canonical_json_bytes(ledger)
        validate_canonical_bytes(output_bytes)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_bytes(output_bytes)
    except (OSError, UnicodeError, json.JSONDecodeError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print("UNKNOWN-only ledger exported")
    print(f"  Total records: {ledger['totalRecords']}")
    print(f"  Development: {ledger['developmentRecords']}")
    print(f"  Holdout: {ledger['holdoutRecords']}")
    print("  Human truth/privacy/provenance approvals: 0")
    print("  Holdout truth exposure: 0")
    print("  Manual Gate A: OPEN / INCOMPLETE")
    print(f"  Output bytes: {len(output_bytes)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
