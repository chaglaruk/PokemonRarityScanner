"""Synthetic-data tests for the read-only costume reference inventory."""
import unittest

from audit_costume_reference_coverage import analyze, valid_signature


def signature():
    return {
        "aHash": "0" * 16,
        "dHash": "f" * 16,
        "pHash": "a" * 16,
        "headPHash": "b" * 16,
        "edge": [0.125] * 8,
    }


class CostumeCoverageAuditTest(unittest.TestCase):
    def test_validates_hash_and_edge_shape(self):
        self.assertTrue(valid_signature(signature()))
        self.assertFalse(valid_signature({**signature(), "headPHash": "invalid"}))
        self.assertFalse(valid_signature({**signature(), "edge": [0.5, 0.5]}))
        self.assertFalse(valid_signature(None))

    def test_reports_costume_and_normal_reference_coverage(self):
        master = {"entries": [
            {"species": "Cottonee", "spriteKey": "546_00", "isCostumeLike": False,
             "signature": signature()},
            {"species": "Cottonee", "spriteKey": "546_00_SPRING_2024",
             "isCostumeLike": True, "signature": signature()},
            {"species": "Slowpoke", "spriteKey": "079_00_EVENT",
             "isCostumeLike": True, "signature": None},
        ]}
        legacy = {"signatures": [
            {"key": "546_00_SPRING_2024", "species": "Cottonee", "isCostume": True},
            {"key": "025_00_07", "species": "Pikachu", "isCostume": True},
        ]}
        report = analyze(master, legacy)
        self.assertEqual(2, report["masterCostumeEntries"])
        self.assertEqual(2, report["masterCostumeSpecies"])
        self.assertEqual(1, report["masterCostumesWithWellFormedSignatures"])
        self.assertEqual(1, report["masterCostumesMissingOrMalformedSignatures"])
        self.assertEqual(["079_00_EVENT"], report["missingSignatureExamples"])
        self.assertEqual(1, report["costumeSpeciesWithoutNormalSignature"])
        self.assertEqual(1, report["legacyKeysNotInMaster"])
        self.assertEqual(2, report["legacyCostumeEntries"])
        self.assertEqual("bundled_catalogue_and_signature_coverage_not_accuracy", report["scope"])

    def test_flags_duplicates_and_malformed_signatures_without_claiming_accuracy(self):
        master = {"entries": [
            {"species": "Pikachu", "spriteKey": "025_00_01", "isCostumeLike": True,
             "signature": {**signature(), "aHash": "invalid"}},
            {"species": "Pikachu", "spriteKey": "025_00_01", "isCostumeLike": True,
             "signature": signature()},
        ]}
        report = analyze(master, {"signatures": []})
        self.assertEqual(1, report["masterDuplicateSpriteKeys"])
        self.assertEqual(1, report["masterEntriesWithInvalidNonNullSignatures"])
        self.assertEqual(["025_00_01"], report["invalidSignatureExamples"])

    def test_counts_unsigned_non_costume_species_in_master_inventory(self):
        master = {"entries": [
            {"species": "Cottonee", "spriteKey": "546_00", "isCostumeLike": False,
             "signature": None},
            {"species": "Slowpoke", "spriteKey": "079_00_EVENT", "isCostumeLike": True,
             "signature": signature()},
            {"species": "Pikachu", "spriteKey": "025_00", "isCostumeLike": False,
             "signature": None},
            {"species": "Invalid", "spriteKey": "", "isCostumeLike": False,
             "signature": None},
        ]}
        report = analyze(master, {"signatures": []})
        self.assertEqual(3, report["masterSpeciesCount"])
        self.assertEqual(1, report["masterCostumeSpecies"])

    def test_rejects_invalid_top_level_arrays(self):
        with self.assertRaises(ValueError):
            analyze({"entries": {}}, {"signatures": []})


if __name__ == "__main__":
    unittest.main()
