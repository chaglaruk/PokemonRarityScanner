# Stitch UI Adaptation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adapt dashboard and result UI to the approved Stitch direction while preserving current behavior and data flow.

**Architecture:** Keep existing screen entry points and navigation, add only small shared Compose primitives for common Stitch chrome, and update screen composition in place. Use build verification plus focused existing tests for regression protection.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, existing app theme/components

---

### Task 1: Write the approved design to repo

**Files:**
- Create: `docs/superpowers/specs/2026-04-09-stitch-ui-adaptation-design.md`
- Create: `docs/superpowers/plans/2026-04-09-stitch-ui-adaptation.md`

- [ ] Save design doc
- [ ] Save implementation plan

### Task 2: Add shared Stitch chrome primitives

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/ui/components/StitchNavigation.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Add bottom navigation shell and item model
- [ ] Add any new string resources needed for nav labels
- [ ] Keep behavior callback-driven, not route-owning

### Task 3: Update dashboard screen

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/screens/CollectionScreen.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/main/MainActivity.kt`

- [ ] Keep data flow intact
- [ ] Add bottom bar and wire actions
- [ ] Tune spacing and card hierarchy to align with Stitch

### Task 4: Update result screens

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt`

- [ ] Align result hierarchy with Stitch
- [ ] Keep EXACT/RANGE/INSUFFICIENT visible
- [ ] Keep save/share/close behavior intact
- [ ] Add bottom bar to full result screen only

### Task 5: Verify and ship

**Files:**
- Modify: `artifacts/agent_worklog.md`
- Modify: `artifacts/rollback_notes.md`

- [ ] Run focused existing tests
- [ ] Build release APK
- [ ] Install and launch on device
- [ ] Publish release APK with changelist
- [ ] Commit and push source changes
