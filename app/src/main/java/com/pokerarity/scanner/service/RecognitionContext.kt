package com.pokerarity.scanner.service

import android.graphics.Rect
import com.pokerarity.scanner.util.ocr.FrameCalibrationHint
import com.pokerarity.scanner.util.ocr.ScreenAnchor
import com.pokerarity.scanner.util.ocr.ScreenGeometry
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
            else -> {
                fun scale(rect: Rect): Rect = Rect(
                    (rect.left * width.toFloat() / sourceWidth).roundToInt(),
                    (rect.top * height.toFloat() / sourceHeight).roundToInt(),
                    (rect.right * width.toFloat() / sourceWidth).roundToInt(),
                    (rect.bottom * height.toFloat() / sourceHeight).roundToInt()
                )

                fun List<ScreenAnchor>.scaled() = map { anchor -> anchor.copy(rect = scale(anchor.rect)) }

                val classification = geometry.classification.copy(anchors = geometry.classification.anchors.scaled())
                val resizedGeometry = geometry.copy(
                    imageWidth = width,
                    imageHeight = height,
                    classification = classification,
                    crops = geometry.crops.mapValues { (_, crop) -> crop.copy(rect = crop.rect?.let(::scale)) },
                    anchors = geometry.anchors.scaled()
                )
                val resizedCalibration = calibrationHint?.copy(seededBarRect = scale(calibrationHint.seededBarRect))
                RecognitionContext(resizedGeometry, resizedCalibration)
            }
        }
    }
}
