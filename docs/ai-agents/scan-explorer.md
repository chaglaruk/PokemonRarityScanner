# scan-explorer

Read-only role.

Maps the scan pipeline from services and overlays through OCR, parsing,
frame fusion, appraisal/CP/species extraction, telemetry, and tests.

Output exact paths, important classes/functions, risk areas, and smallest next
fixes. Do not edit files.

## Key Files Observed

### Service Orchestration
- `service/ScanManager.kt` — main pipeline orchestrator (656 lines)
- `service/ScreenCaptureService.kt` — foreground service, 2-frame capture
- `service/ScreenCaptureManager.kt` — MediaProjection lifecycle, consent
- `service/ScanFrameFusion.kt` — frame selection and fusion logic

### OCR Processing
- `util/ocr/OCRProcessor.kt` — parallel field recognition (CP, HP, name, date, candy)
- `util/ocr/TextParser.kt` — 913 lines, all text parsing (CP, name, candy, dates, etc.)
- `util/ocr/SpeciesRefiner.kt` — 417 lines, species refinement logic
- `util/ocr/ScanConsistencyGate.kt` — authoritative frame enforcement
- `util/ocr/MLKitOcrProvider.kt` — MLKit wrapper
- `util/ocr/ImagePreprocessor.kt` — OpenCV preprocessing

### Vision / Rarity
- `util/vision/VariantDecisionEngine.kt` — visual feature classification
- `util/vision/Phase2VariantClassifier.kt` — secondary variant detection
- `util/vision/VisualFeatureDetector.kt` — shimmer/signature detection
- `data/repository/RarityCalculator.kt` — CP validation
- `data/repository/CollectionResultMapper.kt` — rarity score mapping

### Privacy / Telemetry
- `service/ScreenCaptureService.kt` — rate-limiting (10/min), app-private cache
- `ui/dialog/TelemetryConsentDialog.kt` — opt-in consent
- `data/local/db/OfflineTelemetryDao.kt` — offline queue
- `data/local/SecurePreferencesFactory.kt` — AES-256 encrypted prefs
- `data/local/DataRetentionManager.kt` — data retention
- `util/ScanErrorHandler.kt` — error taxonomy
