package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.OcrField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ScanOcrConfidenceReasonFactoryTest {

    @Test
    fun testCreatesConfidenceReasonsCorrectly() {
        // Arrange
        val date = Date()
        val pokemon = PokemonData(
            cp = 1500,
            hp = 100,
            maxHp = 100,
            name = "Pikachu",
            realName = null,
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = date,
            rawOcrText = "raw_ocr_mock"
        )
        val rarityScore = RarityScore(
            tier = RarityTier.COMMON,
            totalScore = 50,
            breakdown = emptyMap(),
            explanation = emptyList(),
            recognitionSummary = "Partial name match"
        )

        // Act
        val reasons = ScanOcrConfidenceReasonFactory.create(pokemon, rarityScore)

        // Assert
        assertTrue("CP should be considered high confidence because it was parsed", reasons.isParsed(OcrField.CP))
        assertTrue("HP should be considered high confidence because it was parsed", reasons.isParsed(OcrField.HP))
        assertTrue("Date should be considered high confidence because it was parsed", reasons.isParsed(OcrField.CAUGHT_DATE))
        
        assertEquals(1, reasons.warnings.size)
        assertEquals("Partial name match", reasons.warnings[0])
    }
    
    @Test
    fun testMissingFieldsGenerateWarningsAndLowConfidence() {
        // Arrange
        val pokemon = PokemonData(
            cp = null,
            hp = null,
            maxHp = null,
            name = "Pikachu",
            realName = null,
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = "raw_ocr_mock"
        )
        val rarityScore = RarityScore(
            tier = RarityTier.COMMON,
            totalScore = 50,
            breakdown = emptyMap(),
            explanation = emptyList(),
            recognitionSummary = null
        )

        // Act
        val reasons = ScanOcrConfidenceReasonFactory.create(pokemon, rarityScore)

        // Assert
        assertFalse("CP should be considered high confidence because it's null", reasons.isParsed(OcrField.CP))
        assertFalse("HP should be considered high confidence because it's null", reasons.isParsed(OcrField.HP))
        assertFalse("Date should be considered high confidence because it's null", reasons.isParsed(OcrField.CAUGHT_DATE))
        
        // Ensure that missing fields triggered proper reason codes
        val cpField = reasons.forField(OcrField.CP)
        assertTrue("Expected cp_missing code", cpField?.reasonCodes?.contains("cp_missing") == true)
        
        val hpField = reasons.forField(OcrField.HP)
        assertTrue("Expected hp_missing code", hpField?.reasonCodes?.contains("hp_missing") == true)
        
        val dateField = reasons.forField(OcrField.CAUGHT_DATE)
        assertTrue("Expected date_missing code", dateField?.reasonCodes?.contains("date_missing") == true)
    }
}
