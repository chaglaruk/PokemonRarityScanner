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
PR-00 Plan publication (complete: 4f8dcc8)
   |
   v
PR-01 Executable measurement + fixture integrity (complete: cac4c8b)
   |
   +----------------------+
   |                      |
   v                      v
Manual fixture truth   PR-02 Fail-closed name decision (complete: 2ff0a5de)
recovery/labeling          |
                           v
                    PR-M1 Dependency-submission CI repair (complete: 468e9001)
                           |
                           v
                     PR-03 Refiner authority hardening (complete: 6e0580e6)
                            |
                            v
                     Security Gate D (complete: PASS)
                            |
                            v
                     PR-04 Consistency/confidence/early-exit (complete: 7404f3c0)
                            |
                            v
                     PR-05 Visual species-authority containment (complete: PR #36, 23ad338a)
                            |
                            v
                     PR-06 Geometry and OCR-scale experiment (incomplete: evidence-gated)
                            |
                            v
                     PR-07 End-to-end holdout accuracy gate (blocked: Manual Gate A)
                            |
                            v
                     PR-08 Logging/privacy/release hardening
                            |
                            v
                     PR-09 Signed release verification + MobSF
```

Manual Gate A remains open and may proceed in parallel. PR-M1 is an independent maintenance phase and does not renumber PR-00 through PR-09. PR-04 is complete; PR #33 was squash-merged with merge SHA `7404f3c001710d5129deeb0ec5596446b3f5f82e`. The implementation introduced structured species evidence across frame fusion, consistency, confidence and detailed-pass decisions. Exact canonical and reviewed alias evidence retain the efficient path when profile and required fields are compatible; fuzzy, uncertain, no-match, conflicting, close-candidate, missing-profile, contradictory, impossible and indeterminate evidence fail closed or request detailed processing. Cross-family conflicts retry instead of silently accepting or restoring a potentially wrong species. Weak visual evidence is not treated as global species correctness, and raw OCR is not exposed in telemetry-safe reason codes. PR-05 is complete through PR #36 (`23ad338aae2e0e77a9e422c7c0c5c49c553cb59f`). PR-06 remains incomplete and blocked on genuine native-1440 physical-device evidence, controlled OCR-policy comparison, and real-device memory/performance comparison; its completed Slice 1 harness and 1080-wide development corpus do not satisfy those gates. PR-07 remains blocked until Manual Gate A is sufficiently complete for confirmed truth and an immutable end-to-end holdout. AGP/Robolectric remediation remains a separate nonblocking maintenance stream.

### Execution status — PR-00 through PR-05 complete; PR-06 evidence-gated

#### PR-00 completion

- **PR:** #23
- **Purpose:** authoritative plan publication
- **Merge SHA:** `4f8dcc8afdb705301e328370ea7be973b444998f`
- **Result:** `docs/POKERARITY_IMPLEMENTATION_PLAN.md` became authoritative on main.

#### PR-01 completion

- **PR:** #24
- **Purpose:** Kotlin/JVM recognition characterization and active fixture integrity
- **Merge SHA:** `cac4c8b76c097263df954d8a1ffe92e3f58ca1dd`
- **Production behavior:** unchanged
- **Deterministic baseline SHA-256:** `F467112892EB50E373B023C67CD88712ACD699AF35ED25537AD7C36B4A2B6EC1`

Authoritative Kotlin/JVM results:

| Measurement | Evaluated | Correct | Wrong | Uncertain |
| --- | ---: | ---: | ---: | ---: |
| canonical `parseName` | 1011 | 1011 | 0 | 0 |
| final-one truncation | 1001 | 945 | 56 | 0 |
| final-two truncation | 979 | 811 | 167 | 1 |
| internal deletion | 1007 | 947 | 60 | 0 |
| deterministic substitution | 1003 | 980 | 23 | 0 |
| transposition | 968 | 870 | 98 | 0 |
| glyph confusion | 943 | 941 | 2 | 0 |
| four-digit/year suffix through `parseName` | 1011 | 964 | 39 | 8 |
| four-digit/year suffix through ranked candidates | 1011 | 924 | 47 | 40 |

- Dynamic/static policy-adapter disagreements: 4444 of 11016 compared observations.
- All four known adversarial wrong selections were reproduced: `Nidoran` → `Nidorina`, `HoOh` → `Hoopa`, `Poliwrat` → `Poliwag`, and `metapo` → `Metang`.

#### PR-02 completion

- **PR:** #26
- **Purpose:** fail-closed species-name decision contract and shared dynamic/static OCR selection
- **Merge SHA:** `2ff0a5deff3e2b1df3a29244214dfb1d28631c24`
- **Production result:** only explicit `Accepted` decisions may select species
- **SpeciesRefiner:** unchanged
- **Manual Gate A:** remains open
- **PR-03:** complete at `6e0580e6`

Authoritative Kotlin/JVM results:

| Family | Correct | Wrong | Uncertain |
| --- | ---: | ---: | ---: |
| exact canonical | 1011 | 0 | 0 |
| final-one truncation | 751 | 0 | 250 |
| final-two truncation | 0 | 0 | 979 |
| internal deletion | 522 | 0 | 485 |
| deterministic substitution | 923 | 0 | 80 |
| adjacent transposition | 14 | 0 | 954 |
| punctuation/spacing removal | 1 | 0 | 35 |
| glyph confusion | 804 | 0 | 139 |
| one-digit suffix | 1011 | 0 | 0 |
| two-digit suffix | 1011 | 0 | 0 |
| three-digit suffix | 1011 | 0 | 0 |
| four-digit/year suffix | 1011 | 0 | 0 |

- Accepted-wrong across every selectable characterization path: 0.
- Dynamic/static selected-result disagreements: 0 of 11016.
- Repeated deterministic report SHA-256: `347E0607547B04611AC2EDE5930DDD63BD6B46CEFD4911E2C1052C90AF1052A6`.
- Exact canonical numeric suffix resolution uses the unique longest valid canonical prefix.
- `Nidoran-f2020` and `Nidoran-m2020` are accepted reviewed normalizations.
- Plain `Nidoran`, `Nidorano`, and `Nidoranp` remain uncertain.
- Porygon/Porygon2 boundary cases are explicitly tested.

Intentional lost recoveries relative to PR-01:

- final-one: 194;
- final-two: 811;
- internal deletion: 425;
- substitution: 57;
- transposition: 856;
- punctuation/spacing: 31;
- glyph confusion: 137.

These losses are the intended fail-closed trade-off and must not be silently recovered by weakening the zero-wrong invariant.

#### Fixture result

- Initial active entries: 47.
- Three approved corrupt Mewtwo files were identified and removed.
- Final active entries: 44; labeled: 16; unlabeled: 28; strict: 16; corrupt: 0; missing: 0; decode failures: 0.
- Unlabeled fixtures are not counted as passed.
- No replacement fixtures or ground truth were fabricated.

#### Deviations and limitations

- Kotlin/JVM measurements are authoritative for future comparisons; earlier Python-port figures remain point-in-time evidence.
- Differences resulted from explicit collision/minimum rules, full-corpus measurement, and current production Kotlin behavior.
- Dynamic/static OCR entries are test-only selection-policy adapters, not full ML Kit image benchmarks.
- Manual Gate A remains open. The three Mewtwo fixtures still require recovery or real-device recapture, and twenty-eight active fixtures remain unlabeled.

#### Dependency and next phase

- PR-00 is complete at `4f8dcc8`; PR-01 is complete at `cac4c8b`; PR-02 is complete at `2ff0a5de`; PR-M1 is complete at `468e9001`; PR-03 is complete at `6e0580e6`; PR-04 is complete at `7404f3c0`; and PR-05 is complete through PR #36 at `23ad338aae2e0e77a9e422c7c0c5c49c553cb59f`.
- PR-06 remains incomplete: Slice 1 and the 1080-wide development corpus are complete, but genuine native-1440 physical-device evidence, controlled OCR-policy comparison, and real-device memory/performance comparison remain required.
- Manual Gate A remains open. Confirmed truth must be manually verified from the source screen and must never be guessed by an agent.
- PR-07 remains blocked on sufficient Manual Gate A completion and a separate immutable end-to-end holdout; the 1080-wide development corpus is excluded from that holdout.
- No synthetic, resized, display-overridden, emulated, or web-sourced substitute is approved as native-1440 evidence.
- AGP/Robolectric remediation remains a separate nonblocking maintenance stream and requires its own dependency-path decision.
- The zero accepted-wrong invariant remains active on every selectable deterministic path.

### External audit package — point-in-time evidence (18 July 2026)

- **Archive:** `files.zip`
- **Archive SHA-256:** `d81768ac28c65b79db88fc9f7f6c888f05c41ac8d45847c10a16bbf98149f0c1`
- **Analyzed repository:** `c577aac157fa9999f5ff32e67a038979932981ce`
- The package contains ten audit, report and measurement files.
- The audit used source inspection, Python reproduction and direct asset measurements.
- It did not execute the Android/JVM production test suite.
- It must not override newer executable Kotlin results or current GitHub source.
- Do not commit the ZIP or extracted audit files.

#### Audit disposition — superseded by merged implementation

- old matcher wrong-acceptance findings;
- nickname acceptance;
- dynamic/static disagreement;
- canonical numeric suffix defects;
- corrupt active Mewtwo fixtures.

PR-01 and PR-02 executable evidence supersedes these findings while preserving the audit as valuable historical root-cause evidence.

#### Audit disposition — PR-03 superseded findings

- SpeciesRefiner lock provenance and Candy authority.

PR-03 executable JVM/Robolectric evidence supersedes this source-audit finding while the audit remains historical root-cause evidence.

#### Audit disposition — still actionable

- under-sampled Phase-2 visual model slots;
- resolution-diverse fixture need before OCR scaling changes;
- privacy review of clipboard and diagnostics exporters;
- signed-release MobSF verification.

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
- The latest external audit found the existing decodable fixture set effectively limited to the 1080-wide source class.
- Capture at least one validated 1440-wide fixture set.
- The completed 1080-wide corpus is development-only and excluded from the future immutable holdout.
- A 1080-wide device using a display size or resolution override is not acceptable evidence for the native-1440 requirement.
- Preserve device model, resolution, Android version, game language, scroll state and timestamp.
- Include shifted/scrolled card positions.
- Do not change the 900-pixel default solely from theoretical area-loss calculations.
- A new OCR image policy may become default only after a controlled resolution-diverse before/after measurement.
- A pure preprocessing de-duplication refactor must prove byte-identical output and must not silently change the 900 value.

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

## PR-M1 — Restore dependency-submission CI

**Branch:** `build/fix-gradle-dependency-submission`<br>

### Completion status

- **PR:** #28
- **Purpose:** restore GitHub Automatic Dependency Submission
- **Merge SHA:** `468e90016ce2a3d34cdd29c188dc0a12497b3261` (`468e9001`)
- **Final scope:** mode-only `gradlew` repair
- **Stage 2 archive-name migration:** not needed
- **Production behavior:** unchanged
- **Recognition behavior:** unchanged
- **Dependencies and versions:** unchanged
- **Artifact naming:** unchanged
- **Gradle/workflow source:** unchanged
- **Release build:** not run
- **PR-03:** complete at `6e0580e6`

The repository-owned workflow audit found no dependency-submission workflow; the submission workflow is GitHub-managed. No duplicate workflow was added.

### Objective

Restore the currently failing Linux `submit-gradle` dependency-submission check without changing recognition or application behavior.

### Resolved point-in-time failure (historical)

- Linux wrapper execution/permission issue;
- fallback Gradle 9 validation rejects the current `archivesBaseName` configuration.

The initial `gradlew` Git mode was `100644`; the final mode was `100755`. The blob SHA before and after was `f5feea6d6b116baaca5a2642d4d9fa1f47d574a7`, and the file contents remained byte-identical. The pre-fix automatic-submission job could not execute `./gradlew`, then fell back to Gradle 9.6.1 and exposed the legacy `archivesBaseName` configuration error. Restoring the executable Git mode allowed project validation and dependency submission to succeed. No `app/build.gradle.kts` migration was needed because the mode-only repair resolved the actual submission failure, and no repository-owned workflow change was needed.

### Remote completion evidence

Pre-fix:

- run `29655703312`, job `88109538238` — failed;
- sequence: `./gradlew: Permission denied`, followed by the Gradle 9.6.1 `archivesBaseName` error.

Post-fix:

- dependency-submission run `29656111570`, job `88110697125` — passed;
- `validate-project` — passed;
- `submit-dependency-snapshot` — passed.

Other final-head checks:

- Run Tests `29656111548` — passed;
- CodeQL `29656111538` — passed;
- Semgrep `29656111547` — passed;
- SonarQube Quality Gate — passed;
- unit tests, detekt, Android lint, and debug APK build — passed.

The repository wrapper reported Gradle 8.9. The debug APK before and after was `app/build/outputs/apk/debug/PokeRarityScanner-v1.10.0-debug.apk`; the artifact filename was unchanged. No release artifact was built.

The old fallback error is resolved historical evidence, not a current modernization requirement. Future Gradle modernization must not be inferred from that old fallback error alone; any future failure must be diagnosed from its own current logs.

### Allowed scope

- `gradlew` executable bit;
- `app/build.gradle.kts`;
- the dependency-submission workflow only when inspection proves a workflow adjustment is necessary;
- directly relevant build verification documentation in the PR body only.

### Forbidden scope

- source recognition logic;
- tests unrelated to build configuration;
- version bump;
- release signing values;
- dependencies;
- application ID;
- output-name change;
- release build;
- lint/detekt baseline regeneration.

### Required tests first

- verify Linux can execute the repository wrapper;
- verify the configured Gradle/AGP path evaluates `app/build.gradle.kts`;
- verify artifact base naming remains equivalent;
- reproduce the current dependency-submission failure before the fix when practical.

### Acceptance

- `submit-gradle` succeeds on Linux;
- the repository wrapper is used rather than an unintended Gradle 9 fallback;
- debug APK naming remains unchanged;
- standard unit, detekt, lintDebug and assembleDebug checks pass;
- no recognition or runtime behavior changes.

### Rollback

Revert PR-M1.

### Sequence

1. Merge the documentation PR that introduced PR-M1.
2. Implement and review PR-M1.
3. Update this plan through this separate documentation PR.
4. Begin PR-03 from the then-current `main` only after this documentation PR merges.

PR-M1 is maintenance, not a renumbering of the core PR-00 through PR-09 recognition plan.

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

1. `exactParsedSpeciesLock`, `directParsedSpeciesMatch`, `directMatchBlock` and equivalent locks must consume PR-02 decision provenance rather than infer exactness from a non-null `String`.
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
6. Merely having a nonblank candy field must not remove exact/reviewed species protection.
7. A candy value may participate in replacement only when:
   - reliable candy provenance;
   - an explicit candy-family relationship;
   - compatible profile evidence;
   - meaningful score margin;
   - conflicting evidence retained in diagnostics.
8. `uniqueCandyOverride` and `candyFamilyAuthorityOverride` must not force a species from uncertain, weak, corrupted or cross-family candy text.
9. Wrong or weak candy may not override an exact canonical or reviewed species.
10. Add explicit trace reasons for kept/replaced/uncertain outcomes.
11. Preserve current behavior for exact, profile-consistent scans.
12. Do not implement a standalone one-line `directMatchBlock` edit.

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
- exact canonical plus unrelated nonblank candy;
- reviewed alias plus unrelated candy;
- safe-fuzzy result plus correct candy;
- uncertain name plus correct candy;
- uncertain name plus wrong candy;
- Nidoran ambiguity;
- same-family drift;
- unique-candy false positive;
- candy-family false positive;
- profile mismatch with and without reliable candy;
- blank candy versus nonblank-but-unreliable candy;
- no weak result becomes a lock.

### Rollback

Revert PR-03 while retaining PR-02.

---

### PR-03 completion evidence

- **PR:** #30
- **Purpose:** SpeciesRefiner name and Candy authority hardening
- **Merge SHA:** `6e0580e6e7040548831124d075acaa98e2b2dbdd`
- **Short SHA:** `6e0580e6`
- **Original head SHA:** `cadb72848e3de91be15f04c72fa6ddcae950cce1`
- **Branch:** `fix/species-refiner-authority-contract`
- **PR-04:** not started
- **Manual Gate A:** remains open
- **Release build:** not run

The detailed PR-03 specification above remains the historical implementation scope. The implementation changed exactly:

- `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt`
- `app/src/test/java/com/pokerarity/scanner/SpeciesRefinerAuthorityTest.kt`
- `app/src/androidTest/java/com/pokerarity/scanner/SpeciesRefinerTest.kt`
- `docs/AI_RUN_REPORT.md`

Unchanged areas: `TextParser.kt`, `OCRProcessor.kt`, `SpeciesFormResolver.kt`, `ScanManager.kt`, consistency and confidence gates, geometry and 900-pixel image policy, visual classifiers, telemetry schema and transport, fixtures and recognition baselines, dependencies and Gradle, and workflows.

#### Implemented authority contract

Name authority:

- Every usable name observation is evaluated through the PR-02 `decideSpeciesName(...)` contract.
- Exact canonical and reviewed alias may create hard authority; safe fuzzy remains soft evidence only.
- Uncertain, no-match and conflicting accepted names cannot create a hard lock.
- Ranked candidates and resolver proposals remain candidate evidence only.
- Accepted-name conflicts are retained deterministically.
- A non-null compatibility-wrapper result is no longer treated as proof of exactness.

Candy authority:

- Nonblank `PokemonData.candyName` alone is not authoritative.
- Candy may influence replacement only through one trusted, non-conflicting, winning Candy diagnostic from the existing trusted parser sources.
- Parsed, selected and parser values must agree with `PokemonData.candyName`.
- Missing, losing, synthetic, mismatched, not-found or conflicting Candy fails closed.
- Untrusted Candy is removed from resolver input, candidate generation, family bonuses and override paths.
- Candy replacement requires an explicit repository family relationship, observed compatible profile, the existing conservative absolute fit requirement, a fit-score margin and a total-score margin; no authority threshold was lowered.

Final trace:

- The final trace describes the actual final SpeciesRefiner decision.
- Stable machine-readable reason codes replace stale resolver-only reasons.
- Trace confidence is derived from the actual authority that selected the result.
- No raw OCR, screenshot, local path, username, timestamp, credential or secret was added.

#### Focused scenario and validation evidence

Focused tests cover exact canonical plus unrelated untrusted Candy; reviewed alias plus unrelated untrusted Candy; safe fuzzy plus reliable corroborating Candy; uncertain and no-match name evidence; conflicting accepted names in both observation orders; blank Candy; nonblank Candy without diagnostics; losing, not-found and mismatched Candy diagnostics; untrusted Candy source; conflicting Candy winners; one reliable matching Candy winner; uncertain name plus correct Candy without sufficient profile; uncertain name plus wrong Candy; unique-Candy and Candy-family false-positive resistance; profile mismatch with and without reliable compatible Candy; same-family drift; resolver proposal without final authority; move-corroborated replacement; and accepted-name/singleton-Candy trigger overlap.

Focused scenario counts (evidence, not corpus-level accuracy claims): hard exact-name authority decisions 12; hard reviewed locks 2; safe-fuzzy soft decisions 1; uncertain/no-match decisions 10; accepted-name/Candy conflicts 2; trusted-Candy replacements 1; accepted-name replacements 1; move-corroborated replacements 1; rejected untrusted-Candy override scenarios 10; unexpected after-change replacements 0; accepted-wrong result 0.

Validation evidence: tests-first RED phase 10 focused tests with 10 expected failures; final focused authority suite 12 of 12 passed; all JVM tests 553 tests, 0 failures, 0 errors, 0 skipped; `SpeciesNameDecisionTest` passed; `SpeciesFormResolverTest` passed; `RecognitionMatcherCharacterizationTest` passed twice with identical output; Android test APK compilation passed; detekt passed; Android lint passed with 0 errors; debug APK build passed; `git diff --check` passed; Run Tests, CodeQL, Semgrep and SonarQube Quality Gate passed; Sonar security hotspots 0; CodeRabbit completed without an actionable inline review thread; dependency submission was not required unless actually triggered for this PR.

The PR comment displayed one new non-security code-quality issue while the Quality Gate passed. The exact rule/path was not retrieved in the closeout evidence, so one non-security Sonar issue remained unclassified in the closeout evidence. It is not a security finding.

PR-02 invariants preserved: exact canonical 1011 correct / 0 wrong / 0 uncertain; accepted-wrong 0 across every selectable name-decision path; dynamic/static selected-result disagreement 0; repeated deterministic report SHA-256 `347E0607547B04611AC2EDE5930DDD63BD6B46CEFD4911E2C1052C90AF1052A6`; Nidoran ambiguity, reviewed aliases and numeric suffix decisions retained; recognition baseline files unchanged. PR-03 does not prove real-device OCR accuracy.

#### Sequence

PR-03 is complete at `6e0580e6`. Security Gate D completed read-only with PASS on `fac15d22`. PR-04 is complete at `7404f3c0`; PR-05 subsequently completed through PR #36 at `23ad338a`. PR-06 is the next numbered recognition phase but remains evidence-gated, and PR-07 remains blocked on Manual Gate A.

### Security Gate D — Dependabot alert triage (read-only)

#### Status

Completed read-only on 19 July 2026 against `origin/main` `fac15d220a862158b33c58562ce37e27c303953f` using the authenticated official Dependabot alerts API. The point-in-time result was 43 open alerts: 1 critical, 16 high, 24 medium and 2 low; 42 unique advisories; 13 unique packages; 0 direct and 43 transitive. GitHub associated all 43 with `settings.gradle.kts` and reported no dependency scope value.

All 43 alerts were rooted through the AGP `8.7.3` build environment. No affected package was found in the inspected app debug compile/runtime or Android-test runtime classpaths. Alert #43 was additionally resolved as `org.bouncycastle:bcprov-jdk18on:1.78.1` through `org.robolectric:robolectric:4.14.1` on the unit-test path. Current critical/high alert numbers were #2, #9, #10, #12, #16, #19, #22, #23, #24, #31, #32, #34, #35, #36, #37, #40 and #43.

The build/test-only evidence does not make an advisory universally unreachable: exploitability inside third-party build tooling was not dynamically tested, and no release task or release build was run. However, no demonstrated critical/high production-runtime blocker was found. **Gate verdict: PASS — PR-04 may proceed** after this documentation closeout is reviewed and squash-merged. No dependency or alert was changed. Recommended nonblocking, separate maintenance batches are an AGP build-classpath remediation and a Robolectric unit-test dependency remediation; neither may be mixed into PR-04.

PR #30's previously unclassified Sonar follow-up was retrieved separately: `kotlin:S6511`, **"when" statements should be used instead of chained "if" statements**, at `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt:295`. It was classified as OPEN, MAJOR, `CODE_SMELL`, with MEDIUM maintainability impact and no security classification. The focused independent maintenance follow-up completed through PR #43 at `878158afce9726aa612f721e220cf1390fc527a7`; it is not a numbered recognition phase.

#### Objective and required output

Before PR-04, perform a separate read-only Dependabot security triage against the then-current `main`. Using the official current alert list, group findings by severity; package and advisory; direct versus transitive dependency; runtime versus build/test/development exposure; manifest and dependency path; fix version availability; duplicate/root-cause package; affected production configuration; realistic reachability or exploit preconditions; and recommended remediation batch.

#### Rules and decision gate

The triage is read-only: no dependency update, alert dismissal, security-setting change, branch or PR. Do not assume every alert needs an individual PR, and do not infer alert contents from the count alone. Passing CodeQL, Semgrep and Sonar does not replace Dependabot dependency triage. Use the official GitHub Dependabot alerts endpoint through authenticated `gh api` when permission is available; never expose GitHub tokens or response headers. If the token lacks Dependabot-alert read permission, stop, report the exact permission limitation, and do not infer alert contents.

Unresolved critical/high runtime or production-reachable alerts with a compatible fix require independent security remediation PRs. Critical/high build/test-only alerts require documented impact and exploitability assessment. Medium/low alerts may be grouped into later compatible update PRs. PR-04 may proceed only when no unresolved critical/high runtime blocker remains, or after required blocking security PRs are merged and recorded. Dependabot remediation remains separate from PR-04 recognition logic.

#### PR-04 completion

- **PR:** #33
- **Purpose:** structured consistency, confidence and early-exit hardening
- **Merge SHA:** `7404f3c001710d5129deeb0ec5596446b3f5f82e`
- **Final implementation head:** `ed8188f4ef9a4d9730219756ad1163872b4a0f1c`
- **Production files:** 4
- **Tests:** 8 files, including the new `ScanFrameFusionDetailedPassTest.kt`
- **Report:** `docs/AI_RUN_REPORT.md`
- **Final PR scope:** 13 files

Verified behavior:

- structured `SpeciesEvidence` is passed through `ScanManager`, `ScanFrameFusion`, `ScanConsistencyGate` and `ScanConfidenceGate`;
- exact canonical and reviewed alias are hard authority;
- safe fuzzy, uncertain, no-match and conflict evidence cannot authorize an early accept;
- close candidates block early exit;
- missing, contradictory, impossible or indeterminate profile status blocks acceptance or requests a detailed pass;
- identical repeated fuzzy OCR frames cannot become correctness merely through repetition;
- cross-family conflicts return retry/fail-closed behavior;
- Candy remains non-authoritative without trusted provenance;
- structured detailed-pass logic is separate from the legacy genuine `topTextConfidence` overload;
- legacy `topTextConfidence` threshold remains 0.86;
- `CP_QUALITY_MIN` remains 0.55;
- candidate-close margin remains 0.08;
- no threshold was lowered;
- no raw OCR was added to safe reasons.

Validation evidence:

- focused PR-04 tests:
  - fusion: 34;
  - manager detailed-pass: 12;
  - consistency edge: 8;
  - confidence: 17;
  - total: 71;
  - failures/errors/skipped: 0;
- full JVM: 583 tests, 0 failures, 0 errors, 0 skipped;
- Android-test compilation passed;
- detekt passed with zero findings and no suppression;
- lintDebug passed;
- assembleDebug passed;
- determinism passed twice: SHA-256 `347E0607547B04611AC2EDE5930DDD63BD6B46CEFD4911E2C1052C90AF1052A6`; 42773 bytes; disagreements 0; acceptedWrong 0;
- GitHub Run Tests passed;
- CodeQL passed;
- Semgrep CE passed;
- Sonar Quality Gate passed;
- Sonar security hotspots: 0;
- CodeRabbit completed review of PR #33;
- all actionable review threads on PR #33 were resolved.

Limitations:

- no release build;
- no connected-device or real-device run;
- Manual Gate A remains open;
- 28 active fixtures remain unlabeled;
- three removed corrupt Mewtwo fixtures still require original recovery or recapture;
- PR-04's scope did not include PR-05.

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

- Record the external point-in-time model measurements:
  - supported species: 43;
  - total species-target slots: 162;
  - zero-positive slots: 100;
  - zero-negative slots: 35;
  - slots with fewer than 10 total samples: 141.
- visual classifier may not introduce or replace global species identity;
- remove or disable cross-family and unknown-species global override;
- species-scoped variant classification runs only after exact/alias/safely accepted species;
- species-scoped Phase-2 variant output may drive a user-visible variant decision only when its species-target slot meets an explicit minimum sample adequacy policy;
- zero-positive slots are diagnostics-only;
- zero-negative slots are diagnostics-only for decisions requiring negative discrimination;
- slots below the approved minimum combined sample count are diagnostics-only unless an independently labeled holdout proves that exact slot;
- missing sample metadata fails closed;
- uncertain species means visual results are diagnostics-only;
- retain shiny/costume/form logic only where existing tests prove bounded behavior;
- do not lower confidence or margin thresholds to compensate for insufficient samples;
- do not regenerate `variant_phase2_model.json` in PR-05;
- do not add remote model update behavior;
- add tests proving:
  - under-sampled slots cannot promote or demote a variant;
  - adequate slots retain existing bounded behavior;
  - missing sample metadata fails closed;
  - no visual output changes selected species;
  - uncertainty remains uncertainty.

### Allowed scope

- `VariantDecisionEngine.kt`;
- `ScanAuthorityLogic` or an equivalent authority helper;
- `Phase2VariantClassifier.kt`, limited to preserving sample-metadata presence, exposing typed capability metadata, and distinguishing absent counts from explicit zero counts;
- `Phase2VariantFeatureMerger.kt`, limited to enforcing the approved sample-adequacy eligibility gate without changing confidence or margin thresholds;
- narrowly required orchestration in `ScanManager.kt`, limited to carrying the existing structured species-authority state into Phase-2 application and making uncertain, no-match, conflict, missing-authority and retry states diagnostics-only;
- the deterministic slot-adequacy JVM report generator/test;
- `app/src/test/resources/phase2_slot_adequacy_expected.json`;
- narrowly required authority, classifier and visual-merger tests;
- diagnostics and the generated build report.

Adding these files to allowed scope does not authorize a broad refactor. `Phase2VariantClassifier.kt` may not change model features, thresholds, prototypes, crop geometry or model loading behavior. `Phase2VariantFeatureMerger.kt` may not lower or retune any confidence or margin threshold. `ScanManager.kt` changes must be limited to authority-state gating and diagnostics-only behavior. `app/build/reports/phase2/phase2_slot_adequacy_actual.json` is generated validation output and is not committed. `variant_phase2_model.json` remains unchanged.

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
- no threshold is loosened without a measured independent holdout;
- a deterministic report lists slots that are decision-capable, slots that are diagnostics-only, and the exclusion reason for each slot;
- the report confirms no threshold loosening.

### Approved sample-adequacy policy

#### A. Numeric minimum

The approved minimum combined sample count is:

`MIN_COMBINED_SAMPLES = 10`

This is a minimum eligibility floor, not an accuracy claim or independent holdout result.

#### B. Requirements for any user-visible Phase-2 promotion or demotion

A species-target slot is eligible to influence a user-visible visual decision only when all of the following are true:

- sample metadata is explicitly present;
- `supported` is `true`;
- `positiveCount` is at least 1;
- `negativeCount` is at least 1;
- `positiveCount + negativeCount` is at least 10;
- the species authority state is exact canonical, reviewed alias, or safely accepted species under the existing structured authority contract;
- all existing confidence and margin thresholds also pass.

Clarifications:
- User-visible promotion is a discriminative decision and therefore also requires negative examples (`negativeCount >= 1`).
- User-visible demotion requires both positive and negative examples (`positiveCount >= 1` and `negativeCount >= 1`).
- Meeting sample adequacy does not automatically apply a visual decision; existing target-specific confidence and margin thresholds remain mandatory.
- No confidence or margin threshold is lowered.

#### C. Deterministic exclusion reasons

The exact stable capability reason codes and their deterministic precedence order are:

1. `missing_metadata`
2. `unsupported`
3. `zero_positive`
4. `zero_negative`
5. `below_minimum_combined_samples`
6. `decision_capable`

Clarifications:
- Missing count fields must remain distinguishable from explicit numeric zero (`missing_metadata` vs `zero_positive`/`zero_negative`).
- Runtime parsing must not silently convert absent counts into authoritative zero-count metadata.
- `zero_positive` and `zero_negative` slots are diagnostics-only.
- Slots with fewer than 10 combined samples (`positiveCount + negativeCount < 10`) are diagnostics-only.
- No existing slot currently has an approved independent-holdout exemption.
- Any future exemption requires a separate reviewed plan update containing the exact slot and labeled holdout evidence.

#### D. Species-authority containment

- Global visual classifiers never introduce, replace, or restore global species.
- Visual species predictions are diagnostics-only.
- Visual results can act only inside an already accepted species boundary.
- Uncertain, no-match, conflict, missing authority, and retry states are diagnostics-only.
- A non-blank `PokemonData` name is not by itself proof of accepted authority.
- Structured species authority from the scan pipeline must control whether Phase-2 output may be applied.
- Cross-family visual output never changes the selected species.
- Global and species-scoped target slots use the same sample-adequacy policy.

#### E. Runtime metadata requirement

PR-05 must preserve metadata presence explicitly. The implementation must distinguish:
- count field absent (`missing_metadata`);
- count present with value 0 (`zero_positive` or `zero_negative`);
- count present with positive value (`positiveCount >= 1`, `negativeCount >= 1`).

The current default-to-zero behavior may not be used to label absent metadata as `zero_positive` or `zero_negative`. The narrow implementation may modify `Phase2VariantClassifier` or an equivalent typed metadata boundary when required to preserve this distinction.

#### F. Deterministic report contract

Define the canonical expected evidence file for PR-05:
`app/src/test/resources/phase2_slot_adequacy_expected.json`

Define the generated comparison output:
`app/build/reports/phase2/phase2_slot_adequacy_actual.json`

The PR-05 JVM validation must:
- read the existing bundled `variant_phase2_model.json` without modifying it;
- enumerate every species-target slot exactly once;
- sort deterministically by species, target, and source;
- include `positiveCount`, `negativeCount`, `combinedCount`, `supported` state, `capability` reason, and `decision-capable` boolean;
- include policy value `MIN_COMBINED_SAMPLES = 10`;
- include confirmation that visual confidence and margin thresholds were not loosened;
- generate the actual report twice and verify byte-identical output;
- compare actual output against the checked-in expected report;
- contain no raw OCR;
- contain no local filesystem paths;
- use no network access.

The expected report is evidence generated from the existing model, not a regenerated model.

#### G. External measurement classification

The existing point-in-time measurements are retained:
- supported species: 43;
- total species-target slots: 162;
- zero-positive slots: 100;
- zero-negative slots: 35;
- slots with fewer than 10 total samples: 141.

Clarifications:
- These are point-in-time measurements of the current bundled model.
- They motivated the fail-closed floor.
- They are not independent accuracy validation.
- They do not make any slot decision-capable unless the approved runtime policy also passes.

### Completion record — merged 22 July 2026

#### Status

- PR-05 completed and squash-merged through PR #36.
- Merge SHA: `23ad338aae2e0e77a9e422c7c0c5c49c553cb59f`
- Final implementation head: `1806a35c461edfdebf47e8f875080497177679c3`
- Base SHA: `c6c85adfac2ca0f98741c4e7e291dd6d4ed3b9e7`

#### Implemented containment

- Visual classifier output cannot introduce, replace or restore global species.
- `Unknown` cannot become a species-scoped classifier target.
- Phase-2 classification and merger application require accepted structured species authority.
- Retry, conflict, uncertain, no-match, missing-authority, ambiguity and species mismatch states remain diagnostics-only.
- A nonblank `PokemonData` name alone is not authority.

#### Sample-adequacy enforcement

- `MIN_COMBINED_SAMPLES = 10`.
- Missing metadata remains distinct from explicit zero.
- Promotions, demotions, species-negative blocking and applied targets require a decision-capable slot.
- Existing confidence and margin thresholds were not lowered or retuned.
- Effective target-threshold reporting inherits global defaults for omitted target fields.

#### Deterministic report evidence

- Expected report: `app/src/test/resources/phase2_slot_adequacy_expected.json`
- Ignored generated report: `app/build/reports/phase2/phase2_slot_adequacy_actual.json`
- Species-model entries: 43.
- `supportedSpecies` entries: 43.
- Total slots: 162.
- Decision-capable slots: 12.
- Diagnostics-only slots: 150.
- Raw zero-positive slots: 100.
- Raw zero-negative slots: 35.
- Raw slots below 10 combined samples: 141.

Exclusive production reason counts:

- `missing_metadata`: 0
- `unsupported`: 103
- `zero_positive`: 0
- `zero_negative`: 32
- `below_minimum_combined_samples`: 15
- `decision_capable`: 12

Hashes:

- Canonical source-model SHA-256: `34cc755d6197a7e3958b45f8fb754eb0348f163101ff4ac926599b64598020aa`
- Expected and repeated actual report SHA-256: `69A5D328BFFE95A349F3AEF3E8F7E6D1FCC807CD874E74A3119262A66940C5C5`

These measurements describe the current bundled model; they are not independent accuracy or holdout validation. The model JSON was not regenerated. Only 12 current slots satisfy the approved decision-capability policy.

#### Validation

- Focused deterministic-report suite: 8 tests.
- Phase-2 regression, authority and seam suites: 75 tests.
- Slice-1 guardrails: 58 tests.
- Complete JVM suite: 632 tests.
- Zero failures, errors and skipped tests.
- detekt passed.
- lintDebug passed.
- assembleDebug passed.
- GitHub Run Tests passed.
- CodeQL passed.
- Semgrep CE passed.
- SonarCloud Quality Gate passed.
- Final CodeRabbit review had no new actionable findings.
- All review threads were resolved.

#### Remaining gates

- Manual Gate A remains open.
- PR-05 completion does not prove independent real-device or holdout accuracy.
- PR-06 and later roadmap requirements remain unchanged.
- This completion record does not authorize threshold tuning, model regeneration or remote model updates.

### Independent maintenance completion — SpeciesRefiner S6511

- **PR:** #43
- **Merge SHA:** `878158afce9726aa612f721e220cf1390fc527a7`
- **Scope:** independent focused maintenance; it does not add or renumber a recognition phase.
- The replacement-candidate `if`/`else if` selection became an ordered Kotlin `when` while preserving candy-family, evolution-family, accepted-name, then fallback precedence.
- A focused overlapping-condition characterization verifies that candy-family selection retains precedence when an accepted-name candidate also applies.
- Focused `SpeciesRefinerAuthorityTest`: 13 tests passed. Full JVM suite: 648 tests passed, with zero failures, errors, or skips.
- detekt, `lintDebug`, `assembleDebug`, GitHub CI, security checks, SonarCloud, and CodeRabbit passed; all review threads were resolved.
- Runtime behavior was intended and verified to remain equivalent. No OCR policy, matcher threshold, authority band, species data, fixture, UI, dependency, Gradle, workflow, or implementation-plan behavior changed in PR #43.

### Independent maintenance audit closeout — dependency/CI and local screenshot readiness (23 July 2026)

#### Dependency decision

- The current declarations remain AGP `8.7.3`, Gradle `8.9`, Kotlin `1.9.24`, `compileSdk`/`targetSdk` 35, `minSdk` 26, Robolectric `4.14.1`, AndroidX Test Core `1.6.1`, AndroidX Test Ext JUnit `1.2.1`, and AndroidX Test Runner `1.6.2`.
- The 23 July point-in-time Dependabot inventory was 43 open transitive alerts: 1 critical, 16 high, 24 medium and 2 low, covering 42 advisories and 13 packages. GitHub associated every alert in that export with `settings.gradle.kts` and reported no scope value. This is historical evidence, not a permanently current count.
- All 13 affected packages resolve through the AGP build environment. None resolves into the app debug runtime, release runtime, or debug Android-test runtime. `org.bouncycastle:bcprov-jdk18on:1.78.1` also appears on the debug unit-test path through Robolectric `4.14.1`.
- This build/test-path evidence does not mean the advisories are risk-free or universally unreachable. It preserves the distinction between build/test exposure and production-runtime exposure; third-party tooling exploitability was not dynamically disproved.
- AGP `8.8.2` is not a complete remediation: it still resolves protobuf `3.22.3`, below the patched `3.25.5`, and requires Gradle `8.10.2` or newer. AGP `8.13.2` requires Gradle `8.13` or newer. Both compatibility attempts timed out; both timeouts are inconclusive, not evidence of compatibility or incompatibility.
- Robolectric `4.16.1` moves its Bouncy Castle path to `1.81`, above alert #43's critical threshold but below another live alert's `1.84` fix. It does not clear the critical Bouncy Castle family because AGP retains a separate Bouncy Castle `1.77` build path.
- **Decision:** AGP and Robolectric remediation remain separately blocked because no safe, small and complete dependency-remediation PR is currently demonstrated. No dependency remediation was completed. Blocked does not mean risk-free; it means the presently tested paths do not support a safe small update.

#### CI performance audit

- Five recent successful `main` pushes showed Run Tests at 9:00–9:29 (median 9:10), with four Gradle invocations dominating; CodeQL Java/Kotlin at 5:19–7:15 (median 7:09), with Android build at 4:14–5:52 (median 5:46); and Semgrep at 0:22–0:32 (median 0:25), with its pinned install at 0:10–0:18 (median 0:12).
- These durations are measurable but not obviously pathological. Ranked future, coverage-preserving benchmark candidates are: (1) consolidate Run Tests Gradle invocations; (2) add official `gradle/actions/setup-gradle` caching to Run Tests and CodeQL; and (3) cache the pinned Semgrep install.
- No optimization, duration-savings claim, workflow change, or benchmark completion is recorded here. Any CI optimization requires a separate benchmark PR.

#### Local screenshot candidate audit

- A local, flat, uncommitted `screenshots/2026/` candidate corpus contains 730 PNG files totaling 473,826,206 bytes. All 730 decode; none is corrupt or zero-byte. Every image is 1080×2340 portrait RGBA at 8 bits per channel, with no mixed resolution and no embedded EXIF or text metadata.
- The images match the 1080×2340 geometry class only. Embedded device provenance is absent, so they are not claimed as Samsung S25/SM-S931B evidence and cannot satisfy the native-1440 gate.
- All 730 SHA-256 hashes are unique, with no exact duplicates. A strict pHash, dHash and thumbnail comparison estimates 61 near-duplicate groups covering 176 files, or 115 possible redundant candidates. This is an estimate requiring human review, not a truth label.
- Sampled structure suggests 729–730 likely Pokémon details candidates, predominantly ordinary details screens, with likely CP, HP, visible-name and candy-family regions, an English-like sample, varied subjects and some repeats. No authoritative species, language, scroll-state, or unique-Pokémon counts were inferred.
- Recurring turquoise scanner-overlay controls and possible catch date/location text make image-by-image pixel-content privacy review mandatory before any publication. The audit grants no privacy, provenance, truth, curation, or publication approval and records no screenshot filename or raw content.
- Potential use classifications are: **MEDIUM POTENTIAL** for Manual Gate A truth-recovery assistance; **HIGH POTENTIAL** for 1080 development-corpus expansion; **MEDIUM POTENTIAL** for future immutable-holdout candidate sourcing; **LOW POTENTIAL** for PR-06 geometry/OCR-policy work because the corpus is 1080-only; **MEDIUM POTENTIAL** for PR-07 holdout preparation; and **HIGH POTENTIAL** for general regression or characterization support.
- The corpus remains local, uncommitted and non-authoritative. No subset was curated, copied, approved, or added as merged repository evidence.

#### Roadmap effect and next action

- PR-06 remains incomplete and evidence-gated; Manual Gate A remains open; PR-07 remains blocked; PR-08 and PR-09 are not advanced. The local corpus does not replace native-1440 evidence, controlled OCR-policy comparison, real-device performance evidence, confirmed truth, or an approved immutable holdout.
- AGP/Robolectric remediation and CI optimization remain independent maintenance streams, neither completed by this audit.
- **Best next actionable step:** a dedicated screenshot-corpus curation/inventory PR for a safe candidate subset. Start with deduplication review and a stratified shortlist of approximately 100–120 candidates, then perform image-by-image privacy, provenance and manual-truth review; separate development candidates from prospective holdout candidates before tuning; and record immutable hashes only after approval. Do not curate or copy the subset as part of this closeout. The native-1440 evidence gate remains unchanged.

### Independent security-maintenance reconciliation — 3 August 2026

- **Task-start main:** `3137e4014ab1c408390a7bc99f6cb10c16240e61`.
- PR #46 merged as `3ed8f5184403e8705f7b87fe839bd4957433fdba` and published the 120-record candidate manifest: 100 development candidates and 20 quarantined prospective-holdout candidates. All 120 remain unreviewed and non-authoritative. No screenshot gained privacy, provenance, truth, publication or holdout approval.
- PR #47 merged as `15ef00cf2cfd13cfe05dbff9dd6d6f399fee89ff` and published repository guidance.
- PR #49 merged as `fbc85d0fade0b4c82b027619ea34c03a3864fffa` and published the dated security evidence at `docs/security/SECURITY_SCAN_EVIDENCE_2026-08-03.md`.
- PR #48 merged as `3137e4014ab1c408390a7bc99f6cb10c16240e61` and completed only the verified narrow release-diagnostics and audit/database-logging privacy-containment scope. It did not complete all PR-08 release work; no signed-release or MobSF completion is claimed.
- The earlier 3 August Dependabot export recorded 52 open transitive alerts: 1 critical, 20 high, 29 medium and 2 low. A task-start refresh recorded 53: 1 critical, 21 high, 29 medium and 2 low. Both are dated snapshots. The refresh added one high Netty HTTP/2 advisory; all 53 were associated with `settings.gradle.kts`, and GitHub supplied no dependency scope.
- The task-start targeted Snyk dependency scan reproduced 358 resolved dependencies, 47 unique findings and 250 vulnerable paths: 1 critical, 24 high, 21 medium and 1 low by unique finding. These are dated results, not permanently current counts.
- **M2-A evidence publication:** complete through PR #49.
- **M2-B narrow release privacy containment:** complete through PR #48 only.
- **M2-C dependency compatibility matrix:** this maintenance task. AGP/Gradle, Robolectric/Bouncy Castle and Kotlin/Compose/KAPT trials were isolated, and their individual compatibility results are recorded. None safely cleared its target alert family; no speculative dependency change was promoted.
- **M2-D scoped local-script triage:** this maintenance task. All 48 findings remaining after excluding `.agent-references` were reviewed as local CLI-boundary findings; none established a lower-trust security boundary or justified a script restriction or code change.
- This maintenance task is not renamed PR-08 and does not claim the remaining PR-08 release scope complete.
- PR-06 remains incomplete and evidence-gated. Genuine native-1440 physical-device evidence remains required. Manual Gate A remains open. PR-07 remains blocked. The zero confidently accepted wrong-species invariant is unchanged.

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
8. Treat the external audit's effectively 1080-wide decodable fixture set as insufficient for a default-policy change.
9. Require at least one validated 1440-wide fixture set with device model, resolution, Android version, game language, scroll state and timestamp.
10. Include shifted/scrolled card positions in the controlled comparison.
11. Do not change the 900-pixel default solely from theoretical area-loss calculations.
12. A pure preprocessing de-duplication refactor must prove byte-identical output and must not silently change the 900 value.
13. Do not move PR-06 ahead of PR-03, PR-04 or PR-05.

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
- any new default is supported by a controlled resolution-diverse before/after measurement;
- OOM/performance regression is bounded;
- fallback geometry is explicitly reported;
- no unmeasured “61% accuracy improvement” claim.

### Rollback

Switch default back to the preserved baseline policy or revert PR.

### Slice 1 completion record — merged 22 July 2026

#### Status

- PR-06 Slice 1 completed and squash-merged through PR #39.
- Merge SHA: `a2ce837042d95abbe9c5921e5f5262be1a00e33e`
- Documentation closeout: PR #40, squash-merged at `2831a27166b18d16f623e6d1ccb1acf48072ff58`.
- Final implementation head: `0938f231f577d93583c34c1d6f4118fcd95eff38`
- Base SHA: `cd33528b92e2973e76e47a90c1f15d038a8efdf3`
- This is measurement-harness infrastructure only.
- PR-06 as a whole remains incomplete.

#### Policy-planning seam

- Added pure internal policy planning for `baseline_900_width`, `native_crop_first`, and `bounded_crop_upscale`.
- Field classes: name, candy, CP, HP, and other.
- The exact current baseline formula was preserved: `(sourceHeight * (900f / sourceWidth)).toInt()`.
- Native crop-first is represented only for name and candy.
- CP, HP, and other fields are forced to the existing baseline when a non-baseline policy is requested.
- Bounded name/candy policy requires explicit caller-owned bounds.
- No implicit production bounds or recommended upscale values were added.
- No runtime caller was connected to the policy helper.

Stable reason codes:

- `baseline_selected`
- `native_name_candy`
- `bounded_name_candy`
- `numeric_field_baseline_locked`
- `other_field_baseline_locked`
- `bounded_configuration_required`

#### Deterministic synthetic evidence

- Expected report: `app/src/test/resources/ocr_geometry_policy_expected.json`
- Ignored generated report: `app/build/reports/recognition/ocr_geometry_policy_actual.json`
- Evidence class: `synthetic_geometry_only`
- Source cases: 1080×2340 and 1440×3120.
- Total deterministic plan combinations: 24.
- Production runtime changed: false.
- Production default: `baseline_900_width`.
- Baseline maximum width: 900.
- Expected and repeated actual report SHA-256: `2F60ADE05E43C57DD2DECB179BD6E0DF5109DC778D3FF2B67BFE0E5C7BE37BC1`

The 1440 case is synthetic geometry input, not a validated 1440 screenshot. The report contains no OCR result, is not accuracy evidence, and cannot authorize a production-default change. No arbitrary bounded-policy candidate constants were inserted into the golden report; explicit test-only bounds are used only in focused tests to verify bounded policy and dimension mathematics.

#### Validation

- Focused policy/report suite: 12 tests, run twice.
- Complete JVM suite: 644 tests.
- Zero failures, errors, and skipped tests.
- detekt passed.
- lintDebug passed.
- assembleDebug passed.
- assembleDebugAndroidTest passed.
- `git diff --check` passed.
- GitHub Run Tests passed.
- CodeQL passed.
- Semgrep CE passed.
- SonarCloud Quality Gate passed with zero new issues and zero security hotspots.

CodeRabbit proposed adding configured bounded-upscale constants to the golden report. That suggestion was reviewed but not applied because the approved Slice 1 contract intentionally requires the golden report to represent the no-configuration fallback and forbids arbitrary candidate constants. Configured bounded behavior and bounded dimension mathematics are covered by focused tests. The review thread was resolved after verification. A named-argument suggestion was non-blocking style feedback.

#### Runtime invariants

- `ScanManager.kt` was unchanged.
- `OCRProcessor.kt` was unchanged.
- `ImagePreprocessor.kt` was unchanged.
- `ScreenGeometryBuilder.kt` was unchanged.
- `ScreenRegions.kt` was unchanged.
- The production 900-width behavior was unchanged.
- CP/HP behavior was unchanged.
- No matcher, refiner, species-authority, or visual threshold changed.
- No UI, telemetry, fixture, dependency, or workflow changed.
- No screenshots or device actions occurred.

#### 1080-wide real-device development-corpus evidence — completed through PR #41

- **Merge commit:** `1bf335edca3a92b2cb6ca85f523e87137c068331`
- **PR:** #41
- **Documentation closeout:** PR #42, squash-merged at `924e76d3392eac132e1d016c546d3590bac5d666`
- **Dataset role:** development corpus, not holdout
- **Device:** Samsung SM-S931B
- **Native resolution:** 1080×2340
- **Display size override:** none
- **Fixture count:** 15
- **Pokémon set count:** 5
- **Position distribution:** 5 reference / 5 shifted / 5 scrolled
- **Unique PNG hashes:** 15
- **Aggregate bytes:** 8,725,743
- **Source metadata SHA-256:** `1057AC98F1702D8B484C3A5AF727FDAD4C7520EAE455A98BFCDA05423575954E`
- **Source manifest SHA-256:** `4FAD46777EB5B77D581416E3B82ADC0E691EA1EDC9023C97A91360B46EADC06B`
- Repository PNGs matched their source files byte-for-byte before import.
- PNG ancillary metadata audit: IHDR 15, sBIT 15, sRGB 15, IDAT 1,070, IEND 15; textual or EXIF ancillary chunks: 0.

Integrity/privacy JVM coverage verifies exact dataset identity, per-set truth, one reference/shifted/scrolled record per set, PNG dimensions, hashes, byte total, a bounded ancillary-chunk parser, privacy-pattern rejection, and malformed-PNG rejection. The full JVM suite passed: 647 tests, 0 failures, errors, or skips. detekt, lintDebug, assembleDebug, assembleDebugAndroidTest, Run Tests, CodeQL, Semgrep CE/Cloud, SonarCloud, and CodeRabbit all passed. All three actionable CodeRabbit threads were resolved.

#### Remaining gates and next evidence step

- Production runtime remains unchanged.
- The production OCR default remains `baseline_900_width`.
- No OCR accuracy conclusion has been drawn from this development corpus.
- No production policy selection has been made.
- PR-06 remains incomplete.
- Manual Gate A remains open.
- Real-device 1440-wide fixture evidence is missing.
- A controlled OCR-policy comparison is missing.
- A real-device memory/performance comparison is missing.
- A future immutable holdout corpus must remain separate from this development corpus.
- **Next evidence step:** obtain a real-device 1440-wide corpus with the same controlled reference/shifted/scrolled structure and confirmed truth, then run the controlled policy comparison. The 1440-wide capture alone does not complete PR-06.
- PR-07 prerequisites remain unchanged.

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

The latest external audit supports this existing direction:

- debug certificate, debuggable and tooling-component findings came from a debug APK and are not by themselves release defects;
- PR-08 still must verify clipboard lifecycle, diagnostics exporters, release-visible logging and sensitive data;
- remote model update behavior is not a security fix and remains forbidden;
- `minSdk` must not be raised solely to improve a MobSF score.

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

The latest external audit does not replace this phase: PR-09 still requires an actual signed release build and MobSF rescan. It does not authorize remote model updates or a `minSdk` increase as score-oriented security changes.

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
11. Project chat independently verifies scope, code, CI, security checks, CodeRabbit and review threads.
12. When every required gate passes and no valid unresolved issue remains, the project chat may mark ready and squash-merge without requesting another routine user confirmation.
13. User confirmation remains required for:
    - real-device or ground-truth decisions;
    - release signing or release builds;
    - secrets or credentials;
    - destructive or irreversible operations;
    - material scope expansion;
    - unresolved safety or product ambiguity.
14. Delete the merged branch.
15. Create a separate documentation PR updating this plan:
    - completed phase;
    - merge SHA;
    - observed metrics;
    - deviations;
    - next phase.
16. Do not proceed while another implementation PR is open unless the scopes are provably independent and the user explicitly approves parallel work.

When GitHub Automatic Dependency Submission is triggered for a future implementation PR, both `validate-project` and `submit-dependency-snapshot` must pass. A failure may not be ignored as unrelated without current-log analysis. Manual reruns are not required when the job is not triggered.

Passing CodeQL, Semgrep and Sonar does not replace Dependabot dependency triage. A known untriaged dependency-alert count may not be described as resolved. Future dependency remediation uses independent, reviewable PRs.

---

# 8. Model and prompt policy

Every future Codex task must be presented in two separate parts.

Part 1 appears in the project-chat message outside the copyable prompt and contains:

- Recommended model: one exact currently available Codex model name;
- Effort: Low, Medium, High, Extra High or Max, limited to options actually available for that model and account;
- Estimated weekly limit use: an approximate percentage range of the user's weekly Codex allowance;
- Session: Fresh or Continue current PR session;
- Why: one concise sentence.

Part 2 is the copyable Codex prompt in a code block. The copyable prompt must begin directly with the repository task. It must not contain:

- Recommended model;
- Effort;
- estimated token count;
- Expected token intensity;
- estimated weekly limit use;
- Session;
- Why.

## Model-selection requirements

- Use one exact currently available model name, not phrases such as “strongest available model,” “capable Codex model” or “best available coding model.”
- Verify current model availability through official OpenAI sources whenever the information may have changed.
- Select the lowest-cost model that can complete the task reliably.
- Typical guidance:
  - GPT-5.6 Luna for mechanical edits, narrow documentation changes and simple repository maintenance;
  - GPT-5.6 Terra for bounded implementation work, CI fixes and standard repository tasks;
  - GPT-5.6 Sol for complex recognition logic, architecture, security, concurrency and broad multi-file reasoning.
- Do not present these examples as permanent availability guarantees. Re-evaluate the exact model for every task.

## Limit estimate requirements

- Express expected usage as an approximate percentage of the weekly Codex allowance.
- Do not use raw token count as the primary user-facing estimate.
- Clearly treat the percentage as an estimate, not a guaranteed billing or quota calculation.
- Base the estimate on the selected model, effort, repository-reading scope, expected test and build runs, likely review/fix iterations, and whether the session is fresh or continuing.
- Use a range rather than a single precise percentage.
- Update the estimate when a task materially expands.

## Session requirements

- Use a fresh session for every new PR.
- Continue the current session only for corrections or review fixes within the same PR.
- Do not place the session recommendation inside the copyable prompt.

## Existing prompt-safety rules

Preserve these requirements:

- never send an entire multi-PR roadmap as one implementation task;
- prompts must list exact allowed and forbidden scope;
- prompts must instruct Codex to stop before editing if scope expansion is required;
- prompts must include validation and rollback;
- prompts must not instruct Codex to regenerate measurement baselines without explicit authorization.

Older in-document model or token-intensity metadata is historical planning metadata, not a requirement for the format of future copyable prompts.

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

# 10. Current state and next action

- PR-05 is complete through PR #36 at `23ad338aae2e0e77a9e422c7c0c5c49c553cb59f`.
- PR-06 is incomplete and blocked on genuine native-1440 physical-device evidence, a controlled OCR-policy comparison, and a real-device memory/performance comparison. Its completed Slice 1 harness and 1080-wide development corpus preserve production `baseline_900_width`; no OCR-policy selection has been made.
- PR-07 is blocked on sufficient Manual Gate A completion, manually confirmed truth data, and a separate immutable end-to-end holdout. The 1080-wide development corpus does not satisfy that prerequisite.
- Manual Gate A remains open. No synthetic, resized, display-overridden, emulated, or web-sourced substitute is approved for the native-1440 or immutable-holdout requirements.
- While hardware evidence is unavailable, only explicitly plan-supported, independent maintenance work may proceed.
- The 3 August 2026 controlled maintenance matrix confirmed that AGP/Gradle, Robolectric/Bouncy Castle and Kotlin/Compose/KAPT candidates did not provide a safe, complete alert-family remediation. No dependency change was promoted; blocked does not mean risk-free or universally unreachable.
- The dated 3 August Snyk dependency result remains 47 unique findings over 250 vulnerable paths. Dependabot increased from the earlier 3 August export of 52 open alerts to 53 at this task's start. These are point-in-time results and must be refreshed before future decisions.
- CI optimization remains separate future maintenance requiring its own benchmark PR; no workflow optimization or savings claim is complete.
- The local, uncommitted 1080×2340 screenshot corpus is a non-authoritative candidate resource only. Its best next use is a dedicated dedupe-first curation/inventory PR with image-by-image privacy, provenance and manual-truth review, while preserving a pre-tuning split between development candidates and any prospective immutable holdout.
- Do not rename this maintenance work PR-08 or nominate PR-08 as next while the dependency graph still places it after blocked PR-07.
