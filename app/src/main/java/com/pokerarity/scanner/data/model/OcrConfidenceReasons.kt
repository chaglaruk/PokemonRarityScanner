package com.pokerarity.scanner.data.model

/**
 * Structured OCR confidence data for a single parsed field.
 *
 * Replaces pipe-delimited marker strings in rawOcrText with typed,
 * privacy-safe metadata. Introduced incrementally — initially only
 * CP, HP, and CAUGHT_DATE are populated.
 */
data class OcrFieldConfidence(
    val field: OcrField,
    val status: OcrFieldStatus,
    val source: OcrSignalSource,
    val confidence: Float? = null,
    val reasonCodes: List<String> = emptyList()
)

/**
 * Aggregated OCR confidence reasons for a scan result.
 *
 * Designed to travel alongside [PokemonData] without exposing raw OCR
 * text or local file paths. Only reason codes, statuses, and boolean
 * presence flags should appear in telemetry payloads.
 */
data class OcrConfidenceReasons(
    val fields: List<OcrFieldConfidence> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    /** Find the confidence entry for a specific field, if present. */
    fun forField(field: OcrField): OcrFieldConfidence? =
        fields.find { it.field == field }

    /** True if the given field was parsed successfully. */
    fun isParsed(field: OcrField): Boolean =
        forField(field)?.status == OcrFieldStatus.PARSED

    /** True if the given field is missing from OCR output. */
    fun isMissing(field: OcrField): Boolean =
        forField(field)?.status == OcrFieldStatus.MISSING

    /** Collect all reason codes across all fields. */
    fun allReasonCodes(): List<String> =
        fields.flatMap { it.reasonCodes }

    companion object {
        /** Empty instance for backward compatibility. */
        val EMPTY = OcrConfidenceReasons()
    }
}

/** Enumeration of OCR-extracted fields that can carry confidence metadata. */
enum class OcrField {
    CP,
    HP,
    CAUGHT_DATE,
    SPECIES,
    SIZE_TAG,
    LUCKY
}

/** Status of an individual OCR field extraction attempt. */
enum class OcrFieldStatus {
    /** Field was successfully parsed from OCR output. */
    PARSED,
    /** Field was not found in OCR output. */
    MISSING,
    /** Field was parsed but with low confidence. */
    LOW_CONFIDENCE,
    /** Multiple conflicting values were found for this field. */
    CONFLICT
}

/** Source/method that produced the OCR field value. */
enum class OcrSignalSource {
    /** Primary top-text OCR pass. */
    TOP_TEXT,
    /** Secondary detailed OCR pass. */
    DETAIL_PASS,
    /** Inferred from candy name or family hints. */
    CANDY,
    /** Inferred from visual/image analysis. */
    VISUAL,
    /** Computed via mathematical fallback (e.g., CP from stats). */
    MATH_FALLBACK
}
