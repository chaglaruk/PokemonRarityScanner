package com.pokerarity.scanner.util.vision

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** Small, deterministic foreground mask for the centered Pokemon model in a GO detail frame. */
internal object ForegroundSpriteMask {
    private const val CLUSTER_COUNT = 4
    private const val ITERATIONS = 7
    private const val MIN_CLUSTER_PIXELS = 4
    private const val MIN_FOREGROUND_RATIO = 0.06f
    private const val MAX_FOREGROUND_RATIO = 0.72f

    data class Result(
        val pixels: BooleanArray,
        val confidence: Float,
        val foregroundRatio: Float
    )

    private data class Component(val pixels: IntArray, val centerDistance: Float)

    fun segment(argb: IntArray, width: Int, height: Int): Result? {
        if (width < 16 || height < 16 || argb.size != width * height) return null
        val labs = Array(argb.size) { index -> toLab(argb[index]) }
        val centers = initialCenters(labs, width, height)
        val labels = IntArray(argb.size)
        repeat(ITERATIONS) {
            val sums = Array(CLUSTER_COUNT) { DoubleArray(3) }
            val counts = IntArray(CLUSTER_COUNT)
            for (i in labs.indices) {
                val label = nearest(labs[i], centers)
                labels[i] = label
                counts[label]++
                for (channel in 0..2) sums[label][channel] += labs[i][channel]
            }
            for (cluster in 0 until CLUSTER_COUNT) {
                if (counts[cluster] > 0) {
                    for (channel in 0..2) centers[cluster][channel] = (sums[cluster][channel] / counts[cluster]).toFloat()
                }
            }
        }

        val counts = IntArray(CLUSTER_COUNT)
        val centerWeights = FloatArray(CLUSTER_COUNT)
        val borderCounts = IntArray(CLUSTER_COUNT)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val label = labels[y * width + x]
                counts[label]++
                val nx = (x + 0.5f) / width
                val ny = (y + 0.5f) / height
                val dx = (nx - 0.5f) / 0.30f
                val dy = (ny - 0.5f) / 0.36f
                val insideCenter = dx * dx + dy * dy <= 1f
                val radial = exp(-2.2f * ((nx - 0.5f).pow(2) + (ny - 0.5f).pow(2)))
                if (insideCenter) centerWeights[label] += radial
                if (x < width / 10 || x >= width - width / 10 || y < height / 10 || y >= height - height / 10) {
                    borderCounts[label]++
                }
            }
        }

        val scores = FloatArray(CLUSTER_COUNT) { cluster ->
            if (counts[cluster] < MIN_CLUSTER_PIXELS) return@FloatArray 0f
            val centrality = centerWeights[cluster] / counts[cluster]
            val borderRatio = borderCounts[cluster].toFloat() / counts[cluster]
            centrality * (1f - borderRatio)
        }
        val active = scores.indices.filter { counts[it] >= MIN_CLUSTER_PIXELS && scores[it] > 0f }
        if (active.size < 2) return null
        val bestScore = active.maxOf { scores[it] }
        if (bestScore < 0.24f) return null
        val selected = active.filter { cluster ->
            scores[cluster] >= bestScore * 0.72f &&
                centerWeights[cluster] / counts[cluster] >= 0.34f &&
                borderCounts[cluster].toFloat() / counts[cluster] <= 0.58f
        }.toSet()
        if (selected.isEmpty() || selected.size == active.size) return null

        val candidate = BooleanArray(argb.size)
        var candidateCount = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val nx = (x + 0.5f) / width
                val ny = (y + 0.5f) / height
                val dx = (nx - 0.5f) / 0.52f
                val dy = (ny - 0.5f) / 0.58f
                val index = y * width + x
                if (labels[index] in selected && dx * dx + dy * dy <= 1f) {
                    candidate[index] = true
                    candidateCount++
                }
            }
        }
        if (candidateCount == 0) return null

        val components = connectedComponents(candidate, width, height)
            .filter { it.pixels.size >= 3 && it.centerDistance <= 0.48f }
        if (components.isEmpty()) return null
        val primary = components.maxBy { component ->
            component.pixels.size * (1f - component.centerDistance).coerceAtLeast(0.05f)
        }
        val mask = BooleanArray(argb.size)
        var retained = 0
        for (component in components) {
            if (component === primary || component.centerDistance <= primary.centerDistance + 0.16f) {
                for (index in component.pixels) {
                    mask[index] = true
                    retained++
                }
            }
        }
        val ratio = retained.toFloat() / argb.size
        if (ratio !in MIN_FOREGROUND_RATIO..MAX_FOREGROUND_RATIO) return null
        val confidence = (bestScore * (1f - (ratio - 0.30f).let { kotlin.math.abs(it) } * 0.8f))
            .coerceIn(0f, 1f)
        return Result(mask, confidence, ratio)
    }

    private fun connectedComponents(mask: BooleanArray, width: Int, height: Int): List<Component> {
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        val result = mutableListOf<Component>()
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var read = 0
            var write = 0
            queue[write++] = start
            visited[start] = true
            var sumX = 0f
            var sumY = 0f
            while (read < write) {
                val index = queue[read++]
                val x = index % width
                val y = index / width
                sumX += (x + 0.5f) / width
                sumY += (y + 0.5f) / height
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nextX = x + dx
                    val nextY = y + dy
                    if (nextX !in 0 until width || nextY !in 0 until height) continue
                    val next = nextY * width + nextX
                    if (mask[next] && !visited[next]) {
                        visited[next] = true
                        queue[write++] = next
                    }
                }
            }
            if (write > 0) {
                val centerX = sumX / write
                val centerY = sumY / write
                val distance = sqrt((centerX - 0.5f).pow(2) + (centerY - 0.5f).pow(2))
                result += Component(queue.copyOf(write), distance)
            }
        }
        return result
    }

    private fun initialCenters(labs: Array<FloatArray>, width: Int, height: Int): Array<FloatArray> {
        val selected = mutableListOf<Int>()
        selected += (height / 2) * width + width / 2
        while (selected.size < CLUSTER_COUNT) {
            var bestIndex = 0
            var bestDistance = -1f
            for (index in labs.indices) {
                val distance = selected.minOf { centerIndex -> distanceSquared(labs[index], labs[centerIndex]) }
                if (distance > bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
            if (bestDistance <= 0f) break
            selected += bestIndex
        }
        while (selected.size < CLUSTER_COUNT) selected += selected.first()
        return Array(CLUSTER_COUNT) { cluster -> labs[selected[cluster]].copyOf() }
    }

    private fun nearest(color: FloatArray, centers: Array<FloatArray>): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (index in centers.indices) {
            val distance = distanceSquared(color, centers[index])
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    private fun distanceSquared(a: FloatArray, b: FloatArray): Float =
        (a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2)

    private fun toLab(argb: Int): FloatArray {
        fun linear(channel: Int): Double {
            val srgb = channel / 255.0
            return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(argb ushr 16 and 0xFF)
        val g = linear(argb ushr 8 and 0xFF)
        val b = linear(argb and 0xFF)
        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883
        fun f(value: Double) = if (value > 0.008856) value.pow(1.0 / 3.0) else 7.787 * value + 16.0 / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return floatArrayOf((116.0 * fy - 16.0).toFloat(), (500.0 * (fx - fy)).toFloat(), (200.0 * (fy - fz)).toFloat())
    }
}
