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
    val label: String,
    val points: String,
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
    val type: String,
    val displayDate: String,
    val caughtDate: String,
    val tags: List<String>,
    val analysis: List<RarityAnalysisItem>,
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
}

private val displayDateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
private val shortDateFormatter = SimpleDateFormat("MMM yyyy", Locale.US)

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
            rarity == Rarity.LEGENDARY -> add(RarityAnalysisItem("Legendary or mythical score band", "+20", true))
            rarity == Rarity.RARE -> add(RarityAnalysisItem("Rare score band", "+10", true))
            else -> add(RarityAnalysisItem("Common score band", "-", false))
        }
        if (isShiny) add(RarityAnalysisItem("Shiny variant", "+18", true))
        if (isLucky) add(RarityAnalysisItem("Lucky Pokemon", "+10", true))
        if (hasCostume) add(RarityAnalysisItem("Costume variant", "+5", true))
        if (isShadow) add(RarityAnalysisItem("Shadow form", "+5", true))
        caughtDate?.let { add(RarityAnalysisItem("Caught ${shortDateFormatter.format(it)}", "+8", true)) }
    }.ifEmpty {
        listOf(RarityAnalysisItem("No extra rarity signals detected", "-", false))
    }

    return Pokemon(
        id = id.toInt(),
        sourceId = id,
        name = resolvedName,
        cp = cp ?: 0,
        hp = hp,
        iv = null,
        ivText = null,
        rarityScore = rarityScore.coerceIn(0, 100),
        rarity = rarity,
        type = resolvedType,
        displayDate = displayDateFormatter.format(timestamp),
        caughtDate = caughtDate?.let { displayDateFormatter.format(it) } ?: "Unknown",
        tags = resolvedTags,
        analysis = analysis,
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
        if (isShiny) add(RarityAnalysisItem("Shiny variant", "+18", true))
        if (isLucky) add(RarityAnalysisItem("Lucky Pokemon", "+10", true))
        if (hasCostume) add(RarityAnalysisItem("Costume variant", "+5", true))
        if (hasSpecialForm) add(RarityAnalysisItem("Special form", "+6", true))
        if (isShadow) add(RarityAnalysisItem("Shadow form", "+5", true))
        if (tier.isNotBlank()) add(RarityAnalysisItem("Tier: ${tier.uppercase(Locale.US)}", "+${score.coerceAtMost(20)}", true))
    }.ifEmpty {
        listOf(RarityAnalysisItem("No extra rarity signals detected", "-", false))
    }

    return Pokemon(
        id = 0,
        sourceId = 0,
        name = name.ifBlank { "Unknown" },
        cp = cp,
        hp = hp,
        iv = ivValue,
        ivText = ivText,
        rarityScore = score.coerceIn(0, 100),
        rarity = rarity,
        type = inferTypeFromSpecies(name),
        displayDate = dateText ?: "Unknown",
        caughtDate = dateText ?: "Unknown",
        tags = tags,
        analysis = analysis,
    )
}

fun buildAnalysisItems(
    breakdownKeys: List<String>,
    breakdownValues: List<Int>,
    explanations: List<String>,
    fallbackScore: Int,
): List<RarityAnalysisItem> {
    if (breakdownKeys.isNotEmpty() && breakdownKeys.size == breakdownValues.size) {
        return breakdownKeys.mapIndexed { index, key ->
            val points = breakdownValues[index]
            RarityAnalysisItem(
                label = key.replace('_', ' '),
                points = if (points >= 0) "+$points" else points.toString(),
                isPositive = points > 0,
            )
        }
    }

    if (explanations.isNotEmpty()) {
        return explanations.map { explanation ->
            val trimmed = explanation.trim()
            val parsedPoints = Regex("\\(([+-]?\\d+)\\)").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val label = trimmed.replace(Regex("\\s*\\([^)]+\\)\\s*"), "").ifBlank { trimmed }
            RarityAnalysisItem(
                label = label,
                points = parsedPoints?.let { if (it >= 0) "+$it" else it.toString() } ?: "--",
                isPositive = (parsedPoints ?: 0) > 0,
            )
        }
    }

    return listOf(
        RarityAnalysisItem(
            label = "Calculated rarity score",
            points = "+${fallbackScore.coerceIn(0, 100)}",
            isPositive = fallbackScore > 0,
        )
    )
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
