package com.pokerarity.scanner.util.ocr

import android.graphics.Rect

/** Strict, explicitly labeled CP reads shared by full-frame and targeted-crop extraction. */
internal object CpFieldEvidence {
    fun read(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        nameBottom: Int?,
        nameBand: Rect?
    ): FieldRead<Int> {
        val eligibleLines = lines.filter { line ->
            val bounds = line.bounds
            bounds != null && (nameBottom == null || bounds.bottom < nameBottom) &&
                !insideNameBand(bounds, nameBand)
        }
        val cpLabels = eligibleLines.flatMap { line ->
            AnchoredScreenText.CP_LABEL.findAll(line.text).flatMap { match ->
                readLabel(line, eligibleLines, match.range.last + 1)
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

    fun readCrop(
        layout: MLKitOcrProvider.Layout,
        allowUnlabeledHeaderNumber: Boolean = false
    ): FieldRead<Int> {
        if (layout.documentStatus == OcrDocumentStatus.FAILED) {
            return FieldRead.unreadable("cp_crop_ocr_failed")
        }
        val labeledRead = read(layout.lines, nameBottom = null, nameBand = null)
        return if (allowUnlabeledHeaderNumber && labeledRead.status == FieldReadStatus.MISSING_NOT_VISIBLE) {
            readUnlabeledHeaderNumber(layout.lines)
        } else {
            labeledRead
        }
    }

    /**
     * The detail-screen CP crop is already bounded by a detected CP header anchor.
     * A Pokémon pose can obscure the literal "CP" label while ML Kit still reads its
     * single numeric value. Accept only one complete, in-range number on a located OCR
     * line; malformed or conflicting numbers remain withheld. The caller still applies
     * species-profile and frame-fusion validation before publishing this candidate.
     */
    private fun readUnlabeledHeaderNumber(lines: List<MLKitOcrProvider.RecognizedBlock>): FieldRead<Int> {
        val numericLines = lines.filter { line ->
            line.bounds != null && line.text.any { character ->
                character.isDigit() || character.uppercaseChar() in "OIL|SBZ"
            }
        }
        return when {
            numericLines.isEmpty() -> FieldRead.missing("no_cp_label")
            numericLines.size != 1 -> FieldRead.missing("unlabeled_cp_header_numeric_ambiguous")
            else -> readUniqueUnlabeledHeaderNumber(numericLines.single().text)
        }
    }

    private fun readUniqueUnlabeledHeaderNumber(rawText: String): FieldRead<Int> {
        val text = rawText.trim()
        val tokenStart = text.indexOfFirst { character ->
            character.isDigit() || character.uppercaseChar() in "OIL|SBZ"
        }
        val prefix = text.take((tokenStart.coerceAtLeast(0)))
        val numericText = text.drop(tokenStart.coerceAtLeast(0))
        val token = CP_NUMERIC_TOKEN.find(numericText)
        val completeToken = token?.takeIf {
            it.range.first == 0 && it.range.last == numericText.lastIndex
        }
        val value = completeToken?.groupValues?.get(1)?.let(::parseCompleteAnchoredCpToken)
        return when {
            tokenStart < 0 -> FieldRead.missing("unlabeled_cp_header_numeric_unresolved")
            !prefix.all { character -> character.isWhitespace() || character.uppercaseChar() in "CP:" } ->
                FieldRead.missing("unlabeled_cp_header_numeric_unresolved")
            completeToken == null || value == null ->
                FieldRead.missing("unlabeled_cp_header_numeric_unresolved")
            else -> FieldRead.read(value).copy(reasonCode = "unlabeled_cp_header_numeric")
        }
    }

    /** Full-frame reads are preserved; the caller gates this merge on MISSING_NOT_VISIBLE. */
    fun applyCrop(
        fields: AnchoredScreenText.Fields,
        cropLayout: MLKitOcrProvider.Layout,
        allowUnlabeledHeaderNumber: Boolean = false
    ): AnchoredScreenText.Fields = if (fields.cpRead.status != FieldReadStatus.MISSING_NOT_VISIBLE) fields
    else mergeCrop(fields, readCrop(cropLayout, allowUnlabeledHeaderNumber))

    private fun mergeCrop(
        fields: AnchoredScreenText.Fields,
        cropRead: FieldRead<Int>
    ): AnchoredScreenText.Fields = if (cropRead.status == FieldReadStatus.MISSING_NOT_VISIBLE) fields else fields.copy(
            cp = cropRead.value,
            cpRead = cropRead,
            detailScreen = fields.detailScreen && cropRead.status != FieldReadStatus.CONFLICT,
            numericConflict = fields.numericConflict || cropRead.status == FieldReadStatus.CONFLICT,
            identityBlockingNumericConflict = fields.identityBlockingNumericConflict ||
                cropRead.status == FieldReadStatus.CONFLICT
        )

    private fun insideNameBand(bounds: Rect, nameBand: Rect?): Boolean =
        nameBand != null && bounds.centerY() in nameBand.top until nameBand.bottom &&
            bounds.centerX() in nameBand.left..nameBand.right

    private fun readLabel(
        labelLine: MLKitOcrProvider.RecognizedBlock,
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        valueStart: Int
    ): List<CpLabel> {
        val followingText = labelLine.text.substring(valueStart)
        val token = CP_NUMERIC_TOKEN.find(followingText)
        val values = when {
            token == null && followingText.isBlank() ->
                recoverAdjacentCpValues(labelLine, lines).ifEmpty { listOf(null) }
            token == null -> listOf(null)
            hasMalformedCpSuffix(followingText, token) -> listOf(null)
            else -> {
                val tokenText = token.groupValues[1]
                val value = parseCompleteAnchoredCpToken(tokenText)
                val isCleanPrefix = hasCleanCpPrefixSuffix(followingText, token) &&
                    isSingleNonZeroCpDigit(tokenText)
                when {
                    value != null -> (listOf(value) +
                        recoverAdjacentCpValues(labelLine, lines, tokenText).filterNotNull()).distinct()
                    isCleanPrefix -> recoverAdjacentCpValues(labelLine, lines, tokenText)
                        .ifEmpty { listOf(null) }
                    else -> listOf(null)
                }
            }
        }
        return values.map(::CpLabel)
    }

    private data class CpLabel(val value: Int?)
}
