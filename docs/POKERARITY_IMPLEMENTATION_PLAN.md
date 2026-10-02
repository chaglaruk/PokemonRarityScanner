# PokémonRarityScanner — Authoritative Recognition Integration Plan

**Plan revision:** 2026-10-02 — Phase 1 closeout / Phase 2 handoff  
**Repository:** https://github.com/chaglaruk/PokemonRarityScanner  
**Authoritative path:** docs/POKERARITY_IMPLEMENTATION_PLAN.md  
**Active mission:** docs/CURRENT_RECOGNITION_MISSION.md  
**Implementation base:** latest origin/main; Phase 2 integration work must branch from the post-Phase-1 main tree

> origin/main is authoritative for repository state. This file is the execution roadmap for the active recognition-recovery mission once merged to main. Every implementation task must re-verify live GitHub state. Historical plans, audits, Manual Gates, research reports, and benchmark-product analysis are evidence, not execution authority unless explicitly promoted here.

---

## 0. Verified publication baseline

At plan publication:

- origin/main: 91d7555fc18615f8d9eff7355032ce10bf0f1d4a
- fix/recognition-recovery: 28948f4439381f26f1e865bc2b14c888616752fa
- recovery vs main: 5 commits ahead / 0 behind
- open PRs: #54 only; unrelated and not to be modified opportunistically
- previous implementation-plan blob: 8e0258d41de2bf3909181803b8ee9ee763aa209f
- CodeQL and Semgrep were green on the verified main baseline
- Refresh Living Pokedex scheduled workflow was failing independently of recognition; do not report that as recognition CI

### Phase 1 closure update — 2026-10-02

Phase 1 has now passed its exit gate and is merged to main.

- Phase 1 integration PR: #59
- merged main SHA: `f7a73a43dc210c3090a73a6d5cd7c561ed6d14e3`
- final recovery head before merge: `134de25d162386ec595d087adfe0810003d9ef00`
- final recovery relation before merge: 19 ahead / 0 behind main
- full JVM suite before merge: 796 tests, 0 failures/errors/skips
- detekt: 0 findings
- lintDebug: 0 errors; existing baseline warnings unchanged
- debug + androidTest builds: successful
- Samsung S25 preserved exact-frame replay: 0/17 compared-field changes against the validated Phase 1 baseline, 13/13 real-detail species unchanged, 0 new confidently-wrong regressions
- adversarial ambiguous-family exact-title case now fails closed
- anchored profile reconciliation now uses the same per-form RecognitionProfiles authority as the resolver while the legacy non-observation path is preserved
- Sonar: 0 new issues / 0 security hotspots; CodeQL and Semgrep passed
- GitHub Advanced Security AI review remained externally blocked by Copilot monthly quota (HTTP 402), with no security finding attached
- PR #54 remains unrelated and untouched

This closes Phase 1 only. Fresh live capture/overlay verification, independent holdout acceptance, broad screen-state coverage, persistent calibration, level/IV completion, and production readiness remain open work.

### Recovery branch foundations already present

The recovery branch already contains useful work and is the implementation base:

- RecognitionObservation
- RecognitionProfiles
- strict current-HP vs max-HP handling
- anchored screen text extraction
- HealthBarLocator
- FamilySpeciesResolver
- same-witness CP/HP feasibility support
- numeric parsing hardening
- capture busy-request handling
- recognition recovery tests and benchmark tooling

The branch is not production-ready.

### Independently validated gate-semantics evidence

A separate main-based candidate patch was validated before this plan:

- INDETERMINATE profile evidence no longer acts like contradiction when strong identity evidence already exists
- exact-frame replay kept 13/13 species unchanged
- UNCERTAIN changed 13 -> 2
- ACCEPT changed 0 -> 11
- overlay-capable results changed 0 -> 11
- adversarial authority cases remained fail-closed
- CONTRADICTORY, IMPOSSIBLE, weak/fuzzy, close-candidate, and conflict paths remained blocked
- the old detectArcLevel experiment was degenerate and must remain unwired

