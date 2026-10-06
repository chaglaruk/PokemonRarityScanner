package com.pokerarity.scanner

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.util.ocr.ProfileTupleFeasibility
import com.pokerarity.scanner.util.ocr.ProfileTupleFeasibility.ExactIvConstraints
import com.pokerarity.scanner.util.ocr.ProfileTupleFeasibility.StardustIntersection
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Phase 3B tuple-feasibility semantics over independent game-domain fixtures.
 *
 * Fixture expectations were derived by hand from the published Pokemon GO HP/CP
 * formulas against the real pinned snapshot CPM values (subset domains), NOT from
 * the implementation under test:
 * - fixture A: domain {30.0, 30.5, 33.0}, stats(atk=100, def=100, sta=70), maxHp=52:
 *   HP 52 is achievable at 30.0 (stamina IV 2), 30.5 (IV 1 or 2) and 33.0 (IV 0);
 *   CP 470 is jointly achievable at all three levels, CP 554 ONLY at 33.0 (the gap
 *   between the two 5000-stardust windows), and CP 613 nowhere (HP possible, CP
 *   cannot coexist with the same HP/level/stamina witness).
 * - fixture B: domain {49.0..51.0}, stats(100, 100, 55), maxHp=50: CP 656 is jointly
 *   achievable ONLY at effective 50.0 and 51.0; effective 51.0 has no normal
 *   underlying interpretation (underlying levels stop at 50.0), so it can only be
 *   witnessed as underlying 50.0 with a Best Buddy offset.
 */
@Suppress("MagicNumber")
class ProfileTupleFeasibilityTest {

    private val realCpm: Map<Double, Double> = Gson().fromJson<Map<String, Double>>(
        cpmValuesJson(
            File(RecognitionSnapshotTestSupport.assetDir(), "recognition_profiles.json").readText()
        ),
        object : TypeToken<Map<String, Double>>() {}.type
    ).mapKeys { it.key.toDouble() }

    /** Extracts only the cpMultipliers values object without Android JSON classes. */
    private fun cpmValuesJson(recognitionJson: String): String {
        val valuesStart = recognitionJson.indexOf("\"values\"")
        require(valuesStart > 0)
        val objectStart = recognitionJson.indexOf('{', valuesStart)
        var depth = 0
        for (index in objectStart until recognitionJson.length) {
            when (recognitionJson[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return recognitionJson.substring(objectStart, index + 1)
                }
            }
        }
        error("cpMultipliers values object not found")
    }

    private val fixtureADomain = mapOf(
        30.0 to realCpm.getValue(30.0),
        30.5 to realCpm.getValue(30.5),
        33.0 to realCpm.getValue(33.0)
    )
    private val fixtureAStats = RecognitionSnapshot.ProfileStats(atk = 100, def = 100, sta = 70)
    private val fixtureAMaxHp = 52

    private val fixtureBDomain = listOf(49.0, 49.5, 50.0, 50.5, 51.0)
        .associateWith { realCpm.getValue(it) }
    private val fixtureBStats = RecognitionSnapshot.ProfileStats(atk = 100, def = 100, sta = 55)
    private val fixtureBMaxHp = 50

    private fun witnesses(
        stats: RecognitionSnapshot.ProfileStats = fixtureAStats,
        cp: Int?,
        maxHp: Int? = fixtureAMaxHp,
        domain: Map<Double, Double> = fixtureADomain,
        appraisal: ExactIvConstraints? = null
    ) = ProfileTupleFeasibility.legalWitnesses(stats, cp, maxHp, domain, appraisal)

    private fun effectiveLevels(witnesses: List<ProfileTupleFeasibility.LevelWitness>) =
        witnesses.map { it.effectiveLevel }.distinct().sorted()

    private fun underlyingLevels(witnesses: List<ProfileTupleFeasibility.LevelWitness>) =
        witnesses.map { it.underlyingLevel }.distinct().sorted()

    private fun levels5000(): StardustLevelEvidence.Levels = StardustLevelEvidence.Levels(
        levels = listOf(29.0, 29.5, 30.0, 30.5) + listOf(39.0, 39.5, 40.0, 40.5),
        minLevel = 29.0,
        maxLevel = 40.5,
        modifiersUsed = listOf(com.pokerarity.scanner.util.ocr.PowerUpCostModifier.NORMAL),
        interpretations = listOf("tier14_normal", "tier19_lucky"),
        reasonCodes = listOf("anchored_power_up_row", "cost_modifier_unknown")
    )

