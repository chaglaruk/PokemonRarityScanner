package com.pokerarity.scanner.util.ocr

import android.graphics.Rect

internal data class NameCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal fun Rect.toNameCropBounds() = NameCropBounds(left, top, right, bottom)
