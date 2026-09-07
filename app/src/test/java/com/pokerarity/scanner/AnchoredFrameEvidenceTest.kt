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
import com.pokerarity.scanner.util.ocr.RecognitionProfiles
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
        // JVM tests do not package Android assets; use the actual generated facts.
        val asset = listOf(File("src/main/assets/data/recognition_profiles.json"),
            File("app/src/main/assets/data/recognition_profiles.json")).first(File::isFile)
        val profiles = asset.reader().use(RecognitionProfiles::read)
        RarityCalculator::class.java.getDeclaredField("recognitionProfiles\$delegate")
            .apply { isAccessible = true }.set(it, lazyOf(profiles))
    }

    @Test
    fun uncertainCandyFamilyTransitionCannotHideBehindTheSameNickname() {
        val first = candidate()
        // Same name and numbers; only the independently observed candy label differs.
        val changed = candidate(candy = "Eevee", path = "frame-1", frameIndex = 1)
        assertTrue(first.speciesEvidence.hasHardAuthority)
        assertFalse(changed.speciesEvidence.hasHardAuthority)

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun sameSpeciesWithTwoObservedCpValuesIsAConflict() {
        val first = candidate()
        val changed = candidate(cp = 735, path = "frame-1", frameIndex = 1)

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun sameSpeciesWithDifferentMaximumHpIsAConflict() {
        val first = candidate()
        val changed = candidate(maxHp = 134, path = "frame-1", frameIndex = 1)

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun observedTypeContradictionCannotBeDroppedBecauseOneIdentityIsUncertain() {
        val first = candidate(types = setOf("normal"))
        val changed = candidate(types = setOf("dark"), path = "frame-1", frameIndex = 1)

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun conflictingAnchoredPowerUpCostsRemainConflicting() {
        val first = candidate(cost = 1000)
        val changed = candidate(cost = 1300, path = "frame-1", frameIndex = 1)

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun weakerFrameWithTrulyMissingNumericFieldsDoesNotVetoAUsableScreen() {
        val first = candidate(types = setOf("normal"))
        val weak = candidate(cp = null, maxHp = null, types = setOf("normal"), path = "frame-1", frameIndex = 1)
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
        val invalid = candidate(cp = 9999, maxHp = 999, numericConflict = true,
            path = "frame-1", frameIndex = 1)

        val selected = resolve(listOf(first, invalid), first)

        assertSame(first, selected.frame)
        assertTrue(selected.speciesEvidence.hasHardAuthority)
        assertEquals(734, selected.frame.data.cp)
        assertEquals(133, selected.frame.data.maxHp)
    }

    @Test
    fun malformedHpDoesNotHideAnIndependentlyAnchoredCandyFamilyChange() {
        val first = candidate(types = setOf("normal"))
        val changed = candidate(cp = 9999, maxHp = 999, candy = "Eevee", types = setOf("normal"),
            detailScreen = false, numericConflict = true, path = "frame-1", frameIndex = 1)

        assertConflict(resolve(listOf(first, changed), first))
    }

    @Test
    fun authoritativeObservationIsCheckedEvenIfOmittedFromFramesArgument() {
        val first = candidate()
        val changed = candidate(cp = 735, path = "frame-1", frameIndex = 1)

        assertConflict(resolve(listOf(changed), first))
    }

    @Test
    fun nonDetailTextDoesNotBecomeContradictoryScreenEvidence() {
        val first = candidate()
        val unrelated = candidate(cp = 424, maxHp = 80, candy = "Eevee", detailScreen = false,
            path = "frame-1", frameIndex = 1)

        val selected = resolve(listOf(first, unrelated), first)

        assertSame(first, selected.frame)
        assertTrue(selected.speciesEvidence.hasHardAuthority)
    }

    @Test
    fun strongerDetailedResultReplacesTheWholeSameSourceObservation() {
        val first = candidate(cp = null)
        val detailed = candidate(frameIndex = -1)
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
        val other = candidate(path = "different-source", frameIndex = -1)

        val selected = resolve(listOf(first), first, other)

        assertSame(first, selected.frame)
        assertNull(selected.frame.data.cp)
        assertFalse(selected.speciesEvidence.hasHardAuthority)
    }

    @Test
    fun contradictoryDetailedResultCannotOverwriteTheFastObservation() {
        val first = candidate()
        val detailed = candidate(cp = 735, frameIndex = -1)

        val selected = resolve(listOf(first), first, detailed)

        assertConflict(selected)
        assertSame(first, selected.frame)
        assertEquals(734, selected.frame.data.cp)
    }

    @Test
    fun detailedResultCanAddAnObservedTypeWithoutCombiningTwoDataObjects() {
        val first = candidate()
        val detailed = candidate(types = setOf("normal"), frameIndex = -1)

        val selected = resolve(listOf(first), first, detailed)

        assertSame(detailed.data, selected.frame.data)
        assertEquals(setOf("normal"), selected.frame.data.recognitionObservation?.types)
    }

    private fun resolve(
        frames: List<ScanFrameCandidate>,
        first: ScanFrameCandidate,
        detailed: ScanFrameCandidate? = null
    ): AnchoredFrameSelection = checkNotNull(ScanFrameFusion.resolveAnchoredFrames(frames, first, detailed, ::evidence))

    private fun assertConflict(selected: AnchoredFrameSelection) {
        assertEquals(SpeciesAuthority.CONFLICT, selected.speciesEvidence.authority)
        assertEquals(SpeciesProfileStatus.CONTRADICTORY, selected.speciesEvidence.profileStatus)
        assertTrue(selected.speciesEvidence.authorityConflict)
        assertFalse(selected.speciesEvidence.hasHardAuthority)
    }

    private fun evidence(pokemon: PokemonData): SpeciesEvidence =
        ScanManager.deriveSpeciesEvidence(emptyList(), pokemon, calculator)

    private fun candidate(
        cp: Int? = 734,
        maxHp: Int? = 133,
        candy: String = "Skwovet",
        types: Set<String>? = null,
        cost: Int? = null,
        path: String = "frame-0",
        frameIndex: Int = 0,
        detailScreen: Boolean = true,
        numericConflict: Boolean = false
    ): ScanFrameCandidate {
        val pokemon = PokemonData(cp = cp, hp = maxHp, maxHp = maxHp, name = "Skwovet", realName = "Skwovet",
            candyName = candy, megaEnergy = null, weight = null, height = null, stardust = null, caughtDate = null,
            recognitionObservation = RecognitionObservation(candy, cost, types, detailScreen, numericConflict, frameIndex))
        return ScanFrameCandidate(path, pokemon, .9, evidence(pokemon))
    }
}
