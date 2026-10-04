package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting

/**
 * App-private persistence for versioned screen-calibration records (Phase 2B).
 *
 * A small dedicated [SharedPreferences] file detached from unrelated user preferences.
 * One entry per display-configuration signature; the value is an explicit, deterministic
 * field encoding (fixed order, fixed precision). Reading is corruption-safe: any malformed,
 * structurally invalid or foreign-key entry fails closed (null) and is cleared. Only
 * non-sensitive geometry/configuration metadata is ever written.
 */
class ScreenCalibrationStore(private val preferences: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    )

    fun load(signatureKey: String): ScreenCalibrationRecord? {
        val raw = preferences.getString(entryKey(signatureKey), null)
        val record = raw?.let { parseRecord(it, signatureKey) }
        if (record == null && raw != null) clear(signatureKey)
        return record
    }

    fun save(record: ScreenCalibrationRecord) {
        require(record.isStructurallyValid()) { "refusing to persist an invalid calibration record" }
        preferences.edit()
            .putString(entryKey(record.signature.stableKey), encode(record))
            .apply()
    }

    fun clear(signatureKey: String) {
        preferences.edit().remove(entryKey(signatureKey)).apply()
    }

    @VisibleForTesting
    internal fun entryKey(signatureKey: String): String = "$KEY_PREFIX$signatureKey"

    private fun parseRecord(raw: String, expectedSignatureKey: String): ScreenCalibrationRecord? =
        runCatching { decode(raw, expectedSignatureKey) }.getOrNull()?.takeIf { it.isStructurallyValid() }

    internal companion object {
        private const val PREFS_FILE_NAME = "screen_calibration_geometry"
        private const val KEY_PREFIX = "cal|"
        private const val FIELD_COUNT = 9
        private const val FLOAT_PRECISION = 6
        private const val RECT_COMPONENTS = 4
        private const val SEPARATOR = "|"
        private const val COMPONENT_SEPARATOR = ","
        private const val REVISION_PREFIX = "v"
        private const val IDX_REVISION = 0
        private const val IDX_SIGNATURE_KEY = 1
        private const val IDX_HP_BAR = 2
        private const val IDX_DETAIL_CARD = 3
        private const val IDX_NAME_BAND = 4
        private const val IDX_SUCCESSES = 5
        private const val IDX_FAILURES = 6
        private const val IDX_CREATED_AT = 7
        private const val IDX_LAST_VALIDATED = 8
        private const val KEY_PART_SOURCE_DIMS = 0
        private const val KEY_PART_ORIENTATION = 1
        private const val KEY_PART_DENSITY = 2
        private const val KEY_PART_RECOGNITION_DIMS = 3

        /**
         * Field order (pipe-separated, fixed):
         * v<revision> | signatureKey | hpBar | detailCard | nameBand |
         * validationSuccesses | consecutiveValidationFailures | createdAtMs | lastValidatedAtMs
         * Rect fields are left,top,right,bottom as fixed-precision floats.
         */
        internal fun encode(record: ScreenCalibrationRecord): String = listOf(
            "$REVISION_PREFIX${record.schemaRevision}",
            record.signature.stableKey,
            encodeRect(record.hpBar),
            encodeRect(record.detailCard),
            encodeRect(record.nameBand),
            record.validationSuccesses.toString(),
            record.consecutiveValidationFailures.toString(),
            record.createdAtMs.toString(),
            record.lastValidatedAtMs.toString()
        ).joinToString(SEPARATOR)

        private fun encodeRect(rect: NormalizedRect): String =
            listOf(rect.left, rect.top, rect.right, rect.bottom)
                .joinToString(COMPONENT_SEPARATOR) { "%.${FLOAT_PRECISION}f".format(it) }

        private data class DecodedFields(
            val revision: Int?,
            val signatureKey: String,
            val hpBar: NormalizedRect?,
            val detailCard: NormalizedRect?,
            val nameBand: NormalizedRect?,
            val successes: Int?,
            val failures: Int?,
            val createdAtMs: Long?,
            val lastValidatedAtMs: Long?
        ) {
            val complete: Boolean
                get() = revision != null && hpBar != null && detailCard != null && nameBand != null &&
                    successes != null && failures != null && createdAtMs != null && lastValidatedAtMs != null
        }

        private fun decode(raw: String, expectedSignatureKey: String): ScreenCalibrationRecord? {
            val fields = raw.split(SEPARATOR)
            // Index access on a short list throws and is treated as corruption upstream.
            val parsed = if (fields.size != FIELD_COUNT) {
                null
            } else {
                runCatching {
                    DecodedFields(
                        revision = fields[IDX_REVISION].removePrefix(REVISION_PREFIX).toIntOrNull(),
                        signatureKey = fields[IDX_SIGNATURE_KEY],
                        hpBar = decodeRect(fields[IDX_HP_BAR]),
                        detailCard = decodeRect(fields[IDX_DETAIL_CARD]),
                        nameBand = decodeRect(fields[IDX_NAME_BAND]),
                        successes = fields[IDX_SUCCESSES].toIntOrNull(),
                        failures = fields[IDX_FAILURES].toIntOrNull(),
                        createdAtMs = fields[IDX_CREATED_AT].toLongOrNull(),
                        lastValidatedAtMs = fields[IDX_LAST_VALIDATED].toLongOrNull()
                    )
                }.getOrNull()
            }
            val signature = parsed
                ?.takeIf { it.complete && it.signatureKey == expectedSignatureKey }
                ?.let { parseSignatureKey(it.signatureKey) }
            val usable = signature != null && parsed != null
            if (!usable) return null
            return ScreenCalibrationRecord(
                schemaRevision = requireNotNull(requireNotNull(parsed).revision),
                signature = requireNotNull(signature),
                hpBar = requireNotNull(parsed.hpBar),
                detailCard = requireNotNull(parsed.detailCard),
                nameBand = requireNotNull(parsed.nameBand),
                createdAtMs = requireNotNull(parsed.createdAtMs),
                lastValidatedAtMs = requireNotNull(parsed.lastValidatedAtMs),
                validationSuccesses = requireNotNull(parsed.successes),
                consecutiveValidationFailures = requireNotNull(parsed.failures)
            )
        }

        private fun decodeRect(raw: String): NormalizedRect? {
            val values = raw.split(COMPONENT_SEPARATOR).map { it.toFloatOrNull() }
            val complete = values.size == RECT_COMPONENTS && values.none { it == null }
            if (!complete) return null
            val cursor = values.map { requireNotNull(it) }.iterator()
            return NormalizedRect(cursor.next(), cursor.next(), cursor.next(), cursor.next())
        }

        /** Rebuilds the signature from its stable key so restored records stay typed. */
        private fun parseSignatureKey(key: String): DisplayGeometrySignature? {
            // stableKey layout: "<sw>x<sh>:<O>:<dpi>:<rw>x<rh>" → 4 colon-separated parts.
            val parts = key.split(":")
            val source = parts.getOrNull(KEY_PART_SOURCE_DIMS)?.split("x").orEmpty()
            val recognition = parts.getOrNull(KEY_PART_RECOGNITION_DIMS)?.split("x").orEmpty()
            val orientation = parseOrientation(parts.getOrNull(KEY_PART_ORIENTATION))
            val parsed = ParsedSignature(
                sourceWidth = source.getOrNull(0)?.toIntOrNull(),
                sourceHeight = source.getOrNull(1)?.toIntOrNull(),
                orientation = orientation,
                densityDpi = parts.getOrNull(KEY_PART_DENSITY)?.toIntOrNull(),
                recognitionWidth = recognition.getOrNull(0)?.toIntOrNull(),
                recognitionHeight = recognition.getOrNull(1)?.toIntOrNull()
            )
            if (!parsed.complete) return null
            return DisplayGeometrySignature(
                sourceWidth = requireNotNull(parsed.sourceWidth),
                sourceHeight = requireNotNull(parsed.sourceHeight),
                orientation = requireNotNull(parsed.orientation),
                densityDpi = requireNotNull(parsed.densityDpi),
                recognitionWidth = requireNotNull(parsed.recognitionWidth),
                recognitionHeight = requireNotNull(parsed.recognitionHeight)
            )
        }

        private fun parseOrientation(token: String?): DisplayGeometrySignature.Orientation? = when (token) {
            DisplayGeometrySignature.Orientation.PORTRAIT.name.first().toString() ->
                DisplayGeometrySignature.Orientation.PORTRAIT
            DisplayGeometrySignature.Orientation.LANDSCAPE.name.first().toString() ->
                DisplayGeometrySignature.Orientation.LANDSCAPE
            else -> null
        }

        private data class ParsedSignature(
            val sourceWidth: Int?,
            val sourceHeight: Int?,
            val orientation: DisplayGeometrySignature.Orientation?,
            val densityDpi: Int?,
            val recognitionWidth: Int?,
            val recognitionHeight: Int?
        ) {
            val complete: Boolean
                get() = sourceWidth != null && sourceHeight != null && orientation != null &&
                    densityDpi != null && recognitionWidth != null && recognitionHeight != null
        }
    }
}
