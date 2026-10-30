package com.pokerarity.scanner.util.vision

import android.content.Context
import com.google.gson.GsonBuilder
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.RecognitionIdentity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

private const val MAX_QA_CANDIDATE_COUNT = 100_000

internal data class QaBuildIdentity(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val sourceSha: String
)

internal data class QaMetadataAsset(val id: String, val sha256: String?)

internal data class QaMetadataRevision(
    val source: String,
    val revisionSha256: String,
    val assets: List<QaMetadataAsset>
)

internal data class QaSelectedFrameReference(
    val ref: String?,
    val algorithm: String,
    val status: String
)

internal data class QaBooleanConfidence(val decision: Boolean, val confidence: Float)

internal data class QaShinySignatureMatch(
    val matched: Boolean,
    val confidence: Float,
    val normalScore: Float?,
    val shinyScore: Float?,
    val normalColorScore: Float?,
    val shinyColorScore: Float?,
    val scoreGap: Float?,
    val colorGap: Float?,
    val histogramSource: String,
    val strictMode: Boolean,
    val reasonCode: String
)

internal data class QaShinySignatureStage(
    val referenceStatus: String,
    val referenceKey: String?,
    val strictShiny: Boolean,
    val primary: QaShinySignatureMatch?,
    val alternates: List<QaShinySignatureMatch>,
    val selectedSource: String,
    val selectedMatched: Boolean,
    val selectedConfidence: Float,
    val rejectionReason: String?
)

internal data class QaCostumeStage(
    val source: String,
    val referenceAvailable: Boolean,
    val candidateCount: Int,
    val costumeCandidateCount: Int,
    val bestNormalKey: String?,
    val bestNormalScore: Float?,
    val bestCostumeKey: String?,
    val bestCostumeScore: Float?,
    val scoreGap: Float?,
    val confidence: Float?,
    val matched: Boolean?,
    val denseVariantSpecies: Boolean?,
    val rejectionReason: String,
    val heuristicPath: String? = null,
    val signatureDecisionPath: String? = null,
    val heuristicCalled: Boolean = false,
    val heuristicMatched: Boolean? = null,
    val heuristicConfidence: Float? = null,
    val strictShiny: Boolean? = null,
    val strictShinyReason: String? = null,
    val finalConfidence: Float? = null,
    val finalMatched: Boolean? = null
)

internal data class QaCostumeDecision(
    val matched: Boolean,
    val confidence: Float,
    val heuristicPath: String,
    val signatureDecisionPath: String,
    val heuristicCalled: Boolean,
    val heuristicResult: Pair<Boolean, Float>?,
    val strictShiny: Boolean,
    val strictShinyReason: String
)

internal data class QaPrototypeMatch(
    val role: String,
    val species: String,
    val assetKey: String,
    val spriteKey: String,
    val variantType: String,
    val shiny: Boolean,
    val costume: Boolean,
    val score: Float,
    val confidence: Float,
    val speciesMargin: Float,
    val variantMargin: Float,
    val bestBaseScore: Float?,
    val bestBaseSpriteKey: String?,
    val bestNonBaseScore: Float?,
    val bestNonBaseSpriteKey: String?,
    val bestNonBaseType: String?,
    val bestShinyPeerScore: Float?,
    val bestShinyPeerSpriteKey: String?,
    val bestBaseShinyPeerScore: Float?,
    val bestBaseShinyPeerSpriteKey: String?,
    val rescueKind: String?,
    val topSpecies: List<String>
)

internal data class QaPrototypeStageStatus(
    val role: String,
    val resultAvailable: Boolean,
    val reasonCode: String?
)

internal data class QaFullVariantCandidate(
    val species: String,
    val spriteKey: String,
    val variantClass: String,
    val shiny: Boolean,
    val costume: Boolean,
    val source: String,
    val score: Float,
    val confidence: Float,
    val rescueKind: String?
)

internal data class QaFullVariantStage(
    val lockedSpecies: String?,
    val selectedSpecies: String?,
    val selectedSpriteKey: String?,
    val selectedClass: String?,
    val selectionStatus: String,
    val shiny: Boolean?,
    val costume: Boolean?,
    val form: Boolean?,
    val decisionConfidence: Float?,
    val shinyConfidence: Float?,
    val explanationMode: String?,
    val candidateCount: Int,
    val candidates: List<QaFullVariantCandidate>
)

internal data class QaMergeDecision(
    val stage: String,
    val target: String,
    val before: Boolean,
    val after: Boolean,
    val action: String,
    val reasonCode: String
)

