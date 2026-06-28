#!/usr/bin/env python3
"""Evaluate the local Pokemon GO visual descriptor model.

This intentionally evaluates the app's packaged runtime model first. The
generated report DB is useful source evidence, but production readiness depends
on the compact descriptor file the app actually loads.
"""

import argparse
import csv
import json
import math
import os
import statistics
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

import train_variant_prototypes as tvp  # noqa: E402


SAMPLE_STEP = 4
ANALYSIS_WIDTH = 360
HIGH_CONFIDENCE = 0.80
MIN_SPECIES_MARGIN = 0.05
MAX_STRONG_SCORE = 0.38
MAX_AUGMENTED_SAMPLE = 300


def load_json(path):
    with open(path, "r", encoding="utf-8-sig") as f:
        return json.load(f)


def write_csv(path, rows, fieldnames):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({name: row.get(name, "") for name in fieldnames})


def normalize(value):
    return str(value or "").strip().lower()


def rgb_to_hsv_tuple(pixel):
    r, g, b = pixel[:3]
    return tvp.rgb_to_hsv(r, g, b)


def hue_distance(a, b):
    diff = abs(a - b)
    return min(diff, 360.0 - diff)


def downscale_for_analysis(image):
    if image.width <= ANALYSIS_WIDTH:
        return image.convert("RGBA")
    ratio = ANALYSIS_WIDTH / float(image.width)
    target_height = max(1, int(image.height * ratio))
    return image.convert("RGBA").resize((ANALYSIS_WIDTH, target_height), Image.Resampling.BILINEAR)


def background_regions(width, height):
    top = int(height * 0.12)
    bottom = min(height, int(height * 0.35))
    left_width = min(width, int(width * 0.15))
    right_start = max(0, int(width * 0.85))
    return [(0, top, left_width, bottom), (right_start, top, width, bottom)]


def estimate_background_hsv(image):
    sum_x = 0.0
    sum_y = 0.0
    s_sum = 0.0
    v_sum = 0.0
    count = 0
    for left, top, right, bottom in background_regions(image.width, image.height):
        for y in range(top, bottom, SAMPLE_STEP):
            for x in range(left, right, SAMPLE_STEP):
                h, s, v = rgb_to_hsv_tuple(image.getpixel((x, y)))
                if v > 0.05:
                    rad = math.radians(h)
                    sum_x += math.cos(rad)
                    sum_y += math.sin(rad)
                    s_sum += s
                    v_sum += v
                    count += 1
    if count == 0:
        return (0.0, 0.0, 0.0)
    avg_hue = math.degrees(math.atan2(sum_y, sum_x))
    if avg_hue < 0:
        avg_hue += 360.0
    return (avg_hue, s_sum / count, v_sum / count)


def fallback_sprite_region(width, height):
    center_x = width // 2
    center_y = int(height * 0.38)
    half_w = int(width * 0.30)
    half_h = int(height * 0.25)
    return (
        max(0, center_x - half_w),
        max(0, center_y - half_h),
        min(width, center_x + half_w),
        min(height, center_y + half_h),
    )


def sprite_region_adaptive(image):
    bg = estimate_background_hsv(image)
    width, height = image.size
    left_bound = int(width * 0.08)
    right_bound = int(width * 0.92)
    top_bound = int(height * 0.18)
    default_bottom = int(height * 0.78)
    card_top = detect_detail_card_top(image)
    bottom_bound = min(default_bottom, card_top + int(height * 0.04)) if card_top is not None else default_bottom
    bottom_bound = max(top_bound + 1, bottom_bound)
    min_x = right_bound
    max_x = left_bound
    min_y = bottom_bound
    max_y = top_bound
    for y in range(top_bound, bottom_bound, SAMPLE_STEP):
        for x in range(left_bound, right_bound, SAMPLE_STEP):
            h, s, v = rgb_to_hsv_tuple(image.getpixel((x, y)))
            h_diff = hue_distance(h, bg[0])
            s_diff = abs(s - bg[1])
            v_diff = abs(v - bg[2])
            is_foreground = (
                (h_diff > 16.0 and s > 0.15)
                or (s_diff > 0.20 and v > 0.15 and s > 0.10)
                or (v_diff > 0.25 and s > 0.12)
            )
            if is_foreground:
                min_x = min(min_x, x)
                max_x = max(max_x, x)
                min_y = min(min_y, y)
                max_y = max(max_y, y)
    if max_x <= min_x or max_y <= min_y:
        return fallback_sprite_region(width, height)
    pad_x = int(width * 0.02)
    pad_y = int(height * 0.02)
    return (
        max(0, min_x - pad_x),
        max(0, min_y - pad_y),
        min(width, max_x + pad_x),
        min(height, max_y + pad_y),
    )


