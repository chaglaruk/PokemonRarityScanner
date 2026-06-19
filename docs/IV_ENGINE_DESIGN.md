# IV Engine Design

## Target

Add a pure Kotlin IV candidate solver that can be unit-tested without Android services, OCR, UI, storage, or network access.

## Inputs

- Base stats: attack, defense, stamina.
- Optional evidence: CP, HP, exact level, level range, regular stardust cost, exact appraisal attack/defense/stamina, and appraisal star rating.
- Evidence is treated as a constraint. Missing evidence widens the result instead of forcing precision.

## Outputs

- `EXACT`: exactly one matching candidate.
- `RANGE`: multiple matching candidates.
- `INSUFFICIENT`: no useful constraints, invalid base stats, or conflicting evidence.
- Candidate count, IV percent min/max, level min/max, signals used, warnings, and exact candidate when unique.

## Math

- CP: `max(10, floor((atk * sqrt(def) * sqrt(sta) * cpm^2) / 10))`.
- HP: `max(10, floor(sta * cpm))`.
- Effective stats include IVs: `base + iv`.
- Levels are half-levels from 1.0 through 50.0.
- Stardust cost narrows regular Pokemon level buckets only. Shadow/lucky/purified cost normalization is deferred until visual state is reliably provided.
- Appraisal stars constrain total IV sum: 0-star `0..22`, 1-star `23..29`, 2-star `30..36`, 3-star `37..44`, 4-star `45`.

## Search Order

1. Validate base stats and at least one constraining evidence field.
2. Build the legal level list from exact level, level range, or stardust bucket.
3. Filter stamina IVs by HP if present.
4. Filter attack/defense/stamina by appraisal evidence.
5. Filter by CP if present.
6. Filter by star rating if present.
7. Summarize without claiming exact IV unless one candidate remains.

## Integration Points

- Later adapter can load `pokemon_base_stats.json` by species/form and pass `PokemonBaseStats`.
- Later scan integration can map `PokemonData` CP/HP/stardust/appraisal/arc fields into `IvEvidence`.
- Later UI can render `IvResult` or map it into existing `IvSolveDetails`.

## Non-Goals

- No OCR/appraisal detector changes.
- No PvP ranking.
- No persistence or schema changes.
- No network calls or live data fetch.
- No copied third-party source.
