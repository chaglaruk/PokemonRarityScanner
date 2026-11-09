package com.pokerarity.scanner.util.vision

import android.content.Context
import org.json.JSONObject
import kotlin.math.min

object ShinySignatureStore {

    private const val ASSET_PATH = "data/shiny_signatures.json"
    private const val SHINY_SCORE_THRESHOLD = 0.38f
    private const val SHINY_MARGIN = 0.01f
    private const val SHINY_RELATIVE_MARGIN = 0.04f
    private const val SHINY_RELATIVE_MARGIN_SOFT = 0.010f
    private const val SHINY_COLOR_GAP = 0.08f
    private const val SHINY_COLOR_GAP_SOFT = 0.015f
    private const val SHINY_MAX_COLOR = 0.70f
    private const val SHINY_MAX_TOTAL_RELATIVE = 0.52f
    private const val SHINY_RELATIVE_MARGIN_STRICT = 0.08f
    private const val SHINY_COLOR_GAP_STRICT = 0.12f
    private const val SHINY_MAX_COLOR_STRICT = 0.68f
    private const val SHINY_MAX_TOTAL_RELATIVE_STRICT = 0.50f
    private const val SHINY_REF_HUE_GAP_SOFT = 45f
    private const val EDGE_BINS = 8

    private var loaded = false
    private var bySpecies: Map<String, List<ShinyEntry>> = emptyMap()

    data class SignatureRef(
        val aHash: String,
        val dHash: String,
        val edge: FloatArray,
        val color: FloatArray
    )

    data class ShinyEntry(
        val species: String,
        val key: String,
        val normal: SignatureRef,
        val shiny: SignatureRef,
        /** Exact source form ID; a display label is not a verified crosswalk. */
        val formId: String? = null
    )

    data class ScoreParts(
        val total: Float,
        val color: Float,
        val dh: Float,
        val ah: Float,
        val ed: Float
    )

    data class SignatureMatchDetails(
        val referenceAvailable: Boolean,
        val matched: Boolean,
        val confidence: Float,
        val normalScore: Float? = null,
        val shinyScore: Float? = null,
        val normalColorScore: Float? = null,
        val shinyColorScore: Float? = null,
        val scoreGap: Float? = null,
        val colorGap: Float? = null,
        val selectedHistogram: String? = null,
        val rejectionReason: String
    )

    /** Values that jointly select a reference set without widening call signatures. */
    data class MatchRequest(
        val species: String?,
        val strictMode: Boolean = false,
        /** Exact source form ID backed by independent evidence; display labels are invalid. */
        val verifiedFormId: String? = null
    )

    fun hasSpecies(context: Context, species: String?, verifiedFormId: String? = null): Boolean {
        if (species.isNullOrBlank()) return false
        ensureLoaded(context)
        return selectEntry(bySpecies[species.lowercase()].orEmpty(), verifiedFormId) != null
    }

    /** A species-level color fallback cannot resolve which form produced the pixels. */
    fun hasUnresolvedForms(context: Context, species: String?, verifiedFormId: String? = null): Boolean {
        if (species.isNullOrBlank()) return false
        ensureLoaded(context)
        return unresolvedForms(bySpecies[species.lowercase()].orEmpty(), verifiedFormId)
    }

    internal fun unresolvedForms(entries: List<ShinyEntry>, verifiedFormId: String? = null): Boolean =
        entries.isNotEmpty() && selectEntry(entries, verifiedFormId) == null

    /** Keep all pairs for one species so form selection can fail closed. */
    internal fun groupEntries(entries: Iterable<ShinyEntry>): Map<String, List<ShinyEntry>> =
        entries.filter { it.species.isNotBlank() }
            .groupBy { it.species.trim().lowercase() }

    /** Never choose an arbitrary form when several source pairs exist. */
    internal fun selectEntry(entries: List<ShinyEntry>, verifiedFormId: String? = null): ShinyEntry? {
        val exactForm = verifiedFormId?.trim()?.takeIf { it.isNotEmpty() }
        return if (exactForm == null) {
            entries.singleOrNull()
        } else {
            entries.filter { it.formId?.equals(exactForm, ignoreCase = true) == true }.singleOrNull()
        }
    }

    fun matchSignature(
        signature: SpriteSignature.Signature,
        colorHist: FloatArray,
        species: String?
    ): Pair<Boolean, Float> {
        return matchSignature(signature, colorHist, null, MatchRequest(species))
    }

