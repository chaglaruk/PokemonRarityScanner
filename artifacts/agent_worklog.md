# Agent Worklog

## 2026-04-06

- Fixed SQLCipher package import in DatabasePassphraseStore and resumed verification build for security fixes.
- Verified SQLCipher integration failure is now narrowed to API signature mismatch in DatabasePassphraseStore.
- Matched DatabasePassphraseStore to sqlcipher-android 4.5.4 API: removed loadLibs call, switched to openDatabase(..., errorHandler, hook) and changePassword(byte[]).
- assembleDebug passed; remaining failure is a stale unit test expectation in ScanTelemetryUploaderTest after stricter screenshot_url validation.
- Updated ScanTelemetryUploaderTest to match strict non-null/non-empty screenshot_url contract.
- Applied security hardening from deeper_security_analysis: encrypted telemetry prefs, moved telemetry API key/base URL to encrypted local config, added telemetry settings dialog, replaced deterministic DB key flow with per-install passphrase store + legacy SQLCipher rekey path, and tightened upload response validation.
- Investigating startup crash with adb logcat before any code changes.
- Captured startup logcat to artifacts/startup_logcat.txt using adb am start after monkey hit a SecurityException.
- Confirmed launcher activity is SplashActivity; explicit adb start is blocked by exported=false, testing launcher intent path instead.
- Root-cause evidence: launcher SplashActivity had intent-filter MAIN/LAUNCHER but exported=false, blocking shell/launcher startup. Set exported=true.
- Confirmed startup failure via ApplicationExitInfo: APP CRASH(EXCEPTION) at 17:00:08; extracting dropbox crash report for concrete stacktrace.
- Crash stacktrace showed SQLCipher UnsatisfiedLinkError at SQLiteConnection.nativeOpen during Room startup. Added explicit sqlcipher native init before any DB access in app startup and DB passphrase handling.
- Follow-up crash after native init was SQLiteException \"file is not a database\" on pokerarity_db. Added startup recovery: detect plaintext legacy DB header and retry once after deleting unreadable DB artifacts.
- Verification: build succeeded, APK installed to RFCY11MX0TM, app launched into MainActivity, and adb logcat confirmed SqlCipherInit loaded plus one-time plaintext DB cleanup/recreate.
- Identified current scan-result gap in code path: MainActivity Scan Now only started overlay/service, but did not guarantee an actual capture. Added service-level auto-capture trigger and extra logging around overlay click, capture receiver, and no-frame failure.
- Added versioned build metadata and release APK naming. Introduced GitHub Actions workflow to build/publish versioned release APKs on v* tags, with real signing from secrets when available and debug-sign fallback locally.
- Verified versioned APK outputs: debug and release now emit PokeRarityScanner-v1.1.0-*.apk, release APK installed successfully on device, and launcher start still works after scan-flow changes.

## 2026-04-07 - telemetry-driven scan repair pass
- Evidence reviewed from adb logcat and latest iv_diagnostics summaries.
- Identified two live causes: OCR-locked species being overridden by classifier, and unconditional active live event rescue forcing false costume/form labels.
- Changed VariantDecisionEngine to prefer OCR-parsed species tokens over unknown current species during classifier override/scoped pass.
- Changed FullVariantCandidateBuilder to require same-species non-base classifier support before adding authoritative_live_species_event candidate.
- Added regression test for active live event gating.

- Added parser repair for noisy shared power-up OCR rows like '5800 31' => (800,1), based on live Drilbur telemetry evidence.
- Fixed GitHub release workflow with contents:write permission and chmod for ./gradlew on ubuntu.
- Bumped local version to 1.1.1 for the new release tag.

- Fixed build version source precedence: app/build.gradle.kts now reads gradle.properties via findProperty, so local release filenames follow repo version bumps.

- Latest live telemetry shows many RANGE outcomes are genuine cp+hp-only ambiguity because the power-up row is not visible at all in the screenshot.
- Suppressed authoritative event metadata on classifier remap candidates when caughtDate is missing; event names now come only from live-event support or caught-date-backed authoritative matches.
- Bumped version to 1.1.2 for this metadata honesty fix.

## 2026-04-08 - live scan diagnosis, release asset fix, 1.1.2

- Inspected latest five scan outcomes from on-device `iv_diagnostics` bundles after user reported wrong scan results and persistent IV ranges.
- Evidence showed most current IV `RANGE` cases are honest ambiguity, not solver failure:
  - recent summaries included `cp+hp+state` only, or `cp+hp+stardust+candy+state` with still-multiple valid candidates.
  - example: `cp=912 hp=87` -> `RANGE`, `26 candidates`, no readable costs.
  - example: `cp=1311 hp=96 stardust=1900` -> `RANGE`, `20 candidates`, even with both costs.
- Fixed event-metadata honesty for authoritative remaps:
  - `FullVariantCandidateBuilder` now strips `eventLabel/eventStart/eventEnd` from classifier-driven authoritative remap candidates when `caughtDate` is missing.
  - This keeps species/variant remap support without showing speculative event names.
- Repaired failing regression test to match supported remap behavior:
  - changed test fixture from base shiny remap to costume remap because builder intentionally excludes base remaps.
- Verified release build/version path:
  - `gradle.properties` bumped to `1.1.2` / `4`
  - release artifact confirmed at `app/build/outputs/apk/release/PokeRarityScanner-v1.1.2-release.apk`
- Ran focused unit tests:
  - `FullVariantCandidateBuilderTest`
  - `TextParserPowerUpCostTest`
  - `ScanAuthorityLogicTest`
  - `IvCostSolverTest`
  - all passed in the final run.
- Built release APK, installed to device `RFCY11MX0TM`, and launched app successfully.
- Next repo step after this log entry: commit source changes, push `main`, and push tag `v1.1.2` so the fixed GitHub release workflow publishes the APK asset.

## 2026-04-09 - shiny false positives and invalid release workflow expression

- Captured fresh logs after five non-shiny scans. Evidence:
  - `Lucario` was marked shiny by visual fallback despite no signature support.
  - `Hitmonchan` was marked shiny by visual fallback despite the user reporting no shiny scans.
  - Several scans still returned `RANGE` because no readable power-up row existed; this remains honest ambiguity, not a solver crash.
- Root cause for false shiny positives:
  - `VisualFeatureDetector.chooseShinyResult` accepted fallback color-only shiny decisions without any hue-based support.
- Fix applied:
  - shiny fallback now requires hue support, at least two supporting signals, and higher fallback confidence.
  - added rejection logging for fallback cases.
- Root cause for missing APK on GitHub Releases:
  - workflow file was invalid on GitHub, not just failing at runtime.
  - GitHub Actions error: `Invalid workflow file ... Unrecognized named-value: 'secrets'` in step-level `if`.
- Fix applied:
  - moved release secrets to job-level `env`
  - changed step condition to `if: env.POKERARITY_RELEASE_STORE_BASE64 != ''`
  - build step now consumes those env vars instead of direct `secrets.*` references.

## 2026-04-09 - local GitHub release upload fallback

- Verified the current GitHub Actions block is not caused by repo artifacts or caches:
  - public API reported `0` artifacts and `0` active caches for this repository.
- Added a local fallback for publishing APKs to GitHub Releases without GitHub Actions:
  - `scripts/publish_github_release.ps1`
  - `docs/github-release-upload.md`
- The script:
  - finds the newest local release APK by default
  - infers the release tag from the APK filename
  - creates the release if missing
  - replaces an existing APK asset with the same filename
- This path still needs a GitHub token with release upload permission, but it avoids the GitHub Actions billing lock entirely.