    private fun levels15000(): StardustLevelEvidence.Levels = StardustLevelEvidence.Levels(
        levels = listOf(49.0, 49.5),
        minLevel = 49.0,
        maxLevel = 49.5,
        modifiersUsed = listOf(com.pokerarity.scanner.util.ocr.PowerUpCostModifier.NORMAL),
        interpretations = listOf("tier24_normal"),
        reasonCodes = listOf("anchored_power_up_row", "cost_modifier_unknown")
    )

    // -- basic same-witness math ---------------------------------------------------------

    @Test
    fun legalSameWitnessTupleSurvives() {
        // CP 470 + maxHp 52 are jointly achievable at 30.0, 30.5 and 33.0.
        assertEquals(listOf(30.0, 30.5, 33.0), effectiveLevels(witnesses(cp = 470)))
    }

    @Test
    fun hpPossibleButCpCannotCoexistWithTheSameWitnessIsEliminated() {
        // HP 52 is possible, but CP 613 is not achievable at ANY level with the
        // stamina IV that witness requires: no legal tuple exists.
        assertTrue(witnesses(cp = null).isNotEmpty())
        assertTrue(witnesses(cp = 613).isEmpty())
    }

    @Test
    fun cpAloneWithoutMaxHpIsNeverASameWitnessAuthority() {
        assertTrue(witnesses(cp = 470, maxHp = null).isEmpty())
    }

    @Test
    fun maxHpAlonePreservesTheSupportedBehaviorWithoutRequiringCp() {
        val hpOnly = witnesses(cp = null)
        assertEquals(listOf(30.0, 30.5, 33.0), effectiveLevels(hpOnly))
    }

    @Test
    fun cpAndHpMustBeSatisfiedByTheSameLevelAndStaminaWitness() {
        // At effective 33.0 only stamina IV 0 achieves maxHp 52, so a legal witness
        // there must carry exactly that stamina IV — CP satisfaction at another
        // stamina/level pair must not fabricate it.
        val at33Normal = witnesses(cp = 470).filter { it.effectiveLevel == 33.0 && !it.bestBuddyOffset }
        assertEquals(1, at33Normal.size)
        assertEquals(0, at33Normal.single().minStaminaIv)
        assertEquals(0, at33Normal.single().maxStaminaIv)
        assertEquals(1, at33Normal.single().staminaIvCount)
        assertTrue(at33Normal.single().ivTupleCount >= 1)
    }

    // -- underlying versus witnessed/effective level -------------------------------------

    @Test
    fun effective51IsOnlyWitnessedAsUnderlying50WithBestBuddyOffset() {
        val all = witnesses(fixtureBStats, cp = 656, maxHp = fixtureBMaxHp, domain = fixtureBDomain)
        // CP 656 is jointly achievable only at effective 50.0 and 51.0.
        assertEquals(listOf(50.0, 51.0), effectiveLevels(all))
        // Underlying levels stop at 50.0: no witness may claim a higher base level.
        assertTrue(underlyingLevels(all).max() <= ProfileTupleFeasibility.MAX_UNDERLYING_LEVEL)
        val at51 = all.filter { it.effectiveLevel == 51.0 }
        assertTrue(at51.all { it.bestBuddyOffset && it.underlyingLevel == 50.0 })
        // Effective 50.0 keeps BOTH interpretations (normal and Best Buddy +1).
        val at50 = all.filter { it.effectiveLevel == 50.0 }
        assertEquals(setOf(false, true), at50.map { it.bestBuddyOffset }.toSet())
        assertEquals(setOf(50.0, 49.0), at50.map { it.underlyingLevel }.toSet())
    }

    @Test
    fun noUnderlyingLevelEverExceedsThePowerUpCapOnTheFullDomain() {
        val stats = RecognitionSnapshot.ProfileStats(atk = 150, def = 120, sta = 90)
        val all = ProfileTupleFeasibility.legalWitnesses(stats, cp = 1200, maxHp = 85, cpMultipliers = realCpm)
        assertTrue(all.isNotEmpty())
        assertTrue(all.none { it.underlyingLevel > ProfileTupleFeasibility.MAX_UNDERLYING_LEVEL })
        assertTrue(all.size <= 2 * realCpm.size)
    }

