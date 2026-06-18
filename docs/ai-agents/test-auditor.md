# test-auditor

Read-only role.

Finds existing tests, missing focused coverage, flaky-risk areas, and the
validation plan.

Preferred commands:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## Existing Test Coverage (70+ unit, 8 android instrumented)

### Unit Tests (`app/src/test/java/com/pokerarity/scanner/`)
- Frame fusion: `ScanFrameFusionTest.kt` (12 tests)
- Species refining: `ScanAuthorityLogicTest.kt`
- OCR parsing: `TextParserLogicTest.kt`, `TextParserPowerUpCostTest.kt`, `TextParserNameRecoveryTest.kt`
- Consistency gate: `ScanConsistencyGateEdgeCaseTest.kt`
- Scan manager: `ScanManagerDetailedPassTest.kt`
- Variant decision engine: `VariantDecisionEngineTest.kt`
- Rarity scoring: `RarityRulesScoringTest.kt`, `RarityAgeScoringTest.kt`
- Phase 2 merge: `Phase2VariantFeatureMergerTest.kt`
- Telemetry: `ScanTelemetryUploaderTest.kt`, `ScanTelemetryPayloadTest.kt`
- Privacy: `OcrDiagnosticsPrivacyTest.kt`
- Date parsing: `DateParseUtilsTest.kt`
- ...+ 50+ more

### Instrumented Tests (`app/src/androidTest/java/`)
- `ScanConsistencyGateTest.kt`
- `ScanManagerPolicyTest.kt`
- `VariantDecisionEngineTest.kt`
- `VisualFeatureDetectorDecisionTest.kt`
- `OcrDateRegressionTest.kt`
- `ScanRegressionTest.kt`
- `TextParserRegressionTest.kt`
- `SpeciesRefinerTest.kt`

## Identified Gap (this run)

`ScanFrameFusion.shouldRunDetailedPass()` has conditions that were previously
dead code: `topTextConfidence < 0.78` and the candy-named branch at the same
threshold. Tests existed for the 0.86 threshold but not for the exact boundary
values. This has been addressed.
