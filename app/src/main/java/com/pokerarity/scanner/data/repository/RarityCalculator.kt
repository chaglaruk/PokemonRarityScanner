package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityAxisScore
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.ReleaseWindow
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry
import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.LiveEventContext
import com.pokerarity.scanner.data.model.VariantCatalogEntry
import com.pokerarity.scanner.data.model.VisualFeatures
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Calculates rarity scores for Pokemon based on multiple weighted factors.
 *
 * Score breakdown (0â€“100):
 *   â€¢ Base Species Rarity : 0-25  (from rarity_manifest.json)
 *   â€¢ Shiny Bonus         : 0-20  (based on shiny detection)
 *   â€¢ Costume Bonus       : 0-15  (based on costume rarity tier)
 *   â€¢ Form Bonus          : 0-10  (shadow / lucky / purified)
 *   â€¢ Age Bonus           : 0-30  (days since capture)
 *
 * Total is capped at 100.
 */
class RarityCalculator(private val context: android.content.Context) {
    private companion object {
        const val DAY_MS = 86_400_000L
    }

    private val supportDateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    private val baseStats: Map<String, BaseStats> by lazy { loadBaseStats() }
    private val variantCatalogBySprite: Map<String, VariantCatalogEntry> by lazy {
        runCatching {
            VariantCatalogLoader.indexBySpriteKey(VariantCatalogLoader.load(context).entries)
        }.getOrDefault(emptyMap())
    }
    private val authoritativeVariantBySprite by lazy {
        runCatching {
            AuthoritativeVariantDbLoader.indexBySpriteKey(AuthoritativeVariantDbLoader.load(context).entries)
        }.getOrDefault(emptyMap())
    }
    private val authoritativeVariantBySpecies by lazy {
        runCatching {
            AuthoritativeVariantDbLoader.indexBySpecies(AuthoritativeVariantDbLoader.load(context).entries)
        }.getOrDefault(emptyMap())
    }
    private val globalLegacyBySprite by lazy {
        runCatching {
            GlobalRarityLegacyLoader.indexBySpriteKey(GlobalRarityLegacyLoader.load(context).entries)
        }.getOrDefault(emptyMap())
    }
    private val bulbapediaEventArchiveBySpecies by lazy {
        runCatching {
            BulbapediaEventArchiveLoader.indexBySpecies(BulbapediaEventArchiveLoader.load(context).entries)
        }.getOrDefault(emptyMap())
    }

    data class BaseStats(val atk: Int, val def: Int, val sta: Int, val heightM: Double, val weightKg: Double)
    data class SpeciesFit(
        val species: String,
        val score: Double,
        val hpPossible: Boolean,
        val cpPossible: Boolean,
        val minArcDiff: Double,
        val sizeScore: Double = 0.0
    )
    data class SpeciesProfileCandidate(val species: String, val score: Double)