This is evidence, not a patch to cherry-pick blindly. Recovery has INDEPENDENT_PROFILE authority and requires a recovery-specific adaptation.

### Calcy IV research status

Calcy IV interoperability research is sufficiently complete for independent implementation design.

Architectural findings that may guide independent engineering:

- persistent MediaProjection / VirtualDisplay / ImageReader hot path
- screen-state routing before expensive OCR
- persistent layout calibration, autoconfiguration, and self-healing
- field-specific ROI/preprocessing/OCR
- long-lived OCR engine and cached game data
- field-level partial-result semantics
- stardust / arc / CP / HP / appraisal cross-validation
- game-domain feasibility constraints
- early exits, skip-when-known, and drop-don't-queue behavior
- native pixel kernels for expensive preprocessing

No Calcy source, assets, thresholds, traineddata, private databases, or native implementation may be copied.

---

# 1. Product objective and invariants

## 1.1 Objective

Reliably identify the Pokémon shown on normal supported Pokémon GO screens without routinely asking the user to identify the species manually.

Calcy IV and Poke Genie are practical UX benchmarks, not code or threshold sources.

## 1.2 Primary invariant

A confidently accepted wrong species is a critical failure.

But a scanner that rejects an impractical share of normal scans is also a product failure.

Optimize for both:

1. extremely low confidently-wrong species rate
2. high useful automatic coverage

## 1.3 Evidence-semantics invariant

Never collapse these states:

- Positive: supports a candidate
- Negative: contradicts or makes a candidate impossible
- Missing: not visible / not extracted / not available
- Unreadable: region exists but recognition failed
- Unsupported: mechanic not modeled
- Conflict: credible observations disagree

Missing or INDETERMINATE must never be treated as contradiction merely to remain conservative.

## 1.4 Identity invariant

Species, form, and variant are separate.

- species can be known while form is unknown
- species/form can be known while shiny/shadow/costume/lucky remains unknown
- unknown must not silently become normal/false
- a weak downstream classifier must not overwrite a strong accepted species identity

## 1.5 Passive-product boundaries

Keep the scanner passive:

- no gameplay automation
- no input injection
- no Pokémon GO memory/process access
- no root requirement
- no security bypass
- no private Pokémon GO endpoints
- no account scraping/automation
- preserve MediaProjection and overlay consent/safety
- no runtime screenshot/OCR upload without separate explicit approval and privacy/security review

---

# 2. Authority, source-of-truth, and branch policy

## 2.1 Authority order

Every coding agent must read:

1. current user instruction
2. live root AGENTS.md
3. live docs/CURRENT_RECOGNITION_MISSION.md
4. live docs/POKERARITY_IMPLEMENTATION_PLAN.md
5. live repository state and measured evidence
6. historical reports only as supporting evidence

## 2.2 Mandatory live preflight before every code task

Verify and report:

- origin URL / repository identity
- origin/main full SHA
- fix/recognition-recovery full SHA
- recovery ahead/behind
- open PRs
- latest relevant CI/check state
- current plan blob SHA
- current branch/HEAD
- worktree/staging state
- target files changed since prior checkpoint

Never use uploaded snapshots to make current repository claims when live GitHub is available.

## 2.3 Branch strategy

- main is authoritative; no direct implementation commits
- fix/recognition-recovery is the long-lived integration branch
- create short-lived slice branches from current recovery HEAD
- open slice PRs into fix/recognition-recovery
- merge only after review and required CI
- after a full phase is complete, open reviewed recovery -> main PR
- after each completed phase merges to main, update this plan through a separate reviewed docs-only PR
- resynchronize recovery with main without losing unrelated work

Do not rewrite shared branch history unless branch ownership is explicitly confirmed. Prefer a non-destructive merge from updated main when uncertain.

## 2.4 PR #54

PR #54 is development-truth evidence only.

- do not modify it during unrelated work
- do not treat it as a fresh final holdout
- do not merge it implicitly as part of another task

---

# 3. Evidence taxonomy and validation pools

