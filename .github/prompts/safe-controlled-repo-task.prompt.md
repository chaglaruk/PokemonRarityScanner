---
name: safe-controlled-repo-task
description: Use for a controlled PokemonRarityScanner task that verifies live repository authority, fails closed on unexpected state, preserves passive/privacy/recognition invariants, applies only approved changes, and separates local from external validation.
---

# Safe controlled repository task

Use this prompt for any controlled PokemonRarityScanner task.

## Required Start State

- Verify repository identity, current branch, HEAD, `origin/main`, worktree and staging status, open PRs, and the authoritative plan against the expected context of the current task.
- Stop and request confirmation when repository identity is wrong, an explicitly stated branch or HEAD baseline conflicts, the current branch belongs to an unrelated active task, staged or tracked unrelated changes exist, untracked changes exist and have not been explicitly acknowledged, or the requested scope conflicts with the live plan.
- Stop and request confirmation when required authority or baseline evidence is unavailable, stale, or unverifiable.
- Allow explicitly acknowledged pre-existing unrelated items to remain untouched.
- Never reset, clean, stash, delete, ignore, stage, or modify unrelated items merely to obtain a clean worktree.
- Continue only when the task scope and relevant baseline are clear.

## Operating Rules

- Preserve passive operation only.
- No gameplay automation.
- No input injection.
- No game-memory access.
- No root requirement.
- No security bypass.
- No private Pokémon GO endpoints.
- No account login or gameplay integration.
- No unauthorized network calls.
- Preserve explicit MediaProjection consent and checks.
- Preserve overlay consent and safety controls.
- Telemetry is disabled by default.
- Telemetry requires explicit opt-in.
- Do not expose screenshots, raw OCR, local paths, secrets, or sensitive payloads in telemetry or release-visible logs.
- Do not weaken privacy, consent, or local-data safeguards.
- Screenshot candidates are not truth or publishable fixtures before the required human privacy, provenance, and truth review.
- Preserve the recognition invariant exactly:

```text
A confidently accepted wrong species must be impossible in every executable deterministic test corpus. When evidence is insufficient, return Uncertain or request user confirmation rather than silently choosing another species.
```

- Fail closed on insufficient or conflicting evidence.
- Never invent manual truth labels.
- Never infer truth from corrupt fixtures.
- Weak visual evidence cannot establish global species identity.
- No OCR threshold, crop geometry, scaling, or classifier-authority change without evidence required by the live plan.
- Compilation alone does not establish recognition correctness.

## Live Plan Gates

- Read and report the live status of PR-06 evidence gates.
- Read and report the live status of Manual Gate A.
- Read and report the live status of the PR-07 holdout gate.
- Read and report the live status of logging, privacy, and release hardening.
- Read and report the live status of signed-release and MobSF verification.
- Do not state current completion status as permanent policy.

## Scope Discipline

- Apply only the approved changes.
- Allow explicitly acknowledged pre-existing unrelated items to remain untouched.
- Do not touch source code, tests, dependencies, workflows, build configuration, repository settings, or MCP configuration unless the current task explicitly authorizes it.
- Keep `docs/AI_RUN_REPORT.md` unchanged unless the current task explicitly authorizes updating it.

## Managed-Agent Workflow

- Use the roles already defined in `AGENTS.md` without redefining them.
- Read-only agents stay read-only.
- `implementation-worker` edits only manager-approved scope.
- `release-reviewer` performs final read-only review.
- `docs/AI_RUN_REPORT.md` is updated only when the current implementation task explicitly authorizes it.

## Secrets, Builds, And Artifacts

- Prohibit unauthorized access or modification of `local.properties`, `.env` files, signing keys, keystores, telemetry keys, signing configuration, credentials, and user-level settings.
- No release build unless explicitly authorized.
- No APK, AAB, build output, cache, screenshot, telemetry, device, or local evidence artifact committed.
- No lint or detekt baseline regeneration to hide findings.
- No proprietary Calcy code, assets, data, or thresholds.

## Exact Verification Commands

- Use the exact PowerShell commands from `AGENTS.md`, including the `.\` prefix:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

- The narrowest relevant verification runs first.
- Local verification and external validation are reported separately.
- Ordinary validation must not run release builds.
- Compilation does not prove recognition accuracy, privacy safety, or release readiness.

## Detailed MCP Policies

### GitHub MCP

- Use for live repository files, metadata, PRs, reviews, and CI context when relevant.
- No branch, issue, PR, review, merge, close, settings, or other GitHub mutation without explicit authorization.
- Never claim CI or review success without a successful live result.

### Sentry MCP

- Use only for authorized existing runtime errors, crashes, traces, or performance evidence.
- Do not add the Sentry SDK merely because MCP access exists.
- Do not expose screenshots, OCR content, local paths, secrets, or sensitive telemetry.
- Do not modify Sentry projects, issues, alerts, or settings without explicit authorization.
- Sentry evidence is not species ground truth.

### BrowserStack MCP

- Use only for justified device, browser, or platform validation.
- Prefer repository-defined local validation first unless the task explicitly targets BrowserStack.
- Do not add BrowserStack CI or broad device matrices merely because access exists.
- Browser emulation cannot replace required physical-device evidence for Android recognition or MediaProjection claims.

### Figma MCP

- Use only when the task provides a Figma URL, node, component, or explicitly authorized design scope.
- Figma cannot override the live plan, recognition logic, privacy, consent, accessibility, or tests.
- Do not redesign UI during recognition work unless separately authorized.

### JetBrains

- JetBrains products are manual IDE environments, not MCP tools.
- Do not claim Android Studio or IntelliJ verification unless it actually occurred or its output was supplied.

## Impact Checklist

For each relevant item, report exactly one of `not affected`, `verified`, `requires follow-up`, or `blocked by evidence/manual gate`.

- passive-operation boundaries
- MediaProjection or overlay consent
- telemetry or privacy
- species authority or fail-closed recognition
- OCR, parsing, or frame fusion
- visual authority
- crop geometry or image scaling
- fixture privacy, provenance, or truth
- Manual Gate A
- PR-06 evidence gates
- PR-07 holdout gate
- logging/privacy/release hardening
- signed release or MobSF verification
- secrets, signing, or release artifacts

## Hard Constraints

- No commit, push, pull request, or merge unless explicitly authorized.
- No destructive Git operation.
- No unrelated worktree cleanup.
- No release build unless explicitly authorized.
- No secrets, credentials, signing material, or user-level setting changes.
- No automatic Sentry, BrowserStack, or Figma use.
- No fabricated labels, device results, CI results, review results, or evidence.
- No production code, dependency, workflow, or implementation-plan modification merely to service this reusable prompt.

## Reporting Standard

- Explicitly acknowledge any pre-existing unrelated items left untouched.
- State authority conflicts or missing evidence explicitly.
- Keep changes small, reviewable, and limited to the approved scope.
