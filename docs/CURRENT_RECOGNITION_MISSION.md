# PokemonRarityScanner Current Recognition Mission

**Status:** ACTIVE

This document defines the current recognition-recovery mission. It supersedes previous recognition roadmaps, PR phase gates, Manual Gate sequencing and AI-generated implementation constraints as execution policy. Older documents remain valuable historical evidence.

## Why this mission exists

PokemonRarityScanner still does not provide sufficiently reliable automatic Pokemon species recognition in real use.

Much of the existing architecture, debugging, roadmap design and process policy was developed while working with GPT-5.6 Sol. That work produced useful evidence and fixed real defects, especially unsafe fuzzy species acceptance, but its conclusions, architecture and process should not be treated as optimal by default.

The next engineering agent should independently re-audit the system from first principles rather than merely continue the previous plan.

## Product objective

Make PokemonRarityScanner reliably identify the Pokemon shown on normal supported Pokemon GO screens without routinely requiring the user to identify the species manually.

Poke Genie and Calcy IV are practical product-experience benchmarks. This is not a claim that either competitor achieves literal 100% recognition accuracy. The relevant benchmark is that mature IV scanners normally provide useful automatic species recognition during ordinary use.

A local/manual validation website or repeated manual species confirmation is not an acceptable normal product workflow. Manual confirmation should be reserved for genuinely ambiguous cases or trustworthy ground-truth creation.

## Core quality target

A confidently accepted wrong species is a critical failure.

The system should prefer explicit uncertainty when evidence is genuinely insufficient, but a scanner that returns `Uncertain` for a large proportion of ordinary supported screens is also unsuccessful.

Optimize for both:

1. extremely low confidently-wrong species rate;
2. high practical automatic coverage.

## Known historical evidence

Treat these as clues, not assumed root causes:

- Real-world species recognition remains insufficiently reliable.
- OCR has produced incomplete, corrupted and ambiguous names.
- Older fuzzy matching could confidently choose wrong species.
- The deterministic species decision layer was hardened and achieved zero accepted-wrong results in its characterization corpus.
- Production OCR historically retained a `baseline_900_width` policy.
- A Samsung S25 1080-wide development corpus exists.
- Historical native-resolution / 1440 evidence and controlled OCR-policy comparisons were incomplete.
- Manual Gate A remained open.
- A larger local screenshot corpus historically contained roughly 730 source screenshots.
- A 120-candidate manual-validation workflow was created because trustworthy recognition/ground truth had not been established sufficiently.
- The latest Manual Gate tooling intentionally represented candidates as UNKNOWN rather than fabricating truth.
- Some historical fixtures lacked reliable truth and some were corrupt.
- Previous process became highly conservative and may have shifted engineering effort from solving recognition toward validation infrastructure.
- ML Kit has been the primary OCR provider, but it is not an immutable requirement.
- Previous work intentionally prevented weak visual classifiers from becoming authoritative global species identity.

## Independent diagnosis first

Before making broad changes, inspect the current live repository and reconstruct the actual end-to-end recognition path:

```text
screen capture
-> region/crop selection
-> geometry normalization
-> preprocessing/scaling
-> OCR and/or visual extraction
-> text parsing
-> species candidate generation
-> auxiliary evidence
-> evidence fusion/refinement
-> confidence
-> final species decision
-> user-visible result
```

For representative failures, identify the earliest stage where useful information is lost or corrupted.

Do not assume OCR is the root cause merely because the final result is wrong.

Inspect real screenshots and intermediate crops visually when possible. Compare:

- what is visible to a human;
- what region the scanner extracts;
- preprocessed and scaled images;
- recognizer input;
- OCR/visual output;
- parser output;
- generated candidates;
- auxiliary evidence;
- confidence and final decision.

Cluster failures by root cause instead of patching individual Pokemon unless the issue is genuinely species-specific.

## Engineering freedom

The current recognition architecture is open to change.

The agent may investigate and, when supported by measurements, change or replace:

- crop detection and anchor logic;
- geometry normalization;
- scaling policy;
- image preprocessing;
- full-resolution OCR;
- multiple OCR passes or OCR consensus;
- OCR provider;
- parser and candidate generation;
- CP, HP, candy-family, moves or appraisal evidence;
- screen-state recognition;
- visual species features;
- local embeddings or lightweight local vision models;
- template/feature matching where appropriate;
- temporal evidence across frames;
- evidence fusion;
- confidence calibration;
- validation/test infrastructure;
- relevant development/runtime dependencies.

These are possibilities, not prescribed solutions.

Do not preserve ML Kit, `baseline_900_width`, current crop geometry, current confidence logic or previous recognition architecture solely because earlier documents selected them.

Do not weaken fail-closed behavior merely to improve headline accuracy.

## Development internet access

The engineering agent may use the public internet when useful to solve the problem.

Appropriate uses include:

- official Android, ML Kit and library documentation;
- public technical references and papers;
- dependency and OCR/vision-engine research;
- public GitHub repositories and examples;
- public competitor behavior and documentation;
- legitimate development tools and dependencies.

