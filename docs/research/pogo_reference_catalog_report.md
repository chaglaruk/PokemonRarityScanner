# Pokemon GO Reference Catalog Report

Date: 2026-06-27

Generated files:

* `build/reports/pogo_reference/complete_pogo_catalog.json`
* `build/reports/pogo_reference/coverage_summary.md`
* `build/reports/pogo_reference/source_diff_report.md`
* `build/reports/pogo_reference/missing_variant_metadata.csv`
* `build/reports/pogo_reference/missing_reference_assets.csv`
* `build/reports/pogo_reference/ambiguous_assets.csv`
* `build/reports/pogo_reference/unsupported_variant_categories.csv`

## Counts

* catalog_entries=5,139
* released_species=937
* forms=8 from PoGoAPI first-pass endpoint
* shiny_available_species_forms=863
* shadow_available_species_forms=245
* purified_possible_species_forms=245
* costume_event_variants=883
* mega_primal=8
* dynamax=0
* gigantamax=0
* gender_visual_differences=not covered
* unknown_unsupported_go_visual_states=dynamax, gigantamax, purified indicator, gender visual differences

## Readiness

NOT READY. The catalog is useful for tooling, but it is a first-pass union of PoGoAPI and project data, not a fully reconciled current GO truth database.
