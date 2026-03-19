package com.pokerarity.scanner.data.repository

import android.content.Context
import org.json.JSONObject

object PokemonMoveRegistry {

    private const val ASSET_PATH = "data/pokemon_moves.json"

    private var loaded = false
    private var speciesToMoves: Map<String, Set<String>> = emptyMap()
    private var moveToSpecies: Map<String, List<String>> = emptyMap()
    private var moveKeysByLength: List<String> = emptyList()

    fun extractMoveHint(context: Context, rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        ensureLoaded(context)
        if (moveToSpecies.isEmpty()) return null

        val normalizedText = normalize(rawText)
        if (normalizedText.length < 4) return null

        moveKeysByLength.firstOrNull { moveKey -> normalizedText.contains(moveKey) }?.let { return it }

        val tokens = normalizedText.split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .distinct()
        for (token in tokens) {
            for (moveKey in moveKeysByLength) {
                if (kotlin.math.abs(moveKey.length - token.length) > 2) continue
                if (levenshtein(token, moveKey) <= if (moveKey.length >= 10) 2 else 1) {
                    return moveKey
                }
            }
        }

        return null
    }

    fun getSpeciesForMove(context: Context, normalizedMove: String?): List<String> {
        if (normalizedMove.isNullOrBlank()) return emptyList()
        ensureLoaded(context)
        return moveToSpecies[normalizedMove].orEmpty()
    }

    fun moveMatchScore(context: Context, species: String?, normalizedMove: String?): Double {
        if (species.isNullOrBlank() || normalizedMove.isNullOrBlank()) return 0.0
        ensureLoaded(context)
        val moves = speciesToMoves[species.trim().lowercase()] ?: return 0.0
        return if (moves.contains(normalizedMove.trim().lowercase())) 1.0 else 0.0
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val root = JSONObject(context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() })
            val speciesObject = root.optJSONObject("speciesToMoves")
            val moveObject = root.optJSONObject("moveToSpecies")

            val speciesMap = mutableMapOf<String, Set<String>>()
            speciesObject?.keys()?.forEach { species ->
                val arr = speciesObject.optJSONArray(species) ?: return@forEach
                val moves = mutableSetOf<String>()
                for (index in 0 until arr.length()) {
                    val move = normalize(arr.optString(index, ""))
                    if (move.length >= 4) {
                        moves += move
                    }
                }
                if (moves.isNotEmpty()) {
                    speciesMap[species.lowercase()] = moves
                }
            }

            val moveMap = mutableMapOf<String, List<String>>()
            moveObject?.keys()?.forEach { move ->
                val arr = moveObject.optJSONArray(move) ?: return@forEach
                val species = mutableListOf<String>()
                for (index in 0 until arr.length()) {
                    val value = arr.optString(index, "").trim()
                    if (value.isNotBlank()) {
                        species += value
                    }
                }
                if (species.isNotEmpty()) {
                    moveMap[move.lowercase()] = species
                }
            }

            speciesToMoves = speciesMap
            moveToSpecies = moveMap
            moveKeysByLength = moveMap.keys.sortedByDescending { it.length }
        } catch (_: Exception) {
            speciesToMoves = emptyMap()
            moveToSpecies = emptyMap()
            moveKeysByLength = emptyList()
        }
    }

    private fun normalize(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length
        var prev = IntArray(rhs.length + 1) { it }
        var curr = IntArray(rhs.length + 1)
        for (i in 1..lhs.length) {
            curr[0] = i
            for (j in 1..rhs.length) {
                val cost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val swap = prev
            prev = curr
            curr = swap
        }
        return prev[rhs.length]
    }
}
