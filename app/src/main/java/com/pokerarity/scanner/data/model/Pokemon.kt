package com.pokerarity.scanner.data.model

import androidx.compose.ui.graphics.Color
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.ui.theme.PokemonType
import com.pokerarity.scanner.ui.theme.RarityColor
import com.pokerarity.scanner.ui.theme.TypeColors
import java.text.SimpleDateFormat
import java.util.Locale

enum class Rarity { LEGENDARY, RARE, SHINY, COMMON }

data class RarityAnalysisItem(
    val title: String,
    val detail: String? = null,
    val isPositive: Boolean,
)

data class Pokemon(
    val id: Int,
    val sourceId: Long,
    val name: String,
    val cp: Int,
    val hp: Int?,
    val iv: Int?,
    val ivText: String? = null,
    val rarityScore: Int,
    val rarity: Rarity,
    val rarityTierCode: String = "COMMON",
    val type: String,
    val displayDate: String,
    val caughtDate: String,
    val tags: List<String>,
    val analysis: List<RarityAnalysisItem>,
    val decisionSupport: ScanDecisionSupport? = null,
    val telemetryUploadId: String? = null,
) {
    val typeColors: TypeColors
        get() = PokemonType.fromString(type)

    val rarityColor: Color
        get() = when (rarity) {
            Rarity.LEGENDARY -> RarityColor.Legendary
            Rarity.RARE -> RarityColor.Rare
            Rarity.SHINY -> RarityColor.Shiny
            Rarity.COMMON -> RarityColor.Common
        }

    val rarityTierLabel: String
        get() = formatRarityTierLabel(rarityTierCode)
}

private val displayDateFormatter
    get() = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
private val shortDateFormatter
    get() = SimpleDateFormat("MMM yyyy", Locale.getDefault())

fun ScanHistoryEntity.toUiPokemon(): Pokemon {
    val resolvedName = pokemonName?.takeIf { it.isNotBlank() } ?: "Unknown"
    val resolvedType = inferTypeFromSpecies(resolvedName)
    val resolvedTags = buildList {
        if (isShiny) add("SHINY")
        if (isLucky) add("LUCKY")
        if (hasCostume) add("COSTUME")
        if (isShadow) add("SHADOW")
        if (rarityTier.equals("LEGENDARY", ignoreCase = true) || rarityTier.equals("MYTHICAL", ignoreCase = true)) {
            add("LEGENDARY")
        } else if (rarityScore >= 30) {
            add("RARE")
        }
    }

    val rarity = when {
        isShiny -> Rarity.SHINY
        rarityTier.equals("LEGENDARY", ignoreCase = true) || rarityTier.equals("MYTHICAL", ignoreCase = true) -> Rarity.LEGENDARY
        rarityScore >= 30 -> Rarity.RARE
        else -> Rarity.COMMON
    }

    val analysis = buildList {
        when {
            rarity == Rarity.LEGENDARY -> add(RarityAnalysisItem("Legendary or mythical rarity band", null, true))
            rarity == Rarity.RARE -> add(RarityAnalysisItem("Rare rarity band", null, true))
            else -> add(RarityAnalysisItem("No major rarity signal detected", null, false))
        }
        if (isShiny) add(RarityAnalysisItem("Shiny variant", null, true))
        if (isLucky) add(RarityAnalysisItem("Lucky Pokemon", null, true))
        if (hasCostume) add(RarityAnalysisItem("Costume variant", null, true))
        if (isShadow) add(RarityAnalysisItem("Shadow form", null, true))
        caughtDate?.let { add(RarityAnalysisItem("Caught on ${displayDateFormatter.format(it)}", shortDateFormatter.format(it), true)) }
    }.ifEmpty {
        listOf(RarityAnalysisItem("No extra rarity signals detected", null, false))
    }

    return Pokemon(
        id = id.toInt(),
        sourceId = id,
        name = resolvedName,
        cp = cp ?: 0,
        hp = hp,
        iv = null,
        ivText = null,
        rarityScore = rarityScore.coerceAtLeast(0),
        rarity = rarity,
        rarityTierCode = rarityTier.ifBlank { RarityTier.fromScore(rarityScore.coerceAtLeast(0)).name },
        type = resolvedType,
        displayDate = displayDateFormatter.format(timestamp),
        caughtDate = caughtDate?.let { displayDateFormatter.format(it) } ?: "Unknown",
        tags = resolvedTags,
        analysis = analysis,
        decisionSupport = null,
        telemetryUploadId = null,
    )
}

