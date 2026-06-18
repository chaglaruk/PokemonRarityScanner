# Security Fix Plan

Manager decision:

Implement the smallest confirmed hardening slice that closes high-risk privacy and platform-lifecycle gaps without redesigning UI or rewriting the scan pipeline.

Approved implementation slice:

1. Protect internal broadcasts with a signature permission and keep receivers non-exported on API 33+.
2. Treat screenshots from broadcasts as untrusted input; accept only direct app-cache scan PNGs.
3. Make scan telemetry metadata-only to match consent wording.
4. Purge pending/offline telemetry when consent is disabled and make retention delete old telemetry rows/files.
5. Require fresh MediaProjection consent per scanner start and clear grants on stop/failure.
6. Fail closed if the service cannot promote to `MEDIA_PROJECTION` foreground type.
7. Reduce CI checkout token exposure and ignore local generated-agent artifacts.

Out of scope:

- UI redesign or telemetry consent copy expansion.
- New network endpoints or server-side telemetry protocol changes.
- Full OCR diagnostics retention redesign.
- Dependency upgrades without a dedicated compatibility check.
- Release build execution.

Changed files:

- `.github/workflows/release-apk.yml`
- `.codex/agents/*.toml` removed from tracking
- `.codex/config.toml` removed from tracking
- `.gitignore`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/pokerarity/scanner/data/local/DataRetentionManager.kt`
- `app/src/main/java/com/pokerarity/scanner/data/local/db/OfflineTelemetryDao.kt`
- `app/src/main/java/com/pokerarity/scanner/data/local/db/TelemetryUploadDao.kt`
- `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryCoordinator.kt`
- `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
- `app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt`
- `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- `app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureManager.kt`
- `app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureService.kt`
- `app/src/main/java/com/pokerarity/scanner/ui/main/MainActivity.kt`
- `app/src/test/java/com/pokerarity/scanner/ScanManagerDetailedPassTest.kt`
- `app/src/test/java/com/pokerarity/scanner/ScreenCaptureManagerTest.kt`

Next recommended task:

Add dedicated tests and controls for OCR diagnostics retention, then audit debug logging for OCR text/path leakage in debug and internal builds.