Every result must be identified as:

- GROUND_TRUTH
- BENCHMARK_OUTPUT
- SYSTEM_OUTPUT
- INFERENCE
- UNKNOWN

Calcy/Poke Genie agreement is benchmark agreement, not accuracy.

Maintain three separate data roles:

1. development/regression truth
2. controlled beta evaluation
3. frozen final production holdout

A case must not silently move from development data into final holdout.

Minimum metrics for recognition-affecting work:

- confidently-wrong species count
- uncertainty/retry count
- automatic usable coverage
- form accuracy separately
- variant accuracy separately
- screen-state correctness
- field-read success
- candidate-set size / ambiguity
- capture-to-visible-result latency
- stage timings
- memory/CPU when material
- dropped/stale/duplicated/out-of-order request results

Compilation alone never proves recognition correctness.

---

# 4. Target pipeline

~~~text
capture request
-> bounded request/session ownership
-> frame acquisition
-> cheap screen-state routing
-> persistent calibrated geometry validation
-> targeted field extraction
-> typed field evidence
-> common species/form candidate evaluation
-> level/stat/game-domain feasibility
-> optional visual challenger / variant evidence
-> confidence + partial-result decision
-> stale-result publication guard
-> overlay / persistence
~~~

Core design rules:

- route before expensive OCR
- calibrate once; validate cheaply each scan
- crop before recognition
- read the cheapest/highest-information evidence first
- skip fields already resolved
- apply all supported constraints to the same candidate set before acceptance
- unavailable evidence is not contradiction
- use game-domain feasibility bidirectionally
- allow partial results
- weak late stages may not overwrite stronger identity
- improve latency by removing work, not by hiding correctness checks

---

# 5. Phase 1 — Identity core and evidence semantics

**Phase status:** MERGED — completed through PR #59 at main `f7a73a43dc210c3090a73a6d5cd7c561ed6d14e3`.

## 5.1 Objective

Remove branch-dependent species acceptance and establish one common auditable identity contract before adding more recognition authority.

## 5.2 Common candidate/profile evaluator

Primary areas:

- FamilySpeciesResolver
- RecognitionObservation
- RecognitionProfiles
- feasibility helpers
- resolver tests / existing evaluator

All supported evidence must be applied to the same form-profile candidates before species acceptance, where available:

- candy/family
- complete type evidence
- CP
- max HP
- current HP only for validity/range, never base-stat identity
- visibly anchored power-up stardust
- ordinary EVOLVE action/cost
- status modifiers only when independently supported

No branch may accept a species before all applicable supported constraints have been evaluated.

### Preserve form-level candidates

Do not collapse form profiles prematurely. Canonical species projection happens after candidate evaluation.

### Minimum typed evidence

Use a bounded representation equivalent to:

- Read(value, provenance)
- Alternatives(values, provenance)
- Missing(cause)
- Conflict(alternatives)
- Unsupported(reason)

Do not build a generic SAT/CSP/evidence-graph framework unless later measurements prove this insufficient.

### EVOLVE semantics

Distinguish:

- ordinary EVOLVE observed
- cost observed
- action unseen
- visible but unreadable
- unsupported mechanic
- impossible/contradictory cost

Null mechanics must not mean terminal evolution.

## 5.3 Incumbent/challenger comparison

Keep incumbent behavior available while the new evaluator is tested.

Record:

- incumbent result/candidates
- challenger result/candidates
- constraints applied
- constraints unavailable
- first divergence
- improvement/regression/unresolved

Do not route production through the challenger until counterexamples and regression evidence support it.

## 5.4 Required counterexamples

At minimum:

- Weedle CP150 / maxHP61 + EVOLVE 12
- same with EVOLVE absent
- same with EVOLVE 999
- Torchic early-return contradictions
- Farfetch'd positive control
- Cascoon/Silcoon real ambiguity
- Chimchar/Torchic wrong-candy limitation
- Nidoran ambiguity
- Ho-Oh / HoOh normalization
- Poliwrat-style corrupted observation
- metapo-style observation
- nickname-only observations
- cross-family conflicts
- close-candidate conflicts

