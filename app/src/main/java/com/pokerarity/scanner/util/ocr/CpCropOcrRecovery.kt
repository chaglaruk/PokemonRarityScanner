package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.roundToInt

/** One bounded CP-only retry, used only when full-frame OCR found no CP label. */
internal object CpCropOcrRecovery {
    private const val MIN_CROP_WIDTH = 64
    private const val MIN_CROP_HEIGHT = 24
    private const val MIN_DETAIL_SCREEN_CONFIDENCE = 0.70f
    private const val MIN_CP_HEADER_ANCHOR_CONFIDENCE = 0.70f
    private const val BASELINE_OCR_MAX_WIDTH = 900
    private const val MAX_NATIVE_PIXELS = 4_500_000L
    private const val NEUTRAL_CROP_HORIZONTAL_MARGIN_RATIO = 0.08f
    private const val NEUTRAL_TEXT_MIN_LUMA = 180
    private const val NEUTRAL_TEXT_MAX_CHROMA = 40
    private const val NEUTRAL_RETRY_UPSCALE = 2
    private const val RED_LUMA_WEIGHT = 0.299
    private const val GREEN_LUMA_WEIGHT = 0.587
    private const val BLUE_LUMA_WEIGHT = 0.114
    private val SUPPORTED_DETAIL_SCREEN_TYPES = setOf(ScreenType.PokemonDetail, ScreenType.PokemonDetailScrolled)

    internal data class Result(
        val fields: AnchoredScreenText.Fields,
        val diagnostic: CropDiagnostic
    )

    suspend fun recoverMissingCp(
        bitmap: Bitmap,
        fields: AnchoredScreenText.Fields,
        geometry: ScreenGeometry?,
        recognizeLayout: suspend (Bitmap) -> MLKitOcrProvider.Layout
    ): Result = preflightFailure(bitmap, fields, geometry) ?: run {
        val crop = requireNotNull(geometry?.crop(ScreenField.CP))
        val rect = requireNotNull(crop.rect)

        val pixels = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        val cropLayout = try {
            recognizeLayout(pixels)
        } finally {
            if (pixels !== bitmap) pixels.recycle()
        }
        val cropRead = CpFieldEvidence.readCrop(cropLayout)
        if (cropLayout.documentStatus == OcrDocumentStatus.FAILED) {
            result(CpFieldEvidence.applyCrop(fields, cropLayout), crop, "failed", "cp_crop_ocr_failed")
        } else when (cropRead.status) {
            FieldReadStatus.MISSING_NOT_VISIBLE -> result(fields, crop, "empty", "cp_crop_label_not_found")
            FieldReadStatus.READ -> result(
                CpFieldEvidence.applyCrop(fields, cropLayout), crop, "supplemented", "explicit_cp_label_read"
            )
            FieldReadStatus.VISIBLE_UNREADABLE -> result(
                CpFieldEvidence.applyCrop(fields, cropLayout), crop, "unreadable", "cp_crop_label_unreadable"
            )
            FieldReadStatus.CONFLICT -> result(
                CpFieldEvidence.applyCrop(fields, cropLayout), crop, "conflict", "cp_crop_conflicting_candidates"
            )
            FieldReadStatus.UNSUPPORTED -> result(
                CpFieldEvidence.applyCrop(fields, cropLayout), crop, "unsupported", "cp_crop_unsupported_evidence"
            )
        }
    }

