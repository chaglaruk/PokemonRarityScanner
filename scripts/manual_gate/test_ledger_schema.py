"""Fail-closed tests for the Manual Gate A UNKNOWN-only export."""

from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from manual_gate.export_unknown_ledger import ensure_output_outside_repo, main as export_main
from manual_gate.ledger_schema import (
    DEVELOPMENT_COUNT,
    EXPECTED_SOURCE_BYTES,
    EXPECTED_SOURCE_COUNT,
    EXPECTED_SOURCE_DIGEST,
    MANIFEST_RECORD_KEYS,
    RECORD_COUNT,
    build_unknown_ledger,
    canonical_json_bytes,
    validate_canonical_bytes,
    validate_ledger,
    validate_manifest_for_review,
)


def _valid_manifest() -> dict:
    records = []
    for index in range(RECORD_COUNT):
        is_dev = index < DEVELOPMENT_COUNT
        number = index + 1 if is_dev else index - DEVELOPMENT_COUNT + 1
        records.append({
            "bitDepth": 8,
            "byteSize": 500_000 + index,
            "colorMode": "RGBA",
            "duplicateDecisionReason": None,
            "format": "PNG",
            "height": 2340,
            "id": f"s25_2026_{'dev' if is_dev else 'holdout'}_{number:03}",
            "lane": "development_candidate" if is_dev else "prospective_holdout_candidate",
            "likelyCandyFamilyPresent": True,
            "likelyCpPresent": True,
            "likelyDetailsScreen": True,
            "likelyHpPresent": True,
            "likelyNamePresent": True,
            "manualTruthStatus": "unreviewed",
            "nearDuplicateClusterId": None,
            "overlayPresent": True,
            "privacyClassification": "NEEDS_HUMAN_PRIVACY_REVIEW",
            "provenanceStatus": "user_supplied_local_corpus",
            "publicationStatus": "not_approved",
            "sha256": f"{index + 1:064x}",
            "width": 1080,
        })
    assert all(set(record) == MANIFEST_RECORD_KEYS for record in records)
    return {
        "authoritative": False,
        "candidateOnly": True,
        "containsScreenshotBytes": False,
        "datasetId": "candidate_2026_s25",
        "nearDuplicateClusters": [],
        "nearDuplicateGroupCount": 61,
        "nearDuplicateGroupedFileCount": 176,
        "nearDuplicateMethod": {},
        "prospectiveHoldoutQuarantined": True,
        "records": records,
        "redundantExcludedCount": 5,
        "schemaVersion": 1,
        "sourceAggregateBytes": EXPECTED_SOURCE_BYTES,
        "sourceDigestSha256": EXPECTED_SOURCE_DIGEST,
        "sourceFileCount": EXPECTED_SOURCE_COUNT,
        "structurallyEligibleCount": 724,
        "truthLabelsPresent": False,
    }


