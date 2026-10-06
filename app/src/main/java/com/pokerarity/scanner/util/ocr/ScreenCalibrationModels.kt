package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * Phase 2B persistent screen-calibration domain.
 *
 * A [ScreenCalibrationRecord] holds normalized anchor/derived geometry for ONE display
 * configuration ([DisplayGeometrySignature]). Rectangles are persisted normalized against
 * the recognition bitmap frame (the space they were measured in) and transformed back with
 * [NormalizedRect.toRect] using that frame's dimensions — the transform is explicit and
 * unit-tested. Records never carry pixels, OCR text, species, paths or device identifiers.
 */

/** Current persisted schema revision; bump on any incompatible record layout change. */
const val CALIBRATION_SCHEMA_REVISION = 1

/** Reason recorded on the hp_bar anchor diagnostic when the bar came from calibration. */
const val CALIBRATED_BAR_ANCHOR_REASON = "calibrated_bar_fallback"

/**
 * Layout-stability tolerances, measured on the preserved S25 corpus (900-wide recognition
 * space, 17 frames): the HP bar rect is pixel-identical across settled detail frames, the
 * detail-card top varies by <=0.003h, one mid-animation frame drifts the bar left edge by
 * 0.051w, and scroll states move the bar/card pair together. 0.02 gives >3x headroom over
 * the settled spread and sits well below the observed bounded animation drift; 0.15
 * separates bounded drift from a real layout change. These are generic layout constants,
 * not Pokémon-specific values.
 */
object CalibrationTolerances {
    const val BAR_LEFT_RATIO = 0.02f
    const val BAR_TOP_RATIO = 0.02f
    const val SCROLL_STATE_RATIO = 0.02f
    const val MAJOR_DRIFT_RATIO = 0.15f

    /** Consecutive failed validations tolerated before a record is invalidated. */
    const val REBUILD_AFTER_CONSECUTIVE_FAILURES = 3

    /** Structural minimums for a persisted normalized rect (bar height measured 0.0062). */
    const val MIN_NORMALIZED_WIDTH = 0.005f
    const val MIN_NORMALIZED_HEIGHT = 0.003f
}

/** A rectangle in normalized [0..1] image coordinates; validated on construction and load. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {

    fun width(): Float = right - left

    fun height(): Float = bottom - top

    fun isValid(minWidth: Float, minHeight: Float): Boolean {
        val inside = left >= 0f && top >= 0f && right <= 1f && bottom <= 1f
        val sized = width() >= minWidth && height() >= minHeight && left < right && top < bottom
        return inside && sized
    }

    /** Explicit transform into recognition-space pixels using the given frame dimensions. */
    fun toRect(frameWidth: Int, frameHeight: Int): Rect? {
        val selfValid = isValid(CalibrationTolerances.MIN_NORMALIZED_WIDTH, CalibrationTolerances.MIN_NORMALIZED_HEIGHT)
        if (frameWidth <= 0 || frameHeight <= 0 || !selfValid) return null
        val left = (left * frameWidth).roundToInt().coerceIn(0, frameWidth - 1)
        val top = (top * frameHeight).roundToInt().coerceIn(0, frameHeight - 1)
        val right = (right * frameWidth).roundToInt().coerceIn(left + 1, frameWidth)
        val bottom = (bottom * frameHeight).roundToInt().coerceIn(top + 1, frameHeight)
        return Rect(left, top, right, bottom)
    }

    companion object {
        fun fromRect(rect: Rect, frameWidth: Int, frameHeight: Int): NormalizedRect? {
            val frameValid = frameWidth > 0 && frameHeight > 0
            val horizontalInside = rect.left >= 0 && rect.right <= frameWidth
            val verticalInside = rect.top >= 0 && rect.bottom <= frameHeight
            val checksValid = listOf(frameValid, !rect.isEmpty, horizontalInside, verticalInside)
            if (checksValid.any { !it }) return null
            return NormalizedRect(
                left = rect.left / frameWidth.toFloat(),
                top = rect.top / frameHeight.toFloat(),
                right = rect.right / frameWidth.toFloat(),
                bottom = rect.bottom / frameHeight.toFloat()
            )
        }
    }
}

/**
 * Per-frame geometry evidence exchanged between the router, the calibration manager and
 * the recognizer: the live bar located by the recognizer (null before OCR ran), the
 * normalized detail-card anchor from the routing classification, and the recognition
 * bitmap dimensions used for all normalization transforms.
 */
data class FrameGeometry(
    val liveBarRect: Rect? = null,
    val detailCard: NormalizedRect? = null,
    val frameWidth: Int,
    val frameHeight: Int
)

/**
 * Persisted calibration for one display configuration. Only geometry/configuration metadata:
 * normalized anchors (HpBar, DetailCard), derived field rectangles (NameBand, mirroring the
 * extractor's name-band ratios), schema revision and validation health counters.
 */
