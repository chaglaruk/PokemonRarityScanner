# Calcy IV Recognition Gap Report

Date: 2026-06-26

Scope: report-only defensive interoperability research comparing PokemonRarityScanner with the decompiled Calcy IV APK at `C:\Users\Caglar\Downloads\tesmath.calcy_3.44.apk-decompiled`.

## Executive Summary

Calcy IV appears more reliable because it treats recognition as a calibrated screen-understanding problem, not only as OCR over fixed crops. The decompiled reference shows a pipeline with MediaProjection capture, repeated screenshot sampling, screen-state routing, device/layout autoconfiguration, field-specific OCR settings, field-specific bitmap preprocessing, game-data validation, localization-aware dictionaries, and explicit partial/fallback result states.

PokemonRarityScanner already has a strong passive capture boundary, path sanitization, frame fusion, species refinement, visual variant classifiers, CP validation code, metadata-only telemetry, and a useful test surface. The main reliability gap is that the live recognition path feeds downstream logic too little structured evidence. Current OCR primarily emits CP, HP, name, candy, and date; many fields that downstream logic expects or could use, such as arc level, stardust, appraisal IVs, size tag, power-up cost, and robust screen state, are null or absent.

The highest-leverage original fix is an adaptive geometry layer: classify the current Pokemon GO screen, locate stable anchors, then derive field crops from those anchors. That should come before deeper species heuristics. After that, improve OCR preprocessing, raw field consistency, resolver validation, confidence gates, and screenshot fixtures.

## Legal And Ethical Boundary

Do not copy Calcy IV source code, assets, database content, resource strings, identifiers used as proprietary data, or implementation details into PokemonRarityScanner.

Use the Calcy folder only as evidence of architectural categories and behavioral strategies. The recommendations below are original implementations using PokemonRarityScanner's own code, data, screenshots, tests, and passive Android APIs.

Do not decompile further for reusable code. Do not import Calcy native libraries, Tesseract assets, game-stat databases, visual thresholds, localization tables, resources, or matching constants.

## Calcy IV Inferred Recognition Architecture

Calcy appears to run this pipeline:

1. A foreground MediaProjection service receives explicit scan or auto-scan actions.
2. A capture provider creates a virtual display and ImageReader, retries null images, and can collect multiple frames.
3. A central screenshot analyzer receives screenshot batches, checks whether the stored device layout still matches the screenshot size, and routes the frame through screen-type deciders.
4. If the device layout is missing or stale, an autoconfiguration step searches for Pokemon GO UI structure such as white panel/box evidence, HP bar geometry, CP region, name/candy separators, arc geometry, and related regions.
5. OCR is handled by a Tesseract wrapper with language-specific trained data, page segmentation modes, character whitelists, and bounding-box access.
6. Bitmap preprocessing is substantial and field-specific: white text, dark text, color filtering, edge/region color ratios, move/name preprocessing, and multi-image text merging are exposed through native image-processing functions.
7. Pokemon name, CP, HP, gender, dust/level/appraisal-like values, moves, and visual states are read through specialized analyzers rather than one generic OCR pass.
8. Recognition is validated against embedded game stats, localized names, power-up/level constraints, and visual decider data.
9. Result types model many screen outcomes: monster, catch, appraisal, arena/gym/raid-like screens, lucky/scrolled states, ignore/not-found/unknown, and partial field statuses.
10. Visual deciders use configurable relative regions and color thresholds for states such as shadow/purified/lucky/forms and other non-text signals.

Important inference: Calcy's reliability appears to come from layered redundancy. A bad OCR character is not decisive because geometry, language, dictionaries, numeric constraints, and visual state checks can confirm or reject it.

## PokemonRarityScanner Current Recognition Architecture

PokemonRarityScanner currently runs this pipeline:

1. `MainActivity` requests MediaProjection consent and starts `ScreenCaptureService` plus the overlay.
2. `OverlayService` sends an internal capture-request broadcast when the user taps the overlay.
3. `ScreenCaptureService` captures two frames about 80 ms apart, writes cache PNGs, and broadcasts the screenshot paths with an internal permission.
4. `ScanManager` validates that screenshot paths are direct cache files named `scan_*.png`, decodes/scales frames, estimates CP crop quality, runs OCR, fuses frames, optionally runs a detailed pass, refines species, applies a consistency gate, runs variant visual classifiers, calculates rarity/confidence, persists a scan, and updates the overlay.
5. `OCRProcessor` uses ML Kit text recognition and percentage-based crop regions. CP, HP, name, candy, and date are read; arc, appraisal, stardust, power-up costs, gender, size, weight, height, and lucky text are not currently populated by the live path.
6. `TextParser`, `SpeciesRefiner`, `ScanConsistencyGate`, `VariantDecisionEngine`, and `RarityCalculator` contain richer logic than the live OCR feed can usually support.
7. Tests cover parser logic, frame fusion, detailed-pass policy, consistency gate cases, variant decisions, telemetry payload privacy, and an Android screenshot regression harness. The harness has 47 fixture cases and 47 PNGs, but only 16 are strict and 28 are all-null exploratory cases.

