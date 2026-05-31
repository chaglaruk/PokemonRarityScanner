package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.PokemonData
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDiagnosticsExporterTest {

    @Test
    fun buildSummaryJson_includesRecognitionFields() {
        val pokemon = PokemonData(
            cp = 3266,
            hp = 168,
            maxHp = 168,
            name = "Porygon-z",
            realName = "Porygon-Z",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = 10000,
            caughtDate = null,
            rawOcrText = "ClassifierSpecies:Porygon-Z|FullVariantSpecies:Porygon-Z|FullVariantShiny:false|FullVariantCostume:false|FullVariantForm:false",
            powerUpCandyCost = 10,
            powerUpCandySource = "row_pair_alt",
            powerUpStardustSource = "row_pair_alt"
        )
        val solve = IvSolveDetails(
            ivMin = 80,
            ivMax = 100,
            ivCandidateCount = 3,
            levelMin = 39.0f,
            levelMax = 40.5f,
            ivSolveMode = IvSolveMode.RANGE,
            ivSolveSignalsUsed = listOf("cp", "hp", "stardust", "candy")
        )

        val summary = OcrDiagnosticsExporter.buildSummaryJsonForTest(
            screenshotPath = "C:/tmp/test.png",
            pokemon = pokemon,
            solve = solve,
            whyNotExact = "Multiple candidates remain."
        )
        val json = JsonParser.parseString(summary).asJsonObject

        assertEquals("Porygon-Z", json.get("species").asString)
        assertEquals("Porygon-Z", json.get("classifierSpecies").asString)
        assertEquals("Porygon-Z", json.get("fullVariantSpecies").asString)
        assertEquals(false, json.get("shiny").asBoolean)
        assertEquals(false, json.get("costume").asBoolean)
        assertEquals(false, json.get("form").asBoolean)
        assertTrue(json.getAsJsonObject("selectedSources").has("powerUpStardust"))
    }

    @Test
    fun buildSummaryJson_prefersStructuredTrace() {
        val trace = com.pokerarity.scanner.data.model.VariantDecisionTrace(
            classifierSpecies = "Pikachu",
            fullVariantSpecies = "Pikachu",
            fullVariantShiny = true,
            fullVariantCostume = true,
            fullVariantForm = false
        )
        val pokemon = PokemonData(
            cp = 10,
            hp = 10,
            maxHp = 10,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = 200,
            caughtDate = null,
            rawOcrText = "ClassifierSpecies:Wrong|FullVariantSpecies:Wrong|FullVariantShiny:false|FullVariantCostume:false|FullVariantForm:true",
            variantDecisionTrace = trace
        )

        val summary = OcrDiagnosticsExporter.buildSummaryJsonForTest(
            screenshotPath = "C:/tmp/test.png",
            pokemon = pokemon,
            solve = null,
            whyNotExact = null
        )
        val json = JsonParser.parseString(summary).asJsonObject

        assertEquals("Pikachu", json.get("species").asString)
        assertEquals("Pikachu", json.get("classifierSpecies").asString)
        assertEquals("Pikachu", json.get("fullVariantSpecies").asString)
        assertEquals(true, json.get("shiny").asBoolean)
        assertEquals(true, json.get("costume").asBoolean)
        assertEquals(false, json.get("form").asBoolean)
        
        // Ensure structured trace itself wasn't bluntly dumped with paths or secrets
        assertTrue(!json.has("variantDecisionTrace")) 
    }
}
