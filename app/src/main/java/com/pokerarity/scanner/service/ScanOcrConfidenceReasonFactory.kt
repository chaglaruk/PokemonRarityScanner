package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.OcrConfidenceReasons
import com.pokerarity.scanner.data.model.OcrConfidenceReasonsBuilder
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore

/**
 * Factory class to encapsulate the construction of [OcrConfidenceReasons] based on
 * the parsed [PokemonData] and the [RarityScore] evaluation.
 */
object ScanOcrConfidenceReasonFactory {

    /**
     * Creates an [OcrConfidenceReasons] object by extracting confidence indicators
     * and potential warnings from the current OCR results and pipeline evaluations.
     */
    fun create(
        pokemon: PokemonData,
        rarityScore: RarityScore
    ): OcrConfidenceReasons {
        val summary = rarityScore.recognitionSummary ?: rarityScore.decisionSupport?.recognitionSummary
        
        val builder = OcrConfidenceReasonsBuilder()
            .withCp(pokemon.cp)
            .withHp(pokemon.hp, pokemon.maxHp)
            .withCaughtDate(pokemon.caughtDate)
            
        if (!summary.isNullOrBlank()) {
            builder.addWarning(summary)
        }
        
        return builder.build()
    }
}
