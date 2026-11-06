package com.pokerarity.scanner.util.ocr

private const val BASELINE_OCR_MAX_WIDTH = 900
private const val MAX_NATIVE_PIXELS = 4_500_000L

internal data class NativeResolutionRetryInput(
    val fields: AnchoredScreenText.Fields,
    val cropDiagnostic: CropDiagnostic,
    val geometry: ScreenGeometry?,
    val recognitionWidth: Int,
    val recognitionHeight: Int,
    val sourceWidth: Int,
    val sourceHeight: Int
)

internal object NativeCpRetryEligibility {
    fun shouldAttempt(input: NativeResolutionRetryInput): Boolean =
        hasConfirmedCropMiss(input) &&
            usesReducedFrame(input) &&
            hasUsableSource(input) &&
            hasValidNativeCrop(input)

    private fun hasConfirmedCropMiss(input: NativeResolutionRetryInput): Boolean {
        val fieldsMissing = input.fields.cpRead.status == FieldReadStatus.MISSING_NOT_VISIBLE
        val targetedEmptyCrop = input.cropDiagnostic.source == "targeted_ocr" &&
            input.cropDiagnostic.status == "empty" &&
            "cp_crop_label_not_found" in input.cropDiagnostic.reasons
        return fieldsMissing && targetedEmptyCrop
    }

    private fun usesReducedFrame(input: NativeResolutionRetryInput): Boolean =
        input.recognitionWidth in 1 until input.sourceWidth &&
            input.recognitionHeight in 1 until input.sourceHeight &&
            input.sourceWidth > BASELINE_OCR_MAX_WIDTH

    private fun hasUsableSource(input: NativeResolutionRetryInput): Boolean {
        val sourcePixels = input.sourceWidth.toLong() * input.sourceHeight
        return input.sourceWidth > 0 && input.sourceHeight > 0 && sourcePixels <= MAX_NATIVE_PIXELS
    }

    private fun hasValidNativeCrop(input: NativeResolutionRetryInput): Boolean {
        val rect = input.geometry
            ?.takeIf(CpCropOcrRecovery::supportsCpCropRetry)
            ?.takeIf { it.imageWidth == input.recognitionWidth && it.imageHeight == input.recognitionHeight }
            ?.scaledTo(input.sourceWidth, input.sourceHeight)
            ?.crop(ScreenField.CP)
            ?.rect
        return CpCropOcrRecovery.hasValidRect(rect, input.sourceWidth, input.sourceHeight)
    }
}

internal fun nativeRetryDiagnostic(
    geometry: ScreenGeometry?,
    width: Int,
    height: Int,
    status: String,
    reason: String
): CropDiagnostic {
    val crop = geometry?.scaledTo(width, height)?.crop(ScreenField.CP)
    return CropDiagnostic(
        field = ScreenField.CP.diagnosticName,
        source = "native_targeted_ocr",
        left = crop?.rect?.left,
        top = crop?.rect?.top,
        right = crop?.rect?.right,
        bottom = crop?.rect?.bottom,
        status = status,
        provenance = crop?.provenance?.diagnosticName,
        confidence = crop?.confidence,
        reasons = listOf(reason)
    )
}
