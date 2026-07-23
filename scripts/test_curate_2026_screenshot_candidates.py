import copy
import hashlib
import json
import re
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from PIL import Image, ImageDraw

from curate_2026_screenshot_candidates import (
    build_manifest,
    canonical_json_bytes,
    ensure_output_outside_source,
    validate_manifest,
    write_manifest,
)


DEVELOPMENT_COUNT = 100
HOLDOUT_COUNT = 20
RECORD_COUNT = DEVELOPMENT_COUNT + HOLDOUT_COUNT
BOOLEAN_FIELDS = (
    "overlayPresent",
    "likelyDetailsScreen",
    "likelyCpPresent",
    "likelyHpPresent",
    "likelyNamePresent",
    "likelyCandyFamilyPresent",
)
DUPLICATE_DECISIONS = {
    "REDUNDANT_NEAR_IDENTICAL",
    "PRESERVE_SCROLL_VARIANT",
    "PRESERVE_STATE_VARIANT",
    "PRESERVE_LAYOUT_VARIANT",
    "FALSE_POSITIVE_SIMILARITY",
    "NEEDS_HUMAN_REVIEW",
}
NEAR_DUPLICATE_METHOD = {
    "imageLibrary": "Pillow",
    "colorMode": "L",
    "pHash": {"resize": "32x32", "dctSize": 8, "median": "excluding_dc", "maxHammingDistance": 8},
    "dHash": {"resize": "9x8", "maxHammingDistance": 8},
    "thumbnail": {"resize": "36x78", "maxMae": 1.0, "minCorrelation": 0.999},
}


