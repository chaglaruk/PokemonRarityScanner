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
    private val rarityCalculator: RarityCalculator
) {

    private val textParser = TextParser(context)

    fun refine(pokemon: PokemonData): PokemonData {
        val currentSpecies = pokemon.realName ?: pokemon.name
        val rawName = extractRawField(pokemon.rawOcrText, "Name")
        val fallbackName = extractRawField(pokemon.rawOcrText, "NameHC")
        val bottomRaw = extractRawField(pokemon.rawOcrText, "Bottom")
        val parsedRawName = textParser.parseName(rawName)
        val parsedFallbackName = textParser.parseName(fallbackName)
        val moveHint = PokemonMoveRegistry.extractMoveHint(context, bottomRaw)
        val candyFamilySize = PokemonFamilyRegistry.familySize(context, pokemon.candyName)
        val uniqueCandySpecies = !pokemon.candyName.isNullOrBlank() && candyFamilySize == 1
        val currentInitialFit = currentSpecies?.let { rarityCalculator.scoreSpeciesFit(pokemon, it) }
        val rankedRaw = textParser.rankNameCandidates(rawName, limit = 6)
        val rankedFallback = textParser.rankNameCandidates(fallbackName, limit = 6)
        val currentRankScore = maxOf(
            rankedRaw.firstOrNull { it.name.equals(currentSpecies, ignoreCase = true) }?.score ?: 0.0,
            rankedFallback.firstOrNull { it.name.equals(currentSpecies, ignoreCase = true) }?.score ?: 0.0
        )
        val topTextConfidence = maxOf(rankedRaw.firstOrNull()?.score ?: 0.0, rankedFallback.firstOrNull()?.score ?: 0.0)
        val normalizedRawLength = normalizeName(rawName).length
        val shortRawName = normalizedRawLength in 1..4
        val weakNameSignal = shortRawName || topTextConfidence < 0.56
        val directParsedSpeciesMatch =
            parsedRawName.equals(currentSpecies, ignoreCase = true) ||
                parsedFallbackName.equals(currentSpecies, ignoreCase = true)
        val currentLooksLikeNickname = currentRankScore < 0.40 && !directParsedSpeciesMatch
        val currentHasStrongTextAnchor = directParsedSpeciesMatch ||
            hasStrongSpeciesAnchor(rawName, currentSpecies) ||
            hasStrongSpeciesAnchor(fallbackName, currentSpecies)
        val exactParsedSpeciesLock = !currentSpecies.isNullOrBlank() &&
            (
                parsedRawName.equals(currentSpecies, ignoreCase = true) ||
                    parsedFallbackName.equals(currentSpecies, ignoreCase = true)
                ) &&
            topTextConfidence >= 0.90
        val normalizedCurrentSpecies = normalizeName(currentSpecies.orEmpty())
        val normalizedRawName = normalizeName(rawName)
        val rawExtendsCurrentSpecies = normalizedCurrentSpecies.length in 3..4 &&
            normalizedRawName.length >= normalizedCurrentSpecies.length + 1 &&
            normalizedRawName.contains(normalizedCurrentSpecies)
        val currentHasProfileMismatch = currentInitialFit != null &&
            (
                (!currentInitialFit.cpPossible && currentInitialFit.minArcDiff >= 10.0) ||
                    (!currentInitialFit.cpPossible && currentInitialFit.score <= 0.20)
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
                currentRankScore >= 0.48 ||
                    exactParsedSpeciesLock ||
                    directParsedSpeciesMatch ||
                    (currentHasStrongTextAnchor && topTextConfidence >= 0.32)
                )
        val shouldOpenGlobalCandidates = (pokemon.candyName.isNullOrBlank() || weakNameSignal || currentLooksLikeNickname || currentHasProfileMismatch) &&
            !trustedResolvedSpecies
        val observedProfileCandidates = if (shouldOpenGlobalCandidates) {
            rarityCalculator.rankSpeciesByObservedProfile(pokemon, limit = 14)
        } else {
            emptyList()
        }
        val physicalCandidates = if (shouldOpenGlobalCandidates) {
            rarityCalculator.rankSpeciesByPhysicalProfile(pokemon, limit = 14)
        } else {
            emptyList()
        }

        val candidatePool = linkedSetOf<String>()
        currentSpecies?.let { candidatePool += it }
        pokemon.candyName?.let { candidatePool += it }
        parsedRawName?.let { candidatePool += it }
        parsedFallbackName?.let { candidatePool += it }
        candidatePool += prefixRelatedCandidates

        candidatePool += rankedRaw.take(4).map { it.name }
        candidatePool += rankedFallback.take(4).map { it.name }

        currentSpecies?.let { candidatePool += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        pokemon.candyName?.let { candidatePool += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        rankedRaw.take(3).forEach { candidate ->
            candidatePool += PokemonFamilyRegistry.getFamilyMembers(context, candidate.name)
        }
        moveHint?.let { hintedMove ->
            val moveCandidates = PokemonMoveRegistry.getSpeciesForMove(context, hintedMove)
            if (moveCandidates.size <= 24) {
                candidatePool += moveCandidates
            } else {
                candidatePool += moveCandidates
                    .map { species -> species to rarityCalculator.scoreSpeciesFit(pokemon, species).score }
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
        if (resolvedCandidates.isEmpty()) return pokemon

        val scored = resolvedCandidates.map { candidate ->
            val rawScore = textParser.rankNameCandidates(rawName, limit = 6, restrictTo = listOf(candidate))
                .firstOrNull()?.score ?: 0.0
            val fallbackScore = textParser.rankNameCandidates(fallbackName, limit = 6, restrictTo = listOf(candidate))
                .firstOrNull()?.score ?: 0.0
            val currentPrior = if (currentSpecies.equals(candidate, ignoreCase = true)) {
                when {
                    currentLooksLikeNickname -> 0.10
                    weakNameSignal -> 0.18
                    else -> currentRankScore.coerceAtLeast(0.28)
                }
            } else {
                0.0
            }
            val textScore = maxOf(rawScore, fallbackScore, currentPrior)
            val fit = rarityCalculator.scoreSpeciesFit(pokemon, candidate)
            val moveScore = PokemonMoveRegistry.moveMatchScore(context, candidate, moveHint)
            val candyBonus = if (PokemonFamilyRegistry.isSameFamily(context, candidate, pokemon.candyName)) 0.10 else 0.0
            val candyExactBonus = if (uniqueCandySpecies && candidate.equals(pokemon.candyName, ignoreCase = true)) 0.26 else 0.0
            val familyBonus = if (PokemonFamilyRegistry.isSameFamily(context, candidate, currentSpecies)) 0.04 else 0.0
            val observedProfileScore = observedProfileCandidates.firstOrNull { it.species.equals(candidate, ignoreCase = true) }?.score ?: 0.0
            val physicalProfileScore = physicalCandidates.firstOrNull { it.species.equals(candidate, ignoreCase = true) }?.score ?: 0.0
            val weights = if (weakNameSignal || moveHint != null || currentLooksLikeNickname) {
                Triple(0.16, 0.46, 0.22)
            } else {
                Triple(0.34, 0.40, 0.14)
            }
            val movePenalty = if (moveHint != null && moveScore == 0.0) 0.05 else 0.0
            val nicknamePenalty = if (currentSpecies.equals(candidate, ignoreCase = true) && currentLooksLikeNickname && textScore < 0.30) 0.08 else 0.0
            val profileMismatchPenalty = if (
                currentHasProfileMismatch &&
                currentSpecies.equals(candidate, ignoreCase = true)
            ) {
                0.18
            } else {
                0.0
            }
            val shortSpeciesExtensionBonus = if (
                currentHasProfileMismatch &&
                rawExtendsCurrentSpecies &&
                !currentSpecies.equals(candidate, ignoreCase = true) &&
                normalizeName(candidate).startsWith(normalizedCurrentSpecies)
            ) {
                0.12
            } else {
                0.0
            }
            CandidateScore(
                species = candidate,
                totalScore = (
                    weights.first * textScore +
                        weights.second * fit.score +
                        weights.third * moveScore +
                        0.08 * observedProfileScore +
                        0.10 * physicalProfileScore +
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
        val bestAlternateCandyFamilyCandidate = scored.firstOrNull {
            PokemonFamilyRegistry.isSameFamily(context, it.species, pokemon.candyName) &&
                !it.species.equals(currentSpecies, ignoreCase = true)
        }
        val moveOverride = currentScore != null &&
            moveHint != null &&
            best.species != currentScore.species &&
            best.moveScore >= 1.0 &&
            currentScore.moveScore <= 0.0 &&
            best.totalScore >= currentScore.totalScore + 0.04
        val familyFitOverride = currentScore != null &&
            best.species != currentScore.species &&
            PokemonFamilyRegistry.isSameFamily(context, best.species, currentScore.species) &&
            best.fitScore >= max(0.60, currentScore.fitScore + 0.12) &&
            (!currentScore.cpPossible || best.cpPossible || best.sizeScore >= currentScore.sizeScore + 0.10)
        val nicknameOverride = currentScore != null &&
            best.species != currentScore.species &&
            currentLooksLikeNickname &&
            best.fitScore >= max(0.62, currentScore.fitScore + 0.10) &&
            best.totalScore >= currentScore.totalScore + 0.03
        val evolutionFamilyOverride = currentScore != null &&
            bestAlternateCandyFamilyCandidate != null &&
            candyFamilySize > 1 &&
            currentSpecies.equals(pokemon.candyName, ignoreCase = true) &&
            (
                (
                    !currentScore.cpPossible &&
                        bestAlternateCandyFamilyCandidate.cpPossible &&
                        bestAlternateCandyFamilyCandidate.fitScore >= currentScore.fitScore + 0.08 &&
                        bestAlternateCandyFamilyCandidate.sizeScore >= currentScore.sizeScore + 0.12
                    ) ||
                    (
                        bestAlternateCandyFamilyCandidate.fitScore >= currentScore.fitScore + 0.18 &&
                            bestAlternateCandyFamilyCandidate.sizeScore >= currentScore.sizeScore + 0.20 &&
                            bestAlternateCandyFamilyCandidate.totalScore >= currentScore.totalScore + 0.04
                        )
                )
        val uniqueCandyOverride = uniqueCandySpecies &&
            best.species.equals(pokemon.candyName, ignoreCase = true) &&
            (currentScore == null ||
                !best.species.equals(currentScore.species, ignoreCase = true)) &&
            best.fitScore >= 0.48 &&
            best.totalScore >= (currentScore?.totalScore ?: 0.0) + 0.02
        val strongSpeciesLock = trustedResolvedSpecies &&
            currentScore != null &&
            currentHasStrongTextAnchor &&
            (!currentHasProfileMismatch || exactParsedSpeciesLock) &&
            (currentScore.cpPossible || currentScore.fitScore >= 0.32) &&
            moveHint == null &&
            pokemon.candyName.isNullOrBlank()
        val exactFamilySpeciesLock = !currentSpecies.isNullOrBlank() &&
            directParsedSpeciesMatch &&
            !currentHasProfileMismatch &&
            moveHint == null &&
            pokemon.candyName.isNullOrBlank()
        val replacementCandidate = if (evolutionFamilyOverride) {
            bestAlternateCandyFamilyCandidate ?: best
        } else {
            best
        }
        val anchoredCurrentSpecies = !currentSpecies.isNullOrBlank() &&
            (
                currentSpecies.equals(pokemon.candyName, ignoreCase = true) ||
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
        val shouldReplaceBase = currentSpecies.isNullOrBlank() ||
            currentSpecies.equals("Unknown", ignoreCase = true) ||
            uniqueCandyOverride ||
            evolutionFamilyOverride ||
            moveOverride ||
            familyFitOverride ||
            nicknameOverride ||
            (currentScore != null && replacementCandidate.species != currentScore.species && replacementCandidate.totalScore >= currentScore.totalScore + 0.12) ||
            (currentScore == null && replacementCandidate.totalScore >= 0.55)
        val shouldReplace = shouldReplaceBase && !(
            strongSpeciesLock &&
                !uniqueCandyOverride &&
                !evolutionFamilyOverride &&
                !moveOverride &&
                !familyFitOverride
            )
            && !lowConfidenceFamilyOverride
            && !exactFamilyDriftBlocked

        val topSummary = scored.take(3).joinToString(" | ") {
            "${it.species}:total=${"%.3f".format(it.totalScore)},text=${"%.3f".format(it.textScore)},fit=${"%.3f".format(it.fitScore)},size=${"%.3f".format(it.sizeScore)},move=${"%.1f".format(it.moveScore)}"
        }
        if (!shouldReplace) {
            if (moveHint != null || (currentScore != null && currentScore.fitScore <= 0.35) || shortRawName || weakNameSignal) {
                Log.d(
                    "SpeciesRefiner",
                    "Species kept: current=$currentSpecies raw='$rawName' candy=${pokemon.candyName} candyFamilySize=$candyFamilySize move=$moveHint weakName=$weakNameSignal nickname=$currentLooksLikeNickname top=[$topSummary]"
                )
            }
            return pokemon
        }

        Log.d(
            "SpeciesRefiner",
            "Species refined: current=$currentSpecies -> best=${replacementCandidate.species} (score=${replacementCandidate.totalScore}, text=${replacementCandidate.textScore}, fit=${replacementCandidate.fitScore}, size=${replacementCandidate.sizeScore}, move=${replacementCandidate.moveScore}, moveHint=$moveHint, candy=${pokemon.candyName}, candyFamilySize=$candyFamilySize, weakName=$weakNameSignal, nickname=$currentLooksLikeNickname, top=[$topSummary])"
        )
        return pokemon.copy(
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
