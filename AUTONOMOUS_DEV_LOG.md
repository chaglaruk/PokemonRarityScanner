# Purpose: Track autonomous development tasks, risk, and verification.

# Autonomous Development Log

## Avoid local screenshot path exposure in ScanManager logs

- Why it matters: failed OCR/decode logs currently include local screenshot paths,
  which can expose user folder names or diagnostic file locations.
- Files touched: `ScanManager.kt`, safe debug log helper, helper unit test.
- Risk level: low. The change affects log text only and does not alter scan
  pipeline decisions.
- Verification: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
  and `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`.

## Avoid database artifact absolute path exposure in cleanup logs

- Why it matters: failed database cleanup logs should not expose user directory
  paths when deleting SQLCipher sidecar files.
- Files touched: `DatabasePassphraseStore.kt` and a focused unit test.
- Risk level: low. The change affects warning log text only.
- Verification: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
  and `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`.

## Add OCR confidence reasons model (Antigravity session 2026-05-31)

- Why it matters: replaces brittle pipe-delimited rawOcrText markers with typed,
  privacy-safe structured metadata. This is the first step of the staged
  OCR confidence migration from docs/OCR_CONFIDENCE_REASONS_PLAN.md.
- Files touched: `OcrConfidenceReasons.kt`, `OcrConfidenceReasonsBuilder.kt`,
  `OcrConfidenceReasonsTest.kt`.
- Risk level: low. New data classes and builder only, no pipeline integration yet.
- Verification: 19 unit tests pass. Debug build succeeds.

## Add caught date ambiguity regression tests (Antigravity session 2026-05-31)

- Why it matters: date parsing has many edge cases including MM/DD vs DD/MM
  ambiguity, OCR noise substitution, compact formats, and boundary years.
  These tests document current behavior and catch future regressions.
- Files touched: `CaughtDateAmbiguityTest.kt`.
- Risk level: none. Test-only addition.
- Verification: 25 test cases pass covering unambiguous, ambiguous, impossible,
  OCR noise, compact, boundary, and garbage input scenarios.

## Add OCR diagnostics privacy coverage (Antigravity session 2026-05-31)

- Why it matters: OcrDiagnosticsExporter produces local-only diagnostic JSON
  that includes screenshot paths, raw OCR text, and diagnostic file markers.
  These must never leak to telemetry payloads.
- Files touched: `OcrDiagnosticsPrivacyTest.kt`.
- Risk level: none. Test-only addition.
- Verification: 14 test cases covering summary JSON structure, null handling,
  HP status codes, path markers, solve details, and forbidden tokens.

## OcrConfidenceReasons integration hardening (Task B)

- Why it matters: Prevents data loss during copy operations and verifies telemetry doesn't accidentally leak confidence internals.
- Files touched: OcrConfidenceReasonsIntegrationTest.kt
- Risk level: None (test only)
- Verification: 	estDebugUnitTest and ssembleDebug succeeded.

## Plan Scan History Repository Regression Coverage (Task D)

- Why it matters: History DB relies on SQLCipher, which breaks on standard JVM environments. The plan maps how to decouple encryption to allow safe in-memory Robolectric coverage.
- Files touched: HISTORY_REPOSITORY_TEST_PLAN.md
- Risk level: None (docs only)
- Verification: Docs written and reviewed.

## TextParser UI/noise regression coverage (Task A)

- Why it matters: Pokemon GO UI labels like "SHORTEST", "POWER UP", "XS" and noisy HP strings ("120 | / 120 HP") frequently corrupt name and stat parsers. Documenting these ensures safe future adjustments.
- Files touched: TextParserNameRecoveryTest.kt, TextParseUtilsRegressionTest.kt
- Risk level: None (test only)
- Verification: 	estDebugUnitTest and ssembleDebug passed.

## VariantDecisionTrace fallback/compatibility coverage (Task C)

- Why it matters: Prevents regression where new structured VariantDecisionTrace logic inadvertently pollutes awOcrText (which is still heavily relied on by legacy parsing engines). Also verifies exporter correctly avoids re-appending structured keys into the raw output.
- Files touched: OcrDiagnosticsExporterTest.kt
- Risk level: None (test only)
- Verification: 	estDebugUnitTest and ssembleDebug passed.

## Gradle warning audit (Task E)

- Why it matters: Prevents developers from chasing harmless SDK version or deprecation warnings. Isolates Gradle 9.0 and KSP upgrades as independent future tasks rather than blocking current feature work.
- Files touched: docs/GRADLE_WARNING_AUDIT.md
- Risk level: None (docs only)
- Verification: Manual review. No code changes.

## OCR confidence telemetry privacy coverage (Task B)

- Why it matters: Proves that injecting a malicious OcrConfidenceReasons block containing local paths (C:/Users, /tmp) or raw secrets into PokemonData does not bleed into the final ScanTelemetryPayload JSON. Guarantees that future expansions of OCR reason tracking will be subject to the strict telemetry privacy gateway test.
- Files touched: ScanTelemetryRepositoryTest.kt
- Risk level: None (test only)
- Verification: 	estDebugUnitTest and ssembleDebug passed.

## Refine history repository test plan (Task D)

- Why it matters: Prevents developers from attempting doomed SQLCipher unit tests on plain JVMs by clearly explaining the native .so library limitation. Establishes that pure mapping logic can be unit-tested safely, while SQLite/Room queries require Robolectric or instrumentation tests.
- Files touched: docs/HISTORY_REPOSITORY_TEST_PLAN.md
- Risk level: None (docs only)
- Verification: Manual review. No code changes.

## Add scan history mapper regression coverage (Task A)

- Why it matters: Extracting the pure ScanHistoryMapper allows us to securely verify PokemonData mapping to Room entities without invoking SQLCipher native binaries or Robolectric, eliminating architecture-dependent test crashes on Windows JVMs. Proves that structured logic paths do not bleed local variables.
- Files touched:
  - pp/src/main/java/com/pokerarity/scanner/data/repository/PokemonRepository.kt
  - pp/src/main/java/com/pokerarity/scanner/data/repository/ScanHistoryMapper.kt
  - pp/src/test/java/com/pokerarity/scanner/data/repository/ScanHistoryMapperTest.kt
- Risk level: Low (pure logic extraction, no DB schema changes)
- Verification: 	estDebugUnitTest and ssembleDebug passed successfully.
