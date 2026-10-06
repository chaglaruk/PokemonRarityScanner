package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.FamilySpeciesResolver
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File

/**
 * Phase 1A characterization of the CURRENT (branch-dependent) resolver, written
 * BEFORE the common evaluator exists. Cases assert the EXPECTED post-fix semantics
 * from the authoritative plan (blob 41fc0780, sections 5.2/5.4). The pre-fix run
 * documents which expected semantics the incumbent fails (A, C, D, J partially),
 * and the incumbent replica records the exact incumbent result for the case matrix.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class FamilySpeciesResolverCharacterizationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val profiles = listOf(
        File("src/main/assets/data/recognition_profiles.json"),
        File("app/src/main/assets/data/recognition_profiles.json")
    ).first { it.isFile }.reader().use(RecognitionSnapshot::load)
    private val resolver = FamilySpeciesResolver(profiles, RarityCalculator(context))

    private fun pokemon(cp: Int?, hp: Int?, maxHp: Int? = hp, name: String = "nickname") = PokemonData(
        cp = cp, hp = hp, maxHp = maxHp, name = name, realName = name, candyName = null,
        megaEnergy = null, weight = null, height = null, stardust = null, caughtDate = null)

    private fun obs(
        candy: String,
        powerUp: Int? = null,
        types: Set<String>? = null,
        evolve: Int? = null,
        anchored: Boolean = true
    ) = FamilySpeciesResolver.Observation(
        candySpecies = candy, exactCandyLabel = true, powerUpStardust = powerUp,
        anchoredPowerUpCost = anchored && powerUp != null, types = types, evolutionCandyCost = evolve)

    // Test-only incumbent replica of the pre-1A branch-dependent resolver.
    private val incumbentCalculator = RarityCalculator(context)

    private fun incumbent(pokemon: PokemonData, observed: FamilySpeciesResolver.Observation): Pair<String?, String> {
        val candy = observed.candySpecies
        val guard = when {
            !observed.exactCandyLabel || candy == null -> "candy_label_missing"
            profiles.forCandy(candy).isEmpty() -> "family_metadata_missing"
            else -> null
        }
        if (guard != null) return null to guard
        val family = profiles.forCandy(candy!!)
        val result = if (pokemon.maxHp == null) {
            incumbentMaxHpMissing(pokemon, observed, family)
        } else {
            incumbentMaxHpPresent(pokemon, observed, family)
        }
        return result
    }

    private fun incumbentMaxHpMissing(
        pokemon: PokemonData,
        observed: FamilySpeciesResolver.Observation,
        family: List<RecognitionSnapshot.Profile>
    ): Pair<String?, String> {
        val typedProfiles = family.filter { !observed.types.isNullOrEmpty() && it.types == observed.types }
        val typed = typedProfiles.map { it.species }.toSet()
        val evolutionCost = observed.evolutionCandyCost
        return when {
            pokemon.cp == null && typed.size == 1 -> typed.single() to "independent_family_profile"
            pokemon.cp == null && evolutionCost != null && evolutionCost in 0..1000 -> {
                val possible = typedProfiles.filter {
                    it.evolutionCandyCosts == null || evolutionCost in it.evolutionCandyCosts
                }
                val candidates = possible.map { it.species }.toSet()
                if (candidates.size == 1 && possible.none { it.evolutionCandyCosts == null }) {
                    candidates.single() to "independent_family_profile"
                } else {
                    null to "evolution_family_ambiguous_or_unsupported"
                }
            }
            else -> null to "maximum_hp_missing"
        }
    }

    private fun incumbentMaxHpPresent(
        pokemon: PokemonData,
        observed: FamilySpeciesResolver.Observation,
        family: List<RecognitionSnapshot.Profile>
    ): Pair<String?, String> {
        val cost = observed.powerUpStardust.takeIf { observed.anchoredPowerUpCost }
        if (pokemon.cp == null && cost == null && observed.types.isNullOrEmpty()) {
            return null to "cp_or_power_up_cost_missing"
        }
        val candidates = family.filter { profile ->
            (observed.types.isNullOrEmpty() || profile.types == observed.types) &&
                incumbentCalculator.matchingProfileLevels(pokemon, profile.stats, profiles.cpMultipliers)
                    .any { level -> cost == null || costMatchesIncumbent(cost, level) }
        }.map { it.species }.toSet()
        return when (candidates.size) {
            0 -> null to "family_profile_contradiction"
            1 -> candidates.single() to "independent_family_profile"
            else -> null to "family_profile_ambiguous"
        }
    }

    private fun costMatchesIncumbent(observed: Int, level: Double): Boolean {
        val costs = listOf(200, 400, 600, 800, 1000, 1300, 1600, 1900, 2200, 2500,
            3000, 3500, 4000, 4500, 5000, 6000, 7000, 8000, 9000, 10000, 11000, 12000, 13000, 14000, 15000)
        val modifiers = listOf(1.0, .5, .9, .45, 1.2)
        fun canonicalDisplayedCosts(base: Int, modifier: Double): Set<Int> {
            val exact = kotlin.math.ceil(base * modifier).toInt()
            if (modifier != 1.2) return setOf(exact)
            val float32 = kotlin.math.ceil((base.toFloat() * 1.2f).toDouble()).toInt()
            return setOf(exact, float32)
        }
        return listOf(level, level - 1).filter { it >= 1 && it < 50 }.any { baseLevel ->
            val base = costs[((baseLevel - 1) / 2).toInt()]
            modifiers.any { modifier -> observed in canonicalDisplayedCosts(base, modifier) }
        }
    }

    // A. WEEDLE POSITIVE: CP 150 + maxHP 61 + ordinary EVOLVE 12 must resolve Weedle.
    @Test
    fun caseA_weedlePositive() {
        val result = resolver.resolve(pokemon(150, 61), obs("Weedle", evolve = 12))
        assertEquals("Weedle", result.species)
        assertEquals(setOf("Weedle"), result.candidates)
    }

    // B. SAME NUMERICS, EVOLVE ABSENT: ambiguity preserved.
    @Test
    fun caseB_evolveAbsent_keepsAmbiguity() {
        val result = resolver.resolve(pokemon(150, 61), obs("Weedle"))
        assertNull(result.species)
        assertTrue(result.candidates.contains("Weedle"))
        assertTrue(result.candidates.contains("Kakuna"))
    }

    // C. IMPOSSIBLE EVOLVE 999: never a confident accepted species.
    @Test
    fun caseC_impossibleEvolveCost_neverAccepts() {
        assertNull(resolver.resolve(pokemon(150, 61), obs("Weedle", evolve = 999)).species)
    }

    // D. TORCHIC EARLY-RETURN CONTRADICTION: the incumbent accepts Torchic here;
    // the common evaluator must not accept a candidate that the observed EVOLVE
    // cost contradicts, regardless of which branch processed the numbers.
    @Test
    fun caseD_typeUniqueEarlyReturn_contradictedByEvolveCost() {
        val hidden = pokemon(null, null, maxHp = null)
        val evidence = obs("Torchic", types = setOf("fire"), evolve = 50)
        assertEquals(
            "incumbent documents the branch-dependent acceptance",
            "Torchic", incumbent(hidden, evidence).first)
        assertNull("contradicted candidate must not be accepted", resolver.resolve(hidden, evidence).species)
    }

    @Test
    fun caseD_positiveControl_matchingEvolveCostStillAccepts() {
        assertEquals(
            "Torchic",
            resolver.resolve(pokemon(null, null, maxHp = null),
                obs("Torchic", types = setOf("fire"), evolve = 25)).species)
    }

    // E. FARFETCH'D POSITIVE CONTROL.
    @Test
    fun caseE_farfetchdPositiveControl() {
        assertEquals("Farfetch'd", resolver.resolve(pokemon(468, 69), obs("Farfetch'd")).species)
    }

    // F. CASCOON / SILCOON TRUE AMBIGUITY, even with a matching EVOLVE cost.
    @Test
    fun caseF_cascoonSilcoonRemainAmbiguous() {
        val result = resolver.resolve(pokemon(null, 90), obs("Wurmple", evolve = 50))
        assertNull(result.species)
        assertTrue(result.candidates.contains("Cascoon"))
        assertTrue(result.candidates.contains("Silcoon"))
    }

    // G. WRONG-CANDY CROSS-FAMILY LIMITATION: impossible numbers for the candy
    // family end in contradiction, never a cross-family rescue.
    @Test
    fun caseG_wrongCandy_neverRescuedAcrossFamily() {
        assertNull(resolver.resolve(pokemon(20, 95), obs("Chimchar")).species)
    }

    // H. TYPE-ONLY UNIQUE CASE without contradicting evidence still resolves.
    @Test
    fun caseH_typeOnlyUnique_stillResolves() {
        assertEquals(
            "Torchic",
            resolver.resolve(pokemon(null, null, maxHp = null), obs("Torchic", types = setOf("fire"))).species)
    }

    // I. EVOLVE UNKNOWN METADATA: unknown metadata neither matches nor gains
    // authority; a lone surviving unknown-metadata candidate stays unresolved.
    @Test
    fun caseI_unknownMetadata_neverBecomesPositiveSupport() {
        val template = profiles.forSpecies("Torchic").first()
        fun profile(species: String, costs: Set<Int>?) = template.copy(
            species = species, forms = setOf(species), candySpecies = "Synthetic",
            types = setOf("fire"), evolutionCandyCosts = costs)
        fun synthetic(vararg alternatives: RecognitionSnapshot.Profile) = FamilySpeciesResolver(
            RecognitionSnapshot.fromRows(alternatives.toList(), profiles.cpMultipliers), RarityCalculator(context))

        // Known candidate eliminated by the observed cost; only the unknown-metadata
        // candidate survives: must stay unresolved (incumbent also returns null here,
        // but for the wrong reason - empty typed candidates instead of explicit semantics).
        val onlyUnknownSurvives = synthetic(profile("Known", setOf(50)), profile("Mystery", null))
        assertNull(
            onlyUnknownSurvives.resolve(
                pokemon(null, null, maxHp = null),
                obs("Synthetic", types = setOf("fire"), evolve = 25)).species)

        // Two matched candidates remain ambiguous.
        assertNull(
            synthetic(profile("Alpha", setOf(25)), profile("Beta", setOf(25)))
                .resolve(pokemon(null, null, maxHp = null),
                    obs("Synthetic", types = setOf("fire"), evolve = 25)).species)
    }

    // J. POWER-UP + CP/maxHP + EVOLVE all constrain the SAME evaluation; a
    // power-up cost outside the feasible window contradicts even the
    // EVOLVE-matching candidate (no branch may override another constraint).
    @Test
    fun caseJ_powerUpAndNumericConstraints_inOneEvaluation() {
        assertEquals(
            "Weedle",
            resolver.resolve(pokemon(150, 61), obs("Weedle", powerUp = 1600, evolve = 12)).species)
        assertNull(
            resolver.resolve(pokemon(150, 61), obs("Weedle", powerUp = 9600, evolve = 12)).species)
    }

    // ------------------------------------------------------------------
    // Incumbent/challenger regression guards: exact incumbent successes and
    // ambiguity/rejection outcomes that must survive the rewrite unchanged.
    // ------------------------------------------------------------------
    @Test
    fun incumbentComparison_exactSuccessfulIncumbentCasesUnchanged() {
        val cases = listOf(
            pokemon(734, 133) to obs("Skwovet"),
            pokemon(340, 70) to obs("Chikorita"),
            pokemon(236, 51) to obs("Pikipek", powerUp = 1000),
            pokemon(437, 74) to obs("Aipom", powerUp = 1921),
            pokemon(468, 69) to obs("Farfetch'd"),
            pokemon(null, 84) to obs("Torchic", powerUp = 2500)
        )
        cases.forEach { (p, evidence) ->
            assertEquals(incumbent(p, evidence).first, resolver.resolve(p, evidence).species)
        }
    }

    @Test
    fun incumbentComparison_ambiguityAndRejectionUnchanged() {
        val cases = listOf(
            pokemon(424, 80, name = "Umbreon") to obs("Eevee"),
            pokemon(1382, 135, name = "Slowking") to obs("Slowpoke"),
            pokemon(236, 51) to obs("Pikipek"),
            pokemon(540, 76) to obs("Registeel", powerUp = 962),
            pokemon(734, 133) to obs("Skwovet").copy(exactCandyLabel = false),
            pokemon(9000, 999) to obs("Skwovet")
        )
        cases.forEach { (p, evidence) ->
            assertNull(incumbent(p, evidence).first)
            assertNull(resolver.resolve(p, evidence).species)
        }
    }
}
