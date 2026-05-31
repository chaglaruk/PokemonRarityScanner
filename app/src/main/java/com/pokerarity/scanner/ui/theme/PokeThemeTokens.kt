// Purpose: Define crash-safe selectable UI theme tokens.
package com.pokerarity.scanner.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PokeThemeId(
    val storageValue: String,
    val displayName: String
) {
    CLASSIC("classic", "Classic"),
    OBSIDIAN_RARITY("obsidian_rarity", "Obsidian Rarity"),
    POKEDEX_RED("pokedex_red", "Pokedex Red"),
    MYSTIC_BLUE("mystic_blue", "Mystic Blue"),
    FOREST_RESEARCH("forest_research", "Forest Research"),
    AURORA_VIOLET("aurora_violet", "Aurora Violet")
}

fun safeThemeId(raw: String?): PokeThemeId {
    val normalized = raw?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return PokeThemeId.CLASSIC
    return runCatching {
        PokeThemeId.entries.firstOrNull {
            it.storageValue == normalized || it.name.lowercase() == normalized
        } ?: PokeThemeId.CLASSIC
    }.getOrDefault(PokeThemeId.CLASSIC)
}

data class PokeThemeTokens(
    val id: PokeThemeId,
    val displayName: String,
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val rarityCommon: Color,
    val rarityUncommon: Color,
    val rarityRare: Color,
    val rarityEpic: Color,
    val rarityLegendary: Color,
    val rarityMythical: Color,
    val rarityShiny: Color,
    val spacingXs: Dp,
    val spacingSm: Dp,
    val spacingMd: Dp,
    val spacingLg: Dp,
    val spacingXl: Dp,
    val radiusSm: Dp,
    val radiusMd: Dp,
    val radiusLg: Dp,
    val cardElevation: Dp,
    val glowAlpha: Float
)

data class PokeThemeOverrides(
    val id: PokeThemeId,
    val displayName: String = id.displayName,
    val background: Color? = null,
    val surface: Color? = null,
    val elevatedSurface: Color? = null,
    val card: Color? = null,
    val textPrimary: Color? = null,
    val textSecondary: Color? = null,
    val textMuted: Color? = null,
    val border: Color? = null,
    val accent: Color? = null,
    val accentSoft: Color? = null,
    val danger: Color? = null,
    val warning: Color? = null,
    val success: Color? = null,
    val rarityCommon: Color? = null,
    val rarityUncommon: Color? = null,
    val rarityRare: Color? = null,
    val rarityEpic: Color? = null,
    val rarityLegendary: Color? = null,
    val rarityMythical: Color? = null,
    val rarityShiny: Color? = null,
    val spacingXs: Dp? = null,
    val spacingSm: Dp? = null,
    val spacingMd: Dp? = null,
    val spacingLg: Dp? = null,
    val spacingXl: Dp? = null,
    val radiusSm: Dp? = null,
    val radiusMd: Dp? = null,
    val radiusLg: Dp? = null,
    val cardElevation: Dp? = null,
    val glowAlpha: Float? = null
)