internal data class QaPhase2Decision(
    val target: String,
    val prediction: Boolean,
    val confidence: Float,
    val margin: Float,
    val positiveScore: Float,
    val negativeScore: Float,
    val positiveCount: Int?,
    val negativeCount: Int?,
    val source: String,
    val decisionCapable: Boolean,
    val passedThreshold: Boolean,
    val action: String,
    val reasonCode: String
)

internal data class QaVisualStage(
    val inputWidth: Int? = null,
    val inputHeight: Int? = null,
    val spriteCropWidth: Int? = null,
    val spriteCropHeight: Int? = null,
    val maskedColor: QaBooleanConfidence? = null,
    val rawColor: QaBooleanConfidence? = null,
    val hue: QaBooleanConfidence? = null,
    val histogramHue: QaBooleanConfidence? = null,
    val shiny: Boolean? = null,
    val shinyConfidence: Float? = null,
    val overallConfidence: Float? = null,
    val costume: Boolean? = null,
    val costumeConfidence: Float? = null,
    val costumeSignaturePath: String? = null,
    val costumeHeuristicPath: String? = null,
    val strictShiny: Boolean? = null,
    val strictShinyReason: String? = null,
    val colorFallbackCalled: Boolean? = null,
    val colorFallbackReason: String? = null,
    val shinyDecisionPath: String? = null
)

internal data class QaIdentityStage(
    val species: String?,
    val formStatus: String,
    val knownForm: String?,
    val ambiguousForms: List<String>,
    val shiny: String,
    val costume: String,
    val formTruth: String
)

internal data class QaVariantDiagnosticReport(
    val schemaVersion: Int,
    val scanId: String,
    val capturedAtEpochMs: Long,
    val build: QaBuildIdentity,
    val metadata: QaMetadataRevision,
    val selectedFrame: QaSelectedFrameReference,
    val lockedSpecies: String?,
    val detectorInputSpecies: String?,
    val visualDetector: QaVisualStage,
    val shinySignature: QaShinySignatureStage?,
    val costumeSignatures: List<QaCostumeStage>,
    val prototypeStageStatus: List<QaPrototypeStageStatus>,
    val prototypeClassifier: List<QaPrototypeMatch>,
    val fullVariantMatcher: QaFullVariantStage?,
    val variantMerge: List<QaMergeDecision>,
    val phase2Status: String?,
    val phase2Merge: List<QaPhase2Decision>,
    val finalIdentity: QaIdentityStage?,
    val instrumentationFailures: List<String>
)

internal data class QaDiagnosticWriteResult(val file: File?, val failureReason: String?)

