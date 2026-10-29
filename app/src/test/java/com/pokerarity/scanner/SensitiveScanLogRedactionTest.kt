package com.pokerarity.scanner

import android.graphics.Bitmap
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.pokerarity.scanner.data.repository.RarityManifestLoader
import com.pokerarity.scanner.util.ocr.ImagePreprocessor
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SensitiveScanLogRedactionTest {

    @Test
    fun unknownSpeciesWarningDoesNotLogSpeciesName() {
        val sensitiveSpecies = "PrivateSpeciesFixture"
        RarityManifestLoader.reset()
        ShadowLog.clear()

        try {
            RarityManifestLoader.getSpeciesRarity(sensitiveSpecies)

            val warnings = ShadowLog.getLogsForTag("RarityManifestLoader")
                .filter { it.type == Log.WARN }
            assertTrue("Expected the missing-species warning", warnings.isNotEmpty())
            assertFalse(warnings.any { it.msg.contains(sensitiveSpecies) })
        } finally {
            RarityManifestLoader.reset()
        }
    }

    @Test
    fun featureExtractionWarningDoesNotLogSpeciesName() {
        val sensitiveSpecies = "PrivateSpeciesFixture"
        val classifier = Phase2VariantClassifier(ApplicationProvider.getApplicationContext())
        val payloadField = Phase2VariantClassifier::class.java.getDeclaredField("payload")
            .apply { isAccessible = true }
        payloadField.set(
            classifier,
            Gson().fromJson(
                """{"image":{"size":0},"supportedSpecies":{"$sensitiveSpecies":["isShiny"]}}""",
                payloadField.type
            )
        )
        Phase2VariantClassifier::class.java.getDeclaredField("loaded")
            .apply { isAccessible = true }
            .get(classifier)
            .let { (it as AtomicBoolean).set(true) }

        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        ShadowLog.clear()
        try {
            classifier.classify(bitmap, sensitiveSpecies)
        } finally {
            bitmap.recycle()
        }

        val warnings = ShadowLog.getLogsForTag("Phase2VariantClassifier")
            .filter { it.type == Log.WARN }
        assertTrue("Expected the feature-extraction warning", warnings.isNotEmpty())
        assertFalse(warnings.any { it.msg.contains(sensitiveSpecies) })
    }

    @Test
    fun screenshotDerivedMeasurementsAreNotWrittenToLogcat() {
        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        ShadowLog.clear()
        try {
            ImagePreprocessor.getDominantColor(bitmap)
            ImagePreprocessor.detectArcLevel(bitmap)
        } finally {
            bitmap.recycle()
        }

        val messages = ShadowLog.getLogsForTag("ImagePreprocessor").map { it.msg }
        listOf("RGB(", "Arc Level:", "Fill ratio too low:").forEach { marker ->
            assertFalse("Screenshot-derived measurement leaked: $marker", messages.any { marker in it })
        }
    }

    @Test
    fun recognitionAndCaptureLogsDoNotInterpolateScanContent() {
        val mainRoot = listOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
        val forbiddenMarkers = mapOf(
            "com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt" to
                listOf("RGB(", "Arc Level:", "Fill ratio too low:"),
            "com/pokerarity/scanner/util/vision/CostumeSignatureStore.kt" to listOf(
                "Costume scores for \$species",
                "Costume decision for \$species"
            ),
            "com/pokerarity/scanner/util/vision/ShinySignatureStore.kt" to listOf(
                "Shiny scores for \$species",
                "fallback for \$species"
            ),
            "com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt" to
                listOf("\$currentSpecies"),
            "com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt" to listOf(
                "name=\${nameAuthority.evidenceToken}",
                "candy=\${candyAuthority.evidenceToken}"
            ),
            "com/pokerarity/scanner/service/ScreenCaptureService.kt" to
                listOf("\${file.name}"),
            "com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt" to
                listOf("uploadId=\${entity.uploadId}")
        )

        forbiddenMarkers.forEach { (relativePath, markers) ->
            val source = File(mainRoot, relativePath).readText()
            markers.forEach { marker ->
                assertFalse("Scan-derived log marker remains in $relativePath: $marker", marker in source)
            }
        }
    }
}
