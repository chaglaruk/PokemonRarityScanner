package com.pokerarity.scanner.data.repository

import android.content.Context
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.AuthoritativeVariantDb
import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.util.perf.DebugScanTiming
import com.pokerarity.scanner.util.perf.ScanTimingStage

object AuthoritativeVariantDbLoader {
    private const val ASSET_PATH = "data/authoritative_variant_db.json"
    private val gson = Gson()

    @Volatile
    private var cached: AuthoritativeVariantDb? = null

    fun load(context: Context): AuthoritativeVariantDb =
        DebugScanTiming.measure(ScanTimingStage.VARIANT_AUTHORITATIVE_DB_ACCESS) {
            cached ?: synchronized(this) {
                cached ?: run {
                    val json = RemoteMetadataStore.readTextIfExists(context, "authoritative_variant_db.json")
                        ?: context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
                    parseJson(json).also { cached = it }
                }
            }
        }

    fun reset() {
        cached = null
    }

    fun parseJson(json: String): AuthoritativeVariantDb =
        DebugScanTiming.measure(ScanTimingStage.VARIANT_AUTHORITATIVE_DB_PARSE) {
            val parsed = gson.fromJson(json, AuthoritativeVariantDb::class.java)
            parsed.copy(entries = parsed.entries.map(EventMetadataEvidence::sanitize))
        }

    fun indexBySpriteKey(entries: List<AuthoritativeVariantEntry>): Map<String, AuthoritativeVariantEntry> {
        return entries.associateBy { it.spriteKey }
    }

    fun indexBySpecies(entries: List<AuthoritativeVariantEntry>): Map<String, List<AuthoritativeVariantEntry>> {
        return entries.groupBy { it.species }
    }
}
