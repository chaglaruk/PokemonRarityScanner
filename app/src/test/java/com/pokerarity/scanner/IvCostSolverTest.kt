package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.repository.IvCostSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.floor
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
class IvCostSolverTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val stats = loadBaseStats()

    @Test
    fun exactSolution_case_returnsExactOnly() {
        val fixture = findFixture(exact = true, requireArc = false)
        val result = IvCostSolver.solve(
            species = fixture.species,
            stats = IvCostSolver.BaseStats(fixture.atk, fixture.def, fixture.sta),
            cp = fixture.cp,
            hp = fixture.hp,
            stardustCost = fixture.stardust,
            candyCost = fixture.candyCost,
            arcLevel = null,
            stateHint = IvCostSolver.PokemonState.REGULAR
        )
        println("EXACT_FIXTURE species=${fixture.species} cp=${fixture.cp} hp=${fixture.hp} stardust=${fixture.stardust} candy=${fixture.candyCost} result=$result")

        assertEquals(IvCostSolver.SolveMode.EXACT, result.ivSolveMode)
        assertEquals(1, result.ivCandidateCount)
        assertEquals(fixture.ivPercent, result.ivExact)
        assertEquals(fixture.ivPercent, result.ivMin)
        assertEquals(fixture.ivPercent, result.ivMax)
    }

    @Test
    fun ambiguousCase_returnsRangeAndNeverFalseExact() {
        val fixture = findFixture(exact = false, requireArc = false)
        val result = IvCostSolver.solve(
            species = fixture.species,
            stats = IvCostSolver.BaseStats(fixture.atk, fixture.def, fixture.sta),
            cp = fixture.cp,
            hp = fixture.hp,
            stardustCost = fixture.stardust,
            candyCost = fixture.candyCost,
            arcLevel = null,
            stateHint = IvCostSolver.PokemonState.REGULAR
        )
        println("RANGE_FIXTURE species=${fixture.species} cp=${fixture.cp} hp=${fixture.hp} stardust=${fixture.stardust} candy=${fixture.candyCost} result=$result")

        assertEquals(IvCostSolver.SolveMode.RANGE, result.ivSolveMode)
        assertTrue(result.ivCandidateCount > 1)
        assertNull(result.ivExact)
        assertNotNull(result.ivMin)
        assertNotNull(result.ivMax)
        assertTrue(result.ivMin!! < result.ivMax!!)
    }

    @Test
    fun missingArc_stillUsesCostsAndReturnsUsefulResult() {
        val fixture = findFixture(exact = false, requireArc = false)
        val result = IvCostSolver.solve(
            species = fixture.species,
            stats = IvCostSolver.BaseStats(fixture.atk, fixture.def, fixture.sta),
            cp = fixture.cp,
            hp = fixture.hp,
            stardustCost = fixture.stardust,
            candyCost = fixture.candyCost,
            arcLevel = null,
            stateHint = IvCostSolver.PokemonState.REGULAR
        )
        println("NO_ARC_FIXTURE species=${fixture.species} cp=${fixture.cp} hp=${fixture.hp} stardust=${fixture.stardust} candy=${fixture.candyCost} result=$result")

        assertTrue(result.ivSolveMode != IvCostSolver.SolveMode.INSUFFICIENT)
        assertTrue(result.ivCandidateCount > 0)
        assertNotNull(result.levelMin)
        assertNotNull(result.levelMax)
        assertTrue(result.ivSolveSignalsUsed.contains("cp"))
        assertTrue(result.ivSolveSignalsUsed.contains("hp"))
        assertTrue(result.ivSolveSignalsUsed.contains("stardust"))
    }

    @Test
    fun insufficientData_returnsInsufficient() {
        val fixture = findFixture(exact = false, requireArc = false)
        val result = IvCostSolver.solve(
            species = fixture.species,
            stats = IvCostSolver.BaseStats(fixture.atk, fixture.def, fixture.sta),
            cp = null,
            hp = fixture.hp,
            stardustCost = fixture.stardust,
            candyCost = fixture.candyCost,
            arcLevel = null,
            stateHint = IvCostSolver.PokemonState.REGULAR
        )
        println("INSUFFICIENT_FIXTURE species=${fixture.species} cp=null hp=${fixture.hp} stardust=${fixture.stardust} candy=${fixture.candyCost} result=$result")

        assertEquals(IvCostSolver.SolveMode.INSUFFICIENT, result.ivSolveMode)
        assertEquals(0, result.ivCandidateCount)
        assertNull(result.ivExact)
    }

    @Test
    fun missingHp_returnsInsufficient() {
        val fixture = findFixture(exact = false, requireArc = false)
        val result = IvCostSolver.solve(
            species = fixture.species,
            stats = IvCostSolver.BaseStats(fixture.atk, fixture.def, fixture.sta),
            cp = fixture.cp,
            hp = null,
            stardustCost = fixture.stardust,
            candyCost = fixture.candyCost,
            arcLevel = null,
            stateHint = IvCostSolver.PokemonState.REGULAR
        )

        assertEquals(IvCostSolver.SolveMode.INSUFFICIENT, result.ivSolveMode)
        assertEquals(0, result.ivCandidateCount)
        assertNull(result.ivExact)
    }

    @Test
    fun bothCostsPresent_narrowsMoreThanNoCosts() {
        val fixture = findNarrowingFixture(NarrowingKind.BOTH)
        val withBoth = solveFixture(fixture, stardustCost = fixture.stardust, candyCost = fixture.candyCost)
        val withNone = solveFixture(fixture, stardustCost = null, candyCost = null)
        println("BOTH_COSTS_FIXTURE species=${fixture.species} cp=${fixture.cp} hp=${fixture.hp} stardust=${fixture.stardust} candy=${fixture.candyCost} both=${withBoth.ivCandidateCount} none=${withNone.ivCandidateCount}")

        assertTrue(withBoth.ivCandidateCount > 0)
        assertTrue(withNone.ivCandidateCount > withBoth.ivCandidateCount)
    }

    @Test
    fun candyOnlyStillNarrowsAgainstNoCosts() {
        val fixture = findNarrowingFixture(NarrowingKind.CANDY_ONLY)
        val withCandyOnly = solveFixture(fixture, stardustCost = null, candyCost = fixture.candyCost)
        val withNone = solveFixture(fixture, stardustCost = null, candyCost = null)
        println("CANDY_ONLY_FIXTURE species=${fixture.species} cp=${fixture.cp} hp=${fixture.hp} candy=${fixture.candyCost} candyOnly=${withCandyOnly.ivCandidateCount} none=${withNone.ivCandidateCount}")

        assertTrue(withCandyOnly.ivCandidateCount > 0)
        assertTrue(withNone.ivCandidateCount > withCandyOnly.ivCandidateCount)
    }

    @Test
    fun stardustOnlyStillNarrowsAgainstNoCosts() {
        val fixture = findNarrowingFixture(NarrowingKind.STARDUST_ONLY)
        val withStardustOnly = solveFixture(fixture, stardustCost = fixture.stardust, candyCost = null)
        val withNone = solveFixture(fixture, stardustCost = null, candyCost = null)
        println("STARDUST_ONLY_FIXTURE species=${fixture.species} cp=${fixture.cp} hp=${fixture.hp} stardust=${fixture.stardust} stardustOnly=${withStardustOnly.ivCandidateCount} none=${withNone.ivCandidateCount}")

        assertTrue(withStardustOnly.ivCandidateCount > 0)
        assertTrue(withNone.ivCandidateCount > withStardustOnly.ivCandidateCount)
    }

    @Test
    fun exactCase_staysExactEvenWithoutArc() {
        val fixture = findFixture(exact = true, requireArc = false)
        val result = solveFixture(fixture, stardustCost = fixture.stardust, candyCost = fixture.candyCost)

        assertEquals(IvCostSolver.SolveMode.EXACT, result.ivSolveMode)
        assertEquals(1, result.ivCandidateCount)
    }

    @Test
    fun costsCanUpgradeRangeToExact_whenDataSupportsIt() {
        val fixture = findRangeToExactFixture()
        val withCosts = solveFixture(fixture, stardustCost = fixture.stardust, candyCost = fixture.candyCost)
        val withoutCosts = solveFixture(fixture, stardustCost = null, candyCost = null)
        println("RANGE_TO_EXACT_FIXTURE species=${fixture.species} cp=${fixture.cp} hp=${fixture.hp} stardust=${fixture.stardust} candy=${fixture.candyCost} withCosts=${withCosts.ivCandidateCount}/${withCosts.ivSolveMode} withoutCosts=${withoutCosts.ivCandidateCount}/${withoutCosts.ivSolveMode}")

        assertEquals(IvCostSolver.SolveMode.EXACT, withCosts.ivSolveMode)
        assertEquals(IvCostSolver.SolveMode.RANGE, withoutCosts.ivSolveMode)
        assertTrue(withoutCosts.ivCandidateCount > 1)
    }

    @Test
    fun noFalseExactWhenMultipleCandidatesRemain() {
        val fixture = findFixture(exact = false, requireArc = false)
        val result = solveFixture(fixture, stardustCost = fixture.stardust, candyCost = fixture.candyCost)

        assertEquals(IvCostSolver.SolveMode.RANGE, result.ivSolveMode)
        assertNull(result.ivExact)
        assertTrue(result.ivCandidateCount > 1)
    }

    @Test
    fun bogusCandyDoesNotCollapseValidCpHpSolveToInsufficient() {
        val fixture = findFixture(exact = false, requireArc = false)
        val result = solveFixture(fixture, stardustCost = null, candyCost = 99)

        assertEquals(IvCostSolver.SolveMode.RANGE, result.ivSolveMode)
        assertTrue(result.ivCandidateCount > 0)
        assertTrue(result.ivSolveSignalsUsed.contains("cp"))
        assertTrue(result.ivSolveSignalsUsed.contains("hp"))
        assertTrue(!result.ivSolveSignalsUsed.contains("candy"))
    }

    @Test
    fun xlCandyBucketStillProducesCandidates() {
        val fixture = findFixtureForCandyCost(17)
        val result = solveFixture(fixture, stardustCost = fixture.stardust, candyCost = fixture.candyCost)

        assertTrue(result.ivSolveMode != IvCostSolver.SolveMode.INSUFFICIENT)
        assertTrue(result.ivCandidateCount > 0)
        assertTrue(result.ivSolveSignalsUsed.contains("candy"))
    }

    private fun solveFixture(
        fixture: Fixture,
        stardustCost: Int?,
        candyCost: Int?
    ): IvCostSolver.Result {
        return IvCostSolver.solve(
            species = fixture.species,
            stats = IvCostSolver.BaseStats(fixture.atk, fixture.def, fixture.sta),
            cp = fixture.cp,
            hp = fixture.hp,
            stardustCost = stardustCost,
            candyCost = candyCost,
            arcLevel = null,
            stateHint = IvCostSolver.PokemonState.REGULAR
        )
    }

    private fun findNarrowingFixture(kind: NarrowingKind): Fixture {
        for ((species, base) in stats.entries) {
            for ((stardust, levelRange) in REGULAR_STARDUST_TO_LEVEL) {
                val candyCost = candyCostForLevel(levelRange.start)
                for (level in halfLevels(levelRange.start, levelRange.endInclusive)) {
                    for (ivAtk in 0..15) {
                        for (ivDef in 0..15) {
                            for (ivSta in 0..15) {
                                val cp = calculateCp(base.atk, base.def, base.sta, ivAtk, ivDef, ivSta, level)
                                val hp = calculateHp(base.sta, ivSta, level)
                                val fixture = Fixture(
                                    species = species,
                                    atk = base.atk,
                                    def = base.def,
                                    sta = base.sta,
                                    cp = cp,
                                    hp = hp,
                                    stardust = stardust,
                                    candyCost = candyCost,
                                    ivPercent = (ivAtk + ivDef + ivSta) * 100 / 45
                                )
                                val none = solveFixture(fixture, null, null).ivCandidateCount
                                val candyOnly = solveFixture(fixture, null, candyCost).ivCandidateCount
                                val stardustOnly = solveFixture(fixture, stardust, null).ivCandidateCount
                                val both = solveFixture(fixture, stardust, candyCost).ivCandidateCount
                                val matched = when (kind) {
                                    NarrowingKind.BOTH -> both in 1 until none
                                    NarrowingKind.CANDY_ONLY -> candyOnly in 1 until none
                                    NarrowingKind.STARDUST_ONLY -> stardustOnly in 1 until none
                                }
                                if (matched) {
                                    return fixture
                                }
                            }
                        }
                    }
                }
            }
        }
        error("No narrowing fixture found for $kind")
    }

    private fun findRangeToExactFixture(): Fixture {
        for ((species, base) in stats.entries) {
            for ((stardust, levelRange) in REGULAR_STARDUST_TO_LEVEL) {
                val candyCost = candyCostForLevel(levelRange.start)
                for (level in halfLevels(levelRange.start, levelRange.endInclusive)) {
                    for (ivAtk in 0..15) {
                        for (ivDef in 0..15) {
                            for (ivSta in 0..15) {
                                val fixture = Fixture(
                                    species = species,
                                    atk = base.atk,
                                    def = base.def,
                                    sta = base.sta,
                                    cp = calculateCp(base.atk, base.def, base.sta, ivAtk, ivDef, ivSta, level),
                                    hp = calculateHp(base.sta, ivSta, level),
                                    stardust = stardust,
                                    candyCost = candyCost,
                                    ivPercent = (ivAtk + ivDef + ivSta) * 100 / 45
                                )
                                val withCosts = solveFixture(fixture, stardust, candyCost)
                                val withoutCosts = solveFixture(fixture, null, null)
                                if (withCosts.ivSolveMode == IvCostSolver.SolveMode.EXACT &&
                                    withoutCosts.ivSolveMode == IvCostSolver.SolveMode.RANGE
                                ) {
                                    return fixture
                                }
                            }
                        }
                    }
                }
            }
        }
        error("No range-to-exact fixture found")
    }

    private fun findFixtureForCandyCost(targetCandy: Int): Fixture {
        for ((species, base) in stats.entries) {
            for ((stardust, levelRange) in REGULAR_STARDUST_TO_LEVEL) {
                for (level in halfLevels(levelRange.start, levelRange.endInclusive)) {
                    if (candyCostForLevel(level) != targetCandy) continue
                    for (ivAtk in 0..15) {
                        for (ivDef in 0..15) {
                            for (ivSta in 0..15) {
                                return Fixture(
                                    species = species,
                                    atk = base.atk,
                                    def = base.def,
                                    sta = base.sta,
                                    cp = calculateCp(base.atk, base.def, base.sta, ivAtk, ivDef, ivSta, level),
                                    hp = calculateHp(base.sta, ivSta, level),
                                    stardust = stardust,
                                    candyCost = targetCandy,
                                    ivPercent = (ivAtk + ivDef + ivSta) * 100 / 45
                                )
                            }
                        }
                    }
                }
            }
        }
        error("No fixture found for candy cost $targetCandy")
    }

    private fun findFixture(exact: Boolean, requireArc: Boolean): Fixture {
        for ((species, base) in stats.entries.take(60)) {
            for ((stardust, levelRange) in REGULAR_STARDUST_TO_LEVEL) {
                val candyCost = candyCostForLevel(levelRange.start)
                for (level in halfLevels(levelRange.start, levelRange.endInclusive)) {
                    for (ivAtk in 0..15) {
                        for (ivDef in 0..15) {
                            for (ivSta in 0..15) {
                                val cp = calculateCp(base.atk, base.def, base.sta, ivAtk, ivDef, ivSta, level)
                                val hp = calculateHp(base.sta, ivSta, level)
                                val candidateCount = enumerateCandidates(
                                    atk = base.atk,
                                    def = base.def,
                                    sta = base.sta,
                                    cp = cp,
                                    hp = hp,
                                    stardust = stardust,
                                    candyCost = candyCost,
                                    arcLevel = if (requireArc) ((level - 1.0) / 49.0).toFloat() else null
                                ).size
                                if ((exact && candidateCount == 1) || (!exact && candidateCount > 1)) {
                                    val total = ivAtk + ivDef + ivSta
                                    return Fixture(
                                        species = species,
                                        atk = base.atk,
                                        def = base.def,
                                        sta = base.sta,
                                        cp = cp,
                                        hp = hp,
                                        stardust = stardust,
                                        candyCost = candyCost,
                                        ivPercent = total * 100 / 45
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        error("No suitable fixture found")
    }

    private fun enumerateCandidates(
        atk: Int,
        def: Int,
        sta: Int,
        cp: Int,
        hp: Int,
        stardust: Int,
        candyCost: Int?,
        arcLevel: Float?
    ): List<Int> {
        val levels = halfLevels(
            REGULAR_STARDUST_TO_LEVEL[stardust]!!.start,
            REGULAR_STARDUST_TO_LEVEL[stardust]!!.endInclusive
        ).filter { candyCost == null || candyCostForLevel(it) == candyCost }
        val arcTarget = arcLevel?.let { (it * 49.0 + 1.0).coerceIn(1.0, 50.0) }
        return buildList {
            for (level in levels) {
                if (arcTarget != null && kotlin.math.abs(level - arcTarget) > 2.0) continue
                val cpm = CPM[level] ?: continue
                for (ivSta in 0..15) {
                    val calcHp = floor((sta + ivSta) * cpm).toInt()
                    if (calcHp != hp) continue
                    for (ivAtk in 0..15) {
                        for (ivDef in 0..15) {
                            val calcCp = calculateCp(atk, def, sta, ivAtk, ivDef, ivSta, level)
                            if (calcCp == cp) add(ivAtk + ivDef + ivSta)
                        }
                    }
                }
            }
        }
    }

    private fun loadBaseStats(): Map<String, Base> = linkedMapOf(
        "Abomasnow" to Base(atk = 178, def = 158, sta = 207),
        "Abra" to Base(atk = 195, def = 82, sta = 93),
        "Absol" to Base(atk = 246, def = 120, sta = 163),
        "Aerodactyl" to Base(atk = 221, def = 159, sta = 190),
        "Aggron" to Base(atk = 198, def = 257, sta = 172)
    )

    private fun calculateCp(
        atk: Int,
        def: Int,
        sta: Int,
        ivAtk: Int,
        ivDef: Int,
        ivSta: Int,
        level: Double
    ): Int {
        val cpm = CPM[level] ?: error("Missing CPM for $level")
        val raw = ((atk + ivAtk) * sqrt((def + ivDef).toDouble()) * sqrt((sta + ivSta).toDouble()) * cpm * cpm) / 10.0
        return maxOf(10, floor(raw).toInt())
    }

    private fun calculateHp(sta: Int, ivSta: Int, level: Double): Int {
        val cpm = CPM[level] ?: error("Missing CPM for $level")
        return floor((sta + ivSta) * cpm).toInt()
    }

    private fun candyCostForLevel(level: Double): Int = when {
        level <= 10.5 -> 1
        level <= 20.5 -> 2
        level <= 25.5 -> 3
        level <= 30.5 -> 4
        level <= 32.5 -> 6
        level <= 34.5 -> 8
        level <= 36.5 -> 10
        level <= 38.5 -> 12
        level <= 39.5 -> 15
        level <= 41.5 -> 10
        level <= 43.5 -> 12
        level <= 45.5 -> 15
        level <= 47.5 -> 17
        else -> 20
    }

    private fun halfLevels(start: Double, end: Double): Sequence<Double> =
        generateSequence(start) { it + 0.5 }.takeWhile { it <= end }

    private data class Base(val atk: Int, val def: Int, val sta: Int)

    private data class Fixture(
        val species: String,
        val atk: Int,
        val def: Int,
        val sta: Int,
        val cp: Int,
        val hp: Int,
        val stardust: Int,
        val candyCost: Int,
        val ivPercent: Int
    )

    private enum class NarrowingKind {
        BOTH,
        CANDY_ONLY,
        STARDUST_ONLY
    }

    companion object {
        private val REGULAR_STARDUST_TO_LEVEL = linkedMapOf(
            200 to (1.0..2.5),
            400 to (3.0..4.5),
            600 to (5.0..6.5),
            800 to (7.0..8.5),
            1000 to (9.0..10.5),
            1300 to (11.0..12.5),
            1600 to (13.0..14.5),
            1900 to (15.0..16.5),
            2200 to (17.0..18.5),
            2500 to (19.0..20.5),
            3000 to (21.0..22.5),
            3500 to (23.0..24.5),
            4000 to (25.0..26.5),
            4500 to (27.0..28.5),
            5000 to (29.0..30.5),
            6000 to (31.0..32.5),
            7000 to (33.0..34.5),
            8000 to (35.0..36.5),
            9000 to (37.0..38.5),
            10000 to (39.0..40.5),
            11000 to (41.0..42.5),
            12000 to (43.0..44.5),
            13000 to (45.0..46.5),
            14000 to (47.0..48.5),
            15000 to (49.0..50.0)
        )

        private val CPM = mapOf(
            1.0 to 0.094, 1.5 to 0.135137432, 2.0 to 0.16639787, 2.5 to 0.192650919,
            3.0 to 0.21573247, 3.5 to 0.236572661, 4.0 to 0.25572005, 4.5 to 0.273530381,
            5.0 to 0.29024988, 5.5 to 0.306057378, 6.0 to 0.3210876, 6.5 to 0.335445036,
            7.0 to 0.34921268, 7.5 to 0.362457751, 8.0 to 0.3752356, 8.5 to 0.387592416,
            9.0 to 0.39956728, 9.5 to 0.411193551, 10.0 to 0.4225, 10.5 to 0.432926409,
            11.0 to 0.44310755, 11.5 to 0.453059959, 12.0 to 0.4627984, 12.5 to 0.472336093,
            13.0 to 0.48168495, 13.5 to 0.4908558, 14.0 to 0.49985844, 14.5 to 0.508701765,
            15.0 to 0.51739395, 15.5 to 0.525942511, 16.0 to 0.5343543, 16.5 to 0.542635738,
            17.0 to 0.5507927, 17.5 to 0.558830586, 18.0 to 0.5667545, 18.5 to 0.574569133,
            19.0 to 0.5822789, 19.5 to 0.589887907, 20.0 to 0.5974, 20.5 to 0.604823665,
            21.0 to 0.6121573, 21.5 to 0.619404122, 22.0 to 0.6265671, 22.5 to 0.633649143,
            23.0 to 0.64065295, 23.5 to 0.647580967, 24.0 to 0.65443563, 24.5 to 0.661219252,
            25.0 to 0.667934, 25.5 to 0.674581896, 26.0 to 0.6811649, 26.5 to 0.687684904,
            27.0 to 0.69414365, 27.5 to 0.70054287, 28.0 to 0.7068842, 28.5 to 0.713169109,
            29.0 to 0.7193991, 29.5 to 0.725575614, 30.0 to 0.7317, 30.5 to 0.734741009,
            31.0 to 0.7377695, 31.5 to 0.740785594, 32.0 to 0.74378943, 32.5 to 0.746781211,
            33.0 to 0.74976104, 33.5 to 0.752729087, 34.0 to 0.7556855, 34.5 to 0.758630368,
            35.0 to 0.76156384, 35.5 to 0.764486065, 36.0 to 0.76739717, 36.5 to 0.770297266,
            37.0 to 0.7731865, 37.5 to 0.776064962, 38.0 to 0.77893275, 38.5 to 0.781790055,
            39.0 to 0.784637, 39.5 to 0.787473608, 40.0 to 0.7903, 40.5 to 0.792803968,
            41.0 to 0.79530001, 41.5 to 0.797800015, 42.0 to 0.8003, 42.5 to 0.802799995,
            43.0 to 0.8053, 43.5 to 0.8078, 44.0 to 0.81029999, 44.5 to 0.812799985,
            45.0 to 0.81529999, 45.5 to 0.81779999, 46.0 to 0.82029999, 46.5 to 0.82279999,
            47.0 to 0.82529999, 47.5 to 0.82779999, 48.0 to 0.83029999, 48.5 to 0.83279999,
            49.0 to 0.83529999, 49.5 to 0.83779999, 50.0 to 0.84029999
        )
    }
}
