package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.data.model.VisualFeatures
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.min

/**
 * Detects visual features (Shiny, Shadow, Lucky, Costume) from a Pokemon GO screenshot
 * using native Android color analysis. No OpenCV dependency required.
 *
 * All analysis runs on a 360p downscaled bitmap for performance.
 */
class VisualFeatureDetector(private val context: Context) {

    /**
     * Reference color data for normal vs shiny forms.
     */
    data class ColorReference(val normal: List<Int>, val shiny: List<Int>)

    private val pokemonColors: Map<String, ColorReference> by lazy { loadPokemonColors() }

    // ──────────────────────────────────────────────────
    // Constants for feature detection
    // ──────────────────────────────────────────────────

    companion object {
        /** Shadow Pokemon: purple aura around sprite */
        private val SHADOW_HUE_RANGE = 260..280
        private const val SHADOW_MIN_SATURATION = 0.50f
        private const val SHADOW_MIN_VALUE = 0.30f
        private const val SHADOW_THRESHOLD = 0.08f // 8% purple pixels in border = shadow

        /** Lucky Pokemon: golden/yellow background */
        private val LUCKY_HUE_RANGE = 45..65
        private const val LUCKY_MIN_SATURATION = 0.60f
        private const val LUCKY_MIN_VALUE = 0.70f
        private const val LUCKY_THRESHOLD = 0.15f // 15% yellow in background = lucky

        /** RGB Euclidean distance threshold for shiny match */
        private const val SHINY_COLOR_DIST_THRESHOLD = 50.0
    }

    /**
     * Run all detections and return combined results with a confidence score.
     */
    fun detect(bitmap: Bitmap, pokemonName: String? = null, sizeTag: String? = null): VisualFeatures {
        android.util.Log.d("VisualFeatureDetector", "Detecting features for pokemon: $pokemonName (SizeTag: $sizeTag)")
        
        // ImagePreprocessor içindeki yeni dominant renk tespiti
        val dominantColor = com.pokerarity.scanner.util.ocr.ImagePreprocessor.getDominantColor(bitmap)
        
        val smallBitmap = ColorAnalyzer.downscaleForAnalysis(bitmap)

        val shinyResult = isShinyByColor(dominantColor, pokemonName)
        val shadowResult = isShadow(smallBitmap)
        val luckyResult = isLucky(smallBitmap)
        
        // Kostüm tespiti şimdilik devre dışı (IV matematiğine odaklanılıyor)
        val costumeResult = Pair(false, 0f) 
        
        val locationCardResult = isLocationCard(smallBitmap)
        
        android.util.Log.d("VisualFeatureDetector", "Results: shiny=${shinyResult.first}, shadow=${shadowResult.first}, lucky=${luckyResult.first}, locationCard=${locationCardResult.first}")

        // Aggregate confidence from individual detections
        val confidenceScores = mutableListOf<Float>()
        if (shinyResult.first) confidenceScores.add(shinyResult.second)
        if (shadowResult.first) confidenceScores.add(shadowResult.second)
        if (luckyResult.first) confidenceScores.add(luckyResult.second)
        if (costumeResult.first) confidenceScores.add(costumeResult.second)

        val avgConfidence = if (confidenceScores.isNotEmpty()) {
            confidenceScores.average().toFloat()
        } else {
            1.0f // No special features = high confidence in "normal"
        }

        return VisualFeatures(
            isShiny = shinyResult.first,
            isShadow = shadowResult.first,
            isLucky = luckyResult.first,
            hasCostume = costumeResult.first,
            isXXS = sizeTag == "XXS",
            isXXL = sizeTag == "XXL",
            hasLocationCard = locationCardResult.first,
            confidence = avgConfidence
        )
    }

    // ──────────────────────────────────────────────────
    // Location Card Detection
    // ──────────────────────────────────────────────────