def detect_detail_card_top(image):
    width, height = image.size
    start_y = int(height * 0.28)
    end_y = int(height * 0.65)
    start_x = int(width * 0.05)
    end_x = int(width * 0.95)
    consecutive = 0
    first_y = None
    for y in range(start_y, end_y, SAMPLE_STEP):
        samples = 0
        white = 0
        for x in range(start_x, end_x, SAMPLE_STEP):
            r, g, b, _ = image.getpixel((x, y))
            spread = max(r, g, b) - min(r, g, b)
            if r > 220 and g > 220 and b > 220 and spread < 35:
                white += 1
            samples += 1
        ratio = white / max(1, samples)
        if ratio > 0.55:
            if consecutive == 0:
                first_y = y
            consecutive += 1
            if consecutive >= 2:
                return first_y
        else:
            consecutive = 0
            first_y = None
    return None


def extract_masked_sprite_for_fixture(image):
    scaled = downscale_for_analysis(image)
    bg = estimate_background_hsv(scaled)
    box = sprite_region_adaptive(scaled)
    sprite = scaled.crop(box).convert("RGBA")
    pixels = sprite.load()
    for y in range(sprite.height):
        for x in range(sprite.width):
            r, g, b, a = pixels[x, y]
            h, s, v = tvp.rgb_to_hsv(r, g, b)
            h_diff = hue_distance(h, bg[0])
            s_diff = abs(s - bg[1])
            v_diff = abs(v - bg[2])
            is_bg = (h_diff < 12.0 and s_diff < 0.18 and v_diff < 0.18) or (s < 0.15 and v_diff < 0.20)
            if is_bg:
                pixels[x, y] = (0, 0, 0, 0)
    return sprite


def features_from_fixture(path):
    with Image.open(path) as image:
        masked = extract_masked_sprite_for_fixture(image)
        return tvp.extract_features(masked)


def histogram_distance(observed, reference):
    if not observed or not reference:
        return 1.0
    size = min(len(observed), len(reference))
    return max(0.0, min(1.0, sum(abs(float(observed[i]) - float(reference[i])) for i in range(size)) / 2.0))


def edge_distance(observed, reference):
    if not observed or not reference or len(observed) != len(reference):
        return 1.0
    return max(0.0, min(1.0, sum(abs(float(a) - float(b)) for a, b in zip(observed, reference)) / 2.0))


def score_features(features, entry):
    prototype = entry["prototype"]
    d_hash = tvp.hamming_hex(features["dHash"], prototype.get("dHash", "")) / 64.0
    a_hash = tvp.hamming_hex(features["aHash"], prototype.get("aHash", "")) / 64.0
    edge = edge_distance(features["edge"], prototype.get("edge", []))
    full_hist = histogram_distance(features["fullHist"], prototype.get("fullHist", []))
    head_hist = histogram_distance(features["headHist"], prototype.get("headHist", []))
    upper_hist = histogram_distance(features["upperHist"], prototype.get("upperHist", []))
    body_hist = histogram_distance(features["bodyHist"], prototype.get("bodyHist", []))
    fg = abs(float(features["foregroundRatio"]) - float(prototype.get("foregroundRatio", 0.0)))
    aspect = min(1.0, abs(float(features["aspectRatio"]) - float(prototype.get("aspectRatio", 0.0))))
    return max(
        0.0,
        min(
            1.0,
            0.23 * d_hash
            + 0.18 * a_hash
            + 0.15 * edge
            + 0.15 * body_hist
            + 0.14 * head_hist
            + 0.09 * upper_hist
            + 0.08 * full_hist
            + 0.04 * fg
            + 0.02 * aspect,
        ),
    )


