package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.AuthoritativeVariantDbLoader
import com.pokerarity.scanner.data.repository.GlobalRarityLegacyLoader
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry
import com.pokerarity.scanner.util.ocr.ScanAuthorityLogic
import com.pokerarity.scanner.util.ocr.TextParser
import java.util.Locale

class VariantDecisionEngine(
    private val context: Context,
    private val classifier: VariantPrototypeClassifier = VariantPrototypeClassifier(context)
) {
    private val textParser by lazy { TextParser(context) }
    private val authoritativeVariantBySpecies by lazy {
        runCatching {
            AuthoritativeVariantDbLoader.indexBySpecies(AuthoritativeVariantDbLoader.load(context).entries)
        }.getOrDefault(emptyMap())
    }
    private val globalLegacyBySpecies by lazy {
        runCatching {
            GlobalRarityLegacyLoader.indexBySpecies(GlobalRarityLegacyLoader.load(context).entries)
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val CLASSIFIER_SPECIES_CONFIDENCE = 0.68f
        private const val CLASSIFIER_SPECIES_CONFIDENCE_FAMILY = 0.62f
        private const val CLASSIFIER_VARIANT_CONFIDENCE = 0.66f
        private const val CLASSIFIER_VARIANT_CONFIDENCE_SPECIES = 0.52f
        private const val CLASSIFIER_FORM_CONFIDENCE_SPECIES = 0.34f
        private const val CLASSIFIER_COSTUME_RESCUE_CONFIDENCE_SPECIES = 0.44f
        private const val CLASSIFIER_FAMILY_COSTUME_SUPPORT_CONFIDENCE = 0.58f
        private const val CLASSIFIER_BASE_SHINY_CONFIDENCE = 0.80f
        private const val CLASSIFIER_VARIANT_CONSENSUS_MARGIN = 0.03f
        private const val CLASSIFIER_FAMILY_COSTUME_RESCUE_CONFIDENCE = 0.52f  // Increased from 0.43f to reduce false positives
    }

    data class ClassificationResult(
        val pokemon: PokemonData,
        val globalMatch: VariantPrototypeClassifier.MatchResult?,
        val speciesMatch: VariantPrototypeClassifier.MatchResult?,
        val resolvedMatch: VariantPrototypeClassifier.MatchResult?,
        val fullMatch: FullVariantMatch?
    )

    fun classify(bitmap: Bitmap, pokemon: PokemonData): ClassificationResult {
        val initialRawFields = parseRawOcrFields(pokemon.rawOcrText)
        val parsedRawSpecies = textParser.parseName(initialRawFields["Name"].orEmpty())
        val parsedFallbackSpecies = textParser.parseName(initialRawFields["NameHC"].orEmpty())
        val currentSpecies = parsedRawSpecies ?: parsedFallbackSpecies ?: pokemon.realName ?: pokemon.name
        val skipGlobalClassifier = ScanAuthorityLogic.shouldSkipGlobalClassifierForLockedOcr(
            currentSpecies = currentSpecies,
            parsedRawSpecies = parsedRawSpecies,
            parsedFallbackSpecies = parsedFallbackSpecies,
            candyName = pokemon.candyName
        )
        if (skipGlobalClassifier) {
            Log.d(
                "VariantDecisionEngine",
                "Skipping global classifier for OCR-locked species '$currentSpecies'"
            )
        }
        val globalMatch = if (skipGlobalClassifier) {
            null
        } else {
            runCatching {
                classifier.classify(bitmap, buildHints(pokemon))
            }.getOrNull()
        }
        val classifiedBase = applyClassifierSpecies(pokemon, globalMatch)
        val speciesScopeTarget = chooseSpeciesScopeTarget(classifiedBase, globalMatch)
        val speciesMatch = runCatching {
            classifier.classifyForSpecies(bitmap, speciesScopeTarget)
        }.getOrNull()
        val resolvedMatch = resolveVariantClassifierMatch(classifiedBase, globalMatch, speciesMatch)
        val finalSpecies = classifiedBase.realName ?: classifiedBase.name ?: globalMatch?.species ?: "Unknown"
        val fullMatcherSpeciesSeed = FullVariantSeedSelection.chooseSpeciesSeed(
            finalSpecies = finalSpecies,
            speciesMatch = speciesMatch,
            resolvedMatch = resolvedMatch
        )
        val matcherCandidates = FullVariantCandidateBuilder.build(
            pokemon = classifiedBase,
            finalSpecies = finalSpecies,
            globalMatch = globalMatch,
            speciesMatch = fullMatcherSpeciesSeed,
            authoritativeBySpecies = authoritativeVariantBySpecies,
            globalLegacyBySpecies = globalLegacyBySpecies
        ) + buildFamilyCostumeSupportCandidates(
            finalSpecies = finalSpecies,
            globalMatch = globalMatch
        )
        val fullMatch = FullVariantMatcher.match(
            finalSpecies = finalSpecies,
            candidates = matcherCandidates
        )
        val tracedClassifier = appendClassifierTrace(classifiedBase, resolvedMatch, "VariantClassifier")
        val traced = appendFullVariantTrace(tracedClassifier, fullMatch).copy(fullVariantMatch = fullMatch)
        return ClassificationResult(
            pokemon = traced,
            globalMatch = globalMatch,
            speciesMatch = speciesMatch,
            resolvedMatch = resolvedMatch,
            fullMatch = fullMatch
        )
    }

    fun mergeVisualFeatures(
        visualFeatures: VisualFeatures,
        fullMatch: FullVariantMatch?,
        fallbackMatch: VariantPrototypeClassifier.MatchResult?
    ): VisualFeatures = VariantMergeLogic.mergeVisualFeatures(visualFeatures, fullMatch, fallbackMatch)

    fun mergeVisualFeatures(
        visualFeatures: VisualFeatures,
        match: VariantPrototypeClassifier.MatchResult?
    ): VisualFeatures = VariantMergeLogic.mergeVisualFeatures(visualFeatures, match)

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
        val rawFields = parseRawOcrFields(pokemon.rawOcrText)
        val parsedRawSpecies = textParser.parseName(rawFields["Name"].orEmpty())
        val parsedFallbackSpecies = textParser.parseName(rawFields["NameHC"].orEmpty())
        val currentSpecies = parsedRawSpecies ?: parsedFallbackSpecies ?: pokemon.realName ?: pokemon.name
        val sameSpecies = currentSpecies.equals(match.species, ignoreCase = true)
        val inCandyFamily = !pokemon.candyName.isNullOrBlank() &&
            PokemonFamilyRegistry.isSameFamily(context, match.species, pokemon.candyName)
        val authorityAllowsOverride = ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(
            currentSpecies = currentSpecies,
            parsedRawSpecies = parsedRawSpecies,
            parsedFallbackSpecies = parsedFallbackSpecies,
            candyName = pokemon.candyName,
            classifierSpecies = match.species,
            classifierInCandyFamily = inCandyFamily
        )
        val shouldOverride = when {
            sameSpecies -> false
            !authorityAllowsOverride -> false
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

    private fun chooseSpeciesScopeTarget(
        pokemon: PokemonData,
        globalMatch: VariantPrototypeClassifier.MatchResult?
    ): String? {
        val rawFields = parseRawOcrFields(pokemon.rawOcrText)
        val parsedRawSpecies = textParser.parseName(rawFields["Name"].orEmpty())
        val parsedFallbackSpecies = textParser.parseName(rawFields["NameHC"].orEmpty())
        val currentSpecies = parsedRawSpecies ?: parsedFallbackSpecies ?: pokemon.realName ?: pokemon.name
        if (globalMatch == null || currentSpecies.isNullOrBlank()) return currentSpecies
        val sameFamilyWithCurrent = PokemonFamilyRegistry.isSameFamily(context, currentSpecies, globalMatch.species)
        val currentSpeciesScore = parseSpeciesScore(globalMatch.topSpecies, currentSpecies)
        val shouldPreferClassifierSpecies = ScanAuthorityLogic.shouldPreferClassifierSpeciesForScopedPass(
            currentSpecies = currentSpecies,
            parsedRawSpecies = parsedRawSpecies,
            parsedFallbackSpecies = parsedFallbackSpecies,
            candyName = pokemon.candyName,
            classifierSpecies = globalMatch.species,
            classifierConfidence = globalMatch.confidence,
            classifierScore = globalMatch.score,
            currentSpeciesScore = currentSpeciesScore,
            sameFamilyWithCurrent = sameFamilyWithCurrent
        )
        return if (shouldPreferClassifierSpecies) globalMatch.species else currentSpecies
    }

    private fun resolveVariantClassifierMatch(
        pokemon: PokemonData,
        globalMatch: VariantPrototypeClassifier.MatchResult?,
        speciesMatch: VariantPrototypeClassifier.MatchResult?
    ): VariantPrototypeClassifier.MatchResult? {
        val sameFamilyGlobalNonBase = globalMatch != null &&
            globalMatch.variantType != "base" &&
            PokemonFamilyRegistry.isSameFamily(context, globalMatch.species, pokemon.realName ?: pokemon.name)
        return VariantResolutionLogic.resolve(globalMatch, speciesMatch, sameFamilyGlobalNonBase)
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

    private fun buildFamilyCostumeSupportCandidates(
        finalSpecies: String,
        globalMatch: VariantPrototypeClassifier.MatchResult?
    ): List<FullVariantCandidate> {
        if (globalMatch == null || !globalMatch.isCostumeLike) return emptyList()
        if (globalMatch.confidence < CLASSIFIER_FAMILY_COSTUME_SUPPORT_CONFIDENCE) return emptyList()
        if (!PokemonFamilyRegistry.isSameFamily(context, globalMatch.species, finalSpecies)) return emptyList()
        if (globalMatch.species.equals(finalSpecies, ignoreCase = true)) return emptyList()

        return authoritativeVariantBySpecies[finalSpecies].orEmpty()
            .filter { it.isCostumeLike && it.isShiny == globalMatch.isShiny }
            .map { entry ->
                FullVariantCandidate(
                    species = entry.species,
                    spriteKey = entry.spriteKey,
                    variantClass = entry.variantClass,
                    isShiny = entry.isShiny,
                    isCostumeLike = entry.isCostumeLike,
                    eventLabel = entry.eventLabel,
                    eventStart = entry.eventStart,
                    eventEnd = entry.eventEnd,
                    matchScore = globalMatch.score,
                    rescueKind = "family_global_costume_support",
                    source = "authoritative_family_costume_support",
                    classifierConfidence = globalMatch.confidence
                )
            }
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
        fields["${prefix}RescueKind"] = match.rescueKind ?: ""
        return fields.entries.joinToString("|") { "${it.key}:${it.value}" }
    }

    private fun appendFullVariantTrace(
        pokemon: PokemonData,
        fullMatch: FullVariantMatch?
    ): PokemonData {
        if (fullMatch == null) return pokemon
        val fields = parseRawOcrFields(pokemon.rawOcrText)
        fields["FullVariantSpecies"] = fullMatch.finalSpecies
        fields["FullVariantSpriteKey"] = fullMatch.finalSpriteKey ?: ""
        fields["FullVariantClass"] = fullMatch.resolvedVariantClass
        fields["FullVariantShiny"] = fullMatch.resolvedShiny.toString()
        fields["FullVariantCostume"] = fullMatch.resolvedCostume.toString()
        fields["FullVariantForm"] = fullMatch.resolvedForm.toString()
        fields["FullVariantEvent"] = fullMatch.resolvedEventLabel ?: ""
        fields["FullVariantExplanationMode"] = fullMatch.explanationMode
        fields["FullVariantSpeciesConfidence"] = "%.3f".format(Locale.US, fullMatch.speciesConfidence)
        fields["FullVariantVariantConfidence"] = "%.3f".format(Locale.US, fullMatch.variantConfidence)
        fields["FullVariantShinyConfidence"] = "%.3f".format(Locale.US, fullMatch.shinyConfidence)
        fields["FullVariantEventConfidence"] = "%.3f".format(Locale.US, fullMatch.eventConfidence)
        fields["FullVariantDebug"] = fullMatch.debugSummary
        val augmentedRaw = fields.entries.joinToString("|") { "${it.key}:${it.value}" }
        return if (augmentedRaw == pokemon.rawOcrText) pokemon else pokemon.copy(rawOcrText = augmentedRaw)
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

    private fun parseSpeciesScore(topSpecies: List<String>, species: String): Float? {
        return topSpecies.firstOrNull {
            it.substringBefore(':').equals(species, ignoreCase = true)
        }?.substringAfter(':')?.toFloatOrNull()
    }

    private fun isUnknownSpecies(value: String?): Boolean {
        return value.isNullOrBlank() || value.equals("Unknown", ignoreCase = true)
    }
}
