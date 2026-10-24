package com.pokerarity.scanner.data.repository

import android.content.Context
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.VariantCatalog
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import java.io.Reader

object VariantCatalogLoader {
    private const val ASSET_PATH = "data/variant_catalog.json"
    private val gson = Gson()

    @Volatile
    private var cached: VariantCatalog? = null

    fun load(context: Context): VariantCatalog {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val overrideJson = RemoteMetadataStore.readTextIfExists(context, "variant_catalog.json")
            val catalog = if (overrideJson != null) {
                parseJson(overrideJson)
            } else {
                context.assets.open(ASSET_PATH).bufferedReader().use(::parseJson)
            }
            return catalog.also { cached = it }
        }
    }

    fun reset() {
        cached = null
    }

    fun parseJson(json: String): VariantCatalog =
        gson.fromJson(json, VariantCatalog::class.java)

    fun parseJson(reader: Reader): VariantCatalog =
        gson.fromJson(reader, VariantCatalog::class.java)

    fun indexBySpriteKey(entries: List<VariantCatalogEntry>): Map<String, VariantCatalogEntry> {
        return entries.associateBy { it.spriteKey }
    }

    fun indexBySpecies(entries: List<VariantCatalogEntry>): Map<String, List<VariantCatalogEntry>> {
        return entries.groupBy { it.species }
    }
}
