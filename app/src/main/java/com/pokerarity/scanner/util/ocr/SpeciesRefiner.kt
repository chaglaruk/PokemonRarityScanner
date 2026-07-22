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
        val currentSpecies = pokemon.realName ?: pokemon.name
        val rawName = extractRawField(pokemon.rawOcrText, "Name")
        val fallbackName = extractRawField(pokemon.rawOcrText, "NameHC")
        val dynamicName = extractRawField(pokemon.rawOcrText, "NameDynamic")
        val nameAuthority = summarizeNameAuthority(
            currentSpecies,
            rawName,
            fallbackName,
            dynamicName,
            fieldCandidates
        )
        val candyAuthority = summarizeCandyAuthority(pokemon.candyName, fieldCandidates)
        val trustedCandyName = pokemon.candyName.takeIf { candyAuthority == CandyAuthority.RELIABLE }
        val resolverResolution = speciesFormResolver.resolve(
            resolverInput(pokemon, trustedCandyName),
            fieldCandidates
        )
        val tracedPokemon = pokemon.copy(speciesResolverTrace = resolverResolution.trace)
        val bottomRaw = extractRawField(tracedPokemon.rawOcrText, "Bottom")
        val acceptedNameSpecies = nameAuthority.acceptedSpecies
        val moveHint = PokemonMoveRegistry.extractMoveHint(context, bottomRaw)
        val candyFamilySize = PokemonFamilyRegistry.familySize(context, trustedCandyName)
        val uniqueCandySpecies = !trustedCandyName.isNullOrBlank() && candyFamilySize == 1
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
        val directParsedSpeciesMatch = nameAuthority.hardAuthorityMatchesCurrent
        val currentLooksLikeNickname = currentRankScore < config.nicknameScoreThreshold && !directParsedSpeciesMatch
        val exactParsedSpeciesLock = nameAuthority.hardAuthorityMatchesCurrent
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
            textParser.findNamesWithPrefix(normalizeName(acceptedNameSpecies ?: currentSpecies.orEmpty()), limit = 8)
        } else {
            emptyList()
        }
        val trustedResolvedSpecies = !currentSpecies.isNullOrBlank() && nameAuthority.hardAuthorityMatchesCurrent
        val shouldOpenGlobalCandidates = (trustedCandyName.isNullOrBlank() || weakNameSignal || currentLooksLikeNickname || currentHasProfileMismatch) &&
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
        trustedCandyName?.let { candidatePool += it }
        acceptedNameSpecies?.let { candidatePool += it }
        resolverResolution.species?.let { candidatePool += it }
        candidatePool += resolverResolution.alternatives.take(4).map { it.species }
        candidatePool += prefixRelatedCandidates

        candidatePool += rankedRaw.take(4).map { it.name }
        candidatePool += rankedFallback.take(4).map { it.name }

        currentSpecies?.let { candidatePool += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        trustedCandyName?.let { candidatePool += PokemonFamilyRegistry.getFamilyMembers(context, it) }
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
        if (resolvedCandidates.isEmpty()) {
            return FinalTraceDecision(
                    finalSpecies = currentSpecies,
                    nameAuthority = nameAuthority,
                    candyAuthority = candyAuthority,
                    currentHasProfileMismatch = currentHasProfileMismatch && hasObservedProfile(tracedPokemon),
                    resolverProposalOnly = resolverResolution.species != null &&
                        nameAuthority.hardAcceptedSpecies == null
                ).applyTo(tracedPokemon)
        }

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
            val candyBonus = if (PokemonFamilyRegistry.isSameFamily(context, candidate, trustedCandyName)) {
                config.candyBonus
            } else {
                0.0
            }
            val candyExactBonus = if (uniqueCandySpecies && candidate.equals(trustedCandyName, ignoreCase = true)) {
                config.candyExactBonus
            } else {
                0.0
            }
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
        val acceptedNameCandidate = nameAuthority.acceptedSpecies?.let { accepted ->
            scored.firstOrNull { it.species.equals(accepted, ignoreCase = true) }
        }
        val bestCandyFamilyCandidate = scored.firstOrNull {
            PokemonFamilyRegistry.isSameFamily(context, it.species, trustedCandyName)
        }
        val bestAlternateCandyFamilyCandidate = scored.firstOrNull {
            PokemonFamilyRegistry.isSameFamily(context, it.species, trustedCandyName) &&
                !it.species.equals(currentSpecies, ignoreCase = true)
        }
        val hasObservedProfile = tracedPokemon.hp != null && tracedPokemon.arcLevel != null
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
        val evolutionFamilyOverride = currentScore != null &&
            bestAlternateCandyFamilyCandidate != null &&
            hasObservedProfile &&
            candyFamilySize > 1 &&
            currentSpecies.equals(trustedCandyName, ignoreCase = true) &&
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
                ) &&
            bestAlternateCandyFamilyCandidate.totalScore >= currentScore.totalScore + config.totalGapSmall
        val uniqueCandyOverride = hasObservedProfile &&
            uniqueCandySpecies &&
            best.species.equals(trustedCandyName, ignoreCase = true) &&
            (currentScore == null ||
                !best.species.equals(currentScore.species, ignoreCase = true)) &&
            best.fitScore >= config.uniqueCandyFit &&
            best.totalScore >= (currentScore?.totalScore ?: 0.0) + config.totalGapSmall
        val candyFamilyAuthorityOverride =
            hasObservedProfile &&
                !trustedCandyName.isNullOrBlank() &&
                candyFamilySize > 1 &&
                currentSpecies != null &&
                !PokemonFamilyRegistry.isSameFamily(context, currentSpecies, trustedCandyName) &&
                bestCandyFamilyCandidate != null &&
                !bestCandyFamilyCandidate.species.equals(currentSpecies, ignoreCase = true) &&
                bestCandyFamilyCandidate.fitScore >= config.candyAuthorityFit &&
                bestCandyFamilyCandidate.fitScore >= (currentScore?.fitScore ?: 0.0) + config.fitGapSmall &&
                bestCandyFamilyCandidate.totalScore >=
                (currentScore?.totalScore ?: 0.0) + config.totalGapSmall
        val strongSpeciesLock = trustedResolvedSpecies &&
            currentScore != null &&
            (!currentHasProfileMismatch || exactParsedSpeciesLock) &&
            (currentScore.cpPossible || currentScore.fitScore >= config.fitLockThreshold) &&
            moveHint == null
        val exactFamilySpeciesLock = !currentSpecies.isNullOrBlank() &&
            directParsedSpeciesMatch &&
            !currentHasProfileMismatch &&
            moveHint == null
        val acceptedNameOverride = acceptedNameCandidate != null &&
            nameAuthority.hardAcceptedSpecies != null &&
            !acceptedNameCandidate.species.equals(currentSpecies, ignoreCase = true)
        val replacementCandidate = when {
            candyFamilyAuthorityOverride -> bestCandyFamilyCandidate ?: best
            evolutionFamilyOverride -> bestAlternateCandyFamilyCandidate ?: best
            acceptedNameOverride -> requireNotNull(acceptedNameCandidate)
            else -> best
        }
        val anchoredCurrentSpecies = !currentSpecies.isNullOrBlank() &&
            (
                currentSpecies.equals(trustedCandyName, ignoreCase = true) ||
                    acceptedNameSpecies.equals(currentSpecies, ignoreCase = true)
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
            !familyFitOverride
        val exactSpeciesAuthorityBlock = exactParsedSpeciesLock &&
            currentSpecies != null &&
            replacementCandidate.species != currentSpecies &&
            PokemonFamilyRegistry.isSameFamily(context, replacementCandidate.species, currentSpecies) &&
            moveHint == null &&
            !uniqueCandyOverride &&
            !candyFamilyAuthorityOverride &&
            !moveOverride
        val shouldReplaceBase = acceptedNameOverride ||
            uniqueCandyOverride ||
            candyFamilyAuthorityOverride ||
            evolutionFamilyOverride ||
            moveOverride ||
            familyFitOverride
        val directMatchBlock = directParsedSpeciesMatch &&
            !currentLooksLikeNickname &&
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
            && !directMatchBlock

        val refined = if (shouldReplace) {
            tracedPokemon.copy(name = replacementCandidate.species, realName = replacementCandidate.species)
        } else {
            tracedPokemon
        }
        val replacementTrigger = when {
            !shouldReplace -> ReplacementTrigger.NONE
            candyFamilyAuthorityOverride || evolutionFamilyOverride ->
                ReplacementTrigger.CANDY
            acceptedNameOverride -> ReplacementTrigger.ACCEPTED_NAME
            uniqueCandyOverride -> ReplacementTrigger.CANDY
            moveOverride -> ReplacementTrigger.MOVE
            familyFitOverride -> ReplacementTrigger.FAMILY_FIT
            else -> ReplacementTrigger.NONE
        }
        val final = FinalTraceDecision(
                finalSpecies = refined.realName ?: refined.name,
                nameAuthority = nameAuthority,
                candyAuthority = candyAuthority,
                currentHasProfileMismatch = currentHasProfileMismatch && hasObservedProfile(tracedPokemon),
                resolverProposalOnly = resolverResolution.species != null &&
                    nameAuthority.hardAcceptedSpecies == null,
                replacementTrigger = replacementTrigger,
                replacementScore = replacementCandidate.totalScore.takeIf { shouldReplace }
            ).applyTo(refined)
        Log.d(
            "SpeciesRefiner",
            "decision=${final.speciesResolverTrace?.winnerReason} replaced=$shouldReplace " +
                "trigger=$replacementTrigger name=${nameAuthority.evidenceToken} " +
                "candy=${candyAuthority.evidenceToken}"
        )
        return final
    }

    private fun extractRawField(rawOcrText: String, key: String): String {
        return rawOcrText.split("|")
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
    }

    private fun resolverInput(pokemon: PokemonData, trustedCandyName: String?): PokemonData {
        val resolverRawText = if (trustedCandyName == null) {
            pokemon.rawOcrText.split("|")
                .filterNot { it.substringBefore(":").equals("Candy", ignoreCase = true) }
                .joinToString("|")
        } else {
            pokemon.rawOcrText
        }
        return pokemon.copy(candyName = trustedCandyName, rawOcrText = resolverRawText)
    }

    private fun summarizeNameAuthority(
        currentSpecies: String?,
        rawName: String,
        fallbackName: String,
        dynamicName: String,
        fieldCandidates: List<FieldCandidateDiagnostic>
    ): NameAuthoritySummary {
        val decisions = buildList {
            listOf(rawName, fallbackName, dynamicName)
                .filter(String::isNotBlank)
                .forEach { add(textParser.decideSpeciesName(it)) }
            fieldCandidates
                .asSequence()
                .filter { it.field in NAME_FIELDS }
                .mapNotNull(FieldCandidateDiagnostic::rawText)
                .filter(String::isNotBlank)
                .forEach { add(textParser.decideSpeciesName(it)) }
        }
        val accepted = decisions.filterIsInstance<SpeciesNameDecision.Accepted>()
            .sortedWith(
                compareBy<SpeciesNameDecision.Accepted> { namePriority(it.source) }
                    .thenBy { normalizeName(it.species) }
            )
        val conflictingSpecies = accepted.map { it.species }
            .distinctBy(::normalizeName)
            .sortedBy(::normalizeName)
        val selected = accepted.firstOrNull().takeIf { conflictingSpecies.size <= 1 }
        val evidenceToken = when {
            conflictingSpecies.size > 1 -> NAME_CONFLICT
            selected?.source == SpeciesNameAcceptanceSource.EXACT_CANONICAL -> NAME_EXACT
            selected?.source == SpeciesNameAcceptanceSource.REVIEWED_ALIAS -> NAME_REVIEWED
            selected?.source == SpeciesNameAcceptanceSource.SAFE_FUZZY -> NAME_SAFE_FUZZY
            decisions.any { it is SpeciesNameDecision.Uncertain } -> NAME_UNCERTAIN
            else -> NAME_NO_MATCH
        }
        val hardAcceptedSpecies = selected
            ?.takeIf { it.source != SpeciesNameAcceptanceSource.SAFE_FUZZY }
            ?.species
        return NameAuthoritySummary(
            acceptedSpecies = selected?.species,
            acceptedSource = selected?.source,
            hardAcceptedSpecies = hardAcceptedSpecies,
            hardAuthorityMatchesCurrent = hardAcceptedSpecies.equals(currentSpecies, ignoreCase = true),
            acceptedMatchesCurrent = selected?.species.equals(currentSpecies, ignoreCase = true),
            conflictingSpecies = conflictingSpecies.takeIf { it.size > 1 }.orEmpty(),
            evidenceToken = evidenceToken
        )
    }

    private fun summarizeCandyAuthority(
        candyName: String?,
        fieldCandidates: List<FieldCandidateDiagnostic>
    ): CandyAuthority {
        val authority = if (candyName.isNullOrBlank()) {
            CandyAuthority.ABSENT
        } else {
            val trustedWinners = fieldCandidates.filter(::isTrustedCandyWinner)
            val values = trustedWinners.mapNotNull(::consistentCandyValue)
            val distinctValues = values.distinctBy(::normalizeName)
            val allValuesParsed = trustedWinners.isNotEmpty() && values.size == trustedWinners.size
            val rawMatchesCandy = trustedWinners.all { candidate ->
                normalizeName(candidate.rawText.orEmpty()).contains(normalizeName(candyName))
            }
            val valueMatchesCandy = distinctValues.singleOrNull().equals(candyName, ignoreCase = true)
            val reliable = allValuesParsed && rawMatchesCandy && valueMatchesCandy
            when {
                distinctValues.size > 1 -> CandyAuthority.CONFLICT
                reliable -> CandyAuthority.RELIABLE
                else -> CandyAuthority.UNTRUSTED
            }
        }
        return authority
    }

    private fun isTrustedCandyWinner(candidate: FieldCandidateDiagnostic): Boolean = listOf(
        candidate.field.equals("Candy", ignoreCase = true),
        candidate.source in TRUSTED_CANDY_SOURCES,
        !candidate.rawText.isNullOrBlank(),
        candidate.status == "found",
        candidate.winner,
        candidate.reason == "winner:candy_parser"
    ).all { it }

    private fun consistentCandyValue(candidate: FieldCandidateDiagnostic): String? {
        val values = listOfNotNull(candidate.parsedValue, candidate.selectedValue, candidate.parserResult)
            .filter(String::isNotBlank)
            .distinctBy(::normalizeName)
        return values.singleOrNull()
    }

    private fun hasObservedProfile(pokemon: PokemonData): Boolean = pokemon.hp != null && pokemon.arcLevel != null

    private fun namePriority(source: SpeciesNameAcceptanceSource): Int = when (source) {
        SpeciesNameAcceptanceSource.EXACT_CANONICAL -> 0
        SpeciesNameAcceptanceSource.REVIEWED_ALIAS -> 1
        SpeciesNameAcceptanceSource.SAFE_FUZZY -> 2
    }

    private data class NameAuthoritySummary(
        val acceptedSpecies: String?,
        val acceptedSource: SpeciesNameAcceptanceSource?,
        val hardAcceptedSpecies: String?,
        val hardAuthorityMatchesCurrent: Boolean,
        val acceptedMatchesCurrent: Boolean,
        val conflictingSpecies: List<String>,
        val evidenceToken: String
    )

    private data class FinalTraceDecision(
        val finalSpecies: String?,
        val nameAuthority: NameAuthoritySummary,
        val candyAuthority: CandyAuthority,
        val currentHasProfileMismatch: Boolean,
        val resolverProposalOnly: Boolean,
        val replacementTrigger: ReplacementTrigger = ReplacementTrigger.NONE,
        val replacementScore: Double? = null
    ) {
        val replaced: Boolean = replacementTrigger != ReplacementTrigger.NONE

        fun applyTo(pokemon: PokemonData): PokemonData {
            val trace = pokemon.speciesResolverTrace ?: SpeciesResolverTrace()
            val reason = finalReason()
            val confidence = finalConfidence()
            val conflictReasons = nameAuthority.conflictingSpecies.map {
                "accepted_name_conflict:${normalizeName(it)}"
            }
            return pokemon.copy(
                speciesResolverTrace = trace.copy(
                    canonicalCandidates = finalCandidates(trace, reason, confidence),
                    winningSpecies = finalSpecies,
                    winningForm = trace.winningForm.takeIf {
                        trace.winningSpecies.equals(finalSpecies, ignoreCase = true)
                    },
                    confidence = confidence,
                    winnerReason = reason,
                    loserReasons = (trace.loserReasons + conflictReasons).distinct().sorted(),
                    evidenceUsed = finalEvidence(trace),
                    fallbackPath = "species_refiner_final"
                )
            )
        }

        private fun finalConfidence(): Float {
            val retainedNameAuthority = nameAuthority.acceptedSpecies.equals(finalSpecies, ignoreCase = true)
            val nameConfidence = when (nameAuthority.acceptedSource) {
                SpeciesNameAcceptanceSource.EXACT_CANONICAL -> EXACT_AUTHORITY_CONFIDENCE
                SpeciesNameAcceptanceSource.REVIEWED_ALIAS -> REVIEWED_AUTHORITY_CONFIDENCE
                SpeciesNameAcceptanceSource.SAFE_FUZZY -> SAFE_FUZZY_AUTHORITY_CONFIDENCE
                else -> 0f
            }
            return when {
                replacementTrigger == ReplacementTrigger.CANDY -> replacementScore
                    ?.coerceAtMost(MAX_CANDY_AUTHORITY_CONFIDENCE)
                    ?.toFloat() ?: 0f
                replacementTrigger == ReplacementTrigger.ACCEPTED_NAME -> nameConfidence
                replaced -> replacementScore
                    ?.coerceAtMost(MAX_CORROBORATED_CONFIDENCE)
                    ?.toFloat() ?: 0f
                retainedNameAuthority -> nameConfidence
                else -> 0f
            }
        }

        private fun finalEvidence(trace: SpeciesResolverTrace): List<String> = buildList {
            add(nameAuthority.evidenceToken)
            candyAuthority.evidenceToken?.let(::add)
            if (replacementTrigger == ReplacementTrigger.CANDY) add(PROFILE_COMPATIBLE)
            if (currentHasProfileMismatch && replacementTrigger != ReplacementTrigger.CANDY) {
                add(PROFILE_MISMATCH)
            }
            if (resolverProposalOnly) add(RESOLVER_PROPOSAL_ONLY)
            addAll(trace.evidenceUsed.filterNot { it == "candy_family" })
        }.distinct().sorted()

        private fun finalCandidates(
            trace: SpeciesResolverTrace,
            reason: String,
            confidence: Float
        ): List<SpeciesCandidateDiagnostic> {
            val candidates = trace.canonicalCandidates.map { candidate ->
                val winner = candidate.species.equals(finalSpecies, ignoreCase = true)
                candidate.copy(
                    score = if (winner) confidence else candidate.score,
                    winner = winner,
                    loserReason = if (winner) null else "refiner_not_selected"
                )
            }.toMutableList()
            if (!finalSpecies.isNullOrBlank() && candidates.none { it.winner }) {
                candidates.add(
                    0,
                    SpeciesCandidateDiagnostic(
                        species = finalSpecies,
                        score = confidence,
                        winner = true,
                        reasons = listOf(reason)
                    )
                )
            }
            return candidates
        }

        private fun finalReason(): String =
            replacementReason()
                ?: conflictReason()
                ?: nameReason()
                ?: evidenceReason()

        private fun replacementReason(): String? = when (replacementTrigger) {
            ReplacementTrigger.CANDY -> REPLACED_RELIABLE_CANDY_PROFILE
            ReplacementTrigger.ACCEPTED_NAME -> REPLACED_ACCEPTED_NAME
            ReplacementTrigger.MOVE -> REPLACED_MOVE_CORROBORATION
            ReplacementTrigger.FAMILY_FIT -> REPLACED_EXISTING_CORROBORATION
            ReplacementTrigger.NONE -> null
        }

        private fun conflictReason(): String? = when {
            nameAuthority.conflictingSpecies.isNotEmpty() -> KEPT_NAME_CONFLICT
            candyAuthority == CandyAuthority.CONFLICT -> KEPT_CANDY_CONFLICT
            else -> null
        }

        private fun nameReason(): String? = when {
            nameAuthority.hardAuthorityMatchesCurrent &&
                nameAuthority.acceptedSource == SpeciesNameAcceptanceSource.EXACT_CANONICAL -> KEPT_EXACT
            nameAuthority.hardAuthorityMatchesCurrent &&
                nameAuthority.acceptedSource == SpeciesNameAcceptanceSource.REVIEWED_ALIAS -> KEPT_REVIEWED
            nameAuthority.acceptedMatchesCurrent &&
                nameAuthority.acceptedSource == SpeciesNameAcceptanceSource.SAFE_FUZZY &&
                candyAuthority == CandyAuthority.RELIABLE -> KEPT_SAFE_FUZZY
            else -> null
        }

        private fun evidenceReason(): String = when {
            currentHasProfileMismatch && candyAuthority != CandyAuthority.RELIABLE ->
                KEPT_PROFILE_MISMATCH
            nameAuthority.evidenceToken == NAME_UNCERTAIN && candyAuthority == CandyAuthority.RELIABLE ->
                KEPT_INSUFFICIENT_PROFILE
            resolverProposalOnly && nameAuthority.evidenceToken == NAME_NO_MATCH ->
                KEPT_RESOLVER_PROPOSAL_ONLY
            nameAuthority.evidenceToken == NAME_UNCERTAIN -> KEPT_UNCERTAIN
            nameAuthority.evidenceToken == NAME_NO_MATCH -> KEPT_NO_MATCH
            candyAuthority == CandyAuthority.UNTRUSTED -> KEPT_CANDY_UNTRUSTED
            else -> KEPT_INSUFFICIENT_MARGIN
        }

        private fun normalizeName(value: String): String =
            value.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private enum class CandyAuthority(val evidenceToken: String?) {
        ABSENT(null),
        RELIABLE(CANDY_RELIABLE),
        UNTRUSTED(CANDY_UNTRUSTED),
        CONFLICT(CANDY_CONFLICT)
    }

    private enum class ReplacementTrigger {
        NONE,
        ACCEPTED_NAME,
        CANDY,
        MOVE,
        FAMILY_FIT
    }

    private companion object {
        val NAME_FIELDS = setOf("Name", "NameDynamic", "NameHC")
        val TRUSTED_CANDY_SOURCES = setOf("candy", "candy_wide")

        const val MAX_CANDY_AUTHORITY_CONFIDENCE = 0.70
        const val EXACT_AUTHORITY_CONFIDENCE = 0.90f
        const val REVIEWED_AUTHORITY_CONFIDENCE = 0.75f
        const val SAFE_FUZZY_AUTHORITY_CONFIDENCE = 0.60f
        const val MAX_CORROBORATED_CONFIDENCE = 0.65

        const val KEPT_EXACT = "kept_exact_canonical"
        const val KEPT_REVIEWED = "kept_reviewed_alias"
        const val KEPT_SAFE_FUZZY = "kept_safe_fuzzy_with_corroboration"
        const val KEPT_UNCERTAIN = "kept_uncertain_name"
        const val KEPT_NO_MATCH = "kept_no_name_match"
        const val KEPT_NAME_CONFLICT = "kept_conflicting_accepted_names"
        const val KEPT_CANDY_CONFLICT = "kept_candy_conflicted"
        const val KEPT_CANDY_UNTRUSTED = "kept_candy_untrusted"
        const val KEPT_INSUFFICIENT_PROFILE = "kept_insufficient_profile"
        const val KEPT_PROFILE_MISMATCH = "kept_profile_mismatch"
        const val KEPT_RESOLVER_PROPOSAL_ONLY = "kept_resolver_proposal_only"
        const val KEPT_INSUFFICIENT_MARGIN = "kept_insufficient_margin"
        const val REPLACED_RELIABLE_CANDY_PROFILE = "replaced_reliable_candy_profile"
        const val REPLACED_ACCEPTED_NAME = "replaced_accepted_name"
        const val REPLACED_MOVE_CORROBORATION = "replaced_existing_move_corroboration"
        const val REPLACED_EXISTING_CORROBORATION = "replaced_existing_corroboration"

        const val NAME_EXACT = "name_exact_canonical"
        const val NAME_REVIEWED = "name_reviewed_alias"
        const val NAME_SAFE_FUZZY = "name_safe_fuzzy"
        const val NAME_UNCERTAIN = "name_uncertain"
        const val NAME_NO_MATCH = "name_no_match"
        const val NAME_CONFLICT = "name_conflict"
        const val CANDY_RELIABLE = "candy_reliable"
        const val CANDY_UNTRUSTED = "candy_untrusted"
        const val CANDY_CONFLICT = "candy_conflict"
        const val PROFILE_COMPATIBLE = "profile_compatible"
        const val PROFILE_MISMATCH = "profile_mismatch"
        const val RESOLVER_PROPOSAL_ONLY = "resolver_proposal_only"
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

}
