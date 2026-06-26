package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect

class ScreenGeometryBuilder(
    private val classifier: ScreenClassifier = ScreenClassifier()
) {

    fun build(bitmap: Bitmap): ScreenGeometry {
        val classification = classifier.classify(bitmap)
        val canUseAnchors = !classification.safeFallback &&
            classification.confidence >= ANCHOR_CONFIDENCE_THRESHOLD &&
            classification.screenType in anchorSupportedScreens

        val crops = if (canUseAnchors) {
            buildAnchorDerivedCrops(bitmap, classification)
        } else {
            buildLegacyFallbackCrops(bitmap, classification)
        }

        val fallbackReasons = if (canUseAnchors) {
            emptyList()
        } else {
            listOf(
                "geometry_fallback:${classification.screenType}:${"%.2f".format(classification.confidence)}"
            ) + classification.reasons
        }

        return ScreenGeometry(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            classification = classification,
            crops = crops,
            anchors = classification.anchors,
            fallbackReasons = fallbackReasons.distinct()
        )
    }

    private fun buildAnchorDerivedCrops(
        bitmap: Bitmap,
        classification: ScreenClassificationResult
    ): Map<ScreenField, ScreenCrop> {
        val width = bitmap.width
        val height = bitmap.height
        val detailCard = classification.anchor(ScreenAnchorName.DetailCard)?.rect
            ?: Rect(0, (height * 0.42f).toInt(), width, (height * 0.90f).toInt())
        val appraisal = classification.anchor(ScreenAnchorName.AppraisalPanel)?.rect
        val dateBadge = ImagePreprocessor.detectOrangeBadge(bitmap)

        val map = linkedMapOf<ScreenField, ScreenCrop>()
        fun put(field: ScreenField, rect: Rect?, confidence: Float, reason: String) {
            map[field] = crop(field, clamp(width, height, rect), confidence, CropProvenance.AnchorDerived, reason)
        }

        put(ScreenField.CP, classification.anchor(ScreenAnchorName.CpHeader)?.rect ?: relative(width, height, 0.10f, 0.050f, 0.80f, 0.052f), 0.78f, "cp_header_anchor")
        put(ScreenField.DynamicName, Rect((width * 0.12f).toInt(), 0, (width * 0.88f).toInt(), minOf((height * 0.58f).toInt(), detailCard.bottom)), 0.70f, "detail_card_search_band")
        put(ScreenField.Name, Rect((width * 0.10f).toInt(), detailCard.top - (height * 0.040f).toInt(), (width * 0.90f).toInt(), detailCard.top + (height * 0.018f).toInt()), 0.72f, "detail_card_top")
        put(ScreenField.HP, Rect((width * 0.14f).toInt(), detailCard.top + (height * 0.026f).toInt(), (width * 0.86f).toInt(), detailCard.top + (height * 0.088f).toInt()), 0.70f, "detail_card_hp_band")
        put(ScreenField.Candy, Rect((width * 0.20f).toInt(), detailCard.top + (height * 0.205f).toInt(), (width * 0.94f).toInt(), detailCard.top + (height * 0.285f).toInt()), 0.64f, "detail_card_lower_fields")
        if (dateBadge != null) {
            put(ScreenField.Date, dateBadge, 0.72f, "orange_badge_anchor")
        } else {
            map[ScreenField.Date] = legacy(bitmap, ScreenField.Date, ScreenRegions.REGION_DATE_BADGE, "date_badge_legacy")
        }
        put(ScreenField.Stardust, Rect((width * 0.42f).toInt(), detailCard.top + (height * 0.300f).toInt(), (width * 0.86f).toInt(), detailCard.top + (height * 0.382f).toInt()), 0.58f, "detail_card_power_up_area")
        put(ScreenField.SizeTag, Rect((width * 0.04f).toInt(), detailCard.top + (height * 0.090f).toInt(), (width * 0.42f).toInt(), detailCard.top + (height * 0.160f).toInt()), 0.52f, "detail_card_size_area")
        put(ScreenField.Arc, Rect((width * 0.10f).toInt(), (height * 0.145f).toInt(), (width * 0.90f).toInt(), detailCard.top), 0.54f, "detail_card_arc_band")

        if (appraisal != null) {
            put(ScreenField.AppraisalBox, appraisal, 0.72f, "appraisal_panel_anchor")
            val boxHeight = appraisal.height().coerceAtLeast(1)
            val left = (width * 0.34f).toInt()
            val right = (width * 0.90f).toInt()
            put(ScreenField.AppraisalAttack, Rect(left, appraisal.top + (boxHeight * 0.22f).toInt(), right, appraisal.top + (boxHeight * 0.34f).toInt()), 0.64f, "appraisal_panel_anchor")
            put(ScreenField.AppraisalDefense, Rect(left, appraisal.top + (boxHeight * 0.43f).toInt(), right, appraisal.top + (boxHeight * 0.55f).toInt()), 0.64f, "appraisal_panel_anchor")
            put(ScreenField.AppraisalStamina, Rect(left, appraisal.top + (boxHeight * 0.64f).toInt(), right, appraisal.top + (boxHeight * 0.76f).toInt()), 0.64f, "appraisal_panel_anchor")
        } else {
            putUnavailable(map, ScreenField.AppraisalBox, "appraisal_panel_not_detected")
            putUnavailable(map, ScreenField.AppraisalAttack, "appraisal_panel_not_detected")
            putUnavailable(map, ScreenField.AppraisalDefense, "appraisal_panel_not_detected")
            putUnavailable(map, ScreenField.AppraisalStamina, "appraisal_panel_not_detected")
        }

        return map
    }

    private fun buildLegacyFallbackCrops(
        bitmap: Bitmap,
        classification: ScreenClassificationResult
    ): Map<ScreenField, ScreenCrop> {
        val map = linkedMapOf<ScreenField, ScreenCrop>()
        map[ScreenField.CP] = legacy(bitmap, ScreenField.CP, ScreenRegions.REGION_CP, "legacy_screen_region")
        map[ScreenField.HP] = legacy(bitmap, ScreenField.HP, ScreenRegions.REGION_HP, "legacy_screen_region")
        map[ScreenField.Name] = legacy(bitmap, ScreenField.Name, ScreenRegions.REGION_NAME, "legacy_screen_region")
        map[ScreenField.DynamicName] = crop(
            ScreenField.DynamicName,
            clamp(
                bitmap.width,
                bitmap.height,
                Rect(
                    (bitmap.width * 0.12f).toInt(),
                    0,
                    (bitmap.width * 0.88f).toInt(),
                    (bitmap.height * 0.58f).toInt()
                )
            ),
            0.45f,
            CropProvenance.LegacyFallback,
            "legacy_dynamic_search_band"
        )
        map[ScreenField.Candy] = legacy(bitmap, ScreenField.Candy, ScreenRegions.REGION_CANDY, "legacy_screen_region")
        map[ScreenField.Date] = legacy(bitmap, ScreenField.Date, ScreenRegions.REGION_DATE_BADGE, "legacy_screen_region")
        map[ScreenField.Stardust] = legacy(bitmap, ScreenField.Stardust, ScreenRegions.REGION_STARDUST, "legacy_screen_region")
        putUnavailable(map, ScreenField.SizeTag, "no_legacy_size_tag_crop")
        putUnavailable(map, ScreenField.Arc, "classifier_${classification.screenType}_too_weak")

        val appraisal = ScreenRegions.detectAppraisalBox(bitmap)
        if (appraisal != null) {
            map[ScreenField.AppraisalBox] = crop(
                ScreenField.AppraisalBox,
                clamp(bitmap.width, bitmap.height, Rect(0, appraisal.top, bitmap.width, appraisal.bottom)),
                0.42f,
                CropProvenance.LegacyFallback,
                "legacy_appraisal_box_detector"
            )
        } else {
            putUnavailable(map, ScreenField.AppraisalBox, "appraisal_panel_not_detected")
        }
        putUnavailable(map, ScreenField.AppraisalAttack, "appraisal_panel_not_detected")
        putUnavailable(map, ScreenField.AppraisalDefense, "appraisal_panel_not_detected")
        putUnavailable(map, ScreenField.AppraisalStamina, "appraisal_panel_not_detected")
        return map
    }

    private fun legacy(bitmap: Bitmap, field: ScreenField, region: ScreenRegions.Region, reason: String): ScreenCrop =
        crop(
            field = field,
            rect = clamp(bitmap.width, bitmap.height, ScreenRegions.getRectForRegion(bitmap, region)),
            confidence = 0.45f,
            provenance = CropProvenance.LegacyFallback,
            reason = reason
        )

    private fun crop(
        field: ScreenField,
        rect: Rect?,
        confidence: Float,
        provenance: CropProvenance,
        reason: String
    ): ScreenCrop {
        val safeProvenance = if (rect == null) CropProvenance.NotAvailable else provenance
        return ScreenCrop(
            field = field,
            rect = rect,
            confidence = if (rect == null) 0f else confidence,
            provenance = safeProvenance,
            reasons = listOf(reason)
        )
    }

    private fun putUnavailable(map: MutableMap<ScreenField, ScreenCrop>, field: ScreenField, reason: String) {
        map[field] = ScreenCrop(
            field = field,
            rect = null,
            confidence = 0f,
            provenance = CropProvenance.NotAvailable,
            reasons = listOf(reason)
        )
    }

    private fun ScreenClassificationResult.anchor(name: ScreenAnchorName): ScreenAnchor? =
        anchors.firstOrNull { it.name == name }

    private fun clamp(width: Int, height: Int, rect: Rect?): Rect? {
        if (rect == null || width <= 0 || height <= 0) return null
        val left = rect.left.coerceIn(0, width - 1)
        val top = rect.top.coerceIn(0, height - 1)
        val right = rect.right.coerceIn(left + 1, width)
        val bottom = rect.bottom.coerceIn(top + 1, height)
        return Rect(left, top, right, bottom)
    }

    private fun relative(
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

    companion object {
        private const val ANCHOR_CONFIDENCE_THRESHOLD = 0.68f
        private val anchorSupportedScreens = setOf(
            ScreenType.PokemonDetail,
            ScreenType.PokemonDetailScrolled,
            ScreenType.Appraisal,
            ScreenType.Encounter
        )
    }
}
