# Purpose: Preserve project context for future Codex work in PokeRarityScanner.

# PokeRarityScanner Project State

## Project Purpose

PokeRarityScanner is a passive Android/Kotlin scanner for Pokemon GO collection
screens. It captures user-approved screen frames, extracts OCR and visual
signals, resolves Pokemon species and variants, calculates rarity, and displays
an explanatory overlay.

The app must remain passive. It must not automate gameplay, inject input, read
game memory, bypass security, require root, or weaken Android consent and
permission flows.

## Architecture Overview

- Android app with Kotlin, Gradle Kotlin DSL, Hilt, Room, Compose, ViewBinding,
  ML Kit OCR, OpenCV, SQLCipher, and coroutine-based pipeline work.
- Main runtime flow is centered on foreground services and user-consented
  MediaProjection capture.
- Domain logic is split across service orchestration, OCR parsing, visual
  classifiers, repository-backed metadata loaders, rarity scoring, telemetry,
  and UI/overlay presentation.
- Assets under `app/src/main/assets/data/` provide local metadata, rarity rules,
  Pokemon names, family data, variant catalogs, event history, and vision models.
- Tests are split between JVM unit tests under `app/src/test/` and Android
  instrumentation/regression tests under `app/src/androidTest/`.

## Main App Module

- Main module: `app`
- Application ID: `com.pokerarity.scanner`
- Main Kotlin root: `app/src/main/java/com/pokerarity/scanner/`
- Android manifest: `app/src/main/AndroidManifest.xml`
- Asset metadata: `app/src/main/assets/data/`
- Debug validation commands are preferred. Do not run release builds unless the
  user explicitly asks.

## Important Packages And Classes

Service and lifecycle:
- `app/src/main/java/com/pokerarity/scanner/service/`
- `ScreenCaptureService`
- `ScreenCaptureManager`
- `OverlayService`
- `OverlayManager`
- `OverlayStateStore`
- `ScanManager`
- `ScanStartupPolicy`

OCR and parsing:
- `app/src/main/java/com/pokerarity/scanner/util/ocr/`
- `OCRProcessor`
- `MLKitOcrProvider`
- `ImagePreprocessor`
- `ScreenRegions`
- `TextParser`
- `SpeciesRefiner`
- `SpeciesRefinerConfig`
- `ScanConsistencyGate`
- `ScanAuthorityLogic`

Vision and variant resolution:
- `app/src/main/java/com/pokerarity/scanner/util/vision/`
- `VisualFeatureDetector`
- `VariantDecisionEngine`
- `VariantPrototypeClassifier`
- `VariantPrototypeStore`
- `FullVariantMatcher`
- `FullVariantCandidateBuilder`
- `Phase2VariantClassifier`
- `Phase2VariantFeatureMerger`
- `ShinySignatureStore`
- `CostumeSignatureStore`

Data, scoring, and metadata:
- `app/src/main/java/com/pokerarity/scanner/data/repository/`
- `RarityCalculator`
- `RarityRuleLoader`
- `RarityManifestLoader`
- `VariantCatalogLoader`
- `VariantCatalogSelection`
- `VariantExplanationMetadata`
- `VariantExplanationSanity`
- `AuthoritativeVariantDbLoader`
- `AuthoritativeHistoricalEventResolver`
- `RemoteMetadataSyncManager`
- `PokemonRepository`
- `MasterPokedexLoader`
- `EventContextManager`

Remote and telemetry:
- `app/src/main/java/com/pokerarity/scanner/data/remote/`
- `ScanTelemetryCoordinator`
- `ScanTelemetryUploader`
- `ScanTelemetryConfig`
- `TelemetryRequestSigner`
- `ScanTelemetryPayload`

UI:
- `MainActivity`
- `ResultActivity`
- `HistoryActivity`
- `ProjectionPermissionActivity`
- `ScanResultOverlayCard`
- `TelemetryConsentDialog`
- `TelemetrySettingsDialog`

## Scan Pipeline

1. User starts scanning through the app/overlay permission flow.
2. `ScreenCaptureService` runs as a foreground service, uses MediaProjection,
   captures a short screenshot sequence, and broadcasts screenshot paths.
3. `ScanManager` listens for `ACTION_SCREENSHOT_READY` and serializes scan work
   with a mutex.
4. Frames are decoded, optionally scaled, and scored for CP/OCR quality.
5. `OCRProcessor` extracts candidate CP, HP, name, date, candy, size, and other
   text fields.
6. `ScanManager` fuses multiple frame results and may run a detailed OCR pass.
7. `SpeciesRefiner` and `ScanConsistencyGate` refine and guard the species
   decision.
8. `VariantDecisionEngine`, `VisualFeatureDetector`, and `Phase2VariantClassifier`
   resolve shiny, costume, form, lucky, shadow, location card, and related
   variant signals.
9. `RarityCalculator` produces score, tier, explanations, breakdown, and
   decision support.
10. `OverlayService` displays the result overlay and can submit feedback.

## OCR And Vision Pipeline

OCR:
- `OCRProcessor` uses `MLKitOcrProvider` and `ScreenRegions` crops.
- `ImagePreprocessor` prepares white-mask, HP, candy, date, and other targeted
  crops.
- `TextParser` parses CP, HP, species names, candy family hints, date text,
  weight, height, gender, size tags, lucky labels, and power-up cost fields.
- `SpeciesRefiner` ranks species candidates using OCR confidence, family/candy
  hints, move hints, CP/HP/arc compatibility, and physical profile scoring.
- `ScanConsistencyGate` blocks or downgrades inconsistent scan states.

