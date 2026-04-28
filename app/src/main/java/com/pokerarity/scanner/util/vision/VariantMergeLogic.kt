package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.VisualFeatures

/**
 * Pure-logic extraction of variant merge decisions.
 * No Android Context dependency — testable via JVM JUnit.
 */
object VariantMergeLogic {

    private const val CLASSIFIER_VARIANT_CONFIDENCE = 0.66f
    private const val CLASSIFIER_VARIANT_CONFIDENCE_SPECIES = 0.52f
    private const val CLASSIFIER_FORM_CONFIDENCE_SPECIES = 0.34f
    private const val CLASSIFIER_BASE_SHINY_CONFIDENCE = 0.80f
    private const val CLASSIFIER_NON_VISUAL_SHINY_CONFIDENCE = 0.60f
    private const val CLASSIFIER_FORM_PROMOTION_CONFIDENCE_SPECIES = 0.52f
    private const val CLASSIFIER_NON_VISUAL_COSTUME_CONFIDENCE = 0.60f
    private const val CLASSIFIER_SHINY_PEER_MARGIN = 0.06f
    private const val CLASSIFIER_SHINY_PEER_MIN_GAP = 0.03f
    private const val CLASSIFIER_COSTUME_SHINY_PEER_MARGIN = 0.08f
    private const val CLASSIFIER_COSTUME_SHINY_PEER_MIN_GAP = 0.02f
    private const val CLASSIFIER_BASE_RESCUE_MARGIN = 0.015f
    private const val FULL_MATCH_BASE_SHINY_CONFIDENCE = 0.78f
    private const val FULL_MATCH_NON_BASE_SHINY_CONFIDENCE = 0.70f
    private const val FULL_MATCH_COSTUME_CONFIDENCE = 0.68f
    private const val FULL_MATCH_FORM_CONFIDENCE = 0.60f
    private const val FULL_MATCH_FALLBACK_SUPPORT_CONFIDENCE = 0.60f
    private const val FULL_MATCH_GENERIC_COSTUME_FALLBACK_CONFIDENCE = 0.50f
    private const val FULL_MATCH_SHINY_COSTUME_FALLBACK_CONFIDENCE = 0.32f
    private const val FULL_MATCH_GLOBAL_SAME_SPECIES_COSTUME_CONFIDENCE = 0.32f
    private const val FULL_MATCH_BASE_SHINY_FALLBACK_CONFIDENCE = 0.52f
    private const val FULL_MATCH_VISUAL_COSTUME_FALLBACK_CONFIDENCE = 0.40f
    private const val FULL_MATCH_FORM_FALLBACK_CONFIDENCE = 0.55f
    private const val FULL_MATCH_FORM_SHINY_FALLBACK_CONFIDENCE = 0.44f
    private const val DIRECT_SPECIES_COSTUME_MIN_CONFIDENCE = 0.38f
    private const val DIRECT_SPECIES_COSTUME_BASE_GAP = 0.03f

