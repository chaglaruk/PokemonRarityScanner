# Scan Authority Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop scan layers from overriding each other by introducing an authoritative scan decision layer for species, variant, date, and HP fields.

**Architecture:** Keep OCR, species refinement, variant classification, and visual detection as separate producers. Add one pure-logic authority layer that decides which producer is allowed to set each final field. Use tests first so species drift and low-confidence variant/date overrides cannot regress.

**Tech Stack:** Kotlin, Android, Jetpack, JUnit4, existing Android instrumented regression suite

---

### Task 1: Add authority logic for species override gating

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanAuthorityLogic.kt`
- Create: `app/src/test/java/com/pokerarity/scanner/ScanAuthorityLogicTest.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Write minimal implementation**
- [ ] **Step 4: Run test to verify it passes**

### Task 2: Route scan pipeline through authority layer

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`

- [ ] **Step 1: Write failing/coverage test for final field selection**
- [ ] **Step 2: Implement authority-based routing**
- [ ] **Step 3: Keep raw trace fields intact for debugging**
- [ ] **Step 4: Run targeted tests**

### Task 3: Add date and HP confidence gates

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanAuthorityLogic.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- Modify: `app/src/androidTest/java/com/pokerarity/scanner/ScanManagerPolicyTest.kt`

- [ ] **Step 1: Add tests for weak date and HP not overriding authoritative scan**
- [ ] **Step 2: Implement minimal gates**
- [ ] **Step 3: Verify targeted tests**

### Task 4: Verify, build, deploy, document

**Files:**
- Modify: `rapor.md`

- [ ] **Step 1: Run unit tests**
- [ ] **Step 2: Run targeted instrumented tests**
- [ ] **Step 3: Build debug APK**
- [ ] **Step 4: Install on device and launch**
- [ ] **Step 5: Append dated report entry**
