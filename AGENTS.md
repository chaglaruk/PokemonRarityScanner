# AGENTS.md

## Project

PokemonRarityScanner is a passive Android/Kotlin scanner for Pokemon GO collection screens.

The current primary engineering objective is reliable, practical, automatic Pokemon species recognition on real Pokemon GO screens.

## Active Authority

For recognition work, use this priority order:

1. the user's current instructions;
2. this `AGENTS.md`;
3. `docs/CURRENT_RECOGNITION_MISSION.md`;
4. the live repository state, executable behavior, real-device evidence and measured results;
5. older plans, handoffs, AI reports and phase documents as historical engineering evidence only.

Previous roadmaps, PR phases, Manual Gates, handoff documents, implementation plans, AI reports and architecture decisions are not execution constraints for the current recognition-recovery mission unless the user explicitly says otherwise.

Do not preserve an existing architecture merely because it was previously documented.

## Engineering Freedom

Diagnose the actual system and choose the strongest solution supported by evidence.

Recognition-related architecture may be redesigned or replaced when justified, including:

- crop and geometry logic;
- preprocessing and scaling;
- OCR strategy and OCR provider;
- ML Kit usage;
- candidate generation and parsing;
- evidence fusion and confidence logic;
- visual recognition and local recognition models;
- frame fusion and temporal evidence;
- validation infrastructure;
- relevant dependencies.

Large or cross-cutting changes are allowed when they are the strongest coherent solution. Do not artificially split a fix into small patches because an older workflow required it.

Generic repo skills and historical workflow documents may provide useful techniques, but they must not reintroduce superseded scope, approval, PR-size, roadmap or gate restrictions.

Prefer experiments, implementation and quantitative validation over additional planning documents.

## Development Internet Access

Development-time internet access is allowed when it materially helps solve, validate or research the engineering problem. This includes:

- official documentation;
- library and platform documentation;
- dependency research;
- public technical references;
- public competitor behavior and product research;
- public papers, repositories and implementation techniques;
- downloading legitimate development dependencies or tools where appropriate.

Do not treat previous `no network calls` wording as a ban on the development agent using the internet.

Keep a strict distinction between development research and application runtime behavior.

Do not add runtime transmission of Pokemon GO screenshots, OCR text, scan content, user data or device data to external services merely because development internet access is allowed. Any new runtime network dependency that handles scan/user data requires explicit user approval and a privacy/security review.

Public internet research must not use private Pokemon GO APIs, account scraping, proprietary datasets, leaked code, credentials or unauthorized access.

## Real Device — User-Authorized QA Policy (2026-10-26)

The user may disconnect the Samsung Galaxy S25 and connect the Pixel 4a during a long-running engineering session. **Do not assume that the S25 must be connected.** On the next device-relevant engineering step, run `adb devices -l`, identify the authorized connected physical device, and target it explicitly with `adb -s <serial>` when multiple devices are present.

**Pixel 4a is the preferred, disposable scanner test device.** The user explicitly authorizes hands-on Android QA on the Pixel, including:

- building, installing, updating and uninstalling PokemonRarityScanner debug/test APKs;
- clearing or replacing **PokemonRarityScanner's own** data and History on the Pixel, if the experiment requires it;
- adjusting the scanner's permissions, using ADB diagnostics, launching the scanner and running appropriate app-owned instrumentation;
- running genuine end-to-end scanner tests where access and consent permit: capture → OCR → species/CP/HP/IV decision → overlay → History;
- reproducing bugs, repeating captures and collecting bounded local performance/accuracy evidence.

**Strict isolation boundary:** The Pokemon GO application **must remain installed**, and its app data, account, login, game state and progression must be preserved. Do not uninstall, clear, reset, reinstall or otherwise alter Pokemon GO or other unrelated apps. Keep the scanner passive: do not automate gameplay, inject game inputs, access the game's process memory, bypass security or use private game APIs. Use explicit Android screen-capture consent; avoid disclosing private screenshots/OCR.

**Samsung Galaxy S25:** It may also be connected and used for nondestructive diagnostic/validation work, but do not clear its app data, uninstall or overwrite existing user installations without further authorization.

The Pixel's *scanner* installation/data no longer need to be protected as an immutable fixture. Do not skip legitimate Pixel testing merely because previous scanner instrumentation accidentally removed that package: reinstalling/resetting **the scanner on the Pixel** is now explicitly authorized. This does not authorize changing Pokemon GO.

This is standing user authorization for future development sprints. During an already-running session, if this updated file becomes visible, incorporate it at the next natural device-testing opportunity **without abandoning ongoing useful work**. If the Pixel is disconnected, proceed with other engineering tasks and check again when device validation becomes relevant. Record exactly what was physically tested; emulator and fixture results are not equivalent to real-game capture.

## Product and Safety Boundaries

Keep the app passive.

Do not:

- automate Pokemon GO gameplay;
- inject input;
- access Pokemon GO process memory;
- require root;
- bypass platform or game security;
- use private Pokemon GO endpoints;
- access, automate or scrape a Pokemon GO account;
- copy proprietary code, assets, private data or thresholds from competing products.

