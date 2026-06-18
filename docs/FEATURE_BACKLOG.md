# Feature Backlog

Small, safe next slices (prioritized by risk/reward):

## High Priority
- [x] Dead code removal in `ScanFrameFusion.shouldRunDetailedPass` (done)
- [ ] Boundary tests for `ScanConsistencyGate` — cross-family confidence thresholds
- [ ] Boundary tests for `ScanFrameFusion.frameScore` — tie-breaking by CP quality
- [ ] Test for `SpeciesRefiner.isBlockedFamilyDowngrade` — verify all 12 pairs covered

## Medium Priority
- [ ] Regression test for `RarityCalculator.validateAndFixCP` — mathematical CP fallback
- [ ] Edge case: empty `pokemonNames` fallback doesn't crash in `TextParser`
- [ ] Test for `ScanManager.cleanOldScreenshots` — exactly-20 boundary
- [ ] Telemetry opt-out removes pending queue entries (`OfflineTelemetryDao`)

## Lower Priority
- [ ] Document `SpeciesRefiner` 15+ override conditions with ASCII decision tree
- [ ] Telemetry payload audit — verify no nickname leakage in `rawOcrText` field
- [ ] RateLimiter stress test — verify 10/min cap holds under concurrent calls

Avoid broad pipeline rewrites until focused reliability gaps are covered above.

## Closed Items
- Dead code removal in `shouldRunDetailedPass` — fixed in this run
