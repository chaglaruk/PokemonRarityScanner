# AI Run Report

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

Pending final verification.
