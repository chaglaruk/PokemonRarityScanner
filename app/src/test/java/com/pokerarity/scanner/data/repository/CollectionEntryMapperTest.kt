package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CollectionEntryMapperTest {

    @Test
    fun testMapperSetsFieldsCorrectly() {
        val date = Date()
        val pokemonData = PokemonData(
            cp = 500,
            hp = 50,
            maxHp = 50,
            name = "Pikachu",
            realName = null,
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = date,
            fullVariantMatch = FullVariantMatch(
                finalSpecies = "Pikachu",
                resolvedEventLabel = "Halloween 2023"
            )
        )
        
        val features = VisualFeatures(
            isShiny = true,
            hasCostume = true,
            isXXL = true,
            isXXS = false
        )
        
        val rarityScore = RarityScore(
            totalScore = 45,
            tier = RarityTier.RARE,
            breakdown = emptyMap(),
            explanation = emptyList()
        )

        val entity = CollectionEntryMapper.toEntity(
            scanHistoryId = 100L,
            dex = 25,
            formId = "BASE",
            variantId = "COSTUME_HALLOWEEN",
            pokemonData = pokemonData,
            features = features,
            rarityScore = rarityScore,
            backgroundType = "SPECIAL",
            backgroundLabel = "GlobalFest"
        )

        assertEquals(100L, entity.scanHistoryId)
        assertEquals(25, entity.dex)
        assertEquals("Pikachu", entity.speciesName)
        assertEquals("BASE", entity.formId)
        assertEquals("COSTUME_HALLOWEEN", entity.variantId)
        assertTrue(entity.isShiny)
        assertTrue(entity.isCostume)
        assertTrue(entity.isXXL)
        assertEquals("Halloween 2023", entity.costumeLabel)
        assertEquals("SPECIAL", entity.backgroundType)
        assertEquals("GlobalFest", entity.backgroundLabel)
        assertEquals(date, entity.caughtDate)
        assertEquals(45, entity.rarityScore)
        assertEquals("RARE", entity.rarityTierCode)
        assertEquals(
            "25-BASE-COSTUME_HALLOWEEN-SC-[BG:SPECIAL_GlobalFest]",
            entity.variantIdentityKey
        )
    }

    @Test
    fun testMapperHandlesNullableFields() {
        val pokemonData = PokemonData(
            cp = null, hp = null, maxHp = null, name = null, realName = null, candyName = null,
            megaEnergy = null, weight = null, height = null, stardust = null, caughtDate = null,
            fullVariantMatch = FullVariantMatch(finalSpecies = "UnknownSpecies")
        )
        val features = VisualFeatures()
        val rarityScore = RarityScore(
            totalScore = 0,
            tier = RarityTier.COMMON,
            breakdown = emptyMap(),
            explanation = emptyList()
        )

        val entity = CollectionEntryMapper.toEntity(
            scanHistoryId = null,
            dex = 0,
            formId = null,
            variantId = null,
            pokemonData = pokemonData,
            features = features,
            rarityScore = rarityScore,
            backgroundType = null,
            backgroundLabel = null
        )

        assertNull(entity.scanHistoryId)
        assertEquals("UnknownSpecies", entity.speciesName)
        assertNull(entity.formId)
        assertNull(entity.variantId)
        assertNull(entity.costumeLabel)
        assertNull(entity.backgroundType)
        assertNull(entity.backgroundLabel)
        assertNull(entity.caughtDate)
        assertEquals("0-BASE-NONE-NORMAL", entity.variantIdentityKey)
    }
}
