package com.pokerarity.scanner.data.model

internal fun mergeScanExtraAnalysis(
    baseAnalysis: List<RarityAnalysisItem>,
    recognitionIdentity: RecognitionIdentity?,
): List<RarityAnalysisItem> {
    val identityAnalysis = buildList {
        if (recognitionIdentity?.specialForm == RecognitionTruth.TRUE &&
            !baseAnalysis.containsSpecialFormExplanation()
        ) {
            add(RarityAnalysisItem("Special form", null, true))
        }
        recognitionIdentity?.let { addAll(it.positiveVariantAnalysisItems()) }
    }
    return (baseAnalysis + identityAnalysis).distinctBy { it.title to it.isPositive }
}
