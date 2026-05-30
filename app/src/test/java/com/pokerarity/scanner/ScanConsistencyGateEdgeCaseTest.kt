// Purpose: Regression coverage for ScanConsistencyGate species consistency edge cases.
package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScanConsistencyGateEdgeCaseTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val rarityCalculator = RarityCalculator(context)
    private val gate = ScanConsistencyGate(context, rarityCalculator)

    @Test
    fun weakOcrNameDoesNotOverrideStrongCpHpCompatibleAuthoritativeSpecies() {
        val authoritative = pokemon(
            name = "Gyarados",
            realName = "Gyarados",
            candyName = null,
            cp = 2319,
            hp = 150,
            maxHp = 150,
            arcLevel = 0.49f,
            rawOcrText = "Name:Gyarados|NameHC:"
        )
        val candidate = authoritative.copy(
            name = "Eelektrik",
            realName = "Eelektrik",
            rawOcrText = "Name:Eelektrik|NameHC:"
        )

        val decision = gate.evaluate(authoritative, candidate)

        assertFalse(decision.shouldRetry)
        assertEquals("Gyarados", decision.pokemon.name)
        assertEquals("Gyarados", decision.pokemon.realName)
        assertEquals("restored_authoritative_species", decision.reason)
    }

    @Test
    fun crossFamilyDriftRetriesWhenCandyFamilyAndNumericEvidenceConflict() {
        val authoritative = pokemon(
            name = "Unknown",
            realName = null,
            candyName = "Eevee",
            cp = 1152,
            hp = 115,
            maxHp = 115,
            arcLevel = 0.49f,
            rawOcrText = "Name:|NameHC:|Candy:Eevee Candy"
        )
        val candidate = authoritative.copy(
            name = "Eelektrik",
            realName = "Eelektrik",
            rawOcrText = "Name:Eelektrik|NameHC:|Candy:Eevee Candy"
        )

        val decision = gate.evaluate(authoritative, candidate)

        assertTrue(decision.shouldRetry)
        assertEquals("Eelektrik", decision.pokemon.realName)
        assertEquals("cross_family_conflict", decision.reason)
    }

    @Test
    fun unknownSpeciesWithStrongNumericEvidenceRetriesWithoutUnsafeCorrection() {
        val scan = pokemon(
            name = "Unknown",
            realName = null,
            candyName = null,
            cp = 424,
            hp = 80,
            maxHp = 80,
            arcLevel = 0.286f,
            rawOcrText = "Name:|NameHC:"
        )

        val decision = gate.evaluate(scan, scan)

        assertTrue(decision.shouldRetry)
        assertEquals("Unknown", decision.pokemon.name)
        assertEquals("unknown_species", decision.reason)
    }

    @Test
    fun consistentSpeciesCpHpAndDateDataRemainAcceptedUnchanged() {
        val caughtDate = Date(1_700_000_000_000L)
        val scan = pokemon(
            name = "Eevee",
            realName = "Eevee",
            candyName = null,
            cp = 424,
            hp = 80,
            maxHp = 80,
            arcLevel = 0.286f,
            caughtDate = caughtDate,
            rawOcrText = "Name:Eevee|NameHC:Eevee|Date:2023-11-14"
        )

        val decision = gate.evaluate(scan, scan)

        assertFalse(decision.shouldRetry)
        assertEquals(scan, decision.pokemon)
        assertEquals("accepted", decision.reason)
    }

    private fun pokemon(
        name: String?,
        realName: String?,
        candyName: String?,
        cp: Int?,
        hp: Int?,
        maxHp: Int?,
        arcLevel: Float?,
        caughtDate: Date? = null,
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
            arcLevel = arcLevel,
            caughtDate = caughtDate,
            rawOcrText = rawOcrText
        )
    }
}
