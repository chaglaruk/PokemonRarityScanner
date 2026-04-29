# Purpose: Build a local training manifest from manually collected scan screenshots.
import argparse
import csv
import hashlib
import json
import os
import re
import shutil
from pathlib import Path
from typing import Any

from PIL import Image


TRUTH_FIELDS = (
    "species",
    "is_shiny",
    "has_costume",
    "has_location_card",
    "has_special_form",
    "form",
)
PREDICTED_FIELDS = {
    "species": "predicted_species",
    "is_shiny": "predicted_is_shiny",
    "has_costume": "predicted_has_costume",
    "has_location_card": "predicted_has_location_card",
    "has_special_form": "predicted_has_special_form",
    "cp": "predicted_cp",
    "hp": "predicted_hp",
    "rarity_score": "predicted_rarity_score",
    "rarity_tier": "predicted_rarity_tier",
}
PHASE2_FIELDS = {
    "isShiny": "is_shiny",
    "hasCostume": "has_costume",
    "hasLocationCard": "has_location_card",
    "hasSpecialForm": "has_special_form",
}
HIGH_CONFIDENCE_THRESHOLD = 0.78
LOW_CONFIDENCE_THRESHOLD = 0.62


def read_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8-sig") as handle:
        data = json.load(handle)
    return data if isinstance(data, dict) else {}


def load_metadata(paths: list[Path]) -> dict[str, dict[str, Any]]:
    records: dict[str, dict[str, Any]] = {}
    for path in paths:
        if not path.exists():
            continue
        if path.suffix.lower() == ".jsonl":
            load_manifest_records(path, records)
        else:
            load_export_records(path, records)
    return records


def load_export_records(path: Path, records: dict[str, dict[str, Any]]) -> None:
    items = read_json(path).get("items") or []
    for item in items:
        if not isinstance(item, dict):
            continue
        store_record_aliases(records, item)


def load_manifest_records(path: Path, records: dict[str, dict[str, Any]]) -> None:
    with path.open("r", encoding="utf-8-sig") as handle:
        for line in handle:
            if not line.strip():
                continue
            record = json.loads(line)
            store_record_aliases(records, from_manifest_record(record))


def store_record_aliases(records: dict[str, dict[str, Any]], item: dict[str, Any]) -> None:
    for key in (clean_text(item.get("upload_id")), clean_text(item.get("id"))):
        if key and key not in records:
            records[key] = item


def from_manifest_record(record: dict[str, Any]) -> dict[str, Any]:
    predicted = record.get("predicted") or {}
    truth = record.get("truth") or {}
    return {
        "id": record.get("id"),
        "upload_id": record.get("upload_id"),
        "created_at": record.get("created_at"),
        "device_model": record.get("device_model"),
        "predicted_species": predicted.get("species"),
        "predicted_is_shiny": predicted.get("is_shiny"),
        "predicted_has_costume": predicted.get("has_costume"),
        "predicted_has_location_card": predicted.get("has_location_card"),
        "predicted_has_special_form": predicted.get("has_special_form"),
        "predicted_cp": predicted.get("cp"),
        "predicted_hp": predicted.get("hp"),
        "predicted_rarity_score": predicted.get("rarity_score"),
        "predicted_rarity_tier": predicted.get("rarity_tier"),
        "user_truth_species": truth.get("species"),
        "user_truth_is_shiny": truth.get("is_shiny"),
        "user_truth_has_costume": truth.get("has_costume"),
        "user_truth_has_location_card": truth.get("has_location_card"),
        "user_truth_has_special_form": truth.get("has_special_form"),
        "user_truth_form": truth.get("form"),
        "payload": record.get("payload"),
    }


def clean_text(value: Any) -> str:
    return str(value or "").strip()


def safe_name(value: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9._-]+", "_", value.strip())
    return safe.strip("_") or "unknown"


def parse_bool(value: Any) -> bool | None:
    text = clean_text(value).lower()
    if text in {"1", "true", "yes", "y"}:
        return True
    if text in {"0", "false", "no", "n"}:
        return False
    return None


def truth_from_metadata(item: dict[str, Any]) -> dict[str, str]:
    truth: dict[str, str] = {}
    for field in TRUTH_FIELDS:
        value = clean_text(item.get(f"user_truth_{field}"))
        if value:
            truth[field] = value
    return truth


def predicted_from_metadata(item: dict[str, Any]) -> dict[str, str]:
    predicted: dict[str, str] = {}
    for field, source in PREDICTED_FIELDS.items():
        value = clean_text(item.get(source))
        if value:
            predicted[field] = value
    return predicted


def payload_from_metadata(item: dict[str, Any]) -> dict[str, Any]:
    payload = item.get("payload")
    if isinstance(payload, dict):
        return payload
    text = clean_text(item.get("payload_json"))
    if not text:
        return {}
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def phase2_from_payload(payload: dict[str, Any]) -> dict[str, Any]:
    debug = payload.get("debug") if isinstance(payload.get("debug"), dict) else {}
    phase2 = debug.get("phase2") if isinstance(debug.get("phase2"), dict) else {}
    return phase2 if isinstance(phase2, dict) else {}


