package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
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

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val calculator = RarityCalculator(context)

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
        val baseStats = readBaseStats()
        val species = baseStats.keys.first()
        val stats = baseStats[species]!!
        val level = stardustLevelStart(stardust)
        val cpm = cpm(level)
        val cp = calculateCp(stats.atk, stats.def, stats.sta, ivAtk, ivDef, ivSta, level)
        val hp = floor((stats.sta + ivSta) * cpm).toInt()
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

    @Suppress("UNCHECKED_CAST")
    private fun readBaseStats(): Map<String, RarityCalculator.BaseStats> =
        readPrivateField("baseStats") as Map<String, RarityCalculator.BaseStats>

    @Suppress("UNCHECKED_CAST")
    private fun readStardustMap(): Map<Int, ClosedFloatingPointRange<Double>> =
        readPrivateField("stardustToLevel") as Map<Int, ClosedFloatingPointRange<Double>>

    @Suppress("UNCHECKED_CAST")
    private fun readCpmMap(): Map<Double, Double> =
        readPrivateField("cpmMap") as Map<Double, Double>

    private fun firstStardustKey(): Int = readStardustMap().keys.first()

    private fun anotherStardustKey(excluding: Int): Int =
        readStardustMap().keys.first { it != excluding }

    private fun stardustLevelStart(stardust: Int): Double =
        readStardustMap()[stardust]!!.start

    private fun cpm(level: Double): Double =
        readCpmMap()[level]!!

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

    private fun readPrivateField(name: String): Any {
        val field = RarityCalculator::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(calculator)
    }
}
