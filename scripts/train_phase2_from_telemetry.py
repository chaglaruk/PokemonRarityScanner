# Purpose: Merge verified telemetry screenshots into the Phase 2 variant model.
import argparse
import json
import math
import os
from datetime import datetime, timezone
from typing import Any

from PIL import Image


DEFAULT_MODEL = os.path.join("app", "src", "main", "assets", "data", "variant_phase2_model.json")
TRUTH_TARGETS = {
    "is_shiny": "isShiny",
    "has_costume": "hasCostume",
    "has_special_form": "hasSpecialForm",
    "has_location_card": "hasLocationCard",
}
POSITIVE_ONLY_MIN_COUNT = 2
PHASE2_TARGET_THRESHOLDS = {
    "isShiny": {"minConfidence": 0.64, "minMargin": 0.18, "requirePositivePrediction": True},
    "hasCostume": {"minConfidence": 0.64, "minMargin": 0.18, "requirePositivePrediction": True},
    "hasSpecialForm": {"minConfidence": 0.70, "minMargin": 0.18, "requirePositivePrediction": True},
    "hasLocationCard": {"minConfidence": 0.80, "minMargin": 0.20, "requirePositivePrediction": True},
}


def parse_bool(value: Any) -> bool | None:
    if value is None:
        return None
    text = str(value).strip().lower()
    if text in {"1", "true", "yes", "y"}:
        return True
    if text in {"0", "false", "no", "n"}:
        return False
    return None


def l2_normalize(values: list[float]) -> list[float]:
    norm = math.sqrt(sum(value * value for value in values))
    if not math.isfinite(norm) or norm == 0:
        return [0.0 for _ in values]
    return [value / norm for value in values]


def average_vectors(vectors: list[list[float]]) -> list[float] | None:
    if not vectors:
        return None
    length = len(vectors[0])
    sums = [0.0] * length
    for vector in vectors:
        for index, value in enumerate(vector[:length]):
            sums[index] += value
    return l2_normalize([value / len(vectors) for value in sums])


def merge_prototype(existing: list[float] | None, existing_count: int, vectors: list[list[float]]) -> tuple[list[float] | None, int]:
    new_average = average_vectors(vectors)
    if new_average is None:
        return existing, existing_count
    new_count = len(vectors)
    if not existing or existing_count <= 0:
        return new_average, new_count
    length = min(len(existing), len(new_average))
    total = existing_count + new_count
    merged = [
        (float(existing[index]) * existing_count + new_average[index] * new_count) / total
        for index in range(length)
    ]
    return l2_normalize(merged), total


def extract_feature_vector(image_path: str, image_config: dict[str, Any]) -> list[float]:
    size = int(image_config.get("size") or 32)
    crop = image_config.get("screenshotCrop") or {}
    with Image.open(image_path) as image:
        rgb = image.convert("RGB")
        width, height = rgb.size
        left = max(0, min(width - 1, round(width * float(crop.get("left", 0.15)))))
        top = max(0, min(height - 1, round(height * float(crop.get("top", 0.18)))))
        crop_width = max(1, min(width, round(width * float(crop.get("width", 0.7)))))
        crop_height = max(1, min(height, round(height * float(crop.get("height", 0.5)))))
        if left + crop_width > width:
            crop_width = width - left
        if top + crop_height > height:
            crop_height = height - top
        cropped = rgb.crop((left, top, left + crop_width, top + crop_height))
        resample = getattr(Image, "Resampling", Image).LANCZOS
        scaled = cropped.resize((size, size), resample)
        pixel_bytes = scaled.tobytes()

    grayscale = [
        (
            0.299 * pixel_bytes[index] +
            0.587 * pixel_bytes[index + 1] +
            0.114 * pixel_bytes[index + 2]
        ) / 255.0
        for index in range(0, len(pixel_bytes), 3)
    ]

    def at(x: int, y: int) -> float:
        return grayscale[y * size + x]

    sobel = [0.0] * (size * size)
    for y in range(1, size - 1):
        for x in range(1, size - 1):
            gx = (
                -at(x - 1, y - 1) + at(x + 1, y - 1)
                - 2.0 * at(x - 1, y) + 2.0 * at(x + 1, y)
                - at(x - 1, y + 1) + at(x + 1, y + 1)
            )
            gy = (
                at(x - 1, y - 1) + 2.0 * at(x, y - 1) + at(x + 1, y - 1)
                - at(x - 1, y + 1) - 2.0 * at(x, y + 1) - at(x + 1, y + 1)
            )
            sobel[y * size + x] = math.sqrt(gx * gx + gy * gy)
    return l2_normalize(sobel)


def load_manifest(manifest_path: str) -> list[dict[str, Any]]:
    records = []
    with open(manifest_path, "r", encoding="utf-8-sig") as handle:
        for line in handle:
            if line.strip():
                records.append(json.loads(line))
    return records


