// Purpose: Phase 2E compatibility + PokemonData integration tests for the identity contract.
package com.pokerarity.scanner.data.model

import com.pokerarity.scanner.util.ocr.RecognitionIdentityFactory
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionIdentityCompatTest {

    private fun baseIdentity(): RecognitionIdentity = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN,
        canonicalSpecies = "Vulpix",
        speciesAuthority = SpeciesEvidenceReason.EXACT,
        speciesReasonCodes = listOf(SpeciesEvidenceReason.EXACT),
        formStatus = RecognitionFormStatus.UNKNOWN,
        shiny = RecognitionTruth.UNKNOWN,
        shadow = RecognitionTruth.UNKNOWN,
        purified = RecognitionTruth.UNKNOWN,
        lucky = RecognitionTruth.UNKNOWN,
        costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.UNKNOWN,
        xxs = RecognitionTruth.UNKNOWN,
        xxl = RecognitionTruth.UNKNOWN,
        locationCard = RecognitionTruth.UNKNOWN
    )

    // ── Tri-state -> compatibility boolean mapping ────────────────────────

    @Test
    fun trueMapsToTrue_falseAndUnknownMapToFalse() {
        assertTrue(RecognitionIdentityCompat.toBoolean(RecognitionTruth.TRUE))
        assertFalse(RecognitionIdentityCompat.toBoolean(RecognitionTruth.FALSE))
        assertFalse(RecognitionIdentityCompat.toBoolean(RecognitionTruth.UNKNOWN))
    }

    @Test
    fun unknownNeverMapsToAnAffirmativeFlag() {
        val unknownIdentity = baseIdentity()
        val features = RecognitionIdentityCompat.toVisualFeatures(unknownIdentity)

        assertFalse(features.isShiny)
        assertFalse(features.isShadow)
        assertFalse(features.isPurified)
        assertFalse(features.isLucky)
        assertFalse(features.hasCostume)
        assertFalse(features.hasSpecialForm)
        assertFalse(features.isXXS)
        assertFalse(features.isXXL)
        assertFalse(features.hasLocationCard)
    }

    @Test
    fun compatibilityViewCannotDistinguishFalseFromUnknown() {
        // FALSE and UNKNOWN produce identical compatibility booleans, so the legacy
        // view cannot become a source of FALSE authority: a compatibility `false` is
        // never positive evidence of absence.
        val demoted = RecognitionIdentityCompat.toVisualFeatures(
            baseIdentity().copy(shiny = RecognitionTruth.FALSE)
        )
        val notDetected = RecognitionIdentityCompat.toVisualFeatures(
            baseIdentity().copy(shiny = RecognitionTruth.UNKNOWN)
        )

        assertEquals(demoted.isShiny, notDetected.isShiny)
        assertFalse(demoted.isShiny)
    }

    @Test
    fun compatViewRoundTripDoesNotAmplifyFalseAuthority() {
        // Rebuilding the contract from compatibility-view booleans (no demotion signal)
        // yields UNKNOWN, never FALSE — the compatibility layer cannot feed back.
        val original = baseIdentity().copy(
            shiny = RecognitionTruth.FALSE,
            xxs = RecognitionTruth.FALSE
        )
        val compat = RecognitionIdentityCompat.toVisualFeatures(original)
        val rebuilt = RecognitionIdentityFactory.build(
            RecognitionIdentityFactory.Input(
                speciesEvidence = SpeciesEvidence(
                    selectedCanonicalSpecies = "Vulpix",
                    authority = SpeciesAuthority.EXACT_CANONICAL,
                    profileStatus = SpeciesProfileStatus.COMPATIBLE,
                    reasonCodes = listOf(SpeciesEvidenceReason.EXACT),
                    observationsAgree = true,
                    authorityConflict = false
                ),
                scanAccepted = true,
                lockedSpecies = "Vulpix",
                classifierSpecies = null,
                fullMatchWinnerSpecies = null,
                formCandidates = emptyList(),
                supportedFormIds = setOf("VULPIX_NORMAL"),
                mergedFeatures = compat,
                phase2ShinyDemoted = false,
                sizeTag = null
            )
        )

        assertEquals(RecognitionTruth.UNKNOWN, rebuilt.shiny)
        assertEquals(RecognitionTruth.UNKNOWN, rebuilt.xxs)
    }

    @Test
    fun trueFlagsMapThrough() {
        val features = RecognitionIdentityCompat.toVisualFeatures(
            baseIdentity().copy(
                shiny = RecognitionTruth.TRUE,
                lucky = RecognitionTruth.TRUE,
                costume = RecognitionTruth.TRUE,
                xxl = RecognitionTruth.TRUE
            )
        )

        assertTrue(features.isShiny)
        assertTrue(features.isLucky)
        assertTrue(features.hasCostume)
        assertTrue(features.isXXL)
        assertFalse(features.isShadow)
    }

    // ── PokemonData integration ───────────────────────────────────────────

    @Test
    fun pokemonDataDefaultsToNullIdentity_forLegacyConstructedResults() {
        val legacy = PokemonData(
            cp = 500, hp = 60, maxHp = 60, name = "Pikachu", realName = "Pikachu",
            candyName = null, megaEnergy = null, weight = null, height = null,
            stardust = null, caughtDate = null
        )

        assertNull(legacy.recognitionIdentity)
    }

    @Test
    fun compatibilityNameStringsDoNotOverrideTheExplicitIdentity() {
        val contract = identity(canonicalSpeciesIfKnown = "Vulpix")
        val pokemon = PokemonData(
            cp = 500, hp = 60, maxHp = 60,
            name = "SomeNickname", realName = "SomeNickname",
            candyName = null, megaEnergy = null, weight = null, height = null,
            stardust = null, caughtDate = null,
            recognitionIdentity = contract
        )

        // The explicit contract, not the nullable compatibility strings, is the
        // recognition authority; rewriting the strings leaves the identity untouched.
        val renamed = pokemon.copy(name = "Ninetales", realName = "Ninetales")

        assertEquals("Vulpix", renamed.recognitionIdentity?.canonicalSpecies)
        assertEquals(RecognitionSpeciesStatus.KNOWN, renamed.recognitionIdentity?.speciesStatus)
        assertSame(contract, renamed.recognitionIdentity)
    }

    private fun identity(canonicalSpeciesIfKnown: String): RecognitionIdentity = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN,
        canonicalSpecies = canonicalSpeciesIfKnown,
        speciesAuthority = SpeciesEvidenceReason.EXACT,
        speciesReasonCodes = listOf(SpeciesEvidenceReason.EXACT),
        formStatus = RecognitionFormStatus.UNKNOWN,
        shiny = RecognitionTruth.UNKNOWN,
        shadow = RecognitionTruth.UNKNOWN,
        purified = RecognitionTruth.UNKNOWN,
        lucky = RecognitionTruth.UNKNOWN,
        costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.UNKNOWN,
        xxs = RecognitionTruth.UNKNOWN,
        xxl = RecognitionTruth.UNKNOWN,
        locationCard = RecognitionTruth.UNKNOWN
    )
}
