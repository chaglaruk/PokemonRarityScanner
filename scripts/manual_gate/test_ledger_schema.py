"""Tests for Manual Gate A ledger schema and UNKNOWN-only export.

Validates all trust boundaries, privacy protections, determinism,
and canonical output format.
"""

import copy
import json
import os
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from manual_gate.ledger_schema import (
    FORBIDDEN_RECORD_FIELDS,
    HOLDOUT_FORBIDDEN_TRUTH_FIELDS,
    build_unknown_ledger,
    build_unknown_review_entry,
    canonical_json_bytes,
    validate_canonical_bytes,
    validate_ledger,
    validate_manifest_for_review,
    validate_no_forbidden_fields,
    validate_no_holdout_truth,
    validate_privacy_all_fields,
)
from manual_gate.export_unknown_ledger import main as export_main


DEVELOPMENT_COUNT = 100
HOLDOUT_COUNT = 20
RECORD_COUNT = DEVELOPMENT_COUNT + HOLDOUT_COUNT

# Path to the committed candidate manifest (relative to scripts/)
MANIFEST_PATH = Path(__file__).resolve().parent.parent.parent / (
    "app/src/test/resources/scan_fixtures/candidate_2026_s25_manifest.json"
)


def _valid_manifest() -> dict:
    """Build a minimal valid manifest for testing."""
    records = []
    for idx in range(1, RECORD_COUNT + 1):
        lane = (
            "development_candidate" if idx <= DEVELOPMENT_COUNT
            else "prospective_holdout_candidate"
        )
        prefix = "dev" if idx <= DEVELOPMENT_COUNT else "holdout"
        num = idx if idx <= DEVELOPMENT_COUNT else idx - DEVELOPMENT_COUNT
        records.append({
            "id": f"s25_2026_{prefix}_{num:03}",
            "lane": lane,
            "sha256": f"{idx:064x}",
            "width": 1080,
            "height": 2340,
            "format": "PNG",
            "colorMode": "RGBA",
            "bitDepth": 8,
            "byteSize": 500000 + idx,
            "privacyClassification": "NEEDS_HUMAN_PRIVACY_REVIEW",
            "manualTruthStatus": "unreviewed",
            "provenanceStatus": "user_supplied_local_corpus",
            "publicationStatus": "not_approved",
            "overlayPresent": True,
            "likelyDetailsScreen": True,
            "likelyCpPresent": True,
            "likelyHpPresent": True,
            "likelyNamePresent": True,
            "likelyCandyFamilyPresent": True,
            "nearDuplicateClusterId": None,
            "duplicateDecisionReason": None,
        })
    return {
        "schemaVersion": 1,
        "datasetId": "candidate_2026_s25",
        "candidateOnly": True,
        "authoritative": False,
        "containsScreenshotBytes": False,
        "truthLabelsPresent": False,
        "prospectiveHoldoutQuarantined": True,
        "sourceFileCount": 730,
        "sourceAggregateBytes": 473_826_206,
        "sourceDigestSha256": "f" * 64,
        "nearDuplicateGroupCount": 61,
        "nearDuplicateGroupedFileCount": 176,
        "redundantExcludedCount": 5,
        "structurallyEligibleCount": 724,
        "nearDuplicateMethod": {},
        "nearDuplicateClusters": [],
        "records": records,
    }


class TestUnknownOnlyExportDeterminism(unittest.TestCase):
    """UNKNOWN-only export must be deterministic and byte-identical."""

    def test_deterministic_output_byte_identity(self):
        """Run UNKNOWN-only export twice and prove byte identity."""
        manifest = _valid_manifest()
        ledger_1 = build_unknown_ledger(manifest)
        ledger_2 = build_unknown_ledger(manifest)

        bytes_1 = canonical_json_bytes(ledger_1)
        bytes_2 = canonical_json_bytes(ledger_2)

        self.assertEqual(bytes_1, bytes_2)
        self.assertEqual(ledger_1, ledger_2)

    def test_canonical_json_format(self):
        """Output must be canonical JSON: sorted keys, LF, no BOM, no CRLF."""
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        output = canonical_json_bytes(ledger)

        # No BOM
        self.assertFalse(output.startswith(b"\xef\xbb\xbf"))
        # No CRLF
        self.assertNotIn(b"\r", output)
        # Trailing LF
        self.assertTrue(output.endswith(b"\n"))
        # Not double LF
        self.assertFalse(output.endswith(b"\n\n"))
        # Sorted keys match
        expected = json.dumps(
            ledger, ensure_ascii=False, indent=2, sort_keys=True
        ).encode("utf-8") + b"\n"
        self.assertEqual(output, expected)

        # Validate passes
        validate_canonical_bytes(output)

    def test_cli_export_determinism(self):
        """CLI export produces byte-identical output on repeated runs."""
        manifest = _valid_manifest()
        with TemporaryDirectory() as tmpdir:
            manifest_path = Path(tmpdir) / "manifest.json"
            manifest_path.write_bytes(canonical_json_bytes(manifest))

            out_1 = Path(tmpdir) / "ledger_1.json"
            out_2 = Path(tmpdir) / "ledger_2.json"

            rc1 = export_main([
                "--manifest", str(manifest_path),
                "--output", str(out_1),
            ])
            rc2 = export_main([
                "--manifest", str(manifest_path),
                "--output", str(out_2),
            ])

            self.assertEqual(rc1, 0)
            self.assertEqual(rc2, 0)
            self.assertEqual(out_1.read_bytes(), out_2.read_bytes())