def classify_features(features, entries, scope):
    if not entries:
        return None
    scored = sorted(((entry, score_features(features, entry)) for entry in entries), key=lambda item: item[1])
    best_entry, best_score = scored[0]
    by_species = {}
    for entry, score in scored:
        species = entry.get("species", "")
        by_species[species] = min(score, by_species.get(species, score))
    species_best = sorted(by_species.items(), key=lambda item: item[1])
    second_species_score = species_best[1][1] if len(species_best) > 1 else best_score + 0.25
    second_entry_score = scored[1][1] if len(scored) > 1 else best_score + 0.25
    species_margin = max(0.0, second_species_score - species_best[0][1])
    variant_margin = max(0.0, second_entry_score - best_score)
    absolute_strength = 1.0 - min(1.0, max(0.0, best_score))
    if scope == "species":
        confidence = max(0.0, min(1.0, 0.70 * absolute_strength + 0.30 * min(1.0, variant_margin / 0.12)))
    else:
        confidence = max(
            0.0,
            min(
                1.0,
                0.55 * absolute_strength
                + 0.30 * min(1.0, species_margin / 0.20)
                + 0.15 * min(1.0, variant_margin / 0.15),
            ),
        )
    best_base = next(((entry, score) for entry, score in scored if entry.get("variantType") == "base"), None)
    best_non_base = next(((entry, score) for entry, score in scored if entry.get("variantType") != "base"), None)
    return {
        "species": best_entry.get("species", ""),
        "assetKey": best_entry.get("assetKey", ""),
        "spriteKey": best_entry.get("spriteKey", ""),
        "variantType": best_entry.get("variantType", ""),
        "isShiny": bool(best_entry.get("isShiny", False)),
        "isCostumeLike": bool(best_entry.get("isCostumeLike", False)),
        "scope": scope,
        "score": round(best_score, 6),
        "confidence": round(confidence, 6),
        "speciesMargin": round(species_margin, 6),
        "variantMargin": round(variant_margin, 6),
        "candidateCount": len(entries),
        "bestBaseScore": round(best_base[1], 6) if best_base else None,
        "bestBaseSpriteKey": best_base[0].get("spriteKey", "") if best_base else None,
        "bestNonBaseScore": round(best_non_base[1], 6) if best_non_base else None,
        "bestNonBaseSpriteKey": best_non_base[0].get("spriteKey", "") if best_non_base else None,
        "topSpecies": [f"{species}:{score:.3f}" for species, score in species_best[:3]],
    }


def is_strong(match):
    return bool(
        match
        and match["confidence"] >= HIGH_CONFIDENCE
        and match["score"] <= MAX_STRONG_SCORE
        and match["speciesMargin"] >= MIN_SPECIES_MARGIN
    )


def expected_flag(expected, name):
    if name not in expected:
        return None
    return bool(expected.get(name))


def variant_prediction(match, flag):
    if not match:
        return None
    if flag == "shiny":
        return bool(match.get("isShiny"))
    if flag == "costume":
        return bool(match.get("isCostumeLike")) or match.get("variantType") == "costume"
    return None


