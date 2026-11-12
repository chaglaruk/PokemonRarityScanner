package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import com.pokerarity.scanner.util.perf.DebugScanTiming
import com.pokerarity.scanner.util.perf.ScanTimingStage
import kotlinx.coroutines.CancellationException

/** A bounded title-only retry for confirmed detail screens whose primary OCR missed the name. */
internal object NameCropOcrRecovery {
    internal data class Result(
        val layout: MLKitOcrProvider.Layout,
        val fields: AnchoredScreenText.Fields,
        val diagnostic: CropDiagnostic?,
        val durationMs: Long?
    )

    internal data class Request(
        val bitmap: Bitmap,
        val layout: MLKitOcrProvider.Layout,
        val fields: AnchoredScreenText.Fields,
        val parser: TextParser,
        val extractionContext: ExtractionContext,
        val recognizeLayout: suspend (Bitmap) -> MLKitOcrProvider.Layout,
        /** Independently routed, non-provisional Pokémon detail, not inferred from OCR title. */
        val routedDetail: Boolean = false
    )

    private data class CropRead(val layout: MLKitOcrProvider.Layout, val durationMs: Long)

    internal fun shouldAttempt(fields: AnchoredScreenText.Fields, routedDetail: Boolean = false): Boolean =
        fields.name?.acceptedSpeciesOrNull() == null && !fields.identityBlockingNumericConflict &&
            (meetsLegacyEligibility(fields) || meetsRoutedDetailEligibility(fields, routedDetail))

    internal fun boundedBounds(bounds: NameCropBounds?, width: Int, height: Int): NameCropBounds? {
        if (bounds == null || width <= 0 || height <= 0) return null
        val left = bounds.left.coerceAtLeast(0)
        val top = bounds.top.coerceAtLeast(0)
        val right = bounds.right.coerceAtMost(width)
        val bottom = bounds.bottom.coerceAtMost(height)
        val cropWidth = right - left
        val cropHeight = bottom - top
        return if (cropWidth <= 0 || cropHeight <= 0) {
            null
        } else {
            val areaRatio = cropWidth.toDouble() * cropHeight / (width.toDouble() * height)
            val withinBounds = cropWidth >= width * MIN_WIDTH_RATIO && cropWidth <= width * MAX_WIDTH_RATIO &&
                cropHeight >= height * MIN_HEIGHT_RATIO && cropHeight <= height * MAX_HEIGHT_RATIO &&
                areaRatio <= MAX_AREA_RATIO
            if (withinBounds) NameCropBounds(left, top, right, bottom) else null
        }
    }

    internal fun translate(bounds: NameCropBounds, crop: NameCropBounds) = NameCropBounds(
        bounds.left + crop.left,
        bounds.top + crop.top,
        bounds.right + crop.left,
        bounds.bottom + crop.top
    )

    internal fun merge(
        source: MLKitOcrProvider.Layout,
        cropped: MLKitOcrProvider.Layout,
        cropBounds: NameCropBounds
    ): MLKitOcrProvider.Layout {
        if (cropped.documentStatus != OcrDocumentStatus.SUCCESS) return source
        fun translate(block: MLKitOcrProvider.RecognizedBlock) = block.copy(
            bounds = block.bounds?.let {
                translate(it.toNameCropBounds(), cropBounds).toAndroidRect()
            }
        )
        return source.copy(
            lines = source.lines + cropped.lines.map(::translate),
            elements = source.elements + cropped.elements.map(::translate)
        )
    }

    internal suspend fun recover(request: Request): Result {
        val original = Result(request.layout, request.fields, null, null)
        if (!shouldAttempt(request.fields, request.routedDetail)) return original
        val bounds = request.fields.nameRect?.toNameCropBounds()?.let {
            boundedBounds(it, request.bitmap.width, request.bitmap.height)
        }
        return bounds?.let { recoverAtBounds(request, it) } ?: original
    }

