# PokémonRarityScanner — Authoritative Codex Implementation Plan

**Prepared:** 17 July 2026<br>
**Repository:** `https://github.com/chaglaruk/PokemonRarityScanner`<br>
**Audit baseline:** `origin/main @ c577aac157fa9999f5ff32e67a038979932981ce`<br>
**Planned repository path:** `docs/POKERARITY_IMPLEMENTATION_PLAN.md`

> The latest GitHub `origin/main` remains authoritative. Before every task, re-read this plan from GitHub and compare the current `main` SHA with the SHA recorded in the relevant phase.

---

## 1. Executive decision

The audit is valuable, but its final one-line recommendation is too narrow.

`SpeciesRefiner.directMatchBlock` is a critical defect because it treats any `parseName()` result as if it were an exact authoritative name and then prevents all replacement when candy and move evidence are absent. However, changing only that block would expose the pipeline to other weak authorities, including profile ranking and candy overrides. It also would not stop `TextParser` and `OCRProcessor` from confidently accepting wrong fuzzy species in the first place.

The safe implementation order is therefore:

1. establish executable Kotlin measurements and honest fixture integrity;
2. introduce one fail-closed species-name decision contract;
3. make locks and overrides depend on decision provenance and corroboration;
4. harden consistency, confidence and early-exit behavior;
5. remove visual classifiers as a source of global species identity;
6. change crop geometry and image scaling only through measured A/B work;
7. build an end-to-end labeled holdout gate;
8. complete release logging/privacy hardening and a signed-release MobSF rescan.

Primary invariant:

```text
A confidently accepted wrong species must be impossible in every executable
deterministic test corpus. When evidence is insufficient, return Uncertain or
request user confirmation rather than silently choosing another species.
```

---

## 2. Live repository baseline

At the time this plan was prepared:

- authoritative `main`: `c577aac157fa9999f5ff32e67a038979932981ce`;
- commit message: `Fix MobSF PowerShell parser error`;
- no open pull requests were returned by the connected GitHub repository;
- current `AGENTS.md` requires small reviewable patches, passive behavior, no new network calls, no release builds during ordinary validation, no secrets, and preservation of privacy/consent/MediaProjection boundaries;
- `docs/POKERARITY_IMPLEMENTATION_PLAN.md` did not yet exist.

Before PR-00, verify this baseline again. If `main` changed, compare the changed files with every allowed-scope list below before continuing.

---

## 3. Evidence assessment

### Confirmed and implementation-relevant

1. Clean canonical names are handled correctly in the audit port: 1011/1011.
2. Final-character truncation produced accepted-wrong results:
   - final minus one: 57/993;
   - final minus two: 149/982.
3. Confirmed adversarial wrong selections include:
   - `Nidoran` → `Nidorina`;
   - `HoOh` → `Hoopa`;
   - `Poliwrat` → `Poliwag`;
   - `metapo` → `Metang`.
4. Pure alphabetic nicknames were accepted as species candidates.
5. Four-digit suffix handling is inconsistent because the exact suffix rule supports only one to three digits.
6. Dynamic and static OCR paths disagree for digit-containing observations.
7. `rankNameCandidates()` sorts by score, distance and then shorter name.
8. `scoreCandidate()` can saturate wrong fuzzy candidates near 1.0.
9. `SpeciesRefiner.directMatchBlock` has no bypass.
10. Three Mewtwo PNG fixtures are irrecoverably corrupt as stored.
11. Twenty-eight of forty-seven fixtures have null expectations.
12. Species name and candy crop handling are not consistently anchor-corrected.
13. OCR frames wider than 900 pixels are downscaled before OCR.
14. Visual variant logic depends heavily on the selected species and can also write species through classifier override paths.
15. The scanned MobSF binary was a debug APK, so debug certificate and debuggable findings are not evidence of a release vulnerability.

### Important limitations and corrections

