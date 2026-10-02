# Recognition Recovery: Implementation Complete

## Status: Debug Candidate — Replay-Verified, Not Production-Verified

Implementation is complete, committed, and verified against preserved bitmap replays and the
preserved device corpus. The recovery work achieves the core mission objective on readable
detail screens: reliable automatic Pokemon species recognition using independent family
evidence. Per [RECOGNITION_RECOVERY_RESULTS.md](RECOGNITION_RECOVERY_RESULTS.md): fresh live
Pokemon GO capture, overlay delivery, and independent holdout acceptance remain unverified.
No release build or publication was performed, and no parity or production-readiness claim is
made.

## What Changed

**Architecture Shift:**
- **Before:** Species chosen by ML Kit nickname, arc detection veto, error-prone multi-pass OCR
- **After:** Species identified from independent candy, HP, CP, power-up cost, and type evidence

**Key Changes:**
1. **FamilySpeciesResolver** - Replaces ambiguous species logic with family-based resolution
2. **AnchoredScreenText** - HP-anchored spatial crop geometry (fixes scrolling issues)
3. **RecognitionProfiles** - Complete 1,216-profile family database (1,011 canonical species)
4. **ScanConfidenceGate** - Evidence-coherent confidence scoring
5. **RecognitionObservation** - Preserves observed frame data without synthesis
6. **Removed:** Multi-pass OCR, title-based species logic, arc requirement

## Root Causes Fixed

✅ **Arc detector veto** - No longer required for species identity; accepted 0/15 before, now 14/15
✅ **Crop geometry drift** - HP label now anchors geometry; scrolling no longer breaks recognition
✅ **Title editing corruption** - Title excluded from species logic; edited pencil/slash no longer matters
✅ **Incomplete profiles** - Added all regional/form variants and corrected stats
✅ **Evidence fusion failures** - Single frame selection prevents incompatible observations
✅ **Numeric edge cases** - CP <100 and fainted Pokemon (damaged HP) now safe

## Measured Results (Bitmap Replay)

| Corpus | Correct | Accepted Right | Accepted Wrong | Uncertain | Median P95 |
|--------|---------|-----------------|----------------|-----------|-----------|
| Development 15 (900px) | 14/15 | 14/15 | 0 | 1/15 | 448ms / 1,867ms |
| Development 15 (1080px) | 14/15 | 14/15 | 0 | 1/15 | 460ms / 550ms |
| Historical 16 (900px) | 16/16 | 16/16 | 0 | 0/16 | 505ms |
| Historical 16 (1080px) | 16/16 | 16/16 | 0 | 0/16 | 523ms |

**Recognition Correctness:** 93.3% automatic coverage on this small correlated corpus, with
0 confidently-wrong accepts observed in it. This is not an established error rate.
**Performance:** 3x latency improvement (1,745ms → 448ms median)
**Memory:** PSS improved from 388 MiB to 326 MiB

## Unit Test Results (historical snapshot; see integration update below)

✅ **Build Status:** SUCCESSFUL (3m 31s)
✅ **Test Count:** 710 tests executed at the recovery milestone
✅ **Core Metrics:**
  - RecognitionMatcher: 1011/1011 exact canonical names correct, 0 wrong
  - FamilySpeciesResolver: 10/10 tests passing (ambiguity, type disambiguation, edge cases)
  - AnchoredScreenText: Spatial crop validation
  - All OCR corruption patterns: Properly rejected (no false accepts)

## Code Quality

✅ **No synthesized evidence** - Confidence based only on observed frame
✅ **Fail-closed design** - Uncertain when evidence insufficient (1/15 = legitimate Farfetch'd scrolled case)
✅ **Type safety** - Nullable fields distinguish observed vs missing
✅ **Backward compatible** - Existing telemetry, privacy, and consent unchanged

## Deliverables

**Branch:** `fix/recognition-recovery`
**APK:** PokeRarityScanner-v1.10.0-debug.apk installed on Samsung Galaxy S25
**Tests:** At the recovery milestone all 710 unit tests passed; the current integrated suite
is larger (see the integration update in [RECOGNITION_RECOVERY_RESULTS.md](RECOGNITION_RECOVERY_RESULTS.md))
**Assets:** RecognitionProfiles.json (1,216 profiles, byte-for-byte reproducible)

## Remaining Verification Before Any Release Consideration

No release action is planned or authorized by this document. The outstanding verification is:

1. **Live Device Testing** (User Action Required)
   - Grant overlay + MediaProjection permissions
   - Test with fresh Pokemon GO captures
   - Validate zero wrong species results
   - Measure the automatic recognition rate (no target is established yet)

2. **Holdout Validation**
   - Verify on a fresh independent test set

3. **Release Decision** — out of scope for this recovery work; a release build, signing
   decision, and any store submission would require their own review.

## Safety Guarantees Maintained

✅ **Passive Only** - No gameplay automation, input injection, memory access, or root required
✅ **No External Services** - All recognition stays on device (local-only)
✅ **Consent Preserved** - MediaProjection and overlay controls intact
✅ **Privacy Protected** - No OCR, screenshots, or sensitive paths in telemetry
✅ **Credentials Safe** - Signing keys and keystores untouched

## Competitive Position

No competitive-parity claim is made. Poke Genie and Calcy IV are practical UX benchmarks, but
no independently comparable accuracy dataset exists for this candidate. The measured figures
above describe this corpus only: the 0-confidently-wrong observation is a small-corpus result,
not an established error rate, and cannot be compared against any competitor.

## Known Limitations

- This corpus covers 13 distinct species on readable detail screens only
- Collection grid, appraisal screen, and encounter screens not yet tested
- Exact IV accuracy and variant identification use existing infrastructure
- Further coverage expansion depends on additional real-world testing

---

**Recommendation:** The implementation solves the stated recognition problem on the preserved
corpora: reliable automatic species identification on readable Pokemon GO detail screens
without requiring manual user confirmation. Live capture, holdout acceptance, and any release
decision remain open and require separate validation.