    private fun loadBaseStats(): Map<String, BaseStats> {
        return try {
            val jsonString = context.assets.open("data/pokemon_base_stats.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val map = mutableMapOf<String, BaseStats>()
            json.keys().forEach { key ->
                val obj = json.getJSONObject(key)
                map[key] = BaseStats(
                    atk = obj.getInt("atk"),
                    def = obj.getInt("def"),
                    sta = obj.getInt("sta"),
                    heightM = obj.optDouble("heightM", 0.0),
                    weightKg = obj.optDouble("weightKg", 0.0)
                )
            }
            map
        } catch (e: Exception) {
            android.util.Log.e("RarityCalculator", "Load base stats failed", e)
            emptyMap()
        }
    }

    // Combat Power Multipliers (CPM) for Levels 1 to 50 (including half levels)
    // Values from official GameMaster file
    private val cpmMap = mapOf(
        1.0 to 0.094, 1.5 to 0.135137432, 2.0 to 0.16639787, 2.5 to 0.192650919,
        3.0 to 0.21573247, 3.5 to 0.236572661, 4.0 to 0.25572005, 4.5 to 0.273530381,
        5.0 to 0.29024988, 5.5 to 0.306057378, 6.0 to 0.3210876, 6.5 to 0.335445036,
        7.0 to 0.34921268, 7.5 to 0.362457751, 8.0 to 0.3752356, 8.5 to 0.387592416,
        9.0 to 0.39956728, 9.5 to 0.411193551, 10.0 to 0.4225, 10.5 to 0.432926409,
        11.0 to 0.44310755, 11.5 to 0.453059959, 12.0 to 0.4627984, 12.5 to 0.472336093,
        13.0 to 0.48168495, 13.5 to 0.4908558, 14.0 to 0.49985844, 14.5 to 0.508701765,
        15.0 to 0.51739395, 15.5 to 0.525942511, 16.0 to 0.5343543, 16.5 to 0.542635738,
        17.0 to 0.5507927, 17.5 to 0.558830586, 18.0 to 0.5667545, 18.5 to 0.574569133,
        19.0 to 0.5822789, 19.5 to 0.589887907, 20.0 to 0.5974, 20.5 to 0.604823665,
        21.0 to 0.6121573, 21.5 to 0.619404122, 22.0 to 0.6265671, 22.5 to 0.633649143,
        23.0 to 0.64065295, 23.5 to 0.647580967, 24.0 to 0.65443563, 24.5 to 0.661219252,
        25.0 to 0.667934, 25.5 to 0.674581896, 26.0 to 0.6811649, 26.5 to 0.687684904,
        27.0 to 0.69414365, 27.5 to 0.70054287, 28.0 to 0.7068842, 28.5 to 0.713169109,
        29.0 to 0.7193991, 29.5 to 0.725575614, 30.0 to 0.7317, 30.5 to 0.734741009,
        31.0 to 0.7377695, 31.5 to 0.740785594, 32.0 to 0.74378943, 32.5 to 0.746781211,
        33.0 to 0.74976104, 33.5 to 0.752729087, 34.0 to 0.7556855, 34.5 to 0.758630368,
        35.0 to 0.76156384, 35.5 to 0.764486065, 36.0 to 0.76739717, 36.5 to 0.770297266,
        37.0 to 0.7731865, 37.5 to 0.776064962, 38.0 to 0.77893275, 38.5 to 0.781790055,
        39.0 to 0.784637, 39.5 to 0.787473608, 40.0 to 0.7903, 40.5 to 0.792803968,
        41.0 to 0.79530001, 41.5 to 0.797800015, 42.0 to 0.8003, 42.5 to 0.802799995,
        43.0 to 0.8053, 43.5 to 0.8078, 44.0 to 0.81029999, 44.5 to 0.812799985,
        45.0 to 0.81529999, 45.5 to 0.81779999, 46.0 to 0.82029999, 46.5 to 0.82279999,
        47.0 to 0.82529999, 47.5 to 0.82779999, 48.0 to 0.83029999, 48.5 to 0.83279999,
        49.0 to 0.83529999, 49.5 to 0.83779999, 50.0 to 0.84029999
    )

    // Stardust cost to Level range mapping (Supports Level 1-50)
    // For regular (non-lucky, non-shadow) Pokemon
    private val stardustToLevel = mapOf(
        200 to (1.0..2.5), 
        400 to (3.0..4.5), 
        600 to (5.0..6.5), 
        800 to (7.0..8.5),
        1000 to (9.0..10.5), 
        1300 to (11.0..12.5), 
        1600 to (13.0..14.5), 
        1900 to (15.0..16.5),
        2200 to (17.0..18.5), 
        2500 to (19.0..20.5), 
        3000 to (21.0..22.5), 
        3500 to (23.0..24.5),
        4000 to (25.0..26.5), 
        4500 to (27.0..28.5), 
        5000 to (29.0..30.5), 
        6000 to (31.0..32.5),
        7000 to (33.0..34.5), 
        8000 to (35.0..36.5), 
        9000 to (37.0..38.5), 
        10000 to (39.0..40.5),
        11000 to (41.0..42.5), 
        12000 to (43.0..44.5), 
        13000 to (45.0..46.5), 
        14000 to (47.0..48.5), 
        15000 to (49.0..50.0)
    )

    /**
     * Resmi Pokemon GO CP FormÃ¼lÃ¼
     */
    fun calculateCP(baseAtk: Int, baseDef: Int, baseSta: Int, ivAtk: Int, ivDef: Int, ivSta: Int, level: Double): Int {
        val cpm = cpmMap[level] ?: return 0
        val totalAtk = baseAtk + ivAtk
        val totalDef = baseDef + ivDef
        val totalSta = baseSta + ivSta

        // CP = floor((Atk * sqrt(Def) * sqrt(Sta) * CPM^2) / 10)
        val cp = (totalAtk * sqrt(totalDef.toDouble()) * sqrt(totalSta.toDouble()) * cpm.pow(2.0)) / 10.0
        return Math.max(10, floor(cp).toInt())
    }

    /**
     * Matematiksel Fallback ve CP Tamamlama (CP tamamen null olsa bile)
     * OCR'dan gelen tÃ¼m olasÄ± CP adaylarÄ±nÄ± (OCR'Ä±n gÃ¼rÃ¼ltÃ¼lÃ¼ okuduÄŸu her ÅŸey) dikkate alÄ±r.
     */
    fun validateAndFixCP(pokemon: PokemonData, allOcrCPs: List<Int> = emptyList(), features: VisualFeatures? = null): Int? {
        val species = pokemon.realName ?: pokemon.name ?: return pokemon.cp
        val stats = baseStats[species] ?: return pokemon.cp
        val hp = pokemon.hp ?: return pokemon.cp
        val arc = pokemon.arcLevel ?: return pokemon.cp
        var stardust = pokemon.stardust
        val reliableHpOcr = hasReliableHpOcr(pokemon.rawOcrText)

        // Shadow/Lucky/Purified Stardust dÃ¼zeltmesi
        // Stardust-Level tablomuz "Regular" Pokemonlar iÃ§indir.
        // EÄŸer Pokemon Shadow ise, okunan stardust 1.2x'tir. Normal deÄŸerine Ã§evirelim.
        if (stardust != null) {
            stardust = when {
                features?.isLucky == true -> (stardust * 2.0).toInt() // 0.5x -> 1.0x
                features?.isShadow == true -> (stardust / 1.2).toInt() // 1.2x -> 1.0x
                else -> stardust
            }
        }

        android.util.Log.d("RarityCalculator", "Deep Validation for $species (HP:$hp, Arc:$arc, Stardust:$stardust, OCR_CP:${pokemon.cp}, AllOCR:$allOcrCPs, reliableHp=$reliableHpOcr)")

        if (!reliableHpOcr && pokemon.cp != null && pokemon.cp > 0) {
            android.util.Log.w("RarityCalculator", "HP OCR is weak for $species, keeping OCR CP ${pokemon.cp}")
            return pokemon.cp
        }

        // 1. Level AralÄ±ÄŸÄ±nÄ± Belirle
        val levelRange = if (stardust != null && stardustToLevel.containsKey(stardust)) {
            stardustToLevel[stardust]!!
        } else {
            1.0..50.0
        }

        // 2. Arc Level'dan tahmin edilen seviye
        val estimatedLevelFromArc = (arc * 49.0 + 1.0)

        // 3. Arama DÃ¶ngÃ¼sÃ¼
        val candidates = mutableListOf<Triple<Int, Double, Int>>() // Triple(CP, LevelDifference, IV_Sum)
        
        for (lv in generateSequence(levelRange.start) { it + 0.5 }.takeWhile { it <= levelRange.endInclusive }) {
            val cpm = cpmMap[lv] ?: continue
            
            // Bu seviyede bu HP mÃ¼mkÃ¼n mÃ¼?
            for (ivSta in 0..15) {
                val calculatedHP = floor((stats.sta + ivSta) * cpm).toInt()
                if (calculatedHP == hp) {
                    // HP eÅŸleÅŸti, bu seviyedeki tÃ¼m CP olasÄ±lÄ±klarÄ±nÄ± tara
                    for (ivAtk in 0..15) {
                        for (ivDef in 0..15) {
                            val cp = calculateCP(stats.atk, stats.def, stats.sta, ivAtk, ivDef, ivSta, lv)
                            candidates.add(Triple(cp, abs(lv - estimatedLevelFromArc), ivAtk + ivDef + ivSta))
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            android.util.Log.w("RarityCalculator", "No candidates found for $species with HP:$hp at Level Range $levelRange")
            return pokemon.cp
        }

        // 4. En MantÄ±klÄ± AdayÄ± SeÃ§
        
        // Ã–NCELÄ°K 1: OCR'dan gelen bir CP adayÄ± matematiksel olarak MÃœMKÃœN mÃ¼?
        val ocrCounts = allOcrCPs.groupingBy { it }.eachCount()
        val bestOcr = ocrCounts.entries.maxByOrNull { it.value }
        if (bestOcr != null) {
            val ocrVal = bestOcr.key
            val count = bestOcr.value
            val ocrCandidates = candidates.filter { it.first == ocrVal }
            if (ocrCandidates.isNotEmpty()) {
                val minLevelDiff = ocrCandidates.minOf { it.second }
                if (count >= 2 || minLevelDiff <= 0.5) {
                    android.util.Log.i("RarityCalculator", "Trusting OCR candidate: $ocrVal (count=$count, minLevelDiff=$minLevelDiff)")
                    return ocrVal
                }
            }
        }

        // Ã–NCELÄ°K 2: Mevcut OCR sonucunu kontrol et
        val currentCpPossible = pokemon.cp != null && candidates.any { it.first == pokemon.cp }
        if (currentCpPossible) {
            android.util.Log.d("RarityCalculator", "Current CP ${pokemon.cp} is valid.")
            return pokemon.cp
        }
        if (pokemon.cp != null && pokemon.cp > 0 && allOcrCPs.isEmpty() && stardust == null && candidates.size > 2048) {
            android.util.Log.w(
                "RarityCalculator",
                "Keeping OCR CP ${pokemon.cp} for ${species}: no corroborated OCR CP and candidate space is too large (${candidates.size})"
            )
            return pokemon.cp
        }

        // Ã–NCELÄ°K 3: Arc Level'a en yakÄ±n olan aday grubunu bul
        // Arc Level 1.0 ise (Full) genelde Level 40 veya 50 demektir.
        val closeToArc = candidates.filter { it.second < 1.0 }
        if ((pokemon.cp == null || pokemon.cp <= 0) && allOcrCPs.isEmpty()) {
            if (closeToArc.isEmpty() && candidates.size > 1024) {
                android.util.Log.w("RarityCalculator", "CP estimate skipped for $species: candidate space too large (${candidates.size})")
                return pokemon.cp
            }
            if (closeToArc.isNotEmpty()) {
                val distinctCloseCp = closeToArc.map { it.first }.distinct().size
                if (distinctCloseCp > 48) {
                    android.util.Log.w("RarityCalculator", "CP estimate skipped for $species: near-arc CP set too ambiguous ($distinctCloseCp)")
                    return pokemon.cp
                }
            }
        }
        val sourceList = if (closeToArc.isNotEmpty()) closeToArc else candidates
        
        // Arc'a en yakÄ±n ve ortalama IV'ye (22.5) en yakÄ±n olanÄ± seÃ§
        val best = sourceList.minWithOrNull(compareBy({ it.second }, { abs(it.third.toDouble() - 22.5) }))?.first
        
        android.util.Log.d("RarityCalculator", "Mathematical CP Estimate: $best (Candidates: ${candidates.size}, NearArc: ${closeToArc.size})")
        return best
    }

    private fun hasReliableHpOcr(rawOcrText: String?): Boolean {
        if (rawOcrText.isNullOrBlank()) return false
        val hpRaw = rawOcrText
            .split("|")
            .firstOrNull { it.startsWith("HP:") }
            ?.substringAfter("HP:")
            ?.uppercase()
            ?.replace("O", "0")
            ?.replace("I", "1")
            ?.replace("S", "5")
            ?: return false

        if (Regex("""\d{2,3}\s*/\s*\d{2,3}""").containsMatchIn(hpRaw)) {
            return true
        }

        if (!hpRaw.contains("HP")) return false
        val numbers = Regex("""\d{2,3}""").findAll(hpRaw).count()
        return numbers >= 2
    }

    fun scoreSpeciesFit(pokemon: PokemonData, species: String): SpeciesFit {
        val stats = baseStats[species] ?: return SpeciesFit(species, 0.0, false, false, Double.MAX_VALUE)
        val hp = pokemon.hp ?: return SpeciesFit(species, 0.05, false, false, Double.MAX_VALUE)
        val arc = pokemon.arcLevel ?: return SpeciesFit(species, 0.10, false, false, Double.MAX_VALUE)
        val stardust = pokemon.stardust

        val levelRange = if (stardust != null && stardustToLevel.containsKey(stardust)) {
            stardustToLevel[stardust]!!
        } else {
            1.0..50.0
        }
        val estimatedLevelFromArc = (arc * 49.0 + 1.0)

        var hpPossible = false
        var cpPossible = false
        var minArcDiff = Double.MAX_VALUE
        var hpMatchCount = 0

        for (lv in generateSequence(levelRange.start) { it + 0.5 }.takeWhile { it <= levelRange.endInclusive }) {
            val cpm = cpmMap[lv] ?: continue
            var levelHpMatch = false
            for (ivSta in 0..15) {
                val calcHP = floor((stats.sta + ivSta) * cpm).toInt()
                if (calcHP != hp) continue
                hpPossible = true
                hpMatchCount++
                levelHpMatch = true
                if (pokemon.cp != null && pokemon.cp > 0) {
                    for (ivAtk in 0..15) {
                        for (ivDef in 0..15) {
                            val cp = calculateCP(stats.atk, stats.def, stats.sta, ivAtk, ivDef, ivSta, lv)
                            if (cp == pokemon.cp) {
                                cpPossible = true
                                break
                            }
                        }
                        if (cpPossible) break
                    }
                }
            }
            if (levelHpMatch) {
                minArcDiff = min(minArcDiff, abs(lv - estimatedLevelFromArc))
            }
            if (cpPossible && minArcDiff <= 0.5) break
        }

        if (!hpPossible) {
            return SpeciesFit(species, 0.0, false, false, Double.MAX_VALUE)
        }

        val hpScore = min(1.0, hpMatchCount / 6.0)
        val cpScore = when {
            pokemon.cp == null || pokemon.cp <= 0 -> 0.25
            cpPossible -> 1.0
            else -> 0.0
        }
        val arcScore = if (minArcDiff == Double.MAX_VALUE) 0.0 else (1.0 / (1.0 + minArcDiff)).coerceIn(0.0, 1.0)
        val stardustScore = if (stardust != null && stardustToLevel.containsKey(stardust)) 1.0 else 0.5
        val sizeScore = scorePhysicalProfile(pokemon, stats)
        val totalScore = (0.26 * hpScore + 0.32 * cpScore + 0.22 * arcScore + 0.08 * stardustScore + 0.12 * sizeScore).coerceIn(0.0, 1.0)

        return SpeciesFit(
            species = species,
            score = totalScore,
            hpPossible = true,
            cpPossible = cpPossible,
            minArcDiff = minArcDiff,
            sizeScore = sizeScore
        )
    }

    fun rankSpeciesByObservedProfile(pokemon: PokemonData, limit: Int = 12): List<SpeciesProfileCandidate> {
        return baseStats.entries.mapNotNull { (species, _) ->
            val fit = scoreSpeciesFit(pokemon, species)
            val total = (0.74 * fit.score + 0.26 * fit.sizeScore).coerceIn(0.0, 1.0)
            total.takeIf { it >= 0.40 }?.let { SpeciesProfileCandidate(species, it) }
        }.sortedByDescending { it.score }
            .take(limit)
    }

    fun rankSpeciesByPhysicalProfile(pokemon: PokemonData, limit: Int = 12): List<SpeciesProfileCandidate> {
        return baseStats.entries.mapNotNull { (species, stats) ->
            val sizeScore = scorePhysicalProfile(pokemon, stats)
            sizeScore.takeIf { it >= 0.58 }?.let { SpeciesProfileCandidate(species, it) }
        }.sortedByDescending { it.score }
            .take(limit)
    }

    private fun scorePhysicalProfile(pokemon: PokemonData, stats: BaseStats): Double {
        val heightScore = scoreRelativeMetric(pokemon.height?.toDouble(), stats.heightM)
        val weightScore = scoreRelativeMetric(pokemon.weight?.toDouble(), stats.weightKg)
        return when {
            heightScore == null && weightScore == null -> 0.5
            heightScore != null && weightScore != null -> (0.55 * heightScore + 0.45 * weightScore).coerceIn(0.0, 1.0)
            heightScore != null -> heightScore
            else -> weightScore ?: 0.5
        }
    }

    private fun scoreRelativeMetric(observed: Double?, expected: Double): Double? {
        if (observed == null || observed <= 0.0 || expected <= 0.0) return null
        val ratio = observed / expected
        val logDiff = abs(kotlin.math.ln(ratio))
        return (1.0 / (1.0 + (logDiff * 2.5))).coerceIn(0.0, 1.0)
    }

    private fun matchPlaceholder(cp: String, pattern: String): Boolean {
        val cleanPattern = pattern.replace(" ", "").replace("CP", "")
        if (cp.length != cleanPattern.length && !cleanPattern.contains("?")) return false
        
        // Ã–rn: ??80 -> Regex ..80
        val regexStr = cleanPattern.replace("?", ".")
        return try {
            Regex("^$regexStr$").matches(cp)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Primary entry point. Calculates a comprehensive rarity score.
     */
    fun calculate(
        pokemon: PokemonData,
        features: VisualFeatures,
        baseRarity: Int = 0,
        eventWeight: Int = 0,
        liveEventContext: LiveEventContext? = null
    ): RarityScore {
        return calculateRulesBased(pokemon, features, baseRarity, eventWeight, liveEventContext)
    }

    // â”€â”€ IV Analysis Logic â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun calculateRulesBased(
        pokemon: PokemonData,
        features: VisualFeatures,
        baseRarity: Int,
        eventWeight: Int,
        liveEventContext: LiveEventContext?
    ): RarityScore {
        val explanation = mutableListOf<String>()
        val breakdown = linkedMapOf<String, Int>()
        val axes = mutableListOf<RarityAxisScore>()
        val speciesName = pokemon.realName ?: pokemon.name ?: "Unknown"
        val fullMatch = pokemon.fullVariantMatch
        val variantSelection = lookupVariantCatalogEntry(pokemon)
        val variantEntry = variantSelection.entry
        val resolvedShiny = features.isShiny || (fullMatch?.resolvedShiny == true)
        val explanationCostume =
            features.hasCostume || (fullMatch?.resolvedCostume == true)
        val explanationForm =
            features.hasSpecialForm || (fullMatch?.resolvedForm == true)
        val resolvedExplanationMetadata = VariantExplanationMetadata.resolve(
            selection = variantSelection,
            fullMatch = fullMatch,
            authoritativeBySprite = authoritativeVariantBySprite,
            caughtDate = pokemon.caughtDate
        )
        val authoritativeFallback = AuthoritativeVariantEventFallback.resolve(
            finalSpecies = speciesName,
            caughtDate = pokemon.caughtDate,
            costumeLike = explanationCostume,
            shiny = resolvedShiny,
            bySpecies = authoritativeVariantBySpecies
        )
        val bulbapediaSpeciesFallback = BulbapediaSpeciesEventFallback.resolve(
            finalSpecies = speciesName,
            caughtDate = pokemon.caughtDate,
            costumeLike = explanationCostume,
            fullMatch = fullMatch,
            bySpecies = bulbapediaEventArchiveBySpecies
        )
        val authoritativeHistoricalMetadata =
            if (fullMatch?.explanationMode == "exact_authoritative") {
                AuthoritativeHistoricalEventResolver.resolve(
                    entry = variantSelection.entry?.spriteKey?.let(authoritativeVariantBySprite::get),
                    caughtDate = pokemon.caughtDate
                )
            } else {
                null
            }
        val shouldUseGlobalLegacyFallback =
            resolvedShiny ||
                explanationCostume ||
                explanationForm ||
                fullMatch?.resolvedVariantClass != "base"
        val globalLegacyEntry = if (shouldUseGlobalLegacyFallback) {
            lookupGlobalLegacyEntry(
                pokemon = pokemon,
                speciesName = speciesName,
                variantEntry = variantEntry
            )
        } else {
            null
        }
        val globalLegacyFallback = GlobalLegacyExplanationFallback.resolve(globalLegacyEntry)
        val rawExplanationVariantLabel =
            authoritativeHistoricalMetadata?.variantLabel
                ?: resolvedExplanationMetadata.variantLabel
                ?: bulbapediaSpeciesFallback?.variantLabel
                ?: authoritativeFallback?.variantLabel
                ?: globalLegacyEntry?.variantLabel
        val rawExplanationEventLabel =
            authoritativeHistoricalMetadata?.eventLabel
                ?: resolvedExplanationMetadata.eventLabel
                ?: bulbapediaSpeciesFallback?.eventLabel
                ?: authoritativeFallback?.eventLabel
                ?: globalLegacyFallback.eventLabel.takeIf { pokemon.caughtDate != null }
        val rawExplanationReleaseWindow =
            authoritativeHistoricalMetadata?.releaseWindow
                ?: resolvedExplanationMetadata.releaseWindow
                ?: bulbapediaSpeciesFallback?.releaseWindow
                ?: authoritativeFallback?.releaseWindow
                ?: globalLegacyFallback.releaseWindow.takeIf { pokemon.caughtDate != null }
        val sanitizedExplanation = VariantExplanationSanity.sanitize(
            caughtDate = pokemon.caughtDate,
            variantLabel = rawExplanationVariantLabel,
            eventLabel = rawExplanationEventLabel,
            releaseWindow = rawExplanationReleaseWindow
        )
        val explanationVariantLabel = sanitizedExplanation.variantLabel
        val explanationEventLabel = sanitizedExplanation.eventLabel
        val explanationReleaseWindow = sanitizedExplanation.releaseWindow
        val decisionSupport = buildDecisionSupport(
            pokemon = pokemon,
            rawEventLabel = rawExplanationEventLabel,
            rawReleaseWindow = rawExplanationReleaseWindow,
            sanitizedEventLabel = explanationEventLabel,
            sanitizedReleaseWindow = explanationReleaseWindow,
            liveEventContext = liveEventContext
        )

        android.util.Log.d(
            "RarityCalculator",
            "Rules-based rarity for $speciesName: shiny=${features.isShiny}, shadow=${features.isShadow}, lucky=${features.isLucky}, costume=${features.hasCostume}, form=${features.hasSpecialForm}, locationCard=${features.hasLocationCard}"
        )

        val rules = RarityRuleLoader.get(context)
        val resolvedBaseRarity = maxOf(RarityManifestLoader.getSpeciesRarity(speciesName), baseRarity)
        val baseScore = scaleBaseRarityToAxis(resolvedBaseRarity, rules.axisCaps.baseSpecies)
        val baseDetails = mutableListOf<String>()
        when {
            resolvedBaseRarity >= 20 -> baseDetails.add("Legendary or mythical base species")
            resolvedBaseRarity >= 12 -> baseDetails.add("Rare species family")
            resolvedBaseRarity >= 8 -> baseDetails.add("Uncommon collector species")
            else -> baseDetails.add("Base species rarity")
        }
        explanation.addAll(baseDetails)
        breakdown["Base Score"] = baseScore
        axes.add(RarityAxisScore("base", "Base Species", baseScore, rules.axisCaps.baseSpecies, baseDetails))

        val variantDetails = mutableListOf<String>()
        variantDetails.addAll(
            RarityExplanationFormatter.buildVariantReasons(
                species = speciesName,
                variantClass = variantEntry?.variantClass,
                isShiny = resolvedShiny,
                isCostumeLike = explanationCostume,
                variantLabel = explanationVariantLabel,
                primaryEventLabel = explanationEventLabel,
                eventTags = variantSelection.eventTagsOrEmpty(),
                releaseWindow = explanationReleaseWindow
            )
        )
        if (!explanationCostume && explanationForm && variantDetails.none { it.contains("Form:") || it.contains("Special form") }) {
            variantDetails.add(
                RarityExplanationFormatter.buildVariantReasons(
                    species = speciesName,
                    variantClass = "form",
                    isShiny = features.isShiny || (fullMatch?.resolvedShiny == true),
                    isCostumeLike = false,
                variantLabel = explanationVariantLabel,
                primaryEventLabel = explanationEventLabel,
                eventTags = variantSelection.eventTagsOrEmpty(),
                releaseWindow = explanationReleaseWindow
                ).firstOrNull() ?: com.pokerarity.scanner.data.model.encodeExplanationItem(
                    title = "Special form",
                    detail = "Classifier matched a non-base variant"
                )
            )
        }
        val activeSignals = mutableSetOf<String>()
        var variantScoreRaw = 0
        fun addVariantBonus(key: String, detail: String? = null) {
            val rule = rules.variantBonuses[key] ?: return
            activeSignals += key
            variantScoreRaw += rule.points
            variantDetails += com.pokerarity.scanner.data.model.encodeExplanationItem(
                title = rule.label,
                detail = detail ?: "+${rule.points} variant points"
            )
        }

        if (resolvedShiny) addVariantBonus("shiny")
        if (features.isShadow) addVariantBonus("shadow")
        if (features.isLucky) addVariantBonus("lucky")
        if (features.hasLocationCard) addVariantBonus("locationCard")
        if (explanationCostume) {
            addVariantBonus(
                key = "costume",
                detail = explanationEventLabel?.let { "Date-backed event: $it" }
                    ?: "Costume detected; exact event name requires caught-date evidence"
            )
        }
        if (explanationForm) addVariantBonus("form")
        if (features.isPurified) {
            val purifiedPoints = ((rules.variantBonuses["shadow"]?.points ?: 6) / 2).coerceAtLeast(1)
            activeSignals += "purified"
            variantScoreRaw += purifiedPoints
            variantDetails += com.pokerarity.scanner.data.model.encodeExplanationItem(
                title = "Purified form",
                detail = "+$purifiedPoints variant points"
            )
        }
        rules.combos
            .filter { combo -> combo.requires.all(activeSignals::contains) }
            .forEach { combo ->
                variantScoreRaw += combo.points
                variantDetails += com.pokerarity.scanner.data.model.encodeExplanationItem(
                    title = combo.label,
                    detail = "+${combo.points} combo points"
                )
            }

        val variantScore = variantScoreRaw.coerceIn(0, rules.axisCaps.variant)
        explanation.addAll(variantDetails)
        breakdown["Variant Score"] = variantScore
        axes.add(RarityAxisScore("variant", "Variant", variantScore, rules.axisCaps.variant, variantDetails))

        val ageDetails = mutableListOf<String>()
        val ageScore = calculateRulesAgeScore(pokemon.caughtDate, rules, ageDetails)
        explanation.addAll(ageDetails)
        breakdown["Age Score"] = ageScore
        axes.add(RarityAxisScore("age", "Age", ageScore, rules.axisCaps.age, ageDetails))

        val collectorDetails = mutableListOf<String>()
        val collectorScore = calculateCollectorScore(
            pokemon = pokemon,
            features = features,
            rules = rules,
            eventWeight = eventWeight,
            eventLabel = explanationEventLabel,
            details = collectorDetails
        )
        explanation.addAll(collectorDetails)
        breakdown["Collector Score"] = collectorScore
        axes.add(RarityAxisScore("collector", "Collector", collectorScore, rules.axisCaps.collector, collectorDetails))

        val totalScore = (baseScore + variantScore + ageScore + collectorScore).coerceIn(0, 100)
        val valueReasons = RarityExplanationFormatter.buildValueReasons(
            isShiny = resolvedShiny,
            isCostumeLike = explanationCostume,
            hasLocationCard = features.hasLocationCard,
            hasSpecialForm = explanationForm,
            variantLabel = explanationVariantLabel,
            eventLabel = explanationEventLabel,
            releaseWindow = explanationReleaseWindow,
            caughtDate = pokemon.caughtDate,
            totalScore = totalScore,
            baseScore = baseScore,
            variantScore = variantScore,
            ageScore = ageScore,
            collectorScore = collectorScore
        )
        return RarityScore(
            totalScore = totalScore,
            tier = determineRarityTier(totalScore),
            recognitionSummary = decisionSupport?.recognitionSummary,
            breakdown = breakdown,
            explanation = valueReasons.ifEmpty { listOf("No extra rarity signals detected") },
            axes = axes,
            confidence = calculateRarityConfidence(pokemon, features),
            decisionSupport = decisionSupport
        )
    }

    private fun buildDecisionSupport(
        pokemon: PokemonData,
        rawEventLabel: String?,
        rawReleaseWindow: ReleaseWindow?,
        sanitizedEventLabel: String?,
        sanitizedReleaseWindow: ReleaseWindow?,
        liveEventContext: LiveEventContext?
    ): ScanDecisionSupport {
        val isTurkish = Locale.getDefault().language.startsWith("tr", ignoreCase = true)
        val mismatchGuardActive =
            rawEventLabel != null &&
                sanitizedEventLabel == null &&
                pokemon.caughtDate != null
        val mismatchTitle = if (mismatchGuardActive) {
            if (isTurkish) "Event yakalanma tarihine takildi" else "Event blocked by catch date"
        } else {
            null
        }
        val mismatchDetail = if (mismatchGuardActive) {
            val caughtText = supportDateFormatter.format(pokemon.caughtDate!!)
            val windowText = rawReleaseWindow?.let(RarityExplanationFormatter::formatReleaseWindow)
                ?: rawEventLabel
                ?: "unknown event window"
            if (isTurkish) {
                "$caughtText tarihinde yakalanmis, ama bu bilgi $windowText ile uyusmuyor."
            } else {
                "Caught on $caughtText, but that does not fit $windowText."
            }
        } else {
            null
        }

        val eventLabel = liveEventContext?.eventName ?: sanitizedEventLabel
        val eventDetail = when {
            liveEventContext != null && isTurkish ->
                "${liveEventContext.boostedSpecies} icin canli etkinlik bonusu aktif (+${liveEventContext.eventBonusScore})."
            liveEventContext != null ->
                "Live event boost is active for ${liveEventContext.boostedSpecies} (+${liveEventContext.eventBonusScore})."
            !sanitizedEventLabel.isNullOrBlank() && sanitizedReleaseWindow != null ->
                sanitizedReleaseWindow.let(RarityExplanationFormatter::formatReleaseWindow)
            else -> null
        }

        val recognitionSummary = when {
            liveEventContext != null && isTurkish ->
                "${liveEventContext.boostedSpecies} icin canli etkinlik baglami aktif."
            liveEventContext != null ->
                "Live event context is active for ${liveEventContext.boostedSpecies}."
            !sanitizedEventLabel.isNullOrBlank() && isTurkish ->
                "$sanitizedEventLabel tarih destekli event metadata ile eslesti."
            !sanitizedEventLabel.isNullOrBlank() ->
                "$sanitizedEventLabel matched a date-backed event context."
            mismatchGuardActive && isTurkish ->
                "Event metadata yakalanma tarihiyle celistigi icin bastirildi."
            mismatchGuardActive ->
                "Event metadata was suppressed because it conflicts with the catch date."
            else -> null
        }

        return ScanDecisionSupport(
            eventConfidenceCode = when {
                liveEventContext != null -> "LIVE_EVENT"
                !sanitizedEventLabel.isNullOrBlank() -> "DATE_BACKED_EVENT"
                else -> ""
            },
            eventConfidenceLabel = eventLabel.orEmpty(),
            eventConfidenceDetail = eventDetail.orEmpty(),
            scanConfidenceScore = 0,
            scanConfidenceLabel = "",
            scanConfidenceDetail = "",
            mismatchGuardTitle = mismatchTitle,
            mismatchGuardDetail = mismatchDetail,
            recognitionSummary = recognitionSummary
        )
    }

    private fun lookupVariantCatalogEntry(
        pokemon: PokemonData
    ): VariantExplanationSelection {
        val finalSpecies = pokemon.realName ?: pokemon.name ?: return VariantExplanationSelection()
        return VariantCatalogSelection.selectForExplanation(
            finalSpecies = finalSpecies,
            fullMatch = pokemon.fullVariantMatch,
            bySprite = variantCatalogBySprite
        )
    }

    private fun lookupGlobalLegacyEntry(
        pokemon: PokemonData,
        speciesName: String,
        variantEntry: VariantCatalogEntry?
    ): GlobalRarityLegacyEntry? {
        val spriteKeys = buildList {
            add(pokemon.fullVariantMatch?.finalSpriteKey)
            add(variantEntry?.spriteKey)
        }.filterNotNull().filter { it.isNotBlank() }

        return spriteKeys
            .mapNotNull { globalLegacyBySprite[it] }
            .firstOrNull { it.species.equals(speciesName, ignoreCase = true) }
    }

    private fun GlobalRarityLegacyEntry.toReleaseWindow(): ReleaseWindow? {
        if (firstSeen.isNullOrBlank() && lastSeen.isNullOrBlank()) return null
        return ReleaseWindow(
            firstSeen = firstSeen,
            lastSeen = lastSeen
        )
    }

    private fun scaleBaseRarityToAxis(baseRarity: Int, axisCap: Int): Int {
        val manifestMax = 25.0
        return ((baseRarity.coerceIn(0, manifestMax.toInt()) / manifestMax) * axisCap)
            .roundToInt()
            .coerceIn(0, axisCap)
    }

    private fun calculateRulesAgeScore(
        caughtDate: Date?,
        rules: RarityRuleLoader.Rules,
        details: MutableList<String>
    ): Int {
        if (caughtDate == null) return 0
        val daysSinceCapture = ((Date().time - caughtDate.time) / DAY_MS).toInt()
        if (daysSinceCapture < 0) return 0
        val tier = rules.ageTiers.firstOrNull { daysSinceCapture >= it.minDays } ?: return 0
        val points = tier.points.coerceIn(0, rules.axisCaps.age)
        details += com.pokerarity.scanner.data.model.encodeExplanationItem(
            title = tier.label,
            detail = "${formatDateSimple(caughtDate)} catch date (+$points age points)"
        )
        return points
    }

    private fun calculateCollectorScore(
        pokemon: PokemonData,
        features: VisualFeatures,
        rules: RarityRuleLoader.Rules,
        eventWeight: Int,
        eventLabel: String?,
        details: MutableList<String>
    ): Int {
        var rawScore = 0
        fun addRule(rule: RarityRuleLoader.BonusRule) {
            rawScore += rule.points
            details += com.pokerarity.scanner.data.model.encodeExplanationItem(
                title = rule.label,
                detail = "+${rule.points} collector points"
            )
        }

        if (features.isXXL) addRule(rules.collector.xxl)
        if (features.isXXS) addRule(rules.collector.xxs)
        if (isRareGender(pokemon.realName ?: pokemon.name.orEmpty(), pokemon.gender)) {
            addRule(rules.collector.rareFemale)
        }

        val eventPoints = if (pokemon.caughtDate != null && eventWeight > 0) {
            (eventWeight * rules.collector.eventWeightScale)
                .roundToInt()
                .coerceIn(0, rules.collector.eventWeightCap)
        } else {
            0
        }
        if (eventPoints > 0) {
            val title = eventLabel?.takeIf { it.isNotBlank() } ?: rules.collector.eventLabel
            rawScore += eventPoints
            details += com.pokerarity.scanner.data.model.encodeExplanationItem(
                title = title,
                detail = "+$eventPoints date-backed collector points"
            )
        }

        return rawScore.coerceIn(0, rules.axisCaps.collector)
    }

    private fun calculateRarityConfidence(
        pokemon: PokemonData,
        features: VisualFeatures,
    ): Float {
        var score = 0.0
        if (!pokemon.realName.isNullOrBlank() || !pokemon.name.isNullOrBlank()) score += 0.30
        if ((pokemon.cp ?: 0) > 0) score += 0.20
        if ((pokemon.hp ?: 0) > 0) score += 0.10
        if (pokemon.caughtDate != null) score += 0.20
        val variantConfidence = if (
            features.isShiny ||
            features.isShadow ||
            features.isPurified ||
            features.isLucky ||
            features.hasSpecialForm ||
            features.hasCostume ||
            features.hasLocationCard
        ) features.confidence.toDouble().coerceIn(0.0, 1.0) else 1.0
        score += 0.20 * variantConfidence
        return score.coerceIn(0.0, 1.0).toFloat()
    }

    data class IVResult(
        val bonusPoints: Int,
        val rangeText: String?,
        val explanation: String?,
        val solveDetails: IvSolveDetails?
    )

    private fun analyzeIV(pokemon: PokemonData, features: VisualFeatures): IVResult {
        return IVResult(0, null, null, null)
    }

    private data class IVSearchResult(val ivSums: List<Int>)

    private fun runIVSearch(
        pokemon: PokemonData,
        stats: BaseStats,
        hp: Int,
        arc: Float,
        stardust: Int?
    ): IVSearchResult {
        val estimatedLevelFromArc = (arc * 49.0 + 1.0)
        val normalizedArcLevel = estimatedLevelFromArc.coerceIn(1.0, 50.0)

        // Stardust varsa onu kullan. Yoksa ilk passta arc merkezli dar bir pencere tara.
        val levelRange = if (stardust != null && stardustToLevel.containsKey(stardust)) {
            stardustToLevel[stardust]!!
        } else {
            max(1.0, normalizedArcLevel - 3.0)..min(50.0, normalizedArcLevel + 3.0)
        }
        
        val ivSums = mutableListOf<Int>()
        
        // Strateji: Ã–nce Stardust aralÄ±ÄŸÄ±ndaki seviyeleri tara.
        // EÄŸer Stardust varsa, Arc Level sadece bu aralÄ±ktaki en yakÄ±n yarÄ±m seviyeyi seÃ§mek iÃ§in bir ipucudur.
        for (lv in generateSequence(levelRange.start) { it + 0.5 }.takeWhile { it <= levelRange.endInclusive }) {
            val cpm = cpmMap[lv] ?: continue
            
            // HP KontrolÃ¼ (IV_Sta 0-15 arasÄ±)
            for (ivSta in 0..15) {
                val calcHP = floor((stats.sta + ivSta) * cpm).toInt()
                if (calcHP == hp) {
                    // Bu seviyede bu HP mÃ¼mkÃ¼n! Åžimdi CP kombinasyonlarÄ±nÄ± tara.
                    for (ivAtk in 0..15) {
                        for (ivDef in 0..15) {
                            val cp = calculateCP(stats.atk, stats.def, stats.sta, ivAtk, ivDef, ivSta, lv)
                            // EÄŸer OCR CP'si varsa onunla tam eÅŸleÅŸmeli
                            if (pokemon.cp == null || pokemon.cp == 0 || pokemon.cp == cp) {
                                ivSums.add(ivAtk + ivDef + ivSta)
                            }
                        }
                    }
                }
            }
        }

        // İlk passta aday bulunamadıysa arc çevresinde daha geniş bir pencere tara.
        if (ivSums.isEmpty()) {
            val retryWindow = if (stardust != null) 5.0 else 8.0
            android.util.Log.w(
                "RarityCalculator",
                "Primary IV range $levelRange failed for HP $hp (stardust=$stardust, arc=$arc). Retrying around arc level $normalizedArcLevel +/- $retryWindow..."
            )
            for (lv in generateSequence(1.0) { it + 0.5 }.takeWhile { it <= 50.0 }) {
                val cpm = cpmMap[lv] ?: continue
                
                if (abs(lv - normalizedArcLevel) > retryWindow) continue
                
                for (ivSta in 0..15) {
                    val calcHP = floor((stats.sta + ivSta) * cpm).toInt()
                    if (calcHP == hp) {
                        for (ivAtk in 0..15) {
                            for (ivDef in 0..15) {
                                val cp = calculateCP(stats.atk, stats.def, stats.sta, ivAtk, ivDef, ivSta, lv)
                                if (pokemon.cp == null || pokemon.cp == 0 || pokemon.cp == cp) {
                                    ivSums.add(ivAtk + ivDef + ivSta)
                                }
                            }
                        }
                    }
                }
            }
        }

        android.util.Log.d(
            "RarityCalculator",
            "IV search completed for ${pokemon.realName ?: pokemon.name}: candidates=${ivSums.size}, stardust=$stardust, arc=$arc, primaryRange=$levelRange"
        )

        return IVSearchResult(ivSums)
    }

    private fun isRareGender(species: String, gender: String?): Boolean {
        if (gender != "Female") return false
        val rareFemaleSpecies = listOf("Combee", "Salandit", "Vespiquen", "Salazzle")
        return rareFemaleSpecies.contains(species)
    }

    // â”€â”€ Age Bonus â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun calculateAgeBonus(caughtDate: Date?, explanation: MutableList<String>): Int {
        if (caughtDate == null) return 0

        val daysSinceCapture = ((Date().time - caughtDate.time) / (1000L * 60 * 60 * 24)).toInt()
        if (daysSinceCapture < 0) return 0 // Future date protection

        val points = RarityManifestLoader.getAgeBonusPoints(daysSinceCapture)
        if (points > 0) {
            val label = RarityManifestLoader.getAgeBonusLabel(daysSinceCapture)
            explanation.add("ðŸ“… $label â€” ${formatDateSimple(caughtDate)} (+$points)")
        }
        return points
    }

    // â”€â”€ Tier Logic â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun determineRarityTier(score: Int): RarityTier {
        return RarityTier.fromScore(score)
    }

    // â”€â”€ Utility â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun formatDateSimple(date: Date?): String {
        if (date == null) return "Unknown"
        val format = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        return format.format(date)
    }

}
