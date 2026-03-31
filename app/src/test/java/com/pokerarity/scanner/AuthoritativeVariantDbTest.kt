package com.pokerarity.scanner

import com.pokerarity.scanner.data.repository.AuthoritativeVariantDbLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AuthoritativeVariantDbTest {

    private fun dbFile(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "app/src/main/assets/data/authoritative_variant_db.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        return File("app/src/main/assets/data/authoritative_variant_db.json")
    }

    @Test
    fun parsesGeneratedAuthoritativeDb() {
        val file = dbFile()
        assertTrue(file.exists())

        val db = AuthoritativeVariantDbLoader.parseJson(file.readText())
        assertTrue(db.entries.isNotEmpty())
        assertTrue(db.sourceSummary["local_variant_catalog"] ?: 0 > 0)
        assertTrue(db.sourceSummary["bulbapedia_snapshot_matches"] ?: 0 > 0)
        assertTrue(db.sourceSummary["external_overrides"] ?: 0 > 0)

        val pikachu = db.entries.first { it.spriteKey == "025_00_12" }
        assertEquals("Pikachu", pikachu.species)
        assertEquals("costume", pikachu.variantClass)
        assertTrue(pikachu.aliases.isNotEmpty())
        assertEquals("World Championships costume", pikachu.variantLabel)
        assertEquals("2022 World Championships Celebration", pikachu.eventLabel)

        val raichu = db.entries.first { it.spriteKey == "026_00_01" }
        assertEquals("Festive hat costume", raichu.variantLabel)
        assertTrue(raichu.historicalEvents.any { it.eventLabel == "Holiday 2016" })

        val slowpoke = db.entries.first { it.spriteKey == "079_00_PGO_2020" }
        assertEquals("Slowpoke", slowpoke.species)
        assertEquals("costume", slowpoke.variantClass)
        assertTrue(slowpoke.isCostumeLike)
    }

    @Test
    fun carriesForwardAddressableCostumeCoverageForHoothootAndCubchoo() {
        val file = dbFile()
        assertTrue(file.exists())

        val db = AuthoritativeVariantDbLoader.parseJson(file.readText())

        val hoothoot = db.entries.first { it.spriteKey == "163_00_JAN_2022_NOEVOLVE" }
        assertEquals("Hoothoot", hoothoot.species)
        assertEquals("costume", hoothoot.variantClass)
        assertTrue(hoothoot.isCostumeLike)
        assertEquals("New Year's outfit costume", hoothoot.variantLabel)
        assertTrue(hoothoot.historicalEvents.any { it.eventLabel?.contains("New Year's 2022") == true })

        val cubchoo = db.entries.first { it.spriteKey == "613_00_PGO_WINTER_2020" }
        assertEquals("Cubchoo", cubchoo.species)
        assertEquals("costume", cubchoo.variantClass)
        assertTrue(cubchoo.isCostumeLike)
        assertEquals("Holiday ribbon costume", cubchoo.variantLabel)
        assertTrue(cubchoo.historicalEvents.isNotEmpty())
    }

    @Test
    fun keepsSpecialFormsAsFormsInsideAuthoritativeDb() {
        val file = dbFile()
        assertTrue(file.exists())

        val db = AuthoritativeVariantDbLoader.parseJson(file.readText())

        val megaGengar = db.entries.first { it.spriteKey == "094_00_FMEGA" }
        assertEquals("Gengar", megaGengar.species)
        assertEquals("form", megaGengar.variantClass)
        assertFalse(megaGengar.isCostumeLike)
    }

    @Test
    fun storesHistoricalAppearancesForOldAndReturningEventVariants() {
        val file = dbFile()
        assertTrue(file.exists())

        val db = AuthoritativeVariantDbLoader.parseJson(file.readText())

        val pikachuParty = db.entries.first { it.spriteKey == "025_00_02" }
        assertEquals("Party hat costume", pikachuParty.variantLabel)
        assertTrue(pikachuParty.historicalEvents.size >= 3)
        assertTrue(pikachuParty.historicalEvents.any { it.eventLabel?.contains("2017") == true })

        val lapras = db.entries.first { it.spriteKey == "131_00_FCOSTUME_2020" }
        assertEquals("Scarf costume", lapras.variantLabel)
        assertTrue(lapras.historicalEvents.size >= 3)
        assertTrue(lapras.historicalEvents.any { it.eventLabel?.contains("Water Festival") == true })
    }

    @Test
    fun resolvesHistoricalEventsForPreviouslyGenericCostumeEntries() {
        val file = dbFile()
        assertTrue(file.exists())

        val db = AuthoritativeVariantDbLoader.parseJson(file.readText())

        val pikachuSpring = db.entries.first { it.spriteKey == "025_00_SPRING_2023" }
        assertEquals("Pikachu", pikachuSpring.species)
        assertEquals("Cherry blossoms costume", pikachuSpring.variantLabel)
        assertTrue(pikachuSpring.historicalEvents.any { it.eventLabel == "Spring 2023" })

        val pikachuWcs = db.entries.first { it.spriteKey == "025_00_12" }
        assertTrue(pikachuWcs.historicalEvents.any { it.eventLabel?.contains("World Championships") == true })

        val snorlaxJacket = db.entries.first { it.spriteKey == "143_00_FWILDAREA_2024" }
        assertEquals("Studded Jacket costume", snorlaxJacket.variantLabel)
        assertTrue(snorlaxJacket.historicalEvents.any { it.eventLabel?.contains("Wild Area") == true })

        val cubone = db.entries.first { it.spriteKey == "104_00_FALL_2023" }
        assertEquals("costume", cubone.variantClass)
        assertTrue(cubone.isCostumeLike)
        assertTrue(cubone.historicalEvents.any { it.eventLabel?.contains("Día de Muertos 2023") == true })

        val vulpix = db.entries.first { it.spriteKey == "037_00_FALL_2022" }
        assertEquals("costume", vulpix.variantClass)
        assertTrue(vulpix.isCostumeLike)
        assertTrue(vulpix.historicalEvents.any { it.eventLabel?.contains("Halloween 2022") == true })
    }
}
