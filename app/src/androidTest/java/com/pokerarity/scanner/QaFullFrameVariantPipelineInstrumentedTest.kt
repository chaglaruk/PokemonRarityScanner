package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionIdentityCompat
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.AuthoritativeVariantDbLoader
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.FormCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.RecognitionIdentityFactory
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import com.pokerarity.scanner.util.vision.ColorAnalyzer
import com.pokerarity.scanner.util.vision.CostumeSignatureStore
import com.pokerarity.scanner.util.vision.FullVariantCandidateBuilder
import com.pokerarity.scanner.util.vision.FullVariantMatcher
import com.pokerarity.scanner.util.vision.PerceptualHash
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import com.pokerarity.scanner.util.vision.QaBuildIdentity
import com.pokerarity.scanner.util.vision.QaMergeDecision
import com.pokerarity.scanner.util.vision.QaPhase2Decision
import com.pokerarity.scanner.util.vision.ShinySignatureStore
import com.pokerarity.scanner.util.vision.SpriteColorSignature
import com.pokerarity.scanner.util.vision.SpriteSignature
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VariantStageDiagnosticRecorder
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

/** Offline replay of one preserved native screen through the production variant pipeline. */
@RunWith(AndroidJUnit4::class)
class QaFullFrameVariantPipelineInstrumentedTest {

    @Test
    @Suppress("LongMethod", "LongParameterList")
    fun replayNormalCrownCottoneeFullFrameWithCurrentAndCandidateReferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(requireNotNull(context.getExternalFilesDir(null)), INPUT_DIRECTORY)
        require(root.isDirectory) { "Private full-frame inputs are missing" }
        val manifestFile = File(root, MANIFEST_FILE)
        val traceFile = File(root, TRACE_FILE)
        val manifest = JSONObject(manifestFile.readText())
        val sourceTrace = JSONObject(traceFile.readText())
        val candidateBytes = File(root, CANDIDATE_FILE).readBytes()
        val candidateInput = JSONObject(String(candidateBytes, Charsets.UTF_8))
        assertEquals(EXPECTED_SCAN_ID, manifest.getString("scanId"))
        assertEquals(EXPECTED_SCAN_ID, sourceTrace.getString("scanId"))
        assertEquals("qa_only_explicit_reference_input_no_runtime_download", candidateInput.getString("scope"))
        assertEquals(CANDIDATE_BASE_SHA, candidateInput.getString("baseSourceSha"))

        val frameFile = File(root, FRAME_FILE)
        val frameHash = sha256(frameFile)
        assertEquals(EXPECTED_FRAME_SHA256, frameHash)
        assertManifestImage(manifest, FRAME_FILE, frameHash)
        val savedMaskFile = File(root, MASK_FILE)
        val savedSourceCropFile = File(root, SOURCE_CROP_FILE)
        val savedMaskHash = sha256(savedMaskFile)
        val savedSourceCropHash = sha256(savedSourceCropFile)
        assertManifestImage(manifest, MASK_FILE, savedMaskHash)
        assertManifestImage(manifest, SOURCE_CROP_FILE, savedSourceCropHash)
        val originalMetadata = manifest.getJSONObject("metadata").getString("revisionSha256")
        val metadata = QaVariantDiagnosticWriter.metadataRevision(context)
        assertEquals(EXPECTED_METADATA_REVISION, originalMetadata)
        assertEquals(originalMetadata, metadata.revisionSha256)
        val recordedFrameRef = manifest.getJSONObject("selectedFrame").getString("ref")
        assertEquals("sha256:" + frameHash.take(24), recordedFrameRef)
        val manifestHash = sha256(manifestFile)
        val traceHash = sha256(traceFile)

