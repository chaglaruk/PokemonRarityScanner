package com.pokerarity.scanner.data.model

/**
 * Builder for constructing [OcrConfidenceReasons] from scan pipeline data.
 *
 * Initially supports only CP, HP, and CAUGHT_DATE fields. Additional fields
 * (SPECIES, SIZE_TAG, LUCKY) will be added incrementally as the pipeline
 * migrates away from rawOcrText markers.
 */
class OcrConfidenceReasonsBuilder {

    private val fields = mutableListOf<OcrFieldConfidence>()
    private val warnings = mutableListOf<String>()

    /** Record CP field status based on parsed value presence. */
    fun withCp(
        cp: Int?,
        source: OcrSignalSource = OcrSignalSource.TOP_TEXT,
        confidence: Float? = null,
        reasonCodes: List<String> = emptyList()
    ): OcrConfidenceReasonsBuilder {
        val status = if (cp != null) OcrFieldStatus.PARSED else OcrFieldStatus.MISSING
        val codes = reasonCodes.ifEmpty {
            if (cp != null) listOf("cp_parsed") else listOf("cp_missing")
        }
        fields.add(OcrFieldConfidence(OcrField.CP, status, source, confidence, codes))
        return this
    }

    /** Record HP field status based on parsed values. */
    fun withHp(
        hp: Int?,
        maxHp: Int?,
        source: OcrSignalSource = OcrSignalSource.TOP_TEXT,
        confidence: Float? = null,
        reasonCodes: List<String> = emptyList()
    ): OcrConfidenceReasonsBuilder {
        val status = when {
            maxHp != null -> OcrFieldStatus.PARSED
            hp != null -> OcrFieldStatus.LOW_CONFIDENCE
            else -> OcrFieldStatus.MISSING
        }
        val codes = reasonCodes.ifEmpty {
            when {
                maxHp != null -> listOf("hp_pair_parsed")
                hp != null -> listOf("hp_current_only")
                else -> listOf("hp_missing")
            }
        }
        fields.add(OcrFieldConfidence(OcrField.HP, status, source, confidence, codes))
        return this
    }

    /** Record caught date field status. */
    fun withCaughtDate(
        caughtDate: java.util.Date?,
        source: OcrSignalSource = OcrSignalSource.TOP_TEXT,
        confidence: Float? = null,
        reasonCodes: List<String> = emptyList()
    ): OcrConfidenceReasonsBuilder {
        val status = if (caughtDate != null) OcrFieldStatus.PARSED else OcrFieldStatus.MISSING
        val codes = reasonCodes.ifEmpty {
            if (caughtDate != null) listOf("date_parsed") else listOf("date_missing")
        }
        fields.add(OcrFieldConfidence(OcrField.CAUGHT_DATE, status, source, confidence, codes))
        return this
    }

    /** Add a general warning message. */
    fun addWarning(warning: String): OcrConfidenceReasonsBuilder {
        warnings.add(warning)
        return this
    }

    /** Build the immutable [OcrConfidenceReasons]. */
    fun build(): OcrConfidenceReasons = OcrConfidenceReasons(
        fields = fields.toList(),
        warnings = warnings.toList()
    )
}
