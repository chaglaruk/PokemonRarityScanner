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
    val costumeDecisionDiagnostics: CostumeDecisionDiagnostics? = null
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
