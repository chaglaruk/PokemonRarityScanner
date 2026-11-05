package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.max

/** A symbol found immediately to the right of an OCR title line. */
internal enum class VisualGenderMarker { Male, Female }

/**
 * Recognizes the compact circle-and-arrow/cross glyph beside an ambiguous Nidoran title.
 * This deliberately does not infer gender from OCR text or scan the rest of the frame.
 */
internal object AdjacentGenderSymbolDetector {
    private const val MAX_SYMBOL_WIDTH_IN_LINE_HEIGHTS = 1.05f
    private const val VERTICAL_PADDING_IN_LINE_HEIGHTS = 0.14f

    fun detect(bitmap: Bitmap, titleBounds: Rect?, nameBand: Rect?): VisualGenderMarker? {
        if (titleBounds == null || nameBand == null || bitmap.isRecycled) return null
        val lineHeight = titleBounds.height()
        val roi = if (lineHeight > 0) symbolBounds(bitmap, titleBounds, nameBand, lineHeight) else null
        return roi?.let { classify(bitmap, it, lineHeight) }
    }

    private fun symbolBounds(bitmap: Bitmap, title: Rect, band: Rect, lineHeight: Int): Rect? {
        if (title.right < 0 || title.right >= bitmap.width) return null
        val padding = (lineHeight * VERTICAL_PADDING_IN_LINE_HEIGHTS).toInt()
        val roi = Rect(
            title.right,
            title.top - padding,
            title.right + (lineHeight * MAX_SYMBOL_WIDTH_IN_LINE_HEIGHTS).toInt(),
            title.bottom + padding
        )
        val visible = roi.intersect(Rect(0, 0, bitmap.width, bitmap.height)) && roi.intersect(band)
        return roi.takeIf { visible && it.width() >= lineHeight / 2 && it.height() >= lineHeight / 2 }
    }

