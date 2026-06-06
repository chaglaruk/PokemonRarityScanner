#!/usr/bin/env python3
"""Validate the Collection Score catalog before publishing."""

from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


COLLECTIONS = (
    "costumes",
    "events",
    "regionals",
    "specialSpecies",
    "currentAvailability",
    "metaDemand",
)
VERIFIED_STATUSES = {"verified_official", "verified_community"}
ALLOWED_STATUSES = VERIFIED_STATUSES | {"manual_review_needed", "unknown"}
PLACEHOLDER_TOKENS = (
    "REAL_URL_REQUIRED",
    "SEE_RULE_BELOW",
    "YYYY-MM-DD",
    "IN_PERSON_EVENT_NAME",
    "example.com",
    "pokemongolive.com/post/...",
    "https://pokemongolive.com/post/...",
    "ExampleSpecies",
    "Unverified Example",
)


class CatalogError(ValueError):
    """Raised when catalog validation fails."""


def load_catalog(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)
    if not isinstance(payload, dict):
        raise CatalogError("Catalog root must be a JSON object")
    return payload


def validate_catalog(catalog: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    _collect(errors, validate_schema, catalog)
    _collect(errors, validate_unique_ids, catalog)
    _collect(errors, validate_event_windows, catalog)
    _collect(errors, validate_source_links, catalog)
    _collect(errors, validate_manual_review, catalog)
    _collect(errors, validate_no_placeholders, catalog)
    return errors


def validate_schema(catalog: dict[str, Any]) -> None:
    version = catalog.get("version")
    if not isinstance(version, dict):
        raise CatalogError("version must be an object")
    _require_text(version, "version", "version")
    if not isinstance(version.get("schemaVersion"), int):
        raise CatalogError("version.schemaVersion must be an integer")

    for key in COLLECTIONS:
        value = catalog.get(key)
        if value is None:
            catalog[key] = []
            continue
        if not isinstance(value, list):
            raise CatalogError(f"{key} must be an array")
        for index, record in enumerate(value):
            if not isinstance(record, dict):
                raise CatalogError(f"{key}[{index}] must be an object")
            status = record.get("verificationStatus")
            if status not in ALLOWED_STATUSES:
                raise CatalogError(f"{key}[{index}] has invalid verificationStatus: {status}")

    for index, record in enumerate(catalog.get("costumes", [])):
        _require_text(record, "id", f"costumes[{index}]")
        _require_text(record, "species", f"costumes[{index}]")
        _require_text(record, "costumeName", f"costumes[{index}]")
        _require_text(record, "costumeType", f"costumes[{index}]")

    for index, record in enumerate(catalog.get("events", [])):
        _require_text(record, "id", f"events[{index}]")
        _require_text(record, "name", f"events[{index}]")
        _require_text(record, "eventType", f"events[{index}]")
        _require_text(record, "startDate", f"events[{index}]")
        _require_text(record, "endDate", f"events[{index}]")

    for index, record in enumerate(catalog.get("regionals", [])):
        _require_text(record, "species", f"regionals[{index}]")
        _require_text(record, "region", f"regionals[{index}]")

    for index, record in enumerate(catalog.get("specialSpecies", [])):
        _require_text(record, "species", f"specialSpecies[{index}]")
        _require_text(record, "category", f"specialSpecies[{index}]")
        if not isinstance(record.get("baseSpeciesScore"), int):
            raise CatalogError(f"specialSpecies[{index}].baseSpeciesScore must be an integer")

    for index, record in enumerate(catalog.get("currentAvailability", [])):
        _require_text(record, "species", f"currentAvailability[{index}]")
        _require_text(record, "availabilityType", f"currentAvailability[{index}]")
        if not isinstance(record.get("baseAvailabilityScore"), int):
            raise CatalogError(f"currentAvailability[{index}].baseAvailabilityScore must be an integer")

    for index, record in enumerate(catalog.get("metaDemand", [])):
        _require_text(record, "species", f"metaDemand[{index}]")
        _require_text(record, "demandLevel", f"metaDemand[{index}]")
        if not isinstance(record.get("metaScore"), int):
            raise CatalogError(f"metaDemand[{index}].metaScore must be an integer")


def validate_unique_ids(catalog: dict[str, Any]) -> None:
    seen: dict[str, str] = {}
    for key in COLLECTIONS:
        for index, record in enumerate(catalog.get(key, [])):
            record_id = _record_identity(key, record)
            if record_id in seen:
                raise CatalogError(f"Duplicate catalog identity {record_id!r}: {seen[record_id]} and {key}[{index}]")
            seen[record_id] = f"{key}[{index}]"


def validate_event_windows(catalog: dict[str, Any]) -> None:
    for index, record in enumerate(catalog.get("events", [])):
        start = _parse_date(record.get("startDate"), f"events[{index}].startDate")
        end = _parse_date(record.get("endDate"), f"events[{index}].endDate")
        if end < start:
            raise CatalogError(f"events[{index}] endDate is before startDate")


def validate_source_links(catalog: dict[str, Any]) -> None:
    for key in COLLECTIONS:
        for index, record in enumerate(catalog.get(key, [])):
            links = record.get("sourceLinks") or []
            if not isinstance(links, list) or not all(isinstance(link, str) for link in links):
                raise CatalogError(f"{key}[{index}].sourceLinks must be a string array")
            status = record.get("verificationStatus")
            if status in VERIFIED_STATUSES and not links:
                raise CatalogError(f"{key}[{index}] is verified but has no sourceLinks")
            for link in links:
                parsed = urlparse(link)
                if parsed.scheme not in {"http", "https"} or not parsed.netloc:
                    raise CatalogError(f"{key}[{index}] has invalid source URL: {link}")


def validate_manual_review(catalog: dict[str, Any]) -> None:
    for key in COLLECTIONS:
        for index, record in enumerate(catalog.get(key, [])):
            if record.get("verificationStatus") != "manual_review_needed":
                continue
            links = record.get("sourceLinks") or []
            if links:
                raise CatalogError(f"{key}[{index}] manual-review records must not carry sourceLinks")


def validate_no_placeholders(catalog: dict[str, Any]) -> None:
    payload = json.dumps(catalog, sort_keys=True)
    for token in PLACEHOLDER_TOKENS:
        if token in payload:
            raise CatalogError(f"Catalog still contains placeholder token: {token}")


def generate_report(old_catalog: dict[str, Any] | None, new_catalog: dict[str, Any]) -> str:
    old_catalog = old_catalog or {}
    lines = [
        "# Collection Catalog Update Report",
        "",
        f"- New version: {new_catalog.get('version', {}).get('version', 'unknown')}",
        f"- Schema version: {new_catalog.get('version', {}).get('schemaVersion', 'unknown')}",
        "",
    ]
    for key in COLLECTIONS:
        old_ids = {_record_identity(key, record): record for record in old_catalog.get(key, [])}
        new_ids = {_record_identity(key, record): record for record in new_catalog.get(key, [])}
        added = sorted(set(new_ids) - set(old_ids))
        removed = sorted(set(old_ids) - set(new_ids))
        changed = sorted(
            identity for identity in set(new_ids) & set(old_ids)
            if new_ids[identity] != old_ids[identity]
        )
        manual = [
            identity for identity, record in sorted(new_ids.items())
            if record.get("verificationStatus") == "manual_review_needed"
        ]
        lines.extend([
            f"## {key}",
            f"- Added: {len(added)}",
            f"- Changed: {len(changed)}",
            f"- Removed: {len(removed)}",
            f"- Needs review: {len(manual)}",
        ])
        if added:
            lines.append(f"- Added records: {', '.join(added)}")
        if changed:
            lines.append(f"- Changed records: {', '.join(changed)}")
        if removed:
            lines.append(f"- Removed records: {', '.join(removed)}")
        if manual:
            lines.append(f"- Manual review records: {', '.join(manual)}")
        lines.append("")

    lines.extend([
        "## Score Impact Preview",
        "",
        "These examples are static sanity checks for reviewer context.",
        "",
        "### Shadow Mewtwo",
        "- Expected high collection value only when verified catalog evidence and scan signals agree.",
        "- Manual-review meta-demand rows contribute 0 until verified.",
        "",
        "### Party Hat Pikachu",
        "- Verified retired costume plus matching event evidence should contribute variant and event context points.",
        "",
    ])
    return "\n".join(lines)


def _record_identity(collection: str, record: dict[str, Any]) -> str:
    if collection in {"costumes", "events"}:
        return str(record.get("id", "")).strip()
    if collection == "regionals":
        return f"{record.get('species', '')}:{record.get('region', '')}".strip()
    if collection == "specialSpecies":
        return str(record.get("species", "")).strip()
    if collection == "currentAvailability":
        return f"{record.get('species', '')}:{record.get('costumeId') or ''}:{record.get('availabilityType', '')}".strip()
    if collection == "metaDemand":
        return f"{record.get('species', '')}:{record.get('formId') or ''}:{record.get('demandLevel', '')}".strip()
    return ""


def _require_text(record: dict[str, Any], key: str, context: str) -> None:
    value = record.get(key)
    if not isinstance(value, str) or not value.strip():
        raise CatalogError(f"{context}.{key} must be a non-empty string")


def _parse_date(value: Any, context: str) -> date:
    if not isinstance(value, str):
        raise CatalogError(f"{context} must be a YYYY-MM-DD string")
    try:
        return date.fromisoformat(value)
    except ValueError as error:
        raise CatalogError(f"{context} must be a valid YYYY-MM-DD date") from error


def _collect(errors: list[str], validator: Any, catalog: dict[str, Any]) -> None:
    try:
        validator(catalog)
    except CatalogError as error:
        errors.append(str(error))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path, nargs="?", default=Path("catalog/v1/catalog.json"))
    parser.add_argument("--old-catalog", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    try:
        catalog = load_catalog(args.catalog)
        errors = validate_catalog(catalog)
        if errors:
            for error in errors:
                print(f"ERROR: {error}", file=sys.stderr)
            return 1
        if args.report:
            old_catalog = load_catalog(args.old_catalog) if args.old_catalog and args.old_catalog.exists() else None
            args.report.write_text(generate_report(old_catalog, catalog), encoding="utf-8")
        print(f"Catalog valid: {args.catalog}")
        return 0
    except (OSError, json.JSONDecodeError, CatalogError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