@Suppress("TooManyFunctions") // One bounded recorder owns the per-scan decision evidence.
internal class VariantStageDiagnosticRecorder(
    val scanId: String = UUID.randomUUID().toString().replace("-", "")
) {
    private val lock = Any()
    private var lockedSpecies: String? = null
    private var detectorInputSpecies: String? = null
    private var visual = QaVisualStage()
    private var shinySignature: QaShinySignatureStage? = null
    private val costumeStages = mutableListOf<QaCostumeStage>()
    private val prototypeStatuses = mutableListOf<QaPrototypeStageStatus>()
    private val prototypeMatches = mutableListOf<QaPrototypeMatch>()
    private var fullVariantMatcher: QaFullVariantStage? = null
    private var selectedFrameReference: QaSelectedFrameReference? = null
    private val variantMerge = mutableListOf<QaMergeDecision>()
    private var phase2Status: String? = null
    private val phase2Merge = mutableListOf<QaPhase2Decision>()
    private var finalIdentity: QaIdentityStage? = null
    private val failures = mutableListOf<String>()

    fun recordSpeciesAuthority(value: String?) = synchronized(lock) {
        lockedSpecies = short(value)
    }

    fun recordDetectorInput(species: String?, width: Int, height: Int) = synchronized(lock) {
        detectorInputSpecies = short(species)
        visual = visual.copy(inputWidth = width.coerceAtLeast(0), inputHeight = height.coerceAtLeast(0))
    }

    fun recordSelectedFrameReference(reference: QaSelectedFrameReference) = synchronized(lock) {
        selectedFrameReference = reference.copy(
            ref = short(reference.ref),
            algorithm = short(reference.algorithm) ?: "unknown",
            status = short(reference.status) ?: "unknown"
        )
    }

    fun recordSpriteCrop(width: Int, height: Int) = synchronized(lock) {
        visual = visual.copy(spriteCropWidth = width.coerceAtLeast(0), spriteCropHeight = height.coerceAtLeast(0))
    }

    fun recordColorEvidence(
        masked: Pair<Boolean, Float>,
        raw: Pair<Boolean, Float>,
        hue: Pair<Boolean, Float>,
        histogramHue: Pair<Boolean, Float>
    ) = synchronized(lock) {
        visual = visual.copy(
            maskedColor = masked.toQaBooleanConfidence(),
            rawColor = raw.toQaBooleanConfidence(),
            hue = hue.toQaBooleanConfidence(),
            histogramHue = histogramHue.toQaBooleanConfidence()
        )
    }

    fun recordShinySignature(stage: QaShinySignatureStage) = synchronized(lock) {
        shinySignature = stage.bounded()
    }

    fun recordCostumeSignature(stage: QaCostumeStage) = synchronized(lock) {
        if (costumeStages.size < MAX_STAGE_RECORDS) costumeStages += stage.bounded()
    }

    fun recordCostumeDecision(decision: QaCostumeDecision) = synchronized(lock) {
        val visualIndex = costumeStages.indexOfLast { it.source == VISUAL_COSTUME_SOURCE }
        if (visualIndex >= 0) {
            val visualStage = costumeStages[visualIndex]
            costumeStages[visualIndex] = visualStage.copy(
                finalMatched = decision.matched,
                finalConfidence = decision.confidence.finiteOrNull(),
                heuristicPath = short(decision.heuristicPath),
                signatureDecisionPath = short(decision.signatureDecisionPath),
                heuristicCalled = decision.heuristicCalled,
                heuristicMatched = decision.heuristicResult?.first,
                heuristicConfidence = decision.heuristicResult?.second?.finiteOrNull(),
                strictShiny = decision.strictShiny,
                strictShinyReason = short(decision.strictShinyReason)
            ).bounded()
        }
        visual = visual.copy(
            costume = decision.matched,
            costumeConfidence = decision.confidence.finiteOrNull(),
            costumeSignaturePath = short(decision.signatureDecisionPath),
            costumeHeuristicPath = short(decision.heuristicPath),
            strictShiny = decision.strictShiny,
            strictShinyReason = short(decision.strictShinyReason)
        )
    }

    fun recordShinyChoice(
        matched: Boolean,
        confidence: Float,
        fallbackCalled: Boolean,
        fallbackReason: String,
        decisionPath: String
    ) = synchronized(lock) {
        visual = visual.copy(
            shiny = matched,
            shinyConfidence = confidence.finiteOrNull(),
            colorFallbackCalled = fallbackCalled,
            colorFallbackReason = short(fallbackReason),
            shinyDecisionPath = short(decisionPath)
        )
    }

    fun recordOverallVisualConfidence(confidence: Float) = synchronized(lock) {
        visual = visual.copy(overallConfidence = confidence.finiteOrNull())
    }

    fun recordPrototypeMatch(
        role: String,
        match: VariantPrototypeClassifier.MatchResult?,
        noMatchReason: String = "classifier_returned_no_match"
    ) = synchronized(lock) {
        if (prototypeStatuses.size < MAX_CANDIDATES) {
            prototypeStatuses += QaPrototypeStageStatus(
                role = short(role) ?: "unknown",
                resultAvailable = match != null,
                reasonCode = if (match == null) short(noMatchReason) else null
            )
        }
        if (match == null || prototypeMatches.size >= MAX_STAGE_RECORDS) return@synchronized
        prototypeMatches += QaPrototypeMatch(
            role = short(role) ?: "unknown",
            species = short(match.species) ?: "unknown",
            assetKey = short(match.assetKey) ?: "unknown",
            spriteKey = short(match.spriteKey) ?: "unknown",
            variantType = short(match.variantType) ?: "unknown",
            shiny = match.isShiny,
            costume = match.isCostumeLike,
            score = match.score.finiteOrZero(),
            confidence = match.confidence.finiteOrZero(),
            speciesMargin = match.speciesMargin.finiteOrZero(),
            variantMargin = match.variantMargin.finiteOrZero(),
            bestBaseScore = match.bestBaseScore.finiteOrNull(),
            bestBaseSpriteKey = short(match.bestBaseSpriteKey),
            bestNonBaseScore = match.bestNonBaseScore.finiteOrNull(),
            bestNonBaseSpriteKey = short(match.bestNonBaseSpriteKey),
            bestNonBaseType = short(match.bestNonBaseVariantType),
            bestShinyPeerScore = match.bestShinyPeerScore.finiteOrNull(),
            bestShinyPeerSpriteKey = short(match.bestShinyPeerSpriteKey),
            bestBaseShinyPeerScore = match.bestBaseShinyPeerScore.finiteOrNull(),
            bestBaseShinyPeerSpriteKey = short(match.bestBaseShinyPeerSpriteKey),
            rescueKind = short(match.rescueKind),
            topSpecies = match.topSpecies.take(MAX_CANDIDATES).mapNotNull(::short)
        )
    }

    fun recordFullVariant(match: FullVariantMatch?, candidates: List<FullVariantCandidate>) = synchronized(lock) {
        fullVariantMatcher = QaFullVariantStage(
            lockedSpecies = short(match?.finalSpecies),
            selectedSpecies = short(match?.winnerSpecies),
            selectedSpriteKey = short(match?.finalSpriteKey),
            selectedClass = short(match?.resolvedVariantClass),
            selectionStatus = when {
                match == null -> "matcher_returned_no_match"
                match.winnerSpecies == null -> "no_candidate_selected"
                else -> "candidate_selected"
            },
            shiny = match?.resolvedShiny,
            costume = match?.resolvedCostume,
            form = match?.resolvedForm,
            decisionConfidence = match?.variantConfidence?.finiteOrNull(),
            shinyConfidence = match?.shinyConfidence?.finiteOrNull(),
            explanationMode = short(match?.explanationMode),
            candidateCount = candidates.size.coerceIn(0, MAX_QA_CANDIDATE_COUNT),
            candidates = candidates.take(MAX_CANDIDATES).map(::candidateSnapshot)
        )
    }

    fun recordVariantMerge(decisions: List<QaMergeDecision>) = synchronized(lock) {
        variantMerge += decisions.take(MAX_CANDIDATES).map(QaMergeDecision::bounded)
        if (variantMerge.size > MAX_STAGE_RECORDS) {
            variantMerge.subList(MAX_STAGE_RECORDS, variantMerge.size).clear()
        }
    }

    fun recordPhase2Merge(decisions: List<QaPhase2Decision>) = synchronized(lock) {
        phase2Merge.clear()
        phase2Merge += decisions.take(MAX_CANDIDATES).map(QaPhase2Decision::bounded)
        phase2Status = if (decisions.isEmpty()) "merge_ran_no_predictions" else "merge_ran"
    }

    fun recordPhase2Status(status: String) = synchronized(lock) {
        phase2Status = short(status)
    }

    fun recordIdentity(identity: RecognitionIdentity) = synchronized(lock) {
        finalIdentity = QaIdentityStage(
            species = short(identity.canonicalSpecies),
            formStatus = short(identity.formStatus.name) ?: "UNKNOWN",
            knownForm = short(identity.knownForm),
            ambiguousForms = identity.ambiguousForms.take(MAX_CANDIDATES).mapNotNull(::short),
            shiny = short(identity.shiny.name) ?: "UNKNOWN",
            costume = short(identity.costume.name) ?: "UNKNOWN",
            formTruth = short(identity.specialForm.name) ?: "UNKNOWN"
        )
    }

    fun recordFailure(reasonCode: String) = synchronized(lock) {
        if (failures.size < MAX_STAGE_RECORDS) failures += short(reasonCode) ?: "unknown_failure"
    }

    fun snapshot(
        build: QaBuildIdentity,
        metadata: QaMetadataRevision
    ): QaVariantDiagnosticReport = synchronized(lock) {
        QaVariantDiagnosticReport(
            schemaVersion = SCHEMA_VERSION,
            scanId = scanId,
            capturedAtEpochMs = System.currentTimeMillis(),
            build = build,
            metadata = metadata,
            selectedFrame = selectedFrameReference
                ?: QaSelectedFrameReference(null, "sha256-prefix-96bit", "not_recorded"),
            lockedSpecies = lockedSpecies,
            detectorInputSpecies = detectorInputSpecies,
            visualDetector = visual,
            shinySignature = shinySignature,
            costumeSignatures = costumeStages.toList(),
            prototypeStageStatus = prototypeStatuses.toList(),
            prototypeClassifier = prototypeMatches.toList(),
            fullVariantMatcher = fullVariantMatcher,
            variantMerge = variantMerge.toList(),
            phase2Status = phase2Status,
            phase2Merge = phase2Merge.toList(),
            finalIdentity = finalIdentity,
            instrumentationFailures = failures.toList()
        )
    }

    private fun candidateSnapshot(candidate: FullVariantCandidate) = QaFullVariantCandidate(
        species = short(candidate.species) ?: "",
        spriteKey = short(candidate.spriteKey) ?: "",
        variantClass = short(candidate.variantClass) ?: "",
        shiny = candidate.isShiny,
        costume = candidate.isCostumeLike,
        source = short(candidate.source) ?: "",
        score = candidate.matchScore.finiteOrZero(),
        confidence = candidate.classifierConfidence.finiteOrZero(),
        rescueKind = short(candidate.rescueKind)
    )

    companion object {
        const val MAX_CANDIDATES = 8
        const val MAX_STAGE_RECORDS = 12
        const val SCHEMA_VERSION = 1
        const val VISUAL_COSTUME_SOURCE = "visual_detector_signature"
    }
}

