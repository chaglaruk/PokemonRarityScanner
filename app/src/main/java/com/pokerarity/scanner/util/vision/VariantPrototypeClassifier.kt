package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap

class VariantPrototypeClassifier(private val context: Context) {

    data class MatchResult(
        val species: String,
        val assetKey: String,
        val spriteKey: String,
        val variantType: String,
        val isShiny: Boolean,
        val isCostumeLike: Boolean,
        val scope: String,
        val score: Float,
        val confidence: Float,
        val speciesMargin: Float,
        val variantMargin: Float,
        val bestBaseScore: Float? = null,
        val bestBaseAssetKey: String? = null,
        val bestBaseSpriteKey: String? = null,
        val bestNonBaseScore: Float? = null,
        val bestNonBaseSpecies: String? = null,
        val bestNonBaseAssetKey: String? = null,
        val bestNonBaseSpriteKey: String? = null,
        val bestNonBaseVariantType: String? = null,
        val bestNonBaseIsShiny: Boolean = false,
        val bestNonBaseIsCostumeLike: Boolean = false,
        val bestShinyPeerScore: Float? = null,
        val bestShinyPeerAssetKey: String? = null,
        val bestShinyPeerSpriteKey: String? = null,
        val bestBaseShinyPeerScore: Float? = null,
        val bestBaseShinyPeerAssetKey: String? = null,
        val bestBaseShinyPeerSpriteKey: String? = null,
        val rescueKind: String? = null,
        val topSpecies: List<String>
    )

    internal data class ScopeMatches(
        val globalMatch: MatchResult?,
        val speciesMatch: MatchResult?
    )

    internal data class ScreenshotFeatures(
        val signature: SpriteSignature.Signature,
        val fullHist: FloatArray,
        val headHist: FloatArray,
        val upperHist: FloatArray,
        val bodyHist: FloatArray,
        val foregroundRatio: Float,
        val aspectRatio: Float
    )

    fun classify(bitmap: Bitmap, candidateSpecies: Collection<String> = emptyList()): MatchResult? {
        val candidates = VariantPrototypeStore.entriesForSpecies(context, candidateSpecies)
        if (candidates.isEmpty()) return null
        return classifyEntries(bitmap, candidates, "global")
    }

    fun classifyForSpecies(bitmap: Bitmap, species: String?): MatchResult? {
        if (species.isNullOrBlank()) return null
        val candidates = VariantPrototypeStore.entriesForSpecies(context, setOf(species))
        if (candidates.isEmpty()) return null
        return classifyEntries(bitmap, candidates, "species")
    }

    private fun classifyEntries(
        bitmap: Bitmap,
        candidates: List<VariantPrototypeStore.Entry>,
        scope: String
    ): MatchResult? = classifyEntries(extractFeatures(bitmap), candidates, scope)

    internal fun classifyEntries(
        features: ScreenshotFeatures,
        candidates: List<VariantPrototypeStore.Entry>,
        scope: String
    ): MatchResult? {
        val scored = candidates.map { entry -> entry to score(features, entry) }.sortedBy { it.second }
        val best = scored.firstOrNull() ?: return null
        val summary = summarizeScores(scored, scope)
        val peers = findVariantPeers(scored, best.first)
        val bestEntry = best.first
        return MatchResult(
            species = bestEntry.species,
            assetKey = bestEntry.assetKey,
            spriteKey = bestEntry.spriteKey,
            variantType = bestEntry.variantType,
            isShiny = bestEntry.isShiny,
            isCostumeLike = bestEntry.isCostumeLike,
            scope = scope,
            score = best.second,
            confidence = summary.confidence,
            speciesMargin = summary.speciesMargin,
            variantMargin = summary.variantMargin,
            bestBaseScore = peers.bestBase?.second,
            bestBaseAssetKey = peers.bestBase?.first?.assetKey,
            bestBaseSpriteKey = peers.bestBase?.first?.spriteKey,
            bestNonBaseScore = peers.bestNonBase?.second,
            bestNonBaseSpecies = peers.bestNonBase?.first?.species,
            bestNonBaseAssetKey = peers.bestNonBase?.first?.assetKey,
            bestNonBaseSpriteKey = peers.bestNonBase?.first?.spriteKey,
            bestNonBaseVariantType = peers.bestNonBase?.first?.variantType,
            bestNonBaseIsShiny = peers.bestNonBase?.first?.isShiny ?: false,
            bestNonBaseIsCostumeLike = peers.bestNonBase?.first?.isCostumeLike ?: false,
            bestShinyPeerScore = peers.bestShinyPeer?.second,
            bestShinyPeerAssetKey = peers.bestShinyPeer?.first?.assetKey,
            bestShinyPeerSpriteKey = peers.bestShinyPeer?.first?.spriteKey,
            bestBaseShinyPeerScore = peers.bestBaseShinyPeer?.second,
            bestBaseShinyPeerAssetKey = peers.bestBaseShinyPeer?.first?.assetKey,
            bestBaseShinyPeerSpriteKey = peers.bestBaseShinyPeer?.first?.spriteKey,
            topSpecies = summary.topSpecies
        )
    }

