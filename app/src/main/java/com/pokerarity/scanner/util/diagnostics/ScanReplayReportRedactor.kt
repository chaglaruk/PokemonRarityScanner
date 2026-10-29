package com.pokerarity.scanner.util.diagnostics

import kotlin.math.ceil

/** Projects replay rows onto aggregate fields safe for routine local report artifacts. */
internal object ScanReplayReportRedactor {
    private val allowedOutcomes = setOf(
        "ACCEPT",
        "ACCEPT_LOW_CONFIDENCE",
        "UNCERTAIN",
        "RETRY",
        "REJECT_NOT_POKEMON_SCREEN",
        "REJECT_NON_DETAIL",
        "RETRY_UNSTABLE",
        "RETRY_UNKNOWN",
        "PROCEED_DETAIL",
        "PokemonDetail",
        "PokemonDetailScrolled",
        "Appraisal",
        "StorageList",
        "Encounter",
        "Transition",
        "Unknown",
        "COMPLETE",
        "STOPPED_BEFORE_CONFIDENCE",
        "ERROR",
        "CALIBRATION_AVAILABLE",
        "CALIBRATION_UNAVAILABLE"
    )

    fun aggregate(rows: List<Map<String, Any?>>): Map<String, Any?> {
        val outcomes = rows.map { normalizeOutcome(it["outcome"]) }
        val outcomeCounts = outcomes.groupingBy { it }.eachCount().toSortedMap()
        val latencies = rows.mapNotNull { row ->
            (row["totalLatencyMs"] as? Number)?.toLong()?.takeIf { it >= 0L }
        }.sorted()

        return linkedMapOf(
            "frameCount" to rows.size,
            "outcomeCounts" to outcomeCounts,
            "errorCount" to outcomes.count { it == "ERROR" },
            "totalLatencyMs" to linkedMapOf(
                "p50" to percentile(latencies, P50),
                "p95" to percentile(latencies, P95)
            )
        )
    }

    private fun normalizeOutcome(value: Any?): String =
        (value as? String)?.takeIf { it in allowedOutcomes } ?: "OTHER"

    private const val P50 = 0.50
    private const val P95 = 0.95

    private fun percentile(sortedValues: List<Long>, percentile: Double): Long? {
        if (sortedValues.isEmpty()) return null
        val nearestRank = ceil(percentile * sortedValues.size).toInt().coerceIn(1, sortedValues.size)
        return sortedValues[nearestRank - 1]
    }
}
