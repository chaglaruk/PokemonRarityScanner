# Collector Intelligence Phase 2A: Scan Pipeline Analysis

## Scope

This report maps the current scan pipeline at `8c0274b8` and identifies the
lowest-risk place to evaluate Collector Intelligence. It does not propose UI
redesign, automatic collection recording, telemetry changes, or production
wiring in Phase 2A.

## 1. Current Scan Pipeline Map

1. **Capture and handoff**
   - `ScreenCaptureService.captureSequence()` currently writes two scan PNGs in
     the app cache and broadcasts `ACTION_SCREENSHOT_READY` with their paths.
   - `ScanManager.screenshotReceiver.onReceive()` validates that every path is
     an app-cache `scan_*.png`, caps input at three paths, and then calls
     `processScanSequence()`.

2. **Pipeline coroutine and frame preparation**
   - `ScanManager` owns `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and
     serializes scans with `scanMutex`.
   - `processScanSequence()` decodes frames with child work on
     `Dispatchers.Default`. OCR then runs sequentially because the OCR engine is
     not thread-safe.

3. **OCR result selection and species arbitration**
   - `OCRProcessor.processImage()` produces `PokemonData` for each frame.
   - `ScanFrameFusion.selectBestFrame()` and `ScanFrameFusion.fuse()` choose and
     combine OCR observations.
   - `SpeciesRefiner.refine()` and `ScanConsistencyGate.evaluate()` refine or
     reject the result. The accepted value is still `PokemonData`.
   - `PokemonData` contains OCR/appraisal fields, `FullVariantMatch?`, and
     `VariantDecisionTrace?`; it does not contain `VariantIdentityKey`, dex,
     form ID, variant ID, or normalized background identity.

4. **Visual and variant analysis**
   - `VariantDecisionEngine.classify()` and `VisualFeatureDetector.detect()` run
     as `Dispatchers.Default` child tasks.
   - The classifier returns an updated `PokemonData` (`tracedBase`) with
     `FullVariantMatch` and trace data. `FullVariantMatch` exposes a final sprite
     key and confidence values, but not dex/form/variant fields directly.
   - `VariantDecisionEngine.mergeVisualFeatures()` and
     `Phase2VariantFeatureMerger.merge()` produce `scoringVisualFeatures`, the
     final `VisualFeatures` used for scoring.

5. **Rarity calculation**
   - In `ScanManager.processScanSequence()`, `PokemonRepository` resolves base
     rarity and event context.
   - `RarityCalculator.calculate()` receives `finalResult`,
     `scoringVisualFeatures`, and event inputs. It returns `RarityScore` with
     total score, tier, explanations, breakdown, confidence, and decision
     support.
   - `RarityScore.ivSolve` exists as a nullable field, but the current
     `RarityCalculator` construction path never assigns it; its private
     `analyzeIV()` is currently an unused null-result stub. Therefore
     `IvSolveDetails` is structurally available but currently null in live scan
     results.

6. **Overlay transport**
   - `ScanManager` flattens selected values into primitive `Intent` extras and
     starts `OverlayService` on `Dispatchers.Main`.
   - There is no single completed-scan aggregate at this boundary. The live
     values are separate locals: `PokemonData`, `VisualFeatures`, and
     `RarityScore`.

7. **History and telemetry after display dispatch**
   - After overlay dispatch, a child IO coroutine calls
     `PokemonRepository.saveScan()`.
   - `ScanHistoryMapper.toEntity()` reduces the live values to
     `ScanHistoryEntity`; history stores basic OCR fields, four visual flags,
     rarity score, and tier. It does not store full variant identity,
     `CollectorDecision`, or the complete `RarityScore`.
   - Telemetry is enqueued separately. Collector or collection data is not part
     of this path and must remain excluded.

8. **Overlay reconstruction and display**
   - `OverlayService.onStartCommand()` handles `ACTION_SHOW_RESULT` on the
     service/main thread.
   - `buildOverlayPokemon()` reconstructs the UI `Pokemon` model from extras,
     and `OverlayStateStore` holds it in process memory.
   - `ScanResultOverlayCard` consumes only `Pokemon`. It has no collector field
     and would continue rendering unchanged if a future field were nullable and
     ignored.

9. **Save and correction behavior**
   - The overlay Save button only shows a saved toast and dismisses the card;
     the scan was already queued for history persistence by `ScanManager`.
   - `ResultActivity.saveSnapshot()` can insert a separate basic history row,
     but it is not a correction pipeline and does not write changes back to the
     original `PokemonData`, `RarityScore`, or collection entry.
   - No manual edit/correction flow was found that feeds corrected values back
     into the active scan result. Feedback submission is telemetry feedback,
     not result mutation.

## 2. Collector Decision Input Availability

| Required input | Available now? | Source file/class | Confidence / risk | Missing work |
|---|---|---|---|---|
| `VariantIdentityKey?` | No reliable complete key | `PokemonData.fullVariantMatch`, `VariantCatalogLoader`, `VisualFeatures` | High risk if guessed. A sprite key can map to catalog dex/form/variant, but background type/label is not carried as typed identity. `hasLocationCard` is only a Boolean. | Add a tested, fail-closed resolver. Return null for ambiguous or background scans without enough identity data. Do not collapse location backgrounds. |
| `RarityScore?` | Yes | `ScanManager.processScanSequence()` result of `RarityCalculator.calculate()` | High confidence | None. Pass the existing object unchanged. |
| `VisualFeatures?` | Yes | `scoringVisualFeatures` in `ScanManager` | High confidence | None. Use the final merged features, not the provisional detector result. |
| `IvSolveDetails?` | Type yes, live value currently null | `RarityScore.ivSolve` | High confidence about current null behavior | Pass `rarityScore.ivSolve`; do not add or rewrite IV solving in this phase. |
| Scan confidence `Float` | Yes | `RarityScore.confidence` | High confidence. `RarityCalculator.calculateRarityConfidence()` clamps to 0..1, matching the decision engine input scale. | Confirm boundary behavior in an integration-focused unit test. |
| `CollectionContextLookup` / repository access | Available but not wired into `ScanManager` | `CollectionDexRepository`, `DatabaseModule`, `AppDatabase.collectionEntryDao()` | Medium risk. DAO calls are suspend functions and the scan runs on IO, but `ScanManager` is manually constructed and does not use Hilt injection. | Reuse the singleton `AppDatabase` and create one lazy repository/service in `ScanManager`, or explicitly change construction later. Avoid a broad DI refactor. |

## 3. Candidate Integration Points

### A. `ScanManager.processScanSequence()`, immediately after rarity calculation

- **Pros:** `PokemonData`, final `VisualFeatures`, `RarityScore`,
  `RarityScore.ivSolve`, and confidence coexist here. The pipeline is already on
  `Dispatchers.IO`, so Room lookup is allowed. Evaluation can happen once.
- **Cons:** A reliable identity key is still missing. Awaiting Room adds latency
  before overlay dispatch unless evaluation is deliberately isolated and
  failure-tolerant. There is no completed-scan aggregate to receive the result.
- **Risk:** Medium until identity resolution is proven; low-to-medium afterward.
- **DB access:** Safe on the current IO coroutine.
- **UI behavior:** None if the decision remains nullable and is not rendered.
- **Testability:** Identity/input mapping can be pure unit-tested. The current
  Android-heavy `ScanManager` orchestration is harder to unit-test directly.

### B. The background history-save child coroutine

- **File/function:** `ScanManager.processScanSequence()` around
  `PokemonRepository.saveScan()`.
- **Pros:** Already off the display-critical path and on IO.
- **Cons:** The overlay has already received its data, so the decision cannot be
  attached to that result without a second update path. It also risks coupling
  decision evaluation to persistence timing.
- **Risk:** Low for invisible evaluation, medium for later state synchronization.
- **DB access:** Safe.
- **UI behavior:** None initially; cannot support a single coherent result.
- **Testability:** Moderate, but the timing relationship is awkward.

### C. `PokemonRepository.saveScan()` / `ScanHistoryMapper`

- **Pros:** All current history inputs arrive together and DAO access already
  exists.
- **Cons:** Collector decision logic does not belong in history mapping. The
  variant key is still absent, and this location encourages accidental coupling
  between scan history and collection recording.
- **Risk:** Medium-high architectural and migration risk.
- **DB access:** Safe.
- **UI behavior:** No immediate update.
- **Testability:** Mapper tests are easy, but they would test the wrong boundary.

### D. `OverlayService.buildOverlayPokemon()`

- **Pros:** Direct access to the UI model.
- **Cons:** Rich scan objects have already been flattened into extras. The
  service callback runs on the main thread, identity is absent, and Room access
  here would be inappropriate. Reconstructing domain inputs from UI extras would
  duplicate and weaken scan truth.
- **Risk:** High.
- **DB access:** Not safe synchronously at this point.
- **UI behavior:** Directly affected.
- **Testability:** Poor without Android service tests.

### E. `RarityCalculator.calculate()`

- **Pros:** Score and visual inputs are present.
- **Cons:** Collection state is a separate concern, database access would make a
  pure calculator stateful, and identity is still incomplete.
- **Risk:** High and explicitly out of scope.
- **DB access:** Inappropriate.
- **UI behavior:** Indirect scoring risk.
- **Testability:** Existing calculator tests would gain unrelated dependencies.

## 4. Recommended Integration Point

Choose **Candidate A**: evaluate once in
`ScanManager.processScanSequence()` immediately after
`RarityCalculator.calculate()` and before overlay intent construction.

This is the only point where the final scan truth and rarity result coexist
before presentation and persistence diverge. It is already serialized per scan
and runs on IO, so the collection lookup does not require a thread switch. The
call must be fail-closed: unresolved identity produces the service's existing
`REVIEW` behavior, while lookup failure must not fail the scan or trigger any
automatic action.

The recommendation is conditional on a tested identity resolver. The current
pipeline must not synthesize dex/form/background identity from names or a
location-card Boolean alone.

## 5. Proposed Phase 2B Implementation Plan

Keep Phase 2B to one reviewable wiring slice:

1. Add tests first for a pure scan-to-`VariantIdentityKey?` resolver.
   - Resolve catalog dex/form/variant only from a species-consistent,
     authoritative sprite-key match.
   - Copy shiny/shadow/purified/lucky/costume flags from final
     `VisualFeatures`.
   - Return null for missing, conflicting, or ambiguous catalog identity.
   - Return null for location/special background scans until their typed label
     can be preserved; never collapse distinct backgrounds.
2. Create one lazy `CollectionDexRepository` and
   `CollectorIntelligenceService` for `ScanManager` using the existing
   `AppDatabase` singleton. Do not refactor application-wide DI.
3. After `RarityCalculator.calculate()`, call `evaluateScan()` once with the
   resolved key, `rarityScore`, `scoringVisualFeatures`,
   `rarityScore.ivSolve`, and `rarityScore.confidence`.
4. Keep the resulting `CollectorDecision?` internal and nullable in Phase 2B.
   Do not add unused Intent extras or a visible overlay element. If a concrete
   internal completed-scan model is introduced, it must only group the existing
   final values and the nullable decision; avoid refactoring the whole pipeline.
5. Treat resolver or collection lookup failure as no decision / manual review,
   without failing OCR, rarity display, or history save.
6. Add focused tests for exact identity mapping, unresolved identity, lookup
   failure, low confidence, duplicate XXL/XXS, and one-call evaluation. Then run
   the full unit suite and `assembleDebug`.

Adding `collectorDecision` directly to the UI `Pokemon` model is not recommended
in Phase 2B. It would create a data-model-to-domain dependency and still require
new Intent serialization. Add UI transport only when a later phase has a real
consumer; the current overlay already tolerates absence because it knows nothing
about the field.

## 6. Risks and Unknowns

- **Identity is the blocker.** `VariantIdentityKey` requires dex, form, variant,
  and typed background identity. The live result does not expose all of them.
- `FullVariantMatch.finalSpriteKey` may be absent for valid base scans. The
  report does not assume catalog resolution succeeds in those cases.
- Location background detection is Boolean-only at the scan boundary. No safe
  location label source was found.
- `RarityScore.ivSolve` is currently null in production scoring. Phase 2B must
  preserve that nullable behavior and must not turn IV work into the feature.
- `ScanManager` is created manually in `PokeRarityApp`, despite Hilt providers
  for the collection repository. A DI conversion would expand scope and is not
  justified for this slice.
- A synchronous collection query before overlay display adds database latency.
  It should be measured, and failures must be isolated. Current lookup performs
  one count query and only fetches entries for duplicate XXL/XXS checks.
- No manual correction stream exists. A later correction feature would need to
  explicitly recompute identity and decision rather than mutate stale output.
- History and collection persistence are separate. The history insert ID is not
  currently captured by `ScanManager`, and no automatic collection-recording
  moment exists.

## 7. Explicit Non-Goals for Phase 2B

- No UI redesign.
- No overlay rewrite or visible collector badge.
- No automatic collection recording unless separately specified and proven safe.
- No old `scan_history` backfill.
- No `RarityCalculator` rewrite.
- No `rarity_rules.json` changes.
- No telemetry or collector-data upload.
- No network calls.
- No Pokemon GO login.
- No gameplay automation, scraping, root access, memory reading, or input injection.
