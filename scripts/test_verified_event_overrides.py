import copy
import hashlib
import json
import unittest
from collections import Counter
from pathlib import Path
from generate_global_rarity_legacy_db import build_bulbapedia_override_snapshots

from verified_event_overrides import (
    apply_authoritative_override,
    apply_catalog_override,
    apply_legacy_override,
    load_verified_event_overrides,
)

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "app/src/main/assets/data"
OVERRIDES = ROOT / "scripts/bulbapedia_event_overrides.json"
KEYS = ("546_00_SPRING_2024", "546_00_SPRING_2024_shiny",
        "547_00_SPRING_2024", "547_00_SPRING_2024_shiny")


class VerifiedCrownEventTest(unittest.TestCase):
    def setUp(self):
        self.overrides = load_verified_event_overrides(OVERRIDES)

    def test_event_dates_do_not_create_unverified_2026_costume_association(self):
        payload = json.loads(OVERRIDES.read_text(encoding="utf-8"))
        windows = {x["eventLabel"]: x for x in payload["verifiedEventWindows"]}
        self.assertEqual("2024-03-25", windows["Verdant Wonders"]["endDate"])
        self.assertEqual("2026-05-18", windows["Spring Marathon"]["endDate"])
        self.assertEqual(set(KEYS), set(self.overrides))
        self.assertTrue(all(x["eventLabel"] == "Verdant Wonders" for x in self.overrides.values()))

    def test_exact_override_removes_only_unverified_appearance(self):
        entry = {"spriteKey": KEYS[0], "historicalEvents": [
            {"eventLabel": "Other", "startDate": "2020-01-01", "endDate": "2020-01-02"},
            {"eventLabel": "Verdant Wonders", "startDate": "2024-03-21", "endDate": None},
            {"eventLabel": "Spring Marathon", "startDate": "2026-05-12", "endDate": None},
        ], "aliases": ["Verdant Wonders", "Existing"], "sourceIds": []}
        apply_authoritative_override(entry, self.overrides[KEYS[0]])
        self.assertEqual(["Other", "Verdant Wonders"], [x["eventLabel"] for x in entry["historicalEvents"]])
        self.assertEqual("2024-03-25", entry["historicalEvents"][-1]["endDate"])
        self.assertIn("pokemongolive:news", entry["sourceIds"])
        self.assertNotIn("Verdant Wonders", entry["aliases"])
        self.assertIn("Existing", entry["aliases"])
        unchanged = copy.deepcopy(entry)
        apply_authoritative_override(entry, None)
        self.assertEqual(unchanged, entry)

    def test_catalog_and_legacy_preserve_uncertain_current_availability(self):
        catalog = {"spriteKey": KEYS[0], "eventTags": ["Spring Marathon"]}
        apply_catalog_override(catalog, self.overrides[KEYS[0]])
        self.assertEqual(["Verdant Wonders"], catalog["eventTags"])
        legacy = {"spriteKey": KEYS[0], "aliases": [], "sourceIds": [], "sourceNames": [], "liveAvailability": "retired"}
        apply_legacy_override(legacy, self.overrides[KEYS[0]])
        self.assertEqual("unknown", legacy["liveAvailability"])
        self.assertEqual("2024-03-25", legacy["lastSeen"])
        snapshot = build_bulbapedia_override_snapshots()[KEYS[0]]
        self.assertEqual("2024-03-25", snapshot["lastSeen"])
        self.assertIn("pokemongolive:news", snapshot["sourceIds"])
        self.assertNotIn("snapshot_adapter:bulbapedia_event_overrides", snapshot["sourceIds"])

    def test_override_cannot_cross_sprite_identity(self):
        with self.assertRaises(ValueError):
            apply_catalog_override({"spriteKey": KEYS[2]}, self.overrides[KEYS[0]])

    def test_captured_fixture_manifest_preserves_source_and_hashes_current_subsets(self):
        root = ROOT / "app/src/androidTest/assets"
        manifest = json.loads((root / "data/qa_exact_frame_reference_manifest.json").read_bytes())
        self.assertEqual("2d5911fc7dbb1e41cf76e147880b8fd8e3541a5e", manifest["captureSourceSha"])
        self.assertEqual("127df8f751688c20aa42618aadb238ad7c0c21f33d4768e6c1bdcd29dd4458d8", manifest["metadataRevisionSha256"])
        self.assertIn("metadata updated", manifest["fixtureTransformation"])
        expected_sources = {
            "shiny_signatures": "336c412b63ec7a09a3110e7b9be0bac0ea588e6ead848638f3c2e9e147488ca2",
            "master_pokedex": "895405cfd36c23cceb6fbaa8f4f7efa88b9283f88bed7d98137d569019d907b0",
            "variant_catalog": "740b755418cc415046546f8526c5c03168246b4621303b2b0b3c691187fbdaf4",
            "variant_prototypes": "acaee0bfe0599d77b553408952e501571b6c93c30cc08b407cec7db6deee4c1b",
        }
        for asset in manifest["assets"]:
            self.assertEqual(expected_sources[asset["id"]], asset["sourceApkAssetSha256"])
            self.assertEqual(hashlib.sha256((root / asset["path"]).read_bytes()).hexdigest(), asset["fixtureSha256"])

    def test_source_summaries_match_applied_overrides_and_legacy_entries(self):
        overrides = json.loads(OVERRIDES.read_text(encoding="utf-8"))["entries"]
        authoritative = json.loads((DATA / "authoritative_variant_db.json").read_bytes())
        self.assertEqual(len(overrides), authoritative["sourceSummary"]["external_overrides"])
        legacy = json.loads((DATA / "global_rarity_legacy_db.json").read_bytes())
        observed = Counter(source for entry in legacy["entries"] for source in entry.get("sourceIds", []))
        self.assertEqual(dict(observed), legacy["sourceSummary"])

    def test_generated_assets_and_fixture_copies_agree(self):
        names = ("variant_classifier_model.json", "variant_catalog.json", "authoritative_variant_db.json",
                 "master_pokedex.json", "global_rarity_legacy_db.json")
        manifest = json.loads((ROOT / "metadata_manifest.json").read_text(encoding="utf-8"))
        originals = {}
        for name in names:
            with self.subTest(asset=name):
                raw = (DATA / name).read_bytes()
                entries = {x["spriteKey"]: x for x in json.loads(raw)["entries"] if x.get("spriteKey") in KEYS}
                self.assertEqual(set(KEYS), set(entries))
                originals[name] = entries
                if name in manifest["files"]:
                    self.assertEqual(hashlib.sha256(raw).hexdigest(), manifest["files"][name]["sha256"])
                for key, entry in entries.items():
                    self.assertTrue(entry["isCostumeLike"])
                    self.assertEqual(key.endswith("_shiny"), entry["isShiny"])
                    if name in ("variant_classifier_model.json", "variant_catalog.json"):
                        self.assertIn("Verdant Wonders", entry["eventTags"])
                        self.assertEqual("2024-03-25", entry["releaseWindow"]["lastSeen"])
                    else:
                        self.assertEqual("Verdant Wonders", entry["eventLabel"])
                        self.assertEqual("2024-03-25", entry["eventEnd"])
                        if "historicalEvents" in entry:
                            self.assertFalse(any(x.get("eventLabel") == "Spring Marathon" for x in entry["historicalEvents"]))
                        if name == "global_rarity_legacy_db.json":
                            self.assertEqual("unknown", entry["liveAvailability"])
        for name, fields in {
            "variant_classifier_model.json": ("eventTags", "releaseWindow", "prototype"),
            "variant_catalog.json": ("eventTags", "releaseWindow", "variantLabel"),
            "master_pokedex.json": ("eventLabel", "eventStart", "eventEnd", "historicalEvents", "signature"),
        }.items():
            fixture = json.loads((ROOT / "app/src/androidTest/assets/data" / name).read_bytes())
            entries = {x["spriteKey"]: x for x in fixture["entries"] if x.get("spriteKey") in KEYS}
            self.assertEqual(set(KEYS[:2]), set(entries))
            for key, entry in entries.items():
                for field in fields:
                    self.assertEqual(originals[name][key][field], entry[field], (name, key, field))


if __name__ == "__main__":
    unittest.main()
