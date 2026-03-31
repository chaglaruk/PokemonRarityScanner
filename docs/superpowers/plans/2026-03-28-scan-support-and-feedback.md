# Scan Support And Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add scan confidence, event/date guard visibility, user feedback capture, and telemetry export filters without destabilizing the scan pipeline.

**Architecture:** Keep scan decisions in the existing matcher/rarity path, derive UI support fields from `FullVariantMatch` and sanitized explanation metadata, and route user feedback through the existing telemetry backend. Avoid changing scan decision semantics except for exposing mismatch guards and exactness state.

**Tech Stack:** Kotlin, Jetpack Compose, Room, existing telemetry HTTP uploader, PHP/MySQL export API.

---

### Task 1: Decision support model

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/data/model/ScanDecisionSupport.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/model/RarityScore.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`

- [ ] Add a model that carries event confidence, mismatch guard, why-not-exact, and scan confidence.
- [ ] Populate it from `RarityCalculator` using sanitized vs raw event metadata.

### Task 2: UI surfaces

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/model/Pokemon.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/ui/components/DecisionSupportComponents.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt`

- [ ] Pass decision support and telemetry upload id through the existing result intent path.
- [ ] Show event confidence, mismatch guard, why-not-exact, and scan confidence in both result surfaces.
- [ ] Add feedback buttons for wrong species/event/costume/shiny.

### Task 3: Telemetry feedback and export filters

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/model/ScanTelemetryPayload.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/data/model/ScanFeedbackPayload.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryCoordinator.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt`
- Modify: `web/scan-telemetry/api/bootstrap.php`
- Create: `web/scan-telemetry/api/scan-feedback.php`
- Modify: `web/scan-telemetry/api/scan-export.php`
- Modify: `web/scan-telemetry/schema.sql`

- [ ] Make upload ids available to the UI.
- [ ] Add a feedback endpoint and client call path.
- [ ] Add export filters for costume/shiny/event-confidence/mismatch/feedback.

### Task 4: Verification and rollout

**Files:**
- Modify: `app/src/test/java/com/pokerarity/scanner/ScanTelemetryPayloadTest.kt`
- Modify: `rapor.md`

- [ ] Run targeted JVM tests.
- [ ] Build debug and androidTest APKs.
- [ ] Install debug APK to device.
- [ ] Record rollout evidence in `rapor.md`.
