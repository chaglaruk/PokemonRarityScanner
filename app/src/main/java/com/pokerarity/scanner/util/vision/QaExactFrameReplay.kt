package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap

private const val HEAD_CROP_FRACTION = 0.35f
private const val UPPER_CROP_FRACTION = 0.55f
private const val BODY_CROP_FRACTION = 0.30f
private const val HUE_HISTOGRAM_BINS = 12
private const val HUE_RANGE_DEGREES = 360.0
private const val HUE_BIN_CENTER_OFFSET = 0.5
private const val MIN_HUE_MASS = 0.0001

internal data class QaExactFrameReplayResult(
    val costumeReferenceDistances: List<QaCostumeReferenceDistance>,
    val shinyPrimary: QaShinySignatureMatch?,
    val shinyAlternates: List<QaShinySignatureMatch>,
    val costumeDecisionReason: String,
    val hueEvidence: QaExactFrameHueEvidence
)

internal data class QaExactFrameHueEvidence(
    val maskedBodyHistogram: List<Float>,
    val sourceBodyHistogram: List<Float>,
    val maskedBodyHueCentroid: Float?,
    val sourceBodyHueCentroid: Float?
)

private data class ReplaySignatureFeatures(
    val signature: SpriteSignature.Signature,
    val pHash: String,
    val headPHash: String
)

private data class ReplayCostumeResult(
    val referenceDistances: List<QaCostumeReferenceDistance>,
    val decisionReason: String
)

private data class ReplayShinyResult(
    val primary: QaShinySignatureMatch?,
    val alternates: List<QaShinySignatureMatch>,
    val hueEvidence: QaExactFrameHueEvidence
)

private data class ReplayShinyInput(
    val context: Context,
    val species: String?,
    val maskedSprite: Bitmap,
    val analysisRegion: Bitmap,
    val signature: SpriteSignature.Signature,
    val strictShiny: Boolean
)

/** Deterministic replay over the PNGs written by the exact-frame QA evidence writer. */
internal object QaExactFrameReplay {

    fun evaluate(
        context: Context,
        species: String?,
        maskedSprite: Bitmap,
        analysisRegion: Bitmap,
        strictShiny: Boolean
    ): QaExactFrameReplayResult {
        val features = signatureFeatures(maskedSprite)
        val costume = replayCostume(context, species, features)
        val shiny = replayShiny(
            ReplayShinyInput(context, species, maskedSprite, analysisRegion, features.signature, strictShiny)
        )
        return QaExactFrameReplayResult(
            costumeReferenceDistances = costume.referenceDistances,
            shinyPrimary = shiny.primary,
            shinyAlternates = shiny.alternates,
            costumeDecisionReason = costume.decisionReason,
            hueEvidence = shiny.hueEvidence
        )
    }

    private fun signatureFeatures(maskedSprite: Bitmap): ReplaySignatureFeatures {
        val signature = SpriteSignature.computeFromMaskedSprite(maskedSprite)
        val pHash = PerceptualHash.compute(maskedSprite)
        val headHeight = (maskedSprite.height * HEAD_CROP_FRACTION).toInt().coerceIn(1, maskedSprite.height)
        val head = if (headHeight == maskedSprite.height) maskedSprite else
            Bitmap.createBitmap(maskedSprite, 0, 0, maskedSprite.width, headHeight)
        val headPHash = try {
            PerceptualHash.compute(head)
        } finally {
            if (head !== maskedSprite) head.recycle()
        }
        return ReplaySignatureFeatures(signature, pHash, headPHash)
    }

    private fun replayCostume(
        context: Context,
        species: String?,
        features: ReplaySignatureFeatures
    ): ReplayCostumeResult {
        val referencesAvailable = CostumeSignatureStore.hasSpecies(context, species)
        val details = if (referencesAvailable) {
            CostumeSignatureStore.matchSignatureDetails(
                features.signature,
                species,
                features.pHash,
                features.headPHash
            )
        } else {
            null
        }
        val referenceDistances = if (referencesAvailable) {
            CostumeSignatureStore.diagnosticCandidateDistances(
                features.signature,
                species,
                features.pHash,
                features.headPHash
            ).mapIndexed { index, item ->
                QaCostumeReferenceDistance(
                    key = item.key,
                    costume = item.isCostume,
                    aHashDistance = item.aHashDistance,
                    dHashDistance = item.dHashDistance,
                    edgeDistance = item.edgeDistance,
                    pHashDistance = item.pHashDistance,
                    headPHashDistance = item.headPHashDistance,
                    weightedScore = item.weightedScore,
                    rank = index + 1
                )
            }
        } else {
            emptyList()
        }
        return ReplayCostumeResult(
            referenceDistances = referenceDistances,
            decisionReason = details?.rejectionReason ?: "species_reference_missing"
        )
    }