def evaluate_fixtures(cases, fixture_root, entries, by_species):
    rows = []
    species_confusion = Counter()
    variant_confusion = Counter()
    false_positive_rows = []
    false_negative_rows = []
    subtle_rows = []
    latencies = []
    labeled_species = 0
    species_correct = 0
    strict_species_total = 0
    strict_species_correct = 0
    fixture_decode_errors = 0
    supported_flags = ["shiny", "costume"]
    unsupported_flags = ["shadow", "purified", "lucky", "locationCard", "dynamax", "gigantamax"]

    for case in cases:
        expected = case.get("expected", {}) or {}
        expected_species = expected.get("species")
        asset_path = fixture_root / case.get("assetPath", "")
        if not asset_path.exists():
            rows.append(
                {
                    "caseId": case.get("id", ""),
                    "strict": bool(case.get("strict")),
                    "assetPath": case.get("assetPath", ""),
                    "expectedSpecies": expected_species or "",
                    "status": "missing_fixture",
                }
            )
            continue
        started = time.perf_counter()
        try:
            features = features_from_fixture(asset_path)
        except Exception as exc:
            fixture_decode_errors += 1
            rows.append(
                {
                    "caseId": case.get("id", ""),
                    "strict": bool(case.get("strict")),
                    "assetPath": case.get("assetPath", ""),
                    "expectedSpecies": expected_species or "",
                    "status": "feature_error",
                    "error": str(exc),
                }
            )
            continue
        extract_ms = (time.perf_counter() - started) * 1000.0

        global_started = time.perf_counter()
        global_match = classify_features(features, entries, "global")
        global_ms = (time.perf_counter() - global_started) * 1000.0

        species_match = None
        species_ms = 0.0
        scoped_entries = []
        if expected_species:
            scoped_entries = by_species.get(normalize(expected_species), [])
            species_started = time.perf_counter()
            species_match = classify_features(features, scoped_entries, "species")
            species_ms = (time.perf_counter() - species_started) * 1000.0

        chosen = species_match or global_match
        if expected_species:
            labeled_species += 1
            predicted_species = (global_match or {}).get("species", "")
            correct = normalize(predicted_species) == normalize(expected_species)
            species_correct += 1 if correct else 0
            if case.get("strict"):
                strict_species_total += 1
                strict_species_correct += 1 if correct else 0
            species_confusion[(expected_species, predicted_species, bool(case.get("strict")))] += 1

        for flag in supported_flags:
            exp = expected_flag(expected, flag)
            if exp is None:
                continue
            pred = variant_prediction(chosen, flag)
            variant_confusion[(flag, str(exp).lower(), str(pred).lower())] += 1
            strong = is_strong(chosen)
            if pred is True and exp is False and strong:
                false_positive_rows.append(
                    {
                        "caseId": case.get("id", ""),
                        "flag": flag,
                        "expected": exp,
                        "predicted": pred,
                        "confidence": chosen["confidence"],
                        "score": chosen["score"],
                        "margin": chosen["speciesMargin"],
                        "spriteKey": chosen["spriteKey"],
                        "reason": "high_confidence_false_positive",
                    }
                )
            if pred is False and exp is True and strong:
                false_negative_rows.append(
                    {
                        "caseId": case.get("id", ""),
                        "flag": flag,
                        "expected": exp,
                        "predicted": pred,
                        "confidence": chosen["confidence"],
                        "score": chosen["score"],
                        "margin": chosen["speciesMargin"],
                        "spriteKey": chosen["spriteKey"],
                        "reason": "high_confidence_false_negative",
                    }
                )
            if flag == "shiny" and exp is True:
                subtle_rows.append(
                    {
                        "caseId": case.get("id", ""),
                        "species": expected_species or "",
                        "predictedShiny": pred,
                        "confidence": chosen.get("confidence") if chosen else "",
                        "margin": chosen.get("speciesMargin") if chosen else "",
                        "status": "evaluated_fixture_shiny",
                    }
                )

        for flag in unsupported_flags:
            exp = expected_flag(expected, flag)
            if exp is None:
                continue
            predicted = "unsupported_no_score"
            variant_confusion[(flag, str(exp).lower(), predicted)] += 1
            if exp is True:
                false_negative_rows.append(
                    {
                        "caseId": case.get("id", ""),
                        "flag": flag,
                        "expected": exp,
                        "predicted": predicted,
                        "confidence": "",
                        "score": "",
                        "margin": "",
                        "spriteKey": "",
                        "reason": "descriptor_model_does_not_support_flag",
                    }
                )

        total_ms = extract_ms + global_ms + species_ms
        latencies.append(total_ms)
        rows.append(
            {
                "caseId": case.get("id", ""),
                "strict": bool(case.get("strict")),
                "assetPath": case.get("assetPath", ""),
                "expectedSpecies": expected_species or "",
                "globalSpecies": (global_match or {}).get("species", ""),
                "globalSpriteKey": (global_match or {}).get("spriteKey", ""),
                "globalConfidence": (global_match or {}).get("confidence", ""),
                "globalScore": (global_match or {}).get("score", ""),
                "globalSpeciesMargin": (global_match or {}).get("speciesMargin", ""),
                "speciesScopedSpriteKey": (species_match or {}).get("spriteKey", ""),
                "speciesScopedConfidence": (species_match or {}).get("confidence", ""),
                "speciesScopedScore": (species_match or {}).get("score", ""),
                "speciesScopedCandidateCount": len(scoped_entries),
                "extractMs": round(extract_ms, 3),
                "globalMatchMs": round(global_ms, 3),
                "speciesMatchMs": round(species_ms, 3),
                "totalMs": round(total_ms, 3),
                "status": "evaluated",
            }
        )

    metrics = {
        "fixture_count": len(cases),
        "labeled_species": labeled_species,
        "species_accuracy": species_correct / labeled_species if labeled_species else 0.0,
        "strict_species_total": strict_species_total,
        "strict_species_accuracy": strict_species_correct / strict_species_total if strict_species_total else 0.0,
        "fixture_decode_errors": fixture_decode_errors,
        "high_conf_false_positives": len(false_positive_rows),
        "high_conf_false_negatives": len(false_negative_rows),
        "avg_latency_ms": statistics.mean(latencies) if latencies else 0.0,
        "p95_latency_ms": percentile(latencies, 95),
    }
    return {
        "rows": rows,
        "metrics": metrics,
        "species_confusion": species_confusion,
        "variant_confusion": variant_confusion,
        "false_positive_rows": false_positive_rows,
        "false_negative_rows": false_negative_rows,
        "subtle_rows": subtle_rows,
        "latencies": latencies,
    }


