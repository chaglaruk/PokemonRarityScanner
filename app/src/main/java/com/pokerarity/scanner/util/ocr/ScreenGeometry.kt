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
}
