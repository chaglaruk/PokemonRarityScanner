package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.pokerarity.scanner.util.vision.ColorAnalyzer
import com.pokerarity.scanner.util.vision.CostumeSignatureStore
import com.pokerarity.scanner.util.vision.PerceptualHash
import com.pokerarity.scanner.util.vision.SpriteSignature
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class QaCostumeReferenceNormalizationInstrumentedTest {

    @Test
    @Suppress("LongMethod")
    fun comparesStoredAndAlphaNormalizedReferencesOnTheSameSavedCottoneeFrame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val inputDir = File(requireNotNull(context.getExternalFilesDir(null)), INPUT_DIRECTORY)
        require(inputDir.isDirectory) { "Offline exact-frame inputs are missing" }
        val manifest = JSONObject(File(inputDir, "manifest.json").readText())
        val source = File(inputDir, REFERENCE_NORMALIZATIONS_FILE)
        val sourceBytes = source.readBytes()
        val payload = JSONObject(String(sourceBytes, Charsets.UTF_8))
        assertEquals(1, payload.getInt("schemaVersion"))
        assertEquals("PokeMiners/pogo_assets", payload.getString("sourceRepository"))
        assertTrue(payload.getJSONObject("rawFeaturesMatchStoredMasterPokedex").let { checks ->
            listOf("546_00", "546_00_shiny", "546_00_SPRING_2024", "546_00_SPRING_2024_shiny")
                .all { checks.optBoolean(it, false) }
        })
        val selectedFrame = manifest.getJSONObject("selectedFrame")
        val fullFrameSha = payload.getJSONObject("fullFrameCueInput").getString("sha256")
        assertEquals("available", selectedFrame.getString("status"))
        assertEquals("sha256:${fullFrameSha.take(FRAME_REF_HEX_LENGTH)}", selectedFrame.getString("ref"))
        val masterBytes = context.assets.open("data/master_pokedex.json").use { it.readBytes() }
        assertEquals(
            findAssetSha256(manifest.getJSONObject("metadata").getJSONArray("assets"), MASTER_POKEDEX_ASSET_ID),
            sha256(masterBytes)
        )
        val storedMasterCandidates = loadStoredMasterCandidates(masterBytes)

        val output = JSONObject()
            .put("schemaVersion", 1)
            .put("scanId", manifest.getString("scanId"))
            .put("selectedFrame", manifest.optJSONObject("selectedFrame"))
            .put(
                "savedPixelRun",
                JSONObject()
                    .put("sourceSha", manifest.getJSONObject("build").getString("sourceSha"))
                    .put("metadataRevisionSha256", manifest.getJSONObject("metadata").getString("revisionSha256"))
                    .put("costumeDecisionReason", manifest.getString("replayCostumeDecisionReason"))
            )
            .put(
                "referenceInput",
                JSONObject()
                    .put("sourceRepository", payload.getString("sourceRepository"))
                    .put("sourceCommit", payload.getString("sourceCommit"))
                    .put("inputSha256", sha256(sourceBytes))
                    .put("bundledMasterPokedexSha256", sha256(masterBytes))
                    .put("rawFeaturesMatchStoredMasterPokedex", true)
            )
            .put(
                "sourceAssetFingerprints",
                JSONArray().apply {
                    val assets = payload.getJSONArray("sourceAssets")
                    for (index in 0 until assets.length()) {
                        val asset = assets.getJSONObject(index)
                        put(
                            JSONObject()
                                .put("key", asset.getString("key"))
                                .put("sha256", asset.getString("sha256"))
                        )
                    }
                }
            )
            .put(
                "diagnosticBuild",
                JSONObject()
                    .put("sourceSha", BuildConfig.QA_SOURCE_SHA)
                    .put("applicationId", context.packageName)
            )
        val reports = JSONArray()

        for ((fileName, label) in MASK_VARIANTS) {
            val file = File(inputDir, fileName)
            val bitmap = decode(file)
            try {
                val signature = SpriteSignature.computeFromMaskedSprite(bitmap)
                val pHash = PerceptualHash.compute(bitmap)
                val head = headCrop(bitmap)
                val headPHash = try {
                    PerceptualHash.compute(head)
                } finally {
                    if (head !== bitmap) head.recycle()
                }
                val item = JSONObject()
                    .put("maskVariant", label)
                    .put(
                        "queryImage",
                        JSONObject()
                            .put("sha256", sha256(file.readBytes()))
                            .put("width", bitmap.width)
                            .put("height", bitmap.height)
                    )
                val storedMasterScore = scoreReferenceSet(
                    normalization = "stored_master_pokedex",
                    signature = signature,
                    pHash = pHash,
                    headPHash = headPHash,
                    candidates = storedMasterCandidates
                )
                item.put(
                    "storedMasterPokedex",
                    storedMasterScore
                )
                if (label == PRODUCTION_MASK_LABEL) {
                    assertSavedProductionFrameParity(
                        manifest = manifest,
                        queryImage = item.getJSONObject("queryImage"),
                        storedMasterScore = storedMasterScore
                    )
                }
                for (normalization in FULL_NORMALIZATIONS) {
                    item.put(
                        normalization,
                        scoreReferenceSet(
                            normalization = normalization,
                            signature = signature,
                            pHash = pHash,
                            headPHash = headPHash,
                            candidates = loadCandidates(payload, normalization)
                        )
                    )
                }
                val fractions = payload.getJSONObject("normalizations")
                    .getJSONArray(LOCALIZED_NORMALIZATION)
                    .getJSONObject(0)
                    .getJSONObject("roiFractions")
                val localized = localizedContentCrop(
                    bitmap = bitmap,
                    xStartFraction = fractions.getJSONArray("x").getDouble(0).toFloat(),
                    xEndFraction = fractions.getJSONArray("x").getDouble(1).toFloat(),
                    yStartFraction = fractions.getJSONArray("y").getDouble(0).toFloat(),
                    yEndFraction = fractions.getJSONArray("y").getDouble(1).toFloat()
                )
                try {
                    val localSignature = SpriteSignature.computeFromMaskedSprite(localized)
                    val localPHash = PerceptualHash.compute(localized)
                    val localHead = headCrop(localized)
                    val localHeadPHash = try {
                        PerceptualHash.compute(localHead)
                    } finally {
                        if (localHead !== localized) localHead.recycle()
                    }
                    item.put("localizedUpperAccessory", JSONObject()
                        .put("roiFractions", fractions)
                        .put("queryWidth", localized.width)
                        .put("queryHeight", localized.height)
                        .put(
                            "scores",
                            scoreReferenceSet(
                                normalization = LOCALIZED_NORMALIZATION,
                                signature = localSignature,
                                pHash = localPHash,
                                headPHash = localHeadPHash,
                                candidates = loadCandidates(payload, LOCALIZED_NORMALIZATION)
                            )
                        ))
                } finally {
                    localized.recycle()
                }
                reports.put(item)
            } finally {
                bitmap.recycle()
            }
        }
        output.put("experiments", reports)
        output.put("upperHeadAccessoryCue", measureUpperHeadAccessoryCue(context, inputDir, payload))
        val report = output.toString(2)
        assertTrue(report.length <= MAX_REPORT_CHARS)
        assertFalse(report.contains("rawOcr"))
        assertFalse(report.contains("C:\\"))
        File(inputDir, OUTPUT_FILE).writeText(report)
    }

    private fun measureUpperHeadAccessoryCue(
        context: android.content.Context,
        inputDir: File,
        payload: JSONObject
    ): JSONObject {
        val expected = payload.getJSONObject("fullFrameCueInput")
        val input = File(inputDir, FULL_FRAME_CUE_INPUT)
        val inputBytes = input.readBytes()
        val inputHash = sha256(inputBytes)
        assertEquals(expected.getString("sha256"), inputHash)
        val fullFrame = decode(input)
        try {
            assertEquals(expected.getInt("width"), fullFrame.width)
            assertEquals(expected.getInt("height"), fullFrame.height)
            val detectorInput = ColorAnalyzer.downscaleForAnalysis(fullFrame)
            try {
                val counters = countUpperHeadCuePixels(detectorInput)
                val detectorResult = VisualFeatureDetector(context)
                    .hasUpperHeadRedAccessoryCue(detectorInput)
                assertEquals(counters.getBoolean("expectedResult"), detectorResult)
                return JSONObject()
                    .put("sourceFrameSha256", inputHash)
                    .put("sourceFrameWidth", fullFrame.width)
                    .put("sourceFrameHeight", fullFrame.height)
                    .put("analysisWidth", detectorInput.width)
                    .put("analysisHeight", detectorInput.height)
                    .put("detectorResult", detectorResult)
                    .put("pixelEvidence", counters)
            } finally {
                if (detectorInput !== fullFrame) detectorInput.recycle()
            }
        } finally {
            fullFrame.recycle()
        }
    }

    private fun assertSavedProductionFrameParity(
        manifest: JSONObject,
        queryImage: JSONObject,
        storedMasterScore: JSONObject
    ) {
        val images = manifest.getJSONArray("images")
        val savedMask = (0 until images.length())
            .map { images.getJSONObject(it) }
            .first { it.getString("fileName") == SAVED_PRODUCTION_MASK_FILE }
        assertEquals(savedMask.getString("sha256"), queryImage.getString("sha256"))
        assertEquals(savedMask.getInt("width"), queryImage.getInt("width"))
        assertEquals(savedMask.getInt("height"), queryImage.getInt("height"))

        val savedDistances = manifest.getJSONArray("costumeReferenceDistances")
        val currentDistances = storedMasterScore.getJSONArray("candidateScores")
        assertEquals(savedDistances.length(), currentDistances.length())
        for (savedIndex in 0 until savedDistances.length()) {
            val saved = savedDistances.getJSONObject(savedIndex)
            val current = (0 until currentDistances.length())
                .map { currentDistances.getJSONObject(it) }
                .first { it.getString("key") == saved.getString("key") }
            assertEquals(saved.getBoolean("costume"), current.getBoolean("isCostume"))
            for (field in SCORE_DISTANCE_FIELDS) {
                assertEquals(saved.getDouble(field), current.getDouble(field), SCORE_PARITY_TOLERANCE)
            }
        }
        assertEquals(
            manifest.getString("replayCostumeDecisionReason"),
            storedMasterScore.getJSONObject("decision").getString("rejectionReason")
        )
    }

    private fun findAssetSha256(assets: JSONArray, assetId: String): String =
        (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .first { it.getString("id") == assetId }
            .getString("sha256")

    private fun countUpperHeadCuePixels(bitmap: Bitmap): JSONObject {
        val left = (bitmap.width * 0.28f).toInt().coerceIn(0, bitmap.width)
        val top = (bitmap.height * 0.24f).toInt().coerceIn(0, bitmap.height)
        val right = (bitmap.width * 0.60f).toInt().coerceIn(0, bitmap.width)
        val bottom = (bitmap.height * 0.37f).toInt().coerceIn(0, bitmap.height)
        val hsv = FloatArray(3)
        var total = 0
        var red = 0
        var dark = 0
        for (y in top until bottom step CUE_SAMPLE_STEP) {
            for (x in left until right step CUE_SAMPLE_STEP) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                if (hsv[2] >= CUE_MIN_VISIBLE_VALUE) {
                    total++
                    val hue = hsv[0].toInt()
                    if ((hue >= CUE_RED_HUE_START || hue <= CUE_RED_HUE_END) &&
                        hsv[1] >= CUE_MIN_RED_SATURATION && hsv[2] >= CUE_MIN_RED_VALUE
                    ) {
                        red++
                    }
                    if (hsv[2] <= CUE_MAX_DARK_VALUE && hsv[1] >= CUE_MIN_DARK_SATURATION) {
                        dark++
                    }
                }
            }
        }
        val redRatio = if (total == 0) 0f else red.toFloat() / total
        val darkRatio = if (total == 0) 0f else dark.toFloat() / total
        val reason = when {
            total == 0 -> "no_visible_pixels"
            red < CUE_MIN_RED_PIXELS -> "red_pixel_count_below_minimum"
            redRatio < CUE_MIN_RED_RATIO -> "red_ratio_below_minimum"
            darkRatio < CUE_MIN_DARK_RATIO -> "dark_ratio_below_minimum"
            else -> "cue_thresholds_passed"
        }
        return JSONObject()
            .put("region", JSONObject().put("left", left).put("top", top).put("right", right).put("bottom", bottom))
            .put("visiblePixelCount", total)
            .put("redPixelCount", red)
            .put("darkPixelCount", dark)
            .put("redRatio", redRatio)
            .put("darkRatio", darkRatio)
            .put("reasonCode", reason)
            .put("expectedResult", reason == "cue_thresholds_passed")
    }

    private fun scoreReferenceSet(
        normalization: String,
        signature: SpriteSignature.Signature,
        pHash: String,
        headPHash: String,
        candidates: List<CostumeSignatureStore.CostumeSignature>
    ): JSONObject {
        assertEquals(4, candidates.size)
        assertEquals(2, candidates.count { it.isCostume })
        val query = CostumeSignatureStore.ReferenceSetQuery(
            signature = signature,
            species = SPECIES,
            candidates = candidates,
            pHash = pHash,
            headPHash = headPHash
        )
        val details = CostumeSignatureStore.matchSignatureDetailsWithReferences(query)
        val distances = CostumeSignatureStore.diagnosticCandidateDistancesWithReferences(query)
        return JSONObject()
            .put("normalization", normalization)
            .put("decision", details?.let { JSONObject(Gson().toJson(it)) })
            .put("candidateScores", JSONArray().apply {
                distances.forEach { put(JSONObject(Gson().toJson(it))) }
            })
    }

    private fun loadCandidates(
        payload: JSONObject,
        normalization: String
    ): List<CostumeSignatureStore.CostumeSignature> {
        val rows = payload.getJSONObject("normalizations").getJSONArray(normalization)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val features = row.getJSONObject("features")
            val edgeJson = features.getJSONArray("edge")
            require(edgeJson.length() == EDGE_BINS) { "Reference edge vector has an invalid size" }
            CostumeSignatureStore.CostumeSignature(
                species = SPECIES,
                key = row.getString("key"),
                isCostume = row.getBoolean("isCostume"),
                aHash = features.getString("aHash"),
                dHash = features.getString("dHash"),
                pHash = features.getString("pHash"),
                headPHash = features.getString("headPHash"),
                edge = FloatArray(EDGE_BINS) { edgeJson.getDouble(it).toFloat() }
            )
        }
    }

    private fun loadStoredMasterCandidates(masterBytes: ByteArray): List<CostumeSignatureStore.CostumeSignature> {
        val root = JSONObject(String(masterBytes, Charsets.UTF_8))
        val entries = root.getJSONArray("entries")
        val candidates = mutableListOf<CostumeSignatureStore.CostumeSignature>()
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val key = entry.optString("spriteKey")
            if (entry.optString("species") != SPECIES || key !in COTTONEE_REFERENCE_KEYS) continue
            val signature = entry.optJSONObject("signature") ?: continue
            val edgeJson = signature.optJSONArray("edge") ?: continue
            require(edgeJson.length() == EDGE_BINS) { "Stored master edge vector has an invalid size" }
            candidates += CostumeSignatureStore.CostumeSignature(
                species = SPECIES,
                key = key,
                isCostume = entry.optBoolean("isCostumeLike"),
                aHash = signature.getString("aHash"),
                dHash = signature.getString("dHash"),
                pHash = signature.optString("pHash").ifBlank { null },
                headPHash = signature.optString("headPHash").ifBlank { null },
                edge = FloatArray(EDGE_BINS) { edgeJson.getDouble(it).toFloat() }
            )
        }
        assertEquals(4, candidates.size)
        return candidates
    }

    private fun localizedContentCrop(
        bitmap: Bitmap,
        xStartFraction: Float,
        xEndFraction: Float,
        yStartFraction: Float,
        yEndFraction: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height step TRIM_STEP) {
            for (x in 0 until width step TRIM_STEP) {
                val pixel = pixels[y * width + x]
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                val luminance = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
                if (luminance > 18 || red + green + blue > 60) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        require(maxX >= minX && maxY >= minY) { "Manual object mask has no content" }
        val contentWidth = maxX - minX + 1
        val contentHeight = maxY - minY + 1
        val startX = (contentWidth * xStartFraction).roundToInt().coerceIn(0, contentWidth - 1)
        val endX = (contentWidth * xEndFraction).roundToInt().coerceIn(startX + 1, contentWidth)
        val startY = (contentHeight * yStartFraction).roundToInt().coerceIn(0, contentHeight - 1)
        val endY = (contentHeight * yEndFraction).roundToInt().coerceIn(startY + 1, contentHeight)
        return Bitmap.createBitmap(bitmap, minX + startX, minY + startY, endX - startX, endY - startY)
    }

    private fun headCrop(bitmap: Bitmap): Bitmap {
        val height = (bitmap.height * HEAD_CROP_FRACTION).toInt().coerceIn(1, bitmap.height)
        return if (height == bitmap.height) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, height)
    }

    private fun decode(file: File): Bitmap {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath, options)) {
            "Could not decode a bounded offline experiment image"
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val INPUT_DIRECTORY = "same-frame-ab"
        const val REFERENCE_NORMALIZATIONS_FILE = "reference-normalizations.json"
        const val OUTPUT_FILE = "reference-normalization-results.json"
        const val FULL_FRAME_CUE_INPUT = "crown-full-frame.png"
        const val SPECIES = "Cottonee"
        const val EDGE_BINS = 8
        const val TRIM_STEP = 2
        const val HEAD_CROP_FRACTION = 0.35f
        const val MAX_REPORT_CHARS = 32_000
        const val CUE_SAMPLE_STEP = 3
        const val CUE_RED_HUE_START = 345
        const val CUE_RED_HUE_END = 18
        const val CUE_MIN_VISIBLE_VALUE = 0.08f
        const val CUE_MIN_RED_SATURATION = 0.45f
        const val CUE_MIN_RED_VALUE = 0.30f
        const val CUE_MAX_DARK_VALUE = 0.35f
        const val CUE_MIN_DARK_SATURATION = 0.10f
        const val CUE_MIN_RED_PIXELS = 20
        const val CUE_MIN_RED_RATIO = 0.025f
        const val CUE_MIN_DARK_RATIO = 0.006f
        const val FRAME_REF_HEX_LENGTH = 24
        const val MASTER_POKEDEX_ASSET_ID = "master_pokedex"
        const val SAVED_PRODUCTION_MASK_FILE = "masked-signature-sprite.png"
        const val PRODUCTION_MASK_LABEL = "A_production_mask"
        const val SCORE_PARITY_TOLERANCE = 0.000001
        const val LOCALIZED_NORMALIZATION = "localized_upper_accessory"
        const val MANUAL_UPPER_BOUND_LABEL = "C_manual_object_upper_bound"
        val COTTONEE_REFERENCE_KEYS = setOf(
            "546_00",
            "546_00_shiny",
            "546_00_SPRING_2024",
            "546_00_SPRING_2024_shiny"
        )
        val FULL_NORMALIZATIONS = listOf("alpha_composited_black", "opaque_core_black")
        val SCORE_DISTANCE_FIELDS = listOf(
            "aHashDistance",
            "dHashDistance",
            "edgeDistance",
            "pHashDistance",
            "headPHashDistance",
            "weightedScore"
        )
        val MASK_VARIANTS = listOf(
            "A_production.png" to "A_production_mask",
            "B_conservative_roi.png" to "B_conservative_roi_crop",
            "C_manual_object_upper_bound.png" to MANUAL_UPPER_BOUND_LABEL
        )
    }
}
