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
    fun acceptedInputSpeciesIsTheScopedPassTarget() {
        val pokemon = samplePokemonData(name = "Wartortle", realName = "Wartortle")

        val scopeTarget = engine.chooseSpeciesScopeTarget(pokemon)

        assertEquals("Wartortle", scopeTarget)
    }

    @Test
    fun acceptedInputSpeciesNameIsPreferredForSpeciesScope() {
        val pokemon = samplePokemonData(
            name = "Pikachu",
            realName = null
        )

        val scopeTarget = engine.chooseSpeciesScopeTarget(pokemon)

        assertEquals("Pikachu", scopeTarget)
    }

    @Test
    fun acceptedInputRealNameIsPreferredForSpeciesScope() {
        val pokemon = samplePokemonData(
            name = null,
            realName = "Bulbasaur"
        )

        val scopeTarget = engine.chooseSpeciesScopeTarget(pokemon)

        assertEquals("Bulbasaur", scopeTarget)
    }

    @Test
    fun missingSpeciesDoesNotCreateSpeciesScope() {
        val pokemon = samplePokemonData(name = null, realName = null)

        val scopeTarget = engine.chooseSpeciesScopeTarget(pokemon)

        assertNull(scopeTarget)
    }

    @Test
    fun unknownSpeciesDoesNotBecomeSpeciesScopeTarget() {
        val pokemonUpper = samplePokemonData(name = "Unknown", realName = null)
        val pokemonLower = samplePokemonData(name = "unknown", realName = null)

        val scopeTargetUpper = engine.chooseSpeciesScopeTarget(pokemonUpper)
        val scopeTargetLower = engine.chooseSpeciesScopeTarget(pokemonLower)

        assertNull(scopeTargetUpper)
        assertNull(scopeTargetLower)
    }

    @Test
    fun finalSpeciesForUsesRealNameFirst() {
        val pokemon = samplePokemonData(name = "Pikachu", realName = "Raichu")

        val finalSpecies = engine.finalSpeciesFor(pokemon)

        assertEquals("Raichu", finalSpecies)
    }

    @Test
    fun finalSpeciesForUsesNameWhenRealNameIsAbsent() {
        val pokemon = samplePokemonData(name = "Charizard", realName = null)

        val finalSpecies = engine.finalSpeciesFor(pokemon)

        assertEquals("Charizard", finalSpecies)
    }

    @Test
    fun finalSpeciesForReturnsUnknownWhenBothNameAndRealNameAreAbsent() {
        val pokemon = samplePokemonData(name = null, realName = null)

        val finalSpecies = engine.finalSpeciesFor(pokemon)

        assertEquals("Unknown", finalSpecies)
    }

    @Test
    fun deterministicRepeatIdenticalInputProducesIdenticalTargetAndFinalSpecies() {
        val pokemon = samplePokemonData(name = "Eevee", realName = "Eevee")

        val firstScope = engine.chooseSpeciesScopeTarget(pokemon)
        val secondScope = engine.chooseSpeciesScopeTarget(pokemon)
        assertEquals(firstScope, secondScope)

        val firstFinal = engine.finalSpeciesFor(pokemon)
        val secondFinal = engine.finalSpeciesFor(pokemon)
        assertEquals(firstFinal, secondFinal)
    }

    @Test
    fun existingSpeciesBoundedVariantBehaviorRemainsAvailable() {
        val pokemon = samplePokemonData(name = "Piplup", realName = "Piplup")

        val finalSpecies = engine.finalSpeciesFor(pokemon)

        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(hasCostume = true),
            fullMatch = FullVariantMatch(
                finalSpecies = finalSpecies,
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                explanationMode = "exact_authoritative"
            ),
            fallbackMatch = null
        )

        assertEquals("Piplup", finalSpecies)
        assertEquals("Piplup", pokemon.realName)
        assertTrue(merged.hasCostume)
    }

    private fun samplePokemonData(
        name: String?,
        realName: String?,
        candyName: String? = null,
        rawOcrText: String = ""
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
        caughtDate = null,
        rawOcrText = rawOcrText
    )
}