    fun matchSignature(
        signature: SpriteSignature.Signature,
        colorHist: FloatArray,
        altColorHist: FloatArray?,
        request: MatchRequest
    ): Pair<Boolean, Float> {
        val details = matchSignatureDetails(signature, colorHist, altColorHist, request)
        return Pair(details.matched, details.confidence)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    fun matchSignatureDetails(
        signature: SpriteSignature.Signature,
        colorHist: FloatArray,
        altColorHist: FloatArray?,
        request: MatchRequest
    ): SignatureMatchDetails {
        val species = request.species
        if (species.isNullOrBlank()) {
            return SignatureMatchDetails(
                referenceAvailable = false,
                matched = false,
                confidence = 0f,
                rejectionReason = "species_missing"
            )
        }
        val entries = bySpecies[species.lowercase()].orEmpty()
        val entry = selectEntry(entries, request.verifiedFormId) ?: return SignatureMatchDetails(
            referenceAvailable = entries.isNotEmpty(),
            matched = false,
            confidence = 0f,
            rejectionReason = if (entries.isEmpty()) "signature_reference_missing" else "signature_form_ambiguous"
        )

        val normalPartsPrimary = scoreWithBreakdown(signature, colorHist, entry.normal)
        val shinyPartsPrimary = scoreWithBreakdown(signature, colorHist, entry.shiny)
        val (normalParts, shinyParts, selected) = if (altColorHist != null) {
            val normalPartsAlt = scoreWithBreakdown(signature, altColorHist, entry.normal)
            val shinyPartsAlt = scoreWithBreakdown(signature, altColorHist, entry.shiny)
            selectBestScores(
                normalPartsPrimary,
                shinyPartsPrimary,
                normalPartsAlt,
                shinyPartsAlt
            )
        } else {
            Triple(normalPartsPrimary, shinyPartsPrimary, "primary")
        }
        val normalScore = normalParts.total
        val shinyScore = shinyParts.total
        val shinyWins = shinyScore + SHINY_MARGIN < normalScore
        val colorGap = normalParts.color - shinyParts.color
        val scoreGap = normalScore - shinyScore
        val minColorGap = if (request.strictMode) SHINY_COLOR_GAP_STRICT else SHINY_COLOR_GAP
        val minScoreGap = if (request.strictMode) SHINY_RELATIVE_MARGIN_STRICT else SHINY_RELATIVE_MARGIN
        val maxColor = if (request.strictMode) SHINY_MAX_COLOR_STRICT else SHINY_MAX_COLOR
        val maxTotal = if (request.strictMode) SHINY_MAX_TOTAL_RELATIVE_STRICT else SHINY_MAX_TOTAL_RELATIVE
        val relativeWin = shinyWins &&
            colorGap >= minColorGap &&
            scoreGap >= minScoreGap &&
            shinyParts.color <= maxColor &&
            shinyScore <= maxTotal
        val matched = (shinyScore < SHINY_SCORE_THRESHOLD && shinyWins) || relativeWin
        val observedHist = when (selected) {
            "alt" -> altColorHist ?: colorHist
            else -> colorHist
        }
        val refHueGap = run {
            val normalHue = hueCentroid(entry.normal.color)
            val shinyHue = hueCentroid(entry.shiny.color)
            if (normalHue != null && shinyHue != null) hueDistance(normalHue, shinyHue) else 0f
        }
        val weakRelativeWin = !matched &&
            shinyWins &&
            refHueGap >= 45f &&
            scoreGap >= 0.04f &&
            colorGap >= 0.04f &&
            shinyParts.color <= normalParts.color + 0.015f &&
            shinyScore <= maxTotal + 0.03f
        val softRelativeWin = !matched &&
            !weakRelativeWin &&
            shinyWins &&
            refHueGap >= SHINY_REF_HUE_GAP_SOFT &&
            scoreGap >= 0.05f &&
            colorGap >= 0.08f &&
            shinyParts.color <= SHINY_MAX_COLOR &&
            shinyScore <= maxTotal
        val histogramFallback = if (!matched && !weakRelativeWin) {
            val normalHue = hueCentroid(entry.normal.color)
            val shinyHue = hueCentroid(entry.shiny.color)
            if (normalHue != null && shinyHue != null) {
                val normalMass = histogramMassNearHue(observedHist, normalHue)
                val shinyMass = histogramMassNearHue(observedHist, shinyHue)
                val strongRefGap = refHueGap >= 45f
                val ambiguousScore = scoreGap >= 0.01f
                val histWin = shinyWins &&
                    shinyMass >= 0.10f &&
                    shinyMass > normalMass + 0.05f &&
                    shinyParts.color <= maxColor &&
                    shinyScore <= maxTotal + 0.01f
                if (strongRefGap && ambiguousScore && histWin) {
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } else {
            false
        }
        val hueFallback = if (!matched) {
            val observedHue = hueCentroid(observedHist)
            val normalHue = hueCentroid(entry.normal.color)
            val shinyHue = hueCentroid(entry.shiny.color)
            if (observedHue != null && normalHue != null && shinyHue != null) {
                val refGap = hueDistance(normalHue, shinyHue)
                val dNormal = hueDistance(observedHue, normalHue)
                val dShiny = hueDistance(observedHue, shinyHue)
                val hueWin = shinyWins && dShiny + 12f < dNormal && dShiny <= 32f
                val strongRefGap = refGap >= 45f
                val ambiguousScore = (kotlin.math.abs(scoreGap) <= 0.06f || kotlin.math.abs(colorGap) <= 0.06f) &&
                    shinyScore <= maxTotal
                if (hueWin && strongRefGap && ambiguousScore) {
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } else {
            false
        }
        val finalMatched = matched || hueFallback || weakRelativeWin || softRelativeWin || histogramFallback
        val confidence = if (matched) {
            if (relativeWin && shinyScore >= SHINY_SCORE_THRESHOLD) {
                val colorScore = (colorGap / 0.2f).coerceIn(0f, 1f)
                val marginScore = (scoreGap / 0.2f).coerceIn(0f, 1f)
                (0.7f * colorScore + 0.3f * marginScore).coerceIn(0f, 1f)
            } else {
                val absScore = ((SHINY_SCORE_THRESHOLD - shinyScore) / SHINY_SCORE_THRESHOLD).coerceIn(0f, 1f)
                val marginScore = (scoreGap / 0.2f).coerceIn(0f, 1f)
                (0.6f * absScore + 0.4f * marginScore).coerceIn(0f, 1f)
            }
        } else if (hueFallback) {
            0.45f
        } else if (weakRelativeWin) {
            val hueScore = ((refHueGap - 35f) / 60f).coerceIn(0f, 1f)
            val marginScore = (scoreGap / 0.08f).coerceIn(0f, 1f)
            (0.25f + 0.35f * hueScore + 0.40f * marginScore).coerceIn(0.3f, 0.65f)
        } else if (softRelativeWin) {
            val hueScore = ((refHueGap - SHINY_REF_HUE_GAP_SOFT) / 80f).coerceIn(0f, 1f)
            val colorScore = ((colorGap - SHINY_COLOR_GAP_SOFT) / 0.12f).coerceIn(0f, 1f)
            val marginScore = ((scoreGap - SHINY_RELATIVE_MARGIN_SOFT) / 0.08f).coerceIn(0f, 1f)
            (0.22f + 0.30f * hueScore + 0.28f * colorScore + 0.20f * marginScore).coerceIn(0.28f, 0.60f)
        } else if (histogramFallback) {
            0.48f
        } else {
            0f
        }

        val rejectionReason = when {
            finalMatched && matched -> "signature_threshold_match"
            finalMatched && hueFallback -> "hue_fallback_match"
            finalMatched && weakRelativeWin -> "weak_relative_match"
            finalMatched && softRelativeWin -> "soft_relative_match"
            finalMatched && histogramFallback -> "histogram_fallback_match"
            !shinyWins -> "shiny_reference_not_closer"
            colorGap < minColorGap -> "color_gap_below_minimum"
            scoreGap < minScoreGap -> "score_gap_below_minimum"
            shinyParts.color > maxColor -> "shiny_color_distance_too_high"
            shinyScore > maxTotal -> "shiny_total_distance_too_high"
            else -> "relative_signature_conditions_unmet"
        }
        return SignatureMatchDetails(
            referenceAvailable = true,
            matched = finalMatched,
            confidence = confidence,
            normalScore = normalScore,
            shinyScore = shinyScore,
            normalColorScore = normalParts.color,
            shinyColorScore = shinyParts.color,
            scoreGap = scoreGap,
            colorGap = colorGap,
            selectedHistogram = selected,
            rejectionReason = rejectionReason
        )
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            bySpecies = parseEntries(json)
        } catch (_: Exception) {
            bySpecies = emptyMap()
        }
    }

    internal fun parseEntries(json: String): Map<String, List<ShinyEntry>> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("entries") ?: return emptyMap()
        val entries = mutableListOf<ShinyEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val species = obj.optString("species", "").trim()
            if (species.isEmpty()) continue

            val normalObj = obj.optJSONObject("normal") ?: continue
            val shinyObj = obj.optJSONObject("shiny") ?: continue

            val normal = SignatureRef(
                aHash = normalObj.optString("aHash", ""),
                dHash = normalObj.optString("dHash", ""),
                edge = readEdge(normalObj.optJSONArray("edge")),
                color = readColor(normalObj.opt("color"))
            )
            val shiny = SignatureRef(
                aHash = shinyObj.optString("aHash", ""),
                dHash = shinyObj.optString("dHash", ""),
                edge = readEdge(shinyObj.optJSONArray("edge")),
                color = readColor(shinyObj.opt("color"))
            )

            val entry = ShinyEntry(
                species = species,
                key = obj.optString("key", ""),
                normal = normal,
                shiny = shiny,
                formId = obj.optString("form", "").trim().takeIf { it.isNotEmpty() }
            )
            entries.add(entry)
        }
        return groupEntries(entries)
    }

    private fun readEdge(edgeArr: org.json.JSONArray?): FloatArray {
        val edge = FloatArray(EDGE_BINS)
        if (edgeArr != null) {
            for (j in 0 until min(edgeArr.length(), EDGE_BINS)) {
                edge[j] = edgeArr.optDouble(j, 0.0).toFloat()
            }
        }
        return edge
    }

    private fun readColor(colorObj: Any?): FloatArray {
        val color = FloatArray(12)
        val arr = when (colorObj) {
            is org.json.JSONArray -> colorObj
            is org.json.JSONObject -> colorObj.optJSONArray("value")
            else -> null
        }
        if (arr != null) {
            for (j in 0 until min(arr.length(), color.size)) {
                color[j] = arr.optDouble(j, 0.0).toFloat()
            }
        }
        return color
    }

    private fun colorDistance(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty()) return 1f
        val len = min(a.size, b.size)
        var sum = 0f
        for (i in 0 until len) {
            sum += kotlin.math.abs(a[i] - b[i])
        }
        return (sum / 2f).coerceIn(0f, 1f)
    }

    private fun scoreWithBreakdown(signature: SpriteSignature.Signature, colorHist: FloatArray, ref: SignatureRef): ScoreParts {
        val ah = SpriteSignature.hammingHex(signature.aHash, ref.aHash) / 64f
        val dh = SpriteSignature.hammingHex(signature.dHash, ref.dHash) / 64f
        val ed = SpriteSignature.edgeDistance(signature.edge, ref.edge)
        val cd = colorDistance(colorHist, ref.color)
        val total = 0.60f * cd + 0.20f * dh + 0.15f * ah + 0.05f * ed
        return ScoreParts(
            total = total,
            color = cd,
            dh = dh,
            ah = ah,
            ed = ed
        )
    }

    private fun hueCentroid(hist: FloatArray): Float? {
        if (hist.isEmpty()) return null
        var x = 0.0
        var y = 0.0
        var total = 0.0
        val bins = hist.size
        val step = 360.0 / bins.toDouble()
        for (i in hist.indices) {
            val w = hist[i].toDouble()
            if (w <= 0.0) continue
            val angle = (i + 0.5) * step
            val rad = Math.toRadians(angle)
            x += Math.cos(rad) * w
            y += Math.sin(rad) * w
            total += w
        }
        if (total <= 0.0001) return null
        var deg = Math.toDegrees(Math.atan2(y, x)).toFloat()
        if (deg < 0f) deg += 360f
        return deg
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val diff = kotlin.math.abs(a - b)
        return kotlin.math.min(diff, 360f - diff)
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

    private fun selectBestScores(
        normalA: ScoreParts,
        shinyA: ScoreParts,
        normalB: ScoreParts,
        shinyB: ScoreParts
    ): Triple<ScoreParts, ScoreParts, String> {
        val gapA = kotlin.math.abs(normalA.total - shinyA.total)
        val gapB = kotlin.math.abs(normalB.total - shinyB.total)
        val minA = kotlin.math.min(normalA.total, shinyA.total)
        val minB = kotlin.math.min(normalB.total, shinyB.total)

        val chooseA = when {
            gapA > gapB + 0.02f -> true
            gapB > gapA + 0.02f -> false
            else -> minA <= minB
        }

        return if (chooseA) {
            Triple(normalA, shinyA, "primary")
        } else {
            Triple(normalB, shinyB, "alt")
        }
    }
}
