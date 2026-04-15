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
        val denseVariantSpecies: Boolean
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
        val details = matchSignatureInternal(signature, species, candidates, pHash, headPHash)
        return Pair(details.matched, details.confidence)
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
        return matchSignatureInternal(signature, species, candidates, pHash, headPHash)
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

    private fun matchSignatureInternal(
        signature: SpriteSignature.Signature,
        species: String,
        candidates: List<CostumeSignature>,
        pHash: String?,
        headPHash: String?
    ): MatchDetails {
        var bestCostume = Float.MAX_VALUE
        var bestNormal = Float.MAX_VALUE

        for (entry in candidates) {
            val ah = SpriteSignature.hammingHex(signature.aHash, entry.aHash) / 64f
            val dh = SpriteSignature.hammingHex(signature.dHash, entry.dHash) / 64f
            val ed = SpriteSignature.edgeDistance(signature.edge, entry.edge)
            val ph = normalizedHashDistance(pHash, entry.pHash)
            val head = normalizedHashDistance(headPHash, entry.headPHash)
            val score = 0.28f * dh + 0.20f * ah + 0.18f * ed + 0.14f * ph + 0.20f * head
            if (entry.isCostume) {
                if (score < bestCostume) bestCostume = score
            } else {
                if (score < bestNormal) bestNormal = score
            }
        }

        android.util.Log.d(
            "CostumeSignatureStore",
            "Costume scores for $species: bestCostume=$bestCostume, bestNormal=$bestNormal"
        )

        val scoreGap = if (bestNormal == Float.MAX_VALUE) 1f else bestNormal - bestCostume
        val costumeCandidateCount = candidates.count { it.isCostume }
        val denseVariantSpecies = costumeCandidateCount >= 8 || candidates.size >= 14
        val costumeWins = bestCostume + COSTUME_MARGIN < bestNormal
        val strongMargin = scoreGap >= 0.03f
        val denseVariantMatch = denseVariantSpecies &&
            bestCostume <= DENSE_VARIANT_SCORE_THRESHOLD &&
            scoreGap >= DENSE_VARIANT_MARGIN_THRESHOLD
        val matched = if (denseVariantSpecies) {
            denseVariantMatch
        } else {
            costumeWins && (bestCostume < COSTUME_SCORE_THRESHOLD || strongMargin)
        }
        android.util.Log.d(
            "CostumeSignatureStore",
            "Costume decision for $species: matched=$matched (threshold=$COSTUME_SCORE_THRESHOLD, margin=$COSTUME_MARGIN, scoreGap=$scoreGap, costumeCandidates=$costumeCandidateCount, denseVariantSpecies=$denseVariantSpecies)"
        )
        val confidence = if (matched) {
            if (denseVariantSpecies) {
                val scoreStrength = ((DENSE_VARIANT_SCORE_THRESHOLD - bestCostume) / 0.05f).coerceIn(0f, 1f)
                val gapStrength = ((scoreGap - DENSE_VARIANT_MARGIN_THRESHOLD) / 0.04f).coerceIn(0f, 1f)
                (0.20f + 0.55f * scoreStrength + 0.25f * gapStrength).coerceIn(0f, 1f)
            } else {
                val absScore = ((COSTUME_SCORE_THRESHOLD - bestCostume) / COSTUME_SCORE_THRESHOLD).coerceIn(0f, 1f)
                val marginScore = if (bestNormal == Float.MAX_VALUE) 1f else (scoreGap / 0.2f).coerceIn(0f, 1f)
                if (!costumeWins) {
                    0f
                } else {
                    (0.6f * absScore + 0.4f * marginScore).coerceIn(0f, 1f)
                }
            }
        } else {
            0f
        }

        return MatchDetails(
            matched = matched,
            confidence = confidence,
            bestCostume = bestCostume,
            bestNormal = bestNormal,
            scoreGap = scoreGap,
            costumeCandidateCount = costumeCandidateCount,
            denseVariantSpecies = denseVariantSpecies
        )
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
