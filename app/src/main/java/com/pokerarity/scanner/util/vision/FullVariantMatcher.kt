package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.ReleaseWindow

object FullVariantMatcher {
    private const val LIVE_EVENT_SHINY_PRESERVE_MIN_CONFIDENCE = 0.52f
    private const val CLASSIFIER_NON_BASE_SHINY_MIN_CONFIDENCE = 0.60f
    private const val CLASSIFIER_GLOBAL_REMAP_SHINY_CONFIDENCE = 0.70f
    private const val CLASSIFIER_FORM_REMAP_SHINY_CONFIDENCE = 0.66f
    private const val CLASSIFIER_COSTUME_RESOLVE_MIN_CONFIDENCE = 0.48f
    private const val CLASSIFIER_FORM_RESOLVE_MIN_CONFIDENCE = 0.52f
    private const val GENERIC_UNRESOLVED_COSTUME_MIN_CONFIDENCE = 0.50f
    private const val EXACT_SPECIES_EVENT_OVERRIDE_MIN_CONFIDENCE = 0.60f
    private const val SPECULATIVE_COSTUME_REMAP_MIN_CONFIDENCE = 0.95f

    fun match(
        finalSpecies: String,
        candidates: List<FullVariantCandidate>
    ): FullVariantMatch {
        val filtered = candidates.filter { FullVariantConstraints.keep(it, finalSpecies) }
        val initialWinner = filtered.maxByOrNull { FullVariantScoring.rankScore(it, finalSpecies) }
        val winner = initialWinner
            ?.let { preferExactSpeciesCostumeCandidate(it, filtered, finalSpecies) }
            ?.let { demoteSpeculativeCostumeRemap(it, filtered, finalSpecies) }

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
            val suppressLowConfidenceClassifierShiny =
                winner.isShiny &&
                    winner.source.startsWith("classifier") &&
                    winner.classifierConfidence < shinyConfidenceGate(winner)
            val suppressLowConfidenceClassifierCostume =
                winner.isCostumeLike &&
                    winner.source.startsWith("classifier") &&
                    winner.classifierConfidence < CLASSIFIER_COSTUME_RESOLVE_MIN_CONFIDENCE &&
                    winner.rescueKind.isNullOrBlank()
            val suppressSpeculativeAuthoritativeRemapCostume =
                isSpeculativeWindowlessAuthoritativeRemapCostume(winner)
            val suppressAuthoritativeFamilySupportCostume =
                winner.source == "authoritative_family_costume_support"
            val suppressAuthoritativeLiveEventCostume =
                winner.source == "authoritative_live_species_event"
            val suppressWeakGenericUnresolvedShinyCostume =
                winner.isCostumeLike &&
                    winner.isShiny &&
                    winner.source.startsWith("classifier") &&
                    winner.rescueKind.isNullOrBlank() &&
                    winner.eventStart == null &&
                    winner.eventEnd == null &&
                    winner.classifierConfidence < GENERIC_UNRESOLVED_COSTUME_MIN_CONFIDENCE
            val suppressLowConfidenceClassifierForm =
                winner.variantClass == "form" &&
                    winner.source.startsWith("classifier") &&
                    winner.classifierConfidence < CLASSIFIER_FORM_RESOLVE_MIN_CONFIDENCE &&
                    winner.rescueKind.isNullOrBlank() &&
                    !hasConcreteEventWindow(winner)
            val variantIdentityCandidate = promotedShinyCandidate ?: winner
            val resolvedShiny =
                promotedShinyCandidate != null ||
                    (winner.isShiny && !suppressLowConfidenceClassifierShiny)
            val resolvedCostume =
                winner.isCostumeLike &&
                    !suppressLowConfidenceClassifierCostume &&
                    !suppressAuthoritativeLiveEventCostume &&
                    !suppressAuthoritativeFamilySupportCostume &&
                    !suppressSpeculativeAuthoritativeRemapCostume &&
                    !suppressWeakGenericUnresolvedShinyCostume
            val resolvedForm =
                when {
                    promotedShinyCandidate?.variantClass == "form" -> true
                    winner.variantClass == "form" && !suppressLowConfidenceClassifierForm -> true
                    else -> false
                }
            val resolvedVariantClass = when {
                resolvedCostume -> winner.variantClass
                resolvedForm -> if (variantIdentityCandidate.variantClass == "form") "form" else winner.variantClass
                else -> "base"
            }
            val explanationMode = when {
                suppressWeakGenericUnresolvedShinyCostume -> "generic_species_only"
                suppressLowConfidenceClassifierCostume -> "generic_species_only"
                suppressAuthoritativeLiveEventCostume -> "generic_species_only"
                suppressAuthoritativeFamilySupportCostume -> "generic_species_only"
                suppressSpeculativeAuthoritativeRemapCostume -> "generic_species_only"
                suppressLowConfidenceClassifierForm -> "generic_species_only"
                winner.source == "authoritative_species_date" -> "derived_authoritative"
                winner.rescueKind.isNullOrBlank() && winner.eventLabel != null && hasConcreteEventWindow(winner) -> "exact_authoritative"
                winner.variantClass != "base" -> "generic_variant"
                else -> "generic_species_only"
            }
            val resolvedEventLabel = when {
                suppressSpeculativeAuthoritativeRemapCostume -> null
                suppressAuthoritativeLiveEventCostume -> null
                suppressAuthoritativeFamilySupportCostume -> null
                winner.eventLabel.isNullOrBlank() -> null
                hasConcreteEventWindow(winner) -> winner.eventLabel
                winner.source == "authoritative_species_date" -> winner.eventLabel
                else -> null
            }
            FullVariantMatch(
                finalSpecies = finalSpecies,
                finalSpriteKey = promotedShinyCandidate?.spriteKey ?: winner.spriteKey,
                resolvedVariantClass = resolvedVariantClass,
                resolvedShiny = resolvedShiny,
                resolvedCostume = resolvedCostume,
                resolvedForm = resolvedForm,
                resolvedEventLabel = resolvedEventLabel,
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
                eventConfidence = if (resolvedEventLabel != null) 0.8f else 0f,
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
        if (winner.source == "authoritative_species_date") {
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

    private fun demoteSpeculativeCostumeRemap(
        winner: FullVariantCandidate,
        candidates: List<FullVariantCandidate>,
        finalSpecies: String
    ): FullVariantCandidate {
        if (winner.source == "authoritative_family_costume_support" ||
            winner.source == "authoritative_live_species_event"
        ) {
            return findBestBaseFallback(candidates, finalSpecies) ?: winner
        }
        if (!isSpeculativeWindowlessAuthoritativeRemapCostume(winner)) {
            return winner
        }

        return findBestBaseFallback(candidates, finalSpecies) ?: winner
    }

    private fun findBestBaseFallback(
        candidates: List<FullVariantCandidate>,
        finalSpecies: String
    ): FullVariantCandidate? {
        return candidates
            .asSequence()
            .filter { candidate ->
                candidate.species.equals(finalSpecies, ignoreCase = true) &&
                    candidate.variantClass == "base"
            }
            .maxByOrNull { FullVariantScoring.rankScore(it, finalSpecies) }
    }

    private fun isSpeculativeWindowlessAuthoritativeRemapCostume(candidate: FullVariantCandidate): Boolean {
        return candidate.isCostumeLike &&
            candidate.variantClass == "costume" &&
            (candidate.source.endsWith("authoritative_remap") || candidate.source == "authoritative_family_costume_support") &&
            !hasConcreteEventWindow(candidate) &&
            candidate.classifierConfidence < SPECULATIVE_COSTUME_REMAP_MIN_CONFIDENCE
    }

    private fun shinyConfidenceGate(winner: FullVariantCandidate): Float {
        return when {
            winner.rescueKind == "exact_non_base_consensus" &&
                winner.source.endsWith("authoritative_remap") &&
                winner.variantClass == "form" &&
                !winner.isCostumeLike -> 0.50f
            winner.rescueKind == "exact_non_base_consensus" ||
            winner.rescueKind == "same_species_shiny_costume_rescue" ||
            winner.rescueKind == "family_costume_rescue" ||
            winner.rescueKind == "same_family_non_base_rescue" -> 0.60f
            winner.source == "classifier_species_secondary_authoritative_remap" -> 0.35f
            winner.source == "classifier_global_authoritative_remap" -> CLASSIFIER_GLOBAL_REMAP_SHINY_CONFIDENCE
            winner.source.endsWith("authoritative_remap") &&
                winner.variantClass == "form" &&
                !winner.isCostumeLike -> CLASSIFIER_FORM_REMAP_SHINY_CONFIDENCE
            winner.variantClass == "base" -> 0.78f
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
