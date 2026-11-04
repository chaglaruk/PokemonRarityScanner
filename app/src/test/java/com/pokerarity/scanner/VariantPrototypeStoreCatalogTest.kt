package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import com.pokerarity.scanner.util.vision.VariantPrototypeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantPrototypeStoreCatalogTest {

    @Test
    fun authoritativeCatalogOverridesPrototypeSemantics() {
        val entry = VariantPrototypeStore.Entry(
            dex = 9,
            species = "Blastoise",
            formId = "51",
            variantId = null,
            assetKey = "009_51",
            spriteKey = "009_51",
            isShiny = false,
            isCostumeLike = true,
            variantType = "costume",
            eventTags = emptyList(),
            hasEventMetadata = false,
            releaseWindow = null,
            gameMasterCostumeForms = emptyList(),
            filename = "pokemon_icon_009_51.png",
            sampleCount = 5,
            prototype = VariantPrototypeStore.PrototypeFeatures(
                aHash = "",
                dHash = "",
                edge = emptyList(),
                fullHist = emptyList(),
                headHist = emptyList(),
                upperHist = emptyList(),
                bodyHist = emptyList(),
                foregroundRatio = 0f,
                aspectRatio = 1f
            )
        )

        val catalog = VariantCatalogEntry(
            dex = 9,
            species = "Blastoise",
            formId = "51",
            variantId = null,
            assetKey = "009_51",
            spriteKey = "009_51",
            isShiny = false,
            variantClass = "form",
            isCostumeLike = false,
            eventTags = listOf("Pokemon GO Fest 2023"),
            hasEventMetadata = true,
            releaseWindow = ReleaseWindow(firstSeen = "2023-08-04", lastSeen = "2023-08-06"),
            gameMasterCostumeForms = emptyList(),
            assetPath = "external/pogo_assets/Images/Pokemon - 256x256/pokemon_icon_009_51.png"
        )

        val merged = VariantPrototypeStore.applyCatalog(entry, catalog)

        assertEquals("form", merged.variantType)
        assertFalse(merged.isCostumeLike)
        assertTrue(merged.hasEventMetadata)
        assertEquals(listOf("Pokemon GO Fest 2023"), merged.eventTags)
    }

    @Test
    fun legacyCostumeModelKeysResolveOnlyByUniqueMatchingAssetFilename() {
        val normalModel = cottoneePrototypeEntry(
            spriteKey = "546_00_CSPRING_2024",
            filename = "pm546.cSPRING_2024.icon.png",
            isShiny = false
        )
        val shinyModel = cottoneePrototypeEntry(
            spriteKey = "546_00_CSPRING_2024_shiny",
            filename = "pm546.cSPRING_2024.s.icon.png",
            isShiny = true
        )
        val normalCatalog = cottoneeCatalogEntry(
            spriteKey = "546_00_SPRING_2024",
            filename = "pm546.cSPRING_2024.icon.png",
            isShiny = false
        )
        val shinyCatalog = cottoneeCatalogEntry(
            spriteKey = "546_00_SPRING_2024_shiny",
            filename = "pm546.cSPRING_2024.s.icon.png",
            isShiny = true
        )
        val catalog = listOf(normalCatalog, shinyCatalog)
        val index = catalog.associateBy { it.spriteKey }

        val normalResolved = VariantPrototypeStore.resolveCatalogEntry(normalModel, index, catalog)
        val shinyResolved = VariantPrototypeStore.resolveCatalogEntry(shinyModel, index, catalog)

        assertEquals(normalCatalog, normalResolved)
        assertEquals(shinyCatalog, shinyResolved)
        assertEquals("costume", VariantPrototypeStore.applyCatalog(normalModel, normalResolved).variantType)
        assertTrue(VariantPrototypeStore.applyCatalog(normalModel, normalResolved).isCostumeLike)
        assertEquals("costume", VariantPrototypeStore.applyCatalog(shinyModel, shinyResolved).variantType)
        assertTrue(VariantPrototypeStore.applyCatalog(shinyModel, shinyResolved).isCostumeLike)
    }

    @Test
    fun ambiguousOrIdentityMismatchedFilenameAliasesFailClosed() {
        val model = cottoneePrototypeEntry(
            spriteKey = "546_00_CSPRING_2024",
            filename = "pm546.cSPRING_2024.icon.png",
            isShiny = false
        )
        val first = cottoneeCatalogEntry(
            spriteKey = "546_00_SPRING_2024",
            filename = "pm546.cSPRING_2024.icon.png",
            isShiny = false
        )
        val conflicting = first.copy(spriteKey = "546_00_OTHER_EVENT")
        val wrongSpecies = first.copy(species = "Whimsicott")
        val wrongForm = first.copy(formId = "01")
        val wrongShiny = first.copy(isShiny = true)

        assertEquals(
            null,
            VariantPrototypeStore.resolveCatalogEntry(model, emptyMap(), listOf(first, conflicting))
        )
        assertEquals(
            null,
            VariantPrototypeStore.resolveCatalogEntry(model, emptyMap(), listOf(wrongSpecies))
        )
        assertEquals(
            null,
            VariantPrototypeStore.resolveCatalogEntry(model, emptyMap(), listOf(wrongForm))
        )
        assertEquals(
            null,
            VariantPrototypeStore.resolveCatalogEntry(model, emptyMap(), listOf(wrongShiny))
        )
        val unchanged = VariantPrototypeStore.applyCatalog(model, null)
        assertEquals("form", unchanged.variantType)
        assertFalse(unchanged.isCostumeLike)
    }

    private fun cottoneePrototypeEntry(
        spriteKey: String,
        filename: String,
        isShiny: Boolean
    ) = VariantPrototypeStore.Entry(
        dex = 546,
        species = "Cottonee",
        formId = "00",
        variantId = "CSPRING_2024",
        assetKey = spriteKey,
        spriteKey = spriteKey,
        isShiny = isShiny,
        isCostumeLike = false,
        variantType = "form",
        eventTags = emptyList(),
        hasEventMetadata = false,
        releaseWindow = null,
        gameMasterCostumeForms = emptyList(),
        filename = filename,
        sampleCount = 5,
        prototype = VariantPrototypeStore.PrototypeFeatures(
            aHash = "",
            dHash = "",
            edge = emptyList(),
            fullHist = emptyList(),
            headHist = emptyList(),
            upperHist = emptyList(),
            bodyHist = emptyList(),
            foregroundRatio = 0f,
            aspectRatio = 1f
        )
    )

    private fun cottoneeCatalogEntry(
        spriteKey: String,
        filename: String,
        isShiny: Boolean
    ) = VariantCatalogEntry(
        dex = 546,
        species = "Cottonee",
        formId = "00",
        variantId = "SPRING_2024",
        assetKey = "546_00_SPRING_2024",
        spriteKey = spriteKey,
        isShiny = isShiny,
        variantClass = "costume",
        isCostumeLike = true,
        eventTags = emptyList(),
        hasEventMetadata = false,
        releaseWindow = null,
        gameMasterCostumeForms = emptyList(),
        assetPath = "external/pogo_assets/Pokemon Icons/$filename"
    )
}