def collect_samples(manifest_path: str, model: dict[str, Any]) -> dict[str, dict[str, dict[bool, list[list[float]]]]]:
    manifest_dir = os.path.dirname(os.path.abspath(manifest_path))
    image_config = model.get("image") or {"size": 32, "screenshotCrop": {"left": 0.15, "top": 0.18, "width": 0.7, "height": 0.5}}
    samples: dict[str, dict[str, dict[bool, list[list[float]]]]] = {}

    for record in load_manifest(manifest_path):
        if not bool(record.get("verified")):
            continue
        species = str(record.get("truth", {}).get("species") or record.get("predicted", {}).get("species") or "").strip()
        image_rel = str(record.get("image") or "").strip()
        if not species or not image_rel:
            continue
        image_path = os.path.join(manifest_dir, image_rel.replace("/", os.sep))
        if not os.path.exists(image_path):
            continue

        truth = record.get("truth") or {}
        target_values = {
            model_target: parse_bool(truth.get(truth_key))
            for truth_key, model_target in TRUTH_TARGETS.items()
        }
        target_values = {target: value for target, value in target_values.items() if value is not None}
        if not target_values:
            continue

        vector = extract_feature_vector(image_path, image_config)
        species_samples = samples.setdefault(species, {})
        for target, value in target_values.items():
            target_samples = species_samples.setdefault(target, {True: [], False: []})
            target_samples[value].append(vector)

    return samples


def ensure_species_model(model: dict[str, Any], species: str) -> dict[str, Any]:
    species_models = model.setdefault("speciesModels", {})
    species_model = species_models.setdefault(species, {})
    species_model.setdefault("targets", {})
    return species_model


def update_model(model: dict[str, Any], samples: dict[str, dict[str, dict[bool, list[list[float]]]]]) -> dict[str, int]:
    summary = {"species": 0, "targets": 0, "positiveSamples": 0, "negativeSamples": 0}
    all_targets = set(model.get("targets") or [])

    for species, species_samples in sorted(samples.items()):
        species_model = ensure_species_model(model, species)
        supported_for_species = set((model.setdefault("supportedSpecies", {}).get(species)) or [])
        touched_species = False

        for target, target_samples in sorted(species_samples.items()):
            all_targets.add(target)
            target_model = species_model["targets"].setdefault(target, {})
            positive, positive_count = merge_prototype(
                target_model.get("positivePrototype"),
                int(target_model.get("positiveCount") or 0),
                target_samples.get(True, []),
            )
            negative, negative_count = merge_prototype(
                target_model.get("negativePrototype"),
                int(target_model.get("negativeCount") or 0),
                target_samples.get(False, []),
            )
            target_model["positiveCount"] = positive_count
            target_model["negativeCount"] = negative_count
            if positive is not None:
                target_model["positivePrototype"] = [round(value, 8) for value in positive]
            if negative is not None:
                target_model["negativePrototype"] = [round(value, 8) for value in negative]
            target_model["supported"] = bool(
                positive is not None and
                (negative is not None or positive_count >= POSITIVE_ONLY_MIN_COUNT)
            )
            if target_model["supported"]:
                supported_for_species.add(target)
            summary["positiveSamples"] += len(target_samples.get(True, []))
            summary["negativeSamples"] += len(target_samples.get(False, []))
            summary["targets"] += 1
            touched_species = True

        if touched_species:
            model["supportedSpecies"][species] = sorted(supported_for_species)
            summary["species"] += 1

    model["targets"] = sorted(all_targets)
    model["generatedAt"] = datetime.now(timezone.utc).isoformat()
    model["telemetryTrainingSummary"] = summary
    tune_thresholds(model)
    return summary


def tune_thresholds(model: dict[str, Any]) -> None:
    app_thresholds = model.setdefault("appThresholds", {})
    target_thresholds = app_thresholds.setdefault("targetThresholds", {})
    for target, values in PHASE2_TARGET_THRESHOLDS.items():
        target_thresholds.setdefault(target, {}).update(values)


def main() -> None:
    parser = argparse.ArgumentParser(description="Train Phase 2 variant prototypes from verified telemetry screenshots.")
    parser.add_argument("--manifest", required=True, help="Path to verified_manifest.jsonl")
    parser.add_argument("--base-model", default=DEFAULT_MODEL, help="Existing variant_phase2_model.json")
    parser.add_argument("--out", default=DEFAULT_MODEL, help="Output model JSON path")
    args = parser.parse_args()

    with open(args.base_model, "r", encoding="utf-8-sig") as handle:
        model = json.load(handle)

    samples = collect_samples(args.manifest, model)
    summary = update_model(model, samples)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(model, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    print(json.dumps({"output": args.out, **summary}, indent=2))


if __name__ == "__main__":
    main()