    /** One bounded color-isolation fallback after native CP crop OCR finds no label. */
    suspend fun recoverNativeCpWithNeutralTextRetry(
        bitmap: Bitmap,
        fields: AnchoredScreenText.Fields,
        geometry: ScreenGeometry?,
        recognizeLayout: suspend (Bitmap) -> MLKitOcrProvider.Layout
    ): Result {
        var neutralRetryUsed = false
        val recovered = recoverMissingCp(bitmap, fields, geometry) { rawCrop ->
            val rawLayout = recognizeLayout(rawCrop)
            if (rawLayout.documentStatus == OcrDocumentStatus.FAILED ||
                CpFieldEvidence.readCrop(rawLayout).status != FieldReadStatus.MISSING_NOT_VISIBLE
            ) {
                rawLayout
            } else {
                neutralRetryUsed = true
                val neutralCrop = createNeutralBrightTextCrop(rawCrop)
                try {
                    recognizeLayout(neutralCrop)
                } finally {
                    if (!neutralCrop.isRecycled) neutralCrop.recycle()
                }
            }
        }
        if (!neutralRetryUsed) return recovered.copy(diagnostic = recovered.diagnostic.copy(
            source = "native_targeted_ocr"
        ))

        val retryReason = when (recovered.fields.cpRead.status) {
            FieldReadStatus.READ -> "neutral_bright_retry_read"
            FieldReadStatus.CONFLICT -> "neutral_bright_retry_conflict"
            FieldReadStatus.VISIBLE_UNREADABLE -> "neutral_bright_retry_unreadable"
            FieldReadStatus.MISSING_NOT_VISIBLE -> "neutral_bright_retry_no_cp_label"
            FieldReadStatus.UNSUPPORTED -> "neutral_bright_retry_unsupported"
        }
        return recovered.copy(diagnostic = recovered.diagnostic.copy(
            source = "native_targeted_ocr",
            reasons = listOf("native_raw_crop_no_cp_label", retryReason)
        ))
    }

    /** Turns bright, near-neutral pixels into black text and suppresses colored UI/art. */
    internal fun createNeutralBrightTextCrop(source: Bitmap): Bitmap {
        val horizontalMargin = (source.width * NEUTRAL_CROP_HORIZONTAL_MARGIN_RATIO).roundToInt()
        val safeMargin = horizontalMargin.coerceIn(0, (source.width - 1) / 2)
        val width = (source.width - safeMargin * 2).coerceAtLeast(1)
        val centerCrop = Bitmap.createBitmap(source, safeMargin, 0, width, source.height)
        try {
            val sourcePixels = IntArray(width * centerCrop.height)
            centerCrop.getPixels(sourcePixels, 0, width, 0, 0, width, centerCrop.height)
            val binaryPixels = IntArray(sourcePixels.size)
            for (index in sourcePixels.indices) {
                val color = sourcePixels[index]
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                val maximum = maxOf(red, green, blue)
                val minimum = minOf(red, green, blue)
                val luma = red * RED_LUMA_WEIGHT + green * GREEN_LUMA_WEIGHT + blue * BLUE_LUMA_WEIGHT
                binaryPixels[index] = if (
                    luma >= NEUTRAL_TEXT_MIN_LUMA && maximum - minimum <= NEUTRAL_TEXT_MAX_CHROMA
                ) Color.BLACK else Color.WHITE
            }
            val binary = Bitmap.createBitmap(width, centerCrop.height, Bitmap.Config.ARGB_8888)
            binary.setPixels(binaryPixels, 0, width, 0, 0, width, centerCrop.height)
            val upscaled = Bitmap.createScaledBitmap(
                binary, width * NEUTRAL_RETRY_UPSCALE, centerCrop.height * NEUTRAL_RETRY_UPSCALE, false
            )
            if (upscaled !== binary) binary.recycle()
            return upscaled
        } finally {
            if (!centerCrop.isRecycled) centerCrop.recycle()
        }
    }

