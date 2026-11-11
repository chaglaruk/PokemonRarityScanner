#!/usr/bin/env python3
"""Unit tests for privacy-safe, same-frame Android replay comparisons."""

import unittest

from compare_shiny_archive_replays import compare_pair, summarize_report, validate_report


def frame(image_id, shiny, species="Cottonee", reason=None, opposing=0):
    return {
        "frame": image_id,
        "species": species,
        "speciesCandidate": species,
        "shinyStatus": shiny,
        "shinyDecisionReason": reason,
        "normalCounterevidence": {"strongOpposingViewCount": opposing},
        "form": {"status": "KNOWN", "knownForm": "Normal Form"},
        "costume": {"status": "UNKNOWN", "visualPositive": False},
    }


class ShinyArchiveReplayComparisonTest(unittest.TestCase):
    def test_counts_directional_shiny_transitions_and_normal_opposition(self):
        first_id = "H" + "1" * 64
        second_id = "H" + "2" * 64
        left = {
            first_id: frame(first_id, "TRUE", reason="typed_positive_evidence"),
            second_id: frame(second_id, "UNKNOWN", reason="typed_insufficient_evidence"),
        }
        right = {
            first_id: frame(first_id, "UNKNOWN", reason="typed_insufficient_evidence", opposing=1),
            second_id: frame(second_id, "TRUE", reason="typed_positive_evidence"),
        }

        result = compare_pair(left, right)

        self.assertEqual(result["frameCount"], 2)
        self.assertEqual(result["changedFrames"], 2)
        self.assertEqual(result["changedFields"]["shiny"], 2)
        self.assertEqual(result["shinyTransitions"], {"TRUE->UNKNOWN": 1, "UNKNOWN->TRUE": 1})
        self.assertEqual(result["shinyChangesWithStrongNormalCounterevidenceAfter"], 1)

    def test_validates_frame_key_set_and_exact_revision(self):
        image_id = "H" + "a" * 64
        report = {
            "sourceRevision": "a" * 40,
            "frameSummaries": [frame(image_id, "UNKNOWN")],
        }

        indexed = validate_report(report, "baseline", "a" * 40, expected_count=1)

        self.assertEqual(list(indexed), [image_id])
        with self.assertRaises(ValueError):
            validate_report(report, "baseline", "b" * 40)
        with self.assertRaises(ValueError):
            validate_report(report, "baseline", expected_count=2)

    def test_rejects_non_hash_frame_keys(self):
        report = {"frameSummaries": [frame("local-filename.png", "UNKNOWN")]}
        with self.assertRaises(ValueError):
            validate_report(report, "candidate")

    def test_coverage_counts_only_publishable_scan_results(self):
        accepted_id = "H" + "3" * 64
        candidate_id = "H" + "4" * 64
        accepted = frame(accepted_id, "FALSE")
        accepted["speciesStatus"] = "KNOWN"
        accepted["scanDecision"] = {"mayShowOverlay": True, "maySaveScan": True}
        candidate = frame(candidate_id, "NO_RESULT")
        candidate["speciesStatus"] = "KNOWN"
        candidate["scanDecision"] = {"mayShowOverlay": False, "maySaveScan": False}
        rows = {accepted_id: accepted, candidate_id: candidate}

        result = summarize_report({"sourceRevision": "a" * 40}, rows)

        self.assertEqual(result["recognizedSpeciesCount"], 1)
        self.assertEqual(result["predictedSpeciesCount"], 2)
        self.assertEqual(result["speciesStatus"], {"KNOWN": 1, "NO_RESULT": 1})
        self.assertEqual(result["formDecisions"]["NO_RESULT"], 1)


if __name__ == "__main__":
    unittest.main()
