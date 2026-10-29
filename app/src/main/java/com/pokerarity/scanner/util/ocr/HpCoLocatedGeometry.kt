package com.pokerarity.scanner.util.ocr

import android.graphics.Rect

/** Repeated OCR rows may share one HP anchor only when their boxes strongly overlap. */
internal fun coLocatedHpRect(lines: List<MLKitOcrProvider.RecognizedBlock>): Rect? {
    val bounds = lines.mapNotNull { it.bounds }
    val intersection = bounds.takeIf { it.size == lines.size && it.size >= 2 }?.let { rectangles ->
        Rect(
            rectangles.maxOf { it.left },
            rectangles.maxOf { it.top },
            rectangles.minOf { it.right },
            rectangles.minOf { it.bottom }
        )
    }
    return intersection?.takeIf { shared ->
        val sharedArea = shared.width().toLong() * shared.height()
        !shared.isEmpty && bounds.all { rect ->
            val rectArea = rect.width().toLong() * rect.height()
            rectArea > 0L && sharedArea * 2 >= rectArea
        }
    }
}
