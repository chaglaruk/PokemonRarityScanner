package com.pokerarity.scanner.data.repository

import android.content.Context
import org.json.JSONObject

object PokemonFamilyRegistry {

    private const val ASSET_PATH = "data/pokemon_families.json"

    private var loaded = false
    private var speciesToFamily: Map<String, String> = emptyMap()
    private var familyToSpecies: Map<String, List<String>> = emptyMap()

    fun getFamilyMembers(context: Context, species: String?): List<String> {
        if (species.isNullOrBlank()) return emptyList()
        ensureLoaded(context)
        val familyId = speciesToFamily[species.trim().lowercase()] ?: return emptyList()
        return familyToSpecies[familyId].orEmpty()
    }

    fun isSameFamily(context: Context, first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return false
        ensureLoaded(context)
        val firstFamily = speciesToFamily[first.trim().lowercase()] ?: return false
        val secondFamily = speciesToFamily[second.trim().lowercase()] ?: return false
        return firstFamily == secondFamily
    }

    fun familySize(context: Context, species: String?): Int {
        if (species.isNullOrBlank()) return 0
        ensureLoaded(context)
        val familyId = speciesToFamily[species.trim().lowercase()] ?: return 0
        return familyToSpecies[familyId].orEmpty().size
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val root = JSONObject(context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() })
            val speciesObject = root.optJSONObject("speciesToFamily")
            val familiesObject = root.optJSONObject("families")

            val speciesMap = mutableMapOf<String, String>()
            speciesObject?.keys()?.forEach { key ->
                val familyId = speciesObject.optString(key, "")
                if (familyId.isNotBlank()) {
                    speciesMap[key.lowercase()] = familyId
                }
            }

            val familyMap = mutableMapOf<String, List<String>>()
            familiesObject?.keys()?.forEach { familyId ->
                val arr = familiesObject.optJSONArray(familyId) ?: return@forEach
                val members = mutableListOf<String>()
                for (index in 0 until arr.length()) {
                    val value = arr.optString(index, "").trim()
                    if (value.isNotBlank()) {
                        members += value
                    }
                }
                familyMap[familyId] = members
            }

            speciesToFamily = speciesMap
            familyToSpecies = familyMap
        } catch (_: Exception) {
            speciesToFamily = emptyMap()
            familyToSpecies = emptyMap()
        }
    }
}
