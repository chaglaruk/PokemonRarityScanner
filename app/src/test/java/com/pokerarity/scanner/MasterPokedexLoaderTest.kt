package com.pokerarity.scanner

import com.pokerarity.scanner.data.repository.MasterPokedexLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MasterPokedexLoaderTest {

    @Test
    fun generatedCottoneeCrownSignaturesAreCostumeCandidates() {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var masterFile: File? = null
        for (attempt in 0 until 6) {
            val candidate = File(dir, "app/src/main/assets/data/master_pokedex.json")
            if (candidate.exists()) {
                masterFile = candidate
                break
            }
            val parent = dir.parentFile ?: break
            dir = parent
        }
        val file = masterFile ?: File("app/src/main/assets/data/master_pokedex.json")
        assertTrue(file.exists())
        val db = MasterPokedexLoader.parseJson(file.readText())

        val base = db.entries.first { it.spriteKey == "546_00" }
        assertTrue(base.signature != null)
        assertFalse(base.isCostumeLike)
        listOf("546_00_SPRING_2024", "546_00_SPRING_2024_shiny").forEach { spriteKey ->
            val entry = db.entries.first { it.spriteKey == spriteKey }
            assertTrue(entry.isCostumeLike)
            assertTrue("$spriteKey should retain its generated signature", entry.signature != null)
        }
    }

    @Test
    fun parsesVariantHistoryAndAccessorySignatures() {
        val json = """
            {
              "version": 1,
              "generatedAt": "2026-04-15T00:00:00Z",
              "entries": [
                {
                  "species": "Pikachu",
                  "spriteKey": "025_00_POPSTAR",
                  "variantClass": "costume",
                  "isShiny": false,
                  "isCostumeLike": true,
                  "variantLabel": "Pop Star costume",
                  "historicalEvents": [
                    { "eventLabel": "Pokemon GO Fest 2021", "startDate": "2021-07-17", "endDate": "2021-07-18" }
                  ],
                  "signature": {
                    "aHash": "aaaa",
                    "dHash": "bbbb",
                    "pHash": "cccc",
                    "headPHash": "dddd",
                    "edge": [0.1,0.2,0.3,0.4,0.0,0.0,0.0,0.0]
                  }
                }
              ]
            }
        """.trimIndent()

        val db = MasterPokedexLoader.parseJson(json)
        val entry = db.entries.firstOrNull()

        assertNotNull(entry)
        assertEquals("Pikachu", entry!!.species)
        assertEquals("025_00_POPSTAR", entry.spriteKey)
        assertTrue(entry.historicalEvents.isNotEmpty())
        assertEquals("dddd", entry.signature?.headPHash)
    }

    @Test
    fun stripsSyntheticEventMetadataFromBaseAndWindowlessEntries() {
        val json = """
            {
              "version": 1,
              "generatedAt": "2026-04-15T00:00:00Z",
              "entries": [
                {
                  "species": "Torchic",
                  "spriteKey": "255_00",
                  "variantClass": "base",
                  "isShiny": false,
                  "isCostumeLike": false,
                  "variantLabel": null,
                  "eventLabel": "Community Day",
                  "eventStart": "2019-07-21",
                  "eventEnd": "2019-07-21",
                  "historicalEvents": []
                },
                {
                  "species": "Pikachu",
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

        val db = MasterPokedexLoader.parseJson(json)
        val torchic = db.entries.first { it.spriteKey == "255_00" }
        val flying = db.entries.first { it.spriteKey == "025_00_FFLYING_03" }

        assertNull(torchic.eventLabel)
        assertNull(torchic.eventStart)
        assertNull(torchic.eventEnd)
        assertNull(flying.eventLabel)
    }
}