Important inference: the app has good downstream scaffolding, but the weakest layer is early screen understanding: the app does not first decide which Pokemon GO screen it is seeing, then derive anchored regions from detected UI geometry.

## Side-By-Side Comparison

| Area | Calcy IV inferred approach | PokemonRarityScanner current approach | Gap |
|---|---|---|---|
| Capture | MediaProjection service, auto-scan actions, retries, multiple screenshots | Passive MediaProjection service, two frames at fixed timing | PRS has safe capture but less adaptive retry/sampling |
| Screen state | Explicit screen-result classes and deciders for multiple Pokemon GO screens | Mostly assumes a Pokemon detail-like screen before OCR | High risk of OCR on the wrong UI state |
| Layout handling | Stored device scan config, screenshot-size compatibility, autoconfig | Percentage crops based on a reference screen, limited lower-screen anchor | Fixed crops drift across devices, DPI, aspect ratios, scroll states |
| OCR engine | Tesseract wrapper with language data, PSM, whitelists, boxes | ML Kit default text recognizer | PRS lacks field-specific OCR configuration and localization |
| Preprocessing | Native field-specific kernels for text/color/edges/multi-image merge | Kotlin/OpenCV helpers for masks, contrast, adaptive thresholding | PRS preprocessing is useful but not anchor- or field-complete |
| Name recognition | OCR plus dynamic crop discovery, game-stat dictionaries, fuzzy matching | Dynamic upper-screen ML Kit block filtering plus English species parser | Missing localized aliases/forms and screen-aware crop selection |
| CP/HP parsing | Specialized crops, OCR settings, range checks | Fixed crops with several preprocessing variants | Numeric parsing is not sufficiently anchored or constrained |
| Level/appraisal/IV | Dedicated scan-value/arc/appraisal paths with game-stat constraints | IV solver exists, but live OCR does not populate appraisal or arc evidence | Validation cannot run often enough |
| Visual states | Generic visual deciders from data plus color/geometry checks | Variant classifiers and visual feature detector run after OCR | PRS visual logic is strong but late and species-dependent |
| Confidence | Field-level statuses and explicit result types | Field presence, frame fusion, CP/name support, rarity confidence labels | PRS needs field-level confidence and fallback decisions |
| Localization | Game language model, tessdata, localized game data | Primarily English names and date/month parsing | Non-English Pokemon GO screens are weak |
| Tests | Not evaluated as runnable tests; evidence suggests many built-in deciders | Parser/fusion/unit tests plus 47 Android fixture cases | Fixtures need stricter expected labels and crop/geometry assertions |

## Ranked Root-Cause Hypotheses

| Rank | Hypothesis | Confidence | Evidence | Expected failure mode |
|---:|---|---|---|---|
| 1 | Fixed or weakly anchored crop geometry is the largest recognition weakness. | High | PRS CP/name regions are percentage constants; Calcy stores/validates many device-specific rectangles and autoconfigures from UI anchors. | Correct Pokemon is on screen, but CP/name/HP crop misses text or includes noise. |
| 2 | PRS lacks first-class Pokemon GO screen-state detection. | High | Calcy has result classes and screen deciders; PRS OCR runs before a comparable screen classifier. | Storage, appraisal, scrolled detail, encounter, or transition screens are parsed as the same layout. |
| 3 | Live OCR emits too few fields for downstream validation. | High | `OCRProcessor` sets arc/appraisal to null; `RarityCalculator` and `IvSolver` need those fields to constrain candidates. | Species or CP mistakes are not rejected by level/IV/stat constraints. |
| 4 | OCR normalization is not field-specific enough. | High | Calcy configures OCR language, PSM, whitelists, and boxes; PRS uses ML Kit default recognition and post-filters. | Numeric/text confusion such as CP/HP/name glyph errors survives into parsing. |
| 5 | Species resolution lacks a complete localized alias/form layer. | Medium-high | Calcy has game-language/localization infrastructure and embedded game data; PRS mainly loads English-ish names and local aliases. | Non-English names, forms, costumes, and nicknames are misread or downgraded to Unknown. |
| 6 | PRS confidence is not a true field-level recognition model. | Medium-high | Calcy tracks per-field status; PRS confidence mainly uses frame agreement, field presence, and rarity summaries. | The overlay may show a confident-looking result from weak evidence, or retry too late. |
| 7 | Visual features are underused for early recognition. | Medium | PRS visual classifiers run after species/OCR; Calcy uses visual deciders in screen classification and variant recognition. | Screens with poor text still have visual anchors, but PRS does not use them early. |
| 8 | Multi-frame strategy is too narrow. | Medium | Calcy can collect configurable multi-frame batches and merge text; PRS captures two fixed-timing frames. | Capture happens during animation, blur, or overlay transition and both frames are weak. |
| 9 | Regression fixtures are not strict enough. | Medium | 47 fixture cases exist, but 28 are all-null exploratory and only 16 are strict. | Regressions in real screenshots pass because expected fields are absent. |
| 10 | Raw OCR field names have drifted from downstream consumers. | Medium | Live OCR emits `NameDynamic`; downstream code/tests also expect `NameHC`, `Bottom`, `SizeTag`, and `LuckyDetected`. | Refiner/variant code silently loses evidence it was designed to use. |

