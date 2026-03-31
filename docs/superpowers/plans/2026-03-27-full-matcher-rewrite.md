# Full Matcher Rewrite Implementation Plan

> Status: Completed on 27 March 2026. Runtime migration, cleanup regression, verification, and device rollout are recorded in `rapor.md`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current OCR/classifier/merge patchwork with an authoritative full matcher that resolves `species + variant + shiny + costume + form + event metadata` as one coherent decision pipeline.

**Architecture:** Introduce a new matcher core that consumes OCR hints, sprite classifier candidates, authoritative variant DB constraints, and visual checks in one structured ranking pass. Existing `VariantDecisionEngine`, `VariantMergeLogic`, and explanation selection become thin adapters over this matcher instead of independently mutating the result.

**Tech Stack:** Kotlin, Android, JSON assets, Python asset generators, JVM unit tests, Android instrumentation tests, debug APK deployment.

---

### Task 1: Define the new authoritative match contract

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/data/model/FullVariantMatch.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/data/model/FullVariantCandidate.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/model/VisualFeatures.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/FullVariantMatchContractTest.kt`

- [ ] **Step 1: Write the failing contract test**

Create a JVM test that asserts the new contract can represent:
- exact species match
- family-level fallback
- exact event metadata
- blocked exact metadata
- confidence per axis (`species`, `variant`, `shiny`, `event`)

- [ ] **Step 2: Run the contract test to verify it fails**

Run: `./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantMatchContractTest --console=plain`
Expected: FAIL because the new contract classes do not exist yet.

- [ ] **Step 3: Implement the contract**

Add `FullVariantCandidate` with fields for:
- `species`
- `spriteKey`
- `variantClass`
- `isShiny`
- `isCostumeLike`
- `eventLabel`
- `eventStart`
- `eventEnd`
- `matchScore`
- `rescueKind`
- `source`

Add `FullVariantMatch` with fields for:
- `finalSpecies`
- `finalSpriteKey`
- `resolvedVariantClass`
- `resolvedShiny`
- `resolvedCostume`
- `resolvedForm`
- `resolvedEventLabel`
- `resolvedEventWindow`
- `speciesConfidence`
- `variantConfidence`
- `shinyConfidence`
- `eventConfidence`
- `explanationMode`
- `debugSummary`

- [ ] **Step 4: Run the contract test to verify it passes**

Run: `./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantMatchContractTest --console=plain`
Expected: PASS

### Task 2: Build candidate generation from all runtime signals

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeClassifier.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantDbLoader.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt`

- [ ] **Step 1: Write the failing candidate-builder test**

Cover:
- OCR exact species + classifier same-species costume candidate
- OCR species + family classifier candidate
- authoritative DB candidate recovery by species/date
- blocked candidate when date is impossible for the event

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --console=plain`
Expected: FAIL because the builder is not implemented.

- [ ] **Step 3: Implement candidate generation**

Candidate builder must merge:
- OCR species/current species
- candy family hints
- global classifier candidate
- species classifier candidate
- authoritative DB entries for final species
- date-based authoritative fallback candidates

Each candidate must record where it came from:
- `ocr_exact`
- `classifier_global`
- `classifier_species`
- `authoritative_exact`
- `authoritative_species_date`
- `family_remap`

- [ ] **Step 4: Extend classifier output if needed**

Expose enough fields from `VariantPrototypeClassifier.MatchResult` so the builder can compare:
- best base candidate
- best shiny peer
- best non-base candidate
- family relation

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --console=plain`
Expected: PASS

