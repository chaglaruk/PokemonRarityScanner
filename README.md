# PokeRarityScanner

PokeRarityScanner is an Android scanner for Pokemon GO. It captures the visible Pokemon card, extracts OCR and visual signals, resolves species and variant state, computes IV confidence, and renders an overlay/result screen with telemetry-backed diagnostics.

## Current scope

- Real-device scan pipeline with MediaProjection
- OCR for `CP`, `HP`, power-up `Stardust`, and power-up `Candy`
- Species, shiny, costume, form, shadow, lucky, and appraisal-aware analysis
- Cost-based IV solver with `EXACT`, `RANGE`, and `INSUFFICIENT`
- Optional telemetry queue with offline staging and release-safe diagnostics
- Local release build and GitHub Release publishing scripts

## Module map

```text
.
├── app/                         Android application module
│   ├── src/main/java/com/pokerarity/scanner/
│   │   ├── data/                Models, Room DB, repositories, remote sync
│   │   ├── service/             Scan orchestration, overlay, capture lifecycle
│   │   ├── ui/                  Compose screens, overlay cards, dialogs
│   │   └── util/                OCR, CV, parsing, diagnostics, vision matching
│   └── src/test/               Focused unit tests for OCR, IV, matcher, telemetry
├── docs/                        Plans, release notes, implementation specs
├── external/                    External data snapshots and asset mirrors
├── scripts/                     Local build, release, and data refresh utilities
└── artifacts/                   Worklog, rollback notes, local diagnostic output
```

## Main flow

1. `ScreenCaptureService` acquires the current frame.
2. `OCRProcessor` crops the relevant regions and runs OCR/parsing.
3. Vision components resolve species, shiny, costume, form, and support signals.
4. `IvCostSolver` computes exact or range IV outcomes.
5. `RarityCalculator` builds the final score, notes, and decision-support payload.
6. `OverlayService` or `ResultActivity` renders the scan result.
7. `ScanTelemetryCoordinator` stages and flushes telemetry when enabled.

## Build

```powershell
.\gradlew.bat :app:assembleRelease
```

Release APK output:

```text
app/build/outputs/apk/release/PokeRarityScanner-v<version>-release.apk
```

## Test

Focused unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.pokerarity.scanner.IvCostSolverTest
.\gradlew.bat :app:testDebugUnitTest --tests com.pokerarity.scanner.TextParserPowerUpCostTest
```

Full debug unit suite:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Release

Local release build and GitHub upload:

```powershell
.\scripts\build_and_publish_release.ps1
```

Direct upload of an existing APK:

```powershell
.\scripts\publish_github_release.ps1 -Tag v1.6.0 -ApkPath .\app\build\outputs\apk\release\PokeRarityScanner-v1.6.0-release.apk
```

The local script is the primary release path. It does not depend on GitHub Actions billing state.

## Diagnostics

When OCR or IV solving is weak, the app can export a diagnostic bundle under:

```text
Android/data/com.pokerarity.scanner/files/iv_diagnostics/<diagnostic-id>/
```

Each bundle contains crop images plus `summary.json` with parsed signals, selected sources, and IV reasoning.

## Folder READMEs

- [app/README.md](app/README.md)
- [docs/README.md](docs/README.md)
- [scripts/README.md](scripts/README.md)
- [external/README.md](external/README.md)
- [data README](app/src/main/java/com/pokerarity/scanner/data/README.md)
- [util README](app/src/main/java/com/pokerarity/scanner/util/README.md)