No Weedle-specific hardcoded fix.

Fresh Weedle trace work may run in parallel. Use oracle-fields vs OCR-fields and incumbent-vs-challenger to locate the first failing layer.

## 5.5 Recovery-specific gate semantics

After the common evaluator is stable, adapt the validated gate behavior to recovery.

Do not blindly transplant the main-based patch.

### Must hard-block

- insufficient authority
- accepted identity != authoritative identity
- authority conflict
- observations disagree
- cross-family conflict
- close candidates
- NO_MATCH / UNCERTAIN
- weak fuzzy without independent corroboration

### Must hard-block negative profile evidence

- CONTRADICTORY
- IMPOSSIBLE

### Must not fabricate positive support

INDETERMINATE may continue to normal scoring only when strong identity authority already exists and no conflict exists.

INDETERMINATE must:

- add zero positive profile score
- remain diagnostically unavailable
- never become COMPATIBLE
- never upgrade weak authority

Keep MISSING conservative until dedicated evidence supports different semantics.

## 5.6 Phase 1 validation

Required:

- narrow resolver/gate tests
- full unit suite
- detekt/lint without baseline regeneration
- debug build
- all named counterexamples
- no new confidently accepted wrong species
- same preserved exact-frame replay
- no species regression on exact frames
- strong identity no longer rejected solely because optional profile evidence is unavailable
- weak/conflicting identity still blocked

## 5.7 Phase 1 exit gate

Phase 1 is complete only when:

- all branch-dependent acceptance paths use the common evaluator
- candidate/constraint traces are inspectable
- unavailable vs contradictory semantics are explicit
- adversarial accepted-wrong regressions remain zero
- Phase 1 recovery -> main PR is reviewed and merged

Then update this plan in a separate docs-only PR.

---

# 6. Phase 2 — Screen understanding and trustworthy evidence acquisition

**Phase status:** IN_PROGRESS — unblocked by the Phase 1 merge. Phase 2A/2B/2D/2E are not started; existing Phase 2C/2F foundations are partial and must be revalidated from latest main.

## 6.1 Objective

Reliably identify the current Pokémon GO screen, maintain trustworthy geometry, and extract typed evidence from correct regions.

## 6.2 Screen-state router

Recognize at least:

- detail
- scrolled detail
- appraisal
- storage/list
- encounter/map/non-detail controls covered by tests
- transition/unstable
- ignore/unknown

Rules:

- cheap pixel/anchor features first
- content validation second
- expensive OCR only after a plausible state
- ignore/non-detail states must not enter normal species OCR
- scrolled state requires content corroboration

Known controls map -> Encounter and list -> detail must be explicitly corrected or safely rejected.

## 6.3 Persistent calibration + autoconfig

Independently implement persistent geometry.

Store per compatible display configuration:

- screenshot dimensions
- orientation
- density/inset-relevant signature
- anchor geometry
- derived field rectangles
- calibration revision
- validation health counters

Per scan:

1. compatibility check
2. cheap anchor/config validation
3. use calibrated ROIs if healthy
4. targeted recalibration for drifting sub-regions
5. full autoconfig only when necessary

Invalidation must consider:

- resolution/orientation changes
- incompatible screenshot dimensions
- schema/app revision
- repeated crop-validation failures
- major layout drift

Do not copy competitor rectangles or thresholds.

## 6.4 Targeted structured extraction

Priority fields:

1. EVOLVE action/cost
2. POWER UP association
3. stardust/power-up cost
4. CP including valid values below 100
5. HP/maxHP
6. candy/family
7. type
8. appraisal when visible

Must cover known defects:

- merged/split EVOLVE text
- cost belongs to the correct action
- stardust crop horizontal offset
- HP/Candy anchor-band drift
- CP<100 floor bug
- missing vs unreadable vs unsupported

Do not add arbitrary OCR heuristics before verifying state and crop.

