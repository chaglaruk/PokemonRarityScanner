package com.pokerarity.scanner.util.ocr

import android.graphics.Rect

/** Strict, explicitly labeled CP reads shared by full-frame and targeted-crop extraction. */
internal object CpFieldEvidence {
    fun read(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        nameBottom: Int?,
        nameBand: Rect?
    ): FieldRead<Int> {
        val cpLabels = lines.filter { line ->
            val bounds = line.bounds
            bounds != null && (nameBottom == null || bounds.bottom < nameBottom) &&
                !insideNameBand(bounds, nameBand)
        }.flatMap { line ->
            AnchoredScreenText.CP_LABEL.findAll(line.text).map { match ->
                val followingText = line.text.substring(match.range.last + 1)
                val token = AnchoredScreenText.CP_NUMERIC_TOKEN.find(followingText)
                val hasMalformedSuffix = token?.let { hasMalformedAnchoredCpSuffix(followingText, it) } == true
                val value = token?.takeUnless { hasMalformedSuffix }
                    ?.groupValues?.get(1)
                    ?.let(::parseCompleteAnchoredCpToken)
                CpLabel(value)
            }.toList()
        }
        val values = cpLabels.mapNotNull { it.value }.distinct()
        val malformedLabels = cpLabels.count { it.value == null }
        val conflict = values.size > 1 || (values.isNotEmpty() && malformedLabels > 0)
        return when {
            conflict -> FieldRead.conflict("conflicting_cp_candidates", values.size + malformedLabels)
            values.size == 1 -> FieldRead.read(values.single())
            malformedLabels > 0 -> FieldRead.unreadable("cp_label_unreadable", malformedLabels)
            else -> FieldRead.missing("no_cp_label")
        }
    }

    fun readCrop(layout: MLKitOcrProvider.Layout): FieldRead<Int> {
        if (layout.documentStatus == OcrDocumentStatus.FAILED) {
            return FieldRead.unreadable("cp_crop_ocr_failed")
        }
        val labeledLines = layout.lines.filter { line ->
            line.bounds != null && AnchoredScreenText.CP_LABEL.containsMatchIn(line.text)
        }
        return read(labeledLines, nameBottom = null, nameBand = null)
    }

    /** Full-frame reads are preserved; the caller gates this merge on MISSING_NOT_VISIBLE. */
    fun applyCrop(
        fields: AnchoredScreenText.Fields,
        cropLayout: MLKitOcrProvider.Layout
    ): AnchoredScreenText.Fields = if (fields.cpRead.status != FieldReadStatus.MISSING_NOT_VISIBLE) fields
    else mergeCrop(fields, readCrop(cropLayout))

    private fun mergeCrop(
        fields: AnchoredScreenText.Fields,
        cropRead: FieldRead<Int>
    ): AnchoredScreenText.Fields = if (cropRead.status == FieldReadStatus.MISSING_NOT_VISIBLE) fields else fields.copy(
            cp = cropRead.value,
            cpRead = cropRead,
            detailScreen = fields.detailScreen && cropRead.status != FieldReadStatus.CONFLICT,
            numericConflict = fields.numericConflict || cropRead.status == FieldReadStatus.CONFLICT
        )

    private fun insideNameBand(bounds: Rect, nameBand: Rect?): Boolean =
        nameBand != null && bounds.centerY() in nameBand.top until nameBand.bottom &&
            bounds.centerX() in nameBand.left..nameBand.right

    private data class CpLabel(val value: Int?)
}
