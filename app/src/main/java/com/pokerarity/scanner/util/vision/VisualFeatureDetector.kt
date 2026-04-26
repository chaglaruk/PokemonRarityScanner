package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityManifestLoader
import java.util.ArrayDeque
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

    internal data class SignatureConsensus(
        val result: Pair<Boolean, Float>,
        val matchedCount: Int,
        val primaryMatched: Boolean
    )

    companion object {
        private const val GENERATED_COLORS_PATH = "data/pokemon_colors_generated.json"
        private const val MIN_COSTUME_CONFIDENCE = 0.20f
        private const val BORDERLINE_COSTUME_CONFIDENCE = 0.24f
        private const val SPARSE_SIGNATURE_COSTUME_CONFIDENCE = 0.21f
        private const val MIN_HEURISTIC_ONLY_COSTUME_CONFIDENCE = 0.65f  // Raised from 0.55 to reduce Pikachu shiny→costume false positive

    // ──────────────────────────────────────────────────
    // Constants for feature detection
    // ──────────────────────────────────────────────────

        /** Shadow Pokemon: purple aura around sprite */
        private val SHADOW_HUE_RANGE = 255..285
        private const val SHADOW_MIN_SATURATION = 0.40f
        private const val SHADOW_MIN_VALUE = 0.30f
        private const val SHADOW_THRESHOLD = 0.05f // 5% purple pixels in border = shadow

        /** Lucky Pokemon: golden/yellow background */
        private val LUCKY_HUE_RANGE = 45..65
        private const val LUCKY_MIN_SATURATION = 0.60f
        private const val LUCKY_MIN_VALUE = 0.70f
        private const val LUCKY_THRESHOLD = 0.15f // 15% yellow in background = lucky

        /** RGB Euclidean distance threshold for shiny match */
        private const val SHINY_COLOR_DIST_THRESHOLD = 50.0
    }

    /**
     * Reference color data for normal vs shiny forms.
     */
    data class ColorReference(val normal: List<Int>, val shiny: List<Int>)

    private val pokemonColors: Map<String, ColorReference> by lazy { loadPokemonColors() }

    /**
     * Run all detections and return combined results with a confidence score.
     */
    fun detect(bitmap: Bitmap, pokemonName: String? = null, sizeTag: String? = null): VisualFeatures {
        android.util.Log.d("VisualFeatureDetector", "Detecting features for pokemon: $pokemonName (SizeTag: $sizeTag)")

        val smallBitmapRaw = ColorAnalyzer.downscaleForAnalysis(bitmap)
        val shadowResult = isShadow(smallBitmapRaw)
        
        val smallBitmap = if (shadowResult.first) {
            ColorAnalyzer.maskShadowFlames(smallBitmapRaw)
        } else {
            smallBitmapRaw
        }
        val shouldRecycleSmall = smallBitmap !== bitmap
        val maskedSprite = ColorAnalyzer.extractMaskedSprite(smallBitmap)

        val dominantColor = ColorAnalyzer.getDominantRgb(maskedSprite, null)
        val spriteSignature = SpriteSignature.computeFromMaskedSprite(maskedSprite)
        val spritePHash = PerceptualHash.compute(maskedSprite)
        val costumeHeadPHash = computeHeadPHash(maskedSprite)
        val maskedFullHist = SpriteColorSignature.computeHueHistogram(maskedSprite)
        val bodyTop = (maskedSprite.height * 0.30f).toInt().coerceIn(0, maskedSprite.height.coerceAtLeast(1) - 1)
        val bodyHeight = (maskedSprite.height - bodyTop).coerceAtLeast(1)
        val bodySprite = if (bodyTop == 0 && bodyHeight == maskedSprite.height) maskedSprite else Bitmap.createBitmap(maskedSprite, 0, bodyTop, maskedSprite.width, bodyHeight)
        val bodyHist = SpriteColorSignature.computeHueHistogram(bodySprite)
        val upperHeight = (maskedSprite.height * 0.55f).toInt().coerceIn(1, maskedSprite.height)
        val upperSprite = if (upperHeight == maskedSprite.height) maskedSprite else Bitmap.createBitmap(maskedSprite, 0, 0, maskedSprite.width, upperHeight)
        val upperHist = SpriteColorSignature.computeHueHistogram(upperSprite)
        val bodyDominantColor = ColorAnalyzer.getDominantRgb(bodySprite, null)
        val rawSpriteRegion = ColorAnalyzer.getSpriteRegionAdaptive(smallBitmap)
        val rawSprite = Bitmap.createBitmap(smallBitmap, rawSpriteRegion.left, rawSpriteRegion.top, rawSpriteRegion.width(), rawSpriteRegion.height())
        val rawBodyTop = (rawSprite.height * 0.30f).toInt().coerceIn(0, rawSprite.height.coerceAtLeast(1) - 1)
        val rawBodyHeight = (rawSprite.height - rawBodyTop).coerceAtLeast(1)
        val rawBody = if (rawBodyTop == 0 && rawBodyHeight == rawSprite.height) rawSprite else Bitmap.createBitmap(rawSprite, 0, rawBodyTop, rawSprite.width, rawBodyHeight)
        val altColorHist = SpriteColorSignature.computeHueHistogram(rawBody)
        val rawBodyDominantColor = ColorAnalyzer.getDominantRgb(rawBody, null)
        val rawBodyHue = ColorAnalyzer.getDominantHue(rawBody, null)
        val rawBodySat = ColorAnalyzer.getAverageSaturation(rawBody, null)
        val rawBodyVal = ColorAnalyzer.getAverageBrightness(rawBody, null)
        if (rawBody !== rawSprite) rawBody.recycle()
        rawSprite.recycle()

        // Varyantlar bağımsız olarak tespit edilir ve kombinasyonlar desteklenir
        val shouldCheckCostume = VariantRegistry.hasCostumeLikeSpecies(context, pokemonName) || RarityManifestLoader.hasCostumeSpecies(pokemonName)
        val hasSignatureSpecies = CostumeSignatureStore.hasSpecies(context, pokemonName)
        val costumeResult = if (hasSignatureSpecies) {
            val signatureDetails = CostumeSignatureStore.matchSignatureDetails(
                signature = spriteSignature,
                species = pokemonName,
                pHash = spritePHash,
                headPHash = costumeHeadPHash
            )
            val borderlineCostumeRescue = signatureDetails?.let {
                it.matched &&
                    !it.denseVariantSpecies &&
                    it.bestCostume <= 0.28f &&
                    it.scoreGap >= 0.07f &&
                    it.confidence >= 0.18f
            } == true
            val sparseMatchedCostumeRescue = signatureDetails?.let {
                it.matched &&
                    !it.denseVariantSpecies &&
                    it.bestCostume <= 0.36f &&
                    it.scoreGap >= 0.03f
            } == true
            val signatureResultRaw = if (signatureDetails != null) {
                if (borderlineCostumeRescue) {
                    android.util.Log.d(
                        "VisualFeatureDetector",
                        "Costume signature rescue accepted for $pokemonName: confidence=${signatureDetails.confidence}, bestCostume=${signatureDetails.bestCostume}, scoreGap=${signatureDetails.scoreGap}"
                    )
                    Pair(true, maxOf(signatureDetails.confidence, BORDERLINE_COSTUME_CONFIDENCE))
                } else if (sparseMatchedCostumeRescue) {
                    android.util.Log.d(
                        "VisualFeatureDetector",
                        "Sparse costume signature rescue accepted for $pokemonName: confidence=${signatureDetails.confidence}, bestCostume=${signatureDetails.bestCostume}, scoreGap=${signatureDetails.scoreGap}"
                    )
                    Pair(true, maxOf(signatureDetails.confidence, SPARSE_SIGNATURE_COSTUME_CONFIDENCE))
                } else {
                    Pair(signatureDetails.matched, signatureDetails.confidence)
                }
            } else {
                Pair(false, 0f)
            }
            val signatureResult = if (signatureResultRaw.first && signatureResultRaw.second < MIN_COSTUME_CONFIDENCE) {
                android.util.Log.d(
                    "VisualFeatureDetector",
                    "Costume signature rejected for $pokemonName: confidence=${signatureResultRaw.second}"
                )
                Pair(false, 0f)
            } else {
                signatureResultRaw
            }
            if (signatureResult.first) {
                signatureResult
            } else if (shouldCheckCostume && shouldUseCostumeHeuristic(signatureDetails, pokemonName)) {
                val heuristicResult = hasCostume(smallBitmap, pokemonName)
                if (heuristicResult.first) {
                    android.util.Log.d("VisualFeatureDetector", "Costume heuristic fallback accepted for $pokemonName")
                }
                heuristicResult
            } else if (shouldCheckCostume && signatureDetails != null) {
                android.util.Log.d(
                    "VisualFeatureDetector",
                    "Costume heuristic skipped for $pokemonName: bestCostume=${signatureDetails.bestCostume}, scoreGap=${signatureDetails.scoreGap}, dense=${signatureDetails.denseVariantSpecies}"
                )
                Pair(false, 0f)
            } else {
                Pair(false, 0f)
            }
        } else if (shouldCheckCostume) {
            val heuristicOnlyResult = hasCostume(smallBitmap, pokemonName)
            if (heuristicOnlyResult.first && heuristicOnlyResult.second < MIN_HEURISTIC_ONLY_COSTUME_CONFIDENCE) {
                android.util.Log.d(
                    "VisualFeatureDetector",
                    "Heuristic-only costume rejected for $pokemonName: confidence=${heuristicOnlyResult.second}"
                )
                Pair(false, 0f)
            } else {
                heuristicOnlyResult
            }
        } else {
            Pair(false, 0f)
        }

        val strictShiny = costumeResult.first && costumeResult.second >= 0.6f
        var shinyResult = if (ShinySignatureStore.hasSpecies(context, pokemonName)) {
            val signatureConsensus = chooseBestShinySignatureResult(
                primaryResult = ShinySignatureStore.matchSignature(spriteSignature, bodyHist, altColorHist, pokemonName, strictShiny),
                extraResults = listOf(
                    ShinySignatureStore.matchSignature(spriteSignature, maskedFullHist, null, pokemonName, strictShiny),
                    ShinySignatureStore.matchSignature(spriteSignature, upperHist, null, pokemonName, strictShiny)
                ),
                pokemonName = pokemonName
            )
            val maskedColorResult = isShinyByColor(bodyDominantColor, pokemonName)
            val rawColorResult = isShinyByColor(rawBodyDominantColor, pokemonName)
            val hueResult = isShinyByObservedHue(rawBodyHue, rawBodySat, rawBodyVal, pokemonName)
            val histHueResult = isShinyByHistogramHue(altColorHist, pokemonName)
            chooseShinyResult(signatureConsensus, maskedColorResult, rawColorResult, hueResult, histHueResult, pokemonName, costumeResult)
        } else {
            isShinyByColor(dominantColor, pokemonName)
        }
        if (!shinyResult.first) {
            val sparkleResult = hasShinySparkles(smallBitmap)
            if (sparkleResult.first) {
                android.util.Log.d("VisualFeatureDetector", "Shiny sparkle fallback accepted for $pokemonName")
                shinyResult = sparkleResult
            }
        }
        if (!shinyResult.first && pokemonName?.equals("Pikachu", true) == true) {
            val pikachuHueResult = isPikachuShinyByCentralHue(smallBitmap)
            if (pikachuHueResult.first) {
                android.util.Log.d("VisualFeatureDetector", "Pikachu central hue shiny fallback accepted")
                shinyResult = pikachuHueResult
            }
        }
        // Magikarp özel fallback'ı korunuyor
        if (!shinyResult.first && pokemonName?.equals("Magikarp", true) == true) {
            val yellowScore = altColorHist.getOrElse(1) { 0f } + altColorHist.getOrElse(2) { 0f }
            val redScore = altColorHist.getOrElse(0) { 0f } + altColorHist.getOrElse(11) { 0f }
            val histShiny = yellowScore >= 0.18f && yellowScore >= redScore * 1.35f
            if (histShiny) {
                shinyResult = Pair(true, 0.6f)
            } else {
                val hueShiny = rawBodyHue in 40f..75f && rawBodySat >= 0.25f && rawBodyVal >= 0.25f
                if (hueShiny) {
                    shinyResult = Pair(true, 0.6f)
                }
            }
        }

        // Shadow already evaluated at the beginning
        val luckyResult = isLucky(smallBitmap)
        val locationCardResult = if (luckyResult.first && luckyResult.second >= 0.35f) {
            android.util.Log.d(
                "VisualFeatureDetector",
                "Location card suppressed by lucky background: lucky=${luckyResult.second}"
            )
            Pair(false, 0f)
        } else {
            isLocationCard(smallBitmap)
        }

        if (bodySprite !== maskedSprite) bodySprite.recycle()
        if (upperSprite !== maskedSprite) upperSprite.recycle()
        maskedSprite.recycle()
        if (shouldRecycleSmall) {
            smallBitmap.recycle()
            if (smallBitmap !== smallBitmapRaw && smallBitmapRaw !== bitmap) {
                smallBitmapRaw.recycle()
            }
        }

        // Kombinasyonlu varyantlar için bağımsız skorlar ve işaretler
        val confidenceScores = mutableListOf<Float>()
        if (shinyResult.first) confidenceScores.add(shinyResult.second)
        if (shadowResult.first) confidenceScores.add(shadowResult.second)
        if (luckyResult.first) confidenceScores.add(luckyResult.second)
        if (costumeResult.first) confidenceScores.add(costumeResult.second)
        if (locationCardResult.first) confidenceScores.add(locationCardResult.second)

        val avgConfidence = if (confidenceScores.isNotEmpty()) confidenceScores.average().toFloat() else 1.0f

        android.util.Log.d("VisualFeatureDetector", "Results: shiny=${shinyResult.first}(${shinyResult.second}), shadow=${shadowResult.first}(${shadowResult.second}), lucky=${luckyResult.first}(${luckyResult.second}), costume=${costumeResult.first}(${costumeResult.second}), locationCard=${locationCardResult.first}(${locationCardResult.second}), avgConfidence=$avgConfidence")

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
        // Standard backgrounds:
        // Day: Green/Blue (Hue 80-220)
        // Night: Dark Blue/Purple (Hue 240-280)
        // Lucky: Gold (Hue 45-65)
        val standardRanges = listOf(80..220, 240..280, 45..65)
        val regions = ColorAnalyzer.getBackgroundCornerRegions(bitmap)
        val stats = regions.map { ColorAnalyzer.getHueStats(bitmap, it, standardRanges) }
            .filter { it.total > 0 }

        if (stats.isEmpty()) return Pair(false, 0f)

        val outsideRatio = stats.map { it.outsideStandardRatio }.average().toFloat()
        val avgSat = stats.map { it.avgSaturation }.average().toFloat()
        val avgVal = stats.map { it.avgValue }.average().toFloat()
        val strongCorners = stats.count {
            it.outsideStandardRatio >= 0.60f &&
                it.avgSaturation >= 0.40f &&
                it.avgValue >= 0.40f
        }
        val softStats = regions.map {
            ColorAnalyzer.getHueStats(
                bitmap = bitmap,
                region = it,
                standardRanges = standardRanges,
                minSaturation = 0.08f,
                minValue = 0.25f
            )
        }.filter { it.total > 0 }
        val themedBackground = if (softStats.isNotEmpty()) {
            val softOutsideRatio = softStats.map { it.outsideStandardRatio }.average().toFloat()
            val softAvgSat = softStats.map { it.avgSaturation }.average().toFloat()
            val softAvgVal = softStats.map { it.avgValue }.average().toFloat()
            val softCorners = softStats.count {
                it.outsideStandardRatio >= 0.35f &&
                    it.avgSaturation in 0.08f..0.28f &&
                    it.avgValue >= 0.30f
            }
            softOutsideRatio >= 0.35f &&
                softAvgSat in 0.08f..0.28f &&
                softAvgVal >= 0.30f &&
                softCorners >= 1
        } else {
            false
        }

        // Require a strong "non-standard" signal to avoid false positives
        val isLocationCard =
            (outsideRatio >= 0.50f && avgSat >= 0.35f && avgVal >= 0.35f && strongCorners >= 2) ||
                themedBackground
        val confidence = if (isLocationCard) {
            val rScore = ((outsideRatio - 0.40f) / 0.60f).coerceIn(0f, 1f)
            val sScore = ((avgSat - 0.30f) / 0.70f).coerceIn(0f, 1f)
            val vScore = ((avgVal - 0.30f) / 0.70f).coerceIn(0f, 1f)
            if (themedBackground && strongCorners < 2) {
                maxOf(0.35f, (0.5f * rScore + 0.25f * sScore + 0.25f * vScore)).coerceIn(0f, 1f)
            } else {
                (0.5f * rScore + 0.25f * sScore + 0.25f * vScore).coerceIn(0f, 1f)
            }
        } else {
            0f
        }

        return Pair(isLocationCard, confidence)
    }

    fun hasShinySparkles(bitmap: Bitmap): Pair<Boolean, Float> {
        val top = (bitmap.height * 0.05f).toInt().coerceAtLeast(0)
        val bottom = (bitmap.height * 0.45f).toInt().coerceAtMost(bitmap.height)
        val regionHeight = (bottom - top).coerceAtLeast(1)
        val bright = BooleanArray(bitmap.width * regionHeight)
        val visited = BooleanArray(bright.size)
        val hsv = FloatArray(3)

        for (y in top until bottom) {
            for (x in 0 until bitmap.width) {
                if (isExcludedSparkleUiArea(x, y, bitmap.width, bitmap.height)) continue
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val nearWhite = Color.red(pixel) >= 218 && Color.green(pixel) >= 218 && Color.blue(pixel) >= 218
                if (nearWhite && hsv[1] <= 0.22f && hsv[2] >= 0.82f) {
                    bright[(y - top) * bitmap.width + x] = true
                }
            }
        }

        var sparkleComponents = 0
        for (index in bright.indices) {
            if (!bright[index] || visited[index]) continue
            if (isSparkleComponent(index, bright, visited, bitmap.width, regionHeight)) {
                sparkleComponents++
            }
        }

        return if (sparkleComponents >= 2) {
            Pair(true, (0.48f + sparkleComponents * 0.12f).coerceAtMost(0.84f))
        } else {
            Pair(false, 0f)
        }
    }

    private fun isExcludedSparkleUiArea(x: Int, y: Int, width: Int, height: Int): Boolean {
        val cpText = x in (width * 0.32f).toInt()..(width * 0.68f).toInt() && y < height * 0.18f
        val cameraButton = x > width * 0.80f && y < height * 0.24f
        val lowerCard = y > height * 0.42f
        return cpText || cameraButton || lowerCard
    }

    private fun isSparkleComponent(
        startIndex: Int,
        bright: BooleanArray,
        visited: BooleanArray,
        width: Int,
        height: Int
    ): Boolean {
        val queue = ArrayDeque<Int>()
        queue.add(startIndex)
        visited[startIndex] = true
        var count = 0
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0

        while (!queue.isEmpty()) {
            val index = queue.removeFirst()
            val x = index % width
            val y = index / width
            count++
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val nextIndex = ny * width + nx
                    if (bright[nextIndex] && !visited[nextIndex]) {
                        visited[nextIndex] = true
                        queue.add(nextIndex)
                    }
                }
            }
        }

        val componentWidth = maxX - minX + 1
        val componentHeight = maxY - minY + 1
        return count in 4..220 && componentWidth in 4..56 && componentHeight in 4..56
    }

    private fun isPikachuShinyByCentralHue(bitmap: Bitmap): Pair<Boolean, Float> {
        val region = Rect(
            (bitmap.width * 0.42f).toInt(),
            (bitmap.height * 0.25f).toInt(),
            (bitmap.width * 0.58f).toInt(),
            (bitmap.height * 0.36f).toInt()
        )
        val orangeRatio = ColorAnalyzer.getColorPercentage(
            bitmap = bitmap,
            region = region,
            hueRange = 25..45,
            minSaturation = 0.45f,
            minValue = 0.35f
        )
        val yellowRatio = ColorAnalyzer.getColorPercentage(
            bitmap = bitmap,
            region = region,
            hueRange = 46..65,
            minSaturation = 0.45f,
            minValue = 0.35f
        )
        val isShiny = orangeRatio >= 0.32f && orangeRatio >= yellowRatio * 2.0f
        val confidence = if (isShiny) (0.58f + orangeRatio * 0.35f).coerceAtMost(0.82f) else 0f
        return Pair(isShiny, confidence)
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

        val isShiny = distToShiny < distToNormal && distToShiny < SHINY_COLOR_DIST_THRESHOLD

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

        // Also check average brightness - shadows tend to be darker, but daytime backgrounds can be bright 
        val avgBrightness = ColorAnalyzer.getAverageBrightness(bitmap, borderRegion)

        val isShadow = purplePercentage >= SHADOW_THRESHOLD

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
        val cardTextRegion = android.graphics.Rect(
            (bitmap.width * 0.24f).toInt(),
            (bitmap.height * 0.435f).toInt(),
            (bitmap.width * 0.66f).toInt(),
            (bitmap.height * 0.485f).toInt()
        )
        val cardIconRegion = android.graphics.Rect(
            (bitmap.width * 0.14f).toInt(),
            (bitmap.height * 0.425f).toInt(),
            (bitmap.width * 0.24f).toInt(),
            (bitmap.height * 0.49f).toInt()
        )
        val cardGreen = ColorAnalyzer.getColorPercentage(
            bitmap,
            cardTextRegion,
            85..165,
            0.22f,
            0.30f
        )
        val cardGold = ColorAnalyzer.getColorPercentage(
            bitmap,
            cardIconRegion,
            20..70,
            0.32f,
            0.45f
        )

        val focusRegion = ColorAnalyzer.getLuckyFocusRegion(bitmap)
        val focusYellowStrict = ColorAnalyzer.getColorPercentage(
            bitmap,
            focusRegion,
            43..68,
            0.45f,
            0.55f
        )
        val focusYellowSoft = ColorAnalyzer.getColorPercentage(
            bitmap,
            focusRegion,
            38..78,
            0.28f,
            0.35f
        )
        val supportYellow = ColorAnalyzer.getLuckySupportRegions(bitmap)
            .map { region ->
                ColorAnalyzer.getColorPercentage(
                    bitmap,
                    region,
                    38..78,
                    0.24f,
                    0.30f
                )
            }
        val supportYellowAvg = if (supportYellow.isNotEmpty()) supportYellow.average().toFloat() else 0f
        val supportYellowMax = supportYellow.maxOrNull() ?: 0f
        val upperCornerYellow = ColorAnalyzer.getBackgroundCornerRegions(bitmap)
            .map { region ->
                ColorAnalyzer.getColorPercentage(
                    bitmap,
                    region,
                    38..78,
                    0.28f,
                    0.35f
                )
            }
        val upperCornerAvg = if (upperCornerYellow.isNotEmpty()) upperCornerYellow.average().toFloat() else 0f
        val upperCornerMax = upperCornerYellow.maxOrNull() ?: 0f

        android.util.Log.d(
            "VisualFeatureDetector",
            "Lucky Analysis: cardGreen=$cardGreen, cardGold=$cardGold, focusStrict=$focusYellowStrict, focusSoft=$focusYellowSoft, supportAvg=$supportYellowAvg, supportMax=$supportYellowMax, upperCornerAvg=$upperCornerAvg, upperCornerMax=$upperCornerMax"
        )

        val cardLucky = (cardGreen >= 0.14f && cardGold >= 0.015f) ||
            (cardGreen >= 0.24f && upperCornerAvg <= 0.10f)
        val isLucky = cardLucky ||
            ((focusYellowStrict >= 0.11f && supportYellowAvg >= 0.03f) && upperCornerAvg <= 0.18f) ||
            ((focusYellowSoft >= 0.18f && supportYellowMax >= 0.08f) && upperCornerAvg <= 0.18f) ||
            ((supportYellowMax >= 0.20f && upperCornerAvg >= 0.05f) && upperCornerAvg <= 0.18f)

        val confidence = if (isLucky) {
            val cardGreenScore = (cardGreen / 0.05f).coerceIn(0f, 1f)
            val cardGoldScore = (cardGold / 0.10f).coerceIn(0f, 1f)
            val strictScore = (focusYellowStrict / 0.14f).coerceIn(0f, 1f)
            val softScore = (focusYellowSoft / 0.16f).coerceIn(0f, 1f)
            val supportScore = (supportYellowAvg / 0.12f).coerceIn(0f, 1f)
            val supportMaxScore = (supportYellowMax / 0.18f).coerceIn(0f, 1f)
            val upperScore = (upperCornerAvg / 0.08f).coerceIn(0f, 1f)
            if (cardLucky) {
                (0.55f * cardGreenScore + 0.20f * cardGoldScore + 0.10f * softScore + 0.15f * upperScore).coerceIn(0.45f, 0.98f)
            } else {
                (0.30f * strictScore + 0.25f * softScore + 0.25f * supportScore + 0.15f * supportMaxScore + 0.05f * upperScore).coerceIn(0.25f, 0.95f)
            }
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

        val reference = pokemonColors[pokemonName]

        val spriteRegion = ColorAnalyzer.getSpriteRegionAdaptive(bitmap)
        val spriteBitmap = Bitmap.createBitmap(
            bitmap,
            spriteRegion.left,
            spriteRegion.top,
            spriteRegion.width(),
            spriteRegion.height()
        )
        val bodyRegion = android.graphics.Rect(
            0,
            (spriteBitmap.height * 0.35f).toInt().coerceIn(0, spriteBitmap.height.coerceAtLeast(1) - 1),
            spriteBitmap.width,
            spriteBitmap.height
        )
        val headRegion = android.graphics.Rect(
            0,
            0,
            spriteBitmap.width,
            (spriteBitmap.height * 0.35f).toInt().coerceIn(1, spriteBitmap.height)
        )

        val headDominant = ColorAnalyzer.getDominantHue(spriteBitmap, headRegion)
        val bodyDominant = ColorAnalyzer.getDominantHue(spriteBitmap, bodyRegion)

        val referenceHue = if (reference != null) {
            val normalHSV = FloatArray(3)
            android.graphics.Color.RGBToHSV(reference.normal[0], reference.normal[1], reference.normal[2], normalHSV)
            normalHSV[0]
        } else {
            bodyDominant
        }

        val hueDistToRef = hueDistance(headDominant, referenceHue)
        val hueDistToBody = hueDistance(headDominant, bodyDominant)
        val hueDist = hueDistToBody

        val headSat = ColorAnalyzer.getAverageSaturation(spriteBitmap, headRegion)
        val bodySat = ColorAnalyzer.getAverageSaturation(spriteBitmap, bodyRegion)
        spriteBitmap.recycle()
        
        android.util.Log.d(
            "VisualFeatureDetector",
            "Costume Analysis for $pokemonName: HeadHue=$headDominant, BodyHue=$bodyDominant, RefHue=$referenceHue, DistRef=$hueDistToRef, DistBody=$hueDistToBody, Dist=$hueDist, HeadSat=$headSat, BodySat=$bodySat"
        )

        val minHueDist = 24f
        val minRefHueDist = 14f
        val minHeadSat = 0.22f
        val minBodySat = 0.03f
        val headDiffersFromReference = reference == null || hueDistToRef >= minRefHueDist
        val hasCostume = hueDist > minHueDist &&
            headDiffersFromReference &&
            headSat > minHeadSat &&
            bodySat > minBodySat

        val confidence = if (hasCostume) {
            val hueScore = ((hueDist - minHueDist) / 90f).coerceIn(0f, 1f)
            val refScore = if (reference == null) 1f else ((hueDistToRef - minRefHueDist) / 90f).coerceIn(0f, 1f)
            val satScore = ((headSat - minHeadSat) / 0.6f).coerceIn(0f, 1f)
            val bodyScore = ((bodySat - minBodySat) / 0.20f).coerceIn(0f, 1f)
            (0.18f + 0.30f * hueScore + 0.22f * refScore + 0.18f * satScore + 0.12f * bodyScore).coerceIn(0.35f, 0.9f)
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
    private fun computeHeadPHash(bitmap: Bitmap): String {
        val height = (bitmap.height * 0.35f).toInt().coerceIn(1, bitmap.height)
        val head = if (height == bitmap.height) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, height)
        return try {
            PerceptualHash.compute(head)
        } finally {
            if (head !== bitmap) {
                head.recycle()
            }
        }
    }

    private fun hueDistance(h1: Float, h2: Float): Float {
        val diff = abs(h1 - h2)
        return min(diff, 360f - diff)
    }

    internal fun chooseShinyResult(
        signatureConsensus: SignatureConsensus,
        maskedColorResult: Pair<Boolean, Float>,
        rawColorResult: Pair<Boolean, Float>,
        hueResult: Pair<Boolean, Float>,
        histHueResult: Pair<Boolean, Float>,
        pokemonName: String?,
        costumeResult: Pair<Boolean, Float>
    ): Pair<Boolean, Float> {
        val signatureResult = signatureConsensus.result
        if (signatureResult.first) {
            val supportCount = listOf(maskedColorResult, rawColorResult, hueResult, histHueResult).count { it.first }
            val strongestSupport = listOf(maskedColorResult, rawColorResult, hueResult, histHueResult)
                .filter { it.first }
                .maxOfOrNull { it.second } ?: 0f
            val signatureOnlyAccepted =
                supportCount > 0 ||
                    (signatureConsensus.primaryMatched && signatureResult.second >= 0.95f)
            if (!signatureOnlyAccepted) {
                android.util.Log.d(
                    "VisualFeatureDetector",
                    "Signature-only shiny rejected for $pokemonName: signature=${signatureResult.second}, supports=$supportCount, strongestSupport=$strongestSupport, consensus=${signatureConsensus.matchedCount}, primaryMatched=${signatureConsensus.primaryMatched}, costume=${costumeResult.first}/${costumeResult.second}"
                )
                return Pair(false, 0f)
            }
            return signatureResult
        }

        val reference = pokemonName?.let { pokemonColors[it] } ?: return signatureResult
        val normalHue = rgbHue(reference.normal)
        val shinyHue = rgbHue(reference.shiny)
        val refHueGap = hueDistance(normalHue, shinyHue)
        val refRgbGap = rgbDistance(reference.normal, reference.shiny)
        val allowFallback = refHueGap >= 28f || refRgbGap >= 90.0
        if (!allowFallback) return signatureResult

        val fallback = listOf(maskedColorResult, rawColorResult, hueResult, histHueResult)
            .filter { it.first }
            .maxByOrNull { it.second }
        val supportCount = listOf(maskedColorResult, rawColorResult, hueResult, histHueResult).count { it.first }
        val hueSupported = hueResult.first || histHueResult.first
        val histOnlyFallback = histHueResult.first &&
            !maskedColorResult.first &&
            !rawColorResult.first &&
            !hueResult.first
        val acceptedFallback = when {
            fallback == null -> null
            histOnlyFallback && fallback == histHueResult && histHueResult.second < 0.90f -> null
            !hueSupported -> null
            supportCount < 2 -> null
            fallback.second < 0.78f -> null
            else -> fallback
        }

        if (acceptedFallback != null) {
            android.util.Log.d(
                "VisualFeatureDetector",
                "Shiny fallback accepted for $pokemonName: signature=${signatureResult.second}, masked=${maskedColorResult.second}, raw=${rawColorResult.second}, hue=${hueResult.second}, hist=${histHueResult.second}"
            )
        } else if (fallback != null) {
            android.util.Log.d(
                "VisualFeatureDetector",
                "Shiny fallback rejected for $pokemonName: signature=${signatureResult.second}, masked=${maskedColorResult.second}, raw=${rawColorResult.second}, hue=${hueResult.second}, hist=${histHueResult.second}, supports=$supportCount, hueSupported=$hueSupported"
            )
        }

        return acceptedFallback ?: signatureResult
    }

    internal fun chooseBestShinySignatureResult(
        primaryResult: Pair<Boolean, Float>,
        extraResults: List<Pair<Boolean, Float>>,
        pokemonName: String?
    ): SignatureConsensus {
        val candidates = listOf(primaryResult) + extraResults
        val matched = candidates.filter { it.first }
        if (matched.isNotEmpty()) {
            val alternateMatches = extraResults.filter { it.first }
            val consensusMatch = matched.size >= 2
            val strongPrimary = primaryResult.first
            if (!consensusMatch && !strongPrimary && alternateMatches.isNotEmpty()) {
                android.util.Log.d(
                    "VisualFeatureDetector",
                    "Alternate shiny signature rejected for $pokemonName: primary=${primaryResult.second}, alternates=${alternateMatches.joinToString { it.second.toString() }}"
                )
                return SignatureConsensus(
                    result = primaryResult,
                    matchedCount = matched.size,
                    primaryMatched = primaryResult.first
                )
            }
            val best = matched.maxByOrNull { it.second } ?: primaryResult
            if (best != primaryResult) {
                android.util.Log.d(
                    "VisualFeatureDetector",
                    "Alternate shiny signature accepted for $pokemonName: primary=${primaryResult.second}, chosen=${best.second}"
                )
            }
            return SignatureConsensus(
                result = best,
                matchedCount = matched.size,
                primaryMatched = primaryResult.first
            )
        }
        return SignatureConsensus(
            result = primaryResult,
            matchedCount = 0,
            primaryMatched = primaryResult.first
        )
    }

    internal fun shouldUseCostumeHeuristic(
        signatureDetails: CostumeSignatureStore.MatchDetails?,
        pokemonName: String?
    ): Boolean {
        if (signatureDetails == null || pokemonName.isNullOrBlank()) return true
        val bestCostume = signatureDetails.bestCostume
        val scoreGap = signatureDetails.scoreGap

        val allowed = if (signatureDetails.denseVariantSpecies) {
            (bestCostume <= 0.31f && scoreGap >= 0.018f) ||
                (bestCostume <= 0.38f && scoreGap >= -0.015f)
        } else {
            bestCostume <= 0.33f && scoreGap >= 0.012f
        }

        return allowed
    }

    private fun isShinyByObservedHue(
        observedHue: Float,
        observedSat: Float,
        observedVal: Float,
        pokemonName: String?
    ): Pair<Boolean, Float> {
        if (pokemonName == null) return Pair(false, 0f)
        val reference = pokemonColors[pokemonName] ?: return Pair(false, 0f)
        if (observedSat < 0.20f || observedVal < 0.20f) return Pair(false, 0f)

        val normalHue = rgbHue(reference.normal)
        val shinyHue = rgbHue(reference.shiny)
        val refHueGap = hueDistance(normalHue, shinyHue)
        if (refHueGap < 28f) return Pair(false, 0f)

        val distToNormal = hueDistance(observedHue, normalHue)
        val distToShiny = hueDistance(observedHue, shinyHue)
        val shinyWin = distToShiny + 12f < distToNormal && distToShiny <= 55f
        if (!shinyWin) return Pair(false, 0f)

        val gapScore = ((refHueGap - 28f) / 120f).coerceIn(0f, 1f)
        val winScore = ((distToNormal - distToShiny - 12f) / 90f).coerceIn(0f, 1f)
        val confidence = (0.35f + 0.35f * gapScore + 0.30f * winScore).coerceIn(0f, 0.85f)
        return Pair(true, confidence)
    }

    private fun isShinyByHistogramHue(
        colorHist: FloatArray,
        pokemonName: String?
    ): Pair<Boolean, Float> {
        if (pokemonName == null) return Pair(false, 0f)
        val reference = pokemonColors[pokemonName] ?: return Pair(false, 0f)

        val normalHue = rgbHue(reference.normal)
        val shinyHue = rgbHue(reference.shiny)
        val refHueGap = hueDistance(normalHue, shinyHue)
        if (refHueGap < 40f) return Pair(false, 0f)

        val normalMass = histogramMassNearHue(colorHist, normalHue)
        val shinyMass = histogramMassNearHue(colorHist, shinyHue)
        android.util.Log.d(
            "VisualFeatureDetector",
            "Shiny Hist Analysis for $pokemonName: normalMass=$normalMass, shinyMass=$shinyMass, refGap=$refHueGap"
        )

        val matched = shinyMass >= 0.10f && shinyMass > normalMass + 0.05f
        if (!matched) return Pair(false, 0f)

        val diffScore = ((shinyMass - normalMass - 0.05f) / 0.25f).coerceIn(0f, 1f)
        val massScore = ((shinyMass - 0.10f) / 0.30f).coerceIn(0f, 1f)
        val confidence = (0.25f + 0.45f * diffScore + 0.30f * massScore).coerceIn(0.3f, 0.8f)
        return Pair(true, confidence)
    }

    private fun rgbHue(rgb: List<Int>): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(rgb[0], rgb[1], rgb[2], hsv)
        return hsv[0]
    }

    private fun rgbDistance(a: List<Int>, b: List<Int>): Double {
        val dr = (a[0] - b[0]).toDouble()
        val dg = (a[1] - b[1]).toDouble()
        val db = (a[2] - b[2]).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }

    private fun histogramMassNearHue(hist: FloatArray, hue: Float): Float {
        if (hist.isEmpty()) return 0f
        val bins = hist.size
        val center = ((hue / 360f) * bins).toInt().floorMod(bins)
        var sum = 0f
        for (offset in -1..1) {
            sum += hist[(center + offset).floorMod(bins)]
        }
        return sum.coerceIn(0f, 1f)
    }

    private fun Int.floorMod(mod: Int): Int {
        val value = this % mod
        return if (value < 0) value + mod else value
    }

    /**
     * Load pokemon colors from rarity_manifest.json.
     */
    private fun loadPokemonColors(): Map<String, ColorReference> {
        return try {
            val input = context.assets.open("data/rarity_manifest.json")
            val reader = InputStreamReader(input)
            val json = com.google.gson.JsonParser.parseReader(reader).asJsonObject
            val map = mutableMapOf<String, ColorReference>()
            if (json.has("pokemonColors")) {
                val colorsJson = json.getAsJsonObject("pokemonColors")
                for ((species, value) in colorsJson.entrySet()) {
                    if (species.startsWith("_") || !value.isJsonObject) continue
                    val obj = value.asJsonObject
                    val normal = obj.getAsJsonArray("normal")?.map { it.asInt } ?: continue
                    val shiny = obj.getAsJsonArray("shiny")?.map { it.asInt } ?: continue
                    if (normal.size == 3 && shiny.size == 3) {
                        map[species] = ColorReference(normal, shiny)
                    }
                }
            }
            loadGeneratedPokemonColors().forEach { (species, colorRef) ->
                map.putIfAbsent(species, colorRef)
            }
            android.util.Log.d("VisualFeatureDetector", "Loaded ${map.size} pokemon color definitions")
            map
        } catch (e: Exception) {
            android.util.Log.e("VisualFeatureDetector", "Load pokemon colors failed", e)
            emptyMap()
        }
    }

    private fun loadGeneratedPokemonColors(): Map<String, ColorReference> {
        return try {
            val input = context.assets.open(GENERATED_COLORS_PATH)
            val reader = InputStreamReader(input)
            val json = com.google.gson.JsonParser.parseReader(reader).asJsonObject
            buildMap {
                for ((species, value) in json.entrySet()) {
                    if (!value.isJsonObject) continue
                    val obj = value.asJsonObject
                    val normal = obj.getAsJsonArray("normal")?.map { it.asInt } ?: continue
                    val shiny = obj.getAsJsonArray("shiny")?.map { it.asInt } ?: continue
                    if (normal.size == 3 && shiny.size == 3) {
                        put(species, ColorReference(normal, shiny))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("VisualFeatureDetector", "Generated pokemon colors missing or unreadable", e)
            emptyMap()
        }
    }
}
