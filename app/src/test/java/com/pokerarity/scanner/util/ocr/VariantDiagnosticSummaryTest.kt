package com.pokerarity.scanner.util.ocr

import com.google.gson.JsonParser
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.VariantDecisionTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantDiagnosticSummaryTest {
    private fun identity(shiny: RecognitionTruth) = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN, canonicalSpecies = "Gastly",
        formStatus = RecognitionFormStatus.UNKNOWN, shiny = shiny,
        shadow = RecognitionTruth.UNKNOWN, purified = RecognitionTruth.UNKNOWN,
        lucky = RecognitionTruth.UNKNOWN, costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.UNKNOWN, xxs = RecognitionTruth.UNKNOWN,
        xxl = RecognitionTruth.UNKNOWN, locationCard = RecognitionTruth.UNKNOWN
    )

    private fun summary(raw: Boolean?, final: RecognitionTruth?) = JsonParser.parseString(
        OcrDiagnosticsExporter.buildSummaryJsonForTest("test.png", PokemonData(
            name = "Gastly", realName = "Gastly", cp = 589, hp = 60, maxHp = 60,
            candyName = "Gastly", megaEnergy = null, weight = null, height = null,
            stardust = null, caughtDate = null,
            variantDecisionTrace = raw?.let { VariantDecisionTrace(fullVariantShiny = it) },
            recognitionIdentity = final?.let(::identity)
        ), null, null)
    ).asJsonObject

    @Test fun rawTrueFinalUnknownKeepsLegacyRawFlag() {
        val json = summary(true, RecognitionTruth.UNKNOWN)
        assertEquals(2, json["diagnosticSchemaVersion"].asInt)
        assertEquals(true, json["shiny"].asBoolean)
        assertEquals(true, json["rawFullMatchShiny"].asBoolean)
        assertEquals("UNKNOWN", json["finalTypedShiny"].asString)
    }

    @Test fun rawFalseFinalTrueKeepsBothDistinct() {
        val json = summary(false, RecognitionTruth.TRUE)
        assertEquals(false, json["shiny"].asBoolean)
        assertEquals(false, json["rawFullMatchShiny"].asBoolean)
        assertEquals("TRUE", json["finalTypedShiny"].asString)
    }

    @Test fun missingRawAndIdentityKeepNullInsteadOfInventingNegative() {
        val json = summary(null, null)
        assertEquals(false, json["shiny"].asBoolean)
        assertTrue(json["rawFullMatchShiny"].isJsonNull)
        assertTrue(json["finalTypedShiny"].isJsonNull)
    }
}
