package com.pokerarity.scanner.data.repository

import android.content.Context
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.BulbapediaEventArchive
import com.pokerarity.scanner.data.model.BulbapediaEventArchiveEntry

object BulbapediaEventArchiveLoader {
    private val gson = Gson()

    @Volatile
    private var cached: BulbapediaEventArchive? = null

    fun load(context: Context): BulbapediaEventArchive {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            return runCatching {
                val json = RemoteMetadataStore.readTextIfExists(context, "bulbapedia_event_pokemon_go.json")
                    ?: context.assets.open("data/bulbapedia_event_pokemon_go.json").bufferedReader().use { reader ->
                        reader.readText()
                    }
                gson.fromJson(json, BulbapediaEventArchive::class.java)
            }.getOrDefault(BulbapediaEventArchive()).also {
                cached = it
            }
        }
    }

    fun reset() {
        cached = null
    }

    fun indexBySpecies(entries: List<BulbapediaEventArchiveEntry>): Map<String, List<BulbapediaEventArchiveEntry>> =
        entries.groupBy { it.species }
}
