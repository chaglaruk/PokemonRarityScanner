package com.pokerarity.scanner.data.repository

import android.content.Context
import android.util.Log
import org.json.JSONObject

object RarityRuleLoader {

    private const val TAG = "RarityRuleLoader"
    private const val ASSET_PATH = "data/rarity_rules.json"

    data class AxisCaps(
        val baseSpecies: Int,
        val variant: Int,
        val age: Int,
        val collector: Int
    )

    data class BonusRule(
        val points: Int,
        val label: String
    )

    data class AgeTier(
        val minDays: Int,
        val points: Int,
        val label: String
    )

    data class CollectorRules(
        val xxl: BonusRule,
        val xxs: BonusRule,
        val rareFemale: BonusRule,
        val eventWeightScale: Double,
        val eventWeightCap: Int,
        val eventLabel: String
    )

    data class ComboRule(
        val requires: List<String>,
        val points: Int,
        val label: String
    )

    data class ConfidenceWeights(
        val name: Double,
        val cp: Double,
        val hp: Double,
        val date: Double,
        val variants: Double
    )

    data class Rules(
        val axisCaps: AxisCaps,
        val variantBonuses: Map<String, BonusRule>,
        val ageTiers: List<AgeTier>,
        val collector: CollectorRules,
        val combos: List<ComboRule>,
        val confidence: ConfidenceWeights
    )

    @Volatile
    private var cached: Rules? = null

    fun get(context: Context): Rules {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val parsed = load(context)
            cached = parsed
            return parsed
        }
    }

    private fun load(context: Context): Rules {
        return try {
            val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            Rules(
                axisCaps = parseAxisCaps(root.getJSONObject("axisCaps")),
                variantBonuses = parseVariantBonuses(root.getJSONObject("variantBonuses")),
                ageTiers = parseAgeTiers(root.getJSONArray("ageTiers")),
                collector = parseCollector(root.getJSONObject("collectorBonuses")),
                combos = parseComboRules(root.getJSONArray("comboBonuses")),
                confidence = parseConfidence(root.getJSONObject("confidenceWeights"))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load rarity rules", e)
            fallbackRules()
        }
    }

    private fun parseAxisCaps(obj: JSONObject): AxisCaps = AxisCaps(
        baseSpecies = obj.getInt("baseSpecies"),
        variant = obj.getInt("variant"),
        age = obj.getInt("age"),
        collector = obj.getInt("collector")
    )

    private fun parseVariantBonuses(obj: JSONObject): Map<String, BonusRule> {
        val map = mutableMapOf<String, BonusRule>()
        for (key in obj.keys()) {
            val child = obj.getJSONObject(key)
            map[key] = BonusRule(
                points = child.getInt("points"),
                label = child.getString("label")
            )
        }
        return map
    }

    private fun parseAgeTiers(arr: org.json.JSONArray): List<AgeTier> {
        val list = mutableListOf<AgeTier>()
        for (index in 0 until arr.length()) {
            val child = arr.getJSONObject(index)
            list.add(
                AgeTier(
                    minDays = child.getInt("minDays"),
                    points = child.getInt("points"),
                    label = child.getString("label")
                )
            )
        }
        return list.sortedByDescending { it.minDays }
    }

    private fun parseCollector(obj: JSONObject): CollectorRules = CollectorRules(
        xxl = parseBonusRule(obj.getJSONObject("xxl")),
        xxs = parseBonusRule(obj.getJSONObject("xxs")),
        rareFemale = parseBonusRule(obj.getJSONObject("rareFemale")),
        eventWeightScale = obj.getDouble("eventWeightScale"),
        eventWeightCap = obj.getInt("eventWeightCap"),
        eventLabel = obj.getString("eventLabel")
    )

    private fun parseBonusRule(obj: JSONObject): BonusRule = BonusRule(
        points = obj.getInt("points"),
        label = obj.getString("label")
    )

    private fun parseComboRules(arr: org.json.JSONArray): List<ComboRule> {
        val list = mutableListOf<ComboRule>()
        for (index in 0 until arr.length()) {
            val child = arr.getJSONObject(index)
            val requiresArr = child.getJSONArray("requires")
            val requires = mutableListOf<String>()
            for (rIndex in 0 until requiresArr.length()) {
                requires.add(requiresArr.getString(rIndex))
            }
            list.add(
                ComboRule(
                    requires = requires,
                    points = child.getInt("points"),
                    label = child.getString("label")
                )
            )
        }
        return list
    }

    private fun parseConfidence(obj: JSONObject): ConfidenceWeights = ConfidenceWeights(
        name = obj.getDouble("name"),
        cp = obj.getDouble("cp"),
        hp = obj.getDouble("hp"),
        date = obj.getDouble("date"),
        variants = obj.getDouble("variants")
    )

    private fun fallbackRules(): Rules {
        return Rules(
            axisCaps = AxisCaps(35, 35, 20, 10),
            variantBonuses = mapOf(
                "shiny" to BonusRule(20, "Shiny variant"),
                "shadow" to BonusRule(6, "Shadow form"),
                "lucky" to BonusRule(8, "Lucky Pokemon"),
                "locationCard" to BonusRule(10, "Special background"),
                "costume" to BonusRule(7, "Costume variant"),
                "form" to BonusRule(6, "Special form")
            ),
            ageTiers = listOf(
                AgeTier(2555, 20, "7+ year veteran"),
                AgeTier(1825, 16, "5+ year veteran"),
                AgeTier(1095, 12, "3+ year veteran"),
                AgeTier(365, 6, "1+ year veteran")
            ),
            collector = CollectorRules(
                xxl = BonusRule(4, "XXL size"),
                xxs = BonusRule(4, "XXS size"),
                rareFemale = BonusRule(3, "Rare female ratio"),
                eventWeightScale = 0.35,
                eventWeightCap = 6,
                eventLabel = "Event-caught species"
            ),
            combos = listOf(
                ComboRule(listOf("shiny", "costume"), 8, "Shiny + costume combo"),
                ComboRule(listOf("shiny", "locationCard"), 6, "Shiny + background combo")
            ),
            confidence = ConfidenceWeights(0.22, 0.16, 0.20, 0.14, 0.28)
        )
    }
}
