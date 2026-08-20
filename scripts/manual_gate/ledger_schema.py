"""Manual Gate A review ledger schema, validation, and UNKNOWN-only export.

Trust boundaries:
- No species, CP, HP, shiny, shadow, purified, lucky, costume/form truth
  may be inferred or promoted.
- No privacy or provenance approval may be granted.
- Holdout truth must never be exposed.
- Cross-lane leakage is rejected.
- UNKNOWN-only export does not count as Manual Gate A progress.

Output contract:
- Canonical JSON with sorted keys, 2-space indent, UTF-8, LF line endings.
- Single LF at EOF, no BOM, no CRLF.
- Byte-identical on repeated deterministic runs.
"""

from __future__ import annotations

import json
import re
from typing import Any


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

SCHEMA_VERSION = 1
VALID_LANES = {"development_candidate", "prospective_holdout_candidate"}
VALID_REVIEW_STATUSES = {"UNKNOWN", "VERIFIED_FROM_SOURCE"}
VALID_PRIVACY_DISPOSITIONS = {"NOT_REVIEWED", "APPROVED", "REJECTED"}
VALID_PROVENANCE_DISPOSITIONS = {"NOT_VERIFIED", "VERIFIED", "REJECTED"}

# Fields that MUST NOT appear on any committed record.
FORBIDDEN_RECORD_FIELDS = frozenset({
    "canonicalSpecies",
    "rawOcrText",
    "sourcePath",
    "fileName",
    "accountId",
    "deviceSerial",
    "adbEndpoint",
    "networkIdentifier",
    "authToken",
    "telemetryPayload",
    "screenshotBytes",
    "thumbnailBytes",
    "imageData",
})

# Truth fields that MUST NOT appear on holdout records.
HOLDOUT_FORBIDDEN_TRUTH_FIELDS = frozenset({
    "verifiedSpecies",
    "verifiedCp",
    "verifiedHp",
    "verifiedShiny",
    "verifiedShadow",
    "verifiedPurified",
    "verifiedLucky",
    "verifiedCostume",
    "verifiedForm",
    "verifiedDate",
})

# Patterns that indicate privacy-unsafe content.
_PRIVACY_PATTERNS = [
    re.compile(r"[A-Z]:\\", re.IGNORECASE),                  # Windows path
    re.compile(r"/home/", re.IGNORECASE),                     # Unix home
    re.compile(r"adb\s+connect", re.IGNORECASE),              # ADB command
    re.compile(r"Authorization:\s*Bearer", re.IGNORECASE),    # Auth token
    re.compile(r"telemetry\s+payload", re.IGNORECASE),        # Telemetry
    re.compile(r"emulator-\d+", re.IGNORECASE),               # Emulator serial
    re.compile(r"[0-9a-f]{8,}:[0-9a-f]{4}", re.IGNORECASE),  # Device serial
    re.compile(r"\.png$", re.IGNORECASE),                     # Source filename
    re.compile(r"\.jpg$", re.IGNORECASE),                     # Source filename
    re.compile(r"\.jpeg$", re.IGNORECASE),                    # Source filename
]


# ---------------------------------------------------------------------------
# Canonical JSON
# ---------------------------------------------------------------------------

def canonical_json_bytes(data: Any) -> bytes:
    """Serialize data to canonical JSON bytes: sorted keys, 2-space indent,
    UTF-8, trailing LF, no BOM, no CRLF."""
    text = json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True)
    return (text + "\n").encode("utf-8")


def validate_canonical_bytes(data: bytes) -> None:
    """Validate that bytes conform to canonical JSON format."""
    if data[:3] == b"\xef\xbb\xbf":
        raise ValueError("BOM detected in canonical JSON output")
    if b"\r" in data:
        raise ValueError("CRLF detected in canonical JSON output")
    if not data.endswith(b"\n"):
        raise ValueError("Missing trailing LF in canonical JSON output")
    if data.endswith(b"\n\n"):
        raise ValueError("Double trailing LF in canonical JSON output")


# ---------------------------------------------------------------------------
# Privacy validation
# ---------------------------------------------------------------------------

