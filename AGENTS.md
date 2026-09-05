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

## Real Device

A physical Samsung Galaxy S25 may be connected through ADB and is authorized for recognition engineering.

When available, use it where useful for:

- debug builds and installation;
- launching and testing PokemonRarityScanner;
- MediaProjection experiments;
- genuine screenshot capture;
- crop and OCR-input inspection;
- native-resolution experiments;
- recognition reproduction and validation;
- latency, memory and performance measurements.

Do not declare historical physical-device evidence missing without first checking whether the required evidence can now be obtained from the connected device.

If a physical Pokemon GO interaction is required and cannot be performed autonomously, complete all independent work first and request only the smallest specific user action needed.

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
