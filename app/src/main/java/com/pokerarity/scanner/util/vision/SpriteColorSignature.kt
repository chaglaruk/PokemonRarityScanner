package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color

object SpriteColorSignature {

    private const val BINS = 12
    private const val STEP = 2

    fun computeHueHistogram(maskedSprite: Bitmap): FloatArray {
        val hist = FloatArray(BINS)
        val hsv = FloatArray(3)
        var total = 0f

        var y = 0
        while (y < maskedSprite.height) {
            var x = 0
            while (x < maskedSprite.width) {
                val pixel = maskedSprite.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (r < 12 && g < 12 && b < 12) {
                    x += STEP
                    continue
                }

                Color.colorToHSV(pixel, hsv)
                if (hsv[1] < 0.15f || hsv[2] < 0.15f) {
                    x += STEP
                    continue
                }

                val bin = ((hsv[0] / 360f) * BINS).toInt().coerceIn(0, BINS - 1)
                val weight = (hsv[1] * hsv[2]).coerceIn(0f, 1f)
                hist[bin] += weight
                total += weight
                x += STEP
            }
            y += STEP
        }

        if (total <= 0f) return hist
        for (i in hist.indices) {
            hist[i] /= total
        }
        return hist
    }

    fun computeForegroundRatio(maskedSprite: Bitmap): Float {
        var total = 0
        var foreground = 0
        var y = 0
        while (y < maskedSprite.height) {
            var x = 0
            while (x < maskedSprite.width) {
                val pixel = maskedSprite.getPixel(x, y)
                total++
                if ((Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) > 30) {
                    foreground++
                }
                x += STEP
            }
            y += STEP
        }
        return if (total == 0) 0f else foreground.toFloat() / total.toFloat()
    }
}
