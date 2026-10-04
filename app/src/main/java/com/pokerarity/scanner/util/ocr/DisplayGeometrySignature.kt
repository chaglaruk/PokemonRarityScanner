package com.pokerarity.scanner.util.ocr

/**
 * Display/configuration signature of one screenshot frame (Phase 2B persistent calibration).
 *
 * The signature identifies the display configuration a calibration was built for. It always
 * carries BOTH the source screenshot geometry and the recognition bitmap geometry: the scan
 * pipeline downsizes frames wider than [RECOGNITION_MAX_WIDTH] before routing/OCR, so two
 * different source displays can produce identical recognition bitmaps. Compatibility is
 * therefore decided on the full signature — a source-resolution change never matches a
 * calibration built for another source, even when both scale to the same recognition width.
 *
 * Stored values are non-sensitive display/layout metadata (dimensions, orientation, density).
 */
data class DisplayGeometrySignature(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val orientation: Orientation,
    val densityDpi: Int,
    val recognitionWidth: Int,
    val recognitionHeight: Int
) {

    enum class Orientation { PORTRAIT, LANDSCAPE }

    /** Deterministic, file-safe key used as the persistence identity of a calibration. */
    val stableKey: String =
        "${sourceWidth}x${sourceHeight}:${orientation.name.first()}:$densityDpi:" +
            "${recognitionWidth}x$recognitionHeight"

    /** Only the exact same display configuration reuses a persisted calibration. */
    fun isCompatibleWith(other: DisplayGeometrySignature): Boolean = this == other

    companion object {
        /** Mirrors the production decode policy (ScanManager downsizes wider frames). */
        const val RECOGNITION_MAX_WIDTH = 900

        fun from(
            sourceWidth: Int,
            sourceHeight: Int,
            densityDpi: Int,
            recognitionWidth: Int,
            recognitionHeight: Int
        ): DisplayGeometrySignature? {
            val sourceValid = sourceWidth > 0 && sourceHeight > 0
            val recognitionValid = recognitionWidth > 0 && recognitionHeight > 0
            if (!sourceValid || !recognitionValid || densityDpi <= 0) return null
            return DisplayGeometrySignature(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                orientation = if (sourceHeight >= sourceWidth) {
                    Orientation.PORTRAIT
                } else {
                    Orientation.LANDSCAPE
                },
                densityDpi = densityDpi,
                recognitionWidth = recognitionWidth,
                recognitionHeight = recognitionHeight
            )
        }
    }
}
