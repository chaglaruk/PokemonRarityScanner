package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.OcrConfidenceReasonsBuilder
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VariantDecisionTrace
import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ScanHistoryMapperTest {

    // ── Helper ────────────────────────────────────────────────────────────

    private fun pokemon(
        name: String? = "Pikachu",
        cp: Int? = 500,
        hp: Int? = 60,
        maxHp: Int? = 60,
        caughtDate: Date? = null,
        rawOcrText: String = "CP:500|HP:60/60|Name:Pikachu",
        ocrConfidenceReasons: com.pokerarity.scanner.data.model.OcrConfidenceReasons? = null,
        variantDecisionTrace: VariantDecisionTrace? = null
    ) = PokemonData(
        name = name,
        cp = cp,
        hp = hp,
        maxHp = maxHp,
        realName = name,
        candyName = null,
        megaEnergy = null,
        weight = null,
        height = null,
        stardust = null,
        caughtDate = caughtDate,
        rawOcrText = rawOcrText,
        ocrDiagnosticsDir = null,
        ocrDiagnosticsFiles = emptyMap(),
        ocrConfidenceReasons = ocrConfidenceReasons,
        fullVariantMatch = null,
        variantDecisionTrace = variantDecisionTrace
    )

    private fun score(
        total: Int = 42,
        tier: RarityTier = RarityTier.RARE
    ) = RarityScore(
        totalScore = total,
        tier = tier,
        breakdown = mapOf("species" to 20, "visual" to 22),
        explanation = listOf("Rare species", "Shiny bonus")
    )

    // ── Basic mapping ─────────────────────────────────────────────────────

    @Test
    fun toEntity_mapsBasicFieldsCorrectly() {
        val testDate = Date()
        val p = pokemon(caughtDate = testDate)
        val features = VisualFeatures(isShiny = true, isLucky = true)
        val s = score()

        val entity = ScanHistoryMapper.toEntity(p, features, s)

        assertEquals("Pikachu", entity.pokemonName)
        assertEquals(500, entity.cp)
        assertEquals(60, entity.hp)
        assertEquals(testDate, entity.caughtDate)
        assertEquals("CP:500|HP:60/60|Name:Pikachu", entity.rawOcrText)
        assertTrue(entity.isShiny)
        assertFalse(entity.isShadow)
        assertTrue(entity.isLucky)
        assertFalse(entity.hasCostume)
        assertEquals(42, entity.rarityScore)
        assertEquals("RARE", entity.rarityTier)
    }

    // ── Local path stripping ──────────────────────────────────────────────

    @Test
    fun toEntity_stripsLocalPathsFromRawOcrText() {
        val p = pokemon(
            name = "Bulbasaur",
            cp = null, hp = null, maxHp = null,
            rawOcrText = "Bulbasaur\nC:/Users/TestUser/Desktop/img.png\n/tmp/ocr.txt\nExtraText"
        )

        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score(0, RarityTier.COMMON))

        assertEquals("Bulbasaur\nExtraText", entity.rawOcrText)
        assertFalse(entity.rawOcrText.contains("C:/Users"))
        assertFalse(entity.rawOcrText.contains("/tmp"))
    }

    // ── Null field preservation ────────────────────────────────────────────

    @Test
    fun toEntity_preservesNullFields() {
        val p = pokemon(name = null, cp = null, hp = null, maxHp = null, caughtDate = null, rawOcrText = "")

        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score(0, RarityTier.COMMON))

        assertNull(entity.pokemonName)
        assertNull(entity.cp)
        assertNull(entity.hp)
        assertNull(entity.caughtDate)
        assertEquals("", entity.rawOcrText)
    }

    // ── Visual flag combinations ──────────────────────────────────────────

    @Test
    fun toEntity_mapsAllVisualFlagsFalse() {
        val features = VisualFeatures(
            isShiny = false, isShadow = false, isLucky = false, hasCostume = false
        )
        val entity = ScanHistoryMapper.toEntity(pokemon(), features, score())

        assertFalse(entity.isShiny)
        assertFalse(entity.isShadow)
        assertFalse(entity.isLucky)
        assertFalse(entity.hasCostume)
    }

    @Test
    fun toEntity_mapsAllVisualFlagsTrue() {
        val features = VisualFeatures(
            isShiny = true, isShadow = true, isLucky = true, hasCostume = true
        )
        val entity = ScanHistoryMapper.toEntity(pokemon(), features, score())

        assertTrue(entity.isShiny)
        assertTrue(entity.isShadow)
        assertTrue(entity.isLucky)
        assertTrue(entity.hasCostume)
    }

    @Test
    fun toEntity_mapsShadowOnlyCorrectly() {
        val features = VisualFeatures(isShadow = true)
        val entity = ScanHistoryMapper.toEntity(pokemon(), features, score())

        assertFalse(entity.isShiny)
        assertTrue(entity.isShadow)
        assertFalse(entity.isLucky)
        assertFalse(entity.hasCostume)
    }

    // ── Rarity tier serialization ─────────────────────────────────────────

    @Test
    fun toEntity_serializesAllRarityTierNames() {
        for (tier in RarityTier.entries) {
            val entity = ScanHistoryMapper.toEntity(
                pokemon(), VisualFeatures(), score(tier.minScore, tier)
            )
            assertEquals(
                "Tier ${tier.name} should serialize to its enum name",
                tier.name, entity.rarityTier
            )
            assertEquals(tier.minScore, entity.rarityScore)
        }
    }

    // ── Pipe-delimited rawOcrText format ──────────────────────────────────

    @Test
    fun toEntity_preservesPipeDelimitedOcrFormat() {
        val raw = "CP:1234|HP:100/120|Name:Charizard|Candy:Charmander Candy|Date:1700000000000"
        val p = pokemon(rawOcrText = raw)

        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score())

        assertEquals(raw, entity.rawOcrText)
        assertTrue(entity.rawOcrText.contains("CP:1234"))
        assertTrue(entity.rawOcrText.contains("Name:Charizard"))
        assertTrue(entity.rawOcrText.contains("Candy:Charmander Candy"))
    }

    // ── Structured fields intentionally excluded ──────────────────────────

    @Test
    fun toEntity_excludesOcrConfidenceReasons() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(500)
            .withHp(60, 60)
            .addWarning("Test warning")
            .build()
        val p = pokemon(ocrConfidenceReasons = reasons)

        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score())

        // Entity has no ocrConfidenceReasons field — if it compiled, it's excluded.
        // Verify the rawOcrText doesn't leak confidence data.
        assertFalse(entity.rawOcrText.contains("Test warning"))
        assertFalse(entity.rawOcrText.contains("cp_parsed"))
    }

    @Test
    fun toEntity_excludesVariantDecisionTrace() {
        val trace = VariantDecisionTrace(
            classifierScope = "species",
            classifierSpecies = "Charizard",
            classifierSpriteKey = "charizard_mega_x",
            fullVariantDebug = "secret-debug-info"
        )
        val p = pokemon(variantDecisionTrace = trace)

        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score())

        // Entity has no variantDecisionTrace field — verify rawOcrText doesn't leak.
        assertFalse(entity.rawOcrText.contains("charizard_mega_x"))
        assertFalse(entity.rawOcrText.contains("secret-debug-info"))
        assertFalse(entity.rawOcrText.contains("ClassifierScope"))
    }

    // ── Edge cases ────────────────────────────────────────────────────────

    @Test
    fun toEntity_handlesEmptyRawOcrText() {
        val p = pokemon(rawOcrText = "")
        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score())
        assertEquals("", entity.rawOcrText)
    }

    @Test
    fun toEntity_pathStrippingIsCaseInsensitive() {
        val p = pokemon(rawOcrText = "c:/users/dev/scan.png\nGoodLine")
        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score())
        assertFalse(entity.rawOcrText.contains("c:/users", ignoreCase = true))
        assertEquals("GoodLine", entity.rawOcrText)
    }
}

