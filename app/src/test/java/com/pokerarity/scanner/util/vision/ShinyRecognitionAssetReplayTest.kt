package com.pokerarity.scanner.util.vision

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RecognitionTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * Exercises the production recognition assets with a host context that does not start
 * PokeRarityApp (and therefore does not initialize SQLCipher, Room, or app services).
 * Set PRS_SHINY_FRAME_PATH locally to include the optional private-frame component replay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class,
    sdk = [28]
)
class ShinyRecognitionAssetReplayTest {

    @Test
    fun mainShinyAssetsLoadWithoutStartingDatabaseApplication() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val signatureBytes = context.assets.open("data/shiny_signatures.json").use { it.readBytes() }
        val sourceAsset = listOf(
            File("src/main/assets/data/shiny_signatures.json"),
            File("app/src/main/assets/data/shiny_signatures.json")
        ).firstOrNull(File::isFile)
            ?: error("Could not locate the source shiny-signatures asset")
        val sourceBytes = sourceAsset.readBytes()
        assertEquals(
            "Packaged and source signature assets must have identical bytes",
            sha256(sourceBytes),
            sha256(signatureBytes)
        )
        assertEquals(
            "336c412b63ec7a09a3110e7b9be0bac0ea588e6ead848638f3c2e9e147488ca2",
            sha256(normalizeLineEndings(signatureBytes))
        )
        val entries = String(signatureBytes, Charsets.UTF_8)
        val entryArray = org.json.JSONObject(entries).getJSONArray("entries")
        assertTrue((0 until entryArray.length()).any { index ->
            entryArray.getJSONObject(index).optString("species") == "Meowth"
        })
        assertTrue(ShinySignatureStore.hasSpecies(context, "Meowth"))
        assertTrue(ShinySignatureStore.hasSpecies(context, "Meowth", "052_00_shiny"))
        assertFalse(ShinySignatureStore.hasSpecies(context, "Meowth", "052_00_FALOLA_shiny"))

        val framePath = System.getenv("PRS_SHINY_FRAME_PATH")?.takeIf(String::isNotBlank) ?: return
        replayPrivateFrameComponents(context, File(framePath))
    }

    @Suppress("LongMethod")
    private fun replayPrivateFrameComponents(context: Context, frameFile: File) {
        require(frameFile.isFile) { "PRS_SHINY_FRAME_PATH does not name a local file" }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(frameFile.absolutePath)) {
            "Could not decode the local replay frame"
        }
        try {
            val detector = VisualFeatureDetector(context)
            val baselineVisual = detector.detect(bitmap, "Meowth")
            val prototypeClassifier = VariantPrototypeClassifier(context)
            val global = prototypeClassifier.classify(bitmap, setOf("Meowth"))
            val species = prototypeClassifier.classifyForSpecies(bitmap, "Meowth")
            val phase2 = Phase2VariantClassifier(context).classify(bitmap, "Meowth")
            val pokemon = PokemonData(
                cp = null,
                hp = null,
                maxHp = null,
                name = "Meowth",
                realName = "Meowth",
                candyName = "Meowth",
                megaEnergy = null,
                weight = null,
                height = null,
                stardust = null,
                caughtDate = null,
                rawOcrText = "Name:Meowth|NameHC:Meowth"
            )
            val variantEngine = VariantDecisionEngine(context)
            val integrated = variantEngine.classify(bitmap, pokemon)
            val formKey = ShinyReferenceScope.resolvedFormSpriteKey(integrated.fullMatch, "Meowth")
            val scopedVisual = detector.detect(bitmap, "Meowth", resolvedFormSpriteKey = formKey)
            val merged = variantEngine.mergeVisualFeaturesForLockedSpecies(
                visualFeatures = scopedVisual,
                lockedSpecies = "Meowth",
                fullMatch = integrated.fullMatch,
                fallbackMatch = integrated.resolvedMatch ?: integrated.globalMatch
            )
            val phase2Merged = Phase2VariantFeatureMerger.merge(merged, phase2)
            val demoted = Phase2VariantFeatureMerger.shinyDemotionApplied(merged, phase2)
            val shinyTruth = when {
                phase2Merged.isShiny -> RecognitionTruth.TRUE
                demoted -> RecognitionTruth.FALSE
                else -> RecognitionTruth.UNKNOWN
            }
            val phase2Shiny = phase2?.predictions?.filter { it.target == "isShiny" }
                ?.joinToString(";") {
                    "${it.source}:${it.predictedValue}:${it.confidence}:${it.margin}:" +
                        "${it.capability.positiveCount}/${it.capability.negativeCount}:${it.capability.decisionCapable}"
                }.orEmpty()
            println(
                "PRIVATE_SHINY_REPLAY truth=$shinyTruth " +
                    "baselineVisual=${baselineVisual.isShiny}:${baselineVisual.confidence} " +
                    "scopedVisual=${scopedVisual.isShiny}:${scopedVisual.confidence} " +
                    "formKey=$formKey exactSignature=${ShinySignatureStore.hasSpecies(context, "Meowth", formKey)} " +
                    "global=${global?.spriteKey}:${global?.isShiny}:${global?.score}:${global?.confidence} " +
                    "species=${species?.spriteKey}:${species?.isShiny}:${species?.score}:${species?.confidence} " +
                    "phase2=$phase2Shiny applied=${phase2?.appliedTargets.orEmpty().joinToString(",")} " +
                    "variantMerged=${merged.isShiny} finalMerged=${phase2Merged.isShiny} demoted=$demoted " +
                    "full=${integrated.fullMatch?.finalSpriteKey}:${integrated.fullMatch?.resolvedForm}:" +
                    "${integrated.fullMatch?.resolvedShiny}:${integrated.fullMatch?.shinyConfidence}:" +
                    "${integrated.fullMatch?.explanationMode}"
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun normalizeLineEndings(bytes: ByteArray): ByteArray =
        String(bytes, Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .toByteArray(Charsets.UTF_8)

}
