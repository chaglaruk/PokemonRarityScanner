package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.OcrFrameResult
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence

/** A detailed OCR pass revisits an existing capture; it is not a new frame. */
internal fun associateDetailedObservationWithSourceFrame(
    detailed: PokemonData,
    source: PokemonData
): PokemonData {
    val sourceFrameIndex = source.recognitionObservation?.frameIndex
    val detailedObservation = detailed.recognitionObservation
    return if (sourceFrameIndex == null || detailedObservation == null) {
        detailed
    } else {
        detailed.copy(
            recognitionObservation = detailedObservation.copy(frameIndex = sourceFrameIndex)
        )
    }
}

/** Builds a same-source detailed frame with its own OCR and structured identity evidence. */
internal fun detailedScanFrameCandidate(
    bestEntry: ScanFrameCandidate,
    detailed: OcrFrameResult,
    rarityCalculator: RarityCalculator
): ScanFrameCandidate {
    val detailedPokemon = associateDetailedObservationWithSourceFrame(detailed.pokemon, bestEntry.data)
    val speciesEvidence = ScanManager.deriveSpeciesEvidence(
        detailed.diagnostic.fieldCandidates,
        detailedPokemon,
        rarityCalculator
    )
    val detailedResult = detailed.copy(pokemon = detailedPokemon)
    val recovered = findNativeResolutionCpRecovery(detailedResult, speciesEvidence, rarityCalculator)
        ?: recoverFromContradictedCpWithIndependentNonCpEvidence(
            bestEntry,
            detailedResult,
            speciesEvidence,
            rarityCalculator
        )
    return ScanFrameCandidate(
        path = bestEntry.path,
        data = recovered?.first ?: detailedPokemon,
        cpQuality = bestEntry.cpQuality,
        speciesEvidence = recovered?.second ?: speciesEvidence,
        recognitionContext = bestEntry.recognitionContext,
        fieldCandidates = detailed.diagnostic.fieldCandidates,
        recognitionWidth = detailed.diagnostic.imageWidth
    )
}

private fun findNativeResolutionCpRecovery(
    detailed: OcrFrameResult,
    speciesEvidence: SpeciesEvidence,
    rarityCalculator: RarityCalculator
): Pair<PokemonData, SpeciesEvidence>? = detailed.diagnostic.crops
    .firstOrNull { it.source == "native_targeted_ocr" && it.candidateValue != null }
    ?.candidateValue?.toIntOrNull()
    ?.takeIf {
        speciesEvidence.profileStatus == SpeciesProfileStatus.CONTRADICTORY && it != detailed.pokemon.cp
    }
    ?.let { cp ->
        val recoveredPokemon = detailed.pokemon.copy(cp = cp)
        val recoveredEvidence = ScanManager.deriveSpeciesEvidence(
            detailed.diagnostic.fieldCandidates,
            recoveredPokemon,
            rarityCalculator
        )
        (recoveredPokemon to recoveredEvidence).takeIf {
            recoveredEvidence.isIndependentCompatible(speciesEvidence.selectedCanonicalSpecies, requireExact = false)
        }
    }

/**
 * When a CP contradicts its profile in both fast and native reads, do not pick a
 * value by profile fit. A species may still be accepted when stable non-CP fields
 * yield an exclusive family result that agrees with the exact title. The published
 * CP remains unknown.
 */
private fun recoverFromContradictedCpWithIndependentNonCpEvidence(
    fast: ScanFrameCandidate,
    detailedResult: OcrFrameResult,
    detailedEvidence: SpeciesEvidence,
    rarityCalculator: RarityCalculator
): Pair<PokemonData, SpeciesEvidence>? = RecoveryContext(fast, detailedResult, detailedEvidence, rarityCalculator)
    .takeIf(RecoveryContext::hasStableReadings)
    ?.recoverSpecies()

