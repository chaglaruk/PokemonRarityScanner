package com.pokerarity.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Black = Color(0xFF000000)
val Surface1 = Color(0xFF0D0D0D)
val Surface2 = Color(0xFF161616)
val Surface3 = Color(0xFF1A1A1A)
val Border1 = Color(0xFF161616)
val Border2 = Color(0xFF2A2A2A)
val TextPrimary = Color(0xFFFFFFFF)
val TextMuted = Color(0xCCFFFFFF)
val TextHint = Color(0x66FFFFFF)

val AccentGreen = Color(0xFF00FF8C)
val AccentGold = Color(0xFFFFD700)
val StripeStart = Color(0xFFFF5500)
val StripeMid = Color(0xFFE0003C)
val StripeEnd = Color(0xFF9900CC)

object RarityColor {
    val Legendary = Color(0xFFFFD700)
    val Rare = Color(0xFF60A5FA)
    val Shiny = Color(0xFFC084FC)
    val Common = Color(0xFF4B5563)
}

data class TypeColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

object PokemonType {
    val Water = TypeColors(Color(0xFF0066FF), Color(0xFF0033AA), Color(0xFF0044CC))
    val Fire = TypeColors(Color(0xFFFF4400), Color(0xFFCC0000), Color(0xFFEE2200))
    val Psychic = TypeColors(Color(0xFFCC0055), Color(0xFF7700DD), Color(0xFFAA0044))
    val Dragon = TypeColors(Color(0xFF4400CC), Color(0xFF220088), Color(0xFF3300AA))
    val Electric = TypeColors(Color(0xFFFFAA00), Color(0xFFCC6600), Color(0xFFFF8800))
    val Grass = TypeColors(Color(0xFF00AA44), Color(0xFF006622), Color(0xFF008833))
    val Normal = TypeColors(Color(0xFF666688), Color(0xFF444466), Color(0xFF555577))
    val Ice = TypeColors(Color(0xFF44CCFF), Color(0xFF0099CC), Color(0xFF22AADD))
    val Fighting = TypeColors(Color(0xFFCC4400), Color(0xFF882200), Color(0xFFAA3300))
    val Ghost = TypeColors(Color(0xFF6644AA), Color(0xFF442288), Color(0xFF553399))
    val Steel = TypeColors(Color(0xFF8899AA), Color(0xFF556677), Color(0xFF667788))
    val Dark = TypeColors(Color(0xFF443322), Color(0xFF221100), Color(0xFF332211))

    fun fromString(type: String): TypeColors = when (type.lowercase()) {
        "water" -> Water
        "fire" -> Fire
        "psychic" -> Psychic
        "dragon" -> Dragon
        "electric" -> Electric
        "grass" -> Grass
        "ice" -> Ice
        "fighting" -> Fighting
        "ghost" -> Ghost
        "steel" -> Steel
        "dark" -> Dark
        else -> Normal
    }
}

private val DarkColorScheme = darkColorScheme(
    background = Black,
    surface = Surface1,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

private val LightColorScheme = lightColorScheme(
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    primary = Color(0xFFE3350D),
    onPrimary = Color(0xFFFFFFFF),
)

@Composable
fun PokeRarityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PokeRarityTypography,
        content = content,
    )
}
