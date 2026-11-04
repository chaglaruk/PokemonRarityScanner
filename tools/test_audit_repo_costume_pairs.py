"""Synthetic tests for the existing public QA image-pair discovery audit."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from audit_repo_costume_pairs import PNG_MAGIC, FixtureAuditError, audit_public_fixtures


def row(species="Slowpoke", shiny=False, costume=False, index=1,
        strict=True, form=None, capture_group=None):
    expected = {"species": species, "shiny": shiny, "costume": costume}
    if form is not None:
        expected["formId"] = form
    result = {
        "strict": strict,
        "expected": expected,
        "assetPath": f"scan_fixtures/test_{index}.png",
        "notes": "Manual label from exported live fixture. Confirmed",
    }
    if capture_group is not None:
        result["captureGroup"] = capture_group
    return result


class PublicCostumePairAuditTest(unittest.TestCase):
    def setUp(self):
        self.workspace = TemporaryDirectory()
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)

    def inspect(self, records, missing=()):
        for item in records:
            path = self.root / item["assetPath"]
            if str(path) in missing:
                continue
            path.parent.mkdir(parents=True, exist_ok=True)
            # Only the bytes and PNG signature are used by the discovery audit.
            path.write_bytes(PNG_MAGIC + item["assetPath"].encode())
        return audit_public_fixtures(records, self.root)

    def test_detects_existing_slowpoke_candidate_pair_without_claiming_holdout(self):
        report = self.inspect([
            row(costume=False, index=1),
            row(costume=True, index=2),
        ])
        self.assertEqual(2, report["strictFixtureImagesVerified"])
        self.assertEqual(1, report["strictCostumePositiveImages"])
        self.assertEqual(1, report["strictCostumeNegativeImages"])
        self.assertEqual(1, len(report["potentialSameSpeciesShinyPairs"]))
        pair = report["potentialSameSpeciesShinyPairs"][0]
        self.assertEqual("Slowpoke", pair["species"])
        self.assertTrue(pair["manualLabelsClaimed"])
        self.assertFalse(pair["sameFormExplicitlyVerified"])
        self.assertFalse(pair["distinctCaptureGroupsVerified"])
        self.assertFalse(pair["independentHoldout"])

    def test_different_shiny_status_cannot_create_matched_pair(self):
        report = self.inspect([
            row(species="Cottonee", shiny=False, costume=True, index=1),
            row(species="Cottonee", shiny=True, costume=False, index=2),
        ])
        self.assertEqual([], report["potentialSameSpeciesShinyPairs"])

    def test_explicit_form_and_distinct_capture_groups_produce_verified_candidate(self):
        report = self.inspect([
            row(costume=False, index=1, form="00", capture_group="one"),
            row(costume=True, index=2, form="00", capture_group="two"),
        ])
        pair = report["potentialSameSpeciesShinyPairs"][0]
        self.assertTrue(pair["sameFormExplicitlyVerified"])
        self.assertTrue(pair["distinctCaptureGroupsVerified"])
        self.assertFalse(pair["independentHoldout"])

    def test_missing_png_is_reported_instead_of_silent_exclusion(self):
        first = row(index=1)
        with self.assertRaisesRegex(FixtureAuditError, "missing_or_unsafe_fixture_asset"):
            self.inspect([first], missing={str(self.root / first["assetPath"])})

    def test_rejects_directory_traversal(self):
        invalid = row(index=1)
        invalid["assetPath"] = "scan_fixtures/../../../secret.png"
        with self.assertRaisesRegex(FixtureAuditError, "missing_or_unsafe_fixture_asset"):
            audit_public_fixtures([invalid], self.root)

    def test_rejects_duplicate_image_bytes(self):
        records = [row(index=1), row(index=2)]
        for record in records:
            path = self.root / record["assetPath"]
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(PNG_MAGIC + b"same")
        with self.assertRaisesRegex(FixtureAuditError, "duplicate_png_fixture"):
            audit_public_fixtures(records, self.root)

    def test_does_not_infer_truth_from_non_strict_record(self):
        report = self.inspect([
            row(index=1, strict=True, costume=True),
            row(index=2, strict=False, costume=False),
        ])
        self.assertEqual(1, report["strictFixtureImagesVerified"])
        self.assertEqual([], report["potentialSameSpeciesShinyPairs"])


if __name__ == "__main__":
    unittest.main()
