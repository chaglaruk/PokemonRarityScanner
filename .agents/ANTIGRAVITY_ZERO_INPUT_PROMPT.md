# Purpose: Reusable autonomous development prompt for future Antigravity sessions.

# Antigravity Zero-Input Autonomous Development Prompt

Use this prompt to start an autonomous Antigravity development session on
PokeRarityScanner without any manual input.

---

## Prompt

You are taking over PokeRarityScanner for autonomous development.

### Step 1: Verify State

```powershell
git status -sb
git fetch origin
git rev-parse HEAD
git rev-parse origin/main
```

Confirm HEAD equals origin/main. If behind or diverged, create a safety branch,
rebase safely, stop on conflicts.

### Step 2: Read Context

Read these files before any work:
- `AGENTS.md`
- `ANTIGRAVITY_HANDOFF.md`
- `PROJECT_STATE.md`
- `AUTONOMOUS_DEV_AUDIT.md`
- `AUTONOMOUS_DEV_LOG.md`
- `docs/OCR_CONFIDENCE_REASONS_PLAN.md`

### Step 3: Read Relevant Skills

Before non-trivial work, read `.agents/skills/using-agent-skills/SKILL.md`.
Then read the skill matching your task type:
- Planning: `planning-and-task-breakdown`
- Implementation: `incremental-implementation`
- Bug fixes: `test-driven-development`
- Build failures: `debugging-and-error-recovery`
- Pre-commit: `code-review-and-quality`, `git-workflow-and-versioning`
- Security/privacy: `security-and-hardening`
- Performance: `performance-optimization`
- Design docs: `documentation-and-adrs`
- External APIs: `source-driven-development`
- Refactors: `code-simplification`
- Context handoff: `context-engineering`

### Step 4: Pick Work

Select the highest-priority task from the backlog in `ANTIGRAVITY_HANDOFF.md`
or `AUTONOMOUS_DEV_AUDIT.md`. Prefer tests and documentation over broad
rewrites.

### Step 5: Implement

For every task:
1. Update `AUTONOMOUS_DEV_LOG.md` with task name, rationale, files, risk,
   verification.
2. Write tests first when possible.
3. Make a small, focused change.
4. Run verification:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
   .\gradlew.bat :app:assembleDebug --no-daemon --console=plain
   ```
5. Review diff.
6. Commit with a conventional commit message.
7. Fetch origin, rebase safely if needed.
8. Push normally. Never force push.

### Step 6: Stop Conditions

Stop if:
- Merge conflicts occur.
- Tests fail with non-obvious fix.
- Secrets or credentials are needed.
- Release signing is required.
- Device-only validation is required.
- Task becomes architectural or too broad.
- More than 6 implementation commits in this session.
- Total diff becomes too large to review safely.

### Step 7: Report

Return a compact report:
- Current HEAD.
- Commits created and pushed.
- Tests/build checks run.
- Tasks completed.
- Tasks skipped and why.
- Remaining risks.
- Next 5 recommended tasks.

### Safety Rules

- Keep the app passive. No gameplay automation.
- Do not touch `local.properties`, signing keys, telemetry keys, `.env`,
  keystores, or secrets.
- Do not run release builds unless explicitly requested.
- Do not force push or rewrite history.
- Project safety rules override generic skills.
