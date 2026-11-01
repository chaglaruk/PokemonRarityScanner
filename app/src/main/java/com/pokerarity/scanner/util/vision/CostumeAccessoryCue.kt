package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import java.util.ArrayDeque

/** Candidate-scoped cue for the visible red flower cluster on the Spring 2024 Cottonee crown. */
internal object CostumeAccessoryCue {
    private const val SPECIES = "Cottonee"
    private const val NORMAL_CROWN_KEY = "546_00_SPRING_2024"
    private const val SHINY_CROWN_KEY = "546_00_SPRING_2024_shiny"
    private const val MIN_RED_CLUSTER_SIZE = 8
    private const val MIN_RED_RATIO = 0.025f

    internal data class Result(
        val accepted: Boolean,
        val redPixelCount: Int,
        val largestRedClusterSize: Int,
        val sampledPixelCount: Int,
        val redPixelRatio: Float,
        val reasonCode: String
    )

    fun evaluate(
        lockedSpecies: String?,
        detectorInputSpecies: String?,
        details: CostumeSignatureStore.MatchDetails?,
        spriteCrop: Bitmap
    ): Result {
        if (!lockedSpecies.equals(SPECIES, ignoreCase = true) ||
            !detectorInputSpecies.equals(SPECIES, ignoreCase = true) ||
            !lockedSpecies.equals(detectorInputSpecies, ignoreCase = true)
        ) {
            return rejected("locked_species_or_detector_input_mismatch")
        }
        if (details == null || details.candidateCount < 2 || details.costumeCandidateCount < 1) {
            return rejected("costume_candidate_unavailable")
        }
        if (!details.bestCostumeKey.equals(NORMAL_CROWN_KEY, ignoreCase = true) &&
            !details.bestCostumeKey.equals(SHINY_CROWN_KEY, ignoreCase = true)
        ) {
            return rejected("spring_2024_crown_not_best_costume_candidate")
        }

        val region = Rect(
            (spriteCrop.width * 0.28f).toInt().coerceIn(0, spriteCrop.width),
            (spriteCrop.height * 0.24f).toInt().coerceIn(0, spriteCrop.height),
            (spriteCrop.width * 0.60f).toInt().coerceIn(0, spriteCrop.width),
            (spriteCrop.height * 0.37f).toInt().coerceIn(0, spriteCrop.height)
        )
        if (region.width() <= 0 || region.height() <= 0) return rejected("sprite_crop_too_small")

        val hsv = FloatArray(3)
        var sampledPixels = 0
        val redSamples = HashSet<Int>()
        val sampleColumns = (region.width() + 2) / 3
        var row = 0
        var y = region.top
        while (y < region.bottom) {
            var x = region.left
            var column = 0
            while (x < region.right) {
                val pixel = spriteCrop.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                if (Color.alpha(pixel) >= 128 && hsv[2] >= 0.08f) {
                    sampledPixels++
                    val hue = hsv[0].toInt()
                    if ((hue >= 345 || hue <= 18) && hsv[1] >= 0.45f && hsv[2] >= 0.30f) {
                        redSamples.add(row * sampleColumns + column)
                    }
                }
                x += 3
                column++
            }
            y += 3
            row++
        }

        val largestRedCluster = largestConnectedCluster(redSamples, sampleColumns, row)
        val redPixels = redSamples.size
        val redRatio = if (sampledPixels == 0) 0f else redPixels.toFloat() / sampledPixels
        val accepted = largestRedCluster >= MIN_RED_CLUSTER_SIZE && redRatio >= MIN_RED_RATIO
        return Result(
            accepted = accepted,
            redPixelCount = redPixels,
            largestRedClusterSize = largestRedCluster,
            sampledPixelCount = sampledPixels,
            redPixelRatio = redRatio,
            reasonCode = if (accepted) {
                "cottonee_spring_2024_red_flower_cluster_supported"
            } else {
                "cottonee_spring_2024_red_flower_cluster_below_support"
            }
        )
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
            val pending = ArrayDeque<Int>()
            val first = remaining.first()
            remaining.remove(first)
            pending.addLast(first)
            var size = 0
            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                val row = current / columns
                val column = current % columns
                size++
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nextColumn = column + dx
                        val nextRow = row + dy
                        if (nextColumn !in 0 until columns || nextRow !in 0 until rows) continue
                        val next = nextRow * columns + nextColumn
                        if (remaining.remove(next)) pending.addLast(next)
                    }
                }
            }
            largest = maxOf(largest, size)
        }
        return largest
    }
}
