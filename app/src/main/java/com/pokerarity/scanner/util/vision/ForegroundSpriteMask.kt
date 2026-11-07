@file:Suppress("MagicNumber") // Lab conversion and geometric weights use standard numeric constants.

package com.pokerarity.scanner.util.vision

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

private const val CLUSTER_COUNT = 4

/** Small, deterministic foreground mask for the centered Pokemon model in a GO detail frame. */
internal object ForegroundSpriteMask {
    private const val MIN_CLUSTER_PIXELS = 4
    private const val MIN_FOREGROUND_RATIO = 0.06f
    private const val MAX_FOREGROUND_RATIO = 0.72f
    private const val MAX_CENTER_DISTANCE = 0.48f
    private const val COMPONENT_DISTANCE_MARGIN = 0.16f
    data class Result(
        val pixels: BooleanArray,
        val confidence: Float,
        val foregroundRatio: Float
    )

    private data class ClusterStats(
        val counts: IntArray,
        val centerWeights: FloatArray,
        val borderCounts: IntArray
    )

    private data class ForegroundClusters(val indexes: Set<Int>, val score: Float)

    fun segment(argb: IntArray, width: Int, height: Int): Result? =
        if (width >= 16 && height >= 16 && argb.size == width * height) {
            segmentValidated(argb, width, height)
        } else {
            null
        }

    private fun segmentValidated(argb: IntArray, width: Int, height: Int): Result? {
        val labels = LabColorClusterer.cluster(argb, width, height)
        val stats = clusterStats(labels, width, height)
        val selected = selectForegroundClusters(stats) ?: return null
        val candidate = candidateMask(labels, width, height, selected.indexes)
        return buildResult(candidate, width, height, selected.score)
    }

    private fun clusterStats(labels: IntArray, width: Int, height: Int): ClusterStats {
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
                if (dx * dx + dy * dy <= 1f) {
                    centerWeights[label] += exp(-2.2f * ((nx - 0.5f).pow(2) + (ny - 0.5f).pow(2)))
                }
                if (x < width / 10 || x >= width - width / 10 || y < height / 10 || y >= height - height / 10) {
                    borderCounts[label]++
                }
            }
        }
        return ClusterStats(counts, centerWeights, borderCounts)
    }

    private fun selectForegroundClusters(stats: ClusterStats): ForegroundClusters? {
        val scores = FloatArray(CLUSTER_COUNT) { cluster -> clusterScore(stats, cluster) }
        val active = scores.indices.filter { stats.counts[it] >= MIN_CLUSTER_PIXELS && scores[it] > 0f }
        if (active.size < 2) return null
        val bestScore = active.maxOf { scores[it] }
        val selected = active.filter { isForegroundCluster(it, bestScore, scores, stats) }.toSet()
        return if (bestScore >= 0.24f && selected.isNotEmpty() && selected.size < active.size) {
            ForegroundClusters(selected, bestScore)
        } else {
            null
        }
    }

    private fun clusterScore(stats: ClusterStats, cluster: Int): Float {
        val count = stats.counts[cluster]
        if (count < MIN_CLUSTER_PIXELS) return 0f
        val centrality = stats.centerWeights[cluster] / count
        val borderRatio = stats.borderCounts[cluster].toFloat() / count
        return centrality * (1f - borderRatio)
    }

    private fun isForegroundCluster(
        cluster: Int,
        bestScore: Float,
        scores: FloatArray,
        stats: ClusterStats
    ): Boolean {
        val count = stats.counts[cluster]
        val centrality = stats.centerWeights[cluster] / count
        val borderRatio = stats.borderCounts[cluster].toFloat() / count
        return scores[cluster] >= bestScore * 0.72f && centrality >= 0.34f && borderRatio <= 0.58f
    }

    private fun candidateMask(labels: IntArray, width: Int, height: Int, selected: Set<Int>): BooleanArray {
        val mask = BooleanArray(labels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val nx = (x + 0.5f) / width
                val ny = (y + 0.5f) / height
                val dx = (nx - 0.5f) / 0.52f
                val dy = (ny - 0.5f) / 0.58f
                val index = y * width + x
                mask[index] = labels[index] in selected && dx * dx + dy * dy <= 1f
            }
        }
        return mask
    }

    private fun buildResult(candidate: BooleanArray, width: Int, height: Int, bestScore: Float): Result? {
        val components = ConnectedComponentFinder.find(candidate, width, height)
            .filter { it.pixels.size >= 3 && it.centerDistance <= MAX_CENTER_DISTANCE }
        if (components.isEmpty()) return null

        val primary = components.maxBy { component ->
            component.pixels.size * (1f - component.centerDistance).coerceAtLeast(0.05f)
        }
        val mask = retainPrimaryComponents(candidate.size, components, primary)
        val ratio = mask.count { it }.toFloat() / candidate.size
        return if (ratio in MIN_FOREGROUND_RATIO..MAX_FOREGROUND_RATIO) {
            val confidence = (bestScore * (1f - kotlin.math.abs(ratio - 0.30f) * 0.8f)).coerceIn(0f, 1f)
            Result(mask, confidence, ratio)
        } else {
            null
        }
    }

    private fun retainPrimaryComponents(
        size: Int,
        components: List<ConnectedComponentFinder.Component>,
        primary: ConnectedComponentFinder.Component
    ): BooleanArray {
        val mask = BooleanArray(size)
        components.forEach { component ->
            val nearPrimary = component.centerDistance <= primary.centerDistance + COMPONENT_DISTANCE_MARGIN
            if (component === primary || nearPrimary) {
                component.pixels.forEach { mask[it] = true }
            }
        }
        return mask
    }
}

