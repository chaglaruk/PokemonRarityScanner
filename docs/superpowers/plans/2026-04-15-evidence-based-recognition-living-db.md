# Evidence-Based Recognition & Living DB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop startup auto-scan, remove speculative event/costume hallucinations, and add a scheduled living-metadata update path with runtime sync.

**Architecture:** Keep ML Kit and OpenCV, but tighten the decision flow so variant/event resolution is evidence-based. Variant candidates must be supported by exact species, valid caught/live windows, and strong visual signature evidence. Remote metadata already exists; extend it with a generated `master_pokedex.json` and scheduled GitHub automation rather than inventing a second sync system.

**Tech Stack:** Kotlin, Android, Room, GitHub Actions, Python metadata generators, ML Kit, existing OpenCV/image-hash utilities.

---

### Task 1: Lock startup into idle and remove automatic capture

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/main/MainActivity.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureService.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/ScreenCaptureManagerTest.kt`

- [ ] Write failing tests for manual-start capture policy
- [ ] Verify they fail
- [ ] Make manual start arm the service without auto-capture
- [ ] Keep explicit auto-start paths opt-in only
- [ ] Run focused tests

### Task 2: Remove speculative live-event/family costume rescue from variant resolution

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt`

- [ ] Write failing tests for Torchic/Pikachu-style speculative event/costume resolution
- [ ] Verify they fail
- [ ] Remove active-live candidate injection from the variant path
- [ ] Remove family costume support promotion from the variant path
- [ ] Require date-backed or concrete window-backed event labels
- [ ] Make weak same-species remaps fall back to base
- [ ] Run focused tests

### Task 3: Add living metadata and accessory signature runtime support

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/data/model/MasterPokedex.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/data/repository/MasterPokedexLoader.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/CostumeSignatureStore.kt`
- Modify: `metadata_manifest.json`
- Create: `scripts/generate_master_pokedex.py`
- Modify: `scripts/generate_costume_signatures.py`
- Test: `app/src/test/java/com/pokerarity/scanner/MasterPokedexLoaderTest.kt`

- [ ] Write failing tests for remote/local master pokedex loading
- [ ] Verify they fail
- [ ] Generate and load `master_pokedex.json`
- [ ] Extend signature data with pHash/head-pHash support
- [ ] Prefer remote `master_pokedex.json`/`costume_signatures.json` when present
- [ ] Run focused tests

### Task 4: Automate metadata refresh in CI

**Files:**
- Create: `.github/workflows/refresh-living-pokedex.yml`
- Modify: `metadata_manifest.json`
- Modify: `README.md`
- Modify: `scripts/README.md`

- [ ] Add scheduled workflow that refreshes snapshots and generated metadata
- [ ] Make manifest version update deterministic
- [ ] Document the living metadata pipeline
- [ ] Validate workflow YAML locally for syntax

### Task 5: Telemetry endpoint correctness and offline drain behavior

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/ScanTelemetryUploaderTest.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/ScanTelemetryRepositoryTest.kt`

- [ ] Write failing tests for probe URL selection and offline summary endpoint tracking
- [ ] Verify they fail
- [ ] Remove the dead legacy probe path from normal operation
- [ ] Stage offline rows against the configured endpoint
- [ ] Run focused tests

### Task 6: Verify, build, install, release, and document

**Files:**
- Modify: `gradle.properties`
- Modify: `artifacts/agent_worklog.md`
- Modify: `artifacts/rollback_notes.md`

- [ ] Run focused test suites
- [ ] Run full `:app:testDebugUnitTest`
- [ ] Build release APK
- [ ] Install and open APK on device
- [ ] Publish release asset
- [ ] Update worklog and rollback notes