def _check_privacy_safe(value: str, field_context: str) -> None:
    """Raise ValueError if a string value contains privacy-unsafe patterns."""
    for pattern in _PRIVACY_PATTERNS:
        if pattern.search(value):
            raise ValueError(
                f"Privacy-unsafe content in {field_context}: "
                f"matched pattern {pattern.pattern!r}"
            )


def validate_no_forbidden_fields(record: dict, record_id: str) -> None:
    """Raise ValueError if a record contains any forbidden field."""
    for field in FORBIDDEN_RECORD_FIELDS:
        if field in record:
            raise ValueError(
                f"Forbidden field '{field}' found in record '{record_id}'"
            )


def validate_no_holdout_truth(record: dict, record_id: str) -> None:
    """Raise ValueError if a holdout record contains any truth field."""
    if record.get("lane") != "prospective_holdout_candidate":
        return
    for field in HOLDOUT_FORBIDDEN_TRUTH_FIELDS:
        if field in record:
            raise ValueError(
                f"Holdout truth field '{field}' found in holdout "
                f"record '{record_id}'"
            )


def validate_privacy_all_fields(record: dict, record_id: str) -> None:
    """Validate all string fields in a record for privacy safety."""
    for key, value in record.items():
        if isinstance(value, str):
            _check_privacy_safe(value, f"record '{record_id}' field '{key}'")


# ---------------------------------------------------------------------------
# Ledger record construction
# ---------------------------------------------------------------------------

def build_unknown_review_entry(candidate_record: dict) -> dict:
    """Build a single UNKNOWN review ledger entry from a candidate record.

    No truth, no approval, no suggestion promotion. Pure UNKNOWN status.
    """
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


# ---------------------------------------------------------------------------
# Ledger construction
# ---------------------------------------------------------------------------

def build_unknown_ledger(manifest: dict) -> dict:
    """Build a complete UNKNOWN-only review ledger from a candidate manifest.

    Every record gets UNKNOWN / NOT_REVIEWED / NOT_VERIFIED status.
    No truth is inferred, no approval is granted.
    """
    validate_manifest_for_review(manifest)

    entries = []
    for record in manifest["records"]:
        entry = build_unknown_review_entry(record)
        entries.append(entry)

    dev_count = sum(
        1 for e in entries if e["lane"] == "development_candidate"
    )
    holdout_count = sum(
        1 for e in entries if e["lane"] == "prospective_holdout_candidate"
    )

    ledger = {
        "schemaVersion": SCHEMA_VERSION,
        "sourceManifestDatasetId": manifest["datasetId"],
        "sourceManifestSchemaVersion": manifest["schemaVersion"],
        "gateStatus": "OPEN",
        "completionStatus": "INCOMPLETE",
        "reviewType": "UNKNOWN_ONLY_EXPORT",
        "totalRecords": len(entries),
        "developmentRecords": dev_count,
        "holdoutRecords": holdout_count,
        "truthCompletedCount": 0,
        "privacyApprovedCount": 0,
        "provenanceVerifiedCount": 0,
        "suggestionsPromotedCount": 0,
        "holdoutTruthExposureCount": 0,
        "entries": entries,
    }

    return ledger


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------

def validate_manifest_for_review(manifest: dict) -> None:
    """Validate a candidate manifest is suitable for review processing."""
    if manifest.get("schemaVersion") != 1:
        raise ValueError(
            f"Unsupported manifest schema version: "
            f"{manifest.get('schemaVersion')}"
        )
    if not manifest.get("candidateOnly"):
        raise ValueError("Manifest must be candidateOnly for review")
    if manifest.get("authoritative"):
        raise ValueError("Manifest must not be authoritative for review")
    if manifest.get("truthLabelsPresent"):
        raise ValueError("Manifest must not have truth labels for review")
    if not manifest.get("prospectiveHoldoutQuarantined"):
        raise ValueError(
            "Manifest must have prospective holdouts quarantined"
        )

    records = manifest.get("records", [])
    seen_ids = set()
    seen_sha256 = set()
    lane_ids: dict[str, set] = {}

    for record in records:
        record_id = record["id"]

        # Unique ID
        if record_id in seen_ids:
            raise ValueError(f"Duplicate record ID: {record_id}")
        seen_ids.add(record_id)

        # Unique SHA-256
        sha = record["sha256"]
        if sha in seen_sha256:
            raise ValueError(f"Duplicate SHA-256 in record {record_id}")
        seen_sha256.add(sha)

        # Valid lane
        lane = record["lane"]
        if lane not in VALID_LANES:
            raise ValueError(f"Invalid lane '{lane}' in record {record_id}")
        lane_ids.setdefault(lane, set()).add(record_id)

        # Privacy checks
        validate_no_forbidden_fields(record, record_id)
        validate_no_holdout_truth(record, record_id)
        validate_privacy_all_fields(record, record_id)