def phase2_predictions(phase2: dict[str, Any]) -> dict[str, dict[str, Any]]:
    predictions: dict[str, dict[str, Any]] = {}
    for item in phase2.get("predictions") or []:
        if not isinstance(item, dict):
            continue
        target = clean_text(item.get("target"))
        if target:
            predictions[target] = item
    return predictions


def auto_label(item: dict[str, Any]) -> dict[str, Any]:
    predicted = predicted_from_metadata(item)
    payload = payload_from_metadata(item)
    phase2 = phase2_from_payload(payload)
    target_predictions = phase2_predictions(phase2)
    labels: dict[str, Any] = {
        "species": predicted.get("species"),
        "source": "telemetry_prediction",
        "confidence": 0.0,
        "needs_review": True,
        "reasons": [],
    }

    confidences: list[float] = []
    for target, field in PHASE2_FIELDS.items():
        phase2_item = target_predictions.get(target)
        metadata_value = parse_bool(predicted.get(field))
        if phase2_item:
            labels[field] = bool(phase2_item.get("predictedValue"))
            confidence = float(phase2_item.get("confidence") or 0.0)
            confidences.append(confidence)
            if bool(phase2_item.get("passedThreshold")):
                labels["reasons"].append(f"{target}:phase2_pass")
            elif confidence < LOW_CONFIDENCE_THRESHOLD:
                labels["reasons"].append(f"{target}:low_confidence")
        elif metadata_value is not None:
            labels[field] = metadata_value
            labels["reasons"].append(f"{target}:metadata_only")

    if predicted.get("species") and not confidences:
        confidences.append(0.72)
    labels["confidence"] = round(min(confidences) if confidences else 0.0, 4)
    labels["needs_review"] = labels["confidence"] < HIGH_CONFIDENCE_THRESHOLD
    return labels


def image_info(path: Path) -> dict[str, Any]:
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    with Image.open(path) as image:
        width, height = image.size
    return {"sha256": digest, "width": width, "height": height}


def build_record(source: Path, image_rel: str, item: dict[str, Any] | None) -> dict[str, Any]:
    item = item or {}
    truth = truth_from_metadata(item)
    payload = payload_from_metadata(item)
    debug = payload.get("debug") if isinstance(payload.get("debug"), dict) else {}
    return {
        "id": clean_text(item.get("id")) or source.stem,
        "upload_id": clean_text(item.get("upload_id")) or source.stem,
        "image": image_rel,
        "source_file": source.name,
        "created_at": clean_text(item.get("created_at")),
        "device_model": clean_text(item.get("device_model")),
        "image_info": image_info(source),
        "predicted": predicted_from_metadata(item),
        "auto_label": auto_label(item) if item else {"needs_review": True, "reasons": ["missing_metadata"]},
        "truth": truth,
        "verified": bool(truth),
        "debug": {
            "scan_confidence": debug.get("scanConfidenceScore"),
            "event_confidence": debug.get("eventConfidenceLabel"),
            "explanation_mode": debug.get("explanationMode"),
            "phase2": phase2_from_payload(payload),
        },
    }


def write_jsonl(path: Path, records: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")))
            handle.write("\n")


def write_review_csv(path: Path, records: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "upload_id",
                "source_file",
                "species",
                "is_shiny",
                "has_costume",
                "has_location_card",
                "has_special_form",
                "confidence",
                "reasons",
            ],
        )
        writer.writeheader()
        for record in records:
            label = record.get("auto_label") or {}
            writer.writerow(
                {
                    "upload_id": record.get("upload_id"),
                    "source_file": record.get("source_file"),
                    "species": label.get("species"),
                    "is_shiny": label.get("is_shiny"),
                    "has_costume": label.get("has_costume"),
                    "has_location_card": label.get("has_location_card"),
                    "has_special_form": label.get("has_special_form"),
                    "confidence": label.get("confidence"),
                    "reasons": ";".join(label.get("reasons") or []),
                }
            )


def write_species_summary_csv(path: Path, records: list[dict[str, Any]]) -> None:
    rows: dict[str, dict[str, int]] = {}
    for record in records:
        predicted = record.get("predicted") or {}
        species = clean_text(predicted.get("species")) or clean_text((record.get("auto_label") or {}).get("species")) or "unknown"
        row = rows.setdefault(
            species,
            {
                "total": 0,
                "predicted_shiny": 0,
                "predicted_costume": 0,
                "predicted_location_card": 0,
                "predicted_special_form": 0,
                "needs_review": 0,
            },
        )
        row["total"] += 1
        row["predicted_shiny"] += int(parse_bool(predicted.get("is_shiny")) is True)
        row["predicted_costume"] += int(parse_bool(predicted.get("has_costume")) is True)
        row["predicted_location_card"] += int(parse_bool(predicted.get("has_location_card")) is True)
        row["predicted_special_form"] += int(parse_bool(predicted.get("has_special_form")) is True)
        row["needs_review"] += int(bool((record.get("auto_label") or {}).get("needs_review")))

    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "species",
                "total",
                "predicted_shiny",
                "predicted_costume",
                "predicted_location_card",
                "predicted_special_form",
                "needs_review",
            ],
        )
        writer.writeheader()
        for species, values in sorted(rows.items(), key=lambda item: (-item[1]["total"], item[0])):
            writer.writerow({"species": species, **values})


