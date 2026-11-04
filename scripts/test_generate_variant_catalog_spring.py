"""Synthetic generation regressions for Spring event costume keys."""
import unittest

from generate_variant_catalog import determine_variant_class, looks_like_costume_variant


class SpringCostumeVariantGeneratorTest(unittest.TestCase):
    def test_2024_spring_crown_is_costume_for_evolved_whimsicott(self):
        self.assertTrue(looks_like_costume_variant("SPRING_2024"))
        self.assertEqual(
            "costume",
            determine_variant_class("Whimsicott", "00", "SPRING_2024", set(), {}, set()),
        )

    def test_spring_year_token_does_not_make_generic_form_a_costume(self):
        self.assertFalse(looks_like_costume_variant("SPRING_FORM"))
        self.assertEqual(
            "form",
            determine_variant_class("Whimsicott", "00", "SPRING_FORM", set(), {}, set()),
        )

    def test_base_and_explicit_special_form_semantics_remain_unchanged(self):
        self.assertEqual(
            "base",
            determine_variant_class("Whimsicott", "00", None, set(), {}, set()),
        )
        self.assertEqual(
            "form",
            determine_variant_class("Giratina", "00", "ALTERED", set(), {}, set()),
        )


if __name__ == "__main__":
    unittest.main()
