package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect

internal data class NameCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal fun Rect.toNameCropBounds() = NameCropBounds(left, top, right, bottom)

/**
 * Name-only OCR is attempted only after a detail layout has independent visual fields
 * and the full-frame OCR missed the species title. The rectangle is bounded to a title
 * band so this recovery cannot turn a whole-screen replay into an unconditional second OCR.
 */
internal fun shouldAttemptTargetedNameCrop(fields: AnchoredScreenText.Fields): Boolean =
    fields.detailScreen &&
        fields.name?.acceptedSpeciesOrNull() == null &&
        fields.cpRead.status == FieldReadStatus.READ &&
        fields.hpRead.status == FieldReadStatus.READ &&
        fields.types != null &&
        fields.powerUpRead.status == FieldReadStatus.READ &&
        !fields.identityBlockingNumericConflict

internal fun boundedNameCropBounds(rect: NameCropBounds?, width: Int, height: Int): NameCropBounds? {
    if (rect == null || width <= 0 || height <= 0) return null
    val left = rect.left.coerceAtLeast(0)
    val top = rect.top.coerceAtLeast(0)
    val right = rect.right.coerceAtMost(width)
    val bottom = rect.bottom.coerceAtMost(height)
    val boundedWidth = right - left
    val boundedHeight = bottom - top
    if (boundedWidth <= 0 || boundedHeight <= 0) return null
    val areaRatio = boundedWidth.toDouble() * boundedHeight / (width.toDouble() * height)
    val withinBounds = boundedWidth >= width * MIN_NAME_WIDTH_RATIO &&
        boundedWidth <= width * MAX_NAME_WIDTH_RATIO &&
        boundedHeight >= height * MIN_NAME_HEIGHT_RATIO &&
        boundedHeight <= height * MAX_NAME_HEIGHT_RATIO &&
        areaRatio <= MAX_NAME_AREA_RATIO
    return if (withinBounds) NameCropBounds(left, top, right, bottom) else null
}

internal fun translateNameCropBounds(bounds: NameCropBounds, cropBounds: NameCropBounds) =
    NameCropBounds(
        bounds.left + cropBounds.left,
        bounds.top + cropBounds.top,
        bounds.right + cropBounds.left,
        bounds.bottom + cropBounds.top
    )

internal fun mergeNameCropLayout(
    source: MLKitOcrProvider.Layout,
    cropped: MLKitOcrProvider.Layout,
    cropBounds: NameCropBounds
): MLKitOcrProvider.Layout {
    if (cropped.documentStatus != OcrDocumentStatus.SUCCESS) return source
    fun translate(block: MLKitOcrProvider.RecognizedBlock) = block.copy(
        bounds = block.bounds?.let { translateNameCropBounds(it.toNameCropBounds(), cropBounds) }
            ?.let { Rect(it.left, it.top, it.right, it.bottom) }
    )
    return source.copy(
        lines = source.lines + cropped.lines.map(::translate),
        elements = source.elements + cropped.elements.map(::translate)
    )
}

private const val MIN_NAME_WIDTH_RATIO = 0.30f
private const val MAX_NAME_WIDTH_RATIO = 0.90f
private const val MIN_NAME_HEIGHT_RATIO = 0.025f
private const val MAX_NAME_HEIGHT_RATIO = 0.15f
private const val MAX_NAME_AREA_RATIO = 0.12
