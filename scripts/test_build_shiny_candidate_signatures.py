"""Offline tests for private, source-hash-verified whole-species icon candidates."""

import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from PIL import Image

from build_shiny_candidate_signatures import (
    candidate_form_token,
    compile_candidates,
    read_verified_index,
    verify_pair,
)
from download_visual_reference_assets import Asset, MANIFEST_NAME, PAIR_INDEX_NAME


def git_blob(data: bytes) -> str:
    return hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()


class WholeSpeciesCandidateTests(unittest.TestCase):
    def create_case(self, root: Path) -> tuple[Path, Path, dict]:
        archive = root / "external"
        archive.mkdir()
        paths = [
            "Images/Pokemon - 256x256/pokemon_icon_422_11.png",
            "Images/Pokemon - 256x256/pokemon_icon_422_11_shiny.png",
            "Images/Pokemon - 256x256/pm928.icon.png",
            "Images/Pokemon - 256x256/pm928.s.icon.png",
        ]
        assets = []
        for index, rel in enumerate(paths):
            location = archive / rel
            location.parent.mkdir(parents=True, exist_ok=True)
            Image.new("RGBA", (12, 12), (30 + index * 40, 60, 80, 255)).save(location)
            data = location.read_bytes()
            assets.append(Asset(rel, git_blob(data), len(data), "icons"))

        commit = "c" * 40
        pairs = [
            {
                "normalPath": paths[0], "normalBlobSha": assets[0].sha,
                "shinyPath": paths[1], "shinyBlobSha": assets[1].sha,
                "dexNumberHint": 422, "filenamePairRule": "shiny_suffix",
                "identityStatus": "unverified_filename_candidate",
            },
            {
                "normalPath": paths[2], "normalBlobSha": assets[2].sha,
                "shinyPath": paths[3], "shinyBlobSha": assets[3].sha,
                "dexNumberHint": 928, "filenamePairRule": "addressable_s_icon",
                "identityStatus": "unverified_filename_candidate",
            },
        ]
        manifest = {
            "version": 1, "sourceRepo": "PokeMiners/pogo_assets",
            "sourceCommit": commit, "scope": "icons", "limited": False,
            "assets": [asset.__dict__ for asset in assets],
        }
        index = {
            "version": 1, "sourceRepo": "PokeMiners/pogo_assets",
            "sourceCommit": commit, "assetScope": "icons", "verifiedAssetCount": 4,
            "byteStatus": "source_git_blobs_verified_locally",
            "identityStatus": "unverified_filename_candidates_only",
            "pairCount": 2, "pairs": pairs,
        }
        (archive / MANIFEST_NAME).write_text(json.dumps(manifest))
        (archive / PAIR_INDEX_NAME).write_text(json.dumps(index))
        pins = {
            "pogoMinersAssets": {
                "repository": "https://github.com/PokeMiners/pogo_assets",
                "observedCommit": commit,
                "pokemon2dTreeSha": "a" * 40,
                "pokemon3dTreeSha": "b" * 40,
            }
        }
        pins_path = root / "pins.json"
        pins_path.write_text(json.dumps(pins))
        return archive, pins_path, index

    def test_build_all_pairs_with_form_hints_not_runtime_truth(self):
        with TemporaryDirectory() as folder:
            root = Path(folder)
            archive, pins, _ = self.create_case(root)
            index, assets = read_verified_index(archive, pins)
            names = ["Unknown"] * 1025
            names[421] = "Shellos"
            names[927] = "Smoliv"
            payload = compile_candidates(archive, names, index, assets)
            self.assertEqual(2, payload["summary"]["hash_verified_pairs"])
            self.assertEqual(2, payload["summary"]["candidate_pairs_with_dex_hint"])
            self.assertEqual("unverified_candidates_not_training_truth", payload["identityStatus"])
            self.assertEqual("11", payload["candidates"][0]["formIdHint"])
            self.assertIsNone(payload["candidates"][1]["formIdHint"])
            self.assertEqual("Smoliv", payload["candidates"][1]["speciesNameHint"])
            self.assertIn("normalFeatures", payload["candidates"][0])
            self.assertEqual(12, len(payload["candidates"][0]["normalFeatures"]["color"]))

    def test_tampered_icon_byte_must_fail_not_be_used_as_truth(self):
        with TemporaryDirectory() as folder:
            root = Path(folder)
            archive, pins, _ = self.create_case(root)
            index, assets = read_verified_index(archive, pins)
            normal = archive / index["pairs"][0]["normalPath"]
            normal.write_bytes(b"modified")
            with self.assertRaisesRegex(ValueError, "pinned Git blob"):
                verify_pair(index["pairs"][0], assets, archive)

    def test_reject_stale_index_and_filename_only_labels(self):
        with TemporaryDirectory() as folder:
            root = Path(folder)
            archive, pins, index = self.create_case(root)
            index["verifiedAssetCount"] = 3
            (archive / PAIR_INDEX_NAME).write_text(json.dumps(index))
            with self.assertRaisesRegex(ValueError, "stale"):
                read_verified_index(archive, pins)
            self.assertEqual("12", candidate_form_token("pokemon_icon_422_12_shiny.png"))
            self.assertIsNone(candidate_form_token("pm928.icon.png"))


if __name__ == "__main__":
    unittest.main()
