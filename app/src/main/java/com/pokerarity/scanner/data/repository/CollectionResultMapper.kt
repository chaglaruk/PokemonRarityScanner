package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.CollectionResult
import com.pokerarity.scanner.data.model.CollectionTier
import com.pokerarity.scanner.data.model.RarityAxisScore
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.encodeExplanationItem

object CollectionResultMapper {
    fun toRarityScore(result: CollectionResult): RarityScore {
        val breakdown = result.axes.associate { it.axis.label to it.score }
        val explanations = buildList {
            result.axes.forEach { axis ->
                axis.details.forEach { detail ->
                    add(encodeExplanationItem(axis.axis.label, detail))
                }
            }
            result.eventDateMismatchMessage?.let {
                add(encodeExplanationItem("Event date mismatch", it))
            }
            result.trophySignals.forEach { signal ->
                add(encodeExplanationItem("Trophy signal", signal))
            }
        }
        return RarityScore(
            totalScore = result.totalScore,
            tier = result.tier.toLegacyTier(),
            breakdown = breakdown,
            explanation = explanations.ifEmpty { listOf("No collection score signals detected") },
            axes = result.axes.map {
                RarityAxisScore(
                    key = it.axis.key,
                    label = it.axis.label,
                    score = it.score,
                    maxScore = it.maxScore,
                    details = it.details
                )
            },
            confidence = 1.0f,
            collectionResult = result
        )
    }

    private fun CollectionTier.toLegacyTier(): RarityTier = when (this) {
        CollectionTier.COMMON -> RarityTier.COMMON
        CollectionTier.UNCOMMON -> RarityTier.UNCOMMON
        CollectionTier.NOTABLE -> RarityTier.RARE
        CollectionTier.RARE -> RarityTier.EPIC
        CollectionTier.VERY_RARE -> RarityTier.LEGENDARY
        CollectionTier.ULTRA_RARE -> RarityTier.MYTHICAL
        CollectionTier.TROPHY -> RarityTier.GOD_TIER
    }
}