## 6.5 One revisioned recognition snapshot/facade

Use one immutable recognition-domain authority for:

- canonical species
- forms
- base stats
- types
- family
- CPM
- supported recognition mechanics
- supported evolution/action costs

Rarity/event freshness remains separate.

Legacy family data must not independently veto the new recognition authority.

## 6.6 Species/form/variant contract

Make explicit:

- species: known/unknown
- form: known/ambiguous/unknown
- variant flags: true/false/unknown where appropriate

Weak variant classifiers must not silently overwrite hard species identity.

## 6.7 Bounded request ownership

Before substantial beta collection implement:

- requestId
- attemptId
- projection/session epoch
- one active logical pipeline
- at most one pending logical request
- explicit terminal outcome
- stale-result publication guard

Old results must never overwrite newer requests.

## 6.8 Phase 2 validation

Required:

- screen-state fixture tests
- calibration compatibility/invalidation tests
- ROI bounds/non-empty assertions
- resolution/orientation cases
- exact-frame replay
- per-field extraction matrix
- overlapping/stale-request tests
- Samsung S25 debug validation when available
- privacy/network boundaries unchanged

## 6.9 Phase 2 exit gate

Complete only when:

- wrong-screen OCR is structurally prevented for known controls
- geometry is persistent, validated, and self-healing
- known EVOLVE/stardust/CP/HP/candy defects are addressed
- recognition data has one authority
- stale publication is prevented
- Phase 2 recovery -> main PR is reviewed and merged

Then update this plan separately.

---

# 7. Phase 3 — Level/stat validation, partial results, latency, and ambiguity technology

**Phase status:** BLOCKED_ON_PHASE_2 for production integration; isolated feasibility work may proceed.

## 7.1 Objective

Add the missing evidence and scheduling architecture needed for useful level/IV output, partial results, and materially faster warm scans.

## 7.2 Stardust level-window oracle

Build from approved/public project data.

Input:

- visibly anchored power-up stardust
- independently supported modifiers only

Output:

- legal level window/set
- invalid/unreadable/unsupported status
- provenance

Invalid dust must not become a guessed level.

## 7.3 CP/HP/species/level feasibility

Evaluate legal tuples:

~~~text
(species/form candidate, level candidate, CP, maxHP, optional appraisal)
~~~

Prune both impossible levels and impossible species/forms.

Preserve ambiguity when multiple tuples remain.

## 7.4 New forward-model arc fitter

Do not wire or repair the old degenerate detector.

Build independently with these characteristics:

- arc geometry comes from calibrated UI
- model expected arc progression as a continuous parameter
- score observed arc pixels against that model
- return value/range/alternatives/unknown, not a forced scalar
- validate against stardust + CP/HP feasibility
- fail safely under occlusion

Do not copy competitor equations, constants, or thresholds.

## 7.5 Appraisal bars as geometric evidence

Evaluate pixel-fill/bar-length reading before adding more OCR.

Use it to narrow IV/level candidates, not to fabricate identity.

## 7.6 Partial-result model

Represent:

- exact
- range
- discrete alternatives
- unknown
- unsupported

Examples:

- Level 13
- Level 13–14
- IV range
- species known / form unknown

One missing field must not invalidate an otherwise useful scan.

## 7.7 Field scheduling and early exits

Order work by information value and cost:

1. route state
2. validate calibration
3. read cheap/high-value identity evidence
4. stop identity OCR when authority is sufficient
5. read only fields needed for result quality
6. escalate hard fields only as needed

Explicitly test whether anchored candy/family evidence can avoid a separate species-name pass.

Goal: remove work, not merely parallelize everything.

## 7.8 Numeric OCR/provider experiment

Do not switch OCR engines by imitation.

Compare:

1. current ML Kit baseline
2. calibrated small-ROI ML Kit
3. alternative local numeric/text recognizer only if 2 is insufficient
4. native preprocessing only where profiling proves a bottleneck

Measure:

- field correctness / benchmark agreement where truth unavailable
- false-read rate
- latency
- memory
- complexity

