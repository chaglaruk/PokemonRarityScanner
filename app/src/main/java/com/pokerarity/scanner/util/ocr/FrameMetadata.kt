package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import java.time.LocalDate

internal data class FrameMetadata(val caughtLocalDate: LocalDate?, val sizeTag: String?, val lucky: Boolean)

internal fun frameMetadata(
    layout: MLKitOcrProvider.Layout,
    bitmap: Bitmap,
    geometry: ScreenGeometry?
): FrameMetadata {
    val rect = geometry?.crop(ScreenField.Date)?.rect?.takeUnless(Rect::isEmpty)
        ?: ScreenRegions.getRectForRegion(bitmap, ScreenRegions.REGION_DATE_BADGE)
    val caughtLocalDate = parseCaughtLocalDateFromOcrLayout(layout, bitmap.height, rect)
    val (size, lucky) = sizeTagAndLuckyFromOcrLayout(layout)
    return FrameMetadata(caughtLocalDate, size, lucky)
}
