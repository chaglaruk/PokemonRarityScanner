package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import kotlin.math.roundToInt

object AppraisalBarAnalyzer {

    data class Result(
        val attack: Int,
        val defense: Int,
        val stamina: Int,
        val confidence: Float
    )

    fun analyze(bitmap: Bitmap): Result? {
        val width = bitmap.width
        val height = bitmap.height
        val searchTop = (height * 0.60f).toInt()
        val searchBottom = (height * 0.84f).toInt()
        val left = (width * 0.16f).toInt()
        val right = (width * 0.90f).toInt()
        val rowHits = mutableListOf<Int>()

        for (y in searchTop until searchBottom step 2) {
            var orange = 0
            var samples = 0
            for (x in left until right step 4) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r >= 220 && g in 90..190 && b <= 90) orange++
                samples++
            }
            if (samples > 0 && orange.toFloat() / samples.toFloat() > 0.12f) {
                rowHits += y
            }
        }
        if (rowHits.isEmpty()) return null

        val bands = mutableListOf<IntRange>()
        var start = rowHits.first()
        var previous = rowHits.first()
        rowHits.drop(1).forEach { y ->
            if (y - previous > 10) {
                bands += start..previous
                start = y
            }
            previous = y
        }
        bands += start..previous
        val selected = bands.sortedByDescending { it.last - it.first }.take(3).sortedBy { it.first }
        if (selected.size != 3) return null

        val barLeft = left + ((right - left) * 0.06f).toInt()
        val barWidth = ((right - left) * 0.78f).toInt().coerceAtLeast(1)
        val values = selected.map { band ->
            val y = (band.first + band.last) / 2
            var maxColoredX = barLeft
            for (x in barLeft until (barLeft + barWidth).coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r >= 220 && g in 90..190 && b <= 90) {
                    maxColoredX = x
                }
            }
            val ratio = ((maxColoredX - barLeft + 1).toFloat() / barWidth.toFloat()).coerceIn(0f, 1f)
            ratioToIv(ratio)
        }
        val confidence = selected.size / 3f
        return Result(values[0], values[1], values[2], confidence)
    }

    internal fun ratioToIv(ratio: Float): Int {
        return (ratio.coerceIn(0f, 1f) * 15f).roundToInt().coerceIn(0, 15)
    }
}