## Evidence From Calcy Decompiled Files

This evidence is paraphrased. It cites paths/classes/methods only and does not reproduce Calcy implementation.

| Path | Class / method | Paraphrased behavior | Why it matters |
|---|---|---|---|
| `AndroidManifest.xml` | `ScreenCaptureService`, `MediaProjectionActivity`, scan actions | Declares overlay/media-projection permissions, a foreground media-projection service, transparent permission activity, and scan/auto-scan broadcast actions. | Calcy uses passive screen capture with service orchestration. |
| `sources/com/tesmath/calcy/ScreenCaptureService.java` | `ScreenCaptureService.onStartCommand` | Handles explicit analyze-screen, start-auto-scan, stop-auto-scan, and related service actions. | Recognition is integrated with repeated/service-driven scanning. |
| `sources/com/tesmath/screencapture/MediaProjectionActivity.java` | `MediaProjectionActivity` permission flow | Starts Android's screen capture permission flow and returns the MediaProjection result to the service path. | Confirms legal passive capture entry point. |
| `sources/com/tesmath/screencapture/b.java` | `b.I`, inner capture callbacks | Creates MediaProjection/ImageReader/virtual display, retries missing images, handles display metrics and device-specific capture behavior. | Capture reliability is treated as part of recognition reliability. |
| `sources/com/tesmath/screencapture/c.java` | `c.d` | Reads configurable screenshot count. | Calcy expects more than a single fixed frame in some modes. |
| `sources/com/tesmath/calcy/image/analysis/z.java` | `z.w`, `z.r`, `z.d` | Receives screenshot batches, starts analysis, dispatches screen-specific result types, and invalidates stale layout config when screenshot size no longer matches. | Central screen analyzer plus layout compatibility check. |
| `sources/com/tesmath/calcy/image/analysis/y.java` | nested result classes | Represents monster, catch, appraisal, arena/gym/raid-like, scrolled, ignore, not-found, unknown, and related results. | Screen classification is explicit, not implicit. |
| `sources/com/tesmath/calcy/image/analysis/v.java` | `v.N`, `v.a`, `v.c`, `v.k` | Tracks per-field scan status for Pokemon, CP, HP, dust, level, moves, gender, catch date, and related values. | Supports partial results and targeted fallback. |
| `sources/com/tesmath/calcy/image/analysis/u.java` | `u` | Persists screenshot size and many calibrated rectangles, including CP, HP, arc, candy, dust, gender, arena, raid, and white-box regions. | Device/layout calibration is broad and durable. |
| `sources/com/tesmath/calcy/image/analysis/g.java` | `g.b` | Runs autoconfiguration across screenshot candidates and keeps language-suspicion results. | Calcy can recover from missing or wrong layout configuration. |
| `sources/com/tesmath/calcy/image/analysis/Autoconfig.java` | `Autoconfig.p`, `Autoconfig.K`, `Autoconfig.j`, `SuspectWrongLanguageResult` | Searches for CP/arc/name/HP-related regions using visual structure, OCR boxes, retries, and wrong-language signals. | Field regions are discovered from screen evidence. |
| `sources/com/tesmath/calcy/image/analysis/x.java` | `x.a.c`, `x.A`, `x.s`, `x.r`, `x.t` | Detects monster screens without full config, scrolled Pokemon screens, appraisal screens, and other screen types using HP bars, lines, stamps, OCR/visual regions, and preferences. | Calcy routes recognition by screen state before interpreting fields. |
| `sources/b3/C0990b.java` | `C0990b` | Wraps Tesseract API initialization, bitmap input, text output, and OCR boxes. | OCR can expose both text and geometry. |
| `sources/b3/C0993e.java` | `C0993e.o`, `C0993e.i`, `C0993e.k`, `C0993e.m`, `C0993e.n`, `C0993e.c`, `C0993e.d` | Selects OCR language, page segmentation, whitelists, recognition modes, and bounding-box extraction. | OCR is tuned per field and locale. |
| `assets/tessdata/*.traineddata` | OCR assets | Contains trained OCR data for multiple language/model variants. | Calcy can OCR non-English Pokemon GO screens. |
| `lib/arm64-v8a/libtesseract.so`, `libleptonica.so`, `libcalcy_image_native.so` | native libraries | Provide Tesseract/Leptonica and custom native image operations. | Heavy preprocessing and OCR runtime are native-backed. |
| `sources/com/tesmath/calcy/image/NativeImageProcessorBitmapAPI.java` | native methods | Exposes color counts, signal-color checks, overlay edge finding, rect color ratios, white/dark text preprocessing, move/name filtering, multi-frame text merge, and unsharp blending. | Recognition has field-specific image processing beyond simple crop OCR. |
| `sources/O2/C0642s.java` | image processor implementation | Calls native bitmap functions through an image-processor abstraction. | Native preprocessing is part of the analysis path. |
| `sources/com/tesmath/calcy/image/analysis/n.java` | `n.I`, `n.J`, `n.P`, `n.F`, `n.N` | Reads Pokemon name, CP, HP, CP variants, and gender through specialized regions/preprocessing/OCR/matching. | Each field gets its own strategy. |
| `sources/com/tesmath/calcy/image/analysis/d.java` | scan-value analyzer | Handles level/arc-related scan values with fallback when narrower constraints fail. | Level detection is multi-pass. |
| `sources/com/tesmath/calcy/calc/B2/C0967e.java` | IV/level candidate calculation | Combines recognized scan values with game-stat constraints to enumerate plausible level/IV candidates. | Recognition is validated mathematically. |
| `sources/com/tesmath/calcy/image/analysis/p.java` | `p.l`, `p.D0`, `p.k0` | Applies generic visual decider data and color/ratio checks for states such as shadow/purified/lucky/form-like signals. | Visual evidence is data-driven and non-OCR. |
| `sources/com/tesmath/calcy/gamestats/serverdata/GenericVisualMonsterDeciderData.java` | model accessors | Stores relative visual regions, thresholds, applicability flags, and selected options. | Visual recognition rules can be updated as data. |
| `sources/com/tesmath/calcy/image/analysis/s.java` | `s.F`, `s.T`, `s.Y`, `s.G`, `s.W`, `s.R` | Performs fuzzy name/move/gym matching and normalizes numeric fields. | OCR output is corrected against known domains. |
| `assets/gamestats/gamestats.db` | embedded game data | Contains species, moves, evolutions, localization, form/shadow-related, power-up, and related metadata tables. | Recognition is backed by game-domain data. |
| `sources/com/tesmath/calcy/helper/GameLanguage.java` | `GameLanguage` | Defines supported game languages and language-specific CP/HP labels. | Localization affects OCR and parsing. |
| `sources/com/tesmath/calcy/gamestats/serverdata/LocalizationUpdate.java` | localization update model | Models localized names/labels as updateable data. | Localized recognition is maintainable. |

