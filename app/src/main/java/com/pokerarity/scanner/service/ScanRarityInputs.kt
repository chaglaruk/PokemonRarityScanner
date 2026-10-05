// Purpose: Phase 2F seam over repository-backed rarity inputs for the scan pipeline.
package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.LiveEventContext
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures

/**
 * Phase 2F bounded seam: the pipeline's rarity-input lookups (base rarity, event bonus,
 * live event context) go through one interface so ownership integration tests can run
 * the real pipeline without touching persistent storage. Production implementation
 * delegates to [com.pokerarity.scanner.data.repository.PokemonRepository].
 */
internal interface ScanRarityInputs {
    suspend fun baseRarity(name: String?): Int

    suspend fun eventBonus(pokemon: PokemonData, features: VisualFeatures): Int

    suspend fun liveEventContext(pokemon: PokemonData, features: VisualFeatures): LiveEventContext?
}