internal object QaVariantDiagnosticPolicy {
    const val DIAGNOSTIC_APPLICATION_ID = "com.pokerarity.scanner.sprint6qa.diag1"

    fun enabled(isDebug: Boolean, qaFlag: Boolean, applicationId: String, sourceSha: String): Boolean =
        isDebug &&
            qaFlag &&
            applicationId == DIAGNOSTIC_APPLICATION_ID &&
            sourceSha.matches(Regex("[a-fA-F0-9]{40}"))
}

internal object QaVariantDiagnosticWriter {
    const val MAX_REPORT_BYTES = 32 * 1024
    const val MAX_REPORTS = 8
    private const val REPORT_DIRECTORY = "variant_diagnostics"
    private const val FRAME_DIGEST_PREFIX_LENGTH = 24
    private const val SHA256_BUFFER_SIZE_BYTES = 8_192
    private const val BYTE_MASK = 0xff
    private const val HEX_RADIX = 16
    private const val HEX_WIDTH = 2
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val metadataAssets = listOf(
        "shiny_signatures" to "data/shiny_signatures.json",
        "master_pokedex" to "data/master_pokedex.json",
        "variant_catalog" to "data/variant_catalog.json",
        "authoritative_variant_db" to "data/authoritative_variant_db.json",
        "costume_signatures" to "data/costume_signatures.json",
        "variant_prototypes" to "data/variant_classifier_model.json",
        "phase2_variant_model" to "data/variant_phase2_model.json",
        "legacy_rarity" to "data/global_rarity_legacy_db.json"
    )

