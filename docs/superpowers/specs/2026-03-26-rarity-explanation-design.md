# Rarity Explanation Design

## Goal

Keep the total rarity score, but replace the current point-heavy breakdown with human-readable reasons that explain why the Pokemon is valuable.

## Scope

This change only affects the way rarity is explained to the user. It does not change scan detection rules, variant matching, or the score math itself.

## User-Facing Behavior

- The total rarity score remains visible.
- The main explanation area becomes text-first.
- Each reason should say what was detected and why it matters.
- Reasons should prefer concrete signals over generic score labels.

Examples:

- `Caught during GO Fest 2023`
- `Holiday costume variant`
- `Shiny costume release`
- `First seen in Oct 2020`
- `Caught on Jan 5, 2017`

## Data Strategy

Use existing rarity and catalog data instead of introducing a new scoring system.

- `RarityScore.totalScore` remains the numeric headline.
- `RarityScore.explanation` becomes the primary human-readable explanation source.
- Variant metadata comes from the authoritative variant catalog already present in runtime:
  - `variantClass`
  - `isCostumeLike`
  - `eventTags`
  - `releaseWindow`
- Existing booleans like `isShiny`, `hasCostume`, `hasSpecialForm`, `isLucky`, `isShadow` still contribute reasons.

## UI Strategy

Overlay and full result screens should show:

- score header unchanged
- stat row unchanged
- IV row unchanged
- `WHY IT'S VALUABLE` section instead of `RARITY BREAKDOWN`

Each row should be text-first:

- primary text: explanation sentence
- optional secondary text: supporting detail
- no point badge per row

The ordering should prefer strongest positive reasons first:

1. event / costume / shiny / form reasons
2. age / caught date reasons
3. fallback generic rarity reasons

## Implementation Notes

- Keep the existing `analysis` plumbing, but repurpose it for explanations instead of numeric rows.
- Extend the UI model so one analysis item can carry:
  - title
  - optional detail
  - positive flag
- Generate richer explanation text in `RarityCalculator`.
- Rebuild `buildAnalysisItems()` so it prefers textual explanations over numeric `breakdown`.
- Overlay and full result UI should render title + optional detail, not title + points.

## Non-Goals

- No score formula changes.
- No scan logic changes.
- No rarity balancing changes.

