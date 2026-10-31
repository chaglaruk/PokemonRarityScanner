package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityManifestLoader
import java.util.ArrayDeque
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.min

private const val ABSOLUTE_SHINY_SIGNATURE_REASON = "absolute_score_passed"

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
        val primaryMatched: Boolean,
        val primaryConfidence: Float,
        val chosenFromPrimary: Boolean,
        val selectedSource: String = "primary",
        val rejectionReason: String? = null,
        val primaryReasonCode: String? = null,
        val selectedReasonCode: String? = null
    )

    internal data class ShinyChoiceDetails(
        val result: Pair<Boolean, Float>,
        val colorFallbackCalled: Boolean,
        val colorFallbackReason: String,
        val selectedPath: String
    )

    private data class ColorFallbackDetails(
        val result: Pair<Boolean, Float>?,
        val reasonCode: String
    )

    internal data class ShinyColorEvidence(
        val masked: Pair<Boolean, Float>,
        val raw: Pair<Boolean, Float>,
        val hue: Pair<Boolean, Float>,
        val histogramHue: Pair<Boolean, Float>
    )

    companion object {
        private const val GENERATED_COLORS_PATH = "data/pokemon_colors_generated.json"
        private const val MIN_COSTUME_CONFIDENCE = 0.20f
        private const val BORDERLINE_COSTUME_CONFIDENCE = 0.24f
        private const val MIN_HEURISTIC_ONLY_COSTUME_CONFIDENCE = 0.82f

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
    fun detect(bitmap: Bitmap, pokemonName: String? = null, sizeTag: String? = null): VisualFeatures =
        detect(bitmap, pokemonName, sizeTag, null, null)

    @Suppress("LongMethod", "CyclomaticComplexMethod", "MaxLineLength")
    // Diagnostics follow the same ordered detector decisions.
    internal fun detect(
        bitmap: Bitmap,
        pokemonName: String?,
        sizeTag: String?,
        diagnostics: VariantStageDiagnosticRecorder?,
        lockedSpecies: String?
    ): VisualFeatures {
        diagnostics?.recordSpeciesAuthority(lockedSpecies)
        diagnostics?.recordDetectorInput(pokemonName, bitmap.width, bitmap.height)
        val smallBitmapRaw = ColorAnalyzer.downscaleForAnalysis(bitmap)
        val shadowResult = isShadow(smallBitmapRaw)
        
        val smallBitmap = if (shadowResult.first) {
            ColorAnalyzer.maskShadowFlames(smallBitmapRaw)
        } else {
            smallBitmapRaw
        }
        val shouldRecycleSmall = smallBitmap !== bitmap
        val spriteRegion = ColorAnalyzer.getSpriteRegionAdaptive(smallBitmap)
        val maskedSprite = ColorAnalyzer.maskSpriteBackground(smallBitmap, spriteRegion)
        diagnostics?.recordSpriteCrop(maskedSprite.width, maskedSprite.height)

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
        val rawSprite = Bitmap.createBitmap(smallBitmap, spriteRegion.left, spriteRegion.top, spriteRegion.width(), spriteRegion.height())
        diagnostics?.captureExactFrame(
            QaExactFrameCaptureInput(
                detectorInput = bitmap,
                maskedSprite = maskedSprite,
                analysisRegion = rawSprite,
                crop = spriteRegion,
                analysisWidth = smallBitmap.width,
                analysisHeight = smallBitmap.height,
                shadowMaskApplied = shadowResult.first
            )
        )
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
        var costumeDetails: CostumeSignatureStore.MatchDetails? = null
        var costumeHeuristicPath = "not_applicable"
        var costumeSignatureDecisionPath = "signature_reference_missing"
        var costumeHeuristicCalled = false
        var costumeHeuristicResult: Pair<Boolean, Float>? = null
        val costumeResult = if (hasSignatureSpecies) {
            val signatureDetails = CostumeSignatureStore.matchSignatureDetails(
                signature = spriteSignature,
                species = pokemonName,
                pHash = spritePHash,
                headPHash = costumeHeadPHash
            )
            costumeDetails = signatureDetails
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
            val upperHeadAccessoryCue = sparseMatchedCostumeRescue &&
                hasUpperHeadRedAccessoryCue(smallBitmap)
            val signatureResultRaw = if (signatureDetails != null) {
                if (borderlineCostumeRescue) {
                    Pair(true, maxOf(signatureDetails.confidence, BORDERLINE_COSTUME_CONFIDENCE))
                } else if (upperHeadAccessoryCue) {
                    Pair(true, maxOf(signatureDetails.confidence, MIN_COSTUME_CONFIDENCE))
                } else if (sparseMatchedCostumeRescue) {
                    Pair(true, signatureDetails.confidence)
                } else {
                    Pair(signatureDetails.matched, signatureDetails.confidence)
                }
            } else {
                Pair(false, 0f)
            }
            val signatureResult = if (signatureResultRaw.first && signatureResultRaw.second < MIN_COSTUME_CONFIDENCE) {
                Pair(false, 0f)
            } else {
                signatureResultRaw
            }
            costumeSignatureDecisionPath = when {
                signatureResult.first && borderlineCostumeRescue -> "borderline_costume_rescue"
                signatureResult.first && upperHeadAccessoryCue -> "upper_head_red_accessory_rescue"
                signatureResult.first && sparseMatchedCostumeRescue -> "sparse_matched_costume_rescue"
                signatureResult.first -> "direct_signature_match"
                else -> "signature_rejected_${signatureDetails?.rejectionReason ?: "details_missing"}"
            }
            if (signatureResult.first) {
                costumeHeuristicPath = "heuristic_not_invoked_signature_positive"
                signatureResult
            } else if (shouldCheckCostume && shouldUseCostumeHeuristic(signatureDetails, pokemonName)) {
                costumeHeuristicPath = "signature_rejected_heuristic_allowed"
                costumeHeuristicCalled = true
                hasCostume(smallBitmap, pokemonName).also { costumeHeuristicResult = it }
            } else if (shouldCheckCostume && signatureDetails != null) {
                costumeHeuristicPath = "signature_rejected_heuristic_not_allowed"
                Pair(false, 0f)
            } else {
                costumeHeuristicPath = "signature_details_missing"
                Pair(false, 0f)
            }
        } else if (shouldCheckCostume) {
            costumeHeuristicPath = "heuristic_only"
            costumeHeuristicCalled = true
            val heuristicOnlyResult = hasCostume(smallBitmap, pokemonName).also { costumeHeuristicResult = it }
            if (heuristicOnlyResult.first && heuristicOnlyResult.second < MIN_HEURISTIC_ONLY_COSTUME_CONFIDENCE) {
                Pair(false, 0f)
            } else {
                heuristicOnlyResult
            }
        } else {
            Pair(false, 0f)
        }

        val strictShiny = costumeResult.first && costumeResult.second >= 0.6f
        val strictShinyReason = when {
            strictShiny -> "costume_true_confidence_at_least_0_60"
            !costumeResult.first -> "costume_not_confirmed"
            else -> "costume_confidence_below_0_60"
        }
        diagnostics?.recordCostumeSignature(
            costumeDetails.toQaCostumeStage(
                referenceAvailable = hasSignatureSpecies,
                rejectionReason = if (hasSignatureSpecies) "costume_details_missing" else "species_reference_missing"
            )
        )
        diagnostics?.recordCostumeDecision(
            QaCostumeDecision(
                matched = costumeResult.first,
                confidence = costumeResult.second,
                heuristicPath = costumeHeuristicPath,
                signatureDecisionPath = costumeSignatureDecisionPath,
                heuristicCalled = costumeHeuristicCalled,
                heuristicResult = costumeHeuristicResult,
                strictShiny = strictShiny,
                strictShinyReason = strictShinyReason
            )
        )

        var shinyDecisionPath = "signature_detector"
        val hasShinySignature = ShinySignatureStore.hasSpecies(context, pokemonName)
        var shinyResult = if (hasShinySignature) {
            val primaryDetails = ShinySignatureStore.matchSignatureDetails(
                spriteSignature, bodyHist, altColorHist, pokemonName, strictShiny
            )
            val alternateDetails = listOf(
                ShinySignatureStore.matchSignatureDetails(
                    spriteSignature,
                    maskedFullHist,
                    null,
                    pokemonName,
                    strictShiny
                ),
                ShinySignatureStore.matchSignatureDetails(
                    spriteSignature,
                    upperHist,
                    null,
                    pokemonName,
                    strictShiny
                )
            )
            val signatureConsensus = chooseBestShinySignatureResult(
                primaryResult = Pair(primaryDetails.matched, primaryDetails.confidence),
                extraResults = alternateDetails.map { Pair(it.matched, it.confidence) },
                primaryReasonCode = primaryDetails.reasonCode,
                extraReasonCodes = alternateDetails.map { it.reasonCode }
            )
            val maskedColorResult = isShinyByColor(bodyDominantColor, pokemonName)
            val rawColorResult = isShinyByColor(rawBodyDominantColor, pokemonName)
            val hueResult = isShinyByObservedHue(rawBodyHue, rawBodySat, rawBodyVal, pokemonName)
            val histHueResult = isShinyByHistogramHue(altColorHist, pokemonName)
            diagnostics?.recordColorEvidence(maskedColorResult, rawColorResult, hueResult, histHueResult)
            diagnostics?.recordShinySignature(
                QaShinySignatureStage(
                    referenceStatus = if (primaryDetails.referenceAvailable) "available" else "missing",
                    referenceKey = primaryDetails.referenceKey,
                    strictShiny = strictShiny,
                    primary = primaryDetails.toQaMatch(),
                    alternates = alternateDetails.map(ShinySignatureStore.MatchDetails::toQaMatch),
                    selectedSource = signatureConsensus.selectedSource,
                    selectedMatched = signatureConsensus.result.first,
                    selectedConfidence = signatureConsensus.result.second,
                    rejectionReason = signatureConsensus.rejectionReason
                )
            )
            chooseShinyResult(
                signatureConsensus,
                ShinyColorEvidence(
                    masked = maskedColorResult,
                    raw = rawColorResult,
                    hue = hueResult,
                    histogramHue = histHueResult
                ),
                pokemonName,
                diagnostics
            )
        } else {
            shinyDecisionPath = "direct_color_without_signature_reference"
            diagnostics?.recordShinySignature(
                QaShinySignatureStage(
                    referenceStatus = if (pokemonName.isNullOrBlank()) "species_missing" else "missing",
                    referenceKey = null,
                    strictShiny = strictShiny,
                    primary = null,
                    alternates = emptyList(),
                    selectedSource = "signature_store_bypassed",
                    selectedMatched = false,
                    selectedConfidence = 0f,
                    rejectionReason = "species_reference_missing_direct_color_path"
                )
            )
            val colorResult = isShinyByColor(dominantColor, pokemonName)
            val rawColor = isShinyByColor(rawBodyDominantColor, pokemonName)
            val hue = isShinyByObservedHue(rawBodyHue, rawBodySat, rawBodyVal, pokemonName)
            val histHue = isShinyByHistogramHue(altColorHist, pokemonName)
            diagnostics?.recordColorEvidence(colorResult, rawColor, hue, histHue)
            diagnostics?.recordShinyChoice(
                colorResult.first,
                colorResult.second,
                fallbackCalled = false,
                fallbackReason = "signature_reference_unavailable_direct_color_path",
                decisionPath = shinyDecisionPath
            )
            colorResult
        }
        if (!shinyResult.first && pokemonName.isNullOrBlank()) {
            val sparkleResult = hasShinySparkles(smallBitmap)
            if (sparkleResult.first) {
                shinyResult = sparkleResult
                shinyDecisionPath = "sparkle_fallback"
            }
        }
        // Magikarp özel fallback'ı korunuyor
        if (!shinyResult.first && pokemonName?.equals("Magikarp", true) == true) {
            val yellowScore = altColorHist.getOrElse(1) { 0f } + altColorHist.getOrElse(2) { 0f }
            val redScore = altColorHist.getOrElse(0) { 0f } + altColorHist.getOrElse(11) { 0f }
            val histShiny = yellowScore >= 0.18f && yellowScore >= redScore * 1.35f
            if (histShiny) {
                shinyResult = Pair(true, 0.6f)
                shinyDecisionPath = "magikarp_histogram_fallback"
            } else {
                val hueShiny = rawBodyHue in 40f..75f && rawBodySat >= 0.25f && rawBodyVal >= 0.25f
                if (hueShiny) {
                    shinyResult = Pair(true, 0.6f)
                    shinyDecisionPath = "magikarp_hue_fallback"
                }
            }
        }
        if (shinyDecisionPath == "sparkle_fallback" || shinyDecisionPath.startsWith("magikarp_")) {
            diagnostics?.recordShinyChoice(
                shinyResult.first,
                shinyResult.second,
                fallbackCalled = false,
                fallbackReason = shinyDecisionPath,
                decisionPath = shinyDecisionPath
            )
        }

        // Shadow already evaluated at the beginning
        val luckyResult = isLucky(smallBitmap)
        val locationCardResult = if (luckyResult.first && luckyResult.second >= 0.35f) {
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
        diagnostics?.recordOverallVisualConfidence(avgConfidence)

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
        colorEvidence: ShinyColorEvidence,
        pokemonName: String?,
        diagnostics: VariantStageDiagnosticRecorder? = null
    ): Pair<Boolean, Float> {
        val details = chooseShinyDecision(signatureConsensus, colorEvidence, pokemonName)
        diagnostics?.recordShinyChoice(
            matched = details.result.first,
            confidence = details.result.second,
            fallbackCalled = details.colorFallbackCalled,
            fallbackReason = details.colorFallbackReason,
            decisionPath = details.selectedPath
        )
        return details.result
    }

    internal fun chooseShinyDecision(
        signatureConsensus: SignatureConsensus,
        colorEvidence: ShinyColorEvidence,
        pokemonName: String?
    ): ShinyChoiceDetails {
        val signatureResult = signatureConsensus.result
        val strongPrimaryPositive = signatureResult.first && isStrongPrimarySignature(signatureConsensus)
        val weakPositiveSignature = signatureResult.first && !strongPrimaryPositive
        val decisiveSignature = signatureResult.takeIf { strongPrimaryPositive }
        if (decisiveSignature != null) {
            return ShinyChoiceDetails(
                result = decisiveSignature,
                colorFallbackCalled = false,
                colorFallbackReason = "skipped_strong_primary_signature_positive",
                selectedPath = "strong_primary_signature"
            )
        }
        val fallback = chooseColorFallback(signatureConsensus, colorEvidence, pokemonName)
        val selectedResult = fallback.result ?: if (weakPositiveSignature) Pair(false, 0f) else signatureResult
        return ShinyChoiceDetails(
            result = selectedResult,
            colorFallbackCalled = true,
            colorFallbackReason = fallback.reasonCode,
            selectedPath = when {
                fallback.result != null -> "color_fallback"
                weakPositiveSignature -> "weak_signature_rejected_after_unsupported_color_fallback"
                else -> "signature_result"
            }
        )
    }

    internal fun chooseBestShinySignatureResult(
        primaryResult: Pair<Boolean, Float>,
        extraResults: List<Pair<Boolean, Float>>,
        primaryReasonCode: String? = null,
        extraReasonCodes: List<String?> = emptyList()
    ): SignatureConsensus {
        val candidates = listOf(primaryResult) + extraResults
        val matched = candidates.filter { it.first }
        val unsupportedAlternateOnly = matched.size == 1 &&
            !primaryResult.first &&
            extraResults.any { it.first }
        val chosenIndex = if (matched.isEmpty() || unsupportedAlternateOnly) {
            0
        } else {
            candidates.indices.filter { candidates[it].first }.maxBy { candidates[it].second }
        }
        val selectedSource = when (chosenIndex) {
            0 -> "primary"
            1 -> "alternate_masked_full"
            else -> "alternate_upper"
        }
        return SignatureConsensus(
            result = candidates[chosenIndex],
            matchedCount = matched.size,
            primaryMatched = primaryResult.first,
            primaryConfidence = primaryResult.second,
            chosenFromPrimary = chosenIndex == 0,
            selectedSource = selectedSource,
            primaryReasonCode = primaryReasonCode,
            selectedReasonCode = if (chosenIndex == 0) {
                primaryReasonCode
            } else {
                extraReasonCodes.getOrNull(chosenIndex - 1)
            },
            rejectionReason = when {
                unsupportedAlternateOnly -> "alternate_only_positive_rejected_without_primary_support"
                matched.isEmpty() -> "no_signature_match"
                else -> null
            }
        )
    }

    // Signature confidence measures score separation, not calibrated correctness. A relative win can
    // be driven by accessory colors, so only an absolute score pass may bypass corroboration.
    private fun isStrongPrimarySignature(consensus: SignatureConsensus): Boolean =
        consensus.chosenFromPrimary &&
            consensus.primaryMatched &&
            consensus.primaryReasonCode == ABSOLUTE_SHINY_SIGNATURE_REASON

    private fun chooseColorFallback(
        consensus: SignatureConsensus,
        evidence: ShinyColorEvidence,
        pokemonName: String?
    ): ColorFallbackDetails {
        val reference = pokemonName?.let { pokemonColors[it] }
        val primaryIsUsable = consensus.primaryMatched && consensus.primaryConfidence >= 0.55f
        val rejectionReason = when {
            reference == null || !hasDistinctShinyReference(reference) ->
                "color_fallback_blocked_missing_distinct_species_reference"
            !primaryIsUsable -> "color_fallback_blocked_primary_signature_unmatched_or_below_0_55"
            else -> null
        }
        return rejectionReason?.let { ColorFallbackDetails(null, it) }
            ?: chooseSupportedColorFallback(evidence)
    }

    private fun hasDistinctShinyReference(reference: ColorReference): Boolean {
        val normalHue = rgbHue(reference.normal)
        val shinyHue = rgbHue(reference.shiny)
        val hueGap = hueDistance(normalHue, shinyHue)
        val rgbGap = rgbDistance(reference.normal, reference.shiny)
        return hueGap >= 28f || rgbGap >= 90.0
    }

    private fun chooseSupportedColorFallback(evidence: ShinyColorEvidence): ColorFallbackDetails {
        val signals = listOf(evidence.masked, evidence.raw, evidence.hue, evidence.histogramHue)
        val fallback = signals.filter { it.first }.maxByOrNull { it.second }
        // Hue and histogram hue come from the same color family and cannot corroborate each other.
        val bodyColorSupported = evidence.masked.first || evidence.raw.first
        val hueSupported = evidence.hue.first || evidence.histogramHue.first
        val histogramOnlyFallback = evidence.histogramHue.first &&
            !evidence.masked.first &&
            !evidence.raw.first &&
            !evidence.hue.first
        val rejectionReason = colorFallbackRejectionReason(
            evidence = evidence,
            fallback = fallback,
            histogramOnlyFallback = histogramOnlyFallback,
            bodyColorSupported = bodyColorSupported,
            hueSupported = hueSupported,
        )
        return if (rejectionReason == null) {
            ColorFallbackDetails(checkNotNull(fallback), "color_fallback_positive_supported_by_rgb_and_hue_groups")
        } else {
            ColorFallbackDetails(null, rejectionReason)
        }
    }

    private fun colorFallbackRejectionReason(
        evidence: ShinyColorEvidence,
        fallback: Pair<Boolean, Float>?,
        histogramOnlyFallback: Boolean,
        bodyColorSupported: Boolean,
        hueSupported: Boolean
    ): String? = when {
        fallback == null -> "color_fallback_no_positive_color_signal"
        histogramOnlyFallback && fallback == evidence.histogramHue && evidence.histogramHue.second < 0.90f ->
            "color_fallback_histogram_only_below_0_90"
        !hueSupported -> "color_fallback_missing_hue_support"
        !bodyColorSupported -> "color_fallback_missing_independent_rgb_support"
        fallback.second < 0.78f -> "color_fallback_confidence_below_0_78"
        else -> null
    }

    internal fun shouldUseCostumeHeuristic(
        signatureDetails: CostumeSignatureStore.MatchDetails?,
        pokemonName: String?
    ): Boolean {
        if (signatureDetails == null || pokemonName.isNullOrBlank()) return true
        val bestCostume = signatureDetails.bestCostume
        val scoreGap = signatureDetails.scoreGap

        val allowed = if (signatureDetails.denseVariantSpecies) {
            bestCostume <= 0.30f && scoreGap >= 0.04f
        } else {
            bestCostume <= 0.30f && scoreGap >= 0.035f
        }

        return allowed
    }

    internal fun hasUpperHeadRedAccessoryCue(bitmap: Bitmap): Boolean {
        val region = android.graphics.Rect(
            (bitmap.width * 0.28f).toInt().coerceIn(0, bitmap.width),
            (bitmap.height * 0.24f).toInt().coerceIn(0, bitmap.height),
            (bitmap.width * 0.60f).toInt().coerceIn(0, bitmap.width),
            (bitmap.height * 0.37f).toInt().coerceIn(0, bitmap.height)
        )
        if (region.width() <= 0 || region.height() <= 0) return false

        val hsv = FloatArray(3)
        var total = 0
        var red = 0
        var dark = 0
        var y = region.top
        while (y < region.bottom) {
            var x = region.left
            while (x < region.right) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                if (hsv[2] >= 0.08f) {
                    total++
                    val hue = hsv[0].toInt()
                    if ((hue >= 345 || hue <= 18) && hsv[1] >= 0.45f && hsv[2] >= 0.30f) {
                        red++
                    }
                    if (hsv[2] <= 0.35f && hsv[1] >= 0.10f) {
                        dark++
                    }
                }
                x += 3
            }
            y += 3
        }
        if (total <= 0) return false
        val redRatio = red.toFloat() / total.toFloat()
        val darkRatio = dark.toFloat() / total.toFloat()
        return red >= 20 && redRatio >= 0.025f && darkRatio >= 0.006f
    }

    /** Replays the existing color predicates over a stored detector crop for bounded QA tests. */
    internal fun shinyColorEvidenceForPreparedCrop(
        maskedSprite: Bitmap,
        rawSprite: Bitmap,
        pokemonName: String?
    ): ShinyColorEvidence {
        val maskedBody = bodyCrop(maskedSprite)
        val rawBody = bodyCrop(rawSprite)
        try {
            return ShinyColorEvidence(
                masked = isShinyByColor(ColorAnalyzer.getDominantRgb(maskedBody, null), pokemonName),
                raw = isShinyByColor(ColorAnalyzer.getDominantRgb(rawBody, null), pokemonName),
                hue = isShinyByObservedHue(
                    ColorAnalyzer.getDominantHue(rawBody, null),
                    ColorAnalyzer.getAverageSaturation(rawBody, null),
                    ColorAnalyzer.getAverageBrightness(rawBody, null),
                    pokemonName
                ),
                histogramHue = isShinyByHistogramHue(
                    SpriteColorSignature.computeHueHistogram(rawBody),
                    pokemonName
                )
            )
        } finally {
            if (maskedBody !== maskedSprite) maskedBody.recycle()
            if (rawBody !== rawSprite) rawBody.recycle()
        }
    }

    private fun bodyCrop(bitmap: Bitmap): Bitmap {
        val top = (bitmap.height * 0.30f).toInt().coerceIn(0, bitmap.height.coerceAtLeast(1) - 1)
        val height = (bitmap.height - top).coerceAtLeast(1)
        return if (top == 0 && height == bitmap.height) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
        }
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

internal fun ShinySignatureStore.MatchDetails.toQaMatch() = QaShinySignatureMatch(
    matched = matched,
    confidence = confidence,
    normalScore = normalScore,
    shinyScore = shinyScore,
    normalColorScore = normalColorScore,
    shinyColorScore = shinyColorScore,
    scoreGap = scoreGap,
    colorGap = colorGap,
    histogramSource = histogramSource,
    strictMode = strictMode,
    reasonCode = reasonCode,
    normalComponents = normalParts?.toQaComponents(),
    shinyComponents = shinyParts?.toQaComponents()
)

private fun ShinySignatureStore.ScoreParts.toQaComponents() = QaShinyComponents(
    weightedScore = total,
    colorDistance = color,
    dHashDistance = dh,
    aHashDistance = ah,
    edgeDistance = ed
)

private fun CostumeSignatureStore.MatchDetails?.toQaCostumeStage(
    referenceAvailable: Boolean,
    rejectionReason: String
): QaCostumeStage = QaCostumeStage(
    source = VariantStageDiagnosticRecorder.VISUAL_COSTUME_SOURCE,
    referenceAvailable = referenceAvailable,
    candidateCount = this?.candidateCount ?: 0,
    costumeCandidateCount = this?.costumeCandidateCount ?: 0,
    bestNormalKey = this?.bestNormalKey,
    bestNormalScore = this?.bestNormal?.takeIf { it.isFinite() && it != Float.MAX_VALUE },
    bestCostumeKey = this?.bestCostumeKey,
    bestCostumeScore = this?.bestCostume?.takeIf { it.isFinite() && it != Float.MAX_VALUE },
    scoreGap = this?.scoreGap?.takeIf { it.isFinite() },
    confidence = this?.confidence,
    matched = this?.matched ?: false,
    denseVariantSpecies = this?.denseVariantSpecies,
    rejectionReason = this?.rejectionReason ?: rejectionReason
)