fun pokemonFromScanExtras(
    name: String,
    cp: Int,
    hp: Int?,
    score: Int,
    tier: String,
    isShiny: Boolean,
    isLucky: Boolean,
    hasCostume: Boolean,
    hasSpecialForm: Boolean,
    isShadow: Boolean,
    dateText: String?,
    ivText: String?,
    analysisOverride: List<RarityAnalysisItem>? = null,
    decisionSupport: ScanDecisionSupport? = null,
    telemetryUploadId: String? = null,
): Pokemon {
    val tags = buildList {
        if (tier.equals("LEGENDARY", ignoreCase = true) || tier.equals("MYTHICAL", ignoreCase = true)) add("LEGENDARY")
        if (isShiny) add("SHINY")
        if (isLucky) add("LUCKY")
        if (hasCostume) add("COSTUME")
        if (hasSpecialForm) add("FORM")
        if (isShadow) add("SHADOW")
    }
    val rarity = when {
        isShiny -> Rarity.SHINY
        tier.equals("LEGENDARY", ignoreCase = true) || tier.equals("MYTHICAL", ignoreCase = true) -> Rarity.LEGENDARY
        score >= 30 -> Rarity.RARE
        else -> Rarity.COMMON
    }
    val ivValue = ivText
        ?.replace("%", "")
        ?.substringBefore(" ")
        ?.toIntOrNull()

    val analysis = analysisOverride ?: buildList {
        if (isShiny) add(RarityAnalysisItem("Shiny variant", null, true))
        if (isLucky) add(RarityAnalysisItem("Lucky Pokemon", null, true))
        if (hasCostume) add(RarityAnalysisItem("Costume variant", null, true))
        if (hasSpecialForm) add(RarityAnalysisItem("Special form", null, true))
        if (isShadow) add(RarityAnalysisItem("Shadow form", null, true))
        if (tier.isNotBlank()) add(RarityAnalysisItem("${tier.uppercase(Locale.US)} rarity tier", null, true))
    }.ifEmpty {
        listOf(RarityAnalysisItem("No extra rarity signals detected", null, false))
    }

    return Pokemon(
        id = 0,
        sourceId = 0,
        name = name.ifBlank { "Unknown" },
        cp = cp,
        hp = hp,
        iv = ivValue,
        ivText = ivText,
        rarityScore = score.coerceAtLeast(0),
        rarity = rarity,
        rarityTierCode = tier.ifBlank { RarityTier.fromScore(score.coerceAtLeast(0)).name },
        type = inferTypeFromSpecies(name),
        displayDate = dateText ?: "Unknown",
        caughtDate = dateText ?: "Unknown",
        tags = tags,
        analysis = analysis,
        decisionSupport = decisionSupport,
        telemetryUploadId = telemetryUploadId,
    )
}

private fun formatRarityTierLabel(code: String): String {
    val isTurkish = Locale.getDefault().language.startsWith("tr", ignoreCase = true)
    return when (code.uppercase(Locale.US)) {
        "COMMON" -> if (isTurkish) "Yaygin" else "Common"
        "UNCOMMON" -> if (isTurkish) "Az Yaygin" else "Uncommon"
        "RARE" -> if (isTurkish) "Nadir" else "Rare"
        "EPIC" -> if (isTurkish) "Epik" else "Epic"
        "LEGENDARY" -> if (isTurkish) "Efsanevi" else "Legendary"
        "MYTHICAL" -> if (isTurkish) "Mistik" else "Mythical"
        "GOD_TIER" -> if (isTurkish) "God Tier" else "God Tier"
        else -> code.lowercase(Locale.getDefault()).replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }
}

