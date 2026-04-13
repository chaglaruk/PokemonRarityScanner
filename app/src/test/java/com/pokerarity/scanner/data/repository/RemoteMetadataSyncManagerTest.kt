package com.pokerarity.scanner.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMetadataSyncManagerTest {

    @Test
    fun parseManifest_extractsVersionAndFiles() {
        val manifest = """
            {
              "version": "2026-04-13",
              "files": {
                "rarity_manifest.json": "https://example.com/rarity_manifest.json",
                "variant_catalog.json": "https://example.com/variant_catalog.json"
              }
            }
        """.trimIndent()

        val parsed = RemoteMetadataSyncManager.parseManifest(manifest)

        assertEquals("2026-04-13", parsed.version)
        assertEquals(2, parsed.files.size)
        assertEquals(
            "https://example.com/variant_catalog.json",
            parsed.files["variant_catalog.json"]
        )
    }

    @Test
    fun parseManifest_returnsEmptyFilesWhenMissing() {
        val parsed = RemoteMetadataSyncManager.parseManifest("""{"version":"2026-04-13"}""")

        assertEquals("2026-04-13", parsed.version)
        assertTrue(parsed.files.isEmpty())
    }
}
