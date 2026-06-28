# Recognition Fixture Truth Plan

Date: 2026-06-27

## Current State

Fixture audit reports:

* cases=47
* fixtures=47
* strict=16
* expected_species=19
* expected_cp=19
* expected_hp=17
* expected_screen_type=0
* expected_confidence_decision=0
* missing_fixture_files=0
* evaluable_labeled_species_for_descriptor_eval=16
* descriptor_eval_decode_errors=3

## Required Schema Labels

Add labels for:

* expected_screen_type
* expected_species
* expected_form
* expected_cp
* expected_hp
* expected_date
* expected_shiny
* expected_shadow
* expected_purified
* expected_lucky
* expected_costume
* expected_special_background
* expected_dynamax
* expected_gigantamax
* expected_decision
* expected_score_min
* expected_score_max
* expected_latency_ms
* expected_may_show_overlay
* expected_may_save_scan
* expected_descriptor_top_candidate
* expected_descriptor_min_margin

## First 20 Must-Label Fixtures

Use `.\scripts\audit_scan_fixtures.ps1` recommendations first:

1. armored_lucky_mewtwo_seed_0
2. armored_lucky_mewtwo_seed_1
3. armored_lucky_mewtwo_seed_2
4. live_variant_batch_20260318_slowpoke_regular
5. live_variant_batch_20260318_slowpoke_costume
6. live_variant_batch_20260318_pikachu_costume
7. live_variant_batch_20260318_butterfree_costume_shiny
8. regression_20260317_1355/scan_1773753484791_1
9. regression_20260317_1355/scan_1773753496448_0
10. regression_20260317_1355/scan_1773753497382_1
11. regression_20260317_1059/scan_1773715504772_0
12. regression_20260317_1059/scan_1773715505303_1
13. regression_20260317_1059/scan_1773715515668_0
14. regression_20260317_1059/scan_1773715516151_1
15. regression_20260317_1059/scan_1773715527586_0
16. regression_20260317_1059/scan_1773715527993_1
17. regression_20260317_1059/scan_1773715539213_0
18. regression_20260317_1059/scan_1773715539737_1
19. regression_20260317_1355/scan_1773754453843_0
20. regression_20260317_1355/scan_1773754454164_1

No labels were fabricated in this sprint.

## Immediate Fixture Repairs

The descriptor evaluator could not decode these labeled files even though the audit script finds the paths:

1. `scan_fixtures/armored_lucky_mewtwo_seed/scan_1773704944533_0.png`
2. `scan_fixtures/armored_lucky_mewtwo_seed/scan_1773704945365_1.png`
3. `scan_fixtures/armored_lucky_mewtwo_seed/scan_1773704946115_2.png`

Repair or replace these files before using them as visual holdout truth.

## Minimum Holdout Before Readiness

Before claiming production descriptor readiness, capture and label at least:

* 50 decodable species-labeled detail screenshots.
* 3 positive and 3 negative cases each for shiny, shadow, purified, lucky, costume, special background/location card, Dynamax, Gigantamax, Mega/Primal, regional form, special form, gender visual difference, and subtle shiny.
* Samsung S25 and Pixel 4a latency runs for normal detail, appraisal, storage list, transition/blur, and heavy variant review screens.

## Scope C Tooling

Added local-only fixture tools:

* `scripts/fixture_tools/create_live_fixture_session.ps1`
* `scripts/fixture_tools/import_device_diagnostics.ps1`
* `scripts/fixture_tools/build_fixture_contact_sheet.ps1`
* `scripts/fixture_tools/label_fixture_template.ps1`
* `scripts/fixture_tools/validate_fixture_labels.ps1`
* `scripts/fixture_tools/split_fixture_holdout.ps1`

Smoke result on 2026-06-28:

* cases=47
* decoded=44
* decode_errors=3
* species-labeled decodable fixtures eligible for split preview=16
* preview split: training=12, calibration=2, holdout=2
* committed holdout labels=0
* descriptor_readiness=NOT_READY

The tools do not fabricate labels and write reports under ignored `build/reports/fixture_tools/`.

## Final Validation Note

`validate_fixture_labels.ps1` still reports `descriptor_readiness=NOT_READY`: 44 fixtures decode, 3
tracked fixtures are undecodable, and there are 0 committed decodable species-labeled holdout cases.
This blocks broad descriptor readiness but does not block the production runtime guardrail PR.
