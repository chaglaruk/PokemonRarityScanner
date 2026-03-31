# Result UI, Share, Telemetry, and Event Metadata Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove low-value confidence UI, fix result feedback layout, share a visual scan card, verify telemetry ingestion, and improve date-safe event naming.

**Architecture:** Keep telemetry/debug data intact for backend filtering, but stop exposing noisy confidence UI in the result surfaces. Move the user-visible result experience toward localized strings and image-based sharing while tightening event metadata exposure so catch dates cannot imply impossible events.

**Tech Stack:** Android Compose, Kotlin, FileProvider sharing, PHP telemetry backend, JSON metadata generators, Gradle/JUnit.

---

### Task 1: Clean Result Support UI

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/components/DecisionSupportComponents.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`

- [ ] Replace visible event/scan confidence UI with mismatch/why-not-exact only.
- [ ] Convert feedback chips into equal-width buttons in a stable grid.
- [ ] Keep telemetry hooks unchanged.

### Task 2: Share Result as Card

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/ui/share/ResultShareRenderer.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt`

- [ ] Add a shared render-to-image helper for result sharing.
- [ ] Switch `ResultActivity` share from plain text to image+text.
- [ ] Reuse the same helper for overlay sharing to avoid duplicated capture logic.

### Task 3: Localization Foundation

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-tr/strings.xml`
- Modify: result/overlay/component files that still hardcode labels

- [ ] Move visible screen/button/section strings into resources.
- [ ] Add Turkish translations for current result-related text.
- [ ] Keep runtime event metadata as-is; only localize app-owned copy in this slice.

### Task 4: Date-Safe Event Metadata

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityExplanationFormatter.kt`
- Modify: `scripts/variant_token_aliases.json`
- Modify: metadata generator scripts if needed
- Test: `app/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt`

- [ ] Prevent generic paths from surfacing token-like or impossible event names.
- [ ] Improve human-readable alias formatting for known token families.
- [ ] Keep exact event text only for date-safe authoritative paths.

### Task 5: Telemetry Verification

**Files:**
- Modify: `web/scan-telemetry/README.md` if needed

- [ ] Verify live export endpoint behavior with current API key.
- [ ] Report whether friend APK test uploads are currently retrievable.
- [ ] If server config mismatch persists, document exact required fix.

### Task 6: Verification

**Files:**
- Modify: `rapor.md`

- [ ] Run targeted unit tests.
- [ ] Run build.
- [ ] Install APK if device is reachable.
- [ ] Update report with concrete results and remaining risks.