data class ScreenCalibrationRecord(
    val schemaRevision: Int = CALIBRATION_SCHEMA_REVISION,
    val signature: DisplayGeometrySignature,
    val hpBar: NormalizedRect,
    val detailCard: NormalizedRect,
    val nameBand: NormalizedRect,
    val createdAtMs: Long,
    val lastValidatedAtMs: Long,
    val validationSuccesses: Int = 0,
    val consecutiveValidationFailures: Int = 0
) {

    fun isStructurallyValid(): Boolean {
        val countersValid = createdAtMs >= 0 && lastValidatedAtMs >= 0 &&
            validationSuccesses >= 0 && consecutiveValidationFailures >= 0
        val geometryValid = hpBar.isValid(MIN_WIDTH, MIN_HEIGHT) &&
            detailCard.isValid(MIN_WIDTH, MIN_HEIGHT) &&
            nameBand.isValid(MIN_WIDTH, MIN_HEIGHT)
        return schemaRevision > 0 && countersValid && geometryValid
    }

    fun isSchemaCurrent(): Boolean = schemaRevision == CALIBRATION_SCHEMA_REVISION

    fun withValidation(success: Boolean, atMs: Long): ScreenCalibrationRecord =
        if (success) {
            copy(
                lastValidatedAtMs = atMs,
                validationSuccesses = validationSuccesses + 1,
                consecutiveValidationFailures = 0
            )
        } else {
            copy(consecutiveValidationFailures = consecutiveValidationFailures + 1)
        }

    private companion object {
        const val MIN_WIDTH = CalibrationTolerances.MIN_NORMALIZED_WIDTH
        const val MIN_HEIGHT = CalibrationTolerances.MIN_NORMALIZED_HEIGHT
    }
}

/** Why a frame's calibration lookup produced no reusable record. */
enum class CalibrationMissReason(val code: String) {
    NO_RECORD("no_record_for_signature"),
    SCHEMA_MISMATCH("schema_revision_mismatch")
}

/** How persistent calibration participated in one frame's recognition. */
enum class CalibrationResolution {
    /** Persisted calibration validated healthy against the live frame. */
    HIT_VALIDATED,

    /** Persisted calibration alive but a bounded drift was counted. */
    HIT_DEGRADED,

    /** Live bar detection failed; validated-compatible persisted bar seeded the extractor. */
    HIT_SEEDED,

    /** Cold/incompatible signature: full autoconfig built and persisted a fresh record. */
    REBUILT,

    /** Stale record invalidated (major drift or repeated failures) and rebuilt this frame. */
    INVALIDATED_REBUILT,

    /** Stale record invalidated; frame evidence insufficient to rebuild. */
    INVALIDATED,

    /** Persisted record was unusable (schema/corruption) and was cleared. */
    SCHEMA_INVALIDATED,

    /** Record exists but frame evidence could not judge it (no counters change). */
    NOT_APPLICABLE,

    /** No calibration and insufficient evidence to build one. */
    UNAVAILABLE
}

/**
 * Per-frame calibration result flowing between [ScreenCalibrationManager], ScanManager and
 * the recognizer. Carries only typed, non-sensitive fields.
 */
data class FrameResolution(
    val resolution: CalibrationResolution,
    val signatureKey: String? = null,
    val schemaRevision: Int? = null,
    val record: ScreenCalibrationRecord? = null,
    /** Recognition-space bar rect offered to the recognizer as a fallback anchor. */
    val seededBarRect: Rect? = null,
    val reasonCodes: List<String> = emptyList(),
    val lookupMs: Long? = null,
    val validationMs: Long? = null
) {
    val hasHint: Boolean get() = seededBarRect != null

    /** Slim per-frame input for the recognizer: only what a seeded fallback needs. */
    fun toHint(): FrameCalibrationHint? = seededBarRect?.let { seed ->
        signatureKey?.let { key ->
            schemaRevision?.let { revision -> FrameCalibrationHint(key, revision, seed) }
        }
    }
}

/** Calibrated bar fallback handed to the recognizer; never carries sensitive data. */
data class FrameCalibrationHint(
    val signatureKey: String,
    val schemaRevision: Int,
    val seededBarRect: Rect
)

/** Typed per-frame calibration diagnostics attached to [FrameDiagnostic]; never sensitive. */
data class CalibrationDiagnostic(
    val signatureKey: String?,
    val schemaRevision: Int?,
    val resolution: String,
    val provenance: String,
    val barSource: String?,
    val reasonCodes: List<String>,
    val lookupMs: Long?,
    val validationMs: Long?
) {
    companion object {
        const val PROVENANCE_PERSISTED = "persisted"
        const val PROVENANCE_REBUILT = "rebuilt-this-frame"
        const val PROVENANCE_NONE = "none"
        const val BAR_SOURCE_LIVE = "live"
        const val BAR_SOURCE_CALIBRATED = "calibrated-fallback"
    }
}
