package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.repository.RarityCalculator
import org.json.JSONObject
import java.io.Reader

/** Public numeric facts bundled with the app; never fetched while scanning. */
internal class RecognitionProfiles(
    val profiles: List<Profile>,
    val cpMultipliers: Map<Double, Double>
) {
    data class Profile(
        val species: String,
        val forms: Set<String>,
        val stats: RarityCalculator.BaseStats,
        val types: Set<String>,
        val familyId: String,
        val candySpecies: String
    )

    private val bySpecies = profiles.groupBy { it.species.lowercase() }
    fun forSpecies(species: String): List<Profile> = bySpecies[species.lowercase()].orEmpty()
    fun forCandy(candySpecies: String): List<Profile> = profiles.filter { it.candySpecies.equals(candySpecies, true) }

    companion object {
        val EMPTY = RecognitionProfiles(emptyList(), emptyMap())
        val TYPES = setOf("normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
            "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy")

        fun read(reader: Reader): RecognitionProfiles {
            val root = JSONObject(reader.readText())
            require(root.getInt("version") == 1)
            val source = root.getJSONObject("source")
            require(source.getString("revision").matches(Regex("[a-f0-9]{40}")))
            require(source.getString("sha256").matches(Regex("[a-f0-9]{64}")))
            val multiplierData = root.getJSONObject("cpMultipliers")
            require(multiplierData.getInt("version") == 1)
            require(multiplierData.getString("method") == "float32_integer_rms_half")
            val multiplierValues = multiplierData.getJSONObject("values")
            val expectedLevels = (0..100).map { 1.0 + it / 2.0 }
            require(multiplierValues.keys().asSequence().toSet() == expectedLevels.map { it.toString() }.toSet())
            val cpMultipliers = expectedLevels.associateWith { level ->
                val raw = multiplierValues.get(level.toString())
                require(raw is Number)
                raw.toDouble().also { require(it.isFinite() && it > 0.0 && it < 1.0) }
            }
            require(cpMultipliers.values.zipWithNext().all { (a, b) -> a < b })
            val rows = root.getJSONArray("profiles")
            val profiles = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val forms = row.getJSONArray("forms").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                val types = row.getJSONArray("types").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                val stats = RarityCalculator.BaseStats(row.getInt("atk"), row.getInt("def"), row.getInt("sta"), 0.0, 0.0)
                val species = row.getString("species")
                val family = row.getString("familyId")
                val candy = row.getString("candySpecies")
                require(species.isNotBlank() && candy.isNotBlank() && family.startsWith("FAMILY_"))
                require(forms.isNotEmpty() && forms.none { it.isBlank() })
                require(types.size in 1..2 && TYPES.containsAll(types))
                require(listOf(stats.atk, stats.def, stats.sta).all { it in 1..1000 })
                Profile(species, forms, stats, types, family, candy)
            }
            require(profiles.map { it.species }.distinct().size == root.getInt("speciesCount"))
            return RecognitionProfiles(profiles, cpMultipliers)
        }
    }
}
