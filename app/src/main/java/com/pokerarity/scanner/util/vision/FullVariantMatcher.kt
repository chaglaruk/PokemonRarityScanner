package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow

object FullVariantMatcher {
    private const val LIVE_EVENT_SHINY_PRESERVE_MIN_CONFIDENCE = 0.52f
    private const val CLASSIFIER_NON_BASE_SHINY_MIN_CONFIDENCE = 0.60f
    private const val CLASSIFIER_GLOBAL_REMAP_SHINY_CONFIDENCE = 0.70f
    private const val CLASSIFIER_FORM_REMAP_SHINY_CONFIDENCE = 0.66f
    private const val CLASSIFIER_COSTUME_RESOLVE_MIN_CONFIDENCE = 0.35f
    private const val GENERIC_UNRESOLVED_COSTUME_MIN_CONFIDENCE = 0.30f
    private const val EXACT_SPECIES_EVENT_OVERRIDE_MIN_CONFIDENCE = 0.42f

    fun match(
        finalSpecies: String,
        candidates: List<FullVariantCandidate>
    ): FullVariantMatch {
        val filtered = candidates.filter { FullVariantConstraints.keep(it, finalSpecies) }
        val initialWinner = filtered.maxByOrNull { FullVariantScoring.rankScore(it, finalSpecies) }
        val winner = initialWinner?.let { preferExactSpeciesCostumeCandidate(it, filtered, finalSpecies) }

        return if (winner == null) {
            FullVariantMatch(
                finalSpecies = finalSpecies,
                explanationMode = "generic_species_only",
                candidates = candidates,
                debugSummary = "no eligible candidates"
            )
        } else {
            val promotedShinyCandidate = findLiveEventShinyPromotion(
                winner = winner,
                finalSpecies = finalSpecies,
                candidates = filtered
            )
            // FIX: Removed inverted suppression logic that was blocking valid classified shiny/costume
            // Trust classifier decisions with reasonable confidence thresholds
            val suppressLowConfidenceClassifierNonBaseShiny =
                winner.isShiny &&
                    winner.variantClass != "base" &&
                    winner.source.startsWith("classifier") &&
                    winner.classifierConfidence < shinyConfidenceGate(winner)
            val suppressLowConfidenceClassifierCostume =
                winner.isCostumeLike &&
                    winner.source.startsWith("classifier") &&
                    winner.classifierConfidence < CLASSIFIER_COSTUME_RESOLVE_MIN_CONFIDENCE &&
                    winner.rescueKind.isNullOrBlank()
            val suppressWeakGenericUnresolvedShinyCostume =
                winner.isCostumeLike &&
                    winner.isShiny &&
                    winner.source.startsWith("classifier") &&
                    winner.rescueKind.isNullOrBlank() &&
                    winner.eventStart == null &&
                    winner.eventEnd == null &&
                    winner.classifierConfidence < GENERIC_UNRESOLVED_COSTUME_MIN_CONFIDENCE
            val resolvedShiny =
                promotedShinyCandidate != null ||
                    (winner.isShiny && !suppressLowConfidenceClassifierNonBaseShiny)
            val resolvedCostume =
                winner.isCostumeLike &&
                    !suppressLowConfidenceClassifierCostume &&
                    !suppressWeakGenericUnresolvedShinyCostume
            val resolvedVariantClass = when {
                resolvedCostume -> winner.variantClass
                winner.variantClass == "form" -> "form"
                else -> "base"
            }
            val explanationMode = when {
                suppressWeakGenericUnresolvedShinyCostume -> "generic_species_only"
                suppressLowConfidenceClassifierCostume -> "generic_species_only"
                winner.source == "authoritative_species_date" -> "derived_authoritative"
                winner.source == "authoritative_live_species_event" -> "derived_authoritative"
                winner.rescueKind.isNullOrBlank() && winner.eventLabel != null && hasConcreteEventWindow(winner) -> "exact_authoritative"
                winner.variantClass != "base" -> "generic_variant"
                else -> "generic_species_only"
            }
            FullVariantMatch(
                finalSpecies = finalSpecies,
                finalSpriteKey = promotedShinyCandidate?.spriteKey ?: winner.spriteKey,
                resolvedVariantClass = resolvedVariantClass,
                resolvedShiny = resolvedShiny,
                resolvedCostume = resolvedCostume,
                resolvedForm = winner.variantClass == "form",
                resolvedEventLabel = winner.eventLabel,
                resolvedEventWindow = if (winner.eventStart != null || winner.eventEnd != null) {
                    ReleaseWindow(
                        firstSeen = winner.eventStart,
                        lastSeen = winner.eventEnd
                    )
                } else {
                    null
                },
                speciesConfidence = if (winner.species.equals(finalSpecies, ignoreCase = true)) 0.9f else 0.5f,
                variantConfidence = (1f - winner.matchScore).coerceIn(0f, 1f),
                shinyConfidence = when {
                    promotedShinyCandidate != null -> (1f - promotedShinyCandidate.matchScore).coerceIn(0f, 1f)
                    resolvedShiny -> (1f - winner.matchScore).coerceIn(0f, 1f)
                    else -> 0f
                },
                eventConfidence = if (winner.eventLabel != null) 0.8f else 0f,
                explanationMode = explanationMode,
                candidates = filtered,
                debugSummary = buildString {
                    append("winner=${winner.spriteKey}, source=${winner.source}, rescue=${winner.rescueKind.orEmpty()}")
                    promotedShinyCandidate?.let {
                        append(", shiny_promoted_from=${it.spriteKey}")
                    }
                }
            )
        }
    }