class TestNoApprovalFromUnknownExport(unittest.TestCase):
    """UNKNOWN-only export must not produce any approval or truth."""

    def test_zero_truth_completed(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        self.assertEqual(ledger["truthCompletedCount"], 0)

    def test_zero_privacy_approved(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        self.assertEqual(ledger["privacyApprovedCount"], 0)

    def test_zero_provenance_verified(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        self.assertEqual(ledger["provenanceVerifiedCount"], 0)

    def test_zero_suggestions_promoted(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        self.assertEqual(ledger["suggestionsPromotedCount"], 0)

    def test_gate_remains_open(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        self.assertEqual(ledger["gateStatus"], "OPEN")
        self.assertEqual(ledger["completionStatus"], "INCOMPLETE")

    def test_all_entries_unknown(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        for entry in ledger["entries"]:
            self.assertEqual(entry["reviewStatus"], "UNKNOWN")
            self.assertEqual(entry["privacyDisposition"], "NOT_REVIEWED")
            self.assertEqual(entry["provenanceDisposition"], "NOT_VERIFIED")
            self.assertEqual(entry["truthFields"], {})
            self.assertFalse(entry["suggestionsPromoted"])

    def test_suggestions_cannot_become_truth(self):
        """Even if a record has scanner suggestions, they cannot become truth
        through UNKNOWN-only export."""
        manifest = _valid_manifest()
        # Simulate scanner suggestions existing on the manifest
        manifest["records"][0]["scannerSuggestedSpecies"] = "Pikachu"
        manifest["records"][0]["scannerSuggestedCp"] = 1500
        ledger = build_unknown_ledger(manifest)
        entry = ledger["entries"][0]
        self.assertEqual(entry["reviewStatus"], "UNKNOWN")
        self.assertEqual(entry["truthFields"], {})
        self.assertFalse(entry["suggestionsPromoted"])
        self.assertNotIn("verifiedSpecies", entry)


class TestHoldoutProtection(unittest.TestCase):
    """Holdout records must remain quarantined with zero truth exposure."""

    def test_zero_holdout_truth_exposure(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        self.assertEqual(ledger["holdoutTruthExposureCount"], 0)

        holdout_entries = [
            e for e in ledger["entries"]
            if e["lane"] == "prospective_holdout_candidate"
        ]
        self.assertEqual(len(holdout_entries), HOLDOUT_COUNT)
        for entry in holdout_entries:
            self.assertEqual(entry["truthFields"], {})
            self.assertEqual(entry["reviewStatus"], "UNKNOWN")

    def test_holdout_truth_fields_rejected(self):
        """Manifest validation rejects holdout records with truth fields."""
        manifest = _valid_manifest()
        for field in HOLDOUT_FORBIDDEN_TRUTH_FIELDS:
            with self.subTest(field=field):
                bad = copy.deepcopy(manifest)
                holdout_idx = DEVELOPMENT_COUNT  # First holdout
                bad["records"][holdout_idx][field] = "test_value"
                with self.assertRaises(ValueError):
                    validate_manifest_for_review(bad)

    def test_holdout_truth_in_ledger_rejected(self):
        """Ledger validation rejects holdout entries with truth fields."""
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        holdout_entry = next(
            e for e in ledger["entries"]
            if e["lane"] == "prospective_holdout_candidate"
        )
        holdout_entry["truthFields"] = {"verifiedSpecies": "Mewtwo"}
        ledger["holdoutTruthExposureCount"] = 1
        with self.assertRaises(ValueError):
            validate_ledger(ledger)


class TestCrossLaneLeakage(unittest.TestCase):
    """Cross-lane leakage must be rejected."""

    def test_cross_lane_rejected_in_manifest(self):
        manifest = _valid_manifest()
        # Make a dev record claim to be holdout
        bad = copy.deepcopy(manifest)
        bad["records"][0]["lane"] = "prospective_holdout_candidate"
        bad["records"][0]["id"] = bad["records"][DEVELOPMENT_COUNT]["id"]
        with self.assertRaises(ValueError):
            validate_manifest_for_review(bad)

    def test_cross_lane_rejected_in_ledger(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        # Create artificial cross-lane entry
        ledger["entries"][0]["lane"] = "prospective_holdout_candidate"
        ledger["entries"][0]["candidateId"] = (
            ledger["entries"][DEVELOPMENT_COUNT]["candidateId"]
        )
        with self.assertRaises(ValueError):
            validate_ledger(ledger)


class TestPrivacyValidation(unittest.TestCase):
    """Privacy-unsafe content must be rejected."""

    def test_windows_absolute_paths_rejected(self):
        manifest = _valid_manifest()
        bad = copy.deepcopy(manifest)
        bad["records"][0]["provenanceStatus"] = "C:\\Users\\test\\file.txt"
        with self.assertRaises(ValueError):
            validate_manifest_for_review(bad)

    def test_unix_home_paths_rejected(self):
        manifest = _valid_manifest()
        bad = copy.deepcopy(manifest)
        bad["records"][0]["provenanceStatus"] = "/home/user/corpus"
        with self.assertRaises(ValueError):
            validate_manifest_for_review(bad)

    def test_source_filenames_rejected(self):
        manifest = _valid_manifest()
        for ext in [".png", ".jpg", ".jpeg"]:
            with self.subTest(ext=ext):
                bad = copy.deepcopy(manifest)
                bad["records"][0]["provenanceStatus"] = f"source{ext}"
                with self.assertRaises(ValueError):
                    validate_manifest_for_review(bad)

    def test_account_identifiers_rejected(self):
        manifest = _valid_manifest()
        bad = copy.deepcopy(manifest)
        bad["records"][0]["accountId"] = "private-account"
        with self.assertRaises(ValueError):
            validate_manifest_for_review(bad)

    def test_device_serial_rejected(self):
        manifest = _valid_manifest()
        bad = copy.deepcopy(manifest)
        bad["records"][0]["deviceSerial"] = "ABC123"
        with self.assertRaises(ValueError):
            validate_manifest_for_review(bad)

    def test_adb_identifiers_rejected(self):
        manifest = _valid_manifest()
        bad = copy.deepcopy(manifest)
        bad["records"][0]["provenanceStatus"] = "adb connect 192.0.2.1:5555"
        with self.assertRaises(ValueError):
            validate_manifest_for_review(bad)

    def test_secrets_tokens_rejected(self):
        manifest = _valid_manifest()
        bad = copy.deepcopy(manifest)
        bad["records"][0]["authToken"] = "secret-token"
        with self.assertRaises(ValueError):
            validate_manifest_for_review(bad)

    def test_emulator_serial_rejected(self):
        manifest = _valid_manifest()
        bad = copy.deepcopy(manifest)
        bad["records"][0]["provenanceStatus"] = "emulator-5554"
        with self.assertRaises(ValueError):
            validate_manifest_for_review(bad)

    def test_screenshot_bytes_absent(self):
        """No screenshot/image bytes fields may be present."""
        manifest = _valid_manifest()
        for field in ["screenshotBytes", "thumbnailBytes", "imageData"]:
            with self.subTest(field=field):
                bad = copy.deepcopy(manifest)
                bad["records"][0][field] = "base64data"
                with self.assertRaises(ValueError):
                    validate_manifest_for_review(bad)


class TestForbiddenFields(unittest.TestCase):
    """All forbidden fields must be rejected."""

    def test_all_forbidden_fields_rejected(self):
        manifest = _valid_manifest()
        for field in FORBIDDEN_RECORD_FIELDS:
            with self.subTest(field=field):
                bad = copy.deepcopy(manifest)
                bad["records"][0][field] = "test"
                with self.assertRaises(ValueError):
                    validate_manifest_for_review(bad)


class TestOutputOrdering(unittest.TestCase):
    """Output ordering must be deterministic."""

    def test_entry_order_matches_manifest_record_order(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        manifest_ids = [r["id"] for r in manifest["records"]]
        ledger_ids = [e["candidateId"] for e in ledger["entries"]]
        self.assertEqual(manifest_ids, ledger_ids)


class TestRecordCounts(unittest.TestCase):
    """Verify correct record counts in ledger."""

    def test_development_and_holdout_counts(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        self.assertEqual(ledger["totalRecords"], RECORD_COUNT)
        self.assertEqual(ledger["developmentRecords"], DEVELOPMENT_COUNT)
        self.assertEqual(ledger["holdoutRecords"], HOLDOUT_COUNT)


class TestCanonicalBytesValidation(unittest.TestCase):
    """Canonical bytes format checks."""

    def test_bom_rejected(self):
        with self.assertRaises(ValueError):
            validate_canonical_bytes(b"\xef\xbb\xbf{}\n")

    def test_crlf_rejected(self):
        with self.assertRaises(ValueError):
            validate_canonical_bytes(b"{}\r\n")

    def test_missing_trailing_lf_rejected(self):
        with self.assertRaises(ValueError):
            validate_canonical_bytes(b"{}")

    def test_double_trailing_lf_rejected(self):
        with self.assertRaises(ValueError):
            validate_canonical_bytes(b"{}\n\n")

    def test_valid_bytes_pass(self):
        validate_canonical_bytes(b"{}\n")


class TestCommittedManifestIntegrity(unittest.TestCase):
    """Validate the committed candidate manifest against schema."""

    def setUp(self):
        if not MANIFEST_PATH.exists():
            self.skipTest("Committed manifest not available")
        with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
            self.manifest = json.load(f)

    def test_record_count(self):
        self.assertEqual(len(self.manifest["records"]), RECORD_COUNT)

    def test_development_count(self):
        devs = [r for r in self.manifest["records"]
                if r["lane"] == "development_candidate"]
        self.assertEqual(len(devs), DEVELOPMENT_COUNT)

    def test_holdout_count(self):
        holds = [r for r in self.manifest["records"]
                 if r["lane"] == "prospective_holdout_candidate"]
        self.assertEqual(len(holds), HOLDOUT_COUNT)

    def test_unique_sha256(self):
        hashes = [r["sha256"] for r in self.manifest["records"]]
        self.assertEqual(len(hashes), len(set(hashes)))

    def test_no_truth_labels(self):
        self.assertFalse(self.manifest["truthLabelsPresent"])

    def test_holdout_quarantined(self):
        self.assertTrue(self.manifest["prospectiveHoldoutQuarantined"])

    def test_candidate_only(self):
        self.assertTrue(self.manifest["candidateOnly"])

    def test_not_authoritative(self):
        self.assertFalse(self.manifest["authoritative"])

    def test_source_corpus_identity(self):
        self.assertEqual(self.manifest["sourceFileCount"], 730)
        self.assertEqual(self.manifest["sourceAggregateBytes"], 473_826_206)
        self.assertEqual(
            self.manifest["sourceDigestSha256"],
            "e3e3dadc4ffb64bf0db32f63f0ec0d08321eebdb82952bde068e6d6eaccc0dd1",
        )

    def test_manifest_passes_review_validation(self):
        validate_manifest_for_review(self.manifest)

    def test_unknown_export_from_committed_manifest(self):
        """UNKNOWN-only export from the committed manifest works and
        produces valid output."""
        ledger = build_unknown_ledger(self.manifest)
        validate_ledger(ledger)
        self.assertEqual(ledger["totalRecords"], RECORD_COUNT)
        self.assertEqual(ledger["truthCompletedCount"], 0)
        self.assertEqual(ledger["holdoutTruthExposureCount"], 0)
        output = canonical_json_bytes(ledger)
        validate_canonical_bytes(output)
        # Verify byte identity on second run
        ledger_2 = build_unknown_ledger(self.manifest)
        output_2 = canonical_json_bytes(ledger_2)
        self.assertEqual(output, output_2)

    def test_no_forbidden_fields_in_committed_manifest(self):
        for record in self.manifest["records"]:
            validate_no_forbidden_fields(record, record["id"])

    def test_no_privacy_violations_in_committed_manifest(self):
        for record in self.manifest["records"]:
            validate_privacy_all_fields(record, record["id"])

    def test_canonical_lf_format(self):
        """Committed manifest should use LF line endings."""
        raw = MANIFEST_PATH.read_bytes()
        self.assertNotIn(b"\r\n", raw, "CRLF found in committed manifest")
        self.assertFalse(
            raw.startswith(b"\xef\xbb\xbf"),
            "BOM found in committed manifest",
        )


if __name__ == "__main__":
    unittest.main()
