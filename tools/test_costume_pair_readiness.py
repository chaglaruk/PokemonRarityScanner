"""Synthetic tests: real-game costume pairing and holdout leakage gates."""
import unittest

from costume_pair_readiness import ManifestError, audit_manifest


def frame(index, species="Slowpoke", costume=False, shiny=False,
          role="reference", group=None, form="00", confirmed=True):
    return {
        "sha256": f"{index:064x}",
        "species": species,
        "formId": form,
        "shiny": shiny,
        "costume": costume,
        "captureGroup": group or f"group-{index}",
        "role": role,
        "truthStatus": "human_confirmed" if confirmed else "unverified",
    }


def manifest(*records):
    return {"schemaVersion": 1, "records": list(records)}


class CostumePairReadinessTest(unittest.TestCase):
    def test_paired_same_species_references_and_independent_holdout(self):
        report = audit_manifest(manifest(
            frame(1, costume=False),
            frame(2, costume=True),
            frame(3, costume=True, role="holdout"),
            frame(4, costume=False, role="holdout"),
            frame(5, costume=True, role="development"),
        ))
        self.assertEqual(1, report["totals"]["sameStratumPaired"])
        self.assertEqual(2, report["totals"]["holdoutFramesEligible"])
        self.assertEqual(1, report["totals"]["strataWithPairedHoldoutClasses"])
        self.assertTrue(report["strata"][0]["pairedReferenceReady"])
        self.assertTrue(report["strata"][0]["holdoutBothClassesReady"])
        self.assertEqual("same_species_form_shiny_reference_readiness_not_recognition_accuracy",
                         report["scope"])

    def test_different_species_neighbours_cannot_supply_negative_reference(self):
        report = audit_manifest(manifest(
            frame(1, species="Slowpoke", costume=True),
            frame(2, species="Pikachu", costume=False),
            frame(3, species="Slowpoke", costume=True, role="holdout"),
        ))
        slowpoke = next(x for x in report["strata"] if x["species"] == "Slowpoke")
        self.assertFalse(slowpoke["pairedReferenceReady"])
        self.assertEqual(1, report["totals"]["holdoutFramesIneligible"])
        self.assertEqual(0, report["totals"]["sameStratumPaired"])

    def test_shiny_and_form_are_separate_reference_strata(self):
        report = audit_manifest(manifest(
            frame(1, species="Cottonee", shiny=True, costume=False),
            frame(2, species="Cottonee", shiny=False, costume=True),
            frame(3, species="Cottonee", shiny=False, costume=False, form="ALOLAN"),
        ))
        self.assertEqual(3, len(report["strata"]))
        self.assertEqual(0, report["totals"]["sameStratumPaired"])

    def test_identical_frame_in_multiple_roles_is_rejected(self):
        with self.assertRaisesRegex(ManifestError, "duplicate_frame_digest"):
            audit_manifest(manifest(
                frame(1, costume=False, role="reference"),
                frame(1, costume=True, role="holdout"),
            ))

    def test_same_capture_group_across_splits_is_rejected(self):
        with self.assertRaisesRegex(ManifestError, "capture_group_leaks"):
            audit_manifest(manifest(
                frame(1, costume=False, group="same-pokemon", role="reference"),
                frame(2, costume=True, group="same-pokemon", role="holdout"),
            ))

    def test_one_reference_capture_group_cannot_supply_both_classes(self):
        with self.assertRaisesRegex(ManifestError, "capture_group_conflicting_truth"):
            audit_manifest(manifest(
                frame(1, costume=False, group="same-session"),
                frame(2, costume=True, group="same-session"),
            ))

    def test_one_holdout_capture_group_cannot_supply_both_classes(self):
        with self.assertRaisesRegex(ManifestError, "capture_group_conflicting_truth"):
            audit_manifest(manifest(
                frame(1, costume=False, role="holdout", group="same-session"),
                frame(2, costume=True, role="holdout", group="same-session"),
            ))

    def test_missing_form_is_not_silently_assigned_default(self):
        row = frame(1)
        row.pop("formId")
        with self.assertRaisesRegex(ManifestError, "missing_verified_form"):
            audit_manifest(manifest(row))

    def test_holdout_capture_groups_must_be_distinct_between_classes(self):
        report = audit_manifest(manifest(
            frame(1, costume=False), frame(2, costume=True),
            frame(3, costume=False, role="holdout", group="holdout-a"),
            frame(4, costume=True, role="holdout", group="holdout-b"),
        ))
        self.assertTrue(report["strata"][0]["holdoutBothClassesReady"])

    def test_case_variant_duplicate_hash_is_rejected(self):
        lower = frame(1, costume=False)
        upper = frame(1, costume=True)
        upper["sha256"] = upper["sha256"].upper()
        with self.assertRaisesRegex(ManifestError, "duplicate_frame_digest"):
            audit_manifest(manifest(lower, upper))

    def test_unconfirmed_truth_is_excluded(self):
        report = audit_manifest(manifest(
            frame(1, costume=False),
            frame(2, costume=True, confirmed=False),
            frame(3, costume=True, role="holdout"),
        ))
        self.assertEqual(1, report["unconfirmedFramesExcluded"])
        self.assertFalse(report["strata"][0]["pairedReferenceReady"])

    def test_private_path_and_image_fields_are_rejected(self):
        for key in ("path", "filename", "imageData", "rawOcr"):
            record = frame(1)
            record[key] = "private-material"
            with self.assertRaisesRegex(ManifestError, "private_payload_field"):
                audit_manifest(manifest(record))

    def test_input_schema_is_bounded(self):
        with self.assertRaisesRegex(ManifestError, "unsupported_manifest_schema"):
            audit_manifest({"schemaVersion": 2, "records": []})
        with self.assertRaisesRegex(ManifestError, "missing_ground_truth"):
            row = frame(1)
            row.pop("costume")
            audit_manifest(manifest(row))


if __name__ == "__main__":
    unittest.main()