### Task 3: Implement the authoritative ranking engine

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantScoring.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantConstraints.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt`

- [ ] **Step 1: Write the failing matcher test**

Include cases for:
- wrong family member should lose to same-species lower score
- impossible event date should be rejected
- costume+shiny combo should outrank costume-only when supported
- weak rescue should not emit exact event metadata
- regular base should beat noisy false costume candidate

- [ ] **Step 2: Run the matcher test to verify it fails**

Run: `./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --console=plain`
Expected: FAIL because the matcher does not exist.

- [ ] **Step 3: Implement constraint filtering**

`FullVariantConstraints` should reject or down-rank candidates when:
- species conflicts with authoritative OCR lock
- caught date is before event start
- final species cannot remap the candidate variant ID/form ID
- shiny/costume combo is impossible in authoritative DB

- [ ] **Step 4: Implement scoring**

`FullVariantScoring` should combine:
- OCR authority
- classifier score/confidence
- visual feature support
- authoritative DB support
- same-family penalty
- event-date consistency bonus

- [ ] **Step 5: Implement final matcher**

`FullVariantMatcher` must:
- build candidates
- filter impossible candidates
- rank them
- emit one `FullVariantMatch`
- emit explanation mode:
  - `exact_authoritative`
  - `derived_authoritative`
  - `generic_variant`
  - `generic_species_only`

- [ ] **Step 6: Run the matcher test to verify it passes**

Run: `./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --console=plain`
Expected: PASS

### Task 4: Replace VariantDecisionEngine with the matcher

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- Test: `app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt`
- Test: `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt`

- [ ] **Step 1: Write the failing integration test**

Add regression expectations for known bad families:
- Pikachu/Raichu/Pichu costume drift
- Slowpoke/Slowbro drift
- Lapras costume miss
- Snorlax costume miss
- Wurmple shiny false positive

- [ ] **Step 2: Run the integration test to verify it fails**

Run:
`./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain`

Expected: FAIL on current behavior.

- [ ] **Step 3: Route classification through the matcher**

`VariantDecisionEngine.classify()` should:
- call classifier
- call authoritative candidate builder/matcher
- stop mutating species/variant in multiple places
- attach one coherent debug trace from the final `FullVariantMatch`

- [ ] **Step 4: Collapse legacy merge heuristics**

`VariantMergeLogic` should become a narrow adapter:
- trust the new match object
- only preserve independent visual flags that the full matcher intentionally leaves unresolved

- [ ] **Step 5: Run the integration tests**

Run:
- `./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain`
- `./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain`

Expected: PASS

### Task 5: Move explanation generation onto the full match object

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantEventFallback.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/PokemonAnalysisFormattingTest.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/VariantExplanationSanityTest.kt`

- [ ] **Step 1: Write the failing explanation test**

Cover:
- exact event name from full match
- derived event name from species/date authoritative fallback
- generic wording when no exact event can be proven
- event suppressed when caught date is impossible

- [ ] **Step 2: Run the test to verify it fails**

Run:
`./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --tests=com.pokerarity.scanner.VariantExplanationSanityTest --console=plain`

Expected: FAIL until explanation reads from `FullVariantMatch`.

- [ ] **Step 3: Refactor explanation generation**

`RarityCalculator` should stop recomputing event metadata from scattered raw fields.
It should consume:
- `resolvedVariantClass`
- `resolvedEventLabel`
- `resolvedEventWindow`
- `explanationMode`

- [ ] **Step 4: Run the test to verify it passes**

Run:
`./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --tests=com.pokerarity.scanner.VariantExplanationSanityTest --console=plain`

Expected: PASS

### Task 6: Remove dead legacy paths

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/util/vision/VariantResolutionLogic.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantEventFallback.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/LegacyVariantPathRemovalTest.kt`

- [ ] **Step 1: Write the failing cleanup test**

Assert that:
- legacy rescue-only exact metadata no longer drives explanations
- no path can produce exact event text without a `FullVariantMatch` decision

- [ ] **Step 2: Run the test to verify it fails**

Run:
`./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.LegacyVariantPathRemovalTest --console=plain`

Expected: FAIL because legacy paths still exist.

- [ ] **Step 3: Delete or reduce legacy selection logic**

Keep only helper utilities that remain necessary after the matcher rewrite.

- [ ] **Step 4: Run the cleanup test**

Run:
`./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.LegacyVariantPathRemovalTest --console=plain`

Expected: PASS

### Task 7: Full verification and device rollout

**Files:**
- Modify: `rapor.md`

- [ ] **Step 1: Run the focused JVM suite**

Run:
`./gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantMatchContractTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain`

- [ ] **Step 2: Run the Android regression suite**

Run:
`./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain`

- [ ] **Step 3: Build the APK**

Run:
`./gradlew --no-daemon assembleDebug --console=plain`

- [ ] **Step 4: Install and launch on device**

Run:
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- `adb shell monkey -p com.pokerarity.scanner -c android.intent.category.LAUNCHER 1`

- [ ] **Step 5: Record rollout notes**

Add to `rapor.md`:
- matcher rewrite branch status
- regression results
- remaining false-positive/false-negative families
- performance delta vs current pipeline
