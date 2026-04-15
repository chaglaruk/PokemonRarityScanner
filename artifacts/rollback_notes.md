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

## 2026-04-11 18:45 - Rollback note for OCR-first phase execution (v1.2.0)

Changed areas:
- Added lower/wide OCR regions for HP and power-up row capture.
- Relaxed low-contrast numeric preprocessing for power-up cost extraction.
- Expanded noisy merged power-up row parsing.
- Skipped global classifier work when OCR species is already exact and locked.
- Extended OCR diagnostics export and stage timing logs.
- Improved `RANGE` explanation text for same-level collisions.
- Bumped app version to `1.2.0` / `9`.

Rollback guidance:
- If OCR regressions appear, first revert:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenRegions.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt`
- If classifier latency/trace behavior regresses, revert:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanAuthorityLogic.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`
- If only user-facing explanation text is problematic, revert:
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`

Expected rollback result:
- Previous `v1.1.6` OCR/classifier behavior returns, including older diagnostics and longer global classifier path.

## 2026-04-11 19:25 - Rollback note for v1.3.0 OCR/memory/state pass

Changed areas:
- Added OpenCV + ML Kit dependencies and runtime OCR fallbacks.
- Added bitmap pooling in capture/decode paths.
- Added overlay state store plus settings-driven auto-copy and haptic feedback.
- Added PvP summary output and richer range metadata wiring through result surfaces.
- Tightened power-up row parsing so incompatible dust/candy pairs are rejected.
- Bumped version to `1.3.0` / `10`.

Rollback guidance:
- If startup size or native library load becomes a problem, first revert:
  - `app/build.gradle.kts`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/MLKitOcrProvider.kt`
- If scan latency or bitmap reuse causes regressions, revert together:
  - `app/src/main/java/com/pokerarity/scanner/service/BitmapPool.kt`
  - `app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureService.kt`
  - `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- If overlay behavior or quick actions regress, revert together:
  - `app/src/main/java/com/pokerarity/scanner/service/OverlayContract.kt`
  - `app/src/main/java/com/pokerarity/scanner/service/OverlayStateStore.kt`
  - `app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/local/ScanUiPreferences.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ClipboardService.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/HapticFeedbackManager.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/dialog/TelemetrySettingsDialog.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/main/MainActivity.kt`
- If only parser strictness regresses live OCR, revert:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParseUtils.kt`
  - `app/src/test/java/com/pokerarity/scanner/TextParserPowerUpCostTest.kt`

Validation evidence:
- Focused OCR/IV/authority tests green.
- Release build green.
- Release APK installed and launched on device.

## 2026-04-12 17:10 - Rollback note for v1.4.0 precision scanner pass

Changed areas:
- Added offline telemetry staging table and legacy/primary endpoint probe logic.
- Added HP slash-pair arbitration, OCR glyph recovery, appraisal bar extraction, and arc-point level hooks.
- Extended `PokemonData` and diagnostics with appraisal/arc metadata.
- Extended `IvCostSolver` for appraisal-driven exact IV and reliable-level narrowing.
- Tightened noisy dedicated candy parsing and bumped version to `1.4.0` / `11`.

Rollback guidance:
- If database migration/regression appears, revert together:
  - `app/src/main/java/com/pokerarity/scanner/data/local/db/AppDatabase.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/local/db/OfflineTelemetryEntity.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/local/db/OfflineTelemetryDao.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
- If appraisal/arc precision causes false narrowing, revert together:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/ArcPointAnalyzer.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/AppraisalBarAnalyzer.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/model/PokemonData.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/IvCostSolver.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
- If only OCR parse strictness regresses live candy reads, revert:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParseUtils.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt`
  - `app/src/test/java/com/pokerarity/scanner/TextParserPowerUpCostTest.kt`

Validation evidence:
- Focused OCR/IV/telemetry suite green.
- Live device logs confirm HP recovery improvement and expose remaining missing-stardust cases cleanly.

