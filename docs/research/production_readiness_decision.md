# Production Readiness Decision

Date: 2026-06-28
Branch: `feature/calcy-level-visual-recognition-engine`
Decision: **SPLIT READY**

## Split Decision

The dirty branch is not ready as a Calcy-level broad descriptor recognition release.
It is ready to split into reviewable scopes if the final validation commands remain green:

* Scope A: production runtime guardrails are merge-safe.
* Scope B: reference catalog, asset, and descriptor pipeline is dev-only tooling.
* Scope C: fixture capture, labeling, and holdout tooling is ready for the next real-data step.

Broad descriptor species recognition remains disabled/support-only. It must not produce ACCEPT, save
collection history, or award rarity points by itself.

## Evidence

The strongest evidence is from the real descriptor evaluation:

* Packaged runtime descriptor model: 4,044 entries for 928 species.
* Generated dev-only descriptor report DB: 3,591 entries for 953 species.
* Indexed PokeMiners sprite assets cached locally: 5,840 / 5,840.
* Augmented cached-asset evaluation: 1,500 cases, species accuracy 1.000, exact sprite accuracy 0.981.
* Live fixture evaluation: 16 evaluable labeled species fixtures, broad species accuracy 0.000.
* Undecodable labeled fixtures: 3.
* High-confidence false positives in the limited labeled fixture set: 0.

This proves descriptor mechanics inside the sprite domain. It does not prove production screenshot
recognition from live Pokemon GO detail screens.

## Scope A Runtime Guardrails

The production runtime scope preserves safe recognition improvements:

* Date OCR remains in the fast path and feeds age scoring.
* 2017 Flareon age scoring is guarded by JVM regression tests.
* Recent Flareon remains base-only when no age/variant evidence is present.
* Candy text can rescue species names for cases like `RAIKOU CANDY` and `FARFETCH'D CANDY`.
* Marker tokens such as `missing`, `not-run`, `skipped`, `RawText`, numeric-only strings, and CP/HP/date/stardust-like strings are rejected as species.
* Visual classifier species override now requires candy/family support when it would replace an unknown or different species.
* Cross-family visual classifier overrides are blocked when candy/family evidence exists.
* Shiny/costume promotion requires stronger visual evidence and merged/gated visual flags.
* Rarity scoring uses merged visual flags, not raw `FullVariantMatch`, for variant score eligibility.
* Uncertain variants do not add full rarity points.
* Storage and transition screens remain blocked by the confidence gate.

This scope is intentionally narrow. It does not claim broad visual species recognition.

## Scope B Dev-Only Pipeline

The reference pipeline can be kept as dev-only tooling:

* `scripts/reference_pipeline/fetch_pogo_metadata.ps1`
* `scripts/reference_pipeline/fetch_pogo_assets.ps1`
* `scripts/reference_pipeline/build_pogo_reference_catalog.ps1`
* `scripts/reference_pipeline/generate_visual_descriptors.ps1`
* `scripts/reference_pipeline/evaluate_visual_descriptors.ps1`

Raw external assets stay under ignored local caches. Generated descriptor DB files stay under ignored
`build/reports/pogo_reference/` unless a future legal/product review approves a release artifact.
The current packaged runtime descriptor model also remains a legal/product review item.

## Scope C Fixture Tooling

Local fixture tools now exist under `scripts/fixture_tools/`:

* `create_live_fixture_session.ps1`
* `import_device_diagnostics.ps1`
* `build_fixture_contact_sheet.ps1`
* `label_fixture_template.ps1`
* `validate_fixture_labels.ps1`
* `split_fixture_holdout.ps1`

They are local-only and write under ignored `build/reports/fixture_tools/` by default. Validation
reports descriptor readiness separately from command success, so local validation can pass while the
real holdout blocker remains visible.

## Remaining Descriptor Blockers

* Labeled holdout coverage is insufficient: 16 evaluable labeled species fixtures versus the minimum gate of 50.
* Major variant-positive holdout labels are missing or sparse for shadow, purified, lucky, special background/location card, Dynamax, Gigantamax, Mega/Primal, gender visual differences, and subtle shiny cases.
* Three tracked labeled fixture files are present but not decodable as images by the evaluator and need replacement or repair.
* Real device latency still needs Samsung S25 and Pixel 4a measurement.
* The packaged runtime descriptor model is derived from external sprites. Legal/product review is required before treating it as production release data.

## Release Gate Result

Scope A can proceed to human review as a production runtime guardrail PR. Scope B and Scope C are
safe as dev-only/local-only tooling. Broad descriptor species recognition remains **NOT READY**.

## Final Validation Update

Final validation on 2026-06-28 is green for the split scope:

* Connected Android regression passed on `SM-S931B - 16`.
* Full JVM unit tests passed.
* `assembleDebug`, `lintDebug`, and `compileDebugAndroidTestKotlin` passed.
* Reference pipeline completed with 5,840 / 5,840 usable cached assets and 3,591 generated dev descriptors.
* Descriptor evaluator still reports `descriptor_eval_status=NOT READY`, so broad descriptor species recognition stays disabled/support-only.

Final runtime fixes added during validation:

* Android regression harness now mirrors production Phase 2 visual merge before confidence/rarity assertions.
* Phase 2 shiny promotion no longer accepts low-margin trained predictions unless visual shiny is already present or the standalone signal is strict.
* Slowpoke glasses fixture is covered by a conservative upper-head accessory cue gated behind existing sparse costume evidence.
