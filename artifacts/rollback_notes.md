# Rollback Notes

## 2026-04-06

- DatabasePassphraseStore import changed from net.sqlcipher to net.zetetic; rollback by reverting the file if SQLCipher 4.5.4 integration causes issues.
- SQLCipher migration logic now uses SQLiteDatabase.changePassword(byte[]) with sqlcipher-android 4.5.4 openDatabase signature.
- ScanTelemetryUploaderTest expected error string updated to Missing or invalid screenshot_url after tightening response validation.
- Security pass touched app/build.gradle.kts, telemetry prefs/config, AppDatabase/DatabasePassphraseStore, MainActivity/CollectionScreen settings UI, and ScanTelemetryUploader config path. Roll back these files together if reverting to pre-hardening behavior.
- AndroidManifest launcher activity SplashActivity changed to exported=true. Revert only if launch entrypoint handling is redesigned.
- Added SqlCipherInitializer and explicit System.loadLibrary(\"sqlcipher\") calls in app startup and DB init. If reverting, remove these only if SQLCipher native loading is guaranteed elsewhere.
- AppDatabase now forces an initial encrypted open and retries once after deleting unreadable/plaintext pokerarity_db artifacts. Revert only if a dedicated plaintext-to-SQLCipher migration is implemented.
- Temporary local inspection artifact directory artifacts/tmp_sqlcipher is not part of the fix and can be deleted safely.
- ScreenCaptureService now supports EXTRA_AUTO_CAPTURE and MainActivity/ProjectionPermissionActivity use it to trigger immediate scans. Revert these together if returning to manual overlay-only capture behavior.
- app/build.gradle.kts now derives versionCode/versionName from gradle.properties/env and emits versioned APK filenames. Release signing falls back to debug signing when no release keystore is configured.
- .github/workflows/release-apk.yml publishes tagged release APKs. Remove or disable if GitHub Releases should not distribute APK artifacts.
- docs/release-process.md documents versioning, signing inputs, and GitHub Release publishing. Safe to remove if release distribution stays manual.

## 2026-04-07 - scan repair rollback
Files: app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt; app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt; app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt
Rollback: revert the commit for this pass if classifier stops rescuing legitimate same-species costume/event detections. Primary behavioral changes are stricter OCR species lock and stricter active live event gating.

- 2026-04-08 follow-up: TextParser noisy row repair may over-accept edge OCR rows; rollback TextParser.kt if false candy/dust pairs appear. release-apk.yml now requires contents:write and chmod +x gradlew for GitHub-hosted linux runners.

- app/build.gradle.kts now reads gradle/project properties for version values. Roll back if local.properties-only behavior was intentionally relied on.

- 2026-04-08 event label honesty fix: authoritative remaps no longer carry event labels without caughtDate. Roll back FullVariantCandidateBuilder.kt if you explicitly want speculative event names back.

## 2026-04-08 - rollback notes for 1.1.2 live-scan/release pass

- Files changed in this pass:
  - `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt`
  - `app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt`
  - `gradle.properties`
  - `artifacts/agent_worklog.md`
  - `artifacts/rollback_notes.md`
- Behavioral rollback:
  - To restore old speculative event metadata on classifier remaps, revert the `keepEventMetadata = caughtDate != null` gating in `FullVariantCandidateBuilder.buildAuthoritativeRemapCandidates`.
  - To revert version bump only, restore previous `VERSION_NAME` / `VERSION_CODE` in `gradle.properties`.
- Rationale for current behavior:
  - live telemetry showed wrong event names appearing from remap candidates without date evidence.
  - current fix preserves remap usefulness while reducing false event claims.
- Validation completed before keeping change:
  - focused tests passed
  - `assembleRelease` passed
  - `PokeRarityScanner-v1.1.2-release.apk` installed and launched on the connected phone.
