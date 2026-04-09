# Stitch UI Adaptation Design

**Goal:** Adapt the app's dashboard and scan result screens to match the Stitch visual direction without changing scan, OCR, telemetry, or solver behavior.

## Scope

- Update the Compose dashboard in [CollectionScreen](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/ui/screens/CollectionScreen.kt).
- Update both result surfaces:
  - [ScanResultScreen](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt)
  - [ScanResultOverlayCard](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt)
- Add a lightweight shared bottom navigation shell and shared Stitch-style surface primitives.

## Constraints

- Keep the current app architecture stable.
- Do not change scan flow, navigation data flow, telemetry behavior, or IV solving.
- Keep EXACT / RANGE / INSUFFICIENT surfacing honest.
- Preserve current actions: scan, open result, save, close/back, share, telemetry settings.

## Screen Mapping

### Dashboard

- Retain current data wiring and recent scan list.
- Match the Stitch structure:
  - top brand/settings row
  - hero scan frequency block
  - summary cards
  - central scan CTA
  - filter chips
  - recent scan list
  - persistent bottom bar

### Result

- Move both result surfaces closer to the Stitch result mock:
  - gradient hero header
  - prominent rarity summary card
  - compact stats + IV surface
  - explanation section
  - three-button action row
  - persistent bottom bar on the full result screen

## Reuse Strategy

- Introduce only small shared Compose primitives where reuse is obvious:
  - bottom nav shell
  - subtle glow/gradient helpers
  - result section chrome
- Keep existing data models and repository/view-model usage unchanged.

## Risk Controls

- No business-logic refactor.
- No OCR/solver/variant threshold changes in this pass.
- No new screens or route rewrites.
- Use release build verification after UI changes.
