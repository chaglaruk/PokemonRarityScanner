package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import java.util.ArrayDeque

private const val SPECIES = "Cottonee"
private const val NORMAL_CROWN_KEY = "546_00_SPRING_2024"
private const val SHINY_CROWN_KEY = "546_00_SPRING_2024_shiny"
private const val MIN_RED_CLUSTER_SIZE = 8
private const val MIN_RED_RATIO = 0.025f
private const val SAMPLE_STEP_PIXELS = 3
private const val HSV_COMPONENT_COUNT = 3
private const val MIN_ALPHA = 128
private const val MIN_VISIBLE_VALUE = 0.08f
private const val FIRST_RED_HUE_DEGREES = 345
private const val LAST_RED_HUE_DEGREES = 18
private const val MIN_RED_SATURATION = 0.45f
private const val MIN_RED_VALUE = 0.30f
private const val HEAD_LEFT_RATIO = 0.28f
private const val HEAD_TOP_RATIO = 0.24f
private const val HEAD_RIGHT_RATIO = 0.60f
private const val HEAD_BOTTOM_RATIO = 0.37f

private fun isMatchingCottoneeSpecies(lockedSpecies: String?, detectorInputSpecies: String?): Boolean {
    val lockedIsCottonee = lockedSpecies.equals(SPECIES, ignoreCase = true)
    val detectorIsCottonee = detectorInputSpecies.equals(SPECIES, ignoreCase = true)
    val speciesAgree = lockedSpecies.equals(detectorInputSpecies, ignoreCase = true)
    return lockedIsCottonee && detectorIsCottonee && speciesAgree
}

private fun isSpringCrownKey(key: String?): Boolean =
    key.equals(NORMAL_CROWN_KEY, ignoreCase = true) ||
        key.equals(SHINY_CROWN_KEY, ignoreCase = true)

private fun sampleCount(regionLength: Int): Int =
    (regionLength + SAMPLE_STEP_PIXELS - 1) / SAMPLE_STEP_PIXELS

private fun isVisiblePixel(pixel: Int, hsv: FloatArray): Boolean =
    Color.alpha(pixel) >= MIN_ALPHA && hsv[2] >= MIN_VISIBLE_VALUE

private fun isRedFlowerSample(hsv: FloatArray): Boolean {
    val hue = hsv[0].toInt()
    val redHue = hue >= FIRST_RED_HUE_DEGREES || hue <= LAST_RED_HUE_DEGREES
    return redHue && hsv[1] >= MIN_RED_SATURATION && hsv[2] >= MIN_RED_VALUE
}

/** Candidate-scoped cue for the visible red flower cluster on the Spring 2024 Cottonee crown. */
internal object CostumeAccessoryCue {
    internal data class Result(
        val accepted: Boolean,
        val redPixelCount: Int,
        val largestRedClusterSize: Int,
        val sampledPixelCount: Int,
        val redPixelRatio: Float,
        val reasonCode: String
    )

    private data class RedSamples(
        val positions: Set<Int>,
        val columns: Int,
        val rows: Int,
        val visiblePixelCount: Int
    )

    private data class ClusterSearch(
        val remaining: MutableSet<Int>,
        val pending: ArrayDeque<Int>,
        val columns: Int,
        val rows: Int
    )

    fun evaluate(
        lockedSpecies: String?,
        detectorInputSpecies: String?,
        details: CostumeSignatureStore.MatchDetails?,
        spriteCrop: Bitmap
    ): Result {
        val rejection = candidateRejectionReason(lockedSpecies, detectorInputSpecies, details)
        val region = if (rejection == null) sampleRegion(spriteCrop) else null
        val result = when {
            rejection != null -> rejected(rejection)
            region == null -> rejected("sprite_crop_too_small")
            else -> evaluateRegion(spriteCrop, region)
        }
        return result
    }

    private fun candidateRejectionReason(
        lockedSpecies: String?,
        detectorInputSpecies: String?,
        details: CostumeSignatureStore.MatchDetails?
    ): String? = when {
        !isMatchingCottoneeSpecies(lockedSpecies, detectorInputSpecies) ->
            "locked_species_or_detector_input_mismatch"
        details == null || details.candidateCount < 2 || details.costumeCandidateCount < 1 ->
            "costume_candidate_unavailable"
        !isSpringCrownKey(details.bestCostumeKey) ->
            "spring_2024_crown_not_best_costume_candidate"
        else -> null
    }

