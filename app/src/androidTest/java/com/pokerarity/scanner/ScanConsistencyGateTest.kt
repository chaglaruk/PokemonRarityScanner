package com.pokerarity.scanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanConsistencyGateTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val rarityCalculator = RarityCalculator(context)
    private val gate = ScanConsistencyGate(context, rarityCalculator)

    @Test
    fun restoresAuthoritativeSpeciesWhenCandyFamilyConflicts() {
        val authoritative = pokemon(
            name = "Eevee",
            realName = "Eevee",
            candyName = "Eevee",
            cp = 470,
            hp = 82,
            maxHp = 82,
            rawOcrText = "Name:EeveeZQY|NameHC:|Candy:Eevee Candy"
        )
        val candidate = authoritative.copy(name = "Eelektrik", realName = "Eelektrik")

        val decision = gate.evaluate(authoritative, candidate)

        assertFalse(decision.shouldRetry)
        assertEquals("Eevee", decision.pokemon.realName)
        assertEquals("corrected_to_authoritative_candy_family", decision.reason)
    }

    @Test
    fun prefersUniqueCandySpeciesWhenResolvedSpeciesDrifts() {
        val authoritative = pokemon(
            name = "Unknown",
            realName = null,
            candyName = "Minun",
            cp = 868,
            hp = 105,
            maxHp = 105,
            rawOcrText = "Name:iiM|NameHC:|Candy:MINUN CANDY"
        )
        val candidate = authoritative.copy(name = "Pikachu", realName = "Pikachu")

        val decision = gate.evaluate(authoritative, candidate)

        assertFalse(decision.shouldRetry)
        assertEquals("Minun", decision.pokemon.realName)
    }

    @Test
    fun retriesUnknownSpeciesWithoutAnyStableAnchor() {
        val authoritative = pokemon(
            name = "Unknown",
            realName = null,
            candyName = null,
            cp = null,
            hp = null,
            maxHp = null,
            rawOcrText = "Name:|NameHC:"
        )

        val decision = gate.evaluate(authoritative, authoritative)

        assertTrue(decision.shouldRetry)
        assertEquals("unknown_species", decision.reason)
    }

    private fun pokemon(
        name: String?,
        realName: String?,
        candyName: String?,
        cp: Int?,
        hp: Int?,
        maxHp: Int?,
        rawOcrText: String
    ): PokemonData {
        return PokemonData(
            cp = cp,
            hp = hp,
            maxHp = maxHp,
            name = name,
            realName = realName,
            candyName = candyName,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = null,
            rawOcrText = rawOcrText
        )
    }
}