    fun isEnabled(): Boolean = QaVariantDiagnosticPolicy.enabled(
        isDebug = BuildConfig.DEBUG,
        qaFlag = BuildConfig.QA_VARIANT_DIAGNOSTICS,
        applicationId = BuildConfig.APPLICATION_ID,
        sourceSha = BuildConfig.QA_SOURCE_SHA
    )

    fun serialize(report: QaVariantDiagnosticReport): String {
        val json = gson.toJson(report)
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES) {
            "bounded diagnostic report exceeded maximum size"
        }
        return json
    }

    fun write(context: Context, recorder: VariantStageDiagnosticRecorder): QaDiagnosticWriteResult {
        if (!isEnabled()) return QaDiagnosticWriteResult(null, "diagnostics_disabled")
        var phase = "directory"
        val result = try {
            val directory = File(context.filesDir, REPORT_DIRECTORY)
            check(directory.exists() || directory.mkdirs())
            val existing = directory.listFiles { file -> file.isFile && file.extension == "json" }.orEmpty()
            if (existing.size >= MAX_REPORTS) {
                QaDiagnosticWriteResult(null, "report_retention_cap_reached")
            } else {
                phase = "metadata_hash"
                val sourceSha = BuildConfig.QA_SOURCE_SHA
                val build = QaBuildIdentity(
                    applicationId = BuildConfig.APPLICATION_ID,
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    sourceSha = sourceSha
                )
                val metadata = metadataRevision(context)
                val report = recorder.snapshot(build, metadata)
                phase = "serialize"
                val json = serialize(report)
                val target = File(directory, "variant-${recorder.scanId}.json")
                val temporary = File(directory, "${target.name}.tmp")
                phase = "write"
                FileOutputStream(temporary).use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                phase = "rename"
                check(temporary.renameTo(target))
                QaDiagnosticWriteResult(target, null)
            }
        } catch (_: Exception) {
            QaDiagnosticWriteResult(null, "diagnostic_${phase}_failed")
        }
        return result
    }

    internal fun metadataRevision(context: Context): QaMetadataRevision {
        val assets = metadataAssets.map { (id, path) -> QaMetadataAsset(id, assetSha256(context, path)) }
        val material = assets.joinToString("|") { "${it.id}:${it.sha256.orEmpty()}" }
        return QaMetadataRevision(
            source = "qa_apk_bundled_assets",
            revisionSha256 = sha256(material.toByteArray(Charsets.UTF_8)),
            assets = assets
        )
    }

    internal fun selectedFrameReference(path: String): QaSelectedFrameReference {
        val file = File(path)
        val digest = runCatching { FileInputStream(file).use(::sha256) }.getOrNull()
        return QaSelectedFrameReference(
            ref = digest?.take(FRAME_DIGEST_PREFIX_LENGTH)?.let { "sha256:$it" },
            algorithm = "sha256-prefix-96bit",
            status = if (digest == null) "selected_frame_file_unavailable" else "available"
        )
    }

    private fun assetSha256(context: Context, path: String): String? = runCatching {
        context.assets.open(path).use(::sha256)
    }.getOrNull()

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(SHA256_BUFFER_SIZE_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_WIDTH, '0')
    }
}