class ManifestValidationTest(unittest.TestCase):
    def test_valid_manifest(self):
        validate_manifest_for_review(_valid_manifest())

    def test_requires_exact_record_count_and_lane_split(self):
        for mutation in ("missing", "wrong_lane"):
            with self.subTest(mutation=mutation):
                manifest = _valid_manifest()
                if mutation == "missing":
                    manifest["records"].pop()
                else:
                    manifest["records"][0]["lane"] = "prospective_holdout_candidate"
                with self.assertRaises(ValueError):
                    validate_manifest_for_review(manifest)

    def test_requires_exact_ids_order_and_unique_hashes(self):
        for mutation in ("id", "order", "hash"):
            with self.subTest(mutation=mutation):
                manifest = _valid_manifest()
                if mutation == "id":
                    manifest["records"][0]["id"] = "s25_2026_dev_999"
                elif mutation == "order":
                    manifest["records"][0], manifest["records"][1] = manifest["records"][1], manifest["records"][0]
                else:
                    manifest["records"][1]["sha256"] = manifest["records"][0]["sha256"]
                with self.assertRaises(ValueError):
                    validate_manifest_for_review(manifest)

    def test_rejects_extra_root_record_and_scanner_suggestion_fields(self):
        mutations = [
            lambda value: value.__setitem__("extra", True),
            lambda value: value["records"][0].__setitem__("extra", True),
            lambda value: value["records"][0].__setitem__("scannerSuggestedCp", 123),
        ]
        for mutate in mutations:
            manifest = _valid_manifest()
            mutate(manifest)
            with self.assertRaises(ValueError):
                validate_manifest_for_review(manifest)

    def test_requires_source_identity_and_non_authoritative_flags(self):
        for key, bad_value in (
            ("sourceFileCount", 729),
            ("sourceAggregateBytes", 1),
            ("sourceDigestSha256", "0" * 64),
            ("nearDuplicateGroupCount", 60),
            ("nearDuplicateGroupedFileCount", 175),
            ("redundantExcludedCount", 4),
            ("structurallyEligibleCount", 723),
            ("authoritative", True),
            ("truthLabelsPresent", True),
            ("prospectiveHoldoutQuarantined", False),
        ):
            with self.subTest(key=key):
                manifest = _valid_manifest()
                manifest[key] = bad_value
                with self.assertRaises(ValueError):
                    validate_manifest_for_review(manifest)

    def test_rejects_private_values(self):
        for value in (r"C:\Users\person\secret", "/home/person/secret", "emulator-5554", "capture.png"):
            with self.subTest(value=value):
                manifest = _valid_manifest()
                manifest["records"][0]["provenanceStatus"] = value
                with self.assertRaises(ValueError):
                    validate_manifest_for_review(manifest)


