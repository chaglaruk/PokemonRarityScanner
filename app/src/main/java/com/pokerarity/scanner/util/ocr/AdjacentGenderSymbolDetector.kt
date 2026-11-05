package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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
        if (lineHeight <= 0 || titleBounds.right < 0 || titleBounds.right >= bitmap.width) return null

        val padding = (lineHeight * VERTICAL_PADDING_IN_LINE_HEIGHTS).toInt()
        val roi = Rect(
            titleBounds.right,
            titleBounds.top - padding,
            titleBounds.right + (lineHeight * MAX_SYMBOL_WIDTH_IN_LINE_HEIGHTS).toInt(),
            titleBounds.bottom + padding
        )
        if (!roi.intersect(Rect(0, 0, bitmap.width, bitmap.height))) return null
        if (!roi.intersect(nameBand)) return null
        if (roi.width() < lineHeight / 2 || roi.height() < lineHeight / 2) return null

        val pixels = IntArray(roi.width() * roi.height())
        bitmap.getPixels(pixels, 0, roi.width(), roi.left, roi.top, roi.width(), roi.height())
        return classify(pixels, roi.width(), roi.height(), lineHeight)
    }

    private fun classify(pixels: IntArray, width: Int, height: Int, lineHeight: Int): VisualGenderMarker? {
        if (width <= 0 || height <= 0 || pixels.size != width * height) return null
        val background = medianBorderLuminance(pixels, width, height)
        if (background < MIN_LIGHT_PANEL_LUMINANCE) return null
        val threshold = (background - max(MIN_INK_DELTA.toFloat(), background * INK_DELTA_RATIO).toInt())
            .coerceAtLeast(MIN_INK_LUMINANCE)
        val ink = BooleanArray(pixels.size) { index -> luminance(pixels[index]) < threshold }
        val component = largestComponent(ink, width, height) ?: return null

        val componentWidth = component.right - component.left
        val componentHeight = component.bottom - component.top
        val minDimension = (lineHeight * MIN_SYMBOL_DIMENSION_RATIO).toInt().coerceAtLeast(1)
        val maxDimension = ceil(lineHeight * MAX_SYMBOL_DIMENSION_RATIO).toInt()
        if (componentWidth !in minDimension..maxDimension ||
            componentHeight !in minDimension..maxDimension
        ) {
            return null
        }

        val aspectRatio = componentWidth.toFloat() / componentHeight
        if (aspectRatio !in MIN_SYMBOL_ASPECT_RATIO..MAX_SYMBOL_ASPECT_RATIO) return null

        val hole = largestEnclosedHole(component, width, height) ?: return null
        val centerX = (hole.centerX - component.left) / componentWidth.toFloat()
        val centerY = (hole.centerY - component.top) / componentHeight.toFloat()
        val topArrowHead = longestHorizontalRun(
            component, width, height,
            rowStart = component.top,
            rowEnd = component.top + ceil(componentHeight * MALE_HEAD_HEIGHT_RATIO).toInt(),
            columnStart = component.left + (componentWidth * MALE_HEAD_LEFT_RATIO).toInt(),
            columnEnd = component.right
        )
        val lowerCrossbar = longestHorizontalRun(
            component, width, height,
            rowStart = component.top + (componentHeight * FEMALE_CROSSBAR_TOP_RATIO).toInt(),
            rowEnd = component.bottom,
            columnStart = component.left + (componentWidth * FEMALE_CROSSBAR_LEFT_RATIO).toInt(),
            columnEnd = component.left + ceil(componentWidth * FEMALE_CROSSBAR_RIGHT_RATIO).toInt()
        )

        val maleHole = centerX in MALE_HOLE_X_RANGE && centerY in MALE_HOLE_Y_RANGE
        val femaleHole = centerX in FEMALE_HOLE_X_RANGE && centerY in FEMALE_HOLE_Y_RANGE
        return when {
            maleHole && topArrowHead >= ceil(componentWidth * MIN_GENDER_STROKE_RATIO).toInt() ->
                VisualGenderMarker.Male
            femaleHole && lowerCrossbar >= ceil(componentWidth * MIN_GENDER_STROKE_RATIO).toInt() ->
                VisualGenderMarker.Female
            else -> null
        }
    }

    private data class Component(
        val pixels: BooleanArray,
        val roiWidth: Int,
        val roiHeight: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val area: Int
    )

    private data class Hole(val area: Int, val centerX: Float, val centerY: Float)

    private fun largestComponent(mask: BooleanArray, width: Int, height: Int): Component? {
        val visited = BooleanArray(mask.size)
        val queue = ArrayDeque<Int>()
        var best: Component? = null
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            queue.clear()
            queue.add(start)
            visited[start] = true
            var left = width
            var top = height
            var right = 0
            var bottom = 0
            var area = 0
            val componentPixels = BooleanArray(mask.size)
            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                val x = index % width
                val y = index / width
                componentPixels[index] = true
                left = min(left, x)
                top = min(top, y)
                right = max(right, x + 1)
                bottom = max(bottom, y + 1)
                area++
                for (deltaY in -1..1) for (deltaX in -1..1) {
                    if (deltaX == 0 && deltaY == 0) continue
                    val nextX = x + deltaX
                    val nextY = y + deltaY
                    if (nextX !in 0 until width || nextY !in 0 until height) continue
                    val next = nextY * width + nextX
                    if (mask[next] && !visited[next]) {
                        visited[next] = true
                        queue.add(next)
                    }
                }
            }
            if (area > (best?.area ?: 0)) {
                best = Component(componentPixels, width, height, left, top, right, bottom, area)
            }
        }
        val minimumArea = max(MIN_COMPONENT_AREA, (mask.size * MIN_COMPONENT_AREA_RATIO).toInt())
        return best?.takeIf { it.area >= minimumArea }
    }

    private fun largestEnclosedHole(component: Component, width: Int, height: Int): Hole? {
        val localWidth = component.right - component.left
        val localHeight = component.bottom - component.top
        val visited = BooleanArray(localWidth * localHeight)
        val queue = ArrayDeque<Int>()
        fun isForeground(index: Int): Boolean {
            val x = index % localWidth + component.left
            val y = index / localWidth + component.top
            return component.pixels[y * width + x]
        }
        fun enqueueExterior(x: Int, y: Int) {
            val index = y * localWidth + x
            if (!visited[index] && !isForeground(index)) {
                visited[index] = true
                queue.add(index)
            }
        }

        for (x in 0 until localWidth) {
            enqueueExterior(x, 0)
            enqueueExterior(x, localHeight - 1)
        }
        for (y in 0 until localHeight) {
            enqueueExterior(0, y)
            enqueueExterior(localWidth - 1, y)
        }
        floodBackground(queue, visited, localWidth, localHeight, ::isForeground)

        var best: Hole? = null
        for (start in visited.indices) {
            if (visited[start] || isForeground(start)) continue
            queue.clear()
            queue.add(start)
            visited[start] = true
            var area = 0
            var sumX = 0L
            var sumY = 0L
            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                val x = index % localWidth
                val y = index / localWidth
                area++
                sumX += x
                sumY += y
                for (delta in HOLE_NEIGHBORS) {
                    val nextX = x + delta.first
                    val nextY = y + delta.second
                    if (nextX !in 0 until localWidth || nextY !in 0 until localHeight) continue
                    val next = nextY * localWidth + nextX
                    if (!visited[next] && !isForeground(next)) {
                        visited[next] = true
                        queue.add(next)
                    }
                }
            }
            if (area > (best?.area ?: 0)) {
                best = Hole(
                    area,
                    sumX.toFloat() / area + component.left,
                    sumY.toFloat() / area + component.top
                )
            }
        }
        val minimumHole = max(MIN_HOLE_AREA, (component.area * MIN_HOLE_AREA_RATIO).toInt())
        return best?.takeIf { it.area >= minimumHole }
    }

    private fun floodBackground(
        queue: ArrayDeque<Int>,
        visited: BooleanArray,
        width: Int,
        height: Int,
        isForeground: (Int) -> Boolean
    ) {
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val x = index % width
            val y = index / width
            for (delta in HOLE_NEIGHBORS) {
                val nextX = x + delta.first
                val nextY = y + delta.second
                if (nextX !in 0 until width || nextY !in 0 until height) continue
                val next = nextY * width + nextX
                if (!visited[next] && !isForeground(next)) {
                    visited[next] = true
                    queue.add(next)
                }
            }
        }
    }

    private fun longestHorizontalRun(
        component: Component,
        roiWidth: Int,
        roiHeight: Int,
        rowStart: Int,
        rowEnd: Int,
        columnStart: Int,
        columnEnd: Int
    ): Int {
        var longest = 0
        for (y in rowStart.coerceAtLeast(component.top) until rowEnd.coerceAtMost(component.bottom)) {
            var current = 0
            for (x in columnStart.coerceAtLeast(component.left) until columnEnd.coerceAtMost(component.right)) {
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
        val border = ArrayList<Int>((width + height) * 2)
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
        (RED_WEIGHT * ((color ushr 16) and 0xff) +
            GREEN_WEIGHT * ((color ushr 8) and 0xff) +
            BLUE_WEIGHT * (color and 0xff)) / LUMINANCE_DIVISOR

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
    private const val LUMINANCE_DIVISOR = 1000

    private val MALE_HOLE_X_RANGE = MALE_HOLE_X_MIN..MALE_HOLE_X_MAX
    private val MALE_HOLE_Y_RANGE = MALE_HOLE_Y_MIN..MALE_HOLE_Y_MAX
    private val FEMALE_HOLE_X_RANGE = FEMALE_HOLE_X_MIN..FEMALE_HOLE_X_MAX
    private val FEMALE_HOLE_Y_RANGE = FEMALE_HOLE_Y_MIN..FEMALE_HOLE_Y_MAX
    private val HOLE_NEIGHBORS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
}
