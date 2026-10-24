package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.vision.SpriteSignature.Signature
import com.pokerarity.scanner.util.vision.VariantPrototypeStore.Entry
import com.pokerarity.scanner.util.vision.VariantPrototypeStore.PrototypeFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class VariantPrototypeClassifierTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun globalAndSpeciesScopesShareFeaturesWithoutChangingEitherMatch() {
        val classifier = VariantPrototypeClassifier(context)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            val features = screenshotFeatures()
            val globalCandidates = listOf(
                entry("Cacnea", "cacnea_base", "base", shiny = false),
                entry("Pikipek", "pikipek_shiny", "base", shiny = true)
            )
            val speciesCandidates = listOf(
                globalCandidates.first(),
                entry("Cacnea", "cacnea_shiny", "base", shiny = true)
            )
            var extractionCalls = 0

            val combined = classifier.classifyCandidateScopes(
                bitmap = bitmap,
                globalCandidates = globalCandidates,
                speciesCandidates = speciesCandidates,
                featureExtractor = {
                    extractionCalls++
                    features
                }
            )

            assertEquals(1, extractionCalls)
            assertEquals(
                classifier.classifyEntries(features, globalCandidates, "global"),
                combined.globalMatch
            )
            assertEquals(
                classifier.classifyEntries(features, speciesCandidates, "species"),
                combined.speciesMatch
            )
            assertEquals("global", combined.globalMatch?.scope)
            assertEquals("species", combined.speciesMatch?.scope)
            assertNotNull(combined.globalMatch)
            assertNotNull(combined.speciesMatch)
        } finally {
            bitmap.recycle()
        }
    }

    private fun screenshotFeatures() = VariantPrototypeClassifier.ScreenshotFeatures(
        signature = Signature(
            aHash = "0000000000000000",
            dHash = "0000000000000000",
            edge = FloatArray(48) { 0f }
        ),
        fullHist = FloatArray(12) { if (it == 0) 1f else 0f },
        headHist = FloatArray(12) { if (it == 0) 1f else 0f },
        upperHist = FloatArray(12) { if (it == 0) 1f else 0f },
        bodyHist = FloatArray(12) { if (it == 0) 1f else 0f },
        foregroundRatio = 0.5f,
        aspectRatio = 1f
    )

    private fun entry(
        species: String,
        assetKey: String,
        variantType: String,
        shiny: Boolean
    ): Entry {
        val histogram = List(12) { index -> if (index == 0) 1f else 0f }
        return Entry(
            dex = 1,
            species = species,
            formId = "base",
            assetKey = assetKey,
            spriteKey = assetKey.removeSuffix("_shiny"),
            isShiny = shiny,
            isCostumeLike = false,
            variantType = variantType,
            filename = "$assetKey.png",
            sampleCount = 1,
            prototype = PrototypeFeatures(
                aHash = "0000000000000000",
                dHash = "0000000000000000",
                edge = List(48) { 0f },
                fullHist = histogram,
                headHist = histogram,
                upperHist = histogram,
                bodyHist = histogram,
                foregroundRatio = 0.5f,
                aspectRatio = 1f
            )
        )
    }
}
