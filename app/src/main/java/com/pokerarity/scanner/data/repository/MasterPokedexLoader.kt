package com.pokerarity.scanner.data.repository

import android.content.Context
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.MasterPokedex

object MasterPokedexLoader {
    private const val ASSET_PATH = "data/master_pokedex.json"
    private val gson = Gson()

    @Volatile
    private var cached: MasterPokedex? = null

    fun load(context: Context): MasterPokedex {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val json = RemoteMetadataStore.readTextIfExists(context, "master_pokedex.json")
                ?: context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            return parseJson(json).also { cached = it }
        }
    }

    fun parseJson(json: String): MasterPokedex = gson.fromJson(json, MasterPokedex::class.java)

    fun reset() {
        cached = null
    }
}
