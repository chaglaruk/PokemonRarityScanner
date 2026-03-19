# Scan Variant Stabilization Design

**Goal:** Make the live scan pipeline and the Android regression harness evaluate the same species and variant decisions, then close the remaining measured failures around weak species overrides, low-confidence costume promotion, and missing caught-date extraction.

**Context**

The current project has already moved to an asset-backed variant classifier, but recent debugging showed two structural problems:

1. The Android regression harness was not exercising the same classifier merge path as the live `ScanManager`.
2. Remaining failures now come from three concrete subsystems, not general uncertainty:
   - `SpeciesRefiner` overrides to a new species on very weak evidence (`Slowpoke -> Slowbro`)
   - `VariantDecisionEngine` rejects same-species costume matches that are strong enough to be trustworthy in practice (`Pikachu costume`)
   - badge/date OCR still fails on otherwise-correct scans (`datePresent=false`)

**Design**

The fix stays incremental and test-first.

- Keep the new shared `VariantDecisionEngine` as the single place where live and regression classification logic meet.
- Add a low-confidence guard to `SpeciesRefiner` so it does not replace the current species when text support is weak and fit evidence is marginal.
- Relax the species-scoped costume promotion path in `VariantDecisionEngine`, but only when the OCR/refined species already anchors the same family and both global/species classifier results agree on a non-base costume-like branch.
- Treat caught-date extraction as a separate OCR issue. Improve the date path only after species/variant regressions are stable.

**Non-goals**

- No new heuristic sweep across all shiny/costume logic.
- No redesign of the rarity model.
- No changes to unrelated UI, storage, or scan history flows.

**Verification**

- The strict live regression fixtures in `app/src/androidTest/assets/scan_fixtures/live_variant_batch_20260318/` remain the primary gate.
- `connectedDebugAndroidTest` for `ScanRegressionTest` must pass on device before any claim that scan stability improved.
