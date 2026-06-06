package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.catalog.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogParserTest {

    private val parser = CatalogParser()

    @Test
    fun parsesCatalogWithMissingOptionalArraysAsEmpty() {
        val catalog = parser.parse(
            """
            {
              "version": {
                "version": "test",
                "generatedAt": "2026-06-05T00:00:00Z",
                "schemaVersion": 1
              },
              "costumes": [
                {
                  "id": "pikachu_party_hat_2017",
                  "species": "Pikachu",
                  "costumeName": "Party Hat Pikachu",
                  "costumeType": "retired",
                  "eventIds": [],
                  "sourceLinks": ["https://example.com/source"],
                  "verificationStatus": "verified_community"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("test", catalog.version.version)
        assertEquals(1, catalog.costumes.size)
        assertEquals(VerificationStatus.VERIFIED_COMMUNITY, catalog.costumes.single().verificationStatus)
        assertTrue(catalog.events.isEmpty())
        assertTrue(catalog.regionals.isEmpty())
        assertTrue(catalog.specialSpecies.isEmpty())
    }

    @Test
    fun invalidCatalogReturnsEmptyFallback() {
        val catalog = parser.parse("not json")

        assertEquals("bundled-empty", catalog.version.version)
        assertTrue(catalog.costumes.isEmpty())
    }

    @Test
    fun parsesStandaloneVersionPayload() {
        val version = parser.parseVersion(
            """
            {
              "version": "2026.06.05",
              "generatedAt": "2026-06-05T12:00:00Z",
              "schemaVersion": 1
            }
            """.trimIndent()
        )

        assertEquals("2026.06.05", version?.version)
        assertEquals(1, version?.schemaVersion)
    }
}
