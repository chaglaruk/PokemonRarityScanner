package com.pokerarity.scanner.util.vision

import android.content.Context
import org.json.JSONObject

object VariantRegistry {

    private const val ASSET_PATH = "data/variant_registry.json"

    private var loaded = false
    private var costumeLikeSpecies: Set<String> = emptySet()

    fun hasCostumeLikeSpecies(context: Context, species: String?): Boolean {
        if (species.isNullOrBlank()) return false
        ensureLoaded(context)
        return costumeLikeSpecies.contains(species.trim().lowercase())
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val arr = root.optJSONArray("costumeLikeSpecies")
            if (arr == null) {
                costumeLikeSpecies = emptySet()
                return
            }
            val species = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val name = arr.optString(i, "").trim()
                if (name.isNotEmpty()) {
                    species.add(name.lowercase())
                }
            }
            costumeLikeSpecies = species
        } catch (_: Exception) {
            costumeLikeSpecies = emptySet()
        }
    }
}