## 2026-04-12 18:35 - Rollback note for v1.5.0 live scan stabilization pass

Changed areas:
- Added CP-aware HP pair arbitration so implausible tiny slash pairs are rejected on high-CP scans.
- Extended power-up cost compatibility for current late-level dust/candy pairs such as `10,000 / 10` and `12,000 / 15`.
- Added ML Kit and row-only stardust rescue paths from the already-correct alternate power-up crop.
- Added angular-cluster confidence gating for arc points so noisy ring hits no longer masquerade as reliable levels.
- Relaxed detailed-pass triggering when only stardust is missing and parallelized classifier/visual passes to reduce latency.
- Extended telemetry debug payload with OCR confidence, calculation error margin, and contradiction-field hints.
- Bumped version to `1.5.0` / `12`.

Rollback guidance:
- If HP parsing regresses on live scans, revert together:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParseUtils.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
  - `app/src/test/java/com/pokerarity/scanner/util/ocr/TextParseUtilsRegressionTest.kt`
- If power-up cost parsing becomes too permissive or too strict, revert together:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
  - `app/src/test/java/com/pokerarity/scanner/TextParserPowerUpCostTest.kt`
- If level narrowing or exactness regresses due to arc confidence, revert together:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/ArcPointAnalyzer.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
  - `app/src/test/java/com/pokerarity/scanner/util/ocr/ArcPointAnalyzerTest.kt`
- If latency gains cause secondary-pass misses, revert:
  - `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
  - `app/src/test/java/com/pokerarity/scanner/ScanManagerDetailedPassTest.kt`
- If telemetry schema changes cause downstream issues, revert together:
  - `app/src/main/java/com/pokerarity/scanner/data/model/ScanTelemetryPayload.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
  - `app/src/test/java/com/pokerarity/scanner/ScanTelemetryPayloadTest.kt`

Validation evidence:
- Focused OCR/solver/telemetry regression suites green.
- Live diagnostics confirmed crop geometry was already correct; fixes targeted arbitration and parsing instead.

## 2026-04-13 14:10 - Rollback note for v1.6.0 infrastructure and live-event pass

Changed areas:
- Added explicit overlay idle reset on app startup/resume to eliminate stale or ghost result state.
- Changed telemetry queue semantics so metadata-only uploads can succeed, non-retryable failures become `BLOCKED`, and successful rows are deleted.
- Extended OCR diagnostics summaries with resolved recognition fields and selected-source context.
- Added live community-day ingestion through `EventContextManager` and surfaced active-event context in decision-support UI.
- Hardened local and workflow GitHub release upload paths with retry/overwrite behavior.
- Rewrote README coverage for root, app, docs, scripts, external, data, and util folders.
- Bumped version to `1.6.0` / `13`.

Rollback guidance:
- If startup idling regresses or legitimate overlay state is cleared unexpectedly, revert together:
  - `app/src/main/java/com/pokerarity/scanner/service/OverlayStateStore.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/main/MainActivity.kt`
  - `app/src/main/java/com/pokerarity/scanner/PokeRarityApp.kt`
  - `app/src/test/java/com/pokerarity/scanner/service/OverlayStateStoreTest.kt`
- If telemetry uploads stop draining or server expectations change, revert together:
  - `app/src/main/java/com/pokerarity/scanner/data/local/db/TelemetryUploadEntity.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/local/db/TelemetryUploadDao.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
  - `app/src/test/java/com/pokerarity/scanner/ScanTelemetryUploaderTest.kt`
- If diagnostics JSON causes downstream tooling issues, revert together:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt`
  - `app/src/test/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporterTest.kt`
- If live event labels or bonuses become noisy, revert together:
  - `app/src/main/java/com/pokerarity/scanner/data/repository/EventContextManager.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RarityUpdater.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/PokemonRepository.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/model/LiveEventContext.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/model/ScanDecisionSupport.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/components/DecisionSupportComponents.kt`
  - `app/src/test/java/com/pokerarity/scanner/data/repository/EventContextManagerTest.kt`
