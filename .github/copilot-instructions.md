# PokemonRarityScanner Repository Guidance

## Active Recognition Mission

For recognition engineering, follow this authority order:

1. current user instructions;
2. root `AGENTS.md`;
3. `docs/CURRENT_RECOGNITION_MISSION.md`;
4. live repository state and measured evidence.

Older implementation plans, PR phases, Manual Gates, AI run reports, handoffs and historical architecture documents are evidence, not current execution authority.

Do not stop merely because a historical plan says a phase is blocked when the needed evidence can now be obtained through the available repository, local data, internet research or connected physical device.

Do not require adherence to old small-patch, manager-approved-scope or fixed-pipeline rules during the current recognition mission.

## Repository State

Use live `origin/main` and the current worktree as the source of truth for code state.

Preserve unrelated user changes. Do not reset, clean, stash, delete, stage or modify unrelated work merely to obtain a clean worktree.

When repository state creates a genuine risk of overwriting unrelated changes, avoid those files and continue independent work where possible.

## Engineering Scope

Recognition architecture may be changed when evidence supports it, including OCR provider, crop geometry, scaling, preprocessing, candidate generation, evidence fusion, visual recognition, local models, confidence logic and validation infrastructure.

The goal is practical real-world recognition quality, not preservation of the previous architecture.

## Development Internet Access

Internet access for development research is allowed when useful for solving the problem, including official documentation, public technical material, dependency research, public repositories, papers and public competitor behavior.

This permission is for engineering research and tooling. It does not authorize the application to transmit Pokemon GO screenshots, OCR text, scan content or user/device data to external services at runtime.

Any proposed runtime external service that would receive scan or user data requires explicit user approval and privacy/security review before implementation.

Never use private Pokemon GO endpoints, account scraping, leaked/proprietary code, unauthorized datasets, credentials or security bypasses.

## Product Boundaries

- Passive scanning only.
- No gameplay automation.
- No input injection.
- No game-memory access.
- No root requirement.
- No security bypass.
- No private Pokemon GO endpoints.
- No Pokemon GO account login, automation or scraping.
- Preserve explicit MediaProjection consent and overlay safety controls.

## Privacy And Secrets

- Telemetry must not expose screenshots, raw OCR, local paths, secrets or sensitive scan payloads.
- Do not weaken privacy, consent or local-data safeguards.
- Do not access or modify signing keys, keystores, credentials, `.env` secrets, sensitive `local.properties` values or unrelated user settings.
- Do not commit APKs, AABs, build outputs, screenshots, telemetry payloads, device artifacts or sensitive local evidence.
- Do not publish or run release builds unless explicitly requested.

## Recognition Correctness

A confidently accepted wrong species is a critical failure.

Fail closed when evidence is genuinely insufficient, but do not optimize correctness by rejecting an impractical proportion of normal scans.

Do not fabricate ground truth or infer truth from corrupt/untrustworthy fixtures. Do not use the recognizer under test as the sole authority for its own labels.

Manual identification should remain an exceptional fallback rather than the normal product workflow.

Poke Genie and Calcy IV may be used as public user-experience benchmarks. Do not copy proprietary code, assets, private data or thresholds.

## Connected Device

A Samsung Galaxy S25 may be connected over ADB and is authorized for debug builds, installation, screenshot/crop inspection, MediaProjection experiments, native-resolution OCR tests, recognition validation and relevant performance measurements.

Check for and use the connected device before declaring historical real-device evidence unavailable.

## Verification

Use the narrowest useful validation first, then broader validation as needed. Existing commands include:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Compilation is not recognition validation. Prefer end-to-end measurements on trustworthy real screenshots and the physical device where possible.

Track species accuracy, confidently wrong results, uncertain rate, usable coverage, latency/performance and failure categories.

## Reporting

Prefer implementation, experiments and measured evidence over new process documents.

When genuinely blocked by something outside the available environment, finish all independent work first and request only the minimum concrete user action needed.
