package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.OcrFrameResult

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
    val alternateCp = detailed.diagnostic.crops
        .firstOrNull { it.source == "native_targeted_ocr" && it.candidateValue != null }
        ?.candidateValue?.toIntOrNull()
    val alternate = alternateCp?.takeIf {
        speciesEvidence.profileStatus == com.pokerarity.scanner.util.ocr.SpeciesProfileStatus.CONTRADICTORY &&
            it != detailedPokemon.cp
    }?.let { cp ->
        val pokemon = detailedPokemon.copy(cp = cp)
        val evidence = ScanManager.deriveSpeciesEvidence(
            detailed.diagnostic.fieldCandidates,
            pokemon,
            rarityCalculator
        )
        val independentlySupported = evidence.authority ==
            com.pokerarity.scanner.util.ocr.SpeciesAuthority.INDEPENDENT_PROFILE &&
            evidence.profileStatus == com.pokerarity.scanner.util.ocr.SpeciesProfileStatus.COMPATIBLE &&
            evidence.observationsAgree && !evidence.authorityConflict && !evidence.candidatesClose &&
            evidence.selectedCanonicalSpecies.equals(
                speciesEvidence.selectedCanonicalSpecies, ignoreCase = true
            )
        (pokemon to evidence).takeIf { independentlySupported }
    }
    val selectedPokemon = alternate?.first ?: detailedPokemon
    val selectedEvidence = alternate?.second ?: speciesEvidence
    return ScanFrameCandidate(
        path = bestEntry.path,
        data = selectedPokemon,
        cpQuality = bestEntry.cpQuality,
        speciesEvidence = selectedEvidence,
        recognitionContext = bestEntry.recognitionContext,
        fieldCandidates = detailed.diagnostic.fieldCandidates,
        recognitionWidth = detailed.diagnostic.imageWidth
    )
}
