package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

object ArcPointAnalyzer {

    private data class Hit(
        val x: Int,
        val y: Int,
        val angle: Double
    )

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
        val hits = mutableListOf<Hit>()

        for (y in (centerY - radiusMax).toInt().coerceAtLeast(0) until (centerY + radiusMax).toInt().coerceAtMost(bitmap.height)) {
            for (x in (centerX - radiusMax).toInt().coerceAtLeast(0) until (centerX + radiusMax).toInt().coerceAtMost(bitmap.width)) {
                val dx = x - centerX
                val dy = centerY - y
                val radius = hypot(dx, dy)
                if (radius !in radiusMin..radiusMax) continue
                val angle = rawArcAngle(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))) ?: continue
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = (r + g + b) / 3f
                val chroma = maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))
                if (brightness < 235f || chroma > 28) continue
                if (localWhiteDensity(bitmap, x, y) >= 0.15f) {
                    hits += Hit(x, y, angle)
                }
            }
        }

        if (hits.isEmpty()) {
            var brightest: Hit? = null
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
                        val dx = x - centerX
                        val dy = centerY - y
                        val angle = fallbackArcAngle(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())))
                        brightest = Hit(x, y, angle)
                    }
                    if (brightness < 245f || chroma > 20) continue
                    if (localWhiteDensity(bitmap, x, y) >= 0.15f) {
                        hits += Hit(x, y, fallbackArcAngle(Math.toDegrees(atan2((centerY - y).toDouble(), (x - centerX).toDouble()))))
                    }
                }
            }
            brightest?.let { if (hits.isEmpty()) hits += it }
        }

        if (hits.isEmpty()) return null
        val cluster = densestAngularCluster(hits)
        val bestX = cluster.map { it.x }.average().toFloat()
        val bestY = cluster.map { it.y }.average().toFloat()
        val angle = cluster.map { it.angle }.average()
        val progress = ((START_ANGLE - angle) / (START_ANGLE - END_ANGLE)).coerceIn(0.0, 1.0)
        val level = 1.0 + progress * 49.0
        val angularSpread = (cluster.maxOfOrNull { it.angle } ?: angle) - (cluster.minOfOrNull { it.angle } ?: angle)
        val clusterRatio = if (hits.isEmpty()) 0f else cluster.size.toFloat() / hits.size.toFloat()
        val hitDensity = (cluster.size / 24f).coerceIn(0f, 1f)
        val spreadPenalty = when {
            angularSpread <= 3.0 -> 1.0f
            angularSpread <= 6.0 -> 0.8f
            angularSpread <= 10.0 -> 0.55f
            else -> 0.25f
        }
        val confidence = (hitDensity * 0.55f + clusterRatio * 0.45f) * spreadPenalty
        Log.d("ArcPointAnalyzer", "Arc dot detected at ($bestX,$bestY) angle=$angle level=$level confidence=$confidence")
        return Result(bestX, bestY, angle, level, confidence)
    }

    private fun densestAngularCluster(hits: List<Hit>): List<Hit> {
        if (hits.size <= 4) return hits
        val buckets = mutableMapOf<Int, MutableList<Hit>>()
        hits.forEach { hit ->
            val bucket = (hit.angle / 2.0).toInt()
            buckets.getOrPut(bucket) { mutableListOf() }.add(hit)
        }
        val bestBucket = buckets.maxByOrNull { it.value.size }?.key ?: return hits
        return hits.filter { hit ->
            val bucket = (hit.angle / 2.0).toInt()
            kotlin.math.abs(bucket - bestBucket) <= 1
        }.ifEmpty { hits }
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
