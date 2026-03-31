package com.pokerarity.scanner.data.repository

import android.content.Context
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.AuthoritativeVariantDb
import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry

object AuthoritativeVariantDbLoader {
    private const val ASSET_PATH = "data/authoritative_variant_db.json"
    private val gson = Gson()

    @Volatile
    private var cached: AuthoritativeVariantDb? = null

    fun load(context: Context): AuthoritativeVariantDb {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            return parseJson(json).also { cached = it }
        }
    }

    fun parseJson(json: String): AuthoritativeVariantDb {
        return gson.fromJson(json, AuthoritativeVariantDb::class.java)
    }

    fun indexBySpriteKey(entries: List<AuthoritativeVariantEntry>): Map<String, AuthoritativeVariantEntry> {
        return entries.associateBy { it.spriteKey }
    }

    fun indexBySpecies(entries: List<AuthoritativeVariantEntry>): Map<String, List<AuthoritativeVariantEntry>> {
        return entries.groupBy { it.species }
    }
}