    private suspend fun recoverAtBounds(request: Request, bounds: NameCropBounds): Result {
        val cropRead = readCrop(request.bitmap, bounds, request.recognizeLayout)
        val diagnostic = diagnostic(bounds, cropRead.layout.documentStatus, false)
        if (cropRead.layout.documentStatus != OcrDocumentStatus.SUCCESS) {
            return Result(request.layout, request.fields, diagnostic, cropRead.durationMs)
        }
        return resolveCrop(request, bounds, cropRead)
    }

    private fun resolveCrop(request: Request, bounds: NameCropBounds, cropRead: CropRead): Result {
        val merged = merge(request.layout, cropRead.layout, bounds)
        val recoveredFields = DebugScanTiming.measure(ScanTimingStage.FIELD_PARSE) {
            AnchoredScreenText.extract(
                merged, request.parser, request.bitmap.width, request.bitmap.height, request.extractionContext
            )
        }
        val recovered = recoveredFields.name?.acceptedSpeciesOrNull() != null
        return if (recovered) {
            Result(
                merged,
                recoveredFields,
                diagnostic(bounds, cropRead.layout.documentStatus, true),
                cropRead.durationMs
            )
        } else {
            Result(
                request.layout,
                request.fields,
                diagnostic(bounds, cropRead.layout.documentStatus, false),
                cropRead.durationMs
            )
        }
    }

    private suspend fun readCrop(
        bitmap: Bitmap,
        bounds: NameCropBounds,
        recognizeLayout: suspend (Bitmap) -> MLKitOcrProvider.Layout
    ): CropRead {
        val started = SystemClock.elapsedRealtime()
        val layout = try {
            val crop = Bitmap.createBitmap(
                bitmap, bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top
            )
            try {
                recognizeLayout(crop)
            } finally {
                if (!crop.isRecycled) crop.recycle()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MLKitOcrProvider.Layout(emptyList(), emptyList(), OcrDocumentStatus.FAILED)
        }
        return CropRead(layout, SystemClock.elapsedRealtime() - started)
    }

    private fun diagnostic(
        bounds: NameCropBounds,
        documentStatus: OcrDocumentStatus,
        recovered: Boolean
    ) = CropDiagnostic(
        field = "NameFallback",
        source = "targeted_ocr",
        left = bounds.left,
        top = bounds.top,
        right = bounds.right,
        bottom = bounds.bottom,
        status = when {
            documentStatus == OcrDocumentStatus.FAILED -> "failed"
            documentStatus == OcrDocumentStatus.EMPTY -> "empty"
            recovered -> "recovered"
            else -> "unresolved"
        },
        provenance = "source_resolution_title_band",
        confidence = null,
        reasons = listOf("full_frame_name_missing")
    )

    private fun NameCropBounds.toAndroidRect() = Rect(left, top, right, bottom)

    private const val MIN_WIDTH_RATIO = 0.30f
    private const val MAX_WIDTH_RATIO = 0.90f
    private const val MIN_HEIGHT_RATIO = 0.025f
    private const val MAX_HEIGHT_RATIO = 0.15f
    private const val MAX_AREA_RATIO = 0.12
}

private fun meetsLegacyEligibility(fields: AnchoredScreenText.Fields): Boolean =
    fields.detailScreen && fields.cpRead.status == FieldReadStatus.READ &&
        fields.hpRead.status == FieldReadStatus.READ && fields.types != null &&
        fields.powerUpRead.status == FieldReadStatus.READ

private fun meetsRoutedDetailEligibility(fields: AnchoredScreenText.Fields, routedDetail: Boolean): Boolean =
    routedDetail && fields.nameRect != null && hasIndependentDetailCorroboration(fields)

private fun hasIndependentDetailCorroboration(fields: AnchoredScreenText.Fields): Boolean {
    val cpRead = fields.cpRead.status == FieldReadStatus.READ
    val hpRead = fields.hpRead.status == FieldReadStatus.READ
    val candyRead = fields.candyRead.status == FieldReadStatus.READ
    return cpRead && (hpRead || candyRead) || hpRead && (candyRead || fields.types != null)
}