private object LabColorClusterer {
    private const val ITERATIONS = 7

    fun cluster(argb: IntArray, width: Int, height: Int): IntArray {
        val labs = Array(argb.size) { index -> toLab(argb[index]) }
        val centers = initialCenters(labs, width, height)
        val labels = IntArray(labs.size)
        repeat(ITERATIONS) {
            val sums = Array(CLUSTER_COUNT) { DoubleArray(3) }
            val counts = IntArray(CLUSTER_COUNT)
            labs.indices.forEach { index ->
                val label = nearest(labs[index], centers)
                labels[index] = label
                counts[label]++
                for (channel in 0..2) sums[label][channel] += labs[index][channel]
            }
            centers.indices.forEach { cluster ->
                if (counts[cluster] > 0) {
                    for (channel in 0..2) {
                        centers[cluster][channel] = (sums[cluster][channel] / counts[cluster]).toFloat()
                    }
                }
            }
        }
        return labels
    }

    private fun initialCenters(labs: Array<FloatArray>, width: Int, height: Int): Array<FloatArray> {
        val selected = mutableListOf((height / 2) * width + width / 2)
        while (selected.size < CLUSTER_COUNT) {
            val (bestIndex, bestDistance) = farthestColor(labs, selected)
            if (bestDistance <= 0f) break
            selected += bestIndex
        }
        while (selected.size < CLUSTER_COUNT) selected += selected.first()
        return Array(CLUSTER_COUNT) { cluster -> labs[selected[cluster]].copyOf() }
    }

    private fun farthestColor(labs: Array<FloatArray>, selected: List<Int>): Pair<Int, Float> {
        var bestIndex = 0
        var bestDistance = -1f
        labs.indices.forEach { index ->
            val distance = selected.minOf { centerIndex -> distanceSquared(labs[index], labs[centerIndex]) }
            if (distance > bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex to bestDistance
    }

    private fun nearest(color: FloatArray, centers: Array<FloatArray>): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        centers.indices.forEach { index ->
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
        return floatArrayOf(
            (116.0 * fy - 16.0).toFloat(),
            (500.0 * (fx - fy)).toFloat(),
            (200.0 * (fy - fz)).toFloat()
        )
    }
}

private object ConnectedComponentFinder {
    data class Component(val pixels: IntArray, val centerDistance: Float)

    private data class Grid(
        val mask: BooleanArray,
        val width: Int,
        val height: Int,
        val visited: BooleanArray,
        val queue: IntArray
    )

    private val neighborDx = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
    private val neighborDy = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)

    fun find(mask: BooleanArray, width: Int, height: Int): List<Component> {
        val grid = Grid(mask, width, height, BooleanArray(mask.size), IntArray(mask.size))
        val result = mutableListOf<Component>()
        mask.indices.forEach { start ->
            if (mask[start] && !grid.visited[start]) result += collect(grid, start)
        }
        return result
    }

    private fun collect(grid: Grid, start: Int): Component {
        var read = 0
        var write = 0
        grid.queue[write++] = start
        grid.visited[start] = true
        var sumX = 0f
        var sumY = 0f
        while (read < write) {
            val index = grid.queue[read++]
            sumX += ((index % grid.width) + 0.5f) / grid.width
            sumY += ((index / grid.width) + 0.5f) / grid.height
            for (neighbor in neighborDx.indices) write = enqueue(grid, write, index, neighbor)
        }
        val centerX = sumX / write
        val centerY = sumY / write
        val distance = sqrt((centerX - 0.5f).pow(2) + (centerY - 0.5f).pow(2))
        return Component(grid.queue.copyOf(write), distance)
    }

    private fun enqueue(grid: Grid, write: Int, index: Int, neighbor: Int): Int {
        val x = index % grid.width + neighborDx[neighbor]
        val y = index / grid.width + neighborDy[neighbor]
        var nextWrite = write
        if (x in 0 until grid.width && y in 0 until grid.height) {
            val next = y * grid.width + x
            if (grid.mask[next] && !grid.visited[next]) {
                grid.visited[next] = true
                grid.queue[write] = next
                nextWrite++
            }
        }
        return nextWrite
    }
}
