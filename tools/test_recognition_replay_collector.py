import base64
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from unittest.mock import patch
from pathlib import Path

import collect_recognition_replay as host_collector
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

    def test_accepts_schema_v3_reports_while_preserving_v2_compatibility(self):
        for schema_version in (2, 3):
            with self.subTest(schema_version=schema_version):
                report = valid_report()
                report["schemaVersion"] = schema_version

                collected, _, _ = collect_report(instrumentation_output(report), RUN_ID, REVISION)

                self.assertEqual(schema_version, collected["schemaVersion"])

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

    def test_gradle_failure_writes_sanitized_partial_from_an_unrelated_working_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            external_cwd = Path(directory) / "unrelated"
            external_cwd.mkdir()
            output = Path(directory) / "reports" / "failed-build.json"
            repository_root = Path(host_collector.__file__).resolve().parents[1]
            calls = []

            def fake_run(command, *, capture=False, env=None, cwd=None):
                calls.append((command, Path(cwd) if cwd is not None else None))
                if command[:3] == ["git", "rev-parse", "HEAD"]:
                    return subprocess.CompletedProcess(command, 0, stdout=f"{REVISION}\n", stderr="")
                return subprocess.CompletedProcess(
                    command, 23, stdout="", stderr="C:\\private\\source\\build.log"
                )

            original_cwd = Path.cwd()
            try:
                os.chdir(external_cwd)
                with patch.object(host_collector, "run", side_effect=fake_run), \
                     patch.object(host_collector, "connected_avd_name", return_value="PokemonRarityScanner_API36"), \
                     patch.object(host_collector, "resolve_android_sdk", return_value=Path(directory)), \
                     patch.object(sys, "argv", ["collect_recognition_replay.py", "--serial", "emulator-5554",
                                                  "--run-id", RUN_ID, "--output", str(output)]), \
                     redirect_stdout(StringIO()), redirect_stderr(StringIO()):
                    exit_code = host_collector.main()
            finally:
                os.chdir(original_cwd)

            artifact_text = output.read_text(encoding="utf-8")
            artifact = json.loads(artifact_text)
            self.assertEqual(23, exit_code)
            self.assertEqual(repository_root, calls[0][1])
            self.assertEqual(repository_root, calls[1][1])
            self.assertEqual(RUN_ID, artifact["runId"])
            self.assertEqual(REVISION, artifact["sourceRevision"])
            self.assertEqual("gradle_build_failed", artifact["failureCode"])
            self.assertIsNone(artifact["instrumentationExitCode"])
            self.assertEqual("partial", artifact["collection"]["status"])
            self.assertEqual("gradle_build", artifact["collection"]["failureStage"])
            self.assertEqual(23, artifact["collection"]["processExitCode"])
            self.assertNotIn("private", artifact_text.lower())
            self.assertNotIn("stderr", artifact_text.lower())

    def test_interrupted_instrumentation_writes_partial_with_adaptive_run_context(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "reports" / "interrupted.json"
            repository_root = Path(host_collector.__file__).resolve().parents[1]
            commands = []

            def fake_run(command, *, capture=False, env=None, cwd=None):
                commands.append((command, Path(cwd) if cwd is not None else None))
                if command[:3] == ["git", "rev-parse", "HEAD"]:
                    return subprocess.CompletedProcess(command, 0, stdout=f"{REVISION}\n", stderr="")
                if "install" in command:
                    return subprocess.CompletedProcess(command, 0, stdout="Success", stderr="")
                if "instrument" in command:
                    raise KeyboardInterrupt
                return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

            original_cwd = Path.cwd()
            try:
                os.chdir(directory)
                with patch.object(host_collector, "run", side_effect=fake_run), \
                     patch.object(host_collector, "connected_avd_name", return_value="PokemonRarityScanner_API36"), \
                     patch.object(host_collector, "resolve_android_sdk", return_value=Path(directory)), \
                     patch.object(host_collector, "find_single_apk", side_effect=[Path(directory) / "app.apk",
                                                                                   Path(directory) / "test.apk"]), \
                     patch.object(sys, "argv", ["collect_recognition_replay.py", "--serial", "emulator-5554",
                                                  "--run-id", RUN_ID, "--output", str(output),
                                                  "--policies", "baseline,native,adaptive"]), \
                     redirect_stdout(StringIO()), redirect_stderr(StringIO()):
                    exit_code = host_collector.main()
            finally:
                os.chdir(original_cwd)

            artifact_text = output.read_text(encoding="utf-8")
            artifact = json.loads(artifact_text)
            instrumentation_command = next(command for command, _ in commands if "instrument" in command)
            self.assertEqual(130, exit_code)
            self.assertIn("baseline,native,adaptive", instrumentation_command)
            self.assertEqual(repository_root, next(cwd for command, cwd in commands
                                                   if command[0] not in ("git", "adb")))
            self.assertEqual("collector_interrupted", artifact["failureCode"])
            self.assertIsNone(artifact["instrumentationExitCode"])
            self.assertEqual("partial", artifact["collection"]["status"])
            self.assertEqual("instrumentation", artifact["collection"]["failureStage"])
            self.assertEqual(130, artifact["collection"]["processExitCode"])
            self.assertNotIn("stderr", artifact_text.lower())


if __name__ == "__main__":
    unittest.main()
