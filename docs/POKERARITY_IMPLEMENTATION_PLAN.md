# PokémonRarityScanner — Authoritative Recognition Integration Plan

**Plan revision:** 2026-10-09 — Phase 3 interim evidence checkpoint (Phase 3 remains open)  
**Repository:** https://github.com/chaglaruk/PokemonRarityScanner  
**Authoritative path:** docs/POKERARITY_IMPLEMENTATION_PLAN.md  
**Active mission:** docs/CURRENT_RECOGNITION_MISSION.md  
**Implementation base:** latest origin/main; Phase 3 integration work must branch from the post-Phase-2 main tree

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

### Phase 2 closure update — 2026-10-06

Phase 2 has now passed its exit gate and is merged to main.

- Phase 2 slice PRs: #62 (2A), #63 (2B), #64 (2C), #65 (2D), #66 (2E), #67 (2F)
- Phase 2 integration PR to main: #68
- final Phase 2 integration head before main merge: `5169c680cb7a94ca30e881a92086224f03fb3d7b`
- merged main SHA: `fe3b6f742f9dabeb026a700e415000256cefa893`
- final Phase 2F slice head: `b38d7390319d8cc001d68db10f1f72d9982766ee`
- final Phase 2 JVM suite before merge: 1004 tests, 0 failures/errors
- detekt: 0 findings; lintDebug, debug build, and androidTest build: successful
- Samsung S25 preserved 17-frame replay: accepted-detail 7 -> 7 with unchanged accepted species; N01/N02/X07 OCR = 0 and fail-closed; F06/X06 remained blocked; X05 EVOLVE cost remained 25; 0 new confidently-wrong species and 0 control acceptances
- persistent calibration cold/warm validation remained healthy, with warm reuse requiring 0 rebuilds on the preserved corpus
- one pinned revisioned RecognitionSnapshot is now the recognition-domain authority; legacy family data no longer independently vetoes recognition
- explicit species/form/variant identity preserves unknown/ambiguous states and prevents weak cross-species variant evidence from overwriting the locked species
- bounded request ownership now carries requestId/attemptId/projection epoch/capture sequence, enforces one active plus at most one pending logical request, and prevents stale/old-attempt overlay/save/telemetry/error publication
- Phase 2 integration PR checks: Run Tests, CodeQL, SonarCloud, Semgrep, and dependency submission passed; the dynamic GHAS AI helper on PR #68 was externally blocked by Copilot monthly quota (HTTP 402) while CodeQL itself was green
- post-merge main checks: Run Tests, CodeQL, SonarCloud, Semgrep, and dependency submission passed
- an independent Dependabot security-update job failed because its requested dependency names were not present in the submitted dependency snapshot; this is separate repository security-maintenance work, not a Phase 2 recognition regression
- PR #54 remains unrelated and untouched

This closes Phase 2. Phase 3 production integration is now unblocked. Controlled beta/frozen-holdout production-confidence work remains later Phase 4 work.

### Phase 3 interim evidence checkpoint — 2026-10-09

This is an **interim implementation-priority correction**, not a Phase 3 closeout, production release approval, or assertion that Phase 3 code is on `main`. The 2026-10-09 PokéGenie REA/JADX and Calcy IV reconciliation reports are dated external research evidence; their original APKs, proprietary code, raw decompilation, and local investigation bundle are not repository inputs or implementation sources. Findings below were cross-checked against live main source; their *runtime improvement* is not yet measured.

**Verified live repository baseline for this checkpoint**

- `origin/main`: `edd5793f9774abedc5a5b5904dbcabf6e052b68b`, a 2026-10-09 living-metadata refresh.
- Current plan blob before this amendment: `abb091d0e0dff8b64780fc63735fedfec990eca0`; latest prior plan commit: `f1c57a2d03e4072223c29484e113e2de79ad034c`.
- Only open PR: #54, human-reviewed *development* fixture truth; leave it untouched and do not reclassify its data as a frozen holdout.
- Phase 3 integration branch `fix/recognition-phase3`: `ce7fc911601fa6aaa17b9fd551c82373bca3d03b`; **18 ahead / 1 behind** current main. The one main-only commit refreshes metadata/data; audit that delta and its snapshot/solver compatibility before incorporating it.
- Phase 3A PR #72, Phase 3B PR #73, and Phase 3C PR #74 were merged **into the Phase 3 integration branch only**, not main. They provide a typed stardust level-window oracle, same-witness CP/HP/level feasibility and a **DIAGNOSTIC_ONLY** forward arc fitter. Review them; never reimplement the same subsystems from scratch or promote arc to species authority without evidence.
- `phase3d/appraisal-bar-geometric-evidence` currently points to the same `ce7fc911...` integration head; branch creation does **not** count as an implemented Phase 3D slice. No Phase 3D PR is open at this checkpoint.
- On exact current main, SonarCloud and automatic dependency submission checks succeeded. The latest relevant `Run Tests` main run is not at `edd5793f...`; latest Phase 3C `Run Tests` and CodeQL results at `705693027e7d944fcf1690684f5ca86779a08731` succeeded. Do not describe any of these as a fresh full-suite pass at the new main/data revision.

