// Purpose: Regression coverage for ScanConsistencyGate species consistency edge cases.
package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
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

        val decision = gate.evaluate(authoritative, candidate, hardEvidence("Gyarados"))

        assertTrue(decision.shouldRetry)
        assertEquals("Eelektrik", decision.pokemon.name)
        assertEquals("Eelektrik", decision.pokemon.realName)
        assertEquals(SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT, decision.reason)
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

        val decision = gate.evaluate(authoritative, candidate, noAuthority())

        assertTrue(decision.shouldRetry)
        assertEquals("Eelektrik", decision.pokemon.realName)
        assertEquals(SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT, decision.reason)
    }

    @Test
    fun strongAuthoritativeAnchorBeatsCrossFamilyCandyConflict() {
        val authoritative = pokemon(
            name = "Eevee",
            realName = "Eevee",
            candyName = "Eevee",
            cp = 424,
            hp = 80,
            maxHp = 80,
            arcLevel = 0.286f,
            rawOcrText = "Name:Eevee|NameHC:|Candy:Eevee Candy"
        )
        val candidate = authoritative.copy(
            name = "Eelektrik",
            realName = "Eelektrik",
            rawOcrText = "Name:Eelektrik|NameHC:|Candy:Eevee Candy"
        )

        val decision = gate.evaluate(authoritative, candidate, hardEvidence("Eevee"))

        assertTrue(decision.toString(), decision.shouldRetry)
        assertEquals("Eelektrik", decision.pokemon.name)
        assertEquals("Eelektrik", decision.pokemon.realName)
        assertEquals(SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT, decision.reason)
    }

    @Test
    fun crossFamilyAuthorityConflictFailsClosedWithoutRewriting() {
        val authoritative = pokemon(
            name = "Eevee",
            realName = "Eevee",
            candyName = null,
            cp = 424,
            hp = 80,
            maxHp = 80,
            arcLevel = 0.286f,
            rawOcrText = "Name:Eevee|NameHC:Eevee"
        )
        val candidate = authoritative.copy(
            name = "Eelektrik",
            realName = "Eelektrik",
            rawOcrText = "Name:Eelektrik|NameHC:Eelektrik"
        )

        val decision = gate.evaluate(authoritative, candidate, hardEvidence("Eevee"))

        assertTrue(decision.shouldRetry)
        assertEquals("Eelektrik", decision.pokemon.name)
        assertEquals("Eelektrik", decision.pokemon.realName)
        assertEquals(SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT, decision.reason)
    }

    @Test
    fun uniqueCandyDoesNotAuthorizeSpecies() {
        val authoritative = pokemon(
            name = "Unknown",
            realName = null,
            candyName = "Minun",
            cp = 500,
            hp = 80,
            maxHp = 80,
            arcLevel = 0.4f,
            rawOcrText = "Name:|NameHC:|Candy:Minun Candy"
        )
        val candidate = authoritative.copy(
            name = "Pikachu",
            realName = "Pikachu",
            rawOcrText = "Name:Pikachu|NameHC:Pikachu|Candy:Minun Candy"
        )

        val decision = gate.evaluate(authoritative, candidate, noAuthority())

        assertTrue(decision.shouldRetry)
        assertEquals("Pikachu", decision.pokemon.name)
        assertEquals("Pikachu", decision.pokemon.realName)
        assertEquals(SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT, decision.reason)
    }

    @Test
    fun unknownRawNameDoesNotBecomeStrongAuthoritativeAnchor() {
        val authoritative = pokemon(
            name = "Unknown",
            realName = null,
            candyName = "Eevee",
            cp = 1152,
            hp = 115,
            maxHp = 115,
            arcLevel = 0.49f,
            rawOcrText = "Name:Unknown|NameHC:|Candy:Eevee Candy"
        )
        val candidate = authoritative.copy(
            name = "Eelektrik",
            realName = "Eelektrik",
            rawOcrText = "Name:Eelektrik|NameHC:|Candy:Eevee Candy"
        )

        val decision = gate.evaluate(authoritative, candidate, noAuthority())

        assertTrue(decision.shouldRetry)
        assertEquals("Eelektrik", decision.pokemon.realName)
        assertEquals(SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT, decision.reason)
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

        val decision = gate.evaluate(scan, scan, noAuthority())

        assertTrue(decision.shouldRetry)
        assertEquals("Unknown", decision.pokemon.name)
        assertEquals(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY, decision.reason)
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

        val decision = gate.evaluate(scan, scan, hardEvidence("Eevee"))

        assertFalse(decision.shouldRetry)
        assertEquals(scan, decision.pokemon)
        assertEquals("accepted", decision.reason)
    }

    private fun hardEvidence(species: String): SpeciesEvidence = SpeciesEvidence(
        selectedCanonicalSpecies = species,
        authority = SpeciesAuthority.EXACT_CANONICAL,
        profileStatus = SpeciesProfileStatus.COMPATIBLE,
        reasonCodes = listOf(SpeciesEvidenceReason.EXACT, SpeciesEvidenceReason.PROFILE_COMPATIBLE),
        observationsAgree = true,
        authorityConflict = false
    )

    private fun noAuthority(): SpeciesEvidence =
        SpeciesEvidence.failClosed(SpeciesProfileStatus.COMPATIBLE)

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
