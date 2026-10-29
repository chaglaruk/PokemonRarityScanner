package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.util.ocr.PowerUpCostModifier
import com.pokerarity.scanner.util.ocr.RecognitionIvEstimator
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionIvEstimatorTest {
    private val snapshot = RecognitionSnapshot.fromRows(
        profiles = listOf(
            RecognitionSnapshot.Profile(
                species = "Testmon",
                forms = setOf("base"),
                stats = RecognitionSnapshot.ProfileStats(atk = 100, def = 100, sta = 70),
                types = setOf("normal"),
                familyId = "testmon",
                candySpecies = "Testmon"
            )
        ),
        cpMultipliers = mapOf(
            30.0 to 0.7317,
            30.5 to 0.734741009,
            33.0 to 0.74976104
        )
    )

    private val identity = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN,
        canonicalSpecies = "Testmon",
        speciesAuthority = "test_fixture",
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

    private val multiFormSnapshot = RecognitionSnapshot.fromRows(
        profiles = listOf(
            RecognitionSnapshot.Profile(
                species = "Testmon",
                forms = setOf("TESTMON_NORMAL"),
                stats = RecognitionSnapshot.ProfileStats(atk = 100, def = 100, sta = 70),
                types = setOf("normal"),
                familyId = "testmon",
                candySpecies = "Testmon"
            ),
            RecognitionSnapshot.Profile(
                species = "Testmon",
                forms = setOf("TESTMON_ALOLA"),
                stats = RecognitionSnapshot.ProfileStats(atk = 101, def = 100, sta = 70),
                types = setOf("psychic"),
                familyId = "testmon",
                candySpecies = "Testmon"
            )
        ),
        cpMultipliers = mapOf(33.0 to 0.74976104)
    )

    private fun stardustLevels(vararg levels: Double) = StardustLevelEvidence.Levels(
        levels = levels.toList(),
        minLevel = levels.minOrNull(),
        maxLevel = levels.maxOrNull(),
        modifiersUsed = listOf(PowerUpCostModifier.NORMAL),
        interpretations = listOf("test_fixture"),
        reasonCodes = listOf("anchored_power_up_row")
    )

    @Test
    fun typedStardustNarrowsSameWitnessCpHpToExactIvAndEffectiveLevel() {
        val estimate = RecognitionIvEstimator.estimate(
            snapshot = snapshot,
            identity = identity,
            cp = 470,
            maxHp = 52,
            stardust = stardustLevels(33.0)
        )

        assertEquals(IvSolveMode.EXACT, estimate.ivSolveMode)
        assertEquals(0, estimate.ivExact)
        assertEquals(0, estimate.ivMin)
        assertEquals(0, estimate.ivMax)
        assertEquals(33f, estimate.levelMin)
        assertEquals(33f, estimate.levelMax)
        assertTrue(estimate.ivSolveSignalsUsed.containsAll(listOf("CP", "MAX_HP", "POWER_UP_STARDUST")))
    }

    @Test
    fun unqualifiedStardustAndMissingMaxHpNeverInventAnEstimate() {
        val invalidDust = RecognitionIvEstimator.estimate(
            snapshot, identity, 470, 52, StardustLevelEvidence.Invalid(1234, listOf("invalid_cost"))
        )
        assertEquals(IvSolveMode.INSUFFICIENT, invalidDust.ivSolveMode)
        assertEquals(0, invalidDust.ivCandidateCount)

        val noMaxHp = RecognitionIvEstimator.estimate(snapshot, identity, 470, null, null)
        assertEquals(IvSolveMode.INSUFFICIENT, noMaxHp.ivSolveMode)
        assertEquals(null, noMaxHp.ivMin)
        assertEquals(null, noMaxHp.levelMin)
    }

    @Test
    fun validStardustPreservesOnlyAContiguousLevelWindowWhenIdentityIsUnknown() {
        val estimate = RecognitionIvEstimator.estimate(
            snapshot = snapshot,
            identity = identity.copy(speciesStatus = RecognitionSpeciesStatus.UNKNOWN),
            cp = 470,
            maxHp = 52,
            stardust = stardustLevels(30.0, 30.5, 31.0, 31.5)
        )

        assertEquals(IvSolveMode.INSUFFICIENT, estimate.ivSolveMode)
        assertNull(estimate.ivExact)
        assertNull(estimate.ivMin)
        assertNull(estimate.ivMax)
        assertEquals(0, estimate.ivCandidateCount)
        assertEquals(30f, estimate.levelMin)
        assertEquals(32.5f, estimate.levelMax)
        assertEquals(listOf(30f, 30.5f, 31f, 31.5f, 32f, 32.5f), estimate.levelAlternatives)
        assertEquals(listOf("POWER_UP_STARDUST"), estimate.ivSolveSignalsUsed)

        val noMaxHp = RecognitionIvEstimator.estimate(
            snapshot = snapshot,
            identity = identity,
            cp = 470,
            maxHp = null,
            stardust = stardustLevels(30.0, 30.5, 31.0, 31.5)
        )
        assertEquals(IvSolveMode.INSUFFICIENT, noMaxHp.ivSolveMode)
        assertNull(noMaxHp.ivMin)
        assertNull(noMaxHp.ivMax)
        assertEquals(30f, noMaxHp.levelMin)
        assertEquals(32.5f, noMaxHp.levelMax)
        assertEquals(estimate.levelAlternatives, noMaxHp.levelAlternatives)
    }

    @Test
    fun levelOnlyFallbackKeepsDisconnectedCandidatesAndRejectsInvalidEvidence() {
        val unknownIdentity = identity.copy(speciesStatus = RecognitionSpeciesStatus.UNKNOWN)
        val invalid = RecognitionIvEstimator.estimate(
            snapshot, unknownIdentity, 470, 52, StardustLevelEvidence.Invalid(1234, listOf("invalid_cost"))
        )
        val disconnected = RecognitionIvEstimator.estimate(
            snapshot, unknownIdentity, 470, 52, stardustLevels(33.0, 30.0, 30.0)
        )
        val malformedLevels = RecognitionIvEstimator.estimate(
            snapshot, unknownIdentity, 470, 52, stardustLevels(30.25)
        )
        val conflict = RecognitionIvEstimator.estimate(
            snapshot,
            unknownIdentity,
            470,
            52,
            StardustLevelEvidence.Conflict(2, listOf("anchored_power_up_row", "numeric_conflict"))
        )

        assertEquals(IvSolveMode.INSUFFICIENT, invalid.ivSolveMode)
        assertNull(invalid.levelMin)
        assertNull(invalid.levelMax)
        assertTrue(invalid.levelAlternatives.isEmpty())
        assertEquals(IvSolveMode.INSUFFICIENT, disconnected.ivSolveMode)
        assertNull(disconnected.levelMin)
        assertNull(disconnected.levelMax)
        assertEquals(listOf(30f, 31f, 33f, 34f), disconnected.levelAlternatives)
        assertEquals(listOf("POWER_UP_STARDUST"), disconnected.ivSolveSignalsUsed)
        assertTrue(malformedLevels.levelAlternatives.isEmpty())
        assertTrue(conflict.levelAlternatives.isEmpty())
    }

    @Test
    fun stardustDoesNotRecoverLevelWhenSameWitnessNumericEvidenceConflicts() {
        val estimate = RecognitionIvEstimator.estimate(
            snapshot = snapshot,
            identity = identity,
            cp = 10_000,
            maxHp = 52,
            stardust = stardustLevels(30.0)
        )

        assertEquals(IvSolveMode.INSUFFICIENT, estimate.ivSolveMode)
        assertNull(estimate.ivMin)
        assertNull(estimate.ivMax)
        assertEquals(0, estimate.ivCandidateCount)
        assertNull(estimate.levelMin)
        assertNull(estimate.levelMax)
        assertTrue(estimate.levelAlternatives.isEmpty())
    }

    @Test
    fun conflictingPowerUpReadDoesNotDiscardIndependentCpHpWitnesses() {
        val withoutCost = RecognitionIvEstimator.estimate(
            snapshot = snapshot,
            identity = identity,
            cp = 470,
            maxHp = 52,
            stardust = null
        )
        val conflictingCost = RecognitionIvEstimator.estimate(
            snapshot = snapshot,
            identity = identity,
            cp = 470,
            maxHp = 52,
            stardust = StardustLevelEvidence.Conflict(
                2, listOf("anchored_power_up_row", "multiple_distinct_costs")
            )
        )

        assertEquals(withoutCost.ivSolveMode, conflictingCost.ivSolveMode)
        assertEquals(withoutCost.ivMin, conflictingCost.ivMin)
        assertEquals(withoutCost.ivMax, conflictingCost.ivMax)
        assertEquals(withoutCost.ivCandidateCount, conflictingCost.ivCandidateCount)
        assertEquals(withoutCost.levelMin, conflictingCost.levelMin)
        assertEquals(withoutCost.levelMax, conflictingCost.levelMax)
        assertEquals(withoutCost.ivSolveSignalsUsed, conflictingCost.ivSolveSignalsUsed)
        assertTrue(conflictingCost.ivSolveSignalsUsed.containsAll(listOf("CP", "MAX_HP")))
        assertTrue("POWER_UP_STARDUST" !in conflictingCost.ivSolveSignalsUsed)
    }

    @Test
    fun disconnectedLegalLevelsAreNotFlattenedIntoOneDisplayedRange() {
        val estimate = RecognitionIvEstimator.estimate(
            snapshot = snapshot,
            identity = identity,
            cp = 470,
            maxHp = 52,
            stardust = stardustLevels(33.0, 30.0, 33.0)
        )

        assertEquals(IvSolveMode.RANGE, estimate.ivSolveMode)
        assertEquals(null, estimate.levelMin)
        assertEquals(null, estimate.levelMax)
        assertEquals(listOf(30f, 33f), estimate.levelAlternatives)
    }

    @Test
    fun knownAlternateFormUsesOnlyItsDifferentBaseStats() {
        val alolan = RecognitionIvEstimator.estimate(
            snapshot = multiFormSnapshot,
            identity = identity.copy(
                formStatus = RecognitionFormStatus.KNOWN,
                knownForm = "Alolan",
                formProvenance = "authoritative_variant_db"
            ),
            cp = 475,
            maxHp = 52,
            stardust = stardustLevels(33.0)
        )

        assertEquals(IvSolveMode.EXACT, alolan.ivSolveMode)
        assertEquals(0, alolan.ivMin)
        assertEquals(0, alolan.ivMax)
        assertEquals(1, alolan.ivCandidateCount)
    }

    @Test
    fun ambiguousAndUnknownFormsKeepTheirSupportedFormWitnesses() {
        val ambiguous = RecognitionIvEstimator.estimate(
            snapshot = multiFormSnapshot,
            identity = identity.copy(
                formStatus = RecognitionFormStatus.AMBIGUOUS,
                ambiguousForms = listOf("Normal Form", "Alolan Form")
            ),
            cp = 475,
            maxHp = 52,
            stardust = stardustLevels(33.0)
        )
        val unknown = RecognitionIvEstimator.estimate(
            snapshot = multiFormSnapshot,
            identity = identity,
            cp = 475,
            maxHp = 52,
            stardust = stardustLevels(33.0)
        )

        assertEquals(IvSolveMode.RANGE, ambiguous.ivSolveMode)
        assertEquals(3, ambiguous.ivCandidateCount)
        assertEquals(0, ambiguous.ivMin)
        assertEquals(4, ambiguous.ivMax)
        assertEquals(ambiguous.ivCandidateCount, unknown.ivCandidateCount)
        assertEquals(ambiguous.ivMin, unknown.ivMin)
        assertEquals(ambiguous.ivMax, unknown.ivMax)
    }

    @Test
    fun knownFormWithoutSupportedSnapshotProfileFailsClosed() {
        val estimate = RecognitionIvEstimator.estimate(
            snapshot = multiFormSnapshot,
            identity = identity.copy(
                formStatus = RecognitionFormStatus.KNOWN,
                knownForm = "Hisuian Form"
            ),
            cp = 475,
            maxHp = 52,
            stardust = stardustLevels(33.0)
        )

        assertEquals(IvSolveMode.INSUFFICIENT, estimate.ivSolveMode)
        assertNull(estimate.ivMin)
        assertNull(estimate.ivMax)
        assertEquals(0, estimate.ivCandidateCount)
    }
}
