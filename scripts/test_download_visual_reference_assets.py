"""Offline tests for pinned private source-asset downloads."""

import hashlib
import io
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from download_visual_reference_assets import (
    Asset,
    asset_summary,
    candidate_icon_pairs,
    prioritized_assets,
    remaining_download_bytes,
    run_download,
    download_one,
    file_hashes,
    raw_url,
    read_pins,
    safe_relative_path,
    tree_assets,
    verified_local,
    verify_offline,
    write_json,
    MANIFEST_NAME,
    PAIR_INDEX_NAME,
)


def blob_sha(payload: bytes) -> str:
    return hashlib.sha1(f"blob {len(payload)}\0".encode() + payload).hexdigest()


class WholeArchiveTransferTests(unittest.TestCase):
    def test_all_source_kinds_prioritize_icon_coverage_before_3d(self):
        assets = [
            Asset("3D Assets/Pokemon/z.obj", "a" * 40, 1, "models"),
            Asset("3D Assets/Pokemon/x.png", "b" * 40, 1, "textures"),
            Asset("Images/Pokemon - 256x256/pokemon_icon_001_00.png", "c" * 40, 1, "icons"),
        ]
        ordered = prioritized_assets(assets)
        self.assertEqual(["icons", "textures", "models"], [asset.kind for asset in ordered])
        self.assertEqual(3, len(ordered))

    def test_resume_reserves_only_nonverified_bytes(self):
        normal = b"verified icon"
        existing = Asset(
            "Images/Pokemon - 256x256/pm1.icon.png", blob_sha(normal), len(normal), "icons"
        )
        missing = Asset("3D Assets/Pokemon/missing.obj", "a" * 40, 100, "models")
        with TemporaryDirectory() as folder:
            output = Path(folder)
            path = output / existing.path
            path.parent.mkdir(parents=True)
            path.write_bytes(normal)
            self.assertEqual(100, remaining_download_bytes([existing, missing], output))
            path.write_bytes(b"invalid bytes")
            self.assertEqual(100 + len(normal), remaining_download_bytes([existing, missing], output))

    def test_origin_failure_aborts_before_bulk_parallel_requests(self):
        assets = [
            Asset("3D Assets/Pokemon/z.obj", "a" * 40, 100, "models"),
            Asset("Images/Pokemon - 256x256/pm1.icon.png", "b" * 40, 100, "icons"),
        ]
        with TemporaryDirectory() as folder:
            output = Path(folder)
            with patch(
                "download_visual_reference_assets.download_one",
                side_effect=ConnectionError("network origin denied"),
            ) as downloader:
                status = run_download("c" * 40, assets, output, workers=4, replace_invalid=False)
            self.assertEqual(1, status)
            self.assertEqual(1, downloader.call_count)
            self.assertEqual("icons", downloader.call_args.args[0].kind)
            summary = json.loads((output / ".pinned_visual_assets_summary.json").read_text())
            self.assertEqual(1, summary["transfer"]["failed"])
            self.assertEqual(0, summary["transfer"]["downloaded"])


