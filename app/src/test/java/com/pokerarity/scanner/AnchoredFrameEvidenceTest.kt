package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.AnchoredFrameSelection
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AnchoredFrameEvidenceTest {
    private val calculator = RarityCalculator(ApplicationProvider.getApplicationContext<Context>()).also {
        // JVM tests do not package Android assets; seed the snapshot holder explicitly.
        RecognitionSnapshotTestSupport.seedHolder()
    }

    @Test
    fun uncertainCandyFamilyTransitionCannotHideBehindTheSameNickname() {
        val first = candidate()
        // Same name and numbers; only the independently observed candy label differs.
        val changed = candidate(candy = "Eevee", options = CandidateOptions(path = "frame-1", frameIndex = 1))
        assertTrue(first.speciesEvidence.hasHardAuthority)
        assertFalse(changed.speciesEvidence.hasHardAuthority)

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun sameSpeciesWithTwoObservedCpValuesIsAConflict() {
        val first = candidate()
        val changed = candidate(cp = 735, options = CandidateOptions(path = "frame-1", frameIndex = 1))

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun sameSpeciesWithDifferentMaximumHpIsAConflict() {
        val first = candidate()
        val changed = candidate(maxHp = 134, options = CandidateOptions(path = "frame-1", frameIndex = 1))

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun observedTypeContradictionCannotBeDroppedBecauseOneIdentityIsUncertain() {
        val first = candidate(observation = Observation(types = setOf("normal")))
        val changed = candidate(
            observation = Observation(types = setOf("dark")),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        )

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun conflictingAnchoredPowerUpCostsRemainConflicting() {
        val first = candidate(observation = Observation(powerUpStardust = 1000))
        val changed = candidate(
            observation = Observation(powerUpStardust = 1300),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        )

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun conflictingAnchoredEvolutionCostsRemainConflicting() {
        val first = candidate(observation = Observation(evolutionCandyCost = 50))
        val changed = candidate(
            observation = Observation(evolutionCandyCost = 75),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        )

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun weakerFrameWithTrulyMissingNumericFieldsDoesNotVetoAUsableScreen() {
        val first = candidate(observation = Observation(types = setOf("normal")))
        val weak = candidate(
            cp = null,
            maxHp = null,
            observation = Observation(types = setOf("normal")),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        )
        assertFalse(weak.speciesEvidence.hasHardAuthority)

        val selected = resolve(listOf(first, weak), first)

        assertSame(first, selected.frame)
        assertEquals("Skwovet", selected.speciesEvidence.selectedCanonicalSpecies)
        assertEquals(SpeciesProfileStatus.COMPATIBLE, selected.speciesEvidence.profileStatus)
        assertTrue(selected.speciesEvidence.hasHardAuthority)
    }

    @Test
    fun numericConflictFrameCannotSupplyTrustedValues() {
        val first = candidate()
        val invalid = candidate(
            cp = 9999,
            maxHp = 999,
            options = CandidateOptions(path = "frame-1", frameIndex = 1, numericConflict = true)
        )

        val selected = resolve(listOf(first, invalid), first)

        assertSame(first, selected.frame)
        assertTrue(selected.speciesEvidence.hasHardAuthority)
        assertEquals(734, selected.frame.data.cp)
        assertEquals(133, selected.frame.data.maxHp)
    }

    @Test
    fun malformedHpDoesNotHideAnIndependentlyAnchoredCandyFamilyChange() {
        val first = candidate(observation = Observation(types = setOf("normal")))
        val changed = candidate(
            cp = 9999,
            maxHp = 999,
            candy = "Eevee",
            observation = Observation(types = setOf("normal")),
            options = CandidateOptions(
                path = "frame-1",
                frameIndex = 1,
                detailScreen = false,
                numericConflict = true
            )
        )

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun authoritativeObservationIsCheckedEvenIfOmittedFromFramesArgument() {
        val first = candidate()
        val changed = candidate(cp = 735, options = CandidateOptions(path = "frame-1", frameIndex = 1))

        assertConflict(resolve(listOf(changed), first))
    }

    @Test
    fun nonDetailTextDoesNotBecomeContradictoryScreenEvidence() {
        val first = candidate()
        val unrelated = candidate(
            cp = 424,
            maxHp = 80,
            candy = "Eevee",
            options = CandidateOptions(path = "frame-1", frameIndex = 1, detailScreen = false)
        )

        val selected = resolve(listOf(first, unrelated), first)

        assertSame(first, selected.frame)
        assertTrue(selected.speciesEvidence.hasHardAuthority)
    }

    @Test
    fun strongerDetailedResultReplacesTheWholeSameSourceObservation() {
        val first = candidate(cp = null)
        val detailed = candidate(options = CandidateOptions(frameIndex = -1))
        assertFalse(first.speciesEvidence.hasHardAuthority)

        val selected = resolve(listOf(first), first, detailed)

        assertSame(detailed, selected.frame)
        assertSame(detailed.data, selected.frame.data)
        assertEquals(-1, selected.frame.data.recognitionObservation?.frameIndex)
        assertEquals("Skwovet", selected.speciesEvidence.selectedCanonicalSpecies)
        assertTrue(selected.speciesEvidence.hasHardAuthority)
    }

    @Test
    fun detailedResultFromAnotherSourceCannotUpgradeTheSelectedScreen() {
        val first = candidate(cp = null)
        val other = candidate(options = CandidateOptions(path = "different-source", frameIndex = -1))

        val selected = resolve(listOf(first), first, other)

        assertSame(first, selected.frame)
        assertNull(selected.frame.data.cp)
        assertFalse(selected.speciesEvidence.hasHardAuthority)
    }

    @Test
    fun contradictoryDetailedResultCannotOverwriteTheFastObservation() {
        val first = candidate()
        val detailed = candidate(cp = 735, options = CandidateOptions(frameIndex = -1))

        val selected = resolve(listOf(first), first, detailed)

        assertConflict(selected)
        assertSame(first, selected.frame)
        assertEquals(734, selected.frame.data.cp)
    }

    @Test
    fun detailedResultCanAddAnObservedTypeWithoutCombiningTwoDataObjects() {
        val first = candidate()
        val detailed = candidate(
            observation = Observation(types = setOf("normal")),
            options = CandidateOptions(frameIndex = -1)
        )

        val selected = resolve(listOf(first), first, detailed)

        assertSame(detailed.data, selected.frame.data)
        assertEquals(setOf("normal"), selected.frame.data.recognitionObservation?.types)
    }

    @Test
    fun conflictingEvolutionEvidenceCannotHideBehindAnUnresolvedFrame() {
        fun frame(cost: Int, path: String): ScanFrameCandidate {
            val base = candidate(
                cp = null,
                maxHp = null,
                candy = "Farfetch'd",
                observation = Observation(types = setOf("fighting")),
                options = CandidateOptions(path = path)
            )
            val data = base.data.copy(name = "Farfetch'd", realName = "Farfetch'd",
                recognitionObservation = base.data.recognitionObservation!!.copy(evolutionCandyCost = cost))
            return base.copy(data = data, speciesEvidence = evidence(data))
        }
        val accepted = frame(50, "first")
        val uncertain = frame(100, "second")
        assertTrue(accepted.speciesEvidence.hasHardAuthority)
        assertFalse(uncertain.speciesEvidence.hasHardAuthority)
        assertConflict(resolve(listOf(accepted, uncertain), accepted))
        assertConflict(resolve(listOf(accepted), accepted, frame(100, "first")))
    }

    private fun resolve(
        frames: List<ScanFrameCandidate>,
        first: ScanFrameCandidate,
        detailed: ScanFrameCandidate? = null
    ): AnchoredFrameSelection = checkNotNull(ScanFrameFusion.resolveAnchoredFrames(frames, first, detailed))

    private fun assertConflict(selected: AnchoredFrameSelection) {
        assertEquals(SpeciesAuthority.CONFLICT, selected.speciesEvidence.authority)
        assertEquals(SpeciesProfileStatus.CONTRADICTORY, selected.speciesEvidence.profileStatus)
        assertTrue(selected.speciesEvidence.authorityConflict)
        assertFalse(selected.speciesEvidence.hasHardAuthority)
    }

    private fun evidence(pokemon: PokemonData): SpeciesEvidence =
        ScanManager.deriveSpeciesEvidence(emptyList(), pokemon, calculator)

    private data class CandidateOptions(
        val path: String = "frame-0",
        val frameIndex: Int = 0,
        val detailScreen: Boolean = true,
        val numericConflict: Boolean = false
    )

    private data class Observation(
        val powerUpStardust: Int? = null,
        val types: Set<String>? = null,
        val evolutionCandyCost: Int? = null
    )

    private fun candidate(
        cp: Int? = 734,
        maxHp: Int? = 133,
        candy: String = "Skwovet",
        observation: Observation = Observation(),
        options: CandidateOptions = CandidateOptions()
    ): ScanFrameCandidate {
        val pokemon = PokemonData(
            cp = cp,
            hp = maxHp,
            maxHp = maxHp,
            name = "Skwovet",
            realName = "Skwovet",
            candyName = candy,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            recognitionObservation = RecognitionObservation(
                candySpecies = candy,
                powerUpStardust = observation.powerUpStardust,
                types = observation.types,
                detailScreen = options.detailScreen,
                numericConflict = options.numericConflict,
                frameIndex = options.frameIndex,
                evolutionCandyCost = observation.evolutionCandyCost
            )
        )
        return ScanFrameCandidate(options.path, pokemon, .9, evidence(pokemon))
    }
}