**Research scope and limits**

- The PokéGenie 8.19.0 APKM study inventoried 36 APKs and 2,685 inner entries, with structural analysis of 481 emitted source files, **28 selected files receiving manual method review**, 28 native library surfaces surveyed, and 34 protected payloads left opaque. These totals do not establish full semantic reconstruction.
- Neither competitor was freshly run against the current device in this research. Static code observations are not benchmarked accuracy, timing, or runtime function-call evidence. No competitor source, assets, models, thresholds, native libraries, game databases, or proprietary training data may enter this repository.
- The historical Calcy IV CP fallback belongs to the `WhiteTextAllScreensMax` call path, not `MergeEqual` as previously attributed; a historical CPU calculation must include 19 omitted system ticks (44 rather than 25 total), with unknown tick rate and stage attribution. Do not justify native migration or a warm latency budget from that evidence.

**Verified main-code gaps to address before broad new evidence authority**

1. **Action resource-role provenance (S03):** `ActionRowAssociation.costEvidence` accepts one aligned numeric amount without distinguishing candy, special item, XL candy, mega energy or an inventory count. Historical F06/X06 Applin evidence includes item quantity 20 alongside candy cost 200 and a rejected evolution constraint. Reproduce on exact current source before claiming the original failure persists; in all cases, an item amount must never become a trusted `evolutionCandyCost`. A special-item/multi-resource/occluded value with no verified association remains UNREADABLE/UNSUPPORTED, never fabricated READ. Keep the existing contradiction gate and independent positive-species requirement.
2. **Document/provider failure provenance (S04):** `MLKitOcrProvider.recognizeDocument` maps OCR task failure to null; `recognizeLayout` then produces an empty layout. A provider failure, a successful empty OCR result, visibility-based absence and cancellation need distinct bounded statuses. Reuse the existing typed `FieldRead` contract and request/attempt/epoch ownership; do not create a second owner or erase retry/terminal semantics.
3. **Ordinary-form evidence wiring (S05):** current anchored recognition does not populate the legacy `speciesResolverTrace.formCandidates` path used by the final identity factory. Preserve the canonical species lock; carry trusted per-form surviving witness evidence through a dedicated same-species form decision with KNOWN/AMBIGUOUS/UNKNOWN outcomes. Never revive weak global nickname ranking, use costume hints to rewrite species, or force a form on an inconclusive family.
4. **Unmeasured input/retry hypothesis (S01/S02):** `ScanManager` downsizes full frames over 900 px before current ML Kit OCR; the optional detailed pass repeats the same provider, geometry and transform. Do **not** remove either policy by inspection alone. Run paired identical-image comparisons of baseline 900, native whole frame, native field ROI and changed-information ROI retries; record field correctness, contradictory reads, useful upgrades, accepted-wrong, warm/cold P50/P95, OCR-call count and memory. Retain the smaller safe winner or baseline as appropriate.
5. **Phase 3D appraisal / resize (S06/S08):** appraisal IV bar extraction is not yet a production anchored source; begin geometric intervals as diagnostic evidence and validate settled/animated/occluded bars. Missing `onCapturedContentResize` handling suggests a lifecycle risk, **not a reproduced defect**. Perform passive resize/revoke/resume tests before implementation.
6. **Optional visual or temporal challenger (S07):** keep deferred until residual independently verified ambiguity supports the complexity; preserve whole-witness and same-subject boundaries. No pixel-composite or cross-frame digit synthesis becomes new species authority by analogy with competitors.

**Next engineering order (applies to Phase 3 work in progress)**

