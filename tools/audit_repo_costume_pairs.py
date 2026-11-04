"""Audit source-verified same-species costume candidates already bundled as QA fixtures.

This identifies *potential* pairs, not independently validated form/capture-group
partitions, model recognition accuracy, or fresh holdout examples. All strict
fixtures are already used in regression tests and may not be rebranded as holdout.
No screenshots, source paths, hashes or OCR are written to the report.
"""
from __future__ import annotations

import hashlib
import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "androidTest" / "assets"
MANIFEST = ASSETS / "scan_regression_cases.json"
PNG_MAGIC = bytes((137, 80, 78, 71, 13, 10, 26, 10))


class FixtureAuditError(ValueError):
    """An incomplete or unsafe public-fixture audit."""


def audit_public_fixtures(rows: list, assets_root: Path) -> dict:
    if not isinstance(rows, list):
        raise FixtureAuditError("invalid_manifest")
    groups = defaultdict(lambda: {True: [], False: []})
    inspected = 0
    manual_labels = 0
    digests = set()
    explicit_form_count = 0
    for index, row in enumerate(rows):
        if not isinstance(row, dict) or row.get("strict") is not True:
            continue
        expected = row.get("expected")
        if not isinstance(expected, dict) or not isinstance(expected.get("species"), str):
            raise FixtureAuditError("invalid_strict_case")
        if type(expected.get("shiny")) is not bool or type(expected.get("costume")) is not bool:
            raise FixtureAuditError("missing_strict_truth")
        relative = row.get("assetPath")
        if not isinstance(relative, str) or not relative.startswith("scan_fixtures/"):
            raise FixtureAuditError("invalid_fixture_asset")
        root = assets_root.resolve()
        file = (assets_root / relative).resolve()
        if not file.is_relative_to(root) or not file.is_file():
            raise FixtureAuditError("missing_or_unsafe_fixture_asset")
        with file.open("rb") as stream:
            if stream.read(8) != PNG_MAGIC:
                raise FixtureAuditError("invalid_png_fixture")
            stream.seek(0)
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if digest in digests:
            raise FixtureAuditError("duplicate_png_fixture")
        digests.add(digest)
        inspected += 1
        species = expected["species"].strip()
        if not species or len(species) > 96:
            raise FixtureAuditError("invalid_species")
        shiny = expected["shiny"]
        costume = expected["costume"]
        form = expected.get("formId")
        explicit_form_count += int(isinstance(form, str) and bool(form))
        manual = str(row.get("notes") or "").startswith("Manual label from exported live fixture.")
        manual_labels += int(manual)
        groups[(species, shiny)][costume].append({
            "manual": manual,
            "form": form if isinstance(form, str) and form else None,
            "captureGroup": row.get("captureGroup"),
        })

    if inspected == 0:
        raise FixtureAuditError("no_strict_fixture_images")
    candidates = []
    for (species, shiny), classes in sorted(groups.items()):
        normals, costumes = classes[False], classes[True]
        if not normals or not costumes:
            continue
        # A candidate pair is not formally verified if form / capture-group
        # provenance is absent from the authoritative fixture manifest.
        form_verified = all(
            x["form"] is not None for x in normals + costumes
        ) and len({x["form"] for x in normals + costumes}) == 1
        group_verified = all(
            isinstance(x["captureGroup"], str) and x["captureGroup"]
            for x in normals + costumes
        )
        independent_groups = group_verified and not (
            {x["captureGroup"] for x in normals} &
            {x["captureGroup"] for x in costumes}
        )
        candidates.append({
            "species": species,
            "shiny": shiny,
            "normalImages": len(normals),
            "costumeImages": len(costumes),
            "manualLabelsClaimed": all(x["manual"] for x in normals + costumes),
            "sameFormExplicitlyVerified": form_verified,
            "distinctCaptureGroupsVerified": independent_groups,
            "independentHoldout": False,
        })

    return {
        "schemaVersion": 1,
        "scope": "public_regression_asset_candidate_pairs_not_holdout_or_accuracy",
        "strictFixtureImagesVerified": inspected,
        "strictCostumePositiveImages": sum(len(z[True]) for z in groups.values()),
        "strictCostumeNegativeImages": sum(len(z[False]) for z in groups.values()),
        "strictManualLabelClaimCount": manual_labels,
        "strictExplicitFormLabelCount": explicit_form_count,
        "potentialSameSpeciesShinyPairs": candidates,
        "independentHoldoutGroupsVerified": 0,
    }


def main() -> None:
    with MANIFEST.open(encoding="utf-8-sig") as stream:
        records = json.load(stream)
    report = audit_public_fixtures(records, ASSETS)
    print(json.dumps(report, indent=2, sort_keys=True))
    if __import__("os").environ.get("GITHUB_STEP_SUMMARY"):
        with open(__import__("os").environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as f:
            f.write("### Existing QA costume candidate pairs (not holdout)\n\n")
            f.write(f"Verified strict PNGs: {report['strictFixtureImagesVerified']}; ")
            f.write(f"costumed: {report['strictCostumePositiveImages']}; ")
            f.write(f"uncostumed: {report['strictCostumeNegativeImages']}.\n\n")
            for pair in report["potentialSameSpeciesShinyPairs"]:
                f.write(f"- {pair['species']} (Shiny={pair['shiny']}): ")
                f.write(f"{pair['normalImages']} normal, {pair['costumeImages']} costume; ")
                f.write("independent holdout **not established**.\n")


if __name__ == "__main__":
    main()
