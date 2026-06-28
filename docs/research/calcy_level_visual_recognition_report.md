# Calcy-Level Visual Recognition Sprint Report

Date: 2026-06-27
Branch: `feature/calcy-level-visual-recognition-engine`
Status: NOT READY

## Workstreams

* Orchestrator: preserved dirty work, created branch, ran preflight, added local-only reference pipeline, integrated reports.
* Codebase baseline agent: read-only scan/visual/test inventory.
* Privacy/build agent: read-only telemetry/build/privacy safeguard review.
* Research/catalog: PoGoAPI, PokeMiners Game Master, PokeMiners assets, project data, and manual public event sources.
* Descriptor/evaluation: reused existing prototype descriptor stack and generated dev-only descriptors.

## What Changed

* Added `.local/` and `scripts/cache/pogo_reference/` to `.gitignore`.
* Added `scripts/reference_pipeline/*.ps1` pipeline scripts.
* Generated local reports under `build/reports/pogo_reference/`.
* Added research docs under `docs/research/`.
* Did not add runtime network, permissions, raw assets, new OCR, new model runtime, or telemetry fields.

## Counts

* catalog_entries=5,139
* released_species=937
* indexed_assets=5,840
* downloaded_or_cached_assets=5,840
* usable_assets=5,840
* generated_descriptors=3,591
* descriptor_species=953
* packaged_runtime_descriptors=4,044
* packaged_runtime_descriptor_species=928
* costume_event_variants=883
* shiny_available_species_forms=863
* shadow_available_species_forms=245

## Runtime Integration

No new runtime matcher dependency was added in this pass. The existing runtime already has `VariantPrototypeClassifier`, `VariantDecisionEngine`, `VisualFeatureDetector`, `FullVariantMatcher`, and `Phase2VariantClassifier`, and it loads `app/src/main/assets/data/variant_classifier_model.json`.

The runtime sprite cropper was hardened so the adaptive visual crop detects the white Pokémon detail card and avoids expanding sprite bounds with lower-card UI content. White low-saturation UI such as CP arcs/text is also less likely to become foreground evidence. This is a narrow recognition fix, not a Phase G expansion.

The new scripts make the existing descriptor data path reproducible and measurable. Runtime descriptor DB replacement remains blocked until legal review and live holdout calibration pass.

## Acceptance Result

NOT READY because:

* source reconciliation is first-pass only
* descriptor evaluation is now real, but broad live-fixture species accuracy is 0.000 on 16 evaluable labeled fixtures
* 3 labeled fixture files are present but undecodable by PIL and must be repaired or replaced
* the labeled fixture set is too small for the production gate: 16 evaluable labeled species fixtures versus the minimum 50
* false-positive/false-negative reports contain real evaluated rows, but major variant categories still lack enough positive holdout labels
* latency descriptor report has no device measurements
* Dynamax, Gigantamax, purified indicator, gender differences, and special background catalog coverage are incomplete

## Final Stabilization Notes

After connected-regression review, the existing runtime path received conservative fixes:

* Candy OCR can rescue exact species names from noisy `... SPECIES CANDY` text.
* Marker OCR values are rejected as species candidates.
* Candy/family evidence blocks cross-family visual classifier overrides.
* Base shiny promotion now requires stricter standalone confidence when the visual detector does not confirm shiny.
* Rarity scoring uses merged/gated visual flags for variant score eligibility.

The last full local validation passed:

* fixture audit
* full JVM unit tests
* debug assemble
* debug lint
* Android test Kotlin compile
* `git diff --check` with CRLF warnings only

Connected Android regression could not be rerun in the final pass because `adb devices` returned no attached devices. Earlier connected runs exposed the species and shiny issues above; those fixes now have JVM guardrail tests.

## Privacy

Raw downloaded assets are stored only under `.local/pogo_reference_cache/`, which is ignored. Generated descriptor DB is under `build/reports/pogo_reference/`, also ignored by the existing build ignore rule. Live telemetry remains metadata-only and still passes `screenshotPath = null`.

## Scope Split Follow-Up

Date: 2026-06-28

The branch should not be reviewed as one Calcy-level runtime rewrite. Split it into:

* Scope A: production runtime guardrails.
* Scope B: dev-only reference pipeline.
* Scope C: live fixture capture and labeling tools.

Broad descriptor species recognition remains disabled/support-only because the real evaluator reports
0.000 broad live-fixture species accuracy. Descriptor evidence can support OCR/candy/family narrowed
candidates and can downgrade conflicts, but it must not create ACCEPT/save behavior by itself.

Additional Scope C tooling was added under `scripts/fixture_tools/` to create live fixture sessions,
import app-private diagnostics, build contact sheets, emit label templates, validate labels, and
create holdout split previews.

## Final Split Validation Update

Status for this branch is **SPLIT READY**, not Calcy-level broad visual READY.

Final validation changed the earlier connected-test status: `connectedDebugAndroidTest` now passes on
`SM-S931B - 16`. The fixes were limited to merge-safe runtime guardrails:

* Android regression tests now apply Phase 2 visual merge like production does.
* Phase 2 shiny cannot promote from tiny-margin trained predictions without visual confirmation.
* A small red upper-head accessory cue catches the manually labeled glasses Slowpoke fixture while the regular Slowpoke control remains false.

The descriptor evaluator still reports `descriptor_eval_status=NOT READY` because broad live fixture
species accuracy is 0.000 and labeled holdout coverage is below the gate. Therefore broad descriptor
species recognition remains disabled/support-only.
