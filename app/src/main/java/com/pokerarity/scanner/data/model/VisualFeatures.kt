package com.pokerarity.scanner.data.model

/**
 * Visual features detected from color analysis of a Pokemon screenshot.
 */
data class VisualFeatures(
    val isShiny: Boolean = false,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isLucky: Boolean = false,
    val hasCostume: Boolean = false,
    val hasSpecialForm: Boolean = false,
    val isXXS: Boolean = false,
    val isXXL: Boolean = false,
    val hasLocationCard: Boolean = false,
    val confidence: Float = 0f,
    /** Local QA-build-only provenance. Never persisted to History or emitted to Logcat. */
    val costumeDecisionDiagnostics: CostumeDecisionDiagnostics? = null,
    /** Local QA-build-only evidence for the shiny decision path. */
    val shinyDecisionDiagnostics: ShinyDecisionDiagnostics? = null,
    /** True only when the final positive is supported by a strong primary shiny signature. */
    val hasStrongPrimaryShinySignature: Boolean = false
)

/** Bounded costume matcher provenance for one QA scan. Contains no image or OCR content. */
data class CostumeDecisionDiagnostics(
    val cropWidth: Int,
    val cropHeight: Int,
    val candidateCount: Int,
    val normalCandidateCount: Int,
    val costumeCandidateCount: Int,
    val bestCostumeKey: String?,
    val bestCostumeScore: Float?,
    val bestNormalKey: String?,
    val bestNormalScore: Float?,
    val scoreGap: Float?,
    val signatureMatched: Boolean?,
    val signatureConfidence: Float?,
    val signatureRejectionReason: String?,
    val denseVariantSpecies: Boolean?,
    val heuristicPath: String,
    val heuristicEligible: Boolean?,
    val heuristicCalled: Boolean,
    val heuristicMatched: Boolean?,
    val heuristicConfidence: Float?,
    val borderlineRescue: Boolean,
    val sparseMatchRescue: Boolean,
    val upperHeadAccessoryCue: Boolean,
    val strictShinyActivated: Boolean,
    val finalCostume: Boolean,
    val finalConfidence: Float,
    val decisionReason: String
)

/** Bounded visual evidence used to explain one QA shiny decision. */
data class ShinyDecisionDiagnostics(
    val signatureReferenceAvailable: Boolean,
    val signatureCandidates: List<ShinySignatureCandidateDiagnostics>,
    val matchedSignatureCount: Int,
    val primarySignatureMatched: Boolean,
    val chosenSignatureCandidate: String?,
    val maskedColorMatched: Boolean,
    val maskedColorConfidence: Float,
    val rawColorMatched: Boolean,
    val rawColorConfidence: Float,
    val hueMatched: Boolean,
    val hueConfidence: Float,
    val histogramHueMatched: Boolean,
    val histogramHueConfidence: Float,
    val directColorMatched: Boolean?,
    val directColorConfidence: Float?,
    val chooseShinyResultColorFallbackCalled: Boolean,
    val colorFallbackReason: String,
    val finalDetectorShiny: Boolean,
    val finalDetectorConfidence: Float,
    val neutralColorMatched: Boolean = false,
    val neutralColorConfidence: Float = 0f,
    val neutralColorPixelRatio: Float = 0f,
    val neutralColorFallbackApplied: Boolean = false
)

data class ShinySignatureCandidateDiagnostics(
    val source: String,
    val referenceAvailable: Boolean,
    val matched: Boolean,
    val confidence: Float,
    val normalScore: Float?,
    val shinyScore: Float?,
    val normalColorScore: Float?,
    val shinyColorScore: Float?,
    val scoreGap: Float?,
    val colorGap: Float?,
    val selectedHistogram: String?,
    val rejectionReason: String
)
