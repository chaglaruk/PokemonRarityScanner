package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

object ArcPointAnalyzer {

    data class Result(
        val x: Float,
        val y: Float,
        val angleDegrees: Double,
        val estimatedLevel: Double,
        val confidence: Float
    )

    private const val START_ANGLE = 192.0
    private const val END_ANGLE = -12.0

    fun detect(bitmap: Bitmap): Result? {
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height * 0.402f
        val radiusMin = bitmap.width * 0.33f
        val radiusMax = bitmap.width * 0.37f
        val hits = mutableListOf<Pair<Int, Int>>()

        for (y in (centerY - radiusMax).toInt().coerceAtLeast(0) until (centerY + radiusMax).toInt().coerceAtMost(bitmap.height)) {
            for (x in (centerX - radiusMax).toInt().coerceAtLeast(0) until (centerX + radiusMax).toInt().coerceAtMost(bitmap.width)) {
                val dx = x - centerX
                val dy = centerY - y
                val radius = hypot(dx, dy)
                if (radius !in radiusMin..radiusMax) continue
                if (rawArcAngle(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))) == null) continue
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = (r + g + b) / 3f
                val chroma = maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))
                if (brightness < 235f || chroma > 28) continue
                if (localWhiteDensity(bitmap, x, y) >= 0.15f) {
                    hits += x to y
                }
            }
        }

        if (hits.isEmpty()) {
            var brightest: Pair<Int, Int>? = null
            var bestBrightness = -1f
            for (y in 0 until bitmap.height step 2) {
                for (x in 0 until bitmap.width step 2) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val brightness = (r + g + b) / 3f
                    val chroma = maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))
                    if (brightness > bestBrightness) {
                        bestBrightness = brightness
                        brightest = x to y
                    }
                    if (brightness < 245f || chroma > 20) continue
                    if (localWhiteDensity(bitmap, x, y) >= 0.15f) {
                        hits += x to y
                    }
                }
            }
            brightest?.let { if (hits.isEmpty()) hits += it }
        }

        if (hits.isEmpty()) return null
        val bestX = hits.map { it.first }.average().toFloat()
        val bestY = hits.map { it.second }.average().toFloat()
        val angle = rawArcAngle(Math.toDegrees(atan2((centerY - bestY).toDouble(), (bestX - centerX).toDouble())))
            ?: fallbackArcAngle(Math.toDegrees(atan2((centerY - bestY).toDouble(), (bestX - centerX).toDouble())))
        val progress = ((START_ANGLE - angle) / (START_ANGLE - END_ANGLE)).coerceIn(0.0, 1.0)
        val level = 1.0 + progress * 49.0
        val confidence = (hits.size / 80f).coerceIn(0f, 1f)
        Log.d("ArcPointAnalyzer", "Arc dot detected at ($bestX,$bestY) angle=$angle level=$level confidence=$confidence")
        return Result(bestX, bestY, angle, level, confidence)
    }

    private fun rawArcAngle(angle: Double): Double? {
        val normalized = if (angle < 0.0) angle + 360.0 else angle
        val direct = normalized
        if (direct in END_ANGLE..START_ANGLE) {
            return direct
        }
        val wrapped = normalized - 360.0
        return wrapped.takeIf { it in END_ANGLE..START_ANGLE }
    }

    private fun fallbackArcAngle(angle: Double): Double {
        val normalized = if (angle < 0.0) angle + 360.0 else angle
        return (if (normalized > START_ANGLE) normalized - 360.0 else normalized)
            .coerceIn(END_ANGLE, START_ANGLE)
    }

    private fun localWhiteDensity(bitmap: Bitmap, x: Int, y: Int): Float {
        var hits = 0
        var total = 0
        for (dy in -2..2) {
            for (dx in -2..2) {
                val px = (x + dx).coerceIn(0, bitmap.width - 1)
                val py = (y + dy).coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(px, py)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = (r + g + b) / 3
                val chroma = maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))
                if (brightness >= 220 && chroma <= 36) hits++
                total++
            }
        }
        return if (total == 0) 0f else hits.toFloat() / total.toFloat()
    }
}
