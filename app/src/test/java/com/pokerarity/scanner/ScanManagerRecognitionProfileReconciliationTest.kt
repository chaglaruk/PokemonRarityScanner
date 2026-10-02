package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

/**
 * profileStatus must reconcile against the SAME RecognitionProfiles authority the
 * family resolver used (per-form rows + float32 multipliers including best-buddy
 * levels 50.5/51.0) whenever the anchored observation path produced the evidence.
 * The legacy single baseStats row and legacy multiplier map stay authoritative only
 * for paths without a recognition observation. Reconciliation never creates a new
 * hard-positive authority; it only maps feasibility to a profile status.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScanManagerRecognitionProfileReconciliationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = RarityCalculator(context).also { calculator ->
        // This project does not package Android assets in JVM tests. Load the real
        // production facts explicitly, as the feasibility suite does.
        val assetDir = listOf(File("src/main/assets/data"), File("app/src/main/assets/data"))
            .first { it.isDirectory }
        val stats = com.google.gson.Gson().fromJson<Map<String, RarityCalculator.BaseStats>>(
            File(assetDir, "pokemon_base_stats.json").readText(),
            object : com.google.gson.reflect.TypeToken<Map<String, RarityCalculator.BaseStats>>() {}.type
        )
        require(stats.containsKey("Aggron") && stats.containsKey("Farfetch'd"))
        RarityCalculator::class.java.getDeclaredField("baseStats\$delegate")
            .apply { isAccessible = true }.set(calculator, lazyOf(stats))
        val profiles = File(assetDir, "recognition_profiles.json").reader()
            .use(com.pokerarity.scanner.util.ocr.RecognitionProfiles::read)
        RarityCalculator::class.java.getDeclaredField("recognitionProfiles\$delegate")
            .apply { isAccessible = true }.set(calculator, lazyOf(profiles))
    }

    private fun observed(species: String, cp: Int?, maxHp: Int?) = PokemonData(
        cp = cp, hp = maxHp, maxHp = maxHp, name = species, realName = species,
        candyName = species, megaEnergy = null, weight = null, height = null,
        stardust = null, caughtDate = null,
        recognitionObservation = RecognitionObservation(
            candySpecies = species, powerUpStardust = null, types = null,
            detailScreen = true))

    private fun legacy(species: String, cp: Int?, maxHp: Int?) = PokemonData(
        cp = cp, hp = maxHp, maxHp = maxHp, name = species, realName = species,
        candyName = species, megaEnergy = null, weight = null, height = null,
        stardust = null, caughtDate = null)

    @Test
    fun observationPathFeasibilityUsesTheResolverProfileRows() {
        // Aggron CP 110 / maxHP 24 is witnessed only by the Mega recognition row;
        // the legacy single baseStats row contradicts the same observation.
        assertEquals(
            SpeciesProfileStatus.COMPATIBLE,
            ScanManager.profileStatus(observed("Aggron", 110, 24), "Aggron", calculator)
        )
    }

    @Test
    fun bestBuddyLevelsBeyondTheLegacyMultiplierMapStayFeasible() {
        // maxHP 158 for Aggron exists only at level 51.0; the legacy multiplier map
        // stops at 50.0 and reports the observation IMPOSSIBLE.
        assertEquals(
            SpeciesProfileStatus.COMPATIBLE,
            ScanManager.profileStatus(observed("Aggron", 3101, 158), "Aggron", calculator)
        )
    }

    @Test
    fun formRowsAreTestedIndividuallyBeforeReconciliation() {
        // CP 20 / maxHP 14 is witnessed only by the Galarian Farfetch'd row; the
        // normal-form row that backs the legacy table cannot produce it.
        assertEquals(
            SpeciesProfileStatus.COMPATIBLE,
            ScanManager.profileStatus(observed("Farfetch'd", 20, 14), "Farfetch'd", calculator)
        )
    }

    @Test
    fun noRowWitnessStillFailsClosedOnTheObservationPath() {
        assertEquals(
            SpeciesProfileStatus.CONTRADICTORY,
            ScanManager.profileStatus(observed("Farfetch'd", 424, 20), "Farfetch'd", calculator)
        )
    }

    @Test
    fun impossibleMaximumHpStaysImpossibleOnTheObservationPath() {
        assertEquals(
            SpeciesProfileStatus.IMPOSSIBLE,
            ScanManager.profileStatus(observed("Aggron", 110, 999), "Aggron", calculator)
        )
    }

    @Test
    fun legacyPathWithoutObservationKeepsTheLegacyEvaluator() {
        assertEquals(
            SpeciesProfileStatus.CONTRADICTORY,
            ScanManager.profileStatus(legacy("Aggron", 110, 24), "Aggron", calculator)
        )
        assertEquals(
            SpeciesProfileStatus.IMPOSSIBLE,
            ScanManager.profileStatus(legacy("Aggron", 3101, 158), "Aggron", calculator)
        )
    }
}
