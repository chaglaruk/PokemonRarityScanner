#!/usr/bin/env python3
"""Compare privacy-safe Android screenshot replay reports by content hash."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any


IMAGE_ID = re.compile(r"H[0-9a-f]{64}\Z")


def _counter(values: list[Any]) -> dict[str, int]:
    return dict(sorted(Counter("UNKNOWN" if value is None else str(value) for value in values).items()))


def _percentile(values: list[float], percentile: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, round((len(ordered) - 1) * percentile)))
    return round(ordered[index], 2)


def _variant(row: dict[str, Any], key: str) -> str:
    value = row.get(key)
    if not isinstance(value, dict):
        return "UNKNOWN"
    parts = [value.get("status")]
    parts.append(value.get("knownForm") if key == "form" else value.get("visualPositive"))
    parts.append(value.get("variantCandidate"))
    return "|".join("UNKNOWN" if part is None else str(part) for part in parts)


def _normalized_json(value: Any) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def validate_report(
    report: dict[str, Any],
    label: str,
    expected_sha: str | None = None,
    expected_count: int | None = None,
) -> dict[str, dict[str, Any]]:
    source_sha = report.get("sourceRevision")
    if expected_sha and source_sha != expected_sha:
        raise ValueError(f"{label}: sourceRevision {source_sha!r} does not match expected {expected_sha}")
    rows = report.get("frameSummaries")
    if not isinstance(rows, list):
        raise ValueError(f"{label}: frameSummaries is missing")
    indexed: dict[str, dict[str, Any]] = {}
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError(f"{label}: invalid frame summary")
        image_id = row.get("frame")
        if not isinstance(image_id, str) or not IMAGE_ID.fullmatch(image_id):
            raise ValueError(f"{label}: frame key is not a content SHA identifier")
        if image_id in indexed:
            raise ValueError(f"{label}: duplicate frame key {image_id}")
        indexed[image_id] = row
    if expected_count is not None and len(indexed) != expected_count:
        raise ValueError(f"{label}: expected {expected_count} frames but found {len(indexed)}")
    return indexed


def summarize_report(report: dict[str, Any], rows: dict[str, dict[str, Any]]) -> dict[str, Any]:
    ordered = list(rows.values())
    latencies = [float(row["totalLatencyMs"]) for row in ordered if row.get("totalLatencyMs") is not None]
    first_five_latencies = [float(row["totalLatencyMs"]) for row in ordered
                            if row.get("frameIndex") is not None and int(row["frameIndex"]) < 5
                            and row.get("totalLatencyMs") is not None]
    after_five_latencies = [float(row["totalLatencyMs"]) for row in ordered
                            if row.get("frameIndex") is not None and int(row["frameIndex"]) >= 5
                            and row.get("totalLatencyMs") is not None]
    pss = [float(row["instrumentationProcessPssKb"]) for row in ordered
           if row.get("instrumentationProcessPssKb") is not None]
    species = [row.get("species") for row in ordered]
    candidates = [row.get("speciesCandidate") for row in ordered]
    signatures = [row.get("signatureSource") for row in ordered]
    signature_confidence = [float(row["signatureConfidence"]) for row in ordered
                            if row.get("signatureConfidence") is not None]
    body_color = [row.get("bodyColor") or {} for row in ordered]
    normal_views = [int((row.get("normalCounterevidence") or {}).get("strongOpposingViewCount") or 0)
                    for row in ordered]
    ocr_field_statuses: dict[str, Counter[str]] = {}
    for row in ordered:
        for field in row.get("ocrFields") or []:
            if isinstance(field, dict) and field.get("field"):
                ocr_field_statuses.setdefault(str(field["field"]), Counter())[
                    str(field.get("status") or "UNKNOWN")
                ] += 1
    forms = [_variant(row, "form") for row in ordered]
    costumes = [_variant(row, "costume") for row in ordered]
    reasons = [row.get("shinyDecisionReason") for row in ordered]
    shiny = [row.get("shinyStatus", "UNKNOWN") for row in ordered]
    return {
        "sourceRevision": report.get("sourceRevision"),
        "targetApplicationId": report.get("targetApplicationId"),
        "frameCount": len(ordered),
        "routeOutcomes": _counter([row.get("outcome") for row in ordered]),
        "recognizedSpeciesCount": sum(value not in (None, "") for value in species),
        "predictedSpeciesCount": sum(value not in (None, "") for value in candidates),
        "distinctRecognizedSpecies": len({value for value in species if value not in (None, "")}),
        "distinctSpeciesCandidates": len({value for value in candidates if value not in (None, "")}),
        "speciesStatus": _counter([row.get("speciesStatus") for row in ordered]),
        "shinyStatus": _counter(shiny),
        "formDecisions": _counter(forms),
        "costumeDecisions": _counter(costumes),
        "signatureSources": _counter(signatures),
        "signatureConfidence": {
            "p50": _percentile(signature_confidence, 0.50),
            "p95": _percentile(signature_confidence, 0.95),
            "availableCount": len(signature_confidence),
        },
        "shinyDecisionReasons": _counter(reasons),
        "ocrFieldStatuses": {
            field: dict(sorted(statuses.items())) for field, statuses in sorted(ocr_field_statuses.items())
        },
        "normalCounterevidenceFrames": sum(
            count > 0 for count in normal_views
        ),
        "normalOpposingViewCount": _counter(normal_views),
        "independentBodyColorSupportFrames": sum(
            color.get("independentSupportAtPr195Threshold") is True for color in body_color
        ),
        "bodyColor": {
            "maskedMatchedCount": sum(color.get("maskedMatched") is True for color in body_color),
            "maskedConfidenceP50": _percentile([
                float(color["maskedConfidence"]) for color in body_color if color.get("maskedConfidence") is not None
            ], 0.50),
            "rawMatchedCount": sum(color.get("rawMatched") is True for color in body_color),
            "rawConfidenceP50": _percentile([
                float(color["rawConfidence"]) for color in body_color if color.get("rawConfidence") is not None
            ], 0.50),
        },
        "latencyMs": {
            "p50": _percentile(latencies, 0.50),
            "p95": _percentile(latencies, 0.95),
            "max": max(latencies) if latencies else None,
            "firstFiveP50": _percentile(first_five_latencies, 0.50),
            "firstFiveP95": _percentile(first_five_latencies, 0.95),
            "afterFirstFiveP50": _percentile(after_five_latencies, 0.50),
            "afterFirstFiveP95": _percentile(after_five_latencies, 0.95),
        },
        "processPssKb": {
            "p50": _percentile(pss, 0.50),
            "p95": _percentile(pss, 0.95),
            "max": max(pss) if pss else None,
        },
        "initializationMs": report.get("initializationMs"),
    }


def compare_pair(left: dict[str, dict[str, Any]], right: dict[str, dict[str, Any]]) -> dict[str, Any]:
    if set(left) != set(right):
        missing_right = sorted(set(left) - set(right))
        missing_left = sorted(set(right) - set(left))
        raise ValueError(
            f"frame sets differ: missing from right={len(missing_right)}, missing from left={len(missing_left)}"
        )
    transitions: Counter[str] = Counter()
    reason_transitions: Counter[str] = Counter()
    changed: list[dict[str, str]] = []
    changed_fields = Counter()
    changed_with_normal_opposition = 0
    for image_id in sorted(left):
        before = left[image_id]
        after = right[image_id]
        fields = {
            "species": (before.get("species"), after.get("species")),
            "speciesCandidate": (before.get("speciesCandidate"), after.get("speciesCandidate")),
            "shiny": (before.get("shinyStatus", "UNKNOWN"), after.get("shinyStatus", "UNKNOWN")),
            "form": (_variant(before, "form"), _variant(after, "form")),
            "costume": (_variant(before, "costume"), _variant(after, "costume")),
            "signatureSource": (before.get("signatureSource"), after.get("signatureSource")),
            "signatureConfidence": (
                round(float(before["signatureConfidence"]), 3) if before.get("signatureConfidence") is not None else None,
                round(float(after["signatureConfidence"]), 3) if after.get("signatureConfidence") is not None else None,
            ),
            "ocrFields": (_normalized_json(before.get("ocrFields") or []),
                          _normalized_json(after.get("ocrFields") or [])),
            "normalCounterevidence": (
                _normalized_json(before.get("normalCounterevidence") or {}),
                _normalized_json(after.get("normalCounterevidence") or {}),
            ),
            "bodyColorSupport": (
                bool((before.get("bodyColor") or {}).get("independentSupportAtPr195Threshold")),
                bool((after.get("bodyColor") or {}).get("independentSupportAtPr195Threshold")),
            ),
        }
        for name, (old, new) in fields.items():
            if old != new:
                changed_fields[name] += 1
        if fields["shiny"][0] != fields["shiny"][1]:
            transitions[f"{fields['shiny'][0]}->{fields['shiny'][1]}"] += 1
            before_reason = before.get("shinyDecisionReason") or "UNKNOWN"
            after_reason = after.get("shinyDecisionReason") or "UNKNOWN"
            reason_transitions[f"{before_reason}->{after_reason}"] += 1
            opposing = (after.get("normalCounterevidence") or {}).get("strongOpposingViewCount", 0)
            changed_with_normal_opposition += int(bool(opposing))
        if any(old != new for old, new in fields.values()):
            changed.append({
                "imageId": image_id,
                "beforeShiny": str(fields["shiny"][0]),
                "afterShiny": str(fields["shiny"][1]),
                "beforeSpecies": str(fields["species"][0] or "UNKNOWN"),
                "afterSpecies": str(fields["species"][1] or "UNKNOWN"),
                "beforeReason": str(before.get("shinyDecisionReason") or "UNKNOWN"),
                "afterReason": str(after.get("shinyDecisionReason") or "UNKNOWN"),
                "beforeSignature": {
                    "source": before.get("signatureSource"),
                    "confidence": before.get("signatureConfidence"),
                    "candidates": before.get("signatureCandidates") or [],
                },
                "afterSignature": {
                    "source": after.get("signatureSource"),
                    "confidence": after.get("signatureConfidence"),
                    "candidates": after.get("signatureCandidates") or [],
                },
                "beforeNormalCounterevidence": before.get("normalCounterevidence") or {},
                "afterNormalCounterevidence": after.get("normalCounterevidence") or {},
                "beforeBodyColor": before.get("bodyColor") or {},
                "afterBodyColor": after.get("bodyColor") or {},
                "beforeOcrFieldStatuses": before.get("ocrFields") or [],
                "afterOcrFieldStatuses": after.get("ocrFields") or [],
                "beforeLatencyMs": before.get("totalLatencyMs"),
                "afterLatencyMs": after.get("totalLatencyMs"),
            })
    return {
        "frameCount": len(left),
        "changedFrames": len(changed),
        "changedFields": dict(sorted(changed_fields.items())),
        "shinyTransitions": dict(sorted(transitions.items())),
        "shinyReasonTransitions": dict(sorted(reason_transitions.items())),
        "shinyChangesWithStrongNormalCounterevidenceAfter": changed_with_normal_opposition,
        "changedFrameSummaries": changed,
    }


def _parse_pairs(values: list[str], kind: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for value in values:
        label, separator, item = value.partition("=")
        if not separator or not label or not item or label in result:
            raise ValueError(f"invalid or duplicate {kind}: {value!r}; expected LABEL=VALUE")
        result[label] = item
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replay", action="append", required=True, metavar="LABEL=JSON",
                        help="ordered replay input; give one per source revision")
    parser.add_argument("--revision", action="append", default=[], metavar="LABEL=SHA",
                        help="expected application source SHA for a replay label")
    parser.add_argument("--expected-count", type=int, default=None,
                        help="reject inputs unless each replay has this many unique frames")
    parser.add_argument("--output", type=Path, help="write JSON summary to this path instead of stdout")
    args = parser.parse_args(argv)
    try:
        replay_paths = _parse_pairs(args.replay, "replay")
        expected_revisions = _parse_pairs(args.revision, "revision")
        if len(replay_paths) < 2:
            raise ValueError("at least two --replay inputs are required")
        if set(expected_revisions) - set(replay_paths):
            raise ValueError("--revision label has no matching --replay label")
        reports: dict[str, dict[str, Any]] = {}
        indexed: dict[str, dict[str, dict[str, Any]]] = {}
        for label, filename in replay_paths.items():
            report = json.loads(Path(filename).read_text(encoding="utf-8-sig"))
            if not isinstance(report, dict):
                raise ValueError(f"{label}: replay report must be a JSON object")
            reports[label] = report
            indexed[label] = validate_report(
                report, label, expected_revisions.get(label), args.expected_count
            )
        labels = list(replay_paths)
        comparison_pairs = [(labels[i], labels[i + 1]) for i in range(len(labels) - 1)]
        if len(labels) > 2:
            comparison_pairs.append((labels[0], labels[-1]))
        output = {
            "schemaVersion": 1,
            "privacy": {
                "imageBytesIncluded": False,
                "sourcePathsIncluded": False,
                "ocrTextIncluded": False,
                "frameKeys": "content SHA-256 identifiers only",
            },
            "revisions": {
                label: summarize_report(reports[label], indexed[label]) for label in labels
            },
            "comparisons": {
                f"{left}->{right}": compare_pair(indexed[left], indexed[right])
                for left, right in comparison_pairs
            },
        }
        serialized = json.dumps(output, indent=2, ensure_ascii=False) + "\n"
        if args.output:
            args.output.write_text(serialized, encoding="utf-8")
        else:
            sys.stdout.write(serialized)
        return 0
    except (OSError, json.JSONDecodeError, ValueError) as error:
        parser.error(str(error))
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
