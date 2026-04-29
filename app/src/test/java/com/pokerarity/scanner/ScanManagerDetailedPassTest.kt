package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.service.ScanManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanManagerDetailedPassTest {

    @Test
    fun missingHp_requestsDetailedPass() {
        val pokemon = basePokemon().copy(hp = null, maxHp = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            topTextConfidence = 0.95
        )

        assertTrue(shouldRun)
    }

    @Test
    fun missingOnlyStardust_doesNotRequestDetailedPass() {
        val pokemon = basePokemon().copy(stardust = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            topTextConfidence = 0.95
        )

        assertFalse(shouldRun)
    }

    @Test
    fun missingBothCosts_andWeakName_requestsDetailedPass() {
        val pokemon = basePokemon().copy(stardust = null, powerUpCandyCost = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            topTextConfidence = 0.82
        )

        assertTrue(shouldRun)
    }

    @Test
    fun reliablePrimaryAndSecondaryFields_skipDetailedPass() {
        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = basePokemon(),
            cpQuality = 0.9,
            topTextConfidence = 0.95
        )

        assertFalse(shouldRun)
    }

    @Test
    fun missingCaughtDate_requestsDetailedPass() {
        val pokemon = basePokemon().copy(caughtDate = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            topTextConfidence = 0.95
        )

        assertTrue(shouldRun)
    }

    private fun basePokemon(): PokemonData {
        return PokemonData(
            cp = 1234,
            hp = 120,
            maxHp = 120,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = "Pikachu",
            megaEnergy = null,
            weight = null,
            height = null,
            gender = null,
            stardust = 3000,
            arcLevel = 0.5f,
            caughtDate = java.util.Date(),
            rawOcrText = ""
        )
    }
}
