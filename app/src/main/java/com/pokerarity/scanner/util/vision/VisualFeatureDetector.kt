package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.data.model.CostumeDecisionDiagnostics
import com.pokerarity.scanner.data.model.ShinyDecisionDiagnostics
import com.pokerarity.scanner.data.model.ShinySignatureCandidateDiagnostics
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityManifestLoader
import com.pokerarity.scanner.util.ocr.QaVariantDiagnosticsPolicy
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
        val primaryMatched: Boolean,
        val primaryConfidence: Float,
        val chosenFromPrimary: Boolean
    )

    internal data class ShinyColorEvidence(
        val masked: Pair<Boolean, Float>,
        val raw: Pair<Boolean, Float>,
        val hue: Pair<Boolean, Float>,
        val histogramHue: Pair<Boolean, Float>,
        val neutral: NeutralShinyColorEvidence = NeutralShinyColorEvidence()
    )

    internal data class NeutralShinyColorEvidence(
        val matched: Boolean = false,
        val confidence: Float = 0f,
        val pixelRatio: Float = 0f
    )

    internal data class ShinyResolution(
        val result: Pair<Boolean, Float>,
        val colorFallbackCalled: Boolean,
        val colorFallbackReason: String,
        val neutralFallbackApplied: Boolean = false
    )

    private data class ShinySignatureDecision(
        val result: Pair<Boolean, Float>? = null,
        val reason: String? = null,
        val suppressHueFallback: Boolean = false
    )

    companion object {
        private const val GENERATED_COLORS_PATH = "data/pokemon_colors_generated.json"
        private const val MIN_COSTUME_CONFIDENCE = 0.20f
        private const val BORDERLINE_COSTUME_CONFIDENCE = 0.24f
        private const val MIN_HEURISTIC_ONLY_COSTUME_CONFIDENCE = 0.82f
        private const val QA_MAX_CANDIDATES = 9_999
        private const val QA_MAX_KEY_LENGTH = 64
        private const val QA_MAX_REJECTION_LENGTH = 96
        private const val QA_MAX_PATH_LENGTH = 48
        private const val QA_MAX_REASON_LENGTH = 96
        private const val NEUTRAL_SHINY_MAX_REFERENCE_SATURATION = 0.18f
        private const val NEUTRAL_SHINY_MIN_NORMAL_SATURATION = 0.30f
        private const val NEUTRAL_SHINY_MIN_REFERENCE_GAP = 90.0
        private const val NEUTRAL_SHINY_PIXEL_MAX_SATURATION = 0.22f
        private const val NEUTRAL_SHINY_PIXEL_MIN_VALUE = 0.25f
        private const val NEUTRAL_SHINY_PIXEL_MAX_VALUE = 0.92f
        private const val NEUTRAL_SHINY_PIXEL_MAX_DISTANCE = 70.0
        private const val NEUTRAL_SHINY_PIXEL_MIN_NORMAL_DISTANCE = 90.0
        private const val NEUTRAL_SHINY_CROP_WIDTH_RATIO = 0.70f
        private const val NEUTRAL_SHINY_FOREGROUND_MIN_CHANNEL_SUM = 30
        private const val NEUTRAL_SHINY_MIN_PIXEL_RATIO = 0.30f
        private const val NEUTRAL_SHINY_MIN_MATCHED_PIXELS = 20
        private const val NEUTRAL_SHINY_MIN_FOREGROUND_PIXELS = 40
        private const val NEUTRAL_SHINY_MAX_NORMAL_SIGNATURE_ADVANTAGE = -0.08f
        private const val NEUTRAL_SHINY_BASE_CONFIDENCE = 0.70f
        private const val NEUTRAL_SHINY_CONFIDENCE_RANGE = 0.30f
        private const val NEUTRAL_SHINY_CONFIDENCE_RATIO_RANGE = 0.50f
        private const val ACCESSORY_COSTUME_MAX_SCORE = 0.38f
        private const val ACCESSORY_COSTUME_MAX_NORMAL_ADVANTAGE = 0.025f
        private const val UPPER_HEAD_CUE_LEFT_RATIO = 0.32f
        private const val UPPER_HEAD_CUE_TOP_RATIO = 0.09f
        private const val UPPER_HEAD_CUE_RIGHT_RATIO = 0.68f
        private const val UPPER_HEAD_CUE_BOTTOM_RATIO = 0.20f
        private const val UPPER_HEAD_CUE_MIN_RED_PIXELS = 20
        private const val UPPER_HEAD_CUE_MIN_RED_RATIO = 0.015f
        private const val UPPER_HEAD_CUE_MIN_BRIGHT_RED_RATIO = 0.015f
        private const val MULTI_VIEW_SHINY_MIN_SCORE_GAP = 0.10f
        private const val MULTI_VIEW_SHINY_MIN_COLOR_GAP = 0.08f
        private const val MULTI_VIEW_SHINY_MAX_SCORE = 0.50f
        private const val MULTI_VIEW_SHINY_CONFIDENCE = 0.78f

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
    // This existing bitmap-owned pipeline intentionally keeps the ordered feature decisions in one scope.
    @Suppress("NestedBlockDepth", "LongMethod", "CyclomaticComplexMethod")
    fun detect(bitmap: Bitmap, pokemonName: String? = null, sizeTag: String? = null): VisualFeatures {
        val smallBitmapRaw = ColorAnalyzer.downscaleForAnalysis(bitmap)
        val shadowResult = isShadow(smallBitmapRaw)
        
        val smallBitmap = if (shadowResult.first) {
            ColorAnalyzer.maskShadowFlames(smallBitmapRaw)
        } else {
            smallBitmapRaw
        }
        val shouldRecycleSmall = smallBitmap !== bitmap
        val maskedSprite = ColorAnalyzer.extractMaskedSprite(smallBitmap)
        val costumeCropWidth = maskedSprite.width
        val costumeCropHeight = maskedSprite.height

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
        val hasSignatureSpecies = CostumeSignatureStore.hasSpecies(context, pokemonName)
        val shouldCheckCostume = hasSignatureSpecies ||
            VariantRegistry.hasCostumeLikeSpecies(context, pokemonName) ||
            RarityManifestLoader.hasCostumeSpecies(pokemonName)
        var diagnosticSignatureDetails: CostumeSignatureStore.MatchDetails? = null
        var diagnosticBorderlineRescue = false
        var diagnosticSparseRescue = false
        var diagnosticAccessoryCue = false
        var diagnosticHeuristicEligible: Boolean? = null
        var diagnosticHeuristicCalled = false
        var diagnosticHeuristicResult: Pair<Boolean, Float>? = null
        var diagnosticHeuristicPath = "not_applicable"
        val costumeResult = if (hasSignatureSpecies) {
            val signatureDetails = CostumeSignatureStore.matchSignatureDetails(
                signature = spriteSignature,
                species = pokemonName,
                pHash = spritePHash,
                headPHash = costumeHeadPHash
            )
            diagnosticSignatureDetails = signatureDetails
            val borderlineCostumeRescue = signatureDetails?.let {
                it.matched &&
                    !it.denseVariantSpecies &&
                    it.bestCostume <= 0.28f &&
                    it.scoreGap >= 0.07f &&
                    it.confidence >= 0.18f
            } == true
            diagnosticBorderlineRescue = borderlineCostumeRescue
            val sparseMatchedCostumeRescue = signatureDetails?.let {
                it.matched &&
                    !it.denseVariantSpecies &&
                    it.bestCostume <= 0.36f &&
                    it.scoreGap >= 0.03f
            } == true
            diagnosticSparseRescue = sparseMatchedCostumeRescue
            val nearTieAccessoryCandidate = shouldUseUpperHeadAccessoryRescue(signatureDetails)
            // The adaptive sprite crop may exclude the top accessory, so inspect the source
            // screen's centered upper-sprite region for this corroborating cue.
            val upperHeadAccessoryCue = (sparseMatchedCostumeRescue || nearTieAccessoryCandidate) &&
                hasUpperHeadRedAccessoryCue(smallBitmap)
            diagnosticAccessoryCue = upperHeadAccessoryCue
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
            if (signatureResult.first) {
                diagnosticHeuristicPath = when {
                    borderlineCostumeRescue -> "borderline_signature_rescue"
                    upperHeadAccessoryCue -> "signature_plus_upper_head_accessory"
                    sparseMatchedCostumeRescue -> "sparse_signature_rescue"
                    else -> "signature_match"
                }
                signatureResult
            } else {
                val heuristicAllowed = shouldCheckCostume && shouldUseCostumeHeuristic(signatureDetails, pokemonName)
                diagnosticHeuristicEligible = heuristicAllowed
                if (heuristicAllowed) {
                    diagnosticHeuristicPath = "signature_rejected_heuristic"
                    diagnosticHeuristicCalled = true
                    hasCostume(smallBitmap, pokemonName).also { diagnosticHeuristicResult = it }
                } else {
                    diagnosticHeuristicPath = when {
                        !shouldCheckCostume -> "species_not_costume_eligible"
                        signatureDetails == null -> "signature_details_missing"
                        else -> "signature_heuristic_gate_rejected"
                    }
                    Pair(false, 0f)
                }
            }
        } else if (shouldCheckCostume) {
            diagnosticHeuristicPath = "heuristic_only"
            diagnosticHeuristicEligible = true
            diagnosticHeuristicCalled = true
            val heuristicOnlyResult = hasCostume(smallBitmap, pokemonName).also { diagnosticHeuristicResult = it }
            if (heuristicOnlyResult.first && heuristicOnlyResult.second < MIN_HEURISTIC_ONLY_COSTUME_CONFIDENCE) {
                diagnosticHeuristicPath = "heuristic_only_below_confidence_gate"
                Pair(false, 0f)
            } else {
                heuristicOnlyResult
            }
        } else {
            diagnosticHeuristicEligible = false
            diagnosticHeuristicPath = "species_not_costume_eligible"
            Pair(false, 0f)
        }

        val strictShiny = costumeResult.first && costumeResult.second >= 0.6f
        val hasShinySignature = ShinySignatureStore.hasSpecies(context, pokemonName)
        val collectShinyDiagnostics = QaVariantDiagnosticsPolicy.isEnabled(BuildConfig.BUILD_TYPE)
        val shinyColorEvidence = if (hasShinySignature || collectShinyDiagnostics) {
            ShinyColorEvidence(
                masked = isShinyByColor(bodyDominantColor, pokemonName),
                raw = isShinyByColor(rawBodyDominantColor, pokemonName),
                hue = isShinyByObservedHue(rawBodyHue, rawBodySat, rawBodyVal, pokemonName),
                histogramHue = isShinyByHistogramHue(altColorHist, pokemonName),
                neutral = pokemonName?.let { name ->
                    pokemonColors[name]?.let { reference -> detectNeutralShinyEvidence(maskedSprite, reference) }
                } ?: NeutralShinyColorEvidence()
            )
        } else {
            null
        }
        val signatureMatches = if (hasShinySignature || collectShinyDiagnostics) {
            listOf(
                "primary" to ShinySignatureStore.matchSignatureDetails(
                    spriteSignature, bodyHist, altColorHist, pokemonName, strictShiny
                ),
                "alternate_masked" to ShinySignatureStore.matchSignatureDetails(
                    spriteSignature, maskedFullHist, null, pokemonName, strictShiny
                ),
                "alternate_upper" to ShinySignatureStore.matchSignatureDetails(
                    spriteSignature, upperHist, null, pokemonName, strictShiny
                )
            )
        } else {
            emptyList()
        }
        val signatureConsensus = signatureMatches.takeIf { hasShinySignature && it.isNotEmpty() }?.let { matches ->
            chooseBestShinySignatureResult(
                primaryResult = Pair(matches[0].second.matched, matches[0].second.confidence),
                extraResults = matches.drop(1).map { (_, details) -> Pair(details.matched, details.confidence) }
            )
        }
        var colorFallbackCalled = false
        var colorFallbackReason = "signature_reference_missing_direct_color_path"
        var neutralFallbackApplied = false
        var directColorResult: Pair<Boolean, Float>? = null
        var shinyResult = if (signatureConsensus != null) {
            val resolution = resolveShinyResult(
                signatureConsensus,
                requireNotNull(shinyColorEvidence),
                pokemonName,
                signatureMatches.map { it.second },
                costumeAccessoryCue = diagnosticAccessoryCue
            )
            colorFallbackCalled = resolution.colorFallbackCalled
            colorFallbackReason = resolution.colorFallbackReason
            neutralFallbackApplied = resolution.neutralFallbackApplied
            resolution.result
        } else {
            isShinyByColor(dominantColor, pokemonName).also { directColorResult = it }
        }
        if (!shinyResult.first && pokemonName.isNullOrBlank()) {
            val sparkleResult = hasShinySparkles(smallBitmap)
            if (sparkleResult.first) {
                shinyResult = sparkleResult
                colorFallbackReason = "species_unresolved_sparkle_fallback"
            }
        }
        // Magikarp özel fallback'ı korunuyor
        if (!shinyResult.first && pokemonName?.equals("Magikarp", true) == true) {
            val yellowScore = altColorHist.getOrElse(1) { 0f } + altColorHist.getOrElse(2) { 0f }
            val redScore = altColorHist.getOrElse(0) { 0f } + altColorHist.getOrElse(11) { 0f }
            val histShiny = yellowScore >= 0.18f && yellowScore >= redScore * 1.35f
            if (histShiny) {
                shinyResult = Pair(true, 0.6f)
                colorFallbackReason = "magikarp_histogram_fallback"
            } else {
                val hueShiny = rawBodyHue in 40f..75f && rawBodySat >= 0.25f && rawBodyVal >= 0.25f
                if (hueShiny) {
                    shinyResult = Pair(true, 0.6f)
                    colorFallbackReason = "magikarp_hue_fallback"
                }
            }
        }

        // Shadow already evaluated at the beginning
        val luckyResult = isLucky(smallBitmap)
        val shinyResultBeforeLuckyGuard = shinyResult
        shinyResult = guardShinyResultAgainstLuckyBackground(shinyResult, luckyResult)
        if (shinyResultBeforeLuckyGuard.first && !shinyResult.first) {
            colorFallbackReason = "lucky_background_visual_contamination_guard"
            neutralFallbackApplied = false
        }
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

        return VisualFeatures(
            isShiny = shinyResult.first,
            isShadow = shadowResult.first,
            isLucky = luckyResult.first,
            hasCostume = costumeResult.first,
            isXXS = sizeTag == "XXS",
            isXXL = sizeTag == "XXL",
            hasLocationCard = locationCardResult.first,
            confidence = avgConfidence,
            costumeDecisionDiagnostics = if (QaVariantDiagnosticsPolicy.isEnabled(BuildConfig.BUILD_TYPE)) {
                buildCostumeDecisionDiagnostics(
                    CostumeDiagnosticState(
                        cropWidth = costumeCropWidth,
                        cropHeight = costumeCropHeight,
                        signatureDetails = diagnosticSignatureDetails,
                        hasSignatureSpecies = hasSignatureSpecies,
                        shouldCheckCostume = shouldCheckCostume,
                        heuristicPath = diagnosticHeuristicPath,
                        heuristicEligible = diagnosticHeuristicEligible,
                        heuristicCalled = diagnosticHeuristicCalled,
                        heuristicResult = diagnosticHeuristicResult,
                        borderlineRescue = diagnosticBorderlineRescue,
                        sparseRescue = diagnosticSparseRescue,
                        accessoryCue = diagnosticAccessoryCue,
                        finalResult = costumeResult
                    )
                )
            } else {
                null
            },
            shinyDecisionDiagnostics = if (QaVariantDiagnosticsPolicy.isEnabled(BuildConfig.BUILD_TYPE)) {
                ShinyDecisionDiagnostics(
                    signatureReferenceAvailable = hasShinySignature,
                    signatureCandidates = signatureMatches.take(3).map { (source, details) ->
                        ShinySignatureCandidateDiagnostics(
                            source = source,
                            referenceAvailable = details.referenceAvailable,
                            matched = details.matched,
                            confidence = details.confidence,
                            normalScore = details.normalScore,
                            shinyScore = details.shinyScore,
                            normalColorScore = details.normalColorScore,
                            shinyColorScore = details.shinyColorScore,
                            scoreGap = details.scoreGap,
                            colorGap = details.colorGap,
                            selectedHistogram = details.selectedHistogram,
                            rejectionReason = details.rejectionReason
                        )
                    },
                    matchedSignatureCount = signatureConsensus?.matchedCount ?: 0,
                    primarySignatureMatched = signatureConsensus?.primaryMatched ?: false,
                    chosenSignatureCandidate = signatureConsensus?.let { consensus ->
                        when {
                            consensus.chosenFromPrimary -> "primary"
                            signatureMatches.getOrNull(1)?.second?.let {
                                Pair(it.matched, it.confidence) == consensus.result
                            } == true -> "alternate_masked"
                            else -> "alternate_upper"
                        }
                    },
                    maskedColorMatched = shinyColorEvidence?.masked?.first ?: false,
                    maskedColorConfidence = shinyColorEvidence?.masked?.second ?: 0f,
                    rawColorMatched = shinyColorEvidence?.raw?.first ?: false,
                    rawColorConfidence = shinyColorEvidence?.raw?.second ?: 0f,
                    hueMatched = shinyColorEvidence?.hue?.first ?: false,
                    hueConfidence = shinyColorEvidence?.hue?.second ?: 0f,
                    histogramHueMatched = shinyColorEvidence?.histogramHue?.first ?: false,
                    histogramHueConfidence = shinyColorEvidence?.histogramHue?.second ?: 0f,
                    directColorMatched = directColorResult?.first,
                    directColorConfidence = directColorResult?.second,
                    chooseShinyResultColorFallbackCalled = colorFallbackCalled,
                    colorFallbackReason = colorFallbackReason,
                    finalDetectorShiny = shinyResult.first,
                    finalDetectorConfidence = shinyResult.second,
                    neutralColorMatched = shinyColorEvidence?.neutral?.matched ?: false,
                    neutralColorConfidence = shinyColorEvidence?.neutral?.confidence ?: 0f,
                    neutralColorPixelRatio = shinyColorEvidence?.neutral?.pixelRatio ?: 0f,
                    neutralColorFallbackApplied = neutralFallbackApplied
                )
            } else {
                null
            }
        )
    }

    private data class CostumeDiagnosticState(
        val cropWidth: Int,
        val cropHeight: Int,
        val signatureDetails: CostumeSignatureStore.MatchDetails?,
        val hasSignatureSpecies: Boolean,
        val shouldCheckCostume: Boolean,
        val heuristicPath: String,
        val heuristicEligible: Boolean?,
        val heuristicCalled: Boolean,
        val heuristicResult: Pair<Boolean, Float>?,
        val borderlineRescue: Boolean,
        val sparseRescue: Boolean,
        val accessoryCue: Boolean,
        val finalResult: Pair<Boolean, Float>
    )

    private fun buildCostumeDecisionDiagnostics(state: CostumeDiagnosticState): CostumeDecisionDiagnostics {
        val details = state.signatureDetails
        return CostumeDecisionDiagnostics(
            cropWidth = state.cropWidth,
            cropHeight = state.cropHeight,
            candidateCount = details?.candidateCount?.coerceIn(0, QA_MAX_CANDIDATES) ?: 0,
            normalCandidateCount = details?.normalCandidateCount?.coerceIn(0, QA_MAX_CANDIDATES) ?: 0,
            costumeCandidateCount = details?.costumeCandidateCount?.coerceIn(0, QA_MAX_CANDIDATES) ?: 0,
            bestCostumeKey = details?.bestCostumeKey?.take(QA_MAX_KEY_LENGTH),
            bestCostumeScore = details?.bestCostume?.diagnosticScore(),
            bestNormalKey = details?.bestNormalKey?.take(QA_MAX_KEY_LENGTH),
            bestNormalScore = details?.bestNormal?.diagnosticScore(),
            scoreGap = details?.takeIf { it.costumeCandidateCount > 0 }
                ?.scoreGap?.diagnosticSignedScore(),
            signatureMatched = details?.matched,
            signatureConfidence = details?.confidence?.diagnosticScore(),
            signatureRejectionReason = details?.rejectionReason?.take(QA_MAX_REJECTION_LENGTH),
            denseVariantSpecies = details?.denseVariantSpecies,
            heuristicPath = state.heuristicPath.take(QA_MAX_PATH_LENGTH),
            heuristicEligible = state.heuristicEligible,
            heuristicCalled = state.heuristicCalled,
            heuristicMatched = state.heuristicResult?.first,
            heuristicConfidence = state.heuristicResult?.second?.diagnosticScore(),
            borderlineRescue = state.borderlineRescue,
            sparseMatchRescue = state.sparseRescue,
            upperHeadAccessoryCue = state.accessoryCue,
            strictShinyActivated = state.finalResult.first && state.finalResult.second >= 0.6f,
            finalCostume = state.finalResult.first,
            finalConfidence = state.finalResult.second.coerceIn(0f, 1f),
            decisionReason = costumeDecisionReason(state, details).take(QA_MAX_REASON_LENGTH)
        )
    }

    private fun costumeDecisionReason(
        state: CostumeDiagnosticState,
        details: CostumeSignatureStore.MatchDetails?
    ): String = if (state.finalResult.first) {
        positiveCostumeDecisionReason(state)
    } else {
        rejectedCostumeDecisionReason(state, details)
    }

    private fun positiveCostumeDecisionReason(state: CostumeDiagnosticState): String = when {
            state.heuristicCalled && state.heuristicResult?.first == true ->
                "heuristic_match"
            state.borderlineRescue -> "borderline_signature_rescue"
            state.accessoryCue -> "upper_head_accessory_rescue"
            state.sparseRescue -> "sparse_signature_rescue"
            else -> "signature_match"
        }

    private fun rejectedCostumeDecisionReason(
        state: CostumeDiagnosticState,
        details: CostumeSignatureStore.MatchDetails?
    ): String {
        val rejection = details?.rejectionReason
        return when {
            state.heuristicResult?.first == true && state.finalResult.second == 0f ->
                "heuristic_below_confidence_gate"
            state.heuristicCalled -> "heuristic_rejected"
            details?.matched == true && details.confidence < MIN_COSTUME_CONFIDENCE ->
                "signature_confidence_below_minimum"
            rejection != null -> rejection
            details == null && state.hasSignatureSpecies -> "signature_details_missing"
            !state.shouldCheckCostume -> "species_not_costume_eligible"
            else -> "no_costume_match"
        }
    }

    private fun Float.diagnosticScore(): Float? =
        takeIf { isFinite() && this != Float.MAX_VALUE }?.coerceIn(0f, 1f)

    private fun Float.diagnosticSignedScore(): Float? =
        takeIf { isFinite() && this != Float.MAX_VALUE }?.coerceIn(-1f, 1f)

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

    /** Detects a neutral shiny palette inside the center body crop. */
    internal fun detectNeutralShinyEvidence(
        maskedSprite: Bitmap,
        reference: ColorReference
    ): NeutralShinyColorEvidence {
        val referenceEligible = isNeutralShinyReference(reference)
        val cropWidth = min(maskedSprite.width, (maskedSprite.height * NEUTRAL_SHINY_CROP_WIDTH_RATIO)
            .toInt().coerceAtLeast(1))
        val cropLeft = ((maskedSprite.width - cropWidth) / 2).coerceAtLeast(0)
        val bodyTop = (maskedSprite.height * 0.30f).toInt().coerceIn(0, maskedSprite.height)
        val counts = if (referenceEligible) {
            countNeutralShinyPixels(maskedSprite, cropLeft, cropWidth, bodyTop, reference)
        } else {
            NeutralShinyPixelCounts()
        }
        val pixelRatio = if (counts.foreground > 0) counts.matched.toFloat() / counts.foreground.toFloat() else 0f
        val matched = referenceEligible &&
            counts.foreground >= NEUTRAL_SHINY_MIN_FOREGROUND_PIXELS &&
            counts.matched >= NEUTRAL_SHINY_MIN_MATCHED_PIXELS &&
            pixelRatio >= NEUTRAL_SHINY_MIN_PIXEL_RATIO
        val confidence = if (matched) {
            (NEUTRAL_SHINY_BASE_CONFIDENCE + NEUTRAL_SHINY_CONFIDENCE_RANGE *
                ((pixelRatio - NEUTRAL_SHINY_MIN_PIXEL_RATIO) / NEUTRAL_SHINY_CONFIDENCE_RATIO_RANGE))
                .coerceIn(0.78f, 0.95f)
        } else {
            0f
        }
        return NeutralShinyColorEvidence(matched, confidence, pixelRatio.coerceIn(0f, 1f))
    }

    private data class NeutralShinyPixelCounts(val foreground: Int = 0, val matched: Int = 0)

    private fun isNeutralShinyReference(reference: ColorReference): Boolean {
        val normalHsv = FloatArray(3)
        val shinyHsv = FloatArray(3)
        Color.RGBToHSV(reference.normal[0], reference.normal[1], reference.normal[2], normalHsv)
        Color.RGBToHSV(reference.shiny[0], reference.shiny[1], reference.shiny[2], shinyHsv)
        return shinyHsv[1] <= NEUTRAL_SHINY_MAX_REFERENCE_SATURATION &&
            normalHsv[1] >= NEUTRAL_SHINY_MIN_NORMAL_SATURATION &&
            rgbDistance(reference.normal, reference.shiny) >= NEUTRAL_SHINY_MIN_REFERENCE_GAP
    }

    private fun countNeutralShinyPixels(
        bitmap: Bitmap,
        left: Int,
        width: Int,
        top: Int,
        reference: ColorReference
    ): NeutralShinyPixelCounts {
        val hsv = FloatArray(3)
        var foregroundPixels = 0
        var matchedPixels = 0
        for (y in top until bitmap.height step 2) {
            for (x in left until (left + width) step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) <=
                    NEUTRAL_SHINY_FOREGROUND_MIN_CHANNEL_SUM
                ) continue
                foregroundPixels++
                if (matchesNeutralShinyPixel(pixel, hsv, reference)) matchedPixels++
            }
        }
        return NeutralShinyPixelCounts(foregroundPixels, matchedPixels)
    }

    private fun matchesNeutralShinyPixel(
        pixel: Int,
        hsv: FloatArray,
        reference: ColorReference
    ): Boolean {
        Color.colorToHSV(pixel, hsv)
        if (hsv[1] > NEUTRAL_SHINY_PIXEL_MAX_SATURATION ||
            hsv[2] !in NEUTRAL_SHINY_PIXEL_MIN_VALUE..NEUTRAL_SHINY_PIXEL_MAX_VALUE
        ) return false
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        val shinyDistance = rgbDistanceSquared(red, green, blue, reference.shiny)
        val normalDistance = rgbDistanceSquared(red, green, blue, reference.normal)
        return shinyDistance <= NEUTRAL_SHINY_PIXEL_MAX_DISTANCE * NEUTRAL_SHINY_PIXEL_MAX_DISTANCE &&
            normalDistance >= NEUTRAL_SHINY_PIXEL_MIN_NORMAL_DISTANCE * NEUTRAL_SHINY_PIXEL_MIN_NORMAL_DISTANCE
    }

    private fun rgbDistanceSquared(red: Int, green: Int, blue: Int, reference: List<Int>): Double {
        val redGap = red - reference[0]
        val greenGap = green - reference[1]
        val blueGap = blue - reference[2]
        return (redGap * redGap + greenGap * greenGap + blueGap * blueGap).toDouble()
    }

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
        pokemonName: String?
    ): Pair<Boolean, Float> = resolveShinyResult(signatureConsensus, colorEvidence, pokemonName).result

    internal fun guardShinyResultAgainstLuckyBackground(
        shinyResult: Pair<Boolean, Float>,
        luckyResult: Pair<Boolean, Float>
    ): Pair<Boolean, Float> {
        return if (shinyResult.first && luckyResult.first) {
            Pair(false, 0f)
        } else {
            shinyResult
        }
    }

    internal fun resolveShinyResult(
        signatureConsensus: SignatureConsensus,
        colorEvidence: ShinyColorEvidence,
        pokemonName: String?,
        signatureCandidates: List<ShinySignatureStore.SignatureMatchDetails> = emptyList(),
        costumeAccessoryCue: Boolean = false
    ): ShinyResolution {
        val signatureDecision = resolveSignatureDecision(signatureConsensus, signatureCandidates, costumeAccessoryCue)
        val colorEvidenceForFallback = if (signatureDecision.suppressHueFallback) {
            colorEvidence.copy(hue = Pair(false, 0f), histogramHue = Pair(false, 0f))
        } else {
            colorEvidence
        }
        val colorFallback = if (signatureDecision.result == null) {
            chooseColorFallback(signatureConsensus, colorEvidenceForFallback, pokemonName)
        } else {
            null
        }
        val neutralFallback = chooseNeutralFallback(
            signatureResolved = signatureDecision.result != null,
            colorFallback = colorFallback,
            evidence = colorEvidence.neutral,
            signatureCandidates = signatureCandidates
        )
        // A weak positive signature is not sufficient on its own without independent support.
        val rejectedWeakSignature = signatureConsensus.result.let {
            if (it.first) Pair(false, 0f) else it
        }
        val reason = signatureDecision.reason ?: if (neutralFallback != null) {
            "neutral_color_ambiguous_signature"
        } else {
            colorFallbackReason(null, colorFallback, signatureConsensus, pokemonName)
        }
        return ShinyResolution(
            result = signatureDecision.result ?: colorFallback ?: neutralFallback ?: rejectedWeakSignature,
            colorFallbackCalled = signatureDecision.result == null,
            colorFallbackReason = reason,
            neutralFallbackApplied = neutralFallback != null
        )
    }

    private fun resolveSignatureDecision(
        signatureConsensus: SignatureConsensus,
        signatureCandidates: List<ShinySignatureStore.SignatureMatchDetails>,
        costumeAccessoryCue: Boolean
    ): ShinySignatureDecision {
        val signatureResult = signatureConsensus.result
        val multiViewSignature = if (signatureResult.first && hasStrongMultiViewShinySignature(signatureCandidates)) {
            Pair(true, maxOf(signatureResult.second, MULTI_VIEW_SHINY_CONFIDENCE))
        } else {
            null
        }
        val accessoryConflict = costumeAccessoryCue && signatureResult.first && multiViewSignature == null
        val strongPrimarySignature = signatureResult.takeIf {
            it.first && !accessoryConflict && isStrongPrimarySignature(signatureConsensus)
        }
        return when {
            multiViewSignature != null -> ShinySignatureDecision(
                result = multiViewSignature,
                reason = "multi_view_signature_consensus"
            )
            accessoryConflict -> ShinySignatureDecision(
                reason = "costume_accessory_signature_conflict",
                suppressHueFallback = true
            )
            strongPrimarySignature != null -> ShinySignatureDecision(
                result = strongPrimarySignature,
                reason = "strong_primary_signature_preserved"
            )
            else -> ShinySignatureDecision()
        }
    }

    private fun chooseNeutralFallback(
        signatureResolved: Boolean,
        colorFallback: Pair<Boolean, Float>?,
        evidence: NeutralShinyColorEvidence,
        signatureCandidates: List<ShinySignatureStore.SignatureMatchDetails>
    ): Pair<Boolean, Float>? {
        if (signatureResolved || colorFallback != null) return null
        return if (shouldUseNeutralShinyFallback(evidence, signatureCandidates)) {
            Pair(true, evidence.confidence)
        } else {
            null
        }
    }

    internal fun shouldUseNeutralShinyFallback(
        evidence: NeutralShinyColorEvidence,
        signatureCandidates: List<ShinySignatureStore.SignatureMatchDetails>
    ): Boolean {
        val available = signatureCandidates.filter { it.referenceAvailable }
        val gaps = available.mapNotNull { it.scoreGap }
        return evidence.matched && evidence.confidence >= 0.78f &&
            available.size >= 2 &&
            available.none { it.matched } &&
            gaps.size >= 2 &&
              gaps.all { it >= NEUTRAL_SHINY_MAX_NORMAL_SIGNATURE_ADVANTAGE }
    }

    internal fun hasStrongMultiViewShinySignature(
        signatureCandidates: List<ShinySignatureStore.SignatureMatchDetails>
    ): Boolean {
        val available = signatureCandidates.filter { it.referenceAvailable }
        return available.size >= 3 &&
            available.count { it.matched } >= 3 &&
            available.all { candidate ->
                val scoreGap = candidate.scoreGap
                val colorGap = candidate.colorGap
                val shinyScore = candidate.shinyScore
                scoreGap != null && scoreGap >= MULTI_VIEW_SHINY_MIN_SCORE_GAP &&
                    colorGap != null && colorGap >= MULTI_VIEW_SHINY_MIN_COLOR_GAP &&
                    shinyScore != null && shinyScore <= MULTI_VIEW_SHINY_MAX_SCORE
            }
    }

    private fun colorFallbackReason(
        strongSignature: Pair<Boolean, Float>?,
        colorFallback: Pair<Boolean, Float>?,
        signatureConsensus: SignatureConsensus,
        pokemonName: String?
    ): String {
        val speciesMissing = pokemonName.isNullOrBlank()
        val reference = if (speciesMissing) null else pokemonColors[pokemonName]
        val primaryIsUsable = signatureConsensus.primaryMatched && signatureConsensus.primaryConfidence >= 0.55f
        return when {
            strongSignature != null -> "strong_primary_signature_preserved"
            colorFallback != null -> "supported_color_fallback"
            speciesMissing -> "species_missing"
            reference == null -> "color_reference_missing"
            !hasDistinctShinyReference(reference) -> "color_reference_not_distinct"
            !primaryIsUsable -> "primary_signature_not_usable"
            else -> "color_signals_insufficient"
        }
    }

    internal fun chooseBestShinySignatureResult(
        primaryResult: Pair<Boolean, Float>,
        extraResults: List<Pair<Boolean, Float>>
    ): SignatureConsensus {
        val candidates = listOf(primaryResult) + extraResults
        val matched = candidates.filter { it.first }
        val unsupportedAlternateOnly = matched.size == 1 &&
            !primaryResult.first &&
            extraResults.any { it.first }
        val chosenResult = if (matched.isEmpty() || unsupportedAlternateOnly) {
            primaryResult
        } else {
            matched.maxBy { it.second }
        }
        return SignatureConsensus(
            result = chosenResult,
            matchedCount = matched.size,
            primaryMatched = primaryResult.first,
            primaryConfidence = primaryResult.second,
            chosenFromPrimary = chosenResult == primaryResult
        )
    }

    private fun isStrongPrimarySignature(consensus: SignatureConsensus): Boolean =
        consensus.chosenFromPrimary &&
            consensus.primaryMatched &&
            consensus.primaryConfidence >= 0.78f

    private fun chooseColorFallback(
        consensus: SignatureConsensus,
        evidence: ShinyColorEvidence,
        pokemonName: String?
    ): Pair<Boolean, Float>? {
        val reference = pokemonName?.let { pokemonColors[it] }
        val primaryIsUsable = consensus.primaryMatched && consensus.primaryConfidence >= 0.55f
        return if (reference != null && hasDistinctShinyReference(reference) && primaryIsUsable) {
            chooseSupportedColorFallback(evidence)
        } else {
            null
        }
    }

    private fun hasDistinctShinyReference(reference: ColorReference): Boolean {
        val normalHue = rgbHue(reference.normal)
        val shinyHue = rgbHue(reference.shiny)
        val hueGap = hueDistance(normalHue, shinyHue)
        val rgbGap = rgbDistance(reference.normal, reference.shiny)
        return hueGap >= 28f || rgbGap >= 90.0
    }

    private fun chooseSupportedColorFallback(evidence: ShinyColorEvidence): Pair<Boolean, Float>? {
        val signals = listOf(evidence.masked, evidence.raw, evidence.hue, evidence.histogramHue)
        val fallback = signals.filter { it.first }.maxByOrNull { it.second }
        val supportCount = signals.count { it.first }
        val hueSupported = evidence.hue.first || evidence.histogramHue.first
        val histogramOnlyFallback = evidence.histogramHue.first &&
            !evidence.masked.first &&
            !evidence.raw.first &&
            !evidence.hue.first
        val fallbackIsSupported = fallback != null &&
            !(histogramOnlyFallback && fallback == evidence.histogramHue && evidence.histogramHue.second < 0.90f) &&
            hueSupported &&
            supportCount >= 2 &&
            fallback.second >= 0.78f
        return fallback.takeIf { fallbackIsSupported }
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

    internal fun shouldUseUpperHeadAccessoryRescue(
        signatureDetails: CostumeSignatureStore.MatchDetails?
    ): Boolean = signatureDetails != null &&
        !signatureDetails.denseVariantSpecies &&
        signatureDetails.costumeCandidateCount > 0 &&
        signatureDetails.bestCostume <= ACCESSORY_COSTUME_MAX_SCORE &&
        signatureDetails.scoreGap >= -ACCESSORY_COSTUME_MAX_NORMAL_ADVANTAGE

    internal fun hasUpperHeadRedAccessoryCue(bitmap: Bitmap): Boolean {
        val region = android.graphics.Rect(
            (bitmap.width * UPPER_HEAD_CUE_LEFT_RATIO).toInt().coerceIn(0, bitmap.width),
            (bitmap.height * UPPER_HEAD_CUE_TOP_RATIO).toInt().coerceIn(0, bitmap.height),
            (bitmap.width * UPPER_HEAD_CUE_RIGHT_RATIO).toInt().coerceIn(0, bitmap.width),
            (bitmap.height * UPPER_HEAD_CUE_BOTTOM_RATIO).toInt().coerceIn(0, bitmap.height)
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
        return red >= UPPER_HEAD_CUE_MIN_RED_PIXELS && redRatio >= UPPER_HEAD_CUE_MIN_RED_RATIO &&
            (darkRatio >= 0.006f || redRatio >= UPPER_HEAD_CUE_MIN_BRIGHT_RED_RATIO)
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
