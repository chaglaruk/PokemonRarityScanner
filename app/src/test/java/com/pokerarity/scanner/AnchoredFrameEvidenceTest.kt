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
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
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
    fun conflictingEvolutionCostCannotSupplyIdentityByItself() {
        fun frame(cost: Int, path: String): ScanFrameCandidate {
            val base = candidate(
                cp = null,
                maxHp = null,
                candy = "Farfetch'd",
                observation = Observation(types = setOf("fighting")),
                options = CandidateOptions(path = path)
            )
            val data = base.data.copy(name = "Farfetch'd", realName = "Farfetch'd",
                recognitionObservation = base.data.recognitionObservation!!.copy(
                    evolutionCandyCost = cost,
                    evolutionCandyCostRead = FieldRead.read(cost)
                ))
            return base.copy(data = data, speciesEvidence = evidence(data))
        }
        val accepted = frame(50, "first")
        val uncertain = frame(100, "second")
        assertTrue(accepted.speciesEvidence.hasHardAuthority)
        assertFalse(uncertain.speciesEvidence.hasHardAuthority)
        listOf(
            resolve(listOf(accepted, uncertain), accepted),
            resolve(listOf(accepted), accepted, frame(100, "first"))
        ).forEach { selection ->
            assertFalse(selection.speciesEvidence.authorityConflict)
            assertEquals(FieldReadStatus.CONFLICT,
                selection.frame.data.recognitionObservation?.evolutionCandyCostRead?.status)
            assertFalse(ScanManager.finalAnchoredSpeciesEvidence(
                selection, selection.frame.data, calculator
            ).hasHardAuthority)
        }
    }

    @Test
    fun conflictingEvolutionCostsDoNotTurnCostOnlySelectionsIntoIdentityConflict() {
        fun withCostIdentity(cost: Int, name: String, path: String): ScanFrameCandidate {
            val base = candidate(
                observation = Observation(evolutionCandyCost = cost),
                options = CandidateOptions(path = path)
            )
            val original = base.speciesEvidence.copy(
                selectedCanonicalSpecies = name,
                authority = SpeciesAuthority.EXACT_CANONICAL
            )
            return base.copy(speciesEvidence = original)
        }
        val first = withCostIdentity(50, "Skwovet", "first")
        val second = withCostIdentity(75, "Farfetch'd", "second")

        val selected = resolve(listOf(first, second), first)

        assertFalse(selected.speciesEvidence.authorityConflict)
        assertEquals(FieldReadStatus.CONFLICT,
            selected.frame.data.recognitionObservation?.evolutionCandyCostRead?.status)
    }

    @Test
    fun costOnlyIdentityCannotVetoIndependentStrongIdentityDuringCostConflict() {
        val first = candidate(observation = Observation(evolutionCandyCost = 50))
        val costOnly = candidate(
            observation = Observation(evolutionCandyCost = 75),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        ).let { frame ->
            frame.copy(
                speciesEvidence = frame.speciesEvidence.copy(
                    selectedCanonicalSpecies = "Farfetch'd",
                    authority = SpeciesAuthority.EXACT_CANONICAL
                )
            )
        }

        val selected = resolve(listOf(first, costOnly), first)

        assertFalse(selected.speciesEvidence.authorityConflict)
        assertEquals("Skwovet", ScanManager.finalAnchoredSpeciesEvidence(
            selected, selected.frame.data, calculator
        ).selectedCanonicalSpecies)
    }

    @Test
    fun conflictingPowerUpCostsDoNotTurnCostOnlySelectionsIntoIdentityConflict() {
        fun withCostIdentity(cost: Int, name: String, path: String): ScanFrameCandidate {
            val base = candidate(
                cp = null,
                maxHp = null,
                observation = Observation(powerUpStardust = cost),
                options = CandidateOptions(path = path)
            )
            val withoutCandy = base.data.copy(
                candyName = null,
                recognitionObservation = base.data.recognitionObservation!!.copy(candySpecies = null)
            )
            return base.copy(
                data = withoutCandy,
                speciesEvidence = base.speciesEvidence.copy(
                    selectedCanonicalSpecies = name,
                    authority = SpeciesAuthority.EXACT_CANONICAL
                )
            )
        }
        val first = withCostIdentity(1000, "Skwovet", "first")
        val second = withCostIdentity(1300, "Farfetch'd", "second")

        val selected = resolve(listOf(first, second), first)

        assertFalse(selected.speciesEvidence.authorityConflict)
        assertTrue(selected.frame.data.recognitionObservation?.powerUpStardustLevelEvidence is
            StardustLevelEvidence.Conflict)
        val finalEvidence = ScanManager.finalAnchoredSpeciesEvidence(selected, selected.frame.data, calculator)
        assertFalse(finalEvidence.authorityConflict)
        assertFalse(finalEvidence.hasHardAuthority)
    }

    @Test
    fun powerUpCostOnlyIdentityCannotVetoIndependentStrongIdentity() {
        val first = candidate(observation = Observation(powerUpStardust = 1000))
        val costOnlyBase = candidate(
            cp = null,
            maxHp = null,
            observation = Observation(powerUpStardust = 1300),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        )
        val costOnlyData = costOnlyBase.data.copy(
            candyName = null,
            recognitionObservation = costOnlyBase.data.recognitionObservation!!.copy(candySpecies = null)
        )
        val costOnly = costOnlyBase.copy(
            data = costOnlyData,
            speciesEvidence = costOnlyBase.speciesEvidence.copy(
                selectedCanonicalSpecies = "Farfetch'd",
                authority = SpeciesAuthority.EXACT_CANONICAL
            )
        )

        val selected = resolve(listOf(first, costOnly), first)

        assertFalse(selected.speciesEvidence.authorityConflict)
        assertEquals("Skwovet", ScanManager.finalAnchoredSpeciesEvidence(
            selected, selected.frame.data, calculator
        ).selectedCanonicalSpecies)
    }

    @Test
    fun powerUpCostConflictDoesNotPreserveCostOnlyNegativeProfile() {
        val first = candidate(observation = Observation(powerUpStardust = 1000))
        val negativeBase = candidate(
            observation = Observation(powerUpStardust = 1300),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        )
        val negative = negativeBase.copy(
            speciesEvidence = negativeBase.speciesEvidence.copy(
                profileStatus = SpeciesProfileStatus.CONTRADICTORY
            )
        )

        val selected = resolve(listOf(first, negative), first)

        assertFalse(selected.speciesEvidence.authorityConflict)
        val finalEvidence = ScanManager.finalAnchoredSpeciesEvidence(
            selected, selected.frame.data, calculator
        )
        assertFalse(finalEvidence.authorityConflict)
        assertEquals("Skwovet", finalEvidence.selectedCanonicalSpecies)
        assertTrue(finalEvidence.profileStatus != SpeciesProfileStatus.CONTRADICTORY)
    }

    @Test
    fun powerUpCostConflictPreservesIndependentNegativeProfileEvidence() {
        val first = candidate(observation = Observation(powerUpStardust = 1000))
        val negativeBase = candidate(
            observation = Observation(powerUpStardust = 1300),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        )
        val negative = negativeBase.copy(
            speciesEvidence = negativeBase.speciesEvidence.copy(
                profileStatus = SpeciesProfileStatus.CONTRADICTORY
            )
        )

        val selected = checkNotNull(ScanFrameFusion.resolveAnchoredFrames(
            listOf(first, negative), first
        ) { frame, excludeEvolutionCost, excludePowerUpCost ->
            val evidence = ScanManager.deriveSpeciesEvidenceWithoutActionCosts(
                frame.fieldCandidates,
                frame.data,
                calculator,
                excludeEvolutionCost = excludeEvolutionCost,
                excludePowerUpCost = excludePowerUpCost
            )
            if (frame.path == negative.path) {
                evidence.copy(profileStatus = SpeciesProfileStatus.CONTRADICTORY)
            } else {
                evidence
            }
        })

        assertConflict(selected)
    }

    @Test
    fun evolutionCostConflictDoesNotEraseSeparateAnchoredFrameConflict() {
        val first = candidate(observation = Observation(evolutionCandyCost = 50))
        val changed = candidate(
            cp = 735,
            observation = Observation(evolutionCandyCost = 75),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        )

        val selected = resolve(listOf(first, changed), first)

        assertConflict(selected)
        assertEquals(FieldReadStatus.CONFLICT,
            selected.frame.data.recognitionObservation?.evolutionCandyCostRead?.status)
        val finalEvidence = ScanManager.finalAnchoredSpeciesEvidence(
            selected, selected.frame.data, calculator
        )
        assertEquals(SpeciesAuthority.CONFLICT, finalEvidence.authority)
        assertTrue(finalEvidence.authorityConflict)
    }

    @Test
    fun evolutionCostConflictDoesNotEraseIndependentNegativeProfileEvidence() {
        val first = candidate(observation = Observation(evolutionCandyCost = 50))
        val negativeBase = candidate(
            observation = Observation(evolutionCandyCost = 75),
            options = CandidateOptions(path = "frame-1", frameIndex = 1)
        )
        val negative = negativeBase.copy(
            speciesEvidence = negativeBase.speciesEvidence.copy(
                profileStatus = SpeciesProfileStatus.CONTRADICTORY
            )
        )

        val selected = checkNotNull(ScanFrameFusion.resolveAnchoredFrames(
            listOf(first, negative), first
        ) { frame, excludeEvolutionCost, excludePowerUpCost ->
            val evidence = ScanManager.deriveSpeciesEvidenceWithoutActionCosts(
                frame.fieldCandidates,
                frame.data,
                calculator,
                excludeEvolutionCost = excludeEvolutionCost,
                excludePowerUpCost = excludePowerUpCost
            )
            if (frame.path == negative.path) {
                evidence.copy(profileStatus = SpeciesProfileStatus.CONTRADICTORY)
            } else {
                evidence
            }
        })

        assertConflict(selected)
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
