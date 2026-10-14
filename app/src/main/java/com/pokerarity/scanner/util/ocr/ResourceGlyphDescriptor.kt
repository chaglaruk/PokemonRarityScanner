package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.sqrt

/** Same-frame glyph shape only; no templates or independent resource authority. */
@Suppress("MagicNumber")
internal object ResourceGlyphDescriptor {
    fun descriptor(bitmap: Bitmap, amount: Rect): DoubleArray? {
        return descriptor(bitmap, amount, false)
    }

    fun intrinsicDescriptor(bitmap: Bitmap, amount: Rect): DoubleArray? {
        return descriptor(bitmap, amount, true)
    }

    private fun descriptor(bitmap: Bitmap, amount: Rect, intrinsic: Boolean): DoubleArray? {
        val height = amount.height()
        val box = Rect(amount.left - height * 3, amount.centerY() - height,
            amount.left - height / 10, amount.centerY() + height)
        return if (height < 2 || !inside(bitmap, box)) null else foreground(bitmap, box)?.let {
            normalize(GlyphAppearance.sample(bitmap, it, background(bitmap, box), intrinsic)) }
    }

    private fun normalize(values: DoubleArray): DoubleArray? {
        val norm = sqrt(values.sumOf { it * it })
        return if (norm == 0.0) null else values.map { it / norm }.toDoubleArray()
    }

    private fun inside(bitmap: Bitmap, box: Rect): Boolean =
        Rect(0, 0, bitmap.width, bitmap.height).contains(box)

    private fun foreground(bitmap: Bitmap, box: Rect): Rect? {
        return foregroundComponents(bitmap, box).filter { foreground ->
            foreground.left != box.left && foreground.right != box.right &&
                foreground.top != box.top && foreground.bottom != box.bottom
        }.singleOrNull()
    }

    /** Keep separated ink separate: a clipped neighboring digit is not part of the glyph. */
    internal fun foregroundComponents(bitmap: Bitmap, box: Rect): List<Rect> {
        if (!inside(bitmap, box) || box.isEmpty) return emptyList()
        val background = background(bitmap, box)
        val components = mutableListOf<Rect>()
        var component: Rect? = null
        for (x in box.left until box.right) {
            val column = Rect(x, box.bottom, x + 1, box.top)
            for (y in box.top until box.bottom) {
                if (distance(bitmap.getPixel(x, y), background) > 1600) {
                    column.top = minOf(column.top, y)
                    column.bottom = maxOf(column.bottom, y + 1)
                }
            }
            if (!column.isEmpty) {
                if (component == null) component = column else component.union(column)
            } else if (component != null) {
                components += component
                component = null
            }
        }
        component?.let { components += it }
        return components
    }

    fun amountClear(bitmap: Bitmap, rect: Rect): Boolean {
        val clearance = Rect(rect.right, rect.top, rect.right + rect.height() / 2, rect.bottom)
        val margin = maxOf(1, rect.height() / 8)
        val backdrop = Rect(rect.left, rect.top - margin, rect.right, rect.bottom + margin)
        if (rect.isEmpty || !inside(bitmap, clearance) || !inside(bitmap, backdrop)) return false
        val localBackground = background(bitmap, backdrop)
        // The known saturated-cover control and the neutral digit-edge control
        // are separate visibility checks; a uniform gray backdrop passes both.
        var occluded = 0
        var edgeCovered = 0
        for (y in clearance.top until clearance.bottom) for (x in clearance.left until clearance.right) {
            val pixel = bitmap.getPixel(x, y)
            val channels = (0..2).map { component(pixel, it) }
            if (channels.max() - channels.min() > 100) occluded++
            // Skip the small antialiasing fringe omitted by a tight OCR box.
            if (x >= clearance.left + margin && x < clearance.left + margin * 2 &&
                distance(pixel, localBackground) > 1600) edgeCovered++
        }
        return occluded * 10 < clearance.width() * clearance.height() &&
            edgeCovered * 4 < margin * clearance.height()
    }

    private fun background(bitmap: Bitmap, rect: Rect): IntArray {
        val corners = listOf(bitmap.getPixel(rect.left, rect.top), bitmap.getPixel(rect.right - 1, rect.top),
            bitmap.getPixel(rect.left, rect.bottom - 1), bitmap.getPixel(rect.right - 1, rect.bottom - 1))
        return IntArray(3) { channel -> corners.map { component(it, channel) }.sorted()[1] }
    }
}

@Suppress("MagicNumber")
private object GlyphAppearance {
    fun sample(bitmap: Bitmap, foreground: Rect, background: IntArray, intrinsic: Boolean): DoubleArray {
        val values = DoubleArray(12 * 12 * 3)
        for (y in 0 until 12) for (x in 0 until 12) {
            val left = foreground.left + x * foreground.width() / 12
            val top = foreground.top + y * foreground.height() / 12
            val cell = Rect(left, top, maxOf(left + 1, foreground.left + (x + 1) * foreground.width() / 12),
                maxOf(top + 1, foreground.top + (y + 1) * foreground.height() / 12))
            val sampled = sampleCell(bitmap, cell, background, intrinsic)
            for (channel in 0..2) values[(y * 12 + x) * 3 + channel] = sampled[channel]
        }
        return values
    }

    private fun sampleCell(bitmap: Bitmap, cell: Rect, background: IntArray, intrinsic: Boolean): DoubleArray {
        val sums = DoubleArray(3)
        for (y in cell.top until cell.bottom) for (x in cell.left until cell.right) {
            accumulate(bitmap.getPixel(x, y), sums, background, intrinsic)
        }
        return DoubleArray(3) { sums[it] / (cell.width() * cell.height()) }
    }

    private fun accumulate(pixel: Int, sums: DoubleArray, background: IntArray, intrinsic: Boolean) {
        val neutral = (0..2).minOf { component(pixel, it) }
        if (distance(pixel, background) > 1600) for (channel in 0..2) {
            // Intrinsic chromatic structure survives white versus tinted action
            // backdrops. Same-frame matching still requires the existing margin.
            sums[channel] += if (intrinsic) (component(pixel, channel) - neutral).toDouble()
                else (background[channel] - component(pixel, channel)).toDouble()
        }
    }

}

private const val RGB_CHANNEL_MASK = 255
private const val BITS_PER_CHANNEL = 8
private fun component(pixel: Int, channel: Int): Int =
    (pixel shr ((2 - channel) * BITS_PER_CHANNEL)) and RGB_CHANNEL_MASK
private fun distance(pixel: Int, background: IntArray): Int = (0..2).sumOf { channel ->
        val delta = component(pixel, channel) - background[channel]
        delta * delta
}

