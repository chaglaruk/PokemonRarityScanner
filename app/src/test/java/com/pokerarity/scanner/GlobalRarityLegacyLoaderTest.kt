package com.pokerarity.scanner

import com.pokerarity.scanner.data.repository.GlobalRarityLegacyLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GlobalRarityLegacyLoaderTest {

    private fun dbFile(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = File(dir, "app/src/main/assets/data/global_rarity_legacy_db.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        return File("app/src/main/assets/data/global_rarity_legacy_db.json")
    }

    @Test
    fun parsesGeneratedGlobalRarityLegacyDb() {
        val file = dbFile()
        assertTrue(file.exists())

        val db = GlobalRarityLegacyLoader.parseJson(file.readText())
        assertTrue(db.entries.isNotEmpty())
        assertTrue(db.sourceSummary["local_variant_catalog"] ?: 0 > 0)
        assertTrue(db.sourceSummary["snapshot_adapter:bulbapedia_event_overrides"] ?: 0 > 0)

        val pikachu = db.entries.first { it.spriteKey == "025_00_01" }
        assertEquals("Pikachu", pikachu.species)
        assertEquals("Holiday 2016", pikachu.lastKnownEvent)
        assertEquals("2016-12-12", pikachu.firstSeen)
        assertEquals("2017-01-03", pikachu.lastSeen)
        assertEquals("retired", pikachu.liveAvailability)
        assertTrue(pikachu.sourceIds.contains("snapshot_adapter:bulbapedia_event_overrides"))

        val shinyPikachu = db.entries.first { it.spriteKey == "025_00_01_shiny" }
        assertEquals("released_variant", shinyPikachu.shinyAvailability)

        val butterfreeFashion = db.entries.first { it.spriteKey == "012_01" }
        assertEquals("Fashion Raid Day", butterfreeFashion.activeEventLabel)
        assertEquals("2026-04-04", butterfreeFashion.activeEventStart)
        assertEquals("2026-04-04", butterfreeFashion.activeEventEnd)
        assertEquals("upcoming", butterfreeFashion.liveAvailability)

        val absolFashion = db.entries.first { it.spriteKey == "359_00_FMEGA" }
        assertEquals("Fashion Raid Day", absolFashion.activeEventLabel)
        assertEquals("2026-04-04", absolFashion.activeEventStart)
        assertEquals("upcoming", absolFashion.liveAvailability)
    }
}
