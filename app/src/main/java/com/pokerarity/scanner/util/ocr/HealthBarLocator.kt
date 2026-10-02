package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/** Finds the narrow green HP bar inside the white detail card, including scrolled cards. */
internal object HealthBarLocator {
    private const val MIN_BITMAP_DIMENSION = 100
    private const val HORIZONTAL_SCAN_DIVISOR = 5
    private const val MIN_RUN_DIVISOR = 4
    private const val MAX_BAR_THICKNESS_DIVISOR = 35
    private const val MIN_BAR_THICKNESS = 3
    private const val PROBE_Y_DIVISOR = 50
    private const val WHITE_SAMPLE_STEP = 5
    private const val WHITE_CHANNEL_MIN = 220
    private const val WHITE_RATIO_MIN = 0.7
    private const val GREEN_CHANNEL_MIN = 150
    private const val GREEN_RED_GAP_MIN = 45
    private const val BLUE_CHANNEL_MIN = 70
    private const val VERTICAL_SCAN_NUMERATOR = 2
    private const val VERTICAL_SCAN_DENOMINATOR = 3
    private const val PIXEL_STEP = 2
    private const val RECT_BOTTOM_PADDING = 2

    private data class GreenRun(val left: Int, val right: Int) {
        val width: Int get() = right - left
    }

    private data class BarState(
        val startY: Int = -1,
        val endY: Int = -1,
        val left: Int = Int.MAX_VALUE,
        val right: Int = 0
    ) {
        val started: Boolean get() = startY >= 0

        fun extend(y: Int, run: GreenRun): BarState = BarState(
            startY = if (started) startY else y,
            endY = y,
            left = minOf(left, run.left),
            right = maxOf(right, run.right)
        )
    }

    fun locate(bitmap: Bitmap): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < MIN_BITMAP_DIMENSION || height < MIN_BITMAP_DIMENSION) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val leftLimit = width / HORIZONTAL_SCAN_DIVISOR
        val rightLimit = width * (HORIZONTAL_SCAN_DIVISOR - 1) / HORIZONTAL_SCAN_DIVISOR
        val minRun = width / MIN_RUN_DIVISOR
        var state = BarState()
        var located: Rect? = null

        for (y in 0 until height * VERTICAL_SCAN_NUMERATOR / VERTICAL_SCAN_DENOMINATOR step PIXEL_STEP) {
            if (located != null) break
            val run = bestGreenRun(pixels, width, y, leftLimit, rightLimit)
            if (run != null && run.width >= minRun) {
                state = state.extend(y, run)
            } else if (state.started) {
                located = candidateRect(state, ScanBounds(pixels, width, height, leftLimit, rightLimit))
                state = BarState()
            }
        }

        return located ?: candidateRect(state, ScanBounds(pixels, width, height, leftLimit, rightLimit))
    }

    private fun bestGreenRun(
        pixels: IntArray,
        width: Int,
        y: Int,
        leftLimit: Int,
        rightLimit: Int
    ): GreenRun? {
        var runStart = -1
        var best: GreenRun? = null
        for (x in leftLimit until rightLimit step PIXEL_STEP) {
            if (isGreen(pixels[y * width + x])) {
                if (runStart < 0) runStart = x
                val candidate = GreenRun(runStart, x)
                if (best == null || candidate.width > best.width) {
                    best = candidate
                }
            } else {
                runStart = -1
            }
        }
        return best
    }

    private data class ScanBounds(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val leftLimit: Int,
        val rightLimit: Int
    )

    private fun candidateRect(state: BarState, bounds: ScanBounds): Rect? {
        val maximumThickness = (bounds.width / MAX_BAR_THICKNESS_DIVISOR).coerceAtLeast(MIN_BAR_THICKNESS)
        val thickness = state.endY - state.startY
        val validShape = state.started && thickness in PIXEL_STEP..maximumThickness
        return if (!validShape) {
            null
        } else {
            val probeY = (state.endY + bounds.height / PROBE_Y_DIVISOR).coerceAtMost(bounds.height - 1)
            val whiteCount = (bounds.leftLimit until bounds.rightLimit step WHITE_SAMPLE_STEP).count { x ->
                isWhite(bounds.pixels[probeY * bounds.width + x])
            }
            val enoughWhite = whiteCount * WHITE_SAMPLE_STEP >=
                (bounds.rightLimit - bounds.leftLimit) * WHITE_RATIO_MIN
            if (enoughWhite) {
                Rect(state.left, state.startY, state.right, state.endY + RECT_BOTTOM_PADDING)
            } else {
                null
            }
        }
    }

    private fun isGreen(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return green > GREEN_CHANNEL_MIN &&
            green - red > GREEN_RED_GAP_MIN &&
            blue > BLUE_CHANNEL_MIN &&
            green >= blue
    }

    private fun isWhite(color: Int): Boolean =
        Color.red(color) > WHITE_CHANNEL_MIN &&
            Color.green(color) > WHITE_CHANNEL_MIN &&
            Color.blue(color) > WHITE_CHANNEL_MIN
}
