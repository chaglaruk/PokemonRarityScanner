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
}
