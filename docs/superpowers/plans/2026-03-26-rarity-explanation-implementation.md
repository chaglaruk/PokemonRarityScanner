# Rarity Explanation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace numeric rarity breakdown rows with human-readable “why this is valuable” reasons while keeping the total score.

**Architecture:** Keep score calculation intact, enrich `RarityScore.explanation` with catalog-aware reasons, convert UI analysis items into text-first reason rows, and render those rows in overlay and result screens.

**Tech Stack:** Kotlin, Jetpack Compose, existing rarity calculator, authoritative variant catalog assets.

---

### Task 1: Add explanation-focused UI model behavior

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/model/Pokemon.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/PokemonAnalysisFormattingTest.kt`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Add title/detail capable analysis item formatting**
- [ ] **Step 4: Run test to verify it passes**

### Task 2: Generate catalog-aware rarity explanation text

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/model/RarityScore.kt` (only if helper structure is needed)
- Test: `app/src/test/java/com/pokerarity/scanner/RarityExplanationFormattingTest.kt`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Add human-readable explanation generation for event/costume/shiny/form/date reasons**
- [ ] **Step 4: Run test to verify it passes**

### Task 3: Replace numeric breakdown UI with explanation UI

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/components/overlay/OverlayComponents.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/PokemonAnalysisFormattingTest.kt`

- [ ] **Step 1: Write the failing test for the data shape needed by UI**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Render explanation rows and rename section label**
- [ ] **Step 4: Run unit tests and assemble debug**

### Task 4: Update runtime wiring and documentation

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt`
- Modify: `rapor.md`

- [ ] **Step 1: Verify intent/builders still pass explanation items correctly**
- [ ] **Step 2: Update report with date-stamped entry**
- [ ] **Step 3: Run final verification**