    fun mergeVisualFeatures(
        visualFeatures: VisualFeatures,
        fullMatch: FullVariantMatch?,
        fallbackMatch: VariantPrototypeClassifier.MatchResult?
    ): VisualFeatures {
        if (fullMatch != null) {
            val fallbackRescueSupport =
                fallbackMatch != null &&
                    fallbackMatch.confidence >= CLASSIFIER_VARIANT_CONFIDENCE_SPECIES &&
                    fallbackMatch.rescueKind in setOf(
                        "exact_non_base_consensus",
                        "same_species_shiny_costume_rescue"
                    )
            val sameSpeciesFallback =
                fallbackMatch != null &&
                    fallbackMatch.species.equals(fullMatch.finalSpecies, ignoreCase = true) &&
                    fallbackMatch.variantType != "base"
            val genericSpeciesOnly = fullMatch.explanationMode == "generic_species_only"
            val fallbackWinsOverBase =
                fallbackMatch?.bestBaseScore?.let { baseScore ->
                    fallbackMatch.score + 0.03f < baseScore
                } == true
            val shinyCostumeFallbackSupport =
                sameSpeciesFallback &&
                    fallbackMatch?.isCostumeLike == true &&
                    fallbackMatch.isShiny &&
                    fallbackMatch.confidence >= FULL_MATCH_SHINY_COSTUME_FALLBACK_CONFIDENCE
            val regularCostumeShinyPeerSupport =
                sameSpeciesFallback &&
                    fallbackMatch?.isCostumeLike == true &&
                    !fallbackMatch.isShiny &&
                    fallbackMatch.bestShinyPeerScore != null &&
                    (fallbackMatch.bestShinyPeerScore - fallbackMatch.score) in
                        CLASSIFIER_COSTUME_SHINY_PEER_MIN_GAP..CLASSIFIER_COSTUME_SHINY_PEER_MARGIN &&
                    fallbackMatch.confidence >= FULL_MATCH_SHINY_COSTUME_FALLBACK_CONFIDENCE
            val sameSpeciesGlobalCostumeSupport =
                sameSpeciesFallback &&
                    fallbackMatch?.scope == "global" &&
                    fallbackMatch.isCostumeLike &&
                    fallbackMatch.confidence >= FULL_MATCH_GLOBAL_SAME_SPECIES_COSTUME_CONFIDENCE
            val fallbackCostumeSupport =
                sameSpeciesFallback &&
                    fallbackMatch?.isCostumeLike == true &&
                    (
                        shinyCostumeFallbackSupport ||
                        regularCostumeShinyPeerSupport ||
                        sameSpeciesGlobalCostumeSupport ||
                        fallbackMatch.confidence >= FULL_MATCH_FALLBACK_SUPPORT_CONFIDENCE ||
                            fallbackRescueSupport ||
                            fallbackWinsOverBase ||
                            (visualFeatures.hasCostume && fallbackMatch.confidence >= FULL_MATCH_VISUAL_COSTUME_FALLBACK_CONFIDENCE) ||
                            (genericSpeciesOnly && fallbackMatch.confidence >= FULL_MATCH_GENERIC_COSTUME_FALLBACK_CONFIDENCE)
                    )
            val fallbackFormSupport =
                sameSpeciesFallback &&
                    fallbackMatch?.variantType == "form" &&
                    (
                        fallbackMatch.confidence >= FULL_MATCH_FALLBACK_SUPPORT_CONFIDENCE ||
                            (visualFeatures.hasSpecialForm && fallbackMatch.confidence >= CLASSIFIER_FORM_CONFIDENCE_SPECIES) ||
                            fallbackMatch.confidence >= FULL_MATCH_FORM_FALLBACK_CONFIDENCE ||
                            (fallbackMatch.isShiny && fallbackMatch.confidence >= FULL_MATCH_FORM_SHINY_FALLBACK_CONFIDENCE)
                    )
            val sameSpeciesBaseShinySupport =
                fallbackMatch != null &&
                    fallbackMatch.species.equals(fullMatch.finalSpecies, ignoreCase = true) &&
                    fallbackMatch.variantType == "base" &&
                    fallbackMatch.isShiny &&
                    fallbackMatch.confidence >= FULL_MATCH_BASE_SHINY_FALLBACK_CONFIDENCE
            val directSpeciesCostumeSupport =
                sameSpeciesFallback &&
                    fallbackMatch?.variantType == "costume" &&
                    fallbackMatch.isCostumeLike &&
                    fallbackMatch.confidence >= DIRECT_SPECIES_COSTUME_MIN_CONFIDENCE &&
                    fallbackMatch.bestBaseScore?.let { baseScore ->
                        fallbackMatch.score + DIRECT_SPECIES_COSTUME_BASE_GAP < baseScore
                    } == true
            val sameSpeciesFallbackSupport =
                fallbackCostumeSupport ||
                    directSpeciesCostumeSupport ||
                    fallbackFormSupport ||
                    sameSpeciesBaseShinySupport ||
                    (
                        sameSpeciesFallback &&
                            (fallbackMatch?.confidence ?: 0f) >= FULL_MATCH_FALLBACK_SUPPORT_CONFIDENCE
                    )
            val promoteShiny = when {
                regularCostumeShinyPeerSupport -> true
                sameSpeciesFallbackSupport && fallbackMatch?.isShiny == true -> true
                !fullMatch.resolvedShiny -> false
                visualFeatures.isShiny -> true
                fullMatch.resolvedVariantClass == "base" ->
                    fullMatch.shinyConfidence >= FULL_MATCH_BASE_SHINY_CONFIDENCE &&
                        fullMatch.explanationMode != "generic_species_only"
                else -> fullMatch.shinyConfidence >= FULL_MATCH_NON_BASE_SHINY_CONFIDENCE
            }
            val promoteCostume = when {
                sameSpeciesFallbackSupport && fallbackMatch?.isCostumeLike == true -> true
                !fullMatch.resolvedCostume -> false
                visualFeatures.hasCostume -> true
                else ->
                    fullMatch.variantConfidence >= FULL_MATCH_COSTUME_CONFIDENCE &&
                        fullMatch.explanationMode != "generic_species_only"
            }
            val promoteForm = when {
                sameSpeciesFallbackSupport && fallbackMatch?.variantType == "form" -> true
                !fullMatch.resolvedForm -> false
                visualFeatures.hasSpecialForm -> true
                else ->
                    fullMatch.variantConfidence >= FULL_MATCH_FORM_CONFIDENCE &&
                        fullMatch.explanationMode != "generic_species_only"
            }
            return visualFeatures.copy(
                isShiny = promoteShiny || visualFeatures.isShiny,
                hasCostume = promoteCostume || visualFeatures.hasCostume,
                hasSpecialForm = promoteForm || visualFeatures.hasSpecialForm,
                confidence = maxOf(
                    visualFeatures.confidence,
                    fullMatch.variantConfidence,
                    fullMatch.shinyConfidence
                )
            )
        }
        val match = fallbackMatch
        if (match == null) {
            return visualFeatures
        }
        val requiredConfidence = if (match.scope == "species") {
            CLASSIFIER_VARIANT_CONFIDENCE_SPECIES
        } else {
            CLASSIFIER_VARIANT_CONFIDENCE
        }
        val formConfidenceGate = if (match.scope == "species") {
            CLASSIFIER_FORM_CONFIDENCE_SPECIES
        } else {
            requiredConfidence
        }
        val promoteCostumeBySpeciesRescue =
            match.scope == "species" &&
                match.variantType == "costume" &&
                match.isCostumeLike &&
                match.confidence >= DIRECT_SPECIES_COSTUME_MIN_CONFIDENCE &&
                match.bestBaseScore != null &&
                match.score + DIRECT_SPECIES_COSTUME_BASE_GAP < match.bestBaseScore
        val promoteCostumeByNearTieRescue =
            match.scope == "species" &&
                match.variantType == "costume" &&
                match.isCostumeLike &&
                match.confidence >= 0.47f &&
                match.bestBaseScore != null &&
                (match.bestBaseScore - match.score) in 0f..0.015f &&
                match.bestNonBaseVariantType == "costume" &&
                match.bestNonBaseIsCostumeLike
        val hasVisualCostumeSupport = visualFeatures.hasCostume
        val promoteCostumeShinyCombo =
            (match.rescueKind == "exact_non_base_consensus" ||
                match.rescueKind == "same_species_shiny_costume_rescue") &&
                match.variantType == "costume" &&
                match.isCostumeLike &&
                match.isShiny &&
                match.confidence >= CLASSIFIER_VARIANT_CONFIDENCE_SPECIES
        val promoteForm = match.variantType == "form" && (
            visualFeatures.hasSpecialForm ||
                match.confidence >= maxOf(formConfidenceGate, CLASSIFIER_FORM_PROMOTION_CONFIDENCE_SPECIES)
            )
        if (match.confidence < requiredConfidence && !promoteForm && !promoteCostumeBySpeciesRescue && !promoteCostumeByNearTieRescue) {
            return visualFeatures
        }
        val allowClassifierOnlyCostume = when {
            promoteCostumeShinyCombo -> true
            match.scope == "species" -> match.confidence >= CLASSIFIER_NON_VISUAL_COSTUME_CONFIDENCE
            else -> match.confidence >= requiredConfidence
        }
        val promoteCostume = match.isCostumeLike && (
            promoteCostumeShinyCombo ||
                (hasVisualCostumeSupport && match.confidence >= requiredConfidence) ||
                (hasVisualCostumeSupport && (promoteCostumeBySpeciesRescue || promoteCostumeByNearTieRescue)) ||
                allowClassifierOnlyCostume
            )
        val promoteShinyBySameVariantPeer =
            !match.isShiny &&
                match.isCostumeLike &&
                hasVisualCostumeSupport &&
                match.bestShinyPeerScore != null &&
                (match.bestShinyPeerScore - match.score) in CLASSIFIER_SHINY_PEER_MIN_GAP..CLASSIFIER_SHINY_PEER_MARGIN &&
                match.confidence >= CLASSIFIER_VARIANT_CONFIDENCE_SPECIES
        val promoteShinyByBasePeerAfterCostumeSuppressed =
            !match.isShiny &&
                match.isCostumeLike &&
                !hasVisualCostumeSupport &&
                match.bestBaseScore != null &&
                (match.bestBaseScore - match.score) in 0f..CLASSIFIER_BASE_RESCUE_MARGIN &&
                match.bestBaseShinyPeerScore != null &&
                (match.bestBaseShinyPeerScore - match.bestBaseScore) in CLASSIFIER_SHINY_PEER_MIN_GAP..CLASSIFIER_SHINY_PEER_MARGIN &&
                match.confidence >= CLASSIFIER_VARIANT_CONFIDENCE_SPECIES
        val suppressVisualShiny =
            promoteCostume &&
            !match.isShiny &&
            !visualFeatures.isShiny &&
            (
                allowClassifierOnlyCostume ||
                    ((hasVisualCostumeSupport || visualFeatures.isShiny) && (match.confidence >= requiredConfidence ||
                        promoteCostumeBySpeciesRescue ||
                        promoteCostumeByNearTieRescue))
                )
        val allowClassifierShiny =
            (match.isShiny || promoteShinyBySameVariantPeer || promoteShinyByBasePeerAfterCostumeSuppressed) &&
                (
                    visualFeatures.isShiny ||
                        promoteCostumeShinyCombo ||
                        promoteShinyBySameVariantPeer ||
                        promoteShinyByBasePeerAfterCostumeSuppressed ||
                        match.confidence >= maxOf(requiredConfidence, CLASSIFIER_NON_VISUAL_SHINY_CONFIDENCE)
                    ) &&
                (
                    match.variantType != "base" ||
                        visualFeatures.isShiny ||
                        match.confidence >= CLASSIFIER_BASE_SHINY_CONFIDENCE
                )
        val allowClassifierBaseShiny =
            !match.isShiny ||
            match.variantType != "base" ||
            visualFeatures.isShiny ||
            match.confidence >= CLASSIFIER_BASE_SHINY_CONFIDENCE
        return visualFeatures.copy(
            isShiny = when {
                allowClassifierShiny && allowClassifierBaseShiny -> true
                suppressVisualShiny -> false
                else -> visualFeatures.isShiny
            },
            hasCostume = if (promoteCostume) true else visualFeatures.hasCostume,
            hasSpecialForm = if (promoteForm) true else visualFeatures.hasSpecialForm,
            confidence = maxOf(
                visualFeatures.confidence,
                if (promoteCostumeBySpeciesRescue || promoteCostumeByNearTieRescue) requiredConfidence else match.confidence
            )
        )
    }

    fun mergeVisualFeatures(
        visualFeatures: VisualFeatures,
        match: VariantPrototypeClassifier.MatchResult?
    ): VisualFeatures = mergeVisualFeatures(
        visualFeatures = visualFeatures,
        fullMatch = null,
        fallbackMatch = match
    )
}
