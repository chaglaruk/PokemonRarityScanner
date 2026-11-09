#!/usr/bin/env python3
"""Compute private normal/Shiny icon-pair features for the WHOLE pinned source archive.

These are filename candidates, NOT verified Pokemon GO identity or Shiny truth.
Never write this output to APK assets or treat icons as in-game detail-screen frames.
"""

from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re

from PIL import Image

from download_visual_reference_assets import (
    Asset,
    DEFAULT_OUTPUT,
    DEFAULT_PINS,
    MANIFEST_NAME,
    PAIR_INDEX_NAME,
    read_pins,
    safe_relative_path,
    verified_local,
    write_json,
)
from train_variant_prototypes import extract_features

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_NAMES = ROOT / "app/src/main/assets/data/pokemon_names.json"
OUTPUT_NAME = ".candidate_shiny_signatures.json"
FORM_TOKEN = re.compile(r"^pokemon_icon_(?:pm)?0*\d+_(\d+)(?:_|\.png$)", re.I)
GIT_SHA = re.compile(r"^[0-9a-f]{40}$")


def read_verified_index(archive: Path, pins: Path) -> tuple[dict, dict[str, Asset]]:
    manifest_path = archive / MANIFEST_NAME
    index_path = archive / PAIR_INDEX_NAME
    if not manifest_path.is_file() or not index_path.is_file():
        raise ValueError("Complete offline --verify required before generating candidate features")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    index = json.loads(index_path.read_text(encoding="utf-8"))
    source_commit, _ = read_pins(pins)
    if (manifest.get("limited") is not False or
        manifest.get("scope") not in ("icons", "all") or
        manifest.get("sourceCommit") != source_commit or
        index.get("sourceCommit") != source_commit or
        index.get("assetScope") != manifest.get("scope") or
        index.get("byteStatus") != "source_git_blobs_verified_locally" or
        index.get("identityStatus") != "unverified_filename_candidates_only"):
        raise ValueError("Source pin, inventory scope, or verified-candidate status mismatch")
    assets = [Asset(**row) for row in manifest["assets"]]
    if index.get("verifiedAssetCount") != len(assets):
        raise ValueError("Incomplete or stale asset-index verification")
    by_path = {item.path: item for item in assets}
    if len(by_path) != len(assets):
        raise ValueError("Duplicate pinned asset path")
    pairs = index.get("pairs")
    if not isinstance(pairs, list) or index.get("pairCount") != len(pairs):
        raise ValueError("Candidate pair count mismatch")
    return index, by_path


def verify_pair(pair: dict, by_path: dict[str, Asset], archive: Path) -> tuple[Path, Path]:
    checked: list[Path] = []
    for prefix in ("normal", "shiny"):
        path = safe_relative_path(pair[prefix + "Path"])
        asset = by_path.get(path)
        expected = pair[prefix + "BlobSha"]
        if (asset is None or asset.kind != "icons" or asset.sha != expected or
            not isinstance(expected, str) or GIT_SHA.fullmatch(expected) is None):
            raise ValueError("Invalid or mismatched pinned pair source")
        local = archive.joinpath(*path.split("/"))
        if not verified_local(local, asset):
            raise ValueError("Asset contents do not match pinned Git blob: " + path)
        checked.append(local)
    if checked[0] == checked[1]:
        raise ValueError("A normal/Shiny candidate must use distinct source paths")
    return checked[0], checked[1]


def candidate_form_token(path: str) -> str | None:
    match = FORM_TOKEN.match(Path(path).name)
    return match.group(1) if match else None


def icon_features(path: Path) -> dict:
    with Image.open(path) as image:
        if image.format != "PNG" or image.width > 2048 or image.height > 2048:
            raise ValueError("Unexpected or oversized icon image")
        f = extract_features(image)
    return {
        "aHash": f["aHash"],
        "dHash": f["dHash"],
        "edge": f["edge"],
        "color": f["fullHist"],
        "bodyColor": f["bodyHist"],
        "foregroundRatio": f["foregroundRatio"],
    }


def compile_candidates(
    archive: Path,
    names: list,
    index: dict,
    by_path: dict[str, Asset],
) -> dict:
    records: list[dict] = []
    summary: Counter = Counter()
    covered_species: set[int] = set()
    seen_pairs: set[tuple[str, str]] = set()

    for pair in index["pairs"]:
        source_pair = (pair["normalPath"], pair["shinyPath"])
        if source_pair in seen_pairs:
            raise ValueError("Duplicate normal/Shiny source pair")
        seen_pairs.add(source_pair)
        normal, shiny = verify_pair(pair, by_path, archive)
        summary["hash_verified_pairs"] += 1
        dex = pair.get("dexNumberHint")
        if isinstance(dex, bool) or not isinstance(dex, int) or not 1 <= dex <= len(names):
            summary["pairs_without_valid_dex_hint"] += 1
            continue
        species = names[dex - 1]
        if not isinstance(species, str) or not species.strip():
            summary["pairs_without_canonical_species_name"] += 1
            continue

        if pair["normalBlobSha"] == pair["shinyBlobSha"]:
            summary["pairs_with_identical_asset_bytes"] += 1
        form_hint = candidate_form_token(pair["normalPath"])
        if form_hint is None:
            summary["pairs_without_numeric_form_hint"] += 1

        records.append({
            "dexNumberHint": dex,
            "speciesNameHint": species,
            "formIdHint": form_hint,
            "identityStatus": "unverified_filename_candidate",
            "normalPath": pair["normalPath"],
            "shinyPath": pair["shinyPath"],
            "normalBlobSha": pair["normalBlobSha"],
            "shinyBlobSha": pair["shinyBlobSha"],
            "sourceByteDifference": pair["normalBlobSha"] != pair["shinyBlobSha"],
            "normalFeatures": icon_features(normal),
            "shinyFeatures": icon_features(shiny),
        })
        covered_species.add(dex)
    summary["candidate_pairs_with_dex_hint"] = len(records)
    summary["species_with_filename_pair_candidates"] = len(covered_species)
    summary["species_without_filename_pair_candidates"] = len(names) - len(covered_species)
    return {
        "version": 1,
        "sourceCommit": index["sourceCommit"],
        "assetScope": index["assetScope"],
        "referenceType": "source_pinned_2d_icons_not_pokemon_go_detail_screens",
        "identityStatus": "unverified_candidates_not_training_truth",
        "runtimeUse": "prohibited_until_independent_crosswalk_and_real_detail_holdout",
        "features": "train_variant_prototypes.extract_features_no_augmentations",
        "summary": dict(sorted(summary.items())),
        "candidates": records,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--pins", type=Path, default=DEFAULT_PINS)
    parser.add_argument("--species-map", type=Path, default=DEFAULT_NAMES)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    archive = args.archive.resolve()
    destination = (args.output or archive / OUTPUT_NAME).resolve()
    if not destination.is_relative_to(archive):
        parser.error("Output must remain inside the private local asset archive, not APK/repository assets")
    index, by_path = read_verified_index(archive, args.pins)
    names = json.loads(args.species_map.read_text(encoding="utf-8"))
    if not isinstance(names, list) or len(names) < 1:
        raise ValueError("Expected a canonical species-name array")
    generated = compile_candidates(archive, names, index, by_path)
    write_json(destination, generated)
    print(json.dumps({"privateOutput": str(destination), "summary": generated["summary"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