class UnknownLedgerTest(unittest.TestCase):
    def test_export_is_exact_unknown_only_state(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        validate_ledger(ledger, manifest)
        self.assertEqual(ledger["totalRecords"], 120)
        self.assertEqual(ledger["developmentRecords"], 100)
        self.assertEqual(ledger["holdoutRecords"], 20)
        self.assertEqual(ledger["gateStatus"], "OPEN")
        self.assertEqual(ledger["completionStatus"], "INCOMPLETE")
        self.assertEqual(ledger["truthCompletedCount"], 0)
        self.assertEqual(ledger["privacyApprovedCount"], 0)
        self.assertEqual(ledger["provenanceVerifiedCount"], 0)
        self.assertEqual(ledger["suggestionsPromotedCount"], 0)
        self.assertEqual(ledger["holdoutTruthExposureCount"], 0)
        self.assertTrue(all(entry["truthFields"] == {} for entry in ledger["entries"]))

    def test_rejects_any_approval_truth_note_or_suggestion_promotion(self):
        mutations = [
            lambda ledger: ledger["entries"][0].__setitem__("reviewStatus", "VERIFIED_FROM_SOURCE"),
            lambda ledger: ledger["entries"][0].__setitem__("privacyDisposition", "APPROVED"),
            lambda ledger: ledger["entries"][0].__setitem__("provenanceDisposition", "VERIFIED"),
            lambda ledger: ledger["entries"][0].__setitem__("truthFields", {"species": "Pikachu"}),
            lambda ledger: ledger["entries"][0].__setitem__("reviewerNotes", "note"),
            lambda ledger: ledger["entries"][0].__setitem__("suggestionsPromoted", True),
        ]
        for mutate in mutations:
            manifest = _valid_manifest()
            ledger = build_unknown_ledger(manifest)
            mutate(ledger)
            with self.assertRaises(ValueError):
                validate_ledger(ledger, manifest)

    def test_rejects_wrong_gate_counts_or_review_type(self):
        for key, value in (
            ("gateStatus", "CLOSED"),
            ("completionStatus", "COMPLETE"),
            ("reviewType", "HUMAN_REVIEW"),
            ("totalRecords", 119),
            ("developmentRecords", 99),
            ("holdoutRecords", 21),
            ("truthCompletedCount", 1),
        ):
            with self.subTest(key=key):
                manifest = _valid_manifest()
                ledger = build_unknown_ledger(manifest)
                ledger[key] = value
                with self.assertRaises(ValueError):
                    validate_ledger(ledger, manifest)

    def test_rejects_extra_or_missing_entry_keys(self):
        for mutation in ("extra", "missing"):
            manifest = _valid_manifest()
            ledger = build_unknown_ledger(manifest)
            if mutation == "extra":
                ledger["entries"][0]["sourcePath"] = r"C:\Users\person\x"
            else:
                ledger["entries"][0].pop("reviewerNotes")
            with self.assertRaises(ValueError):
                validate_ledger(ledger, manifest)

    def test_rejects_entry_hash_lane_id_or_order_drift_from_manifest(self):
        mutations = [
            lambda ledger: ledger["entries"][0].__setitem__("sha256", "f" * 64),
            lambda ledger: ledger["entries"][0].__setitem__("lane", "prospective_holdout_candidate"),
            lambda ledger: ledger["entries"][0].__setitem__("candidateId", "s25_2026_dev_999"),
            lambda ledger: ledger["entries"].__setitem__(slice(0, 2), list(reversed(ledger["entries"][:2]))),
        ]
        for mutate in mutations:
            manifest = _valid_manifest()
            ledger = build_unknown_ledger(manifest)
            mutate(ledger)
            with self.assertRaises(ValueError):
                validate_ledger(ledger, manifest)

    def test_rejects_missing_or_extra_entries_and_root_keys(self):
        mutations = [
            lambda ledger: ledger["entries"].pop(),
            lambda ledger: ledger["entries"].append(copy.deepcopy(ledger["entries"][0])),
            lambda ledger: ledger.__setitem__("extra", True),
        ]
        for mutate in mutations:
            manifest = _valid_manifest()
            ledger = build_unknown_ledger(manifest)
            mutate(ledger)
            with self.assertRaises(ValueError):
                validate_ledger(ledger, manifest)


class CanonicalBytesTest(unittest.TestCase):
    def test_repeated_export_is_byte_identical(self):
        manifest = _valid_manifest()
        one = canonical_json_bytes(build_unknown_ledger(manifest))
        two = canonical_json_bytes(build_unknown_ledger(manifest))
        self.assertEqual(one, two)
        validate_canonical_bytes(one)

    def test_rejects_bom_crlf_missing_or_double_lf(self):
        for data in (b"\xef\xbb\xbf{}\n", b"{}\r\n", b"{}", b"{}\n\n"):
            with self.subTest(data=data):
                with self.assertRaises(ValueError):
                    validate_canonical_bytes(data)

    def test_rejects_valid_json_with_noncanonical_spacing_or_key_order(self):
        for data in (b'{"b": 1, "a": 2}\n', b'{"a":2}\n'):
            with self.subTest(data=data):
                with self.assertRaises(ValueError):
                    validate_canonical_bytes(data)


class CliTest(unittest.TestCase):
    def test_cli_is_deterministic(self):
        with TemporaryDirectory() as tmp:
            manifest_path = Path(tmp) / "manifest.json"
            manifest_path.write_bytes(canonical_json_bytes(_valid_manifest()))
            one = Path(tmp) / "one.json"
            two = Path(tmp) / "two.json"
            self.assertEqual(export_main(["--manifest", str(manifest_path), "--output", str(one)]), 0)
            self.assertEqual(export_main(["--manifest", str(manifest_path), "--output", str(two)]), 0)
            self.assertEqual(one.read_bytes(), two.read_bytes())

    def test_output_inside_repo_is_rejected(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "settings.gradle.kts").write_text("", encoding="utf-8")
            manifest_path = root / "manifest.json"
            manifest_path.write_bytes(canonical_json_bytes(_valid_manifest()))
            with self.assertRaises(ValueError):
                ensure_output_outside_repo(manifest_path, root / "ledger.json")


class CommittedManifestIntegrationTest(unittest.TestCase):
    def test_committed_manifest_matches_unknown_export_contract(self):
        manifest_path = (
            Path(__file__).resolve().parents[2]
            / "app/src/test/resources/scan_fixtures/candidate_2026_s25_manifest.json"
        )
        self.assertTrue(manifest_path.is_file(), "Committed candidate manifest is missing")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        validate_manifest_for_review(manifest)
        ledger = build_unknown_ledger(manifest)
        validate_ledger(ledger, manifest)
        self.assertEqual(len(ledger["entries"]), 120)
        self.assertEqual(ledger["holdoutTruthExposureCount"], 0)


if __name__ == "__main__":
    unittest.main()
