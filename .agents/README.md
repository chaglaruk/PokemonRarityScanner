# Purpose: Explain repo-scoped agent skills and routing for Antigravity/Codex-compatible agents.

# Agent Skills

This directory contains repo-scoped agent skill files imported from
[addyosmani/agent-skills](https://github.com/addyosmani/agent-skills). Each
skill folder includes a `SKILL.md` with instructions and possibly examples,
references, and scripts.

## How These Are Used

These are repo-scoped Markdown skills for Antigravity, Codex, and similar AI
coding agents. Antigravity may not have confirmed native auto-discovery for
these skills, so `AGENTS.md` explicitly routes agents to read relevant
`SKILL.md` files before non-trivial work.

## Installed Skills

| Skill | Purpose |
| --- | --- |
| `using-agent-skills` | How to read and apply agent skills |
| `planning-and-task-breakdown` | Break complex tasks into incremental steps |
| `incremental-implementation` | Implement changes in small verified steps |
| `test-driven-development` | Write tests first, then implementation |
| `debugging-and-error-recovery` | Systematic debugging and error recovery |
| `code-review-and-quality` | Pre-commit review, style, and quality checks |
| `security-and-hardening` | Security review for secrets, permissions, data |
| `performance-optimization` | Performance profiling and optimization |
| `git-workflow-and-versioning` | Git workflow, branching, commit hygiene |
| `documentation-and-adrs` | Architecture Decision Records and docs |
| `source-driven-development` | Source-first research for external APIs/libs |
| `code-simplification` | Simplify and refactor complex code |
| `context-engineering` | Context handoff and session continuity |

## Important Notes

- Project-specific safety rules in `AGENTS.md` always override generic skill
  guidance.
- Do not install additional skills without explicit user approval.
- Skills should be read before non-trivial work, not executed blindly.
- Source: `addyosmani/agent-skills`, imported 2026-05-31.
