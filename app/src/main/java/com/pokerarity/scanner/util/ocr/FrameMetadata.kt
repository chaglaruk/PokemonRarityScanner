package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import java.time.LocalDate
import java.util.Locale

internal data class FrameMetadata(
    val caughtLocalDate: LocalDate?,
    val sizeTag: String?,
    val lucky: Boolean,
    val shadowBonusEvidence: Boolean
)

private const val SHADOW_BONUS_MIN_VERTICAL_RATIO = 0.55f
private const val SHADOW_BONUS_MAX_VERTICAL_RATIO = 0.98f

/** Exact lower-screen Shadow Bonus text is a same-frame positive cue on detail screens only. */
internal fun hasShadowBonusDetailCue(
    layout: MLKitOcrProvider.Layout,
    imageHeight: Int,
    detailScreen: Boolean
): Boolean {
    if (!detailScreen || layout.documentStatus != OcrDocumentStatus.SUCCESS || imageHeight <= 0) return false

    val minCenterY = (imageHeight * SHADOW_BONUS_MIN_VERTICAL_RATIO).toInt()
    val maxCenterY = (imageHeight * SHADOW_BONUS_MAX_VERTICAL_RATIO).toInt()
    return layout.lines.any { line ->
        val bounds = line.bounds ?: return@any false
        val normalized = line.text.uppercase(Locale.ROOT).filterNot(Char::isWhitespace)
        normalized == "SHADOWBONUS" && bounds.centerY() in minCenterY..maxCenterY
    }
}

internal fun frameMetadata(
    layout: MLKitOcrProvider.Layout,
    bitmap: Bitmap,
    geometry: ScreenGeometry?,
    detailScreen: Boolean
): FrameMetadata {
    val rect = geometry?.crop(ScreenField.Date)?.rect?.takeUnless(Rect::isEmpty)
        ?: ScreenRegions.getRectForRegion(bitmap, ScreenRegions.REGION_DATE_BADGE)
    val caughtLocalDate = parseCaughtLocalDateFromOcrLayout(layout, bitmap.height, rect)
    val (size, lucky) = sizeTagAndLuckyFromOcrLayout(layout)
    val supportedDetailRoute = when (geometry?.classification?.screenType) {
        ScreenType.PokemonDetail, ScreenType.PokemonDetailScrolled -> true
        else -> false
    }
    val shadowBonusEvidence = supportedDetailRoute &&
        hasShadowBonusDetailCue(layout, bitmap.height, detailScreen)
    return FrameMetadata(caughtLocalDate, size, lucky, shadowBonusEvidence)
}
