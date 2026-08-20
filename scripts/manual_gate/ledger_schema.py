"""Strict UNKNOWN-only Manual Gate A ledger construction and validation.

This module intentionally supports only the deferred-review closeout state:
all candidate records remain UNKNOWN, non-authoritative, and unapproved. It does
not model completed human review and cannot promote scanner suggestions.
"""

from __future__ import annotations

import json
import re
from typing import Any

SCHEMA_VERSION = 1
DATASET_ID = "candidate_2026_s25"
EXPECTED_SOURCE_COUNT = 730
EXPECTED_SOURCE_BYTES = 473_826_206
EXPECTED_SOURCE_DIGEST = "e3e3dadc4ffb64bf0db32f63f0ec0d08321eebdb82952bde068e6d6eaccc0dd1"
EXPECTED_NEAR_DUPLICATE_GROUPS = 61
EXPECTED_NEAR_DUPLICATE_FILES = 176
EXPECTED_REDUNDANT_EXCLUDED = 5
EXPECTED_STRUCTURALLY_ELIGIBLE = 724
DEVELOPMENT_COUNT = 100
HOLDOUT_COUNT = 20
RECORD_COUNT = DEVELOPMENT_COUNT + HOLDOUT_COUNT

DEVELOPMENT_LANE = "development_candidate"
HOLDOUT_LANE = "prospective_holdout_candidate"
VALID_LANES = {DEVELOPMENT_LANE, HOLDOUT_LANE}

MANIFEST_ROOT_KEYS = {
    "schemaVersion", "datasetId", "candidateOnly", "authoritative", "containsScreenshotBytes",
    "truthLabelsPresent", "prospectiveHoldoutQuarantined", "sourceFileCount", "sourceAggregateBytes",
    "sourceDigestSha256", "nearDuplicateGroupCount", "nearDuplicateGroupedFileCount",
    "redundantExcludedCount", "structurallyEligibleCount", "nearDuplicateMethod", "nearDuplicateClusters",
    "records",
}
MANIFEST_RECORD_KEYS = {
    "id", "lane", "sha256", "nearDuplicateClusterId", "duplicateDecisionReason", "width", "height",
    "format", "colorMode", "bitDepth", "byteSize", "privacyClassification", "manualTruthStatus",
    "provenanceStatus", "publicationStatus", "overlayPresent", "likelyDetailsScreen", "likelyCpPresent",
    "likelyHpPresent", "likelyNamePresent", "likelyCandyFamilyPresent",
}
LEDGER_ROOT_KEYS = {
    "schemaVersion", "sourceManifestDatasetId", "sourceManifestSchemaVersion", "gateStatus",
    "completionStatus", "reviewType", "totalRecords", "developmentRecords", "holdoutRecords",
    "truthCompletedCount", "privacyApprovedCount", "provenanceVerifiedCount", "suggestionsPromotedCount",
    "holdoutTruthExposureCount", "entries",
}
LEDGER_ENTRY_KEYS = {
    "candidateId", "lane", "sha256", "reviewStatus", "privacyDisposition", "provenanceDisposition",
    "truthFields", "reviewerNotes", "suggestionsPromoted",
}

SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
DEV_ID_PATTERN = re.compile(r"^s25_2026_dev_[0-9]{3}$")
HOLDOUT_ID_PATTERN = re.compile(r"^s25_2026_holdout_[0-9]{3}$")

_PRIVACY_PATTERNS = [
    re.compile(r"[A-Z]:[\\/]", re.IGNORECASE),
    re.compile(r"/(?:Users|home)/", re.IGNORECASE),
    re.compile(r"\badb\b", re.IGNORECASE),
    re.compile(r"\bemulator-\d+\b", re.IGNORECASE),
    re.compile(r"\b(?:authorization|bearer|auth[ _-]?token|api[ _-]?key|password|secret|telemetry)\b", re.IGNORECASE),
    re.compile(r"\b[^\s/\\]+\.(?:png|jpe?g|webp)\b", re.IGNORECASE),
]