    internal fun extractFeatures(bitmap: Bitmap): ScreenshotFeatures {
        val scaled = ColorAnalyzer.downscaleForAnalysis(bitmap)
        val shouldRecycleScaled = scaled !== bitmap
        val masked = ColorAnalyzer.extractMaskedSprite(scaled)

        val signature = SpriteSignature.computeFromMaskedSprite(masked)
        val fullHist = SpriteColorSignature.computeHueHistogram(masked)
        val headHeight = (masked.height * 0.30f).toInt().coerceIn(1, masked.height)
        val upperHeight = (masked.height * 0.55f).toInt().coerceIn(1, masked.height)
        val bodyTop = (masked.height * 0.30f).toInt().coerceIn(0, masked.height.coerceAtLeast(1) - 1)
        val bodyHeight = (masked.height - bodyTop).coerceAtLeast(1)
        val head = if (headHeight == masked.height) masked else Bitmap.createBitmap(masked, 0, 0, masked.width, headHeight)
        val upper = if (upperHeight == masked.height) masked else Bitmap.createBitmap(masked, 0, 0, masked.width, upperHeight)
        val body = if (bodyTop == 0 && bodyHeight == masked.height) masked else Bitmap.createBitmap(masked, 0, bodyTop, masked.width, bodyHeight)
        val headHist = SpriteColorSignature.computeHueHistogram(head)
        val upperHist = SpriteColorSignature.computeHueHistogram(upper)
        val bodyHist = SpriteColorSignature.computeHueHistogram(body)
        val foregroundRatio = SpriteColorSignature.computeForegroundRatio(masked)
        val aspectRatio = masked.width.toFloat() / masked.height.coerceAtLeast(1).toFloat()

        if (head !== masked) head.recycle()
        if (upper !== masked) upper.recycle()
        if (body !== masked) body.recycle()
        masked.recycle()
        if (shouldRecycleScaled) scaled.recycle()

        return ScreenshotFeatures(
            signature = signature,
            fullHist = fullHist,
            headHist = headHist,
            upperHist = upperHist,
            bodyHist = bodyHist,
            foregroundRatio = foregroundRatio,
            aspectRatio = aspectRatio
        )
    }

    private fun score(features: ScreenshotFeatures, entry: VariantPrototypeStore.Entry): Float {
        val dHash = SpriteSignature.hammingHex(features.signature.dHash, entry.prototype.dHash) / 64f
        val aHash = SpriteSignature.hammingHex(features.signature.aHash, entry.prototype.aHash) / 64f
        val edge = SpriteSignature.edgeDistance(features.signature.edge, entry.prototype.edge.toFloatArray())
        val fullHist = histogramDistance(features.fullHist, entry.prototype.fullHist)
        val headHist = histogramDistance(features.headHist, entry.prototype.headHist)
        val upperHist = histogramDistance(features.upperHist, entry.prototype.upperHist)
        val bodyHist = histogramDistance(features.bodyHist, entry.prototype.bodyHist)
        val fg = kotlin.math.abs(features.foregroundRatio - entry.prototype.foregroundRatio)
        val aspect = kotlin.math.abs(features.aspectRatio - entry.prototype.aspectRatio).coerceIn(0f, 1f)
        return (
            0.23f * dHash +
                0.18f * aHash +
                0.15f * edge +
                0.15f * bodyHist +
                0.14f * headHist +
                0.09f * upperHist +
                0.08f * fullHist +
                0.04f * fg +
                0.02f * aspect
            ).coerceIn(0f, 1f)
    }

