# PokemonRarityScanner Next Development Backlog

## Current Status
**HEAD**: `a88db38a798aa67904d912be07a232e9145c9dbe`

### Recently Completed Work
- `VariantDecisionTrace` privacy boundaries hardened (`OcrDiagnosticsExporterTest.kt`).
- `OcrConfidenceReasons` telemetry privacy and model-copy integration verified (`OcrConfidenceReasonsIntegrationTest.kt`).
- History DB SQLCipher/Robolectric test approach documented (`HISTORY_REPOSITORY_TEST_PLAN.md`).

### `rawOcrText` Cleanup Status
- **Redundant Markers Removed**: `RecognitionSummary`, `CpOcrStatus`, `HpOcrStatus`, `Classifier*`, `FullVariant*`, `IvDiagnosticDir`, `IvDiagnosticFile_`.
- **Structured Models Now Available**: 
  - `OcrConfidenceReasons`
  - `VariantDecisionTrace`
- **Remaining Markers Intentionally Preserved**: 
  - `CP`, `HP`, `Name`, `Candy`, `Date`
  - `NameHC`, `HPWM`, `HPClean`, `HPBlock`
  - `SizeTag`, `LuckyDetected`

### Current Risks
- History repository is heavily dependent on SQLCipher, meaning native unit tests crash on standard JVM targets.
- Old versions of SDK/Gradle build-tools are triggering harmless but messy console warnings during compilation.
- `rawOcrText` remains necessary for several fallback loops and must not be fully removed yet.

---

## Next Tasks (Ordered by Safety and Value)

1. **Task A: TextParser UI/noise regression coverage**
   - **Risk Level:** None (test only)
   - **Expected Files:** `TextParserTest.kt`, `TextParseUtilsTest.kt`
   - **Verification:** `./gradlew testDebugUnitTest`

2. **Task C: VariantDecisionTrace fallback/compatibility coverage**
   - **Risk Level:** None (test only)
   - **Expected Files:** `OcrDiagnosticsExporterTest.kt`
   - **Verification:** `./gradlew testDebugUnitTest`

3. **Task E: Gradle warning audit**
   - **Risk Level:** None (docs only)
   - **Expected Files:** `docs/GRADLE_WARNING_AUDIT.md`
   - **Verification:** Manual review

4. **Refine history Robolectric dependencies**
   - **Risk Level:** Low
   - **Expected Files:** `build.gradle`, `RobolectricTestRunner.kt`
   - **Verification:** `./gradlew testDebugUnitTest`

5. **Convert remaining OcrConfidenceReasons to Builder pattern**
   - **Risk Level:** Low
   - **Expected Files:** `ScanManager.kt`
   - **Verification:** `./gradlew assembleDebug testDebugUnitTest`

6. **Introduce structured Lucky/Size tags to PokemonData**
   - **Risk Level:** Medium
   - **Expected Files:** `PokemonData.kt`, `OCRProcessor.kt`
   - **Verification:** Unit tests & full scan regression

7. **Migrate CP/HP/Name fallbacks out of rawOcrText**
   - **Risk Level:** Medium
   - **Expected Files:** `ScanManager.kt`, `PokemonData.kt`
   - **Verification:** Comprehensive OCR regression suite

8. **Implement History DB SQLCipher bypass for testing**
   - **Risk Level:** Medium
   - **Expected Files:** `ScanHistoryRepository.kt`, `TestDatabaseModule.kt`
   - **Verification:** `./gradlew testDebugUnitTest`

9. **Deprecate rawOcrText for local diagnostics entirely**
   - **Risk Level:** High
   - **Expected Files:** `OcrDiagnosticsExporter.kt`
   - **Verification:** Telemetry parity checks

10. **Finalize deletion of rawOcrText field from PokemonData**
    - **Risk Level:** High
    - **Expected Files:** `PokemonData.kt`, numerous consumers
    - **Verification:** Full integration and device testing