No provider migration without measurable benefit.

## 7.9 Visual challenger decision

After structured identity stabilizes compare:

- structured-only
- structured + visual challenger/fallback
- visual retrieval + structured verification

Measure:

- candidate recall
- ambiguity recovery
- wrong-family challenge detection
- false-accept impact
- Android latency/RAM
- shortcut/background/text leakage

Vision gains production identity authority only if independent evidence supports it.

## 7.10 Multi-frame pixel merge

PRS already has frame fusion.

Add per-field pixel merge only if:

- calibrated per-frame reads fail
- the field is experimentally shown to benefit
- accuracy gain exceeds latency/complexity cost

Never make merge the default first pass without evidence.

## 7.11 Performance protocol

Calcy's observed ~0.5 s warm UX is a benchmark, not a copied hard target.

Measure PRS:

- request -> first frame
- routing/calibration
- each field read
- candidate evaluation
- level/IV validation
- final decision
- overlay visible

Report warm P50 and P95.

No latency optimization may lower identity precision.

## 7.12 Phase 3 exit gate

Complete only when:

- level is exact/range/unknown rather than forced
- stardust + CP/HP feasibility are live
- arc fitting is validated or safely disabled
- partial results render correctly
- scheduling measurably removes unnecessary work
- OCR/vision decisions are evidence-based
- accepted-wrong regression remains zero on required corpora
- warm S25 latency is measured end-to-end
- Phase 3 recovery -> main PR is reviewed and merged

Then update this plan separately.

---

# 8. Phase 4 — Controlled beta and production confidence

**Phase status:** BLOCKED_ON_PHASES_1_TO_3.

## 8.1 Development truth

PR #54 may later be integrated into development/regression evaluation through a dedicated reviewed task.

It is not a fresh final holdout.

## 8.2 Controlled beta

Use diverse independent encounters to expose architecture failures:

- low/high CP
- damaged/full HP
- normal/scrolled detail
- evolution/family ambiguity
- shadow/purified/lucky where supported
- forms/costumes where available
- appraisal visibility states
- multiple display conditions where available
- rapid/retry request patterns

Measure precision and coverage together.

Do not claim production-level confidence from a small beta sample.

## 8.3 Frozen production holdout

Before final confidence claims:

- freeze implementation
- freeze tuning
- identify untouched independent holdout
- record provenance/truth source
- run the final holdout without tuning against it

Report:

- species precision
- confidently-wrong count
- automatic coverage
- uncertainty/retry
- form accuracy
- variant accuracy
- partial-result correctness
- failure categories

Do not dilute a systematic error with easier cases.

## 8.4 Lifecycle/performance validation

On real Samsung where available:

- warm P50/P95 capture-to-visible-result
- repeated scans
- overlapping requests
- retry during new request
- projection/session epoch changes
- background/foreground
- memory/CPU stability
- zero stale/out-of-order overlay publication

## 8.5 Release/privacy/security gate

Only in explicit release phase:

- signed release artifact
- API/SDK checks
- 16 KB runtime/artifact compatibility where relevant
- release logging review
- cache/diagnostic retention review
- telemetry privacy/consent
- no raw OCR/screenshot/local-path leakage
- DB migration/integrity
- metadata freshness behavior
- signed-release MobSF/security rescan when authorized

## 8.6 Final readiness state

Must be one of:

- PRODUCTION_READY
- BETA_ONLY
- ENGINEERING_INCOMPLETE
- BLOCKED_BY_EXTERNAL_DEPENDENCY

No vague success wording.

---

# 9. Dependency graph

~~~text
Docs plan publication
        |
        v
PHASE 1: Identity core + evidence semantics
        |
        v
PHASE 2: Screen routing + calibration + trustworthy extraction
        |
        v
PHASE 3: Level/game-math + partial results + performance + vision decision
        |
        v
PHASE 4: Controlled beta + frozen holdout + release validation
~~~

Allowed parallel work:

