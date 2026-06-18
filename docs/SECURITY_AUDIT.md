# Android Hardening Audit

Date: 2026-06-18

Branch: `security/android-hardening-audit`

Scope:

- Android manifest, foreground services, dynamic broadcasts, MediaProjection lifecycle.
- Scan screenshot handling, OCR diagnostics, telemetry consent, telemetry queueing, local retention.
- CI release workflow token exposure and local generated-agent artifact hygiene.

Subagents used:

- `manifest-permissions-reviewer`: inspected manifest, services, receivers, foreground-service types, and dynamic broadcast registration.
- `telemetry-data-privacy-reviewer`: inspected telemetry consent, queueing, upload payloads, offline staging, and opt-out behavior.
- `ocr-cache-privacy-reviewer`: inspected screenshot cache paths, OCR diagnostics, local exports, and retention behavior.
- `dependencies-config-reviewer`: inspected dependency/config hardening and release workflow token handling.

Findings:

- High: pre-API 33 dynamic internal broadcasts used package targeting but did not require a signature permission from senders.
- High: MediaProjection grant data could be cached and reused across scanner starts instead of always requiring a fresh user prompt.
- High: `ScreenCaptureService` continued after `MEDIA_PROJECTION` foreground promotion failure by falling back to `specialUse`.
- High: telemetry consent text says screenshots are not collected, while scan telemetry could still enqueue a screenshot path.
- Medium: telemetry flush on app resume did not re-check consent and queued rows could survive opt-out.
- Medium: telemetry retention claimed 30-day deletion but `deleteOldTelemetry()` did not delete telemetry rows or telemetry cache files.
- Medium: screenshot-ready broadcasts accepted arbitrary path strings from the broadcast payload.
- Low: release workflow checkout persisted credentials through the Gradle build step.
- Low: local graph/code-agent artifacts were not ignored consistently.

Implemented fixes:

- Added a signature-only internal broadcast permission and required it for legacy dynamic receivers and internal senders.
- Sanitized screenshot-ready payload paths to direct `cacheDir/scan_*.png` files and capped accepted frames.
- Changed scan telemetry enqueue to metadata-only by passing no screenshot path.
- Added opt-out purge behavior for pending telemetry and offline telemetry rows plus telemetry cache files.
- Made telemetry retention delete old upload/offline rows and only delete old telemetry files under `cache/telemetry_uploads`.
- Removed the MainActivity projection reuse shortcut and clear cached projection grants on service teardown/failure.
- Made `ScreenCaptureService` fail closed if `MEDIA_PROJECTION` foreground promotion fails.
- Set release workflow checkout `persist-credentials: false`.
- Ignored `.codegraph/`, `graphify-out/`, and local `.codex/` artifacts.

Remaining risks:

- OCR diagnostics can still write local diagnostic artifacts for troubleshooting; scope is local, but retention/controls should be tightened in a follow-up.
- Debug logs still contain some OCR and classifier details in non-release builds; review logging policy before wider beta distribution.
- The telemetry export script still places the API key in a query string; migrate to header or signed body before using it outside trusted local operations.
- Dependency advisory review was not completed in this slice; run a dedicated dependency audit before release.
- The release workflow still needs `contents: write` when publishing tagged GitHub releases, even though checkout credentials are no longer persisted.