    /**
     * Detect special background location cards (e.g. GO Fest, City backgrounds).
     * These have distinct non-standard colors in the background region.
     */
    fun isLocationCard(bitmap: Bitmap): Pair<Boolean, Float> {
        val bgRegion = ColorAnalyzer.getBackgroundRegion(bitmap)
        val avgHue = ColorAnalyzer.getDominantHue(bitmap, bgRegion)
        
        // Standart arka planlar: 
        // Gündüz: Yeşil/Mavi (Hue 80-220)
        // Gece: Koyu Mavi/Mor (Hue 240-280)
        // Lucky: Altın (Hue 45-65)
        
        // Özel Arka Planlar (Location Cards):
        // Şehir/GO Fest: Genelde çok farklı (Örn: Turuncu, Pembe, Parlak Mavi)
        val isStandard = (avgHue in 80f..280f) || (avgHue in 45f..65f)
        
        val isLocationCard = !isStandard && avgHue > 0
        
        return Pair(isLocationCard, if (isLocationCard) 0.8f else 0f)
    }

    // ──────────────────────────────────────────────────
    // Shiny Detection (RGB Euclidean Distance)
    // ──────────────────────────────────────────────────

    /**
     * Detect if the Pokemon is shiny by comparing the extracted dominant RGB
     * against the known reference values using Euclidean Distance.
     *
     * @return Pair(isShiny, confidence)
     */
    fun isShinyByColor(dominantColor: Int, pokemonName: String?): Pair<Boolean, Float> {
        if (pokemonName == null) return Pair(false, 0f)

        val reference = pokemonColors[pokemonName] ?: return Pair(false, 0f)
        
        val r1 = android.graphics.Color.red(dominantColor)
        val g1 = android.graphics.Color.green(dominantColor)
        val b1 = android.graphics.Color.blue(dominantColor)
        
        val rNormal = reference.normal[0]; val gNormal = reference.normal[1]; val bNormal = reference.normal[2]
        val rShiny = reference.shiny[0]; val gShiny = reference.shiny[1]; val bShiny = reference.shiny[2]
        
        // Distance = sqrt((R1-R2)^2 + (G1-G2)^2 + (B1-B2)^2)
        val distToNormal = Math.sqrt(
            Math.pow((r1 - rNormal).toDouble(), 2.0) +
            Math.pow((g1 - gNormal).toDouble(), 2.0) +
            Math.pow((b1 - bNormal).toDouble(), 2.0)
        )
        
        val distToShiny = Math.sqrt(
            Math.pow((r1 - rShiny).toDouble(), 2.0) +
            Math.pow((g1 - gShiny).toDouble(), 2.0) +
            Math.pow((b1 - bShiny).toDouble(), 2.0)
        )
        
        android.util.Log.d("VisualFeatureDetector", "Shiny Analysis for $pokemonName: DistToNormal=$distToNormal, DistToShiny=$distToShiny")

        val isShiny = distToShiny < distToNormal && distToShiny < 100.0 // 100.0 is a reasonable cutoff for matching

        val confidence = if (isShiny) {
            val totalDist = distToNormal + distToShiny
            (distToNormal / totalDist).toFloat().coerceIn(0.5f, 1.0f)
        } else {
            0f
        }

        return Pair(isShiny, confidence)
    }

    // ──────────────────────────────────────────────────
    // Shadow Detection
    // ──────────────────────────────────────────────────

    /**
     * Detect shadow Pokemon by looking for a purple aura around the sprite.
     * Shadow Pokemon have a distinctive purple haze (HSV: H=260-280) around
     * the border of their sprite.
     *
     * @return Pair(isShadow, confidence)
     */
    fun isShadow(bitmap: Bitmap): Pair<Boolean, Float> {
        val borderRegion = ColorAnalyzer.getSpriteBorderRegion(bitmap)

        val purplePercentage = ColorAnalyzer.getColorPercentage(
            bitmap,
            borderRegion,
            SHADOW_HUE_RANGE,
            SHADOW_MIN_SATURATION,
            SHADOW_MIN_VALUE
        )

        // Also check average brightness - shadows tend to be darker
        val avgBrightness = ColorAnalyzer.getAverageBrightness(bitmap, borderRegion)

        val isShadow = purplePercentage >= SHADOW_THRESHOLD && avgBrightness < 0.6f

        val confidence = if (isShadow) {
            min(purplePercentage / (SHADOW_THRESHOLD * 2), 1.0f)
        } else {
            0f
        }

        return Pair(isShadow, confidence)
    }

    // ──────────────────────────────────────────────────
    // Lucky Detection
    // ──────────────────────────────────────────────────