## Evidence From PokemonRarityScanner

| Path | Class / method | Current behavior | Recognition impact |
|---|---|---|---|
| `app/src/main/java/com/pokerarity/scanner/MainActivity.kt` | `startCapture`, `mediaProjectionLauncher` | Requests MediaProjection permission and starts capture/overlay services. | Strong passive permission boundary. |
| `app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureManager.kt` | `handleResult`, `buildServiceIntent`, `release` | Stores capture grant result, builds foreground service intent, clears grant on release. | Grant lifecycle exists. |
| `app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt` | `ACTION_CAPTURE_REQUESTED`, `onOverlayClicked` | Sends internal protected capture-request broadcast on overlay click. | Passive user-triggered scan. |
| `app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureService.kt` | `captureSequence` | Captures two frames at fixed timing, writes PNGs to cache, broadcasts paths. | Good baseline, but limited sampling and no screen-state feedback. |
| `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt` | `sanitizeScreenshotPaths` | Accepts only direct cache files named `scan_*.png`, max three frames. | Strong file safety boundary. |
| `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt` | `processScanSequence` | Decodes/scales frames, estimates CP quality, OCRs, fuses frames, runs detailed pass/refinement/visual/rating pipeline. | Good orchestration; early recognition evidence is thin. |
| `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt` | `runDetailedPassIfNeeded`, `shouldRunDetailedPass` | Runs a secondary OCR pass when CP/species/HP/date/confidence/CP-quality are weak. | Useful fallback, but uses same geometry assumptions. |
| `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt` | `estimateCpQuality` | Scores a processed fixed CP crop based on dark-pixel/row coverage. | Detects some bad CP crops but does not relocate them. |
| `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt` | `handleError` | Retries by rebroadcasting `OverlayService.ACTION_CAPTURE_REQUESTED`. | Retry exists, but failure reasons are not yet a rich screen-state signal. |
| `app/src/main/java/com/pokerarity/scanner/service/ScanFrameFusion.kt` | `isHighConfidence`, `shouldRunDetailedPass`, `fuse` | Uses frame agreement, field presence, and CP/name/HP/date/arc scores to choose and merge frames. | Strong concept; often lacks arc/appraisal inputs. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/MLKitOcrProvider.kt` | `MLKitOcrProvider` | Uses on-device ML Kit default text recognizer. | Simple and passive, but lacks per-field OCR whitelists/PSM/language control. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt` | `processImage`, `recognizeCp`, `recognizeHp`, `recognizeName` | Reads CP/HP/name plus optional candy/date; sets arc/appraisal fields to null. | Main live recognition bottleneck. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenRegions.kt` | `REGION_CP`, `REGION_NAME`, `detectAppraisalBox`, `getDynamicRegions` | Defines fixed percentage regions and a limited lower-screen appraisal anchor. | Needs general adaptive geometry for all key fields. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt` | preprocessing helpers | Provides white mask, high contrast, adaptive threshold, HP/candy/date/arc-like visual helpers. | Useful building blocks, but not yet a screen classifier/crop engine. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt` | `parseName`, CP/HP/date/candy/stardust/size parsers | Loads species names and performs exact/fuzzy normalization for multiple fields. | Parser is richer than live OCR inputs. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt` | `refine` | Combines name, candy family, move/profile/physical signals, and fit scores. | Can rescue weak names when supporting fields exist. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanConsistencyGate.kt` | consistency gate | Falls back to authoritative/candy-family species or requests retry on conflicts/unknowns. | Good safety gate; needs better upstream evidence. |
| `app/src/main/java/com/pokerarity/scanner/util/vision/VisualFeatureDetector.kt` | feature extraction/classification | Detects shiny, shadow, lucky, costume, location card, and size-derived signals. | Strong late-stage visual layer; should also inform screen/field confidence. |
| `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt` | decision logic | Uses OCR fallback keys and visual evidence to merge variant decisions. | Some expected raw keys are not emitted by current live OCR. |
| `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt` | CP validation/species fit | Can validate/fix CP using species stats, HP, arc level, and stardust. | Usually underused because arc/stardust are null. |
| `app/src/main/java/com/pokerarity/scanner/domain/iv/IvSolver.kt` | IV evidence solver | Accepts CP/HP/level/appraisal evidence for valid IV candidates. | Solver exists, but live OCR does not populate appraisal values. |
| `app/src/main/java/com/pokerarity/scanner/data/model/PokemonData.kt` | model fields | Model includes arc, appraisal, diagnostics, variant trace, physical metrics. | Model is ready for richer evidence. |
| `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt` | Android fixture harness | Runs fixture PNGs through OCR/refinement/visual/rating path and compares expected values. | Harness exists; many cases are non-strict. |
| `app/src/androidTest/assets/scan_regression_cases.json` | fixture manifest | 47 cases: 16 strict, 19 with any expected labels, 28 all-null exploratory. | Needs stronger labels to catch recognition regressions. |
| `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryCoordinator.kt` | telemetry consent | Requires opt-in upload id/feedback behavior. | Privacy boundary should remain unchanged. |
| `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt` | telemetry payload | Supports optional screenshot path, but current live scan passes null and payload redacts raw diagnostics. | Keep production scans metadata-only unless explicitly changed. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt` | local diagnostics | Writes local debug JSON/crops/raw OCR paths when diagnostic export is triggered. | Useful for Phase A; must stay local and consent-aware. |

