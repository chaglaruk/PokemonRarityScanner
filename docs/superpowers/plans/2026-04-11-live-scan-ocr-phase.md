# Live Scan OCR Reliability Phase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise live OCR reliability for CP, HP, stardust, and candy without replacing the current OCR stack.

**Architecture:** Keep the existing Tesseract pipeline but make it multi-candidate and evidence-arbitrated. Candidate regions will be tuned around current real-device screenshots, then normalized by parser quality so the chosen value is explainable and diagnosable.

**Tech Stack:** Kotlin, Tesseract, bitmap preprocessing, JVM tests, adb diagnostics bundles.

---

### Task 1: HP Reliability

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenRegions.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParseUtils.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/util/ocr/TextParseUtilsRegressionTest.kt`

- [ ] Add failing slash-pair and clipped-digit tests from live bundles.
- [ ] Verify the new tests fail.
- [ ] Expand HP region candidates and parser heuristics minimally.
- [ ] Verify the tests pass.

### Task 2: Power-Up Cost Reliability

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenRegions.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/TextParserPowerUpCostTest.kt`

- [ ] Add failing tests from live row-pair noise cases.
- [ ] Verify the tests fail.
- [ ] Add row sweep candidates, low-contrast numeric processing, and parse arbitration.
- [ ] Verify the tests pass.

### Task 3: Diagnostics and Selection Audit

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`

- [ ] Extend summary.json with chosen/rejected OCR candidates and source rationale.
- [ ] Ensure broad/insufficient solves record enough OCR evidence for later tuning.
- [ ] Keep the export incremental; do not invent a second diagnostics system.
