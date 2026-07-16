# Local MobSF Android audit

This runbook keeps the APK and scan database on the developer machine. Do not upload the application, signing material, telemetry credentials, or private reports to a public MobSF instance.

## Security model

- Uses the official `opensecurity/mobile-security-framework-mobsf:v4.5.1` image.
- Publishes MobSF only on `127.0.0.1`; it is not exposed to the LAN.
- Uses an ephemeral `--rm` container with no host data volume.
- Uses `no-new-privileges` and does not mount the Docker socket or repository.
- Records the resolved image digest in the terminal after pulling the pinned tag.
- Deletes the MobSF container and its scan database when the stop command is run.

The launcher is intended for static APK analysis. Dynamic analysis requires a separate, intentionally configured test device or emulator and is outside this runbook.

## Prerequisites

1. Docker Desktop is installed and running.
2. The repository is checked out locally.
3. JDK 17 and the Android SDK required by the project are available.

## Build the debug APK

From the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

Locate the newest debug APK:

```powershell
$Apk = Get-ChildItem .\app\build\outputs\apk\debug\*.apk |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

$Apk.FullName
```

Use the debug APK for this internal audit. Do not use or distribute the release signing keystore.

## Start MobSF locally

```powershell
.\scripts\security\start_mobsf_local.ps1
```

The script opens `http://127.0.0.1:8000`. The default local login is:

```text
username: mobsf
password: mobsf
```

Use another local port when necessary:

```powershell
.\scripts\security\start_mobsf_local.ps1 -Port 8080
```

## Run the static analysis

1. Upload the debug APK shown by `$Apk.FullName`.
2. Wait for static analysis to complete.
3. Review at least:
   - application security score;
   - exported activities, services, receivers, and providers;
   - dangerous and signature-level permissions;
   - backup, debuggable, cleartext traffic, and network security settings;
   - certificate and APK signing information;
   - WebView and deep-link findings;
   - hardcoded secrets, URLs, trackers, native libraries, and weak cryptography;
   - manifest and code findings mapped to CWE or MASVS categories.
4. Validate each High or Critical result against the source before changing code. Static-analysis findings can be false positives.
5. Export the PDF and JSON reports locally when available.

Recommended local output directory:

```powershell
New-Item -ItemType Directory -Force .\security-reports\mobsf | Out-Null
```

`security-reports/` is ignored by Git. Reports may contain package names, endpoints, class names, and other internal implementation details.

## Stop and erase the local scan container

```powershell
.\scripts\security\start_mobsf_local.ps1 -Stop
```

Because the container is ephemeral and no MobSF data volume is mounted, stopping it removes the local MobSF scan database. Export any report that must be retained before stopping.

## Triage policy

Classify each result as one of:

- validated vulnerability;
- defensive hardening opportunity;
- expected debug-only behavior;
- false positive with source evidence;
- third-party dependency finding requiring version review.

Only validated or plausible findings should become code changes. Fixes should be made in small PRs with focused tests and must pass detekt, unit tests, Android Lint, CodeQL, Semgrep, SonarQube, and CodeRabbit.

## Version review

The pinned MobSF image was reviewed on 2026-07-16. Before changing the tag, check the official MobSF release notes and Docker image tags for security fixes, then update the script in a reviewed PR.