## Do Not Copy From Calcy

Do not copy:

- Calcy source code, decompiled snippets, class bodies, algorithms, constants, thresholds, or native calls.
- Calcy assets, Tesseract traineddata, game-stat databases, localization tables, resource strings, screenshots, or visual-decider data.
- Calcy package names, identifiers, method names, data schemas, server payload formats, or update formats as implementation references.
- Calcy-specific heuristics for species, forms, languages, colors, arc geometry, CP/HP constraints, or fallback ordering.

Allowed original work:

- Reimplement the broad architectural ideas independently: adaptive crops, screen-state detection, field-specific preprocessing, confidence gates, and validation against our own assets.
- Use our own screenshots and user-captured fixture data.
- Use our existing `pokemon_names.json`, `master_pokedex.json`, variant assets, family/move data, and any legally sourced open data already approved for this project.
- Keep all capture passive and within Android MediaProjection/overlay consent boundaries.

## Recommended Original Implementation Plan

1. Add instrumentation before changing recognition. Capture per-frame dimensions, screen classifier result, anchor coordinates, crop rectangles, raw ML Kit blocks, chosen field candidates, parser candidates, and final confidence reasons into local diagnostics only.
2. Introduce a `ScreenClassifier` that returns `PokemonDetail`, `Appraisal`, `StorageList`, `Encounter`, `ScrolledDetail`, `Transition`, or `Unknown` using original color/geometry features from our screenshots.
3. Replace fixed `ScreenRegions` use in `OCRProcessor` with a `ScreenGeometry` result that derives CP/name/HP/candy/date/appraisal crops from detected anchors.
4. Normalize live raw OCR keys. Emit stable keys such as `Name`, `NameHC`, `NameDynamic`, `CP`, `HP`, `Candy`, `Date`, `SizeTag`, `LuckyDetected`, `Stardust`, `Arc`, and `Appraisal*` only when the corresponding detector ran.
5. Add field-specific OCR passes around ML Kit: numeric crops for CP/HP/stardust, name crops with block geometry/ranking, lower-screen crops for candy/date/size, and appraisal bar crops.
6. Improve `SpeciesResolver` behavior by separating display name OCR from canonical species/form resolution, using our own names/forms/families/moves/variant metadata.
7. Gate the overlay on field-level confidence. Return "uncertain" or trigger retry when screen-state confidence, crop confidence, OCR confidence, and validation confidence do not agree.
8. Convert fixture cases into strict expectations and add crop/geometry golden checks.