def validate_ledger(ledger: dict) -> None:
    """Validate a review ledger for structural and trust boundary integrity."""
    if ledger.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError(
            f"Unsupported ledger schema version: "
            f"{ledger.get('schemaVersion')}"
        )

    entries = ledger.get("entries", [])
    seen_ids = set()

    for entry in entries:
        cid = entry["candidateId"]

        # Unique candidate ID
        if cid in seen_ids:
            raise ValueError(f"Duplicate candidateId: {cid}")
        seen_ids.add(cid)

        # Valid review status
        status = entry["reviewStatus"]
        if status not in VALID_REVIEW_STATUSES:
            raise ValueError(
                f"Invalid reviewStatus '{status}' in entry {cid}"
            )

        # Privacy disposition
        priv = entry["privacyDisposition"]
        if priv not in VALID_PRIVACY_DISPOSITIONS:
            raise ValueError(
                f"Invalid privacyDisposition '{priv}' in entry {cid}"
            )

        # Provenance disposition
        prov = entry["provenanceDisposition"]
        if prov not in VALID_PROVENANCE_DISPOSITIONS:
            raise ValueError(
                f"Invalid provenanceDisposition '{prov}' in entry {cid}"
            )

        # Holdout truth exposure
        if entry["lane"] == "prospective_holdout_candidate":
            if entry["truthFields"]:
                raise ValueError(
                    f"Holdout truth exposure in entry {cid}"
                )

        # No suggestions promoted in UNKNOWN entries
        if status == "UNKNOWN" and entry.get("suggestionsPromoted"):
            raise ValueError(
                f"Suggestions promoted in UNKNOWN entry {cid}"
            )

    # Cross-lane leakage check
    dev_ids = {e["candidateId"] for e in entries
               if e["lane"] == "development_candidate"}
    holdout_ids = {e["candidateId"] for e in entries
                   if e["lane"] == "prospective_holdout_candidate"}
    if dev_ids & holdout_ids:
        raise ValueError("Cross-lane leakage detected in ledger entries")

    # Aggregate counts
    truth_count = sum(
        1 for e in entries
        if e["reviewStatus"] == "VERIFIED_FROM_SOURCE"
    )
    privacy_count = sum(
        1 for e in entries if e["privacyDisposition"] == "APPROVED"
    )
    prov_count = sum(
        1 for e in entries if e["provenanceDisposition"] == "VERIFIED"
    )
    promo_count = sum(
        1 for e in entries if e.get("suggestionsPromoted")
    )
    holdout_truth = sum(
        1 for e in entries
        if e["lane"] == "prospective_holdout_candidate"
        and e["truthFields"]
    )

    if ledger["truthCompletedCount"] != truth_count:
        raise ValueError("truthCompletedCount mismatch")
    if ledger["privacyApprovedCount"] != privacy_count:
        raise ValueError("privacyApprovedCount mismatch")
    if ledger["provenanceVerifiedCount"] != prov_count:
        raise ValueError("provenanceVerifiedCount mismatch")
    if ledger["suggestionsPromotedCount"] != promo_count:
        raise ValueError("suggestionsPromotedCount mismatch")
    if ledger["holdoutTruthExposureCount"] != holdout_truth:
        raise ValueError("holdoutTruthExposureCount mismatch")
