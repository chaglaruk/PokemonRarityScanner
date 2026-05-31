package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.util.ocr.OcrDiagnosticsExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRawOcrDiagnosticsTest {

    @Test
    fun testAttach_preservesRawTextAndDoesNotAppendRedundantMarkers() {
        // Arrange
        val initialRawText = "CP:100|HP:10|Name:Pikachu|Candy:Pikachu Candy"
        val pokemon = PokemonData(
            cp = 100,
            hp = 10,
            maxHp = 10,
            name = "Pikachu",
            realName = null,
            candyName = "Pikachu Candy",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = initialRawText
        )
        val rarityScore = RarityScore(
            tier = RarityTier.COMMON,
            totalScore = 50,
            breakdown = emptyMap(),
            explanation = emptyList(),
            recognitionSummary = "Partial name match"
        )
        val bundle = OcrDiagnosticsExporter.Bundle(
            directory = "dummy/path",
            files = emptyMap()
        )

        // Act
        val result = ScanRawOcrDiagnostics.attach(pokemon, rarityScore, bundle)

        // Assert
        // CP/HP/Name/Candy existing markers are preserved perfectly (no mutation)
        assertEquals(initialRawText, result.rawOcrText)
        assertTrue(result.rawOcrText.contains("CP:100"))
        assertTrue(result.rawOcrText.contains("HP:10"))
        assertTrue(result.rawOcrText.contains("Name:Pikachu"))
        assertTrue(result.rawOcrText.contains("Candy:Pikachu Candy"))

        // Redundant / invalid markers are NOT appended
        assertFalse(result.rawOcrText.contains("RecognitionSummary"))
        assertFalse(result.rawOcrText.contains("CpOcrStatus"))
        assertFalse(result.rawOcrText.contains("HpOcrStatus"))
        assertFalse(result.rawOcrText.contains("IvDiagnosticDir"))
        assertFalse(result.rawOcrText.contains("IvDiagnosticFile_"))
        assertFalse(result.rawOcrText.contains("C:/Users"))
        assertFalse(result.rawOcrText.contains("/tmp"))
        
        // Structured fields are correctly mapped
        assertEquals("dummy/path", result.ocrDiagnosticsDir)
        assertTrue(result.ocrConfidenceReasons?.warnings?.contains("Partial name match") == true)
    }
}