class Curate2026ScreenshotCandidatesTest(unittest.TestCase):
    def test_build_is_deterministic_canonical_and_leaves_source_untouched(self):
        with TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            source_root = temporary_root / "source"
            self._write_synthetic_source(source_root)
            before = self._source_snapshot(source_root)

            with self.assertRaisesRegex(ValueError, "Unrecognized source corpus digest"):
                build_manifest(source_root)
            first = build_manifest(source_root, allow_test_corpus=True)
            second = build_manifest(source_root, allow_test_corpus=True)
            output = temporary_root / "candidate_manifest.json"
            ensure_output_outside_source(source_root, output)
            with self.assertRaisesRegex(ValueError, "outside the source corpus"):
                ensure_output_outside_source(source_root, source_root / "candidate_manifest.json")
            write_manifest(first, output)
            first_bytes = output.read_bytes()
            write_manifest(second, output)

            self.assertEqual(before, self._source_snapshot(source_root))
            self.assertEqual(first, second)
            self.assertEqual(first_bytes, output.read_bytes())
            self.assertEqual(first_bytes, canonical_json_bytes(first))
            self.assertTrue(first_bytes.endswith(b"\n"))
            self.assertFalse(first_bytes.endswith(b"\n\n"))
            self.assertNotIn(b"\r", first_bytes)
            self.assertEqual(
                json.dumps(first, ensure_ascii=False, indent=2, sort_keys=True).encode("utf-8") + b"\n",
                first_bytes,
            )

            self.assertEqual(RECORD_COUNT, len(first["records"]))
            self.assertEqual(RECORD_COUNT, first["sourceFileCount"])
            self.assertEqual(sum(item[1] for item in before.values()), first["sourceAggregateBytes"])
            self.assertRegex(first["sourceDigestSha256"], r"^[0-9a-f]{64}$")
            self.assertFalse(first["truthLabelsPresent"])
            self.assertTrue(first["prospectiveHoldoutQuarantined"])
            self.assertEqual(NEAR_DUPLICATE_METHOD, first["nearDuplicateMethod"])
            self.assertEqual(self._expected_ids(), [record["id"] for record in first["records"]])
            self.assertEqual(
                ["development_candidate"] * DEVELOPMENT_COUNT
                + ["prospective_holdout_candidate"] * HOLDOUT_COUNT,
                [record["lane"] for record in first["records"]],
            )
            self._assert_record_contract(first)
            self._assert_cluster_contract(first)
            manifest_text = first_bytes.decode("utf-8")
            for source_name in before:
                self.assertNotIn(source_name, manifest_text)

    def test_validation_rejects_duplicate_sha_invalid_lane_and_bad_clusters(self):
        manifest = self._valid_manifest()
        self.assertIsNone(validate_manifest(manifest))

        cases = {}
        duplicate_sha = copy.deepcopy(manifest)
        duplicate_sha["records"][-1]["sha256"] = duplicate_sha["records"][0]["sha256"]
        cases["duplicate SHA-256"] = duplicate_sha

        invalid_lane = copy.deepcopy(manifest)
        invalid_lane["records"][0]["lane"] = "approved_holdout"
        cases["invalid lane"] = invalid_lane

        dangling_cluster = copy.deepcopy(manifest)
        dangling_cluster["records"][0]["nearDuplicateClusterId"] = "near_001"
        cases["dangling cluster reference"] = dangling_cluster

        cross_lane_cluster = copy.deepcopy(manifest)
        member_ids = ["s25_2026_dev_001", "s25_2026_holdout_001"]
        cross_lane_cluster["nearDuplicateClusters"] = [{
            "id": "near_001",
            "selectedMemberIds": member_ids,
            "sourceMemberCount": 2,
            "decisionReason": "PRESERVE_STATE_VARIANT",
        }]
        cross_lane_cluster["records"][0]["nearDuplicateClusterId"] = "near_001"
        cross_lane_cluster["records"][0]["duplicateDecisionReason"] = "PRESERVE_STATE_VARIANT"
        cross_lane_cluster["records"][DEVELOPMENT_COUNT]["nearDuplicateClusterId"] = "near_001"
        cross_lane_cluster["records"][DEVELOPMENT_COUNT]["duplicateDecisionReason"] = "PRESERVE_STATE_VARIANT"
        cases["cluster crossing lanes"] = cross_lane_cluster

        for description, candidate in cases.items():
            with self.subTest(description=description):
                with self.assertRaises(ValueError):
                    validate_manifest(candidate)

    def test_validation_rejects_forbidden_raw_fields_and_values(self):
        manifest = self._valid_manifest()
        forbidden_fields = {
            "canonicalSpecies": "synthetic_value",
            "rawOcrText": "synthetic_text",
            "sourcePath": "private/source.png",
            "fileName": "source.png",
            "accountId": "private-account",
            "deviceSerial": "private-serial",
            "adbEndpoint": "synthetic_endpoint",
            "networkIdentifier": "private-network",
            "authToken": "private-token",
            "telemetryPayload": "private-payload",
        }
        for key, value in forbidden_fields.items():
            with self.subTest(key=key):
                candidate = copy.deepcopy(manifest)
                candidate["records"][0][key] = value
                with self.assertRaises(ValueError):
                    validate_manifest(candidate)

        for value in (
            "C:" + "\\Users\\example\\source.png",
            "/" + "home/example/source.png",
            "adb " + "connect " + "192.0.2.1" + ":5555",
            "Authorization:" + " Bearer private-token",
            "telemetry payload",
        ):
            with self.subTest(value=value):
                candidate = copy.deepcopy(manifest)
                candidate["records"][0]["provenanceStatus"] = value
                with self.assertRaises(ValueError):
                    validate_manifest(candidate)

    def _assert_record_contract(self, manifest):
        hashes = []
        for record in manifest["records"]:
            self.assertRegex(record["sha256"], r"^[0-9a-f]{64}$")
            hashes.append(record["sha256"])
            self.assertEqual((1080, 2340), (record["width"], record["height"]))
            self.assertEqual("PNG", record["format"])
            self.assertEqual("RGBA", record["colorMode"])
            self.assertEqual(8, record["bitDepth"])
            self.assertGreater(record["byteSize"], 0)
            self.assertEqual("NEEDS_HUMAN_PRIVACY_REVIEW", record["privacyClassification"])
            self.assertEqual("unreviewed", record["manualTruthStatus"])
            self.assertEqual("user_supplied_local_corpus", record["provenanceStatus"])
            self.assertEqual("not_approved", record["publicationStatus"])
            for field in BOOLEAN_FIELDS:
                self.assertIs(type(record[field]), bool)
            cluster_id = record["nearDuplicateClusterId"]
            decision = record["duplicateDecisionReason"]
            if cluster_id is None:
                self.assertIsNone(decision)
            else:
                self.assertIn(decision, DUPLICATE_DECISIONS)
        self.assertEqual(len(hashes), len(set(hashes)))
        self.assertTrue(manifest["candidateOnly"])
        self.assertFalse(manifest["authoritative"])
        self.assertFalse(manifest["containsScreenshotBytes"])
        self.assertFalse(manifest["truthLabelsPresent"])
        self.assertTrue(manifest["prospectiveHoldoutQuarantined"])

    def _assert_cluster_contract(self, manifest):
        records_by_id = {record["id"]: record for record in manifest["records"]}
        clusters_by_id = {cluster["id"]: cluster for cluster in manifest["nearDuplicateClusters"]}
        self.assertEqual(len(clusters_by_id), len(manifest["nearDuplicateClusters"]))
        for cluster_id, cluster in clusters_by_id.items():
            members = cluster["selectedMemberIds"]
            self.assertGreaterEqual(len(members), 1)
            self.assertEqual(len(members), len(set(members)))
            self.assertGreaterEqual(cluster["sourceMemberCount"], len(members))
            self.assertIn(cluster["decisionReason"], DUPLICATE_DECISIONS)
            self.assertTrue(set(members) <= records_by_id.keys())
            self.assertEqual(1, len({records_by_id[member]["lane"] for member in members}))
            for member in members:
                self.assertEqual(cluster_id, records_by_id[member]["nearDuplicateClusterId"])
        for record in manifest["records"]:
            cluster_id = record["nearDuplicateClusterId"]
            if cluster_id is not None:
                self.assertIn(record["id"], clusters_by_id[cluster_id]["selectedMemberIds"])

    def _valid_manifest(self):
        records = []
        for index, candidate_id in enumerate(self._expected_ids(), start=1):
            records.append(
                {
                    "id": candidate_id,
                    "lane": "development_candidate" if index <= DEVELOPMENT_COUNT else "prospective_holdout_candidate",
                    "sha256": f"{index:064x}",
                    "nearDuplicateClusterId": None,
                    "duplicateDecisionReason": None,
                    "width": 1080,
                    "height": 2340,
                    "format": "PNG",
                    "colorMode": "RGBA",
                    "bitDepth": 8,
                    "byteSize": index,
                    "privacyClassification": "NEEDS_HUMAN_PRIVACY_REVIEW",
                    "manualTruthStatus": "unreviewed",
                    "provenanceStatus": "user_supplied_local_corpus",
                    "publicationStatus": "not_approved",
                    "overlayPresent": False,
                    "likelyDetailsScreen": False,
                    "likelyCpPresent": False,
                    "likelyHpPresent": False,
                    "likelyNamePresent": False,
                    "likelyCandyFamilyPresent": False,
                }
            )
        return {
            "schemaVersion": 1,
            "datasetId": "candidate_2026_s25",
            "candidateOnly": True,
            "authoritative": False,
            "containsScreenshotBytes": False,
            "truthLabelsPresent": False,
            "prospectiveHoldoutQuarantined": True,
            "sourceFileCount": 730,
            "sourceAggregateBytes": 473_826_206,
            "sourceDigestSha256": "f" * 64,
            "nearDuplicateGroupCount": 61,
            "nearDuplicateGroupedFileCount": 176,
            "redundantExcludedCount": 5,
            "structurallyEligibleCount": 724,
            "nearDuplicateMethod": copy.deepcopy(NEAR_DUPLICATE_METHOD),
            "nearDuplicateClusters": [],
            "records": records,
        }

    def _write_synthetic_source(self, source_root):
        source_root.mkdir()
        for index in range(RECORD_COUNT):
            image = Image.new("RGBA", (1080, 2340), (245, 245, 245, 255))
            drawing = ImageDraw.Draw(image)
            drawing.rectangle((0, 0, 1079, 159), fill=((index * 37) % 256, (index * 67) % 256, (index * 97) % 256, 255))
            for bit in range(7):
                if index & (1 << bit):
                    left = 60 + bit * 140
                    drawing.rectangle((left, 300, left + 70, 2100), fill=(20 + bit * 25, 40, 220 - bit * 20, 255))
            image.save(source_root / f"synthetic_{RECORD_COUNT - index:03}.png", format="PNG")

    def _source_snapshot(self, source_root):
        return {
            path.name: (
                hashlib.sha256(path.read_bytes()).hexdigest(),
                path.stat().st_size,
                path.stat().st_mtime_ns,
            )
            for path in sorted(source_root.iterdir())
        }

    @staticmethod
    def _expected_ids():
        return [f"s25_2026_dev_{index:03}" for index in range(1, DEVELOPMENT_COUNT + 1)] + [
            f"s25_2026_holdout_{index:03}" for index in range(1, HOLDOUT_COUNT + 1)
        ]


if __name__ == "__main__":
    unittest.main()
