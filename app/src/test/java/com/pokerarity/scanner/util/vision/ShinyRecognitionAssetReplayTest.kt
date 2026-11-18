package com.pokerarity.scanner.util.vision

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.util.ocr.RecognitionIdentityFactory
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
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
        val species = System.getenv("PRS_SHINY_SPECIES")?.takeIf(String::isNotBlank) ?: "Meowth"
        replayPrivateFrameComponents(context, File(framePath), species)
    }

    @Suppress("LongMethod")
    private fun replayPrivateFrameComponents(context: Context, frameFile: File, speciesName: String) {
        require(frameFile.isFile) { "PRS_SHINY_FRAME_PATH does not name a local file" }
        val frameSha256 = sha256(frameFile.readBytes())
        val bitmap = requireNotNull(BitmapFactory.decodeFile(frameFile.absolutePath)) {
            "Could not decode the local replay frame"
        }
        try {
            val detector = VisualFeatureDetector(context)
            val unscopedVisual = detector.detect(bitmap, speciesName)
            val phase2 = Phase2VariantClassifier(context).classify(bitmap, speciesName)
            val pokemon = PokemonData(
                cp = null,
                hp = null,
                maxHp = null,
                name = speciesName,
                realName = speciesName,
                candyName = speciesName,
                megaEnergy = null,
                weight = null,
                height = null,
                stardust = null,
                caughtDate = null,
                rawOcrText = "Name:$speciesName|NameHC:$speciesName"
            )
            val variantEngine = VariantDecisionEngine(context)
            val integrated = variantEngine.classify(bitmap, pokemon)
            val formKey = ShinyReferenceScope.resolvedFormSpriteKey(integrated.fullMatch, speciesName)
            val unresolvedReference = ShinySignatureStore.hasUnresolvedForms(context, speciesName, formKey)
            val scopedVisual = detector.detect(bitmap, speciesName, resolvedFormSpriteKey = formKey)
            val merged = variantEngine.mergeVisualFeaturesForLockedSpecies(
                visualFeatures = scopedVisual,
                lockedSpecies = speciesName,
                fullMatch = integrated.fullMatch,
                fallbackMatch = integrated.resolvedMatch ?: integrated.globalMatch,
                allowUnscopedShinyPromotion = !unresolvedReference
            )
            val phase2Merged = Phase2VariantFeatureMerger.merge(
                merged,
                phase2,
                allowShinyModelDecision = !unresolvedReference
            )
            val demoted = Phase2VariantFeatureMerger.shinyDemotionApplied(
                merged,
                phase2,
                allowShinyModelDecision = !unresolvedReference
            )
            val identity = RecognitionIdentityFactory.build(
                RecognitionIdentityFactory.Input(
                    speciesEvidence = SpeciesEvidence(
                        selectedCanonicalSpecies = speciesName,
                        authority = SpeciesAuthority.EXACT_CANONICAL,
                        profileStatus = SpeciesProfileStatus.COMPATIBLE,
                        reasonCodes = listOf(SpeciesEvidenceReason.EXACT),
                        observationsAgree = true,
                        authorityConflict = false
                    ),
                    scanAccepted = true,
                    lockedSpecies = speciesName,
                    classifierSpecies = integrated.pokemon.variantDecisionTrace?.classifierSpecies,
                    fullMatchWinnerSpecies = integrated.fullMatch?.winnerSpecies,
                    formCandidates = integrated.pokemon.speciesResolverTrace?.formCandidates.orEmpty(),
                    mergedFeatures = phase2Merged,
                    phase2ShinyDemoted = demoted,
                    sizeTag = null
                )
            )
            val shinyTruth = identity.shiny
            val phase2Shiny = phase2?.predictions?.filter { it.target == "isShiny" }
                ?.joinToString(";") {
                    "${it.source}:${it.predictedValue}:${it.confidence}:${it.margin}:" +
                        "${it.capability.positiveCount}/${it.capability.negativeCount}:${it.capability.decisionCapable}"
                }.orEmpty()
            println(
                "PRIVATE_SHINY_REPLAY " +
                    "frameSha256=$frameSha256 basePrSha=7b2af0c2470f0d2da2501e36abedfd3f54bc625b " +
                    "truth=$shinyTruth " +
                    "unscopedVisual=${unscopedVisual.isShiny}:${unscopedVisual.confidence} " +
                    "scopedVisual=${scopedVisual.isShiny}:${scopedVisual.confidence} " +
                    "species=$speciesName formKey=$formKey " +
                    "exactSignature=${ShinySignatureStore.hasSpecies(context, speciesName, formKey)} " +
                    "unresolvedReference=$unresolvedReference " +
                    "runtimeGlobal=${integrated.globalMatch?.spriteKey}:${integrated.globalMatch?.isShiny}:" +
                    "${integrated.globalMatch?.score}:${integrated.globalMatch?.confidence} " +
                    "runtimeSpecies=${integrated.speciesMatch?.spriteKey}:${integrated.speciesMatch?.isShiny}:" +
                    "${integrated.speciesMatch?.score}:${integrated.speciesMatch?.confidence} " +
                    "runtimeResolved=${integrated.resolvedMatch?.spriteKey}:${integrated.resolvedMatch?.isShiny}:" +
                    "${integrated.resolvedMatch?.score}:${integrated.resolvedMatch?.confidence} " +
                    "phase2=$phase2Shiny applied=${phase2?.appliedTargets.orEmpty().joinToString(",")} " +
                    "variantMerged=${merged.isShiny} finalMerged=${phase2Merged.isShiny} demoted=$demoted " +
                    "identity=${identity.shiny} " +
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