- If release publishing becomes unstable again, revert:
  - `.github/workflows/release-apk.yml`
  - `scripts/publish_github_release.ps1`

Validation evidence:
- Focused telemetry/diagnostics/event/IV/variant suite green before release build.

## 2026-04-13 15:10 - Rollback note for upcoming v1.7.0 recognition-first pivot

Planned changed areas:
- Removal of IV, appraisal, arc, and power-up-cost logic from production flow.
- Removal of Tesseract OCR and bundled traineddata.
- Rewrite of `OCRProcessor` toward ML Kit-first recognition.
- Simplification of result/overlay UI and telemetry to recognition/rarity/event focus.
- Addition of remote metadata snapshot sync for living DB behavior.

Rollback guidance:
- If ML Kit-only OCR regresses badly, restore together:
  - `app/build.gradle.kts`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/MLKitOcrProvider.kt`
  - `app/src/main/assets/tessdata/eng.traineddata`
- If the purge breaks result or telemetry compatibility, restore together:
  - `app/src/main/java/com/pokerarity/scanner/data/model/PokemonData.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/model/RarityScore.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/model/ScanTelemetryPayload.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
  - `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt`

## 2026-04-13 17:05 - Rollback note after v1.7.0 completion

- Recognition stack now depends on ML Kit only.
- Remote metadata sync depends on:
  - `metadata_manifest.json`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RemoteMetadataSyncManager.kt`
  - loader reset hooks in `RarityUpdater`, `RarityManifestLoader`, `VariantCatalogLoader`, `AuthoritativeVariantDbLoader`
- If recognition accuracy regresses badly, restore together:
  - `app/build.gradle.kts`
  - `app/src/main/java/com/pokerarity/scanner/ui/splash/SplashActivity.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/MLKitOcrProvider.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
  - `app/src/main/assets/tessdata/eng.traineddata`
- If the recognition-first UI is rejected, restore together:
  - `app/src/main/java/com/pokerarity/scanner/data/model/Pokemon.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
  - `app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt`
- If the Living DB sync causes stale or broken manifests, restore together:
  - `metadata_manifest.json`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RemoteMetadataSyncManager.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RarityUpdater.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
- If the post-scan recognition patch suppresses legitimate costumes or HP on high-CP species, inspect and revert together:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt`
  - `app/src/test/java/com/pokerarity/scanner/util/ocr/TextParseUtilsRegressionTest.kt`
  - `app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt`

## 2026-04-15 01:15 - Rollback note for v1.7.1 bugfix release

- Splash version is now runtime-bound. If the splash ever shows a wrong version again, restore together:
  - `app/src/main/res/layout/activity_splash.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/pokerarity/scanner/ui/splash/SplashActivity.kt`
- Telemetry config now seeds and normalizes the API key/base URL from build config. If telemetry auth regresses, inspect or revert together:
  - `app/build.gradle.kts`
  - `app/src/main/java/com/pokerarity/scanner/data/local/TelemetryConfigPreferences.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt`
- Startup queue cleanup now drops legacy rows that have missing or invalid screenshot paths instead of retrying 422 forever. If legitimate uploads are being dropped, inspect or revert together:
  - `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
  - `app/src/test/java/com/pokerarity/scanner/ScanTelemetryRepositoryTest.kt`
- Release asset publishing now uploads from a temp copy to avoid Windows file locks on the Gradle output APK. If release publishing regresses, inspect or revert:
  - `scripts/publish_github_release.ps1`
- Costume/event overreach is now gated much harder when there is no concrete event window. If real event costumes disappear, inspect or revert together:
  - `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantScoring.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt`
  - `app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt`
  - `app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt`
  - `app/src/test/java/com/pokerarity/scanner/LegacyVariantPathRemovalTest.kt`
