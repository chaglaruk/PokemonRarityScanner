# Rollback Notes

## 2026-04-06

- DatabasePassphraseStore import changed from net.sqlcipher to net.zetetic; rollback by reverting the file if SQLCipher 4.5.4 integration causes issues.
- SQLCipher migration logic now uses SQLiteDatabase.changePassword(byte[]) with sqlcipher-android 4.5.4 openDatabase signature.
- ScanTelemetryUploaderTest expected error string updated to Missing or invalid screenshot_url after tightening response validation.
- Security pass touched app/build.gradle.kts, telemetry prefs/config, AppDatabase/DatabasePassphraseStore, MainActivity/CollectionScreen settings UI, and ScanTelemetryUploader config path. Roll back these files together if reverting to pre-hardening behavior.
- AndroidManifest launcher activity SplashActivity changed to exported=true. Revert only if launch entrypoint handling is redesigned.
- Added SqlCipherInitializer and explicit System.loadLibrary(\"sqlcipher\") calls in app startup and DB init. If reverting, remove these only if SQLCipher native loading is guaranteed elsewhere.
- AppDatabase now forces an initial encrypted open and retries once after deleting unreadable/plaintext pokerarity_db artifacts. Revert only if a dedicated plaintext-to-SQLCipher migration is implemented.
- Temporary local inspection artifact directory artifacts/tmp_sqlcipher is not part of the fix and can be deleted safely.
- ScreenCaptureService now supports EXTRA_AUTO_CAPTURE and MainActivity/ProjectionPermissionActivity use it to trigger immediate scans. Revert these together if returning to manual overlay-only capture behavior.
- app/build.gradle.kts now derives versionCode/versionName from gradle.properties/env and emits versioned APK filenames. Release signing falls back to debug signing when no release keystore is configured.
- .github/workflows/release-apk.yml publishes tagged release APKs. Remove or disable if GitHub Releases should not distribute APK artifacts.
- docs/release-process.md documents versioning, signing inputs, and GitHub Release publishing. Safe to remove if release distribution stays manual.
