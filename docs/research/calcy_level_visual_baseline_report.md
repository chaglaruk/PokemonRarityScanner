# Calcy-Level Visual Baseline Report

Date: 2026-06-27
Branch: `feature/calcy-level-visual-recognition-engine`

## Preflight

* Dirty work from the prior recognition sprint was preserved.
* `git branch --show-current`: `feature/calcy-level-visual-recognition-engine`
* `.\scripts\audit_scan_fixtures.ps1`: passed; `cases=47`, `fixtures=47`, `expected_species=19`, `expected_screen_type=0`, `expected_confidence_decision=0`, `missing_fixture_files=0`.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: passed.
* `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`: passed.
* `.\gradlew.bat :app:lintDebug --no-daemon --console=plain`: passed.

## Existing Recognition Flow

`ScanManager.processScanSequence` decodes frames, runs fast OCR through `OCRProcessor`, fuses frames with `ScanFrameFusion`, refines species through `SpeciesRefiner` / `SpeciesFormResolver`, evaluates consistency and confidence gates, runs `VariantDecisionEngine`, `VisualFeatureDetector`, `Phase2VariantClassifier`, calculates rarity, shows overlay, saves accepted scans, and enqueues metadata-only telemetry.

## Existing Visual Stack

The codebase already has the right primitive descriptors:

* `SpriteSignature`: aHash, dHash, edge descriptor.
* `PerceptualHash`: pHash.
* `SpriteColorSignature`: hue histograms and foreground ratio.
* `VariantPrototypeClassifier`: aHash/dHash/edge/full/head/upper/body histograms, foreground ratio, aspect ratio, candidate narrowing by species.
* `VisualFeatureDetector`: shiny, shadow, lucky, costume, location card, and size cues.
* `VariantDecisionEngine`, `FullVariantMatcher`, `VariantMergeLogic`, `Phase2VariantClassifier`: existing conservative variant decision stack.

The lazy safe path is to build local reference tooling around these descriptors, not to add a parallel runtime matcher.

## Current Blockers

* Fixture truth is too sparse: no expected screen type, form, confidence decision, variant confidence, descriptor margin, or latency labels.
* Runtime prototype model exists, but it is not tied to a reproducible local fetch/evaluation report.
* Asset coverage was partial in this run: 4,068 usable cached assets out of 5,840 indexed PokeMiners PNG assets.
* Descriptor evaluation has no labeled holdout or false-positive thresholds, so Calcy-level readiness is not proved.
