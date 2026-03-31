package com.pokerarity.scanner.util.vision

import android.content.Context
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import com.pokerarity.scanner.data.repository.VariantCatalogLoader

object VariantPrototypeStore {

    private const val ASSET_PATH = "data/variant_classifier_model.json"

    data class PrototypeFeatures(
        val aHash: String,
        val dHash: String,
        val edge: List<Float>,
        val fullHist: List<Float>,
        val headHist: List<Float>,
        val upperHist: List<Float>,
        val bodyHist: List<Float>,
        val foregroundRatio: Float,
        val aspectRatio: Float
    )

    data class Entry(
        val dex: Int,
        val species: String,
        val formId: String,
        val variantId: String? = null,
        val assetKey: String,
        val spriteKey: String,
        val isShiny: Boolean,
        val isCostumeLike: Boolean,
        val variantType: String,
        val eventTags: List<String> = emptyList(),
        val hasEventMetadata: Boolean = false,
        val releaseWindow: com.pokerarity.scanner.data.model.ReleaseWindow? = null,
        val gameMasterCostumeForms: List<String> = emptyList(),
        val filename: String,
        val sampleCount: Int,
        val prototype: PrototypeFeatures
    )

    private data class Payload(
        val version: Int,
        val generatedAt: String,
        val source: String,
        val entryCount: Int,
        val speciesCount: Int,
        val entries: List<Entry>
    )

    private var loaded = false
    private var allEntries: List<Entry> = emptyList()
    private var bySpecies: Map<String, List<Entry>> = emptyMap()

    fun entries(context: Context): List<Entry> {
        ensureLoaded(context)
        return allEntries
    }

    fun entriesForSpecies(context: Context, species: Collection<String>): List<Entry> {
        ensureLoaded(context)
        if (species.isEmpty()) return allEntries
        val wanted = species.map { it.trim().lowercase() }.toSet()
        return wanted.flatMap { bySpecies[it].orEmpty() }
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val payload = Gson().fromJson(json, Payload::class.java)
            val catalogBySprite = VariantCatalogLoader.indexBySpriteKey(
                VariantCatalogLoader.load(context).entries
            )
            allEntries = payload.entries.map { entry ->
                applyCatalog(entry, catalogBySprite[entry.spriteKey])
            }
            bySpecies = allEntries.groupBy { it.species.lowercase() }
        } catch (_: Exception) {
            allEntries = emptyList()
            bySpecies = emptyMap()
        }
    }

    fun applyCatalog(entry: Entry, catalog: VariantCatalogEntry?): Entry {
        if (catalog == null) return entry
        return entry.copy(
            dex = catalog.dex,
            species = catalog.species,
            formId = catalog.formId,
            variantId = catalog.variantId,
            assetKey = catalog.assetKey,
            spriteKey = catalog.spriteKey,
            isShiny = catalog.isShiny,
            isCostumeLike = catalog.isCostumeLike,
            variantType = catalog.variantClass,
            eventTags = catalog.eventTags,
            hasEventMetadata = catalog.hasEventMetadata,
            releaseWindow = catalog.releaseWindow,
            gameMasterCostumeForms = catalog.gameMasterCostumeForms
        )
    }
}
