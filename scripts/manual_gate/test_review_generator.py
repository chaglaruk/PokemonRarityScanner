"""Tests for the strict offline Manual Gate A status page."""

from __future__ import annotations

import unittest

from manual_gate.ledger_schema import build_unknown_ledger
from manual_gate.review_generator import generate_review_html, validate_no_external_references
from manual_gate.test_ledger_schema import _valid_manifest


class ReviewGeneratorTest(unittest.TestCase):
    def test_valid_unknown_status_page(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        page = generate_review_html(manifest, ledger)
        validate_no_external_references(page)
        self.assertIn("UNKNOWN-only status", page)
        self.assertIn("s25_2026_dev_001", page)
        self.assertIn("QUARANTINED", page)
        self.assertNotIn(".png", page.lower())
        self.assertNotIn(manifest["records"][0]["sha256"], page)

    def test_output_is_deterministic(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        self.assertEqual(generate_review_html(manifest, ledger), generate_review_html(manifest, ledger))

    def test_invalid_manifest_is_rejected_before_render(self):
        manifest = _valid_manifest()
        manifest["records"][0]["scannerSuggestedCp"] = 123
        with self.assertRaises(ValueError):
            generate_review_html(manifest)

    def test_invalid_ledger_is_rejected_before_render(self):
        manifest = _valid_manifest()
        ledger = build_unknown_ledger(manifest)
        ledger["entries"][0]["privacyDisposition"] = "APPROVED"
        with self.assertRaises(ValueError):
            generate_review_html(manifest, ledger)

    def test_network_patterns_are_rejected(self):
        for payload in (
            '<script src="https://example.invalid/a.js"></script>',
            '<link href="local.css">',
            "fetch('/x')",
            "new XMLHttpRequest()",
            "new WebSocket('ws://example.invalid')",
        ):
            with self.subTest(payload=payload):
                with self.assertRaises(ValueError):
                    validate_no_external_references(payload)


if __name__ == "__main__":
    unittest.main()
