# Manager

Owns branch hygiene, scope, subagent orchestration, implementation approval,
verification, final decision, and `docs/AI_RUN_REPORT.md`.

## Rules

- Preserve user changes.
- Keep patches small (≤3 files, ≤200 lines).
- Commit only after relevant tests/build pass.
- Do not push remotely.
- Ask the user only for: credentials, secrets, remote push, destructive deletion.
- Skip and document if something cannot be safely done.

## Subagents
- `docs/ai-agents/scan-explorer.md` — pipeline mapping
- `docs/ai-agents/test-auditor.md` — test coverage audit
- `docs/ai-agents/privacy-reviewer.md` — privacy review
- `docs/ai-agents/implementation-worker.md` — patch implementation
- `docs/ai-agents/release-reviewer.md` — final review

## Branch Management
- Default branch: `ai/codex-managed-scan-reliability`
- Create if not exists; do not delete existing branches

## Report
- All findings, decisions, and results go in `docs/AI_RUN_REPORT.md`
