# Device Verification Plan for Recognition Recovery

## Status
- Implementation: Complete (fix/recognition-recovery branch)
- Compilation: Successful
- Unit tests: Prepared (710 tests)
- APK built and installed: PokeRarityScanner-v1.10.0-debug.apk
- App launched: Successfully on Samsung Galaxy S25

## Verification Steps

### Phase 1: Permission Setup (User Action Required)
1. Open Settings > Apps > Poke Rarity Scanner
2. Grant the following permissions:
   - Display over other apps (overlay)
   - MediaProjection (for screen capture)
3. Keep the app open in background

### Phase 2: Live Pokemon GO Testing (User Action Required)
1. Open Pokemon GO and navigate to a Pokemon detail screen
2. Tap the scanner overlay to trigger screen capture
3. Wait for recognition result
4. Repeat with different Pokemon (target: 10-15 different Pokemon)

### Phase 3: Validation Metrics to Collect
For each test case, record:
- Pokemon species shown
- Scanner result (recognized species or "Uncertain")
- Confidence indicator
- Whether result is correct or incorrect

### Expected Results
Based on previous measurements:
- Correct recognition: 93% (14/15 on confirmed corpus)
- False positives (confidently wrong): 0 (target maintained)
- Uncertain rate: ~7%
- Latency: 400-500ms

### Quality Criteria for Acceptance
1. No confidently wrong species identifications
2. At least 85% correct identification rate on readable screens
3. Latency < 1 second
4. No crashes or permission errors
5. Consistent behavior across multiple scans

## Known Limitations
- This is a debug build (not production-ready)
- Testing limited to currently available Pokemon GO accounts
- No measurement of collection-grid or appraisal-screen recognition
- Holdout validation not yet performed

## Success Definition
Recognition recovery is production-ready if:
1. All live testing shows 0 confidently wrong results
2. Automatic recognition rate ≥ 85% on readable detail screens
3. Performance metrics within acceptable bounds
4. No crash or permission issues observed

## Next Steps After Verification
1. Validate results
2. Fix any issues found
3. Prepare for release or further iteration
4. Create independent test holdout for final validation