    /**
     * A single native-source CP crop is useful only after the baseline frame and its
     * anchored crop both missed the CP label. This keeps full-frame OCR at the existing
     * size and bounds the exceptional retry to one selected source frame.
     */
    fun shouldAttemptNativeResolutionRetry(
        fields: AnchoredScreenText.Fields,
        cropDiagnostic: CropDiagnostic,
        geometry: ScreenGeometry?,
        recognitionWidth: Int,
        recognitionHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int
    ): Boolean {
        val sourcePixelCount = sourceWidth.toLong() * sourceHeight
        if (fields.cpRead.status != FieldReadStatus.MISSING_NOT_VISIBLE ||
            cropDiagnostic.source != "targeted_ocr" || cropDiagnostic.status != "empty" ||
            "cp_crop_label_not_found" !in cropDiagnostic.reasons ||
            recognitionWidth !in 1 until sourceWidth || recognitionHeight <= 0 ||
            recognitionHeight >= sourceHeight || sourceWidth <= BASELINE_OCR_MAX_WIDTH ||
            sourcePixelCount > MAX_NATIVE_PIXELS || geometry == null ||
            geometry.imageWidth != recognitionWidth || geometry.imageHeight != recognitionHeight ||
            !supportsCpCropRetry(geometry)
        ) return false

        val nativeGeometry = geometry.scaledTo(sourceWidth, sourceHeight) ?: return false
        val nativeCrop = nativeGeometry.crop(ScreenField.CP)
        return hasValidRect(nativeCrop?.rect, sourceWidth, sourceHeight)
    }

    private fun preflightFailure(
        bitmap: Bitmap,
        fields: AnchoredScreenText.Fields,
        geometry: ScreenGeometry?
    ): Result? {
        val crop = geometry?.crop(ScreenField.CP)
        val rejection = when {
            fields.cpRead.status != FieldReadStatus.MISSING_NOT_VISIBLE ->
                Rejection("skipped", "full_page_cp_not_missing")
            bitmap.isRecycled || geometry == null -> Rejection("unavailable", "cp_geometry_unavailable")
            geometry.imageWidth != bitmap.width || geometry.imageHeight != bitmap.height ->
                Rejection("unavailable", "cp_geometry_dimensions_mismatch")
            !supportsCpCropRetry(geometry) ->
                Rejection("skipped", "cp_crop_requires_detail_and_visible_header")
            !hasValidRect(crop?.rect, bitmap) -> Rejection("unavailable", "cp_crop_rect_invalid")
            else -> null
        }
        return rejection?.let { result(fields, crop, it.status, it.reason) }
    }

    private fun hasValidRect(rect: Rect?, bitmap: Bitmap): Boolean {
        return hasValidRect(rect, bitmap.width, bitmap.height)
    }

    private fun hasValidRect(rect: Rect?, width: Int, height: Int): Boolean {
        val sufficientSize = rect?.let {
            it.width() >= MIN_CROP_WIDTH && it.height() >= MIN_CROP_HEIGHT
        } == true
        val insideBitmap = rect?.let { Rect(0, 0, width, height).contains(it) } == true
        return sufficientSize && insideBitmap
    }

    private fun supportsCpCropRetry(geometry: ScreenGeometry): Boolean {
        val classification = geometry.classification
        // A normal-detail classification alone is not CP geometry evidence. Without a
        // classifier-detected header anchor, ScreenGeometryBuilder may supply a generic
        // top-of-screen fallback crop, which can turn unrelated text into CP evidence.
        return !classification.safeFallback &&
            classification.confidence >= MIN_DETAIL_SCREEN_CONFIDENCE &&
            classification.screenType in SUPPORTED_DETAIL_SCREEN_TYPES &&
            geometry.anchors.any { anchor ->
                anchor.name == ScreenAnchorName.CpHeader && anchor.confidence >= MIN_CP_HEADER_ANCHOR_CONFIDENCE
            }
    }

    private fun result(
        fields: AnchoredScreenText.Fields,
        crop: ScreenCrop?,
        status: String,
        reason: String
    ) = Result(
        fields,
        CropDiagnostic(
            field = ScreenField.CP.diagnosticName,
            source = "targeted_ocr",
            left = crop?.rect?.left,
            top = crop?.rect?.top,
            right = crop?.rect?.right,
            bottom = crop?.rect?.bottom,
            status = status,
            provenance = crop?.provenance?.diagnosticName,
            confidence = crop?.confidence,
            reasons = listOf(reason)
        )
    )

    private data class Rejection(val status: String, val reason: String)
}
