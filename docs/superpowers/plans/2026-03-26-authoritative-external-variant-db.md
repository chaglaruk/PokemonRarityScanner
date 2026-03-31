# Authoritative External Variant DB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an authoritative external variant/event database and simplify result UI by removing IV and expanding the narrative explanation area.

**Architecture:** Keep the current matcher in place, but add a merged external variant DB for explanation-time authority. Remove IV from UI immediately. Expand narrative formatting and reserve more surface area for explanation text.

**Tech Stack:** Kotlin, Jetpack Compose, Python generators, Android assets, JUnit

---

### Task 1: Remove IV from result surfaces

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`

- [ ] Remove IV block from overlay result card
- [ ] Remove IV block from full result screen
- [ ] Increase narrative textbox size, padding, and font weight

### Task 2: Improve narrative explanation formatting

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/model/Pokemon.kt`
- Modify: `app/src/test/java/com/pokerarity/scanner/PokemonAnalysisFormattingTest.kt`

- [ ] Expand narrative wording to 2-3 sentences
- [ ] Mention event name, costume/form, and caught date more explicitly when available
- [ ] Keep score in the final sentence

### Task 3: Add authoritative external DB generator

**Files:**
- Create: `scripts/generate_authoritative_variant_db.py`
- Create: `app/src/main/assets/data/authoritative_variant_db.json`
- Create: `app/src/test/java/com/pokerarity/scanner/AuthoritativeVariantDbTest.kt`

- [ ] Merge local catalog inputs with external Bulbapedia event rows
- [ ] Normalize rows by sprite/species/variant ids
- [ ] Produce a runtime asset with explicit event metadata

### Task 4: Wire runtime explanation lookup

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/data/model/AuthoritativeVariantEntry.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantDbLoader.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`

- [ ] Load the external DB at runtime
- [ ] Prefer external DB event/costume wording over legacy catalog wording
- [ ] Reject exact event wording when caught date predates event window

### Task 5: Verify and report

**Files:**
- Modify: `rapor.md`

- [ ] Run targeted unit tests
- [ ] Run `assembleDebug`
- [ ] Update report with build and deploy status
