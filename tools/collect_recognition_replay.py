"""Run a development fixture replay on a dedicated emulator and save its report."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import uuid
from datetime import datetime, timezone
from pathlib import Path

from recognition_replay_collector import CollectionError, collect_report, make_partial_artifact

APP_ID = "com.pokerarity.scanner.sprint6qa"
APP_VARIANT = "qa"
TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS = "com.pokerarity.scanner.RecognitionRecoveryBenchmarkTest#measureBitmapReplay"
REQUIRED_AVD_PREFIX = "PokemonRarityScanner_"


def run(
    command: list[str],
    *,
    capture: bool = False,
    env: dict[str, str] | None = None,
    cwd: Path | None = None,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, text=True, encoding="utf-8", errors="replace",
                          capture_output=capture, check=False, env=env,
                          cwd=str(cwd) if cwd is not None else None)


def connected_avd_name(adb: str, serial: str) -> str:
    if not serial.startswith("emulator-"):
        raise CollectionError("device_is_not_an_emulator")
    qemu = run([adb, "-s", serial, "shell", "getprop", "ro.kernel.qemu"], capture=True)
    if qemu.returncode != 0 or qemu.stdout.strip() != "1":
        raise CollectionError("device_is_not_an_emulator")
    result = run([adb, "-s", serial, "emu", "avd", "name"], capture=True)
    if result.returncode != 0:
        raise CollectionError("emulator_name_unavailable")
    name = next((line.strip() for line in result.stdout.splitlines()
                 if line.strip() and line.strip().upper() != "OK"), "")
    if not name.startswith(REQUIRED_AVD_PREFIX):
        raise CollectionError("emulator_name_not_isolated")
    return name


def write_atomic(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = (json.dumps(payload, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as temporary:
            temporary.write(encoded)
            temporary.flush()
            os.fsync(temporary.fileno())
        os.replace(temporary_name, path)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def gradle_command(root: Path) -> list[str]:
    tasks = [":app:assembleQa", ":app:assembleQaAndroidTest", "--no-daemon", "--console=plain"]
    wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if os.name == "nt":
        return ["cmd.exe", "/d", "/c", str(wrapper), *tasks]
    return [str(wrapper), *tasks]


def resolve_android_sdk(environment: dict[str, str], home: Path | None = None) -> Path | None:
    home = home or Path.home()
    candidates = [environment.get("ANDROID_HOME"), environment.get("ANDROID_SDK_ROOT")]
    if os.name == "nt":
        candidates.append(str(home / "AppData/Local/Android/Sdk"))
    else:
        candidates.append(str(home / "Android/Sdk"))
    return next((Path(candidate) for candidate in candidates
                 if candidate and Path(candidate).is_dir()), None)


def write_partial_report(
    path: Path,
    run_id: str,
    source_revision: str,
    failure_code: str,
    failure_stage: str,
    *,
    avd_name: str | None = None,
    process_exit_code: int | None = None,
    instrumentation_exit_code: int | None = None,
) -> None:
    artifact = make_partial_artifact(
        run_id, source_revision, failure_code, instrumentation_exit_code)
    artifact["collection"] = {
        "collectorSchemaVersion": 1,
        "status": "partial",
        "failureStage": failure_stage,
        "processExitCode": process_exit_code,
        "avd": avd_name,
        "collectedAtUtc": datetime.now(timezone.utc).isoformat(),
    }
    write_atomic(path, artifact)
    print(f"Recognition replay report: {path}")
    print("Collection status: partial")


def find_single_apk(directory: Path, pattern: str) -> Path:
    matches = sorted(directory.glob(pattern))
    if len(matches) != 1:
        raise CollectionError("expected_apk_output_missing_or_ambiguous")
    return matches[0]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit serial of a dedicated PokemonRarityScanner_* emulator")
    parser.add_argument("--subset", choices=("pr06", "legacy", "all"), default="pr06")
    parser.add_argument("--policies", default="baseline,native", help="Comma-separated baseline/native/adaptive policies")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--adb", help="ADB executable; defaults to the configured Android SDK")
    parser.add_argument("--run-id", default=str(uuid.uuid4()))
    arguments = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    source = run(["git", "rev-parse", "HEAD"], capture=True, cwd=root)
    if source.returncode != 0:
        parser.error("could not read the current source revision")
    source_revision = source.stdout.strip()
    if not source_revision or not all(char in "0123456789abcdefABCDEF" for char in source_revision):
        parser.error("source revision is not a Git SHA")
    policies = arguments.policies.split(",")
    if not policies or len(set(policies)) != len(policies) or any(p not in ("baseline", "native", "adaptive") for p in policies):
        parser.error("policies must be a unique comma-separated list of baseline, native and adaptive")
    if len(arguments.run_id) != 36 or any(c not in "0123456789abcdefABCDEF-" for c in arguments.run_id):
        parser.error("run id must be a UUID")

    output_path = arguments.output or (root / "build/reports/recognition-replay" / f"{arguments.run_id}.json")

    sdk_path = resolve_android_sdk(os.environ)
    if sdk_path is None:
        print("Android SDK was not found; set ANDROID_HOME or ANDROID_SDK_ROOT", file=sys.stderr)
        write_partial_report(output_path, arguments.run_id, source_revision,
                             "android_sdk_missing", "environment_validation")
        return 2
    sdk = str(sdk_path)
    adb = arguments.adb or (str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")) if sdk else shutil.which("adb"))
    if not adb:
        print("ADB was not found; set ANDROID_HOME or pass --adb", file=sys.stderr)
        write_partial_report(output_path, arguments.run_id, source_revision,
                             "adb_missing", "environment_validation")
        return 2
    try:
        avd_name = connected_avd_name(adb, arguments.serial)
    except CollectionError as error:
        print(f"Refusing device before installation: {error.code}", file=sys.stderr)
        write_partial_report(output_path, arguments.run_id, source_revision,
                             error.code, "device_validation")
        return 2
    except KeyboardInterrupt:
        write_partial_report(output_path, arguments.run_id, source_revision,
                             "collector_interrupted", "device_validation",
                             process_exit_code=130)
        return 130
    except OSError:
        print("ADB device validation could not be launched.", file=sys.stderr)
        write_partial_report(output_path, arguments.run_id, source_revision,
                             "adb_launch_failed", "device_validation")
        return 2
    print(f"Using isolated emulator {avd_name} ({arguments.serial})", flush=True)

    build_environment = os.environ.copy()
    build_environment["ANDROID_HOME"] = sdk
    build_environment["ANDROID_SDK_ROOT"] = sdk
    try:
        build = run(gradle_command(root), env=build_environment, cwd=root)
    except KeyboardInterrupt:
        write_partial_report(output_path, arguments.run_id, source_revision,
                             "collector_interrupted", "gradle_build", avd_name=avd_name,
                             process_exit_code=130)
        return 130
    except OSError:
        print("Gradle could not be launched.", file=sys.stderr)
        write_partial_report(output_path, arguments.run_id, source_revision,
                             "gradle_launch_failed", "gradle_build", avd_name=avd_name)
        return 2
    if build.returncode != 0:
        print("Gradle build failed.", file=sys.stderr)
        write_partial_report(output_path, arguments.run_id, source_revision,
                             "gradle_build_failed", "gradle_build", avd_name=avd_name,
                             process_exit_code=build.returncode)
        return build.returncode
    try:
        app_apk = find_single_apk(root / f"app/build/outputs/apk/{APP_VARIANT}", "*-qa.apk")
        test_apk = find_single_apk(root / f"app/build/outputs/apk/androidTest/{APP_VARIANT}", "*-qa-androidTest.apk")
    except CollectionError as error:
        print(f"Expected isolated QA APK outputs were not produced: {error.code}", file=sys.stderr)
        write_partial_report(output_path, arguments.run_id, source_revision,
                             error.code, "apk_lookup", avd_name=avd_name)
        return 2

    for apk in (app_apk, test_apk):
        try:
            install = run([adb, "-s", arguments.serial, "install", "-r", str(apk)], capture=True)
        except KeyboardInterrupt:
            write_partial_report(output_path, arguments.run_id, source_revision,
                                 "collector_interrupted", "apk_install", avd_name=avd_name,
                                 process_exit_code=130)
            return 130
        except OSError:
            print("APK installation command could not be launched.", file=sys.stderr)
            write_partial_report(output_path, arguments.run_id, source_revision,
                                 "adb_launch_failed", "apk_install", avd_name=avd_name)
            return 2
        if install.returncode != 0:
            print("APK installation failed on the isolated emulator.", file=sys.stderr)
            write_partial_report(output_path, arguments.run_id, source_revision,
                                 "apk_install_failed", "apk_install", avd_name=avd_name,
                                 process_exit_code=install.returncode)
            return install.returncode or 2

    report_name = f"recognition_recovery_{arguments.run_id}.json"
    try:
        instrumentation = run([
            adb, "-s", arguments.serial, "shell", "am", "instrument", "-w", "-r",
            "-e", "class", TEST_CLASS,
            "-e", "runId", arguments.run_id,
            "-e", "sourceRevision", source_revision,
            "-e", "report", report_name,
            "-e", "subset", arguments.subset,
            "-e", "policies", ",".join(policies),
            f"{APP_ID}.test/{TEST_RUNNER}",
        ], capture=True)
    except KeyboardInterrupt:
        write_partial_report(output_path, arguments.run_id, source_revision,
                             "collector_interrupted", "instrumentation", avd_name=avd_name,
                             process_exit_code=130)
        return 130
    except OSError:
        print("Instrumentation command could not be launched.", file=sys.stderr)
        write_partial_report(output_path, arguments.run_id, source_revision,
                             "adb_launch_failed", "instrumentation", avd_name=avd_name)
        return 2
    output = instrumentation.stdout + "\n" + instrumentation.stderr
    try:
        report, checksum, chunk_count = collect_report(output, arguments.run_id, source_revision)
        complete = report["completed"] is True and instrumentation.returncode == 0
        status = "complete" if complete else "partial"
        report["collection"] = {
            "collectorSchemaVersion": 1,
            "status": status,
            "avd": avd_name,
            "chunkCount": chunk_count,
            "sha256": checksum,
            "instrumentationExitCode": instrumentation.returncode,
            "collectedAtUtc": datetime.now(timezone.utc).isoformat(),
        }
        artifact = report
    except CollectionError as error:
        write_partial_report(output_path, arguments.run_id, source_revision,
                             error.code, "report_collection", avd_name=avd_name,
                             process_exit_code=instrumentation.returncode,
                             instrumentation_exit_code=instrumentation.returncode)
        return 1

    write_atomic(output_path, artifact)
    print(f"Recognition replay report: {output_path}")
    print(f"Collection status: {status}")
    return 0 if status == "complete" else 1


if __name__ == "__main__":
    sys.exit(main())
