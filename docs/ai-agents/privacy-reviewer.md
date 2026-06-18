# privacy-reviewer

Read-only role.

Reviews overlay safety, MediaProjection/OCR privacy, telemetry persistence,
app-private file boundaries, dynamic receivers, consent checks, and regression
risks.

Return concrete risks with file paths and fix suggestions only.

## Findings (this run)

### MediaProjection
- `ScreenCaptureService.kt:91-97` — RateLimiter set to 10 captures/min, preventing broadcast spam ✅
- `ScreenCaptureManager.kt:29-33` — user must grant consent every session ✅
- Two-phase foreground promotion for Android 14+ (`SPECIAL_USE` then `MEDIA_PROJECTION`) ✅

### Broadcast Receivers
- All use `Context.RECEIVER_NOT_EXPORTED` where supported ✅
- `ScreenCaptureService.captureReceiver` (lines 89-101) registered in `onCreate` ✅

### Telemetry
- `TelemetryConsentDialog.kt` — explicit opt-in before any telemetry ✅
- `OfflineTelemetryDao.kt` — queue-based flush with `flushedAt` marker ✅
- Telemetry payload (`ScanTelemetryPayload.kt`) contains no PII except device model ✅
- `OcrDiagnosticsPrivacyTest.kt` verifies OCR debug text is stripped before storage ✅

### File Boundaries
- Screenshots written to `context.cacheDir` (app-private) only ✅
- `cleanOldScreenshots()` caps at 20 files ✅
- SQLCipher database for stored scan data ✅

### No Risks Found Requiring Immediate Action
