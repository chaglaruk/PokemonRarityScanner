package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.pow

enum class ScreenType {
    PokemonDetail,
    PokemonDetailScrolled,
    Appraisal,
    StorageList,
    Encounter,
    Transition,
    Unknown
}

enum class ScreenAnchorName {
    CpHeader,
    NameHpBand,
    DetailCard,
    AppraisalPanel,
    StorageGrid,
    EncounterRing,
    DateBadge
}

data class ScreenAnchor(
    val name: ScreenAnchorName,
    val rect: Rect,
    val confidence: Float,
    val reason: String
)

data class ScreenClassificationResult(
    val screenType: ScreenType,
    val confidence: Float,
    val reasons: List<String>,
    val anchors: List<ScreenAnchor>,
    val safeFallback: Boolean
)

class ScreenClassifier {

    fun classify(bitmap: Bitmap): ScreenClassificationResult {
        if (bitmap.isRecycled || bitmap.width < MIN_DIMENSION || bitmap.height < MIN_DIMENSION) {
            return unknown(0f, "invalid_bitmap")
        }

        val width = bitmap.width
        val height = bitmap.height
        val fullStats = stats(bitmap, Rect(0, 0, width, height), 10)
        if (fullStats.variance < 10f) {
            return unknown(0.12f, "low_content")
        }

        val anchors = mutableListOf<ScreenAnchor>()
        val reasons = mutableListOf<String>()

        val cpRect = relativeRect(width, height, 0.10f, 0.045f, 0.80f, 0.065f)
        val cpStats = stats(bitmap, cpRect, 4)
        val cpEvidence = cpStats.brightRatio > 0.015f && cpStats.variance > 350f
        if (cpEvidence) {
            anchors += ScreenAnchor(ScreenAnchorName.CpHeader, cpRect, 0.78f, "bright_header_text")
            reasons += "cp_header_evidence"
        }

        val nameHpRect = relativeRect(width, height, 0.10f, 0.35f, 0.80f, 0.18f)
        val nameHpStats = stats(bitmap, nameHpRect, 5)
        val nameHpEvidence = nameHpStats.brightRatio > 0.04f || nameHpStats.darkRatio > 0.015f
        if (nameHpEvidence) {
            anchors += ScreenAnchor(ScreenAnchorName.NameHpBand, nameHpRect, 0.70f, "name_hp_area_contrast")
            reasons += "name_hp_evidence"
        }

        val detailCard = findBrightPanel(bitmap, 0.30f, 0.94f)
        val detailPanelEvidence = detailCard != null
        if (detailCard != null) {
            anchors += ScreenAnchor(ScreenAnchorName.DetailCard, detailCard, 0.74f, "large_neutral_bright_panel")
            reasons += "detail_card_evidence"
        }

        val appraisalRect = findAppraisalPanel(bitmap)
        val appraisalEvidence = appraisalRect != null
        if (appraisalRect != null) {
            anchors += ScreenAnchor(ScreenAnchorName.AppraisalPanel, appraisalRect, 0.72f, "lower_panel_with_bar_rows")
            reasons += "appraisal_panel_evidence"
        }

        val storageEvidence = storageGridEvidence(bitmap)
        if (storageEvidence > 0.72f) {
            val rect = relativeRect(width, height, 0.05f, 0.20f, 0.90f, 0.72f)
            anchors += ScreenAnchor(ScreenAnchorName.StorageGrid, rect, storageEvidence, "repetitive_grid_cells")
            reasons += "storage_grid_evidence"
        }

        val encounterEvidence = encounterRingEvidence(bitmap)
        if (encounterEvidence > 0.70f) {
            val rect = relativeRect(width, height, 0.20f, 0.45f, 0.60f, 0.30f)
            anchors += ScreenAnchor(ScreenAnchorName.EncounterRing, rect, encounterEvidence, "center_catch_ring_color")
            reasons += "encounter_ring_evidence"
        }

        val detailScore = score(
            0.12f to isPortrait(width, height),
            0.28f to cpEvidence,
            0.22f to nameHpEvidence,
            0.30f to detailPanelEvidence,
            0.08f to (fullStats.saturatedRatio > 0.12f)
        )
        val appraisalScore = if (appraisalEvidence) (detailScore * 0.45f + 0.40f).coerceAtMost(0.95f) else 0f
        val transitionScore = if (fullStats.variance < 70f) 0.55f else 0f

        val selected = when {
            appraisalScore >= 0.74f -> ScreenType.Appraisal to appraisalScore
            detailScore >= 0.70f -> {
                val cardTop = detailCard?.top ?: (height * 0.42f).toInt()
                val type = if (cardTop < height * 0.34f) ScreenType.PokemonDetailScrolled else ScreenType.PokemonDetail
                type to detailScore.coerceAtMost(0.94f)
            }
            storageEvidence >= 0.72f -> ScreenType.StorageList to storageEvidence
            encounterEvidence >= 0.70f -> ScreenType.Encounter to encounterEvidence
            transitionScore >= 0.50f -> ScreenType.Transition to transitionScore
            else -> ScreenType.Unknown to maxOf(detailScore, storageEvidence, encounterEvidence, 0.18f).coerceAtMost(0.49f)
        }

        if (selected.first == ScreenType.Unknown && reasons.isEmpty()) {
            reasons += "insufficient_screen_evidence"
        }

        val safeFallback = selected.second < 0.70f ||
            selected.first == ScreenType.Unknown ||
            selected.first == ScreenType.Transition ||
            selected.first == ScreenType.StorageList

        return ScreenClassificationResult(
            screenType = selected.first,
            confidence = selected.second,
            reasons = reasons.distinct(),
            anchors = anchors,
            safeFallback = safeFallback
        )
    }