def percentile(values, pct):
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, math.ceil((pct / 100.0) * len(ordered)) - 1))
    return ordered[index]


def augmented_asset_cases(entries, assets_dir):
    selected = []
    for entry in entries:
        path = assets_dir / "Images" / "Pokemon - 256x256" / entry.get("filename", "")
        if path.exists():
            selected.append((entry, path))
        if len(selected) >= MAX_AUGMENTED_SAMPLE:
            break
    return selected


def asset_augmented_evaluation(entries, by_species, assets_dir):
    selected = augmented_asset_cases(entries, assets_dir)
    total = 0
    species_correct = 0
    exact_sprite_correct = 0
    latencies = []
    confusion = Counter()
    for entry, path in selected:
        with Image.open(path) as image:
            base = tvp.crop_alpha(image)
            variants = [
                ("base", base),
                ("brightness_down", ImageEnhance.Brightness(base).enhance(0.86)),
                ("contrast_up", ImageEnhance.Contrast(base).enhance(1.15)),
                ("blur", base.filter(ImageFilter.GaussianBlur(radius=0.7))),
                ("compress_tint", ImageEnhance.Color(base).enhance(0.82)),
            ]
            for aug_name, aug_image in variants:
                started = time.perf_counter()
                features = tvp.extract_features(aug_image)
                scoped = by_species.get(normalize(entry.get("species", "")), entries)
                match = classify_features(features, scoped, "species")
                elapsed = (time.perf_counter() - started) * 1000.0
                latencies.append(elapsed)
                total += 1
                predicted_species = (match or {}).get("species", "")
                predicted_sprite = (match or {}).get("spriteKey", "")
                expected_species = entry.get("species", "")
                expected_sprite = entry.get("spriteKey", "")
                species_ok = normalize(predicted_species) == normalize(expected_species)
                sprite_ok = predicted_sprite == expected_sprite
                species_correct += 1 if species_ok else 0
                exact_sprite_correct += 1 if sprite_ok else 0
                if not species_ok:
                    confusion[(expected_species, predicted_species, aug_name)] += 1
    return {
        "asset_samples": len(selected),
        "augmented_cases": total,
        "augmented_species_accuracy": species_correct / total if total else 0.0,
        "augmented_exact_sprite_accuracy": exact_sprite_correct / total if total else 0.0,
        "avg_latency_ms": statistics.mean(latencies) if latencies else 0.0,
        "p95_latency_ms": percentile(latencies, 95),
        "confusion": confusion,
    }


