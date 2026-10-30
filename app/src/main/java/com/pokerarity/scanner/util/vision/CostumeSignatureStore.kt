package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import com.pokerarity.scanner.data.repository.MasterPokedexLoader
import com.pokerarity.scanner.data.repository.RemoteMetadataStore
import org.json.JSONObject
import kotlin.math.min

object CostumeSignatureStore {

    private const val ASSET_PATH = "data/costume_signatures.json"
    private const val COSTUME_SCORE_THRESHOLD = 0.30f
    private const val COSTUME_MARGIN = 0.02f
    private const val COSTUME_SOFT_MARGIN = 0.01f
    private const val DENSE_VARIANT_SCORE_THRESHOLD = 0.300f
    private const val DENSE_VARIANT_MARGIN_THRESHOLD = 0.040f
    private const val EDGE_BINS = 8

    private var loaded = false
    private var bySpecies: Map<String, List<CostumeSignature>> = emptyMap()

    data class CostumeSignature(
        val species: String,
        val key: String,
        val isCostume: Boolean,
        val aHash: String,
        val dHash: String,
        val pHash: String?,
        val headPHash: String?,
        val edge: FloatArray
    )

    data class MatchDetails(
        val matched: Boolean,
        val confidence: Float,
        val bestCostume: Float,
        val bestNormal: Float,
        val scoreGap: Float,
        val costumeCandidateCount: Int,
        val denseVariantSpecies: Boolean,
        val bestCostumeKey: String? = null,
        val bestNormalKey: String? = null,
        val candidateCount: Int = 0,
        val rejectionReason: String = "unknown"
    )

    fun hasSpecies(context: Context, species: String?): Boolean {
        if (species.isNullOrBlank()) return false
        ensureLoaded(context)
        return bySpecies.containsKey(species.lowercase())
    }

    fun match(context: Context, bitmap: Bitmap, species: String?): Pair<Boolean, Float> {
        if (species.isNullOrBlank()) return Pair(false, 0f)
        ensureLoaded(context)

        val candidates = bySpecies[species.lowercase()] ?: return Pair(false, 0f)
        if (candidates.isEmpty()) return Pair(false, 0f)

        val signature = SpriteSignature.compute(bitmap)
        val pHash = PerceptualHash.compute(bitmap)
        val headPHash = computeHeadPHash(bitmap)
        val details = matchSignatureInternal(signature, candidates, pHash, headPHash)
        return Pair(details.matched, details.confidence)
    }

    fun matchBitmapDetails(context: Context, bitmap: Bitmap, species: String?): MatchDetails? {
        if (species.isNullOrBlank()) return null
        ensureLoaded(context)

        val candidates = bySpecies[species.lowercase()] ?: return null
        if (candidates.isEmpty()) return null

        val signature = SpriteSignature.compute(bitmap)
        val pHash = PerceptualHash.compute(bitmap)
        val headPHash = computeHeadPHash(bitmap)
        return matchSignatureInternal(signature, candidates, pHash, headPHash)
    }

    fun matchSignature(
        signature: SpriteSignature.Signature,
        species: String?,
        pHash: String? = null,
        headPHash: String? = null
    ): Pair<Boolean, Float> {
        val details = matchSignatureDetails(signature, species, pHash, headPHash) ?: return Pair(false, 0f)
        return Pair(details.matched, details.confidence)
    }

    fun matchSignatureDetails(
        signature: SpriteSignature.Signature,
        species: String?,
        pHash: String? = null,
        headPHash: String? = null
    ): MatchDetails? {
        if (species.isNullOrBlank()) return null
        val candidates = bySpecies[species.lowercase()] ?: return null
        return matchSignatureInternal(signature, candidates, pHash, headPHash)
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        try {
            bySpecies = loadFromMasterPokedex(context) ?: loadFromLegacySignatureJson(context)
        } catch (_: Exception) {
            bySpecies = emptyMap()
        }
    }
    private fun loadFromMasterPokedex(context: Context): Map<String, List<CostumeSignature>>? {
        val masterText = RemoteMetadataStore.readTextIfExists(context, "master_pokedex.json")
            ?: runCatching { context.assets.open("data/master_pokedex.json").bufferedReader().use { it.readText() } }.getOrNull()
            ?: return null
        val db = MasterPokedexLoader.parseJson(masterText)
        val map = mutableMapOf<String, MutableList<CostumeSignature>>()
        db.entries.forEach { entry ->
            val signature = entry.signature ?: return@forEach
            if (signature.aHash.isNullOrBlank() || signature.dHash.isNullOrBlank()) return@forEach
            val edge = FloatArray(EDGE_BINS)
            signature.edge.take(EDGE_BINS).forEachIndexed { index, value -> edge[index] = value }
            val item = CostumeSignature(
                species = entry.species,
                key = entry.spriteKey,
                isCostume = entry.isCostumeLike,
                aHash = signature.aHash,
                dHash = signature.dHash,
                pHash = signature.pHash,
                headPHash = signature.headPHash,
                edge = edge
            )
            map.getOrPut(entry.species.lowercase()) { mutableListOf() }.add(item)
        }
        return map.takeIf { it.isNotEmpty() }
    }

