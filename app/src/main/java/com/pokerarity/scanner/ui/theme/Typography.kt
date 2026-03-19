package com.pokerarity.scanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pokerarity.scanner.R

val OutfitFamily = FontFamily(
    Font(R.font.outfit_regular, weight = FontWeight.Normal),
    Font(R.font.outfit_medium, weight = FontWeight.Medium),
    Font(R.font.outfit_semibold, weight = FontWeight.SemiBold),
    Font(R.font.outfit_bold, weight = FontWeight.Bold),
    Font(R.font.outfit_extrabold, weight = FontWeight.ExtraBold),
    Font(R.font.outfit_black, weight = FontWeight.Black),
)

val PokeRarityTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Black,
        fontSize = 46.sp,
        letterSpacing = (-2).sp,
        lineHeight = 44.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Black,
        fontSize = 90.sp,
        letterSpacing = (-5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        letterSpacing = (-0.3).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 1.5.sp,
    ),
)