Preserve explicit MediaProjection consent and overlay safety controls.

Do not expose screenshots, raw OCR, local paths, secrets or sensitive payloads through telemetry or release-visible logs.

Do not access or modify signing keys, keystores, credentials, `.env` secrets, sensitive `local.properties` values or unrelated user configuration.

Do not publish or run a release build unless explicitly requested.

Do not commit screenshots, device artifacts, build outputs, APKs, AABs, credentials or sensitive local evidence.

Preserve unrelated user changes.

## Recognition Correctness

A confidently accepted wrong species is a critical failure.

Prefer explicit uncertainty when evidence is genuinely insufficient. However, excessive rejection is also a product failure. The scanner must achieve useful automatic recognition coverage on normal supported Pokemon GO screens.

Do not fabricate ground truth. Do not use the system being evaluated as the sole authority for its own ground truth.

Use trustworthy development data and preserve an independent holdout for final validation when feasible.

Manual species identification should be an exceptional fallback for genuinely ambiguous cases, not the normal recognition workflow.

## Competitive Product Target

Poke Genie and Calcy IV are practical user-experience benchmarks, not claims of literal 100% accuracy.

The expected product experience is that normal supported Pokemon GO screens are identified automatically and reliably enough that the user does not routinely need to tell the scanner which Pokemon is visible.

Research public competitor behavior when useful, but independently implement all solutions.

## Verification

Run tests appropriate to the changes. Existing Android commands include:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Compilation and unit tests alone do not establish recognition correctness.

Where possible, validate recognition end-to-end using real screenshots and the connected physical device.

Measure at minimum:

- species accuracy;
- confidently wrong results;
- uncertain rate;
- usable coverage;
- relevant latency and performance;
- major failure categories.

## Autonomy

Investigate and execute independently.

Do not stop merely because a historical roadmap, Manual Gate, PR phase or previous AI-generated document says work is blocked if the evidence can now be obtained safely another way.

When a failure is found, reproduce it, identify the earliest incorrect stage, form hypotheses, run controlled experiments, implement the strongest candidate, retest and inspect regressions.

If a genuine external dependency remains, complete all independent work first and request only the minimum specific user action needed.

## Resource-Safe Local Engineering (2026-10-29)

The development laptop has 16 GB RAM and limited local build capacity. Resource exhaustion that makes Windows unresponsive or fills the system drive is an engineering failure, even when tests succeed.

- Before **each** build, device test, corpus generation or workspace creation, record free physical RAM, free space on C:, active Gradle/Java/emulator processes and the expected maximum temporary output. Recheck during long work. Do not assume yesterday's free space remains available.
- Stop starting new heavy work when available RAM is below 3 GiB or C: free space is below 40 GiB. Investigate existing task-owned processes and generated artifacts first; never terminate unrelated user processes or delete user data. If a required build cannot run within this budget, explain the blocker instead of pushing the machine into paging or disk exhaustion.
- Run at most **one** local Gradle build or Android instrumentation job at a time, without parallel heavyweight agents. Default Gradle flags: `--no-daemon --no-parallel --max-workers=2 --console=plain`. Bound JVM/Gradle heap for the actual build environment and validate memory before increasing it.
- Prefer the connected physical Pixel 4a over starting an emulator. Do not launch multiple emulators or create additional full repository copies/worktrees unless the need and available space are measured. Reuse one existing isolated QA workspace.
- Keep each run's temporary screenshots, logs, Gradle reports and test outputs bounded; prefer aggregate summaries and a small explicitly chosen failure sample. Do not create whole-corpus duplicate exports by default. Clean up **only** verified, task-created disposable artifacts after preserving needed evidence. Never run broad deletion commands against user folders, repository fixtures, game data, Gradle user caches or unknown worktrees.
- Record peak RAM, remaining disk space, number of local build/test processes, task-created artifacts, and what was cleaned up in the final report.

## Real-Game QA Versus Fixture Replay (2026-10-29)

Offline screenshot replay on a physical Pixel is valid, repeatable Android OCR evidence, but **is not** a live Pokemon GO scanner test. Label the evidence explicitly as fixture replay, component instrumentation, or genuine end-to-end live scan.

When the user has manually placed Pokemon GO on a suitable Pokemon detail screen and granted Android MediaProjection consent, prioritize a bounded live test on the Pixel: capture -> OCR -> species/CP/HP/IV decision -> overlay -> History. Exercise several distinct species and at least one uncertain/negative screen, record source/APK SHA and field-level outcomes, and preserve privacy. Compare with the Samsung S25 when connected and safe. Do not automate or modify Pokemon GO gameplay/account/data. If user action is indispensable, ask only for the minimal concrete action after completing independent work.

A passing fixture benchmark or one successful scan does not establish Calcy IV-level real-world recognition. Report correct automated accepts, confidently wrong accepts, uncertain/retries, coverage, and request-to-visible-result latency separately.
