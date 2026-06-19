# Security Risk Register

| Risk | Severity | Status | Notes |
| --- | --- | --- | --- |
| Legacy dynamic internal broadcasts spoofable by same-action broadcasts | High | Fixed | Signature permission now gates pre-API 33 dynamic receivers and senders. |
| Reuse of cached MediaProjection grant | High | Fixed | Scanner start always launches the projection prompt; cached grants clear on release, teardown, and setup failure. |
| Foreground service continues after mediaProjection type promotion failure | High | Fixed | Service now clears grant, notifies projection-required, and stops. |
| Telemetry could upload screenshots despite consent text | High | Fixed for scan telemetry | Scan telemetry enqueue is metadata-only; uploader still supports screenshots for any future explicit caller. |
| Opt-out leaves queued telemetry behind | Medium | Fixed | Resume flush purges when consent is off; reject/disable also triggers purge. |
| Telemetry retention was a no-op | Medium | Fixed | Old upload/offline rows are deleted, and telemetry cache files are removed only from the telemetry cache directory. |
| Screenshot broadcast payload accepts arbitrary paths | Medium | Fixed | Paths are canonicalized to direct app-cache `scan_*.png` files and capped. |
| OCR diagnostics local files lack dedicated retention | Medium | Open | Follow-up should add retention and user-visible deletion controls. |
| Debug logs include OCR/classifier details | Medium | Open | Follow-up should gate or redact logs that can include user-visible scan text. |
| Telemetry export script uses API key in query string | Medium | Open | Requires server/script protocol change. |
| Dependency advisory review pending | Medium | Open | Run a dedicated dependency audit and compatibility test before release. |
| Release workflow checkout persisted credentials | Low | Fixed | Checkout now sets `persist-credentials: false`; release job still requires write permission to publish releases. |
| Local graph/agent artifacts can enter branch accidentally | Low | Fixed | `.gitignore` now covers `.codegraph/`, `graphify-out/`, and local `.codex/` artifacts. |
