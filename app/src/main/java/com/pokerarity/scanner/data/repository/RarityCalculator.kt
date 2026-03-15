package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VisualFeatures
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Calculates rarity scores for Pokemon based on multiple weighted factors.
 *
 * Score breakdown (0–100):
 *   • Base Species Rarity : 0-25  (from rarity_manifest.json)
 *   • Shiny Bonus         : 0-20  (based on shiny detection)
 *   • Costume Bonus       : 0-15  (based on costume rarity tier)
 *   • Form Bonus          : 0-10  (shadow / lucky / purified)
 *   • Age Bonus           : 0-30  (days since capture)
 *
 * Total is capped at 100.
 */
class RarityCalculator(private val context: android.content.Context) {

    private val baseStats: Map<String, BaseStats> by lazy { loadBaseStats() }

    data class BaseStats(val atk: Int, val def: Int, val sta: Int)

    private fun loadBaseStats(): Map<String, BaseStats> {
        return try {
            val jsonString = context.assets.open("data/pokemon_base_stats.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val map = mutableMapOf<String, BaseStats>()
            json.keys().forEach { key ->
                val obj = json.getJSONObject(key)
                map[key] = BaseStats(obj.getInt("atk"), obj.getInt("def"), obj.getInt("sta"))
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
     * Resmi Pokemon GO CP Formülü
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
     * OCR'dan gelen tüm olası CP adaylarını (OCR'ın gürültülü okuduğu her şey) dikkate alır.
     */
    fun validateAndFixCP(pokemon: PokemonData, allOcrCPs: List<Int> = emptyList(), features: VisualFeatures? = null): Int? {
        val species = pokemon.realName ?: pokemon.name ?: return pokemon.cp
        val stats = baseStats[species] ?: return pokemon.cp
        val hp = pokemon.hp ?: return pokemon.cp
        val arc = pokemon.arcLevel ?: return pokemon.cp
        var stardust = pokemon.stardust

        // Shadow/Lucky/Purified Stardust düzeltmesi
        // Stardust-Level tablomuz "Regular" Pokemonlar içindir.
        // Eğer Pokemon Shadow ise, okunan stardust 1.2x'tir. Normal değerine çevirelim.
        if (stardust != null) {
            stardust = when {
                features?.isLucky == true -> (stardust * 2.0).toInt() // 0.5x -> 1.0x
                features?.isShadow == true -> (stardust / 1.2).toInt() // 1.2x -> 1.0x
                else -> stardust
            }
        }

        android.util.Log.d("RarityCalculator", "Deep Validation for $species (HP:$hp, Arc:$arc, Stardust:$stardust, OCR_CP:${pokemon.cp}, AllOCR:$allOcrCPs)")

        // 1. Level Aralığını Belirle
        val levelRange = if (stardust != null && stardustToLevel.containsKey(stardust)) {
            stardustToLevel[stardust]!!
        } else {
            1.0..50.0
        }

        // 2. Arc Level'dan tahmin edilen seviye
        val estimatedLevelFromArc = (arc * 49.0 + 1.0)

        // 3. Arama Döngüsü
        val candidates = mutableListOf<Triple<Int, Double, Int>>() // Triple(CP, LevelDifference, IV_Sum)
        
        for (lv in generateSequence(levelRange.start) { it + 0.5 }.takeWhile { it <= levelRange.endInclusive }) {
            val cpm = cpmMap[lv] ?: continue
            
            // Bu seviyede bu HP mümkün mü?
            for (ivSta in 0..15) {
                val calculatedHP = floor((stats.sta + ivSta) * cpm).toInt()
                if (calculatedHP == hp) {
                    // HP eşleşti, bu seviyedeki tüm CP olasılıklarını tara
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

        // 4. En Mantıklı Adayı Seç
        
        // ÖNCELİK 1: OCR'dan gelen bir CP adayı matematiksel olarak MÜMKÜN mü?
        val validOcrMatch = allOcrCPs.find { ocrVal -> candidates.any { it.first == ocrVal } }
        if (validOcrMatch != null) {
            android.util.Log.i("RarityCalculator", "Trusting OCR candidate: $validOcrMatch (Confirmed by HP)")
            return validOcrMatch
        }

        // ÖNCELİK 2: Mevcut OCR sonucunu kontrol et
        val currentCpPossible = pokemon.cp != null && candidates.any { it.first == pokemon.cp }
        if (currentCpPossible) {
            android.util.Log.d("RarityCalculator", "Current CP ${pokemon.cp} is valid.")
            return pokemon.cp
        }

        // ÖNCELİK 3: Arc Level'a en yakın olan aday grubunu bul
        // Arc Level 1.0 ise (Full) genelde Level 40 veya 50 demektir.
        val closeToArc = candidates.filter { it.second < 1.0 }
        val sourceList = if (closeToArc.isNotEmpty()) closeToArc else candidates
        
        // Arc'a en yakın ve ortalama IV'ye (22.5) en yakın olanı seç
        val best = sourceList.minWithOrNull(compareBy({ it.second }, { abs(it.third.toDouble() - 22.5) }))?.first
        
        android.util.Log.d("RarityCalculator", "Mathematical CP Estimate: $best (Candidates: ${candidates.size}, NearArc: ${closeToArc.size})")
        return best
    }

    private fun matchPlaceholder(cp: String, pattern: String): Boolean {
        val cleanPattern = pattern.replace(" ", "").replace("CP", "")
        if (cp.length != cleanPattern.length && !cleanPattern.contains("?")) return false
        
        // Örn: ??80 -> Regex ..80
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
        eventWeight: Int = 0
    ): RarityScore {
        val breakdown = mutableMapOf<String, Int>()
        val explanation = mutableListOf<String>()
        val speciesName = pokemon.realName ?: pokemon.name ?: "Unknown"

        // ──────────────────────────────────
        // 0. IV Analysis (0-30)
        // ──────────────────────────────────
        val ivResult = analyzeIV(pokemon, features)
        val ivScore = ivResult.bonusPoints.coerceIn(0, 30)
        breakdown["IV"] = ivScore
        if (ivResult.explanation != null) explanation.add(ivResult.explanation)

        // ──────────────────────────────────
        // 1. Base Species Rarity (0-20)
        // ──────────────────────────────────
        val manifestRarity = RarityManifestLoader.getSpeciesRarity(speciesName)
        val baseScore = (maxOf(manifestRarity, baseRarity) * 0.8).toInt().coerceIn(0, 20)
        breakdown["Base"] = baseScore
        when {
            baseScore >= 15 -> explanation.add("🌟 Legendary/Mythical species")
            baseScore >= 10 -> explanation.add("🔶 Very Rare species")
            baseScore >= 5  -> explanation.add("🔹 Uncommon species")
        }

        // ──────────────────────────────────
        // 2. Visual Features (Shiny Multiplier, Form Bonus)
        // ──────────────────────────────────
        // Shiny: x1.3 multiplier (previously 1.5)
        val shinyMultiplier = if (features.isShiny) 1.3 else 1.0
        
        var formScore = 0
        if (features.isShadow) {
            formScore += 5
            explanation.add("🌑 Shadow Form (+5)")
        }
        if (features.isLucky) {
            formScore += 10
            explanation.add("🍀 Lucky Pokemon (+10)")
        }
        if (features.hasLocationCard) {
            formScore += 15
            explanation.add("🗺️ Special Location Card (+15)")
        }
        formScore = formScore.coerceAtMost(20)
        breakdown["VisualExtra"] = formScore

        // ──────────────────────────────────
        // 3. Size & Weight (XXS/XXL)
        // ──────────────────────────────────
        var sizeScore = 0
        if (features.isXXL || features.isXXS) {
            sizeScore = 15
            val label = if (features.isXXL) "XXL (Huge)" else "XXS (Tiny)"
            explanation.add("📏 $label Size (+15)")
        }
        breakdown["Size"] = sizeScore

        // ──────────────────────────────────
        // 4. Age Bonus (0-15)
        // ──────────────────────────────────
        val rawAgeScore = calculateAgeBonus(pokemon.caughtDate, explanation)
        val ageScore = (rawAgeScore * 0.5).toInt().coerceIn(0, 15) // Scale down age bonus
        breakdown["Age"] = ageScore

        // ──────────────────────────────────
        // 5. Costume & Event (0-10)
        // ──────────────────────────────────
        val costumeScore = if (features.hasCostume) 5 else 0
        if (costumeScore > 0) explanation.add("👗 Costume variant (+5)")
        
        val eventScore = (eventWeight / 2).coerceIn(0, 5)
        if (eventScore > 0) explanation.add("🎉 Event capture (+$eventScore)")
        
        breakdown["Misc"] = costumeScore + eventScore

        // ── Final Score Calculation ─────────────────────────────────────
        val additiveSum = baseScore + ivScore + formScore + sizeScore + ageScore + costumeScore + eventScore
        val totalWithShiny = (additiveSum * shinyMultiplier).toInt()
        
        if (features.isShiny) {
            explanation.add("✨ Shiny variant (x1.3 bonus)")
            breakdown["ShinyBonus"] = (totalWithShiny - additiveSum)
        }
        
        val totalScore = totalWithShiny.coerceIn(0, 100)
        val tier = determineRarityTier(totalScore)

        return RarityScore(
            totalScore = totalScore,
            tier = tier,
            ivEstimate = ivResult.rangeText,
            breakdown = breakdown,
            explanation = explanation
        )
    }

    // ── IV Analysis Logic ──────────────────────────────────────────────

    data class IVResult(val bonusPoints: Int, val rangeText: String?, val explanation: String?)

    private fun analyzeIV(pokemon: PokemonData, features: VisualFeatures): IVResult {
        val species = pokemon.realName ?: pokemon.name ?: return IVResult(0, null, null)
        val stats = baseStats[species] ?: return IVResult(0, null, null)
        val hp = pokemon.hp ?: return IVResult(0, null, null)
        val arc = pokemon.arcLevel ?: return IVResult(0, null, null)
        var stardust = pokemon.stardust
        
        // Shadow/Lucky Stardust correction
        if (stardust != null) {
            stardust = when {
                features.isLucky -> (stardust * 2.0).toInt()
                features.isShadow -> (stardust / 1.2).toInt()
                else -> stardust
            }
        }

        // Try with Stardust first
        var result = runIVSearch(pokemon, features, stats, hp, arc, stardust)
        
        // If no match, try WITHOUT Stardust (it might be read incorrectly from noise)
        if (result.ivSums.isEmpty() && stardust != null) {
            android.util.Log.w("RarityCalculator", "No IV candidates with Stardust $stardust, retrying with Arc only...")
            result = runIVSearch(pokemon, features, stats, hp, arc, null)
        }

        if (result.ivSums.isEmpty()) return IVResult(0, "???", null)

        val minIV = result.ivSums.minOrNull()!!
        val maxIV = result.ivSums.maxOrNull()!!
        val minPct = (minIV * 100 / 45)
        val maxPct = (maxIV * 100 / 45)
        
        val rangeText = if (minPct == maxPct) "$minPct%" else "$minPct% - $maxPct%"
        
        var bonus = 0
        var explanation: String? = null
        
        when {
            minIV == 45 -> {
                bonus = 30
                explanation = "💯 Hundo (Perfect IVs) (+30)"
            }
            maxIV == 0 -> {
                bonus = 30
                explanation = "💀 Nundo (0% IVs) (+30)"
            }
            minPct >= 96 -> {
                bonus = 20
                explanation = "💎 Near Perfect IVs (+20)"
            }
            minPct >= 90 -> {
                bonus = 15
                explanation = "📈 Elite IVs (+15)"
            }
            minPct >= 80 -> {
                bonus = 5
                explanation = "💪 Strong IVs (+5)"
            }
        }
        
        return IVResult(bonus, rangeText, explanation)
    }

    private data class IVSearchResult(val ivSums: List<Int>)

    private fun runIVSearch(
        pokemon: PokemonData,
        features: VisualFeatures,
        stats: BaseStats,
        hp: Int,
        arc: Float,
        stardust: Int?
    ): IVSearchResult {
        // Pokemon GO Seviye Mantığı: 
        // Seviye 1'den 50'ye kadar. Toplam 49 aralık.
        // Arc Level (0.0 - 1.0) bu aralığın neresinde olduğumuzu söyler.
        val estimatedLevelFromArc = (arc * 49.0 + 1.0)
        
        // Stardust'a göre seviye aralığını bul
        val levelRange = if (stardust != null && stardustToLevel.containsKey(stardust)) {
            stardustToLevel[stardust]!!
        } else {
            1.0..50.0
        }
        
        val ivSums = mutableListOf<Int>()
        
        // Strateji: Önce Stardust aralığındaki seviyeleri tara.
        // Eğer Stardust varsa, Arc Level sadece bu aralıktaki en yakın yarım seviyeyi seçmek için bir ipucudur.
        for (lv in generateSequence(levelRange.start) { it + 0.5 }.takeWhile { it <= levelRange.endInclusive }) {
            val cpm = cpmMap[lv] ?: continue
            
            // HP Kontrolü (IV_Sta 0-15 arası)
            for (ivSta in 0..15) {
                val calcHP = floor((stats.sta + ivSta) * cpm).toInt()
                if (calcHP == hp) {
                    // Bu seviyede bu HP mümkün! Şimdi CP kombinasyonlarını tara.
                    for (ivAtk in 0..15) {
                        for (ivDef in 0..15) {
                            val cp = calculateCP(stats.atk, stats.def, stats.sta, ivAtk, ivDef, ivSta, lv)
                            // Eğer OCR CP'si varsa onunla tam eşleşmeli
                            if (pokemon.cp == null || pokemon.cp == 0 || pokemon.cp == cp) {
                                ivSums.add(ivAtk + ivDef + ivSta)
                            }
                        }
                    }
                }
            }
        }

        // Eğer Stardust aralığında hiçbir aday bulunamadıysa (OCR hatası olabilir), 
        // Stardust kısıtlamasını kaldırıp sadece Arc Level çevresinde geniş bir arama yap.
        if (ivSums.isEmpty() && stardust != null) {
            android.util.Log.w("RarityCalculator", "Stardust range $levelRange failed for HP $hp. Retrying with Arc only...")
            for (lv in generateSequence(1.0) { it + 0.5 }.takeWhile { it <= 50.0 }) {
                val cpm = cpmMap[lv] ?: continue
                
                // Arc'a çok uzak seviyeleri ele (±5 seviye tolerans)
                if (abs(lv - estimatedLevelFromArc) > 5.0) continue
                
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

        return IVSearchResult(ivSums)
    }

    private fun isRareGender(species: String, gender: String?): Boolean {
        if (gender != "Female") return false
        val rareFemaleSpecies = listOf("Combee", "Salandit", "Vespiquen", "Salazzle")
        return rareFemaleSpecies.contains(species)
    }

    // ── Age Bonus ───────────────────────────────────────────────────────

    private fun calculateAgeBonus(caughtDate: Date?, explanation: MutableList<String>): Int {
        if (caughtDate == null) return 0

        val daysSinceCapture = ((Date().time - caughtDate.time) / (1000L * 60 * 60 * 24)).toInt()
        if (daysSinceCapture < 0) return 0 // Future date protection

        val points = RarityManifestLoader.getAgeBonusPoints(daysSinceCapture)
        if (points > 0) {
            val label = RarityManifestLoader.getAgeBonusLabel(daysSinceCapture)
            explanation.add("📅 $label — ${formatDateSimple(caughtDate)} (+$points)")
        }
        return points
    }

    // ── Tier Logic ──────────────────────────────────────────────────────

    private fun determineRarityTier(score: Int): RarityTier {
        return when {
            score >= 90 -> RarityTier.MYTHICAL
            score >= 75 -> RarityTier.LEGENDARY
            score >= 50 -> RarityTier.EPIC
            score >= 30 -> RarityTier.RARE
            score >= 15 -> RarityTier.UNCOMMON
            else        -> RarityTier.COMMON
        }
    }

    // ── Utility ─────────────────────────────────────────────────────────

    private fun formatDateSimple(date: Date?): String {
        if (date == null) return "Unknown"
        val format = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        return format.format(date)
    }
}
