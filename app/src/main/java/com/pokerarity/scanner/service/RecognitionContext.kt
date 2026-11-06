package com.pokerarity.scanner.service

import com.pokerarity.scanner.util.ocr.FrameCalibrationHint
import com.pokerarity.scanner.util.ocr.ScreenGeometry
import com.pokerarity.scanner.util.ocr.scaledTo
import kotlin.math.roundToInt

/**
 * Same-source recognition context established by the FAST detail frame (Phase 2A
 * geometry + Phase 2B calibration participation) and reused by the same-source detailed
 * pass, with pixel metadata transformed into the detailed bitmap's coordinate space.
 *
 * The geometry is the already-derived [ScreenGeometry]; the hint is exactly what the
 * fast frame legitimately had (never fabricated for the detailed pass).
 */
internal data class RecognitionContext(
    val geometry: ScreenGeometry,
    val calibrationHint: FrameCalibrationHint?
) {
    /**
     * Re-expresses fast-pass pixel metadata in the bitmap space consumed by another OCR
     * pass. Recognition geometry and calibrated anchors are pixel coordinates, so reusing
     * them unchanged after a resize would make extraction compare different coordinate
     * systems.
     */
    fun forImageSize(width: Int, height: Int): RecognitionContext? {
        val sourceWidth = geometry.imageWidth
        val sourceHeight = geometry.imageHeight
        return when {
            width <= 0 || height <= 0 -> null
            sourceWidth <= 0 || sourceHeight <= 0 -> null
            width == sourceWidth && height == sourceHeight -> this
            else -> geometry.scaledTo(width, height)?.let { resizedGeometry ->
                val scaleX = width.toFloat() / sourceWidth
                val scaleY = height.toFloat() / sourceHeight
                val resizedCalibration = calibrationHint?.copy(
                    seededBarRect = android.graphics.Rect(
                        (calibrationHint.seededBarRect.left * scaleX).roundToInt(),
                        (calibrationHint.seededBarRect.top * scaleY).roundToInt(),
                        (calibrationHint.seededBarRect.right * scaleX).roundToInt(),
                        (calibrationHint.seededBarRect.bottom * scaleY).roundToInt()
                    )
                )
                RecognitionContext(resizedGeometry, resizedCalibration)
            }
        }
    }
}
