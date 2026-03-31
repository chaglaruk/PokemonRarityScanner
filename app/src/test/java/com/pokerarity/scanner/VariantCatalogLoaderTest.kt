package com.pokerarity.scanner

import com.pokerarity.scanner.data.repository.VariantCatalogLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VariantCatalogLoaderTest {

    private fun catalogFile(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "app/src/main/assets/data/variant_catalog.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        return File("app/src/main/assets/data/variant_catalog.json")
    }

    @Test
    fun parsesGeneratedCatalogAndIndexesKnownSprites() {
        val file = catalogFile()
        assertTrue("variant_catalog.json should exist", file.exists())

        val catalog = VariantCatalogLoader.parseJson(file.readText())
        assertTrue(catalog.entries.isNotEmpty())

        val blastoise = VariantCatalogLoader.indexBySpriteKey(catalog.entries)["009_00_05_shiny"]
        assertNotNull(blastoise)
        assertEquals("Blastoise", blastoise!!.species)
        assertEquals("costume", blastoise.variantClass)
        assertTrue(blastoise.isShiny)
        assertTrue(blastoise.isCostumeLike)

        val megaBlastoise = VariantCatalogLoader.indexBySpriteKey(catalog.entries)["009_51"]
        assertNotNull(megaBlastoise)
        assertEquals("form", megaBlastoise!!.variantClass)
        assertFalse(megaBlastoise.isCostumeLike)
    }

    @Test
    fun groupsSpeciesEntriesAndExposesEventMetadata() {
        val file = catalogFile()
        assertTrue("variant_catalog.json should exist", file.exists())

        val catalog = VariantCatalogLoader.parseJson(file.readText())
        val bySpecies = VariantCatalogLoader.indexBySpecies(catalog.entries)

        val pikachuEntries = bySpecies["Pikachu"]
        assertNotNull(pikachuEntries)
        assertTrue(pikachuEntries!!.any { it.spriteKey == "025_00_12" && it.variantClass == "costume" })
        assertTrue(pikachuEntries.any { it.gameMasterCostumeForms.isNotEmpty() })
    }

    @Test
    fun exposesReadableAliasForSparsePikachuCostumeVariant() {
        val file = catalogFile()
        assertTrue("variant_catalog.json should exist", file.exists())

        val catalog = VariantCatalogLoader.parseJson(file.readText())
        val bySprite = VariantCatalogLoader.indexBySpriteKey(catalog.entries)

        val pikachu = bySprite["025_00_23"]
        assertNotNull(pikachu)
        assertEquals("Pikachu", pikachu!!.species)
        assertEquals("costume", pikachu.variantClass)
        assertTrue(pikachu.variantLabel?.contains("Flying 03", ignoreCase = true) == true)
        assertTrue(pikachu.primaryEventLabel?.contains("Flying 03", ignoreCase = true) == true)
    }

    @Test
    fun keepsSlowpokeCostumeVariantCoverage() {
        val file = catalogFile()
        assertTrue("variant_catalog.json should exist", file.exists())

        val catalog = VariantCatalogLoader.parseJson(file.readText())
        val bySprite = VariantCatalogLoader.indexBySpriteKey(catalog.entries)

        val slowpoke = bySprite["079_00_PGO_2020"]
        assertNotNull(slowpoke)
        assertEquals("Slowpoke", slowpoke!!.species)
        assertEquals("costume", slowpoke.variantClass)
        assertTrue(slowpoke.isCostumeLike)
    }

    @Test
    fun includesAddressableAndAltCostumeSpritesForPreviouslyMissingSpecies() {
        val file = catalogFile()
        assertTrue("variant_catalog.json should exist", file.exists())

        val catalog = VariantCatalogLoader.parseJson(file.readText())
        val bySprite = VariantCatalogLoader.indexBySpriteKey(catalog.entries)

        val hoothoot = bySprite["163_00_JAN_2022_NOEVOLVE"]
        assertNotNull(hoothoot)
        assertEquals("Hoothoot", hoothoot!!.species)
        assertEquals("costume", hoothoot.variantClass)
        assertTrue(hoothoot.isCostumeLike)
        assertTrue(hoothoot.variantLabel?.contains("Jan 2022", ignoreCase = true) == true)

        val cubchoo = bySprite["613_00_PGO_WINTER_2020"]
        assertNotNull(cubchoo)
        assertEquals("Cubchoo", cubchoo!!.species)
        assertEquals("costume", cubchoo.variantClass)
        assertTrue(cubchoo.isCostumeLike)
        assertTrue(cubchoo.primaryEventLabel?.contains("Winter 2020", ignoreCase = true) == true)
    }

    @Test
    fun keepsSpecialFormsOutOfCostumeClassEvenForCostumeCapableSpecies() {
        val file = catalogFile()
        assertTrue("variant_catalog.json should exist", file.exists())

        val catalog = VariantCatalogLoader.parseJson(file.readText())
        val bySprite = VariantCatalogLoader.indexBySpriteKey(catalog.entries)

        val megaGengar = bySprite["094_00_FMEGA"]
        assertNotNull(megaGengar)
        assertEquals("Gengar", megaGengar!!.species)
        assertEquals("form", megaGengar.variantClass)
        assertFalse(megaGengar.isCostumeLike)
    }
}
