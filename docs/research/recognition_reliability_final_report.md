# Recognition Reliability Final Report

Date: 2026-06-26

## Overview

Phases A-D are present in the current worktree:

* Phase A added local scan diagnostics, OCR/crop/frame diagnostic models, fixture audit support, and local-only diagnostics export.
* Phase B added `ScreenClassifier`, `ScreenGeometry`, `ScreenGeometryBuilder`, crop provenance/fallback diagnostics, and preserved legacy `ScreenRegions`.
* Phase C added field-specific ML Kit OCR preprocessing, deterministic field candidate scoring, stable raw OCR keys, and missing/not-run/found field status.
* Phase D added `SpeciesFormResolver`, display OCR vs canonical species/form separation, resolver trace diagnostics, marker rejection, and project-owned resolver evidence only.

Phases E-F-G added a conservative scan confidence decision layer, fixture audit hardening, focused tests, and documentation. Phase G was limited to weak visual support from existing visual summaries; no new visual matcher, templates, assets, or datasets were added.

## Final Pipeline

```text
ScreenCaptureService
  -> ScanManager
    -> decode/cache-path validation
    -> OCRProcessor
      -> ScreenClassifier
      -> ScreenGeometryBuilder
      -> field-specific ML Kit OCR candidates
    -> ScanFrameFusion
    -> SpeciesFormResolver via SpeciesRefiner
    -> ScanConsistencyGate
    -> existing visual feature and variant classifiers
    -> ScanConfidenceGate
      -> ACCEPT / ACCEPT_LOW_CONFIDENCE / RETRY / UNCERTAIN / REJECT_NOT_POKEMON_SCREEN
    -> rarity/variant scoring only for accepted scans
    -> overlay/save only when gate allows it
    -> metadata-only telemetry with screenshotPath = null
    -> local diagnostics export when triggered
```

## Confidence Decision Rules

`ScanConfidenceGate` consumes existing evidence only:

* screen type and confidence
* geometry provenance and crop confidence
* field candidate status/scores
* CP, HP, name, candy, date, stardust, appraisal, lucky, and size availability
* species resolver confidence and alternatives
* frame agreement
* CP crop quality
* existing consistency gate reason
* existing visual summary as weak support only

Decision behavior:

* `ACCEPT`: strong detail/appraisal screen, usable name, CP or HP support, resolver agreement, no conflicts.
* `ACCEPT_LOW_CONFIDENCE`: usable detail/appraisal scan with incomplete supporting fields; may show/save, but is not collection-safe.
* `RETRY`: transition, unknown weak evidence, raw-text-only evidence, or very low confidence.
* `UNCERTAIN`: conflicting species/field/visual evidence or middling evidence that should not save.
* `REJECT_NOT_POKEMON_SCREEN`: storage list or clear non-detail screen.

Raw markers such as `missing`, `not-run`, `skipped`, `RawText`, numeric-only names, and CP/HP-like text do not increase species/name confidence. Legacy crop fallback reduces confidence but does not automatically fail a scan.

## Privacy And Telemetry Boundary

No remote telemetry behavior was changed. Local diagnostics can include gate traces, resolver traces, raw OCR, and crop provenance. Remote telemetry still uses explicit metadata fields and omits screenshots, diagnostics, raw OCR, resolver traces, and gate traces.

The live scan path still passes `screenshotPath = null` into telemetry. Existing screenshot path sanitization in `ScanManager` remains in place.

## Fixture Status

Before fixture audit hardening:

* `cases=47`
* `fixtures=47`
* `strict=16`
* `all_null_exploratory=28`
* `expected_screen_type=0`

After fixture audit hardening:

* Existing counts are preserved.
* Audit now also reports confidence-decision labels, minimum confidence labels, overlay/save flags, and priority groups.
* Current confidence-decision label count is still `0`; all 47 fixtures need Phase E labels.
* No fixture labels were fabricated or changed.

See `docs/research/scan_fixture_labeling_plan.md` for the prioritized manual labeling plan.

## Tests Added

* `ScanConfidenceGateTest`
  * accept, low/uncertain, retry, reject, marker-only, raw-text-only, fallback, conflict, and appraisal cases.
* `OcrDiagnosticsExporterTest`
  * local JSON includes scan decision at root and in scan diagnostics.
* `ScanTelemetryRepositoryTest`
  * production telemetry debug payload omits scan decision trace data.

Existing focused resolver and diagnostics tests remain part of validation.

## Known Limitations

* Thresholds are intentionally conservative and may retry some valid but weak screenshots.
* Fixture labels for expected screen type and confidence decision are not yet populated.
* The gate does not rewrite the UI; uncertain/retry states use existing error/retry patterns.
* Collection safety is represented in gate metadata and by blocking save for uncertain/retry/reject scans; deeper Collector Intelligence UI treatment is left for later.
* Phase G visual input is supportive only and cannot accept a scan by itself.

## Manual QA Plan

1. On Samsung S25, scan Pokemon detail, scrolled detail, appraisal, storage list, encounter, and transition screens.
2. On Pixel 4a, repeat the same screen categories.
3. Confirm accepted scans show overlay and save history.
4. Confirm low-confidence scans are marked in local diagnostics and do not set `collectionSafe=true`.
5. Confirm storage/non-detail screens do not save a Pokemon scan.
6. Confirm transition/blur captures retry instead of saving.
7. Export local diagnostics for at least one accepted, one low-confidence, one retry, and one reject case.
8. Confirm telemetry consent behavior is unchanged and live telemetry payloads have `screenshotPath = null`.

## Rollback Notes

Rollback can remove these Phase E-F-G changes without touching A-D:

* `ScanConfidenceGate.kt`
* `PokemonData.scanDecision`
* `ScanDiagnosticReport.scanDecision`
* `ScanManager` gate invocation before rarity scoring
* fixture audit confidence-decision reporting
* related tests and docs

If the gate is too strict in manual QA, lower risk is to keep diagnostics attached and temporarily relax only the final decision thresholds.

## Next Recommended Branch / PR Strategy

Open a review branch for the full A-G recognition reliability work, then split review notes by behavior:

1. A-D evidence infrastructure and resolver diagnostics.
2. Phase E gate behavior and save/overlay guardrails.
3. Phase F fixture/test hardening.
4. Manual fixture labeling follow-up.

Do not expand Phase G until the minimum fixture set in `scan_fixture_labeling_plan.md` is labeled and passing.
