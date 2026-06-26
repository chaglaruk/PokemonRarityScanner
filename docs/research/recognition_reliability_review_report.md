# Recognition Reliability Final Review Report

Date: 2026-06-26
Branch: `feature/collector-intelligence-phase-2c-2e-scan-decision`
Scope: final review of the dirty A-G recognition reliability worktree.

## Recommendation

Ready for human review and commit as one recognition reliability branch.

No blocking compile, test, lint, privacy, or architecture regressions were found during this final audit. No runtime code fixes were made during the review. The only review-time change is this report.

## Dirty Worktree Summary

Tracked modified files:

- `app/build.gradle.kts`
- `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt`
- `app/src/main/java/com/pokerarity/scanner/data/model/PokemonData.kt`
- `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
- `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ScanErrorHandler.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt`
- `app/src/test/java/com/pokerarity/scanner/ScanTelemetryRepositoryTest.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporterTest.kt`
- `docs/AI_RUN_REPORT.md`

Untracked scope files:

- `app/src/main/java/com/pokerarity/scanner/util/ocr/FieldCandidateNormalizer.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanConfidenceGate.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanDiagnosticModels.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenClassifier.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenGeometry.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenGeometryBuilder.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesFormResolver.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/FieldCandidateNormalizationTest.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/ScanConfidenceGateTest.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/ScreenClassifierTest.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/ScreenGeometryBuilderTest.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/SpeciesFormResolverTest.kt`
- `docs/research/`
- `scripts/audit_scan_fixtures.ps1`

`git diff --stat` reported 12 tracked files changed with 2036 insertions and 161 deletions. Untracked A-G files are not included in that stat.

No suspicious unrelated changes were found. `app/build.gradle.kts` changed only for test task locale stabilization; no new dependencies, signing changes, or application id changes were found in this review.

## Files Grouped By Phase

Diagnostics:

- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanDiagnosticModels.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporterTest.kt`

Screen classifier and geometry:

- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenClassifier.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenGeometry.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenGeometryBuilder.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/ScreenClassifierTest.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/ScreenGeometryBuilderTest.kt`

OCR candidate normalization:

- `app/src/main/java/com/pokerarity/scanner/util/ocr/FieldCandidateNormalizer.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/FieldCandidateNormalizationTest.kt`

Species and form resolver:

- `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesFormResolver.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/SpeciesFormResolverTest.kt`

Confidence gate:

- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanConfidenceGate.kt`
- `app/src/main/java/com/pokerarity/scanner/data/model/PokemonData.kt`
- `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ScanErrorHandler.kt`
- `app/src/test/java/com/pokerarity/scanner/util/ocr/ScanConfidenceGateTest.kt`

Telemetry and privacy:

