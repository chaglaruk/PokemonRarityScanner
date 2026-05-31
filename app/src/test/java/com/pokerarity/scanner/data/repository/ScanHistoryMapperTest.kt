package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Date

class ScanHistoryMapperTest {

    @Test
    fun toEntity_mapsBasicFieldsCorrectly() {
        val testDate = Date()
        val pokemon = PokemonData(
            name = "Pikachu",
            cp = 500,
            hp = 60,
            maxHp = 60,
            realName = "Pikachu",
            candyName = "Pikachu Candy",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = testDate,
            rawOcrText = "Pikachu\nCP 500\nHP 60",
            ocrDiagnosticsDir = null,
            ocrDiagnosticsFiles = emptyMap(),
            ocrConfidenceReasons = null,
            fullVariantMatch = null
        )
        val features = VisualFeatures(
            isShiny = true,
            isShadow = false,
            isLucky = true,
            hasCostume = false
        )
        val score = RarityScore(
            totalScore = 42,
            tier = RarityTier.RARE,
            breakdown = emptyMap(),
            explanation = emptyList()
        )

        val entity = ScanHistoryMapper.toEntity(pokemon, features, score)

        assertEquals("Pikachu", entity.pokemonName)
        assertEquals(500, entity.cp)
        assertEquals(60, entity.hp)
        assertEquals(testDate, entity.caughtDate)
        assertEquals("Pikachu\nCP 500\nHP 60", entity.rawOcrText)
        
        assertEquals(true, entity.isShiny)
        assertEquals(false, entity.isShadow)
        assertEquals(true, entity.isLucky)
        assertEquals(false, entity.hasCostume)
        
        assertEquals(42, entity.rarityScore)
        assertEquals("RARE", entity.rarityTier)
    }

    @Test
    fun toEntity_stripsLocalPathsFromRawOcrText() {
        val pokemon = PokemonData(
            name = "Bulbasaur",
            cp = null,
            hp = null,
            maxHp = null,
            realName = null,
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = "Bulbasaur\nC:/Users/TestUser/Desktop/img.png\n/tmp/ocr.txt\nExtraText",
            ocrDiagnosticsDir = null,
            ocrDiagnosticsFiles = emptyMap(),
            ocrConfidenceReasons = null,
            fullVariantMatch = null
        )
        val features = VisualFeatures()
        val score = RarityScore(
            totalScore = 0,
            tier = RarityTier.COMMON,
            breakdown = emptyMap(),
            explanation = emptyList()
        )

        val entity = ScanHistoryMapper.toEntity(pokemon, features, score)

        assertEquals("Bulbasaur\nExtraText", entity.rawOcrText)
        assertFalse(entity.rawOcrText.contains("C:/Users"))
        assertFalse(entity.rawOcrText.contains("/tmp"))
    }
}
