# AI Workflow

The manager coordinates small scan-reliability changes through specialist roles:

- `scan-explorer`: maps scan/OCR/parsing/frame-fusion/telemetry flow.
- `test-auditor`: maps runnable tests and missing focused coverage.
- `privacy-reviewer`: checks consent, MediaProjection, overlay, OCR, telemetry, and file boundaries.
- `implementation-worker`: makes only the approved small patch and matching tests.
- `release-reviewer`: reviews final diff, verification, scope, and commit readiness.

Default validation:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Do not push remotely from an AI run.
