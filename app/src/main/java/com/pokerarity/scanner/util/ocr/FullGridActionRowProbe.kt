package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect

/** Pixel evidence permits only provisional OCR; it never names a species or publishes a scan. */
internal enum class ActionRowStatus { NOT_APPLICABLE, INVALID, ABSENT, AMBIGUOUS, PRESENT }

internal data class ActionBand(val top: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

internal data class ActionRowEvidence(
    val status: ActionRowStatus,
    val pair: Pair<ActionBand, ActionBand>? = null
)

/**
 * Faithful, bounded port of the independently measured Samsung S25 ACTIONS kernel.
 * Only the full-grid StorageList conflict can enter the pixel path; ordinary/partial
 * screens return before any bitmap reads or scratch allocations.
 */
internal object FullGridActionRowProbe {
    private const val MAX_PIXEL_BYTES = 8L * 1024L * 1024L
    private const val MIN_BITMAP_DIMENSION = 320
    private const val MIN_PORTRAIT_ASPECT = 1.5
    private const val MAX_PORTRAIT_ASPECT = 3.0
    private const val RGB_COMPONENT_MASK = 255
    private const val MIN_ACTION_GREEN = 160
    private const val MIN_GREEN_OVER_RED = 25
    private const val MIN_GREEN_OVER_BLUE = 4
    private const val MIN_NEUTRAL_COMPONENT_EXCLUSIVE = 185
    private const val MAX_NEUTRAL_CHROMA_EXCLUSIVE = 65

    private fun validAnchor(rect: Rect): Boolean =
        rect.left >= 0 && rect.top >= 0 && rect.right > rect.left && rect.bottom > rect.top

    // Independent fail-closed structural guards are intentionally explicit.
    @Suppress("ComplexCondition", "ReturnCount")
    fun isApplicable(routed: RoutedScreen): Boolean {
        val decision = routed.decision
        val result = routed.classification
        if (decision.action != ScreenRouteAction.REJECT_NON_DETAIL ||
            decision.screenType != ScreenType.StorageList ||
            result.screenType != ScreenType.StorageList ||
            !decision.safeFallback || !result.safeFallback
        ) return false

        val grid = result.anchors.singleOrNull { it.name == ScreenAnchorName.StorageGrid }
            ?: return false
        val name = result.anchors.singleOrNull { it.name == ScreenAnchorName.NameHpBand }
            ?: return false
        return grid.confidence == 1.0f && validAnchor(grid.rect) && validAnchor(name.rect)
    }

    @Suppress("ReturnCount", "ComplexCondition")
    fun evaluate(bitmap: Bitmap, routed: RoutedScreen): ActionRowEvidence {
        if (!isApplicable(routed)) return ActionRowEvidence(ActionRowStatus.NOT_APPLICABLE)
        if (bitmap.isRecycled || bitmap.config != Bitmap.Config.ARGB_8888 ||
            bitmap.width < MIN_BITMAP_DIMENSION || bitmap.height < MIN_BITMAP_DIMENSION
        ) return ActionRowEvidence(ActionRowStatus.INVALID)
        val width = bitmap.width
        val height = bitmap.height
        val ratio = height.toDouble() / width
        if (ratio < MIN_PORTRAIT_ASPECT || ratio > MAX_PORTRAIT_ASPECT) {
            return ActionRowEvidence(ActionRowStatus.INVALID)
        }
        val boxes = routed.classification.anchors.filter {
            it.name == ScreenAnchorName.StorageGrid || it.name == ScreenAnchorName.NameHpBand
        }
        if (boxes.any { it.rect.right > width || it.rect.bottom > height }) {
            return ActionRowEvidence(ActionRowStatus.INVALID)
        }
        return scanPixels(bitmap)
    }

    @Suppress(
        "CyclomaticComplexMethod", "ComplexCondition", "LongMethod",
        "ReturnCount", "MagicNumber", "LoopWithTooManyJumpStatements"
    )
    private fun scanPixels(bitmap: Bitmap): ActionRowEvidence {
        val width = bitmap.width
        val height = bitmap.height
        val x0 = (0.05 * width).toInt()
        val x1 = (0.93 * width).toInt()
        val y1 = (0.82 * height).toInt()
        val rowWidth = x1 - x0
        val rows = (y1 + 1) / 2
        val columns = ((0.49 * width - x0) / 2).toInt()
        val panelLeft = (0.52 * width).toInt()
        val panelRight = (0.92 * width).toInt()
        val count = rowWidth.toLong() * y1
        if (rowWidth <= 0 || y1 <= 0 || rows <= 0 || columns <= 0 ||
            x0 + 2L * (columns - 1) >= x1 ||
            panelLeft < x0 || panelRight > x1 || panelLeft >= panelRight ||
            count <= 0 || count > Int.MAX_VALUE ||
            count * Int.SIZE_BYTES > MAX_PIXEL_BYTES
        ) return ActionRowEvidence(ActionRowStatus.INVALID)

        val pixels = IntArray(count.toInt())
        try {
            bitmap.getPixels(pixels, 0, rowWidth, x0, 0, rowWidth, y1)
        } catch (_: IllegalArgumentException) {
            return ActionRowEvidence(ActionRowStatus.INVALID)
        } catch (_: IllegalStateException) {
            return ActionRowEvidence(ActionRowStatus.INVALID)
        }
        val flags = BooleanArray(rows)
        for (index in 0 until rows) {
            var green = 0
            for (column in 0 until columns) {
                if (isGreen(pixels[index * 2 * rowWidth + column * 2])) green++
            }
            flags[index] = green.toDouble() / columns >= 0.55
        }

        val rawBands = mutableListOf<ActionBand>()
        val gap = (0.003 * height / 2).toInt()
        var start = -1
        var last = -1
        for (index in flags.indices) {
            if (!flags[index]) continue
            if (start < 0) {
                start = index
            } else if (index - last > gap + 1) {
                rawBands.add(ActionBand(start * 2, (last + 1) * 2))
                start = index
            }
            last = index
        }
        if (start >= 0) rawBands.add(ActionBand(start * 2, (last + 1) * 2))
        val bands = rawBands.filter {
            it.height >= height * 0.025 && it.height <= height * 0.085
        }
        fun panelRatio(band: ActionBand): Double {
            var good = 0
            var total = 0
            for (y in band.top until band.bottom step 4) {
                for (x in panelLeft until panelRight step 4) {
                    if (isNeutral(pixels[y * rowWidth + x - x0])) good++
                    total++
                }
            }
            return good.toDouble() / maxOf(1, total)
        }

        var selected: Pair<ActionBand, ActionBand>? = null
        for (i in bands.indices) {
            for (j in i + 1 until bands.size) {
                val upper = bands[i]
                val lower = bands[j]
                val separation = lower.top - upper.bottom
                val heightRatio = upper.height.toDouble() / lower.height
                if (separation < height * 0.002 || separation > height * 0.060 ||
                    heightRatio < 0.75 || heightRatio > 1.333
                ) continue
                if (panelRatio(upper) < 0.65 || panelRatio(lower) < 0.65) continue
                if (selected != null) return ActionRowEvidence(ActionRowStatus.AMBIGUOUS)
                selected = upper to lower
            }
        }
        return if (selected == null) ActionRowEvidence(ActionRowStatus.ABSENT)
        else ActionRowEvidence(ActionRowStatus.PRESENT, selected)
    }

    private fun isGreen(pixel: Int): Boolean {
        val red = pixel ushr 16 and RGB_COMPONENT_MASK
        val green = pixel ushr 8 and RGB_COMPONENT_MASK
        val blue = pixel and RGB_COMPONENT_MASK
        return green >= MIN_ACTION_GREEN &&
            green - red >= MIN_GREEN_OVER_RED && green - blue >= MIN_GREEN_OVER_BLUE
    }

    private fun isNeutral(pixel: Int): Boolean {
        val red = pixel ushr 16 and RGB_COMPONENT_MASK
        val green = pixel ushr 8 and RGB_COMPONENT_MASK
        val blue = pixel and RGB_COMPONENT_MASK
        return minOf(red, green, blue) > MIN_NEUTRAL_COMPONENT_EXCLUSIVE &&
            maxOf(red, green, blue) - minOf(red, green, blue) < MAX_NEUTRAL_CHROMA_EXCLUSIVE
    }
}
