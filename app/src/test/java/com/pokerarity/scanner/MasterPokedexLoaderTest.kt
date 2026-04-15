package com.pokerarity.scanner

import com.pokerarity.scanner.data.repository.MasterPokedexLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterPokedexLoaderTest {

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
}