## Proposed New Recognition Architecture For PokemonRarityScanner

```text
ScreenCaptureService
  -> ScanManager
    -> FrameQualityAnalyzer
    -> ScreenClassifier
    -> ScreenGeometryBuilder
    -> CropSetBuilder
    -> FieldOcrPipeline
    -> SpeciesResolver
    -> FieldValidator
    -> ConfidenceGate
    -> Variant/Rarity pipeline
    -> Overlay + local diagnostics + metadata-only telemetry
```

Key contracts:

- `ScreenClassifier` should run before field OCR and return screen type plus confidence.
- `ScreenGeometryBuilder` should expose anchors and derived crop rectangles with confidence and provenance.
- `FieldOcrPipeline` should return multiple candidates per field, not just final strings.
- `SpeciesResolver` should rank canonical species/form candidates and record why a candidate won.
- `FieldValidator` should use CP/HP/arc/stardust/appraisal constraints when present, but never invent missing evidence.
- `ConfidenceGate` should decide one of: accept, accept with low-confidence explanation, retry, or no-scan-screen.

## Specific File-By-File Changes To Make Later

| File / new file | Later change | Benefit |
|---|---|---|
| `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenClassifier.kt` | New original screen-state classifier. | Prevents parsing wrong screens as Pokemon detail. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenGeometry.kt` | New data model for anchors, crop rects, confidence, and provenance. | Makes crops inspectable and testable. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenRegions.kt` | Keep legacy constants behind fallback; add geometry-derived regions. | Reduces device/layout drift. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt` | Consume `ScreenGeometry`; emit stable raw fields; add field candidate diagnostics. | Feeds downstream logic richer evidence. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt` | Add original anchor detectors for HP bar, name baseline, CP header area, detail panel, appraisal bars, and lower action area. | Improves crops before parser changes. |
| `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt` | Insert classifier/geometry stages and log local diagnostics; keep path sanitization and passive capture unchanged. | Adds reliability without changing capture safety. |
| `app/src/main/java/com/pokerarity/scanner/service/ScanFrameFusion.kt` | Fuse screen-state and geometry confidence; merge field candidates across frames. | Uses multi-frame data more effectively. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt` | Split canonical species resolver from generic parser; add owned alias/form normalization. | Reduces false species matches. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt` | Consume ranked species/form candidates and validation evidence. | Makes rescue behavior explainable. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanConsistencyGate.kt` | Gate on screen type, crop confidence, and validation conflicts. | Retries the right failures. |
| `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt` | Use new validation evidence when arc/stardust/appraisal are present. | Turns existing CP/species validation into a live check. |
| `app/src/main/java/com/pokerarity/scanner/domain/iv/IvSolver.kt` | Add integration tests fed by OCR/appraisal fixtures. | Verifies appraisal/level constraints. |
| `app/src/main/java/com/pokerarity/scanner/util/ocr/OcrDiagnosticsExporter.kt` | Add screen-state, anchors, crop rects, and field candidate dumps. | Makes failures debuggable locally. |
| `app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt` | Compare strict crop/geometry/field expectations. | Prevents fixture regressions. |
| `app/src/androidTest/assets/scan_regression_cases.json` | Label existing all-null cases and add device/screen metadata. | Converts current screenshots into real tests. |
| `scripts/export_device_scan_fixtures.ps1` | Capture screen type, device model, resolution, density, app/game language, and expected labels. | Builds repeatable fixture data. |
| `docs/AI_RUN_REPORT.md` | Record each implementation phase, commands, results, and rollback notes. | Keeps managed-agent workflow auditable. |

## New Tests And Screenshot Fixtures Needed

Add tests:

- `ScreenClassifierTest`: fixture-based assertions for Pokemon detail, appraisal, storage list, encounter, scrolled detail, transition, and unknown screens.
- `ScreenGeometryTest`: crop rectangles are inside image bounds, non-empty, stable under common resolutions, and anchored to detected UI evidence.
- `FieldOcrPipelineTest`: CP/name/HP/date/candy/stardust/size/appraisal candidate extraction from known crops.
- `RawOcrContractTest`: live OCR emits the same raw keys downstream code consumes.
- `SpeciesResolverTest`: aliases, forms, family candy evidence, nickname noise, and localized-name fallback using project-owned data only.
- `ConfidenceGateTest`: accepts strong evidence, retries bad geometry, rejects wrong screen state, and reports uncertainty.
- `ScanRegressionTest` updates: make strict comparisons for representative real screenshots.
- `DataRetentionManagerTest`: retention deletes only intended telemetry/cache files.
- `TelemetryMetadataOnlyTest`: production scan telemetry remains screenshot-free unless explicitly configured.

Fixture set:

- Samsung S25 detail screen: normal, shiny, shadow, lucky, costume, background/location card, scrolled, appraisal.
- Pixel 4a detail screen with same categories.
- At least one small, tall, and tablet-ish emulator resolution.
- English plus at least one non-English Pokemon GO language captured from our own device.
- Transition frames: after opening Pokemon, after scrolling, during appraisal animation, and after overlay tap.
- Crops saved alongside full screenshots for CP/name/HP/date/candy/appraisal/arc.
- Expected JSON labels for screen type, species, form, CP, HP, date, candy family, shiny/shadow/lucky/costume/background, crop rects, and allowed uncertainty.

Current fixture status:

- `app/src/androidTest/assets/scan_regression_cases.json` has 47 cases.
- `app/src/androidTest/assets/scan_fixtures` has 47 PNG files in nested batch folders.
- 16 cases are strict, 19 have any expected labels, and 28 are all-null exploratory.
- The single `screenshots/` sample is useful for manual inspection but is not enough for regression coverage.

## Manual Test Plan

Capture these screenshots using the existing passive overlay and fixture export scripts:

1. Samsung S25, Pokemon detail, English, regular species with clear CP/name/HP/date.
2. Samsung S25, same species scrolled so lower fields move.
3. Samsung S25, appraisal open with attack/defense/stamina bars visible.
4. Samsung S25, shiny Pokemon with CP/name visible.
5. Samsung S25, shadow and purified examples.
6. Samsung S25, lucky Pokemon.
7. Samsung S25, costume/event Pokemon and background/location-card example.
8. Pixel 4a, the same core set for resolution/aspect comparison.
9. One low-CP Pokemon, one high-CP Pokemon, one long name, one form-heavy name.
10. Non-English Pokemon GO screen with known expected species/CP/HP/date.
11. Bad-state screenshots: storage grid, encounter catch screen, transition blur, empty/covered overlay, and non-Pokemon GO screen.

For each screenshot:

- Record device model, Android version, resolution, density, Pokemon GO language, screen type, expected fields, and capture path.
- Save full screenshot and generated crops.
- Run `.\scripts\run_scan_regression.ps1` if available for Android fixtures.
- Run `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`.

## Risks

- Overfitting to current screenshots. Mitigate with device/language/screen-state diversity and geometry tests.
- Regressing privacy boundaries while adding diagnostics. Keep diagnostics local, explicit, and covered by tests.
- Creating a large rewrite. Mitigate by keeping legacy `ScreenRegions` as fallback and landing one phase at a time.
- Misusing Calcy evidence. Keep all data/code original and only use Calcy as architectural comparison.
- More conservative confidence gates may reduce recall at first. Surface uncertainty clearly and improve with fixtures.
- ML Kit may remain weak for some locales or stylized text. Add OCR-provider abstraction only after geometry and preprocessing prove insufficient.
- Additional tests may require connected Android devices for full fixture validation. Keep pure unit tests for geometry/parser/confidence where possible.

## Open Questions

- Which Pokemon GO screens fail most often today: detail, appraisal, storage, encounter, or transitions?
- Are failures concentrated by device, resolution, density, language, or dark/light UI changes?
- Should PRS support non-English Pokemon GO screens in the next milestone, or explicitly gate to English until fixtures exist?
- Which current all-null fixture cases should become strict first?
- Should diagnostics include cropped images by default in debug builds, or only on explicit export?
- Is ML Kit sufficient after adaptive crops, or do we need an optional OCR-provider abstraction later?
- What confidence threshold should block overlay output versus display "uncertain"?
- Which legally sourced datasets should be used for form/localized aliases?

## Phased Roadmap

| Phase | Likely files touched | Expected benefit | Difficulty | Validation command | Rollback risk |
|---|---|---|---|---|---|
| Phase A: instrumentation and screenshot fixture capture | `ScanManager.kt`, `OcrDiagnosticsExporter.kt`, `scan_regression_cases.json`, `scripts/export_device_scan_fixtures.ps1`, `docs/AI_RUN_REPORT.md` | Makes failures measurable; converts existing screenshots into actionable evidence. | Low-medium | `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` plus fixture export smoke test | Low, diagnostics can be disabled or removed. |
| Phase B: adaptive crop/anchor rewrite | New `ScreenClassifier.kt`, new `ScreenGeometry.kt`, `ScreenRegions.kt`, `OCRProcessor.kt`, `ImagePreprocessor.kt` | Biggest expected species/CP/HP reliability gain across devices. | High | Unit geometry tests plus Android fixture regression | Medium, keep fixed-region fallback. |
| Phase C: OCR preprocessing and normalization | `OCRProcessor.kt`, `ImagePreprocessor.kt`, `MLKitOcrProvider.kt`, `TextParser.kt` | Improves noisy CP/name/HP/date reads and stabilizes raw OCR keys. | Medium | `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` and field OCR fixture tests | Medium, can revert individual field passes. |
| Phase D: species/form resolver improvements | `TextParser.kt`, `SpeciesRefiner.kt`, `PokemonRepository.kt`, assets under `app/src/main/assets/data/` | Reduces Unknown/misclassified species and form confusion using owned data. | Medium | Parser/resolver unit tests and strict screenshot fixtures | Medium, asset changes need review. |
| Phase E: confidence and validation gate | `ScanFrameFusion.kt`, `ScanConsistencyGate.kt`, `RarityCalculator.kt`, `IvSolver.kt`, `ScanDecisionSupport.kt` | Prevents weak evidence from becoming confident overlay output; enables smarter retry. | Medium-high | Confidence/gate tests plus fixture regression | Medium, thresholds can affect recall. |
| Phase F: tests, golden fixtures, regression harness | `ScanRegressionTest.kt`, new geometry/OCR tests, `scan_regression_cases.json`, fixture scripts | Locks improvements and catches real screenshot regressions. | Medium | `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`; `.\scripts\run_scan_regression.ps1` when device fixtures are available | Low, tests can be adjusted without app behavior changes. |
| Phase G: optional advanced visual matching | `VisualFeatureDetector.kt`, `VariantPrototypeClassifier.kt`, new early visual anchor helpers | Helps screens where OCR remains weak; improves form/background/lucky detection. | High | Visual fixture tests and confusion-matrix report | Medium-high, keep optional and confidence-gated. |

## Validation Performed

Commands run from `C:\Users\Caglar\Desktop\PokeRarityScanner` unless noted:

| Command | Result |
|---|---|
| `git status --short` | No output before report creation. |
| `git branch --show-current` | `feature/collector-intelligence-phase-2c-2e-scan-decision`. |
| `Test-Path graphify-out\graph.json` | `False`; graph report artifacts existed, but no `graph.json` was available, so source inspection was used directly. |
| `.\gradlew.bat tasks --all --no-daemon --console=plain` | Timed out in an earlier 120s attempt. |
| `.\gradlew.bat tasks --no-daemon --console=plain` | Build successful in 34s; task list includes `testDebugUnitTest`, `connectedDebugAndroidTest`, `assembleDebug`, and related Android tasks. |
| `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` | Build successful in 1m 14s; 36 actionable tasks, 1 executed, 35 up-to-date. SDK XML version warning was printed. |
| Fixture inventory command | 47 JSON cases, 47 PNG fixtures, 16 strict, 19 with any expected label, 28 all-null exploratory. |
| Calcy asset inventory command | Found Tesseract/Leptonica/native image libraries, multiple Tesseract traineddata files, and `assets/gamestats/gamestats.db`. |

No implementation changes were made. No Calcy files were modified. No release build was run. No commit was created.