private data class RecoveryContext(
    val fast: ScanFrameCandidate,
    val detailedResult: OcrFrameResult,
    val detailedEvidence: SpeciesEvidence,
    val rarityCalculator: RarityCalculator
) {
    private val detailed: PokemonData get() = detailedResult.pokemon
    private val diagnostic get() = detailedResult.diagnostic

    fun hasStableReadings(): Boolean =
        hasValidGeometry() && hasUnambiguousAlternateCpCrops() &&
            hasStableObservations() && hasContradictedTitleEvidence()

    private fun hasValidGeometry(): Boolean =
        fast.data.cp != null && detailed.cp != null &&
            fast.recognitionWidth in 1..NativeResolutionProfileRecovery.BASELINE_OCR_MAX_WIDTH &&
            NativeResolutionProfileRecovery.canUseNativeResolution(diagnostic.imageWidth, diagnostic.imageHeight) &&
            diagnostic.imageWidth > fast.recognitionWidth

    private fun hasUnambiguousAlternateCpCrops(): Boolean {
        val nativeValues = diagnostic.crops
            .filter { it.source == "native_targeted_ocr" && it.candidateValue != null }
            .mapNotNull { it.candidateValue }
        val knownCpReads = setOfNotNull(fast.data.cp, detailed.cp)
        return nativeValues.none { raw -> raw.toIntOrNull()?.let { it !in knownCpReads } ?: true }
    }

    private fun hasStableObservations(): Boolean = fast.data.recognitionObservation?.let { first ->
        detailed.recognitionObservation?.let { second ->
            first.frameIndex == second.frameIndex && first.hasStableIdentityLabels(second) &&
                first.hasStableActionCostReads(second) && hasMatchingHpReadings()
        }
    } ?: false

    private fun hasMatchingHpReadings(): Boolean =
        fast.data.hp != null && fast.data.hp == detailed.hp &&
            fast.data.maxHp != null && fast.data.maxHp == detailed.maxHp

    private fun hasContradictedTitleEvidence(): Boolean = supportedSpecies()?.let { species ->
        val fastWithoutCosts = deriveEvidenceWithoutCosts(fast.fieldCandidates, fast.data)
        val detailedWithoutCosts = deriveEvidenceWithoutCosts(diagnostic.fieldCandidates, detailed)
        fastWithoutCosts.isExactContradictedTitle(species) &&
            detailedWithoutCosts.isExactContradictedTitle(species)
    } ?: false

    private fun supportedSpecies(): String? = fast.speciesEvidence.selectedCanonicalSpecies
        ?.takeIf { it.equals(detailedEvidence.selectedCanonicalSpecies, ignoreCase = true) }

    private fun deriveEvidenceWithoutCosts(
        fields: List<FieldCandidateDiagnostic>,
        pokemon: PokemonData
    ): SpeciesEvidence = ScanManager.deriveSpeciesEvidenceWithoutActionCosts(
        fields,
        pokemon,
        rarityCalculator,
        excludeEvolutionCost = true,
        excludePowerUpCost = true
    )

    fun recoverSpecies(): Pair<PokemonData, SpeciesEvidence>? = supportedSpecies()?.let { species ->
        detailed.recognitionObservation?.let { observation ->
            val cpFreePokemon = detailed.copy(
                cp = null,
                recognitionObservation = observation.copy(formEvidence = null)
            )
            val evidenceWithoutCosts = deriveEvidenceWithoutCosts(diagnostic.fieldCandidates, cpFreePokemon)
            val evidenceWithCosts = ScanManager.deriveSpeciesEvidence(
                diagnostic.fieldCandidates,
                cpFreePokemon,
                rarityCalculator
            )
            evidenceWithoutCosts.takeIf {
                it.isIndependentCompatible(species) &&
                    evidenceWithCosts.isIndependentCompatible(species, requireExact = false)
            }?.let {
                cpFreePokemon to it.copy(
                    reasonCodes = (it.reasonCodes + CP_PROFILE_RECOVERY_REASON).distinct()
                )
            }
        }
    }
}

private fun SpeciesEvidence.isIndependentCompatible(
    species: String? = selectedCanonicalSpecies,
    requireExact: Boolean = true
): Boolean =
    authority == SpeciesAuthority.INDEPENDENT_PROFILE &&
        profileStatus == SpeciesProfileStatus.COMPATIBLE &&
        observationsAgree && !authorityConflict && !candidatesClose &&
        species != null && selectedCanonicalSpecies.equals(species, ignoreCase = true) &&
        SpeciesEvidenceReason.INDEPENDENT_PROFILE in reasonCodes &&
        (!requireExact || SpeciesEvidenceReason.EXACT in reasonCodes)

private fun RecognitionObservation.hasStableIdentityLabels(other: RecognitionObservation): Boolean {
    val firstTypes = types?.map { it.lowercase() }?.toSet()?.takeIf { it.isNotEmpty() }
    val secondTypes = other.types?.map { it.lowercase() }?.toSet()?.takeIf { it.isNotEmpty() }
    val typesAgreeOrAreBothUnavailable = firstTypes == secondTypes
    return detailScreen && other.detailScreen &&
        !identityBlockingNumericConflict && !other.identityBlockingNumericConflict &&
        !candySpecies.isNullOrBlank() && candySpecies.equals(other.candySpecies, ignoreCase = true) &&
        typesAgreeOrAreBothUnavailable
}

private fun RecognitionObservation.hasStableActionCostReads(other: RecognitionObservation): Boolean {
    val powerUpValues = listOfNotNull(powerUpStardust, other.powerUpStardust).distinct()
    val hasPowerUpConflict =
        powerUpStardustLevelEvidence is StardustLevelEvidence.Conflict ||
            other.powerUpStardustLevelEvidence is StardustLevelEvidence.Conflict
    val evolveReads = listOf(evolutionCandyCostRead, other.evolutionCandyCostRead)
    val hasUnreadableValueOnRead = evolveReads.any {
        it.status == FieldReadStatus.READ && it.value == null
    }
    val evolveValues = evolveReads.filter {
        it.status == FieldReadStatus.READ
    }.mapNotNull { it.value }.distinct()
    return powerUpValues.size <= 1 && !hasPowerUpConflict &&
        evolveReads.none { it.status == FieldReadStatus.CONFLICT } &&
        !hasUnreadableValueOnRead && evolveValues.size <= 1
}

private fun SpeciesEvidence.isExactContradictedTitle(species: String): Boolean =
    authority == SpeciesAuthority.EXACT_CANONICAL &&
        selectedCanonicalSpecies.equals(species, ignoreCase = true) &&
        profileStatus == SpeciesProfileStatus.CONTRADICTORY &&
        SpeciesEvidenceReason.EXACT in reasonCodes &&
        observationsAgree && !authorityConflict && !candidatesClose

private const val CP_PROFILE_RECOVERY_REASON = "cp_profile_contradiction_recovered_from_independent_non_cp_evidence"
