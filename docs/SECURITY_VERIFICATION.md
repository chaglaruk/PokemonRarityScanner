# Security Verification

Date: 2026-06-18

Commands:

- `.\gradlew.bat :app:testDebugUnitTest --tests "com.pokerarity.scanner.ScanManagerDetailedPassTest" --tests "com.pokerarity.scanner.ScreenCaptureManagerTest" --no-daemon --console=plain`
- `git diff --check`
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
- `.\gradlew.bat :app:lintDebug --no-daemon --console=plain`

Results:

- Focused tests passed: `ScanManagerDetailedPassTest` and `ScreenCaptureManagerTest`.
- `git diff --check` passed; Git printed only CRLF conversion warnings from the Windows worktree.
- Full debug unit tests passed.
- `assembleDebug` passed.
- `lintDebug` initially found two lint errors: a new legacy receiver-registration flag issue and a pre-existing API-28 `longVersionCode` issue. Both were fixed, and the rerun passed.
- Non-blocking recurring warnings: SDK XML version warning and Gradle 9 deprecation warning.

Manual review checklist:

- Internal broadcasts: no pre-API 33 scanner receiver is registered without `INTERNAL_BROADCAST_PERMISSION`.
- Screenshot payloads: scan manager rejects non-cache, wrong-name, missing, and excess frame paths.
- Telemetry consent: scan telemetry no longer passes a screenshot path; pending queues purge on opt-out.
- MediaProjection: start flow always prompts; service clears cached grant on teardown/failure; promotion failure stops service.
- CI/config: release checkout credentials are not persisted; generated local agent artifacts are ignored.