    private fun classify(bitmap: Bitmap, roi: Rect, lineHeight: Int): VisualGenderMarker? {
        val width = roi.width()
        val height = roi.height()
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, roi.left, roi.top, width, height)
        return classifyPixels(pixels, width, height, lineHeight)
    }

    private fun classifyPixels(pixels: IntArray, width: Int, height: Int, lineHeight: Int): VisualGenderMarker? {
        if (width <= 0 || height <= 0 || pixels.size != width * height) return null
        val background = medianBorderLuminance(pixels, width, height)
        return if (background >= MIN_LIGHT_PANEL_LUMINANCE) {
            val threshold = (background - max(MIN_INK_DELTA.toFloat(), background * INK_DELTA_RATIO).toInt())
                .coerceAtLeast(MIN_INK_LUMINANCE)
            val mask = BooleanArray(pixels.size) { index -> luminance(pixels[index]) < threshold }
            val component = ComponentSearch(mask, width, height).largest()
            component?.takeIf { hasSymbolGeometry(it, lineHeight) }?.let { symbol ->
                HoleSearch(symbol, width).largest()?.let { hole -> classifyHole(symbol, hole, width) }
            }
        } else {
            null
        }
    }

    private fun hasSymbolGeometry(component: Component, lineHeight: Int): Boolean {
        val width = component.right - component.left
        val height = component.bottom - component.top
        val minimum = (lineHeight * MIN_SYMBOL_DIMENSION_RATIO).toInt().coerceAtLeast(1)
        val maximum = ceil(lineHeight * MAX_SYMBOL_DIMENSION_RATIO).toInt()
        val withinSize = width in minimum..maximum && height in minimum..maximum
        val aspectRatio = width.toFloat() / height
        return withinSize && aspectRatio in MIN_SYMBOL_ASPECT_RATIO..MAX_SYMBOL_ASPECT_RATIO
    }

    private fun classifyHole(component: Component, hole: Hole, roiWidth: Int): VisualGenderMarker? {
        val width = component.right - component.left
        val height = component.bottom - component.top
        val centerX = (hole.centerX - component.left) / width.toFloat()
        val centerY = (hole.centerY - component.top) / height.toFloat()
        val maleHead = longestHorizontalRun(
            component,
            roiWidth,
            HorizontalRunRegion(
                component.top,
                component.top + ceil(height * MALE_HEAD_HEIGHT_RATIO).toInt(),
                component.left + (width * MALE_HEAD_LEFT_RATIO).toInt(),
                component.right
            )
        )
        val femaleBar = longestHorizontalRun(
            component,
            roiWidth,
            HorizontalRunRegion(
                component.top + (height * FEMALE_CROSSBAR_TOP_RATIO).toInt(),
                component.bottom,
                component.left + (width * FEMALE_CROSSBAR_LEFT_RATIO).toInt(),
                component.left + ceil(width * FEMALE_CROSSBAR_RIGHT_RATIO).toInt()
            )
        )
        val maleHole = centerX in MALE_HOLE_X_RANGE && centerY in MALE_HOLE_Y_RANGE
        val femaleHole = centerX in FEMALE_HOLE_X_RANGE && centerY in FEMALE_HOLE_Y_RANGE
        val minimumStroke = ceil(width * MIN_GENDER_STROKE_RATIO).toInt()
        return when {
            maleHole && maleHead >= minimumStroke -> VisualGenderMarker.Male
            femaleHole && femaleBar >= minimumStroke -> VisualGenderMarker.Female
            else -> null
        }
    }

    private fun longestHorizontalRun(component: Component, roiWidth: Int, region: HorizontalRunRegion): Int {
        var longest = 0
        for (y in region.top.coerceAtLeast(component.top) until region.bottom.coerceAtMost(component.bottom)) {
            var current = 0
            for (x in region.left.coerceAtLeast(component.left) until region.right.coerceAtMost(component.right)) {
                if (component.pixels[y * roiWidth + x]) {
                    current++
                    longest = max(longest, current)
                } else {
                    current = 0
                }
            }
        }
        return longest
    }

    private fun medianBorderLuminance(pixels: IntArray, width: Int, height: Int): Int {
        val border = ArrayList<Int>((width + height) * BORDER_CAPACITY_FACTOR)
        for (x in 0 until width) {
            border += luminance(pixels[x])
            if (height > 1) border += luminance(pixels[(height - 1) * width + x])
        }
        for (y in 1 until height - 1) {
            border += luminance(pixels[y * width])
            if (width > 1) border += luminance(pixels[y * width + width - 1])
        }
        if (border.isEmpty()) return 0
        border.sort()
        return border[border.size / 2]
    }

    private fun luminance(color: Int): Int =
        (RED_WEIGHT * ((color ushr RED_SHIFT) and PIXEL_CHANNEL_MASK) +
            GREEN_WEIGHT * ((color ushr GREEN_SHIFT) and PIXEL_CHANNEL_MASK) +
            BLUE_WEIGHT * (color and PIXEL_CHANNEL_MASK)) / LUMINANCE_DIVISOR

    private const val MIN_LIGHT_PANEL_LUMINANCE = 160
    private const val MIN_INK_DELTA = 45
    private const val INK_DELTA_RATIO = 0.22f
    private const val MIN_INK_LUMINANCE = 60
    private const val MIN_COMPONENT_AREA = 30
    private const val MIN_COMPONENT_AREA_RATIO = 0.035f
    private const val MIN_HOLE_AREA = 8
    private const val MIN_HOLE_AREA_RATIO = 0.08f
    private const val MIN_SYMBOL_DIMENSION_RATIO = 0.35f
    private const val MAX_SYMBOL_DIMENSION_RATIO = 1.15f
    private const val MIN_SYMBOL_ASPECT_RATIO = 0.55f
    private const val MAX_SYMBOL_ASPECT_RATIO = 1.50f
    private const val MALE_HEAD_HEIGHT_RATIO = 0.22f
    private const val MALE_HEAD_LEFT_RATIO = 0.50f
    private const val FEMALE_CROSSBAR_TOP_RATIO = 0.72f
    private const val FEMALE_CROSSBAR_LEFT_RATIO = 0.25f
    private const val FEMALE_CROSSBAR_RIGHT_RATIO = 0.78f
    private const val MIN_GENDER_STROKE_RATIO = 0.30f
    private const val MALE_HOLE_X_MIN = 0.14f
    private const val MALE_HOLE_X_MAX = 0.42f
    private const val MALE_HOLE_Y_MIN = 0.54f
    private const val MALE_HOLE_Y_MAX = 0.86f
    private const val FEMALE_HOLE_X_MIN = 0.36f
    private const val FEMALE_HOLE_X_MAX = 0.65f
    private const val FEMALE_HOLE_Y_MIN = 0.14f
    private const val FEMALE_HOLE_Y_MAX = 0.53f
    private const val RED_WEIGHT = 299
    private const val GREEN_WEIGHT = 587
    private const val BLUE_WEIGHT = 114
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val PIXEL_CHANNEL_MASK = 0xff
    private const val LUMINANCE_DIVISOR = 1000
    private const val BORDER_CAPACITY_FACTOR = 2

    private val MALE_HOLE_X_RANGE = MALE_HOLE_X_MIN..MALE_HOLE_X_MAX
    private val MALE_HOLE_Y_RANGE = MALE_HOLE_Y_MIN..MALE_HOLE_Y_MAX
    private val FEMALE_HOLE_X_RANGE = FEMALE_HOLE_X_MIN..FEMALE_HOLE_X_MAX
    private val FEMALE_HOLE_Y_RANGE = FEMALE_HOLE_Y_MIN..FEMALE_HOLE_Y_MAX
}

