package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.CpCropOcrRecovery
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.ScreenAnchorName
import com.pokerarity.scanner.util.ocr.ScreenField
import com.pokerarity.scanner.util.ocr.ScreenGeometryBuilder
import com.pokerarity.scanner.util.ocr.ScreenType
import com.pokerarity.scanner.util.ocr.TextParser
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the CP-only retry with real ML Kit and repository-labeled visible-CP frames. */
@RunWith(AndroidJUnit4::class)
class CpCropOcrRecoveryInstrumentedTest {

    @Test
    fun productionCpReplayMatchesVisibleReferenceScreens() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val assetContext = instrumentation.context
        val manifest = JSONObject(assetContext.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() })
        val fixtures = manifest.getJSONArray("fixtures")
        assertEquals(
            "CP visibility must come from independent manual visual review",
            "manual_visual_review_of_each_development_fixture",
            manifest.getString("fieldVisibilitySource")
        )
        val visibleCpFixtures = (0 until fixtures.length()).map { fixtures.getJSONObject(it) }
            .filter { it.getJSONObject("fieldVisibility").getBoolean("cp") }
            .onEach {
                val truth = it.getJSONObject("truth")
                require(truth.getString("status") == "confirmed")
                require(truth.getString("source") == "user_confirmed_live_source_screen")
                require(it.getBoolean("excludedFromHoldout") && it.getBoolean("eligibleForDevelopmentMeasurement"))
            }
        // Use the manually reviewed field visibility label; a position name is not proof
        // that a numeric field was on-screen.
        assertEquals("expected five independently labeled visible-CP screens", 5, visibleCpFixtures.size)

        val processor = OCRProcessor(appContext)
        val geometryBuilder = ScreenGeometryBuilder()
        val failures = mutableListOf<String>()
        val observations = mutableListOf<String>()
        try {
            processor.ensureInitialized()
            visibleCpFixtures.forEach { fixture ->
                val bitmap = assetContext.assets.open(fixture.getString("relativePath"))
                    .use(BitmapFactory::decodeStream)
                    ?: throw AssertionError("fixture decode failed")
                try {
                    val expectedCp = fixture.getJSONObject("truth").getInt("cp")
                    val geometry = geometryBuilder.build(bitmap)
                    val result = processor.processImageWithDiagnostics(
                        FrameOcrRequest(
                            bitmap = bitmap,
                            includeSecondaryFields = true,
                            frameIndex = 0,
                            frameRole = "fast",
                            geometry = geometry
                        )
                    )
                    val crop = result.diagnostic.crops.firstOrNull {
                        it.field == ScreenField.CP.diagnosticName && it.source == "targeted_ocr"
                    }
                    val fieldStatus = result.diagnostic.structuredFields
                        .firstOrNull { it.field.equals("CP", ignoreCase = true) }?.status ?: "unknown"
                    val cropStatus = crop?.status ?: "not_attempted"
                    val cropReasons = crop?.reasons.orEmpty().joinToString(",").ifEmpty { "none" }
                    val correct = result.pokemon.cp == expectedCp
                    val observation = "${fixture.getString("id")}:correct=$correct,fieldStatus=$fieldStatus," +
                        "screenState=${result.diagnostic.screenState},cropStatus=$cropStatus,cropReasons=$cropReasons"
                    observations += observation
                    if (!correct) failures += observation
                } finally {
                    bitmap.recycle()
                }
            }
            Log.i("CpCropReplay", observations.joinToString(";"))
            assertTrue(
                "production CP replay across " + visibleCpFixtures.size + " visible-CP screens failed: " +
                    failures.joinToString("; ") + "; observations=" + observations.joinToString("; "),
                failures.isEmpty()
            )
        } finally {
            processor.release()
        }
    }

    @Test
    fun realMlKitCropRecoversCpWhenFullFrameCandidateWasMissed() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val assetContext = instrumentation.context
        val manifest = JSONObject(assetContext.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() })
        val fixtures = manifest.getJSONArray("fixtures")
        val referenceFixtures = (0 until fixtures.length()).map { fixtures.getJSONObject(it) }
            .filter { it.getString("position") == "reference" }
            .onEach {
                val truth = it.getJSONObject("truth")
                require(truth.getString("status") == "confirmed")
                require(truth.getString("source") == "user_confirmed_live_source_screen")
            }
        assertEquals("expected the five independently labeled reference frames", 5, referenceFixtures.size)

        val provider = MLKitOcrProvider(appContext)
        val parser = TextParser(appContext)
        val geometryBuilder = ScreenGeometryBuilder()
        var recoveredFrames = 0
        var skippedFrames = 0
        try {
            provider.warmUp()
            referenceFixtures.forEach { fixture ->
                val bitmap = assetContext.assets.open(fixture.getString("relativePath")).use(BitmapFactory::decodeStream)
                    ?: throw AssertionError("fixture decode failed")
                try {
                    val geometry = geometryBuilder.build(bitmap)
                    val cpHeaderAnchored = geometry.anchors.any { anchor ->
                        anchor.name == ScreenAnchorName.CpHeader && anchor.confidence >= MIN_CP_HEADER_ANCHOR_CONFIDENCE
                    }
                    val shouldRetry = when (geometry.classification.screenType) {
                        ScreenType.PokemonDetail -> true
                        ScreenType.PokemonDetailScrolled -> cpHeaderAnchored
                        else -> false
                    }
                    val missingCp = AnchoredScreenText.extract(
                        MLKitOcrProvider.Layout(emptyList(), emptyList()), parser, bitmap.width, bitmap.height
                    )
                    assertEquals(FieldReadStatus.MISSING_NOT_VISIBLE, missingCp.cpRead.status)
                    var cropOcrCalls = 0
                    val result = CpCropOcrRecovery.recoverMissingCp(bitmap, missingCp, geometry) { crop ->
                        cropOcrCalls++
                        provider.recognizeLayout(crop)
                    }

                    if (shouldRetry) {
                        assertNotNull(geometry.crop(ScreenField.CP)?.rect)
                        assertEquals(fixture.getString("id"), 1, cropOcrCalls)
                        val expectedCp = fixture.getJSONObject("truth").getInt("cp")
                        assertEquals(fixture.getString("id"), expectedCp, result.fields.cp)
                        assertEquals(FieldReadStatus.READ, result.fields.cpRead.status)
                        assertEquals("supplemented", result.diagnostic.status)
                        recoveredFrames++
                    } else {
                        assertEquals("scrolled and unclassified frames must not retry", 0, cropOcrCalls)
                        assertEquals("skipped", result.diagnostic.status)
                        skippedFrames++
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            assertTrue("expected real ML Kit coverage plus classifier-gated skips; recovered=$recoveredFrames, skipped=$skippedFrames",
                recoveredFrames > 0 && recoveredFrames + skippedFrames == referenceFixtures.size)
            assertTrue("only reference frames with confirmed CP truth are included", referenceFixtures.all {
                it.getJSONObject("truth").has("cp")
            })
        } finally {
            provider.close()
        }
    }

    private companion object {
        const val MANIFEST_ASSET = "scan_fixtures/pr06_1080_development/fixture_manifest.json"
        const val MIN_CP_HEADER_ANCHOR_CONFIDENCE = 0.70f
    }
}
