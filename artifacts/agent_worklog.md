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

