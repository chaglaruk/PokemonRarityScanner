# Purpose: Define real UI design variants beyond color themes for PokeRarityScanner.

# PokeRarityScanner Design Variants Plan

## Why Previous Work Was Not Enough

The previous implementation added a strong **theme** layer:

- crash-safe color-token registry
- persisted theme selection
- safe fallback to Classic for unknown values

That work solved color customization and safety, but it did **not** introduce a
separate **design variant** system. Most screens still shared one base layout
with token substitution.

This plan introduces true design variants with separate composition patterns for
core surfaces.

## Core Distinction

- Theme: colors, spacing tokens, radii, elevation, contrast tuning
- Design Variant: visual system and composition
  - header structure
  - card layout
  - information density
  - filter/sort control shape
  - detail hierarchy
  - scanner/result presentation
  - empty/error state composition

Both settings will coexist and be persisted independently.

## Variant Set

1. Classic
2. Dex Console
3. Collector Album
4. Research Lab
5. Battle HUD
6. Aurora Showcase

Classic remains default and fallback.

## Architecture

### Data Model

- `UiDesignVariantId` enum with stable storage IDs:
  - `classic`
  - `dex_console`
  - `collector_album`
  - `research_lab`
  - `battle_hud`
  - `aurora_showcase`
- `safeDesignVariantId(raw: String?)`
  - trims/normalizes input
  - supports enum names and storage IDs
  - falls back to Classic for unknown/corrupt values
- `UiDesignVariantRegistry.getDesignVariant(id)`
  - always returns a known variant
  - falls back to Classic if missing

### App Wiring

- Add `designVariantId` to `ScanUiPreferences`.
- Keep theme and design variant independent in settings.
- Extend `PokeRarityTheme` to provide both:
  - `LocalPokeTheme` (existing)
  - `LocalUiDesignVariant` (new)

### Crash Safety

- Never trust persisted variant value.
- Never allow nullable/unknown design variant into composables.
- Guard all variant branches with Classic fallback.
- Keep scanner and repository logic untouched.

## UI Surface Plan

### 1) Collection/List

- Variant-aware top header treatment.
- Variant-aware filter controls:
  - segmented/console controls for Dex Console
  - chip/sticker controls for Collector Album
  - compact tabular controls for Research Lab
  - HUD-style controls for Battle HUD
  - showcase-style pills for Aurora Showcase
- Variant-aware empty state structure and wording blocks.

### 2) Pokemon/Rarity Card

- Variant-specific card structure (not only recolor):
  - Console strip cards
  - Album card front/back style
  - Lab data panel rows
  - HUD block with rank/status emphasis
  - Showcase hero card layout
- Keep shared data extraction logic centralized.

### 3) Detail Screen

- Variant-aware header hierarchy and stat grouping.
- Keep existing safe fallback fields (`-`, `Unknown`) for missing values.
- Preserve share/save/feedback behavior.

### 4) Scanner/Result Flow

- Variant-aware result-card treatment in `ScanResultOverlayCard`.
- Keep decision support and telemetry feedback paths unchanged.
- Improve readability for partial scan results under all variants.

### 5) Settings

- Keep current theme selector.
- Add parallel design variant selector.
- Persist both selections safely.

### 6) Empty/Loading/Error States

- Variant-aware empty states in collection.
- Scanner/result partial-data and fallback labels remain explicit.

## Testing Plan

Add focused unit tests for:

- `safeDesignVariantId` fallback behavior
- valid variant mapping from storage values and enum names
- design variant registry fallback to Classic
- coexistence of theme + variant selection persistence logic

Run verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## Commit Sequence

1. `docs: plan real selectable design variants`
2. `feat: add crash-safe design variant setting`
3. `feat: add variant-aware rarity cards`
4. `feat: add variant-aware collection and detail layouts`
5. `feat: add variant-aware scanner states`
6. `docs: update final ui audit for design variants`
