package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.pokerarity.scanner.util.vision.CostumeSignatureStore
import com.pokerarity.scanner.util.vision.PerceptualHash
import com.pokerarity.scanner.util.vision.QaExactFrameReplay
import com.pokerarity.scanner.util.vision.QaShinyComponents
import com.pokerarity.scanner.util.vision.QaShinySignatureMatch
import com.pokerarity.scanner.util.vision.SpriteColorSignature
import com.pokerarity.scanner.util.vision.SpriteSignature
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import com.pokerarity.scanner.util.vision.VariantPrototypeStore
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class QaExactFrameObjectMaskInstrumentedTest {

    @Test
    @Suppress("LongMethod")
    fun replaySavedCottoneeFrameAcrossProductionMaskAndControlledAlternatives() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val referenceContext = instrumentation.context
        assertTrue(
            "Reference fixtures must stay inside the separate AndroidTest package",
            referenceContext.packageName != context.packageName
        )
        val inputDir = File(requireNotNull(context.getExternalFilesDir(null)), INPUT_DIRECTORY)
        require(inputDir.isDirectory) { "Offline exact-frame inputs are missing" }

        val manifest = JSONObject(File(inputDir, "manifest.json").readText())
        val scanId = manifest.getString("scanId")
        val trace = JSONObject(File(inputDir, "variant-$scanId.json").readText())
        assertEquals(scanId, trace.getString("scanId"))
        assertEquals(
            manifest.getJSONObject("selectedFrame").getString("ref"),
            trace.getJSONObject("selectedFrame").getString("ref")
        )
        val referenceAssets = verifyReferenceAssets(referenceContext, manifest)
        val sourceRegionFile = File(inputDir, "analysis-source-region.png")
        val capturedMaskFile = File(inputDir, "production-mask-capture.png")
        val sourceRegion = decode(sourceRegionFile)
        val capturedMask = decode(capturedMaskFile)
        val output = JSONObject()
        val variantReports = JSONArray()
        var aReplay: com.pokerarity.scanner.util.vision.QaExactFrameReplayResult? = null
        var aPrototype: VariantPrototypeClassifier.MatchResult? = null
        var aVisualShiny: PreparedShinyReplay? = null

        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            output.put("schemaVersion", 1)
            output.put("scanId", scanId)
            output.put("selectedFrame", manifest.optJSONObject("selectedFrame"))
            output.put("captureBuild", manifest.optJSONObject("build"))
            output.put(
                "diagnosticBuild",
                JSONObject()
                    .put("sourceSha", BuildConfig.QA_SOURCE_SHA)
                    .put("applicationId", context.packageName)
                    .put("versionCode", packageInfo.longVersionCode)
                    .put("versionName", packageInfo.versionName)
            )
            output.put("metadata", manifest.optJSONObject("metadata"))
            output.put("referenceAssets", referenceAssets)
            output.put("sourceRegion", imageRecord(sourceRegionFile, sourceRegion))
            output.put("capturedProductionMask", imageRecord(capturedMaskFile, capturedMask))
            val colorReferenceBytes = context.assets.open(COLOR_REFERENCE_ASSET).use { it.readBytes() }
            output.put(
                "shinyColorReferenceAsset",
                JSONObject()
                    .put("path", COLOR_REFERENCE_ASSET)
                    .put("sha256", sha256(colorReferenceBytes))
                    .put("sourceSha", BuildConfig.QA_SOURCE_SHA)
            )
            assertCapturedImageHash(manifest, "analysis-source-region.png", sourceRegionFile)
            assertCapturedImageHash(manifest, "masked-signature-sprite.png", capturedMaskFile)

            val classifier = VariantPrototypeClassifier(referenceContext)
            val colorDetector = VisualFeatureDetector(context)
            val candidates = VariantPrototypeStore.entriesForSpecies(referenceContext, setOf("Cottonee"))
            val variants = listOf(
                "A_production.png" to "A_current_production_mask",
                "B_conservative_roi.png" to "B_conservative_ui_background_exclusion",
                "C_manual_object_upper_bound.png" to "C_manual_silhouette_upper_bound"
            )

            for ((filename, label) in variants) {
                val file = File(inputDir, filename)
                val masked = decode(file)
                try {
                    if (filename == "A_production.png") assertBitmapsEqual(capturedMask, masked)
                    val replay = QaExactFrameReplay.evaluate(
                        context = referenceContext,
                        species = "Cottonee",
                        maskedSprite = masked,
                        analysisRegion = sourceRegion,
                        strictShiny = false
                    )
                    val visualShiny = replayPreparedShiny(
                        detector = colorDetector,
                        replay = replay,
                        maskedSprite = masked,
                        rawSprite = sourceRegion,
                        species = "Cottonee"
                    )
                    val features = prototypeFeatures(masked)
                    val prototype = classifier.classifyEntries(features, candidates, "species")
                    val prototypeCandidates = candidates.mapNotNull { candidate ->
                        classifier.classifyEntries(features, listOf(candidate), "species")
                    }.sortedBy { it.score }
                    val signature = SpriteSignature.computeFromMaskedSprite(masked)
                    val pHash = PerceptualHash.compute(masked)
                    val headHeight = (masked.height * 0.35f).toInt().coerceIn(1, masked.height)
                    val head = Bitmap.createBitmap(masked, 0, 0, masked.width, headHeight)
                    val headPHash = try {
                        PerceptualHash.compute(head)
                    } finally {
                        if (head !== masked) head.recycle()
                    }
                    val costumeDetails = CostumeSignatureStore.matchSignatureDetails(
                        signature,
                        "Cottonee",
                        pHash,
                        headPHash
                    )
                    val item = JSONObject()
                        .put("label", label)
                        .put("image", imageRecord(file, masked))
                        .put("exactFrameReplay", JSONObject(Gson().toJson(replay)))
                        .put("costumeDecision", costumeDetails?.let { JSONObject(Gson().toJson(it)) })
                        .put("shinySignatureConsensus", JSONObject(Gson().toJson(visualShiny.signatureConsensus)))
                        .put("visualShinyDecision", JSONObject(Gson().toJson(visualShiny.choice)))
                        .put("visualShinyEvidence", JSONObject(Gson().toJson(visualShiny.evidence)))
                        .put("prototypeClassifier", prototype?.let { JSONObject(Gson().toJson(it)) })
                        .put(
                            "prototypeCandidates",
                            JSONArray().apply {
                                prototypeCandidates.forEach { candidate ->
                                    put(
                                        JSONObject()
                                            .put("spriteKey", candidate.spriteKey)
                                            .put("score", candidate.score)
                                            .put("variantType", candidate.variantType)
                                            .put("isCostumeLike", candidate.isCostumeLike)
                                            .put("isShiny", candidate.isShiny)
                                    )
                                }
                            }
                        )
                    variantReports.put(item)
                    if (filename == "A_production.png") {
                        aReplay = replay
                        aPrototype = prototype
                        aVisualShiny = visualShiny
                    }
                } finally {
                    masked.recycle()
                }
            }

            output.put("variants", variantReports)
            val fidelity = try {
                compareProductionBaseline(manifest, trace, aReplay, aPrototype, aVisualShiny)
            } catch (failure: AssertionError) {
                BaselineFidelity(
                    passed = false,
                    shinyScoreTolerance = SCORE_TOLERANCE,
                    costumeComponentTolerance = SCORE_TOLERANCE,
                    hueTolerance = SCORE_TOLERANCE,
                    prototypeScoreDifference = null,
                    prototypeScoreTolerance = PROTOTYPE_SCORE_TOLERANCE,
                    failureReason = failure.message ?: "baseline_assertion_failed"
                )
            } catch (failure: Exception) {
                BaselineFidelity(
                    passed = false,
                    shinyScoreTolerance = SCORE_TOLERANCE,
                    costumeComponentTolerance = SCORE_TOLERANCE,
                    hueTolerance = SCORE_TOLERANCE,
                    prototypeScoreDifference = null,
                    prototypeScoreTolerance = PROTOTYPE_SCORE_TOLERANCE,
                    failureReason = failure.message ?: "baseline_comparison_failed"
                )
            }
            output.put("baselineFidelity", JSONObject(Gson().toJson(fidelity)))
            File(inputDir, OUTPUT_FILE).writeText(output.toString(2))

            assertTrue("Saved baseline replay must match the original Android diagnostic", fidelity.passed)
            assertEquals("costume", aPrototype?.variantType)
            assertTrue("The unique filename alias should restore costume metadata", aPrototype?.isCostumeLike == true)
            assertEquals("546_00_SPRING_2024", aPrototype?.spriteKey)
            assertEquals("546_00_SPRING_2024", aPrototype?.assetKey)
            assertEquals(false, aPrototype?.isShiny)
        } finally {
            sourceRegion.recycle()
            capturedMask.recycle()
        }
    }

    @Suppress("LongMethod")
    private fun compareProductionBaseline(
        manifest: JSONObject,
        trace: JSONObject,
        replay: com.pokerarity.scanner.util.vision.QaExactFrameReplayResult?,
        prototype: VariantPrototypeClassifier.MatchResult?,
        visualShiny: PreparedShinyReplay?
    ): BaselineFidelity {
        val expectedShiny = manifest.optJSONObject("replayShinyPrimary")
        assertNotNull("Android exact-frame replay is required", replay)
        val baseline = requireNotNull(replay)
        val actualShiny = baseline.shinyPrimary
        assertNotNull("Baseline shiny replay is required", expectedShiny)
        assertNotNull("Android replay must return a shiny result", actualShiny)
        expectedShiny!!
        actualShiny!!

        assertShinyBaseline(expectedShiny, actualShiny)
        val expectedAlternates = manifest.getJSONArray("replayShinyAlternates")
        assertEquals(expectedAlternates.length(), baseline.shinyAlternates.size)
        for (index in 0 until expectedAlternates.length()) {
            assertShinyBaseline(expectedAlternates.getJSONObject(index), baseline.shinyAlternates[index])
        }
        assertEquals(manifest.getString("replayCostumeDecisionReason"), baseline.costumeDecisionReason)

        val expectedCostume = manifest.getJSONArray("costumeReferenceDistances")
        val actualCostume = baseline.costumeReferenceDistances.associateBy { it.key }
        assertEquals(expectedCostume.length(), actualCostume.size)
        for (index in 0 until expectedCostume.length()) {
            val expected = expectedCostume.getJSONObject(index)
            val actual = actualCostume[expected.getString("key")]
            assertNotNull("Missing exact-frame costume candidate", actual)
            actual!!
            assertNear(expected.getDouble("weightedScore").toFloat(), actual.weightedScore)
            assertNear(expected.getDouble("aHashDistance").toFloat(), actual.aHashDistance)
            assertNear(expected.getDouble("dHashDistance").toFloat(), actual.dHashDistance)
            assertNear(expected.getDouble("edgeDistance").toFloat(), actual.edgeDistance)
            assertNear(expected.getDouble("pHashDistance").toFloat(), actual.pHashDistance)
            assertNear(expected.getDouble("headPHashDistance").toFloat(), actual.headPHashDistance)
        }

        val expectedHue = manifest.getJSONObject("replayHueEvidence")
        assertFloatListNear(expectedHue.getJSONArray("maskedBodyHistogram"), baseline.hueEvidence.maskedBodyHistogram)
        assertFloatListNear(expectedHue.getJSONArray("sourceBodyHistogram"), baseline.hueEvidence.sourceBodyHistogram)
        assertNear(
            expectedHue.getDouble("maskedBodyHueCentroid").toFloat(),
            baseline.hueEvidence.maskedBodyHueCentroid
        )
        assertNear(
            expectedHue.getDouble("sourceBodyHueCentroid").toFloat(),
            baseline.hueEvidence.sourceBodyHueCentroid
        )

        val expectedSpeciesPrototype = trace.getJSONArray("prototypeClassifier")
            .let { list -> (0 until list.length()).map { list.getJSONObject(it) } }
            .first { it.optString("role") == "species" }
        assertNotNull("Prototype baseline result is required", prototype)
        prototype!!
        val prototypeScoreDifference = kotlin.math.abs(
            expectedSpeciesPrototype.getDouble("score").toFloat() - prototype.score
        )
        assertTrue(
            "Prototype baseline score must stay within the recorded floating-point tolerance",
            prototypeScoreDifference <= PROTOTYPE_SCORE_TOLERANCE
        )
        assertNotNull("Visual shiny replay is required", visualShiny)
        assertVisualShinyBaseline(trace.getJSONObject("visualDetector"), requireNotNull(visualShiny))

        return BaselineFidelity(
            passed = true,
            shinyScoreTolerance = SCORE_TOLERANCE,
            costumeComponentTolerance = SCORE_TOLERANCE,
            hueTolerance = SCORE_TOLERANCE,
            prototypeScoreDifference = prototypeScoreDifference,
            prototypeScoreTolerance = PROTOTYPE_SCORE_TOLERANCE,
            failureReason = null,
            capturedPrototypeSpriteKey = expectedSpeciesPrototype.getString("spriteKey"),
            capturedPrototypeVariantType = expectedSpeciesPrototype.getString("variantType"),
            replayPrototypeSpriteKey = prototype.spriteKey,
            replayPrototypeAssetKey = prototype.assetKey,
            replayPrototypeVariantType = prototype.variantType,
            replayPrototypeIsCostumeLike = prototype.isCostumeLike
        )
    }

    private fun replayPreparedShiny(
        detector: VisualFeatureDetector,
        replay: com.pokerarity.scanner.util.vision.QaExactFrameReplayResult,
        maskedSprite: Bitmap,
        rawSprite: Bitmap,
        species: String
    ): PreparedShinyReplay {
        val evidence = detector.shinyColorEvidenceForPreparedCrop(maskedSprite, rawSprite, species)
        val primary = requireNotNull(replay.shinyPrimary)
        val consensus = detector.chooseBestShinySignatureResult(
            primaryResult = primary.matched to primary.confidence,
            extraResults = replay.shinyAlternates.map { it.matched to it.confidence }
        )
        return PreparedShinyReplay(
            evidence = evidence,
            signatureConsensus = consensus,
            choice = detector.chooseShinyDecision(consensus, evidence, species)
        )
    }

    private fun assertVisualShinyBaseline(expected: JSONObject, actual: PreparedShinyReplay) {
        assertEquals(expected.getBoolean("shiny"), actual.choice.result.first)
        assertNear(expected.getDouble("shinyConfidence").toFloat(), actual.choice.result.second)
        assertEquals(expected.getBoolean("colorFallbackCalled"), actual.choice.colorFallbackCalled)
        assertEquals(expected.getString("colorFallbackReason"), actual.choice.colorFallbackReason)
        assertEquals(expected.getString("shinyDecisionPath"), actual.choice.selectedPath)
        assertColorEvidence(expected.getJSONObject("maskedColor"), actual.evidence.masked)
        assertColorEvidence(expected.getJSONObject("rawColor"), actual.evidence.raw)
        assertColorEvidence(expected.getJSONObject("hue"), actual.evidence.hue)
        assertColorEvidence(expected.getJSONObject("histogramHue"), actual.evidence.histogramHue)
    }

    private fun assertColorEvidence(expected: JSONObject, actual: Pair<Boolean, Float>) {
        assertEquals(expected.getBoolean("decision"), actual.first)
        assertNear(expected.getDouble("confidence").toFloat(), actual.second)
    }

    private fun verifyReferenceAssets(
        referenceContext: android.content.Context,
        captureManifest: JSONObject
    ): JSONArray {
        val fixture = referenceContext.assets.open(REFERENCE_MANIFEST)
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        assertEquals(
            captureManifest.getJSONObject("build").getString("sourceSha"),
            fixture.getString("captureSourceSha")
        )
        val metadata = captureManifest.getJSONObject("metadata")
        assertEquals(metadata.getString("revisionSha256"), fixture.getString("metadataRevisionSha256"))
        val sourceHashes = metadata.getJSONArray("assets").let { assets ->
            (0 until assets.length()).associate { index ->
                val item = assets.getJSONObject(index)
                item.getString("id") to item.getString("sha256")
            }
        }
        val records = JSONArray()
        val entries = fixture.getJSONArray("assets")
        for (index in 0 until entries.length()) {
            val expected = entries.getJSONObject(index)
            val assetId = expected.getString("id")
            assertEquals(expected.getString("sourceApkAssetSha256"), sourceHashes[assetId])
            val bytes = referenceContext.assets.open(expected.getString("path")).use { it.readBytes() }
            val fixtureSha = sha256(bytes)
            assertEquals(expected.getString("fixtureSha256"), fixtureSha)
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            val rows = json.getJSONArray("entries")
            assertEquals(expected.getInt("entryCount"), rows.length())
            for (row in 0 until rows.length()) {
                assertEquals("Cottonee", rows.getJSONObject(row).getString("species"))
            }
            records.put(
                JSONObject()
                    .put("id", assetId)
                    .put("path", expected.getString("path"))
                    .put("sourceApkAssetSha256", expected.getString("sourceApkAssetSha256"))
                    .put("fixtureSha256", fixtureSha)
                    .put("entryCount", rows.length())
                    .put("species", "Cottonee")
            )
        }
        return records
    }

    private fun assertCapturedImageHash(manifest: JSONObject, fileName: String, file: File) {
        val images = manifest.getJSONArray("images")
        val expected = (0 until images.length())
            .map { images.getJSONObject(it) }
            .firstOrNull { it.getString("fileName") == fileName }
        assertNotNull("Captured-image hash is missing for $fileName", expected)
        assertEquals(expected!!.getString("sha256"), sha256(file.readBytes()))
    }

    private fun assertBitmapsEqual(expected: Bitmap, actual: Bitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        val expectedPixels = IntArray(expected.width * expected.height)
        val actualPixels = IntArray(actual.width * actual.height)
        expected.getPixels(expectedPixels, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(actualPixels, 0, actual.width, 0, 0, actual.width, actual.height)
        assertTrue(
            "A production mask must be pixel-identical to the captured mask",
            expectedPixels.contentEquals(actualPixels)
        )
    }

    private fun assertShinyBaseline(expected: JSONObject, actual: QaShinySignatureMatch) {
        assertEquals(expected.getBoolean("matched"), actual.matched)
        assertEquals(expected.getString("reasonCode"), actual.reasonCode)
        assertEquals(expected.getString("histogramSource"), actual.histogramSource)
        assertEquals(expected.getBoolean("strictMode"), actual.strictMode)
        assertNear(expected.getDouble("confidence").toFloat(), actual.confidence)
        assertNear(expected.getDouble("normalScore").toFloat(), actual.normalScore)
        assertNear(expected.getDouble("shinyScore").toFloat(), actual.shinyScore)
        assertNear(expected.getDouble("normalColorScore").toFloat(), actual.normalColorScore)
        assertNear(expected.getDouble("shinyColorScore").toFloat(), actual.shinyColorScore)
        assertNear(expected.getDouble("scoreGap").toFloat(), actual.scoreGap)
        assertNear(expected.getDouble("colorGap").toFloat(), actual.colorGap)
        assertComponentsNear(expected.getJSONObject("normalComponents"), actual.normalComponents)
        assertComponentsNear(expected.getJSONObject("shinyComponents"), actual.shinyComponents)
    }

    private fun assertComponentsNear(expected: JSONObject, actual: QaShinyComponents?) {
        assertNotNull("Shiny score component breakdown is required", actual)
        actual!!
        assertNear(expected.getDouble("weightedScore").toFloat(), actual.weightedScore)
        assertNear(expected.getDouble("colorDistance").toFloat(), actual.colorDistance)
        assertNear(expected.getDouble("dHashDistance").toFloat(), actual.dHashDistance)
        assertNear(expected.getDouble("aHashDistance").toFloat(), actual.aHashDistance)
        assertNear(expected.getDouble("edgeDistance").toFloat(), actual.edgeDistance)
    }

    private fun prototypeFeatures(masked: Bitmap): VariantPrototypeClassifier.ScreenshotFeatures {
        val signature = SpriteSignature.computeFromMaskedSprite(masked)
        val headHeight = (masked.height * 0.30f).toInt().coerceIn(1, masked.height)
        val upperHeight = (masked.height * 0.55f).toInt().coerceIn(1, masked.height)
        val bodyTop = (masked.height * 0.30f).toInt().coerceIn(0, masked.height.coerceAtLeast(1) - 1)
        val head = Bitmap.createBitmap(masked, 0, 0, masked.width, headHeight)
        val upper = Bitmap.createBitmap(masked, 0, 0, masked.width, upperHeight)
        val body = Bitmap.createBitmap(
            masked,
            0,
            bodyTop,
            masked.width,
            (masked.height - bodyTop).coerceAtLeast(1)
        )
        return try {
            VariantPrototypeClassifier.ScreenshotFeatures(
                signature = signature,
                fullHist = SpriteColorSignature.computeHueHistogram(masked),
                headHist = SpriteColorSignature.computeHueHistogram(head),
                upperHist = SpriteColorSignature.computeHueHistogram(upper),
                bodyHist = SpriteColorSignature.computeHueHistogram(body),
                foregroundRatio = SpriteColorSignature.computeForegroundRatio(masked),
                aspectRatio = masked.width.toFloat() / masked.height.coerceAtLeast(1).toFloat()
            )
        } finally {
            head.recycle()
            upper.recycle()
            body.recycle()
        }
    }

    private fun decode(file: File): Bitmap {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath, options)) {
            "Could not decode an offline experiment image"
        }
    }

    private fun imageRecord(file: File, bitmap: Bitmap): JSONObject {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return JSONObject()
            .put("fileName", file.name)
            .put("sha256", digest.joinToString("") { "%02x".format(it.toInt() and 0xff) })
            .put("byteSize", file.length())
            .put("width", bitmap.width)
            .put("height", bitmap.height)
    }

    private fun assertNear(expected: Float, actual: Float?) {
        assertNotNull("Expected production score is missing", actual)
        assertTrue(
            "Production replay score exceeded floating-point tolerance",
            kotlin.math.abs(expected - actual!!) <= SCORE_TOLERANCE
        )
    }

    private fun assertFloatListNear(expected: JSONArray, actual: List<Float>) {
        assertEquals(expected.length(), actual.size)
        for (index in 0 until expected.length()) assertNear(expected.getDouble(index).toFloat(), actual[index])
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class BaselineFidelity(
        val passed: Boolean,
        val shinyScoreTolerance: Float,
        val costumeComponentTolerance: Float,
        val hueTolerance: Float,
        val prototypeScoreDifference: Float?,
        val prototypeScoreTolerance: Float,
        val failureReason: String?,
        val capturedPrototypeSpriteKey: String? = null,
        val capturedPrototypeVariantType: String? = null,
        val replayPrototypeSpriteKey: String? = null,
        val replayPrototypeAssetKey: String? = null,
        val replayPrototypeVariantType: String? = null,
        val replayPrototypeIsCostumeLike: Boolean? = null
    )

    private data class PreparedShinyReplay(
        val evidence: VisualFeatureDetector.ShinyColorEvidence,
        val signatureConsensus: VisualFeatureDetector.SignatureConsensus,
        val choice: VisualFeatureDetector.ShinyChoiceDetails
    )

    private companion object {
        const val INPUT_DIRECTORY = "same-frame-ab"
        const val OUTPUT_FILE = "same-frame-ab-production.json"
        const val REFERENCE_MANIFEST = "data/qa_exact_frame_reference_manifest.json"
        const val COLOR_REFERENCE_ASSET = "data/pokemon_colors_generated.json"
        const val SCORE_TOLERANCE = 0.00001f
        const val PROTOTYPE_SCORE_TOLERANCE = 0.0005f
    }
}
