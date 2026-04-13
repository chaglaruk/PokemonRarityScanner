# Apex Upgrade Plan

## Goal
- Reduce scan latency toward the requested `<1.5s` target.
- Eliminate preventable `INSUFFICIENT` and low-quality `RANGE` results caused by OCR failures.
- Produce exact IV when appraisal bars are visible.
- Enrich telemetry with OCR confidence and calculation ambiguity reasons.

## Evidence Baseline
- Recent live logs show:
  - HP can still collapse to impossible values such as `10/10` and `15/15`.
  - `PowerUpStardustParsed -> null` remains common.
  - `PowerUpCandyParsed` can come from noisy dedicated alt crops.
  - `processScanSequence` is still `~3s` on the best recent batch and `>9s` on bad scans.
- Recent code already has:
  - OpenCV preprocessing
  - ML Kit OCR provider
  - Arc/appraisal analyzers
  - cost-based IV solver
  - diagnostics export

## Scope

### Track 1: Latency Decapitation
- Parallelize frame OCR-independent work inside `ScanManager`.
- Keep Tesseract calls serialized, but move arc/appraisal/region crops and classifier preconditions off the main sequential path.
- Avoid unnecessary full-frame work when OCR already locked species and IV prerequisites are missing.
- Prefer cropped CV-only work over whole-frame OpenCV passes.

### Track 2: Sub-pixel Appraisal & Arc Calibration
- Improve `AppraisalBarAnalyzer` with marker-aware fill measurement.
- Snap `ArcPointAnalyzer` level estimates to half-levels before solver handoff.
- Use exact appraisal values to force `EXACT` mode when all three bars are available.

### Track 3: Stardust Recovery
- Add multi-threshold retry for stardust region only.
- Add lightweight character-shape correction for common digit confusions in cost OCR.
- Add logical stardust narrowing from candy + CP/HP when direct OCR is missing.

### Track 4: UX Automation
- Auto-start analysis when an appraisal screen is detected.
- Reuse existing clipboard/haptics features and make %100/exact appraisal cases prominent.

### Track 5: Telemetry & Error Audit
- Extend payload/debug with:
  - `ocrConfidenceScore`
  - `calculationErrorMargin`
  - contradiction source (`hp`, `cp`, `cost`, `arc`, `appraisal`)
- Keep legacy endpoint staging logic intact.

## Constraints
- No broad OCRProcessor refactor in this pass.
- No fake `EXACT`; appraisal-visible exactness is allowed, otherwise exact only with a single valid candidate.
- Prefer targeted fixes backed by current logs.

## Verification
- Focused JUnit tests for:
  - `TextParserPowerUpCostTest`
  - `TextParseUtilsRegressionTest`
  - `ArcPointAnalyzerTest`
  - `AppraisalBarAnalyzerTest`
  - `IvCostSolverTest`
  - `ScanManagerDetailedPassTest`
  - `ScanTelemetryUploaderTest`
- Release build, device install, launch, and release asset upload.