    private fun sampleRegion(spriteCrop: Bitmap): Rect? {
        val region = Rect(
            (spriteCrop.width * HEAD_LEFT_RATIO).toInt().coerceIn(0, spriteCrop.width),
            (spriteCrop.height * HEAD_TOP_RATIO).toInt().coerceIn(0, spriteCrop.height),
            (spriteCrop.width * HEAD_RIGHT_RATIO).toInt().coerceIn(0, spriteCrop.width),
            (spriteCrop.height * HEAD_BOTTOM_RATIO).toInt().coerceIn(0, spriteCrop.height)
        )
        return region.takeIf { it.width() > 0 && it.height() > 0 }
    }

    private fun evaluateRegion(spriteCrop: Bitmap, region: Rect): Result {
        val samples = sampleRedPixels(spriteCrop, region)
        val largestCluster = largestConnectedCluster(samples.positions, samples.columns, samples.rows)
        val redRatio = if (samples.visiblePixelCount == 0) {
            0f
        } else {
            samples.positions.size.toFloat() / samples.visiblePixelCount
        }
        val accepted = largestCluster >= MIN_RED_CLUSTER_SIZE && redRatio >= MIN_RED_RATIO
        return Result(
            accepted = accepted,
            redPixelCount = samples.positions.size,
            largestRedClusterSize = largestCluster,
            sampledPixelCount = samples.visiblePixelCount,
            redPixelRatio = redRatio,
            reasonCode = if (accepted) {
                "cottonee_spring_2024_red_flower_cluster_supported"
            } else {
                "cottonee_spring_2024_red_flower_cluster_below_support"
            }
        )
    }

    private fun sampleRedPixels(spriteCrop: Bitmap, region: Rect): RedSamples {
        val columns = sampleCount(region.width())
        val rows = sampleCount(region.height())
        val hsv = FloatArray(HSV_COMPONENT_COUNT)
        val redPositions = HashSet<Int>()
        var visiblePixels = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val x = region.left + column * SAMPLE_STEP_PIXELS
                val y = region.top + row * SAMPLE_STEP_PIXELS
                val pixel = spriteCrop.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                if (!isVisiblePixel(pixel, hsv)) continue
                visiblePixels++
                if (isRedFlowerSample(hsv)) redPositions.add(row * columns + column)
            }
        }
        return RedSamples(redPositions, columns, rows, visiblePixels)
    }

    private fun rejected(reasonCode: String) = Result(
        accepted = false,
        redPixelCount = 0,
        largestRedClusterSize = 0,
        sampledPixelCount = 0,
        redPixelRatio = 0f,
        reasonCode = reasonCode
    )

    private fun largestConnectedCluster(samples: Set<Int>, columns: Int, rows: Int): Int {
        if (samples.isEmpty() || columns <= 0 || rows <= 0) return 0
        val remaining = samples.toMutableSet()
        var largest = 0
        while (remaining.isNotEmpty()) {
            val clusterSize = connectedClusterSize(remaining.first(), remaining, columns, rows)
            largest = maxOf(largest, clusterSize)
        }
        return largest
    }

    private fun connectedClusterSize(
        first: Int,
        remaining: MutableSet<Int>,
        columns: Int,
        rows: Int
    ): Int {
        val pending = ArrayDeque<Int>()
        val search = ClusterSearch(remaining, pending, columns, rows)
        remaining.remove(first)
        pending.addLast(first)
        var size = 0
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            size++
            addNeighborSamples(current, search)
        }
        return size
    }

    private fun addNeighborSamples(
        current: Int,
        search: ClusterSearch
    ) {
        val currentRow = current / search.columns
        val currentColumn = current % search.columns
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                enqueueNeighborSample(currentColumn + dx, currentRow + dy, search)
            }
        }
    }

    private fun enqueueNeighborSample(column: Int, row: Int, search: ClusterSearch) {
        if (column in 0 until search.columns && row in 0 until search.rows) {
            val neighbor = row * search.columns + column
            if (search.remaining.remove(neighbor)) search.pending.addLast(neighbor)
        }
    }
}
