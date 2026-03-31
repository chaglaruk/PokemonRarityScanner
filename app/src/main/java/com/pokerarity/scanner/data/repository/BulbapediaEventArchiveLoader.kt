package com.pokerarity.scanner.data.repository

import android.content.Context
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.BulbapediaEventArchive
import com.pokerarity.scanner.data.model.BulbapediaEventArchiveEntry

object BulbapediaEventArchiveLoader {
    private val gson = Gson()

    fun load(context: Context): BulbapediaEventArchive {
        return runCatching {
            context.assets.open("data/bulbapedia_event_pokemon_go.json").bufferedReader().use { reader ->
                gson.fromJson(reader, BulbapediaEventArchive::class.java)
            }
        }.getOrDefault(BulbapediaEventArchive())
    }

    fun indexBySpecies(entries: List<BulbapediaEventArchiveEntry>): Map<String, List<BulbapediaEventArchiveEntry>> =
        entries.groupBy { it.species }
}
