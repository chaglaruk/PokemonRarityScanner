package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.sqrt

/** Same-frame glyph shape only; no templates or independent resource authority. */
@Suppress("MagicNumber")
internal object ResourceGlyphDescriptor {
    fun descriptor(bitmap: Bitmap, amount: Rect): DoubleArray? {
        val height = amount.height()
        val box = Rect(amount.left - height * 2, amount.centerY() - height,
            amount.left - height / 10, amount.centerY() + height)
        return if (height < 2 || !inside(bitmap, box)) null else foreground(bitmap, box)?.let {
            normalize(sample(bitmap, it, background(bitmap, box))) }
    }

    private fun normalize(values: DoubleArray): DoubleArray? {
        val norm = sqrt(values.sumOf { it * it })
        return if (norm == 0.0) null else values.map { it / norm }.toDoubleArray()
    }

    private fun inside(bitmap: Bitmap, box: Rect): Boolean =
        Rect(0, 0, bitmap.width, bitmap.height).contains(box)

    private fun foreground(bitmap: Bitmap, box: Rect): Rect? {
        val background = background(bitmap, box)
        val foreground = Rect(box.right, box.bottom, box.left, box.top)
        for (y in box.top until box.bottom) for (x in box.left until box.right) {
            if (distance(bitmap.getPixel(x, y), background) > 1600) {
                foreground.left = minOf(foreground.left, x)
                foreground.top = minOf(foreground.top, y)
                foreground.right = maxOf(foreground.right, x + 1)
                foreground.bottom = maxOf(foreground.bottom, y + 1)
            }
        }
        val horizontalEdge = foreground.left == box.left || foreground.right == box.right
        val verticalEdge = foreground.top == box.top || foreground.bottom == box.bottom
        val touches = horizontalEdge || verticalEdge
        return foreground.takeUnless { it.isEmpty || touches }
    }

    private fun sample(bitmap: Bitmap, foreground: Rect, background: IntArray): DoubleArray {
        val values = DoubleArray(12 * 12 * 3)
        for (y in 0 until 12) for (x in 0 until 12) {
            val pixel = bitmap.getPixel(foreground.left + x * foreground.width() / 12,
                foreground.top + y * foreground.height() / 12)
            writePixel(values, (y * 12 + x) * 3, pixel, background)
        }
        return values
    }

    private fun writePixel(values: DoubleArray, offset: Int, pixel: Int, background: IntArray) {
        if (distance(pixel, background) > 1600) for (channel in 0..2) {
            values[offset + channel] = (background[channel] - component(pixel, channel)).toDouble()
        }
    }

    fun amountClear(bitmap: Bitmap, rect: Rect): Boolean {
        val clearance = Rect(rect.right, rect.top, rect.right + rect.height() / 2, rect.bottom)
        if (clearance.right > bitmap.width || clearance.bottom > bitmap.height || clearance.top < 0) return false
        // A saturated overlay immediately beside the last recognized digit can hide
        // trailing digits. Chevrons and ordinary action backgrounds are low-chroma.
        var occluded = 0
        for (y in clearance.top until clearance.bottom) for (x in clearance.left until clearance.right) {
            val pixel = bitmap.getPixel(x, y)
            val channels = (0..2).map { component(pixel, it) }
            if (channels.max() - channels.min() > 100) occluded++
        }
        return occluded * 10 < clearance.width() * clearance.height()
    }

    private fun background(bitmap: Bitmap, rect: Rect): IntArray {
        val corners = listOf(bitmap.getPixel(rect.left, rect.top), bitmap.getPixel(rect.right - 1, rect.top),
            bitmap.getPixel(rect.left, rect.bottom - 1), bitmap.getPixel(rect.right - 1, rect.bottom - 1))
        return IntArray(3) { channel -> corners.map { component(it, channel) }.sorted()[1] }
    }
    private fun component(pixel: Int, channel: Int): Int = (pixel shr ((2 - channel) * 8)) and 255
    private fun distance(pixel: Int, background: IntArray): Int = (0..2).sumOf { channel ->
        val delta = component(pixel, channel) - background[channel]
        delta * delta
    }
}

