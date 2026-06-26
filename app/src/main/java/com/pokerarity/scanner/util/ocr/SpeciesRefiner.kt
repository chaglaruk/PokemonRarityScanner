// Amaç: OCR ile elde edilen Pokemon tür isimlerini oyun içi mantıksal verilerle iyileştirmek.
package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.util.Log
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry
import com.pokerarity.scanner.data.repository.PokemonMoveRegistry
import com.pokerarity.scanner.data.repository.RarityCalculator
import kotlin.math.max

class SpeciesRefiner(
    private val context: Context,
    private val rarityCalculator: RarityCalculator,
    private val config: SpeciesRefinerConfig = SpeciesRefinerConfig.default()
) {

    private val textParser = TextParser(context)
    private val speciesFormResolver = SpeciesFormResolver(context, rarityCalculator, textParser)

    fun refine(
        pokemon: PokemonData,
        fieldCandidates: List<FieldCandidateDiagnostic> = emptyList()
    ): PokemonData {
        val resolverResolution = speciesFormResolver.resolve(pokemon, fieldCandidates)
        val tracedPokemon = pokemon.copy(speciesResolverTrace = resolverResolution.trace)
        val currentSpecies = tracedPokemon.realName ?: tracedPokemon.name
        val rawName = extractRawField(tracedPokemon.rawOcrText, "Name")
        val fallbackName = extractRawField(tracedPokemon.rawOcrText, "NameHC")
        val bottomRaw = extractRawField(tracedPokemon.rawOcrText, "Bottom")
        val parsedRawName = textParser.parseName(rawName)
        val parsedFallbackName = textParser.parseName(fallbackName)
        val moveHint = PokemonMoveRegistry.extractMoveHint(context, bottomRaw)
        val candyFamilySize = PokemonFamilyRegistry.familySize(context, tracedPokemon.candyName)
        val uniqueCandySpecies = !tracedPokemon.candyName.isNullOrBlank() && candyFamilySize == 1
        val currentInitialFit = currentSpecies?.let { rarityCalculator.scoreSpeciesFit(tracedPokemon, it) }
        val rankedRaw = textParser.rankNameCandidates(rawName, limit = 6)
        val rankedFallback = textParser.rankNameCandidates(fallbackName, limit = 6)
        val currentRankScore = maxOf(
            rankedRaw.firstOrNull { it.name.equals(currentSpecies, ignoreCase = true) }?.score ?: 0.0,
            rankedFallback.firstOrNull { it.name.equals(currentSpecies, ignoreCase = true) }?.score ?: 0.0
        )
        val topTextConfidence = maxOf(rankedRaw.firstOrNull()?.score ?: 0.0, rankedFallback.firstOrNull()?.score ?: 0.0)
        val normalizedRawLength = normalizeName(rawName).length
        val shortRawName = normalizedRawLength in 1..4
        val weakNameSignal = shortRawName || topTextConfidence < config.weakNameConfidence
        val directParsedSpeciesMatch =
            parsedRawName.equals(currentSpecies, ignoreCase = true) ||
                parsedFallbackName.equals(currentSpecies, ignoreCase = true)
        val currentLooksLikeNickname = currentRankScore < config.nicknameScoreThreshold && !directParsedSpeciesMatch
        val currentHasStrongTextAnchor = directParsedSpeciesMatch ||
            hasStrongSpeciesAnchor(rawName, currentSpecies) ||
            hasStrongSpeciesAnchor(fallbackName, currentSpecies)
        val exactParsedSpeciesLock = !currentSpecies.isNullOrBlank() &&
            (
                parsedRawName.equals(currentSpecies, ignoreCase = true) ||
                    parsedFallbackName.equals(currentSpecies, ignoreCase = true)
                )
        val normalizedCurrentSpecies = normalizeName(currentSpecies.orEmpty())
        val normalizedRawName = normalizeName(rawName)
        val rawExtendsCurrentSpecies = normalizedCurrentSpecies.length in 3..4 &&
            normalizedRawName.length >= normalizedCurrentSpecies.length + 1 &&
            normalizedRawName.contains(normalizedCurrentSpecies)
        val currentHasProfileMismatch = currentInitialFit != null &&
            (
                (!currentInitialFit.cpPossible && currentInitialFit.minArcDiff >= config.arcDiffThreshold) ||
                    (!currentInitialFit.cpPossible && currentInitialFit.score <= config.profileMismatchScore)
                )
        val prefixRelatedCandidates = if (currentHasProfileMismatch) {
            textParser.findNamesWithPrefix(normalizeName(parsedRawName ?: currentSpecies.orEmpty()), limit = 8)
        } else {
            emptyList()
        }
        val trustedResolvedSpecies = !currentSpecies.isNullOrBlank() &&
            !shortRawName &&
            (!currentHasProfileMismatch || exactParsedSpeciesLock) &&
            (
                currentRankScore >= config.trustedRankScore ||
                    exactParsedSpeciesLock ||
                    directParsedSpeciesMatch ||
                    (currentHasStrongTextAnchor && topTextConfidence >= config.anchorConfidence)
                )
        val shouldOpenGlobalCandidates = (tracedPokemon.candyName.isNullOrBlank() || weakNameSignal || currentLooksLikeNickname || currentHasProfileMismatch) &&
            !trustedResolvedSpecies
        val observedProfileCandidates = if (shouldOpenGlobalCandidates) {
            rarityCalculator.rankSpeciesByObservedProfile(tracedPokemon, limit = 14)
        } else {
            emptyList()
        }
        val physicalCandidates = if (shouldOpenGlobalCandidates) {
            rarityCalculator.rankSpeciesByPhysicalProfile(tracedPokemon, limit = 14)
        } else {
            emptyList()
        }

        val candidatePool = linkedSetOf<String>()
        currentSpecies?.let { candidatePool += it }
        tracedPokemon.candyName?.let { candidatePool += it }
        parsedRawName?.let { candidatePool += it }
        parsedFallbackName?.let { candidatePool += it }
        resolverResolution.species?.let { candidatePool += it }
        candidatePool += resolverResolution.alternatives.take(4).map { it.species }
        candidatePool += prefixRelatedCandidates

        candidatePool += rankedRaw.take(4).map { it.name }
        candidatePool += rankedFallback.take(4).map { it.name }

        currentSpecies?.let { candidatePool += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        tracedPokemon.candyName?.let { candidatePool += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        rankedRaw.take(3).forEach { candidate ->
            candidatePool += PokemonFamilyRegistry.getFamilyMembers(context, candidate.name)
        }
        moveHint?.let { hintedMove ->
            val moveCandidates = PokemonMoveRegistry.getSpeciesForMove(context, hintedMove)
            if (moveCandidates.size <= 24) {
                candidatePool += moveCandidates
            } else {
                candidatePool += moveCandidates
                    .map { species -> species to rarityCalculator.scoreSpeciesFit(tracedPokemon, species).score }
                    .sortedByDescending { it.second }
                    .take(24)
                    .map { it.first }
            }
        }
        candidatePool += observedProfileCandidates.map { it.species }
        candidatePool += physicalCandidates.map { it.species }

        val resolvedCandidates = candidatePool
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            .distinct()
        if (resolvedCandidates.isEmpty()) return tracedPokemon

        val scored = resolvedCandidates.map { candidate ->
            val rawScore = textParser.rankNameCandidates(rawName, limit = 6, restrictTo = listOf(candidate))
                .firstOrNull()?.score ?: 0.0
            val fallbackScore = textParser.rankNameCandidates(fallbackName, limit = 6, restrictTo = listOf(candidate))
                .firstOrNull()?.score ?: 0.0
            val resolverScore = resolverResolution.alternatives
                .firstOrNull { it.species.equals(candidate, ignoreCase = true) }
                ?.score
                ?.toDouble() ?: 0.0
            val currentPrior = if (currentSpecies.equals(candidate, ignoreCase = true)) {
                when {
                    currentLooksLikeNickname -> config.priorNickname
                    weakNameSignal -> config.priorWeak
                    else -> currentRankScore.coerceAtLeast(config.priorFloor)
                }
            } else {
                0.0
            }
            val textScore = maxOf(rawScore, fallbackScore, currentPrior, resolverScore)
            val fit = rarityCalculator.scoreSpeciesFit(tracedPokemon, candidate)
            val moveScore = PokemonMoveRegistry.moveMatchScore(context, candidate, moveHint)
            val candyBonus = if (PokemonFamilyRegistry.isSameFamily(context, candidate, tracedPokemon.candyName)) config.candyBonus else 0.0
            val candyExactBonus = if (uniqueCandySpecies && candidate.equals(tracedPokemon.candyName, ignoreCase = true)) config.candyExactBonus else 0.0
            val familyBonus = if (PokemonFamilyRegistry.isSameFamily(context, candidate, currentSpecies)) config.familyBonus else 0.0
            val observedProfileScore = observedProfileCandidates.firstOrNull { it.species.equals(candidate, ignoreCase = true) }?.score ?: 0.0
            val physicalProfileScore = physicalCandidates.firstOrNull { it.species.equals(candidate, ignoreCase = true) }?.score ?: 0.0
            val weights = if (weakNameSignal || moveHint != null || currentLooksLikeNickname) {
                config.weakWeights
            } else {
                config.strongWeights
            }
            val movePenalty = if (moveHint != null && moveScore == 0.0) config.movePenalty else 0.0
            val nicknamePenalty = if (currentSpecies.equals(candidate, ignoreCase = true) && currentLooksLikeNickname && textScore < 0.30) config.nicknamePenalty else 0.0
            val profileMismatchPenalty = if (
                currentHasProfileMismatch &&
                currentSpecies.equals(candidate, ignoreCase = true)
            ) {
                config.profileMismatchPenalty
            } else {
                0.0
            }
            val shortSpeciesExtensionBonus = if (
                currentHasProfileMismatch &&
                rawExtendsCurrentSpecies &&
                !currentSpecies.equals(candidate, ignoreCase = true) &&
                normalizeName(candidate).startsWith(normalizedCurrentSpecies)
            ) {
                config.shortExtensionBonus
            } else {
                0.0
            }
            CandidateScore(
                species = candidate,
                totalScore = (
                    weights.first * textScore +
                        weights.second * fit.score +
                        weights.third * moveScore +
                        config.observedWeight * observedProfileScore +
                        config.physicalWeight * physicalProfileScore +
                        candyBonus +
                        candyExactBonus +
                        shortSpeciesExtensionBonus +
                        familyBonus -
                        movePenalty -
                        nicknamePenalty -
                        profileMismatchPenalty
                    ).coerceIn(0.0, 1.0),
                textScore = textScore,
                fitScore = fit.score,
                moveScore = moveScore,
                sizeScore = fit.sizeScore,
                hpPossible = fit.hpPossible,
                cpPossible = fit.cpPossible
            )
        }.sortedByDescending { it.totalScore }

        val best = scored.first()
        val currentScore = scored.firstOrNull { it.species.equals(currentSpecies, ignoreCase = true) }
        val bestCandyFamilyCandidate = scored.firstOrNull {
            PokemonFamilyRegistry.isSameFamily(context, it.species, tracedPokemon.candyName)
        }
        val bestAlternateCandyFamilyCandidate = scored.firstOrNull {
            PokemonFamilyRegistry.isSameFamily(context, it.species, tracedPokemon.candyName) &&
                !it.species.equals(currentSpecies, ignoreCase = true)
        }
        val moveOverride = currentScore != null &&
            moveHint != null &&
            best.species != currentScore.species &&
            best.moveScore >= 1.0 &&
            currentScore.moveScore <= 0.0 &&
            best.totalScore >= currentScore.totalScore + config.totalGapLarge
        val familyFitOverride = currentScore != null &&
            best.species != currentScore.species &&
            PokemonFamilyRegistry.isSameFamily(context, best.species, currentScore.species) &&
            best.fitScore >= max(config.familyFitOverrideMin, currentScore.fitScore + config.fitGap) &&
            (!currentScore.cpPossible || best.cpPossible || best.sizeScore >= currentScore.sizeScore + 0.10)
        val nicknameOverride = currentScore != null &&
            best.species != currentScore.species &&
            currentLooksLikeNickname &&
            best.fitScore >= max(config.nicknameFitOverrideMin, currentScore.fitScore + 0.10) &&
            best.totalScore >= currentScore.totalScore + config.totalGap
        val evolutionFamilyOverride = currentScore != null &&
            bestAlternateCandyFamilyCandidate != null &&
            candyFamilySize > 1 &&
            currentSpecies.equals(tracedPokemon.candyName, ignoreCase = true) &&
            (
                (
                    !currentScore.cpPossible &&
                        bestAlternateCandyFamilyCandidate.cpPossible &&
                        bestAlternateCandyFamilyCandidate.fitScore >= currentScore.fitScore + config.fitGapSmall &&
                        bestAlternateCandyFamilyCandidate.sizeScore >= currentScore.sizeScore + config.sizeGap
                    ) ||
                    (
                        bestAlternateCandyFamilyCandidate.fitScore >= currentScore.fitScore + config.fitGapLarge &&
                            bestAlternateCandyFamilyCandidate.sizeScore >= currentScore.sizeScore + config.sizeGapLarge &&
                            bestAlternateCandyFamilyCandidate.totalScore >= currentScore.totalScore + config.totalGapLarge
                        )
                )
        val uniqueCandyOverride = uniqueCandySpecies &&
            best.species.equals(tracedPokemon.candyName, ignoreCase = true) &&
            (currentScore == null ||
                !best.species.equals(currentScore.species, ignoreCase = true)) &&
            best.fitScore >= config.uniqueCandyFit &&
            best.totalScore >= (currentScore?.totalScore ?: 0.0) + config.totalGapSmall
        val candyFamilyAuthorityOverride =
            !tracedPokemon.candyName.isNullOrBlank() &&
                candyFamilySize > 1 &&
                currentSpecies != null &&
                !PokemonFamilyRegistry.isSameFamily(context, currentSpecies, tracedPokemon.candyName) &&
                bestCandyFamilyCandidate != null &&
                !bestCandyFamilyCandidate.species.equals(currentSpecies, ignoreCase = true) &&
                bestCandyFamilyCandidate.fitScore >= config.candyAuthorityFit &&
                bestCandyFamilyCandidate.totalScore >= config.candyAuthorityTotal
        val strongSpeciesLock = trustedResolvedSpecies &&
            currentScore != null &&
            currentHasStrongTextAnchor &&
            (!currentHasProfileMismatch || exactParsedSpeciesLock) &&
            (currentScore.cpPossible || currentScore.fitScore >= config.fitLockThreshold) &&
            moveHint == null &&
            tracedPokemon.candyName.isNullOrBlank()
        val exactFamilySpeciesLock = !currentSpecies.isNullOrBlank() &&
            directParsedSpeciesMatch &&
            !currentHasProfileMismatch &&
            moveHint == null &&
            tracedPokemon.candyName.isNullOrBlank()
        val replacementCandidate = if (candyFamilyAuthorityOverride) {
            bestCandyFamilyCandidate ?: best
        } else if (evolutionFamilyOverride) {
            bestAlternateCandyFamilyCandidate ?: best
        } else {
            best
        }
        val anchoredCurrentSpecies = !currentSpecies.isNullOrBlank() &&
            (
                currentSpecies.equals(tracedPokemon.candyName, ignoreCase = true) ||
                    parsedRawName.equals(currentSpecies, ignoreCase = true) ||
                    parsedFallbackName.equals(currentSpecies, ignoreCase = true)
                )
        val lowConfidenceFamilyOverride = anchoredCurrentSpecies &&
            replacementCandidate.species != currentSpecies &&
            replacementCandidate.totalScore < 0.22 &&
            replacementCandidate.textScore < 0.20 &&
            replacementCandidate.fitScore < 0.20 &&
            replacementCandidate.moveScore <= 0.0 &&
            !uniqueCandyOverride &&
            !evolutionFamilyOverride &&
            !moveOverride &&
            !familyFitOverride
        val exactFamilyDriftBlocked = exactFamilySpeciesLock &&
            currentSpecies != null &&
            replacementCandidate.species != currentSpecies &&
            PokemonFamilyRegistry.isSameFamily(context, replacementCandidate.species, currentSpecies) &&
            !uniqueCandyOverride &&
            !evolutionFamilyOverride &&
            !moveOverride &&
            !familyFitOverride &&
            !nicknameOverride
        val exactSpeciesAuthorityBlock = exactParsedSpeciesLock &&
            currentSpecies != null &&
            replacementCandidate.species != currentSpecies &&
            PokemonFamilyRegistry.isSameFamily(context, replacementCandidate.species, currentSpecies) &&
            moveHint == null &&
            !uniqueCandyOverride &&
            !candyFamilyAuthorityOverride &&
            !moveOverride
        val strongFamilyNameAuthorityBlock = !currentSpecies.isNullOrBlank() &&
            currentHasStrongTextAnchor &&
            replacementCandidate.species != currentSpecies &&
            PokemonFamilyRegistry.isSameFamily(context, replacementCandidate.species, currentSpecies) &&
            moveHint == null &&
            !uniqueCandyOverride &&
            !candyFamilyAuthorityOverride &&
            !moveOverride
        val shouldReplaceBase = currentSpecies.isNullOrBlank() ||
            currentSpecies.equals("Unknown", ignoreCase = true) ||
            uniqueCandyOverride ||
            candyFamilyAuthorityOverride ||
            evolutionFamilyOverride ||
            moveOverride ||
            familyFitOverride ||
            nicknameOverride ||
            (currentScore != null && replacementCandidate.species != currentScore.species && replacementCandidate.totalScore >= currentScore.totalScore + 0.12) ||
            (currentScore == null && replacementCandidate.totalScore >= 0.55)
        // HARD BLOCK: parseName exact match + no contradicting candy/move = NEVER replace
        val directMatchBlock = directParsedSpeciesMatch &&
            !currentLooksLikeNickname &&
            tracedPokemon.candyName.isNullOrBlank() &&
            moveHint == null
        val shouldReplace = shouldReplaceBase && !(
            strongSpeciesLock &&
                !uniqueCandyOverride &&
                !evolutionFamilyOverride &&
                !moveOverride &&
                !familyFitOverride
            )
            && !lowConfidenceFamilyOverride
            && !exactFamilyDriftBlocked
            && !exactSpeciesAuthorityBlock
            && !strongFamilyNameAuthorityBlock
            && !directMatchBlock

        val topSummary = scored.take(3).joinToString(" | ") {
            "${it.species}:total=${"%.3f".format(it.totalScore)},text=${"%.3f".format(it.textScore)},fit=${"%.3f".format(it.fitScore)},size=${"%.3f".format(it.sizeScore)},move=${"%.1f".format(it.moveScore)}"
        }
        if (!shouldReplace) {
            if (moveHint != null || (currentScore != null && currentScore.fitScore <= 0.35) || shortRawName || weakNameSignal) {
                Log.d(
                    "SpeciesRefiner",
                    "Species kept: current=$currentSpecies raw='$rawName' candy=${tracedPokemon.candyName} candyFamilySize=$candyFamilySize move=$moveHint weakName=$weakNameSignal nickname=$currentLooksLikeNickname top=[$topSummary]"
                )
            }
            return tracedPokemon
        }

        Log.d(
            "SpeciesRefiner",
            "Species refined: current=$currentSpecies -> best=${replacementCandidate.species} (score=${replacementCandidate.totalScore}, text=${replacementCandidate.textScore}, fit=${replacementCandidate.fitScore}, size=${replacementCandidate.sizeScore}, move=${replacementCandidate.moveScore}, moveHint=$moveHint, candy=${tracedPokemon.candyName}, candyFamilySize=$candyFamilySize, weakName=$weakNameSignal, nickname=$currentLooksLikeNickname, top=[$topSummary])"
        )
        return tracedPokemon.copy(
            name = replacementCandidate.species,
            realName = replacementCandidate.species
        )
    }

    private fun extractRawField(rawOcrText: String, key: String): String {
        return rawOcrText.split("|")
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
    }

    private data class CandidateScore(
        val species: String,
        val totalScore: Double,
        val textScore: Double,
        val fitScore: Double,
        val moveScore: Double,
        val sizeScore: Double,
        val hpPossible: Boolean,
        val cpPossible: Boolean
    )

    private fun normalizeName(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun hasStrongSpeciesAnchor(rawValue: String, species: String?): Boolean {
        if (species.isNullOrBlank()) return false
        val raw = normalizeName(rawValue)
        val target = normalizeName(species)
        if (raw.length < 5 || target.length < 5) return false
        val prefixMatch = sharedPrefixLength(raw, target)
        val suffixMatch = sharedSuffixLength(raw, target)
        val anchorThreshold = (target.length * 0.55f).toInt().coerceAtLeast(4)
        return prefixMatch >= anchorThreshold || suffixMatch >= anchorThreshold
    }

    private fun sharedPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var index = 0
        while (index < limit && a[index] == b[index]) {
            index++
        }
        return index
    }

    private fun sharedSuffixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var count = 0
        while (count < limit && a[a.length - 1 - count] == b[b.length - 1 - count]) {
            count++
        }
        return count
    }
}