1. The matcher experiment used a Python port. Android/JVM production tests were not executed during the audit. The first code PR must reproduce the measurements in Kotlin before changing behavior.
2. The substitution result was 0.33%, not the earlier 9.3% claim.
3. Five previously claimed mappings were not reproduced. They must not become regression expectations.
4. The audit's suggested blanket rule “distance 1 is accepted” is unsafe for short names and canonical collisions. No implementation may use that blanket rule.
5. The 900-pixel downscale exists, but its actual ML Kit accuracy impact was not measured. Pixel-area reduction alone is not an OCR accuracy benchmark. The audit incorrectly references Tesseract in places even though the app uses ML Kit.
6. The visual audit is primarily a source audit. It did not deliver a current executable top-k benchmark over an independent holdout. Visual threshold lowering is therefore not approved.
7. Corrupt PNG bytes cannot be repaired by an agent. They must be removed from the active corpus or replaced using original/re-captured images.
8. Ground-truth labels must not be guessed by Codex. Manual labels require user verification from the actual Pokémon GO screen.
9. The security report under-classified MobSF's displayed high findings. They are expected debug-only findings, not absent findings. A release APK rescan is still required.
10. Remote model updating is a product/maintenance topic, not a security remediation, and is outside this plan.

---

## 4. Product invariants

Every PR must preserve:

- passive operation only;
- no gameplay automation, input injection, root, game-memory access or private endpoints;
- explicit MediaProjection and overlay consent;
- telemetry disabled by default and gated by explicit opt-in;
- no raw OCR, screenshots, local paths or secrets added to telemetry;
- no committed APK, report bundle, keystore, `local.properties`, `.env` or signing material;
- no release build unless the user explicitly starts the release-verification phase;
- no lint/detekt baseline regeneration to hide new findings;
- no UI redesign during recognition work;
- no proprietary Calcy code, assets, data or thresholds;
- one behavioral concern per PR;
- no merge until required CI and review gates are complete.

---

## 5. Pull-request dependency graph

```text
PR-00 Plan publication
   |
   v
PR-01 Executable measurement + fixture integrity
   |
   +----------------------+
   |                      |
   v                      v
Manual fixture truth   PR-02 Fail-closed name decision
recovery/labeling          |
                           v
                    PR-03 Refiner authority hardening
                           |
                           v
                    PR-04 Consistency/confidence/early-exit
                           |
                           v
                    PR-05 Visual species-authority containment
                           |
                           v
                    PR-06 Geometry and OCR-scale experiment
                           |
                           v
                    PR-07 End-to-end holdout accuracy gate
                           |
                           v
                    PR-08 Logging/privacy/release hardening
                           |
                           v
                    PR-09 Signed release verification + MobSF
```

PR-02 may begin after PR-01 even if manual labeling is not finished. PR-06 and PR-07 cannot be accepted without real-device/fixture evidence.

---

# 6. PR specifications

## PR-00 — Publish the authoritative plan

**Branch:** `docs/pokerarity-implementation-plan`<br>
**Recommended model:** capable Codex coding model<br>
**Effort:** Medium<br>
**Expected token intensity:** Low<br>
**Session:** Fresh

### Objective

Add this document at:

`docs/POKERARITY_IMPLEMENTATION_PLAN.md`

Also add a short pointer in `docs/AI_RUN_REPORT.md` or the existing roadmap index only when an appropriate existing index is present.

### Allowed scope

- `docs/POKERARITY_IMPLEMENTATION_PLAN.md`
- one existing documentation index/pointer file
- `docs/AI_RUN_REPORT.md`

### Forbidden scope

All Kotlin, Gradle, workflow, asset and test files.

### Acceptance

- plan records the current `main` SHA;
- no competing implementation roadmap is presented as authoritative;
- documentation-only diff;
- `git diff --check` passes;
- CodeRabbit/Sonar review contains no unresolved actionable item.

### Rollback

Revert the documentation commit.

---

## PR-01 — Kotlin characterization benchmark and fixture integrity