    // -- Phase 3A stardust evidence intersection -----------------------------------------

    @Test
    fun disjointStardustSetsStayDiscreteInsideFeasibility() {
        val constrained = ProfileTupleFeasibility.constrainToUnderlyingLevels(
            witnesses(cp = 470), levels5000()) as StardustIntersection.Constrained
        // Underlying 29.0/29.5 (Best Buddy bases of effective 30.0/30.5) and 30.0/30.5
        // lie in the first 5000 window; effective 33.0 lies in the GAP between the two
        // windows and must be rejected even though it is inside min..max.
        assertEquals(listOf(29.0, 29.5, 30.0, 30.5), underlyingLevels(constrained.witnesses))
        assertTrue(constrained.witnesses.none { it.effectiveLevel == 33.0 })
    }

    @Test
    fun witnessInsideTheWindowGapIsRejected() {
        // CP 554 has its only legal tuple at effective 33.0 (underlying 33.0/32.0),
        // inside the 29.0..40.5 span but in the gap between the legal windows.
        val gapWitnesses = witnesses(cp = 554)
        assertEquals(listOf(33.0), effectiveLevels(gapWitnesses))
        val constrained = ProfileTupleFeasibility.constrainToUnderlyingLevels(
            gapWitnesses, levels5000()) as StardustIntersection.Constrained
        assertTrue(constrained.witnesses.isEmpty())
    }

    @Test
    fun stardust15000NeverCreatesUnderlyingLevelsAtOrAbove50() {
        val constrained = ProfileTupleFeasibility.constrainToUnderlyingLevels(
            witnesses(fixtureBStats, cp = 656, maxHp = fixtureBMaxHp, domain = fixtureBDomain),
            levels15000()
        ) as StardustIntersection.Constrained
        // Only the Best Buddy witness (underlying 49.0, effective 50.0) survives.
        assertEquals(listOf(49.0), underlyingLevels(constrained.witnesses))
        assertTrue(constrained.witnesses.single().bestBuddyOffset)
        assertEquals(50.0, constrained.witnesses.single().effectiveLevel, 0.0)
    }

    @Test
    fun evidenceStatesMapToExplicitIntersectionSemantics() {
        val anyWitnesses = witnesses(cp = 470)
        assertTrue(
            ProfileTupleFeasibility.constrainToUnderlyingLevels(anyWitnesses, null) is
                StardustIntersection.NotConstraining)
        assertTrue(
            ProfileTupleFeasibility.constrainToUnderlyingLevels(
                anyWitnesses, StardustLevelEvidence.Missing(listOf("action_not_detected"))) is
                StardustIntersection.NotConstraining)
        assertTrue(
            ProfileTupleFeasibility.constrainToUnderlyingLevels(
                anyWitnesses, StardustLevelEvidence.Unreadable(listOf("cost_token_unreadable"))) is
                StardustIntersection.NotConstraining)
        assertTrue(
            ProfileTupleFeasibility.constrainToUnderlyingLevels(
                anyWitnesses,
                StardustLevelEvidence.Unsupported(listOf("anchored_power_up_row", "lucky_shadow_cost_unsupported"))) is
                StardustIntersection.NotConstraining)
        // Invalid dust is no legal underlying level: contradiction, never a guess
        // and never a wildcard search over every level.
        val invalid = ProfileTupleFeasibility.constrainToUnderlyingLevels(
            anyWitnesses,
            StardustLevelEvidence.Invalid(
                1_234, listOf("anchored_power_up_row", "cost_not_a_displayed_power_up_value")))
        assertTrue(invalid is StardustIntersection.Constrained)
        assertTrue((invalid as StardustIntersection.Constrained).witnesses.isEmpty())
        // Conflict stays fail-closed with its bounded reason codes.
        val conflict = ProfileTupleFeasibility.constrainToUnderlyingLevels(
            anyWitnesses,
            StardustLevelEvidence.Conflict(2, listOf("anchored_power_up_row", "multiple_distinct_costs")))
        assertTrue(conflict is StardustIntersection.Conflict)
        assertTrue((conflict as StardustIntersection.Conflict)
            .reasonCodes.contains("multiple_distinct_costs"))
    }

    // -- optional appraisal seam (pure synthetic evidence only) --------------------------

