package com.pokerarity.scanner.util.ocr

import android.graphics.Rect

enum class ScreenField(val diagnosticName: String) {
    CP("CP"),
    HP("HP"),
    Name("Name"),
    DynamicName("DynamicName"),
    Candy("Candy"),
    Date("Date"),
    Stardust("Stardust"),
    SizeTag("SizeTag"),
    AppraisalBox("AppraisalBox"),
    AppraisalAttack("AppraisalAttack"),
    AppraisalDefense("AppraisalDefense"),
    AppraisalStamina("AppraisalStamina"),
    Arc("Arc")
}

enum class CropProvenance(val diagnosticName: String) {
    AnchorDerived("anchor-derived"),
    LegacyFallback("legacy-fallback"),
    NotAvailable("not-available")
}

data class ScreenCrop(
    val field: ScreenField,
    val rect: Rect?,
    val confidence: Float,
    val provenance: CropProvenance,
    val reasons: List<String> = emptyList()
)

data class ScreenGeometry(
    val imageWidth: Int,
    val imageHeight: Int,
    val classification: ScreenClassificationResult,
    val crops: Map<ScreenField, ScreenCrop>,
    val anchors: List<ScreenAnchor>,
    val fallbackReasons: List<String> = emptyList()
) {
    fun crop(field: ScreenField): ScreenCrop? = crops[field]

    /** Detail-card anchor rect; the scroll-state reference for persistent calibration. */
    val detailCardRect: Rect?
        get() = anchors.firstOrNull { it.name == ScreenAnchorName.DetailCard }?.rect

    /** A non-detail route may expose a bright-panel anchor; it is not a candy boundary. */
    val candyDetailCardTop: Int?
        get() = when (classification.screenType) {
            ScreenType.PokemonDetail, ScreenType.PokemonDetailScrolled, ScreenType.Appraisal ->
                detailCardRect?.top
            else -> null
        }
}

/** A narrow, independently anchored title area usable when HP text/bar evidence is absent. */
internal fun ScreenGeometry.detailNameFallbackBand(frameWidth: Int, frameHeight: Int): Rect? =
    takeIf { supportsDetailNameFallback(frameWidth, frameHeight) }
        ?.detailCardAnchor()
        ?.let { card ->
            anchoredNameCrop()?.takeIf { isValidDetailNameCrop(it, card.rect, frameWidth, frameHeight) }
                ?.let(::Rect)
        }

private fun ScreenGeometry.supportsDetailNameFallback(frameWidth: Int, frameHeight: Int): Boolean {
    val dimensionsMatch = imageWidth == frameWidth && imageHeight == frameHeight
    val dimensionsPositive = frameWidth > 0 && frameHeight > 0
    val classificationTrusted = !classification.safeFallback &&
        classification.confidence >= DETAIL_NAME_GEOMETRY_CONFIDENCE
    val supportedScreen = classification.screenType in DETAIL_NAME_SCREEN_TYPES
    val frameTrusted = dimensionsMatch && dimensionsPositive
    return frameTrusted && classificationTrusted && supportedScreen
}

private fun ScreenGeometry.detailCardAnchor(): ScreenAnchor? = anchors
    .firstOrNull { it.name == ScreenAnchorName.DetailCard }
    ?.takeIf { it.confidence >= DETAIL_NAME_GEOMETRY_CONFIDENCE && !it.rect.isEmpty }

private fun ScreenGeometry.anchoredNameCrop(): Rect? = crop(ScreenField.Name)
    ?.takeIf {
        it.provenance == CropProvenance.AnchorDerived && it.confidence >= DETAIL_NAME_GEOMETRY_CONFIDENCE
    }
    ?.rect

private fun isValidDetailNameCrop(crop: Rect, card: Rect, frameWidth: Int, frameHeight: Int): Boolean {
    val cropBoundsInsideFrame = crop.left >= 0 && crop.top >= 0 &&
        crop.right <= frameWidth && crop.bottom <= frameHeight
    val cardBoundsInsideFrame = card.left >= 0 && card.top >= 0 &&
        card.right <= frameWidth && card.bottom <= frameHeight
    val minimumHeight = (frameHeight * DETAIL_NAME_MIN_HEIGHT_RATIO).toInt().coerceAtLeast(1)
    val maximumHeight = (frameHeight * DETAIL_NAME_MAX_HEIGHT_RATIO).toInt()
    val cropSizeValid = crop.width() >= frameWidth / 2 && crop.height() in minimumHeight..maximumHeight
    val cardTopIsAnchored = card.top in crop.top until crop.bottom
    val geometryBoundsValid = cropBoundsInsideFrame && cardBoundsInsideFrame
    val geometryShapeValid = cropSizeValid && cardTopIsAnchored
    return geometryBoundsValid && geometryShapeValid
}

private const val DETAIL_NAME_GEOMETRY_CONFIDENCE = 0.68f
private const val DETAIL_NAME_MIN_HEIGHT_RATIO = 0.02f
private const val DETAIL_NAME_MAX_HEIGHT_RATIO = 0.12f
private val DETAIL_NAME_SCREEN_TYPES = setOf(
    ScreenType.PokemonDetail,
    ScreenType.PokemonDetailScrolled,
    ScreenType.Appraisal
)