**Branch:** `test/recognition-measurement-baseline`<br>
**Recommended model:** strongest available Codex coding model<br>
**Effort:** High<br>
**Expected token intensity:** Medium<br>
**Session:** Fresh

### Objective

Create executable production-language measurements before changing recognition behavior.

### Required work

1. Add deterministic JVM tests or a test-support harness that loads all canonical species.
2. Measure:
   - exact canonical;
   - final-one and final-two truncation;
   - deterministic deletion;
   - deterministic substitution;
   - transposition;
   - punctuation removal;
   - OCR glyph confusion;
   - one-to-four digit suffixes;
   - explicit adversarial observations.
3. Classify outputs as:
   - accepted-correct;
   - accepted-wrong;
   - uncertain/no-match.
4. Exclude canonical collisions and multi-source collisions transparently.
5. Add path-consistency characterization for:
   - `parseName`;
   - `parseStrongSpeciesName`;
   - ranked candidates;
   - OCR dynamic path;
   - OCR static path.
6. Add fixture integrity checks:
   - PNG magic;
   - decode success;
   - manifest entry points to a real file;
   - no silent fixture skip;
   - expectation completeness summary.
7. Remove the three corrupt Mewtwo files and their active manifest entries, or move them to an explicitly ignored quarantine location outside the active test corpus. Do not fabricate replacements.
8. Preserve the audit's raw baseline numbers in test output or a small checked-in baseline JSON generated by the Kotlin harness.

### Allowed scope

- relevant `app/src/test/` files;
- fixture integrity test support;
- active fixture manifest;
- deletion/quarantine metadata for the three corrupt fixture files;
- a small deterministic baseline JSON under test resources;
- a narrowly scoped analysis script only if the JVM test cannot produce the report.

### Forbidden scope

- production recognition behavior;
- `TextParser.kt`;
- `OCRProcessor.kt`;
- `SpeciesRefiner.kt`;
- visual classifier code;
- UI;
- Gradle baseline files.

### Acceptance

- canonical exact set remains 100% correct;
- current accepted-wrong counts are reported, not hidden;
- no random data or flaky ordering;
- three corrupt images are no longer silently considered usable;
- test output distinguishes “unlabeled” from “passed”;
- no invented ground truth;
- full debug validation passes.

### Commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TextParser*" --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:detekt --no-daemon --console=plain
.\gradlew.bat :app:lintDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
git diff --check
```

### Rollback

Revert the PR. No production behavior changes.

---

## Manual Gate A — Restore fixture truth

**Owner:** user with device/source images, assisted by the project chat<br>
**Codex role:** tooling only; never infer labels

### Required work

- Re-capture Armored/Lucky Mewtwo images from a real device or recover original binary files.
- Verify species, CP, HP, lucky, costume/form and date truth from the source screen.
- Label exploratory fixtures only when the actual screen truth is known.
- Record device model, resolution, Android version, Pokémon GO language, scroll state and capture timestamp.
- Split fixtures into:
  - development corpus;
  - immutable holdout corpus.
- Do not use the holdout to tune thresholds.

Target before PR-07:

- all active fixtures decodable;
- at least 90% of active fixtures have a species expectation;
- every holdout fixture has species truth;
- variants have explicit true/false/unknown fields rather than omitted ambiguity.

---

## PR-02 — Fail-closed species-name decision contract

**Branch:** `fix/fail-closed-species-name-decision`<br>
**Recommended model:** strongest available Codex coding model<br>
**Effort:** High<br>
**Expected token intensity:** High<br>
**Session:** Fresh

### Objective

Stop converting noisy OCR into a confidently accepted unrelated species.

### Design

Introduce one decision API with provenance:

```text
Accepted(
  species,
  source = ExactCanonical | ReviewedAlias | SafeFuzzy,
  diagnostics
)

Uncertain(
  candidates,
  diagnostics
)