fun buildAnalysisItems(
    breakdownKeys: List<String>,
    breakdownValues: List<Int>,
    explanations: List<String>,
    fallbackScore: Int,
): List<RarityAnalysisItem> {
    if (explanations.isNotEmpty()) {
        return listOf(
            RarityAnalysisItem(
                title = buildNarrativeExplanation(explanations, fallbackScore),
                detail = null,
                isPositive = true,
            )
        )
    }

    if (breakdownKeys.isNotEmpty() && breakdownKeys.size == breakdownValues.size) {
        return breakdownKeys.mapIndexed { index, key ->
            val points = breakdownValues[index]
            RarityAnalysisItem(
                title = key.replace('_', ' '),
                detail = "Added ${if (points >= 0) "+$points" else points} score",
                isPositive = points > 0,
            )
        }
    }

    return listOf(
        RarityAnalysisItem(
            title = "Calculated rarity score",
                detail = "Total score ${fallbackScore.coerceAtLeast(0)}",
                isPositive = fallbackScore > 0,
            )
        )
}

private fun buildNarrativeExplanation(
    explanations: List<String>,
    fallbackScore: Int,
): String {
    val isTurkish = Locale.getDefault().language.startsWith("tr", ignoreCase = true)
    val reasons = explanations.mapNotNull { explanation ->
        val (title, detail) = decodeExplanationItem(explanation)
        explanationToPhrase(title, detail, isTurkish)
    }.distinct()

    if (reasons.isEmpty()) {
        return if (isTurkish) {
            "Bu Pokemon icin hesaplanan nadirlik puani ${fallbackScore.coerceAtLeast(0)}."
        } else {
            "This Pokemon has a calculated rarity of ${fallbackScore.coerceAtLeast(0)}."
        }
    }

    val reasonSentence = when (reasons.size) {
        1 -> reasons[0]
        2 -> if (isTurkish) "${reasons[0]} ve ${reasons[1]}" else "${reasons[0]} and ${reasons[1]}"
        else -> {
            val head = reasons.dropLast(1).joinToString(", ")
            if (isTurkish) "$head ve ${reasons.last()}" else "$head, and ${reasons.last()}"
        }
    }

    val score = fallbackScore.coerceAtLeast(0)
    return if (isTurkish) {
        "Bu Pokemon su nedenle dikkat cekiyor: $reasonSentence. " +
            "Bu koleksiyon sinyalleri onu normal bir yakalamadan daha ayirt edici hale getiriyor. " +
            "Toplamda nadirlik puani $score oluyor."
    } else {
        "This Pokemon stands out because $reasonSentence. " +
            "Those collection signals make it more distinctive than a regular catch. " +
            "Together they place it at a rarity score of $score."
    }
}

