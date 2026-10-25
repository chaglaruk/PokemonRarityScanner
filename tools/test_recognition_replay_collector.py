import base64
import hashlib
import json
import os
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from recognition_replay_collector import (
    CHUNK_COUNT_KEY,
    CHUNK_DATA_KEY,
    CHUNK_INDEX_KEY,
    REPORT_SHA_KEY,
    RUN_ID_KEY,
    CollectionError,
    collect_report,
    make_partial_artifact,
)
from collect_recognition_replay import find_single_apk, resolve_android_sdk, run, write_atomic


RUN_ID = "3a87de19-421e-44db-bcfe-52c5a8c4eab0"
REVISION = "18e0489f4a27cdc4e8d6a80fee9d9a3cf234d1fa"


def instrumentation_output(report: dict, *, chunk_size: int = 48) -> str:
    payload = json.dumps(report, separators=(",", ":")).encode("utf-8")
    pieces = [payload[index:index + chunk_size] for index in range(0, len(payload), chunk_size)]
    checksum = hashlib.sha256(payload).hexdigest()
    lines = []
    for index, piece in reversed(list(enumerate(pieces))):
        lines.extend([
            f"INSTRUMENTATION_STATUS: {RUN_ID_KEY}={RUN_ID}",
            f"INSTRUMENTATION_STATUS: {CHUNK_INDEX_KEY}={index}",
            f"INSTRUMENTATION_STATUS: {CHUNK_COUNT_KEY}={len(pieces)}",
            f"INSTRUMENTATION_STATUS: {REPORT_SHA_KEY}={checksum}",
            f"INSTRUMENTATION_STATUS: {CHUNK_DATA_KEY}={base64.b64encode(piece).decode('ascii')}",
            "INSTRUMENTATION_STATUS_CODE: 0",
        ])
    return "\n".join(lines)


def valid_report() -> dict:
    return {
        "schemaVersion": 2,
        "runId": RUN_ID,
        "sourceRevision": REVISION,
        "completed": True,
        "expectedOutcomes": 1,
        "outcomes": [{"id": "fixture_01", "expectedSpecies": "Pikachu", "rawPresent": True}],
        "summaries": [],
    }


class RecognitionReplayCollectorTest(unittest.TestCase):
    def test_reassembles_correlated_chunks_out_of_order_and_checks_report(self):
        report = valid_report()

        collected, checksum, chunks = collect_report(instrumentation_output(report), RUN_ID, REVISION)

        self.assertEqual(report, collected)
        self.assertEqual(hashlib.sha256(json.dumps(report, separators=(",", ":")).encode()).hexdigest(), checksum)
        self.assertGreater(chunks, 1)

    def test_rejects_missing_chunks(self):
        output = instrumentation_output(valid_report())
        first_status = output.find("INSTRUMENTATION_STATUS:")
        next_block = output.find("INSTRUMENTATION_STATUS_CODE:", first_status)
        truncated = output[:first_status] + output[next_block + len("INSTRUMENTATION_STATUS_CODE: 0\n"):]

        with self.assertRaisesRegex(CollectionError, "incomplete_chunks"):
            collect_report(truncated, RUN_ID, REVISION)

    def test_rejects_raw_ocr_fields_and_source_mismatch(self):
        unsafe = valid_report()
        unsafe["outcomes"][0]["rawOcrText"] = "Pikachu"
        with self.assertRaisesRegex(CollectionError, "report_contains_sensitive_field"):
            collect_report(instrumentation_output(unsafe), RUN_ID, REVISION)

        with self.assertRaisesRegex(CollectionError, "report_source_revision_mismatch"):
            collect_report(instrumentation_output(valid_report()), RUN_ID, "different-revision")

    def test_host_artifact_is_written_atomically_with_collection_metadata(self):
        artifact = valid_report()
        artifact["collection"] = {"status": "complete", "chunkCount": 2}
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "nested" / "report.json"

            write_atomic(target, artifact)

            self.assertEqual(artifact, json.loads(target.read_text(encoding="utf-8")))
            self.assertEqual([], list(target.parent.glob("*.tmp")))

    def test_missing_instrumentation_report_has_sanitized_partial_envelope(self):
        artifact = make_partial_artifact(RUN_ID, REVISION, "instrumentation_report_missing", 1)

        self.assertEqual("partial", artifact["collectionStatus"])
        self.assertEqual("instrumentation_report_missing", artifact["failureCode"])
        self.assertNotIn("stdout", artifact)
        self.assertNotIn("stderr", artifact)

    def test_apk_lookup_requires_one_variant_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "PokeRarityScanner-v1.10.0-debug.apk"
            apk.touch()
            self.assertEqual(apk, find_single_apk(root, "*-debug.apk"))
            (root / "duplicate-debug.apk").touch()
            with self.assertRaisesRegex(CollectionError, "expected_apk_output_missing_or_ambiguous"):
                find_single_apk(root, "*-debug.apk")

    def test_subprocess_helper_forwards_explicit_repository_working_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            root.mkdir()
            with patch("collect_recognition_replay.subprocess.run") as process:
                run(["git", "rev-parse", "HEAD"], capture=True, cwd=root)
                self.assertEqual(str(root), process.call_args.kwargs["cwd"])
                self.assertTrue(process.call_args.kwargs["capture_output"])

                run(["gradlew", ":app:assembleDebug"], env={"ANDROID_HOME": directory}, cwd=root)
                self.assertEqual(str(root), process.call_args.kwargs["cwd"])
                self.assertEqual({"ANDROID_HOME": directory}, process.call_args.kwargs["env"])

    def test_sdk_resolution_uses_configured_or_standard_location(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            sdk = home / "sdk"
            sdk.mkdir()
            fallback = home / ("AppData/Local/Android/Sdk" if os.name == "nt" else "Android/Sdk")
            fallback.mkdir(parents=True)

            self.assertEqual(sdk, resolve_android_sdk({"ANDROID_HOME": str(sdk)}, home))
            self.assertEqual(fallback, resolve_android_sdk({"ANDROID_HOME": str(sdk / "missing")}, home))
            self.assertEqual(fallback, resolve_android_sdk({}, home))


if __name__ == "__main__":
    unittest.main()
