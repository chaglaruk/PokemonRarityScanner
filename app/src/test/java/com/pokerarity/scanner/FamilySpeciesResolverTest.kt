package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.FamilySpeciesResolver
import com.pokerarity.scanner.util.ocr.RecognitionProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class FamilySpeciesResolverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val profiles = listOf(File("src/main/assets/data/recognition_profiles.json"), File("app/src/main/assets/data/recognition_profiles.json"))
        .first { it.isFile }.reader().use(RecognitionProfiles::read)
    private val resolver = FamilySpeciesResolver(profiles, RarityCalculator(context))
    private fun pokemon(cp: Int?, hp: Int, name: String = "nickname") = PokemonData(cp = cp, hp = hp, maxHp = hp,
        name = name, realName = name, candyName = null, megaEnergy = null, weight = null, height = null, stardust = null, caughtDate = null)
    private fun observation(candy: String, cost: Int? = null) = FamilySpeciesResolver.Observation(candy, true, cost, cost != null)

    @Test fun nicknameDoesNotResolveOverlappingSpeciesProfiles() {
        assertNull(resolver.resolve(pokemon(424, 80, "Umbreon"), observation("Eevee")).species)
        assertNull(resolver.resolve(pokemon(1382, 135, "Slowking"), observation("Slowpoke")).species)
    }
    @Test fun uniqueFamilyEvidenceRecoversNicknames() {
        assertEquals("Skwovet", resolver.resolve(pokemon(734, 133, "Pikachu"), observation("Skwovet")).species)
        assertEquals("Chikorita", resolver.resolve(pokemon(340, 70), observation("Chikorita")).species)
    }
    @Test fun anchoredCostDisambiguatesWhileInventoryDoesNot() {
        assertNull(resolver.resolve(pokemon(236, 51), observation("Pikipek")).species)
        assertEquals("Pikipek", resolver.resolve(pokemon(236, 51), observation("Pikipek", 1000)).species)
        assertNull(resolver.resolve(pokemon(236, 51), observation("Pikipek", 1000).copy(anchoredPowerUpCost = false)).species)
    }
    @Test fun shadowDisplayRoundingKeepsCanonicalFamilyProfiles() {
        assertEquals("Aipom", resolver.resolve(pokemon(437, 74), observation("Aipom", 1921)).species)
        assertEquals("Registeel", resolver.resolve(pokemon(540, 76), observation("Registeel", 961)).species)
        assertEquals("Sawk", resolver.resolve(pokemon(1976, 128), observation("Sawk", 4800)).species)
        assertEquals("Aerodactyl", resolver.resolve(pokemon(1461, 117), observation("Aerodactyl", 2640)).species)
    }
    @Test fun shadowDisplayRoundingIsNotGeneralOneUnitTolerance() {
        assertNull(resolver.resolve(pokemon(540, 76), observation("Registeel", 962)).species)
        assertNull(resolver.resolve(pokemon(236, 51), observation("Pikipek", 1001)).species)
    }
    @Test fun formStatsRemainSeparateAndCoverRegionalSpecies() {
        assertEquals("Farfetch'd", resolver.resolve(pokemon(468, 69), observation("Farfetch'd")).species)
        assertEquals(2, profiles.forSpecies("Farfetch'd").size)
    }
    @Test fun hiddenCpRequiresAnchoredCost() {
        assertNull(resolver.resolve(pokemon(null, 84), observation("Torchic")).species)
        assertEquals("Torchic", resolver.resolve(pokemon(null, 84), observation("Torchic", 2500)).species)
    }
    @Test fun untrustedCandyAndImpossibleProfilesAreUncertain() {
        assertNull(resolver.resolve(pokemon(734, 133), observation("Skwovet").copy(exactCandyLabel = false)).species)
        assertNull(resolver.resolve(pokemon(9000, 999), observation("Skwovet")).species)
    }
    @Test fun bestBuddyAndPreciseHalfLevelsNeverEliminateTheTrueSpecies() {
        for ((cp, hp) in listOf(996 to 145, 1033 to 149)) {
            val result = resolver.resolve(pokemon(cp, hp), observation("Skwovet"))
            org.junit.Assert.assertTrue(result.candidates.contains("Skwovet"))
            org.junit.Assert.assertNotEquals("Greedent", result.species)
        }
        val boosted = resolver.resolve(pokemon(108, 35), observation("Pikipek", 400))
        org.junit.Assert.assertTrue(boosted.candidates.contains("Pikipek"))
        org.junit.Assert.assertNotEquals("Trumbeak", boosted.species)
    }
    @Test fun scrolledTypeEvidenceMustIndependentlySeparateFamilyMembers() {
        val hiddenNumbers = pokemon(null, 84).copy(hp = null, maxHp = null)
        assertEquals("Torchic", resolver.resolve(hiddenNumbers, observation("Torchic").copy(types = setOf("fire"))).species)
        assertNull(resolver.resolve(hiddenNumbers, observation("Farfetch'd").copy(types = setOf("fighting"))).species)
        assertEquals("Torchic", resolver.resolve(pokemon(null, 84), observation("Torchic").copy(types = setOf("fire"))).species)
    }
}
