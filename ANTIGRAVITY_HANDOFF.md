# Purpose: Antigravity/Codex agent takeover handoff document.

# Antigravity Takeover Handoff

## Current HEAD

```
1f52cd8bd8031f55c9aed0b056621c9d828656f6
```

Branch `main` is synchronized with `origin/main`. Working tree is clean.

## Project Purpose

PokeRarityScanner is a passive Android/Kotlin scanner for Pokemon GO collection
screens. It captures user-consented screen frames, extracts OCR and visual
signals, resolves Pokemon species and variants, calculates a 0-100 rarity score
across four axes (base species, variant, age, collector), and displays an
explanatory overlay. Optional telemetry uploads metadata-only scan results.

The app must remain passive: no gameplay automation, no input injection, no game
memory reading, no security bypass, no root requirement.

## Architecture Map

```text
ScreenCaptureService (foreground service, MediaProjection)
  -> ScanManager (orchestration, frame fusion, retry logic)
    -> OCRProcessor -> MLKitOcrProvider, ImagePreprocessor, ScreenRegions
    -> TextParser (CP, HP, name, date, candy, size, gender, cost)
    -> SpeciesRefiner (candidate ranking, OCR confidence, family hints)
    -> ScanConsistencyGate (cross-field validation, downgrade)
    -> VisualFeatureDetector (color, shiny, shadow, lucky, costume signals)
    -> VariantDecisionEngine (prototype matching, full variant resolution)
    -> Phase2VariantClassifier (asset-backed model classification)
    -> FullVariantMatcher (authoritative DB, historical events, sanity)
    -> RarityCalculator (four-axis scoring, tier, explanations)
  -> OverlayService (result display, feedback submission)
  -> ScanTelemetryCoordinator -> ScanTelemetryUploader (metadata upload)

Data layer:
  Room + SQLCipher (scan history, encrypted local DB)
  Assets (metadata JSON: pokedex, variants, rarity rules, events)
  RemoteMetadataSyncManager (living DB updates, SHA-256 verification)
```

## Important Packages and Classes

Service: `ScreenCaptureService`, `OverlayService`, `ScanManager`, `ScanStartupPolicy`
OCR: `OCRProcessor`, `TextParser`, `SpeciesRefiner`, `ScanConsistencyGate`
Vision: `VisualFeatureDetector`, `VariantDecisionEngine`, `Phase2VariantClassifier`
Data: `RarityCalculator`, `PokemonRepository`, `VariantCatalogLoader`, `EventContextManager`
Telemetry: `ScanTelemetryCoordinator`, `ScanTelemetryUploader`, `ScanTelemetryPayload`
Model: `PokemonData`, `RarityScore`, `ScanDecisionSupport`, `FullVariantMatch`

## Completed Codex Work

| Commit | Description |
| --- | --- |
| `0bf13cf0` | docs: add autonomous development audit |
| `210ac45c` | test: document visual variant threshold behavior |
| `76fd3f03` | test: add phase two variant conflict coverage |
| `b7bc8060` | feat: add safe pipeline decision summary |
| `bbbd6be0` | docs: plan structured OCR confidence reasons |
| `fcfcc7e7` | fix: avoid logging local screenshot paths |
| `1f52cd8b` | fix: avoid logging database artifact paths |

Earlier Codex work includes scan frame fusion tests, telemetry privacy coverage,
rarity age scoring tests, text parser noise filtering, and consistency gate
edge case tests.

## Current Risks

- `rawOcrText` is still used as an internal pipeline contract with pipe-delimited
  markers. Structured OCR confidence reasons are designed but not implemented.
- Diagnostic exporter stores local raw OCR/path metadata locally; telemetry is
  safer but local diagnostics need cleanup.
- Gradle 9 deprecation warnings and SDK XML version warnings remain.
- `ScanManager.kt` is a high-churn orchestration file with multiple concerns.
- Phase 2 classifier threshold behavior is partially documented but still hard
  to audit.

## Safe Workflow for Antigravity

1. Start every session with `git status -sb` and `git fetch origin`.
2. Confirm HEAD equals origin/main before making changes.
3. Read `AGENTS.md` and this file before editing source code.
4. Inspect target class signatures and call sites before modifying scanner
   pipeline, OCR, vision, scoring, or telemetry code.
5. Never stage `local.properties`, `.vscode/settings.json`, `scan_payload*.json`,
   keystores, `.env`, or signing credentials.
6. Make small, focused commits with tests first.
7. Run verification commands after every change.
8. Fetch origin before pushing; rebase safely; stop on conflicts.
9. Do not force push or rewrite history destructively.

## Commands to Verify

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Do not run release builds unless explicitly requested.

## Next Recommended Tasks

1. Add `OcrConfidenceReasons` model starting only with CP/HP/date.
2. Move diagnostic path metadata out of `rawOcrText`.
3. Expand date parsing ambiguity/regression tests.
4. Add `OcrDiagnosticsExporter` local-only privacy tests.
5. Add small Room repository regression tests for history/save flow.
6. Add parser regression tests for compact CP/HP labels and UI words.
7. Add asset schema validation for variant/rarity JSON files.
8. Improve living DB refresh diagnostics.
9. Add scan confidence badge support via `scanConfidenceLabel`.
10. Extract small pure helpers from `ScanManager` where tests can preserve
    behavior.

## Stop Conditions

- Merge conflicts.
- Failing tests with non-obvious fix.
- Need for secrets, credentials, or release signing.
- Device-only validation required.
- Task becomes architectural or too broad.
- More than 6 implementation commits in one session.
- Total diff becomes too large to review safely.
