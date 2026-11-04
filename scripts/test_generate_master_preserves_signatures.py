import copy
import unittest
from unittest.mock import patch

import generate_master_pokedex as generator


class PreserveMasterSignaturesTest(unittest.TestCase):
    def setUp(self):
        self.signature = {"aHash": "0123456789abcdef", "dHash": "0123456789abcdef",
                          "pHash": "0123456789abcdef", "headPHash": "0123456789abcdef", "edge": [0.125] * 8}
        self.previous = {"entries": [{"spriteKey": "547_00_SPRING_2024", "signature": self.signature}]}
        self.authoritative = {"entries": [{"species": "Whimsicott", "spriteKey": "547_00_SPRING_2024",
                                          "variantClass": "costume", "isCostumeLike": True, "isShiny": False}]}

    def test_missing_source_preserves_valid_existing_signature(self):
        with patch.object(generator, "load_json", side_effect=[self.authoritative, self.previous]), \
             patch.object(generator.os.path, "exists", return_value=True), \
             patch.object(generator, "build_signature_index", return_value={}), \
             patch.object(generator, "compute_signature", return_value=None):
            result = generator.build_master_pokedex("previous.json", preserve_existing_signatures=True)
        self.assertEqual(self.signature, result["entries"][0]["signature"])

    def test_missing_source_fails_closed_without_explicit_reuse(self):
        with patch.object(generator, "load_json", side_effect=[self.authoritative, self.previous]), \
             patch.object(generator.os.path, "exists", return_value=True), \
             patch.object(generator, "build_signature_index", return_value={}), \
             patch.object(generator, "compute_signature", return_value=None):
            with self.assertRaisesRegex(ValueError, "refusing to erase"):
                generator.build_master_pokedex("previous.json")

    def test_new_signature_has_priority_over_previous(self):
        fresh = copy.deepcopy(self.signature)
        fresh["aHash"] = "ffffffffffffffff"
        with patch.object(generator, "load_json", side_effect=[self.authoritative, self.previous]), \
             patch.object(generator.os.path, "exists", return_value=True), \
             patch.object(generator, "build_signature_index", return_value={}), \
             patch.object(generator, "compute_signature", return_value=fresh):
            result = generator.build_master_pokedex("previous.json")
        self.assertEqual(fresh, result["entries"][0]["signature"])

    def test_invalid_and_duplicate_previous_signatures_are_not_reused(self):
        invalid = copy.deepcopy(self.signature)
        invalid["edge"] = [0.5]
        entries = {"entries": [
            {"spriteKey": "invalid", "signature": invalid},
            {"spriteKey": "duplicate", "signature": self.signature},
            {"spriteKey": "duplicate", "signature": self.signature},
            {"spriteKey": "mixed", "signature": self.signature},
            {"spriteKey": "mixed", "signature": invalid},
        ]}
        with patch.object(generator.os.path, "exists", return_value=True), patch.object(generator, "load_json", return_value=entries):
            self.assertEqual({}, generator.build_previous_signature_index("previous.json"))


if __name__ == "__main__":
    unittest.main()
