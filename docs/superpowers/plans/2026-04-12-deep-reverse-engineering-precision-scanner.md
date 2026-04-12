# Deep Reverse-Engineering & Precision Scanner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve live scan correctness by hardening OCR/arc/appraisal evidence capture, integrating exact signals into IV solving, adding resilience for telemetry connectivity, and tightening species recovery logic.

**Architecture:** Keep the current architecture stable. Extend the existing OCR/solver/telemetry pipeline with evidence-driven modules and guarded fallbacks rather than broad rewrites. Add exact-input channels (arc point, appraisal bars, fuzzy species recovery) incrementally and verify each with focused tests.

**Tech Stack:** Kotlin, Android, Compose, Room, ML Kit, OpenCV, existing OCR/solver pipeline.

---

### Task 1: Evidence Audit and Telemetry/Diagnostics Baseline

**Files:**
- Modify: `artifacts/agent_worklog.md`
- Modify: `artifacts/rollback_notes.md`
- Modify: `docs/superpowers/plans/2026-04-12-deep-reverse-engineering-precision-scanner.md`

- [ ] Capture latest adb logs and diagnostics bundle summaries.
- [ ] Verify current telemetry endpoint behavior with sandboxed HTTP.
- [ ] Record root-cause evidence before code changes.

### Task 2: Telemetry Resilience

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/data/local/db/OfflineTelemetryEntity.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/local/db/AppDatabase.kt`
- Create/Modify tests under `app/src/test/java/com/pokerarity/scanner/`

- [ ] Add failing tests for offline telemetry staging and payload alignment.
- [ ] Implement endpoint probe + offline staging.
- [ ] Verify tests pass.

### Task 3: CP Arc Precision Layer

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/IvCostSolver.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
- Create/Modify tests under `app/src/test/java/com/pokerarity/scanner/`

- [ ] Add failing tests for arc-dot detection math and reliable-level integration.
- [ ] Implement dot detection + angle->level mapping.
- [ ] Feed reliable level/CPM into solver as optional exact narrowing.
- [ ] Verify tests pass.

### Task 4: Appraisal Bar Exact IV Path

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenRegions.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/IvCostSolver.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
- Create/Modify tests under `app/src/test/java/com/pokerarity/scanner/`

- [ ] Add failing tests for appraisal anchor detection and bar-fill->IV mapping.
- [ ] Implement appraisal anchor and per-bar fill analysis.
- [ ] Short-circuit solver to exact attack/defense/stamina when appraisal evidence is strong.
- [ ] Verify tests pass.

### Task 5: Fuzzy Species Recovery

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanAuthorityLogic.kt`
- Modify: tests under `app/src/test/java/com/pokerarity/scanner/`

- [ ] Add failing tests for OCR misspellings and nickname-like inputs.
- [ ] Implement Levenshtein fallback and CP/HP-based confirmation guard.
- [ ] Verify tests pass.

### Task 6: Verification / Release

**Files:**
- Modify: `artifacts/agent_worklog.md`
- Modify: `artifacts/rollback_notes.md`

- [ ] Run focused tests for new and impacted paths.
- [ ] Build release APK.
- [ ] Install and launch on device.
- [ ] Commit, tag, push, and publish release asset.
