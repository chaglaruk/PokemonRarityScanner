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

    data class ClassificationResult(
        val pokemon: PokemonData,
        val globalMatch: VariantPrototypeClassifier.MatchResult?,
        val speciesMatch: VariantPrototypeClassifier.MatchResult?,
        val resolvedMatch: VariantPrototypeClassifier.MatchResult?,
        val fullMatch: FullVariantMatch?
    )

    fun classify(bitmap: Bitmap, pokemon: PokemonData): ClassificationResult {
        val initialRawFields = parseRawOcrFields(pokemon.rawOcrText)
        val parsedRawSpecies = textParser.parseStrongSpeciesName(initialRawFields["Name"].orEmpty())
        val parsedFallbackSpecies = textParser.parseStrongSpeciesName(initialRawFields["NameHC"].orEmpty())
        val currentSpecies = chooseLockedCurrentSpecies(
            rawName = initialRawFields["Name"],
            fallbackName = initialRawFields["NameHC"],
            parsedRawSpecies = parsedRawSpecies,
            parsedFallbackSpecies = parsedFallbackSpecies,
            storedSpecies = pokemon.realName ?: pokemon.name
        )
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
        val speciesScopeTarget = chooseSpeciesScopeTarget(pokemon)
        val speciesMatch = runCatching {
            classifier.classifyForSpecies(bitmap, speciesScopeTarget)
        }.getOrNull()
        val resolvedMatch = resolveVariantClassifierMatch(pokemon, globalMatch, speciesMatch)
        val finalSpecies = finalSpeciesFor(pokemon)
        val fullMatcherSpeciesSeed = FullVariantSeedSelection.chooseSpeciesSeed(
            finalSpecies = finalSpecies,
            speciesMatch = speciesMatch,
            resolvedMatch = resolvedMatch
        )
        val costumeSignatureDetails = CostumeSignatureStore.matchBitmapDetails(context, bitmap, finalSpecies)
        val costumeEvidence = costumeSignatureDetails?.let {
            FullVariantMatcher.CostumeEvidence(
                matched = it.matched,
                confidence = it.confidence,
                preferredSpriteKey = it.bestCostumeKey
            )
        }
        val matcherCandidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = finalSpecies,
            globalMatch = globalMatch,
            speciesMatch = fullMatcherSpeciesSeed,
            authoritativeBySpecies = authoritativeVariantBySpecies,
            costumeSignatureKey = costumeSignatureDetails?.bestCostumeKey,
            costumeSignatureConfidence = costumeSignatureDetails?.confidence ?: 0f
        )
        val fullMatch = FullVariantMatcher.match(
            finalSpecies = finalSpecies,
            candidates = matcherCandidates,
            costumeEvidence = costumeEvidence
        )
        val decisionTrace = com.pokerarity.scanner.data.model.VariantDecisionTrace(
            classifierScope = resolvedMatch?.scope,
            classifierSpecies = resolvedMatch?.species,
            classifierSpriteKey = resolvedMatch?.spriteKey,
            classifierVariantType = resolvedMatch?.variantType,
            classifierShiny = resolvedMatch?.isShiny,
            classifierCostume = resolvedMatch?.isCostumeLike,
            classifierConfidence = resolvedMatch?.confidence,
            classifierScore = resolvedMatch?.score,
            classifierVariantMargin = resolvedMatch?.variantMargin,
            classifierBestBaseScore = resolvedMatch?.bestBaseScore,
            classifierBestNonBaseScore = resolvedMatch?.bestNonBaseScore,
            classifierBestNonBaseType = resolvedMatch?.bestNonBaseVariantType,
            classifierRescueKind = resolvedMatch?.rescueKind,
            fullVariantSpecies = fullMatch?.finalSpecies,
            fullVariantSpriteKey = fullMatch?.finalSpriteKey,
            fullVariantClass = fullMatch?.resolvedVariantClass,
            fullVariantShiny = fullMatch?.resolvedShiny,
            fullVariantCostume = fullMatch?.resolvedCostume,
            fullVariantForm = fullMatch?.resolvedForm,
            fullVariantEvent = fullMatch?.resolvedEventLabel,
            fullVariantExplanationMode = fullMatch?.explanationMode,
            fullVariantSpeciesConfidence = fullMatch?.speciesConfidence,
            fullVariantVariantConfidence = fullMatch?.variantConfidence,
            fullVariantShinyConfidence = fullMatch?.shinyConfidence,
            fullVariantEventConfidence = fullMatch?.eventConfidence,
            fullVariantDebug = fullMatch?.debugSummary
        )

        val traced = pokemon.copy(
            fullVariantMatch = fullMatch,
            variantDecisionTrace = decisionTrace
        )
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

    internal fun finalSpeciesFor(pokemon: PokemonData): String {
        return pokemon.realName ?: pokemon.name ?: "Unknown"
    }

    internal fun chooseSpeciesScopeTarget(pokemon: PokemonData): String? {
        val rawFields = parseRawOcrFields(pokemon.rawOcrText)
        val parsedRawSpecies = textParser.parseStrongSpeciesName(rawFields["Name"].orEmpty())
        val parsedFallbackSpecies = textParser.parseStrongSpeciesName(rawFields["NameHC"].orEmpty())
        return chooseLockedCurrentSpecies(
            rawName = rawFields["Name"],
            fallbackName = rawFields["NameHC"],
            parsedRawSpecies = parsedRawSpecies,
            parsedFallbackSpecies = parsedFallbackSpecies,
            storedSpecies = pokemon.realName ?: pokemon.name
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

    private fun chooseLockedCurrentSpecies(
        rawName: String?,
        fallbackName: String?,
        parsedRawSpecies: String?,
        parsedFallbackSpecies: String?,
        storedSpecies: String?
    ): String? {
        parsedRawSpecies?.let { return it }
        parsedFallbackSpecies?.let { return it }

        val relaxedRawSpecies = textParser.parseName(rawName.orEmpty())
        val relaxedFallbackSpecies = textParser.parseName(fallbackName.orEmpty())
        val rawLooksLikeNickname = !rawName.isNullOrBlank() && parsedRawSpecies == null && relaxedRawSpecies != null
        val fallbackLooksLikeNickname = !fallbackName.isNullOrBlank() && parsedFallbackSpecies == null && relaxedFallbackSpecies != null
        val storedLooksDerivedFromWeakOcr =
            !storedSpecies.isNullOrBlank() &&
                (storedSpecies.equals(relaxedRawSpecies, ignoreCase = true) ||
                    storedSpecies.equals(relaxedFallbackSpecies, ignoreCase = true))

        return if ((rawLooksLikeNickname || fallbackLooksLikeNickname) && storedLooksDerivedFromWeakOcr) {
            null
        } else {
            storedSpecies
        }
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