object PokeThemeRegistry {
    val classic = PokeThemeTokens(
        id = PokeThemeId.CLASSIC,
        displayName = PokeThemeId.CLASSIC.displayName,
        background = Color(0xFF000000),
        surface = Color(0xFF0D0D0D),
        elevatedSurface = Color(0xFF161616),
        card = Color(0xFF1A1A1A),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xCCFFFFFF),
        textMuted = Color(0x99FFFFFF),
        border = Color(0xFF2A2A2A),
        accent = Color(0xFFE3350D),
        accentSoft = Color(0x33E3350D),
        danger = Color(0xFFFF4D4D),
        warning = Color(0xFFFFAA00),
        success = Color(0xFF00FF8C),
        rarityCommon = Color(0xFF4B5563),
        rarityUncommon = Color(0xFF10B981),
        rarityRare = Color(0xFF60A5FA),
        rarityEpic = Color(0xFF8B5CF6),
        rarityLegendary = Color(0xFFFFD700),
        rarityMythical = Color(0xFFEC4899),
        rarityShiny = Color(0xFFC084FC),
        spacingXs = 4.dp,
        spacingSm = 8.dp,
        spacingMd = 12.dp,
        spacingLg = 16.dp,
        spacingXl = 24.dp,
        radiusSm = 8.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp,
        cardElevation = 0.dp,
        glowAlpha = 0.32f
    )

    private val themeOverrides = listOf(
        PokeThemeOverrides(
            id = PokeThemeId.OBSIDIAN_RARITY,
            background = Color(0xFF08090A),
            surface = Color(0xFF101214),
            elevatedSurface = Color(0xFF171A1D),
            card = Color(0xFF1D2125),
            textSecondary = Color(0xFFD3D7DD),
            textMuted = Color(0xFF8F98A3),
            border = Color(0xFF2B3137),
            accent = Color(0xFFF5C542),
            accentSoft = Color(0x33F5C542),
            rarityLegendary = Color(0xFFFFC857),
            rarityShiny = Color(0xFFE5B8FF),
            glowAlpha = 0.38f
        ),
        PokeThemeOverrides(
            id = PokeThemeId.POKEDEX_RED,
            background = Color(0xFF120607),
            surface = Color(0xFF1D0A0D),
            elevatedSurface = Color(0xFF2A1014),
            card = Color(0xFF35161A),
            textSecondary = Color(0xFFFFD7D2),
            textMuted = Color(0xFFB98983),
            border = Color(0xFF5B2228),
            accent = Color(0xFFFF3B30),
            accentSoft = Color(0x33FF3B30),
            danger = Color(0xFFFF6B6B),
            warning = Color(0xFFFFB020)
        ),
        PokeThemeOverrides(
            id = PokeThemeId.MYSTIC_BLUE,
            background = Color(0xFF061018),
            surface = Color(0xFF0B1924),
            elevatedSurface = Color(0xFF112638),
            card = Color(0xFF17314A),
            textSecondary = Color(0xFFD8F1FF),
            textMuted = Color(0xFF8EB7CC),
            border = Color(0xFF244B66),
            accent = Color(0xFF32D5FF),
            accentSoft = Color(0x3332D5FF),
            rarityRare = Color(0xFF38BDF8),
            rarityShiny = Color(0xFFB9F2FF)
        ),
        PokeThemeOverrides(
            id = PokeThemeId.FOREST_RESEARCH,
            background = Color(0xFF07120C),
            surface = Color(0xFF0D1B12),
            elevatedSurface = Color(0xFF14261A),
            card = Color(0xFF1B3323),
            textSecondary = Color(0xFFD8EBDD),
            textMuted = Color(0xFF91AD99),
            border = Color(0xFF2B5136),
            accent = Color(0xFF4ADE80),
            accentSoft = Color(0x334ADE80),
            success = Color(0xFF86EFAC),
            rarityUncommon = Color(0xFF34D399)
        ),
        PokeThemeOverrides(
            id = PokeThemeId.AURORA_VIOLET,
            background = Color(0xFF0C0714),
            surface = Color(0xFF151020),
            elevatedSurface = Color(0xFF20182F),
            card = Color(0xFF2A203D),
            textSecondary = Color(0xFFE8DCFF),
            textMuted = Color(0xFFA99AC6),
            border = Color(0xFF44355F),
            accent = Color(0xFFB78CFF),
            accentSoft = Color(0x33B78CFF),
            rarityEpic = Color(0xFFC084FC),
            rarityMythical = Color(0xFFF0ABFC),
            rarityShiny = Color(0xFFFFB8E8),
            glowAlpha = 0.36f
        )
    )

    val allThemes: List<PokeThemeTokens> =
        PokeThemeId.entries.map { getThemeById(it) }

    fun getThemeById(id: PokeThemeId): PokeThemeTokens {
        if (id == PokeThemeId.CLASSIC) return classic
        val overrides = themeOverrides.firstOrNull { it.id == id } ?: return classic
        return mergeWithClassic(overrides)
    }

    fun getThemeByRawId(raw: String?): PokeThemeTokens =
        getThemeById(safeThemeId(raw))

    internal fun mergeWithClassic(overrides: PokeThemeOverrides): PokeThemeTokens =
        classic.copy(
            id = overrides.id,
            displayName = overrides.displayName,
            background = overrides.background ?: classic.background,
            surface = overrides.surface ?: classic.surface,
            elevatedSurface = overrides.elevatedSurface ?: classic.elevatedSurface,
            card = overrides.card ?: classic.card,
            textPrimary = overrides.textPrimary ?: classic.textPrimary,
            textSecondary = overrides.textSecondary ?: classic.textSecondary,
            textMuted = overrides.textMuted ?: classic.textMuted,
            border = overrides.border ?: classic.border,
            accent = overrides.accent ?: classic.accent,
            accentSoft = overrides.accentSoft ?: classic.accentSoft,
            danger = overrides.danger ?: classic.danger,
            warning = overrides.warning ?: classic.warning,
            success = overrides.success ?: classic.success,
            rarityCommon = overrides.rarityCommon ?: classic.rarityCommon,
            rarityUncommon = overrides.rarityUncommon ?: classic.rarityUncommon,
            rarityRare = overrides.rarityRare ?: classic.rarityRare,
            rarityEpic = overrides.rarityEpic ?: classic.rarityEpic,
            rarityLegendary = overrides.rarityLegendary ?: classic.rarityLegendary,
            rarityMythical = overrides.rarityMythical ?: classic.rarityMythical,
            rarityShiny = overrides.rarityShiny ?: classic.rarityShiny,
            spacingXs = overrides.spacingXs ?: classic.spacingXs,
            spacingSm = overrides.spacingSm ?: classic.spacingSm,
            spacingMd = overrides.spacingMd ?: classic.spacingMd,
            spacingLg = overrides.spacingLg ?: classic.spacingLg,
            spacingXl = overrides.spacingXl ?: classic.spacingXl,
            radiusSm = overrides.radiusSm ?: classic.radiusSm,
            radiusMd = overrides.radiusMd ?: classic.radiusMd,
            radiusLg = overrides.radiusLg ?: classic.radiusLg,
            cardElevation = overrides.cardElevation ?: classic.cardElevation,
            glowAlpha = overrides.glowAlpha ?: classic.glowAlpha
        )
}
