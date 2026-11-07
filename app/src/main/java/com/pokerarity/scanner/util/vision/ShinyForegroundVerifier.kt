package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

internal data class ForegroundShinyVerification(
    val supported: Boolean,
    val matched: Boolean? = null,
    val confidence: Float? = null,
    val scoreGap: Float? = null,
    val reason: String
)

internal object ShinyForegroundVerifier {
    private const val ROI_LEFT = 0.28f
    private const val ROI_RIGHT = 0.72f
    private const val ROI_TOP = 0.08f
    private const val ROI_BOTTOM = 0.34f
    private const val MAX_ANALYSIS_WIDTH = 96
    private const val MAX_ANALYSIS_HEIGHT = 128
    private const val MIN_MASK_CONFIDENCE = 0.24f

    fun verify(bitmap: Bitmap, species: String, strictMode: Boolean): ForegroundShinyVerification {
        val left = (bitmap.width * ROI_LEFT).roundToInt().coerceIn(0, bitmap.width - 1)
        val right = (bitmap.width * ROI_RIGHT).roundToInt().coerceIn(left + 1, bitmap.width)
        val top = (bitmap.height * ROI_TOP).roundToInt().coerceIn(0, bitmap.height - 1)
        val bottom = (bitmap.height * ROI_BOTTOM).roundToInt().coerceIn(top + 1, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val scale = minOf(
            1f,
            MAX_ANALYSIS_WIDTH.toFloat() / crop.width,
            MAX_ANALYSIS_HEIGHT.toFloat() / crop.height
        )
        val width = (crop.width * scale).roundToInt().coerceAtLeast(16)
        val height = (crop.height * scale).roundToInt().coerceAtLeast(16)
        val sampled = if (width == crop.width && height == crop.height) crop else {
            Bitmap.createScaledBitmap(crop, width, height, true)
        }
        return try {
            val colors = IntArray(width * height)
            sampled.getPixels(colors, 0, width, 0, 0, width, height)
            val mask = ForegroundSpriteMask.segment(colors, width, height)
                ?: return ForegroundShinyVerification(false, reason = "foreground_mask_unavailable")
            if (mask.confidence < MIN_MASK_CONFIDENCE) {
                return ForegroundShinyVerification(false, reason = "foreground_mask_low_confidence")
            }
            val maskedColors = IntArray(colors.size) { index ->
                if (mask.pixels[index]) colors[index] else Color.BLACK
            }
            val sprite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                sprite.setPixels(maskedColors, 0, width, 0, 0, width, height)
                val signature = SpriteSignature.computeFromMaskedSprite(sprite)
                val histogram = SpriteColorSignature.computeHueHistogram(sprite)
                val details = ShinySignatureStore.matchSignatureDetails(
                    signature = signature,
                    colorHist = histogram,
                    altColorHist = null,
                    species = species,
                    strictMode = strictMode
                )
                if (!details.referenceAvailable) {
                    ForegroundShinyVerification(false, reason = "foreground_reference_unavailable")
                } else {
                    ForegroundShinyVerification(
                        supported = true,
                        matched = details.matched,
                        confidence = details.confidence,
                        scoreGap = details.scoreGap,
                        reason = details.rejectionReason
                    )
                }
            } finally {
                sprite.recycle()
            }
        } finally {
            if (sampled !== crop) sampled.recycle()
            crop.recycle()
        }
    }
}

internal object ShinyForegroundDecisionGate {
    private val signatureDrivenReasons = setOf(
        "multi_view_signature_consensus",
        "supported_color_fallback"
    )

    fun shouldVerify(
        shinyPositive: Boolean,
        referenceAvailable: Boolean,
        neutralFallbackApplied: Boolean,
        strictMode: Boolean,
        reason: String
    ): Boolean = shinyPositive && referenceAvailable && !neutralFallbackApplied && !strictMode && reason in signatureDrivenReasons

    fun shouldRejectPositive(verification: ForegroundShinyVerification?): Boolean =
        verification?.supported == true && verification.matched == false
}