        val frame = decode(frameFile)
        val sourceCrop = prepareProductionCrop(frame, context)
        try {
            val savedMask = decode(savedMaskFile)
            val savedSourceCrop = decode(savedSourceCropFile)
            val savedMaskPixelParity: Boolean
            val savedSourcePixelParity: Boolean
            try {
                savedMaskPixelParity = sourceCrop.masked.sameAs(savedMask)
                savedSourcePixelParity = sourceCrop.source.sameAs(savedSourceCrop)
            } finally {
                savedMask.recycle()
                savedSourceCrop.recycle()
            }
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val build = QaBuildIdentity(
                applicationId = context.packageName,
                versionName = packageInfo.versionName.orEmpty(),
                versionCode = packageInfo.longVersionCode.toInt(),
                sourceSha = BuildConfig.QA_SOURCE_SHA
            )
            val recorder = VariantStageDiagnosticRecorder()
            recorder.recordSelectedFrameReference(
                QaVariantDiagnosticWriter.selectedFrameReference(frameFile.absolutePath)
            )
            val expectedIdentity = sourceTrace.optJSONObject("finalIdentity") ?: JSONObject()
            val formLabel = expectedIdentity.optString("knownForm").takeIf {
                expectedIdentity.optString("formStatus") == "KNOWN" && it.isNotBlank()
            }
            val formCandidates = formLabel?.let {
                listOf(
                    FormCandidateDiagnostic(
                        species = SPECIES,
                        form = it,
                        score = 0f,
                        source = "authoritative_variant_db",
                        reason = "fixed_from_saved_same_frame_identity_for_variant_only_replay"
                    )
                )
            }.orEmpty()
            val lockedEvidence = SpeciesEvidence(
                selectedCanonicalSpecies = SPECIES,
                authority = SpeciesAuthority.EXACT_CANONICAL,
                profileStatus = SpeciesProfileStatus.COMPATIBLE,
                reasonCodes = listOf(SpeciesEvidenceReason.EXACT, SpeciesEvidenceReason.PROFILE_COMPATIBLE),
                observationsAgree = true,
                authorityConflict = false
            )
            val pokemon = PokemonData(
                cp = EXPECTED_CP,
                hp = EXPECTED_HP,
                maxHp = null,
                name = SPECIES,
                realName = SPECIES,
                candyName = SPECIES,
                megaEnergy = null,
                weight = null,
                height = null,
                stardust = null,
                caughtDate = null
            )
            val detector = VisualFeatureDetector(context)
            val decisionEngine = VariantDecisionEngine(context)
            val classification = decisionEngine.classify(frame, pokemon, recorder)
            val baselineVisual = detector.detect(frame, SPECIES, null, recorder, SPECIES)
            val phase2 = Phase2VariantClassifier(context).classify(frame, SPECIES)
            recorder.recordPhase2Status(if (phase2 == null) "classifier_unavailable" else "classifier_completed")
            val baseline = evaluatePipeline(
                context = context,
                pokemon = pokemon,
                classification = classification,
                visualInput = baselineVisual,
                fullMatch = classification.fullMatch,
                phase2 = phase2,
                speciesEvidence = lockedEvidence,
                formCandidates = formCandidates,
                recorder = recorder
            )
            val baselineReport = recorder.snapshot(build, metadata)

            val prepared = sourceCrop
            val signature = SpriteSignature.computeFromMaskedSprite(prepared.masked)
            val pHash = PerceptualHash.compute(prepared.masked)
            val headHeight = (prepared.masked.height * HEAD_FRACTION).toInt()
                .coerceIn(1, prepared.masked.height)
            val head = Bitmap.createBitmap(prepared.masked, 0, 0, prepared.masked.width, headHeight)
            val headPHash = try {
                PerceptualHash.compute(head)
            } finally {
                if (head !== prepared.masked) head.recycle()
            }
            val currentCostume = requireNotNull(
                CostumeSignatureStore.matchSignatureDetails(signature, SPECIES, pHash, headPHash)
            )
            val currentCostumeDistances = CostumeSignatureStore.diagnosticCandidateDistances(
                signature,
                SPECIES,
                pHash,
                headPHash,
                MAX_CANDIDATES
            )
            val bodyTop = (prepared.masked.height * BODY_FRACTION).toInt()
                .coerceIn(0, prepared.masked.height.coerceAtLeast(1) - 1)
            val maskedBody = crop(prepared.masked, bodyTop)
            val sourceBody = crop(prepared.source, bodyTop)
            val maskedBodyHistogram = SpriteColorSignature.computeHueHistogram(maskedBody)
            val sourceBodyHistogram = SpriteColorSignature.computeHueHistogram(sourceBody)
            maskedBody.recycle()
            sourceBody.recycle()
            val fullHistogram = SpriteColorSignature.computeHueHistogram(prepared.masked)
            val upperHeight = (prepared.masked.height * UPPER_FRACTION).toInt()
                .coerceIn(1, prepared.masked.height)
            val upper = Bitmap.createBitmap(prepared.masked, 0, 0, prepared.masked.width, upperHeight)
            val upperHistogram = try {
                SpriteColorSignature.computeHueHistogram(upper)
            } finally {
                if (upper !== prepared.masked) upper.recycle()
            }
            val colorEvidence = detector.shinyColorEvidenceForPreparedCrop(
                prepared.masked,
                prepared.source,
                SPECIES
            )
            val strictShiny = baselineReport.shinySignature?.strictShiny ?: false
            val currentShiny = shinyEntry(
                JSONObject(String(readAsset(context, SHINY_ASSET), Charsets.UTF_8)),
                SPECIES
            )
            val candidateShiny = candidateShinyEntry(candidateInput.getJSONObject("shinyReference"))
            val currentShinyArm = scoreShinyArm(
                detector,
                signature,
                maskedBodyHistogram,
                sourceBodyHistogram,
                fullHistogram,
                upperHistogram,
                colorEvidence,
                currentShiny,
                strictShiny
            )
            val candidateCostumes = candidateCostumeReferences(candidateInput.getJSONArray("costumeReferences"))
            val candidateCostumeQuery = CostumeSignatureStore.ReferenceSetQuery(
                signature,
                SPECIES,
                candidateCostumes,
                pHash,
                headPHash
            )
            val candidateCostume = requireNotNull(
                CostumeSignatureStore.matchSignatureDetailsWithReferences(candidateCostumeQuery)
            )
            val candidateCostumeDistances = CostumeSignatureStore
                .diagnosticCandidateDistancesWithReferences(candidateCostumeQuery)
            val candidateHeuristicAllowed = detector.shouldUseCostumeHeuristic(candidateCostume, SPECIES)
            val candidateCostumeDecision = candidateCostume.matched &&
                candidateCostume.confidence >= MIN_COSTUME_CONFIDENCE
            val candidateStrictShiny = candidateCostumeDecision && candidateCostume.confidence >= STRICT_SHINY_CONFIDENCE
            val candidateShinyArm = scoreShinyArm(
                detector,
                signature,
                maskedBodyHistogram,
                sourceBodyHistogram,
                fullHistogram,
                upperHistogram,
                colorEvidence,
                candidateShiny,
                candidateStrictShiny
            )
            val groupedHueChoice = detector.chooseShinyDecision(
                candidateShinyArm.consensus,
                colorEvidence.copy(histogramHue = falseEvidence()),
                SPECIES
            )
            val rgbOnlyChoice = detector.chooseShinyDecision(
                candidateShinyArm.consensus,
                colorEvidence.copy(hue = falseEvidence(), histogramHue = falseEvidence()),
                SPECIES
            )
            val candidateVisual = baselineVisual.copy(
                isShiny = candidateShinyArm.choice.result.first,
                hasCostume = candidateCostumeDecision
            )
            val authoritative = AuthoritativeVariantDbLoader.load(context).entries
            val candidateMatcherCandidates = FullVariantCandidateBuilder.build(
                pokemon = pokemon,
                finalSpecies = SPECIES,
                globalMatch = classification.globalMatch,
                speciesMatch = FullVariantSeedSelectionCompat.choose(
                    finalSpecies = SPECIES,
                    speciesMatch = classification.speciesMatch,
                    resolvedMatch = classification.resolvedMatch
                ),
                authoritativeBySpecies = AuthoritativeVariantDbLoader.indexBySpecies(authoritative),
                costumeSignatureKey = candidateCostume.bestCostumeKey,
                costumeSignatureConfidence = candidateCostume.confidence
            )
            val candidateFullMatch = FullVariantMatcher.match(
                finalSpecies = SPECIES,
                candidates = candidateMatcherCandidates,
                costumeEvidence = FullVariantMatcher.CostumeEvidence(
                    matched = candidateCostume.matched,
                    confidence = candidateCostume.confidence,
                    preferredSpriteKey = candidateCostume.bestCostumeKey
                )
            )
            val candidate = evaluatePipeline(
                context = context,
                pokemon = pokemon,
                classification = classification,
                visualInput = candidateVisual,
                fullMatch = candidateFullMatch,
                phase2 = phase2,
                speciesEvidence = lockedEvidence,
                formCandidates = formCandidates,
                recorder = null
            )

            val sourceAssetHashes = candidateInput.getJSONObject("sourceAssetSha256")
            val report = JSONObject()
                .put("schemaVersion", 1)
                .put("scope", "pixel_android_offline_native_full_frame_variant_pipeline")
                .put("privacy", JSONObject()
                    .put("localOnly", true)
                    .put("historyWritten", false)
                    .put("screenshotExported", false)
                    .put("rawOcrIncluded", false)
                    .put("localPathsIncluded", false))
                .put("groundTruth", JSONObject()
                    .put("source", "user_confirmed_case_description")
                    .put("species", SPECIES)
                    .put("cp", EXPECTED_CP)
                    .put("hp", EXPECTED_HP)
                    .put("shiny", "FALSE")
                    .put("costume", "TRUE"))
                .put("build", JSONObject()
                    .put("applicationId", build.applicationId)
                    .put("versionName", build.versionName)
                    .put("versionCode", build.versionCode)
                    .put("sourceSha", build.sourceSha)
                    .put("installedApkSha256", sha256(File(context.applicationInfo.sourceDir))))
                .put("runtimeMetadata", JSONObject(Gson().toJson(metadata)))
                .put("sourceCapture", JSONObject()
                    .put("sourceSha", manifest.getJSONObject("build").optString("sourceSha"))
                    .put("sourceMetadataRevision", originalMetadata)
                    .put("opaqueSourceScanId", EXPECTED_SCAN_ID))
                .put("input", JSONObject()
                    .put("sha256", frameHash)
                    .put("manifestSha256", manifestHash)
                    .put("savedTraceSha256", traceHash)
                    .put("selectedFrameRef", recordedFrameRef)
                    .put("width", frame.width)
                    .put("height", frame.height)
                    .put("byteSize", frameFile.length())
                    .put("cropAndMask", JSONObject()
                        .put("analysisWidth", prepared.analysisWidth)
                        .put("analysisHeight", prepared.analysisHeight)
                        .put("left", prepared.crop.left)
                        .put("top", prepared.crop.top)
                        .put("right", prepared.crop.right)
                        .put("bottom", prepared.crop.bottom)
                        .put("shadowMaskApplied", prepared.shadowMaskApplied)
                        .put("savedMaskSha256", savedMaskHash)
                        .put("savedAnalysisRegionSha256", savedSourceCropHash)
                        .put("savedMaskPixelParity", savedMaskPixelParity)
                        .put("savedAnalysisRegionPixelParity", savedSourcePixelParity)
                        .put("spriteWidth", prepared.masked.width)
                        .put("spriteHeight", prepared.masked.height)
                        .put("generatedMaskSha256", sha256(prepared.masked))))
                .put("expectedCase", JSONObject()
                    .put("lockedSpeciesSource", "user_confirmed_ground_truth_seed_for_variant_only_replay")
                    .put("cpHpSource", "user_confirmed_case_description")
                    .put("formSource", "reused_from_saved_same_frame_identity; not independently rerun"))
                .put("productionBaseline", JSONObject()
                    .put("diagnosticTrace", JSONObject(Gson().toJson(baselineReport)))
                    .put("fullVariantMatcher", fullMatchJson(classification.fullMatch))
                    .put("costumeSignature", JSONObject()
                        .put("candidateCount", currentCostume.candidateCount)
                        .put("costumeCandidateCount", currentCostume.costumeCandidateCount)
                        .put("bestNormalKey", currentCostume.bestNormalKey)
                        .put("bestNormalScore", currentCostume.bestNormal)
                        .put("bestCostumeKey", currentCostume.bestCostumeKey)
                        .put("bestCostumeScore", currentCostume.bestCostume)
                        .put("scoreGap", currentCostume.scoreGap)
                        .put("confidence", currentCostume.confidence)
                        .put("matched", currentCostume.matched)
                        .put("rejectionReason", currentCostume.rejectionReason)
                        .put("candidateDistances", JSONArray(Gson().toJson(currentCostumeDistances))))
                    .put("phase2", phase2Json(phase2, baseline.phase2Decisions))
                    .put("variantMergeFeatures", visualJson(baseline.variantMerged))
                    .put("finalIdentity", identityJson(baseline.identity))
                    .put("compatibilityFeatures", visualJson(baseline.compatFeatures))
                    .put("rarity", rarityJson(baseline.rarity))
                    .put("cpHpPreserved", baseline.pokemon.cp == EXPECTED_CP && baseline.pokemon.hp == EXPECTED_HP))
                .put("candidate", JSONObject()
                    .put("referenceInputSha256", sha256(candidateBytes))
                    .put("referenceSourceSha256", candidateInput.getString("candidateSourceJsonSha256"))
                    .put("referenceBaseSourceSha", candidateInput.getString("baseSourceSha"))
                    .put("sourceAssetSha256", sourceAssetHashes)
                    .put("shiny", shinyArmJson(candidateShinyArm))
                    .put("colorCounterfactuals", JSONObject()
                        .put("groupHueAndHistogramAsOneFamily", choiceJson(groupedHueChoice))
                        .put("rgbCorroborationOnly", choiceJson(rgbOnlyChoice)))
                    .put("costume", JSONObject()
                        .put("candidateCount", candidateCostume.candidateCount)
                        .put("costumeCandidateCount", candidateCostume.costumeCandidateCount)
                        .put("bestNormalKey", candidateCostume.bestNormalKey)
                        .put("bestNormalScore", candidateCostume.bestNormal)
                        .put("bestCostumeKey", candidateCostume.bestCostumeKey)
                        .put("bestCostumeScore", candidateCostume.bestCostume)
                        .put("scoreGap", candidateCostume.scoreGap)
                        .put("confidence", candidateCostume.confidence)
                        .put("matched", candidateCostume.matched)
                        .put("heuristicAllowed", candidateHeuristicAllowed)
                        .put("heuristicPath", if (candidateHeuristicAllowed) "not_replayed" else "signature_rejected_heuristic_not_allowed")
                        .put("finalDecision", candidateCostumeDecision)
                        .put("strictShiny", candidateStrictShiny)
                        .put("rejectionReason", candidateCostume.rejectionReason)
                        .put("candidateDistances", JSONArray(Gson().toJson(candidateCostumeDistances))))
                    .put("prototypeMatchesReusedFromSameFrame", JSONObject()
                        .put("global", classification.globalMatch?.let { JSONObject(Gson().toJson(it)) })
                        .put("species", classification.speciesMatch?.let { JSONObject(Gson().toJson(it)) })
                        .put("reason", "same pixels and prototype model; only signature references changed"))
                    .put("fullVariantMatcher", fullMatchJson(candidateFullMatch))
                    .put("variantMerge", JSONArray(Gson().toJson(candidate.mergeDecisions.take(MAX_CANDIDATES))))
                    .put("variantMergeFeatures", visualJson(candidate.variantMerged))
                    .put("phase2", phase2Json(phase2, candidate.phase2Decisions))
                    .put("finalIdentity", identityJson(candidate.identity))
                    .put("compatibilityFeatures", visualJson(candidate.compatFeatures))
                    .put("rarity", rarityJson(candidate.rarity))
                    .put("cpHpPreserved", candidate.pokemon.cp == EXPECTED_CP && candidate.pokemon.hp == EXPECTED_HP))
                .put("earliestIncorrectStages", JSONObject()
                    .put("productionBaseline", JSONObject()
                        .put("shiny", firstShinyPromotion(baselineReport, baseline))
                        .put("costume", firstCostumeMiss(
                            baselineReport,
                            baseline,
                            currentCostume,
                            baselineVisual.hasCostume
                        )))
                    .put("candidate", JSONObject()
                        .put("shiny", firstShinyPromotion(baselineReport, candidate, candidateShinyArm))
                        .put("costume", firstCostumeMiss(
                            baselineReport,
                            candidate,
                            candidateCostume,
                            candidateCostumeDecision,
                            signaturePath = "explicit_candidate_reference",
                            heuristicPath = if (candidateHeuristicAllowed) {
                                "candidate_reference_heuristic_allowed_not_replayed"
                            } else {
                                "candidate_signature_rejected_heuristic_not_allowed"
                            },
                            detectorCostumeConfidence = candidateCostume.confidence
                        ))))
                .put("sameFrameParity", JSONObject()
                    .put("productionCropMatchesRecordedBounds", cropMatchesManifest(prepared, manifest))
                    .put("productionDetectorShiny", baselineVisual.isShiny)
                    .put("productionDetectorCostume", baselineVisual.hasCostume)
                    .put("currentSignatureCostume", currentCostume.matched)
                    .put("currentSignatureCostumeReason", currentCostume.rejectionReason)
                    .put("explicitCurrentReferenceShiny", currentShinyArm.choice.result.first)
                    .put("explicitCurrentReferenceConfidence", currentShinyArm.choice.result.second)
                    .put("explicitCurrentReferenceMatchesProduction", currentShinyArm.choice.result.first == baselineVisual.isShiny)
                    .put("savedOriginalIdentity", expectedIdentity)
                    .put("savedOriginalIsGroundTruth", false))
                .put("controls", JSONObject()
                    .put("independentShinyPositive", false)
                    .put("independentCrownNegative", false)
                    .put("independentCostumeFreeCottonee", false)
                    .put("ordinarySpritzeeOrLatios", false)
                    .put("targetFrameIsControl", false))
                .put("limits", JSONArray()
                    .put("offline saved-frame replay on Pixel; not a new Pokemon GO scan")
                    .put("species, CP and HP are fixed to user-confirmed case labels; OCR was not rerun")
                    .put("saved same-frame form label is held constant; form inference was not rerun")
                    .put("candidate reference scoring bypasses production caches and does not modify APK assets"))

            val outputDir = File(requireNotNull(context.getExternalFilesDir(null)), OUTPUT_DIRECTORY)
            check(outputDir.mkdirs() || outputDir.isDirectory)
            val output = File(outputDir, OUTPUT_FILE)
            assertFalse("Refusing to replace prior QA evidence", output.exists())
            output.writeText(report.toString(2))
            assertTrue("Bounded full-frame report exceeded 128 KiB", output.length() <= MAX_REPORT_BYTES)
            assertTrue("Native frame was not the original 1080x2340 input", frame.width == 1080 && frame.height == 2340)
            assertTrue("Production crop selection differs from the saved frame manifest", cropMatchesManifest(prepared, manifest))
            assertFalse("Candidate costume heuristic path requires an unimplemented alternate-reference replay", candidateHeuristicAllowed)
            assertTrue("CP/HP changed across a variant-only replay", baseline.pokemon.cp == EXPECTED_CP && baseline.pokemon.hp == EXPECTED_HP)
            assertTrue("Candidate CP/HP changed across a variant-only replay", candidate.pokemon.cp == EXPECTED_CP && candidate.pokemon.hp == EXPECTED_HP)
        } finally {
            sourceCrop.recycle()
            frame.recycle()
        }
    }

    @Suppress("LongMethod")
    private fun evaluatePipeline(
        context: android.content.Context,
        pokemon: PokemonData,
        classification: VariantDecisionEngine.ClassificationResult,
        visualInput: VisualFeatures,
        fullMatch: com.pokerarity.scanner.data.model.FullVariantMatch?,
        phase2: Phase2VariantClassifier.Result?,
        speciesEvidence: SpeciesEvidence,
        formCandidates: List<FormCandidateDiagnostic>,
        recorder: VariantStageDiagnosticRecorder?
    ): PipelineOutput {
        val mergeDecisions = mutableListOf<QaMergeDecision>()
        val phase2Decisions = mutableListOf<QaPhase2Decision>()
        val engine = VariantDecisionEngine(context)
        val recordVariantDecisions: (List<QaMergeDecision>) -> Unit = { decisions ->
            mergeDecisions.addAll(decisions.take(MAX_CANDIDATES))
            recorder?.recordVariantMerge(decisions)
        }
        val variantMerged = engine.mergeVisualFeaturesForLockedSpecies(
            visualFeatures = visualInput,
            lockedSpecies = SPECIES,
            fullMatch = fullMatch,
            fallbackMatch = classification.resolvedMatch ?: classification.globalMatch,
            onDecision = recordVariantDecisions
        )
        val recordPhase2Decisions: (List<QaPhase2Decision>) -> Unit = { decisions ->
            phase2Decisions.addAll(decisions.take(MAX_CANDIDATES))
            recorder?.recordPhase2Merge(decisions)
        }
        val merged = Phase2VariantFeatureMerger.merge(
            variantMerged,
            phase2,
            recordPhase2Decisions
        )
        val identity = RecognitionIdentityFactory.build(
            RecognitionIdentityFactory.Input(
                speciesEvidence = speciesEvidence,
                scanAccepted = true,
                lockedSpecies = SPECIES,
                classifierSpecies = classification.resolvedMatch?.species,
                fullMatchWinnerSpecies = fullMatch?.winnerSpecies,
                formCandidates = formCandidates,
                mergedFeatures = merged,
                phase2ShinyDemoted = Phase2VariantFeatureMerger.shinyDemotionApplied(variantMerged, phase2),
                sizeTag = null
            )
        )
        recorder?.recordIdentity(identity)
        val compatFeatures = RecognitionIdentityCompat.toVisualFeatures(identity, merged.confidence)
        val finalPokemon = classification.pokemon.copy(
            cp = pokemon.cp,
            hp = pokemon.hp,
            fullVariantMatch = fullMatch,
            recognitionIdentity = identity
        )
        val rarity = RarityCalculator(context).calculate(finalPokemon, compatFeatures)
        return PipelineOutput(
            pokemon = finalPokemon,
            fullMatch = fullMatch,
            variantMerged = variantMerged,
            merged = merged,
            identity = identity,
            compatFeatures = compatFeatures,
            rarity = rarity,
            mergeDecisions = mergeDecisions,
            phase2Decisions = phase2Decisions
        )
    }

    private fun prepareProductionCrop(
        frame: Bitmap,
        context: android.content.Context
    ): PreparedCrop {
        val downscaled = ColorAnalyzer.downscaleForAnalysis(frame)
        val shadowResult = VisualFeatureDetector(context).isShadow(downscaled)
        val shadowMaskApplied = shadowResult.first
        val analysis = if (shadowMaskApplied) ColorAnalyzer.maskShadowFlames(downscaled) else downscaled
        val crop = ColorAnalyzer.getSpriteRegionAdaptive(analysis)
        val masked = ColorAnalyzer.maskSpriteBackground(analysis, crop)
        val source = Bitmap.createBitmap(analysis, crop.left, crop.top, crop.width(), crop.height())
        return PreparedCrop(
            analysis = analysis,
            downscaled = downscaled,
            masked = masked,
            source = source,
            crop = crop,
            analysisWidth = analysis.width,
            analysisHeight = analysis.height,
            shadowMaskApplied = shadowMaskApplied,
            recycleAnalysis = analysis !== downscaled
        )
    }

    private fun scoreShinyArm(
        detector: VisualFeatureDetector,
        signature: SpriteSignature.Signature,
        maskedBody: FloatArray,
        sourceBody: FloatArray,
        full: FloatArray,
        upper: FloatArray,
        color: VisualFeatureDetector.ShinyColorEvidence,
        reference: ShinySignatureStore.ShinyEntry,
        strict: Boolean
    ): ShinyArm {
        val primary = ShinySignatureStore.matchSignatureDetailsWithReference(
            signature, maskedBody, sourceBody, reference, strict
        )
        val fullMatch = ShinySignatureStore.matchSignatureDetailsWithReference(
            signature, full, null, reference, strict
        )
        val upperMatch = ShinySignatureStore.matchSignatureDetailsWithReference(
            signature, upper, null, reference, strict
        )
        val consensus = detector.chooseBestShinySignatureResult(
            primary.matched to primary.confidence,
            listOf(fullMatch.matched to fullMatch.confidence, upperMatch.matched to upperMatch.confidence)
        )
        return ShinyArm(
            primary = primary,
            alternates = listOf(fullMatch, upperMatch),
            consensus = consensus,
            color = color,
            choice = detector.chooseShinyDecision(consensus, color, SPECIES)
        )
    }

    private fun candidateShinyEntry(json: JSONObject): ShinySignatureStore.ShinyEntry =
        ShinySignatureStore.ShinyEntry(
            species = json.getString("species"),
            key = json.getString("key"),
            normal = shinyReference(json.getJSONObject("normal")),
            shiny = shinyReference(json.getJSONObject("shiny"))
        )

    private fun shinyEntry(root: JSONObject, species: String): ShinySignatureStore.ShinyEntry {
        val entries = root.getJSONArray("entries")
        val json = (0 until entries.length())
            .map(entries::getJSONObject)
            .first { it.getString("species").equals(species, ignoreCase = true) }
        return ShinySignatureStore.ShinyEntry(
            species = json.getString("species"),
            key = json.getString("key"),
            normal = shinyReference(json.getJSONObject("normal")),
            shiny = shinyReference(json.getJSONObject("shiny"))
        )
    }

    private fun shinyReference(json: JSONObject): ShinySignatureStore.SignatureRef =
        ShinySignatureStore.SignatureRef(
            aHash = json.getString("aHash"),
            dHash = json.getString("dHash"),
            edge = floatArray(json.getJSONArray("edge")),
            color = colorArray(json.get("color"))
        )

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

    private fun fullMatchJson(match: com.pokerarity.scanner.data.model.FullVariantMatch?): JSONObject =
        JSONObject().apply {
            if (match == null) {
                put("status", "no_match")
            } else {
                put("finalSpecies", match.finalSpecies)
                put("winnerSpecies", match.winnerSpecies)
                put("spriteKey", match.finalSpriteKey)
                put("variantClass", match.resolvedVariantClass)
                put("shiny", match.resolvedShiny)
                put("costume", match.resolvedCostume)
                put("form", match.resolvedForm)
                put("explanationMode", match.explanationMode)
                put("variantConfidence", match.variantConfidence)
                put("shinyConfidence", match.shinyConfidence)
                put("debugSummary", match.debugSummary)
                put("candidateCount", match.candidates.size)
                put("candidates", JSONArray(Gson().toJson(match.candidates.take(MAX_CANDIDATES))))
            }
        }

    private fun phase2Json(
        result: Phase2VariantClassifier.Result?,
        decisions: List<QaPhase2Decision>
    ): JSONObject = JSONObject()
        .put("available", result != null)
        .put("modelType", result?.modelType)
        .put("appliedTargets", JSONArray(Gson().toJson(result?.appliedTargets.orEmpty().take(MAX_CANDIDATES))))
        .put("predictions", JSONArray(Gson().toJson(result?.predictions.orEmpty().take(MAX_CANDIDATES))))
        .put("mergeDecisions", JSONArray(Gson().toJson(decisions.take(MAX_CANDIDATES))))

    private fun shinyArmJson(arm: ShinyArm): JSONObject = JSONObject()
        .put("primary", JSONObject(Gson().toJson(arm.primary)))
        .put("alternates", JSONArray(Gson().toJson(arm.alternates.take(MAX_CANDIDATES))))
        .put("consensus", JSONObject(Gson().toJson(arm.consensus)))
        .put("colorEvidence", colorEvidenceJson(arm.color))
        .put("choice", choiceJson(arm.choice))

    private fun choiceJson(choice: VisualFeatureDetector.ShinyChoiceDetails): JSONObject = JSONObject()
        .put("shiny", choice.result.first)
        .put("confidence", choice.result.second)
        .put("selectedPath", choice.selectedPath)
        .put("colorFallbackCalled", choice.colorFallbackCalled)
        .put("colorFallbackReason", choice.colorFallbackReason)

    private fun colorEvidenceJson(value: VisualFeatureDetector.ShinyColorEvidence): JSONObject = JSONObject()
        .put("maskedRgb", evidenceJson(value.masked))
        .put("rawRgb", evidenceJson(value.raw))
        .put("hue", evidenceJson(value.hue))
        .put("histogramHue", evidenceJson(value.histogramHue))

    private fun evidenceJson(value: Pair<Boolean, Float>): JSONObject = JSONObject()
        .put("positive", value.first)
        .put("confidence", value.second)

    private fun falseEvidence(): Pair<Boolean, Float> = false to 0f

    private fun identityJson(identity: RecognitionIdentity): JSONObject = JSONObject()
        .put("speciesStatus", identity.speciesStatus.name)
        .put("species", identity.canonicalSpecies)
        .put("speciesAuthority", identity.speciesAuthority)
        .put("formStatus", identity.formStatus.name)
        .put("knownForm", identity.knownForm)
        .put("specialForm", identity.specialForm.name)
        .put("shiny", identity.shiny.name)
        .put("costume", identity.costume.name)

    private fun visualJson(features: VisualFeatures): JSONObject = JSONObject()
        .put("shiny", features.isShiny)
        .put("costume", features.hasCostume)
        .put("specialForm", features.hasSpecialForm)
        .put("confidence", features.confidence)

    private fun rarityJson(score: RarityScore): JSONObject = JSONObject()
        .put("totalScore", score.totalScore)
        .put("tier", score.tier.name)
        .put("breakdown", JSONObject(Gson().toJson(score.breakdown)))
        .put("explanation", JSONArray(Gson().toJson(score.explanation.take(MAX_CANDIDATES))))

    private fun assertManifestImage(manifest: JSONObject, fileName: String, sha256: String) {
        val images = manifest.getJSONArray("images")
        val image = (0 until images.length())
            .map(images::getJSONObject)
            .first { it.getString("fileName") == fileName }
        assertEquals(sha256, image.getString("sha256"))
    }

    private fun firstShinyPromotion(
        diagnostic: com.pokerarity.scanner.util.vision.QaVariantDiagnosticReport,
        pipeline: PipelineOutput,
        candidateArm: ShinyArm? = null
    ): JSONObject {
        if (pipeline.identity.shiny.name != "TRUE") {
            return JSONObject().put("status", "no_false_positive_in_full_frame_replay")
        }
        val signature = diagnostic.shinySignature
        val signatureMatched = candidateArm?.consensus?.result?.first ?: signature?.selectedMatched
        val decisionPath = candidateArm?.choice?.selectedPath ?: diagnostic.visualDetector.shinyDecisionPath
        if (signatureMatched == true) {
            if (decisionPath == "strong_primary_signature") {
                val selectedMatch = candidateArm?.let { arm ->
                    when (arm.consensus.selectedSource) {
                        "primary" -> arm.primary
                        "alternate_masked_full" -> arm.alternates.getOrNull(0)
                        else -> arm.alternates.getOrNull(1)
                    }
                }
                val selectedRecordedMatch = when (signature?.selectedSource) {
                    "primary" -> signature?.primary
                    "alternate_masked_full" -> signature?.alternates?.getOrNull(0)
                    else -> signature?.alternates?.getOrNull(1)
                }
                return JSONObject()
                    .put("stage", "ShinySignatureStore")
                    .put("source", candidateArm?.consensus?.selectedSource ?: signature?.selectedSource)
                    .put("matched", signatureMatched)
                    .put("confidence", candidateArm?.consensus?.result?.second ?: signature?.selectedConfidence)
                    .put("normalScore", selectedMatch?.normalScore ?: selectedRecordedMatch?.normalScore)
                    .put("shinyScore", selectedMatch?.shinyScore ?: selectedRecordedMatch?.shinyScore)
                    .put("scoreGap", selectedMatch?.scoreGap ?: selectedRecordedMatch?.scoreGap)
                    .put("colorGap", selectedMatch?.colorGap ?: selectedRecordedMatch?.colorGap)
                    .put("reason", selectedMatch?.reasonCode ?: selectedRecordedMatch?.reasonCode)
            }
        }
        val detectorShiny = candidateArm?.choice?.result?.first ?: diagnostic.visualDetector.shiny
        if (detectorShiny == true) {
            return JSONObject()
                .put("stage", "VisualFeatureDetector")
                .put("path", decisionPath)
                .put("signatureMatched", signatureMatched)
                .put("signatureSource", candidateArm?.consensus?.selectedSource ?: signature?.selectedSource)
                .put("signatureConfidence", candidateArm?.consensus?.result?.second ?: signature?.selectedConfidence)
                .put("colorFallbackCalled", candidateArm?.choice?.colorFallbackCalled
                    ?: diagnostic.visualDetector.colorFallbackCalled)
                .put("reason", candidateArm?.choice?.colorFallbackReason
                    ?: diagnostic.visualDetector.colorFallbackReason)
        }
        val mergePromotion = pipeline.mergeDecisions.lastOrNull { it.target == "isShiny" && it.after }
        if (mergePromotion != null) {
            return JSONObject()
                .put("stage", "VariantMergeLogic")
                .put("action", mergePromotion.action)
                .put("reason", mergePromotion.reasonCode)
        }
        val phase2Promotion = pipeline.phase2Decisions.lastOrNull { it.target == "isShiny" && it.action == "promoted" }
        if (phase2Promotion != null) {
            return JSONObject()
                .put("stage", "Phase2VariantFeatureMerger")
                .put("action", phase2Promotion.action)
                .put("reason", phase2Promotion.reasonCode)
                .put("confidence", phase2Promotion.confidence)
                .put("margin", phase2Promotion.margin)
        }
        return JSONObject()
            .put("stage", "RecognitionIdentityFactory")
            .put("reason", "typed_identity_is_true_after_all_recorded_variant_inputs")
    }

    private fun firstCostumeMiss(
        diagnostic: com.pokerarity.scanner.util.vision.QaVariantDiagnosticReport,
        pipeline: PipelineOutput,
        costume: CostumeSignatureStore.MatchDetails,
        detectorCostume: Boolean,
        signaturePath: String? = null,
        heuristicPath: String? = null,
        detectorCostumeConfidence: Float? = null
    ): JSONObject {
        if (pipeline.identity.costume.name == "TRUE") {
            return JSONObject().put("status", "costume_true_in_full_frame_replay")
        }
        if (!detectorCostume && !costume.matched) {
            return JSONObject()
                .put("stage", "CostumeSignatureStore")
                .put("bestNormalKey", costume.bestNormalKey)
                .put("bestNormalScore", costume.bestNormal)
                .put("bestCostumeKey", costume.bestCostumeKey)
                .put("bestCostumeScore", costume.bestCostume)
                .put("scoreGap", costume.scoreGap)
                .put("confidence", costume.confidence)
                .put("candidateCount", costume.candidateCount)
                .put("rejectionReason", costume.rejectionReason)
        }
        if (!detectorCostume) {
            return JSONObject()
                .put("stage", "VisualFeatureDetector")
                .put("signaturePath", signaturePath ?: diagnostic.visualDetector.costumeSignaturePath)
                .put("heuristicPath", heuristicPath ?: diagnostic.visualDetector.costumeHeuristicPath)
                .put("costumeConfidence", detectorCostumeConfidence ?: diagnostic.visualDetector.costumeConfidence)
                .put("rejectionReason", costume.rejectionReason)
        }
        val mergeRejection = pipeline.mergeDecisions.lastOrNull { it.target == "hasCostume" && !it.after }
        if (mergeRejection != null) {
            return JSONObject()
                .put("stage", "VariantMergeLogic")
                .put("action", mergeRejection.action)
                .put("reason", mergeRejection.reasonCode)
        }
        val phase2Rejection = pipeline.phase2Decisions.lastOrNull { it.target == "hasCostume" && it.action == "demoted" }
        if (phase2Rejection != null) {
            return JSONObject()
                .put("stage", "Phase2VariantFeatureMerger")
                .put("action", phase2Rejection.action)
                .put("reason", phase2Rejection.reasonCode)
        }
        return JSONObject()
            .put("stage", "RecognitionIdentityFactory")
            .put("reason", "costume_remained_unknown_after_detector_and_merge")
    }

    private fun cropMatchesManifest(crop: PreparedCrop, manifest: JSONObject): Boolean {
        val expected = manifest.getJSONObject("crop")
        return crop.analysisWidth == expected.getInt("analysisWidth") &&
            crop.analysisHeight == expected.getInt("analysisHeight") &&
            crop.crop.left == expected.getInt("left") &&
            crop.crop.top == expected.getInt("top") &&
            crop.crop.right == expected.getInt("right") &&
            crop.crop.bottom == expected.getInt("bottom") &&
            crop.shadowMaskApplied == expected.getBoolean("shadowMaskApplied")
    }

    private fun crop(bitmap: Bitmap, top: Int): Bitmap = Bitmap.createBitmap(
        bitmap,
        0,
        top,
        bitmap.width,
        (bitmap.height - top).coerceAtLeast(1)
    )

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

    private fun readAsset(context: android.content.Context, path: String): ByteArray =
        context.assets.open(path).use { it.readBytes() }

    private fun decode(file: File): Bitmap = requireNotNull(
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    ) { "Could not decode the preserved native frame" }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun sha256(bitmap: Bitmap): String {
        val output = java.io.ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        return MessageDigest.getInstance("SHA-256")
            .digest(output.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class PreparedCrop(
        val analysis: Bitmap,
        val downscaled: Bitmap,
        val masked: Bitmap,
        val source: Bitmap,
        val crop: Rect,
        val analysisWidth: Int,
        val analysisHeight: Int,
        val shadowMaskApplied: Boolean,
        val recycleAnalysis: Boolean
    ) {
        fun recycle() {
            source.recycle()
            masked.recycle()
            if (recycleAnalysis) analysis.recycle()
            downscaled.recycle()
        }
    }

    private data class ShinyArm(
        val primary: ShinySignatureStore.MatchDetails,
        val alternates: List<ShinySignatureStore.MatchDetails>,
        val consensus: VisualFeatureDetector.SignatureConsensus,
        val color: VisualFeatureDetector.ShinyColorEvidence,
        val choice: VisualFeatureDetector.ShinyChoiceDetails
    )

    private data class PipelineOutput(
        val pokemon: PokemonData,
        val fullMatch: com.pokerarity.scanner.data.model.FullVariantMatch?,
        val variantMerged: VisualFeatures,
        val merged: VisualFeatures,
        val identity: RecognitionIdentity,
        val compatFeatures: VisualFeatures,
        val rarity: RarityScore,
        val mergeDecisions: List<QaMergeDecision>,
        val phase2Decisions: List<QaPhase2Decision>
    )

    private companion object {
        const val INPUT_DIRECTORY = "full-frame-input"
        const val OUTPUT_DIRECTORY = "full-frame-output"
        const val OUTPUT_FILE = "cottonee-full-frame-variant-diagnostics.json"
        const val MANIFEST_FILE = "manifest.json"
        const val TRACE_FILE = "variant-b92ef22bcb4a405bbb74f6e5da507d2e.json"
        const val FRAME_FILE = "detector-input.png"
        const val MASK_FILE = "masked-signature-sprite.png"
        const val SOURCE_CROP_FILE = "analysis-source-region.png"
        const val CANDIDATE_FILE = "android-compatible-reference-candidate.json"
        const val SHINY_ASSET = "data/shiny_signatures.json"
        const val SPECIES = "Cottonee"
        const val EXPECTED_CP = 595
        const val EXPECTED_HP = 98
        const val EXPECTED_SCAN_ID = "b92ef22bcb4a405bbb74f6e5da507d2e"
        const val EXPECTED_FRAME_SHA256 = "d0ec9a3151f086496f8194e4e529117307b3bcd49901b0caed158e469d701226"
        const val EXPECTED_METADATA_REVISION = "127df8f751688c20aa42618aadb238ad7c0c21f33d4768e6c1bdcd29dd4458d8"
        const val CANDIDATE_BASE_SHA = "aa97dacadd10ec8006e66f660fb056d5968d37c6"
        const val MAX_CANDIDATES = 8
        const val MAX_REPORT_BYTES = 128L * 1024L
        const val HEAD_FRACTION = 0.35f
        const val BODY_FRACTION = 0.30f
        const val UPPER_FRACTION = 0.55f
        const val STRICT_SHINY_CONFIDENCE = 0.60f
        const val MIN_COSTUME_CONFIDENCE = 0.20f
    }
}

private object FullVariantSeedSelectionCompat {
    fun choose(
        finalSpecies: String,
        speciesMatch: com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult?,
        resolvedMatch: com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult?
    ): com.pokerarity.scanner.util.vision.VariantPrototypeClassifier.MatchResult? =
        com.pokerarity.scanner.util.vision.FullVariantSeedSelection.chooseSpeciesSeed(
            finalSpecies,
            speciesMatch,
            resolvedMatch
        )
}