- fresh Weedle trace during Phase 1
- isolated screen/calibration research during Phase 1, without production authority
- visual feasibility after Phase 1 identity contract stabilizes
- release/privacy inventory early, but release changes remain Phase 4

---

# 10. Explicitly prohibited shortcuts

Agents must not:

- hardcode individual Pokémon to patch generic resolver failures
- interpret null mechanics as terminal evolution
- treat benchmark output as ground truth
- loosen confidence globally to raise coverage
- wire the existing broken arc detector
- switch to Tesseract solely because Calcy uses it
- copy Calcy code/constants/thresholds/assets/traineddata/DB/native libs
- build a generic solver framework before bounded evaluation proves insufficient
- rewrite the whole OCR stack before crop/state evidence requires it
- let weak variant logic overwrite strong species identity
- mix unrelated UI redesign with recognition work
- regenerate lint/detekt baselines to hide findings
- commit screenshots/device artifacts/build outputs
- run release builds during ordinary implementation
- modify PR #54 opportunistically
- tune against the final holdout
- claim recognition is fixed because the app builds
- claim current repo state from stale snapshots

---

# 11. Failure-first debugging protocol

For any failure:

1. reproduce on a trustworthy frame
2. identify the earliest wrong stage
3. classify: state / calibration / crop / unreadable field / parser / candidate generation / feasibility / evidence conflict / confidence / lifecycle / unsupported mechanic
4. compare oracle-fields vs OCR-fields
5. form the smallest testable hypothesis
6. add/extend regression coverage when practical
7. fix the earliest wrong stage, not a downstream symptom
8. rerun narrow tests
9. rerun relevant exact-frame/device regression
10. inspect accepted-wrong behavior
11. broaden validation only after the local cause is resolved

If exact-frame replay succeeds but live scanning fails, investigate capture/timing/lifecycle before core recognition.

---

# 12. Standard agent checklist

## Preflight

Report:

- main SHA
- recovery SHA
- ahead/behind
- open PRs
- CI/check context
- plan blob
- worktree/branch state
- target-file delta

## Before editing

State:

- exact plan subsection
- allowed/likely files
- explicit non-goals
- measurable acceptance criteria
- reproducing test/fixture

## Validation order

1. narrow tests
2. relevant adversarial/regression tests
3. full unit suite
4. static analysis
5. debug build
6. exact-frame replay where relevant
7. real-device validation where required
8. CI after PR creation

## Final report

Include:

- files changed
- test/build results
- before/after measurements
- regressions checked
- known remaining issues
- plan subsection status
- exact next dependency
- USER ACTION REQUIRED: YES/NO

---

# 13. Progress ledger

Allowed status values:

- NOT_STARTED
- IN_PROGRESS
- VALIDATED_LOCAL
- IN_REVIEW
- MERGED
- BLOCKED
- DEFERRED

