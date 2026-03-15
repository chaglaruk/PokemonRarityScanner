package com.pokerarity.scanner.data.repository

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Loads and caches the rarity manifest from assets/data/rarity_manifest.json.
 * Provides lookup methods for species rarity, costume rarity, shiny rates, and age bonuses.
 *
 * Thread-safe singleton pattern — call [initialize] once from Application.onCreate or SplashActivity.
 */
object RarityManifestLoader {

    private const val TAG = "RarityManifestLoader"
    private const val MANIFEST_PATH = "data/rarity_manifest.json"

    // ── Cached data ─────────────────────────────────────────────────────
    private var speciesRarity: Map<String, Int> = emptyMap()
    private var shinyRates: Map<String, ShinyTier> = emptyMap()
    private var costumeFlatMap: Map<String, Int> = emptyMap()   // costume name → points
    private var ageBonusTiers: List<AgeBonusTier> = emptyList() // sorted descending by minDays
    private var formBonuses: Map<String, FormBonus> = emptyMap()
    private var isLoaded = false

    data class ShinyTier(val rate: String, val points: Int)
    data class AgeBonusTier(val minDays: Int, val points: Int, val label: String)
    data class FormBonus(val points: Int, val label: String)

    // ── Initialization ──────────────────────────────────────────────────

    @Synchronized
    fun initialize(context: Context) {
        if (isLoaded) return
        try {
            val json = context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            parseSpeciesRarity(root)
            parseShinyRates(root)
            parseCostumeRarity(root)
            parseAgeBonuses(root)
            parseFormBonuses(root)
            isLoaded = true
            Log.d(TAG, "Manifest loaded: ${speciesRarity.size} species, ${costumeFlatMap.size} costumes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load rarity manifest", e)
        }
    }

    // ── Public Lookups ──────────────────────────────────────────────────

    /**
     * Returns species base rarity (0-25). Defaults to 5 if the species is unknown.
     * Performs case-insensitive lookup with several normalization strategies.
     */
    fun getSpeciesRarity(name: String?): Int {
        if (name.isNullOrBlank()) return 5
        val cleaned = name.trim()

        // 1. Exact match
        speciesRarity[cleaned]?.let { return it }

        // 2. Case-insensitive match
        val lower = cleaned.lowercase()
        speciesRarity.entries.firstOrNull { it.key.lowercase() == lower }?.let { return it.value }

        // 3. Match without special chars (e.g. "Nidoran" without ♀/♂)
        val alphaOnly = cleaned.replace(Regex("[^A-Za-z ]"), "").trim()
        speciesRarity.entries.firstOrNull {
            it.key.replace(Regex("[^A-Za-z ]"), "").trim().equals(alphaOnly, ignoreCase = true)
        }?.let { return it.value }

        Log.w(TAG, "Species not found in manifest: '$name', using default 5")
        return 5
    }

    /**
     * Returns shiny bonus points based on how the Pokemon was likely obtained.
     * For now defaults to standard shiny rate (20 pts) since we detect shiny visually.
     */
    fun getShinyBonusPoints(): Int {
        return shinyRates["standard"]?.points ?: 20
    }

    /**
     * Returns costume rarity points (0-15) for a detected costume name.
     * Performs partial/fuzzy matching against known costume names.
     */
    fun getCostumeRarityPoints(costumeName: String?): Int {
        if (costumeName.isNullOrBlank()) return 2 // Default for "has costume but unknown type"

        val lower = costumeName.lowercase()

        // Exact match
        costumeFlatMap.entries.firstOrNull { it.key.lowercase() == lower }?.let { return it.value }

        // Partial match (costume name contained in known name or vice versa)
        costumeFlatMap.entries.firstOrNull {
            it.key.lowercase().contains(lower) || lower.contains(it.key.lowercase())
        }?.let { return it.value }

        return 2 // Unknown costume default
    }

    /**
     * Returns age bonus points (0-30) based on days since capture.
     */
    fun getAgeBonusPoints(daysSinceCapture: Int): Int {
        for (tier in ageBonusTiers) {
            if (daysSinceCapture >= tier.minDays) {
                return tier.points
            }
        }
        return 0
    }

    /**
     * Returns age bonus label for display.
     */
    fun getAgeBonusLabel(daysSinceCapture: Int): String {
        for (tier in ageBonusTiers) {
            if (daysSinceCapture >= tier.minDays) {
                return tier.label
            }
        }
        return "Recent capture"
    }

    /**
     * Returns form bonus (shadow, lucky, purified).
     */
    fun getFormBonus(formType: String): FormBonus {
        return formBonuses[formType] ?: FormBonus(0, "")
    }

    // ── Parsers ─────────────────────────────────────────────────────────

    private fun parseSpeciesRarity(root: JSONObject) {
        val obj = root.optJSONObject("speciesRarity") ?: return
        val map = mutableMapOf<String, Int>()
        for (key in obj.keys()) {
            if (key.startsWith("_")) continue // skip comments
            map[key] = obj.getInt(key)
        }
        speciesRarity = map
    }

    private fun parseShinyRates(root: JSONObject) {
        val obj = root.optJSONObject("shinyRates") ?: return
        val map = mutableMapOf<String, ShinyTier>()
        for (key in obj.keys()) {
            if (key.startsWith("_")) continue
            val tier = obj.getJSONObject(key)
            map[key] = ShinyTier(tier.getString("rate"), tier.getInt("points"))
        }
        shinyRates = map
    }

    private fun parseCostumeRarity(root: JSONObject) {
        val obj = root.optJSONObject("costumeRarity")?.optJSONObject("tiers") ?: return
        val map = mutableMapOf<String, Int>()
        for (tierKey in obj.keys()) {
            val tier = obj.getJSONObject(tierKey)
            val points = tier.getInt("points")
            val costumes = tier.getJSONArray("costumes")
            for (i in 0 until costumes.length()) {
                map[costumes.getString(i)] = points
            }
        }
        costumeFlatMap = map
    }

    private fun parseAgeBonuses(root: JSONObject) {
        val obj = root.optJSONObject("ageBonus") ?: return
        val arr = obj.optJSONArray("tiers") ?: return
        val list = mutableListOf<AgeBonusTier>()
        for (i in 0 until arr.length()) {
            val tier = arr.getJSONObject(i)
            list.add(
                AgeBonusTier(
                    minDays = tier.getInt("minDays"),
                    points = tier.getInt("points"),
                    label = tier.getString("label")
                )
            )
        }
        // Sort descending so we can pick the first matching tier
        ageBonusTiers = list.sortedByDescending { it.minDays }
    }

    private fun parseFormBonuses(root: JSONObject) {
        val obj = root.optJSONObject("formBonuses") ?: return
        val map = mutableMapOf<String, FormBonus>()
        for (key in obj.keys()) {
            val bonus = obj.getJSONObject(key)
            map[key] = FormBonus(bonus.getInt("points"), bonus.getString("label"))
        }
        formBonuses = map
    }
}
