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
        val recognizeLayout: suspend (Bitmap) -> MLKitOcrProvider.Layout
    )

    private data class CropRead(val layout: MLKitOcrProvider.Layout, val durationMs: Long)

    internal fun shouldAttempt(fields: AnchoredScreenText.Fields): Boolean =
        fields.detailScreen &&
            fields.name?.acceptedSpeciesOrNull() == null &&
            fields.cpRead.status == FieldReadStatus.READ &&
            fields.hpRead.status == FieldReadStatus.READ &&
            fields.types != null &&
            fields.powerUpRead.status == FieldReadStatus.READ &&
            !fields.identityBlockingNumericConflict

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
        if (!shouldAttempt(request.fields)) return original
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
            recoverNidoranGenderFromAdjacentSymbol(request, bounds, cropRead, merged, recoveredFields)
                ?: Result(request.layout, request.fields,
                    diagnostic(bounds, cropRead.layout.documentStatus, false), cropRead.durationMs)
        }
    }

    private fun recoverNidoranGenderFromAdjacentSymbol(
        request: Request,
        bounds: NameCropBounds,
        cropRead: CropRead,
        merged: MLKitOcrProvider.Layout,
        fields: AnchoredScreenText.Fields
    ): Result? {
        val decision = fields.name as? SpeciesNameDecision.Uncertain ?: return null
        if (fields.nameRaw?.trim()?.equals(NIDORAN_TITLE, ignoreCase = true) != true ||
            AMBIGUOUS_NIDORAN_REASON !in decision.diagnostics.reasonCodes
        ) {
            return null
        }
        val nameBand = request.extractionContext.nameBand
            ?: request.extractionContext.anchoredNameFallbackBand
            ?: return null
        val marker = AdjacentGenderSymbolDetector.detect(
            request.bitmap, fields.nameTextBounds, nameBand
        ) ?: return null
        val glyph = when (marker) {
            VisualGenderMarker.Male -> MALE_GLYPH
            VisualGenderMarker.Female -> FEMALE_GLYPH
        }
        val resolved = request.parser.decideSpeciesName("$NIDORAN_TITLE $glyph")
        if (resolved.acceptedSpeciesOrNull() == null) return null

        val markerReason = when (marker) {
            VisualGenderMarker.Male -> "visual_gender_marker_male"
            VisualGenderMarker.Female -> "visual_gender_marker_female"
        }
        val cropDiagnostic = diagnostic(bounds, cropRead.layout.documentStatus, true).copy(
            reasons = listOf("full_frame_name_missing", markerReason)
        )
        return Result(merged, fields.copy(name = resolved), cropDiagnostic, cropRead.durationMs)
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
    private const val NIDORAN_TITLE = "Nidoran"
    private const val AMBIGUOUS_NIDORAN_REASON = "ambiguous_nidoran"
    private const val MALE_GLYPH = "\u2642"
    private const val FEMALE_GLYPH = "\u2640"
}
