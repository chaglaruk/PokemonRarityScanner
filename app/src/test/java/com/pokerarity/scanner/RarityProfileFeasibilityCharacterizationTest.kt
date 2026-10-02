package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.RecognitionProfiles
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

/**
 * Pins the joint CP/maximum-HP feasibility contract of evaluateSpeciesProfile: which
 * observations are decidable, which are impossible, and how the arc distance is chosen.
 * Fixtures are derived from the packaged production facts, not from re-implemented math.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class RarityProfileFeasibilityCharacterizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = RarityCalculator(context).also { calculator ->
        // This project does not package Android assets in JVM tests. Load the real
        // production facts explicitly, as the existing refiner regression suite does.
        val assetDir = listOf(File("src/main/assets/data"), File("app/src/main/assets/data"))
            .first { it.isDirectory }
        val stats = Gson().fromJson<Map<String, RarityCalculator.BaseStats>>(
            File(assetDir, "pokemon_base_stats.json").readText(),
            object : TypeToken<Map<String, RarityCalculator.BaseStats>>() {}.type
        )
        require(stats.containsKey(SPECIES))
        RarityCalculator::class.java.getDeclaredField("baseStats\$delegate")
            .apply { isAccessible = true }.set(calculator, lazyOf(stats))
    }

    private val profiles = listOf(
        File("src/main/assets/data/recognition_profiles.json"),
        File("app/src/main/assets/data/recognition_profiles.json")
    ).first { it.isFile }.reader().use(RecognitionProfiles::read)
    private val stats = Gson().fromJson<Map<String, RarityCalculator.BaseStats>>(
        listOf(File("src/main/assets/data/pokemon_base_stats.json"),
            File("app/src/main/assets/data/pokemon_base_stats.json")).first { it.isFile }.readText(),
        object : TypeToken<Map<String, RarityCalculator.BaseStats>>() {}.type
    ).getValue(SPECIES)
    private val anchorLevel = 20.0
    private val anchorCpm = profiles.cpMultipliers.getValue(anchorLevel)
    private val anchorMaxHp = max(10, floor((stats.sta + 15) * anchorCpm).toInt())
    private val anchorCp = (10..4000).first { candidateCp ->
        anchorLevel in matchingLevels(candidateCp)
    }

    @Test
    fun jointCpAndMaximumHpIsFeasibleAtTheAnchorLevel() {
        val feasibility = calculator.evaluateSpeciesProfile(pokemon(cp = anchorCp, maxHp = anchorMaxHp), SPECIES)

        require(anchorLevel in matchingLevels(anchorCp)) { "fixture derivation broke" }
        assertTrue(feasibility!!.hpPossible)
        assertTrue(feasibility.jointCpHpPossible)
    }

    @Test
    fun feasibleMaximumHpAloneStaysHpPossibleButNeverJoint() {
        val infeasibleCp = ((anchorCp + 1)..4000).first { matchingLevels(it).isEmpty() }

        val feasibility = calculator.evaluateSpeciesProfile(
            pokemon(cp = infeasibleCp, maxHp = anchorMaxHp), SPECIES)

        assertTrue(feasibility!!.hpPossible)
        assertFalse(feasibility.jointCpHpPossible)
    }

    @Test
    fun missingCpOrMissingMaximumHpIsUndecidable() {
        assertNull(calculator.evaluateSpeciesProfile(pokemon(cp = null, maxHp = anchorMaxHp), SPECIES))
        assertNull(calculator.evaluateSpeciesProfile(pokemon(cp = anchorCp, maxHp = null), SPECIES))
    }

    @Test
    fun unknownSpeciesHasNoFeasibility() {
        assertNull(calculator.evaluateSpeciesProfile(pokemon(cp = anchorCp, maxHp = anchorMaxHp), "NotASpecies"))
    }

    @Test
    fun arcDistancePicksTheNearestJointLevelAndStaysNullWithoutAnArc() {
        val jointLevels = matchingLevels(anchorCp)
        val pokemon = pokemon(cp = anchorCp, maxHp = anchorMaxHp)
        val nearest = jointLevels.min()

        val aligned = calculator.evaluateSpeciesProfile(
            pokemon.copy(arcLevel = ((nearest - 1.0) / 49.0).toFloat()), SPECIES)
        // arcLevel is a Float, so the level round-trip carries ~1e-6 slack.
        assertEquals(0.0, aligned!!.minJointArcDiff!!, 1e-4)

        val offArc = calculator.evaluateSpeciesProfile(
            pokemon.copy(arcLevel = ((nearest + 2.5 - 1.0) / 49.0).toFloat()), SPECIES)
        val expected = jointLevels.minOf { abs(it - (nearest + 2.5)) }
        assertEquals(expected, offArc!!.minJointArcDiff!!, 1e-4)

        val noArc = calculator.evaluateSpeciesProfile(pokemon, SPECIES)
        assertTrue(noArc!!.jointCpHpPossible)
        assertNull(noArc.minJointArcDiff)
    }

    private fun matchingLevels(cp: Int) = calculator.matchingProfileLevels(
        pokemon(cp = cp, maxHp = anchorMaxHp), stats, profiles.cpMultipliers)

    private fun pokemon(cp: Int?, maxHp: Int?) = PokemonData(
        cp = cp,
        hp = maxHp,
        maxHp = maxHp,
        name = "nickname",
        realName = SPECIES,
        candyName = null,
        megaEnergy = null,
        weight = null,
        height = null,
        stardust = null,
        caughtDate = null
    )

    private companion object {
        const val SPECIES = "Eevee"
    }
}
