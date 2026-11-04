import json
import hashlib
import unittest
from pathlib import Path

from train_variant_prototypes import (
    looks_like_costume_variant,
    normalize_variant_token,
    parse_sprite_filename,
)


class VariantPrototypeMetadataTest(unittest.TestCase):
    def test_whimsicott_spring_costume_agrees_across_bundled_assets(self):
        repository = Path(__file__).resolve().parents[1]
        data = repository / "app/src/main/assets/data"
        manifest = json.loads((repository / "metadata_manifest.json").read_text(encoding="utf-8"))
        names = ("variant_classifier_model.json", "variant_catalog.json",
                 "authoritative_variant_db.json", "master_pokedex.json")
        keys = ("547_00_SPRING_2024", "547_00_SPRING_2024_shiny")
        for name in names:
            with self.subTest(asset=name):
                raw = (data / name).read_bytes()
                payload = json.loads(raw)
                entries = {entry["spriteKey"]: entry for entry in payload["entries"]
                           if entry.get("species") == "Whimsicott" and "SPRING_2024" in entry.get("spriteKey", "")}
                self.assertEqual(set(keys), set(entries))
                for index, key in enumerate(keys):
                    entry = entries[key]
                    self.assertTrue(entry["isCostumeLike"])
                    self.assertEqual(bool(index), entry["isShiny"])
                    self.assertEqual("costume", entry.get("variantType", entry.get("variantClass")))
                    if name == "variant_classifier_model.json":
                        self.assertEqual("SPRING_2024", entry["variantId"])
                        self.assertTrue(entry["prototype"])
                if name in manifest["files"]:
                    self.assertEqual(hashlib.sha256(raw).hexdigest(), manifest["files"][name]["sha256"])

    def test_production_model_source_is_portable(self):
        repository = Path(__file__).resolve().parents[1]
        path = repository / "app/src/main/assets/data/variant_classifier_model.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual("external_sprite_assets", payload["source"])

    def test_spring_costume_marker_is_normalized_and_classified_without_catalog(self):
        metadata = parse_sprite_filename(
            "pm546.cSPRING_2024.icon.png",
            {546: "Cottonee"},
            {},
        )

        self.assertEqual("SPRING_2024", normalize_variant_token("CSPRING_2024"))
        self.assertEqual("546_00_SPRING_2024", metadata["spriteKey"])
        self.assertEqual("SPRING_2024", metadata["variantId"])
        self.assertTrue(metadata["isCostumeLike"])
        self.assertEqual("costume", metadata["variantType"])
        self.assertFalse(looks_like_costume_variant("SPRING_FORM"))

    def test_shiny_spring_costume_keeps_both_costume_and_shiny_attributes(self):
        metadata = parse_sprite_filename(
            "pm546.cSPRING_2024.s.icon.png",
            {546: "Cottonee"},
            {},
        )

        self.assertEqual("546_00_SPRING_2024_shiny", metadata["spriteKey"])
        self.assertTrue(metadata["isShiny"])
        self.assertTrue(metadata["isCostumeLike"])
        self.assertEqual("costume", metadata["variantType"])

    def test_checked_in_spring_prototypes_use_canonical_costume_metadata(self):
        repository = Path(__file__).resolve().parents[1]
        model_paths = (
            repository / "app/src/main/assets/data/variant_classifier_model.json",
            repository / "app/src/androidTest/assets/data/variant_classifier_model.json",
        )

        for model_path in model_paths:
            with self.subTest(model=model_path.relative_to(repository)):
                payload = json.loads(model_path.read_text(encoding="utf-8"))
                entries = {entry["filename"]: entry for entry in payload["entries"]}
                normal = entries["pm546.cSPRING_2024.icon.png"]
                shiny = entries["pm546.cSPRING_2024.s.icon.png"]

                self.assertEqual("546_00_SPRING_2024", normal["spriteKey"])
                self.assertEqual("costume", normal["variantType"])
                self.assertTrue(normal["isCostumeLike"])
                self.assertEqual("546_00_SPRING_2024_shiny", shiny["spriteKey"])
                self.assertEqual("costume", shiny["variantType"])
                self.assertTrue(shiny["isCostumeLike"])
                self.assertTrue(shiny["isShiny"])


if __name__ == "__main__":
    unittest.main()
