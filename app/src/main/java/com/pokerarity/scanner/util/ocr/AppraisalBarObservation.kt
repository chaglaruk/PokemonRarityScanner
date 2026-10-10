package com.pokerarity.scanner.util.ocr

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Phase 3D typed appraisal models (plan §7.5), following the Phase 3C pattern:
 * stage A records what the pixels establish (a normalized fill endpoint on the
 * stat bar track), stage B interprets it into the legal IV domain 0..15 ONLY
 * through an evidence-backed mapping.
 *
 * MEASUREMENT STATUS: real Samsung panel pixels now support diagnostic geometry
 * validation. Independent IV labels and a calibrated fill-to-IV mapping are absent.
 * Production IV interpretation remains DISABLED; fractional endpoints stay diagnostic.
 *
 * Identity safety: appraisal evidence may never create species identity. It can
 * only filter IV tuples of an already plausible profile via the Phase 3B seam.
 */

/** The three appraisal stat dimensions; each keeps its own evidence state. */
enum class AppraisalStat(val diagnosticName: String) {
    ATTACK("AppraisalAttack"),
    DEFENSE("AppraisalDefense"),
    STAMINA("AppraisalStamina");

    /** The anchor-derived crop field this stat reads. */
    val field: ScreenField
        get() = when (this) {
            ATTACK -> ScreenField.AppraisalAttack
            DEFENSE -> ScreenField.AppraisalDefense
            STAMINA -> ScreenField.AppraisalStamina
        }
}

/**
 * Stage A: geometric reading of ONE stat bar band. [endpoint] is the normalized
 * fill boundary along the measured track (0 = track start, 1 = track end); the
 * interval carries the divider/antialiasing uncertainty instead of a forced scalar.
 * No IV semantics exist at this stage.
 */
data class AppraisalBarObservation(
    val readings: Map<AppraisalStat, AppraisalStatObservation>,
    val reasonCodes: List<String>
)

data class AppraisalStatObservation(
    val endpoint: EndpointState,
    /** Fraction of the measured track that is colored fill (0..1); null when unfitted. */
    val fillFraction: Float?,
    val reasonCodes: List<String>
) {
    sealed interface EndpointState {
        /** Single plausible fill-boundary interval (divider-tolerant). */
        data class Interval(val min: Double, val max: Double) : EndpointState

        /** The bar track or fill could not be measured credibly. */
        data class Unknown(val reasonCode: String) : EndpointState

        /** Appraisal geometry not usable for this band (missing/fallback crop). */
        data class Unsupported(val reasonCode: String) : EndpointState
    }
}

/**
 * Stage B: typed IV evidence for ONE stat, always inside the legal domain 0..15.
 * Exact only when the mapping genuinely supports a single value; boundary-straddling
 * endpoints stay Range/Alternatives — never rounded for coverage.
 */
sealed interface AppraisalIvEvidence {
    val reasonCodes: List<String>

    data class Exact(val iv: Int, override val reasonCodes: List<String>) : AppraisalIvEvidence

    data class Range(
        val minIv: Int,
        val maxIv: Int,
        override val reasonCodes: List<String>
    ) : AppraisalIvEvidence

    data class Alternatives(
        val values: List<Int>,
        override val reasonCodes: List<String>
    ) : AppraisalIvEvidence

    /** No credible IV interpretation (mapping unestablished, weak signal). */
    data class Unknown(override val reasonCodes: List<String>) : AppraisalIvEvidence

    /** Appraisal evidence not supported for this frame/stat. */
    data class Unsupported(override val reasonCodes: List<String>) : AppraisalIvEvidence

    /** Independent credible appraisal observations disagree; never silently resolved. */
    data class Conflict(override val reasonCodes: List<String>) : AppraisalIvEvidence
}

/** Per-stat Stage B panel evidence; each stat keeps its own state. */
data class AppraisalPanelIvEvidence(
    val attack: AppraisalIvEvidence,
    val defense: AppraisalIvEvidence,
    val stamina: AppraisalIvEvidence
)

