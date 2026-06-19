package com.pokerarity.scanner.domain.collector

import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CollectorIdentityResolverTest {

    @Test
    fun `unknown species returns null`() {
        val entry = entry()

        val result = resolver(entry).resolve(
            pokemon = pokemon(name = "Unknown", match = match()),
            features = VisualFeatures()
        )

        assertNull(result)
    }

    @Test
    fun `species mismatch returns null`() {
        val entry = entry(species = "Raichu")

        val result = resolver(entry).resolve(
            pokemon = pokemon(match = match()),
            features = VisualFeatures()
        )

        assertNull(result)
    }

    @Test
    fun `low confidence species identity returns null`() {
        val entry = entry()

        val result = resolver(entry).resolve(
            pokemon = pokemon(match = match(speciesConfidence = 0.5f)),
            features = VisualFeatures()
        )

        assertNull(result)
    }

    @Test
    fun `low confidence variant identity returns null`() {
        val entry = entry()

        val result = resolver(entry).resolve(
            pokemon = pokemon(match = match(variantConfidence = 0.49f)),
            features = VisualFeatures()
        )

        assertNull(result)
    }

    @Test
    fun `known normal species resolves from matching catalog metadata`() {
        val entry = entry()

        val result = resolver(entry).resolve(
            pokemon = pokemon(match = match()),
            features = VisualFeatures()
        )

        assertNotNull(result)
        assertEquals(25, result?.dex)
        assertEquals("00", result?.formId)
        assertEquals(null, result?.variantId)
        assertEquals("25-00-NONE-NORMAL", result?.asStringKey())
    }

    @Test
    fun `shiny identity does not collapse into normal identity`() {
        val normalEntry = entry()
        val shinyEntry = entry(
            spriteKey = "025_00_shiny",
            isShiny = true
        )
        val resolver = resolver(normalEntry, shinyEntry)

        val normal = resolver.resolve(
            pokemon = pokemon(match = match()),
            features = VisualFeatures()
        )
        val shiny = resolver.resolve(
            pokemon = pokemon(match = match(spriteKey = shinyEntry.spriteKey, resolvedShiny = true)),
            features = VisualFeatures(isShiny = true)
        )

        assertNotNull(normal)
        assertNotNull(shiny)
        assertNotEquals(normal?.asStringKey(), shiny?.asStringKey())
    }

    @Test
    fun `authoritative costume identity does not collapse into normal identity`() {
        val normalEntry = entry()
        val costumeEntry = entry(
            variantId = "PARTY_HAT",
            spriteKey = "025_00_PARTY_HAT",
            variantClass = "costume",
            isCostumeLike = true
        )
        val resolver = resolver(normalEntry, costumeEntry)

        val normal = resolver.resolve(pokemon(match = match()), VisualFeatures())
        val costume = resolver.resolve(
            pokemon(
                match = match(
                    spriteKey = costumeEntry.spriteKey,
                    resolvedVariantClass = "costume",
                    resolvedCostume = true
                )
            ),
            VisualFeatures(hasCostume = true)
        )

        assertNotNull(costume)
        assertNotEquals(normal?.asStringKey(), costume?.asStringKey())
    }

    @Test
    fun `authoritative form identity does not collapse into normal identity`() {
        val normalEntry = entry()
        val formEntry = entry(
            formId = "ALOLA",
            variantId = "ALOLA",
            spriteKey = "025_ALOLA",
            variantClass = "form"
        )
        val resolver = resolver(normalEntry, formEntry)

        val normal = resolver.resolve(pokemon(match = match()), VisualFeatures())
        val form = resolver.resolve(
            pokemon(
                match = match(
                    spriteKey = formEntry.spriteKey,
                    resolvedVariantClass = "form",
                    resolvedForm = true
                )
            ),
            VisualFeatures(hasSpecialForm = true)
        )

        assertNotNull(form)
        assertNotEquals(normal?.asStringKey(), form?.asStringKey())
    }

    @Test
    fun `location background uncertainty returns null instead of normal key`() {
        val entry = entry()
        val resolver = resolver(entry)

        val normal = resolver.resolve(pokemon(match = match()), VisualFeatures())
        val location = resolver.resolve(
            pokemon(match = match()),
            VisualFeatures(hasLocationCard = true)
        )

        assertNotNull(normal)
        assertNull(location)
    }

    @Test
    fun `unsupported special background metadata returns null instead of normal key`() {
        val normalEntry = entry()
        val backgroundEntry = entry(
            variantId = "SPECIAL_BACKGROUND",
            spriteKey = "025_SPECIAL_BACKGROUND",
            variantClass = "background"
        )
        val resolver = resolver(normalEntry, backgroundEntry)

        val normal = resolver.resolve(pokemon(match = match()), VisualFeatures())
        val special = resolver.resolve(
            pokemon(match = match(spriteKey = backgroundEntry.spriteKey, resolvedVariantClass = "background")),
            VisualFeatures()
        )

        assertNotNull(normal)
        assertNull(special)
    }

    @Test
    fun `variant signal without matching catalog identity returns null`() {
        val entry = entry()

        val result = resolver(entry).resolve(
            pokemon = pokemon(match = match()),
            features = VisualFeatures(hasCostume = true)
        )

        assertNull(result)
    }

    @Test
    fun `XXL and XXS do not change identity key`() {
        val entry = entry()
        val resolver = resolver(entry)
        val pokemon = pokemon(match = match())

        val xxl = resolver.resolve(pokemon, VisualFeatures(isXXL = true))
        val xxs = resolver.resolve(pokemon, VisualFeatures(isXXS = true))

        assertNotNull(xxl)
        assertEquals(xxl?.asStringKey(), xxs?.asStringKey())
    }

    @Test
    fun `same inputs resolve deterministically`() {
        val entry = entry()
        val resolver = resolver(entry)
        val pokemon = pokemon(match = match())
        val features = VisualFeatures(isLucky = true)

        assertEquals(
            resolver.resolve(pokemon, features),
            resolver.resolve(pokemon, features)
        )
    }

    private fun resolver(vararg entries: VariantCatalogEntry) = CollectorIdentityResolver(
        entries.associateBy { it.spriteKey }
    )

    private fun pokemon(
        name: String = "Pikachu",
        match: FullVariantMatch?
    ) = PokemonData(
        cp = 500,
        hp = 50,
        maxHp = 50,
        name = name,
        realName = name,
        candyName = "Pikachu",
        megaEnergy = null,
        weight = null,
        height = null,
        stardust = 600,
        caughtDate = null,
        fullVariantMatch = match
    )

    private fun match(
        spriteKey: String = "025_00",
        resolvedVariantClass: String = "base",
        resolvedShiny: Boolean = false,
        resolvedCostume: Boolean = false,
        resolvedForm: Boolean = false,
        speciesConfidence: Float = 0.9f,
        variantConfidence: Float = 0.9f
    ) = FullVariantMatch(
        finalSpecies = "Pikachu",
        finalSpriteKey = spriteKey,
        resolvedVariantClass = resolvedVariantClass,
        resolvedShiny = resolvedShiny,
        resolvedCostume = resolvedCostume,
        resolvedForm = resolvedForm,
        speciesConfidence = speciesConfidence,
        variantConfidence = variantConfidence
    )

    private fun entry(
        species: String = "Pikachu",
        formId: String = "00",
        variantId: String? = null,
        spriteKey: String = "025_00",
        isShiny: Boolean = false,
        variantClass: String = "base",
        isCostumeLike: Boolean = false
    ) = VariantCatalogEntry(
        dex = 25,
        species = species,
        formId = formId,
        variantId = variantId,
        assetKey = spriteKey,
        spriteKey = spriteKey,
        isShiny = isShiny,
        variantClass = variantClass,
        isCostumeLike = isCostumeLike
    )
}
