package com.pokerarity.scanner

import android.graphics.Bitmap
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.pokerarity.scanner.data.repository.RarityManifestLoader
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
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
}