private fun explanationToPhrase(title: String, detail: String?, isTurkish: Boolean): String? {
    val normalizedTitle = title.trim()
    return when {
        normalizedTitle.startsWith("Costume:", ignoreCase = true) -> {
            val costumeName = normalizedTitle.substringAfter(":").trim()
            if (costumeName.isBlank()) null else if (isTurkish) "$costumeName ile eslesiyor" else "it matches the $costumeName"
        }
        normalizedTitle.startsWith("Event:", ignoreCase = true) -> {
            val eventName = normalizedTitle.substringAfter(":").trim()
            if (eventName.isBlank()) null else if (isTurkish) "$eventName eventiyle baglaniyor" else "it ties back to the $eventName event"
        }
        normalizedTitle.startsWith("Form:", ignoreCase = true) -> {
            val formName = normalizedTitle.substringAfter(":").trim()
            if (formName.isBlank()) null else if (isTurkish) "$formName formu olarak gorunuyor" else "it appears in the $formName"
        }
        normalizedTitle.startsWith("Caught on ", ignoreCase = true) -> {
            val date = normalizedTitle.removePrefix("Caught on ").trim()
            if (isTurkish) "$date tarihinde yakalanmis" else "it was caught on $date"
        }
        normalizedTitle.equals("Shiny variant", ignoreCase = true) -> if (isTurkish) "shiny varyant" else "it is a shiny variant"
        normalizedTitle.equals("Shiny costume variant", ignoreCase = true) -> if (isTurkish) "shiny kostumlu varyant" else "it is a shiny costume variant"
        normalizedTitle.equals("Special form", ignoreCase = true) -> if (isTurkish) "ozel forma sahip" else "it has a special form"
        normalizedTitle.equals("Release window", ignoreCase = true) -> {
            detail?.takeIf { it.isNotBlank() }?.let {
                if (isTurkish) "yayin araligi $it" else "its release window is $it"
            }
        }
        detail != null && detail.isNotBlank() -> {
            normalizedTitle.replaceFirstChar { it.lowercase(Locale.getDefault()) } +
                " (${detail.replaceFirstChar { ch -> ch.lowercase(Locale.getDefault()) }})"
        }
        normalizedTitle.isNotBlank() -> normalizedTitle.replaceFirstChar { it.lowercase(Locale.getDefault()) }
        else -> null
    }
}

private const val EXPLANATION_DETAIL_SEPARATOR = "||"

fun encodeExplanationItem(title: String, detail: String? = null): String {
    val cleanTitle = title.trim()
    val cleanDetail = detail?.trim()?.takeIf { it.isNotEmpty() }
    return if (cleanDetail == null) cleanTitle else "$cleanTitle$EXPLANATION_DETAIL_SEPARATOR$cleanDetail"
}

fun decodeExplanationItem(value: String): Pair<String, String?> {
    val trimmed = value.trim()
    val separatorIndex = trimmed.indexOf(EXPLANATION_DETAIL_SEPARATOR)
    if (separatorIndex < 0) return trimmed to null
    val title = trimmed.substring(0, separatorIndex).trim().ifBlank { trimmed }
    val detail = trimmed.substring(separatorIndex + EXPLANATION_DETAIL_SEPARATOR.length).trim().ifBlank { null }
    return title to detail
}

private fun inferTypeFromSpecies(name: String): String {
    return when (name.trim().lowercase(Locale.US)) {
        "gyarados", "magikarp", "squirtle", "wartortle", "blastoise", "piplup", "prinplup", "empoleon", "slowbro", "slowking" -> "water"
        "charizard", "charmeleon", "charmander", "blaziken", "torchic", "combusken", "ponyta", "rapidash" -> "fire"
        "mewtwo", "mew", "abra", "kadabra", "alakazam", "slowpoke" -> "psychic"
        "dragonite", "dratini", "dragonair", "rayquaza", "salamence" -> "dragon"
        "pikachu", "raichu", "pichu", "minun", "plusle", "mareep", "flaaffy", "ampharos", "electabuzz", "jolteon" -> "electric"
        "bulbasaur", "ivysaur", "venusaur", "rowlet", "dartrix", "decidueye", "lotad" -> "grass"
        "spheal", "sealeo", "walrein", "cubchoo", "beartic", "delibird" -> "ice"
        "machop", "machoke", "machamp", "sawk", "throh" -> "fighting"
        "gastly", "haunter", "gengar", "shiinotic" -> "ghost"
        "magnemite", "magneton", "magnezone", "armored mewtwo" -> "steel"
        "absol", "umbreon" -> "dark"
        else -> "normal"
    }
}
