package com.pokerarity.scanner.data.repository

import android.content.Context
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.GlobalRarityLegacyDb
import com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry

object GlobalRarityLegacyLoader {
    private const val ASSET_PATH = "data/global_rarity_legacy_db.json"
    private val gson = Gson()

    @Volatile
    private var cached: GlobalRarityLegacyDb? = null

    fun load(context: Context): GlobalRarityLegacyDb {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            return parseJson(json).also { cached = it }
        }
    }

    fun parseJson(json: String): GlobalRarityLegacyDb {
        return gson.fromJson(json, GlobalRarityLegacyDb::class.java)
    }

    fun indexBySpriteKey(entries: List<GlobalRarityLegacyEntry>): Map<String, GlobalRarityLegacyEntry> {
        return entries.associateBy { it.spriteKey }
    }

    fun indexBySpecies(entries: List<GlobalRarityLegacyEntry>): Map<String, List<GlobalRarityLegacyEntry>> {
        return entries.groupBy { it.species }
    }
}
