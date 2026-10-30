// Purpose: Merge visual detector and prototype-classifier variant signals safely.
package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.VisualFeatures

/**
 * Pure-logic extraction of variant merge decisions.
 * No Android Context dependency — testable via JVM JUnit.
 */
@Suppress("TooManyFunctions") // Keep the merge decision helpers beside their evidence gates.
object VariantMergeLogic {

    private const val CLASSIFIER_VARIANT_CONFIDENCE = 0.66f
    private const val CLASSIFIER_VARIANT_CONFIDENCE_SPECIES = 0.52f
    private const val CLASSIFIER_FORM_CONFIDENCE_SPECIES = 0.34f
    private const val CLASSIFIER_BASE_SHINY_CONFIDENCE = 0.80f
    private const val CLASSIFIER_BASE_SHINY_RESCUE_CONFIDENCE = 0.60f
    private const val CLASSIFIER_BASE_SHINY_RESCUE_WITHOUT_VISUAL_CONFIDENCE = 0.80f
    private const val CLASSIFIER_BASE_SHINY_RESCUE_PEER_GAP = 0.08f
    private const val CLASSIFIER_NON_VISUAL_SHINY_CONFIDENCE = 0.60f
    private const val CLASSIFIER_FORM_PROMOTION_CONFIDENCE_SPECIES = 0.52f
    private const val CLASSIFIER_NON_VISUAL_COSTUME_CONFIDENCE = 0.60f
    private const val CLASSIFIER_SHINY_PEER_MARGIN = 0.06f
    private const val CLASSIFIER_SHINY_PEER_MIN_GAP = 0.03f
    private const val CLASSIFIER_COSTUME_SHINY_PEER_MARGIN = 0.08f
    private const val CLASSIFIER_COSTUME_SHINY_PEER_MIN_GAP = 0.02f
    private const val CLASSIFIER_BASE_RESCUE_MARGIN = 0.015f
    private const val FULL_MATCH_BASE_SHINY_CONFIDENCE = 0.78f
    private const val FULL_MATCH_BASE_SHINY_WITHOUT_VISUAL_CONFIDENCE = 0.92f
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
    private const val DIRECT_SPECIES_COSTUME_BASE_GAP = 0.02f
    private const val CLASSIFIER_ONLY_SHINY_CONFIDENCE = 0.72f
    private const val CLASSIFIER_ONLY_COSTUME_CONFIDENCE = 0.82f
    private const val SAME_SPECIES_SHINY_VARIANT_CONFIDENCE = 0.42f
    private const val SAME_SPECIES_SHINY_VARIANT_BASE_GAP = 0.08f

    fun mergeVisualFeatures(
        visualFeatures: VisualFeatures,
        fullMatch: FullVariantMatch?,
        fallbackMatch: VariantPrototypeClassifier.MatchResult?
    ): VisualFeatures = mergeVisualFeatures(visualFeatures, fullMatch, fallbackMatch, null)

