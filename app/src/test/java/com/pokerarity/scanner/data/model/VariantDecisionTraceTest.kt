package com.pokerarity.scanner.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [VariantDecisionTrace] data class.
 * Verifies field defaults, copy semantics, and privacy properties.
 */
class VariantDecisionTraceTest {

    @Test
    fun defaultTrace_hasAllFieldsNull() {
        val trace = VariantDecisionTrace()

        assertNull(trace.classifierScope)
        assertNull(trace.classifierSpecies)
        assertNull(trace.classifierSpriteKey)
        assertNull(trace.classifierVariantType)
        assertNull(trace.classifierShiny)
        assertNull(trace.classifierCostume)
        assertNull(trace.classifierConfidence)
        assertNull(trace.classifierScore)
        assertNull(trace.classifierVariantMargin)
        assertNull(trace.classifierBestBaseScore)
        assertNull(trace.classifierBestNonBaseScore)
        assertNull(trace.classifierBestNonBaseType)
        assertNull(trace.classifierRescueKind)

        assertNull(trace.fullVariantSpecies)
        assertNull(trace.fullVariantSpriteKey)
        assertNull(trace.fullVariantClass)
        assertNull(trace.fullVariantShiny)
        assertNull(trace.fullVariantCostume)
        assertNull(trace.fullVariantForm)
        assertNull(trace.fullVariantEvent)
        assertNull(trace.fullVariantExplanationMode)
        assertNull(trace.fullVariantSpeciesConfidence)
        assertNull(trace.fullVariantVariantConfidence)
        assertNull(trace.fullVariantShinyConfidence)
        assertNull(trace.fullVariantEventConfidence)
        assertNull(trace.fullVariantDebug)
    }

    @Test
    fun copy_preservesClassifierFieldsIndependently() {
        val trace = VariantDecisionTrace(
            classifierScope = "species",
            classifierSpecies = "Pikachu",
            classifierConfidence = 0.95f,
            classifierScore = 0.88f
        )

        val updated = trace.copy(classifierScope = "global")

        assertEquals("global", updated.classifierScope)
        assertEquals("Pikachu", updated.classifierSpecies)
        assertEquals(0.95f, updated.classifierConfidence)
        assertEquals(0.88f, updated.classifierScore)
    }

    @Test
    fun populatedTrace_carriesExpectedValues() {
        val trace = VariantDecisionTrace(
            classifierScope = "species",
            classifierSpecies = "Charizard",
            classifierSpriteKey = "charizard_mega_x",
            classifierVariantType = "mega",
            classifierShiny = true,
            classifierCostume = false,
            classifierConfidence = 0.92f,
            classifierScore = 0.87f,
            classifierVariantMargin = 0.15f,
            classifierBestBaseScore = 0.72f,
            classifierBestNonBaseScore = 0.87f,
            classifierBestNonBaseType = "mega",
            classifierRescueKind = null,
            fullVariantSpecies = "Charizard",
            fullVariantSpriteKey = "charizard_mega_x",
            fullVariantClass = "MEGA",
            fullVariantShiny = true,
            fullVariantCostume = false,
            fullVariantForm = false,
            fullVariantEvent = null,
            fullVariantExplanationMode = "standard",
            fullVariantSpeciesConfidence = 0.95f,
            fullVariantVariantConfidence = 0.88f,
            fullVariantShinyConfidence = 0.91f,
            fullVariantEventConfidence = null,
            fullVariantDebug = "test-debug-info"
        )

        assertEquals("species", trace.classifierScope)
        assertEquals("Charizard", trace.classifierSpecies)
        assertEquals("charizard_mega_x", trace.classifierSpriteKey)
        assertEquals("mega", trace.classifierVariantType)
        assertEquals(true, trace.classifierShiny)
        assertEquals(false, trace.classifierCostume)
        assertEquals(0.92f, trace.classifierConfidence)
        assertEquals(0.87f, trace.classifierScore)
        assertEquals("MEGA", trace.fullVariantClass)
        assertEquals("test-debug-info", trace.fullVariantDebug)
    }

    @Test
    fun toString_doesNotContainLocalPaths() {
        val trace = VariantDecisionTrace(
            classifierSpecies = "Pikachu",
            fullVariantDebug = "some debug info"
        )

        val str = trace.toString()

        assertFalse(str.contains("C:/Users"))
        assertFalse(str.contains("C:\\Users"))
        assertFalse(str.contains("/tmp"))
    }

    @Test
    fun pokemonData_carriesTraceWithoutPersistence() {
        val trace = VariantDecisionTrace(
            classifierScope = "species",
            classifierSpecies = "Eevee"
        )
        val pokemon = PokemonData(
            cp = 500,
            hp = 60,
            maxHp = 60,
            name = "Eevee",
            realName = "Eevee",
            candyName = "Eevee",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = "CP:500|HP:60/60|Name:Eevee",
            variantDecisionTrace = trace
        )

        assertEquals(trace, pokemon.variantDecisionTrace)
        assertEquals("species", pokemon.variantDecisionTrace?.classifierScope)
        assertEquals("Eevee", pokemon.variantDecisionTrace?.classifierSpecies)
    }

    @Test
    fun equality_worksForDataClass() {
        val trace1 = VariantDecisionTrace(
            classifierScope = "species",
            classifierSpecies = "Pikachu"
        )
        val trace2 = VariantDecisionTrace(
            classifierScope = "species",
            classifierSpecies = "Pikachu"
        )
        val trace3 = VariantDecisionTrace(
            classifierScope = "global",
            classifierSpecies = "Pikachu"
        )

        assertEquals(trace1, trace2)
        assertTrue(trace1 == trace2)
        assertFalse(trace1 == trace3)
    }
}
