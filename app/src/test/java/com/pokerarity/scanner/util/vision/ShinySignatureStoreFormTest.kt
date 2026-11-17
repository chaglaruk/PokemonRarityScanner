package com.pokerarity.scanner.util.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ShinySignatureStoreFormTest {

    @Test
    fun exactResolvedVariantKeySelectsOnlyItsOwnSignaturePair() {
        val base = entry("Meowth", "052_00")
        val alolan = entry("Meowth", "052_00_FALOLA")

        assertSame(base, ShinySignatureStore.selectEntry(listOf(base), "052_00_shiny"))
        assertNull(ShinySignatureStore.selectEntry(listOf(base), "052_00_FALOLA_shiny"))
        assertSame(
            alolan,
            ShinySignatureStore.selectEntry(listOf(base, alolan), "052_00_FALOLA_shiny")
        )
        assertNull(ShinySignatureStore.selectEntry(listOf(base, alolan)))
    }

    @Test
    fun groupingRetainsMultipleReferencesForOneSpecies() {
        val base = entry("Meowth", "052_00")
        val alolan = entry("Meowth", "052_00_FALOLA")

        assertEquals(listOf(base, alolan), ShinySignatureStore.groupEntries(listOf(base, alolan))["meowth"])
    }

    private fun entry(species: String, key: String): ShinySignatureStore.ShinyEntry {
        val reference = ShinySignatureStore.SignatureRef(
            aHash = "0000000000000000",
            dHash = "0000000000000000",
            edge = FloatArray(8),
            color = FloatArray(12)
        )
        return ShinySignatureStore.ShinyEntry(species, key, reference, reference)
    }
}