- Check exact main/branch/plan state, open PRs, CI, metadata refresh delta, dirty worktrees, staged Phase 3 source compatibility, and the existing 3D branch before editing anything.
- First run a **bounded, read-only evidence reproduction** of F06/X06 plus simple EVOLVE/power-up and negative controls at the exact source/data revision. Identify the earliest lost resource role and record provider status, bounds and obscured pixels.
- If confirmed, produce **separate reviewed extraction slices** for typed document status and action-resource association. Tests must prove no item quantity masquerades as candy, no false contradiction becomes a silent acceptance, X05 ordinary EVOLVE remains correct, and no new confidently accepted wrong species appears.
- Review/reconcile existing Phase 3A/B/C and carry their tested math contracts forward; do not merge integration to main prematurely. Then continue Phase 3D geometric appraisal and the scoped ordinary-form wiring as focused slices, keeping uncertain data nonauthoritative.
- Run the paired field-ROI/detailed-pass experiment before selecting any OCR engine, full-native pipeline or retry redesign. Preserve one pinned RecognitionSnapshot and existing request ownership.
- Only after implementation and policy are frozen, verify live Samsung capture, 30-minute session/resize/revoke, independent truth and held-out accuracy; final Phase 3 integration to main and the later phase-closeout docs-only PR still require review.

**Non-negotiable promotion criteria**

- No newly confidently accepted wrong species, control-screen acceptance, stale overlay/save publication or evidence downgraded from a real contradiction to a positive match.
- Full matched old/new regression report, genuine ground-truth denominators, explicit UNKNOWN/UNREADABLE/UNSUPPORTED, and exact source/data SHA.
- Record absolute latency/memory and changes versus a measured baseline. Proposed relative guards are evaluation criteria, not vendor-derived performance promises. A sub-second median is an aspiration only, not a demonstrated Phase 3 acceptance condition.
- Respect passive MediaProjection consent, offline scan privacy and existing telemetry boundaries.

### Historical recovery-branch foundations

The recovery branch supplied the Phase 1 foundations, which were merged to main before Phase 2. Phase 2 was then integrated through its own reviewed branch; Phase 3 must branch from the post-Phase-2 main tree:

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

At that historical checkpoint the branch was not production-ready.

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
- current phase integration-branch full SHA
- current phase integration branch ahead/behind main
- open PRs
- latest relevant CI/check state
- current plan blob SHA
- current branch/HEAD
- worktree/staging state
- target files changed since prior checkpoint

Never use uploaded snapshots to make current repository claims when live GitHub is available.

## 2.3 Branch strategy

- main is authoritative; no direct implementation commits
- use one long-lived integration branch per active phase (Phase 3: `fix/recognition-phase3`)
- create short-lived slice branches from the current phase-integration HEAD
- open slice PRs into the current phase integration branch
- merge only after review and required CI
- after a full phase is complete, open reviewed recovery -> main PR
- after each completed phase merges to main, update this plan through a separate reviewed docs-only PR
- after a completed phase merges to main, create the next phase integration branch from that new main SHA; do not carry stale phase ancestry forward

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

**Phase status:** MERGED — completed through PR #68 at main `fe3b6f742f9dabeb026a700e415000256cefa893`.

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

**Phase status:** IN_PROGRESS_ON_PHASE_BRANCH — Phase 3A/B/C integrated into `fix/recognition-phase3` only; no Phase 3 integration into main, Phase 3 exit gate remains open. See the dated interim checkpoint above.

## 7.0 Interim evidence-repair precedence

The dated Phase 3 checkpoint above governs implementation order where the original Phase 3A-first instructions are stale. Reuse already integrated 3A/B/C work. Confirm or falsify the action-resource and provider-status failures before expanding appraisal/form/level authority. ROI/detailed-retry changes require matched field and latency experiments. Keep the same-witness evaluator, locked canonical species, strict metadata authority, and bounded request publication throughout.

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

- item quantities and unrelated action-row numbers cannot become trusted evolution candy costs
- provider failures and successful empty OCR results remain distinct, with cancellation safe
- ordinary-form evidence is wired only within an accepted canonical species, with honest ambiguity
- the exact main/Phase 3 branch metadata and source revisions are reconciled and tested
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

Allowed status values (the interim `INTEGRATED_PHASE_BRANCH` value means merged into the Phase 3 integration branch, **not** origin/main):

