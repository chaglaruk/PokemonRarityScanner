# Authoritative External Variant DB Design

## Goal

Replace partial local costume/event metadata with an authoritative external variant database for `species + costume + form + shiny + event metadata`, while simplifying result UI by removing IV emphasis and giving more room to narrative rarity explanations.

## Scope

This phase does not rewrite the full matcher. It introduces a stronger truth source and uses it first for explanation quality and later for classifier validation.

## Data Model

The new database will store normalized rows keyed by sprite/variant identity:

- `species`
- `dex`
- `formId`
- `variantId`
- `spriteKey`
- `variantClass`
- `isShiny`
- `isCostumeLike`
- `variantLabel`
- `eventLabel`
- `eventStart`
- `eventEnd`
- `sourceIds`
- `aliases`

## Sources

First-pass authoritative inputs:

1. Local PokeMiners sprite assets
2. Local `costume_keys.json`
3. Local `event_history.json`
4. External Bulbapedia Event Pokemon GO list

The generator merges these into one normalized asset file. Runtime uses this file for exact explanation text. Scan-time matching logic stays mostly unchanged in this phase.

## Runtime Rules

- Exact costume/event wording is shown only when the final species matches the external DB row, or can be safely remapped by exact `variantId/formId`.
- If the DB cannot prove the exact event, UI falls back to generic costume/form wording.
- If caught date predates event window, exact event wording is rejected.

## UI Changes

- Remove IV card from result surfaces for now.
- Expand `WHY IT'S VALUABLE` narrative area.
- Increase textbox padding, line-height, and font weight.
- Allow 2-3 sentence explanation with event, costume/form, and caught-date context.

## Success Criteria

- Result UI no longer shows IV.
- `WHY IT'S VALUABLE` has visibly larger, heavier text.
- Exact event names come from authoritative DB, not ad-hoc family propagation.
- Impossible event/date combinations are suppressed.
