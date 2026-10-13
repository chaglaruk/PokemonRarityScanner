# Local variant diagnostic schema, version 2

The debug-only OCR summary keeps existing fields and their meaning. `shiny` remains
the legacy raw full-match flag (including its historical missing-value false default).
It is not final classification truth and must not be read as a verified negative.

Additive fields:

- `diagnosticSchemaVersion`: 2.
- `rawFullMatchShiny`: the optional raw full-match flag; null if absent.
- `finalTypedShiny`: TRUE, FALSE, UNKNOWN, or null if final identity is unavailable
  or the two available typed identity sources disagree.
- `finalShinyReason`: a bounded diagnostic code describing that typed state. It
  does not invent detector provenance beyond the existing identity contract.
- `legacyShinySemantics`: `raw_full_match_with_missing_false_default`.

Raw TRUE / typed UNKNOWN and raw FALSE / typed TRUE are both legitimate. Typed
UNKNOWN is never converted into verified FALSE. Inconsistent typed summaries emit
`diagnostic_identity_disagreement`, retaining both original nested/input records.

No recognition thresholds, flags, identity policy, persistence entity, telemetry
payload, OCR provider, IV mapping or acceptance decision is changed by this schema.