- NOT_STARTED
- IN_PROGRESS
- VALIDATED_LOCAL
- IN_REVIEW
- INTEGRATED_PHASE_BRANCH
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
| Phase 2A screen-state router | MERGED | Wrong-screen routing occurs before OCR; known N01/N02 controls reject and X07 fails closed |
| Phase 2B persistent calibration/autoconfig | MERGED | Persistent compatible-display calibration, validation, health counters, invalidation, cold rebuild, and warm reuse merged |
| Phase 2C structured extraction fixes | MERGED | Geometry-scoped typed extraction; EVOLVE/stardust/CP/HP/candy defects and missing-vs-unreadable semantics addressed |
| Phase 2D recognition snapshot/facade | MERGED | One pinned revisioned RecognitionSnapshot is the recognition authority; legacy family data no longer independently vetoes recognition |
| Phase 2E species/form/variant contract | MERGED | Explicit known/unknown species, known/ambiguous/unknown form, tri-state variants, locked-species enrichment boundary |
| Phase 2F request ownership | MERGED | Exact-attempt bounded ownership, projection epoch, explicit terminal outcomes, stale-result publication suppression |
| Phase 2 integration to main | MERGED | PR #68 merged as `fe3b6f742f9dabeb026a700e415000256cefa893`; post-merge recognition CI green |
| Phase 3A stardust level-window oracle | INTEGRATED_PHASE_BRANCH | PR #72 merged to `fix/recognition-phase3` only; review current data assumptions before promotion to main |
| Phase 3B CP/HP/species/level feasibility | INTEGRATED_PHASE_BRANCH | PR #73 merged into integration; same-witness tuple contracts require current snapshot compatibility verification |
| Phase 3C new arc forward-model fitter | INTEGRATED_PHASE_BRANCH | PR #74 merged into integration; DIAGNOSTIC_ONLY, not production species authority |
| Phase 3D appraisal pixel reader | NOT_STARTED | Branch exists at Phase 3 integration HEAD with no implementation; start only after current-state reconciliation |
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

# 14. Immediate next actions — 2026-10-09 interim Phase 3 checkpoint

Phase 1 and Phase 2 are closed on main. **Phase 3 is in progress exclusively on its integration branch.** The older instruction to create Phase 3 anew or begin a second Phase 3A is superseded by the dated checkpoint.

1. Read latest live main, root AGENTS.md, active mission, this plan, current open PRs, exact-head checks and the existing Phase 3 A/B/C and prepared 3D branch state; report full SHAs and plan blob.
2. Reconcile the October 9 living-metadata commit (eight updated data/metadata files) with the staged Phase 3 RecognitionSnapshot and oracle/tuple tests. Do not force-push, rebase shared branches blindly or mix unrelated living-data fixes into a recognition PR.
3. Reproduce F06/X06 resource-role evidence against current source and safe development fixtures, together with X05 and ordinary EVOLVE/power-up controls. Report actual element bounds, field/provenance statuses and first incorrect decision; distinguish historical observations from today's behavior.
4. If confirmed, implement and review narrow **document-status** and **typed action-resource** extraction PR slices targeting the Phase 3 integration branch. A missing/occluded candy amount must not be invented from an item or inventory number. Keep hard contradiction and positive-identity gates.
5. Review integrated Phase 3A/B/C at their exact tested HEAD and data revision. Continue Phase 3D appraisal-bar interval evidence as a separate controlled slice; do not promote arc/appraisal to hard authority without independently validated geometric and tuple constraints.
6. Repair ordinary-form provenance with tests proving form outcomes are scoped to the accepted species, while variant/costume signals cannot overwrite species.
7. Perform matched 900/native/native-ROI/changed-information retry measurements; select improvements on verified field-level recognition and warm/cold latency/memory, not vendor imitation. Do not remove the detailed pass or migrate OCR without demonstrated benefit.
8. Preserve Phase 3E/3F partial-result and field-scheduler objectives, the existing capture/request ownership contract, and Phase 3I pixel merge deferral until evidence requires them.
9. Use fresh passive Samsung real-device tests for projection consent, capture, resizing, repeat/overlap/retry, appraisal and end-to-end P50/P95. Maintain immutable developer truth, controlled beta and frozen holdout separation.
10. Only after all Phase 3 exit gates and reviewed integration-to-main PR are satisfied, update this plan through the **separate reviewed phase-closeout docs PR**. PR #54 remains unrelated until the planned Phase 4 development-truth integration. Dependabot/security maintenance remains separate.

Do not commit competitor code/assets/models/APKs, local private screenshots or telemetry, secrets, or the bulky research bundle. Historical evidence informs independent implementation, not source copying.

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
