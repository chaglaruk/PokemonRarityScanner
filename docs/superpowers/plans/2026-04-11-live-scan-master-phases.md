# Live Scan Master Phases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize live scan correctness end-to-end by prioritizing OCR input reliability, then variant authority, IV surfacing, latency, diagnostics, and release verification.

**Architecture:** Keep the existing scan pipeline and solver architecture. Improve it by adding evidence-driven OCR candidate sweeps, stricter parser arbitration, stronger species/variant authority guards, clearer IV explanations, richer diagnostics, and lower-cost classification paths when OCR is already trustworthy.

**Tech Stack:** Kotlin, Jetpack Compose, Tesseract OCR, Room, Hilt, local PowerShell release publishing.

---

## File Map

- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenRegions.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParseUtils.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanAuthorityLogic.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VisualFeatureDetector.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`
- Modify: `app/src/test/java/com/pokerarity/scanner/TextParserPowerUpCostTest.kt`
- Modify: `app/src/test/java/com/pokerarity/scanner/util/ocr/TextParseUtilsRegressionTest.kt`
- Modify: `app/src/test/java/com/pokerarity/scanner/ScanAuthorityLogicTest.kt`
- Modify: `app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt`
- Modify: `app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt`
- Modify: `app/src/test/java/com/pokerarity/scanner/ScanManagerDetailedPassTest.kt`
- Modify: `artifacts/agent_worklog.md`
- Modify: `artifacts/rollback_notes.md`

### Phase 1: OCR Evidence + Input Reliability

- [ ] Write failing tests for noisy HP slash-pair repair, noisy row-pair dust/candy recovery, and OCR-locked species speed gating.
- [ ] Run the focused tests and confirm they fail for the intended reason.
- [ ] Add evidence-driven OCR candidate sweeps for HP, stardust, candy, and shared power-up row.
- [ ] Add source arbitration so dedicated, row-pair, and fallback OCR compete by parse quality instead of fixed single-pass order.
- [ ] Improve low-contrast numeric preprocessing only for HP/power-up numeric regions.
- [ ] Re-run focused OCR parser tests and keep them green.

### Phase 2: Variant / Shiny / Event Authority

- [ ] Write failing tests for classifier-overreach cases and weak/speculative event metadata cases.
- [ ] Tighten classifier-only shiny/costume/form promotion when stronger OCR/visual evidence disagrees.
- [ ] Keep same-species authoritative remaps usable without allowing speculative event labeling.
- [ ] Re-run focused variant authority tests and keep them green.

### Phase 3: IV Surfacing + Diagnostics

- [ ] Write failing tests for RANGE / INSUFFICIENT explanation clarity where needed.
- [ ] Enrich `whyNotExact` with candidate count, level range, and missing/weak inputs.
- [ ] Expand diagnostics export to include OCR candidate sources, chosen values, rejected candidates, and solve details.
- [ ] Keep RANGE honest and useful rather than collapsing it into failure language.

### Phase 4: Latency Reduction

- [ ] Write failing tests for skipping unnecessary detailed/classifier passes when OCR is locked enough.
- [ ] Skip global classifier work when OCR species is explicitly locked and species-scoped classification is sufficient.
- [ ] Keep detailed OCR off the critical path unless CP/HP/cost confidence is genuinely weak.
- [ ] Add stage timing logs for OCR/classifier/solve/overlay boundaries.

### Phase 5: Release / Device / Verification

- [ ] Run focused unit tests for OCR, variant authority, IV solver, and scan authority.
- [ ] Build release APK locally with versioned product name.
- [ ] Install APK to the attached device and launch it.
- [ ] Publish the APK to GitHub Releases through the local release script.
- [ ] Update worklog and rollback notes with evidence, files touched, commands, and rollback guidance.