    private fun unknown(confidence: Float, reason: String): ScreenClassificationResult =
        ScreenClassificationResult(
            screenType = ScreenType.Unknown,
            confidence = confidence,
            reasons = listOf(reason),
            anchors = emptyList(),
            safeFallback = true
        )

    private fun findBrightPanel(bitmap: Bitmap, topFraction: Float, bottomFraction: Float): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        val left = (width * 0.04f).toInt()
        val right = (width * 0.96f).toInt()
        val startY = (height * topFraction).toInt()
        val endY = (height * bottomFraction).toInt()
        var bandTop = -1
        var lastBright = -1

        for (y in startY until endY step 6) {
            val row = stats(bitmap, Rect(left, y, right, (y + 1).coerceAtMost(height)), 4)
            val brightPanelRow = row.neutralBrightRatio > 0.58f && row.darkRatio < 0.20f
            if (brightPanelRow) {
                if (bandTop < 0) bandTop = y
                lastBright = y
            } else if (bandTop >= 0 && y - lastBright > 24) {
                break
            }
        }

        if (bandTop < 0 || lastBright - bandTop < height * 0.12f) return null
        return Rect(0, bandTop, width, (lastBright + 12).coerceAtMost(height))
    }

    private fun findAppraisalPanel(bitmap: Bitmap): Rect? {
        val anchor = ScreenRegions.detectAppraisalBox(bitmap) ?: return null
        val barEvidence = darkHorizontalRowCount(bitmap, anchor)
        return if (barEvidence >= 3) Rect(0, anchor.top, bitmap.width, anchor.bottom) else null
    }

    private fun darkHorizontalRowCount(bitmap: Bitmap, rect: ScreenRegions.Anchor): Int {
        val width = bitmap.width
        val left = (width * 0.20f).toInt()
        val right = (width * 0.92f).toInt()
        var rows = 0
        var lastCounted = -100
        for (y in rect.top until rect.bottom step 4) {
            val row = stats(bitmap, Rect(left, y, right, (y + 1).coerceAtMost(bitmap.height)), 3)
            if (row.darkRatio > 0.12f && y - lastCounted > 18) {
                rows++
                lastCounted = y
            }
        }
        return rows
    }

    private fun storageGridEvidence(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val rows = 4
        val columns = 3
        var brightCells = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val left = ((0.08f + column * 0.29f) * width).toInt()
                val top = ((0.24f + row * 0.15f) * height).toInt()
                val rect = Rect(left, top, (left + width * 0.22f).toInt(), (top + height * 0.10f).toInt())
                val cell = stats(bitmap, rect, 8)
                if (cell.neutralBrightRatio > 0.35f && cell.variance > 80f) brightCells++
            }
        }
        return (brightCells / (rows * columns).toFloat()).coerceIn(0f, 1f)
    }

    private fun encounterRingEvidence(bitmap: Bitmap): Float {
        val rect = relativeRect(bitmap.width, bitmap.height, 0.20f, 0.48f, 0.60f, 0.22f)
        val ring = stats(bitmap, rect, 6)
        return if (ring.saturatedRatio > 0.42f && ring.neutralBrightRatio < 0.30f) {
            (ring.saturatedRatio * 1.4f).coerceAtMost(1f)
        } else {
            0f
        }
    }

    private fun score(vararg parts: Pair<Float, Boolean>): Float =
        parts.sumOf { (weight, present) -> if (present) weight.toDouble() else 0.0 }.toFloat()

    private fun isPortrait(width: Int, height: Int): Boolean = height > width && height / width.toFloat() >= 1.65f

    private fun stats(bitmap: Bitmap, rect: Rect, step: Int): RegionStats {
        val safe = Rect(
            rect.left.coerceIn(0, bitmap.width - 1),
            rect.top.coerceIn(0, bitmap.height - 1),
            rect.right.coerceIn(1, bitmap.width),
            rect.bottom.coerceIn(1, bitmap.height)
        )
        if (safe.right <= safe.left || safe.bottom <= safe.top) return RegionStats.EMPTY

        var samples = 0
        var bright = 0
        var neutralBright = 0
        var dark = 0
        var saturated = 0
        var sum = 0f
        var sumSquares = 0f
        val stride = step.coerceAtLeast(1)
        var y = safe.top
        while (y < safe.bottom) {
            var x = safe.left
            while (x < safe.right) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val chroma = max - min
                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                if (luma >= 215f) bright++
                if (luma >= 180f && chroma <= 42) neutralBright++
                if (luma <= 75f) dark++
                if (chroma >= 55 && luma >= 55f) saturated++
                sum += luma
                sumSquares += luma.pow(2)
                samples++
                x += stride
            }
            y += stride
        }
        if (samples == 0) return RegionStats.EMPTY
        val mean = sum / samples
        val variance = (sumSquares / samples) - mean.pow(2)
        return RegionStats(
            brightRatio = bright / samples.toFloat(),
            neutralBrightRatio = neutralBright / samples.toFloat(),
            darkRatio = dark / samples.toFloat(),
            saturatedRatio = saturated / samples.toFloat(),
            variance = variance.coerceAtLeast(0f)
        )
    }

    private fun relativeRect(
        width: Int,
        height: Int,
        left: Float,
        top: Float,
        rectWidth: Float,
        rectHeight: Float
    ): Rect = Rect(
        (width * left).toInt(),
        (height * top).toInt(),
        (width * (left + rectWidth)).toInt(),
        (height * (top + rectHeight)).toInt()
    )

    private data class RegionStats(
        val brightRatio: Float,
        val neutralBrightRatio: Float,
        val darkRatio: Float,
        val saturatedRatio: Float,
        val variance: Float
    ) {
        companion object {
            val EMPTY = RegionStats(0f, 0f, 0f, 0f, 0f)
        }
    }

    companion object {
        private const val MIN_DIMENSION = 64
    }
}
