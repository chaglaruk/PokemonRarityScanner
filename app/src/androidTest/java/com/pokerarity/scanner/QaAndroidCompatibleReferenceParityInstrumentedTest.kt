package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.CostumeSignatureStore
import com.pokerarity.scanner.util.vision.PerceptualHash
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import com.pokerarity.scanner.util.vision.QaPhase2Decision
import com.pokerarity.scanner.util.vision.ShinySignatureStore
import com.pokerarity.scanner.util.vision.SpriteColorSignature
import com.pokerarity.scanner.util.vision.SpriteSignature
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Local-only replay comparing explicit Android-compatible references on one preserved detector input. */
@RunWith(AndroidJUnit4::class)
class QaAndroidCompatibleReferenceParityInstrumentedTest {

    @Test
    fun finalVariantIdentityPreservesUnknownAndExplicitDemotionSemantics() {
        val recorded = JSONObject()
            .put("species", SPECIES)
            .put("formStatus", "KNOWN")
            .put("knownForm", "Normal Form")
            .put("formTruth", "UNKNOWN")
            .put("ambiguousForms", JSONArray())
        val noEvidence = finalVariantIdentityJson(VisualFeatures(isShiny = false), emptyList(), recorded)
        assertEquals("UNKNOWN", noEvidence.getString("shiny"))
        assertEquals("UNKNOWN", noEvidence.getString("costume"))
        val demoted = QaPhase2Decision(
            target = "isShiny",
            prediction = false,
            confidence = 0.90f,
            margin = -0.20f,
            positiveScore = 0.30f,
            negativeScore = 0.70f,
            positiveCount = 5,
            negativeCount = 5,
            source = "species",
            decisionCapable = true,
            passedThreshold = true,
            action = "demoted",
            reasonCode = "explicit_negative_evidence_gates_passed"
        )
        val explicitNegative = finalVariantIdentityJson(
            VisualFeatures(isShiny = false), listOf(demoted), recorded
        )
        assertEquals("FALSE", explicitNegative.getString("shiny"))
        val positive = finalVariantIdentityJson(VisualFeatures(isShiny = true, hasCostume = true), emptyList(), recorded)
        assertEquals("TRUE", positive.getString("shiny"))
        assertEquals("TRUE", positive.getString("costume"))
    }

    @Test
    fun compareCurrentAndCandidateReferencesOnTheSameSavedCottoneeFrame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = requireNotNull(context.getExternalFilesDir(null))
        val inputDir = File(root, INPUT_DIRECTORY)
        val manifest = JSONObject(File(inputDir, MANIFEST_FILE).readText())
        val scanId = manifest.getString("scanId")
        assertEquals(EXPECTED_SCAN_ID, scanId)

        val traceFile = File(inputDir, "variant-$scanId.json")
        val traceBytes = traceFile.readBytes()
        val trace = JSONObject(String(traceBytes, Charsets.UTF_8))
        assertEquals(scanId, trace.getString("scanId"))
        assertEquals(
            manifest.getJSONObject("selectedFrame").getString("ref"),
            trace.getJSONObject("selectedFrame").getString("ref")
        )
        val metadataRevision = manifest.getJSONObject("metadata").getString("revisionSha256")
        assertEquals(EXPECTED_METADATA_REVISION, metadataRevision)

        val candidateFile = File(root, CANDIDATE_INPUT_PATH)
        val candidateBytes = candidateFile.readBytes()
        val candidate = JSONObject(String(candidateBytes, Charsets.UTF_8))
        assertEquals("qa_only_explicit_reference_input_no_runtime_download", candidate.getString("scope"))
        assertEquals(BuildConfig.QA_SOURCE_SHA, candidate.getString("baseSourceSha"))

        val maskFile = File(inputDir, MASK_FILE)
        val sourceFile = File(inputDir, SOURCE_REGION_FILE)
        assertCapturedImageHash(manifest, MASK_FILE, maskFile)
        assertCapturedImageHash(manifest, SOURCE_REGION_FILE, sourceFile)
        val masked = decode(maskFile)
        val source = decode(sourceFile)
        val head = cropTop(masked, HEAD_FRACTION)
        val upper = cropTop(masked, UPPER_FRACTION)
        val maskedBody = cropBody(masked)
        val sourceBody = cropBody(source)

