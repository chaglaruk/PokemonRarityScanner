# Generic Costume Alias Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make costume/event explanations species-aware across all costume-capable Pokemon so exact alias text only appears when the matched variant is valid for the final species.

**Architecture:** Keep alias generation data-driven in `variant_catalog.json`, then apply a runtime selection layer that remaps exact aliases only when the final species has a matching variant id/form id. Otherwise fall back to generic costume/form wording instead of leaking another species' event metadata.

**Tech Stack:** Kotlin, Gson, Python catalog generator, JUnit, Android Gradle

---

### Task 1: Lock down runtime selection semantics

**Files:**
- Modify: `app/src/test/java/com/pokerarity/scanner/VariantCatalogSelectionTest.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt`

- [ ] Step 1: Write failing tests for exact-species and cross-species alias behavior
- [ ] Step 2: Run targeted unit test to verify failure
- [ ] Step 3: Implement minimal selection logic changes
- [ ] Step 4: Re-run targeted unit test to verify pass

### Task 2: Make catalog propagation safe for all costume species

**Files:**
- Modify: `scripts/generate_variant_catalog.py`
- Modify: `app/src/test/java/com/pokerarity/scanner/VariantCatalogLoaderTest.kt`

- [ ] Step 1: Add failing coverage for sparse variant-id mapping and species-safe propagation
- [ ] Step 2: Regenerate catalog and observe failure/incorrect output
- [ ] Step 3: Implement generic propagation rules using species-local ids first, family fallback only when species variant exists
- [ ] Step 4: Re-run catalog tests and inspect sample aliases

### Task 3: Verify build and deploy readiness

**Files:**
- Modify: `rapor.md`

- [ ] Step 1: Run targeted unit tests
- [ ] Step 2: Run `assembleDebug`
- [ ] Step 3: Update report with outcome and deploy status
