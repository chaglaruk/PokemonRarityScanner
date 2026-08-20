"""Tests for the offline review HTML generator."""

import json
import unittest

from manual_gate.review_generator import (
    generate_review_html,
    validate_no_external_references,
)
from manual_gate.ledger_schema import build_unknown_ledger


DEVELOPMENT_COUNT = 100
HOLDOUT_COUNT = 20
RECORD_COUNT = DEVELOPMENT_COUNT + HOLDOUT_COUNT


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


class TestReviewGeneratorOutput(unittest.TestCase):
    """Review HTML generator output validation."""

    def test_no_external_network_dependencies(self):
        """Generated HTML must have zero external network references."""
        manifest = _valid_manifest()
        html = generate_review_html(manifest)
        validate_no_external_references(html)

    def test_no_external_dependencies_with_ledger(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        html = generate_review_html(manifest, ledger)
        validate_no_external_references(html)

    def test_deterministic_output(self):
        """HTML output must be deterministic."""
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        html_1 = generate_review_html(manifest, ledger)
        html_2 = generate_review_html(manifest, ledger)
        self.assertEqual(html_1, html_2)

    def test_contains_gate_status(self):
        manifest = _valid_manifest()
        html = generate_review_html(manifest)
        self.assertIn("OPEN", html)
        self.assertIn("Manual Gate A", html)

    def test_contains_privacy_warning(self):
        manifest = _valid_manifest()
        html = generate_review_html(manifest)
        self.assertIn("Privacy Notice", html)
        self.assertIn("Trust Boundary", html)

    def test_holdout_quarantine_shown(self):
        manifest = _valid_manifest()
        html = generate_review_html(manifest)
        self.assertIn("QUARANTINED", html)
        self.assertIn("Holdout Quarantine", html)

    def test_development_candidates_listed(self):
        manifest = _valid_manifest()
        html = generate_review_html(manifest)
        self.assertIn("s25_2026_dev_001", html)
        self.assertIn("s25_2026_dev_100", html)

    def test_valid_html_structure(self):
        manifest = _valid_manifest()
        html = generate_review_html(manifest)
        self.assertIn("<!DOCTYPE html>", html)
        self.assertIn("<html", html)
        self.assertIn("</html>", html)
        self.assertIn("<meta charset=\"UTF-8\">", html)

    def test_no_source_filenames_in_html(self):
        """HTML must not contain source filenames."""
        manifest = _valid_manifest()
        html = generate_review_html(manifest)
        self.assertNotIn(".png", html.lower())
        self.assertNotIn(".jpg", html.lower())

    def test_no_full_sha256_in_html(self):
        """HTML should show only SHA-256 prefixes, not full hashes."""
        manifest = _valid_manifest()
        # First record's full SHA-256
        full_sha = manifest["records"][0]["sha256"]
        html = generate_review_html(manifest)
        # The full hash should NOT appear — only prefix + ellipsis
        self.assertNotIn(full_sha, html)


class TestExternalReferenceValidation(unittest.TestCase):
    """validate_no_external_references catches external URLs."""

    def test_http_detected(self):
        with self.assertRaises(ValueError):
            validate_no_external_references(
                '<link href="http://example.com/style.css">'
            )

    def test_https_detected(self):
        with self.assertRaises(ValueError):
            validate_no_external_references(
                '<script src="https://cdn.example.com/lib.js"></script>'
            )

    def test_fetch_detected(self):
        with self.assertRaises(ValueError):
            validate_no_external_references("fetch('http://api.example.com')")

    def test_clean_html_passes(self):
        validate_no_external_references(
            "<html><head><style>body{}</style></head></html>"
        )


if __name__ == "__main__":
    unittest.main()
