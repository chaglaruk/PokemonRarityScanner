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

    companion object {
        private const val WEAK_NAME_CONFIDENCE = 0.56
        private const val NICKNAME_SCORE_THRESHOLD = 0.40
        private const val TRUSTED_RANK_SCORE = 0.48
        private const val ANCHOR_CONFIDENCE = 0.32
        private const val PRIOR_FLOOR = 0.28

        private val WEAK_WEIGHTS = Triple(0.16, 0.46, 0.22)
        private val STRONG_WEIGHTS = Triple(0.34, 0.40, 0.14)
        private const val OBSERVED_WEIGHT = 0.08
        private const val PHYSICAL_WEIGHT = 0.10

        private const val CANDY_BONUS = 0.10
        private const val CANDY_EXACT_BONUS = 0.26
        private const val FAMILY_BONUS = 0.04
        private const val SHORT_EXTENSION_BONUS = 0.12

        private const val MOVE_PENALTY = 0.05
        private const val NICKNAME_PENALTY = 0.08
        private const val PROFILE_MISMATCH_PENALTY = 0.18

        private const val PRIOR_NICKNAME = 0.10
        private const val PRIOR_WEAK = 0.18

        private const val PROFILE_MISMATCH_SCORE = 0.20
        private const val ARC_DIFF_THRESHOLD = 10.0

        private const val FIT_LOCK_THRESHOLD = 0.32
        private const val FIT_GAP = 0.12
        private const val FIT_GAP_LARGE = 0.18
        private const val FIT_GAP_SMALL = 0.08
        private const val SIZE_GAP = 0.12
        private const val SIZE_GAP_LARGE = 0.20
        private const val TOTAL_GAP = 0.03
        private const val TOTAL_GAP_SMALL = 0.02
        private const val TOTAL_GAP_LARGE = 0.04

        private const val FAMILY_FIT_OVERRIDE_MIN = 0.60
        private const val NICKNAME_FIT_OVERRIDE_MIN = 0.62
        private const val CANDY_AUTHORITY_FIT = 0.45
        private const val CANDY_AUTHORITY_TOTAL = 0.25
        private const val UNIQUE_CANDY_FIT = 0.48
    }

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
        val weakNameSignal = shortRawName || topTextConfidence < WEAK_NAME_CONFIDENCE
        val directParsedSpeciesMatch =
            parsedRawName.equals(currentSpecies, ignoreCase = true) ||
                parsedFallbackName.equals(currentSpecies, ignoreCase = true)
        val currentLooksLikeNickname = currentRankScore < NICKNAME_SCORE_THRESHOLD && !directParsedSpeciesMatch
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
                (!currentInitialFit.cpPossible && currentInitialFit.minArcDiff >= ARC_DIFF_THRESHOLD) ||
                    (!currentInitialFit.cpPossible && currentInitialFit.score <= PROFILE_MISMATCH_SCORE)
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
                currentRankScore >= TRUSTED_RANK_SCORE ||
                    exactParsedSpeciesLock ||
                    directParsedSpeciesMatch ||
                    (currentHasStrongTextAnchor && topTextConfidence >= ANCHOR_CONFIDENCE)
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
                    currentLooksLikeNickname -> PRIOR_NICKNAME
                    weakNameSignal -> PRIOR_WEAK
                    else -> currentRankScore.coerceAtLeast(PRIOR_FLOOR)
                }
            } else {
                0.0
            }
            val textScore = maxOf(rawScore, fallbackScore, currentPrior)
            val fit = rarityCalculator.scoreSpeciesFit(pokemon, candidate)
            val moveScore = PokemonMoveRegistry.moveMatchScore(context, candidate, moveHint)
            val candyBonus = if (PokemonFamilyRegistry.isSameFamily(context, candidate, pokemon.candyName)) CANDY_BONUS else 0.0
            val candyExactBonus = if (uniqueCandySpecies && candidate.equals(pokemon.candyName, ignoreCase = true)) CANDY_EXACT_BONUS else 0.0
            val familyBonus = if (PokemonFamilyRegistry.isSameFamily(context, candidate, currentSpecies)) FAMILY_BONUS else 0.0
            val observedProfileScore = observedProfileCandidates.firstOrNull { it.species.equals(candidate, ignoreCase = true) }?.score ?: 0.0
            val physicalProfileScore = physicalCandidates.firstOrNull { it.species.equals(candidate, ignoreCase = true) }?.score ?: 0.0
            val weights = if (weakNameSignal || moveHint != null || currentLooksLikeNickname) {
                WEAK_WEIGHTS
            } else {
                STRONG_WEIGHTS
            }
            val movePenalty = if (moveHint != null && moveScore == 0.0) MOVE_PENALTY else 0.0
            val nicknamePenalty = if (currentSpecies.equals(candidate, ignoreCase = true) && currentLooksLikeNickname && textScore < 0.30) NICKNAME_PENALTY else 0.0
            val profileMismatchPenalty = if (
                currentHasProfileMismatch &&
                currentSpecies.equals(candidate, ignoreCase = true)
            ) {
                PROFILE_MISMATCH_PENALTY
            } else {
                0.0
            }
            val shortSpeciesExtensionBonus = if (
                currentHasProfileMismatch &&
                rawExtendsCurrentSpecies &&
                !currentSpecies.equals(candidate, ignoreCase = true) &&
                normalizeName(candidate).startsWith(normalizedCurrentSpecies)
            ) {
                SHORT_EXTENSION_BONUS
            } else {
                0.0
            }
            CandidateScore(
                species = candidate,
                totalScore = (
                    weights.first * textScore +
                        weights.second * fit.score +
                        weights.third * moveScore +
                        OBSERVED_WEIGHT * observedProfileScore +
                        PHYSICAL_WEIGHT * physicalProfileScore +
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
            PokemonFamilyRegistry.isSameFamily(context, it.species, pokemon.candyName)
        }
        val bestAlternateCandyFamilyCandidate = scored.firstOrNull {
            PokemonFamilyRegistry.isSameFamily(context, it.species, pokemon.candyName) &&
                !it.species.equals(currentSpecies, ignoreCase = true)
        }
        val moveOverride = currentScore != null &&
            moveHint != null &&
            best.species != currentScore.species &&
            best.moveScore >= 1.0 &&
            currentScore.moveScore <= 0.0 &&
            best.totalScore >= currentScore.totalScore + TOTAL_GAP_LARGE
        val familyFitOverride = currentScore != null &&
            best.species != currentScore.species &&
            PokemonFamilyRegistry.isSameFamily(context, best.species, currentScore.species) &&
            best.fitScore >= max(FAMILY_FIT_OVERRIDE_MIN, currentScore.fitScore + FIT_GAP) &&
            (!currentScore.cpPossible || best.cpPossible || best.sizeScore >= currentScore.sizeScore + 0.10)
        val nicknameOverride = currentScore != null &&
            best.species != currentScore.species &&
            currentLooksLikeNickname &&
            best.fitScore >= max(NICKNAME_FIT_OVERRIDE_MIN, currentScore.fitScore + 0.10) &&
            best.totalScore >= currentScore.totalScore + TOTAL_GAP
        val evolutionFamilyOverride = currentScore != null &&
            bestAlternateCandyFamilyCandidate != null &&
            candyFamilySize > 1 &&
            currentSpecies.equals(pokemon.candyName, ignoreCase = true) &&
            (
                (
                    !currentScore.cpPossible &&
                        bestAlternateCandyFamilyCandidate.cpPossible &&
                        bestAlternateCandyFamilyCandidate.fitScore >= currentScore.fitScore + FIT_GAP_SMALL &&
                        bestAlternateCandyFamilyCandidate.sizeScore >= currentScore.sizeScore + SIZE_GAP
                    ) ||
                    (
                        bestAlternateCandyFamilyCandidate.fitScore >= currentScore.fitScore + FIT_GAP_LARGE &&
                            bestAlternateCandyFamilyCandidate.sizeScore >= currentScore.sizeScore + SIZE_GAP_LARGE &&
                            bestAlternateCandyFamilyCandidate.totalScore >= currentScore.totalScore + TOTAL_GAP_LARGE
                        )
                )
        val uniqueCandyOverride = uniqueCandySpecies &&
            best.species.equals(pokemon.candyName, ignoreCase = true) &&
            (currentScore == null ||
                !best.species.equals(currentScore.species, ignoreCase = true)) &&
            best.fitScore >= UNIQUE_CANDY_FIT &&
            best.totalScore >= (currentScore?.totalScore ?: 0.0) + TOTAL_GAP_SMALL
        val candyFamilyAuthorityOverride =
            !pokemon.candyName.isNullOrBlank() &&
                candyFamilySize > 1 &&
                currentSpecies != null &&
                !PokemonFamilyRegistry.isSameFamily(context, currentSpecies, pokemon.candyName) &&
                bestCandyFamilyCandidate != null &&
                !bestCandyFamilyCandidate.species.equals(currentSpecies, ignoreCase = true) &&
                bestCandyFamilyCandidate.fitScore >= CANDY_AUTHORITY_FIT &&
                bestCandyFamilyCandidate.totalScore >= CANDY_AUTHORITY_TOTAL
        val strongSpeciesLock = trustedResolvedSpecies &&
            currentScore != null &&
            currentHasStrongTextAnchor &&
            (!currentHasProfileMismatch || exactParsedSpeciesLock) &&
            (currentScore.cpPossible || currentScore.fitScore >= FIT_LOCK_THRESHOLD) &&
            moveHint == null &&
            pokemon.candyName.isNullOrBlank()
        val exactFamilySpeciesLock = !currentSpecies.isNullOrBlank() &&
            directParsedSpeciesMatch &&
            !currentHasProfileMismatch &&
            moveHint == null &&
            pokemon.candyName.isNullOrBlank()
        val replacementCandidate = if (candyFamilyAuthorityOverride) {
            bestCandyFamilyCandidate ?: best
        } else if (evolutionFamilyOverride) {
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
            pokemon.candyName.isNullOrBlank() &&
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
