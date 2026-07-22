package com.pokerarity.scanner.util.vision

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class Phase2SlotAdequacyReportTest {

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    @Test
    fun reportMatchesCheckedInExpectedAndWritesActual() {
        val (report, jsonText) = generateReportAndJson()
        assertNotNull(report)

        val repoRoot = findRepoRoot()
        val actualFile = File(repoRoot, ACTUAL_REPORT_PATH).apply {
            parentFile?.mkdirs()
            writeText(jsonText, Charsets.UTF_8)
        }

        val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        val expectedStream = classLoader?.getResourceAsStream(EXPECTED_RESOURCE_NAME)
        if (expectedStream == null) {
            fail("Missing $EXPECTED_RESOURCE_NAME; actual report written to: ${actualFile.absolutePath}")
            return
        }

        val expectedText = expectedStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .replace("\r\n", "\n")

        assertEquals(
            "Actual generated report does not match checked-in expected file ($EXPECTED_RESOURCE_NAME). " +
                "Actual report: ${actualFile.absolutePath}",
            expectedText,
            jsonText
        )
    }

    @Test
    fun repeatedGenerationIsByteIdentical() {
        val (_, firstJson) = generateReportAndJson()
        val (_, secondJson) = generateReportAndJson()

        assertEquals(firstJson, secondJson)
        val firstHash = sha256Hex(firstJson.toByteArray(Charsets.UTF_8))
        val secondHash = sha256Hex(secondJson.toByteArray(Charsets.UTF_8))
        assertEquals(firstHash, secondHash)
    }

    @Test
    fun sourceModelHashIsStableAcrossLfAndCrlfCheckouts() {
        val rawBytes = File(findRepoRoot(), SOURCE_ASSET_PATH).readBytes()
        val lfText = rawBytes.toString(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        val crlfText = lfText.replace("\n", "\r\n")

        val canonicalLfBytes = canonicalRepositoryTextBytes(lfText.toByteArray(Charsets.UTF_8))
        val canonicalCrlfBytes = canonicalRepositoryTextBytes(crlfText.toByteArray(Charsets.UTF_8))

        assertTrue(canonicalLfBytes.contentEquals(canonicalCrlfBytes))
        assertEquals(lfText, canonicalLfBytes.toString(Charsets.UTF_8))
        assertEquals(lfText, canonicalCrlfBytes.toString(Charsets.UTF_8))
        assertEquals(CANONICAL_SOURCE_MODEL_SHA256, sha256Hex(canonicalLfBytes))
        assertEquals(CANONICAL_SOURCE_MODEL_SHA256, sha256Hex(canonicalCrlfBytes))

        val (_, firstJson) = generateReportAndJson()
        val (_, secondJson) = generateReportAndJson()
        assertEquals(firstJson, secondJson)
    }

    @Test
    fun summaryMatchesRecordedPointInTimeMeasurements() {
        val (report, _) = generateReportAndJson()
        val summary = report.summary

        assertEquals(43, summary.speciesModelCount)
        assertEquals(43, summary.supportedSpeciesEntryCount)
        assertEquals(162, summary.totalSlots)
        assertEquals(100, summary.rawZeroPositiveSlots)
        assertEquals(35, summary.rawZeroNegativeSlots)
        assertEquals(141, summary.rawBelowMinimumCombinedSamplesSlots)

        assertEquals(summary.totalSlots, summary.decisionCapableSlots + summary.diagnosticsOnlySlots)
        assertEquals(summary.totalSlots, summary.reasonCounts.sumOf { it.count })
        assertEquals(summary.totalSlots, report.slots.size)
    }

    @Test
    fun everySlotUsesProductionCapabilityReason() {
        val (report, _) = generateReportAndJson()

        report.slots.forEach { slot ->
            val cap = Phase2VariantClassifier.evaluateCapability(
                target = slot.target,
                source = slot.source,
                supported = slot.supported,
                positiveCount = slot.positiveCount,
                negativeCount = slot.negativeCount
            )
            val msg = "for ${slot.species}/${slot.target}"
            assertEquals("Reason mismatch $msg", cap.reason.code, slot.reason)
            assertEquals("decisionCapable mismatch $msg", cap.decisionCapable, slot.decisionCapable)
            assertEquals("combinedCount mismatch $msg", cap.combinedCount, slot.combinedCount)
            assertEquals("supported mismatch $msg", cap.supported, slot.supported)
            assertEquals("positiveCount mismatch $msg", cap.positiveCount, slot.positiveCount)
            assertEquals("negativeCount mismatch $msg", cap.negativeCount, slot.negativeCount)
        }
    }

    @Test
    fun reportCapturesCurrentThresholdsWithoutLoosening() {
        val (report, _) = generateReportAndJson()
        val snapshot = report.thresholdSnapshot

        assertEquals(0.9f, snapshot.defaultMinConfidence, 1e-5f)
        assertEquals(0.2f, snapshot.defaultMinMargin, 1e-5f)
        assertFalse(snapshot.defaultRequirePositivePrediction)

        assertEquals(4, snapshot.targets.size)

        val costume = snapshot.targets.single { it.target == "hasCostume" }
        assertEquals(0.5f, costume.minConfidence, 1e-5f)
        assertEquals(0.001f, costume.minMargin, 1e-5f)
        assertTrue(costume.requirePositivePrediction)

        val location = snapshot.targets.single { it.target == "hasLocationCard" }
        assertEquals(0.8f, location.minConfidence, 1e-5f)
        assertEquals(0.2f, location.minMargin, 1e-5f)
        assertTrue(location.requirePositivePrediction)

        val form = snapshot.targets.single { it.target == "hasSpecialForm" }
        assertEquals(0.7f, form.minConfidence, 1e-5f)
        assertEquals(0.18f, form.minMargin, 1e-5f)
        assertTrue(form.requirePositivePrediction)

        val shiny = snapshot.targets.single { it.target == "isShiny" }
        assertEquals(0.502f, shiny.minConfidence, 1e-5f)
        assertEquals(0.003f, shiny.minMargin, 1e-5f)
        assertTrue(shiny.requirePositivePrediction)
    }

    @Test
    fun partialTargetOverrideInheritsGlobalThresholdDefaults() {
        val snapshot = buildThresholdSnapshot(
            ModelThresholds(
                minConfidence = 0.84f,
                minMargin = 0.17f,
                requirePositivePrediction = true,
                targetThresholds = linkedMapOf(
                    "zTarget" to TargetThreshold(),
                    "aTarget" to TargetThreshold(minConfidence = 0.91f)
                )
            )
        )

        assertEquals(0.84f, snapshot.defaultMinConfidence, 1e-5f)
        assertEquals(0.17f, snapshot.defaultMinMargin, 1e-5f)
        assertTrue(snapshot.defaultRequirePositivePrediction)
        assertEquals(listOf("aTarget", "zTarget"), snapshot.targets.map { it.target })

        val partialOverride = snapshot.targets.first()
        assertEquals(0.91f, partialOverride.minConfidence, 1e-5f)
        assertEquals(0.17f, partialOverride.minMargin, 1e-5f)
        assertTrue(partialOverride.requirePositivePrediction)
    }

    @Test
    fun outputIsPortableAndPrivacySafe() {
        val (report, jsonText) = generateReportAndJson()

        assertFalse(jsonText.contains("\r"))
        assertTrue(jsonText.endsWith("\n"))
        assertFalse(jsonText.endsWith("\n\n"))

        val forbiddenSubstrings = listOf(
            "C:\\",
            "/Users/",
            "/home/",
            "screenshotPath",
            "rawOcr",
            "positivePrototype",
            "negativePrototype"
        )
        forbiddenSubstrings.forEach { substring ->
            assertFalse("JSON contains forbidden substring: $substring", jsonText.contains(substring))
        }

        assertEquals(SOURCE_ASSET_PATH, report.sourceAsset)
    }

    private fun generateReportAndJson(): Pair<ReportOutput, String> {
        val repoRoot = findRepoRoot()
        val modelFile = File(repoRoot, SOURCE_ASSET_PATH)
        require(modelFile.isFile) { "Model file not found at ${modelFile.absolutePath}" }

        val rawBytes = modelFile.readBytes()
        val canonicalSourceBytes = canonicalRepositoryTextBytes(rawBytes)
        val sha256 = sha256Hex(canonicalSourceBytes)
        val payload = gson.fromJson(canonicalSourceBytes.toString(Charsets.UTF_8), ModelPayload::class.java)

        val speciesModels = payload.speciesModels.orEmpty()
        val supportedSpeciesMap = payload.supportedSpecies.orEmpty()

        val (slots, rawCounts) = buildSlotsAndRawCounts(speciesModels)

        val reasonCounts = Phase2VariantClassifier.TargetCapabilityReason.values().map { reason ->
            ReasonCountReport(
                reason = reason.code,
                count = slots.count { it.reason == reason.code }
            )
        }

        val decisionCapableCount = slots.count { it.decisionCapable }
        val diagnosticsOnlyCount = slots.size - decisionCapableCount
        val thresholdSnapshot = buildThresholdSnapshot(payload.appThresholds)

        val summary = SummaryReport(
            speciesModelCount = speciesModels.size,
            supportedSpeciesEntryCount = supportedSpeciesMap.size,
            totalSlots = slots.size,
            decisionCapableSlots = decisionCapableCount,
            diagnosticsOnlySlots = diagnosticsOnlyCount,
            rawZeroPositiveSlots = rawCounts.zeroPositive,
            rawZeroNegativeSlots = rawCounts.zeroNegative,
            rawBelowMinimumCombinedSamplesSlots = rawCounts.belowMinimum,
            reasonCounts = reasonCounts
        )

        val report = ReportOutput(
            schemaVersion = 1,
            sourceAsset = SOURCE_ASSET_PATH,
            sourceModelSha256 = sha256,
            modelType = payload.modelType ?: "species_conditioned_variant_prototype_v2",
            modelGeneratedAt = payload.generatedAt ?: "",
            minimumCombinedSamples = Phase2VariantClassifier.MIN_COMBINED_SAMPLES,
            thresholdSnapshot = thresholdSnapshot,
            summary = summary,
            slots = slots
        )

        val rawJson = gson.toJson(report)
        val formattedJson = rawJson.replace("\r\n", "\n").trimEnd() + "\n"

        return Pair(report, formattedJson)
    }

    private fun buildSlotsAndRawCounts(
        speciesModels: Map<String, SpeciesModel>
    ): Pair<List<SlotReport>, RawCounts> {
        var rawZeroPositive = 0
        var rawZeroNegative = 0
        var rawBelowMinimumCombined = 0

        val slots = buildList {
            speciesModels.forEach { (speciesName, speciesModel) ->
                val source = if (speciesName == GLOBAL_MODEL_SPECIES) "global" else "species"
                speciesModel.targets.orEmpty().forEach { (targetName, targetModel) ->
                    val pos = targetModel.positiveCount
                    val neg = targetModel.negativeCount

                    if (pos == 0) rawZeroPositive++
                    if (neg == 0) rawZeroNegative++
                    if (isBelowMinimum(pos, neg)) rawBelowMinimumCombined++

                    val cap = Phase2VariantClassifier.evaluateCapability(
                        target = targetName,
                        source = source,
                        supported = targetModel.supported,
                        positiveCount = pos,
                        negativeCount = neg
                    )

                    add(
                        SlotReport(
                            species = speciesName,
                            target = targetName,
                            source = source,
                            supported = cap.supported,
                            positiveCount = cap.positiveCount,
                            negativeCount = cap.negativeCount,
                            combinedCount = cap.combinedCount,
                            reason = cap.reason.code,
                            decisionCapable = cap.decisionCapable
                        )
                    )
                }
            }
        }.sortedWith(compareBy<SlotReport> { it.species }.thenBy { it.target }.thenBy { it.source })

        return Pair(
            slots,
            RawCounts(
                zeroPositive = rawZeroPositive,
                zeroNegative = rawZeroNegative,
                belowMinimum = rawBelowMinimumCombined
            )
        )
    }

    private fun isBelowMinimum(pos: Int?, neg: Int?): Boolean {
        if (pos == null || neg == null) return false
        val combined = pos + neg
        return pos >= 0 && neg >= 0 && combined < Phase2VariantClassifier.MIN_COMBINED_SAMPLES
    }

    private fun canonicalRepositoryTextBytes(rawBytes: ByteArray): ByteArray =
        rawBytes.toString(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .toByteArray(Charsets.UTF_8)

    private fun buildThresholdSnapshot(thresholds: ModelThresholds?): ThresholdSnapshotReport {
        val config = thresholds ?: ModelThresholds()
        val defaultMinConfidence = config.minConfidence ?: 0.9f
        val defaultMinMargin = config.minMargin ?: 0.2f
        val defaultRequirePositive = config.requirePositivePrediction ?: false
        val targetThresholdsList = config.targetThresholds.orEmpty()
            .map { (targetName, targetThresh) ->
                TargetThresholdReport(
                    target = targetName,
                    minConfidence = targetThresh.minConfidence ?: defaultMinConfidence,
                    minMargin = targetThresh.minMargin ?: defaultMinMargin,
                    requirePositivePrediction =
                        targetThresh.requirePositivePrediction ?: defaultRequirePositive
                )
            }.sortedBy { it.target }

        return ThresholdSnapshotReport(
            defaultMinConfidence = defaultMinConfidence,
            defaultMinMargin = defaultMinMargin,
            defaultRequirePositivePrediction = defaultRequirePositive,
            targets = targetThresholdsList
        )
    }

    companion object {
        private const val GLOBAL_MODEL_SPECIES = "__GLOBAL__"
        private const val SOURCE_ASSET_PATH = "app/src/main/assets/data/variant_phase2_model.json"
        private const val ACTUAL_REPORT_PATH = "app/build/reports/phase2/phase2_slot_adequacy_actual.json"
        private const val EXPECTED_RESOURCE_NAME = "phase2_slot_adequacy_expected.json"
        private const val CANONICAL_SOURCE_MODEL_SHA256 =
            "34cc755d6197a7e3958b45f8fb754eb0348f163101ff4ac926599b64598020aa"

        private fun findRepoRoot(): File {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(6) {
                if (File(dir, SOURCE_ASSET_PATH).isFile) return dir
                dir = dir.parentFile ?: return@repeat
            }
            error("Repository root containing $SOURCE_ASSET_PATH was not found")
        }

        private fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            return hash.joinToString("") { "%02x".format(it) }
        }
    }

    private data class RawCounts(
        val zeroPositive: Int,
        val zeroNegative: Int,
        val belowMinimum: Int
    )

    private data class ModelPayload(
        val modelType: String? = null,
        val generatedAt: String? = null,
        val appThresholds: ModelThresholds? = null,
        val supportedSpecies: Map<String, List<String>>? = null,
        val speciesModels: Map<String, SpeciesModel>? = null
    )

    private data class ModelThresholds(
        val minConfidence: Float? = null,
        val minMargin: Float? = null,
        val requirePositivePrediction: Boolean? = null,
        val targetThresholds: Map<String, TargetThreshold>? = null
    )

    private data class TargetThreshold(
        val minConfidence: Float? = null,
        val minMargin: Float? = null,
        val requirePositivePrediction: Boolean? = null
    )

    private data class SpeciesModel(
        val targets: Map<String, TargetModel>? = null
    )

    private data class TargetModel(
        val supported: Boolean? = null,
        val positiveCount: Int? = null,
        val negativeCount: Int? = null
    )

    private data class ReportOutput(
        val schemaVersion: Int = 1,
        val sourceAsset: String,
        val sourceModelSha256: String,
        val modelType: String,
        val modelGeneratedAt: String,
        val minimumCombinedSamples: Int,
        val thresholdSnapshot: ThresholdSnapshotReport,
        val summary: SummaryReport,
        val slots: List<SlotReport>
    )

    private data class ThresholdSnapshotReport(
        val defaultMinConfidence: Float,
        val defaultMinMargin: Float,
        val defaultRequirePositivePrediction: Boolean,
        val targets: List<TargetThresholdReport>
    )

    private data class TargetThresholdReport(
        val target: String,
        val minConfidence: Float,
        val minMargin: Float,
        val requirePositivePrediction: Boolean
    )

    private data class SummaryReport(
        val speciesModelCount: Int,
        val supportedSpeciesEntryCount: Int,
        val totalSlots: Int,
        val decisionCapableSlots: Int,
        val diagnosticsOnlySlots: Int,
        val rawZeroPositiveSlots: Int,
        val rawZeroNegativeSlots: Int,
        val rawBelowMinimumCombinedSamplesSlots: Int,
        val reasonCounts: List<ReasonCountReport>
    )

    private data class ReasonCountReport(
        val reason: String,
        val count: Int
    )

    private data class SlotReport(
        val species: String,
        val target: String,
        val source: String,
        val supported: Boolean?,
        val positiveCount: Int?,
        val negativeCount: Int?,
        val combinedCount: Int?,
        val reason: String,
        val decisionCapable: Boolean
    )
}
