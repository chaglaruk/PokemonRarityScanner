"""Assess whether *real-game* costume benchmarks have valid same-stratum references.

Input: a LOCAL, private JSON manifest of already human-labelled screenshot hashes.
Never commit captures, private manifest, filenames, paths, or raw OCR.
Different-species nearest neighbours do not qualify as costume references.
This tool makes no image-classification or accuracy claims.
"""
from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path

SHA256_PATTERN = re.compile(r"^[a-fA-F0-9]{64}$")
ROLES = frozenset(("reference", "development", "holdout"))
MAX_RECORDS = 100_000


class ManifestError(ValueError):
    """A bounded, privacy-preserving schema or partition error."""


def audit_manifest(document: dict) -> dict:
    if not isinstance(document, dict) or document.get("schemaVersion") != 1:
        raise ManifestError("unsupported_manifest_schema")
    records = document.get("records")
    if not isinstance(records, list) or len(records) > MAX_RECORDS:
        raise ManifestError("invalid_record_count")

    hashes = set()
    group_roles = defaultdict(set)
    strata = defaultdict(list)
    unconfirmed = 0
    for index, row in enumerate(records):
        if not isinstance(row, dict):
            raise ManifestError(f"invalid_record_{index}")
        # Manifest must only carry opaque metadata. Reject private paths or images.
        forbidden = {"path", "filepath", "filename", "image", "imageData",
                     "screenshot", "ocr", "rawOcr", "rawText"}
        if any(key in row for key in forbidden):
            raise ManifestError(f"private_payload_field_{index}")
        digest = row.get("sha256")
        species = row.get("species")
        group = row.get("captureGroup")
        role = row.get("role")
        form = row.get("formId", "00")
        if not isinstance(digest, str) or not SHA256_PATTERN.fullmatch(digest):
            raise ManifestError(f"invalid_digest_{index}")
        if digest.lower() in hashes:
            raise ManifestError(f"duplicate_frame_digest_{index}")
        hashes.add(digest.lower())
        if not isinstance(species, str) or not species.strip() or len(species) > 96:
            raise ManifestError(f"invalid_species_{index}")
        if not isinstance(group, str) or not group.strip() or len(group) > 96:
            raise ManifestError(f"invalid_capture_group_{index}")
        if not isinstance(form, str) or not form or len(form) > 48:
            raise ManifestError(f"invalid_form_{index}")
        if role not in ROLES:
            raise ManifestError(f"invalid_partition_{index}")
        if type(row.get("shiny")) is not bool or type(row.get("costume")) is not bool:
            raise ManifestError(f"missing_ground_truth_{index}")
        if row.get("truthStatus") != "human_confirmed":
            unconfirmed += 1
            continue
        group_roles[group].add(role)
        strata[(species.casefold(), form.casefold(), row["shiny"])].append(row)
    leaked = sorted(group for group, roles in group_roles.items() if len(roles) > 1)
    if leaked:
        raise ManifestError("capture_group_leaks_across_partitions")

    results = []
    totals = {
        "sameStratumPaired": 0,
        "developmentFramesEligible": 0,
        "developmentFramesIneligible": 0,
        "holdoutFramesEligible": 0,
        "holdoutFramesIneligible": 0,
        "strataWithPairedHoldoutClasses": 0,
    }
    for key, members in sorted(strata.items()):
        ref = [x for x in members if x["role"] == "reference"]
        dev = [x for x in members if x["role"] == "development"]
        holdout = [x for x in members if x["role"] == "holdout"]
        normal_ref_groups = {x["captureGroup"] for x in ref if not x["costume"]}
        costume_ref_groups = {x["captureGroup"] for x in ref if x["costume"]}
        paired = bool(normal_ref_groups and costume_ref_groups)
        holdout_pos = sum(x["costume"] for x in holdout)
        holdout_neg = len(holdout) - holdout_pos
        totals["sameStratumPaired"] += int(paired)
        totals["developmentFramesEligible" if paired else "developmentFramesIneligible"] += len(dev)
        totals["holdoutFramesEligible" if paired else "holdoutFramesIneligible"] += len(holdout)
        sufficient_holdout = paired and holdout_pos > 0 and holdout_neg > 0
        totals["strataWithPairedHoldoutClasses"] += int(sufficient_holdout)
        results.append({
            "species": members[0]["species"],
            "formId": members[0].get("formId", "00"),
            "shiny": key[2],
            "normalReferenceGroups": len(normal_ref_groups),
            "costumeReferenceGroups": len(costume_ref_groups),
            "pairedReferenceReady": paired,
            "developmentFrames": len(dev),
            "holdoutCostumeFrames": holdout_pos,
            "holdoutNormalFrames": holdout_neg,
            "holdoutBothClassesReady": sufficient_holdout,
        })
    return {
        "schemaVersion": 1,
        "scope": "same_species_form_shiny_reference_readiness_not_recognition_accuracy",
        "confirmedFrames": sum(len(m) for m in strata.values()),
        "unconfirmedFramesExcluded": unconfirmed,
        "strata": results,
        "totals": totals,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="Local private manifest; do not commit")
    parser.add_argument("--output", type=Path, help="Aggregate-only report path")
    args = parser.parse_args()
    try:
        doc = json.loads(args.manifest.read_text(encoding="utf-8"))
        report = audit_manifest(doc)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        # Deliberately do not echo source paths, hashes or record payloads.
        code = error.args[0] if isinstance(error, ManifestError) else "manifest_unreadable"
        parser.exit(2, f"ERROR: {code}\n")
    result = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result, encoding="utf-8")
    print(result, end="")


if __name__ == "__main__":
    main()