def canonical_json_bytes(data: Any) -> bytes:
    """Serialize canonical UTF-8 JSON with sorted keys and one trailing LF."""
    return (json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def validate_canonical_bytes(data: bytes) -> None:
    """Reject non-canonical JSON bytes, including BOM/CRLF/key-order drift."""
    if data.startswith(b"\xef\xbb\xbf"):
        raise ValueError("BOM detected in canonical JSON output")
    if b"\r" in data:
        raise ValueError("CR detected in canonical JSON output")
    if not data.endswith(b"\n") or data.endswith(b"\n\n"):
        raise ValueError("Canonical JSON must end with exactly one LF")
    try:
        parsed = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError("Invalid UTF-8 JSON") from exc
    if data != canonical_json_bytes(parsed):
        raise ValueError("JSON bytes are not canonical")


def _check_privacy_safe(value: str, context: str) -> None:
    for pattern in _PRIVACY_PATTERNS:
        if pattern.search(value):
            raise ValueError(f"Privacy-unsafe content in {context}")


def _walk_strings(value: Any):
    if isinstance(value, dict):
        for nested in value.values():
            yield from _walk_strings(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from _walk_strings(nested)
    elif isinstance(value, str):
        yield value


def _expected_ids() -> list[str]:
    return [f"s25_2026_dev_{index:03}" for index in range(1, DEVELOPMENT_COUNT + 1)] + [
        f"s25_2026_holdout_{index:03}" for index in range(1, HOLDOUT_COUNT + 1)
    ]


def validate_manifest_for_review(manifest: dict) -> None:
    """Validate the exact committed candidate dataset accepted by this tool."""
    if not isinstance(manifest, dict) or set(manifest) != MANIFEST_ROOT_KEYS:
        raise ValueError("Candidate manifest root schema mismatch")
    expected_scalars = {
        "schemaVersion": 1,
        "datasetId": DATASET_ID,
        "candidateOnly": True,
        "authoritative": False,
        "containsScreenshotBytes": False,
        "truthLabelsPresent": False,
        "prospectiveHoldoutQuarantined": True,
        "sourceFileCount": EXPECTED_SOURCE_COUNT,
        "sourceAggregateBytes": EXPECTED_SOURCE_BYTES,
        "sourceDigestSha256": EXPECTED_SOURCE_DIGEST,
        "nearDuplicateGroupCount": EXPECTED_NEAR_DUPLICATE_GROUPS,
        "nearDuplicateGroupedFileCount": EXPECTED_NEAR_DUPLICATE_FILES,
        "redundantExcludedCount": EXPECTED_REDUNDANT_EXCLUDED,
        "structurallyEligibleCount": EXPECTED_STRUCTURALLY_ELIGIBLE,
    }
    for key, expected in expected_scalars.items():
        if manifest.get(key) != expected or type(manifest.get(key)) is not type(expected):
            raise ValueError(f"Unexpected candidate manifest value for {key}")
    if not isinstance(manifest["nearDuplicateMethod"], dict) or not isinstance(manifest["nearDuplicateClusters"], list):
        raise ValueError("Candidate near-duplicate metadata has invalid type")

    records = manifest["records"]
    if not isinstance(records, list) or len(records) != RECORD_COUNT:
        raise ValueError("Candidate manifest must contain exactly 120 records")
    expected_ids = _expected_ids()
    if [record.get("id") for record in records if isinstance(record, dict)] != expected_ids:
        raise ValueError("Candidate IDs or ordering mismatch")

    hashes: set[str] = set()
    dev_count = 0
    holdout_count = 0
    for index, record in enumerate(records):
        if not isinstance(record, dict) or set(record) != MANIFEST_RECORD_KEYS:
            raise ValueError(f"Candidate record schema mismatch at index {index}")
        record_id = record["id"]
        lane = record["lane"]
        expected_lane = DEVELOPMENT_LANE if index < DEVELOPMENT_COUNT else HOLDOUT_LANE
        pattern = DEV_ID_PATTERN if expected_lane == DEVELOPMENT_LANE else HOLDOUT_ID_PATTERN
        if lane != expected_lane or not pattern.fullmatch(record_id):
            raise ValueError(f"Candidate lane/ID mismatch for {record_id}")
        dev_count += lane == DEVELOPMENT_LANE
        holdout_count += lane == HOLDOUT_LANE

        sha = record["sha256"]
        if not isinstance(sha, str) or not SHA256_PATTERN.fullmatch(sha) or sha in hashes:
            raise ValueError(f"Invalid or duplicate SHA-256 for {record_id}")
        hashes.add(sha)
        if record["width"] != 1080 or record["height"] != 2340 or record["format"] != "PNG":
            raise ValueError(f"Unexpected image metadata for {record_id}")
        if record["colorMode"] != "RGBA" or record["bitDepth"] != 8:
            raise ValueError(f"Unexpected pixel metadata for {record_id}")
        if type(record["byteSize"]) is not int or record["byteSize"] <= 0:
            raise ValueError(f"Invalid byteSize for {record_id}")
        if record["privacyClassification"] != "NEEDS_HUMAN_PRIVACY_REVIEW":
            raise ValueError(f"Unexpected privacy state for {record_id}")
        if record["manualTruthStatus"] != "unreviewed":
            raise ValueError(f"Unexpected truth state for {record_id}")
        if record["provenanceStatus"] != "user_supplied_local_corpus":
            raise ValueError(f"Unexpected provenance state for {record_id}")
        if record["publicationStatus"] != "not_approved":
            raise ValueError(f"Unexpected publication state for {record_id}")
        for flag in (
            "overlayPresent", "likelyDetailsScreen", "likelyCpPresent", "likelyHpPresent",
            "likelyNamePresent", "likelyCandyFamilyPresent",
        ):
            if type(record[flag]) is not bool:
                raise ValueError(f"Invalid boolean {flag} for {record_id}")
    if dev_count != DEVELOPMENT_COUNT or holdout_count != HOLDOUT_COUNT or len(hashes) != RECORD_COUNT:
        raise ValueError("Candidate lane or hash counts mismatch")

    for value in _walk_strings(manifest):
        _check_privacy_safe(value, "candidate manifest")


def build_unknown_review_entry(candidate_record: dict) -> dict:
    return {
        "candidateId": candidate_record["id"],
        "lane": candidate_record["lane"],
        "sha256": candidate_record["sha256"],
        "reviewStatus": "UNKNOWN",
        "privacyDisposition": "NOT_REVIEWED",
        "provenanceDisposition": "NOT_VERIFIED",
        "truthFields": {},
        "reviewerNotes": "",
        "suggestionsPromoted": False,
    }


def build_unknown_ledger(manifest: dict) -> dict:
    """Build the only state supported by this partial-closeout tool."""
    validate_manifest_for_review(manifest)
    entries = [build_unknown_review_entry(record) for record in manifest["records"]]
    ledger = {
        "schemaVersion": SCHEMA_VERSION,
        "sourceManifestDatasetId": DATASET_ID,
        "sourceManifestSchemaVersion": 1,
        "gateStatus": "OPEN",
        "completionStatus": "INCOMPLETE",
        "reviewType": "UNKNOWN_ONLY_EXPORT",
        "totalRecords": RECORD_COUNT,
        "developmentRecords": DEVELOPMENT_COUNT,
        "holdoutRecords": HOLDOUT_COUNT,
        "truthCompletedCount": 0,
        "privacyApprovedCount": 0,
        "provenanceVerifiedCount": 0,
        "suggestionsPromotedCount": 0,
        "holdoutTruthExposureCount": 0,
        "entries": entries,
    }
    validate_ledger(ledger, manifest)
    return ledger


def validate_ledger(ledger: dict, manifest: dict) -> None:
    """Fail closed unless the ledger is the exact deterministic UNKNOWN-only overlay."""
    validate_manifest_for_review(manifest)
    if not isinstance(ledger, dict) or set(ledger) != LEDGER_ROOT_KEYS:
        raise ValueError("Ledger root schema mismatch")
    expected_scalars = {
        "schemaVersion": 1,
        "sourceManifestDatasetId": DATASET_ID,
        "sourceManifestSchemaVersion": 1,
        "gateStatus": "OPEN",
        "completionStatus": "INCOMPLETE",
        "reviewType": "UNKNOWN_ONLY_EXPORT",
        "totalRecords": RECORD_COUNT,
        "developmentRecords": DEVELOPMENT_COUNT,
        "holdoutRecords": HOLDOUT_COUNT,
        "truthCompletedCount": 0,
        "privacyApprovedCount": 0,
        "provenanceVerifiedCount": 0,
        "suggestionsPromotedCount": 0,
        "holdoutTruthExposureCount": 0,
    }
    for key, expected in expected_scalars.items():
        if ledger.get(key) != expected or type(ledger.get(key)) is not type(expected):
            raise ValueError(f"Unexpected UNKNOWN-only ledger value for {key}")

    entries = ledger["entries"]
    if not isinstance(entries, list) or len(entries) != RECORD_COUNT:
        raise ValueError("UNKNOWN-only ledger must contain exactly 120 entries")
    for index, (entry, candidate) in enumerate(zip(entries, manifest["records"], strict=True)):
        if not isinstance(entry, dict) or set(entry) != LEDGER_ENTRY_KEYS:
            raise ValueError(f"Ledger entry schema mismatch at index {index}")
        expected = build_unknown_review_entry(candidate)
        if entry != expected:
            raise ValueError(f"Ledger entry does not match candidate UNKNOWN state: {candidate['id']}")
        if not SHA256_PATTERN.fullmatch(entry["sha256"]):
            raise ValueError(f"Invalid ledger SHA-256 for {entry['candidateId']}")
        if entry["lane"] not in VALID_LANES:
            raise ValueError(f"Invalid ledger lane for {entry['candidateId']}")

    for value in _walk_strings(ledger):
        _check_privacy_safe(value, "UNKNOWN-only ledger")
