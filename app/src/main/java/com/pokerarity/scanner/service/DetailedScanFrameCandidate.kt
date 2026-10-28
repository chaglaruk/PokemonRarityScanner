package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.OcrFrameResult

/** Builds a same-source detailed frame with its own OCR and structured identity evidence. */
internal fun detailedScanFrameCandidate(
    bestEntry: ScanFrameCandidate,
    detailed: OcrFrameResult,
    rarityCalculator: RarityCalculator
): ScanFrameCandidate = ScanFrameCandidate(
    path = bestEntry.path,
    data = detailed.pokemon,
    cpQuality = bestEntry.cpQuality,
    speciesEvidence = ScanManager.deriveSpeciesEvidence(
        detailed.diagnostic.fieldCandidates,
        detailed.pokemon,
        rarityCalculator
    ),
    recognitionContext = bestEntry.recognitionContext,
    fieldCandidates = detailed.diagnostic.fieldCandidates
)
