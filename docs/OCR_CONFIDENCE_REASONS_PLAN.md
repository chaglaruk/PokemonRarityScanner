# Purpose: Plan structured OCR confidence reasons without leaking raw OCR text.

# Structured OCR Confidence Reasons Plan

## Current Problem

The scan pipeline currently stores many parser and classifier hints inside
`PokemonData.rawOcrText` as pipe-delimited markers such as `Name:`, `NameHC:`,
`SizeTag:`, `LuckyDetected:`, `RecognitionSummary:`, `CpOcrStatus:`,
`HpOcrStatus:`, and diagnostic file markers.

That works as a quick cross-stage contract, but it has three practical risks:

- Reliability: downstream logic must repeatedly split strings and infer marker
  meaning from raw text.
- Privacy: raw OCR and local diagnostic paths can move farther through the
  pipeline than needed.
- Testability: confidence reasons are hard to assert without constructing
  brittle marker strings.

## Proposed Model

Introduce structured, non-sensitive OCR confidence data alongside `PokemonData`.

Suggested model:

```kotlin
data class OcrFieldConfidence(
    val field: OcrField,
    val status: OcrFieldStatus,
    val source: OcrSignalSource,
    val confidence: Float?,
    val reasonCodes: List<String> = emptyList()
)

data class OcrConfidenceReasons(
    val fields: List<OcrFieldConfidence>,
    val warnings: List<String> = emptyList()
)
```

Initial enums:

- `OcrField`: `SPECIES`, `CP`, `HP`, `CAUGHT_DATE`, `SIZE_TAG`, `LUCKY`
- `OcrFieldStatus`: `PARSED`, `MISSING`, `LOW_CONFIDENCE`, `CONFLICT`
- `OcrSignalSource`: `TOP_TEXT`, `DETAIL_PASS`, `CANDY`, `VISUAL`, `MATH_FALLBACK`

Reason codes should be short stable identifiers, for example:

- `species_top_text_strong`
- `species_candy_family_conflict`
- `cp_missing`
- `cp_math_fallback`
- `hp_pair_parsed`
- `date_missing`
- `detail_pass_backfill`

## Migration Strategy

1. Add the new model as nullable metadata on `PokemonData` or as a wrapper
   returned by OCR/fusion helpers.
2. Populate only CP, HP, species, and date confidence reasons in OCR and
   `ScanFrameFusion`.
3. Update `SpeciesRefiner`, `ScanConsistencyGate`, and `RarityCalculator` to
   prefer structured reasons while keeping raw marker fallback.
4. Keep `rawOcrText` temporarily for diagnostics and compatibility, but stop
   adding new confidence markers to it.
5. Move diagnostic path metadata out of `rawOcrText` into a dedicated
   diagnostics metadata object.
6. Remove raw marker fallback only after tests cover all downstream consumers.

## Tests Needed

- OCR result creation produces structured status for parsed and missing CP.
- Detailed pass backfill records `DETAIL_PASS` without changing primary fields.
- Species refiner reads structured species confidence before raw `NameHC`.
- Consistency gate uses structured conflict reasons for cross-family drift.
- Rarity calculator uses structured HP/CP/date status for scan confidence.
- Telemetry payload receives only reason codes and statuses, never raw OCR text.
- Diagnostic path metadata remains local-only and is not serialized into
  telemetry payload summaries.

## Telemetry Privacy Considerations

- Upload only reason codes, field statuses, confidence buckets, and boolean
  presence flags.
- Do not upload raw OCR lines, screenshot paths, diagnostic directories, file
  names from local storage, user account names, device identifiers, or API keys.
- Treat `rawOcrText` as local diagnostic data, not telemetry-safe metadata.
- Add forbidden-token tests covering Windows paths, `/tmp`, `apiKey`, `token`,
  `authorization`, `bearer`, `secret`, and diagnostic file paths.

## Staged Implementation Plan

1. Add `OcrConfidenceReasons` and unit tests for safe serialization.
2. Populate CP/HP/date status in `OCRProcessor` and `ScanFrameFusion`.
3. Populate species confidence source in `TextParser` and `SpeciesRefiner`.
4. Update `ScanConsistencyGate` to report structured retry reasons.
5. Update `RarityCalculator` scan confidence to use structured field statuses.
6. Update telemetry tests to assert only reason codes/statuses are serialized.
7. Remove new uses of raw marker strings from pipeline code.
8. Evaluate whether older raw markers can be removed after compatibility tests.

## Work That Should Stay Out Of One Pass

- Rewriting OCR parsing around the new model.
- Changing telemetry schema and remote ingestion simultaneously.
- Removing all `rawOcrText` consumers before characterization tests exist.
- Changing user-visible confidence labels without UX review.
