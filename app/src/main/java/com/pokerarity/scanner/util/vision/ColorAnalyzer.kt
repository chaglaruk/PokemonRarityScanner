package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    data class HueStats(
        val total: Int,
        val outsideStandardRatio: Float,
        val avgSaturation: Float,
        val avgValue: Float
    )

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
     * Compute hue statistics for a region, with "standard" hue ranges excluded.
     */
    fun getHueStats(
        bitmap: Bitmap,
        region: Rect,
        standardRanges: List<IntRange>,
        minSaturation: Float = 0.2f,
        minValue: Float = 0.2f
    ): HueStats {
        val hsv = FloatArray(3)
        var total = 0
        var outside = 0
        var sSum = 0f
        var vSum = 0f

        var y = region.top
        while (y < region.bottom) {
            var x = region.left
            while (x < region.right) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                if (hsv[1] >= minSaturation && hsv[2] >= minValue) {
                    total++
                    sSum += hsv[1]
                    vSum += hsv[2]
                    val hue = hsv[0].toInt()
                    val inStandard = standardRanges.any { hue in it }
                    if (!inStandard) outside++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        if (total == 0) {
            return HueStats(0, 0f, 0f, 0f)
        }

        return HueStats(
            total = total,
            outsideStandardRatio = outside.toFloat() / total.toFloat(),
            avgSaturation = sSum / total.toFloat(),
            avgValue = vSum / total.toFloat()
        )
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
     * Get the average saturation (S channel) of a region.
     */
    fun getAverageSaturation(bitmap: Bitmap, region: Rect? = null): Float {
        val hsv = FloatArray(3)
        var total = 0
        var saturationSum = 0f

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
                saturationSum += hsv[1]
                total++
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        return if (total > 0) saturationSum / total else 0f
    }

    /**
     * Get the dominant RGB color from a bitmap region.
     * Filters out near-white/near-black and low-saturation pixels to avoid UI noise.
     */
    fun getDominantRgb(bitmap: Bitmap, region: Rect? = null): Int {
        val hsv = FloatArray(3)
        val counts = mutableMapOf<Int, Int>()

        val left = region?.left ?: 0
        val top = region?.top ?: 0
        val right = region?.right ?: bitmap.width
        val bottom = region?.bottom ?: bitmap.height

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val p = bitmap.getPixel(x, y)
                Color.colorToHSV(p, hsv)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)

                // Skip low-sat or extreme brightness to avoid background/UI text
                if (hsv[1] < 0.25f || hsv[2] < 0.15f || hsv[2] > 0.95f) {
                    x += SAMPLE_STEP
                    continue
                }

                val qr = (r / 24) * 24
                val qg = (g / 24) * 24
                val qb = (b / 24) * 24
                val qp = Color.rgb(qr, qg, qb)
                counts[qp] = (counts[qp] ?: 0) + 1
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        return counts.maxByOrNull { it.value }?.key ?: Color.GRAY
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
     * Adaptive sprite region based on background HSV separation.
     */
    fun getSpriteRegionAdaptive(bitmap: Bitmap): Rect {
        val bg = estimateBackgroundHSV(bitmap)
        val w = bitmap.width
        val h = bitmap.height
        val leftBound = (w * 0.08f).roundToInt()
        val rightBound = (w * 0.92f).roundToInt()
        val topBound = (h * 0.18f).roundToInt()
        val bottomBound = (h * 0.78f).roundToInt()

        var minX = rightBound
        var maxX = leftBound
        var minY = bottomBound
        var maxY = topBound

        val hsv = FloatArray(3)
        var y = topBound
        while (y < bottomBound) {
            var x = leftBound
            while (x < rightBound) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val hueDiff = hueDistance(hsv[0], bg[0])
                val sDiff = abs(hsv[1] - bg[1])
                val vDiff = abs(hsv[2] - bg[2])

                val isForeground = (hueDiff > 16f && hsv[1] > 0.15f) ||
                    (sDiff > 0.20f && hsv[2] > 0.15f) ||
                    (vDiff > 0.25f && hsv[1] > 0.12f)

                if (isForeground) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        if (maxX <= minX || maxY <= minY) {
            return getSpriteRegion(bitmap)
        }

        val padX = (w * 0.02f).roundToInt()
        val padY = (h * 0.02f).roundToInt()
        return Rect(
            (minX - padX).coerceAtLeast(0),
            (minY - padY).coerceAtLeast(0),
            (maxX + padX).coerceAtMost(w),
            (maxY + padY).coerceAtMost(h)
        )
    }

    /**
     * Extract the sprite region and mask background pixels to black.
     */
    fun extractMaskedSprite(bitmap: Bitmap): Bitmap {
        val sprite = getSpriteRegionAdaptive(bitmap)
        return maskSpriteBackground(bitmap, sprite)
    }

    /**
     * Mask background-like pixels in the sprite region using a background HSV model.
     */
    fun maskSpriteBackground(bitmap: Bitmap, sprite: Rect): Bitmap {
        val bg = estimateBackgroundHSV(bitmap)
        val out = Bitmap.createBitmap(sprite.width(), sprite.height(), Bitmap.Config.ARGB_8888)
        val hsv = FloatArray(3)

        var y = 0
        while (y < sprite.height()) {
            var x = 0
            while (x < sprite.width()) {
                val px = bitmap.getPixel(sprite.left + x, sprite.top + y)
                Color.colorToHSV(px, hsv)
                val hueDiff = hueDistance(hsv[0], bg[0])
                val sDiff = abs(hsv[1] - bg[1])
                val vDiff = abs(hsv[2] - bg[2])

                val isBg = (hueDiff < 12f && sDiff < 0.18f && vDiff < 0.18f) ||
                    (hsv[1] < 0.15f && vDiff < 0.20f)

                out.setPixel(x, y, if (isBg) Color.BLACK else px)
                x++
            }
            y++
        }
        return out
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
     * Background corners: avoids sprite center and CP area.
     */
    fun getBackgroundCornerRegions(bitmap: Bitmap): List<Rect> {
        val w = bitmap.width
        val h = bitmap.height
        val top = (h * 0.12f).roundToInt()
        val bottom = (h * 0.35f).roundToInt()
        val leftWidth = (w * 0.15f).roundToInt()
        val rightStart = (w * 0.85f).roundToInt()

        val left = Rect(0, top, leftWidth.coerceAtMost(w), bottom.coerceAtMost(h))
        val right = Rect(rightStart.coerceAtLeast(0), top, w, bottom.coerceAtMost(h))
        return listOf(left, right)
    }

    /**
     * Replaces shadow aura flames (purple) with pure black to avoid corrupting shiny detection.
     */
    fun maskShadowFlames(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val hsv = FloatArray(3)

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val px = bitmap.getPixel(x, y)
                Color.colorToHSV(px, hsv)
                val hue = hsv[0].toInt()
                
                // Shadow Flame heuristic: 255-285 hue, decent saturation, and decent brightness
                val isFlame = hue in 255..285 && hsv[1] > 0.35f && hsv[2] > 0.25f

                out.setPixel(x, y, if (isFlame) Color.BLACK else px)
                x++
            }
            y++
        }
        return out
    }

    /**
     * Lucky background is usually more visible around the mid/lower side bands
     * than in the very top corners.
     */
    fun getLuckyFocusRegion(bitmap: Bitmap): Rect {
        val w = bitmap.width
        val h = bitmap.height
        return Rect(
            (w * 0.18f).roundToInt().coerceAtLeast(0),
            (h * 0.24f).roundToInt().coerceAtLeast(0),
            (w * 0.82f).roundToInt().coerceAtMost(w),
            (h * 0.62f).roundToInt().coerceAtMost(h)
        )
    }

    /**
     * Support regions around the sprite where lucky gold tends to remain visible
     * without being fully occluded by the Pokemon model.
     */
    fun getLuckySupportRegions(bitmap: Bitmap): List<Rect> {
        val w = bitmap.width
        val h = bitmap.height
        return listOf(
            Rect(
                (w * 0.04f).roundToInt(),
                (h * 0.22f).roundToInt(),
                (w * 0.24f).roundToInt().coerceAtMost(w),
                (h * 0.66f).roundToInt().coerceAtMost(h)
            ),
            Rect(
                (w * 0.76f).roundToInt().coerceAtLeast(0),
                (h * 0.22f).roundToInt(),
                w,
                (h * 0.66f).roundToInt().coerceAtMost(h)
            ),
            Rect(
                (w * 0.18f).roundToInt(),
                (h * 0.56f).roundToInt(),
                (w * 0.42f).roundToInt().coerceAtMost(w),
                (h * 0.78f).roundToInt().coerceAtMost(h)
            ),
            Rect(
                (w * 0.58f).roundToInt().coerceAtLeast(0),
                (h * 0.56f).roundToInt(),
                (w * 0.82f).roundToInt().coerceAtMost(w),
                (h * 0.78f).roundToInt().coerceAtMost(h)
            )
        )
    }

    private fun estimateBackgroundHSV(bitmap: Bitmap): FloatArray {
        val regions = getBackgroundCornerRegions(bitmap)
        val hsv = FloatArray(3)
        var sumX = 0.0
        var sumY = 0.0
        var sSum = 0f
        var vSum = 0f
        var count = 0

        for (region in regions) {
            var y = region.top
            while (y < region.bottom) {
                var x = region.left
                while (x < region.right) {
                    val pixel = bitmap.getPixel(x, y)
                    Color.colorToHSV(pixel, hsv)
                    if (hsv[2] > 0.05f) {
                        val rad = Math.toRadians(hsv[0].toDouble())
                        sumX += cos(rad)
                        sumY += sin(rad)
                        sSum += hsv[1]
                        vSum += hsv[2]
                        count++
                    }
                    x += SAMPLE_STEP
                }
                y += SAMPLE_STEP
            }
        }

        if (count == 0) return floatArrayOf(0f, 0f, 0f)

        val avgHue = Math.toDegrees(atan2(sumY, sumX)).toFloat().let { if (it < 0f) it + 360f else it }
        return floatArrayOf(avgHue, sSum / count.toFloat(), vSum / count.toFloat())
    }

    private fun hueDistance(h1: Float, h2: Float): Float {
        val diff = abs(h1 - h2)
        return kotlin.math.min(diff, 360f - diff)
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
