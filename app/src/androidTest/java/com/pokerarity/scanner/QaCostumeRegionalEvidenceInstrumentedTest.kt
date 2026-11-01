package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.util.vision.ColorAnalyzer
import com.pokerarity.scanner.util.vision.CostumeSignatureStore
import com.pokerarity.scanner.util.vision.PerceptualHash
import com.pokerarity.scanner.util.vision.QaVariantDiagnosticWriter
import com.pokerarity.scanner.util.vision.SpriteColorSignature
import com.pokerarity.scanner.util.vision.SpriteSignature
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import com.pokerarity.scanner.util.vision.VariantPrototypeStore
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
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class QaCostumeRegionalEvidenceInstrumentedTest {

    @Test
    fun comparesRegionalEvidenceAcrossStrictControlsAndSavedCottoneeFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtureContext = instrumentation.context
        val appContext = instrumentation.targetContext
        val manifestBytes = fixtureContext.assets.open(CASE_MANIFEST).use { it.readBytes() }
        val manifest = JSONArray(String(manifestBytes, Charsets.UTF_8))
        val sources = buildStrictSources(manifest)
        val savedFrame = File(
            requireNotNull(appContext.getExternalFilesDir(null)),
            "$INPUT_DIRECTORY/$COTTONEE_FRAME_FILE"
        )
        assertTrue("Saved Cottonee frame is required for the same-frame comparison", savedFrame.isFile)
        val savedFrameBytes = savedFrame.readBytes()
        assertEquals(COTTONEE_FRAME_SHA256, sha256(savedFrameBytes))
        sources += EvidenceSource(
            token = opaqueToken("crown-cottonee-cp595-hp98"),
            species = "Cottonee",
            expectedShiny = false,
            expectedCostume = true,
            bytes = savedFrameBytes,
            compareHistoricalCrop = true
        )

        val metadata = QaVariantDiagnosticWriter.metadataRevision(appContext)
        val detector = VisualFeatureDetector(appContext)
        val prototypeClassifier = VariantPrototypeClassifier(appContext)
        val reports = JSONArray()
        var strictPositiveCount = 0
        var strictNegativeCount = 0

        for (source in sources) {
            if (source.token != opaqueToken("crown-cottonee-cp595-hp98")) {
                if (source.expectedCostume) strictPositiveCount++ else strictNegativeCount++
            }
            val bitmap = decode(source.bytes)
            try {
                val analysis = ColorAnalyzer.downscaleForAnalysis(bitmap)
                try {
                    val fractions = if (source.compareHistoricalCrop) {
                        listOf(ColorAnalyzer.SPRITE_TOP_SCAN_FRACTION, HISTORICAL_TOP_SCAN_FRACTION)
                    } else {
                        listOf(ColorAnalyzer.SPRITE_TOP_SCAN_FRACTION)
                    }
                    val cropReports = JSONArray()
                    for (fraction in fractions) {
                        val bounds = ColorAnalyzer.getSpriteRegionAdaptive(analysis, fraction)
                        val masked = ColorAnalyzer.maskSpriteBackground(analysis, bounds)
                        try {
                            cropReports.put(
                                evaluateCrop(
                                    bitmap = masked,
                                    bounds = bounds,
                                    source = source,
                                    fullFrame = bitmap,
                                    topScanFraction = fraction,
                                    detector = detector,
                                    prototypeClassifier = prototypeClassifier,
                                    appContext = appContext
                                )
                            )
                        } finally {
                            masked.recycle()
                        }
                    }
                    reports.put(
                        JSONObject()
                            .put("caseToken", source.token)
                            .put("frameRef", "sha256:${sha256(source.bytes).take(FRAME_REF_HEX_LENGTH)}")
                            .put("imageWidth", bitmap.width)
                            .put("imageHeight", bitmap.height)
                            .put("expectedSpecies", source.species)
                            .put("expectedShiny", source.expectedShiny)
                            .put("expectedCostume", source.expectedCostume)
                            .put("cropComparisons", cropReports)
                    )
                } finally {
                    if (analysis !== bitmap) analysis.recycle()
                }
            } finally {
                bitmap.recycle()
            }
        }

        assertEquals(STRICT_POSITIVE_COUNT, strictPositiveCount)
        assertEquals(STRICT_NEGATIVE_COUNT, strictNegativeCount)
        val output = JSONObject()
            .put("schemaVersion", 1)
            .put("captureType", "offline_fixture_and_saved_frame_replay")
            .put("sourceSha", BuildConfig.QA_SOURCE_SHA)
            .put("applicationId", appContext.packageName)
            .put("metadataRevision", JSONObject()
                .put("source", metadata.source)
                .put("revisionSha256", metadata.revisionSha256)
                .put("assets", JSONArray().apply {
                    metadata.assets.forEach { asset ->
                        put(JSONObject().put("id", asset.id).put("sha256", asset.sha256))
                    }
                }))
            .put("strictControlCount", strictPositiveCount + strictNegativeCount)
            .put("strictCostumePositiveCount", strictPositiveCount)
            .put("strictCostumeNegativeCount", strictNegativeCount)
            .put("experiments", reports)
        val report = output.toString(2)
        assertTrue("Regional evidence report exceeded the local size bound", report.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES)
        assertFalse("Report must not contain OCR text", report.contains("rawOcr", ignoreCase = true))
        assertFalse("Report must not contain local paths", report.contains("C:\\", ignoreCase = true))

        val outputDirectory = File(appContext.filesDir, OUTPUT_DIRECTORY)
        assertTrue(outputDirectory.isDirectory || outputDirectory.mkdirs())
        File(outputDirectory, OUTPUT_FILE).writeText(report)
    }

    private fun evaluateCrop(
        bitmap: Bitmap,
        bounds: android.graphics.Rect,
        source: EvidenceSource,
        fullFrame: Bitmap,
        topScanFraction: Float,
        detector: VisualFeatureDetector,
        prototypeClassifier: VariantPrototypeClassifier,
        appContext: android.content.Context
    ): JSONObject {
        assertTrue(CostumeSignatureStore.hasSpecies(appContext, source.species))
        val signature = SpriteSignature.computeFromMaskedSprite(bitmap)
        val pHash = PerceptualHash.compute(bitmap)
        val headHeight = (bitmap.height * HEAD_HASH_FRACTION).toInt().coerceIn(1, bitmap.height)
        val head = if (headHeight == bitmap.height) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, headHeight)
        val headPHash = try {
            PerceptualHash.compute(head)
        } finally {
            if (head !== bitmap) head.recycle()
        }
        val details = CostumeSignatureStore.matchSignatureDetails(
            signature = signature,
            species = source.species,
            pHash = pHash,
            headPHash = headPHash
        )
        val candidateDistances = CostumeSignatureStore.diagnosticCandidateDistances(
            signature = signature,
            species = source.species,
            pHash = pHash,
            headPHash = headPHash,
            limit = MAX_CANDIDATES
        )
        val features = featuresFromMasked(bitmap)
        val prototypeEntries = VariantPrototypeStore.entriesForSpecies(appContext, setOf(source.species))
        val prototype = prototypeClassifier.classifyEntries(features, prototypeEntries, "regional_experiment")
        val baseHead = nearestHistogram(source, prototypeEntries, features.headHist, useHead = true, costume = false)
        val costumeHead = nearestHistogram(source, prototypeEntries, features.headHist, useHead = true, costume = true)
        val baseUpper = nearestHistogram(source, prototypeEntries, features.upperHist, useHead = false, costume = false)
        val costumeUpper = nearestHistogram(source, prototypeEntries, features.upperHist, useHead = false, costume = true)
        val visual = if (topScanFraction == ColorAnalyzer.SPRITE_TOP_SCAN_FRACTION) {
            detector.detect(fullFrame, source.species)
        } else {
            null
        }

        return JSONObject()
            .put("topScanFraction", topScanFraction)
            .put("crop", JSONObject()
                .put("left", bounds.left)
                .put("top", bounds.top)
                .put("right", bounds.right)
                .put("bottom", bounds.bottom)
                .put("width", bitmap.width)
                .put("height", bitmap.height))
            .put("costumeSignature", details?.let { item ->
                JSONObject()
                    .put("matched", item.matched)
                    .put("confidence", item.confidence)
                    .put("bestCostume", item.bestCostume)
                    .put("bestNormal", item.bestNormal)
                    .put("scoreGap", item.scoreGap)
                    .put("bestCostumeKey", item.bestCostumeKey)
                    .put("bestNormalKey", item.bestNormalKey)
                    .put("candidateCount", item.candidateCount)
                    .put("costumeCandidateCount", item.costumeCandidateCount)
                    .put("rejectionReason", item.rejectionReason)
            })
            .put("candidateDistances", JSONArray().apply {
                candidateDistances.forEach { item ->
                    put(JSONObject()
                        .put("key", item.key)
                        .put("costume", item.isCostume)
                        .put("weighted", item.weightedScore)
                        .put("dHash", item.dHashDistance)
                        .put("pHash", item.pHashDistance)
                        .put("headPHash", item.headPHashDistance))
                }
            })
            .put("prototype", prototype?.let { item ->
                JSONObject()
                    .put("selectedKey", item.spriteKey)
                    .put("selectedCostume", item.isCostumeLike)
                    .put("selectedShiny", item.isShiny)
                    .put("score", item.score)
                    .put("confidence", item.confidence)
                    .put("variantMargin", item.variantMargin)
                    .put("bestBaseKey", item.bestBaseSpriteKey)
                    .put("bestBaseScore", item.bestBaseScore)
                    .put("bestNonBaseKey", item.bestNonBaseSpriteKey)
                    .put("bestNonBaseScore", item.bestNonBaseScore)
                    .put("bestNonBaseCostume", item.bestNonBaseIsCostumeLike)
            })
            .put("regionalHistogram", JSONObject()
                .put("headFraction", HEAD_HISTOGRAM_FRACTION)
                .put("bestBaseHead", baseHead?.toJson())
                .put("bestCostumeHead", costumeHead?.toJson())
                .put("headAdvantage", advantage(baseHead, costumeHead))
                .put("upperFraction", UPPER_HISTOGRAM_FRACTION)
                .put("bestBaseUpper", baseUpper?.toJson())
                .put("bestCostumeUpper", costumeUpper?.toJson())
                .put("upperAdvantage", advantage(baseUpper, costumeUpper)))
            .put("localColorCue", measureHeadColorCue(bitmap))
            .put("currentDetector", visual?.let {
                JSONObject()
                    .put("costume", it.hasCostume)
                    .put("shiny", it.isShiny)
                    .put("overallConfidence", it.confidence)
            } ?: JSONObject.NULL)
    }

    private fun featuresFromMasked(bitmap: Bitmap): VariantPrototypeClassifier.ScreenshotFeatures {
        val headHeight = (bitmap.height * HEAD_HISTOGRAM_FRACTION).toInt().coerceIn(1, bitmap.height)
        val upperHeight = (bitmap.height * UPPER_HISTOGRAM_FRACTION).toInt().coerceIn(1, bitmap.height)
        val bodyTop = (bitmap.height * HEAD_HISTOGRAM_FRACTION).toInt().coerceIn(0, bitmap.height - 1)
        val head = if (headHeight == bitmap.height) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, headHeight)
        val upper = if (upperHeight == bitmap.height) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, upperHeight)
        val body = Bitmap.createBitmap(bitmap, 0, bodyTop, bitmap.width, bitmap.height - bodyTop)
        return try {
            VariantPrototypeClassifier.ScreenshotFeatures(
                signature = SpriteSignature.computeFromMaskedSprite(bitmap),
                fullHist = SpriteColorSignature.computeHueHistogram(bitmap),
                headHist = SpriteColorSignature.computeHueHistogram(head),
                upperHist = SpriteColorSignature.computeHueHistogram(upper),
                bodyHist = SpriteColorSignature.computeHueHistogram(body),
                foregroundRatio = SpriteColorSignature.computeForegroundRatio(bitmap),
                aspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
            )
        } finally {
            if (head !== bitmap) head.recycle()
            if (upper !== bitmap) upper.recycle()
            body.recycle()
        }
    }

    private data class HistogramMatch(val key: String, val distance: Float)

    private fun nearestHistogram(
        source: EvidenceSource,
        entries: List<VariantPrototypeStore.Entry>,
        observed: FloatArray,
        useHead: Boolean,
        costume: Boolean
    ): HistogramMatch? = entries.asSequence()
        .filter { it.isCostumeLike == costume && it.isShiny == source.expectedShiny }
        .map { entry ->
            val reference = if (useHead) entry.prototype.headHist else entry.prototype.upperHist
            HistogramMatch(entry.spriteKey, histogramDistance(observed, reference))
        }
        .minByOrNull(HistogramMatch::distance)

    private fun histogramDistance(observed: FloatArray, reference: List<Float>): Float {
        if (observed.isEmpty() || reference.isEmpty()) return 1f
        var sum = 0f
        for (index in observed.indices) {
            sum += abs(observed[index] - reference[index])
        }
        return (sum / 2f).coerceIn(0f, 1f)
    }

    private fun advantage(base: HistogramMatch?, costume: HistogramMatch?): Float? =
        if (base == null || costume == null) null else base.distance - costume.distance

    private fun HistogramMatch.toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("distance", distance)

    private fun measureHeadColorCue(bitmap: Bitmap): JSONObject {
        val width = bitmap.width
        val height = (bitmap.height * HEAD_COLOR_FRACTION).toInt().coerceIn(1, bitmap.height)
        val left = (width * HEAD_COLOR_LEFT).toInt().coerceIn(0, width)
        val right = (width * HEAD_COLOR_RIGHT).toInt().coerceIn(left + 1, width)
        val hsv = FloatArray(3)
        var visible = 0
        var saturated = 0
        var warm = 0
        var red = 0
        var purple = 0
        for (y in 0 until height step COLOR_SAMPLE_STEP) {
            for (x in left until right step COLOR_SAMPLE_STEP) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) < 12 && Color.green(pixel) < 12 && Color.blue(pixel) < 12) continue
                Color.colorToHSV(pixel, hsv)
                if (hsv[2] < COLOR_MIN_VALUE) continue
                visible++
                if (hsv[1] >= COLOR_MIN_SATURATION) {
                    saturated++
                    val hue = hsv[0].toInt()
                    if (hue <= WARM_HUE_END || hue >= WARM_HUE_START) warm++
                    if (hue <= RED_HUE_END || hue >= RED_HUE_START) red++
                    if (hue in PURPLE_HUE_RANGE) purple++
                }
            }
        }
        fun ratio(value: Int): Double = if (saturated == 0) 0.0 else value.toDouble() / saturated
        return JSONObject()
            .put("visiblePixels", visible)
            .put("saturatedPixels", saturated)
            .put("warmFractionOfSaturated", ratio(warm))
            .put("redFractionOfSaturated", ratio(red))
            .put("purpleFractionOfSaturated", ratio(purple))
    }

    private data class EvidenceSource(
        val token: String,
        val species: String,
        val expectedShiny: Boolean,
        val expectedCostume: Boolean,
        val bytes: ByteArray,
        val compareHistoricalCrop: Boolean
    )

    private fun buildStrictSources(manifest: JSONArray): MutableList<EvidenceSource> =
        (0 until manifest.length()).mapNotNull { index ->
            val item = manifest.getJSONObject(index)
            val expected = item.optJSONObject("expected") ?: return@mapNotNull null
            if (!item.optBoolean("strict") || !expected.has("costume") || expected.isNull("costume")) {
                return@mapNotNull null
            }
            val assetPath = item.optString("assetPath").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets
                .open(assetPath).use { it.readBytes() }
            EvidenceSource(
                token = opaqueToken(item.optString("id")),
                species = expected.getString("species"),
                expectedShiny = expected.optBoolean("shiny"),
                expectedCostume = expected.getBoolean("costume"),
                bytes = bytes,
                compareHistoricalCrop = item.optString("id") in HISTORICAL_CROP_CASES
            )
        }.toMutableList()

    private fun decode(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    ) ?: error("Could not decode an offline fixture frame")

    private fun opaqueToken(value: String): String = sha256(value.toByteArray(Charsets.UTF_8)).take(TOKEN_LENGTH)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val CASE_MANIFEST = "scan_regression_cases.json"
        const val INPUT_DIRECTORY = "costume-regional-experiment"
        const val COTTONEE_FRAME_FILE = "crown-full-frame.png"
        const val COTTONEE_FRAME_SHA256 = "d0ec9a3151f086496f8194e4e529117307b3bcd49901b0caed158e469d701226"
        const val OUTPUT_DIRECTORY = "variant_diagnostics"
        const val OUTPUT_FILE = "costume-regional-evidence.json"
        const val MAX_REPORT_BYTES = 64 * 1024
        const val MAX_CANDIDATES = 8
        const val FRAME_REF_HEX_LENGTH = 24
        const val TOKEN_LENGTH = 16
        const val STRICT_POSITIVE_COUNT = 3
        const val STRICT_NEGATIVE_COUNT = 13
        const val HISTORICAL_TOP_SCAN_FRACTION = 0.18f
        const val HEAD_HASH_FRACTION = 0.35f
        const val HEAD_HISTOGRAM_FRACTION = 0.30f
        const val UPPER_HISTOGRAM_FRACTION = 0.55f
        const val HEAD_COLOR_FRACTION = 0.30f
        const val HEAD_COLOR_LEFT = 0.15f
        const val HEAD_COLOR_RIGHT = 0.85f
        const val COLOR_SAMPLE_STEP = 2
        const val COLOR_MIN_VALUE = 0.20f
        const val COLOR_MIN_SATURATION = 0.25f
        const val WARM_HUE_END = 70
        const val WARM_HUE_START = 330
        const val RED_HUE_END = 20
        const val RED_HUE_START = 340
        val PURPLE_HUE_RANGE = 260..330
        val HISTORICAL_CROP_CASES = setOf(
            "live_variant_batch_20260318_slowpoke_regular",
            "live_variant_batch_20260318_slowpoke_costume"
        )
    }
}
