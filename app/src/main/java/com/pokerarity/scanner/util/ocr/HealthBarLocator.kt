package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/** Finds the narrow green HP bar inside the white detail card, including scrolled cards. */
internal object HealthBarLocator {
    fun locate(bitmap: Bitmap): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 100 || height < 100) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val leftLimit = width / 5
        val rightLimit = width * 4 / 5
        val minRun = width / 4
        var startY = -1
        var endY = -1
        var left = width
        var right = 0
        fun green(color: Int): Boolean {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            return g > 150 && g - r > 45 && b > 70 && g >= b
        }
        fun candidate(): Rect? {
            if (startY < 0 || endY - startY !in 2..(width / 35).coerceAtLeast(3)) return null
            val probeY = (endY + height / 50).coerceAtMost(height - 1)
            val white = (leftLimit until rightLimit step 5).count { x ->
                val color = pixels[probeY * width + x]
                Color.red(color) > 220 && Color.green(color) > 220 && Color.blue(color) > 220
            }
            if (white * 5 < (rightLimit - leftLimit) * .7) return null
            return Rect(left, startY, right, endY + 2)
        }
        for (y in 0 until height * 2 / 3 step 2) {
            var runStart = -1
            var bestLeft = 0
            var bestRight = 0
            for (x in leftLimit until rightLimit step 2) {
                if (green(pixels[y * width + x])) {
                    if (runStart < 0) runStart = x
                    if (x - runStart > bestRight - bestLeft) { bestLeft = runStart; bestRight = x }
                } else runStart = -1
            }
            if (bestRight - bestLeft >= minRun) {
                if (startY < 0) startY = y
                endY = y
                left = minOf(left, bestLeft)
                right = maxOf(right, bestRight)
            } else if (startY >= 0) {
                candidate()?.let { return it }
                startY = -1; endY = -1; left = width; right = 0
            }
        }
        return candidate()
    }
}
