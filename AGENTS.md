# Purpose: Project-specific instructions for Codex and other AI coding agents.

# PokeRarityScanner Agent Guide

PokeRarityScanner is a passive Android/Kotlin scanner for Pokemon GO collection
screens. It uses screen capture, OCR, visual signals, rarity metadata, and an
overlay to explain variant and rarity decisions.

## Safety Boundaries

- Keep the app passive. Do not automate gameplay, inject input, read game
  memory, bypass security, or require root.
- Do not add behavior that violates Pokemon GO platform security or fair-play
  boundaries.
- Do not touch `local.properties`, signing keys, telemetry keys, `.env` files,
  keystores, or other secrets.
- Do not run release builds unless explicitly requested.
- Work autonomously and ask only if blocked by credentials, destructive
  actions, or device-only behavior.

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

## Important Classes

- `ScreenCaptureService`
- `OverlayService`
- `ScanManager`
- `OCRProcessor`
- `TextParser`
- `SpeciesRefiner`
- `ScanConsistencyGate`
- `VisualFeatureDetector`
- `VariantDecisionEngine`
- `Phase2VariantClassifier`
- `RarityCalculator`
- `ScanTelemetryCoordinator`

## Verification Commands

Use these checks for normal debug validation:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Do not run release builds unless the user explicitly requests them.

## Working Rules

- Prefer small, focused changes that match the existing Kotlin and Android
  architecture.
- Read current signatures and call sites before changing scanner pipeline,
  lifecycle, OCR, vision, scoring, or telemetry code.
- Do not commit local editor settings, temporary payload files, generated APKs,
  telemetry payloads, screenshots, or device-specific artifacts.
- Treat telemetry payloads as privacy-sensitive even when they do not contain
  obvious secrets.

## Antigravity and Agent Skills Routing

Before non-trivial work, read `.agents/skills/using-agent-skills/SKILL.md` if
present. Then read the skill matching your task type:

- **Planning**: `.agents/skills/planning-and-task-breakdown/SKILL.md`
- **Implementation**: `.agents/skills/incremental-implementation/SKILL.md`
- **Behavior changes / bug fixes**: `.agents/skills/test-driven-development/SKILL.md`
- **Build / test failures**: `.agents/skills/debugging-and-error-recovery/SKILL.md`
- **Before every commit / push**: `.agents/skills/code-review-and-quality/SKILL.md`
  and `.agents/skills/git-workflow-and-versioning/SKILL.md`
- **Telemetry, paths, secrets, exported components, permissions, or user data**:
  `.agents/skills/security-and-hardening/SKILL.md`
- **OCR / vision / performance / memory work**: `.agents/skills/performance-optimization/SKILL.md`
- **Architecture / design docs**: `.agents/skills/documentation-and-adrs/SKILL.md`
- **External framework / library / API decisions**: `.agents/skills/source-driven-development/SKILL.md`
- **Refactors**: `.agents/skills/code-simplification/SKILL.md`
- **Context handoff**: `.agents/skills/context-engineering/SKILL.md`

Project safety rules always override generic skill guidance.
