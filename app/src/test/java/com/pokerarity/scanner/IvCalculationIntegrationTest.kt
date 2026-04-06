package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.floor

@RunWith(RobolectricTestRunner::class)
class IvCalculationIntegrationTest {

    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val calculator = RarityCalculator(context)
    private val baseStats = BaseStats(atk = 195, def = 82, sta = 93)
    private val species = "Abra"

    @Test
    fun hpCpArcPresent_returnsIvEstimate() {
        val sample = buildValidPokemon(stardust = firstStardustKey())

        val rarity = calculator.calculate(sample, VisualFeatures())

        assertNotNull(rarity.ivEstimate)
        assertTrue(rarity.ivEstimate!!.isNotBlank())
        assertFalse(rarity.ivEstimate == "???")
    }

    @Test
    fun invalidStardust_fallsBackToArcOnlyAndStillCalculatesEstimate() {
        val goodStardust = firstStardustKey()
        val badStardust = anotherStardustKey(goodStardust)
        val sample = buildValidPokemon(stardust = badStardust)

        val rarity = calculator.calculate(sample, VisualFeatures())

        assertNotNull(rarity.ivEstimate)
        assertTrue(rarity.ivEstimate!!.isNotBlank())
        assertFalse(rarity.ivEstimate == "???")
    }

    @Test
    fun missingStardust_stillCalculatesEstimateFromArcAndHp() {
        val sample = buildValidPokemon(stardust = firstStardustKey()).copy(stardust = null)

        val rarity = calculator.calculate(sample, VisualFeatures())

        assertNotNull(rarity.ivEstimate)
        assertTrue(rarity.ivEstimate!!.isNotBlank())
        assertFalse(rarity.ivEstimate == "???")
        assertTrue(parseIvRangeWidth(rarity.ivEstimate!!) <= 20)
    }

    @Test
    fun missingCurrentHp_usesMaxHpFallbackForEstimate() {
        val sample = buildValidPokemon(stardust = firstStardustKey()).copy(
            hp = null,
            maxHp = buildValidPokemon(stardust = firstStardustKey()).hp
        )

        val rarity = calculator.calculate(sample, VisualFeatures())

        assertNotNull(rarity.ivEstimate)
        assertTrue(rarity.ivEstimate!!.isNotBlank())
        assertFalse(rarity.ivEstimate == "???")
    }

    @Test
    fun missingData_returnsNullOrUnknownEstimatePath() {
        val missingHp = buildValidPokemon(stardust = firstStardustKey()).copy(hp = null)

        val rarity = calculator.calculate(missingHp, VisualFeatures())

        assertTrue(rarity.ivEstimate == null || rarity.ivEstimate == "???")
        assertNull(rarity.ivEstimate)
    }

    @Test
    fun ivBonus_isReflectedInTotalScore() {
        val stardust = firstStardustKey()
        val highIv = buildValidPokemon(stardust = stardust, ivAtk = 15, ivDef = 15, ivSta = 15)
        val lowIv = buildValidPokemon(stardust = stardust, ivAtk = 0, ivDef = 0, ivSta = 0)

        val highScore = calculator.calculate(highIv, VisualFeatures())
        val lowScore = calculator.calculate(lowIv, VisualFeatures())

        val highBonus = highScore.breakdown["IV Bonus"] ?: 0
        val lowBonus = lowScore.breakdown["IV Bonus"] ?: 0

        assertTrue(highBonus > lowBonus)
        assertEquals(highBonus - lowBonus, highScore.totalScore - lowScore.totalScore)
    }

    private fun buildValidPokemon(
        stardust: Int,
        ivAtk: Int = 15,
        ivDef: Int = 15,
        ivSta: Int = 15,
    ): PokemonData {
        val level = stardustLevelStart(stardust)
        val cpm = cpm(level)
        val cp = calculateCp(baseStats.atk, baseStats.def, baseStats.sta, ivAtk, ivDef, ivSta, level)
        val hp = floor((baseStats.sta + ivSta) * cpm).toInt()
        val arc = ((level - 1.0) / 49.0).toFloat()

        return PokemonData(
            cp = cp,
            hp = hp,
            maxHp = null,
            name = species,
            realName = species,
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = stardust,
            arcLevel = arc,
            caughtDate = null,
            rawOcrText = ""
        )
    }

    private fun firstStardustKey(): Int = STARDUST_TO_LEVEL.keys.first()

    private fun anotherStardustKey(excluding: Int): Int =
        STARDUST_TO_LEVEL.keys.first { it != excluding }

    private fun stardustLevelStart(stardust: Int): Double =
        STARDUST_TO_LEVEL[stardust]!!.start

    private fun cpm(level: Double): Double =
        CPM[level]!!

    private fun calculateCp(
        atk: Int,
        def: Int,
        sta: Int,
        ivAtk: Int,
        ivDef: Int,
        ivSta: Int,
        level: Double,
    ): Int {
        val method = RarityCalculator::class.java.getDeclaredMethod(
            "calculateCP",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(calculator, atk, def, sta, ivAtk, ivDef, ivSta, level) as Int
    }

    private fun parseIvRangeWidth(range: String): Int {
        val parts = range.replace("%", "").split("-").map { it.trim().toInt() }
        return if (parts.size == 1) 0 else parts[1] - parts[0]
    }

    private data class BaseStats(val atk: Int, val def: Int, val sta: Int)

    companion object {
        private val STARDUST_TO_LEVEL = linkedMapOf(
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
