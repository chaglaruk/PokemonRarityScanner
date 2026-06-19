# IV Checker Research

## Roles Used

| Role | Finding |
| --- | --- |
| iv-research-manager | Approved a small first slice: pure local IV candidate solver, no OCR/UI wiring. |
| market-app-researcher | Poke Genie and Calcy IV are the most visible scanner apps; both emphasize passive screen/screenshot workflows, appraisal scan, PvP IVs, and no-login safety. |
| public-formula-researcher | CP, HP, CPM, half-level enumeration, stardust buckets, appraisal star/bar constraints, and purification caveats were mapped from public references. |
| open-source-repo-researcher | PvPoke, GOIV, PvP_IVs, Ohbem, and older CP calculators validate the public math shape; this branch copies no third-party code. |
| ocr-workflow-analyst | Existing app OCR has CP/HP/name and power-up parser pieces, but appraisal and arc output are not yet wired as solver evidence. |
| algorithm-architect | Deterministic bounded enumeration is sufficient for Slice 1: 80 half-levels * 4096 IV spreads before filters. |
| pvp-rank-analyst | PvP rank/stat product is safe later, but skipped in Slice 1 to avoid broad data/ranking scope. |
| test-auditor | Tests should cover formula fixtures, exact solve, range solve, stardust level narrowing, appraisal constraints, and conflicts. |
| accuracy-benchmarker | Benchmark docs should record exact, ambiguous, CP-only, and conflicting evidence cases. |
| release-reviewer | GO only if no fake exact IVs, no network/runtime scraping, no copied proprietary code, and Gradle tests/build pass. |

## Feature Matrix

| Tool | Overlay | Screenshot/import | Appraisal read | Pre-catch range | PvP rank | Power-up/purify/raid |
| --- | --- | --- | --- | --- | --- | --- |
| Poke Genie | iOS screen recording / Android scan flow | Yes | Yes | Not primary listing claim | Yes | Yes |
| Calcy IV | Yes | Yes | Yes | Yes | Yes | Yes |
| Pokebattler | No local overlay | Manual/web collection inputs | No | No | Yes | Raid/team simulator |
| PvPoke | No | Manual/web inputs | No | No | Yes | PvP team/rank tools |
| GO Stadium | No | Manual/web inputs | No | No | Yes | PvP rank checker |
| Pokemon GO Hub / GamePress | No | Manual/web calculators | No | Some calculators/charts | Some references | CP, PvP, DPS, guide data |

## Key Findings

- Fast IV scanners avoid broad image understanding. They read a small number of visible fields: species/form, CP, HP, appraisal bars/stars, power-up cost, level arc, and visible special state.
- Accurate IV scanners solve constraints, not guesses. They enumerate IV spreads and legal half-levels, then filter by CP, HP, appraisal, stardust/power-up cost, and context.
- CP-only evidence is inherently ambiguous. A safe scanner must return a candidate range, not an exact IV.
- Appraisal evidence is high value because bars provide per-stat constraints and stars constrain total IV sum.
- PvP rankers compute battle stats and stat product under a league CP cap, but ranking every spread for every species/form is a separate slice.
- Safe implementation is local/offline. The app should not call Pokemon GO services, scrape accounts, automate input, read memory, or intercept traffic.

## Existing App Map

| Area | Current state |
| --- | --- |
| `PokemonData` | Has fields for CP, HP, stardust, power-up cost, appraisal stats, appraisal confidence, arc estimate/source. |
| `OCRProcessor` | Produces CP, HP, name/date/candy. Appraisal/stardust/arc fields are mostly null today. |
| `TextParser` | Has power-up stardust/candy parsing tests. |
| `RarityCalculator` | Has base stats loading, CPM table, stardust ranges, CP formula, and a private unused IV search. |
| `RarityScore` | Already has `IvSolveDetails`, but no candidate tuple model. |
| `ScanFrameFusion` | Preserves some scan fields, but appraisal/arc propagation is not fully complete. |
| UI/overlay | Some IV/PvP fields are anticipated, but not reliably populated. |

## Safe Opportunities

- Add a pure Kotlin solver that accepts already-visible scan evidence.
- Reuse the app's base stat asset and CPM/stardust tables.
- Return `EXACT` only when one candidate remains.
- Return `RANGE` with candidate counts/ranges when ambiguous.
- Return `INSUFFICIENT` with warnings for missing or conflicting evidence.

## Not Implementing

- No Pokemon GO login, private API, traffic inspection, memory reading, root, botting, spoofing, tap automation, or account scraping.
- No runtime network calls for IV solving.
- No PvP ranker in Slice 1.
- No OCR, overlay, rarity scoring, or UI rewiring in Slice 1.
