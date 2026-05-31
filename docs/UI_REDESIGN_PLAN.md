# Purpose: Plan crash-safe theme selection and UI polish for PokeRarityScanner.

# PokeRarityScanner UI Redesign Plan

## Current Context

PokeRarityScanner is an Android/Kotlin app using Jetpack Compose for the main
collection, result, overlay, dialog, and navigation surfaces, with some legacy
View/XML surfaces for splash and history. The scanner pipeline is passive and
must stay separate from UI polish work.

This plan consolidates the current audit findings into the execution plan for
Codex. It keeps the existing dark "Stitch" look as the fallback while adding
safe selectable themes and then applying tokens in small reversible steps.

## Target Design Language

- Premium mobile collector tool, not a web landing page.
- Dense but readable: scan, rarity, CP, HP, tags, and reasons should be quickly
  scannable.
- Pokemon-flavoured through red accents, type colours, rarity colour, and
  compact badge systems.
- Avoid generic purple SaaS gradients, deep nested cards, decorative blur/glass,
  and over-animated hero effects.
- Prefer hairline borders, clear spacing, and a limited typography hierarchy.

## Theme Architecture

Create a central typed theme model for Compose UI:

- `PokeThemeId`: typed IDs for selectable themes.
- `PokeThemeTokens`: semantic tokens for colour, spacing, radius, typography
  scale hooks, and elevation/glow.
- `PokeThemeRegistry`: all theme definitions plus safe lookup helpers.
- `getThemeById(id)`: returns a fully merged theme, never null.
- `safeThemeId(raw)`: validates persisted values and falls back to Classic.
- `LocalPokeTheme`: CompositionLocal exposing current tokens to composables.
- `ScanUiPreferences.themeId`: existing secure preference path for persistence.

Fallback requirements:

- Classic is the default and must remain available.
- Unknown/corrupt stored theme IDs resolve to Classic.
- Missing theme tokens resolve from Classic.
- UI components must receive a token object, never a nullable theme.

## Theme List

1. **Classic**
   - Current safe fallback.
   - OLED black background, Pokeball red accents, existing rarity colours.

2. **Obsidian Rarity**
   - Premium dark neutral.
   - Deep graphite surfaces, warm gold accent, subtle legendary glow.

3. **Pokedex Red**
   - Energetic red-accented theme.
   - Dark red-black surfaces, readable white text, restrained red fills.

4. **Mystic Blue**
   - Scanner-like blue/cyan style.
   - Dark navy surfaces, cyan accent, cool rare-tier emphasis.

5. **Forest Research**
   - Field-research style.
   - Dark green-neutral surfaces, herbal accent, high contrast text.

6. **Aurora Violet**
   - Premium collector theme.
   - Violet accents only as highlight and rarity glow; no full generic purple
     SaaS gradient.

Optional later:

- **Daylight Lab**
  - Only after XML/history surfaces and dialog contrast are audited for light
    backgrounds.

## Required Tokens

Colour:

- `background`
- `surface`
- `elevatedSurface`
- `card`
- `textPrimary`
- `textSecondary`
- `textMuted`
- `border`
- `accent`
- `accentSoft`
- `danger`
- `warning`
- `success`
- `rarityCommon`
- `rarityUncommon`
- `rarityRare`
- `rarityEpic`
- `rarityLegendary`
- `rarityMythical`
- `rarityShiny`

Shape and layout:

- `spacingXs`, `spacingSm`, `spacingMd`, `spacingLg`, `spacingXl`
- `radiusSm`, `radiusMd`, `radiusLg`
- `cardElevation`
- `glowAlpha`

## Settings Integration

- Theme selector lives in existing settings dialog alongside telemetry, haptics,
  and auto-copy settings.
- Do not show a theme picker on app start.
- Persist selected theme through `ScanUiPreferences`.
- Invalid saved value silently falls back to Classic and can be overwritten on
  next save.
- Labels should be user-readable: "Classic", "Obsidian Rarity", etc.

## Rarity Badge And Card Plan

- Replace scattered rarity colour lookups with theme tokens.
- Rarity badge must show tier label, score, and themed colour.
- Legendary, mythical, shiny, and special forms get token-driven glow/border
  only; avoid heavy gradients or animated decoration.
- Missing image/name/stats must render fallback text and never crash.

## Collection Grid, Filter, And Sort Plan

- Preserve existing filter logic: All, Legendary, Rare, Shiny, Lucky.
- Add clearer empty/no-result states before changing sort behaviour.
- Use theme tokens for background, bento cards, filter chips, FAB, and list
  cards.
- Keep current list layout; do not migrate to a grid until card width and
  tablet behaviour are tested.

## Detail Screen Plan

- Keep `ScanResultScreen` as the detail surface.
- Top identity section: name, score/tier, key CP/HP/date, tags.
- Explain "why this is valuable" via existing `valueSummary`,
  `decisionSupport`, and rarity explanations.
- Avoid card-in-card nesting; use sections and dividers.
- Missing optional fields render as "Unknown" or omit the row safely.

## Scanner, Import, And Result Flow Plan

- Do not change screen capture, OCR, vision, telemetry, or scan pipeline logic.
- Polish only UI states and user-readable messages:
  - permission required
  - import/capture ready
  - processing
  - result
  - error
- Result visuals read from theme tokens.
- Errors should be clear but not expose local paths, OCR raw text, or telemetry
  details.

## Loading, Empty, And Error States

- Collection empty state: explain how to start scanning and why no scans appear.
- No-result filter state: explain that the selected filter has no matches.
- Loading state: keep lightweight; avoid skeletons that imply network loading.
- Error state: show concise message and safe next action.

## Motion Rules

- Keep list and score animations under 500 ms unless they are existing score
  reveal animations.
- No parallax, scroll-jacking, cursor effects, glassmorphism, or web-only hero
  effects.
- Press feedback can use small scale changes if it does not cause layout shift.
- Respect Android reduced-animation settings in future pass.

## Test Plan

Required after each implementation commit:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Unit tests:

- `safeThemeId` accepts known IDs and rejects corrupt strings.
- `getThemeById` always returns Classic fallback for bad IDs.
- Missing token merge returns Classic token values.
- `ScanUiPreferences` persists theme ID safely.

Manual QA:

- Launch main collection screen under every theme.
- Open settings and switch themes repeatedly.
- Close and relaunch; selected theme persists.
- Corrupt preference value falls back to Classic.
- Open detail/result overlay under every theme.
- Verify contrast for text, badges, buttons, and empty states.

## Commit Sequence

1. `docs: add design reference onboarding and redesign plan`
2. `feat: add crash-safe selectable design themes`
3. `refactor: apply theme tokens across core screens`
4. `feat: polish rarity card visuals`
5. `feat: improve collection browsing states`
6. `feat: improve pokemon detail presentation`
7. `feat: polish scanner and result flow`
8. `docs: add final ui audit`

## Non-Goals

- No release builds.
- No new runtime dependencies unless a future task proves one is necessary.
- No scanner/import pipeline rewrites.
- No telemetry schema changes.
- No direct copy of web reference effects into mobile UI.
