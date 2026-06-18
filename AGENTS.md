# AGENTS.md

## Project
PokemonRarityScanner is a passive Android/Kotlin scanner for Pokemon GO
collection screens. It uses screen capture, OCR, visual signals, rarity
metadata, and an overlay to explain variant and rarity decisions.

## Safety Boundaries
- Keep the app passive. Do not automate gameplay, inject input, read game
  memory, bypass security, require root, or add network calls.
- Do not remove privacy, consent, telemetry opt-in, or MediaProjection checks.
- Do not touch `local.properties`, signing keys, telemetry keys, `.env` files,
  keystores, or other secrets.
- Do not run release builds unless explicitly requested.
- Do not commit APKs, build outputs, cache files, screenshots, telemetry
  payloads, or device-specific artifacts.
- Preserve existing user changes.

## Current Priority
Improve scan reliability, OCR/parsing/frame fusion, telemetry safety, and test
coverage with small, reviewable patches. Do not redesign UI or rewrite the
pipeline.

## Important Paths
- `app/src/main/java/com/pokerarity/scanner/service/`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/`
- `app/src/main/java/com/pokerarity/scanner/util/vision/`
- `app/src/main/java/com/pokerarity/scanner/data/repository/`
- `app/src/main/java/com/pokerarity/scanner/data/remote/`
- `app/src/main/assets/data/`
- `app/src/test/`
- `app/src/androidTest/`
- `scripts/`
- `docs/`

## Verification Commands
```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## Managed-Agent Workflow
- Main Codex session acts as manager.
- `scan-explorer`, `test-auditor`, and `privacy-reviewer` are read-only.
- `implementation-worker` edits only the manager-approved patch.
- `release-reviewer` performs the final read-only review.
- Record findings, plan, results, risks, and next task in `docs/AI_RUN_REPORT.md`.
