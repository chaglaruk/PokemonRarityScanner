package com.pokerarity.scanner.service

import com.pokerarity.scanner.util.ocr.FrameCalibrationHint
import com.pokerarity.scanner.util.ocr.ScreenGeometry

/**
 * Same-source recognition context established by the FAST detail frame (Phase 2A
 * geometry + Phase 2B calibration participation) and reused by the same-source detailed
 * pass, so both passes extract with identical recognition-space context and can never
 * produce an artificial fast-vs-detailed disagreement from dropped context.
 *
 * The geometry is the already-derived [ScreenGeometry]; the hint is exactly what the
 * fast frame legitimately had (never fabricated for the detailed pass).
 */
internal data class RecognitionContext(
    val geometry: ScreenGeometry,
    val calibrationHint: FrameCalibrationHint?
)
