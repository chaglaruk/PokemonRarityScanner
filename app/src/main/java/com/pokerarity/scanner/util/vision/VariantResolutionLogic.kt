package com.pokerarity.scanner.util.vision

object VariantResolutionLogic {
    private const val CLASSIFIER_VARIANT_CONFIDENCE_SPECIES = 0.52f
    private const val CLASSIFIER_FORM_CONFIDENCE_SPECIES = 0.34f
    private const val CLASSIFIER_VARIANT_CONSENSUS_MARGIN = 0.03f
    private const val CLASSIFIER_FAMILY_COSTUME_RESCUE_CONFIDENCE = 0.52f

    fun resolve(
        globalMatch: VariantPrototypeClassifier.MatchResult?,
        speciesMatch: VariantPrototypeClassifier.MatchResult?,
        sameFamilyGlobalNonBase: Boolean
    ): VariantPrototypeClassifier.MatchResult? {
        if (speciesMatch == null) return globalMatch
        val exactNonBaseConsensus = globalMatch != null &&
            speciesMatch.variantType != "base" &&
            globalMatch.variantType != "base" &&
            globalMatch.assetKey == speciesMatch.assetKey &&
            globalMatch.isShiny == speciesMatch.isShiny &&
            globalMatch.isCostumeLike == speciesMatch.isCostumeLike &&
            globalMatch.variantType == speciesMatch.variantType
        if (exactNonBaseConsensus) {
            return speciesMatch.copy(
                confidence = maxOf(speciesMatch.confidence, CLASSIFIER_VARIANT_CONFIDENCE_SPECIES),
                rescueKind = "exact_non_base_consensus"
            )
        }

        val bestBaseScore = speciesMatch.bestBaseScore
        val bestNonBaseScore = speciesMatch.bestNonBaseScore
        val bestNonBaseVariantType = speciesMatch.bestNonBaseVariantType
        val bestNonBaseSpriteKey = speciesMatch.bestNonBaseSpriteKey
        val nonBasePenalty = if (bestBaseScore != null && bestNonBaseScore != null) {
            bestNonBaseScore - bestBaseScore
        } else {
            null
        }

        val shouldRescueSameFamilyNonBase =
            speciesMatch.variantType == "base" &&
                bestBaseScore != null &&
                bestNonBaseScore != null &&
                bestNonBaseVariantType != null &&
                bestNonBaseVariantType != "base" &&
                sameFamilyGlobalNonBase &&
                (bestNonBaseScore - bestBaseScore) <= CLASSIFIER_VARIANT_CONSENSUS_MARGIN &&
                speciesMatch.confidence <= 0.42f &&
                bestNonBaseSpriteKey != null
        if (shouldRescueSameFamilyNonBase) {
            val boostedConfidence = if (
                globalMatch != null &&
                globalMatch.variantType == bestNonBaseVariantType &&
                globalMatch.isShiny == speciesMatch.bestNonBaseIsShiny
            ) {
                CLASSIFIER_VARIANT_CONFIDENCE_SPECIES
            } else {
                CLASSIFIER_FORM_CONFIDENCE_SPECIES
            }
            return speciesMatch.copy(
                assetKey = speciesMatch.bestNonBaseAssetKey ?: speciesMatch.assetKey,
                spriteKey = bestNonBaseSpriteKey ?: speciesMatch.spriteKey,
                variantType = bestNonBaseVariantType ?: speciesMatch.variantType,
                isShiny = speciesMatch.bestNonBaseIsShiny,
                isCostumeLike = speciesMatch.bestNonBaseIsCostumeLike,
                score = bestNonBaseScore ?: speciesMatch.score,
                confidence = maxOf(speciesMatch.confidence, boostedConfidence),
                rescueKind = "same_family_non_base_rescue"
            )
        }

        val shouldRescueFamilyCostume =
            speciesMatch.variantType == "base" &&
                sameFamilyGlobalNonBase &&
                globalMatch != null &&
                globalMatch.variantType == "costume" &&
                globalMatch.confidence >= CLASSIFIER_FAMILY_COSTUME_RESCUE_CONFIDENCE &&
                bestNonBaseVariantType == "costume" &&
                bestNonBaseSpriteKey != null &&
                nonBasePenalty != null &&
                nonBasePenalty in 0.09f..0.14f &&
                globalMatch.score + 0.03f < speciesMatch.score
        if (shouldRescueFamilyCostume) {
            return speciesMatch.copy(
                assetKey = speciesMatch.bestNonBaseAssetKey ?: speciesMatch.assetKey,
                spriteKey = bestNonBaseSpriteKey ?: speciesMatch.spriteKey,
                variantType = bestNonBaseVariantType ?: speciesMatch.variantType,
                isShiny = speciesMatch.bestNonBaseIsShiny,
                isCostumeLike = speciesMatch.bestNonBaseIsCostumeLike,
                score = bestNonBaseScore ?: speciesMatch.score,
                confidence = maxOf(speciesMatch.confidence, CLASSIFIER_VARIANT_CONFIDENCE_SPECIES),
                rescueKind = "family_costume_rescue"
            )
        }

        val shouldRescueSameSpeciesShinyCostume =
            speciesMatch.variantType == "base" &&
                speciesMatch.isShiny &&
                speciesMatch.bestNonBaseVariantType == "costume" &&
                speciesMatch.bestNonBaseIsShiny &&
                bestNonBaseSpriteKey != null &&
                bestBaseScore != null &&
                bestNonBaseScore != null &&
                (bestNonBaseScore - bestBaseScore) in 0f..0.015f
        if (shouldRescueSameSpeciesShinyCostume) {
            return speciesMatch.copy(
                assetKey = speciesMatch.bestNonBaseAssetKey ?: speciesMatch.assetKey,
                spriteKey = bestNonBaseSpriteKey ?: speciesMatch.spriteKey,
                variantType = "costume",
                isShiny = true,
                isCostumeLike = speciesMatch.bestNonBaseIsCostumeLike,
                score = bestNonBaseScore ?: speciesMatch.score,
                confidence = maxOf(speciesMatch.confidence, CLASSIFIER_VARIANT_CONFIDENCE_SPECIES),
                rescueKind = "same_species_shiny_costume_rescue"
            )
        }

        return speciesMatch
    }
}