    @Suppress("LongMethod", "CyclomaticComplexMethod", "MaxLineLength", "ReturnCount")
    // Each promotion branch reports its own evidence gate.
    internal fun mergeVisualFeatures(
        visualFeatures: VisualFeatures,
        fullMatch: FullVariantMatch?,
        fallbackMatch: VariantPrototypeClassifier.MatchResult?,
        onDecision: ((List<QaMergeDecision>) -> Unit)?
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
            val fullMatchRejectedAllCandidates =
                fullMatch.debugSummary.equals("no eligible candidates", ignoreCase = true)
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
            val strongSameSpeciesBaseShinySupport =
                fallbackMatch != null &&
                    fallbackMatch.species.equals(fullMatch.finalSpecies, ignoreCase = true) &&
                    hasStrongBaseShinyPeerSupport(fallbackMatch)
            val veryStrongSameSpeciesBaseShinySupport =
                fallbackMatch != null &&
                    fallbackMatch.species.equals(fullMatch.finalSpecies, ignoreCase = true) &&
                    hasVeryStrongBaseShinyPeerSupport(fallbackMatch)
            val sameVariantShinyPeerSupport = fallbackMatch?.let { match ->
                if (match.bestShinyPeerScore != null) {
                    hasBoundedSameVariantShinyPeerSupport(match)
                } else {
                    fullMatch.resolvedCostume &&
                        fullMatch.explanationMode == "derived_authoritative" &&
                        fullMatch.variantConfidence >= FULL_MATCH_COSTUME_CONFIDENCE
                }
            } == true
            val sameSpeciesBaseShinySupport =
                fallbackMatch != null &&
                    fallbackMatch.species.equals(fullMatch.finalSpecies, ignoreCase = true) &&
                    fallbackMatch.variantType == "base" &&
                    fallbackMatch.isShiny &&
                    (
                        visualFeatures.isShiny ||
                            fallbackMatch.confidence >= CLASSIFIER_BASE_SHINY_CONFIDENCE ||
                            veryStrongSameSpeciesBaseShinySupport
                    )
            // A classifier-only variant shiny needs bounded same-variant peer support or trusted variant context.
            val strongSameSpeciesShinyVariantSupport =
                sameSpeciesFallback &&
                    fallbackMatch?.isShiny == true &&
                    fallbackMatch.confidence >= SAME_SPECIES_SHINY_VARIANT_CONFIDENCE &&
                    (fullMatch.resolvedCostume || fullMatch.resolvedForm || visualFeatures.isShiny) &&
                    fallbackMatch.bestBaseScore?.let { baseScore ->
                        fallbackMatch.score + SAME_SPECIES_SHINY_VARIANT_BASE_GAP < baseScore
                    } == true &&
                    sameVariantShinyPeerSupport
            val directSpeciesCostumeSupport =
                sameSpeciesFallback &&
                    !fullMatchRejectedAllCandidates &&
                    fallbackMatch?.variantType == "costume" &&
                    fallbackMatch.isCostumeLike &&
                    fallbackMatch.confidence >= DIRECT_SPECIES_COSTUME_MIN_CONFIDENCE &&
                    fallbackMatch.bestNonBaseVariantType == "costume" &&
                    fallbackMatch.bestNonBaseIsCostumeLike &&
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
                regularCostumeShinyPeerSupport -> visualFeatures.isShiny
                strongSameSpeciesBaseShinySupport ->
                    visualFeatures.isShiny || veryStrongSameSpeciesBaseShinySupport
                strongSameSpeciesShinyVariantSupport -> true
                sameSpeciesFallbackSupport && fallbackMatch?.isShiny == true ->
                    visualFeatures.isShiny
                !fullMatch.resolvedShiny -> false
                visualFeatures.isShiny -> true
                fullMatch.resolvedVariantClass == "base" ->
                    fullMatch.shinyConfidence >= FULL_MATCH_BASE_SHINY_WITHOUT_VISUAL_CONFIDENCE &&
                        fullMatch.explanationMode != "generic_species_only"
                else ->
                    fullMatch.shinyConfidence >= FULL_MATCH_NON_BASE_SHINY_CONFIDENCE &&
                        fullMatch.explanationMode in setOf("exact_authoritative", "derived_authoritative")
            }
            val promoteCostume = when {
                directSpeciesCostumeSupport -> true
                sameSpeciesFallbackSupport && fallbackMatch?.isCostumeLike == true ->
                    visualFeatures.hasCostume ||
                        fallbackMatch.confidence >= CLASSIFIER_ONLY_COSTUME_CONFIDENCE
                !fullMatch.resolvedCostume -> false
                visualFeatures.hasCostume -> true
                else ->
                    fullMatch.variantConfidence >= (
                        if (fullMatch.explanationMode == "exact_authoritative") {
                            FULL_MATCH_COSTUME_CONFIDENCE
                        } else {
                            CLASSIFIER_ONLY_COSTUME_CONFIDENCE
                        }
                    ) &&
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
            val merged = visualFeatures.copy(
                isShiny = promoteShiny || visualFeatures.isShiny,
                hasCostume = promoteCostume || visualFeatures.hasCostume,
                hasSpecialForm = promoteForm || visualFeatures.hasSpecialForm,
                confidence = maxOf(
                    visualFeatures.confidence,
                    fullMatch.variantConfidence,
                    fullMatch.shinyConfidence
                )
            )
            onDecision?.invoke(
                listOf(
                    QaMergeDecision(
                        stage = "variant_merge",
                        target = "isShiny",
                        before = visualFeatures.isShiny,
                        after = merged.isShiny,
                        action = mergeAction(visualFeatures.isShiny, merged.isShiny),
                        reasonCode = when {
                            regularCostumeShinyPeerSupport ->
                                "regular_costume_shiny_peer_preserves_visual_shiny_only"
                            strongSameSpeciesBaseShinySupport -> {
                                if (veryStrongSameSpeciesBaseShinySupport) {
                                    "strong_base_shiny_peer_support"
                                } else {
                                    "base_shiny_peer_requires_visual_or_very_strong_support"
                                }
                            }
                            strongSameSpeciesShinyVariantSupport ->
                                "same_variant_shiny_peer_and_variant_context_passed"
                            sameSpeciesFallbackSupport && fallbackMatch?.isShiny == true -> {
                                if (visualFeatures.isShiny) {
                                    "fallback_shiny_preserved_from_visual"
                                } else {
                                    "fallback_shiny_rejected_by_same_species_support_rule"
                                }
                            }
                            !fullMatch.resolvedShiny -> "full_match_resolved_shiny_false"
                            visualFeatures.isShiny -> "visual_shiny_positive_preserved"
                            fullMatch.resolvedVariantClass == "base" -> {
                                val confidencePassed = fullMatch.shinyConfidence >=
                                    FULL_MATCH_BASE_SHINY_WITHOUT_VISUAL_CONFIDENCE
                                if (confidencePassed && fullMatch.explanationMode != "generic_species_only") {
                                    "base_full_match_confidence_gate_passed"
                                } else {
                                    "base_full_match_confidence_or_explanation_gate_failed"
                                }
                            }
                            else -> {
                                val confidencePassed = fullMatch.shinyConfidence >=
                                    FULL_MATCH_NON_BASE_SHINY_CONFIDENCE
                                val authoritativeMode = fullMatch.explanationMode in
                                    setOf("exact_authoritative", "derived_authoritative")
                                if (confidencePassed && authoritativeMode) {
                                    "non_base_full_match_confidence_gate_passed"
                                } else {
                                    "non_base_full_match_confidence_or_explanation_gate_failed"
                                }
                            }
                        }
                    ),
                    QaMergeDecision(
                        stage = "variant_merge",
                        target = "hasCostume",
                        before = visualFeatures.hasCostume,
                        after = merged.hasCostume,
                        action = mergeAction(visualFeatures.hasCostume, merged.hasCostume),
                        reasonCode = when {
                            directSpeciesCostumeSupport ->
                                "direct_species_costume_score_and_base_gap_passed"
                            sameSpeciesFallbackSupport && fallbackMatch?.isCostumeLike == true -> {
                                val hasFallbackSupport = visualFeatures.hasCostume ||
                                    fallbackMatch.confidence >= CLASSIFIER_ONLY_COSTUME_CONFIDENCE
                                if (hasFallbackSupport) {
                                    "same_species_fallback_costume_support_passed"
                                } else {
                                    "fallback_costume_confidence_below_classifier_only_gate"
                                }
                            }
                            !fullMatch.resolvedCostume -> "full_match_resolved_costume_false"
                            visualFeatures.hasCostume -> "visual_costume_positive_preserved"
                            else -> {
                                val requiredConfidence = if (fullMatch.explanationMode == "exact_authoritative") {
                                    FULL_MATCH_COSTUME_CONFIDENCE
                                } else {
                                    CLASSIFIER_ONLY_COSTUME_CONFIDENCE
                                }
                                val confidencePassed = fullMatch.variantConfidence >= requiredConfidence
                                if (confidencePassed && fullMatch.explanationMode != "generic_species_only") {
                                    "full_match_costume_confidence_gate_passed"
                                } else {
                                    "full_match_costume_confidence_or_explanation_gate_failed"
                                }
                            }
                        }
                    ),
                    QaMergeDecision(
                        stage = "variant_merge",
                        target = "hasSpecialForm",
                        before = visualFeatures.hasSpecialForm,
                        after = merged.hasSpecialForm,
                        action = mergeAction(visualFeatures.hasSpecialForm, merged.hasSpecialForm),
                        reasonCode = when {
                            sameSpeciesFallbackSupport && fallbackMatch?.variantType == "form" ->
                                "same_species_fallback_form_support_passed"
                            !fullMatch.resolvedForm -> "full_match_resolved_form_false"
                            visualFeatures.hasSpecialForm -> "visual_form_positive_preserved"
                            fullMatch.variantConfidence >= FULL_MATCH_FORM_CONFIDENCE &&
                                fullMatch.explanationMode != "generic_species_only" ->
                                "full_match_form_confidence_gate_passed"
                            else -> "full_match_form_confidence_or_explanation_gate_failed"
                        }
                    )
                )
            )
            return merged
        }
        val match = fallbackMatch
        if (match == null) {
            recordNoChange(onDecision, visualFeatures, "fallback_classifier_match_missing")
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
        val strongBaseShinyPeerSupport = hasStrongBaseShinyPeerSupport(match)
        val veryStrongBaseShinyPeerSupport = hasVeryStrongBaseShinyPeerSupport(match)
        val boundedSameVariantShinyPeerSupport = hasBoundedSameVariantShinyPeerSupport(match)
        if (match.confidence < requiredConfidence &&
            !promoteForm &&
            !promoteCostumeBySpeciesRescue &&
            !promoteCostumeByNearTieRescue &&
            !veryStrongBaseShinyPeerSupport
        ) {
            recordNoChange(onDecision, visualFeatures, "fallback_confidence_below_required_and_no_rescue")
            return visualFeatures
        }
        val allowClassifierOnlyCostume = when {
            promoteCostumeShinyCombo -> match.confidence >= CLASSIFIER_ONLY_COSTUME_CONFIDENCE
            match.scope == "species" -> match.confidence >= CLASSIFIER_ONLY_COSTUME_CONFIDENCE
            hasVisualCostumeSupport -> match.confidence >= requiredConfidence
            else -> match.confidence >= CLASSIFIER_ONLY_COSTUME_CONFIDENCE
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
                visualFeatures.isShiny &&
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
        val requiredShinyConfidence = if (visualFeatures.isShiny) {
            maxOf(requiredConfidence, CLASSIFIER_NON_VISUAL_SHINY_CONFIDENCE)
        } else {
            CLASSIFIER_ONLY_SHINY_CONFIDENCE
        }
        val allowClassifierShiny =
            (match.isShiny || promoteShinyBySameVariantPeer || promoteShinyByBasePeerAfterCostumeSuppressed) &&
                (
                    visualFeatures.isShiny ||
                        (promoteCostumeShinyCombo &&
                            match.confidence >= CLASSIFIER_ONLY_SHINY_CONFIDENCE &&
                            boundedSameVariantShinyPeerSupport) ||
                        veryStrongBaseShinyPeerSupport ||
                        promoteShinyBySameVariantPeer ||
                        (promoteShinyByBasePeerAfterCostumeSuppressed && match.confidence >= CLASSIFIER_ONLY_SHINY_CONFIDENCE) ||
                        (match.confidence >= requiredShinyConfidence &&
                            (match.variantType == "base" || boundedSameVariantShinyPeerSupport))
                    ) &&
                (
                    match.variantType != "base" ||
                        visualFeatures.isShiny ||
                        veryStrongBaseShinyPeerSupport ||
                        match.confidence >= CLASSIFIER_BASE_SHINY_CONFIDENCE
                )
        val allowClassifierBaseShiny =
            !match.isShiny ||
            match.variantType != "base" ||
            visualFeatures.isShiny ||
            strongBaseShinyPeerSupport ||
            match.confidence >= CLASSIFIER_BASE_SHINY_CONFIDENCE
        val merged = visualFeatures.copy(
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
        onDecision?.invoke(
            listOf(
                    QaMergeDecision("variant_merge", "isShiny", visualFeatures.isShiny, merged.isShiny,
                        mergeAction(visualFeatures.isShiny, merged.isShiny), when {
                        visualFeatures.isShiny -> "visual_shiny_positive_preserved"
                        allowClassifierShiny && allowClassifierBaseShiny ->
                            "classifier_shiny_and_base_peer_gates_passed"
                        suppressVisualShiny -> "visual_shiny_suppressed_by_non_shiny_costume_classifier"
                        match.isShiny && !allowClassifierShiny -> "classifier_shiny_rejected_by_confidence_or_peer_gate"
                        else -> "no_shiny_promotion_condition_passed"
                    }),
                QaMergeDecision("variant_merge", "hasCostume", visualFeatures.hasCostume, merged.hasCostume,
                    mergeAction(visualFeatures.hasCostume, merged.hasCostume), when {
                        visualFeatures.hasCostume -> "visual_costume_positive_preserved"
                        !match.isCostumeLike -> "classifier_selected_non_costume_variant"
                        promoteCostumeShinyCombo -> "shiny_costume_combo_rescue_passed"
                        hasVisualCostumeSupport && match.confidence >= requiredConfidence ->
                            "visual_costume_supported_by_classifier"
                        hasVisualCostumeSupport &&
                            (promoteCostumeBySpeciesRescue || promoteCostumeByNearTieRescue) ->
                            "visual_costume_species_rescue_passed"
                        allowClassifierOnlyCostume -> "classifier_only_costume_confidence_gate_passed"
                        match.isCostumeLike -> "classifier_costume_rejected_by_confidence_or_evidence_gate"
                        else -> "costume_promotion_gate_not_reached"
                    }),
                QaMergeDecision("variant_merge", "hasSpecialForm", visualFeatures.hasSpecialForm, merged.hasSpecialForm,
                    mergeAction(visualFeatures.hasSpecialForm, merged.hasSpecialForm), when {
                        promoteForm -> "classifier_form_confidence_or_visual_gate_passed"
                        match.variantType == "form" -> "classifier_form_rejected_by_confidence_gate"
                        else -> "classifier_selected_non_form_variant"
                    })
            )
        )
        return merged
    }

    /**
     * Phase 2E species-lock boundary for variant enrichment.
     *
     * Weak/full-variant evidence may enrich flags only when it belongs to the already
     * locked canonical species. Cross-species winners/matches remain available to
     * diagnostics, but their variant flags are not applied to the locked identity.
     * Independent visual/OCR positives in [visualFeatures] are preserved.
     */
    fun mergeVisualFeaturesForLockedSpecies(
        visualFeatures: VisualFeatures,
        lockedSpecies: String?,
        fullMatch: FullVariantMatch?,
        fallbackMatch: VariantPrototypeClassifier.MatchResult?
    ): VisualFeatures = mergeVisualFeaturesForLockedSpecies(
        visualFeatures, lockedSpecies, fullMatch, fallbackMatch, null
    )

    internal fun mergeVisualFeaturesForLockedSpecies(
        visualFeatures: VisualFeatures,
        lockedSpecies: String?,
        fullMatch: FullVariantMatch?,
        fallbackMatch: VariantPrototypeClassifier.MatchResult?,
        onDecision: ((List<QaMergeDecision>) -> Unit)?
    ): VisualFeatures {
        val locked = lockedSpecies?.trim()?.takeUnless {
            it.isBlank() || it.equals("Unknown", ignoreCase = true)
        }
        if (locked == null) {
            recordNoChange(onDecision, visualFeatures, "locked_species_missing")
            return visualFeatures
        }
        val safeFullMatch = fullMatch?.takeIf { match ->
            match.finalSpecies.equals(locked, ignoreCase = true) &&
                (match.winnerSpecies == null || match.winnerSpecies.equals(locked, ignoreCase = true))
        }
        val safeFallbackMatch = fallbackMatch?.takeIf { match ->
            match.species.equals(locked, ignoreCase = true)
        }
        if (fullMatch != null && safeFullMatch == null) {
            onDecision?.invoke(rejectedForLockedSpecies(visualFeatures, "locked_species_full_match_mismatch"))
        }
        if (fallbackMatch != null && safeFallbackMatch == null) {
            onDecision?.invoke(rejectedForLockedSpecies(visualFeatures, "locked_species_classifier_mismatch"))
        }
        return mergeVisualFeatures(
            visualFeatures = visualFeatures,
            fullMatch = safeFullMatch,
            fallbackMatch = safeFallbackMatch,
            onDecision = onDecision
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

    private fun mergeAction(before: Boolean, after: Boolean): String = when {
        !before && after -> "promoted"
        before && !after -> "demoted"
        else -> "unchanged"
    }

    private fun recordNoChange(
        onDecision: ((List<QaMergeDecision>) -> Unit)?,
        features: VisualFeatures,
        reason: String
    ) {
        onDecision?.invoke(
            listOf(
                QaMergeDecision(
                    "variant_merge",
                    "isShiny",
                    features.isShiny,
                    features.isShiny,
                    "unchanged",
                    reason
                ),
                QaMergeDecision(
                    "variant_merge",
                    "hasCostume",
                    features.hasCostume,
                    features.hasCostume,
                    "unchanged",
                    reason
                ),
                QaMergeDecision(
                    "variant_merge",
                    "hasSpecialForm",
                    features.hasSpecialForm,
                    features.hasSpecialForm,
                    "unchanged",
                    reason
                )
            )
        )
    }

    private fun rejectedForLockedSpecies(
        features: VisualFeatures,
        reason: String
    ): List<QaMergeDecision> = listOf(
        QaMergeDecision(
            "variant_merge",
            "isShiny",
            features.isShiny,
            features.isShiny,
            "rejected",
            reason
        ),
        QaMergeDecision(
            "variant_merge",
            "hasCostume",
            features.hasCostume,
            features.hasCostume,
            "rejected",
            reason
        ),
        QaMergeDecision(
            "variant_merge",
            "hasSpecialForm",
            features.hasSpecialForm,
            features.hasSpecialForm,
            "rejected",
            reason
        )
    )

    private fun hasStrongBaseShinyPeerSupport(match: VariantPrototypeClassifier.MatchResult): Boolean =
        match.scope == "species" &&
            match.variantType == "base" &&
            match.isShiny &&
            match.confidence >= CLASSIFIER_BASE_SHINY_RESCUE_CONFIDENCE &&
            match.bestBaseShinyPeerScore?.let { peerScore ->
                peerScore - match.score >= CLASSIFIER_BASE_SHINY_RESCUE_PEER_GAP
            } == true

    private fun hasVeryStrongBaseShinyPeerSupport(match: VariantPrototypeClassifier.MatchResult): Boolean =
        hasStrongBaseShinyPeerSupport(match) &&
            match.confidence >= CLASSIFIER_BASE_SHINY_RESCUE_WITHOUT_VISUAL_CONFIDENCE

    /**
     * Peer scores are distances (lower is better), so peer-minus-winner > 0 means
     * the shiny candidate scored better. This existing bounded window is only a
     * conservative compatibility gate, not a calibrated confidence measure: the
     * observed Latios false positive shows that a wider gap cannot be treated as
     * generally trustworthy classifier-only evidence.
     */
    private fun hasBoundedSameVariantShinyPeerSupport(
        match: VariantPrototypeClassifier.MatchResult
    ): Boolean =
        match.bestShinyPeerScore?.let { peerScore ->
            (peerScore - match.score) in CLASSIFIER_SHINY_PEER_MIN_GAP..CLASSIFIER_SHINY_PEER_MARGIN
        } == true
}