Internet research is not permission to move user/scan data into cloud services.

Do not use private Pokemon GO endpoints, account scraping, leaked/proprietary code, unauthorized datasets, credentials or security bypasses.

Any new runtime architecture that would transmit Pokemon GO screenshots, OCR text, scan content, user data or device data to an external service requires explicit user approval and privacy/security review before implementation.

A local/offline solution remains preferred when it can meet the product target, but local-only implementation is not a dogma that should prevent researching better engineering approaches.

## Physical Samsung Galaxy S25

A physical Samsung Galaxy S25 may be connected to the development machine through ADB.

When available, it is authorized for:

- detecting device resolution/density/insets;
- building and installing debug versions of PokemonRarityScanner;
- launching and inspecting the app;
- genuine MediaProjection experiments;
- collecting development screenshots and diagnostic crops;
- native-resolution recognition experiments;
- controlled OCR comparisons;
- reproduction of real recognition failures;
- latency, memory and performance measurements;
- verification of fixes on the real device.

Do not stop merely because historical documentation says physical-device evidence is missing. Check whether it can now be obtained from the connected device.

If a physical action inside Pokemon GO cannot be performed autonomously, complete every independent task first and ask the user only for the smallest concrete action needed.

## Data and ground truth

Locate and assess the existing screenshot corpus and fixtures.

Determine which items are:

- trustworthy development data;
- independently/human-verified truth;
- unlabeled or ambiguous;
- corrupt/unusable;
- appropriate for development;
- appropriate to quarantine as final holdout.

Do not fabricate ground truth.

Do not use the recognizer being evaluated as the sole authority for its own labels.

At the same time, do not require unnecessary manual review for cases whose identity can be established safely from independent reliable evidence.

Reserve user verification for genuinely unresolved truth cases.

## Experimentation

Do not stop at code inspection or produce another plan as the primary deliverable.

Establish a measurable baseline and run experiments.

Where useful:

- build the Android app;
- run existing tests;
- use ADB and the connected S25;
- inspect real screenshots/crops;
- compare native/full resolution with current scaling;
- compare preprocessing strategies;
- compare OCR/recognition strategies;
- measure failure categories;
- measure latency and memory;
- test candidate architecture changes;
- preserve an untouched holdout for final evaluation.

For competing approaches, record at least:

- end-to-end species accuracy;
- confidently wrong species rate/count;
- uncertain/rejection rate;
- usable automatic coverage;
- relevant latency/performance;
- major failure classes.

A system that reports zero wrong results because it rejects an impractical share of scans does not meet the product objective.

## Competitor research

Publicly research Poke Genie, Calcy IV and other mature IV scanners when useful.

The purpose is to understand the product capability gap and discover public engineering approaches, not to reproduce proprietary internals.

Do not copy proprietary code, assets, private datasets or unpublished thresholds.

Ask:

> What capability allows mature scanners to provide useful automatic species identification during ordinary gameplay that PokemonRarityScanner currently lacks?

## Safety boundaries

The scanner must remain passive.

Do not:

- automate gameplay;
- inject input;
- read Pokemon GO process memory;
- require root;
- bypass game/platform security;
- use private Pokemon GO APIs/endpoints;
- log into, automate or scrape a Pokemon GO account.

Preserve MediaProjection and overlay consent/safety controls.

Do not expose screenshots, raw OCR, scan content, secrets or sensitive paths through telemetry or release-visible logging.

## Autonomy and stopping rules

Work through the recognition problem end-to-end.

For failures:

1. reproduce;
2. find the earliest incorrect stage;
3. form hypotheses;
4. run controlled experiments;
5. implement the strongest candidate;
6. retest;
7. inspect regressions;
8. continue.

Do not stop because an older roadmap, Manual Gate or PR phase labels work as blocked if the needed evidence can now be obtained safely by another method.

Only stop for a genuine external dependency that cannot be resolved with the repository, local files, development internet access, connected phone, ADB or independent experimentation.

Examples include:

- a required physical Pokemon GO action the agent cannot perform;
- unavailable original source data;
- genuinely ambiguous human ground truth;
- unavailable permissions or credentials;
- unavailable hardware.

Complete all independent work before asking for user action.

## Completion standard

Do not claim success because the app builds or unit tests pass.

At completion, report:

### Root causes
What actually prevented reliable automatic recognition?

### Architecture
What recognition strategy now exists and why?

### Changes
What was changed?

### Rejected approaches
What was tested and not good enough?

### Quantitative results
Before/after measurements for species accuracy, confidently wrong results, uncertain rate, automatic coverage and relevant performance.

### Real-device validation
What was tested on the Samsung Galaxy S25?

### Failure analysis
Which cases remain and why?

### Competitive gap
How close is the user experience to mature scanners such as Poke Genie / Calcy IV?

### Remaining dependency
What, if anything, genuinely requires human input?

### Recommendation
Is recognition production-ready, still an engineering problem, or blocked by a demonstrated external/technical constraint?

Optimize for solving the product problem with real evidence, not for preserving the previous roadmap.
