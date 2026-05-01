package com.pokerarity.scanner

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RarityRulesScoringTest {

    private fun rulesJson(): JsonObject {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = File(dir, "app/src/main/assets/data/rarity_rules.json")
            if (candidate.exists()) return JsonParser.parseString(candidate.readText()).asJsonObject
            dir = dir.parentFile ?: return@repeat
        }
        return JsonParser.parseString(File("app/src/main/assets/data/rarity_rules.json").readText()).asJsonObject
    }

    @Test
    fun variantAgeAndCollectorCarryMoreWeightThanBaseSpecies() {
        val rules = rulesJson()
        val caps = rules.getAsJsonObject("axisCaps")

        assertEquals(20, caps["baseSpecies"].asInt)
        assertEquals(48, caps["variant"].asInt)
        assertEquals(22, caps["age"].asInt)
        assertEquals(10, caps["collector"].asInt)
        assertEquals(100, caps["baseSpecies"].asInt + caps["variant"].asInt + caps["age"].asInt + caps["collector"].asInt)
    }

    @Test
    fun confirmedEventStacksHaveDedicatedComboWeight() {
        val combos = rulesJson().getAsJsonArray("comboBonuses")
        val labels = combos.associate {
            val combo = it.asJsonObject
            combo["label"].asString to combo["points"].asInt
        }

        assertTrue((labels["Costume + confirmed event combo"] ?: 0) >= 14)
        assertTrue((labels["Shiny costume event stack"] ?: 0) >= 8)
        assertTrue((labels["Shiny + background combo"] ?: 0) >= 12)
    }
}