    private fun histogramDistance(observed: FloatArray, reference: List<Float>): Float {
        if (observed.isEmpty() || reference.isEmpty()) return 1f
        val size = minOf(observed.size, reference.size)
        var sum = 0f
        for (index in 0 until size) {
            sum += kotlin.math.abs(observed[index] - reference[index])
        }
        return (sum / 2f).coerceIn(0f, 1f)
    }
}
private const val DEFAULT_SCORE_GAP = 0.25f
private const val TOP_MATCH_COUNT = 3
private const val TOP_SCORE_FORMAT = "%.3f"
private const val SPECIES_ABSOLUTE_WEIGHT = 0.70f
private const val SPECIES_VARIANT_MARGIN_WEIGHT = 0.30f
private const val SPECIES_VARIANT_MARGIN_SCALE = 0.12f
private const val GLOBAL_ABSOLUTE_WEIGHT = 0.55f
private const val GLOBAL_SPECIES_MARGIN_WEIGHT = 0.30f
private const val GLOBAL_SPECIES_MARGIN_SCALE = 0.20f
private const val GLOBAL_VARIANT_MARGIN_WEIGHT = 0.15f
private const val GLOBAL_VARIANT_MARGIN_SCALE = 0.15f

private data class ScoreSummary(
    val speciesMargin: Float,
    val variantMargin: Float,
    val confidence: Float,
    val topSpecies: List<String>
)

private data class VariantPeers(
    val bestBase: Pair<VariantPrototypeStore.Entry, Float>?,
    val bestNonBase: Pair<VariantPrototypeStore.Entry, Float>?,
    val bestShinyPeer: Pair<VariantPrototypeStore.Entry, Float>?,
    val bestBaseShinyPeer: Pair<VariantPrototypeStore.Entry, Float>?
)

private fun summarizeScores(
    scored: List<Pair<VariantPrototypeStore.Entry, Float>>,
    scope: String
): ScoreSummary {
    val best = scored.first()
    val speciesBest = scored.groupBy { it.first.species }
        .mapValues { (_, values) -> values.minOf { it.second } }
        .entries
        .sortedBy { it.value }
    val bestSpeciesScore = speciesBest.firstOrNull()?.value ?: best.second
    val secondSpeciesScore = speciesBest.getOrNull(1)?.value ?: (best.second + DEFAULT_SCORE_GAP)
    val secondEntryScore = scored.getOrNull(1)?.second ?: (best.second + DEFAULT_SCORE_GAP)
    val speciesMargin = (secondSpeciesScore - bestSpeciesScore).coerceAtLeast(0f)
    val variantMargin = (secondEntryScore - best.second).coerceAtLeast(0f)
    val absoluteStrength = 1f - best.second.coerceIn(0f, 1f)
    val confidence = confidenceFor(scope, absoluteStrength, speciesMargin, variantMargin)
    val topSpecies = if (scope == "species") {
        scored.take(TOP_MATCH_COUNT).map { "${it.first.spriteKey}:${TOP_SCORE_FORMAT.format(it.second)}" }
    } else {
        speciesBest.take(TOP_MATCH_COUNT).map { "${it.key}:${TOP_SCORE_FORMAT.format(it.value)}" }
    }
    return ScoreSummary(speciesMargin, variantMargin, confidence, topSpecies)
}

