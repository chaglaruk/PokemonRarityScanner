package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect

/** One bounded CP-only retry, used only when full-frame OCR found no CP label. */
internal object CpCropOcrRecovery {
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
            result(fields, crop, "failed", "cp_crop_ocr_failed")
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
        val sufficientSize = rect?.let {
            it.width() >= MIN_CROP_WIDTH && it.height() >= MIN_CROP_HEIGHT
        } == true
        val insideBitmap = rect?.let { Rect(0, 0, bitmap.width, bitmap.height).contains(it) } == true
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

    private const val MIN_CROP_WIDTH = 64
    private const val MIN_CROP_HEIGHT = 24
    private const val MIN_DETAIL_SCREEN_CONFIDENCE = 0.70f
    private const val MIN_CP_HEADER_ANCHOR_CONFIDENCE = 0.70f
    private val SUPPORTED_DETAIL_SCREEN_TYPES = setOf(ScreenType.PokemonDetail, ScreenType.PokemonDetailScrolled)

    private data class Rejection(val status: String, val reason: String)
}
