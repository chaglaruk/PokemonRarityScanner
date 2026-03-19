# Scan Variant Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining measured scan regressions by fixing weak species overrides, promoting trustworthy same-species costume matches, and restoring caught-date extraction in the shared live/test pipeline.

**Architecture:** The live scan pipeline and `ScanRegressionTest` already share `VariantDecisionEngine`. This plan keeps that architecture and applies minimal changes at the decision points that still fail under strict exported fixtures.

**Tech Stack:** Android, Kotlin, Android instrumented tests, exported PNG fixtures, OCR + asset-backed variant classifier.

---

### Task 1: Lock the current failing regression set

**Files:**
- Modify: `app/src/androidTest/assets/scan_regression_cases.json`
- Test: `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt`

- [ ] **Step 1: Run the failing regression suite**

Run: `cmd /c ".\\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain"`

Expected: FAIL with strict cases that still include `Slowpoke regular`, `Slowpoke costume`, `Pikachu costume`, or `Butterfree costume+shiny`.

- [ ] **Step 2: Capture the failure details**

Run: `adb -s RFCY11MX0TM logcat -d -v time | Select-String "ScanRegressionTest|Species refined|Classifier|VariantClassifier"`

Expected: evidence showing the failing field for each remaining strict case.

### Task 2: Stop weak species overrides

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt`
- Test: `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt`

- [ ] **Step 1: Confirm the failing case**

Target: `live_variant_batch_20260318_slowpoke_regular`

Expected failure shape: species resolves to `Slowbro` even though text and fit evidence are weak.

- [ ] **Step 2: Add the minimal guard**

Change `SpeciesRefiner` so it keeps the current species when:
- total replacement score is low,
- text contribution is weak,
- move evidence is absent,
- and the current species is not clearly impossible.

- [ ] **Step 3: Re-run the regression suite**

Run: `cmd /c ".\\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain"`

Expected: `live_variant_batch_20260318_slowpoke_regular` passes or the failure set shrinks without introducing new strict failures.

### Task 3: Promote trustworthy species-scoped costume matches

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`
- Test: `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt`

- [ ] **Step 1: Confirm the failing case**

Target: `live_variant_batch_20260318_pikachu_costume`

Expected failure shape: species-scoped classifier says `costume` for `Pikachu`, but confidence stays below the current gate and visual logic falls through to the wrong result.

- [ ] **Step 2: Add the minimal promotion rule**

Allow species-scoped costume promotion when:
- the resolved species already matches the OCR/refined species,
- the classifier result is costume-like,
- global or family-level evidence also supports a non-base costume branch,
- confidence is in the measured near-pass band instead of requiring the full generic threshold.

- [ ] **Step 3: Re-run the regression suite**

Run: `cmd /c ".\\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain"`

Expected: `live_variant_batch_20260318_pikachu_costume` passes without breaking `Butterfree costume+shiny`.

### Task 4: Restore caught-date extraction on exported fixtures

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt` or `ScreenRegions.kt` only if log evidence requires it
- Test: `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt`

- [ ] **Step 1: Confirm the remaining date-only failures**

Target cases: `live_variant_batch_20260318_slowpoke_costume`, `live_variant_batch_20260318_butterfree_costume_shiny`

- [ ] **Step 2: Implement the smallest date OCR fix**

Prefer parser changes before crop changes. Only widen the OCR path if the parser cannot consume the badge strings currently produced by tests.

- [ ] **Step 3: Re-run the regression suite**

Run: `cmd /c ".\\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain"`

Expected: all strict live variant cases pass.

### Task 5: Verify, document, and deploy

**Files:**
- Modify: `rapor.md`

- [ ] **Step 1: Run final verification**

Run:
- `cmd /c ".\\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain"`
- `cmd /c ".\\gradlew.bat assembleDebug --console=plain"`

Expected: regression suite passes, debug build succeeds.

- [ ] **Step 2: Record the work**

Append a dated entry to `rapor.md` describing:
- the shared live/test classifier path,
- the specific root causes fixed,
- the exact regression fixtures used as the gate,
- the verification commands and outcomes.
