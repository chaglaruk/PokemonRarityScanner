"""Audit shipped costume reference coverage without implying accuracy from catalogue size.

The runtime first uses master_pokedex.json signatures, falling back to the older
costume_signatures.json only when no usable master signatures are loaded.
This script is read-only and requires Python's standard library only.
"""
import argparse
import json
import os
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "app" / "src" / "main" / "assets" / "data"
HEX_64 = re.compile(r"^[0-9a-fA-F]{16}$")


def read_json(path):
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def valid_signature(signature):
    """Conservatively check hash fields used by CostumeSignatureStore."""
    if not isinstance(signature, dict):
        return False
    if not all(HEX_64.fullmatch(str(signature.get(k) or "")) for k in ("aHash", "dHash")):
        return False
    for key in ("pHash", "headPHash"):
        if signature.get(key) and not HEX_64.fullmatch(str(signature[key])):
            return False
    edges = signature.get("edge")
    return isinstance(edges, list) and len(edges) == 8 and all(
        isinstance(x, (float, int)) and 0 <= x <= 1 for x in edges
    )


def analyze(master, legacy):
    entries = master.get("entries", [])
    signatures = legacy.get("signatures", [])
    if not isinstance(entries, list) or not isinstance(signatures, list):
        raise ValueError("Expected master.entries and legacy.signatures arrays")

    per_species = defaultdict(lambda: {
        "costumeEntries": 0,
        "costumeWithSignature": 0,
        "normalWithSignature": 0,
    })
    missing = []
    malformed = []
    duplicate = []
    known_keys = set()
    for row in entries:
        if not isinstance(row, dict):
            continue
        species = str(row.get("species") or "").strip()
        key = str(row.get("spriteKey") or "").strip()
        if not species or not key:
            continue
        if key in known_keys:
            duplicate.append(key)
        known_keys.add(key)
        sig = row.get("signature")
        has_signature = valid_signature(sig)
        if sig is not None and not has_signature:
            malformed.append(key)
        if row.get("isCostumeLike") is True:
            per_species[species]["costumeEntries"] += 1
            if has_signature:
                per_species[species]["costumeWithSignature"] += 1
            else:
                missing.append(key)
        elif has_signature:
            per_species[species]["normalWithSignature"] += 1

    legacy_keys = [str(row.get("key") or "") for row in signatures if isinstance(row, dict)]
    legacy_set = set(legacy_keys)
    legacy_costumes = [
        row for row in signatures
        if isinstance(row, dict) and row.get("isCostume") is True
    ]
    costume_species = {
        species: value for species, value in per_species.items() if value["costumeEntries"]
    }
    result = {
        "schemaVersion": 1,
        "scope": "bundled_catalogue_and_signature_coverage_not_accuracy",
        "masterEntryCount": len(entries),
        "masterSpeciesCount": len(per_species),
        "masterCostumeEntries": sum(v["costumeEntries"] for v in costume_species.values()),
        "masterCostumeSpecies": len(costume_species),
        "masterCostumesWithWellFormedSignatures": sum(
            v["costumeWithSignature"] for v in costume_species.values()
        ),
        "masterCostumesMissingOrMalformedSignatures": len(missing),
        "masterEntriesWithInvalidNonNullSignatures": len(malformed),
        "masterDuplicateSpriteKeys": len(duplicate),
        "costumeSpeciesWithoutNormalSignature": sum(
            v["normalWithSignature"] == 0 for v in costume_species.values()
        ),
        "legacySignatureEntries": len(signatures),
        "legacyCostumeEntries": len(legacy_costumes),
        "legacySpeciesCount": len({
            str(row.get("species") or "") for row in signatures if isinstance(row, dict)
        }),
        "legacyDuplicateKeys": len(legacy_keys) - len(legacy_set),
        "legacyKeysNotInMaster": sum(key not in known_keys for key in legacy_set),
        "largestCostumeFamilies": [
            {"species": species, **counts}
            for species, counts in sorted(
                costume_species.items(),
                key=lambda item: (-item[1]["costumeEntries"], item[0])
            )[:12]
        ],
        "missingSignatureExamples": sorted(missing)[:12],
        "invalidSignatureExamples": sorted(malformed)[:12],
        "duplicateKeyExamples": sorted(duplicate)[:12],
    }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = analyze(read_json(DATA / "master_pokedex.json"),
                     read_json(DATA / "costume_signatures.json"))
    rendered = json.dumps(result, sort_keys=True, indent=2) + "\n"
    print(rendered, end="")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as output:
            output.write("### Costume reference coverage (not recognition accuracy)\n\n")
            output.write("| Metric | Count |\n|---|---:|\n")
            for field in ("masterCostumeEntries", "masterCostumeSpecies",
                          "masterCostumesWithWellFormedSignatures",
                          "masterCostumesMissingOrMalformedSignatures",
                          "costumeSpeciesWithoutNormalSignature",
                          "legacyCostumeEntries", "legacySpeciesCount"):
                output.write(f"| {field} | {result[field]} |\n")
            output.write("\nThese are reference inventory counts, not verified in-game recognition.\n")


if __name__ == "__main__":
    main()