        try {
            val signature = SpriteSignature.computeFromMaskedSprite(masked)
            val pHash = PerceptualHash.compute(masked)
            val headPHash = PerceptualHash.compute(head)
            val maskedBodyHist = SpriteColorSignature.computeHueHistogram(maskedBody)
            val sourceBodyHist = SpriteColorSignature.computeHueHistogram(sourceBody)
            val fullHist = SpriteColorSignature.computeHueHistogram(masked)
            val upperHist = SpriteColorSignature.computeHueHistogram(upper)

            val shinyAssetBytes = readAsset(context, SHINY_ASSET)
            val masterAssetBytes = readAsset(context, MASTER_ASSET)
            val currentShiny = currentShinyEntry(shinyAssetBytes)
            val currentCostumes = currentCostumeReferences(masterAssetBytes)
            assertTrue("Current shiny reference should be loaded", ShinySignatureStore.hasSpecies(context, SPECIES))
            assertTrue("Current costume references should be loaded", CostumeSignatureStore.hasSpecies(context, SPECIES))
            assertEquals(4, currentCostumes.size)

            val candidateShiny = candidateShinyEntry(candidate.getJSONObject("shinyReference"))
            val candidateCostumes = candidateCostumeReferences(candidate.getJSONArray("costumeReferences"))
            assertEquals(currentCostumes.size, candidateCostumes.size)
            assertEquals(currentShiny.key, candidateShiny.key)
            val currentCostumeKeys = currentCostumes.map { it.key }.toSet()
            val candidateCostumeKeys = candidateCostumes.map { it.key }.toSet()
            assertEquals(currentCostumeKeys, candidateCostumeKeys)
            val sourceAssetHashes = candidate.getJSONObject("sourceAssetSha256")
            val sourceAssetKeys = buildSet {
                val keys = sourceAssetHashes.keys()
                while (keys.hasNext()) add(keys.next())
            }
            assertEquals(candidateCostumeKeys, sourceAssetKeys)

            val detector = VisualFeatureDetector(context)
            val colorEvidence = detector.shinyColorEvidenceForPreparedCrop(masked, source, SPECIES)

            val currentMemoryBefore = heapSnapshot()
            val currentStarted = SystemClock.elapsedRealtimeNanos()
            val currentShinyArm = scoreShinyArm(
                signature, maskedBodyHist, sourceBodyHist, fullHist, upperHist,
                colorEvidence, detector, null
            )
            val currentCostume = requireNotNull(
                CostumeSignatureStore.matchSignatureDetails(signature, SPECIES, pHash, headPHash)
            )
            val currentCostumeDistances = CostumeSignatureStore.diagnosticCandidateDistances(
                signature, SPECIES, pHash, headPHash, CANDIDATE_LIMIT
            )
            val liveVisual = trace.getJSONObject("visualDetector")
            assertEquals(liveVisual.getBoolean("shiny"), currentShinyArm.choice.result.first)
            assertEquals(
                liveVisual.getDouble("shinyConfidence").toFloat(),
                currentShinyArm.choice.result.second,
                SCORE_TOLERANCE
            )
            assertEquals(liveVisual.getBoolean("costume"), currentCostume.matched)
            val currentElapsedNanos = SystemClock.elapsedRealtimeNanos() - currentStarted
            val currentMemoryAfter = heapSnapshot()

            val explicitCurrentPrimary = ShinySignatureStore.matchSignatureDetailsWithReference(
                signature, maskedBodyHist, sourceBodyHist, currentShiny, STRICT_SHINY
            )
            assertShinyEquivalent(currentShinyArm.primary, explicitCurrentPrimary)
            val explicitCurrentCostume = CostumeSignatureStore.matchSignatureDetailsWithReferences(
                signature, SPECIES, currentCostumes, pHash, headPHash
            )
            assertNotNull(explicitCurrentCostume)
            assertCostumeEquivalent(currentCostume, explicitCurrentCostume!!)
            val explicitCurrentDistances = CostumeSignatureStore.diagnosticCandidateDistancesWithReferences(
                signature, SPECIES, currentCostumes, pHash, headPHash, CANDIDATE_LIMIT
            )
            assertCandidateDistancesEquivalent(currentCostumeDistances, explicitCurrentDistances)

            val candidateMemoryBefore = heapSnapshot()
            val candidateStarted = SystemClock.elapsedRealtimeNanos()
            val candidateShinyArm = scoreShinyArm(
                signature, maskedBodyHist, sourceBodyHist, fullHist, upperHist,
                colorEvidence, detector, candidateShiny
            )
            val candidateCostume = requireNotNull(
                CostumeSignatureStore.matchSignatureDetailsWithReferences(
                    signature, SPECIES, candidateCostumes, pHash, headPHash
                )
            )
            val candidateCostumeDistances = CostumeSignatureStore.diagnosticCandidateDistancesWithReferences(
                signature, SPECIES, candidateCostumes, pHash, headPHash, CANDIDATE_LIMIT
            )
            val candidateElapsedNanos = SystemClock.elapsedRealtimeNanos() - candidateStarted
            val candidateMemoryAfter = heapSnapshot()

            val currentAfterCandidate = ShinySignatureStore.matchSignatureDetails(
                signature, maskedBodyHist, sourceBodyHist, SPECIES, STRICT_SHINY
            )
            assertShinyEquivalent(currentShinyArm.primary, currentAfterCandidate)
            val currentCostumeAfterCandidate = CostumeSignatureStore.matchSignatureDetails(
                signature, SPECIES, pHash, headPHash
            )
            assertNotNull(currentCostumeAfterCandidate)
            assertCostumeEquivalent(currentCostume, currentCostumeAfterCandidate!!)

            val phase2Predictions = readRecordedPhase2Predictions(trace.getJSONArray("phase2Merge"))
            val currentFeatures = VisualFeatures(
                isShiny = currentShinyArm.choice.result.first,
                hasCostume = currentCostume.matched
            )
            val candidateFeatures = VisualFeatures(
                isShiny = candidateShinyArm.choice.result.first,
                hasCostume = candidateCostume.matched
            )
            val currentPhase2 = mergePhase2(currentFeatures, phase2Predictions)
            assertPhase2Equivalent(trace.getJSONArray("phase2Merge"), currentPhase2.second)
            val candidatePhase2 = mergePhase2(candidateFeatures, phase2Predictions)
            val recordedIdentity = trace.getJSONObject("finalIdentity")
            val currentFinalIdentity = finalVariantIdentityJson(
                currentPhase2.first, currentPhase2.second, recordedIdentity
            )
            assertFinalVariantIdentityEquivalent(recordedIdentity, currentFinalIdentity)
            val candidateFinalIdentity = finalVariantIdentityJson(
                candidatePhase2.first, candidatePhase2.second, recordedIdentity
            )

            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installedApkSha256 = sha256(File(context.applicationInfo.sourceDir))
            val report = JSONObject()
                .put("schemaVersion", 2)
                .put("scope", "offline_same_frame_android_production_scorers_and_phase2_merge")
                .put("privacy", JSONObject()
                    .put("localOnly", true)
                    .put("historyWritten", false)
                    .put("screenshotExported", false)
                    .put("ocrTextIncluded", false))
                .put("build", JSONObject()
                    .put("baseSourceSha", BuildConfig.QA_SOURCE_SHA)
                    .put("sourceFilesSha256", candidate.optString("qaSourceFilesSha256"))
                    .put("applicationId", context.packageName)
                    .put("versionCode", packageInfo.longVersionCode)
                    .put("versionName", packageInfo.versionName)
                    .put("installedApkSha256", installedApkSha256)
                    .put("captureSourceSha", manifest.getJSONObject("build").optString("sourceSha")))
                .put("metadataRevisionSha256", metadataRevision)
                .put("scanId", scanId)
                .put("selectedFrameRef", manifest.getJSONObject("selectedFrame").getString("ref"))
                .put("input", JSONObject()
                    .put("mask", imageRecord(maskFile, masked))
                    .put("analysisRegion", imageRecord(sourceFile, source)))
                .put("references", JSONObject()
                    .put("current", JSONObject()
                        .put("shinyAssetSha256", sha256(shinyAssetBytes))
                        .put("masterPokedexAssetSha256", sha256(masterAssetBytes))
                        .put("shinyReferenceKey", currentShiny.key)
                        .put("costumeReferenceCount", currentCostumes.size)
                        .put("source", "QA APK assets loaded through production stores"))
                    .put("candidate", JSONObject()
                        .put("candidateInputSha256", sha256(candidateBytes))
                        .put("sourceCandidateSha256", candidate.getString("candidateSourceJsonSha256"))
                        .put("sourceAssetSha256", sourceAssetHashes)
                        .put("shinyReferenceKey", candidateShiny.key)
                        .put("costumeReferenceCount", candidateCostumes.size)
                        .put("source", "explicit local QA input; cache bypassed")))
                .put("androidQueryFeatures", JSONObject()
                    .put("aHash", signature.aHash)
                    .put("dHash", signature.dHash)
                    .put("pHash", pHash)
                    .put("headPHash", headPHash)
                    .put("edgeHistogram", floatArrayJson(signature.edge))
                    .put("hueHistograms", JSONObject()
                        .put("maskedBody", floatArrayJson(maskedBodyHist))
                        .put("sourceBody", floatArrayJson(sourceBodyHist))
                        .put("maskedFull", floatArrayJson(fullHist))
                        .put("maskedUpper", floatArrayJson(upperHist))))
                .put("colorEvidence", colorEvidenceJson(colorEvidence))
                .put("current", JSONObject()
                    .put("shiny", shinyArmJson(currentShinyArm))
                    .put("costume", costumeArmJson(currentCostume, currentCostumeDistances))
                    .put("phase2", phase2ResultJson(currentPhase2.first, currentPhase2.second)))
                .put("candidate", JSONObject()
                    .put("shiny", shinyArmJson(candidateShinyArm))
                    .put("costume", costumeArmJson(candidateCostume, candidateCostumeDistances))
                    .put("phase2", phase2ResultJson(candidatePhase2.first, candidatePhase2.second)))
                .put("finalRecognitionIdentity", JSONObject()
                    .put("current", currentFinalIdentity)
                    .put("candidate", candidateFinalIdentity))
                .put("singlePassResources", JSONObject()
                    .put("currentElapsedMs", currentElapsedNanos / 1_000_000.0)
                    .put("candidateElapsedMs", candidateElapsedNanos / 1_000_000.0)
                    .put("candidateMinusCurrentMs", (candidateElapsedNanos - currentElapsedNanos) / 1_000_000.0)
                    .put("currentHeap", heapDeltaJson(currentMemoryBefore, currentMemoryAfter))
                    .put("candidateHeap", heapDeltaJson(candidateMemoryBefore, candidateMemoryAfter))
                    .put("currentReferenceAssetBytes", shinyAssetBytes.size + masterAssetBytes.size)
                    .put("candidateReferenceInputBytes", candidateBytes.size)
                    .put("note", "single same-frame pass; diagnostic only, not a benchmark"))
                .put("storedLiveScanContext", liveTraceContext(trace))
                .put("limits", JSONObject()
                    .put("independentPositiveControlAvailable", false)
                    .put("independentNegativeControlAvailable", false)
                    .put("fullCandidateIdentityReplayed", false)
                    .put("candidateFinalVariantIdentityReplayed", true)
                    .put(
                        "candidateFormDecisionSource",
                        "reused from the genuine saved final identity; reference-only scoring cannot alter form"
                    )
                    .put(
                        "candidateVariantDecisionSource",
                        "production shiny and costume scorers followed by production Phase 2 merge and truth mapping"
                    )
                    .put("phase2Predictions", "reused only from the genuine saved same-frame trace"))

            val outputDir = File(root, OUTPUT_DIRECTORY)
            check(outputDir.mkdirs() || outputDir.isDirectory)
            val outputFile = File(outputDir, OUTPUT_FILE)
            assertFalse("Refusing to replace prior QA evidence", outputFile.exists())
            outputFile.writeText(report.toString(2))
            assertTrue("Bounded report exceeded 64 KiB", outputFile.length() <= MAX_REPORT_BYTES)
        } finally {
            recycleIfDistinct(head, masked)
            recycleIfDistinct(upper, masked)
            recycleIfDistinct(maskedBody, masked)
            recycleIfDistinct(sourceBody, source)
            masked.recycle()
            source.recycle()
        }
    }

    private fun scoreShinyArm(
        signature: SpriteSignature.Signature,
        maskedBodyHist: FloatArray,
        sourceBodyHist: FloatArray,
        fullHist: FloatArray,
        upperHist: FloatArray,
        colorEvidence: VisualFeatureDetector.ShinyColorEvidence,
        detector: VisualFeatureDetector,
        explicitReference: ShinySignatureStore.ShinyEntry?
    ): ShinyArm {
        fun match(histogram: FloatArray, alternate: FloatArray?): ShinySignatureStore.MatchDetails =
            if (explicitReference == null) {
                ShinySignatureStore.matchSignatureDetails(
                    signature, histogram, alternate, SPECIES, STRICT_SHINY
                )
            } else {
                ShinySignatureStore.matchSignatureDetailsWithReference(
                    signature, histogram, alternate, explicitReference, STRICT_SHINY
                )
            }

        val primary = match(maskedBodyHist, sourceBodyHist)
        val full = match(fullHist, null)
        val upper = match(upperHist, null)
        val consensus = detector.chooseBestShinySignatureResult(
            Pair(primary.matched, primary.confidence),
            listOf(Pair(full.matched, full.confidence), Pair(upper.matched, upper.confidence))
        )
        val choice = detector.chooseShinyDecision(consensus, colorEvidence, SPECIES)
        return ShinyArm(primary, full, upper, consensus, choice)
    }

    private fun currentShinyEntry(bytes: ByteArray): ShinySignatureStore.ShinyEntry {
        val entries = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("entries")
        val item = (0 until entries.length())
            .map(entries::getJSONObject)
            .first { it.getString("species").equals(SPECIES, ignoreCase = true) }
        return ShinySignatureStore.ShinyEntry(
            species = item.getString("species"),
            key = item.getString("key"),
            normal = shinyReference(item.getJSONObject("normal")),
            shiny = shinyReference(item.getJSONObject("shiny"))
        )
    }

    private fun shinyReference(json: JSONObject): ShinySignatureStore.SignatureRef =
        ShinySignatureStore.SignatureRef(
            aHash = json.getString("aHash"),
            dHash = json.getString("dHash"),
            edge = floatArray(json.getJSONArray("edge")),
            color = colorArray(json.get("color"))
        )

    private fun candidateShinyEntry(json: JSONObject): ShinySignatureStore.ShinyEntry =
        ShinySignatureStore.ShinyEntry(
            species = json.getString("species"),
            key = json.getString("key"),
            normal = shinyReference(json.getJSONObject("normal")),
            shiny = shinyReference(json.getJSONObject("shiny"))
        )

    private fun currentCostumeReferences(bytes: ByteArray): List<CostumeSignatureStore.CostumeSignature> {
        val entries = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("entries")
        return (0 until entries.length()).map(entries::getJSONObject)
            .filter { it.getString("species").equals(SPECIES, ignoreCase = true) }
            .map { item ->
                val signature = item.getJSONObject("signature")
                CostumeSignatureStore.CostumeSignature(
                    species = item.getString("species"),
                    key = item.getString("spriteKey"),
                    isCostume = item.optBoolean("isCostumeLike", false),
                    aHash = signature.getString("aHash"),
                    dHash = signature.getString("dHash"),
                    pHash = signature.optString("pHash").ifBlank { null },
                    headPHash = signature.optString("headPHash").ifBlank { null },
                    edge = floatArray(signature.getJSONArray("edge"))
                )
            }
    }

    private fun candidateCostumeReferences(items: JSONArray): List<CostumeSignatureStore.CostumeSignature> =
        (0 until items.length()).map(items::getJSONObject).map { item ->
            CostumeSignatureStore.CostumeSignature(
                species = item.getString("species"),
                key = item.getString("key"),
                isCostume = item.getBoolean("isCostume"),
                aHash = item.getString("aHash"),
                dHash = item.getString("dHash"),
                pHash = item.optString("pHash").ifBlank { null },
                headPHash = item.optString("headPHash").ifBlank { null },
                edge = floatArray(item.getJSONArray("edge"))
            )
        }

    private fun readRecordedPhase2Predictions(items: JSONArray): List<Phase2VariantClassifier.Prediction> =
        (0 until items.length()).map(items::getJSONObject).map { item ->
            val target = item.getString("target")
            val source = item.getString("source")
            val positiveCount = item.optInt("positiveCount").takeUnless { item.isNull("positiveCount") }
            val negativeCount = item.optInt("negativeCount").takeUnless { item.isNull("negativeCount") }
            val decisionCapable = item.getBoolean("decisionCapable")
            val capability = Phase2VariantClassifier.TargetCapability(
                target = target,
                source = source,
                supported = decisionCapable,
                positiveCount = positiveCount,
                negativeCount = negativeCount,
                combinedCount = if (positiveCount != null && negativeCount != null) {
                    positiveCount + negativeCount
                } else {
                    null
                },
                reason = if (decisionCapable) {
                    Phase2VariantClassifier.TargetCapabilityReason.DECISION_CAPABLE
                } else {
                    Phase2VariantClassifier.TargetCapabilityReason.UNSUPPORTED
                },
                decisionCapable = decisionCapable
            )
            Phase2VariantClassifier.Prediction(
                target = target,
                predictedValue = item.getBoolean("prediction"),
                confidence = item.getDouble("confidence").toFloat(),
                margin = item.getDouble("margin").toFloat(),
                positiveScore = item.getDouble("positiveScore").toFloat(),
                negativeScore = item.getDouble("negativeScore").toFloat(),
                positiveCount = positiveCount,
                negativeCount = negativeCount,
                passedThreshold = item.getBoolean("passedThreshold"),
                source = source,
                capability = capability
            )
        }

    private fun mergePhase2(
        features: VisualFeatures,
        predictions: List<Phase2VariantClassifier.Prediction>
    ): Pair<VisualFeatures, List<QaPhase2Decision>> {
        var decisions = emptyList<QaPhase2Decision>()
        val merged = Phase2VariantFeatureMerger.mergePredictions(features, predictions) { decisions = it }
        return Pair(merged, decisions)
    }

    private fun assertPhase2Equivalent(expected: JSONArray, actual: List<QaPhase2Decision>) {
        assertEquals(expected.length(), actual.size)
        for (index in 0 until expected.length()) {
            val item = expected.getJSONObject(index)
            val decision = actual.first { it.target == item.getString("target") }
            assertEquals(item.getString("action"), decision.action)
            assertEquals(item.getString("reasonCode"), decision.reasonCode)
            assertEquals(item.getBoolean("prediction"), decision.prediction)
            assertEquals(item.getBoolean("decisionCapable"), decision.decisionCapable)
            assertEquals(item.getBoolean("passedThreshold"), decision.passedThreshold)
        }
    }

    private fun assertShinyEquivalent(
        expected: ShinySignatureStore.MatchDetails,
        actual: ShinySignatureStore.MatchDetails
    ) {
        assertEquals(expected.referenceAvailable, actual.referenceAvailable)
        assertEquals(expected.referenceKey, actual.referenceKey)
        assertEquals(expected.matched, actual.matched)
        assertFloat(expected.confidence, actual.confidence)
        assertNullableFloat(expected.normalScore, actual.normalScore)
        assertNullableFloat(expected.shinyScore, actual.shinyScore)
        assertNullableFloat(expected.normalColorScore, actual.normalColorScore)
        assertNullableFloat(expected.shinyColorScore, actual.shinyColorScore)
        assertNullableFloat(expected.scoreGap, actual.scoreGap)
        assertNullableFloat(expected.colorGap, actual.colorGap)
        assertEquals(expected.histogramSource, actual.histogramSource)
        assertEquals(expected.strictMode, actual.strictMode)
        assertEquals(expected.reasonCode, actual.reasonCode)
    }

    private fun assertCostumeEquivalent(
        expected: CostumeSignatureStore.MatchDetails,
        actual: CostumeSignatureStore.MatchDetails
    ) {
        assertEquals(expected.matched, actual.matched)
        assertFloat(expected.confidence, actual.confidence)
        assertFloat(expected.bestCostume, actual.bestCostume)
        assertFloat(expected.bestNormal, actual.bestNormal)
        assertFloat(expected.scoreGap, actual.scoreGap)
        assertEquals(expected.costumeCandidateCount, actual.costumeCandidateCount)
        assertEquals(expected.bestCostumeKey, actual.bestCostumeKey)
        assertEquals(expected.bestNormalKey, actual.bestNormalKey)
        assertEquals(expected.candidateCount, actual.candidateCount)
        assertEquals(expected.rejectionReason, actual.rejectionReason)
    }

    private fun assertCandidateDistancesEquivalent(
        expected: List<CostumeSignatureStore.CandidateDistance>,
        actual: List<CostumeSignatureStore.CandidateDistance>
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (left, right) ->
            assertEquals(left.key, right.key)
            assertEquals(left.isCostume, right.isCostume)
            assertFloat(left.weightedScore, right.weightedScore)
        }
    }

    private fun assertFloat(expected: Float, actual: Float) =
        assertEquals(expected.toBits(), actual.toBits())

    private fun assertNullableFloat(expected: Float?, actual: Float?) {
        assertEquals(expected == null, actual == null)
        if (expected != null && actual != null) assertFloat(expected, actual)
    }

    private fun shinyArmJson(arm: ShinyArm): JSONObject = JSONObject()
        .put("primary", JSONObject(Gson().toJson(arm.primary)))
        .put("alternates", JSONArray().put(JSONObject(Gson().toJson(arm.full)))
            .put(JSONObject(Gson().toJson(arm.upper))))
        .put("consensus", JSONObject(Gson().toJson(arm.consensus)))
        .put("visualFeatureDetector", JSONObject()
            .put("shiny", arm.choice.result.first)
            .put("confidence", arm.choice.result.second)
            .put("colorFallbackCalled", arm.choice.colorFallbackCalled)
            .put("colorFallbackReason", arm.choice.colorFallbackReason)
            .put("decisionPath", arm.choice.selectedPath))

    private fun costumeArmJson(
        details: CostumeSignatureStore.MatchDetails,
        candidates: List<CostumeSignatureStore.CandidateDistance>
    ): JSONObject = JSONObject()
        .put("decision", JSONObject(Gson().toJson(details)))
        .put("rankedCandidates", JSONArray(Gson().toJson(candidates)))

    private fun phase2ResultJson(
        features: VisualFeatures,
        decisions: List<QaPhase2Decision>
    ): JSONObject = JSONObject()
        .put("merged", JSONObject()
            .put("isShiny", features.isShiny)
            .put("hasCostume", features.hasCostume)
            .put("hasSpecialForm", features.hasSpecialForm)
            .put("hasLocationCard", features.hasLocationCard))
        .put("decisions", JSONArray(Gson().toJson(decisions)))

    private fun finalVariantIdentityJson(
        features: VisualFeatures,
        decisions: List<QaPhase2Decision>,
        recordedIdentity: JSONObject
    ): JSONObject {
        val shinyDemoted = decisions.any { it.target == "isShiny" && it.action == "demoted" }
        val shiny = when {
            features.isShiny -> "TRUE"
            shinyDemoted -> "FALSE"
            else -> "UNKNOWN"
        }
        val costume = if (features.hasCostume) "TRUE" else "UNKNOWN"
        return JSONObject()
            .put("species", recordedIdentity.optString("species"))
            .put("shiny", shiny)
            .put("costume", costume)
            .put("formStatus", recordedIdentity.optString("formStatus"))
            .put("knownForm", recordedIdentity.optString("knownForm"))
            .put("formTruth", recordedIdentity.optString("formTruth"))
            .put("ambiguousForms", recordedIdentity.optJSONArray("ambiguousForms") ?: JSONArray())
            .put("shinySource", when {
                features.isShiny -> "merged_positive_evidence"
                shinyDemoted -> "phase2_explicit_negative_demotion"
                else -> "no_supported_positive_or_demotion"
            })
            .put("costumeSource", if (features.hasCostume) "merged_positive_evidence" else "no_supported_positive")
            .put("formSource", "recorded_live_identity_reused_for_reference_only_replay")
    }

    private fun assertFinalVariantIdentityEquivalent(expected: JSONObject, actual: JSONObject) {
        listOf("species", "shiny", "costume", "formStatus", "knownForm", "formTruth").forEach { field ->
            assertEquals("Current final identity field $field changed", expected.optString(field), actual.optString(field))
        }
        assertEquals(
            expected.optJSONArray("ambiguousForms")?.toString() ?: "[]",
            actual.optJSONArray("ambiguousForms")?.toString() ?: "[]"
        )
    }

    private fun colorEvidenceJson(value: VisualFeatureDetector.ShinyColorEvidence): JSONObject = JSONObject()
        .put("masked", evidencePair(value.masked))
        .put("raw", evidencePair(value.raw))
        .put("hue", evidencePair(value.hue))
        .put("histogramHue", evidencePair(value.histogramHue))

    private fun evidencePair(value: Pair<Boolean, Float>): JSONObject = JSONObject()
        .put("decision", value.first)
        .put("confidence", value.second)

    private fun liveTraceContext(trace: JSONObject): JSONObject {
        val full = trace.optJSONObject("fullVariantMatcher") ?: JSONObject()
        val identity = trace.optJSONObject("finalIdentity") ?: JSONObject()
        val prototypes = trace.optJSONArray("prototypeClassifier") ?: JSONArray()
        val prototypeSummary = JSONArray()
        for (index in 0 until minOf(prototypes.length(), MAX_CANDIDATES)) {
            val item = prototypes.getJSONObject(index)
            prototypeSummary.put(JSONObject()
                .put("role", item.optString("role"))
                .put("spriteKey", item.optString("spriteKey"))
                .put("variantType", item.optString("variantType"))
                .put("isCostumeLike", item.optBoolean("costume"))
                .put("score", item.optDouble("score")))
        }
        val merges = trace.optJSONArray("variantMerge") ?: JSONArray()
        val mergeSummary = JSONArray()
        for (index in 0 until minOf(merges.length(), MAX_CANDIDATES)) {
            val item = merges.getJSONObject(index)
            mergeSummary.put(JSONObject()
                .put("target", item.optString("target"))
                .put("action", item.optString("action"))
                .put("reasonCode", item.optString("reasonCode")))
        }
        return JSONObject()
            .put("liveVisualShiny", trace.optJSONObject("visualDetector")?.optBoolean("shiny"))
            .put("liveVisualCostume", trace.optJSONObject("visualDetector")?.optBoolean("costume"))
            .put("fullVariantMatcher", JSONObject()
                .put("selectedClass", full.optString("selectedClass"))
                .put("selectedSpriteKey", full.optString("selectedSpriteKey"))
                .put("selectionStatus", full.optString("selectionStatus"))
                .put("costume", full.optBoolean("costume"))
                .put("shiny", full.optBoolean("shiny"))
                .put("form", full.optBoolean("form"))
                .put("decisionConfidence", full.optDouble("decisionConfidence")))
            .put("prototypeSummary", prototypeSummary)
            .put("variantMerge", mergeSummary)
            .put("finalIdentity", JSONObject()
                .put("shiny", identity.optString("shiny"))
                .put("costume", identity.optString("costume"))
                .put("formStatus", identity.optString("formStatus"))
                .put("formTruth", identity.optString("formTruth")))
    }

    private fun imageRecord(file: File, bitmap: Bitmap): JSONObject = JSONObject()
        .put("sha256", sha256(file.readBytes()))
        .put("bytes", file.length())
        .put("width", bitmap.width)
        .put("height", bitmap.height)

    private fun assertCapturedImageHash(manifest: JSONObject, name: String, file: File) {
        val images = manifest.getJSONArray("images")
        val expected = (0 until images.length())
            .map(images::getJSONObject)
            .firstOrNull { it.getString("fileName") == name }
        assertNotNull("Saved frame manifest is missing $name", expected)
        assertEquals(expected!!.getString("sha256"), sha256(file.readBytes()))
    }

    private fun readAsset(context: android.content.Context, path: String): ByteArray =
        context.assets.open(path).use { it.readBytes() }

    private fun decode(file: File): Bitmap = requireNotNull(
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    ) { "Could not decode a bounded saved detector input" }

    private fun cropTop(bitmap: Bitmap, fraction: Float): Bitmap {
        val height = (bitmap.height * fraction).toInt().coerceIn(1, bitmap.height)
        return if (height == bitmap.height) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, height)
    }

    private fun cropBody(bitmap: Bitmap): Bitmap {
        val top = (bitmap.height * BODY_CROP_FRACTION).toInt().coerceIn(0, bitmap.height - 1)
        return if (top == 0) bitmap else Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bitmap.height - top)
    }

    private fun recycleIfDistinct(bitmap: Bitmap, reference: Bitmap) {
        if (bitmap !== reference) bitmap.recycle()
    }

    private fun floatArray(json: JSONArray): FloatArray =
        FloatArray(json.length()) { index -> json.getDouble(index).toFloat() }

    private fun colorArray(value: Any): FloatArray {
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("value") ?: JSONArray()
            else -> JSONArray()
        }
        return floatArray(array)
    }

    private fun floatArrayJson(values: FloatArray): JSONArray = JSONArray().apply {
        values.forEach { put(it) }
    }

    private fun heapSnapshot(): JSONObject {
        val runtime = Runtime.getRuntime()
        return JSONObject()
            .put("managedHeapUsedBytes", runtime.totalMemory() - runtime.freeMemory())
            .put("nativeHeapAllocatedBytes", Debug.getNativeHeapAllocatedSize())
    }

    private fun heapDeltaJson(before: JSONObject, after: JSONObject): JSONObject = JSONObject()
        .put("managedHeapBeforeBytes", before.getLong("managedHeapUsedBytes"))
        .put("managedHeapAfterBytes", after.getLong("managedHeapUsedBytes"))
        .put("managedHeapDeltaBytes", after.getLong("managedHeapUsedBytes") - before.getLong("managedHeapUsedBytes"))
        .put("nativeHeapBeforeBytes", before.getLong("nativeHeapAllocatedBytes"))
        .put("nativeHeapAfterBytes", after.getLong("nativeHeapAllocatedBytes"))
        .put("nativeHeapDeltaBytes", after.getLong("nativeHeapAllocatedBytes") - before.getLong("nativeHeapAllocatedBytes"))

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class ShinyArm(
        val primary: ShinySignatureStore.MatchDetails,
        val full: ShinySignatureStore.MatchDetails,
        val upper: ShinySignatureStore.MatchDetails,
        val consensus: VisualFeatureDetector.SignatureConsensus,
        val choice: VisualFeatureDetector.ShinyChoiceDetails
    )

    private companion object {
        const val SPECIES = "Cottonee"
        const val STRICT_SHINY = false
        const val HEAD_FRACTION = 0.35f
        const val UPPER_FRACTION = 0.55f
        const val BODY_CROP_FRACTION = 0.30f
        const val CANDIDATE_LIMIT = 8
        const val MAX_CANDIDATES = 8
        const val MAX_REPORT_BYTES = 64L * 1024L
        const val SCORE_TOLERANCE = 0.00001f
        const val INPUT_DIRECTORY = "same-frame-ab"
        const val MANIFEST_FILE = "manifest.json"
        const val MASK_FILE = "masked-signature-sprite.png"
        const val SOURCE_REGION_FILE = "analysis-source-region.png"
        const val CANDIDATE_INPUT_PATH = "reference-parity-input/android-compatible-reference-candidate.json"
        const val OUTPUT_DIRECTORY = "reference-parity-output"
        const val OUTPUT_FILE = "android-compatible-reference-comparison.json"
        const val SHINY_ASSET = "data/shiny_signatures.json"
        const val MASTER_ASSET = "data/master_pokedex.json"
        const val EXPECTED_SCAN_ID = "b92ef22bcb4a405bbb74f6e5da507d2e"
        const val EXPECTED_METADATA_REVISION = "127df8f751688c20aa42618aadb238ad7c0c21f33d4768e6c1bdcd29dd4458d8"
    }
}
