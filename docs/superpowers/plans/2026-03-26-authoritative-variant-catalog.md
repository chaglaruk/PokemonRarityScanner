# Authoritative Variant Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an authoritative Pokemon GO variant catalog for `species + shiny + costume + form + event metadata`, and wire scan-time variant decisions to use it as the source of truth.

**Architecture:** Add a generated `variant_catalog.json` asset produced from local Game Master + local PokeMiners assets, with one normalized row per sprite/variant. Load it at runtime through a dedicated repository, use it to constrain classifier outputs and validate variant semantics before final merge. Keep `lucky/background/shadow/purified` out of this phase.

**Tech Stack:** Kotlin, Android assets, existing PokeMiners asset dump, local Game Master JSON, Python generation scripts, JUnit/androidTest regression tests.

---

## File Structure

- Create: `scripts/generate_variant_catalog.py`
  - Build normalized authoritative rows from local Game Master + local assets.
- Create: `app/src/main/assets/data/variant_catalog.json`
  - Generated artifact consumed at runtime.
- Create: `app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogLoader.kt`
  - Runtime loader and query surface for catalog rows.
- Create: `app/src/main/java/com/pokerarity/scanner/data/model/VariantCatalogEntry.kt`
  - Stable typed model for catalog rows.
- Modify: `scripts/train_variant_prototypes.py`
  - Stop inferring semantics loosely; consume catalog rows instead.
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeStore.kt`
  - Carry authoritative variant semantics from catalog/model.
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeClassifier.kt`
  - Return catalog-backed row identifiers and semantics.
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`
  - Resolve species/variant using catalog-constrained classifier outputs.
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
  - Use catalog semantics instead of raw heuristic `variantType` assumptions.
- Modify: `app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt`
- Modify: `app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt`
- Modify: `rapor.md`

### Task 1: Generate Authoritative Catalog

**Files:**
- Create: `scripts/generate_variant_catalog.py`
- Create: `app/src/main/assets/data/variant_catalog.json`
- Test: ad-hoc generator output inspection from PowerShell

- [ ] **Step 1: Write the failing expectation**

Document expected catalog behavior in generator comments / script assertions:
- one row per sprite key
- explicit fields: `species`, `dex`, `formId`, `variantId`, `assetKey`, `spriteKey`, `isShiny`, `variantClass`, `isCostumeLike`, `eventTags`, `releaseWindow`
- `Squirtle 007_00_05` and `Blastoise 009_00_05_shiny` must classify as `costume`

- [ ] **Step 2: Run generator placeholder to verify missing file/failing output**

Run: `python scripts/generate_variant_catalog.py`
Expected: fail or produce no file before implementation.

- [ ] **Step 3: Implement minimal generator**

Implementation requirements:
- read `external/game_masters/latest/latest.json`
- read local asset tree under `external/pogo_assets/Images/Pokemon - 256x256`
- read existing helper assets when useful:
  - `scripts/costume_keys.json`
  - `app/src/main/assets/data/costume_species.json`
  - `app/src/main/assets/data/event_history.json`
- emit deterministic JSON with stable ordering

- [ ] **Step 4: Run generator and inspect output**

Run: `python scripts/generate_variant_catalog.py`
Expected:
- `app/src/main/assets/data/variant_catalog.json` exists
- entries include `Squirtle`, `Wartortle`, `Blastoise`, `Pikachu`

- [ ] **Step 5: Commit**

```bash
git add scripts/generate_variant_catalog.py app/src/main/assets/data/variant_catalog.json
git commit -m "feat: generate authoritative variant catalog"
```

### Task 2: Add Runtime Catalog Loader

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/data/model/VariantCatalogEntry.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogLoader.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/VariantCatalogLoaderTest.kt`

- [ ] **Step 1: Write the failing test**

Create loader tests for:
- load succeeds
- lookup by `spriteKey`
- lookup by `species`
- `009_00_05_shiny` is `costume + shiny`

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradlew testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest`
Expected: FAIL because loader/model do not exist.

- [ ] **Step 3: Implement minimal loader**

Loader must:
- lazy-load JSON once
- expose:
  - `getBySpriteKey(spriteKey)`
  - `getBySpecies(species)`
  - `isKnownCostumeSprite(spriteKey)`

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradlew testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pokerarity/scanner/data/model/VariantCatalogEntry.kt app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogLoader.kt app/src/test/java/com/pokerarity/scanner/VariantCatalogLoaderTest.kt
git commit -m "feat: add runtime variant catalog loader"
```

### Task 3: Train Prototype Model From Catalog Semantics

**Files:**
- Modify: `scripts/train_variant_prototypes.py`
- Modify: `app/src/main/assets/data/variant_classifier_model.json`
- Test: manual script run + output inspection

- [ ] **Step 1: Write the failing expectation**

Expected:
- model rows inherit semantics from `variant_catalog.json`
- no ad-hoc `formId != 00 => form` inference for costume-capable species

- [ ] **Step 2: Run training script before change**

Run: `python scripts/train_variant_prototypes.py`
Expected: current model still depends on loose inference.

- [ ] **Step 3: Implement catalog-backed semantics**

Training script should:
- load `variant_catalog.json`
- match sprite file -> authoritative row
- copy `variantClass`, `isCostumeLike`, `isShiny`, event metadata

- [ ] **Step 4: Regenerate model**

Run: `python scripts/train_variant_prototypes.py`
Expected: `variant_classifier_model.json` regenerated with authoritative semantics.

- [ ] **Step 5: Commit**

```bash
git add scripts/train_variant_prototypes.py app/src/main/assets/data/variant_classifier_model.json
git commit -m "feat: train prototype model from authoritative catalog"
```

### Task 4: Constrain Runtime Variant Decisions By Catalog

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeStore.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeClassifier.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt`
- Test: `app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt`

- [ ] **Step 1: Write failing tests for catalog-constrained cases**

Add cases covering:
- `Pikachu costume` must not leak `shiny`
- `Squirtle shiny-only` must not become costume solely from nearby costume match
- `Blastoise costume+shiny` should stay both flags when classifier picks authoritative costume-shiny sprite

- [ ] **Step 2: Run tests to verify failure**

Run:
- `gradlew testDebugUnitTest --tests=com.pokerarity.scanner.VariantMergeLogicTest`
- `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest`

- [ ] **Step 3: Implement runtime catalog constraints**

Implementation requirements:
- classifier results should carry catalog row ids/semantics
- merge logic should prefer catalog truth over inferred `variantType`
- impossible combinations should be dropped before final flags

- [ ] **Step 4: Run tests to verify pass**

Run same commands.
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeStore.kt app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeClassifier.kt app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt
git commit -m "feat: constrain runtime variant decisions with catalog"
```

### Task 5: Validate On Device And Record Baseline

**Files:**
- Modify: `rapor.md`

- [ ] **Step 1: Build debug APK**

Run: `gradlew --no-daemon assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Install to device**

Run:
- `adb devices`
- `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 3: Clear logcat and launch**

Run:
- `adb -s RFCY11MX0TM logcat -c`
- `adb -s RFCY11MX0TM shell cmd activity start-activity -n com.pokerarity.scanner/.ui.splash.SplashActivity`

- [ ] **Step 4: Validate with known batch**

Use batch:
- `Squirtle — shiny`
- `Squirtle — shiny+costume`
- `Blastoise — shiny+costume`
- `Pikachu — costume`

Collect:
- final species
- final shiny/costume flags
- `overlay dispatched in ...ms`

- [ ] **Step 5: Record results**

Update `rapor.md` with:
- catalog generation
- runtime constraint changes
- live batch results

