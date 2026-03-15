package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * Low-level color analysis utilities for detecting visual features in Pokemon screenshots.
 * Uses native Android Bitmap pixel access — no OpenCV dependency needed.
 *
 * All analysis samples every [SAMPLE_STEP]th pixel for performance.
 */
object ColorAnalyzer {

    /** Sample every 4th pixel for speed. */
    private const val SAMPLE_STEP = 4

    /** Target width for analysis (360p for speed). */
    private const val ANALYSIS_WIDTH = 360

    /**
     * HSV color data for a sampled pixel.
     */
    data class HSVPixel(val h: Float, val s: Float, val v: Float)

    /**
     * Downscale a bitmap to [ANALYSIS_WIDTH] for faster pixel analysis.
     */
    fun downscaleForAnalysis(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= ANALYSIS_WIDTH) return bitmap
        val ratio = ANALYSIS_WIDTH.toFloat() / bitmap.width
        val targetHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, ANALYSIS_WIDTH, targetHeight, true)
    }

    /**
     * Get the dominant hue (0-360) from a bitmap region.
     * Builds a histogram of hues (ignoring very dark or desaturated pixels)
     * and returns the peak bin.
     */
    fun getDominantHue(bitmap: Bitmap, region: Rect? = null): Float {
        val histogram = IntArray(360) // 1-degree bins
        val hsv = FloatArray(3)

        val left = region?.left ?: 0
        val top = region?.top ?: 0
        val right = region?.right ?: bitmap.width
        val bottom = region?.bottom ?: bitmap.height

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                // Only count pixels with enough saturation and brightness
                if (hsv[1] > 0.2f && hsv[2] > 0.2f) {
                    val hueBin = hsv[0].toInt().coerceIn(0, 359)
                    histogram[hueBin]++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        // Find peak hue
        var maxCount = 0
        var dominantHue = 0
        for (i in histogram.indices) {
            if (histogram[i] > maxCount) {
                maxCount = histogram[i]
                dominantHue = i
            }
        }
        return dominantHue.toFloat()
    }

    /**
     * Returns the percentage (0.0 - 1.0) of sampled pixels in a region
     * whose hue falls within [hueRange] and passes saturation/value thresholds.
     */
    fun getColorPercentage(
        bitmap: Bitmap,
        region: Rect? = null,
        hueRange: IntRange,
        minSaturation: Float = 0.3f,
        minValue: Float = 0.3f
    ): Float {
        val hsv = FloatArray(3)
        var total = 0
        var matching = 0

        val left = region?.left ?: 0
        val top = region?.top ?: 0
        val right = region?.right ?: bitmap.width
        val bottom = region?.bottom ?: bitmap.height

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                total++

                val hue = hsv[0].toInt()
                if (hue in hueRange && hsv[1] >= minSaturation && hsv[2] >= minValue) {
                    matching++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        return if (total > 0) matching.toFloat() / total else 0f
    }

    /**
     * Check if a specific color range exists in a region at a given threshold.
     */
    fun hasColorInRegion(
        bitmap: Bitmap,
        region: Rect,
        hueRange: IntRange,
        minSaturation: Float = 0.3f,
        minValue: Float = 0.3f,
        threshold: Float = 0.1f
    ): Boolean {
        return getColorPercentage(bitmap, region, hueRange, minSaturation, minValue) >= threshold
    }

    /**
     * Get the average brightness (V channel) of a region. Used for shadow detection.
     */
    fun getAverageBrightness(bitmap: Bitmap, region: Rect? = null): Float {
        val hsv = FloatArray(3)
        var total = 0
        var brightnessSum = 0f

        val left = region?.left ?: 0
        val top = region?.top ?: 0
        val right = region?.right ?: bitmap.width
        val bottom = region?.bottom ?: bitmap.height

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                brightnessSum += hsv[2]
                total++
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        return if (total > 0) brightnessSum / total else 0f
    }

    /**
     * Get the sprite area rectangle (center 30% of screen).
     */
    fun getSpriteRegion(bitmap: Bitmap): Rect {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        val halfW = (bitmap.width * 0.15f).roundToInt()
        val halfH = (bitmap.height * 0.15f).roundToInt()
        return Rect(
            (cx - halfW).coerceAtLeast(0),
            (cy - halfH).coerceAtLeast(0),
            (cx + halfW).coerceAtMost(bitmap.width),
            (cy + halfH).coerceAtMost(bitmap.height)
        )
    }

    /**
     * Get the background region (area behind the Pokemon, excluding the sprite).
     * Uses a ring around the sprite center.
     */
    fun getBackgroundRegion(bitmap: Bitmap): Rect {
        val cx = bitmap.width / 2
        val cy = (bitmap.height * 0.35f).roundToInt() // Slightly above center
        val halfW = (bitmap.width * 0.40f).roundToInt()
        val halfH = (bitmap.height * 0.25f).roundToInt()
        return Rect(
            (cx - halfW).coerceAtLeast(0),
            (cy - halfH).coerceAtLeast(0),
            (cx + halfW).coerceAtMost(bitmap.width),
            (cy + halfH).coerceAtMost(bitmap.height)
        )
    }

    /**
     * Get the border region of the sprite area for aura/shadow detection.
     */
    fun getSpriteBorderRegion(bitmap: Bitmap): Rect {
        val sprite = getSpriteRegion(bitmap)
        val expand = (bitmap.width * 0.05f).roundToInt()
        return Rect(
            (sprite.left - expand).coerceAtLeast(0),
            (sprite.top - expand).coerceAtLeast(0),
            (sprite.right + expand).coerceAtMost(bitmap.width),
            (sprite.bottom + expand).coerceAtMost(bitmap.height)
        )
    }
}
