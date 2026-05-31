# Lucky / Size Structured Fields Migration Plan

## Background

`LuckyDetected:` and `SizeTag:` are legacy rawOcrText markers that ScanManager
reads during the pipeline. However, **no current production code writes these
markers into rawOcrText**. They appear to be remnants of a previous OCR pipeline
version that has been refactored.

### Current State (as of commit 8ec89d6d)

| Marker           | Writer                  | Consumer(s)                          | Status         |
|------------------|-------------------------|--------------------------------------|----------------|
| `LuckyDetected:` | None found (legacy)     | `ScanManager.kt:306-309` (ocrLucky)  | Dead read path |
| `SizeTag:`       | None found (legacy)     | `ScanManager.kt:253` (provisionalSizeTag) | Dead read path |
| `parseSizeTag`   | `TextParser.kt:395-403` | Never called                         | Dead code      |
| `parseLuckyLabel`| `TextParser.kt:330-383` | Never called                         | Dead code      |

### How Lucky Is Currently Detected

1. `VisualFeatureDetector.detect()` uses bitmap analysis for `isLucky`.
2. ScanManager reads `LuckyDetected:` from rawOcrText (line 306) — but since
   nothing writes this marker, `ocrLucky` is always `false`.
3. The "Lucky override" logic (lines 310-319) therefore never fires.

### How Size Is Currently Detected

1. `TextParser.parseSizeTag()` is defined but never called.
2. ScanManager reads `SizeTag:` from rawOcrText (line 253) — but since nothing
   writes this marker, `provisionalSizeTag` is always `null`.
3. `VisualFeatureDetector.detect()` receives `null` sizeTag, so `isXXS`/`isXXL`
   are always `false` from that source.

## Proposed Migration

### Phase 1: Resurrect Functional Paths (Low Risk)

Add `sizeTag` and `isLucky` fields to `PokemonData` so the pipeline can
transport these values structurally instead of relying on rawOcrText markers
that no longer exist.

#### New Fields on PokemonData

```kotlin
data class PokemonData(
    // ... existing fields ...
    val sizeTag: String? = null,     // "XXS", "XS", "XL", "XXL" or null
    val ocrLucky: Boolean = false,   // Whether OCR detected the Lucky Pokémon label
)
```

#### Writer: OCRProcessor.processImage

Call `parseSizeTag` and `parseLuckyLabel` from OCRProcessor's `processImage`
and populate the new fields directly. This eliminates the need for rawOcrText
marker encoding/decoding.

#### Consumer: ScanManager

Replace the rawOcrText marker reads:

```diff
-val provisionalSizeTag = finalBase.rawOcrText.split("|")
-    .find { it.startsWith("SizeTag:") }?.substringAfter(":")
+val provisionalSizeTag = finalBase.sizeTag

-val ocrLucky = tracedBase.rawOcrText.split("|")
-    .find { it.startsWith("LuckyDetected:") }
-    ?.substringAfter(":")
-    ?.equals("true", ignoreCase = true) == true
+val ocrLucky = tracedBase.ocrLucky
```

### Phase 2: Wire Up Callers (Medium Risk)

1. In OCRProcessor, add a new OCR region or reuse existing regions to detect
   the Lucky label and size tag from the screen bitmap.
2. Call `textParser.parseSizeTag()` and `textParser.parseLuckyLabel()` during
   the `processImage` pipeline.
3. Populate `sizeTag` and `ocrLucky` on the returned `PokemonData`.

### Phase 3: Remove Dead Code (Low Risk)

1. Remove the rawOcrText marker consumer code in ScanManager (dead reads).
2. Optionally remove `parseSizeTag` and `parseLuckyLabel` from TextParser if
   they are fully replaced by the structured path.

### Phase 4: ScanHistoryEntity Update (Future / Optional)

If `sizeTag` and `ocrLucky` are important for historical queries, add columns
to `ScanHistoryEntity` and update `ScanHistoryMapper.toEntity()`.

## Risk Assessment

| Phase   | Risk   | Reason                                                |
|---------|--------|-------------------------------------------------------|
| Phase 1 | Low    | Adding nullable fields with defaults; no behavior change |
| Phase 2 | Medium | Requires identifying correct OCR regions for Lucky/Size |
| Phase 3 | Low    | Removing dead code paths                               |
| Phase 4 | Low    | Additive schema change with Room migration             |

## Recommended First Commit

**Phase 1 only** — add `sizeTag` and `ocrLucky` to `PokemonData` with defaults,
update ScanManager consumers to read from the new fields. No behavior change
since the current paths are dead anyway. This unblocks Phase 2 independently.

## Open Questions

1. Should Lucky detection use a dedicated OCR region, or rely on the full-screen
   ML Kit block scan?
2. Should size tag detection happen in the fast pass or only in the detailed pass?
3. Do we want to persist `sizeTag` and `ocrLucky` in `ScanHistoryEntity`
   immediately, or defer to Phase 4?

## Related Files

- `app/src/main/java/com/pokerarity/scanner/data/model/PokemonData.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt`
- `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt` (parseSizeTag, parseLuckyLabel)
- `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt` (lines 253, 306-319)
- `app/src/main/java/com/pokerarity/scanner/util/vision/VisualFeatureDetector.kt` (sizeTag param)
- `app/src/main/java/com/pokerarity/scanner/data/repository/ScanHistoryMapper.kt`

---

## Verified Audit (2026-05-31, commit 9df5640b)

Re-verified by exhaustive grep search:

| Item | Result |
|------|--------|
| `LuckyDetected:` writer | ❌ None found. Only consumer: ScanManager.kt:307 |
| `SizeTag:` writer | ❌ None found. Only consumer: ScanManager.kt:253 |
| `parseSizeTag` callers | ❌ None. Definition only: TextParser.kt:395 |
| `parseLuckyLabel` callers | ❌ None. Definition only: TextParser.kt:330 |

**Conclusion:** Dead read path status is confirmed. No production code writes
`LuckyDetected:` or `SizeTag:` into rawOcrText. The ScanManager consumers
always read `null`/`false`, making the downstream logic dead.

## Dead Read Path Cleanup Candidate

### Safest First Implementation

Remove the dead rawOcrText read paths in ScanManager (lines 253 and 306-319)
and pass `null`/`false` directly. This has zero behavior change since the
markers are never written.

### Tests Needed Before Removal

1. **ScanManager unit test** verifying that `provisionalSizeTag` being `null`
   is handled correctly by `VisualFeatureDetector.detect()` (already the case
   in production — just document it in a test).
2. **ScanFrameFusion test** verifying that rawOcrText without `SizeTag:` or
   `LuckyDetected:` markers round-trips through `mergeRawOcrText` cleanly
   (already passing — existing ScanFrameFusionTest covers this).
3. **TextParser dead code test** (optional): Verify `parseSizeTag` and
   `parseLuckyLabel` still work correctly in isolation so they can be
   resurrected when the OCR pipeline adds size/lucky region detection.

### Status: READY FOR REMOVAL

The dead read path removal is safe to do in a future commit. It requires only:
- Remove 2 dead read blocks in ScanManager
- No test additions required (existing tests already cover the null case)
- No runtime behavior change