/**
 * Stage B seam: fill-endpoint → IV-window interpretation. Production Phase 3D passes
 * NO mapper (no measured fill→IV mapping exists), so every stat stays
 * [AppraisalIvEvidence.Unknown]. Tests and a future measured slice may supply a
 * mapper returning legal sub-windows of 0..15; a mapper that returns nothing for an
 * endpoint keeps that stat Unknown rather than guessing.
 */
internal object AppraisalIvInterpreter {

    private const val MIN_LEGAL_IV = 0
    private const val MAX_LEGAL_IV = 15

    fun interface FillToIvMapper {
        /**
         * Maps a normalized endpoint interval to legal IV sub-windows of 0..15, or
         * null when the endpoint cannot be interpreted under the measured mapping.
         */
        fun ivWindows(endpoint: ClosedFloatingPointRange<Double>): List<ClosedFloatingPointRange<Double>>?
    }

    fun interpretStat(
        observation: AppraisalStatObservation?,
        mapper: FillToIvMapper?
    ): AppraisalIvEvidence = when {
        observation == null -> AppraisalIvEvidence.Unsupported(listOf("appraisal_observation_absent"))
        observation.endpoint is AppraisalStatObservation.EndpointState.Unsupported ->
            AppraisalIvEvidence.Unsupported(observation.reasonCodes + observation.endpoint.reasonCode)
        observation.endpoint is AppraisalStatObservation.EndpointState.Unknown ->
            AppraisalIvEvidence.Unknown(observation.reasonCodes + observation.endpoint.reasonCode)
        mapper == null -> AppraisalIvEvidence.Unknown(
            observation.reasonCodes + "appraisal_iv_mapping_unestablished"
        )
        else -> interpretMapped(observation, mapper)
    }

    private fun interpretMapped(
        observation: AppraisalStatObservation,
        mapper: FillToIvMapper
    ): AppraisalIvEvidence {
        val endpoint = observation.endpoint as AppraisalStatObservation.EndpointState.Interval
        if (invalidEndpoint(endpoint)) {
            return AppraisalIvEvidence.Unknown(listOf("appraisal_endpoint_invalid"))
        }
        val mapped = mapper.ivWindows(endpoint.min..endpoint.max).orEmpty()
        return if (mapped.any(::invalidWindow)) AppraisalIvEvidence.Unknown(listOf("appraisal_mapping_invalid"))
        else classifyWindows(mapped, observation)
    }

    private fun classifyWindows(mapped: List<ClosedFloatingPointRange<Double>>,
        observation: AppraisalStatObservation): AppraisalIvEvidence {
        val windows = mapped
            .map { window -> ceil(window.start).toInt()..floor(window.endInclusive).toInt() }
            .filterNot { it.isEmpty() }
            .distinct()
            .sortedBy { window -> window.first }
        val values = windows.flatMap { window -> window.toList() }.distinct().sorted()
        return when {
            values.isEmpty() -> AppraisalIvEvidence.Unknown(
                observation.reasonCodes + "appraisal_mapping_refused_window")
            windows.size == 1 && windows.single().first == windows.single().last ->
                AppraisalIvEvidence.Exact(windows.single().first,
                    observation.reasonCodes + "appraisal_mapping_applied")
            windows.size == 1 -> AppraisalIvEvidence.Range(
                windows.single().first, windows.single().last,
                observation.reasonCodes + "appraisal_mapping_applied")
            else -> AppraisalIvEvidence.Alternatives(
                values, observation.reasonCodes + "appraisal_mapping_ambiguous")
        }
    }

    private fun invalidEndpoint(endpoint: AppraisalStatObservation.EndpointState.Interval): Boolean {
        val invalidNumbers = !endpoint.min.isFinite() || !endpoint.max.isFinite()
        val invalidBounds = endpoint.min < 0.0 || endpoint.max > 1.0 || endpoint.min > endpoint.max
        return invalidNumbers || invalidBounds
    }

    private fun invalidWindow(window: ClosedFloatingPointRange<Double>): Boolean =
        !window.start.isFinite() || !window.endInclusive.isFinite() ||
            window.start < MIN_LEGAL_IV || window.endInclusive > MAX_LEGAL_IV || window.isEmpty()
}
