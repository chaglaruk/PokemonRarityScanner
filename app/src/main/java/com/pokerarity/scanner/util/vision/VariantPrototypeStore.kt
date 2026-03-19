package com.pokerarity.scanner.util.vision

import android.content.Context
import com.google.gson.Gson

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
            allEntries = payload.entries
            bySpecies = payload.entries.groupBy { it.species.lowercase() }
        } catch (_: Exception) {
            allEntries = emptyList()
            bySpecies = emptyMap()
        }
    }
}
