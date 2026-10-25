"""Parse and validate bounded RecognitionRecoveryBenchmarkTest status output."""

from __future__ import annotations

import base64
import hashlib
import json
from dataclasses import dataclass
from typing import Any

MAX_REPORT_BYTES = 1024 * 1024
MAX_CHUNKS = MAX_REPORT_BYTES
RUN_ID_KEY = "recognitionRecoveryRunId"
CHUNK_INDEX_KEY = "recognitionRecoveryChunkIndex"
CHUNK_COUNT_KEY = "recognitionRecoveryChunkCount"
REPORT_SHA_KEY = "recognitionRecoveryReportSha256"
CHUNK_DATA_KEY = "recognitionRecoveryChunkBase64"
REPORT_ERROR_KEY = "recognitionRecoveryReportError"
FORBIDDEN_KEYS = {
    "rawtext",
    "rawocrtext",
    "ocrtext",
    "filename",
    "filepath",
    "sourcepath",
    "assetpath",
    "screenshot",
    "bitmap",
    "imagedata",
}


@dataclass
class CollectionError(Exception):
    code: str


def parse_instrumentation_status(output: str) -> list[dict[str, str]]:
    """Return status Bundles emitted by `am instrument -w -r`."""
    blocks: list[dict[str, str]] = []
    current: dict[str, str] = {}
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS:"):
            value = line[len("INSTRUMENTATION_STATUS:"):].strip()
            key, separator, item = value.partition("=")
            if separator:
                current[key.strip()] = item
        elif line.startswith("INSTRUMENTATION_STATUS_CODE:"):
            if current:
                blocks.append(current)
                current = {}
    if current:
        blocks.append(current)
    return blocks


def collect_report(
    output: str,
    expected_run_id: str,
    expected_source_revision: str,
) -> tuple[dict[str, Any], str, int]:
    """Reassemble one correlated report and reject partial or unsafe payloads."""
    blocks = [block for block in parse_instrumentation_status(output)
              if block.get(RUN_ID_KEY) == expected_run_id]
    reported_error = next((block.get(REPORT_ERROR_KEY) for block in blocks
                           if block.get(REPORT_ERROR_KEY)), None)
    if reported_error:
        raise CollectionError("report_too_large" if reported_error == "report_too_large"
                              else "instrumentation_report_error")
    chunks = [block for block in blocks if CHUNK_DATA_KEY in block]
    if not chunks:
        raise CollectionError("instrumentation_report_missing")

    try:
        count_values = {int(block[CHUNK_COUNT_KEY]) for block in chunks}
        hash_values = {block[REPORT_SHA_KEY] for block in chunks}
    except (KeyError, ValueError):
        raise CollectionError("invalid_chunk_metadata") from None
    if len(count_values) != 1 or len(hash_values) != 1:
        raise CollectionError("inconsistent_chunk_metadata")
    chunk_count = count_values.pop()
    expected_hash = hash_values.pop().lower()
    if not 1 <= chunk_count <= MAX_CHUNKS or len(chunks) != chunk_count:
        raise CollectionError("incomplete_chunks")

    parts: dict[int, bytes] = {}
    try:
        for block in chunks:
            index = int(block[CHUNK_INDEX_KEY])
            if not 0 <= index < chunk_count or index in parts:
                raise CollectionError("invalid_chunk_index")
            parts[index] = base64.b64decode(block[CHUNK_DATA_KEY], validate=True)
    except (KeyError, ValueError):
        raise CollectionError("invalid_chunk_payload") from None

    payload = b"".join(parts[index] for index in range(chunk_count))
    if len(payload) > MAX_REPORT_BYTES:
        raise CollectionError("report_too_large")
    actual_hash = hashlib.sha256(payload).hexdigest()
    if actual_hash != expected_hash:
        raise CollectionError("report_checksum_mismatch")
    try:
        report = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise CollectionError("invalid_report_json") from None
    validate_report(report, expected_run_id, expected_source_revision)
    return report, actual_hash, chunk_count


def validate_report(report: Any, expected_run_id: str, expected_source_revision: str) -> None:
    if not isinstance(report, dict) or report.get("schemaVersion") != 2:
        raise CollectionError("unsupported_report_schema")
    if report.get("runId") != expected_run_id:
        raise CollectionError("report_run_id_mismatch")
    if report.get("sourceRevision") != expected_source_revision:
        raise CollectionError("report_source_revision_mismatch")
    expected = report.get("expectedOutcomes")
    outcomes = report.get("outcomes")
    if not isinstance(expected, int) or expected < 0 or not isinstance(outcomes, list):
        raise CollectionError("invalid_report_shape")
    if len(outcomes) > expected or (report.get("completed") is True and len(outcomes) != expected):
        raise CollectionError("outcome_count_mismatch")
    if not isinstance(report.get("completed"), bool):
        raise CollectionError("invalid_completion_state")
    _reject_sensitive_keys(report)


def _reject_sensitive_keys(value: Any) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if str(key).replace("_", "").lower() in FORBIDDEN_KEYS:
                raise CollectionError("report_contains_sensitive_field")
            _reject_sensitive_keys(child)
    elif isinstance(value, list):
        for child in value:
            _reject_sensitive_keys(child)


def make_partial_artifact(
    run_id: str,
    source_revision: str,
    failure_code: str,
    instrumentation_exit_code: int | None,
) -> dict[str, Any]:
    return {
        "collectorSchemaVersion": 1,
        "runId": run_id,
        "sourceRevision": source_revision,
        "collectionStatus": "partial",
        "failureCode": failure_code,
        "instrumentationExitCode": instrumentation_exit_code,
    }
