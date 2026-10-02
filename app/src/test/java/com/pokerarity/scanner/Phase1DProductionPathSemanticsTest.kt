package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.reconcileSpeciesProfileEvidence
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class Phase1DProductionPathSemanticsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = RarityCalculator(context)
    private val consistencyGate = ScanConsistencyGate(context, calculator)

    @Test
    fun indeterminateProfileIsNotUpgradedByGenericCompatibleStatus() {
        val evidence = evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.INDETERMINATE)

        val reconciled = reconcileSpeciesProfileEvidence(evidence, SpeciesProfileStatus.COMPATIBLE)

        assertEquals(SpeciesProfileStatus.INDETERMINATE, reconciled.profileStatus)
        assertTrue(reconciled.reasonCodes.contains(SpeciesEvidenceReason.PROFILE_INDETERMINATE))
    }

    @Test
    fun negativeProfileStatusCannotBeWeakenedByLaterGenericValidation() {
        val contradictory = reconcileSpeciesProfileEvidence(
            evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.CONTRADICTORY),
            SpeciesProfileStatus.COMPATIBLE
        )
        val impossible = reconcileSpeciesProfileEvidence(
            evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.IMPOSSIBLE),
            SpeciesProfileStatus.INDETERMINATE
        )
        val newlyNegative = reconcileSpeciesProfileEvidence(
            evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.INDETERMINATE),
            SpeciesProfileStatus.IMPOSSIBLE
        )

        assertEquals(SpeciesProfileStatus.CONTRADICTORY, contradictory.profileStatus)
        assertEquals(SpeciesProfileStatus.IMPOSSIBLE, impossible.profileStatus)
        assertEquals(SpeciesProfileStatus.IMPOSSIBLE, newlyNegative.profileStatus)
    }

    @Test
    fun exactIndeterminateContinuesThroughConsistencyGate() {
        val scan = pokemon("Pikachu", "Pikachu")

        val decision = consistencyGate.evaluate(
            scan,
            scan,
            evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.INDETERMINATE)
        )

        assertFalse(decision.shouldRetry)
        assertEquals("accepted", decision.reason)
    }

    @Test
    fun reviewedAliasIndeterminateContinuesThroughConsistencyGate() {
        val scan = pokemon("Pikachu", "Pikachu")

        val decision = consistencyGate.evaluate(
            scan,
            scan,
            evidence("Pikachu", SpeciesAuthority.REVIEWED_ALIAS, SpeciesProfileStatus.INDETERMINATE)
        )

        assertFalse(decision.shouldRetry)
        assertEquals("accepted", decision.reason)
    }

    @Test
    fun softOrUnavailableAuthorityStillFailsClosedAtConsistencyGate() {
        val scan = pokemon("Pikachu", "Pikachu")
        val blocked = listOf(
            evidence("Pikachu", SpeciesAuthority.SAFE_FUZZY, SpeciesProfileStatus.INDETERMINATE),
            evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.MISSING),
            evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.CONTRADICTORY),
            evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.IMPOSSIBLE)
        )

        blocked.forEach { speciesEvidence ->
            val decision = consistencyGate.evaluate(scan, scan, speciesEvidence)
            assertTrue(speciesEvidence.toString(), decision.shouldRetry)
        }
    }

    @Test
    fun indeterminateHardIdentityStillHonorsCrossFamilyCandyConflict() {
        val scan = pokemon("Pikachu", "Pikachu", candy = "Eevee")

        val decision = consistencyGate.evaluate(
            scan,
            scan,
            evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.INDETERMINATE)
        )

        assertTrue(decision.shouldRetry)
        assertEquals(SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT, decision.reason)
    }

    @Test
    fun anchoredFusionUsesStoredIndeterminateTextualAuthority() {
        val scan = anchoredPokemon("Pikachu")
        val frame = ScanFrameCandidate(
            "same.png",
            scan,
            0.8,
            evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.INDETERMINATE)
        )

        val selection = ScanFrameFusion.resolveAnchoredFrames(
            frames = listOf(frame),
            authoritative = frame
        )

        assertEquals(SpeciesAuthority.EXACT_CANONICAL, selection?.speciesEvidence?.authority)
        assertEquals(SpeciesProfileStatus.INDETERMINATE, selection?.speciesEvidence?.profileStatus)
        assertEquals("Pikachu", selection?.speciesEvidence?.selectedCanonicalSpecies)
    }

    @Test
    fun anchoredFusionDetectsDisagreeingHardIdentitiesEvenWhenProfilesAreIndeterminate() {
        val pikachu = ScanFrameCandidate(
            "a.png",
            anchoredPokemon("Pikachu"),
            0.8,
            evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.INDETERMINATE)
        )
        val raichu = ScanFrameCandidate(
            "b.png",
            anchoredPokemon("Raichu"),
            0.8,
            evidence("Raichu", SpeciesAuthority.REVIEWED_ALIAS, SpeciesProfileStatus.INDETERMINATE)
        )

        val selection = ScanFrameFusion.resolveAnchoredFrames(
            frames = listOf(pikachu, raichu),
            authoritative = pikachu
        )

        assertEquals(SpeciesAuthority.CONFLICT, selection?.speciesEvidence?.authority)
        assertTrue(selection?.speciesEvidence?.authorityConflict == true)
        assertFalse(selection?.speciesEvidence?.observationsAgree ?: true)
    }

    @Test
    fun independentCompatibleBehaviorRemainsAccepted() {
        val scan = pokemon("Weedle", "Weedle")

        val decision = consistencyGate.evaluate(
            scan,
            scan,
            evidence("Weedle", SpeciesAuthority.INDEPENDENT_PROFILE, SpeciesProfileStatus.COMPATIBLE)
        )

        assertFalse(decision.shouldRetry)
        assertEquals("accepted", decision.reason)
    }

    private fun evidence(
        species: String,
        authority: SpeciesAuthority,
        profile: SpeciesProfileStatus
    ) = SpeciesEvidence(
        selectedCanonicalSpecies = species,
        authority = authority,
        profileStatus = profile,
        reasonCodes = emptyList(),
        observationsAgree = true,
        authorityConflict = false
    )

    private fun pokemon(name: String, realName: String, candy: String? = null) = PokemonData(
        cp = 500,
        hp = 80,
        maxHp = 80,
        name = name,
        realName = realName,
        candyName = candy,
        megaEnergy = null,
        weight = null,
        height = null,
        stardust = null,
        caughtDate = null
    )

    private fun anchoredPokemon(species: String) = pokemon(species, species).copy(
        recognitionObservation = RecognitionObservation(
            candySpecies = null,
            powerUpStardust = null,
            types = null,
            detailScreen = true
        )
    )
}
