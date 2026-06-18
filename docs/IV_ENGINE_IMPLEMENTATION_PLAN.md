# IV Engine Implementation Plan

## Manager Decision

Proceed with Slice 1 only: add a pure Kotlin IV solver foundation under `app/src/main/java/com/pokerarity/scanner/domain/iv/` and one focused JVM test class under `app/src/test/java/com/pokerarity/scanner/domain/iv/`.

## Files

- Add `IvModels.kt` for base stats, evidence, candidate, result, solve mode, and star rating.
- Add `IvSolver.kt` for CPM/stardust tables, CP/HP math, and bounded candidate enumeration.
- Add `IvSolverTest.kt` for deterministic fixtures.
- Update required IV docs and ignore local `.codex`/graph artifacts.

## Tests First

Add tests for:

- Mewtwo level 40 perfect CP/HP fixture.
- Bulbasaur known CP/HP fixture.
- Exact result when CP, HP, level, and appraisal exact stats align.
- Range result for CP-only ambiguity.
- Stardust narrowing to known level bucket.
- Conflict result for impossible CP/HP/evidence.

## Non-Goals

- No production scan/OCR/UI wiring.
- No PvP stat product/rank.
- No external dependencies.
- No runtime internet.
- No changes to rarity scoring.

## Planned Verification

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*Iv*" --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## Verification Results

Passed on 2026-06-19:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*Iv*" --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Notes:

- The first wildcard IV test run timed out during cold Gradle setup; the exact `IvSolverTest` class then passed, and the required wildcard command passed on rerun with a longer timeout.
- `git diff --check` passed with only the existing CRLF warning for `.gitignore`.
- Release-reviewer verdict: GO. Scope is pure IV math plus docs/artifact cleanup; no OCR, UI, telemetry, network, or rarity scoring changes.
