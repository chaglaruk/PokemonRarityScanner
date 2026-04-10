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

## 2026-04-09 - rollback notes for shiny fallback and workflow fix

- Files changed:
  - `.github/workflows/release-apk.yml`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VisualFeatureDetector.kt`
- Behavioral rollback:
  - To restore previous shiny aggressiveness, remove the `hueSupported`, `supportCount`, and `fallback.second < 0.78f` guards in `chooseShinyResult`.
  - To restore the previous workflow file, revert the job-level `env` block and the `if: env.POKERARITY_RELEASE_STORE_BASE64 != ''` expression.
- Rationale for keeping the change:
  - live non-shiny scans were being promoted to shiny by color-only fallback.
  - GitHub Actions rejected the workflow before any release job could run, so no APK asset could ever be published.

## 2026-04-09 - rollback notes for local release upload helper

- Files added:
  - `scripts/publish_github_release.ps1`
  - `docs/github-release-upload.md`
- Files updated:
  - `docs/release-process.md`
- Rollback:
  - Remove the helper script and docs if you do not want to support local GitHub API upload.
- Rationale for keeping the change:
  - repository-level Actions storage is already zero, so artifact cleanup cannot resolve the billing lock.
  - the local upload path provides a working release route while Actions remains unavailable.
- Additional behavior:
  - the uploader now uses Git credential manager as a fallback token source and writes a commit-based change list into the release body.
  - a build-and-publish wrapper was added for one-step local release publication.

## 2026-04-09 - rollback notes for Stitch UI adaptation

- Files added:
  - `app/src/main/java/com/pokerarity/scanner/ui/components/StitchNavigation.kt`
  - `docs/superpowers/specs/2026-04-09-stitch-ui-adaptation-design.md`
  - `docs/superpowers/plans/2026-04-09-stitch-ui-adaptation.md`
- Files updated:
  - `app/src/main/java/com/pokerarity/scanner/ui/main/MainActivity.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/screens/CollectionScreen.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`
  - `app/src/main/res/values/strings.xml`
  - `gradle.properties`
- Rollback:
  - revert the files above to restore the previous dashboard and full result screen layouts
  - move `pokerarity.versionName`/`versionCode` back if you need to avoid publishing `v1.1.4`
- Rationale for keeping the change:
  - the current dashboard already partially matched the Stitch direction; this pass finishes the shell and result framing without changing scan logic
  - full result now uses the same design language as the overlay instead of diverging into a separate visual system
- Release uploader note:
  - `scripts/publish_github_release.ps1` now refreshes `target_commitish` when updating an existing release tag.
## 2026-04-10 - rollback notes for live scan OCR/variant/perf repair

- Planned files to update:
  - `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenRegions.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VisualFeatureDetector.kt`
  - related focused tests
- Rollback guidance:
  - revert those files together if scan latency improves but OCR/variant accuracy regresses
  - pay attention to `ScanManager.shouldRunDetailedPassForAuthoritative` and `VariantMergeLogic.mergeVisualFeatures`, because those are the highest-impact behavior changes in this pass
- Rollback notes:
  - If the lower power-up OCR regions introduce regressions on other devices, revert the new `REGION_POWER_UP_*_ALT` additions and the OCRProcessor precedence changes together.
  - If scan correctness regresses for real costumes/forms, review the new full-match suppression thresholds in `FullVariantMatcher.kt` and `VariantMergeLogic.kt` before reverting everything.
  - The scan latency improvement depends on two changes together: fast-pass HP/cost parsing and no longer forcing the detailed pass for missing `caughtDate`.
## 2026-04-10 17:20 - rollback note for v1.1.6 live scan follow-up
Files touched:
- app/src/main/java/com/pokerarity/scanner/util/ocr/ScanAuthorityLogic.kt
- app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt
- app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt
- app/src/test/java/com/pokerarity/scanner/ScanAuthorityLogicTest.kt
- app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt
- gradle.properties
Rollback strategy:
- Revert commit that introduces version 1.1.6 if same-family scoped pass becomes too conservative or Spinda-style shiny forms over-promote.
- Watch for regressions in same-family rescue cases and authoritative remap shiny gating.
Validation evidence:
- Focused tests green
- release APK installed and launched