    private fun loadFromLegacySignatureJson(context: Context): Map<String, List<CostumeSignature>> {
        val json = RemoteMetadataStore.readTextIfExists(context, "costume_signatures.json")
            ?: context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val arr = root.optJSONArray("signatures") ?: return emptyMap()
        val map = mutableMapOf<String, MutableList<CostumeSignature>>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val species = obj.optString("species", "").trim()
            if (species.isEmpty()) continue
            val isCostume = obj.optBoolean("isCostume", false)
            val aHash = obj.optString("aHash", "")
            val dHash = obj.optString("dHash", "")
            val edgeArr = obj.optJSONArray("edge")
            val edge = FloatArray(EDGE_BINS)
            if (edgeArr != null) {
                for (j in 0 until min(edgeArr.length(), EDGE_BINS)) {
                    edge[j] = edgeArr.optDouble(j, 0.0).toFloat()
                }
            }
            val sig = CostumeSignature(
                species = species,
                key = obj.optString("key", ""),
                isCostume = isCostume,
                aHash = aHash,
                dHash = dHash,
                pHash = obj.optString("pHash", "").ifBlank { null },
                headPHash = obj.optString("headPHash", "").ifBlank { null },
                edge = edge
            )
            map.getOrPut(species.lowercase()) { mutableListOf() }.add(sig)
        }
        return map
    }

    private data class SignatureScores(
        val bestCostume: Float,
        val bestNormal: Float,
        val bestCostumeKey: String?,
        val bestNormalKey: String?
    ) {
        val scoreGap: Float
            get() = if (bestNormal == Float.MAX_VALUE) 1f else bestNormal - bestCostume
    }

    private fun matchSignatureInternal(
        signature: SpriteSignature.Signature,
        candidates: List<CostumeSignature>,
        pHash: String?,
        headPHash: String?
    ): MatchDetails {
        val scores = scoreCandidates(signature, candidates, pHash, headPHash)
        val costumeCandidateCount = candidates.count { it.isCostume }
        val denseVariantSpecies = costumeCandidateCount >= 8 || candidates.size >= 14
        val matched = isCostumeMatch(scores, denseVariantSpecies)
        val rejectionReason = when {
            matched -> "costume_thresholds_passed"
            costumeCandidateCount == 0 -> "no_costume_candidates"
            scores.bestCostume == Float.MAX_VALUE -> "costume_candidate_without_score"
            denseVariantSpecies && scores.bestCostume > DENSE_VARIANT_SCORE_THRESHOLD ->
                "dense_costume_score_above_threshold"
            denseVariantSpecies && scores.scoreGap < DENSE_VARIANT_MARGIN_THRESHOLD ->
                "dense_costume_gap_below_threshold"
            !denseVariantSpecies && scores.bestNormal != Float.MAX_VALUE &&
                scores.bestCostume + COSTUME_MARGIN >= scores.bestNormal ->
                "normal_candidate_not_beaten_by_margin"
            !denseVariantSpecies && scores.bestCostume >= COSTUME_SCORE_THRESHOLD && scores.scoreGap < 0.03f ->
                "costume_score_and_gap_rejected"
            else -> "costume_thresholds_failed"
        }

        return MatchDetails(
            matched = matched,
            confidence = costumeConfidence(matched, scores, denseVariantSpecies),
            bestCostume = scores.bestCostume,
            bestNormal = scores.bestNormal,
            scoreGap = scores.scoreGap,
            costumeCandidateCount = costumeCandidateCount,
            denseVariantSpecies = denseVariantSpecies,
            bestCostumeKey = scores.bestCostumeKey,
            bestNormalKey = scores.bestNormalKey,
            candidateCount = candidates.size,
            rejectionReason = rejectionReason
        )
    }

    private fun scoreCandidates(
        signature: SpriteSignature.Signature,
        candidates: List<CostumeSignature>,
        pHash: String?,
        headPHash: String?
    ): SignatureScores {
        var bestCostume = Float.MAX_VALUE
        var bestNormal = Float.MAX_VALUE
        var bestCostumeKey: String? = null
        var bestNormalKey: String? = null

        for (entry in candidates) {
            val ah = SpriteSignature.hammingHex(signature.aHash, entry.aHash) / 64f
            val dh = SpriteSignature.hammingHex(signature.dHash, entry.dHash) / 64f
            val ed = SpriteSignature.edgeDistance(signature.edge, entry.edge)
            val ph = normalizedHashDistance(pHash, entry.pHash)
            val head = normalizedHashDistance(headPHash, entry.headPHash)
            val score = 0.28f * dh + 0.20f * ah + 0.18f * ed + 0.14f * ph + 0.20f * head
            if (entry.isCostume) {
                if (score < bestCostume) {
                    bestCostume = score
                    bestCostumeKey = entry.key
                }
            } else if (score < bestNormal) {
                bestNormal = score
                bestNormalKey = entry.key
            }
        }

        return SignatureScores(bestCostume, bestNormal, bestCostumeKey, bestNormalKey)
    }

    private fun isCostumeMatch(scores: SignatureScores, denseVariantSpecies: Boolean): Boolean {
        if (denseVariantSpecies) {
            return scores.bestCostume <= DENSE_VARIANT_SCORE_THRESHOLD &&
                scores.scoreGap >= DENSE_VARIANT_MARGIN_THRESHOLD
        }

        val costumeWins = scores.bestCostume + COSTUME_MARGIN < scores.bestNormal
        val strongMargin = scores.scoreGap >= 0.03f
        return costumeWins && (scores.bestCostume < COSTUME_SCORE_THRESHOLD || strongMargin)
    }

    private fun costumeConfidence(
        matched: Boolean,
        scores: SignatureScores,
        denseVariantSpecies: Boolean
    ): Float = when {
        !matched -> 0f
        denseVariantSpecies -> denseVariantConfidence(scores)
        else -> standardCostumeConfidence(scores)
    }

    private fun denseVariantConfidence(scores: SignatureScores): Float {
        val scoreStrength = ((DENSE_VARIANT_SCORE_THRESHOLD - scores.bestCostume) / 0.05f)
            .coerceIn(0f, 1f)
        val gapStrength = ((scores.scoreGap - DENSE_VARIANT_MARGIN_THRESHOLD) / 0.04f)
            .coerceIn(0f, 1f)
        return (0.20f + 0.55f * scoreStrength + 0.25f * gapStrength).coerceIn(0f, 1f)
    }

    private fun standardCostumeConfidence(scores: SignatureScores): Float {
        val absScore = ((COSTUME_SCORE_THRESHOLD - scores.bestCostume) / COSTUME_SCORE_THRESHOLD)
            .coerceIn(0f, 1f)
        val marginScore = if (scores.bestNormal == Float.MAX_VALUE) {
            1f
        } else {
            (scores.scoreGap / 0.2f).coerceIn(0f, 1f)
        }
        val costumeWins = scores.bestCostume + COSTUME_MARGIN < scores.bestNormal
        return if (costumeWins) {
            (0.6f * absScore + 0.4f * marginScore).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    private fun normalizedHashDistance(left: String?, right: String?): Float {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return 1f
        val bitCount = left.length * 4
        return (SpriteSignature.hammingHex(left, right).toFloat() / bitCount.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    private fun computeHeadPHash(bitmap: Bitmap): String {
        val height = (bitmap.height * 0.35f).toInt().coerceIn(1, bitmap.height)
        val head = if (height == bitmap.height) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, height)
        return try {
            PerceptualHash.compute(head)
        } finally {
            if (head !== bitmap) head.recycle()
        }
    }
}
