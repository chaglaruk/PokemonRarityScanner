package com.pokerarity.scanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Add Outfit font files to res/font/ (outfit_regular.ttf, outfit_bold.ttf etc.)
// Download from: https://fonts.google.com/specimen/Outfit
val OutfitFamily = FontFamily(
    Font(resId = com.pokerarity.scanner.R.font.outfit_regular,  weight = FontWeight.Normal),
    Font(resId = com.pokerarity.scanner.R.font.outfit_medium,   weight = FontWeight.Medium),
    Font(resId = com.pokerarity.scanner.R.font.outfit_semibold, weight = FontWeight.SemiBold),
    Font(resId = com.pokerarity.scanner.R.font.outfit_bold,     weight = FontWeight.Bold),
    Font(resId = com.pokerarity.scanner.R.font.outfit_extrabold,weight = FontWeight.ExtraBold),
    Font(resId = com.pokerarity.scanner.R.font.outfit_black,    weight = FontWeight.Black),
)

val PokeRarityTypography = Typography(
    // Pokemon name on detail screen — 46sp Black
    displayLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Black,
        fontSize   = 46.sp,
        letterSpacing = (-2).sp,
        lineHeight = 44.sp,
    ),
    // Score number — 90sp Black
    displayMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Black,
        fontSize   = 90.sp,
        letterSpacing = (-5).sp,
    ),
    // Section title — 28sp ExtraBold
    headlineLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize   = 28.sp,
        letterSpacing = (-1).sp,
    ),
    // Stat value — 20sp Bold
    headlineMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 20.sp,
        letterSpacing = (-0.5).sp,
    ),
    // Card pokemon name — 17sp Bold
    titleLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 17.sp,
        letterSpacing = (-0.3).sp,
    ),
    // Body / analysis row — 14sp Medium
    bodyLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
    ),
    // Date, hint text — 11sp Normal
    bodySmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 11.sp,
    ),
    // Eyebrow / label — 10sp SemiBold
    labelSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 10.sp,
        letterSpacing = 4.sp,
    ),
    // Badge text — 9sp Bold
    labelMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 9.sp,
        letterSpacing = 1.5.sp,
    ),
)