| Work item | Status | Note |
|---|---|---|
| Calcy deep architecture audit | MERGED | Evidence-only research record; sufficient for independent design, with no competitor implementation copied |
| Main-based gate-semantics experiment | VALIDATED_LOCAL | Historical evidence retained; its recovery-specific adaptation is now merged in Phase 1D |
| Phase 1A common candidate evaluator | MERGED | One row-preserving evaluator applies supported constraints before canonical projection |
| Phase 1B incumbent/challenger harness | MERGED | Fulfilled through test-first characterization, before/after replay, and candidate-evaluation traces; no permanent dual production path retained |
| Phase 1C Weedle/counterexample suite | MERGED | Named counterexamples and adversarial authority/profile regressions are pinned in tests |
| Phase 1D recovery gate semantics | MERGED | INDETERMINATE vs negative evidence, hard-authority composition, fusion, reconciliation, and fail-closed guards merged |
| Phase 1 integration to main | MERGED | PR #59 squash-merged as `f7a73a43dc210c3090a73a6d5cd7c561ed6d14e3`; final pre-merge suite 796/0 and S25 exact-frame replay unchanged |
| Phase 2A screen-state router | NOT_STARTED | Anchored detail detection exists; full routing incomplete |
| Phase 2B persistent calibration/autoconfig | NOT_STARTED | — |
| Phase 2C structured extraction fixes | IN_PROGRESS | Anchored extraction exists; known gaps remain |
| Phase 2D recognition snapshot/facade | NOT_STARTED | Profiles exist; single authority incomplete |
| Phase 2E species/form/variant contract | NOT_STARTED | — |
| Phase 2F request ownership | IN_PROGRESS | Busy handling exists; requestId/attemptId/session epoch absent |
| Phase 2 integration to main | NOT_STARTED | — |
| Phase 3A stardust level-window oracle | NOT_STARTED | — |
| Phase 3B CP/HP/species/level feasibility | IN_PROGRESS | Same-witness foundations exist |
| Phase 3C new arc forward-model fitter | NOT_STARTED | Old detector prohibited |
| Phase 3D appraisal pixel reader | NOT_STARTED | — |
| Phase 3E partial-result model/UI | NOT_STARTED | — |
| Phase 3F field scheduler/early exits | NOT_STARTED | Frame-level early exit exists |
| Phase 3G OCR provider experiment | NOT_STARTED | Keep ML Kit until measurement says otherwise |
| Phase 3H visual challenger pilot | NOT_STARTED | Visual authority remains restricted |
| Phase 3I multi-frame pixel merge | DEFERRED | Only if earlier work demonstrates need |
| Phase 3 integration to main | NOT_STARTED | — |
| Phase 4 PR #54 development-truth integration | NOT_STARTED | PR #54 untouched |
| Phase 4 controlled beta | NOT_STARTED | — |
| Phase 4 frozen holdout | NOT_STARTED | — |
| Phase 4 release/privacy/security validation | NOT_STARTED | — |

---

# 14. Immediate next actions after this plan merges

Phase 1 is closed. Start Phase 2 from the latest main tree; do not continue work on the old Phase 1 recovery ancestry.

1. re-verify latest main SHA, open PRs, main CI, this plan blob, and the exact target-file delta
2. create a fresh Phase 2 integration branch/worktree from latest main
3. begin with a bounded Phase 2A screen-state-router slice: known detail/scrolled-detail vs map/list/transition/unknown must route or fail closed before expensive species work
4. preserve the Phase 1 identity/gate contract unchanged while adding routing evidence
5. add explicit fixtures for the known map/list misclassification controls and unstable/transition screens
6. validate the slice with narrow tests, the full JVM suite, detekt/lint/build, and the preserved exact-frame corpus
7. once routing is stable, implement Phase 2B persistent calibration/autoconfig as a separate reviewed slice
8. then address Phase 2C structured-extraction defects (EVOLVE association, stardust offset, CP<100, HP/candy drift) against calibrated/state-validated regions
9. follow with Phase 2D recognition-snapshot/facade and Phase 2E species/form/variant contract; legacy data must not independently veto the recognition authority
10. complete Phase 2F bounded request ownership before substantial beta collection, then open the Phase 2 integration PR to main and update this ledger through another separate docs-only PR

The first Phase 2 slice must not include a new arc detector, Tesseract migration, visual species authority, DB redesign, telemetry transport changes, rarity-formula changes, or release work.

---

# 15. Recognition-mission completion standard

Do not declare the mission complete until:

- species identity uses one coherent evidence contract
- strong evidence is not discarded because optional evidence is missing
- contradictory evidence remains fail-closed
- screen state is routed before expensive recognition
- geometry is calibrated/validated rather than brittle
- important structured fields use typed status
- level/stat feasibility is live and independently validated
- partial results are useful instead of all-or-nothing
- warm scan latency is measured and materially improved
- request lifecycle prevents stale/out-of-order publication
- independent encounters show high useful coverage and extremely low confidently-wrong species
- form/variant behavior is measured separately
- final confidence claims use a frozen independent holdout
- the signed release artifact passes privacy/security/release validation

Only then may the project declare PRODUCTION_READY if the final evidence supports it.
