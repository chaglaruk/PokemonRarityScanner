package com.pokerarity.scanner.data.model

import com.pokerarity.scanner.ui.theme.PokemonType
import com.pokerarity.scanner.ui.theme.RarityColor
import com.pokerarity.scanner.ui.theme.TypeColors
import androidx.compose.ui.graphics.Color

enum class Rarity { LEGENDARY, RARE, SHINY, COMMON }

data class RarityAnalysisItem(
    val label: String,
    val points: String,       // e.g. "+30" or "—"
    val isPositive: Boolean,
)

data class Pokemon(
    val id: Int,
    val name: String,
    val cp: Int,
    val hp: Int,
    val iv: Int,              // 0–100
    val rarityScore: Int,     // 0–100
    val rarity: Rarity,
    val type: String,         // "water", "fire", etc.
    val displayDate: String,
    val caughtDate: String,
    val tags: List<String>,   // "LEGENDARY", "SHINY", "HUNDO", "LUCKY"
    val analysis: List<RarityAnalysisItem>,
) {
    val typeColors: TypeColors get() = PokemonType.fromString(type)

    val rarityColor: Color get() = when (rarity) {
        Rarity.LEGENDARY -> RarityColor.Legendary
        Rarity.RARE      -> RarityColor.Rare
        Rarity.SHINY     -> RarityColor.Shiny
        Rarity.COMMON    -> RarityColor.Common
    }

    val rarityLabel: String get() = when (rarity) {
        Rarity.LEGENDARY -> "LEGENDARY"
        Rarity.RARE      -> "RARE"
        Rarity.SHINY     -> "SHINY"
        Rarity.COMMON    -> "COMMON"
    }
}

// ── Sample data ───────────────────────────────────────────────
val samplePokemon = listOf(
    Pokemon(
        id = 1, name = "Gyarados", cp = 3834, hp = 194, iv = 100,
        rarityScore = 78, rarity = Rarity.LEGENDARY, type = "water",
        displayDate = "Mar 16, 2026", caughtDate = "Apr 04, 2017",
        tags = listOf("LEGENDARY", "SHINY", "HUNDO"),
        analysis = listOf(
            RarityAnalysisItem("Hundo — Perfect IVs", "+30", true),
            RarityAnalysisItem("Uncommon species", "—", false),
            RarityAnalysisItem("Lucky Pokemon", "+10", true),
            RarityAnalysisItem("3+ year veteran · Apr 2017", "+30", true),
            RarityAnalysisItem("Shiny variant", "+18", true),
        )
    ),
    Pokemon(
        id = 2, name = "Mewtwo", cp = 1685, hp = 122, iv = 89,
        rarityScore = 47, rarity = Rarity.RARE, type = "psychic",
        displayDate = "Mar 16, 2026", caughtDate = "Mar 16, 2026",
        tags = listOf("RARE", "SHINY"),
        analysis = listOf(
            RarityAnalysisItem("Psychic legendary", "+20", true),
            RarityAnalysisItem("Raid caught", "+10", true),
            RarityAnalysisItem("Shiny variant", "+18", true),
        )
    ),
    Pokemon(
        id = 3, name = "Charizard", cp = 2889, hp = 165, iv = 96,
        rarityScore = 61, rarity = Rarity.RARE, type = "fire",
        displayDate = "Mar 15, 2026", caughtDate = "Jan 12, 2024",
        tags = listOf("RARE", "HUNDO"),
        analysis = listOf(
            RarityAnalysisItem("Near-perfect IVs", "+25", true),
            RarityAnalysisItem("Evolved from starter", "+12", true),
            RarityAnalysisItem("1+ year veteran", "+15", true),
        )
    ),
    Pokemon(
        id = 4, name = "Dragonite", cp = 3287, hp = 182, iv = 91,
        rarityScore = 55, rarity = Rarity.RARE, type = "dragon",
        displayDate = "Mar 13, 2026", caughtDate = "Feb 28, 2025",
        tags = listOf("RARE", "HUNDO"),
        analysis = listOf(
            RarityAnalysisItem("High IVs", "+22", true),
            RarityAnalysisItem("Pseudo-legendary", "+18", true),
            RarityAnalysisItem("Veteran catch", "+12", true),
        )
    ),
    Pokemon(
        id = 5, name = "Jolteon", cp = 512, hp = 77, iv = 71,
        rarityScore = 22, rarity = Rarity.COMMON, type = "electric",
        displayDate = "Mar 14, 2026", caughtDate = "Mar 14, 2026",
        tags = emptyList(),
        analysis = listOf(
            RarityAnalysisItem("Common species", "—", false),
            RarityAnalysisItem("Average IVs", "+5", true),
        )
    ),
)
