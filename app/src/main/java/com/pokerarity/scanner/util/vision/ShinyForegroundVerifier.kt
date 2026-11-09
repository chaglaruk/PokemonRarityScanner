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

private data class ShinyVerificationContext(val species: String, val strictMode: Boolean)

internal object ShinyForegroundVerifier {
    private const val ROI_LEFT = 0.28f
    private const val ROI_RIGHT = 0.72f
    private const val ROI_TOP = 0.08f
    private const val ROI_BOTTOM = 0.34f
    private const val MAX_ANALYSIS_WIDTH = 96
    private const val MAX_ANALYSIS_HEIGHT = 128
    private const val MIN_MASK_CONFIDENCE = 0.24f
    private const val MIN_SAMPLE_DIMENSION = 16

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
        val width = (crop.width * scale).roundToInt().coerceAtLeast(MIN_SAMPLE_DIMENSION)
        val height = (crop.height * scale).roundToInt().coerceAtLeast(MIN_SAMPLE_DIMENSION)
        val sampled = if (width == crop.width && height == crop.height) crop else {
            Bitmap.createScaledBitmap(crop, width, height, true)
        }
        val context = ShinyVerificationContext(species, strictMode)
        return try {
            verifyPixels(sampled, width, height, context)
        } finally {
            if (sampled !== crop) sampled.recycle()
            crop.recycle()
        }
    }

    private fun verifyPixels(
        sampled: Bitmap,
        width: Int,
        height: Int,
        context: ShinyVerificationContext
    ): ForegroundShinyVerification {
        val colors = IntArray(width * height)
        sampled.getPixels(colors, 0, width, 0, 0, width, height)
        val mask = ForegroundSpriteMask.segment(colors, width, height)
        return when {
            mask == null -> ForegroundShinyVerification(false, reason = "foreground_mask_unavailable")
            mask.confidence < MIN_MASK_CONFIDENCE -> ForegroundShinyVerification(
                false,
                reason = "foreground_mask_low_confidence"
            )
            else -> compareMaskedSprite(colors, mask.pixels, width, height, context)
        }
    }

    private fun compareMaskedSprite(
        colors: IntArray,
        mask: BooleanArray,
        width: Int,
        height: Int,
        context: ShinyVerificationContext
    ): ForegroundShinyVerification {
        val maskedColors = IntArray(colors.size) { index -> if (mask[index]) colors[index] else Color.BLACK }
        val sprite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            sprite.setPixels(maskedColors, 0, width, 0, 0, width, height)
            compareReference(sprite, context)
        } finally {
            sprite.recycle()
        }
    }

    private fun compareReference(bitmap: Bitmap, context: ShinyVerificationContext): ForegroundShinyVerification {
        val signature = SpriteSignature.computeFromMaskedSprite(bitmap)
        val histogram = SpriteColorSignature.computeHueHistogram(bitmap)
        val details = ShinySignatureStore.matchSignatureDetails(
            signature = signature,
            colorHist = histogram,
            altColorHist = null,
            request = ShinySignatureStore.MatchRequest(
                species = context.species,
                strictMode = context.strictMode
            )
        )
        return if (!details.referenceAvailable) {
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
    }
}

internal object ShinyForegroundDecisionGate {
    private val signatureDrivenReasons = setOf(
        "multi_view_signature_consensus",
        "strong_primary_signature_preserved",
        "supported_color_fallback"
    )

    fun shouldVerify(
        shinyPositive: Boolean,
        referenceAvailable: Boolean,
        neutralFallbackApplied: Boolean,
        strictMode: Boolean,
        reason: String
    ): Boolean =
        shinyPositive && referenceAvailable && !neutralFallbackApplied && !strictMode &&
            reason in signatureDrivenReasons

    fun shouldRejectPositive(verification: ForegroundShinyVerification?): Boolean =
        verification?.supported == true && verification.matched == false
}