    private fun replayShiny(input: ReplayShinyInput): ReplayShinyResult {
        val maskedBody = bodyCrop(input.maskedSprite)
        val sourceBody = bodyCrop(input.analysisRegion)
        try {
            val maskedBodyHist = SpriteColorSignature.computeHueHistogram(maskedBody)
            val sourceBodyHist = SpriteColorSignature.computeHueHistogram(sourceBody)
            val shinyAvailable = ShinySignatureStore.hasSpecies(input.context, input.species)
            val primary = if (shinyAvailable) {
                replayShinyPrimary(
                    input.signature,
                    maskedBodyHist,
                    sourceBodyHist,
                    input.species,
                    input.strictShiny
                )
            } else {
                null
            }
            val alternates = replayShinyAlternates(
                input.maskedSprite,
                input.signature,
                input.species,
                input.strictShiny,
                shinyAvailable
            )
            return ReplayShinyResult(
                primary = primary,
                alternates = alternates,
                hueEvidence = QaExactFrameHueEvidence(
                    maskedBodyHistogram = maskedBodyHist.toBoundedList(),
                    sourceBodyHistogram = sourceBodyHist.toBoundedList(),
                    maskedBodyHueCentroid = hueCentroid(maskedBodyHist),
                    sourceBodyHueCentroid = hueCentroid(sourceBodyHist)
                )
            )
        } finally {
            if (maskedBody !== input.maskedSprite) maskedBody.recycle()
            if (sourceBody !== input.analysisRegion) sourceBody.recycle()
        }
    }

    private fun replayShinyPrimary(
        signature: SpriteSignature.Signature,
        maskedBodyHist: FloatArray,
        sourceBodyHist: FloatArray,
        species: String?,
        strictShiny: Boolean
    ): QaShinySignatureMatch = ShinySignatureStore.matchSignatureDetails(
            signature,
            maskedBodyHist,
            sourceBodyHist,
            species,
            strictShiny
        ).toQaMatch()

    private fun replayShinyAlternates(
        maskedSprite: Bitmap,
        signature: SpriteSignature.Signature,
        species: String?,
        strictShiny: Boolean,
        shinyAvailable: Boolean
    ): List<QaShinySignatureMatch> {
        if (!shinyAvailable) return emptyList()
        val maskedFullHist = SpriteColorSignature.computeHueHistogram(maskedSprite)
        val upperHeight = (maskedSprite.height * UPPER_CROP_FRACTION).toInt().coerceIn(1, maskedSprite.height)
        val upper = if (upperHeight == maskedSprite.height) maskedSprite else
            Bitmap.createBitmap(maskedSprite, 0, 0, maskedSprite.width, upperHeight)
        return try {
            listOf(
                ShinySignatureStore.matchSignatureDetails(
                    signature,
                    maskedFullHist,
                    null,
                    species,
                    strictShiny
                ).toQaMatch(),
                ShinySignatureStore.matchSignatureDetails(
                    signature,
                    SpriteColorSignature.computeHueHistogram(upper),
                    null,
                    species,
                    strictShiny
                ).toQaMatch()
            )
        } finally {
            if (upper !== maskedSprite) upper.recycle()
        }
    }

    private fun bodyCrop(bitmap: Bitmap): Bitmap {
        val top = (bitmap.height * BODY_CROP_FRACTION).toInt().coerceIn(0, bitmap.height.coerceAtLeast(1) - 1)
        val height = (bitmap.height - top).coerceAtLeast(1)
        return if (top == 0 && height == bitmap.height) bitmap else
            Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
    }

    private fun FloatArray.toBoundedList(): List<Float> = take(HUE_HISTOGRAM_BINS)
        .map { it.takeIf(Float::isFinite) ?: 0f }

    private fun hueCentroid(histogram: FloatArray): Float? {
        var x = 0.0
        var y = 0.0
        var total = 0.0
        val step = HUE_RANGE_DEGREES / histogram.size.coerceAtLeast(1).toDouble()
        histogram.forEachIndexed { index, value ->
            if (value > 0f) {
                val angle = Math.toRadians((index + HUE_BIN_CENTER_OFFSET) * step)
                x += kotlin.math.cos(angle) * value
                y += kotlin.math.sin(angle) * value
                total += value
            }
        }
        if (total <= MIN_HUE_MASS) return null
        return Math.toDegrees(kotlin.math.atan2(y, x)).toFloat().let {
            if (it < 0f) it + HUE_RANGE_DEGREES.toFloat() else it
        }
    }
}