- `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
- `app/src/test/java/com/pokerarity/scanner/ScanTelemetryRepositoryTest.kt`

Fixture scripts, tests, and docs:

- `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt`
- `scripts/audit_scan_fixtures.ps1`
- `docs/AI_RUN_REPORT.md`
- `docs/research/calcy_iv_recognition_gap_report.md`
- `docs/research/recognition_reliability_final_report.md`
- `docs/research/scan_fixture_labeling_plan.md`
- `docs/research/recognition_reliability_review_report.md`

## Architecture Audit Result

The reviewed scan path is:

`ScreenCaptureService -> ScanManager -> OCRProcessor -> ScreenClassifier -> ScreenGeometryBuilder -> field-specific OCR candidates -> ScanFrameFusion -> SpeciesFormResolver / SpeciesRefiner -> ScanConsistencyGate -> visual / variant logic -> ScanConfidenceGate -> rarity / overlay / save / telemetry / local diagnostics`.

Findings:

- `ScanManager` runs OCR diagnostics and field candidates before species/form refinement.
- `SpeciesRefiner.refine(...)` receives Phase C field candidates and uses `SpeciesFormResolver` conservatively.
- Existing `ScanConsistencyGate` remains in place before the final confidence gate.
- `ScanConfidenceGate.evaluate(...)` runs before rarity scoring, overlay broadcast, save, and telemetry enqueue.
- `ACCEPT` and `ACCEPT_LOW_CONFIDENCE` are the only decisions that allow overlay/save.
- `RETRY`, `UNCERTAIN`, and `REJECT_NOT_POKEMON_SCREEN` are exported as local retry diagnostics and return before rarity scoring, overlay, save, or telemetry.
- `collectionSafe` is true only for `ACCEPT`.
- Legacy `ScreenRegions` fallback remains in `ScreenGeometryBuilder` for weak anchor/classifier cases.
- No UI rewrite was found.

Residual architecture risk:

- The confidence gate is intentionally conservative. Until fixtures are labeled with expected screen/decision values and device QA is complete, some valid but weak scans may be downgraded to retry or uncertain.

## Privacy And Security Audit Result

Verified:

- No Calcy decompiled folder access, copying, or integration was performed during this final review.
- No Calcy code, assets, databases, traineddata, constants, thresholds, identifiers, or strings were found in the reviewed changes.
- No new third-party datasets were added.
- ML Kit remains the OCR provider.
- No Tesseract/OpenCV/native OCR dependency was added by this work. Existing OpenCV dependency remains unchanged.
- No external network calls were added.
- Live telemetry still passes `screenshotPath = null` from `ScanManager`.
- `ScanTelemetryRepository` payload debug info omits raw OCR text, diagnostic directories/files, resolver trace, and scan decision trace.
- Local diagnostic JSON can include OCR candidates, resolver trace, and gate trace, but this remains local-only through `OcrDiagnosticsExporter`.
- Screenshot path sanitization is preserved in `ScanManager.sanitizeScreenshotPaths(...)`.
- No broad storage writes were found beyond existing local diagnostic/export paths.
- `AndroidManifest.xml` has no CAMERA permission.
- INTERNET permission exists in the manifest, but it was not introduced by this work.
- No application id, release signing, keystore, or release config changes were found.

Telemetry tests now cover omission of resolver and scan decision traces from upload payload debug info.

## Fixture Audit Result

`.\scripts\audit_scan_fixtures.ps1` passed.

Summary:

- `cases=47`
- `fixtures=47`
- `strict=16`
- `all_null_exploratory=28`
- `expected_species=19`
- `expected_form=0`
- `expected_cp=19`
- `expected_hp=17`
- `expected_appraisal_fields=0`
- `expected_screen_type=0`
- `expected_confidence_decision=0`
- `expected_min_confidence=0`
- `expected_may_show_overlay=0`
- `expected_may_save_scan=0`
- `missing_fixture_files=0`

Readiness:

- The audit script clearly reports missing labels by priority.
- The regression test schema supports expected screen type, confidence decision, minimum confidence, overlay eligibility, and save eligibility.
- No labels appear fabricated in this review.
- `docs/research/scan_fixture_labeling_plan.md` is actionable and lists the first fixtures and exact fields to label.

## Test Results

Focused tests:

- `.\gradlew.bat :app:testDebugUnitTest --tests "*ScanConfidenceGateTest*" --no-daemon --console=plain` - passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*ScreenClassifierTest*" --no-daemon --console=plain` - passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*ScreenGeometryBuilderTest*" --no-daemon --console=plain` - passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*FieldCandidateNormalizationTest*" --no-daemon --console=plain` - passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*SpeciesFormResolverTest*" --no-daemon --console=plain` - passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*OcrDiagnosticsExporterTest*" --no-daemon --console=plain` - passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*ScanTelemetryRepositoryTest*" --no-daemon --console=plain` - passed.

Full validation:

- `git status --short` - dirty worktree contains expected A-G files.
- `git diff --stat` - reviewed.
- `git diff --check` - passed.
- `.\scripts\audit_scan_fixtures.ps1` - passed.
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` - passed.
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` - passed.
- `.\gradlew.bat :app:lintDebug --no-daemon --console=plain` - passed.
- `.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain` - passed.

Connected Android tests:

- `adb devices` reported no attached devices.
- `.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain` was skipped because no device or emulator was connected.

Non-blocking environment note:

- Gradle emitted the existing Android SDK XML version warning. It did not fail any build or test command.

## Regression-Risk Review

No clear blocking regression was found for:

- over-strict confidence causing compile/test failures
- accepted scans missing overlay/save in the reviewed gate path
- low-confidence scans being marked collection-safe
- marker strings resolving as species
- RawText-only evidence becoming ACCEPT
- geometry fallback invalidating all scans
- StorageList or Transition screens saving confident results
- telemetry uploading new diagnostic fields
- local diagnostics serialization failures

Remaining risk:

- The most important residual product risk is calibration, not code correctness: real-device QA on Samsung S25 and Pixel 4a is still needed to verify that the conservative gate accepts enough valid Pokemon detail/appraisal scans while rejecting transition/storage/non-Pokemon screens.

## Fixes Made During Review

No runtime fixes were made. This review added only `docs/research/recognition_reliability_review_report.md`.

## Remaining Manual QA Tasks

- Run live scan smoke tests on Samsung S25 and Pixel 4a.
- Capture detail, appraisal, scrolled detail, encounter, storage list, transition, and non-Pokemon screens.
- Confirm ACCEPT scans show overlay and save correctly.
- Confirm RETRY, UNCERTAIN, and REJECT_NOT_POKEMON_SCREEN do not create confident history or collection-safe conclusions.
- Confirm local diagnostics export contains OCR candidates, resolver trace, and gate trace.
- Confirm telemetry payloads remain metadata-only in a consented debug/session path.
- Label the top fixtures from `docs/research/scan_fixture_labeling_plan.md` with expected screen type, species, CP, HP, and confidence decision.

## Recommended Commit Message

`Add recognition confidence gate and fixture hardening`

## Recommended PR Title

`Recognition reliability: confidence gate, diagnostics, and regression hardening`

## Recommended PR Body Outline

- Summary
  - Adds conservative scan confidence decision layer.
  - Keeps legacy crop fallback and ML Kit OCR.
  - Extends local-only diagnostics and fixture audit coverage.
- Safety
  - No Calcy usage.
  - No telemetry behavior change.
  - No screenshots, raw OCR, resolver traces, or gate traces uploaded.
- Tests
  - Focused OCR/classifier/geometry/resolver/gate/telemetry tests.
  - Full unit tests, debug assemble, lint, and android-test Kotlin compilation passed.
- Manual QA
  - Samsung S25 and Pixel 4a smoke testing.
  - Fixture labeling follow-up.