private data class Component(
    val pixels: BooleanArray,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val area: Int
)

private data class Hole(val area: Int, val centerX: Float, val centerY: Float)

private data class HorizontalRunRegion(val top: Int, val bottom: Int, val left: Int, val right: Int)

private class ComponentSearch(private val mask: BooleanArray, private val width: Int, private val height: Int) {
    private val visited = BooleanArray(mask.size)
    private val queue = java.util.ArrayDeque<Int>()

    fun largest(): Component? {
        var best: Component? = null
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            val component = collect(start)
            if (component.area > (best?.area ?: 0)) best = component
        }
        val minimumArea = max(MIN_COMPONENT_AREA, (mask.size * MIN_COMPONENT_AREA_RATIO).toInt())
        return best?.takeIf { it.area >= minimumArea }
    }

    private fun collect(start: Int): Component {
        queue.clear()
        queue.add(start)
        visited[start] = true
        val pixels = BooleanArray(mask.size)
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        var area = 0
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val x = index % width
            val y = index / width
            pixels[index] = true
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x + 1)
            bottom = maxOf(bottom, y + 1)
            area++
            enqueueNeighbors(x, y)
        }
        return Component(pixels, left, top, right, bottom, area)
    }

    private fun enqueueNeighbors(x: Int, y: Int) {
        for (deltaY in NEIGHBOR_OFFSETS) {
            for (deltaX in NEIGHBOR_OFFSETS) {
                if (deltaX != 0 || deltaY != 0) {
                    val nextX = x + deltaX
                    val nextY = y + deltaY
                    if (nextX in 0 until width && nextY in 0 until height) {
                        val next = nextY * width + nextX
                        if (mask[next] && !visited[next]) {
                            visited[next] = true
                            queue.add(next)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val MIN_COMPONENT_AREA = 30
        const val MIN_COMPONENT_AREA_RATIO = 0.035f
        val NEIGHBOR_OFFSETS = -1..1
    }
}

private class HoleSearch(private val component: Component, private val imageWidth: Int) {
    private val width = component.right - component.left
    private val height = component.bottom - component.top
    private val visited = BooleanArray(width * height)
    private val queue = java.util.ArrayDeque<Int>()

    fun largest(): Hole? {
        markExteriorBackground()
        var best: Hole? = null
        for (start in visited.indices) {
            if (visited[start] || isForeground(start)) continue
            val candidate = collect(start)
            if (candidate.area > (best?.area ?: 0)) best = candidate
        }
        val minimumArea = max(MIN_HOLE_AREA, (component.area * MIN_HOLE_AREA_RATIO).toInt())
        return best?.takeIf { it.area >= minimumArea }
    }

    private fun markExteriorBackground() {
        for (x in 0 until width) {
            enqueueIfBackground(x, 0)
            enqueueIfBackground(x, height - 1)
        }
        for (y in 0 until height) {
            enqueueIfBackground(0, y)
            enqueueIfBackground(width - 1, y)
        }
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            enqueueBackgroundNeighbors(index)
        }
    }

    private fun enqueueIfBackground(x: Int, y: Int) {
        val index = y * width + x
        if (!visited[index] && !isForeground(index)) {
            visited[index] = true
            queue.add(index)
        }
    }

    private fun enqueueBackgroundNeighbors(index: Int) {
        val x = index % width
        val y = index / width
        for (delta in ORTHOGONAL_NEIGHBORS) {
            val nextX = x + delta.first
            val nextY = y + delta.second
            if (nextX in 0 until width && nextY in 0 until height) {
                val next = nextY * width + nextX
                if (!visited[next] && !isForeground(next)) {
                    visited[next] = true
                    queue.add(next)
                }
            }
        }
    }

    private fun collect(start: Int): Hole {
        queue.clear()
        queue.add(start)
        visited[start] = true
        var area = 0
        var sumX = 0L
        var sumY = 0L
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val x = index % width
            val y = index / width
            area++
            sumX += x
            sumY += y
            enqueueBackgroundNeighbors(index)
        }
        return Hole(area, sumX.toFloat() / area + component.left, sumY.toFloat() / area + component.top)
    }

    private fun isForeground(index: Int): Boolean {
        val x = index % width + component.left
        val y = index / width + component.top
        return component.pixels[y * imageWidth + x]
    }

    private companion object {
        const val MIN_HOLE_AREA = 8
        const val MIN_HOLE_AREA_RATIO = 0.08f
        val ORTHOGONAL_NEIGHBORS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
