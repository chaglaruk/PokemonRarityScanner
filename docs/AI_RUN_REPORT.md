# AI Run Report

## Scan Reliability PR Readiness

## Scope

Branch: `ai/codex-managed-scan-reliability`

Goal: keep one small scan-reliability PR against `origin/main`, excluding local
agent/Graphify artifacts and unrelated UI/catalog/history work from the older
branch tip.

## Subagents Used

- `artifact-cleaner` (real Codex subagent): checked generated/local artifact
  payload.
- `test-auditor` (real Codex subagent): reviewed staged tests and call sites.
- `release-reviewer` (real Codex subagent): reviewed staged diff for merge
  risk.

## Findings

- The previous branch tip contained unrelated UI/catalog/history changes, so a
  backup branch was created: `backup/codex-managed-scan-reliability-full`.
- The PR branch was reconstructed from `origin/main` with only scan reliability,
  managed-workflow docs, and local artifact cleanup.
- `.codex/agents/*.toml` and `.codex/config.toml` deletion is intentional per
  the cleanup request to remove tracked local Codex artifacts from this branch.
- `ScanManager` needed the matching call-site update for the new
  `ScanFrameFusion.isHighConfidence(frames)` API.
- Review found the blocked-family tests used unreachable helper inputs; they now
  pass reachable candy-family inputs.
- Review found the `.codex` ignore exception was ineffective; `.gitignore` now
  ignores `.codex/*` while allowing `.codex/README.md`.
- Final review found `Name:Unknown` could be treated as a strong authoritative
  anchor; `ScanConsistencyGate` now rejects `Unknown` anchors and has a
  regression test.
- Tracked local `.codex` agent/config files are deleted from the PR branch by
  design; no `graphify-out/`, `.codex/hooks.json`, `.codex/agents-disabled/`,
  `.codex/skills/`, build outputs, APKs, caches, secrets, or keystores are
  staged.

## Manager Decision

Proceed with the narrowed branch only if focused tests, full unit tests,
`assembleDebug`, diff hygiene, artifact cleanup, and release review pass.

## Changed Files

- `.gitignore`
- `AGENTS.md`
- `.agents/README.md`
- `app/src/main/java/com/pokerarity/scanner/service/ScanFrameFusion.kt`
- `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanConsistencyGate.kt`
- `app/src/test/java/com/pokerarity/scanner/ScanAuthorityLogicTest.kt`
- `app/src/test/java/com/pokerarity/scanner/ScanConsistencyGateEdgeCaseTest.kt`
- `app/src/test/java/com/pokerarity/scanner/ScanFrameFusionTest.kt`
- `docs/AI_RUN_REPORT.md`
- `docs/AI_WORKFLOW.md`
- `docs/BUILD_AND_TEST.md`
- `docs/FEATURE_BACKLOG.md`
- `docs/KNOWN_ISSUES.md`
- `docs/ai-agents/*.md`

Deleted from branch:

- `.codex/agents/*.toml`
- `.codex/config.toml`

## Test And Build Commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.pokerarity.scanner.ScanFrameFusionTest" --tests "com.pokerarity.scanner.ScanAuthorityLogicTest" --tests "com.pokerarity.scanner.ScanConsistencyGateEdgeCaseTest" --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## Results

- `git diff --cached --check`: PASS.
- Focused `ScanFrameFusionTest`, `ScanAuthorityLogicTest`, and
  `ScanConsistencyGateEdgeCaseTest`: PASS after the `Unknown` anchor fix,
  exit code 0.
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: PASS,
  `BUILD SUCCESSFUL in 30s`.
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`: PASS,
  `BUILD SUCCESSFUL in 27s`.
- Nonfatal warnings observed: Android SDK XML version warning, Gradle 9.0
  deprecation warning, and unstripped native debug libraries packaged as-is.

## Remaining Risks

- `ScanFrameFusion` now requires two matching high-confidence frame shapes for
  early exit; this is intentional but should be watched on slow devices.
- Repeated CP frame selection does not filter by `CP_QUALITY_MIN`; current
  two-frame capture limits risk, but revisit if capture count grows.
- Exact score-boundary coverage inside `ScanConsistencyGate` is still partly
  asset-dependent.

## Next Recommended Task

Add one focused `TextParser` regression test for exact raw species-name anchors,
starting with short family names such as `Eevee`.

## Commit

- `02848afd` - `core: improve scan reliability and managed cleanup`
- Final release-reviewer result: GO after the `Unknown` anchor fix and explicit
  local `.codex` cleanup scope.

## Security Hardening Audit

Date: 2026-06-18

Branch: `security/android-hardening-audit`

Objective:

Continue the managed workflow after the scan reliability PR and complete Phase 2 Android/security hardening without pushing the branch.

Subagents used:

- `manifest-permissions-reviewer`
- `telemetry-data-privacy-reviewer`
- `ocr-cache-privacy-reviewer`
- `dependencies-config-reviewer`
- Manager, implementation-worker, tester, and release-reviewer roles were coordinated by Codex in this session.

Findings:

- Legacy dynamic broadcasts needed sender/receiver permission hardening.
- MediaProjection grant reuse and foreground-service fail-open behavior needed correction.
- Telemetry screenshot handling contradicted consent wording.
- Telemetry opt-out and retention needed queue/cache purge behavior.
- Screenshot broadcast paths needed sanitization.
- Release workflow checkout credentials and local generated-agent artifacts needed cleanup.

Manager decision:

Fix confirmed high and medium issues with narrow code changes. Keep scanner UI and scan reliability behavior intact. Do not add network calls, do not remove consent checks, do not run release builds, and do not push.

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
- `docs/AI_RUN_REPORT.md`
- `docs/SECURITY_AUDIT.md`
- `docs/SECURITY_FIX_PLAN.md`
- `docs/SECURITY_RISK_REGISTER.md`
- `docs/SECURITY_VERIFICATION.md`

Verification:

- Focused tests: passed, `ScanManagerDetailedPassTest` and `ScreenCaptureManagerTest`.
- `git diff --check`: passed; only CRLF conversion warnings from the Windows worktree.
- Full unit tests: passed with `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`.
- `assembleDebug`: passed with `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`.
- `lintDebug`: passed after fixing a new receiver-registration lint issue and a pre-existing `longVersionCode` API guard.
- Non-blocking recurring warnings: SDK XML version warning and Gradle 9 deprecation warning.

Commit:

- `2a4a5e96 security: harden scanner telemetry and projection lifecycle`

Remaining risks:

- OCR diagnostics retention and debug log redaction need a dedicated follow-up.
- Telemetry export script should stop placing API keys in query strings.
- Dependency advisory review remains open.
- Release workflow still requires write permission for publishing tagged releases.

Next recommended task:

Harden OCR diagnostics retention and debug logging, with focused tests for local diagnostic cleanup and path/text redaction.
