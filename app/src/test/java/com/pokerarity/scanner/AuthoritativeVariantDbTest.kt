package com.pokerarity.scanner

import com.pokerarity.scanner.data.repository.AuthoritativeVariantDbLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

        val flyingPikachu = db.entries.first { it.spriteKey == "025_00_FFLYING_03" }
        assertEquals("Orange balloon Flying Pikachu costume", flyingPikachu.variantLabel)
        assertEquals("Pokemon Air Adventures", flyingPikachu.eventLabel)
        assertEquals("2023-07-21", flyingPikachu.eventStart)

        val absol = db.entries.first { it.spriteKey == "359_00_FALL_2022_NOEVOLVE" }
        assertEquals("Fashionable costume", absol.variantLabel)
        assertTrue(absol.historicalEvents.any { it.eventLabel == "Fashion Week 2022" })

        val croagunk = db.entries.first { it.spriteKey == "453_01_16_shiny" }
        assertEquals("Fashionable costume", croagunk.variantLabel)
        assertTrue(croagunk.historicalEvents.any { it.eventLabel == "Fashion Week 2022" })

        val bulbasaurParty = db.entries.first { it.spriteKey == "001_00_11" }
        assertEquals("Party hat (red) costume", bulbasaurParty.variantLabel)
        assertTrue(bulbasaurParty.historicalEvents.any { it.eventLabel == "New Year's 2024" })

        val jigglypuffRibbon = db.entries.first { it.spriteKey == "039_00_JAN_2024_shiny" }
        assertEquals("Ribbon costume", jigglypuffRibbon.variantLabel)
        assertTrue(jigglypuffRibbon.historicalEvents.any { it.eventLabel == "New Year's 2025" })

        val psyduckSwim = db.entries.first { it.spriteKey == "054_00_FSWIM_2025_shiny" }
        assertEquals("Swim Ring costume", psyduckSwim.variantLabel)
        assertTrue(psyduckSwim.isCostumeLike)

        val dedenneHoliday = db.entries.first { it.spriteKey == "702_00_WINTER_2024_shiny" }
        assertEquals("Holiday Attire costume", dedenneHoliday.variantLabel)
        assertEquals("Holiday Part 1", dedenneHoliday.eventLabel)

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
        assertTrue(snorlaxJacket.historicalEvents.isEmpty())
        assertNull(snorlaxJacket.eventLabel)

        val cubone = db.entries.first { it.spriteKey == "104_00_FALL_2023" }
        assertEquals("costume", cubone.variantClass)
        assertTrue(cubone.isCostumeLike)
        assertTrue(cubone.historicalEvents.any { it.eventLabel?.contains("Día de Muertos 2023") == true })

        val vulpix = db.entries.first { it.spriteKey == "037_00_FALL_2022" }
        assertEquals("costume", vulpix.variantClass)
        assertTrue(vulpix.isCostumeLike)
        assertTrue(vulpix.historicalEvents.any { it.eventLabel?.contains("Halloween 2022") == true })
    }

    @Test
    fun stripsSyntheticEventMetadataFromBaseAndWindowlessCatalogArtifacts() {
        val json = """
            {
              "version": 1,
              "generatedAt": "2026-04-15T00:00:00Z",
              "count": 2,
              "sourceSummary": {},
              "entries": [
                {
                  "species": "Torchic",
                  "dex": 255,
                  "formId": "00",
                  "spriteKey": "255_00",
                  "variantClass": "base",
                  "isShiny": false,
                  "isCostumeLike": false,
                  "variantLabel": null,
                  "eventLabel": "Legacy bonus event",
                  "eventStart": "2019-07-21",
                  "eventEnd": "2019-07-21",
                  "historicalEvents": []
                },
                {
                  "species": "Pikachu",
                  "dex": 25,
                  "formId": "00",
                  "variantId": "FFLYING_03",
                  "spriteKey": "025_00_FFLYING_03",
                  "variantClass": "costume",
                  "isShiny": false,
                  "isCostumeLike": true,
                  "variantLabel": "Flying 03 costume",
                  "eventLabel": "Pikachu Flying 03",
                  "eventStart": null,
                  "eventEnd": null,
                  "historicalEvents": []
                }
              ]
            }
        """.trimIndent()

        val db = AuthoritativeVariantDbLoader.parseJson(json)
        val torchic = db.entries.first { it.spriteKey == "255_00" }
        val flying = db.entries.first { it.spriteKey == "025_00_FFLYING_03" }

        assertNull(torchic.eventLabel)
        assertNull(torchic.eventStart)
        assertNull(torchic.eventEnd)
        assertNull(flying.eventLabel)
    }
}
