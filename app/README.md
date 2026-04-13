# app/

Android application module.

## Responsibilities

- Application bootstrap (`PokeRarityApp`)
- Hilt wiring and Android manifest
- MediaProjection capture, overlay service, and scan orchestration
- Compose UI for dashboard, result screen, overlay, history, and settings
- OCR/CV pipeline, IV solving, telemetry queueing, and local persistence

## Important entry points

- `src/main/java/com/pokerarity/scanner/PokeRarityApp.kt`
- `src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- `src/main/java/com/pokerarity/scanner/service/OverlayService.kt`
- `src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
- `src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
