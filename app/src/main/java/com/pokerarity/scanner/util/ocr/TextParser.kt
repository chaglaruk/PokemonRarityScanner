package com.pokerarity.scanner.util.ocr

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.util.Date
import kotlin.math.abs
import kotlin.math.min

class TextParser(context: Context) {
    private val pokemonNames: List<String> = loadPokemonNames(context)

    fun parseCP(text: String): Int? {
        if (text.isBlank()) return null
        
        val clean = text.uppercase().replace("O", "0").replace("I", "1").replace("S", "5").replace("B", "8")

        // Strateji 1: "CP" kelimesinden sonraki 3-4 haneli sayıyı ara
        val cpMatch = Regex("""CP\s*(\d{3,4})""").find(clean)
        if (cpMatch != null) return cpMatch.groupValues[1].toIntOrNull()
        
        // Strateji 2: Metindeki tüm sayıları birleştir ve içindeki en mantıklı CP'yi (3-4 hane) bul
        val allDigits = clean.replace(Regex("[^0-9]"), "")
        if (allDigits.length in 3..4) {
            val num = allDigits.toIntOrNull()
            if (num != null && num in 100..5500) return num
        }
        
        // Strateji 3: Çok gürültülü metinlerde (Dragonite kanadı vb.) 3-4 haneli herhangi bir sayıyı ara
        // Rakamlar arasına giren gürültüleri temizle (Örn: 2 705 -> 2705)
        val matches = Regex("""(\d[\d\s]{1,5}\d)""").findAll(clean)
        for (m in matches) {
            val v = m.groupValues[1].replace(" ", "").toIntOrNull() ?: continue
            if (v in 100..5500 && v !in 2016..2026) return v
        }
        
        return null
    }

    fun parseHPPair(text: String): Pair<Int, Int>? {
        if (text.isBlank()) return null
        val upper = text.uppercase().replace("O", "0").replace("I", "1").replace("S", "5")
        
        // Strateji 1: Klasik "123/123" formatı
        val m1 = Regex("""(\d{1,3})\s*/\s*(\d{1,3})""").find(upper)
        if (m1 != null) {
            val cur = m1.groupValues[1].toIntOrNull()
            val max = m1.groupValues[2].toIntOrNull()
            if (cur != null && max != null && cur in 1..999 && max in 1..999 && cur <= max)
                return Pair(cur, max)
        }

        // Strateji 2: Bölü işareti yerine gürültü gelmiş olabilir (Örn: 1222152 HP)
        // Genelde HP değerleri birbirine yakındır veya aynıdır.
        val allDigits = upper.replace(Regex("[^0-9]"), "")
        if (allDigits.length in 4..6) {
            // Ortadan ikiye bölmeyi dene
            val mid = allDigits.length / 2
            val first = allDigits.substring(0, mid).toIntOrNull()
            val second = allDigits.substring(mid).toIntOrNull()
            if (first != null && second != null && first in 10..999 && second in 10..999 && first <= second) {
                return Pair(first, second)
            }
        }

        // Strateji 3: "HP 123" formatı
        val m2 = Regex("""H\s*P\s*(\d{1,3})""").find(upper)
        if (m2 != null) {
            val v = m2.groupValues[1].toIntOrNull()
            if (v != null && v in 1..999) return Pair(v, v)
        }

        // Strateji 4: Herhangi bir 2-3 haneli sayı
        val matches = Regex("""\b(\d{2,3})\b""").findAll(upper)
        for (m in matches) {
            val v = m.groupValues[1].toIntOrNull() ?: continue
            if (v in 10..500) return Pair(v, v)
        }

        return null
    }

