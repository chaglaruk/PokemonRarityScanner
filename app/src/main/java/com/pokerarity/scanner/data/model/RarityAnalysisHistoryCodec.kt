package com.pokerarity.scanner.data.model

import com.google.gson.Gson

/** Bounded local serialization of user-facing rarity reasons and their score breakdown. */
object RarityAnalysisHistoryCodec {
    private const val MAX_ITEMS = 16
    private const val MAX_TEXT_LENGTH = 240
    private const val MAX_BREAKDOWN_KEY_LENGTH = 80
    private const val MAX_ENCODED_LENGTH = 8_192

    private val gson = Gson()

    private data class BreakdownEntry(val key: String?, val points: Int?)
    private data class Payload(
        val explanations: List<String?>?,
        val breakdown: List<BreakdownEntry?>?
    )

    data class Snapshot(
        val explanations: List<String>,
        val breakdown: Map<String, Int>
    )

    fun encode(explanations: List<String>, breakdown: Map<String, Int>): String? {
        val safeExplanations = explanations
            .asSequence()
            .map { it.take(MAX_TEXT_LENGTH).trim() }
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_ITEMS)
            .toList()
        val safeBreakdown = linkedMapOf<String, Int>()
        breakdown.entries.take(MAX_ITEMS).forEach { entry ->
            val key = entry.key.take(MAX_BREAKDOWN_KEY_LENGTH).trim()
            if (key.isNotBlank() && key !in safeBreakdown) safeBreakdown[key] = entry.value
        }
        if (safeExplanations.isEmpty() && safeBreakdown.isEmpty()) return null

        return gson.toJson(
            Payload(
                explanations = safeExplanations,
                breakdown = safeBreakdown.map { (key, points) -> BreakdownEntry(key, points) }
            )
        ).takeIf { it.length <= MAX_ENCODED_LENGTH }
    }

    fun decode(encoded: String?): Snapshot? = encoded
        ?.takeIf { it.isNotBlank() && it.length <= MAX_ENCODED_LENGTH }
        ?.let(::parseSnapshot)

    private fun parseSnapshot(encoded: String): Snapshot? {
        val payload = runCatching { gson.fromJson(encoded, Payload::class.java) }.getOrNull() ?: return null
        val explanations = payload.explanations.orEmpty()
            .asSequence()
            .filterNotNull()
            .map { it.take(MAX_TEXT_LENGTH).trim() }
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_ITEMS)
            .toList()
        val breakdown = linkedMapOf<String, Int>()
        payload.breakdown.orEmpty().asSequence()
            .filterNotNull()
            .take(MAX_ITEMS)
            .forEach { entry ->
                val key = entry.key?.take(MAX_BREAKDOWN_KEY_LENGTH)?.trim().orEmpty()
                val points = entry.points
                if (key.isNotBlank() && points != null && key !in breakdown) breakdown[key] = points
            }
        return if (explanations.isEmpty() && breakdown.isEmpty()) null else Snapshot(explanations, breakdown)
    }
}
