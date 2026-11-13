package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.AnchoredFrameSelection
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.FieldRead
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AnchoredNumericEvidenceTest {
    private val calculator = RarityCalculator(ApplicationProvider.getApplicationContext<Context>()).also {
        RecognitionSnapshotTestSupport.seedHolder()
    }
    @Test
    fun conflictingPowerUpCostsDoNotBlockIdentityOrConstrainLevels() {
        val baseline = candidate()
        val levels = StardustLevelEvidence.Levels(
            levels = listOf(20.0, 20.5),
            minLevel = 20.0,
            maxLevel = 20.5,
            modifiersUsed = emptyList(),
            interpretations = emptyList(),
            reasonCodes = listOf("test_level_evidence")
        )
        val first = withPowerUpCost(baseline, 1000, levels)
        val second = withPowerUpCost(
            candidate(options = CandidateOptions(path = "frame-1", frameIndex = 1)),
            1300,
            levels
        )

        val selected = resolve(listOf(first, second), first)

        assertEquals("Skwovet", ScanManager.finalAnchoredSpeciesEvidence(
            selected, selected.frame.data, calculator
        ).selectedCanonicalSpecies)
        assertEquals(SpeciesProfileStatus.COMPATIBLE, selected.speciesEvidence.profileStatus)
        assertTrue(selected.speciesEvidence.hasHardAuthority)
        assertFalse(selected.speciesEvidence.authorityConflict)
        assertEquals(first.path, selected.frame.path)
        val selectedObservation = checkNotNull(selected.frame.data.recognitionObservation)
        assertEquals(1000, selectedObservation.powerUpStardust)
        val costEvidence = selectedObservation.powerUpStardustLevelEvidence
        assertTrue(costEvidence is StardustLevelEvidence.Conflict)
        assertEquals(2, (costEvidence as StardustLevelEvidence.Conflict).candidateCount)
    }

    @Test
    fun anExistingPowerUpConflictCannotBeOverriddenByAnotherFrameRead() {
        val levels = StardustLevelEvidence.Levels(
            levels = listOf(20.0, 20.5),
            minLevel = 20.0,
            maxLevel = 20.5,
            modifiersUsed = emptyList(),
            interpretations = emptyList(),
            reasonCodes = listOf("test_level_evidence")
        )
        val readable = withPowerUpCost(candidate(), 1000, levels)
        val conflictedBase = candidate(options = CandidateOptions(path = "frame-1", frameIndex = 1))
        val conflictedObservation = checkNotNull(conflictedBase.data.recognitionObservation).copy(
            powerUpStardustLevelEvidence = StardustLevelEvidence.Conflict(
                2,
                listOf("anchored_power_up_row", "multiple_candidates")
            )
        )
        val conflicted = conflictedBase.copy(
            data = conflictedBase.data.copy(recognitionObservation = conflictedObservation)
        )

        val selected = resolve(listOf(readable, conflicted), readable)

        assertEquals("Skwovet", selected.speciesEvidence.selectedCanonicalSpecies)
        assertTrue(selected.speciesEvidence.hasHardAuthority)
        val fusedEvidence = selected.frame.data.recognitionObservation?.powerUpStardustLevelEvidence
        assertTrue(fusedEvidence is StardustLevelEvidence.Conflict)
        assertTrue((fusedEvidence as StardustLevelEvidence.Conflict).reasonCodes.contains("multiple_candidates"))
    }

    @Test
    fun conflictingAnchoredEvolutionCostsDoNotBlockIdentityOrRemainReadable() {
        val first = candidate(observation = Observation(evolutionCandyCost = 50))
        val changed = candidate(
            observation = Observation(evolutionCandyCost = 75),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        )

        val selected = resolve(listOf(first, changed), first)

        assertEquals("Skwovet", selected.speciesEvidence.selectedCanonicalSpecies)
        assertTrue(selected.speciesEvidence.hasHardAuthority)
        val evolveRead = checkNotNull(selected.frame.data.recognitionObservation).evolutionCandyCostRead
        assertEquals(FieldReadStatus.CONFLICT, evolveRead.status)
        assertNull(evolveRead.value)
        assertEquals(2, evolveRead.candidateCount)
        assertNull(selected.frame.data.recognitionObservation?.evolutionCandyCost)
        assertEquals("Skwovet", ScanManager.finalAnchoredSpeciesEvidence(
            selected, selected.frame.data, calculator
        ).selectedCanonicalSpecies)
    }

    @Test
    fun typedEvolutionCostConflictDoesNotBlockIdentityOrRemainReadable() {
        val first = candidate(observation = Observation(evolutionCandyCost = 50))
        val conflictBase = candidate(options = CandidateOptions(path = "frame-1", frameIndex = 1))
        val conflictObservation = checkNotNull(conflictBase.data.recognitionObservation).copy(
            evolutionCandyCostRead = FieldRead.conflict("multiple_distinct_costs", 2)
        )
        val conflicted = conflictBase.copy(
            data = conflictBase.data.copy(recognitionObservation = conflictObservation)
        )

        val selected = resolve(listOf(first, conflicted), first)

        assertEquals("Skwovet", selected.speciesEvidence.selectedCanonicalSpecies)
        assertTrue(selected.speciesEvidence.hasHardAuthority)
        val evolveRead = selected.frame.data.recognitionObservation?.evolutionCandyCostRead
        assertEquals(FieldReadStatus.CONFLICT, evolveRead?.status)
        assertNull(evolveRead?.value)
        assertNull(selected.frame.data.recognitionObservation?.evolutionCandyCost)
        assertEquals("Skwovet", ScanManager.finalAnchoredSpeciesEvidence(
            selected, selected.frame.data, calculator
        ).selectedCanonicalSpecies)
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

    private fun resolve(
        frames: List<ScanFrameCandidate>,
        first: ScanFrameCandidate,
        detailed: ScanFrameCandidate? = null
    ): AnchoredFrameSelection = checkNotNull(ScanFrameFusion.resolveAnchoredFrames(
        frames, first, detailed
    ) { frame, excludeEvolutionCost, excludePowerUpCost ->
        ScanManager.deriveSpeciesEvidenceWithoutActionCosts(
            frame.fieldCandidates,
            frame.data,
            calculator,
            excludeEvolutionCost = excludeEvolutionCost,
            excludePowerUpCost = excludePowerUpCost
        )
    })

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
        val evolutionCandyCost: Int? = null,
        val evolutionCandyCostRead: FieldRead<Int>? = null
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
                evolutionCandyCost = observation.evolutionCandyCost,
                evolutionCandyCostRead = observation.evolutionCandyCostRead
                    ?: observation.evolutionCandyCost?.let { FieldRead.read(it) }
                    ?: FieldRead.missing("test_missing")
            )
        )
        val speciesEvidence = evidence(pokemon)
        return ScanFrameCandidate(options.path, pokemon, .9, speciesEvidence)
    }

    private fun withPowerUpCost(
        base: ScanFrameCandidate,
        cost: Int,
        levelEvidence: StardustLevelEvidence
    ): ScanFrameCandidate {
        val observation = checkNotNull(base.data.recognitionObservation).copy(
            powerUpStardust = cost,
            powerUpStardustLevelEvidence = levelEvidence
        )
        return base.copy(data = base.data.copy(recognitionObservation = observation))
    }
}
