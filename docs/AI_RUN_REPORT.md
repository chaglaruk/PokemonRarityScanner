# AI Run Report: Collector Intelligence Phase 1a

## Metadata
* **Current branch:** `feature/collector-intelligence-phase-1a`
* **Base commit:** `b96fd061` (Merge pull request #7 from chaglaruk/research/iv-checker-engine-v2)
* **Commit hash:** `7f740dc25b0c8c1cbfb4b07ebce60279b6aa511f`
* **Draft PR link:** [Create Pull Request](https://github.com/chaglaruk/PokemonRarityScanner/pull/new/feature/collector-intelligence-phase-1a)

## Operations
* **CodeGraph/Graphify:** Not used as the Codegraph MCP server was available but without eagerly loaded tools/schemas for the repository context provided. Used `grep_search` to map existing classes.
* **Exact files changed:**
  * `app/src/main/java/com/pokerarity/scanner/data/local/db/CollectionEntryDao.kt` (NEW)
  * `app/src/main/java/com/pokerarity/scanner/data/local/db/CollectionEntryEntity.kt` (NEW)
  * `app/src/main/java/com/pokerarity/scanner/data/model/VariantIdentityKey.kt` (NEW)
  * `app/src/main/java/com/pokerarity/scanner/data/repository/CollectionDexRepository.kt` (NEW)
  * `app/src/main/java/com/pokerarity/scanner/data/repository/CollectionEntryMapper.kt` (NEW)
  * `app/src/test/java/com/pokerarity/scanner/data/model/VariantIdentityKeyTest.kt` (NEW)
  * `app/src/test/java/com/pokerarity/scanner/data/repository/CollectionEntryMapperTest.kt` (NEW)
  * `app/src/main/java/com/pokerarity/scanner/data/local/db/AppDatabase.kt` (MODIFIED)
  * `app/src/main/java/com/pokerarity/scanner/di/DatabaseModule.kt` (MODIFIED)

## Tests
* **Exact tests run:**
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
  * `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
* **Results:**
  * Unit tests: **Passed** (Build successful in 1m 52s)
  * assembleDebug: **Passed** (Build successful in 53s)
* **Skipped tests:** Room DB migration test `MigrationTest` is skipped as no local test suite infrastructure existed for database migrations. Relied on standard unit testing for the business logic in Mapper and Identity Keys. Added explicit schema creation in `MIGRATION_4_5`.

## Confirmations
* **Migration details:** Added explicit schema script `MIGRATION_4_5` for Room. Existing scan history data is entirely unaffected, keeping `ScanHistoryEntity` table untouched.
* **scan_history survives:** Confirmed. The migration is strictly additive (adding `collection_entries` table).
* **RarityCalculator was not rewritten:** Confirmed. The mapper strictly receives final calculated rarity data.
* **Collection data is not sent to telemetry:** Confirmed. The `CollectionEntryEntity` mapper logic remains entirely distinct from the telemetry upload logic.
* **No local artifacts committed:** Confirmed.

## Risks / Follow-ups
* Backfilling of old scan_history into the `collection_entries` table is still outstanding (was skipped in Phase 1a per instructions).
* Background queries for CollectionDexRepository could be built upon for future phases.

## Exact next human action
1. Create Draft PR manually using the link above since the agent lacks GH CLI auth.
2. Review the code.
3. Merge `feature/collector-intelligence-phase-1a` into `main`.

---

# AI Run Report: Collector Intelligence Phases 1A-1D Audit

## Metadata
* **Current branch:** `feature/collector-intelligence-phase-1d-domain-cleanup`
* **Branch base:** `6d0c59f6` (PR #10 merge)
* **Latest fetched main:** `9065f97d`
* **Phase 1D commits:** `def479fb`, `6944233f`
* **Draft PR:** https://github.com/chaglaruk/PokemonRarityScanner/pull/11

## Findings and Decision
* Phases 1A-1C preserve background identity, store and index XXL/XXS outside the base identity key, use the explicit additive Room 4-to-5 migration, and do not alter telemetry, UI, `ScanManager`, `RarityCalculator`, or `rarity_rules.json`.
* Phase 1B safety behavior correctly returns `REVIEW` for unknown keys and low confidence, never marks legendary/mythical/god-tier or duplicate XXL/XXS scans transfer-safe, and normalizes blank, `NONE`, location, and special background types.
* Phase 1D removes the database entity from the collector-domain lookup contract and maps entities to `CollectionLookupEntry` in `CollectionDexRepository`.
* Audit issue: Phase 1D lacked direct tests for `hasSameXXL`, `hasSameXXS`, and XXS service safety. Added only those tests; no production code changed during the audit.
* Phase 2A was intentionally deferred because actual decision wiring requires a reviewed `ScanManager` integration point.

## Changed Files
* `app/src/test/java/com/pokerarity/scanner/domain/collector/CollectorIntelligenceServiceTest.kt`
* `docs/AI_RUN_REPORT.md`

## Verification
* Focused `CollectorIntelligenceServiceTest`: **Passed**.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`: **Passed**.
* `git diff --check origin/main...HEAD`: **Passed**.
* PR checks: GitHub test and Semgrep **Passed** for `6944233f`.

## Risks and Next Task
* The branch predates the independent `9065f97d` metadata refresh, but PR #11 is conflict-free and GitHub reports it mergeable.
* After PR #11 merges, prepare a minimal Phase 2A plan for injecting `CollectorIntelligenceService` at the scan-result boundary before editing `ScanManager`; do not change overlay UI, telemetry, or automatic collection recording.

## Exact Next Human Action
Review and merge draft PR #11 when satisfied; then authorize the small Phase 2A `ScanManager` wiring plan.

---

# AI Run Report: Calcy IV Recognition Gap Research

## Metadata
* **Current branch:** `feature/collector-intelligence-phase-2c-2e-scan-decision`
* **Scope:** Report-only defensive interoperability research. No app implementation changes, no Calcy folder modifications, no release build, no commit.
* **Primary report:** `docs/research/calcy_iv_recognition_gap_report.md`

## Findings and Decision
* Calcy IV appears more reliable because recognition is layered: repeated MediaProjection capture, screen-state routing, device/layout autoconfiguration, field-specific OCR/preprocessing, localization-aware dictionaries, visual deciders, and game-stat validation.
* PokemonRarityScanner has solid passive capture, path sanitization, frame fusion, species refinement, visual classifiers, CP validation scaffolding, and privacy boundaries.
* The largest gap is early screen understanding: current live OCR mostly feeds CP, HP, name, candy, and date from percentage crops, while downstream code expects richer fields such as arc, appraisal, stardust, size, lucky, and stable raw OCR keys.
* Recommended next implementation slice is Phase A from the report: instrumentation plus strict fixture labeling before any crop rewrite.

## Changed Files
* `docs/research/calcy_iv_recognition_gap_report.md` (new)
* `docs/AI_RUN_REPORT.md` (this manager log entry)

## Verification
* `git status --short`: clean before report creation.
* `.\gradlew.bat tasks --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: **Passed**.
* Fixture inventory: 47 JSON cases, 47 PNG fixtures, 16 strict, 19 with any expected label, 28 all-null exploratory.

## Risks and Next Task
* Do not copy Calcy code, assets, databases, traineddata, strings, thresholds, or proprietary implementation details.
* Risk is overfitting future recognition fixes to too few screenshots; mitigate by expanding strict fixtures across Samsung S25, Pixel 4a, appraisal/detail/scrolled/storage/transition states, and at least one non-English screen.
* Next task: implement Phase A only, adding local diagnostics for screen state, anchors, crop rectangles, OCR blocks, field candidates, and confidence reasons, then label existing fixture cases.

---

# AI Run Report: Recognition Reliability Phase A — Local Diagnostics and Fixture Audit

## Metadata
* **Current branch:** `feature/collector-intelligence-phase-2c-2e-scan-decision`
* **Scope:** Phase A only. Local recognition diagnostics and offline fixture audit support.
* **Calcy boundary:** No Calcy files touched, copied, or referenced for implementation.

## Files Changed
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanDiagnosticModels.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt`
* `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporterTest.kt`
* `scripts/audit_scan_fixtures.ps1`
* `docs/AI_RUN_REPORT.md`

## What Changed
* Added serialization-friendly local diagnostic models for scan reports, frames, crop rectangles, OCR blocks, field candidates, confidence reasons, Pokemon summaries, and visual summaries.
* Extended `OCRProcessor` with `processImageWithDiagnostics()` while keeping `processImage()` behavior-compatible.
* Extended local diagnostics JSON with frame dimensions, CP quality, `Unknown` screen-state placeholder, current crop rectangles, ML Kit text blocks, field candidates, stable OCR field keys, final parsed summary, confidence reasons, retry/fallback reason, and variant visual summary when available.
* Kept diagnostics on the existing local export/failure path. No screenshots or raw diagnostics are added to telemetry.
* Added `scripts/audit_scan_fixtures.ps1` for offline fixture inventory.

## Fixture Audit Result
* `cases=47`
* `fixtures=47`
* `strict=16`
* `all_null_exploratory=28`
* `expected_species=19`
* `expected_cp=19`
* `expected_screen_type=0`
* `missing_fixture_files=0`

## Verification
* `.\gradlew.bat :app:testDebugUnitTest --tests "com.pokerarity.scanner.util.ocr.OcrDiagnosticsExporterTest" --no-daemon --console=plain`: **Passed**.
* `.\scripts\audit_scan_fixtures.ps1`: **Passed** with counts above.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`: **Passed**.

## Privacy Confirmation
* Production telemetry still passes `screenshotPath = null` from live scans.
* Local diagnostic JSON can include screenshot paths/raw OCR only in the app-local diagnostic export.
* Telemetry debug payload helpers still omit raw OCR text, diagnostic directory, diagnostic files, local paths, and screenshot paths by default.
* No network calls, external storage broad writes, Calcy assets, Calcy code, decompiled files, secrets, or release builds were added.

## Intentionally Not Implemented
* No adaptive crop rewrite.
* No OCR engine replacement.
* No new screen classifier beyond the `Unknown` placeholder.
* No fixture relabeling.
* No screenshot upload or raw diagnostic telemetry.

## Next Recommended Task
Phase B should add an original adaptive crop/anchor layer: a `ScreenClassifier` and `ScreenGeometry` model that derives CP/name/HP/candy/date/appraisal regions from detected Pokemon GO UI anchors, with fixed `ScreenRegions` kept as fallback.

---

# AI Run Report: Recognition Reliability Phase B - ScreenClassifier and ScreenGeometry

## Metadata
* **Current branch:** `feature/collector-intelligence-phase-2c-2e-scan-decision`
* **Scope:** Phase B only. Original screen-state classification, adaptive geometry, OCR crop routing, local diagnostics, and fixture audit improvements.
* **Calcy boundary:** No Calcy decompiled folder, code, assets, databases, traineddata, thresholds, constants, or identifiers were accessed or copied for implementation.

## Files Changed
* `app/build.gradle.kts`
* `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanDiagnosticModels.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenClassifier.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenGeometry.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenGeometryBuilder.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/ScreenClassifierTest.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/ScreenGeometryBuilderTest.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporterTest.kt`
* `scripts/audit_scan_fixtures.ps1`
* `docs/AI_RUN_REPORT.md`

## Implementation Summary
* Added original `ScreenClassifier` states: `PokemonDetail`, `PokemonDetailScrolled`, `Appraisal`, `StorageList`, `Encounter`, `Transition`, and `Unknown`.
* The classifier uses conservative bitmap heuristics: dimensions, CP/header contrast, name/HP band contrast, large bright detail-card evidence, appraisal panel rows, storage grid repetition, encounter ring color, and low-content fallback.
* Added `ScreenGeometry` and `ScreenGeometryBuilder` with per-field crops, anchors, per-crop confidence, crop provenance, and fallback reasons.
* `ScreenGeometryBuilder` derives CP, HP, Name, DynamicName, Candy, Date, Stardust, SizeTag, AppraisalBox, AppraisalAttack/Defense/Stamina, and Arc crops when anchors are strong enough.
* Existing `ScreenRegions` fixed percentage crops remain the fallback. Low-confidence, unknown, transition, and storage screens use legacy fallback for existing fields and `not-available` for fields without a safe crop.
* `OCRProcessor` now builds geometry before field OCR and uses geometry primary crops while keeping existing OCR/parsing behavior and existing HP/candy fallback passes.
* Local diagnostics now include screen type, screen confidence, anchors, crop provenance, crop confidence, crop reasons, and geometry fallback reasons.
* `ScanManager` summarizes the best frame screen state/confidence in the local scan report. Live telemetry still passes `screenshotPath = null`.
* `scripts/audit_scan_fixtures.ps1` now reports screen-type label counts, missing screen-type labels, and the first fixtures to label.
* Unit-test JVM locale is pinned to English/US so Robolectric/Conscrypt native lookup remains stable on Windows.

## Fixture Audit Result
* `cases=47`
* `fixtures=47`
* `strict=16`
* `all_null_exploratory=28`
* `expected_species=19`
* `expected_cp=19`
* `expected_screen_type=0`
* `missing_expected_screen_type=47`
* `missing_fixture_files=0`
* First recommended labels: `armored_lucky_mewtwo_seed_0`, `armored_lucky_mewtwo_seed_1`, `armored_lucky_mewtwo_seed_2`, and the first live variant batch controls.

## Verification
* `git status --short`: showed the expected dirty Phase A worktree plus new Phase B files; existing dirty files were preserved.
* `.\gradlew.bat :app:testDebugUnitTest --tests "com.pokerarity.scanner.util.ocr.ScreenClassifierTest" --tests "com.pokerarity.scanner.util.ocr.ScreenGeometryBuilderTest" --tests "com.pokerarity.scanner.util.ocr.OcrDiagnosticsExporterTest" --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`: **Passed**.
* `.\scripts\audit_scan_fixtures.ps1`: **Passed** with counts above.
* `git diff --check`: **Passed** (line-ending warnings only).

## Privacy Confirmation
* No screenshot telemetry was added.
* No diagnostic telemetry was added.
* No external network calls were added.
* No Calcy code/assets/databases/traineddata/thresholds/constants were used.
* Diagnostics remain local-only through the existing app-local export path.
* Broad storage writes were not added.
* Existing screenshot path sanitization in `ScanManager.sanitizeScreenshotPaths()` remains preserved.
* `ScanManager` still sends live telemetry with `screenshotPath = null`.
* `ScanTelemetryRepository.buildPayloadDebugInfo()` still blanks raw OCR and nulls diagnostic directory/files.

## Intentionally Not Implemented
* No Phase C OCR preprocessing rewrite.
* No ML Kit replacement.
* No species resolver rewrite.
* No remote telemetry behavior changes.
* No screenshot or diagnostic upload.
* No fixture relabeling beyond audit recommendations.
* No removal of legacy fixed-crop fallback.

## Next Recommended Task
Phase C should use the new geometry crops for field-specific OCR preprocessing and raw-field normalization only: numeric-focused CP/HP/stardust passes, stable raw keys (`Name`, `NameHC`, `NameDynamic`, `CP`, `HP`, `Candy`, `Date`, `SizeTag`, `Stardust`, `Arc`, `Appraisal*`), and focused tests proving missing fields remain missing/not-run. Do not rewrite species resolution or telemetry in Phase C.

---

# AI Run Report: Recognition Reliability Phase C - Field-Specific OCR and Raw Key Normalization

## Metadata
* **Current branch:** `feature/collector-intelligence-phase-2c-2e-scan-decision`
* **Scope:** Phase C only. Field-specific ML Kit OCR preprocessing, candidate normalization/scoring, stable raw OCR keys, and local diagnostic detail.
* **Calcy boundary:** No Calcy decompiled folder, code, assets, databases, traineddata, thresholds, constants, or identifiers were accessed or copied for implementation.

## Files Changed
* `app/src/main/java/com/pokerarity/scanner/util/ocr/FieldCandidateNormalizer.kt`
* `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanDiagnosticModels.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt`
* `app/src/test/java/com/pokerarity/scanner/ScanTelemetryRepositoryTest.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/FieldCandidateNormalizationTest.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporterTest.kt`
* `scripts/audit_scan_fixtures.ps1`
* `docs/AI_RUN_REPORT.md`

## Implementation Summary
* Added pure Kotlin `FieldCandidateNormalizer` for conservative CP, HP, stardust, and appraisal OCR noise normalization.
* Kept ML Kit as the only OCR provider and reused existing bitmap preprocessing: original crop, white mask, high contrast, HP text, stardust text, strict mask, and adaptive threshold where already available.
* `OCRProcessor` now runs multiple variants for CP and HP, scores candidates deterministically, records loser/winner reasons, and preserves crop provenance/confidence from `ScreenGeometry`.
* Name OCR now uses geometry-aware dynamic search plus static name crop variants while keeping the existing parser/species resolver behavior.
* Detailed-pass secondary OCR now attempts stardust, size tag, appraisal stat crops when available, and lucky label text; unavailable or intentionally skipped fields are represented as `not-run`.
* Stable raw OCR keys are emitted for `CP`, `HP`, `Name`, `NameDynamic`, `NameHC`, `Candy`, `Date`, `Stardust`, `SizeTag`, `Arc`, `AppraisalAttack`, `AppraisalDefense`, `AppraisalStamina`, `LuckyDetected`, and `RawText`.
* Local scan diagnostics now include crop name/rect/provenance/confidence, preprocessing variant, raw text, normalized text, parser result, candidate score, winner flag, reason, selected value, and field status.
* Telemetry debug parsing now ignores stable marker values (`missing`, `not-run`, `skipped`) for `NameDynamic` so the new always-present key does not create a false dynamic-name source.
* `scripts/audit_scan_fixtures.ps1` now reports expected HP/appraisal counts and first fixtures needing screen type, CP, HP, species, and appraisal labels.
* `app/build.gradle.kts` was already changed in Phase B only for the Windows/Robolectric locale pin; no new dependencies were added in Phase C.

## Fixture Audit Result
* `cases=47`
* `fixtures=47`
* `strict=16`
* `all_null_exploratory=28`
* `expected_species=19`
* `expected_cp=19`
* `expected_hp=17`
* `expected_appraisal_fields=0`
* `expected_screen_type=0`
* `missing_expected_screen_type=47`
* `missing_expected_species=28`
* `missing_expected_cp=28`
* `missing_expected_hp=30`
* `missing_expected_appraisal_fields=47`
* `missing_fixture_files=0`

## Verification
* `.\gradlew.bat :app:testDebugUnitTest --tests "com.pokerarity.scanner.util.ocr.FieldCandidateNormalizationTest" --tests "com.pokerarity.scanner.util.ocr.OcrDiagnosticsExporterTest" --no-daemon --console=plain --no-build-cache`: **Passed**.
* `git status --short`: showed expected dirty Phase A/B worktree plus new Phase C files; existing dirty files were preserved.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`: **Passed**.
* `git diff --check`: **Passed** (line-ending warnings only).
* `.\scripts\audit_scan_fixtures.ps1`: **Passed** with counts above.

## Privacy Confirmation
* No screenshot telemetry was added.
* No diagnostic telemetry was added.
* No external network calls were added.
* No Calcy code/assets/databases/traineddata/thresholds/constants were used.
* Diagnostics remain local-only through the existing app-local export path.
* Broad storage writes were not added.
* Existing screenshot path sanitization in `ScanManager.sanitizeScreenshotPaths()` remains preserved.
* `ScanManager` still sends live telemetry with `screenshotPath = null`.
* Telemetry upload behavior was not changed; the only telemetry-adjacent change ignores new local OCR marker strings when deriving `dynamicNameSource`.

## Intentionally Not Implemented
* No Phase D species/form resolver rewrite.
* No Phase E confidence gate rewrite.
* No OCR engine replacement and no Tesseract/OpenCV/native OCR dependency addition.
* No ML Kit provider replacement.
* No new species/form/localization datasets.
* No screenshot or diagnostic upload.
* No remote telemetry behavior changes.
* No fixture relabeling.
* No removal of legacy fixed-crop fallback.

## Next Recommended Task
Phase D should improve species/form resolution using only project-owned data and the richer Phase C candidate diagnostics: separate display-name OCR from canonical species/form ranking, add focused alias/form tests, preserve existing resolver fallback behavior, and do not change OCR provider, confidence gates, telemetry, or Calcy boundaries.

---

# AI Run Report: Recognition Reliability Phase D - Species and Form Resolver Improvements

## Metadata
* **Current branch:** `feature/collector-intelligence-phase-2c-2e-scan-decision`
* **Scope:** Phase D only. Species/form resolution using project-owned names, family/move/profile assets, and Phase C field candidate diagnostics.
* **Calcy boundary:** No Calcy decompiled folder, code, assets, databases, traineddata, thresholds, constants, or identifiers were accessed or copied for implementation.

## Files Changed
* `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesFormResolver.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt`
* `app/src/main/java/com/pokerarity/scanner/data/model/PokemonData.kt`
* `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanDiagnosticModels.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/SpeciesFormResolverTest.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporterTest.kt`
* `app/src/test/java/com/pokerarity/scanner/ScanTelemetryRepositoryTest.kt`
* `scripts/audit_scan_fixtures.ps1`
* `docs/AI_RUN_REPORT.md`

## Implementation Summary
* Added original `SpeciesFormResolver` to separate display OCR text from canonical species/form ranking.
* Resolver consumes stable raw keys and Phase C `Name`, `NameDynamic`, and `NameHC` field candidates, then ranks project-owned species via existing `TextParser`.
* Ranking uses exact/strong/fuzzy name evidence, candy exact/family evidence, existing move hints, and existing CP/HP/profile fit when available.
* Marker and non-name values (`missing`, `not-run`, `skipped`, empty strings, numeric-only text, CP/HP/date/stardust-like text) are rejected before candidate ranking.
* `SpeciesRefiner` now accepts optional Phase C field candidates and folds resolver scores into its existing conservative replacement logic instead of replacing the old fallback path.
* `ScanManager` passes frame field candidates into `SpeciesRefiner` and carries resolver trace into local retry/final scan diagnostics.
* Form handling is conservative: owned authoritative labels/aliases are scanned for clear form-like labels, but no new external form database or costume/shiny/background rewrite was added.

## Diagnostics
* `PokemonData` now carries nullable `speciesResolverTrace`.
* Local diagnostics include display-name candidates, normalized text, canonical species candidates, form candidates, candidate scores, winner reason, loser reasons, evidence used, evidence missing, and fallback path.
* `OcrDiagnosticsExporter` writes resolver trace only to local summary JSON / local scan diagnostics.
* `ScanTelemetryRepositoryTest` verifies resolver trace strings are not serialized into production telemetry debug payloads.

## Fixture Audit Result
* `cases=47`
* `fixtures=47`
* `strict=16`
* `all_null_exploratory=28`
* `expected_species=19`
* `expected_form=0`
* `expected_cp=19`
* `expected_hp=17`
* `expected_appraisal_fields=0`
* `expected_screen_type=0`
* `missing_expected_screen_type=47`
* `missing_expected_species=28`
* `missing_expected_form=47`
* `missing_expected_cp=28`
* `missing_expected_hp=30`
* `missing_expected_appraisal_fields=47`
* `missing_fixture_files=0`
* First species/form resolver labels to add: the three `armored_lucky_mewtwo_seed_*` fixtures, the first live variant batch fixtures, and the first `regression_20260317_1355` fixtures.

## Verification
* `.\gradlew.bat :app:testDebugUnitTest --tests "com.pokerarity.scanner.util.ocr.SpeciesFormResolverTest" --tests "com.pokerarity.scanner.util.ocr.OcrDiagnosticsExporterTest" --tests "com.pokerarity.scanner.ScanTelemetryRepositoryTest" --no-daemon --console=plain`: **Passed**.
* `git status --short`: showed expected dirty Phase A/B/C worktree plus new Phase D files; existing dirty files were preserved.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`: **Passed**.
* `git diff --check`: **Passed** (line-ending warnings only).
* `.\scripts\audit_scan_fixtures.ps1`: **Passed** with counts above.

## Privacy Confirmation
* No screenshot telemetry was added.
* No diagnostic telemetry was added.
* No raw OCR/resolver trace telemetry was added.
* No external network calls were added.
* No Calcy code/assets/databases/traineddata/thresholds/constants were used.
* No new third-party datasets were added.
* Diagnostics remain local-only through the existing app-local export path.
* Broad storage writes were not added.
* Existing screenshot path sanitization in `ScanManager.sanitizeScreenshotPaths()` remains preserved.
* `ScanManager` still sends live telemetry with `screenshotPath = null`.

## Intentionally Not Implemented
* No Phase E confidence gate rewrite.
* No OCR engine replacement and no OCR dependency addition.
* No rarity scoring rewrite.
* No IV solver rewrite.
* No remote telemetry behavior change.
* No screenshot or diagnostic upload.
* No new species/form/localization dataset.
* No fixture relabeling.
* No removal of legacy fallback behavior.

## Next Recommended Task
Phase E should add a conservative confidence/validation gate that consumes screen classification, crop provenance, field candidate scores, resolver trace confidence, and existing CP/HP/appraisal evidence to decide accept/retry/uncertain. Do not change OCR provider, rarity scoring, telemetry, or screenshot upload behavior.

---

# AI Run Report: Recognition Reliability Phase E-F-G - Confidence Gate, Fixture Hardening, and Final Stabilization

## Metadata
* **Current branch:** `feature/collector-intelligence-phase-2c-2e-scan-decision`
* **Scope:** Remaining recognition reliability work: Phase E confidence gate, Phase F fixture/test hardening, and limited Phase G visual support.
* **Calcy boundary:** No Calcy decompiled folder, code, assets, databases, traineddata, thresholds, constants, or identifiers were accessed or copied for implementation.

## Files Changed
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanConfidenceGate.kt`
* `app/src/main/java/com/pokerarity/scanner/data/model/PokemonData.kt`
* `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ScanErrorHandler.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanDiagnosticModels.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt`
* `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/ScanConfidenceGateTest.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporterTest.kt`
* `app/src/test/java/com/pokerarity/scanner/ScanTelemetryRepositoryTest.kt`
* `scripts/audit_scan_fixtures.ps1`
* `docs/research/scan_fixture_labeling_plan.md`
* `docs/research/recognition_reliability_final_report.md`
* `docs/AI_RUN_REPORT.md`

## Implementation Summary
* Added original `ScanConfidenceGate` with `ACCEPT`, `ACCEPT_LOW_CONFIDENCE`, `RETRY`, `UNCERTAIN`, and `REJECT_NOT_POKEMON_SCREEN` decisions.
* Gate consumes existing A-D evidence: screen state/confidence, crop provenance/confidence, field candidate status and scores, resolver confidence/alternatives, frame agreement, CP crop quality, consistency gate reason, appraisal fields, and existing visual summary.
* Gate is conservative: marker-only fields and raw-text-only evidence cannot accept; unknown screens with weak OCR retry; storage screens reject; conflicts downgrade to uncertain.
* `ScanManager` runs the gate after OCR, resolver, consistency, CP correction, and existing visual summary, but before rarity scoring, overlay, save, and telemetry.
* Only `ACCEPT` and `ACCEPT_LOW_CONFIDENCE` continue to overlay/save. `UNCERTAIN`, `RETRY`, and `REJECT_NOT_POKEMON_SCREEN` export local diagnostics and use existing error/retry handling.
* Added `NOT_POKEMON_SCREEN` as a non-retry scan error for clear non-detail screens.
* Added nullable `scanDecision` metadata to `PokemonData` and local scan diagnostics.
* Local diagnostics export now includes the scan decision at root and in `scanDiagnostics`.
* Remote telemetry remains explicit metadata only and does not serialize gate traces.

## Phase F Fixture/Test Hardening
* `scripts/audit_scan_fixtures.ps1` now reports expected confidence decision counts, expected minimum confidence, overlay/save flag coverage, and missing-label priority groups.
* `ScanRegressionTest` now supports optional fixture expectations for `screenType`, `decision`, `minConfidence`, `mayShowOverlay`, and `maySaveScan`.
* No fixture labels were fabricated or changed.
* Added `docs/research/scan_fixture_labeling_plan.md` with current counts and the first 10 fixtures to label.

## Phase G Status
* Implemented only as weak support from existing visual summary in `ScanConfidenceGate`.
* Visual evidence can slightly support a valid detail/appraisal scan or downgrade a species conflict.
* Visual evidence cannot accept a scan by itself.
* No new visual matcher, templates, assets, thresholds database, native engine, or third-party dataset was added.

## Fixture Audit Result
* `cases=47`
* `fixtures=47`
* `strict=16`
* `all_null_exploratory=28`
* `expected_species=19`
* `expected_form=0`
* `expected_cp=19`
* `expected_hp=17`
* `expected_appraisal_fields=0`
* `expected_screen_type=0`
* `expected_confidence_decision=0`
* `expected_min_confidence=0`
* `expected_may_show_overlay=0`
* `expected_may_save_scan=0`
* `missing_expected_screen_type=47`
* `missing_expected_confidence_decision=47`
* `priority_1_missing_screen_species_decision=47`
* `priority_2_missing_core_fields_gate_flags=47`
* `priority_3_missing_form_appraisal=47`
* `missing_fixture_files=0`

## Verification
* `git status --short`: showed the expected dirty A-D worktree plus new E/F/G files; unrelated dirty files were preserved.
* `.\scripts\audit_scan_fixtures.ps1`: **Passed** with counts above.
* `.\gradlew.bat :app:testDebugUnitTest --tests "*ScanConfidenceGateTest*" --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:testDebugUnitTest --tests "*OcrDiagnosticsExporterTest*" --no-daemon --console=plain`: **Passed** after rerunning serially. The first parallel focused run hit a Windows file lock in `app/build/test-results`.
* `.\gradlew.bat :app:testDebugUnitTest --tests "*ScanTelemetryRepositoryTest*" --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:testDebugUnitTest --tests "*SpeciesFormResolverTest*" --no-daemon --console=plain`: **Passed** after rerunning serially. The first parallel focused run hit the same Windows file lock.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:lintDebug --no-daemon --console=plain`: **Passed**.
* `.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain`: **Passed**.
* `git diff --check`: **Passed** with line-ending warnings only.
* `adb devices`: no connected device or emulator; `connectedDebugAndroidTest` was skipped.

## Privacy Confirmation
* No screenshot telemetry was added.
* No diagnostic telemetry was added.
* No raw OCR telemetry was added.
* No resolver trace telemetry was added.
* No confidence gate trace telemetry was added.
* No external network calls were added.
* No Calcy code/assets/databases/traineddata/thresholds/constants were used.
* No new third-party datasets were added.
* Diagnostics remain local-only through the existing export path.
* Broad storage writes were not added.
* Existing screenshot path sanitization remains preserved.
* `ScanManager` still sends live telemetry with `screenshotPath = null`.
* Legacy fixed-crop fallback remains preserved.

## Intentionally Not Implemented
* No ML Kit replacement.
* No Tesseract/OpenCV/native OCR dependency addition.
* No new advanced visual matcher or visual asset/template database.
* No rarity scoring rewrite.
* No IV solver rewrite.
* No UI rewrite.
* No remote telemetry behavior change.
* No screenshot, diagnostic, raw OCR, resolver trace, or gate trace upload.
* No fixture relabeling.
* No commit, merge, or PR creation.

## Remaining Manual Tasks
* Label the first 10 fixtures listed in `docs/research/scan_fixture_labeling_plan.md`.
* Run connected Android fixture regression on Samsung S25 and Pixel 4a.
* Verify storage/non-detail screens reject without saving, transition screens retry, and accepted scans still show/save normally.
* Review the first local diagnostic exports for accepted, low-confidence, retry, and reject decisions.

## Next Human Action
Review the full dirty worktree as one recognition reliability branch, then manually label the prioritized fixtures before expanding any Phase G visual matching.

---

# AI Run Report: PR-03 SpeciesRefiner Authority Contract

## Metadata
* **Phase:** PR-03
* **Verified base SHA:** `362b01eb3653fb63cf70cb9e040dd4df29203660`
* **Branch:** `fix/species-refiner-authority-contract`
* **Scope:** Harden `SpeciesRefiner` name and Candy authority without changing PR-02 name decisions, resolver schema, telemetry transport, fixtures, or recognition baselines.

## Read-Only Findings
* **Scan explorer:** `SpeciesRefiner` treated any non-null `parseName()` result as direct/exact authority, let nonblank `PokemonData.candyName` enter candidate pools, bonuses, and override paths without selected-parser provenance, and allowed resolver/ranking evidence to participate in final authority decisions.
* **Test auditor:** JVM coverage did not pin the refiner authority matrix. The Android cross-family Candy test supplied only a nonblank Candy value yet expected replacement. Robolectric also required test-only injection of the checked-in canonical names, family registry, and base-stat data.
* **Privacy reviewer:** The existing resolver trace schema was sufficient, but final traces still described earlier resolver proposals and `SpeciesRefiner` logs included raw name/Candy values and ranked summaries. Remote telemetry behavior and schema did not require changes.

## Exact Changed Files
* `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt`
* `app/src/test/java/com/pokerarity/scanner/SpeciesRefinerAuthorityTest.kt` (new)
* `app/src/androidTest/java/com/pokerarity/scanner/SpeciesRefinerTest.kt`
* `docs/AI_RUN_REPORT.md`

## Authority Contract Implemented
* Every nonblank raw `Name`, `NameDynamic`, and `NameHC` observation is evaluated through `TextParser.decideSpeciesName(...)` with deterministic exact, reviewed, safe-fuzzy, uncertain, no-match, and conflict handling.
* Exact-canonical and reviewed-alias evidence are hard authority only when the accepted species matches the current species. Safe fuzzy remains soft evidence. Uncertain, no-match, and conflicting accepted names never create a hard lock.
* Candidate ranking, prefix evidence, and `SpeciesFormResolver` output remain candidate evidence only and cannot independently authorize replacement. Untrusted Candy is removed from the resolver input before candidate generation.
* Candy is reliable only when one non-conflicting winning `Candy` diagnostic comes from `candy` or `candy_wide`, has nonblank raw text containing the normalized Candy observation, has `found` status, records `winner:candy_parser`, and has consistent parsed/selected/parser values matching `PokemonData.candyName`.
* Missing, losing, not-found, mismatched, synthetic, or conflicting Candy diagnostics fail closed. Every Candy pool, family bonus, evolution path, unique-Candy path, cross-family path, and Candy-dependent lock relaxation consumes only reliable Candy.
* Candy replacement requires an explicit repository family relationship, an observed compatible profile, the existing conservative absolute fit threshold, the existing `fitGapSmall` fit margin, and the existing `totalGapSmall` total-score margin over the current candidate. No scoring threshold was lowered or retuned.
* Replacement triggers follow the same priority as replacement-candidate selection: Candy-family/evolution authority, accepted-name authority, unique-Candy authority, move corroboration, then same-family fit. Each produces its own stable final reason and authority-derived confidence, and a replaced species never inherits confidence from the previous name.
* Final `SpeciesResolverTrace` values use the existing schema, the actual final species, bounded authority-derived confidence, fixed reason/evidence codes, deterministic conflict ordering, and a winner candidate score equal to final trace confidence.

## Tests-First Red Phase
* Initial focused result: **10 tests completed, 10 expected failures**.
* Directly observed wrong outcomes included `Squirtle -> Mankey`, `Poliwrath -> Poliwag`, `Mankey -> Squirtle`, and ambiguous `Nidoran-f -> Nidoran-m` / `Mankey -> Nidoran-f` selection.
* Retained-species failures exposed stale or inflated provenance such as `exact_name_match:Name` and `strong_name_match:Name` instead of final refiner authority codes.
* A final-review overlap regression then reproduced a trace mismatch: the accepted-name species won selection while the trace incorrectly reported a reliable-Candy replacement and Candy-bounded confidence.
* Because JUnit stops a test method at its first failed assertion, some paired/data-driven RED cases did not independently reach a species assertion. The table below labels those cases rather than manufacturing a before result.

## Before/After Authority Scenarios

| Case | Observed RED before | PR-03 after | Final stable reason |
|---|---|---|---|
| Exact canonical + unrelated untrusted Candy | `Squirtle -> Mankey` observed | Keep `Squirtle`; exact hard authority | `kept_exact_canonical` |
| Reviewed alias + unrelated untrusted Candy | Not independently reached in the paired RED method; Candy and resolver provenance were not gated | Keep `Ho-Oh`; reviewed hard authority | `kept_reviewed_alias` |
| Safe fuzzy + reliable correct Candy | `Poliwrath -> Poliwag` observed | Keep `Poliwrath`; safe fuzzy remains soft and corroborated | `kept_safe_fuzzy_with_corroboration` |
| Uncertain name + reliable correct Candy without sufficient profile | `Nidoran-f -> Nidoran-m` observed | Keep `Nidoran-f`; no hard authority or Candy replacement | `kept_insufficient_profile` |
| Uncertain name + wrong Candy | Not independently reached after the paired correct-Candy RED failure; the Nidoran path was demonstrably non-deterministic | Keep `Nidoran-f`; wrong Candy cannot authorize replacement | `kept_insufficient_profile` |
| Unique-Candy false-positive resistance | `Mankey -> Nidoran-f` observed with singleton-family evidence present | Keep `Mankey` without sufficient profile | `kept_insufficient_profile` |
| Candy-family false-positive resistance | Not independently reached after the unique-Candy RED failure; family evidence lacked final authority gates | Keep `Mankey` without sufficient profile | `kept_insufficient_profile` |
| Profile mismatch + reliable compatible Candy | Not independently reached after the no-provenance branch failed first | Replace `Mankey` with `Squirtle` using family, absolute fit, fit-margin, and total-margin gates | `replaced_reliable_candy_profile` |
| Profile mismatch without reliable Candy | `Mankey -> Squirtle` observed | Keep `Mankey` for later consistency handling | `kept_profile_mismatch` |
| Exact accepted name + different reliable singleton Candy | Accepted-name species selected, but trace reported Candy authority and confidence | Replace with the accepted-name species using exact-name reason and confidence | `replaced_accepted_name` |
| Blank Candy | Species retained, but final trace used stale resolver reason `exact_name_match:Name` | Keep exact species with final authority provenance | `kept_exact_canonical` |
| Nonblank but unreliable Candy | `Squirtle -> Mankey` observed; missing provenance was not equivalent to blank | Same authority as missing Candy; keep `Squirtle` | `kept_exact_canonical` plus `candy_untrusted` |
| Same-family drift | `Slowpoke` retained, but trace used stale `strong_name_match:Name` provenance | Keep `Slowpoke` under reviewed-name authority | `kept_reviewed_alias` |
| Conflicting accepted names | Not independently reached after the earlier Nidoran RED assertion failed | Keep current species without a hard name lock; retain sorted conflict codes | `kept_conflicting_accepted_names` |

Resolver-only proposals remain candidate evidence, are tagged with `resolver_proposal_only`, and cannot replace the current species without independent final authority. The final reason describes the actual keep trigger. Untrusted Candy cannot create a resolver proposal.

## Scenario Counts
These are focused test-scenario counts, not corpus-level accuracy claims.

* Hard exact-name authority decisions: **12**
* Hard reviewed locks: **2**
* Safe-fuzzy soft decisions: **1**
* Uncertain/no-match decisions: **10**
* Accepted-name/Candy conflicts: **2**
* Trusted-Candy replacements: **1**
* Accepted-name replacements: **1**
* Move-corroborated replacements: **1**
* Rejected untrusted-Candy override scenarios: **10**
* Unexpected after replacements: **0**
* Accepted-wrong result: **0**

## Verification
* RED `SpeciesRefinerAuthorityTest`: **10/10 expected failures**, with the observed outcomes recorded above.
* GREEN `SpeciesRefinerAuthorityTest`: **12/12 passed**, including accepted-name/Candy trigger overlap, move-trigger reason/confidence, and resolver Candy-leakage coverage.
* `SpeciesNameDecisionTest`: **Passed**.
* `SpeciesFormResolverTest`: **Passed**.
* `RecognitionMatcherCharacterizationTest`, run twice: **Passed twice** with byte-identical actual hash `347E0607547B04611AC2EDE5930DDD63BD6B46CEFD4911E2C1052C90AF1052A6`.
* Characterization invariants on both runs: exact canonical **1011 correct / 0 wrong / 0 uncertain**; accepted-wrong **0**; dynamic/static selected disagreement **0**; Nidoran ambiguity, reviewed aliases, and numeric suffix decisions preserved.
* `:app:assembleDebugAndroidTest`: **Passed**.
* Full `:app:testDebugUnitTest`: **Passed**.
* `:app:detekt`: **Passed**.
* `:app:lintDebug`: **Passed**.
* `:app:assembleDebug`: **Passed**.
* `git diff --check`: **Passed** with LF/CRLF conversion warnings only.
* No release build, signing task, emulator, physical-device test, or connected instrumentation run was performed.
* No recognition baseline was regenerated or modified.

## Privacy and Safety Review
* New final trace and log values use fixed codes/status only; they contain no raw OCR payload, absolute path, device identifier, timestamp, username, credential, or secret.
* Raw-value `SpeciesRefiner` logging and ranked candidate summaries were removed.
* Remote telemetry schema, consent, transport, and screenshot-path sanitization are unchanged.
* The implementation remains passive and adds no network call, gameplay automation, input injection, root behavior, or security bypass.

## Known Limitations and Stop Boundary
* `ScanConsistencyGate` still contains legacy Candy/name authority logic. It is explicitly forbidden in PR-03 and owned by PR-04.
* Manual Gate A remains open.
* JVM/Robolectric authority tests do not prove real-device OCR accuracy.
* PR-04 consistency, confidence, and early-exit hardening was not started.

## Next Task
Documentation closeout before PR-04. Keep PR-03 draft/unmerged until independent review gates complete.

---

# AI Run Report: PR-04 Consistency/Confidence/Early-Exit Cleanup and LargeClass Split

## Metadata
* **Phase:** PR-04
* **Verified base SHA:** `d7631f39f10386d9c2e317f6e7ad42a9a4cf5c18`
* **Branch:** `fix/species-confidence-and-early-exit`
* **Scope:** Cleanup hunks (variable extraction, single-return patterns, unused property/function removal, helper extraction) across production code; LargeClass split of `ScanFrameFusionTest` into two files; detekt compliance without `@Suppress`; full regression validation.

## RED Evidence Classification

### PR-03 Codex RED Evidence
* **Scope:** `SpeciesRefinerAuthorityTest`
* **Outcome:** 10 tests completed, 10 expected failures observed before authority implementation.

### PR-04 Codex RED Evidence
* **Scope:** Focused PR-04 test suite execution (48 focused tests executed).
* **Outcome:** Exactly 7 new PR-04 tests failed as expected. There was no compilation failure in this initial RED stage.

### Structured-Seam RED Evidence
* **Scope:** Structured overload / signature test verification.
* **Outcome:** The new structured overload and signature tests initially failed to compile because the structured production overload/signature did not yet exist. (This was a seam compilation check and not the initial test-logic RED stage).

## Phases Completed

### Phase 1 — Baseline Verification
* HEAD: `d7631f39f10386d9c2e317f6e7ad42a9a4cf5c18` matches `origin/main`
* Pre-cleanup backup SHA-256: `D237B4A5FC9655ACF95BB8DE597EDE22B2C8880E78E4EF8EFA355DCCD581FFB7` verified
* Current backup SHA-256: `C011E3568E9DEB363BA5136A9FBD0D393F3136365BA9E8C2DC7724C39F8862BA` written
* Modified files match expected set

### Phase 2-3 — Semantic Equivalence Review
All detekt-cleanup refactor deltas were independently reviewed as behavior-equivalent to the pre-cleanup PR-04 implementation. No SEMANTIC_CHANGE or INDETERMINATE findings among the cleanup hunks.

| File | Pattern | Verdict |
|---|---|---|
| `ScanFrameFusion.kt` | Variable extraction, single-return | PROVEN_EQUIVALENT |
| `ScanManager.kt` | Removed unused property/function, extracted helpers | PROVEN_EQUIVALENT |
| `ScanConfidenceGate.kt` | Extracted helpers, CANDIDATE_CLOSE_MARGIN constant | PROVEN_EQUIVALENT |
| `ScanConsistencyGate.kt` | Extracted `isHardAuthority`, `when` pattern, helpers | PROVEN_EQUIVALENT (detekt-cleanup refactor delta only; the complete ScanConsistencyGate diff against origin/main remains an intentional PR-04 semantic change implementing fail-closed authority, profile and cross-family consistency behavior) |
| `ScanFrameFusionTest.kt` | EvidenceTuning & PokemonConfig data class wrappers | PROVEN_EQUIVALENT |

### Phase 4 — LargeClass Split (ScanFrameFusionTest)
* Original `ScanFrameFusionTest.kt`: 18 remaining tests
* New `ScanFrameFusionDetailedPassTest.kt`: 16 moved tests
* **Total: 34 tests** (preserved, no loss)

### Phase 5 — detekt Cleanup & Fixture Repair
* Resolved `LongParameterList` and `MaxLineLength` detekt findings structurally without `@Suppress`, detekt configuration changes, or baseline modifications.
* Introduced a narrowly scoped test-only `PokemonConfig` data class in `ScanFrameFusionTest.kt` for fixture parameters while preserving all 34 fusion tests exactly once.
* Removed duplicate helpers from `ScanFrameFusionDetailedPassTest.kt`.
* Detekt passed cleanly with zero weighted issues and zero suppressions.

### Phase 6 — Full Regression

| Check | Result |
|---|---|
| `compileDebugUnitTestKotlin` | **Passed** |
| `detekt` | **Passed** (0 weighted issues, 0 suppressions) |
| `testDebugUnitTest` | **Passed** (583 tests) |
| `assembleDebug` | **Passed** |
| Determinism (two runs) | **Passed twice** (SHA-256: `347E0607547B04611AC2EDE5930DDD63BD6B46CEFD4911E2C1052C90AF1052A6`, 42773 bytes, byte-identical) |
| `lintDebug` | **Passed** (exit code 0, BUILD SUCCESSFUL in 3m 16s) |

### Lint Validation
* **Command:** `.\gradlew.bat :app:lintDebug --no-daemon --console=plain`
* **Start:** 2026-07-20T16:17:34+01:00
* **Completion:** 2026-07-20T16:20:50+01:00
* **Exit code:** 0
* **Warnings/Errors:** No blocking lint errors.
* **Report paths:** `app/build/reports/lint-results-debug.html`, `app/build/reports/lint-results-debug.xml`

## Files Changed (PR-04 scope)
* `app/src/main/java/com/pokerarity/scanner/service/ScanFrameFusion.kt`
* `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanConfidenceGate.kt`
* `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanConsistencyGate.kt`
* `app/src/test/java/com/pokerarity/scanner/ScanFrameFusionTest.kt`
* `app/src/test/java/com/pokerarity/scanner/ScanFrameFusionDetailedPassTest.kt` (new)
* `app/src/test/java/com/pokerarity/scanner/ScanManagerDetailedPassTest.kt`
* `app/src/test/java/com/pokerarity/scanner/ScanConsistencyGateEdgeCaseTest.kt`
* `app/src/test/java/com/pokerarity/scanner/util/ocr/ScanConfidenceGateTest.kt`
* `app/src/androidTest/java/com/pokerarity/scanner/ScanConsistencyGateTest.kt`
* `app/src/androidTest/java/com/pokerarity/scanner/ScanManagerPolicyTest.kt`
* `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt`
* `docs/AI_RUN_REPORT.md`

## Safety and Privacy
* All safety boundaries respected: no gameplay automation, input injection, root behavior, network calls, or security bypass.
* No telemetry schema, consent, transport, or screenshot-path changes.
* The detekt-cleanup refactor delta in each production file was independently reviewed as behavior-equivalent to the pre-cleanup PR-04 implementation. The complete ScanConsistencyGate diff against origin/main remains an intentional PR-04 semantic change; it is not classified as a behavior-neutral file-level change.
* The LargeClass split is purely mechanical test relocation; no test logic was altered.

## Next Task
Draft PR #33 submitted. Keep draft/unmerged until independent review gates complete.

---

# AI Run Report: PR-06 Slice 1 - OCR Image Policy Measurement Harness

## Metadata

* **Live base SHA:** `d83e7abcf48140bfc0b4f13c6f8014cb1937acaf`
* **Branch:** `experiment/anchored-native-ocr-crops`
* **Objective:** Pure policy planning and deterministic synthetic geometry evidence only.

## Scope

* `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrImagePolicy.kt` (new)
* `app/src/test/java/com/pokerarity/scanner/util/ocr/OcrImagePolicyTest.kt` (new)
* `app/src/test/java/com/pokerarity/scanner/util/ocr/OcrGeometryPolicyReportTest.kt` (new)
* `app/src/test/resources/ocr_geometry_policy_expected.json` (new)
* `docs/AI_RUN_REPORT.md`

No runtime caller changed. The production default remains the current 900-width baseline; CP and HP remain baseline-locked. Bounded crop upscaling has no implicit configuration.

## Measurement-Harness Boundary

The verdict is **measurement-harness-only**. This adds deterministic planning and synthetic geometry evidence, not an OCR experiment or accuracy evidence. It does not authorize a production-default change.

* Expected report: `app/src/test/resources/ocr_geometry_policy_expected.json`
* Ignored generated report: `app/build/reports/recognition/ocr_geometry_policy_actual.json`
* Expected and repeated actual SHA-256: `2F60ADE05E43C57DD2DECB179BD6E0DF5109DC778D3FF2B67BFE0E5C7BE37BC1`

## Validation

* Focused policy/report tests, run twice: **12 tests, 0 failures, 0 errors, 0 skipped**.
* Full JVM XML totals: **644 tests, 0 failures, 0 errors, 0 skipped**.
* `:app:detekt`: **Passed**.
* `:app:lintDebug`: **Passed**.
* `:app:assembleDebug`: **Passed**.
* `:app:assembleDebugAndroidTest`: **Passed**.
* `git diff --check`: **Passed**.
* Runtime blobs for `ScanManager`, `OCRProcessor`, `ImagePreprocessor`, `ScreenGeometryBuilder`, and `ScreenRegions` remain at their verified main values.

## Remaining Evidence Gaps and Next Task

* No validated 1440-wide fixture set.
* No reference/shifted/scrolled truth set.
* No controlled OCR comparison or default-policy decision.
* Manual Gate A remains open.

Next: after this draft PR is reviewed, prepare the smallest controlled-fixture acquisition and truth-metadata slice; do not wire a policy into runtime until those gates are complete.
