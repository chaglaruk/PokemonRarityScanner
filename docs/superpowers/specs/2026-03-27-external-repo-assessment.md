# External Repo Assessment - 2026-03-27

## Scope

Evaluate external GitHub repositories for direct value to the PokeRarityScanner app runtime, telemetry pipeline, or development workflow.

## Assessment

### 1. thedotmack/claude-mem
- Type: agent memory / context compression system for Claude Code.
- Value to app runtime: none.
- Value to workflow: moderate for long-lived coding sessions, but not an app dependency.
- Decision: do not integrate into app.

### 2. nextlevelbuilder/ui-ux-pro-max-skill
- Type: UI/UX design reasoning skill and design-system generator.
- Value to app runtime: none.
- Value to workflow: high for future result screen / onboarding redesign work.
- Decision: keep as design workflow aid only.

### 3. czlonkowski/n8n-mcp
- Type: n8n MCP integration / automation tooling.
- Value to app runtime: none.
- Value to telemetry pipeline: medium-high.
- Best use: automate telemetry export ingestion, labeling queues, and dashboard sync outside the Android app.
- Decision: defer; useful after telemetry volume grows.

### 4. affaan-m/everything-claude-code
- Type: Claude Code workflow / prompt / environment collection.
- Value to app runtime: none.
- Value to workflow: low incremental value relative to existing superpowers + project plan setup.
- Decision: do not integrate.

### 5. obra/superpowers
- Type: coding workflow skills system.
- Value to app runtime: none.
- Value to workflow: already in use.
- Decision: already integrated; no further app work from this repo.

### 6. gsd-build/get-shit-done
- Type: workflow/spec-driven development system for coding agents.
- Value to app runtime: none.
- Value to workflow: overlaps heavily with superpowers.
- Decision: do not integrate now; redundant with current setup.

## Net Decision

No listed repo is a direct Android runtime dependency for scan accuracy or rarity logic.

Immediate app-facing value:
- none

Workflow/infrastructure value:
- `ui-ux-pro-max-skill` for future UI work
- `n8n-mcp` for future telemetry automation

## Follow-up

Continue the full matcher rewrite and keep external repo usage limited to:
- workflow support
- telemetry processing automation
- design assistance