    fun parseDate(allText: String): java.util.Date? {
        if (allText.isBlank()) return null
        
        // Sadece rakam ve ayraçları al
        val clean = allText.replace(Regex("[^0-9/\\.]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        android.util.Log.d("TextParser", "parseDate clean: '$clean'")

        // Yılı bul (2016-2026)
        val yearMatch = Regex("""\b(201[6-9]|202[0-6])\b""").find(clean)
        if (yearMatch == null) return null
        val year = yearMatch.groupValues[1].toInt()
        
        // Ayracı olan bir ikili ara (Örn: 22/03 veya 03.22)
        val sepMatch = Regex("""(\d{1,2})[/.](\d{1,2})""").find(clean)
        if (sepMatch != null) {
            val v1 = sepMatch.groupValues[1].toInt()
            val v2 = sepMatch.groupValues[2].toInt()
            // Mantıklı değerler mi? (Gün 1-31, Ay 1-12)
            if ((v1 in 1..31 && v2 in 1..12) || (v1 in 1..12 && v2 in 1..31)) {
                val day = if (v1 > 12) v1 else v2
                val mon = if (v1 > 12) v2 else v1
                return makeDate(year, mon - 1, day)
            }
        }

        // Fallback: Yıl dışındaki diğer 1-2 haneli sayıları topla
        val rest = clean.replace(yearMatch.groupValues[1], " ").trim()
        val digits = Regex("""\b(\d{1,2})\b""").findAll(rest)
            .map { it.value.toInt() }
            .filter { it in 1..31 } 
            .toList()
        
        if (digits.size >= 2) {
            val v1 = digits[0]
            val v2 = digits[1]
            if ((v1 in 1..31 && v2 in 1..12) || (v1 in 1..12 && v2 in 1..31)) {
                val day = if (v1 > 12) v1 else v2
                val mon = if (v1 > 12) v2 else v1
                return makeDate(year, mon - 1, day)
            }
        } else if (digits.size == 1) {
             val v = digits[0]
             if (v in 1..12) return makeDate(year, v - 1, 1)
        }

        return null // Sadece yıl varsa null dön ki Bottom bölgesine baksın
    }

    private fun makeDate(year: Int, month: Int, day: Int): Date {
        val cal = java.util.Calendar.getInstance()
        cal.set(year.coerceIn(2016,2026), month.coerceIn(0,11), day.coerceIn(1,31), 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.time
    }

    fun parseBottomDate(text: String): java.util.Date? {
        if (text.isBlank()) return null
        val clean = text.lowercase().trim()
        
        // Format: "London, United Kingdom 22/03/2023" veya "March 22, 2023"
        // Sadece rakamsal olana odaklanalim (Oyun ingilizce ama rakam daha stabil)
        val m = Regex("""(\d{1,2})[/\s\.](\d{1,2})[/\s\.]\b(201[6-9]|202[0-6])\b""").find(clean)
        if (m != null) {
            val v1 = m.groupValues[1].toInt()
            val v2 = m.groupValues[2].toInt()
            val year = m.groupValues[3].toInt()
            val day = if (v1 > 12) v1 else v2
            val mon = if (v1 > 12) v2 else v1
            if (mon in 1..12 && day in 1..31) return makeDate(year, mon - 1, day)
        }
        
        // İngilizce Ay İsimleri Fallback (Sadece Bottom için)
        val monthMap = mapOf("january" to 0,"jan" to 0,"february" to 1,"feb" to 1,
            "march" to 2,"mar" to 2,"april" to 3,"apr" to 3,"may" to 4,
            "june" to 5,"jun" to 5,"july" to 6,"jul" to 6,"august" to 7,"aug" to 7,
            "september" to 8,"sep" to 8,"october" to 9,"oct" to 9,
            "november" to 10,"nov" to 10,"december" to 11,"dec" to 11)
        
        for ((name, idx) in monthMap) {
            if (clean.contains(name)) {
                val yearMatch = Regex("""\b(201[6-9]|202[0-6])\b""").find(clean)
                val dayMatch = Regex("""\b(\d{1,2})\b""").find(clean.replace(name, ""))
                if (yearMatch != null && dayMatch != null) {
                    return makeDate(yearMatch.groupValues[1].toInt(), idx, dayMatch.groupValues[1].toInt())
                }
            }
        }
        
        return null
    }

    fun parseCandyName(text: String): String? {
        if (text.isBlank()) return null
        val upper = text.uppercase().trim()
        // Esnek CANDY regex: CANDY, CNDY, CANOY, CANDYXL vb.
        val m = Regex("""([A-Z][A-Z\s\-]{1,20}?)\s+(?:CANDY|CNDY|CANOY|CAN[D0]Y|CANY)""").find(upper)
        if (m != null) {
            val raw = m.groupValues[1].trim()
            parseName(raw)?.let {
                android.util.Log.d("TextParser", "CandyName '$raw' -> '$it'")
                return it
            }
        }
        return null
    }





    fun parseNameFromFullText(fullText: String): String? {
        if (fullText.isBlank()) return null
        val blocked = Regex("CP|HP|STARDUST|CANDY|POWER|EVOLVE|MEGA|WEIGHT|HEIGHT|NORMAL|TRAINER|BATTLES|GYMS|RAIDS|XL|XS|PURIFIED|SHADOW|LUCKY|TRANSFER|FAVORITE|APPRAISE|WEATHER|BONUS|BUDDY|EGGS", RegexOption.IGNORE_CASE)
        for (line in fullText.split("\n").map { it.trim() }.filter { it.length >= 3 }) {
            if (blocked.containsMatchIn(line)) continue
            if (line.contains("/") || line.contains("kg", true)) continue
            val clean = line.replace(Regex("\\d+$"), "").trim()
            if (clean.length >= 3) parseName(clean)?.let { return it }
            parseName(line)?.let { return it }
        }
        return null
    }

    fun parseName(ocrText: String): String? {
        if (ocrText.isBlank()) return null
        val clean = ocrText.replace(Regex("[^A-Za-z\\s\\-\\.]"), "").trim().lowercase()
        if (clean.length < 3) return null
        
        // Exact match check
        pokemonNames.find { it == clean }?.let { return it.replaceFirstChar { c -> c.uppercase() } }
        
        // Token based search
        for (token in clean.split(Regex("\\s+"))) {
            if (token.length < 3) continue
            pokemonNames.find { it == token }?.let { return it.replaceFirstChar { c -> c.uppercase() } }
            
            // Fuzzy match for token
            var tb: String? = null; var td = Int.MAX_VALUE
            for (name in pokemonNames) {
                // Uzunluk farkı toleransı artırıldı (Örn: Esneonloo(9) vs Espeon(6))
                if (abs(name.length - token.length) > 3) continue
                val d = levenshtein(token, name)
                val maxD = when {
                    name.length <= 4 -> 1 
                    name.length <= 6 -> 3 
                    name.length <= 9 -> 4 
                    else -> 5
                }
                if (d < td && d <= maxD) { td = d; tb = name }
            }
            tb?.let { 
                android.util.Log.d("TextParser", "Token match: '$token' -> '$it' (d=$td)")
                return it.replaceFirstChar { c -> c.uppercase() } 
            }
        }
        
        // Global fuzzy fallback
        var best: String? = null; var bd = Int.MAX_VALUE
        for (name in pokemonNames) {
            if (abs(name.length - clean.length) > 5) continue
            val d = levenshtein(clean, name)
            val maxD = when {
                name.length <= 5 -> 2
                name.length <= 8 -> 4
                else -> 5
            }
            if (d < bd && d <= maxD) { bd = d; best = name }
        }
        
        // Log clean text if no match found
        if (best == null) {
            android.util.Log.d("TextParser", "No name match for clean text: '$clean'")
        }
        
        best?.let { android.util.Log.d("TextParser", "Global match: '$clean' -> '$it' (d=$bd)") }
        return best?.replaceFirstChar { it.uppercase() }
    }

    fun parseMegaEnergy(text: String): Int? =
        Regex("""(\d+)\s*MEGA\s*ENERGY""").find(text.uppercase())?.groupValues?.get(1)?.toIntOrNull()

    fun parseWeight(text: String): Float? =
        Regex("""([0-9]+(?:\.[0-9]+)?)\s*kg""").find(text.lowercase())?.groupValues?.get(1)?.toFloatOrNull()

    fun parseHeight(text: String): Float? =
        Regex("""([0-9]+(?:\.[0-9]+)?)\s*m\b""").find(text.lowercase())?.groupValues?.get(1)?.toFloatOrNull()

    fun parseGender(text: String): String? {
        val clean = text.lowercase()
        return when {
            clean.contains("♂") || clean.contains("(m)") -> "Male"
            clean.contains("♀") || clean.contains("(f)") -> "Female"
            // OCR sometimes reads ♂ as 'o' or '6' and ♀ as 'p' or '9' near the name
            else -> null
        }
    }

    fun parseSizeTag(text: String): String? {
        val upper = text.uppercase()
        return when {
            upper.contains("XXL") -> "XXL"
            upper.contains("XXS") -> "XXS"
            upper.contains(" XL ") || upper.startsWith("XL ") || upper.endsWith(" XL") -> "XL"
            upper.contains(" XS ") || upper.startsWith("XS ") || upper.endsWith(" XS") -> "XS"
            else -> null
        }
    }

    /**
     * Power Up (Güçlendirme) maliyetini ayrıştırır.
     * OCR gürültüsünü temizlemek için virgülleri ve rakam dışı karakterleri atar.
     */
    fun parseStardust(text: String): Int? {
        if (text.isBlank()) return null
        
        // 1. Temizle ama boşlukları koru (kelimeleri ayırmak için)
        val upper = text.uppercase().replace(",", "")
        val words = upper.split(Regex("\\s+"))
        
        // 2. Önce kelime bazlı tam eşleşme ara (En güveniliri)
        for (word in words) {
            val cleanWord = word.replace(Regex("[^0-9]"), "")
            if (cleanWord.isNotEmpty()) {
                val num = cleanWord.toIntOrNull()
                if (num != null && isValidStardust(num)) {
                    return num
                }
            }
        }

        // 3. Eğer tam kelime eşleşmesi yoksa, tüm rakamları birleştirip ara
        // Ancak burada büyükten küçüğe doğru "içerme" kontrolü yaparken dikkatli olmalıyız.
        // "10000" içinde "1000" de vardır. Bu yüzden uzunluk önceliği verelim.
        val allDigits = upper.replace(Regex("[^0-9]"), "")
        if (allDigits.isEmpty()) return null

        for (v in validListDescending) {
            val vStr = v.toString()
            // Sadece alt dize olarak değil, mantıklı bir sınırla eşleşiyor mu bak
            // (Örn: Yanında başka rakam yoksa veya kelime içindeyse)
            if (allDigits.contains(vStr)) {
                // Eğer bulduğumuz değer 1000 ise ama aslında 10000'in parçasıysa, 10000'i seçmeliydik.
                // validListDescending zaten büyükten küçüğe olduğu için 10000'i önce bulur.
                android.util.Log.d("TextParser", "Stardust found in combined digits: $v (Raw: $text)")
                return v
            }
        }

        return null
    }

    private val validListDescending = listOf(15000, 13000, 12000, 11000, 10000, 
        9000, 8000, 7000, 6000, 5000, 4500, 4000, 3500, 3000, 
        2500, 2200, 1900, 1600, 1300, 1000, 800, 600, 400, 200)

    private fun isValidStardust(v: Int): Boolean {
        return validListDescending.contains(v)
    }

    private fun loadPokemonNames(context: Context): List<String> {
        return try {
            val names = Gson().fromJson<List<String>>(
                InputStreamReader(context.assets.open("data/pokemon_names.json")),
                object : TypeToken<List<String>>() {}.type
            )
            names.map { it.lowercase() }
        } catch (e: Exception) {
            android.util.Log.e("TextParser","Failed to load Pokemon names", e)
            emptyList()
        }
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val l0 = lhs.length; val l1 = rhs.length
        var cost = IntArray(l0); var nc = IntArray(l0)
        for (i in 0 until l0) cost[i] = i
        for (j in 1 until l1) {
            nc[0] = j
            for (i in 1 until l0) {
                val m = if (lhs[i-1] == rhs[j-1]) 0 else 1
                nc[i] = min(min(cost[i]+1, nc[i-1]+1), cost[i-1]+m)
            }
            val s = cost; cost = nc; nc = s
        }
        return cost[l0-1]
    }
}