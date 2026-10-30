package com.pokerarity.scanner.util.vision

private const val MAX_EXACT_FRAME_HISTOGRAM_BINS = 12
private const val MAX_QA_DIAGNOSTIC_TEXT_CHARS = 96
private const val MAX_EXACT_FRAME_FORMULA_CHARS = 160

internal fun QaShinySignatureMatch.bounded() = copy(
    confidence = confidence.finiteOrZero(),
    normalScore = normalScore.finiteOrNull(),
    shinyScore = shinyScore.finiteOrNull(),
    normalColorScore = normalColorScore.finiteOrNull(),
    shinyColorScore = shinyColorScore.finiteOrNull(),
    scoreGap = scoreGap.finiteOrNull(),
    colorGap = colorGap.finiteOrNull(),
    histogramSource = histogramSource.take(MAX_QA_DIAGNOSTIC_TEXT_CHARS),
    reasonCode = reasonCode.take(MAX_QA_DIAGNOSTIC_TEXT_CHARS),
    referenceAsset = referenceAsset.take(MAX_QA_DIAGNOSTIC_TEXT_CHARS),
    normalComponents = normalComponents?.bounded(),
    shinyComponents = shinyComponents?.bounded()
)

private fun QaShinyComponents.bounded() = copy(
    weightedScore = weightedScore.finiteOrNull(),
    colorDistance = colorDistance.finiteOrNull(),
    dHashDistance = dHashDistance.finiteOrNull(),
    aHashDistance = aHashDistance.finiteOrNull(),
    edgeDistance = edgeDistance.finiteOrNull()
)

internal fun QaExactFrameHueEvidence.bounded() = copy(
    maskedBodyHistogram = maskedBodyHistogram.take(MAX_EXACT_FRAME_HISTOGRAM_BINS).map { it.finiteOrZero() },
    sourceBodyHistogram = sourceBodyHistogram.take(MAX_EXACT_FRAME_HISTOGRAM_BINS).map { it.finiteOrZero() },
    maskedBodyHueCentroid = maskedBodyHueCentroid.finiteOrNull(),
    sourceBodyHueCentroid = sourceBodyHueCentroid.finiteOrNull()
)

internal fun QaExactFrameScoreDefinitions.bounded() = copy(
    costumeWeightedFormula = costumeWeightedFormula.take(MAX_EXACT_FRAME_FORMULA_CHARS),
    costumeGate = costumeGate.take(MAX_EXACT_FRAME_FORMULA_CHARS),
    shinyWeightedFormula = shinyWeightedFormula.take(MAX_EXACT_FRAME_FORMULA_CHARS),
    shinyGate = shinyGate.take(MAX_EXACT_FRAME_FORMULA_CHARS)
)

internal fun QaCostumeReferenceDistance.bounded() = copy(
    key = key.take(MAX_QA_DIAGNOSTIC_TEXT_CHARS),
    referenceAsset = referenceAsset.take(MAX_QA_DIAGNOSTIC_TEXT_CHARS),
    aHashDistance = aHashDistance.finiteOrZero(),
    dHashDistance = dHashDistance.finiteOrZero(),
    edgeDistance = edgeDistance.finiteOrZero(),
    pHashDistance = pHashDistance.finiteOrZero(),
    headPHashDistance = headPHashDistance.finiteOrZero(),
    weightedScore = weightedScore.finiteOrZero(),
    rank = rank.coerceIn(1, VariantStageDiagnosticRecorder.MAX_CANDIDATES)
)

private fun Float?.finiteOrNull(): Float? = this?.takeIf { it.isFinite() }

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f
