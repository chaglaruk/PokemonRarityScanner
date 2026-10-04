package com.pokerarity.scanner.util.ocr

/**
 * Phase 2C structured-extraction contract for a single field.
 *
 * Extraction must not collapse every failure into null: a value that was visible but
 * unreadable is semantically different from a field that is not present on the screen,
 * and two conflicting readings are different from both. [value] is non-null only for
 * [FieldReadStatus.READ]; the [reasonCode] explains every other state.
 */
enum class FieldReadStatus {
    READ,
    MISSING_NOT_VISIBLE,
    VISIBLE_UNREADABLE,
    CONFLICT,
    UNSUPPORTED
}

data class FieldRead<T>(
    val status: FieldReadStatus,
    val value: T? = null,
    val candidateCount: Int = 0,
    val reasonCode: String
) {

    companion object {
        fun <T> read(value: T, candidateCount: Int = 1): FieldRead<T> =
            FieldRead(FieldReadStatus.READ, value, candidateCount, "read")

        fun <T> missing(reason: String): FieldRead<T> =
            FieldRead(FieldReadStatus.MISSING_NOT_VISIBLE, null, 0, reason)

        fun <T> unreadable(reason: String, candidateCount: Int = 0): FieldRead<T> =
            FieldRead(FieldReadStatus.VISIBLE_UNREADABLE, null, candidateCount, reason)

        fun <T> conflict(reason: String, candidateCount: Int): FieldRead<T> =
            FieldRead(FieldReadStatus.CONFLICT, null, candidateCount, reason)

        fun <T> unsupported(reason: String): FieldRead<T> =
            FieldRead(FieldReadStatus.UNSUPPORTED, null, 0, reason)
    }
}

/**
 * Per-frame inputs for the structured-extraction stage: the located HP bar plus the
 * geometry-layer derivations — the bar-anchored name band (action anchors must never sit
 * inside the title/nickname band) and the detail-card top (upper bound for the candy row
 * when the bar is unavailable). Null bar/name band → the extractor falls back to its
 * conservative spatial-only behavior; a weak band never establishes authority by itself.
 */
data class ExtractionContext(
    val bar: android.graphics.Rect? = null,
    val nameBand: android.graphics.Rect? = null,
    val detailCardTop: Int? = null
)
