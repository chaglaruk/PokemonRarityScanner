package com.pokerarity.scanner.util.ocr

/**
 * Diagnostic temporal seam. Callers must supply distinct frames of the SAME panel and
 * capture ownership. No production caller or measured IV mapping is enabled yet.
 * Disjoint endpoints may be animation/occlusion; they never manufacture an exact IV.
 */
internal object AppraisalBarStability {
    fun combine(observations: List<AppraisalBarObservation>): AppraisalBarObservation =
        AppraisalBarObservation(AppraisalStat.entries.associateWith { stat ->
            stableStat(observations.map { it.readings[stat] })
        }, listOf("appraisal_temporal_diagnostic"))

    private fun stableStat(samples: List<AppraisalStatObservation?>): AppraisalStatObservation {
        val intervals = samples.mapNotNull { it?.endpoint as? AppraisalStatObservation.EndpointState.Interval }
        val reason = when {
            samples.size < 2 -> "appraisal_stability_unobserved"
            intervals.size != samples.size -> "appraisal_stat_occluded_or_unsupported"
            intervals.any { !it.min.isFinite() || !it.max.isFinite() || it.min < 0 || it.max > 1 || it.min > it.max } ->
                "appraisal_endpoint_invalid"
            intervals.maxOf { it.min } > intervals.minOf { it.max } -> "appraisal_endpoint_unstable"
            else -> null
        }
        return if (reason != null) AppraisalStatObservation(
            AppraisalStatObservation.EndpointState.Unknown(reason), null, listOf(reason))
        else AppraisalStatObservation(AppraisalStatObservation.EndpointState.Interval(
            intervals.minOf { it.min }, intervals.maxOf { it.max }), null, listOf("appraisal_endpoint_stable_union"))
    }
}