private fun QaShinySignatureMatch.bounded() = copy(
    confidence = confidence.finiteOrZero(),
    normalScore = normalScore.finiteOrNull(),
    shinyScore = shinyScore.finiteOrNull(),
    normalColorScore = normalColorScore.finiteOrNull(),
    shinyColorScore = shinyColorScore.finiteOrNull(),
    scoreGap = scoreGap.finiteOrNull(),
    colorGap = colorGap.finiteOrNull(),
    histogramSource = short(histogramSource) ?: "unknown",
    reasonCode = short(reasonCode) ?: "unknown"
)

private fun QaShinySignatureStage.bounded() = copy(
    referenceStatus = short(referenceStatus) ?: "unknown",
    referenceKey = short(referenceKey),
    primary = primary?.bounded(),
    alternates = alternates.take(VariantStageDiagnosticRecorder.MAX_CANDIDATES).map(QaShinySignatureMatch::bounded),
    selectedSource = short(selectedSource) ?: "unknown",
    selectedConfidence = selectedConfidence.finiteOrZero(),
    rejectionReason = short(rejectionReason)
)

private fun QaCostumeStage.bounded() = copy(
    source = short(source) ?: "unknown",
    candidateCount = candidateCount.coerceIn(0, MAX_QA_CANDIDATE_COUNT),
    costumeCandidateCount = costumeCandidateCount.coerceIn(0, MAX_QA_CANDIDATE_COUNT),
    bestNormalKey = short(bestNormalKey),
    bestNormalScore = bestNormalScore.finiteOrNull(),
    bestCostumeKey = short(bestCostumeKey),
    bestCostumeScore = bestCostumeScore.finiteOrNull(),
    scoreGap = scoreGap.finiteOrNull(),
    confidence = confidence.finiteOrNull(),
    finalConfidence = finalConfidence.finiteOrNull(),
    rejectionReason = short(rejectionReason) ?: "unknown",
    heuristicPath = short(heuristicPath),
    signatureDecisionPath = short(signatureDecisionPath),
    heuristicConfidence = heuristicConfidence.finiteOrNull(),
    strictShinyReason = short(strictShinyReason)
)

private fun QaMergeDecision.bounded() = copy(
    stage = short(stage) ?: "unknown",
    target = short(target) ?: "unknown",
    action = short(action) ?: "unknown",
    reasonCode = short(reasonCode) ?: "unknown"
)

private fun QaPhase2Decision.bounded() = copy(
    confidence = confidence.finiteOrZero(),
    margin = margin.finiteOrZero(),
    positiveScore = positiveScore.finiteOrZero(),
    negativeScore = negativeScore.finiteOrZero(),
    source = short(source) ?: "unknown",
    action = short(action) ?: "unknown",
    reasonCode = short(reasonCode) ?: "unknown"
)

private fun Pair<Boolean, Float>.toQaBooleanConfidence() =
    QaBooleanConfidence(first, second.finiteOrZero())

private fun Float?.finiteOrNull(): Float? = this?.takeIf { it.isFinite() }

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

private fun short(value: String?): String? = value?.take(MAX_TEXT_CHARS)

private const val MAX_TEXT_CHARS = 96