    private fun preferExactSpeciesCostumeCandidate(
        winner: FullVariantCandidate,
        candidates: List<FullVariantCandidate>,
        finalSpecies: String
    ): FullVariantCandidate {
        if (winner.source != "authoritative_species_date" && winner.source != "authoritative_live_species_event") {
            return winner
        }
        val exactCandidate = candidates
            .asSequence()
            .filter { candidate ->
                candidate.species.equals(finalSpecies, ignoreCase = true) &&
                    candidate.isCostumeLike &&
                    candidate.variantClass != "base" &&
                    !candidate.eventLabel.isNullOrBlank() &&
                    hasConcreteEventWindow(candidate) &&
                    candidate.classifierConfidence >= EXACT_SPECIES_EVENT_OVERRIDE_MIN_CONFIDENCE &&
                    candidate.spriteKey != winner.spriteKey &&
                    when (candidate.source) {
                        "classifier_species",
                        "classifier_species_authoritative_remap",
                        "classifier_species_secondary_authoritative_remap" -> true
                        else -> false
                    }
            }
            .maxByOrNull { FullVariantScoring.rankScore(it, finalSpecies) }

        return exactCandidate ?: winner
    }

    private fun hasConcreteEventWindow(candidate: FullVariantCandidate): Boolean {
        return !candidate.eventStart.isNullOrBlank() && !candidate.eventEnd.isNullOrBlank()
    }

    private fun shinyConfidenceGate(winner: FullVariantCandidate): Float {
        return when {
            winner.rescueKind == "exact_non_base_consensus" ||
            winner.rescueKind == "same_species_shiny_costume_rescue" ||
            winner.rescueKind == "family_costume_rescue" ||
            winner.rescueKind == "same_family_non_base_rescue" -> 0.50f
            winner.source == "classifier_species_secondary_authoritative_remap" -> 0.35f
            winner.source == "classifier_global_authoritative_remap" -> CLASSIFIER_GLOBAL_REMAP_SHINY_CONFIDENCE
            winner.source.endsWith("authoritative_remap") &&
                winner.variantClass == "form" &&
                !winner.isCostumeLike -> CLASSIFIER_FORM_REMAP_SHINY_CONFIDENCE
            else -> CLASSIFIER_NON_BASE_SHINY_MIN_CONFIDENCE
        }
    }

    private fun findLiveEventShinyPromotion(
        winner: FullVariantCandidate,
        finalSpecies: String,
        candidates: List<FullVariantCandidate>
    ): FullVariantCandidate? {
        if (winner.source != "authoritative_live_species_event" &&
            winner.source != "authoritative_species_date" &&
            !winner.source.endsWith("authoritative_remap")
        ) {
            return null
        }
        if (!winner.species.equals(finalSpecies, ignoreCase = true)) return null
        if (!winner.isCostumeLike) return null

        return candidates
            .asSequence()
            .filter { candidate ->
                candidate.species.equals(finalSpecies, ignoreCase = true) &&
                    candidate.source.startsWith("classifier") &&
                    candidate.variantClass != "base" &&
                    candidate.isShiny &&
                    candidate.classifierConfidence >= LIVE_EVENT_SHINY_PRESERVE_MIN_CONFIDENCE &&
                    candidate.rescueKind == "exact_non_base_consensus"
            }
            .maxByOrNull { FullVariantScoring.rankScore(it, finalSpecies) }
    }
}
