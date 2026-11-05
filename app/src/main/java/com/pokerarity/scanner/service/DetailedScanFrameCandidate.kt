package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.OcrFrameResult

/** A detailed OCR pass revisits an existing capture; it is not a new frame. */
internal fun associateDetailedObservationWithSourceFrame(
    detailed: PokemonData,
    source: PokemonData
): PokemonData {
    val sourceFrameIndex = source.recognitionObservation?.frameIndex ?: return detailed
    val detailedObservation = detailed.recognitionObservation ?: return detailed
    return detailed.copy(
        recognitionObservation = detailedObservation.copy(frameIndex = sourceFrameIndex)
    )
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
    return ScanFrameCandidate(
        path = bestEntry.path,
        data = detailedPokemon,
        cpQuality = bestEntry.cpQuality,
        speciesEvidence = speciesEvidence,
        recognitionContext = bestEntry.recognitionContext,
        fieldCandidates = detailed.diagnostic.fieldCandidates
    )
}