    @Test
    fun exactIvConstraintNarrowsTheSameTupleSet() {
        // Stamina IV 2 only witnesses effective 30.0, so constraining stamina to 1
        // removes that tuple while the same-IV witness at 30.5 survives.
        val unconstrained = witnesses(cp = 470)
        val constrained = witnesses(cp = 470, appraisal = ExactIvConstraints(stamina = 1))
        assertEquals(listOf(30.5), effectiveLevels(constrained))
        assertTrue(constrained.size < unconstrained.size)
    }

    @Test
    fun impossibleExactIvConstraintYieldsNoTuple() {
        assertTrue(
            witnesses(cp = 470, appraisal = ExactIvConstraints(stamina = 15)).isEmpty())
    }

    @Test
    fun exactIvConstraintRestrictsTheSameIvTuple() {
        // The 33.0 witness for CP 470 is provable by exactly ONE IV tuple (attack 0,
        // defense 0, stamina 0): the witness count pins it, an exact constraint that
        // participates keeps it, and any other exact attack removes it.
        val witness = witnesses(cp = 470)
            .filter { it.effectiveLevel == 33.0 && !it.bestBuddyOffset }
            .single()
        assertEquals(1, witness.ivTupleCount)
        assertTrue(
            witnesses(cp = 470, appraisal = ExactIvConstraints(attack = 0, defense = 0, stamina = 0))
                .any { it.effectiveLevel == 33.0 })
        assertFalse(
            witnesses(cp = 470, appraisal = ExactIvConstraints(attack = 15))
                .any { it.effectiveLevel == 33.0 })
    }

    // -- bounded enumeration -------------------------------------------------------------

    @Test
    fun enumerationIsBoundedByTheRowAndLevelDomainNotBySpecies() {
        // The helper is row-scoped (one ProfileStats in, no species list anywhere):
        // its witness count is bounded by two interpretations per domain level.
        val full = ProfileTupleFeasibility.legalWitnesses(
            fixtureAStats, cp = null, maxHp = 60, cpMultipliers = realCpm)
        assertTrue(full.isNotEmpty())
        assertTrue(full.size <= 2 * realCpm.size)
    }

    @Test
    fun effectiveLevelProjectionMatchesThePublishedFormulaReplica() {
        // Independent replica of the former matchingProfileLevels loop (published
        // game formulas), swept over the real domain for the fixture rows.
        val replica: (RecognitionSnapshot.ProfileStats, Int?, Int?) -> Set<Double> =
            ::replicaMatchingLevels
        val cases = listOf(
            Triple(fixtureAStats, 470, fixtureAMaxHp),
            Triple(fixtureAStats, 554, fixtureAMaxHp),
            Triple(fixtureAStats, null, fixtureAMaxHp),
            Triple(fixtureBStats, 656, fixtureBMaxHp),
            Triple(RecognitionSnapshot.ProfileStats(150, 120, 90), 1200, 85)
        )
        cases.forEach { (stats, cp, maxHp) ->
            val projected = ProfileTupleFeasibility
                .legalWitnesses(stats, cp, maxHp, realCpm)
                .map { it.effectiveLevel }.toSet()
            assertEquals(replica(stats, cp, maxHp), projected)
        }
    }

/**
 * Verbatim replica of the former matchingProfileLevels loop (published game formulas);
 * its nested loop shape is intentionally preserved as the historical parity oracle.
 */
@Suppress("LoopWithTooManyJumpStatements", "NestedBlockDepth")
private fun replicaMatchingLevels(
    stats: RecognitionSnapshot.ProfileStats,
    cp: Int?,
    maxHp: Int?
): Set<Double> {
    val levels = linkedSetOf<Double>()
    if (maxHp == null) return levels
    for ((level, cpm) in realCpm) {
        for (stamina in 0..15) {
            if (max(10, floor((stats.sta + stamina) * cpm).toInt()) != maxHp) continue
            val cpMatches = cp == null || (0..15).any { attack ->
                (0..15).any { defense ->
                    max(10, floor(
                        (stats.atk + attack) * sqrt((stats.def + defense).toDouble()) *
                            sqrt((stats.sta + stamina).toDouble()) * cpm * cpm / 10
                    ).toInt()) == cp
                }
            }
            if (cpMatches) { levels += level; break }
        }
    }
    return levels
}

}
