package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import java.util.Locale

/** Strict, explicitly labeled CP reads shared by full-frame and targeted-crop extraction. */
internal object CpFieldEvidence {
    private val label = Regex("(?i)\\bC\\s*P(?=\\s|\\d|$)")
    private val numericToken = Regex(
        """^\s*([0-9OIL|SBZ](?:[0-9OIL|SBZ]|[\s,]+(?=[0-9OIL|SBZ]))*)""",
        RegexOption.IGNORE_CASE
    )

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
            label.findAll(line.text).map { match ->
                val followingText = line.text.substring(match.range.last + 1)
                val token = numericToken.find(followingText)
                val hasMalformedSuffix = token?.let { hasMalformedCpSuffix(followingText, it) } == true
                val value = token?.takeUnless { hasMalformedSuffix }
                    ?.groupValues?.get(1)
                    ?.let(::parseCompleteCpToken)
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
        val labeledLines = layout.lines.filter { it.bounds != null && label.containsMatchIn(it.text) }
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

    private fun parseCompleteCpToken(token: String): Int? {
        val compact = token.uppercase(Locale.ROOT)
            .replace(Regex("[\\s,]"), "")
            .map { character ->
                when (character) {
                    'O' -> '0'
                    'I', 'L', '|' -> '1'
                    'S' -> '5'
                    'B' -> '8'
                    'Z' -> '2'
                    else -> character
                }
            }.joinToString("")
        return compact.takeIf { it.length in MIN_CP_DIGITS..MAX_CP_DIGITS && it.all(Char::isDigit) }
            ?.toIntOrNull()
            ?.takeIf { it in FieldCandidateNormalizer.MIN_SUPPORTED_CP..
                FieldCandidateNormalizer.MAX_SUPPORTED_CP }
    }

    private fun hasMalformedCpSuffix(followingText: String, token: MatchResult): Boolean {
        val suffixIndex = token.range.last + 1
        val suffix = followingText.getOrNull(suffixIndex)
        return suffix?.isLetterOrDigit() == true || suffix == '/' || suffix == '\\' || suffix == ':' ||
            (suffix == '.' && followingText.getOrNull(suffixIndex + 1)?.isLetterOrDigit() == true)
    }

    private const val MIN_CP_DIGITS = 2
    private const val MAX_CP_DIGITS = 4
}
