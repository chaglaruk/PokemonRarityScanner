# Final UI Audit

## What Changed

- Added a documented design/reference onboarding path for the Android/Kotlin app.
- Added a crash-safe selectable theme registry with Classic as the default.
- Added five new selectable themes: Obsidian Rarity, Pokedex Red, Mystic Blue, Forest Research, and Aurora Violet.
- Added persisted theme selection through the existing shared-preferences pattern.
- Applied semantic theme tokens to the collection screen, list cards, result screen, overlay result card, bottom navigation, rarity card surfaces, and overlay controls.
- Added theme-aware rarity tier visuals while preserving the Classic tier color behavior.
- Improved collection browsing states with filter counts and clearer no-scan versus no-match messaging.
- Improved result/detail presentation with a compact CP/HP/type stat row.
- Surfaced existing non-sensitive scan decision support in result cards when available.

## Theme List

- Classic: the default fallback and closest match to the existing dark scanner look.
- Obsidian Rarity: premium dark neutral surfaces with gold rarity accents.
- Pokedex Red: red-accented, energetic, readable dark surfaces.
- Mystic Blue: blue/cyan scanner-style surfaces.
- Forest Research: green research-style palette.
- Aurora Violet: violet premium collector palette.

## Fallback Behavior

- `safeThemeId()` accepts stored theme IDs and enum names.
- Blank, unknown, or corrupt theme strings fall back to Classic.
- `PokeThemeRegistry.getThemeById()` returns Classic if a requested theme has no override.
- `PokeThemeRegistry.mergeWithClassic()` backfills missing theme tokens from Classic.
- `LocalPokeTheme` defaults to Classic, so composables have a safe token set even outside the app theme wrapper.

## Files Changed

- `AGENTS.md`
- `docs/DESIGN.md`
- `docs/REFERENCE_ONBOARDING.md`
- `docs/UI_REDESIGN_PLAN.md`
- `app/src/main/java/com/pokerarity/scanner/data/local/ScanUiPreferences.kt`
- `app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/dialog/TelemetrySettingsDialog.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/main/MainActivity.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/theme/Theme.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/theme/PokeThemeTokens.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/components/Components.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/components/PokemonListCard.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/components/StitchNavigation.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/components/overlay/OverlayComponents.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/screens/CollectionScreen.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`
- `app/src/test/java/com/pokerarity/scanner/ui/theme/PokeThemeRegistryTest.kt`
- `app/src/test/java/com/pokerarity/scanner/ui/components/RarityTierVisualsTest.kt`
- `app/src/test/java/com/pokerarity/scanner/ui/screens/CollectionScreenFilterTest.kt`
- `app/src/test/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCardTest.kt`

## Tests Run

Passed repeatedly during the implementation cycle:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Static checks run:

- `git diff --check`
- Search for theme crash risks: `safeThemeId`, `getThemeById`, `getThemeByRawId`, `LocalPokeTheme`, and `themeId`.
- Search for remaining direct color usage in UI files.
- Staged-diff scans for secret/path keywords before commits.

## Known Limitations

- No emulator or device-only manual QA was performed in this run.
- Several legacy hard-coded colors remain where they are part of Pokemon type identity, Classic preservation, gradient stripes, decision-support accents, or small icon affordances.
- The theme selector currently lives inside the existing settings dialog rather than a dedicated full settings screen.
- Existing untracked Antigravity documentation files remain outside these commits and were not staged.
- No release build was run.

## Manual QA Checklist

- Launch the app with no stored theme and confirm Classic renders.
- Change each theme in settings and restart the app; confirm selection persists.
- Corrupt the stored theme value locally and confirm Classic loads without a crash.
- Check collection screen readability for all filters in every theme.
- Check empty collection and empty filtered states.
- Open a result/detail card with CP, HP, and type present.
- Open a result/detail card with missing CP or HP and confirm fallback dashes render.
- Confirm scan decision support appears only when data exists.
- Confirm overlay result card still opens, closes, saves, shares, and accepts feedback.
- Confirm no release, signing, local.properties, or telemetry secret files were touched.

## Screens And Features Checked

- App shell theme wrapper.
- Main collection list.
- Collection filter chips and empty states.
- Pokemon list cards.
- Result/detail screen.
- Overlay result card.
- Rarity tier card.
- Overlay action buttons, stat cells, and tag pills.
- Existing telemetry/settings dialog with theme selection.

## Crash-Safety Notes

- Theme parsing is defensive and falls back to Classic.
- Theme tokens are centralized and nullable overrides are merged into Classic before use.
- Result stat formatting uses safe fallbacks for missing CP, HP, and type.
- Collection empty-state strings are generated by small pure helpers covered by tests.
- Rarity tier visuals preserve Classic behavior and safely fall back for unsupported tier codes.
