# app/

Android application module.

## Responsibilities

- Application bootstrap (`PokeRarityApp`)
- Hilt wiring and Android manifest
- MediaProjection capture, overlay service, and scan orchestration
- Compose UI for dashboard, result screen, overlay, history, and settings
- ML Kit/OpenCV recognition pipeline, evidence-based variant resolution, living metadata sync, and local persistence

## Important entry points

- `src/main/java/com/pokerarity/scanner/PokeRarityApp.kt`
- `src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- `src/main/java/com/pokerarity/scanner/service/OverlayService.kt`
- `src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
- `src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt`
- `src/main/java/com/pokerarity/scanner/util/vision/CostumeSignatureStore.kt`
- `src/main/java/com/pokerarity/scanner/data/repository/RemoteMetadataSyncManager.kt`
- `src/main/java/com/pokerarity/scanner/data/repository/MasterPokedexLoader.kt`
- `src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
