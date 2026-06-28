# Live Recognition Gap Diagnosis

Date: 2026-06-26
Branch: main

## Summary

The observed 2017 normal Flareon score of 4 is consistent with a caught-date miss, not with a rarity-scoring bug. `RarityCalculator` applies age score only when `PokemonData.caughtDate` is present. Flareon's configured base rarity resolves to a base score of 4, so a scan with no caught date and no confident variants can land at `4 / Common`.

## 2017 Flareon Root Cause

The live fast path called `OCRProcessor.processImageWithDiagnostics(... includeSecondaryFields = false)`. Before this sprint, Date OCR was treated as a secondary field and returned `not-run` on that pass. `ScanFrameFusion.shouldRunDetailedPass` then required a detailed pass because `caughtDate == null`, adding latency. If the detailed pass missed the date crop or the parser rejected the OCR text, `PokemonData.caughtDate` stayed null and `RarityCalculator` had no age input.

Date loss path:

1. Fast OCR: Date skipped as secondary.
2. Detailed OCR: Date attempted only after the slower fallback path.
3. Parser: numeric dates were partially supported, but month-name formats and OCR year noise such as `2O17` were fragile.
4. PokemonData: `caughtDate` stayed null when the Date candidate did not parse.
5. RarityCalculator: age score remained 0, so Flareon stayed at base score.

The fix is to attempt Date in the fast detail-screen pass, add Date-specific normalization, reject impossible dates explicitly, and export local diagnostics whenever the final accepted result is missing a caught date.

## Latency Diagnosis

The 5+ second scans are explained by repeated expensive work:

* Date was skipped in the fast pass, so otherwise good detail scans still needed detailed OCR.
* ML Kit was invoked over several crops and preprocessing variants.
* Visual and variant classifiers still run after OCR for accepted scans.
* The app decoded the best frame again for visual processing.

This sprint adds stage timing diagnostics for decode, fast OCR, detailed OCR, screen geometry, per-field OCR, species resolver, consistency gate, visual detector, variant classifier, rarity scoring, and total time. The immediate speed improvement is that high-confidence detail scans with CP, name, and date can now skip the detailed OCR pass.

## Most Likely Missing Fields

Fixture labels do not yet expose live field miss rates, but the current risk order is:

1. Date, because age scoring depends on it and it was previously skipped in fast mode.
2. HP, because it is crop/contrast sensitive.
3. Appraisal fields, because they are screen-state dependent.
4. Variant flags, because visual evidence is intentionally conservative.
5. Screen and decision labels in fixtures, because none of the 47 cases currently has `expected_screen_type` or `expected_confidence_decision`.

## Variant Recognizer Weaknesses

Shiny, shadow, lucky, costume, and location-card recognition remain conservative. Current weaknesses:

* Shiny: subtle shiny differences need species-specific support; global color shifts are not safe enough.
* Shadow: dark sprite color alone is not reliable; visible aura or label evidence should be preferred.
* Lucky: yellow/orange Pokemon bodies can resemble lucky background colors.
* Costume/form: project-owned authoritative data should remain the source of truth; weak visual evidence should downgrade to review.
* Special background/location card: normal detail-card visuals can resemble a background cue, so full score should require high confidence.

No broad visual matcher was added in this sprint. The safer next step is to use local diagnostics from labeled screenshots to identify which existing visual signals are false-positive prone.

## Baseline Commands

* `git status --short`: clean before edits.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: passed.
* `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`: passed.
* `.\scripts\audit_scan_fixtures.ps1`: passed with `cases=47`, `fixtures=47`, `expected_screen_type=0`, and `expected_confidence_decision=0`.

