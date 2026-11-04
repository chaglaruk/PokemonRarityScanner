package com.pokerarity.scanner.util.vision

import android.content.Context
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import com.pokerarity.scanner.data.repository.VariantCatalogLoader

object VariantPrototypeStore {

    private const val ASSET_PATH = "data/variant_classifier_model.json"
    private val gson = Gson()

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

    private data class Snapshot(
        val entries: List<Entry>,
        val bySpecies: Map<String, List<Entry>>
    )

    private val cache = CompletedSnapshotCache<Snapshot>()
    private val emptySnapshot = Snapshot(emptyList(), emptyMap())

    fun entries(context: Context): List<Entry> = ensureLoaded(context).entries

    fun entriesForSpecies(context: Context, species: Collection<String>): List<Entry> {
        val ready = ensureLoaded(context)
        if (species.isEmpty()) return ready.entries
        val wanted = species.map { it.trim().lowercase() }.toSet()
        return wanted.flatMap { ready.bySpecies[it].orEmpty() }
    }

    private fun ensureLoaded(context: Context): Snapshot = cache.getOrLoad(
        load = {
            val payload = context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                gson.fromJson(reader, Payload::class.java)
            }
            val catalogEntries = VariantCatalogLoader.load(context).entries
            val catalogBySprite = VariantCatalogLoader.indexBySpriteKey(catalogEntries)
            val entries = payload.entries.map { entry ->
                applyCatalog(entry, resolveCatalogEntry(entry, catalogBySprite, catalogEntries))
            }
            Snapshot(entries, entries.groupBy { it.species.lowercase() })
        },
        // Missing/corrupt assets fail closed for this call without permanently poisoning retries.
        onFailure = { emptySnapshot }
    )

    /**
     * Resolve old prototype keys only when the unique catalog row points at the exact same
     * source asset file and the species/form/shiny identity also agrees.
     */
    internal fun resolveCatalogEntry(
        entry: Entry,
        catalogBySprite: Map<String, VariantCatalogEntry>,
        catalogEntries: List<VariantCatalogEntry>
    ): VariantCatalogEntry? = catalogBySprite[entry.spriteKey]
        ?: entry.filename.substringAfterLast('/')
            .takeIf(String::isNotBlank)
            ?.let { filename ->
                catalogEntries.asSequence()
                    .filter { candidate ->
                        candidate.assetPath?.substringAfterLast('/') == filename &&
                            candidate.dex == entry.dex &&
                            candidate.species.equals(entry.species, ignoreCase = true) &&
                            candidate.formId == entry.formId &&
                            candidate.isShiny == entry.isShiny
                    }
                    .singleOrNull()
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

/**
 * Readiness is published only after full snapshot construction. A concurrent reader
 * waits for that snapshot, and a failed loader does not cache its safe fallback.
 */
internal class CompletedSnapshotCache<T : Any> {
    @Volatile
    private var snapshot: T? = null

    fun getOrLoad(load: () -> T, onFailure: () -> T): T =
        snapshot ?: synchronized(this) {
            snapshot ?: try {
                load().also { snapshot = it }
            } catch (_: Exception) {
                onFailure()
            }
        }
}
