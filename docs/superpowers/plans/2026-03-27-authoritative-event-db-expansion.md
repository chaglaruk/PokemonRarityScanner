# Authoritative Event DB Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the authoritative variant/event database so event and costume explanations are available across all costume families, not only a few hand-written overrides.

**Architecture:** Keep the existing runtime matcher and explanation pipeline, but improve the data source. The generator will derive normalized variant/event labels from Game Master costume form keys and asset bundle suffixes for every known costume family, then layer precise external overrides on top. Runtime selection stays conservative: exact names only when the chosen metadata is species-safe and time-safe.

**Tech Stack:** Python generator, JSON assets, Kotlin loaders/selectors, Gradle unit tests, Android debug APK.

---

### Task 1: Generalize generator labels from costume metadata

**Files:**
- Modify: `scripts/generate_authoritative_variant_db.py`
- Modify: `scripts/costume_keys.json`
- Test: `app/src/test/java/com/pokerarity/scanner/AuthoritativeVariantDbTest.kt`

- [ ] Add generic normalization helpers for form names and asset suffixes.
- [ ] Derive human-readable variant labels and event labels for every costume-like entry.
- [ ] Preserve explicit external overrides as higher-priority truth.
- [ ] Add tests that prove non-Pikachu families receive non-generic labels.

### Task 2: Make runtime explanation selection consume the broader metadata safely

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantEventFallback.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/VariantCatalogSelectionTest.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/AuthoritativeVariantEventFallbackTest.kt`

- [ ] Keep exact metadata gated by species-safe match logic.
- [ ] Allow species + caught-date fallback to use broader authoritative labels when exact sprite selection is unreliable.
- [ ] Ensure impossible event windows are still rejected.

### Task 3: Polish result narrative and reclaim layout from removed IV block

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/model/Pokemon.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/PokemonAnalysisFormattingTest.kt`

- [ ] Keep IV removed from result UI.
- [ ] Increase textbox emphasis and text weight.
- [ ] Ensure the narrative prefers costume/event names over generic score wording.

### Task 4: Regenerate assets, verify, deploy

**Files:**
- Modify: `app/src/main/assets/data/authoritative_variant_db.json`
- Modify: `rapor.md`

- [ ] Regenerate authoritative DB JSON.
- [ ] Run focused unit tests.
- [ ] Build debug APK.
- [ ] Install on connected device.
- [ ] Record results in `rapor.md`.