private fun confidenceFor(
    scope: String,
    absoluteStrength: Float,
    speciesMargin: Float,
    variantMargin: Float
): Float = if (scope == "species") {
    (
        SPECIES_ABSOLUTE_WEIGHT * absoluteStrength +
            SPECIES_VARIANT_MARGIN_WEIGHT * (variantMargin / SPECIES_VARIANT_MARGIN_SCALE).coerceIn(0f, 1f)
        ).coerceIn(0f, 1f)
} else {
    (
        GLOBAL_ABSOLUTE_WEIGHT * absoluteStrength +
            GLOBAL_SPECIES_MARGIN_WEIGHT * (speciesMargin / GLOBAL_SPECIES_MARGIN_SCALE).coerceIn(0f, 1f) +
            GLOBAL_VARIANT_MARGIN_WEIGHT * (variantMargin / GLOBAL_VARIANT_MARGIN_SCALE).coerceIn(0f, 1f)
        ).coerceIn(0f, 1f)
}

private fun findVariantPeers(
    scored: List<Pair<VariantPrototypeStore.Entry, Float>>,
    bestEntry: VariantPrototypeStore.Entry
): VariantPeers {
    val bestBase = scored.firstOrNull { it.first.variantType == "base" }
    val bestNonBase = scored.firstOrNull { it.first.variantType != "base" }
    val bestShinyPeer = findShinyPeer(scored, bestEntry)
    val bestBaseShinyPeer = bestBase?.let { findShinyPeer(scored, it.first) }
    return VariantPeers(bestBase, bestNonBase, bestShinyPeer, bestBaseShinyPeer)
}

private fun findShinyPeer(
    scored: List<Pair<VariantPrototypeStore.Entry, Float>>,
    reference: VariantPrototypeStore.Entry
): Pair<VariantPrototypeStore.Entry, Float>? {
    val normalizedKey = normalizeVariantKey(reference.assetKey)
    return scored.firstOrNull { (entry, _) ->
        entry.species == reference.species &&
            entry.variantType == reference.variantType &&
            entry.isCostumeLike == reference.isCostumeLike &&
            entry.isShiny != reference.isShiny &&
            normalizeVariantKey(entry.assetKey) == normalizedKey
    }
}

private fun normalizeVariantKey(assetKey: String): String = assetKey.removeSuffix("_shiny")


/** Combines candidate retrieval with one shared screenshot feature pass. */
internal fun VariantPrototypeClassifier.classifyGlobalAndSpecies(
    context: Context,
    bitmap: Bitmap,
    candidateSpecies: Collection<String>,
    species: String?
): VariantPrototypeClassifier.ScopeMatches {
    val globalCandidates = runCatching {
        VariantPrototypeStore.entriesForSpecies(context, candidateSpecies)
    }.getOrDefault(emptyList())
    val speciesCandidates = species
        ?.takeIf { it.isNotBlank() }
        ?.let { target ->
            runCatching {
                VariantPrototypeStore.entriesForSpecies(context, setOf(target))
            }.getOrDefault(emptyList())
        }
        .orEmpty()
    return classifyCandidateScopes(bitmap, globalCandidates, speciesCandidates) { image ->
        extractFeatures(image)
    }
}

/** Feature-sharing seam keeps scope score parity directly testable. */
internal fun VariantPrototypeClassifier.classifyCandidateScopes(
    bitmap: Bitmap,
    globalCandidates: List<VariantPrototypeStore.Entry>,
    speciesCandidates: List<VariantPrototypeStore.Entry>,
    featureExtractor: (Bitmap) -> VariantPrototypeClassifier.ScreenshotFeatures
): VariantPrototypeClassifier.ScopeMatches {
    if (globalCandidates.isEmpty() && speciesCandidates.isEmpty()) {
        return VariantPrototypeClassifier.ScopeMatches(globalMatch = null, speciesMatch = null)
    }
    val features = featureExtractor(bitmap)
    val globalMatch = if (globalCandidates.isEmpty()) null else runCatching {
        classifyEntries(features, globalCandidates, "global")
    }.getOrNull()
    val speciesMatch = if (speciesCandidates.isEmpty()) null else runCatching {
        classifyEntries(features, speciesCandidates, "species")
    }.getOrNull()
    return VariantPrototypeClassifier.ScopeMatches(globalMatch, speciesMatch)
}
