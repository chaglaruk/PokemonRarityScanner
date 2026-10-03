package com.pokerarity.scanner

import android.graphics.Bitmap
import com.pokerarity.scanner.util.ocr.ImagePreprocessor
import com.pokerarity.scanner.util.ocr.ScreenRegions

/**
 * Test-only CP crop-quality estimation shared by the exact-frame replay harnesses.
 *
 * This is the same benchmark logic the Phase 1 exact-frame replay used (its private
 * `estimateCpQualityReplica`), extracted so both harnesses derive the per-frame CP crop
 * quality from the actual staged bitmap instead of duplicating the algorithm. It mirrors
 * the production [ScanManager] estimate for diagnostic comparability only; production
 * code is unchanged.
 */
internal fun estimateReplayCpQuality(bitmap: Bitmap): Double {
    val mask = ImagePreprocessor.processWhiteMask(bitmap)
    val rect = ScreenRegions.getRectForRegion(mask, ScreenRegions.REGION_CP)
    val safeLeft = rect.left.coerceIn(0, mask.width - 1)
    val safeTop = rect.top.coerceIn(0, mask.height - 1)
    val safeWidth = rect.width().coerceAtMost(mask.width - safeLeft)
    val safeHeight = rect.height().coerceAtMost(mask.height - safeTop)
    if (safeWidth <= 0 || safeHeight <= 0) {
        if (!mask.isRecycled) mask.recycle()
        return 0.0
    }
    val cropped = Bitmap.createBitmap(mask, safeLeft, safeTop, safeWidth, safeHeight)
    if (cropped != mask && !mask.isRecycled) mask.recycle()
    val w = cropped.width
    val h = cropped.height
    val pixels = IntArray(w * h)
    cropped.getPixels(pixels, 0, w, 0, 0, w, h)
    if (!cropped.isRecycled) cropped.recycle()
    var blackCount = 0
    var rowsWithBlack = 0
    for (y in 0 until h) {
        var rowHasBlack = false
        val rowStart = y * w
        for (x in 0 until w) {
            val p = pixels[rowStart + x]
            if ((p and 0x00FFFFFF) == 0x000000) {
                blackCount++
                rowHasBlack = true
            }
        }
        if (rowHasBlack) rowsWithBlack++
    }
    val total = w * h
    if (total <= 0) return 0.0
    val blackRatio = blackCount.toDouble() / total.toDouble()
    val rowCoverage = rowsWithBlack.toDouble() / h.toDouble()
    val ratioScore = when {
        blackRatio < 0.005 -> 0.0
        blackRatio < 0.015 -> 0.5
        blackRatio <= 0.20 -> 1.0
        blackRatio <= 0.30 -> 0.5
        else -> 0.0
    }
    val rowScore = when {
        rowCoverage < 0.15 -> 0.0
        rowCoverage < 0.35 -> 0.5
        rowCoverage <= 0.85 -> 1.0
        else -> 0.5
    }
    return (ratioScore * 0.6) + (rowScore * 0.4)
}
