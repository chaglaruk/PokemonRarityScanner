package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap

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

/** Deterministic replay over the PNGs written by the exact-frame QA evidence writer. */
internal object QaExactFrameReplay {

    fun evaluate(
        context: Context,
        species: String?,
        maskedSprite: Bitmap,
        analysisRegion: Bitmap,
        strictShiny: Boolean
    ): QaExactFrameReplayResult {
        val signature = SpriteSignature.computeFromMaskedSprite(maskedSprite)
        val pHash = PerceptualHash.compute(maskedSprite)
        val headHeight = (maskedSprite.height * 0.35f).toInt().coerceIn(1, maskedSprite.height)
        val head = if (headHeight == maskedSprite.height) maskedSprite else
            Bitmap.createBitmap(maskedSprite, 0, 0, maskedSprite.width, headHeight)
        val headPHash = try {
            PerceptualHash.compute(head)
        } finally {
            if (head !== maskedSprite) head.recycle()
        }

        val costumeAvailable = CostumeSignatureStore.hasSpecies(context, species)
        val costumeDetails = if (costumeAvailable) {
            CostumeSignatureStore.matchSignatureDetails(signature, species, pHash, headPHash)
        } else {
            null
        }
        val costumeDistances = if (costumeAvailable) {
            CostumeSignatureStore.diagnosticCandidateDistances(signature, species, pHash, headPHash)
                .mapIndexed { index, item ->
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

        val maskedBody = bodyCrop(maskedSprite)
        val sourceBody = bodyCrop(analysisRegion)
        try {
            val maskedBodyHist = SpriteColorSignature.computeHueHistogram(maskedBody)
            val sourceBodyHist = SpriteColorSignature.computeHueHistogram(sourceBody)
            val shinyAvailable = ShinySignatureStore.hasSpecies(context, species)
            val shinyPrimary = if (shinyAvailable) {
                ShinySignatureStore.matchSignatureDetails(
                    signature,
                    maskedBodyHist,
                    sourceBodyHist,
                    species,
                    strictShiny
                ).toQaMatch()
            } else {
                null
            }
            val maskedFullHist = SpriteColorSignature.computeHueHistogram(maskedSprite)
            val upperHeight = (maskedSprite.height * 0.55f).toInt().coerceIn(1, maskedSprite.height)
            val upper = if (upperHeight == maskedSprite.height) maskedSprite else
                Bitmap.createBitmap(maskedSprite, 0, 0, maskedSprite.width, upperHeight)
            val alternates = try {
                if (!shinyAvailable) emptyList() else listOf(
                    ShinySignatureStore.matchSignatureDetails(
                        signature, maskedFullHist, null, species, strictShiny
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
            return QaExactFrameReplayResult(
                costumeReferenceDistances = costumeDistances,
                shinyPrimary = shinyPrimary,
                shinyAlternates = alternates,
                costumeDecisionReason = costumeDetails?.rejectionReason ?: "species_reference_missing",
                hueEvidence = QaExactFrameHueEvidence(
                    maskedBodyHistogram = maskedBodyHist.toBoundedList(),
                    sourceBodyHistogram = sourceBodyHist.toBoundedList(),
                    maskedBodyHueCentroid = hueCentroid(maskedBodyHist),
                    sourceBodyHueCentroid = hueCentroid(sourceBodyHist)
                )
            )
        } finally {
            if (maskedBody !== maskedSprite) maskedBody.recycle()
            if (sourceBody !== analysisRegion) sourceBody.recycle()
        }
    }

    private fun bodyCrop(bitmap: Bitmap): Bitmap {
        val top = (bitmap.height * 0.30f).toInt().coerceIn(0, bitmap.height.coerceAtLeast(1) - 1)
        val height = (bitmap.height - top).coerceAtLeast(1)
        return if (top == 0 && height == bitmap.height) bitmap else
            Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
    }

    private fun FloatArray.toBoundedList(): List<Float> = take(12).map { it.takeIf(Float::isFinite) ?: 0f }

    private fun hueCentroid(histogram: FloatArray): Float? {
        var x = 0.0
        var y = 0.0
        var total = 0.0
        val step = 360.0 / histogram.size.coerceAtLeast(1).toDouble()
        histogram.forEachIndexed { index, value ->
            if (value > 0f) {
                val angle = Math.toRadians((index + 0.5) * step)
                x += kotlin.math.cos(angle) * value
                y += kotlin.math.sin(angle) * value
                total += value
            }
        }
        if (total <= 0.0001) return null
        return Math.toDegrees(kotlin.math.atan2(y, x)).toFloat().let { if (it < 0f) it + 360f else it }
    }
}
