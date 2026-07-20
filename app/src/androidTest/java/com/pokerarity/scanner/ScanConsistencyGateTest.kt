package com.pokerarity.scanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import org.junit.Assert.assertEquals
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

        val decision = gate.evaluate(authoritative, candidate, hardEvidence("Eevee"))

        assertTrue(decision.shouldRetry)
        assertEquals("Eelektrik", decision.pokemon.realName)
        assertEquals(SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT, decision.reason)
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

        val decision = gate.evaluate(
            authoritative,
            candidate,
            SpeciesEvidence.failClosed(SpeciesProfileStatus.COMPATIBLE)
        )

        assertTrue(decision.shouldRetry)
        assertEquals("Pikachu", decision.pokemon.realName)
        assertEquals(SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT, decision.reason)
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

        val decision = gate.evaluate(
            authoritative,
            authoritative,
            SpeciesEvidence.failClosed(SpeciesProfileStatus.COMPATIBLE)
        )

        assertTrue(decision.shouldRetry)
        assertEquals(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY, decision.reason)
    }

    private fun hardEvidence(species: String): SpeciesEvidence = SpeciesEvidence(
        selectedCanonicalSpecies = species,
        authority = SpeciesAuthority.REVIEWED_ALIAS,
        profileStatus = SpeciesProfileStatus.COMPATIBLE,
        reasonCodes = listOf(
            SpeciesEvidenceReason.REVIEWED_ALIAS,
            SpeciesEvidenceReason.PROFILE_COMPATIBLE
        ),
        observationsAgree = true,
        authorityConflict = false
    )

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
