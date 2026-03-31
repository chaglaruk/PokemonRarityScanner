# Global Rarity & Legacy Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an authoritative external rarity metadata pipeline that merges live and historical Pokémon GO event data into app-readable assets without scraping at runtime.

**Architecture:** External sources are normalized offline into a `global_rarity_legacy_db.json` asset. The app consumes that asset through a small loader layer and uses it for rarity explanations and future legacy scoring. Existing scan/full-matcher code remains source-of-truth for recognition; this engine adds historical/event context, not visual classification.

**Tech Stack:** Python data generators, JSON assets, Kotlin loader/model classes, existing authoritative variant DB pipeline.

---

### Task 1: Source Registry

**Files:**
- Create: `scripts/external_source_registry.json`
- Create: `docs/superpowers/specs/2026-03-27-global-rarity-legacy-engine.md`
- Modify: `rapor.md`

- [ ] **Step 1: Define supported sources**
- [ ] **Step 2: Mark capabilities per source**
- [ ] **Step 3: Record refresh cadence and trust level**
- [ ] **Step 4: Document rejected prompt parts (`Antigravity`, runtime scraping)**

### Task 2: Offline Merge Asset

**Files:**
- Create: `scripts/generate_global_rarity_legacy_db.py`
- Create: `app/src/main/assets/data/global_rarity_legacy_db.json`
- Test: `app/src/test/java/com/pokerarity/scanner/GlobalRarityLegacyLoaderTest.kt`

- [ ] **Step 1: Write failing loader test for generated schema**
- [ ] **Step 2: Generate initial DB from current authoritative assets + overrides**
- [ ] **Step 3: Include source provenance, first/last seen, event label, shiny status fields**
- [ ] **Step 4: Re-run loader test**

### Task 3: Runtime Loader

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/data/model/GlobalRarityLegacyEntry.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/data/repository/GlobalRarityLegacyLoader.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/GlobalRarityLegacyLoaderTest.kt`

- [ ] **Step 1: Write failing loader test**
- [ ] **Step 2: Implement minimal model + loader**
- [ ] **Step 3: Verify asset lookup by spriteKey/species**

### Task 4: Explanation Integration

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityExplanationFormatter.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/RarityExplanationFormatterTest.kt`

- [ ] **Step 1: Write failing explanation test using legacy DB event context**
- [ ] **Step 2: Prefer global legacy DB metadata when available**
- [ ] **Step 3: Keep fallback behavior if metadata is absent**

### Task 5: Verification

**Files:**
- Modify: `rapor.md`

- [ ] **Step 1: Run focused unit tests**
- [ ] **Step 2: Run `assembleDebug --console=plain`**
- [ ] **Step 3: If explanation text changed materially, install APK and smoke-test**