Vision:
- `VisualFeatureDetector` extracts color, shiny, shadow, lucky, costume, and
  visual confidence signals from screenshots.
- `VariantDecisionEngine` combines global and species-scoped prototype matches
  with OCR authority logic and full variant matching.
- `Phase2VariantClassifier` reads asset-backed model data and applies
  feature-vector classification for supported species/targets.
- `Phase2VariantFeatureMerger` merges phase-2 predictions into the final visual
  feature set used by scoring.

## Rarity Scoring Pipeline

- `RarityCalculator.calculate` is the main scoring entry point.
- Base species rarity comes from manifest/rules loaders and repository metadata.
- Variant scoring considers shiny, costume, form, shadow, purified, lucky,
  location card, event boost, and full variant match information.
- Age scoring uses caught date tiers from `rarity_rules.json`.
- Collector scoring uses event context, size, rare gender, IV signals, and other
  rule-backed bonuses.
- Event/costume explanation metadata is guarded by authoritative variant data,
  historical event windows, caught-date compatibility, and sanity filtering.
- Final result includes total score, tier, axis breakdown, explanations, value
  reasons, and decision support cards.

## Telemetry Pipeline

- Telemetry is coordinated by `ScanTelemetryCoordinator`.
- `ScanTelemetryRepository` persists pending uploads and upload state.
- `ScanTelemetryUploader` sends multipart scan telemetry and feedback to the
  configured endpoint when telemetry is enabled.
- `ScanTelemetryPayload` includes app info, coarse device model/manufacturer,
  prediction details, debug fields, phase-2 info, and screenshot metadata.
- Treat telemetry and screenshots as privacy-sensitive. Do not commit sample
  payloads, screenshots, raw telemetry, absolute local paths, API keys, or device
  identifiers unless the user explicitly approves a sanitized fixture.
- Do not touch `local.properties`, signing keys, telemetry keys, `.env` files,
  keystores, or other secrets.

## Living DB And Assets Pipeline

- Static metadata lives in `app/src/main/assets/data/`.
- Important assets include:
  - `authoritative_variant_db.json`
  - `variant_catalog.json`
  - `variant_registry.json`
  - `variant_classifier_model.json`
  - `variant_phase2_model.json`
  - `rarity_rules.json`
  - `rarity_manifest.json`
  - `master_pokedex.json`
  - `pokemon_names.json`
  - `pokemon_families.json`
  - `pokemon_moves.json`
  - `pokemon_base_stats.json`
  - `event_history.json`
  - `bulbapedia_event_pokemon_go.json`
  - `costume_signatures.json`
  - `shiny_signatures.json`
- `RemoteMetadataSyncManager` can fetch trusted metadata manifest/files, verify
  SHA-256 when provided, and write them atomically into app-private
  `remote_metadata` storage.
- Data generation and refresh scripts live under `scripts/`. Review script
  inputs/outputs before running them because some scripts can produce large
  metadata changes.

## Build And Test Commands

Preferred debug checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Do not run release builds unless explicitly requested. Release builds require
signing credentials and must not be attempted during normal Codex validation.

## Current Known Local Git State

At the time this file was created, before committing this file:

- Branch: `main`
- Relationship to `origin/main`: ahead 4, behind 0
- Existing uncommitted local files that must not be staged or committed unless
  explicitly requested:
  - `.vscode/settings.json`
  - `scan_payload.json`
  - `scan_payload_cmd.json`
  - `scan_payload_cmd2.json`
  - `scan_payload_final.json`
- Recent integration status: local Kotlin changes were rebased onto
  `origin/main`; unit tests and debug assemble passed.
- Codex agent setup files exist and are committed.

After committing this file, expect `main` to be one additional commit ahead of
`origin/main` unless a later fetch changes the remote state.

## Safe Future Workflow For Codex Tasks

1. Start with `git status -sb` and identify local-only files.
2. Never stage `.vscode/settings.json` or `scan_payload*.json` unless the user
   explicitly requests it.
3. Read `AGENTS.md` and this file before making project changes.
4. Inspect target classes and call sites before editing.
5. Keep changes focused and avoid unrelated refactors.
6. Do not touch `local.properties`, signing keys, telemetry keys, `.env` files,
   keystores, or release signing configuration.
7. Avoid release builds. Use debug unit tests and debug assemble for validation.
8. Treat telemetry payloads, OCR text, screenshots, and diagnostic exports as
   privacy-sensitive.
9. If asked to integrate remote changes, create a safety branch first and stop
   on conflicts before resolving.
10. Before commit, run `git diff --cached --name-only` and confirm only intended
    files are staged.

## Recommended Next 10 Development Tasks

1. Add focused tests around event metadata exposure when caught date is missing
   or outside known event windows.
2. Add OCR regression cases for size/record UI words so they cannot become
   species candidates.
3. Review `ScanManager` pipeline length and extract smaller testable units for
   frame fusion and detailed-pass decisions.
4. Add privacy review tests for telemetry payload construction and screenshot
   metadata handling.
5. Expand `ScanConsistencyGate` tests for conflicting CP/HP/species scenarios.
6. Add asset schema validation for `rarity_rules.json`, `variant_catalog.json`,
   and `authoritative_variant_db.json`.
7. Improve living DB refresh diagnostics around manifest version, hash mismatch,
   and atomic write failures.
8. Add targeted tests for phase-2 classifier threshold behavior per target.
9. Document safe fixture creation so screenshots and raw payloads are sanitized
   before commit.
10. Review foreground service and overlay lifecycle against current Android API
    policy, especially MediaProjection consent and cleanup paths.
