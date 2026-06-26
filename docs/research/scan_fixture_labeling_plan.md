# Scan Fixture Labeling Plan

Date: 2026-06-26

## Current Counts

Latest `.\scripts\audit_scan_fixtures.ps1` output:

* `cases=47`
* `fixtures=47`
* `strict=16`
* `all_null_exploratory=28`
* `expected_species=19`
* `expected_form=0`
* `expected_cp=19`
* `expected_hp=17`
* `expected_appraisal_fields=0`
* `expected_screen_type=0`
* `expected_confidence_decision=0`
* `expected_min_confidence=0`
* `expected_may_show_overlay=0`
* `expected_may_save_scan=0`
* `missing_fixture_files=0`

All 47 cases are missing the Phase E confidence-decision labels. This plan does not fabricate labels; it prioritizes which existing screenshots should be reviewed first.

## Top 10 Fixtures To Label First

1. `armored_lucky_mewtwo_seed_0`
   * Label: `expected_screen_type`, `expected_decision`, `expected_min_confidence`, `expected_may_show_overlay`, `expected_may_save_scan`, appraisal fields if visible.
   * Why: known manual species/CP/HP lucky fixture; good first high-confidence detail case.
2. `armored_lucky_mewtwo_seed_1`
   * Label: same fields as seed 0.
   * Why: same burst validates frame-to-frame stability.
3. `armored_lucky_mewtwo_seed_2`
   * Label: same fields as seed 0.
   * Why: completes the burst and catches inconsistent confidence decisions.
4. `live_variant_batch_20260318_slowpoke_regular`
   * Label: screen type, decision, min confidence, overlay/save flags, form if obvious.
   * Why: strict regular control case.
5. `live_variant_batch_20260318_slowpoke_costume`
   * Label: screen type, decision, min confidence, overlay/save flags, form/costume review.
   * Why: strict costume case with same family as regular control.
6. `live_variant_batch_20260318_pikachu_costume`
   * Label: screen type, decision, min confidence, overlay/save flags, form/costume review.
   * Why: common species with costume evidence exercises resolver and visual support.
7. `live_variant_batch_20260318_butterfree_costume_shiny`
   * Label: screen type, decision, min confidence, overlay/save flags, form/costume/shiny review.
   * Why: combines visual variant evidence with text fields.
8. `regression_20260317_1355/scan_1773753484791_1`
   * Label: screen type, species, CP, HP, decision, min confidence, overlay/save flags.
   * Why: currently lacks core expected fields, so it is a high-value regression hardening target.
9. `regression_20260317_1355/scan_1773753496448_0`
   * Label: same fields as item 8.
   * Why: same regression batch with missing core labels.
10. `regression_20260317_1355/scan_1773753497382_1`
    * Label: same fields as item 8.
    * Why: completes the initial unlabeled regression subset.

## Labeling Priorities

Priority 1:
* `expected_screen_type`
* `expected_species`
* `expected_decision`

Priority 2:
* `expected_cp`
* `expected_hp`
* `expected_min_confidence`
* `expected_may_show_overlay`
* `expected_may_save_scan`

Priority 3:
* `expected_form`
* `expected_appraisalAttack`
* `expected_appraisalDefense`
* `expected_appraisalStamina`

## Device Capture Plan

Samsung S25:
* Capture detail, detail scrolled, appraisal, storage list, transition/animation, and encounter screens.
* Record resolution, density, game language, and whether the overlay was visible.
* Include at least one high-CP Pokemon, one low-CP Pokemon, one lucky, one shiny/costume if available, and one non-detail screen.

Pixel 4a:
* Capture the same screen categories as Samsung S25.
* Prioritize 1080x2340 and 1080x2400-like layouts to validate crop scaling.
* Include at least one appraised Pokemon with visible IV bars.

## Minimum Fixture Set Before Expanding Phase G

Before adding more advanced visual matching, require at least:

* 5 strict Pokemon detail fixtures with CP, HP, species, screen type, and confidence decision.
* 3 strict appraisal fixtures with appraisal bars and confidence decision.
* 3 strict storage/non-detail fixtures expected to reject or remain uncertain.
* 2 transition/blur fixtures expected to retry.
* At least one Samsung S25 and one Pixel 4a fixture per screen category.

This keeps visual evidence supportive and prevents overfitting to a small screenshot set.
