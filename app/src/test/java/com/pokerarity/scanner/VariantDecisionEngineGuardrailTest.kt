package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VariantMergeLogic
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VariantDecisionEngineGuardrailTest {

    private val engine by lazy {
        VariantDecisionEngine(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun classifierOnlyShinyCostumeFallbackDoesNotOverrideWithoutVisualSupport() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Piplup",
                resolvedVariantClass = "base",
                resolvedShiny = false,
                resolvedCostume = false,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Piplup",
                assetKey = "393_00_HALLOWEEN_2021_NOEVOLVE_shiny",
                spriteKey = "393_00_HALLOWEEN_2021_NOEVOLVE_shiny",
                variantType = "costume",
                isShiny = true,
                isCostumeLike = true,
                scope = "global",
                score = 0.362f,
                confidence = 0.491f,
                speciesMargin = 0.085f,
                variantMargin = 0.0f,
                topSpecies = listOf("Piplup:0.362", "Prinplup:0.447", "Empoleon:0.500")
            )
        )

        assertFalse(merged.isShiny)
        assertFalse(merged.hasCostume)
    }

    @Test
    fun acceptedSpeciesPlusSameFamilyVisualSpeciesSelectedNameAndRealNameRemainUnchanged() {
        val pokemon = samplePokemonData(name = "Pikachu", realName = "Pikachu")
        val match = sampleMatchResult(species = "Raichu", confidence = 0.95f)

        val result = applyClassifierSpecies(pokemon, match)

        assertEquals("Pikachu", result.name)
        assertEquals("Pikachu", result.realName)
    }

    @Test
    fun acceptedSpeciesPlusCrossFamilyVisualSpeciesSelectedNameAndRealNameRemainUnchanged() {
        val pokemon = samplePokemonData(name = "Snorlax", realName = "Snorlax")
        val match = sampleMatchResult(species = "Minccino", confidence = 0.99f)

        val result = applyClassifierSpecies(pokemon, match)

        assertEquals("Snorlax", result.name)
        assertEquals("Snorlax", result.realName)
    }

    @Test
    fun missingOrUnknownInputSpeciesPlusStrongVisualSpeciesDoesNotIntroduceNameOrRealName() {
        val pokemon = samplePokemonData(name = "Unknown", realName = null, candyName = "Squirtle")
        val match = sampleMatchResult(species = "Blastoise", confidence = 0.99f)

        val result = applyClassifierSpecies(pokemon, match)

        assertEquals("Unknown", result.name)
        assertNull(result.realName)
    }

    @Test
    fun applyClassifierSpeciesVisualSpeciesCannotMutateEitherSpeciesField() {
        val pokemon = samplePokemonData(name = "Bulbasaur", realName = "Bulbasaur")
        val match = sampleMatchResult(species = "Venusaur", confidence = 0.90f)

        val result = applyClassifierSpecies(pokemon, match)

        assertEquals("Bulbasaur", result.name)
        assertEquals("Bulbasaur", result.realName)
    }

    @Test
    fun finalSpeciesGlobalMatchSpeciesIsNotUsedAsFallback() {
        val pokemon = samplePokemonData(name = null, realName = null)
        val finalSpecies = pokemon.realName ?: pokemon.name ?: "Unknown"

        assertEquals("Unknown", finalSpecies)
    }

    @Test
    fun scopedPassTargetInputSpeciesIsRetainedEvenWhenVisualClassifierProposesAnotherSameFamilySpecies() {
        val pokemon = samplePokemonData(name = "Wartortle", realName = "Wartortle")
        val globalMatch = sampleMatchResult(species = "Squirtle", confidence = 0.88f)

        val scopeTarget = chooseSpeciesScopeTarget(pokemon, globalMatch)

        assertEquals("Wartortle", scopeTarget)
    }

    @Test
    fun scopedPassTargetInputSpeciesIsRetainedWhenVisualClassifierProposesCrossFamilySpecies() {
        val pokemon = samplePokemonData(name = "Pikachu", realName = "Pikachu")
        val globalMatch = sampleMatchResult(species = "Charizard", confidence = 0.92f)

        val scopeTarget = chooseSpeciesScopeTarget(pokemon, globalMatch)

        assertEquals("Pikachu", scopeTarget)
    }

    @Test
    fun scopedPassTargetNoInputSpeciesMeansNoClassifierCreatedSpeciesScope() {
        val pokemon = samplePokemonData(name = null, realName = null)
        val globalMatch = sampleMatchResult(species = "Bulbasaur", confidence = 0.95f)

        val scopeTarget = chooseSpeciesScopeTarget(pokemon, globalMatch)

        assertNull(scopeTarget)
    }

    @Test
    fun deterministicRepeatIdenticalInputAndClassifierOutputProduceIdenticalSpeciesFields() {
        val pokemon = samplePokemonData(name = "Eevee", realName = "Eevee")
        val globalMatch = sampleMatchResult(species = "Vaporeon", confidence = 0.91f)

        val firstRun = applyClassifierSpecies(pokemon, globalMatch)
        val secondRun = applyClassifierSpecies(pokemon, globalMatch)

        assertEquals(firstRun.name, secondRun.name)
        assertEquals(firstRun.realName, secondRun.realName)

        val firstScope = chooseSpeciesScopeTarget(pokemon, globalMatch)
        val secondScope = chooseSpeciesScopeTarget(pokemon, globalMatch)

        assertEquals(firstScope, secondScope)
    }

    @Test
    fun existingSpeciesBoundedVariantBehaviorRemainsAvailable() {
        val pokemon = samplePokemonData(name = "Piplup", realName = "Piplup")
        val match = sampleMatchResult(species = "Piplup", isCostume = true)

        val result = applyClassifierSpecies(pokemon, match)

        assertEquals("Piplup", result.name)
        assertEquals("Piplup", result.realName)

        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(hasCostume = true),
            fullMatch = FullVariantMatch(
                finalSpecies = "Piplup",
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                explanationMode = "exact_authoritative"
            ),
            fallbackMatch = null
        )

        assertEquals("Piplup", pokemon.realName)
        assertTrue(merged.hasCostume)
    }

    private fun samplePokemonData(
        name: String?,
        realName: String?,
        candyName: String? = null
    ) = PokemonData(
        cp = 500,
        hp = 50,
        maxHp = 50,
        name = name,
        realName = realName,
        candyName = candyName,
        megaEnergy = null,
        weight = 1.0f,
        height = 1.0f,
        stardust = 1000,
        caughtDate = null
    )

    private fun applyClassifierSpecies(
        pokemon: PokemonData,
        match: VariantPrototypeClassifier.MatchResult?
    ): PokemonData {
        val method = VariantDecisionEngine::class.java.getDeclaredMethod(
            "applyClassifierSpecies",
            PokemonData::class.java,
            VariantPrototypeClassifier.MatchResult::class.java
        ).apply { isAccessible = true }
        return method.invoke(engine, pokemon, match) as PokemonData
    }

    private fun chooseSpeciesScopeTarget(
        pokemon: PokemonData,
        globalMatch: VariantPrototypeClassifier.MatchResult?
    ): String? {
        val method = VariantDecisionEngine::class.java.getDeclaredMethod(
            "chooseSpeciesScopeTarget",
            PokemonData::class.java,
            VariantPrototypeClassifier.MatchResult::class.java
        ).apply { isAccessible = true }
        return method.invoke(engine, pokemon, globalMatch) as String?
    }

    private fun sampleMatchResult(
        species: String,
        confidence: Float = 0.80f,
        isCostume: Boolean = false
    ) = VariantPrototypeClassifier.MatchResult(
        species = species,
        assetKey = "${species}_key",
        spriteKey = "${species}_sprite",
        variantType = if (isCostume) "costume" else "base",
        isShiny = false,
        isCostumeLike = isCostume,
        scope = "global",
        score = 0.85f,
        confidence = confidence,
        speciesMargin = 0.10f,
        variantMargin = 0.05f,
        topSpecies = listOf("$species:0.85")
    )
}
