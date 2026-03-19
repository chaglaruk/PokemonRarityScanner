package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry
import java.util.Locale

class VariantDecisionEngine(
    private val context: Context,
    private val classifier: VariantPrototypeClassifier = VariantPrototypeClassifier(context)
) {

    companion object {
        private const val CLASSIFIER_SPECIES_CONFIDENCE = 0.72f
        private const val CLASSIFIER_SPECIES_CONFIDENCE_FAMILY = 0.62f
        private const val CLASSIFIER_VARIANT_CONFIDENCE = 0.66f
        private const val CLASSIFIER_VARIANT_CONFIDENCE_SPECIES = 0.52f
        private const val CLASSIFIER_FORM_CONFIDENCE_SPECIES = 0.34f
        private const val CLASSIFIER_COSTUME_RESCUE_CONFIDENCE_SPECIES = 0.44f
        private const val CLASSIFIER_BASE_SHINY_CONFIDENCE = 0.80f
        private const val CLASSIFIER_VARIANT_CONSENSUS_MARGIN = 0.03f
        private const val CLASSIFIER_FAMILY_COSTUME_RESCUE_CONFIDENCE = 0.43f
    }

    data class ClassificationResult(
        val pokemon: PokemonData,
        val globalMatch: VariantPrototypeClassifier.MatchResult?,
        val speciesMatch: VariantPrototypeClassifier.MatchResult?,
        val resolvedMatch: VariantPrototypeClassifier.MatchResult?
    )

    fun classify(bitmap: Bitmap, pokemon: PokemonData): ClassificationResult {
        val globalMatch = runCatching {
            classifier.classify(bitmap, buildHints(pokemon))
        }.getOrNull()
        val classifiedBase = applyClassifierSpecies(pokemon, globalMatch)
        val speciesMatch = runCatching {
            classifier.classifyForSpecies(bitmap, classifiedBase.realName ?: classifiedBase.name)
        }.getOrNull()
        val resolvedMatch = resolveVariantClassifierMatch(classifiedBase, globalMatch, speciesMatch)
        val traced = appendClassifierTrace(classifiedBase, resolvedMatch, "VariantClassifier")
        return ClassificationResult(
            pokemon = traced,
            globalMatch = globalMatch,
            speciesMatch = speciesMatch,
            resolvedMatch = resolvedMatch
        )
    }

    fun mergeVisualFeatures(
        visualFeatures: VisualFeatures,
        match: VariantPrototypeClassifier.MatchResult?
    ): VisualFeatures {
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
                match.confidence >= CLASSIFIER_COSTUME_RESCUE_CONFIDENCE_SPECIES &&
                match.bestBaseScore != null &&
                match.score + 0.03f < match.bestBaseScore
        val promoteCostumeByNearTieRescue =
            match.scope == "species" &&
                match.variantType == "costume" &&
                match.isCostumeLike &&
                match.confidence >= 0.47f &&
                match.bestBaseScore != null &&
                (match.bestBaseScore - match.score) in 0f..0.015f &&
                match.bestNonBaseVariantType == "costume" &&
                match.bestNonBaseIsCostumeLike
        val promoteForm = match.variantType == "form" && match.confidence >= formConfidenceGate
        if (match.confidence < requiredConfidence && !promoteForm && !promoteCostumeBySpeciesRescue && !promoteCostumeByNearTieRescue) {
            return visualFeatures
        }
        val promoteCostume = match.isCostumeLike && (
            match.confidence >= requiredConfidence ||
                promoteCostumeBySpeciesRescue ||
                promoteCostumeByNearTieRescue
            )
        val suppressVisualShiny =
            promoteCostume &&
            !match.isShiny &&
            (
                match.confidence >= requiredConfidence ||
                    promoteCostumeBySpeciesRescue ||
                    promoteCostumeByNearTieRescue
                )
        val allowClassifierShiny =
            match.isShiny &&
                match.confidence >= requiredConfidence &&
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

    private fun buildHints(pokemon: PokemonData): Set<String> {
        val hints = linkedSetOf<String>()
        pokemon.name?.takeUnless(::isUnknownSpecies)?.let { hints += it }
        pokemon.realName?.takeUnless(::isUnknownSpecies)?.let { hints += it }
        pokemon.candyName?.takeUnless(::isUnknownSpecies)?.let { hints += it }
        pokemon.candyName?.let { hints += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        pokemon.realName?.let { hints += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        pokemon.name?.let { hints += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        return hints.filterNot { it.isBlank() }.toSet()
    }

    private fun applyClassifierSpecies(
        pokemon: PokemonData,
        match: VariantPrototypeClassifier.MatchResult?
    ): PokemonData {
        if (match == null) return pokemon
        val currentSpecies = pokemon.realName ?: pokemon.name
        val sameSpecies = currentSpecies.equals(match.species, ignoreCase = true)
        val inCandyFamily = !pokemon.candyName.isNullOrBlank() &&
            PokemonFamilyRegistry.isSameFamily(context, match.species, pokemon.candyName)
        val shouldOverride = when {
            sameSpecies -> false
            isUnknownSpecies(currentSpecies) -> match.confidence >= CLASSIFIER_SPECIES_CONFIDENCE_FAMILY
            inCandyFamily -> match.confidence >= CLASSIFIER_SPECIES_CONFIDENCE_FAMILY
            else -> match.confidence >= CLASSIFIER_SPECIES_CONFIDENCE
        }
        val augmentedRaw = appendClassifierFields(pokemon.rawOcrText, match)
        if (!shouldOverride) {
            return if (augmentedRaw == pokemon.rawOcrText) pokemon else pokemon.copy(rawOcrText = augmentedRaw)
        }
        return pokemon.copy(
            name = match.species,
            realName = match.species,
            rawOcrText = augmentedRaw
        )
    }

    private fun resolveVariantClassifierMatch(
        pokemon: PokemonData,
        globalMatch: VariantPrototypeClassifier.MatchResult?,
        speciesMatch: VariantPrototypeClassifier.MatchResult?
    ): VariantPrototypeClassifier.MatchResult? {
        if (speciesMatch == null) return globalMatch
        val sameFamilyGlobalNonBase = globalMatch != null &&
            globalMatch.variantType != "base" &&
            PokemonFamilyRegistry.isSameFamily(context, globalMatch.species, pokemon.realName ?: pokemon.name)
        val exactNonBaseConsensus = globalMatch != null &&
            speciesMatch.variantType != "base" &&
            globalMatch.variantType != "base" &&
            globalMatch.assetKey == speciesMatch.assetKey &&
            globalMatch.isShiny == speciesMatch.isShiny &&
            globalMatch.isCostumeLike == speciesMatch.isCostumeLike &&
            globalMatch.variantType == speciesMatch.variantType
        if (exactNonBaseConsensus) {
            return speciesMatch.copy(
                confidence = maxOf(speciesMatch.confidence, CLASSIFIER_VARIANT_CONFIDENCE_SPECIES)
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

        if (
            speciesMatch.variantType == "base" &&
            bestBaseScore != null &&
            bestNonBaseScore != null &&
            bestNonBaseVariantType != null &&
            bestNonBaseVariantType != "base" &&
            sameFamilyGlobalNonBase &&
            (bestNonBaseScore - bestBaseScore) <= CLASSIFIER_VARIANT_CONSENSUS_MARGIN &&
            speciesMatch.confidence <= 0.42f &&
            bestNonBaseSpriteKey != null
        ) {
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
                spriteKey = bestNonBaseSpriteKey,
                variantType = bestNonBaseVariantType,
                isShiny = speciesMatch.bestNonBaseIsShiny,
                isCostumeLike = speciesMatch.bestNonBaseIsCostumeLike,
                score = bestNonBaseScore ?: speciesMatch.score,
                confidence = maxOf(speciesMatch.confidence, boostedConfidence)
            )
        }

        if (
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
        ) {
            return speciesMatch.copy(
                assetKey = speciesMatch.bestNonBaseAssetKey ?: speciesMatch.assetKey,
                spriteKey = bestNonBaseSpriteKey,
                variantType = bestNonBaseVariantType,
                isShiny = speciesMatch.bestNonBaseIsShiny,
                isCostumeLike = speciesMatch.bestNonBaseIsCostumeLike,
                score = bestNonBaseScore ?: speciesMatch.score,
                confidence = maxOf(speciesMatch.confidence, CLASSIFIER_VARIANT_CONFIDENCE_SPECIES)
            )
        }

        return speciesMatch
    }

    private fun appendClassifierTrace(
        pokemon: PokemonData,
        match: VariantPrototypeClassifier.MatchResult?,
        prefix: String
    ): PokemonData {
        if (match == null) return pokemon
        val augmentedRaw = appendClassifierFields(pokemon.rawOcrText, match, prefix)
        return if (augmentedRaw == pokemon.rawOcrText) pokemon else pokemon.copy(rawOcrText = augmentedRaw)
    }

    private fun appendClassifierFields(
        raw: String,
        match: VariantPrototypeClassifier.MatchResult,
        prefix: String = "Classifier"
    ): String {
        val fields = parseRawOcrFields(raw)
        fields["${prefix}Scope"] = match.scope
        fields["${prefix}Species"] = match.species
        fields["${prefix}SpriteKey"] = match.spriteKey
        fields["${prefix}VariantType"] = match.variantType
        fields["${prefix}Shiny"] = match.isShiny.toString()
        fields["${prefix}Costume"] = match.isCostumeLike.toString()
        fields["${prefix}Confidence"] = "%.3f".format(Locale.US, match.confidence)
        fields["${prefix}Score"] = "%.3f".format(Locale.US, match.score)
        fields["${prefix}VariantMargin"] = "%.3f".format(Locale.US, match.variantMargin)
        fields["${prefix}BestBaseScore"] = match.bestBaseScore?.let { "%.3f".format(Locale.US, it) } ?: ""
        fields["${prefix}BestNonBaseScore"] = match.bestNonBaseScore?.let { "%.3f".format(Locale.US, it) } ?: ""
        fields["${prefix}BestNonBaseType"] = match.bestNonBaseVariantType ?: ""
        return fields.entries.joinToString("|") { "${it.key}:${it.value}" }
    }

    private fun parseRawOcrFields(raw: String): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        raw.split("|").forEach { part ->
            val separator = part.indexOf(':')
            if (separator <= 0) return@forEach
            val key = part.substring(0, separator)
            val value = part.substring(separator + 1)
            result[key] = value
        }
        return result
    }

    private fun isUnknownSpecies(value: String?): Boolean {
        return value.isNullOrBlank() || value.equals("Unknown", ignoreCase = true)
    }
}