NoMatch(
  diagnostics
)
```

Compatibility wrappers may return `String?`, but only `Accepted` may become a selected species.

### Required behavior

- exact canonical names remain accepted;
- reviewed aliases remain accepted;
- exact species plus one-to-four digit suffix is explicitly handled;
- `HoOh`, `Ho Oh` and `H0-0h` resolve through reviewed Ho-Oh normalization;
- plain `Nidoran` is uncertain unless explicit gender/canonical form evidence exists;
- `nidoran-f` and `nidoran-m` remain exact;
- pure alphabetic nickname-like non-exact inputs are uncertain without corroboration;
- `Poliwrat` may resolve to Poliwrath only when safe, otherwise uncertain; never Poliwag;
- `metapo` may resolve to Metapod only when safe, otherwise uncertain; never Metang;
- remove the shorter-name final preference;
- candidate ordering must prioritize edit distance and structure before saturated score;
- no blanket “distance one always accepted” rule;
- dynamic and static OCR paths call the same decision API;
- direct acceptance based only on score around 0.72 is removed;
- uncertain candidates remain available for diagnostics but selected species is null.

### Allowed scope

- `TextParser.kt`;
- `OCRProcessor.kt`;
- new decision/diagnostic model under the OCR package;
- directly relevant JVM tests.

### Forbidden scope

- `SpeciesRefiner.kt`;
- consistency/confidence gates;
- visual classifiers;
- crop geometry and image scaling;
- UI and telemetry schema.

### Acceptance

- exact canonical: 1011/1011;
- deterministic accepted-wrong count: zero for the approved collision-filtered corpus;
- adversarial wrong selections listed above are never accepted;
- legitimate recoveries lost to uncertainty are listed in PR description;
- dynamic/static outcome consistency is tested;
- baseline comparison is included.

### Rollback

Revert PR-02. PR-01 provides the pre-change baseline.

---

## PR-03 — SpeciesRefiner lock and override hardening

**Branch:** `fix/species-refiner-authority-contract`<br>
**Recommended model:** strongest available Codex coding model<br>
**Effort:** High<br>
**Expected token intensity:** High<br>
**Session:** Fresh

### Objective

Make species locks depend on decision provenance and corroboration rather than `parseName()` returning any string.

### Required work

1. Replace `directParsedSpeciesMatch` semantics with explicit decision provenance.
2. `directMatchBlock` may protect only:
   - exact canonical;
   - reviewed alias;
   - profile-consistent species.
3. A safe fuzzy result must not create an absolute lock.
4. `Uncertain` and `NoMatch` can never become:
   - trusted resolved species;
   - exact lock;
   - strong lock;
   - authoritative anchor.
5. Profile mismatch can open alternatives but may not by itself force a random global candidate.
6. `uniqueCandyOverride` and `candyFamilyAuthorityOverride` require:
   - reliable candy provenance;
   - species fit/corroboration;
   - meaningful score margin;
   - conflict diagnostics.
7. Wrong or weak candy may not override an exact canonical species.
8. Add explicit trace reasons for kept/replaced/uncertain outcomes.
9. Preserve current behavior for exact, profile-consistent scans.

### Allowed scope

- `SpeciesRefiner.kt`;
- `SpeciesRefinerConfig.kt`;
- narrowly required decision/diagnostic models introduced in PR-02;
- directly relevant refiner tests.

### Forbidden scope

- OCR crop/scale;
- frame fusion;
- confidence gate;
- visual classifiers;
- UI.

### Acceptance

Tests cover:

- wrong fuzzy current species plus impossible profile;
- exact canonical plus wrong candy;
- uncertain name plus correct candy;
- uncertain name plus wrong candy;
- Nidoran ambiguity;
- same-family drift;
- unique-candy false-positive resistance;
- no weak result becomes a lock.

### Rollback

Revert PR-03 while retaining PR-02.

---

## PR-04 — Consistency, confidence and early-exit hardening

**Branch:** `fix/species-confidence-and-early-exit`<br>
**Recommended model:** strongest available Codex coding model<br>
**Effort:** High<br>
**Expected token intensity:** High<br>
**Session:** Fresh

### Objective

Prevent field presence and repeated identical OCR errors from being mistaken for species correctness.

### Required work

- update authoritative-anchor logic to require exact/alias provenance, not generic `parseName()`;
- add structured species-decision status to consistency/confidence inputs;
- an impossible or strongly contradictory CP/HP/arc profile cannot receive `ACCEPT`;
- cross-family conflicts become `UNCERTAIN` or `RETRY`, not silent correction;
- early exit after two frames is blocked when:
  - species decision is fuzzy/uncertain;
  - top candidates are close;
  - profile evidence is missing or contradictory;
- detailed pass is requested when additional candy/move/profile evidence could resolve uncertainty;
- do not convert a weak visual match into species correctness;
- add traceable reason codes.

### Allowed scope

- `ScanFrameFusion.kt`;
- `ScanConsistencyGate.kt`;
- `ScanConfidenceGate.kt`;
- narrowly required orchestration in `ScanManager.kt`;
- relevant tests.

### Forbidden scope

- geometry/scale;
- visual feature algorithms;
- UI redesign;
- telemetry upload schema.

### Acceptance

- two identical wrong fuzzy frames cannot early-accept;
- exact/alias two-frame agreement still uses the efficient path;
- profile contradiction prevents high-confidence acceptance;
- conflict reasons are deterministic and tested;
- no raw OCR text is added to telemetry-safe reasons.

---

## PR-05 — Remove visual classifiers as global species authority

**Branch:** `fix/visual-variant-only-authority`<br>
**Recommended model:** strongest available Codex coding model<br>
**Effort:** High<br>
**Expected token intensity:** Medium<br>
**Session:** Fresh

### Objective

Limit visual classification to variant evidence within an already accepted species until an independent holdout proves global species accuracy.

### Required work

- visual classifier may not introduce or replace global species identity;
- remove or disable cross-family and unknown-species global override;
- species-scoped variant classification runs only after exact/alias/safely accepted species;
- uncertain species means visual results are diagnostics-only;
- retain shiny/costume/form logic only where existing tests prove bounded behavior;
- do not lower Phase2 thresholds;
- do not add remote model update behavior;
- add tests proving visual output cannot change selected species.

### Allowed scope

- `VariantDecisionEngine.kt`;
- `ScanAuthorityLogic` or equivalent authority helper;
- narrowly required visual merger tests;
- diagnostics.

### Forbidden scope

- visual threshold tuning;
- model JSON regeneration;
- new ML architecture;
- network/model downloads;
- broad visual detector refactor.

### Acceptance

- global classifier never writes species;
- variant decisions remain species-bounded;
- uncertainty remains uncertainty;
- no threshold is loosened without a measured independent holdout.

---

## PR-06 — Geometry and OCR-resolution controlled experiment

**Branch:** `experiment/anchored-native-ocr-crops`<br>
**Recommended model:** strongest available Codex coding model<br>
**Effort:** High<br>
**Expected token intensity:** High<br>
**Session:** Fresh

### Objective

Measure, then improve, name/candy crop geometry and OCR scaling without assuming that removing the 900-pixel cap is automatically better.

### Required work

1. Add a selectable internal OCR image policy:
   - current 900-pixel baseline;
   - native-resolution crop-first;
   - bounded crop upscaling.
2. Crop before resizing where technically safe.
3. Extend anchor correction to name and candy only after geometry tests establish expected behavior.
4. Keep CP/HP behavior unchanged unless the same PR contains separate measurements.
5. Produce per-policy fixture metrics:
   - OCR text;
   - species decision;
   - accepted-correct;
   - accepted-wrong;
   - uncertain;
   - processing time;
   - memory peak where measurable.
6. Run real-device comparisons for at least:
   - 1080-wide device;
   - 1440-wide device when available;
   - reference and shifted/scrolled card positions.
7. Select a new default only when it improves correct acceptance without increasing accepted-wrong.

### Allowed scope

- `ScanManager.kt`;
- `ScreenRegions.kt`;
- `ScreenGeometryBuilder.kt`;
- `ImagePreprocessor.kt`;
- OCR image-policy helper;
- geometry/preprocessing tests.

### Forbidden scope

- matcher thresholds;
- refiner authority;
- visual thresholds;
- UI redesign.

### Acceptance

- no accepted-wrong regression;
- selected policy has measured benefit;
- OOM/performance regression is bounded;
- fallback geometry is explicitly reported;
- no unmeasured “61% accuracy improvement” claim.

### Rollback

Switch default back to the preserved baseline policy or revert PR.

---

## PR-07 — End-to-end labeled holdout accuracy gate

**Branch:** `test/end-to-end-recognition-holdout`<br>
**Recommended model:** strongest available Codex coding model<br>
**Effort:** High<br>
**Expected token intensity:** Medium<br>
**Session:** Fresh

### Objective

Turn real screenshot correctness into a release gate.

### Prerequisite

Manual Gate A completed sufficiently for honest truth data.

### Required work

- strict active fixture manifest with explicit unknown values;
- immutable holdout separation;
- fixture integrity and truth-schema validation;
- field metrics for species, CP, HP and variants;
- no silent skip;
- no unlabeled sample counted as success;
- device/instrumentation test instructions;
- CI job when a supported emulator/managed device path is reliable;
- otherwise a required local device report artifact.

### Acceptance

- all active holdout images decode;
- all holdout species labels are explicit;
- accepted-wrong species count is zero;
- every skipped fixture fails the gate unless explicitly quarantined with reason;
- metrics are persisted as an artifact, not manually copied.

---

## PR-08 — Logging, clipboard and diagnostics privacy hardening

**Branch:** `security/logging-diagnostics-hardening`<br>
**Recommended model:** strongest available Codex coding model<br>
**Effort:** High<br>
**Expected token intensity:** Medium<br>
**Session:** Fresh

### Objective

Address the actionable source concerns behind the debug MobSF report without chasing debug-only or dependency false positives.

### Required work

- inventory app-owned `Log.*` calls and values;
- remove/redact raw OCR, local paths, upload identifiers, API-key material and server error bodies from release-visible logs;
- verify R8 removal covers all intended log methods or use a release-safe logger;
- review `SecurityAuditLogger` retention and content;
- review clipboard content and clear it after a reasonable lifecycle where supported;
- verify fixture/diagnostic exporters are disabled or inaccessible in release;
- verify shared/external writes are user-initiated and sanitized;
- add forbidden-token privacy tests;
- do not change minimum SDK solely for MobSF score;
- do not remove required MediaProjection/overlay permissions.

### Allowed scope

Logging abstraction, clipboard service, diagnostics exporters, release-specific manifest/rules and privacy tests.

### Forbidden scope

Recognition behavior, telemetry feature expansion, endpoint changes, signing secret values.

### Acceptance

- no secret/raw OCR/local path enters release logcat;
- diagnostics remain local and explicit;
- debug fixture tools remain unavailable in release;
- all security CI gates pass.

---

## PR-09 — Signed release verification and MobSF rescan

**Branch:** normally no code branch unless PR-08 reveals a required source fix<br>
**Recommended model:** project chat for coordination; Codex only for a validated source fix<br>
**Effort:** High<br>
**Expected token intensity:** Low<br>
**Session:** Fresh coordination session

### Objective

Verify the actual production artifact.

### Manual/local procedure

- use local signing credentials without exposing or committing them;
- build the signed release artifact only after explicit user approval;
- verify merged release manifest:
  - `debuggable=false`;
  - debug fixture receiver disabled;
  - expected exported components only;
- verify signing certificate is not the Android debug certificate;
- run MobSF against the signed release APK;
- classify every finding by exploitability;
- store sanitized report summary, not secrets or the signed APK, in the reviewed documentation PR.

### Release gate

- no debug certificate;
- no debuggable application;
- no unexpected exported debug component;
- no app-owned sensitive logging;
- no committed release artifact or secret;
- outstanding actionable high findings resolved or explicitly accepted by the user.

---

# 7. Required review and merge protocol

For every Codex implementation PR:

1. Start from freshly fetched `origin/main`.
2. Create one branch named in this plan.
3. Read current `AGENTS.md` and the live implementation plan.
4. Report dirty/untracked files before editing.
5. Write or update focused tests first.
6. Run focused tests.
7. Run full required checks once after focused tests pass.
8. Push branch and open a draft PR.
9. Include before/after metrics and lost-recovery cases.
10. Wait for:
    - Run Tests;
    - detekt;
    - Android Lint;
    - Semgrep;
    - CodeQL where triggered;
    - SonarQube;
    - CodeRabbit full review on the PR;
    - zero unresolved valid review threads.
11. Project chat independently reviews diff and CI.
12. User approves merge.
13. Squash merge.
14. Delete merged branch.
15. Create a separate documentation PR updating this plan:
    - completed phase;
    - merge SHA;
    - observed metrics;
    - deviations;
    - next phase.
16. Do not proceed while another implementation PR is open unless the scopes are provably independent and the user explicitly approves parallel work.

---

# 8. Model and prompt policy

Every agent prompt produced in the new project chat must begin with:

```text
Recommended model: <specific currently available model or strongest available class>
Effort: Low | Medium | High
Why: <one sentence>
Expected token intensity: Low | Medium | High
Session: Fresh | Continue current PR session
```

Rules:

- use the strongest available Codex coding model with High effort for recognition contracts, authority, fusion, geometry, privacy and concurrency;
- use Medium effort for focused tests or mechanical follow-up inside the same PR;
- use a fresh Codex session for every new PR;
- continue the existing session only for review fixes in the same PR;
- never send an entire multi-PR roadmap to Codex as one implementation task;
- prompts must list exact allowed and forbidden files;
- prompts must tell Codex to stop before editing when scope expansion is required;
- prompts must never instruct Codex to regenerate baselines.

---

# 9. Project source policy

Keep the uploaded audit files as dated evidence:

- `KIMI_MASTER_AUDIT.md`
- `KIMI_MEASUREMENTS.json`
- `KIMI_01_MATCHER_REPRODUCTION.md`
- `KIMI_02_CAPTURE_GEOMETRY_FIXTURE_AUDIT.md`
- `KIMI_03_SPECIES_AUTHORITY_AUDIT.md`
- `KIMI_04_VISUAL_CLASSIFIER_VALIDATION.md`
- `KIMI_05_SECURITY_RELEASE_AUDIT.md`
- `KIMI_06_INDEPENDENT_PLAN_CRITIQUE.md`
- `COMMAND_LOG.md`
- prior deep recognition report;
- MobSF debug report;
- handoff/research plan.

Do not repeatedly replace these files. They are point-in-time evidence.

The live GitHub versions of these files are authoritative for changing project state:

- `AGENTS.md`;
- `PROJECT_STATE.md`;
- `KNOWN_ISSUES.md`;
- `BACKLOG_NEXT.md`;
- current source and tests;
- `docs/POKERARITY_IMPLEMENTATION_PLAN.md`.

---

# 10. Immediate next action

1. Add this plan and all nine Kimi/OpenCode output files to the new ChatGPT Project.
2. Open a new project chat with the startup prompt supplied alongside this document.
3. The new chat must verify:
   - current `main` SHA;
   - open PRs;
   - latest relevant Actions results;
   - whether files targeted by PR-01 changed after `c577aac1`;
   - whether this plan already exists in GitHub.
4. After reporting the live baseline, create or coordinate PR-00.
5. After PR-00 is reviewed and merged, generate the PR-01 Codex prompt.
6. Do not begin with a direct one-line edit to `SpeciesRefiner.kt`.