class AssetInventoryTests(unittest.TestCase):
    def test_safe_paths(self):
        for candidate in (
            "../x.png", "ok/../../x.png", "/absolute", "C:/x.png", "a\\b.png",
            "x//y.png", "CON.png", "a/.png", "file?.png", "folder. /x.png",
        ):
            with self.subTest(candidate=candidate), self.assertRaises(ValueError):
                safe_relative_path(candidate)
        self.assertEqual("Pokemon/image shiny.png", safe_relative_path("Pokemon/image shiny.png"))

    def test_exact_tree_sha_and_not_truncated(self):
        sha = "a" * 40
        sample = {
            "path": "pokemon_icon_422_11_shiny.png", "type": "blob",
            "mode": "100644", "size": 10, "sha": "b" * 40,
        }
        payload = {"sha": sha, "truncated": False, "tree": [sample]}
        rows = tree_assets(payload, sha, "Images/Pokemon - 256x256")
        self.assertEqual(
            ["Images/Pokemon - 256x256/pokemon_icon_422_11_shiny.png"],
            [asset.path for asset in rows],
        )
        self.assertEqual("icons", rows[0].kind)
        with self.assertRaises(ValueError):
            tree_assets(dict(payload, truncated=True), sha, "Images/Pokemon - 256x256")
        with self.assertRaises(ValueError):
            tree_assets(payload, "c" * 40, "Images/Pokemon - 256x256")

    def test_rejects_unsafe_entry_and_duplicate(self):
        sha = "a" * 40
        sample = {
            "path": "safe.png", "type": "blob", "mode": "100644",
            "size": 10, "sha": "b" * 40,
        }
        with self.assertRaises(ValueError):
            tree_assets({"sha": sha, "truncated": False, "tree": [sample, sample]},
                        sha, "Images/Pokemon - 256x256")
        with self.assertRaises(ValueError):
            tree_assets({
                "sha": sha, "truncated": False,
                "tree": [dict(sample, path="../out.png")],
            }, sha, "Images/Pokemon - 256x256")
        with self.assertRaises(ValueError):
            tree_assets({
                "sha": sha, "truncated": False,
                "tree": [dict(sample, mode="120000")],
            }, sha, "Images/Pokemon - 256x256")

    def test_models_and_textures_are_not_confused(self):
        sha = "a" * 40
        rows = [
            {"path": "rig/foo.fbx", "type": "blob", "mode": "100644",
             "size": 20, "sha": "b" * 40},
            {"path": "rig/foo.png", "type": "blob", "mode": "100644",
             "size": 30, "sha": "c" * 40},
            {"path": "readme.md", "type": "blob", "mode": "100644",
             "size": 5, "sha": "d" * 40},
        ]
        result = tree_assets({"sha": sha, "truncated": False, "tree": rows},
                             sha, "3D Assets/Pokemon")
        self.assertEqual(["models", "textures"], [asset.kind for asset in result])

    def test_pinned_source_must_be_exact(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "pins.json"
            pins = {"pogoMinersAssets": {
                "repository": "https://github.com/PokeMiners/pogo_assets",
                "observedCommit": "a" * 40, "pokemon2dTreeSha": "b" * 40,
                "pokemon3dTreeSha": "c" * 40,
            }}
            path.write_text(json.dumps(pins))
            commit, roots = read_pins(path)
            self.assertEqual("a" * 40, commit)
            self.assertEqual("b" * 40, roots["icons"][1])
            pins["pogoMinersAssets"]["repository"] = "https://github.com/other/other"
            path.write_text(json.dumps(pins))
            with self.assertRaises(ValueError):
                read_pins(path)

    def test_pair_inventory_does_not_claim_full_coverage(self):
        assets = [
            Asset("Images/Pokemon - 256x256/pokemon_icon_422_11.png",
                  "a" * 40, 1, "icons"),
            Asset("Images/Pokemon - 256x256/pokemon_icon_422_11_shiny.png",
                  "b" * 40, 1, "icons"),
            Asset("Images/Pokemon - 256x256/pokemon_icon_422_12.png",
                  "c" * 40, 1, "icons"),
            Asset("Images/Pokemon - 256x256/Addressable Assets/pm928.icon.png",
                  "d" * 40, 1, "icons"),
            Asset("Images/Pokemon - 256x256/Addressable Assets/pm928.s.icon.png",
                  "e" * 40, 1, "icons"),
        ]
        summary = asset_summary(assets)
        self.assertEqual(2, summary["candidateNormalShinyIconPairs"])
        self.assertEqual(2, len(summary["smolivIconPaths"]))
        self.assertEqual(5, summary["count"])


    def test_filename_pairs_only_derive_unverified_identity_hints(self):
        assets = [
            Asset("Images/Pokemon - 256x256/pokemon_icon_422_11.png", "a" * 40, 10, "icons"),
            Asset("Images/Pokemon - 256x256/pokemon_icon_422_11_shiny.png", "b" * 40, 10, "icons"),
            Asset("Images/Pokemon - 256x256/pokemon_icon_422_12.png", "c" * 40, 10, "icons"),
            Asset("Images/Pokemon - 256x256/Addressable Assets/pm928.icon.png", "d" * 40, 10, "icons"),
            Asset("Images/Pokemon - 256x256/Addressable Assets/pm928.s.icon.png", "e" * 40, 10, "icons"),
        ]
        pairs = candidate_icon_pairs(assets)
        self.assertEqual(2, len(pairs))
        self.assertEqual([928, 422], sorted(p["dexNumberHint"] for p in pairs)[::-1])
        self.assertTrue(all(p["identityStatus"] == "unverified_filename_candidate" for p in pairs))
        self.assertTrue(all(p["normalBlobSha"] and p["shinyBlobSha"] for p in pairs))
        self.assertFalse(any("422_12" in p["shinyPath"] for p in pairs))


class SourceTransferTests(unittest.TestCase):
    def test_raw_url_is_pinned_and_encodes_spaces(self):
        asset = Asset("Images/Pokemon - 256x256/a.png", "b" * 40, 8, "icons")
        self.assertEqual(
            "https://raw.githubusercontent.com/PokeMiners/pogo_assets/"
            + "a" * 40 + "/Images/Pokemon%20-%20256x256/a.png",
            raw_url("a" * 40, asset),
        )

    def test_exact_blob_hash_and_cached_file(self):
        with TemporaryDirectory() as directory:
            output = Path(directory)
            payload = b"\x89PNG\r\n\x1a\n" + b"fake-image-for-test"
            asset = Asset("Images/Pokemon - 256x256/test.png",
                          blob_sha(payload), len(payload), "icons")

            class Response:
                def __enter__(self):
                    self.reader = io.BytesIO(payload)
                    return self.reader

                def __exit__(self, *_):
                    self.reader.close()

            with patch("download_visual_reference_assets.urlopen", return_value=Response()) as fetch:
                self.assertEqual("downloaded", download_one(asset, "a" * 40, output))
                self.assertEqual("existing_verified", download_one(asset, "a" * 40, output))
                self.assertEqual(1, fetch.call_count)
            self.assertTrue(verified_local(output / asset.path, asset))

    def test_corrupt_source_not_published(self):
        with TemporaryDirectory() as directory:
            asset = Asset("Images/Pokemon - 256x256/test.png",
                          "a" * 40, 10, "icons")

            class Response:
                def __enter__(self):
                    return io.BytesIO(b"\x89PNG\r\n\x1a\nAA")

                def __exit__(self, *_):
                    return None

            with patch("download_visual_reference_assets.urlopen", return_value=Response()):
                with self.assertRaises(ValueError):
                    download_one(asset, "b" * 40, Path(directory))
            self.assertFalse((Path(directory) / asset.path).exists())
            self.assertFalse((Path(directory) / (asset.path + ".partial")).exists())

    def test_existing_mismatch_preserved(self):
        with TemporaryDirectory() as directory:
            asset = Asset("Images/Pokemon - 256x256/test.png",
                          "a" * 40, 5, "icons")
            path = Path(directory) / asset.path
            path.parent.mkdir(parents=True)
            path.write_bytes(b"local")
            with self.assertRaises(ValueError):
                download_one(asset, "b" * 40, Path(directory))
            self.assertEqual(b"local", path.read_bytes())

    def test_verify_offline_reports_missing_file(self):
        with TemporaryDirectory() as directory:
            output = Path(directory)
            payload = b"test"
            good = Asset("Images/a.png", blob_sha(payload), 4, "icons")
            missing = Asset("Images/b.png", "b" * 40, 4, "icons")
            (output / good.path).parent.mkdir(parents=True)
            (output / good.path).write_bytes(payload)
            write_json(output / MANIFEST_NAME, {
                "assets": [good.__dict__, missing.__dict__],
            })
            self.assertEqual(1, verify_offline(output))
            self.assertTrue(file_hashes(output / good.path, 4))


    def test_verified_pair_index_written_only_after_all_files_match(self):
        with TemporaryDirectory() as directory:
            output = Path(directory)
            normal = bytes.fromhex("89504e470d0a1a0a") + b"normal"
            shiny = bytes.fromhex("89504e470d0a1a0a") + b"shiny"
            assets = [
                Asset("Images/Pokemon - 256x256/Addressable Assets/pm928.icon.png",
                      blob_sha(normal), len(normal), "icons"),
                Asset("Images/Pokemon - 256x256/Addressable Assets/pm928.s.icon.png",
                      blob_sha(shiny), len(shiny), "icons"),
            ]
            for asset, payload in zip(assets, (normal, shiny)):
                file_path = output / asset.path
                file_path.parent.mkdir(parents=True, exist_ok=True)
                file_path.write_bytes(payload)
            write_json(output / MANIFEST_NAME, {
                "sourceRepo": "PokeMiners/pogo_assets",
                "sourceCommit": "a" * 40,
                "scope": "icons",
                "assets": [asset.__dict__ for asset in assets],
            })
            self.assertEqual(0, verify_offline(output))
            index = json.loads((output / PAIR_INDEX_NAME).read_text(encoding="utf-8"))
            self.assertEqual(2, index["verifiedAssetCount"])
            self.assertEqual(1, index["pairCount"])
            self.assertEqual(928, index["pairs"][0]["dexNumberHint"])
            self.assertEqual("unverified_filename_candidates_only", index["identityStatus"])
            # A successful --limit smoke test is not a complete training inventory.
            limited = json.loads((output / MANIFEST_NAME).read_text(encoding="utf-8"))
            limited["limited"] = True
            write_json(output / MANIFEST_NAME, limited)
            self.assertEqual(0, verify_offline(output))
            self.assertFalse((output / PAIR_INDEX_NAME).exists())
            limited["limited"] = False
            write_json(output / MANIFEST_NAME, limited)
            self.assertEqual(0, verify_offline(output))
            # Corrupting one source invalidates the previously generated index.
            (output / assets[1].path).write_bytes(b"changed")
            self.assertEqual(1, verify_offline(output))
            self.assertFalse((output / PAIR_INDEX_NAME).exists())


if __name__ == "__main__":
    unittest.main()
