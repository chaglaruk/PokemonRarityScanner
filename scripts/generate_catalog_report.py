#!/usr/bin/env python3
"""Generate a Markdown report for a Collection Score catalog update."""

from __future__ import annotations

import argparse
from pathlib import Path

from validate_catalog import generate_report, load_catalog, validate_catalog


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path, nargs="?", default=Path("catalog/v1/catalog.json"))
    parser.add_argument("--old-catalog", type=Path)
    parser.add_argument("--output", type=Path, default=Path("metadata-update-report.md"))
    args = parser.parse_args()

    catalog = load_catalog(args.catalog)
    errors = validate_catalog(catalog)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    old_catalog = load_catalog(args.old_catalog) if args.old_catalog and args.old_catalog.exists() else None
    args.output.write_text(generate_report(old_catalog, catalog), encoding="utf-8")
    print(f"Wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