    /**
     * Detect lucky Pokemon by looking for a golden/yellow background.
     * Lucky Pokemon have a distinctive golden sparkle background
     * (HSV: H=45-65, S=60-100%, V=70-100%).
     *
     * @return Pair(isLucky, confidence)
     */
    fun isLucky(bitmap: Bitmap): Pair<Boolean, Float> {
        val bgRegion = ColorAnalyzer.getBackgroundRegion(bitmap)

        val yellowPercentage = ColorAnalyzer.getColorPercentage(
            bitmap,
            bgRegion,
            LUCKY_HUE_RANGE,
            LUCKY_MIN_SATURATION,
            LUCKY_MIN_VALUE
        )

        val isLucky = yellowPercentage >= LUCKY_THRESHOLD

        val confidence = if (isLucky) {
            min(yellowPercentage / (LUCKY_THRESHOLD * 2), 1.0f)
        } else {
            0f
        }

        return Pair(isLucky, confidence)
    }

    // ──────────────────────────────────────────────────
    // Costume Detection
    // ──────────────────────────────────────────────────

    /**
     * Costume detection based on color deviation in the head region.
     * Ash hat (Red/White), Party hats, etc. usually deviate from species normal color.
     */
    fun hasCostume(bitmap: Bitmap, pokemonName: String?): Pair<Boolean, Float> {
        if (pokemonName == null) return Pair(false, 0f)

        val reference = pokemonColors[pokemonName] ?: return Pair(false, 0f)

        // 1. Head Region Check
        val spriteRegion = ColorAnalyzer.getSpriteRegion(bitmap)
        val headRegion = android.graphics.Rect(
            spriteRegion.left,
            maxOf(0, spriteRegion.top - (spriteRegion.height() * 0.1).toInt()), // Extend slightly above head
            spriteRegion.right,
            spriteRegion.top + (spriteRegion.height() * 0.35).toInt()
        )

        val headDominant = ColorAnalyzer.getDominantHue(bitmap, headRegion)
        
        val normalHSV = FloatArray(3)
        android.graphics.Color.RGBToHSV(reference.normal[0], reference.normal[1], reference.normal[2], normalHSV)
        val normalHue = normalHSV[0]

        // If the head color is significantly different from the body (normal) color, it's likely a costume
        val hueDist = hueDistance(headDominant, normalHue)
        
        android.util.Log.d("VisualFeatureDetector", "Costume Analysis for $pokemonName: HeadHue=$headDominant, NormalHue=$normalHue, Dist=$hueDist")

        // Ash Hat is red (~0), Pikachu is yellow (~50). Dist ~50.
        // Party hats are often very different colors.
        val hasCostume = hueDist > 40f 

        val confidence = if (hasCostume) {
            (hueDist / 100f).coerceIn(0.5f, 0.9f)
        } else {
            0f
        }

        return Pair(hasCostume, confidence)
    }

    // ──────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────

    /**
     * Calculate the shortest distance between two hues on the color wheel (0-360).
     */
    private fun hueDistance(h1: Float, h2: Float): Float {
        val diff = abs(h1 - h2)
        return min(diff, 360f - diff)
    }

    /**
     * Load pokemon colors from rarity_manifest.json.
     */
    private fun loadPokemonColors(): Map<String, ColorReference> {
        return try {
            val input = context.assets.open("data/rarity_manifest.json")
            val reader = InputStreamReader(input)
            val json = com.google.gson.JsonParser.parseReader(reader).asJsonObject
            
            if (!json.has("pokemonColors")) {
                android.util.Log.e("VisualFeatureDetector", "JSON missing pokemonColors key")
                return emptyMap()
            }
            
            val colorsJson = json.getAsJsonObject("pokemonColors")
            val type = object : TypeToken<Map<String, ColorReference>>() {}.type
            val map = Gson().fromJson<Map<String, ColorReference>>(colorsJson, type)
            android.util.Log.d("VisualFeatureDetector", "Loaded ${map.size} pokemon color definitions")
            map
        } catch (e: Exception) {
            android.util.Log.e("VisualFeatureDetector", "Load pokemon colors failed", e)
            emptyMap()
        }
    }
}