def main():
    parser = argparse.ArgumentParser(description="Evaluate Pokemon GO visual descriptor readiness.")
    parser.add_argument("--runtime-model", default="app/src/main/assets/data/variant_classifier_model.json")
    parser.add_argument("--descriptor-db", default="build/reports/pogo_reference/visual_descriptor_db.json")
    parser.add_argument("--cases", default="app/src/androidTest/assets/scan_regression_cases.json")
    parser.add_argument("--fixtures-root", default="app/src/androidTest/assets")
    parser.add_argument("--assets-dir", default=".local/pogo_reference_cache/assets/pokeminers_pogo_assets")
    parser.add_argument("--report-root", default="build/reports/pogo_reference")
    args = parser.parse_args()

    report_root = Path(args.report_root)
    report_root.mkdir(parents=True, exist_ok=True)
    runtime_payload = load_json(args.runtime_model)
    entries = runtime_payload.get("entries", [])
    by_species = defaultdict(list)
    for entry in entries:
        by_species[normalize(entry.get("species", ""))].append(entry)

    cases = load_json(args.cases)
    fixture_eval = evaluate_fixtures(cases, Path(args.fixtures_root), entries, by_species)
    asset_eval = asset_augmented_evaluation(entries, by_species, Path(args.assets_dir))

    descriptor_db_entries = 0
    if os.path.exists(args.descriptor_db):
        descriptor_payload = load_json(args.descriptor_db)
        descriptor_db_entries = len(descriptor_payload.get("entries", []))

    species_rows = [
        {"expected": expected, "predicted": predicted, "strict": strict, "count": count}
        for (expected, predicted, strict), count in sorted(fixture_eval["species_confusion"].items())
    ]
    if not species_rows:
        species_rows = [{"expected": "unlabeled", "predicted": "unlabeled", "strict": "", "count": 0}]
    variant_rows = [
        {"flag": flag, "expected": expected, "predicted": predicted, "count": count}
        for (flag, expected, predicted), count in sorted(fixture_eval["variant_confusion"].items())
    ]
    if not variant_rows:
        variant_rows = [{"flag": "unlabeled", "expected": "unlabeled", "predicted": "unlabeled", "count": 0}]

    false_positive_rows = fixture_eval["false_positive_rows"]
    if not false_positive_rows:
        false_positive_rows = [
            {
                "caseId": "",
                "flag": "",
                "expected": "",
                "predicted": "",
                "confidence": "",
                "score": "",
                "margin": "",
                "spriteKey": "",
                "reason": "no_high_confidence_false_positive_in_labeled_fixture_eval",
            }
        ]
    false_negative_rows = fixture_eval["false_negative_rows"]
    if not false_negative_rows:
        false_negative_rows = [
            {
                "caseId": "",
                "flag": "",
                "expected": "",
                "predicted": "",
                "confidence": "",
                "score": "",
                "margin": "",
                "spriteKey": "",
                "reason": "no_high_confidence_false_negative_in_labeled_fixture_eval",
            }
        ]
    subtle_rows = fixture_eval["subtle_rows"] or [
        {
            "caseId": "",
            "species": "",
            "predictedShiny": "",
            "confidence": "",
            "margin": "",
            "status": "insufficient_labeled_subtle_shiny_fixtures",
        }
    ]

    write_csv(
        report_root / "confusion_matrix_species.csv",
        species_rows,
        ["expected", "predicted", "strict", "count"],
    )
    write_csv(
        report_root / "confusion_matrix_variant.csv",
        variant_rows,
        ["flag", "expected", "predicted", "count"],
    )
    write_csv(
        report_root / "false_positive_report.csv",
        false_positive_rows,
        ["caseId", "flag", "expected", "predicted", "confidence", "score", "margin", "spriteKey", "reason"],
    )
    write_csv(
        report_root / "false_negative_report.csv",
        false_negative_rows,
        ["caseId", "flag", "expected", "predicted", "confidence", "score", "margin", "spriteKey", "reason"],
    )
    write_csv(
        report_root / "subtle_shiny_report.csv",
        subtle_rows,
        ["caseId", "species", "predictedShiny", "confidence", "margin", "status"],
    )
    write_csv(
        report_root / "descriptor_fixture_predictions.csv",
        fixture_eval["rows"],
        [
            "caseId",
            "strict",
            "assetPath",
            "expectedSpecies",
            "globalSpecies",
            "globalSpriteKey",
            "globalConfidence",
            "globalScore",
            "globalSpeciesMargin",
            "speciesScopedSpriteKey",
            "speciesScopedConfidence",
            "speciesScopedScore",
            "speciesScopedCandidateCount",
            "extractMs",
            "globalMatchMs",
            "speciesMatchMs",
            "totalMs",
            "status",
            "error",
        ],
    )
    with open(report_root / "descriptor_fixture_predictions.json", "w", encoding="utf-8") as f:
        json.dump(fixture_eval["rows"], f, ensure_ascii=False, indent=2)

    acceptance = {
        "minimum_labeled_holdout": 50,
        "minimum_variant_positive_holdout_per_major_flag": 3,
        "fixture_species_accuracy": 0.98,
        "strict_fixture_species_accuracy": 0.98,
        "augmented_species_accuracy": 0.98,
        "high_confidence_false_positives": 0,
        "fast_narrowed_match_p95_ms": 100.0,
    }
    metrics = fixture_eval["metrics"]
    pass_reasons = []
    fail_reasons = []
    if metrics["labeled_species"] >= acceptance["minimum_labeled_holdout"]:
        pass_reasons.append("minimum_labeled_holdout_met")
    else:
        fail_reasons.append(f"labeled_holdout_too_small:{metrics['labeled_species']}<50")
    if metrics["fixture_decode_errors"] == 0:
        pass_reasons.append("fixture_decode_errors_met")
    else:
        fail_reasons.append(f"fixture_decode_errors:{metrics['fixture_decode_errors']}")
    if metrics["strict_species_accuracy"] >= acceptance["strict_fixture_species_accuracy"]:
        pass_reasons.append("strict_species_accuracy_met")
    else:
        fail_reasons.append(f"strict_species_accuracy_low:{metrics['strict_species_accuracy']:.3f}<0.98")
    if asset_eval["augmented_species_accuracy"] >= acceptance["augmented_species_accuracy"]:
        pass_reasons.append("augmented_species_accuracy_met")
    else:
        fail_reasons.append(f"augmented_species_accuracy_low:{asset_eval['augmented_species_accuracy']:.3f}<0.98")
    if metrics["high_conf_false_positives"] == 0:
        pass_reasons.append("no_high_confidence_fixture_false_positive")
    else:
        fail_reasons.append(f"high_confidence_false_positives:{metrics['high_conf_false_positives']}")
    if asset_eval["p95_latency_ms"] <= acceptance["fast_narrowed_match_p95_ms"]:
        pass_reasons.append("narrowed_asset_latency_met")
    else:
        fail_reasons.append(f"narrowed_asset_latency_p95_high:{asset_eval['p95_latency_ms']:.1f}ms")
    status = "READY" if not fail_reasons else "NOT READY"

    summary = [
        "# Descriptor Evaluation Summary",
        "",
        f"Status: {status}",
        "",
        "## Inputs",
        "",
        f"- runtime_model={args.runtime_model}",
        f"- runtime_entries={len(entries)}",
        f"- runtime_species={len(by_species)}",
        f"- descriptor_report_db={args.descriptor_db}",
        f"- descriptor_report_entries={descriptor_db_entries}",
        f"- fixture_cases={len(cases)}",
        f"- asset_augmented_samples={asset_eval['asset_samples']}",
        "",
        "## Acceptance Gates",
        "",
        f"- minimum_labeled_holdout={acceptance['minimum_labeled_holdout']}",
        f"- fixture_species_accuracy>={acceptance['fixture_species_accuracy']}",
        f"- strict_fixture_species_accuracy>={acceptance['strict_fixture_species_accuracy']}",
        f"- augmented_species_accuracy>={acceptance['augmented_species_accuracy']}",
        f"- high_confidence_false_positives={acceptance['high_confidence_false_positives']}",
        f"- narrowed_match_p95_ms<={acceptance['fast_narrowed_match_p95_ms']}",
        "",
        "## Fixture Holdout",
        "",
        f"- labeled_species={metrics['labeled_species']}",
        f"- fixture_species_accuracy={metrics['species_accuracy']:.3f}",
        f"- strict_species_total={metrics['strict_species_total']}",
        f"- strict_fixture_species_accuracy={metrics['strict_species_accuracy']:.3f}",
        f"- high_confidence_false_positives={metrics['high_conf_false_positives']}",
        f"- high_confidence_false_negatives={metrics['high_conf_false_negatives']}",
        f"- fixture_decode_errors={metrics['fixture_decode_errors']}",
        f"- fixture_avg_latency_ms={metrics['avg_latency_ms']:.1f}",
        f"- fixture_p95_latency_ms={metrics['p95_latency_ms']:.1f}",
        "",
        "## Augmented Asset Evaluation",
        "",
        f"- augmented_cases={asset_eval['augmented_cases']}",
        f"- augmented_species_accuracy={asset_eval['augmented_species_accuracy']:.3f}",
        f"- augmented_exact_sprite_accuracy={asset_eval['augmented_exact_sprite_accuracy']:.3f}",
        f"- augmented_avg_latency_ms={asset_eval['avg_latency_ms']:.1f}",
        f"- augmented_p95_latency_ms={asset_eval['p95_latency_ms']:.1f}",
        "",
        "## Pass Reasons",
        "",
        *(f"- {reason}" for reason in pass_reasons),
        "",
        "## Fail Reasons",
        "",
        *(f"- {reason}" for reason in fail_reasons),
        "",
        "## Readiness Interpretation",
        "",
        "This is a real local evaluation of the packaged descriptor model against tracked fixtures and augmented cached assets. It is still not a substitute for labeled real-device holdout coverage across shiny, shadow, lucky, purified, costume, background, Dynamax, Gigantamax, Mega, Primal, gender, and regional forms.",
    ]
    (report_root / "descriptor_eval_summary.md").write_text("\n".join(summary) + "\n", encoding="utf-8")

    latency = [
        "# Descriptor Latency Report",
        "",
        f"Status: {status}",
        "",
        f"- runtime_entries={len(entries)}",
        f"- fixture_avg_total_ms={metrics['avg_latency_ms']:.1f}",
        f"- fixture_p95_total_ms={metrics['p95_latency_ms']:.1f}",
        f"- augmented_avg_narrowed_ms={asset_eval['avg_latency_ms']:.1f}",
        f"- augmented_p95_narrowed_ms={asset_eval['p95_latency_ms']:.1f}",
        "- broad_search_default=for weak or missing OCR/species only",
        "- normal_fast_path_expected=species-scoped candidate narrowing",
        "- device_latency_status=not_measured_by_jvm_script",
    ]
    (report_root / "latency_descriptor_report.md").write_text("\n".join(latency) + "\n", encoding="utf-8")

    print(f"descriptor_eval_status={status}")
    print(f"runtime_entries={len(entries)}")
    print(f"descriptor_report_entries={descriptor_db_entries}")
    print(f"fixture_labeled_species={metrics['labeled_species']}")
    print(f"fixture_species_accuracy={metrics['species_accuracy']:.3f}")
    print(f"strict_fixture_species_accuracy={metrics['strict_species_accuracy']:.3f}")
    print(f"augmented_species_accuracy={asset_eval['augmented_species_accuracy']:.3f}")
    print(f"high_confidence_false_positives={metrics['high_conf_false_positives']}")
    print(f"fail_reasons={';'.join(fail_reasons)}")
    return 0 if status == "READY" else 2


if __name__ == "__main__":
    raise SystemExit(main())