def find_suspicious_false_negatives(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    suspicious: list[dict[str, Any]] = []
    for record in records:
        predicted = record.get("predicted") or {}
        phase2 = (((record.get("debug") or {}).get("phase2") or {}).get("predictions") or [])
        for item in phase2:
            if not isinstance(item, dict):
                continue
            target = clean_text(item.get("target"))
            field = PHASE2_FIELDS.get(target)
            if not field:
                continue
            predicted_flag = parse_bool(predicted.get(field))
            phase2_value = bool(item.get("predictedValue"))
            confidence = float(item.get("confidence") or 0.0)
            if predicted_flag is False and phase2_value and confidence >= 0.50:
                suspicious.append(
                    {
                        "upload_id": record.get("upload_id"),
                        "source_file": record.get("source_file"),
                        "species": predicted.get("species"),
                        "target": target,
                        "confidence": round(confidence, 4),
                        "margin": item.get("margin"),
                        "passed_threshold": item.get("passedThreshold"),
                    }
                )
    return suspicious


def write_suspicious_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "upload_id",
                "source_file",
                "species",
                "target",
                "confidence",
                "margin",
                "passed_threshold",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)


def copy_image(source: Path, images_dir: Path, item: dict[str, Any] | None) -> str:
    species = safe_name(clean_text((item or {}).get("predicted_species")) or "unknown")
    prefix = safe_name(clean_text((item or {}).get("id")) or source.stem)
    target_name = f"{prefix}_{species}{source.suffix.lower()}"
    target = images_dir / target_name
    shutil.copy2(source, target)
    return f"images/{target_name}"


def summarize(records: list[dict[str, Any]], unmatched: list[str]) -> dict[str, Any]:
    species_counts: dict[str, int] = {}
    for record in records:
        species = clean_text((record.get("auto_label") or {}).get("species")) or "unknown"
        species_counts[species] = species_counts.get(species, 0) + 1
    return {
        "total": len(records),
        "matched_metadata": len(records) - len(unmatched),
        "unmatched_metadata": len(unmatched),
        "verified_truth": sum(1 for record in records if record.get("verified")),
        "high_confidence_auto_labels": sum(
            1 for record in records if not (record.get("auto_label") or {}).get("needs_review")
        ),
        "needs_review": sum(1 for record in records if (record.get("auto_label") or {}).get("needs_review")),
        "top_species": sorted(species_counts.items(), key=lambda item: (-item[1], item[0]))[:25],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Import manual Pokemon GO screenshots into a local training dataset.")
    parser.add_argument("--inbox", default="artifacts/dataset/manual_screenshots/inbox")
    parser.add_argument("--out-dir", default="artifacts/dataset/manual_screenshots/processed")
    parser.add_argument("--metadata", action="append", default=[])
    args = parser.parse_args()

    inbox = Path(args.inbox)
    out_dir = Path(args.out_dir)
    images_dir = out_dir / "images"
    review_dir = out_dir.parent / "review"
    images_dir.mkdir(parents=True, exist_ok=True)
    review_dir.mkdir(parents=True, exist_ok=True)

    metadata_paths = [Path(path) for path in args.metadata]
    records_by_upload = load_metadata(metadata_paths)
    records: list[dict[str, Any]] = []
    unmatched: list[str] = []

    for source in sorted(path for path in inbox.iterdir() if path.is_file()):
        item = records_by_upload.get(source.stem)
        if item is None and "_" in source.stem:
            item = records_by_upload.get(source.stem.split("_", 1)[0])
        if item is None:
            unmatched.append(source.name)
        image_rel = copy_image(source, images_dir, item)
        records.append(build_record(source, image_rel, item))

    manifest_path = out_dir / "manifest.jsonl"
    auto_labels_path = out_dir / "auto_labels.jsonl"
    summary_path = out_dir / "summary.json"
    low_confidence = [record for record in records if (record.get("auto_label") or {}).get("needs_review")]
    write_jsonl(manifest_path, records)
    write_jsonl(auto_labels_path, [{"upload_id": record["upload_id"], **record["auto_label"]} for record in records])
    write_review_csv(review_dir / "low_confidence.csv", low_confidence)
    write_species_summary_csv(review_dir / "by_species_summary.csv", records)
    suspicious = find_suspicious_false_negatives(records)
    write_suspicious_csv(review_dir / "suspicious_false_negatives.csv", suspicious)
    (review_dir / "unmatched_files.txt").write_text("\n".join(unmatched) + ("\n" if unmatched else ""), encoding="utf-8")
    summary = summarize(records, unmatched)
    summary["suspicious_false_negative_targets"] = len(suspicious)
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"manifest": str(manifest_path), "summary": str(summary_path), **summary}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
