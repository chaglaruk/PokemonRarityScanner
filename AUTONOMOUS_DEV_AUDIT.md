# Purpose: Track autonomous development audit findings and safe implementation order.

# Autonomous Development Audit

Date: 2026-05-30

## Current Project State

PokeRarityScanner is a passive Android/Kotlin Pokemon GO collection scanner.
The local and remote `main` branches are synchronized after pushing Task 5
(`4cd7a4fe`). The normal validation commands are:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

The project has broad scanner coverage: 45 JVM unit test files and 8 Android
instrumentation test files. Several Android test assets include screenshot
fixtures under `app/src/androidTest/assets/scan_fixtures/`; these should stay in
test-only paths and should not be mirrored into telemetry payload fixtures.

## Biggest Reliability Risks

- `ScanManager.kt` is still a high-churn orchestration file. Frame fusion has
  been extracted, but capture, OCR, vision, scoring, overlay update, retry, and
  telemetry calls still meet in one runtime path.
- `TextParser.kt` is large and heuristic-heavy. Non-species filtering and
  field extraction need ongoing regression tests for compact UI labels, date
  ambiguity, CP/HP noise, and unknown species paths.
- `SpeciesRefiner.kt` and `ScanConsistencyGate.kt` depend on raw OCR markers
  such as `Name:`, `NameHC:`, and candy fields. This is useful but brittle
  because marker strings can become implicit contracts across modules.
- `VisualFeatureDetector.kt`, `VariantMergeLogic.kt`, and
  `VariantDecisionEngine.kt` contain many threshold-driven rescue/suppression
  paths. Most are now covered by regression tests, but the threshold intent is
  still hard to audit at a glance.
- `Phase2VariantClassifier.kt` and `Phase2VariantFeatureMerger.kt` need more
  conflict tests for global-vs-species predictions and unsupported targets.
- `RarityCalculator.kt` has deterministic age coverage now, but IV/CP/HP
  validation remains mathematically complex and should continue gaining focused
  boundary tests.

## Biggest Privacy And Security Risks

- Raw OCR text is intentionally kept local in several flows, including history
  and diagnostic exports. This is acceptable only while those paths stay local
  and tests prevent telemetry payload exposure.
- `OcrDiagnosticsExporter` writes screenshots, crop images, raw OCR, and
  screenshot path metadata to app-owned external files. This is useful for local
  debugging but must never be uploaded or committed as fixtures without
  sanitization.
- `ScanTelemetryRepository` now avoids raw OCR, diagnostic paths, and full
  screenshot paths in payload JSON, but future telemetry fields need the same
  production-bound privacy tests.
- Debug fixture export is disabled outside debug builds and not exported, which
  is good. Keep this invariant covered if debug tooling changes.
- Logs contain non-secret scan details such as species, CP/HP, confidence, and
  short raw OCR snippets. These are not remote payloads, but verbose production
  logs can still expose user scan context on a device.

## Biggest Missing Tests

- Visual and variant threshold behavior should be documented by named test
  expectations so threshold movement is intentional.
- Phase 2 global-vs-species promotion/demotion conflicts need direct tests.
- Safe pipeline decision summaries need pure tests before UI/log adoption.
- OCR confidence reasons need a design and staged tests before replacing raw
  marker parsing.
- More parser cases are needed for ambiguous caught dates, compact CP/HP labels,
  and UI words that are close to species names.
- History/database tests should cover saving and rendering sanitized scan
  summaries without relying on device-only validation.

## Biggest Performance Risks

- Large asset files are loaded for variants, signatures, phase-2 models, master
  pokedex, and legacy rarity data. Loader caching should remain explicit and
  testable.
- Bitmap-heavy paths depend on careful `recycle()` handling across capture,
  OCR, vision, prototype matching, phase-2 classification, and sharing.
- Detailed OCR and vision passes can be expensive. Avoid adding retries or extra
  passes without a narrow confidence trigger and tests.
- Logging in hot OCR/vision paths is useful for diagnosis but could become
  noisy during repeated scans.

## Safe UX And Debug Improvements

- Add a local-only sanitized pipeline decision summary that reports CP/name/date
  reliability, retry reason, variant evidence source, scoring headline, and
  timing without raw OCR or file paths.
- Surface clearer low-confidence reasons in overlay/result UI using existing
  `ScanDecisionSupport` fields.
- Add a local metadata version/debug line using `RemoteMetadataSyncManager`
  version only, without paths or payload details.
- Add missing-data warnings for CP/HP/date/species uncertainty where the score
  is computed with fallback assumptions.
- Improve local fixture guidance so screenshots and payloads are sanitized
  before they become regression tests.

## Safe Product Feature Ideas

- A scan confidence badge backed by existing `scanConfidenceLabel`.
- A local "why this score?" compact explanation that groups species, variant,
  age, IV, and event reasons.
- A local-only scan troubleshooting summary with no screenshots or raw OCR.
- History filters for uncertain scans or high-rarity scans if existing history
  APIs can support it with small changes.
- A user-visible telemetry privacy note that describes metadata-only operation
  and screenshot handling without adding new collection.

## Architectural Tasks To Avoid In One Pass

- Replacing `rawOcrText` marker strings with structured confidence models.
- Rewriting `ScanManager` orchestration.
- Rebuilding the visual classifier or retraining phase-2 models.
- Changing telemetry endpoint contracts.
- Large UI rewrites of overlay/result/history.
- Changing database schema without a migration plan.

## Recommended Implementation Order

1. Document visual/variant threshold behavior with focused tests.
2. Add Phase 2 classifier conflict coverage for global-vs-species cases.
3. Add a safe pipeline decision summary as a pure helper, or write a design doc
   if the integration surface is too broad.
4. Write the structured OCR confidence reasons design doc.
5. Add parser/date ambiguity regression tests.
6. Add sanitized diagnostics/export guidance.
7. Add history/save-result sanitization tests if Room/SQLCipher constraints can
   be avoided in JVM tests.
8. Continue extracting small pure helpers from `ScanManager` only where tests
   can preserve behavior.
