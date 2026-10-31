package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.ScanConfidenceGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceInput
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.ocr.VariantVisualSummary
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ScanRegressionTest {

    @Test
    fun regressionReportBoundsOpaqueStrictFailureSummaries() {
        val outcomes = List(MAX_STRICT_FAILURE_SAMPLES + 4) {
            RegressionOutcome(
                caseToken = "0123456789abcdef",
                strict = true,
                pass = false,
                failures = List(MAX_FAILURE_CODES_PER_CASE + 2) { "costume_mismatch" }
            )
        }

        val report = buildReport(outcomes)
        val samples = report.getJSONArray("strictFailureSamples")
        val first = samples.getJSONObject(0)

        assertEquals(MAX_STRICT_FAILURE_SAMPLES + 4, report.getInt("caseCount"))
        assertEquals(MAX_STRICT_FAILURE_SAMPLES, samples.length())
        assertTrue(report.getBoolean("strictFailureSamplesTruncated"))
        assertEquals(MAX_CASE_TOKEN_CHARS, first.getString("caseToken").length)
        assertEquals(MAX_FAILURE_CODES_PER_CASE, first.getJSONArray("failureCodes").length())
        assertTrue(report.toString().length <= MAX_REPORT_CHARS)
        assertFalse(report.has("rawOcr"))
        assertFalse(report.has("assetPath"))
        assertFalse(report.toString().contains("C:\\"))
    }

    @Test
    fun missingFixtureAssetReturnsDecodeFailureWithoutThrowingAssetPath() {
        val fixtureContext = InstrumentationRegistry.getInstrumentation().context

        assertNull(decodeFixtureBitmap(fixtureContext, "missing-regression-fixture.png"))
    }

    @Test
    fun runRegressionFixtures() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtureContext = instrumentation.context
        val appContext = instrumentation.targetContext

        val cases = loadCases(fixtureContext)
        require(cases.isNotEmpty()) { "No scan regression cases found" }

        val ocrProcessor = OCRProcessor(appContext)
        val rarityCalculator = RarityCalculator(appContext)
        val speciesRefiner = SpeciesRefiner(appContext, rarityCalculator)
        val visualDetector = VisualFeatureDetector(appContext)
        val variantDecisionEngine = VariantDecisionEngine(appContext)
        val phase2VariantClassifier = Phase2VariantClassifier(appContext)
        val scanConfidenceGate = ScanConfidenceGate()
        ocrProcessor.ensureInitialized()

        val outcomes = mutableListOf<RegressionOutcome>()
        try {
            for (case in cases) {
                outcomes += runCase(
                    fixtureContext = fixtureContext,
                    case = case,
                    ocrProcessor = ocrProcessor,
                    speciesRefiner = speciesRefiner,
                    variantDecisionEngine = variantDecisionEngine,
                    phase2VariantClassifier = phase2VariantClassifier,
                    visualDetector = visualDetector,
                    rarityCalculator = rarityCalculator,
                    scanConfidenceGate = scanConfidenceGate
                )
            }
        } finally {
            ocrProcessor.release()
        }

        writeReport(appContext, outcomes)

        val strictFailures = outcomes.filter { !it.pass && it.strict }
        if (strictFailures.isNotEmpty()) {
            val categories = strictFailures.flatMap { it.failures }.groupingBy { it }.eachCount()
            fail("Strict scan regression cases failed: ${categories.entries.joinToString { "${it.key}=${it.value}" }}")
        }
    }

    private suspend fun runCase(
        fixtureContext: Context,
        case: RegressionCase,
        ocrProcessor: OCRProcessor,
        speciesRefiner: SpeciesRefiner,
        variantDecisionEngine: VariantDecisionEngine,
        phase2VariantClassifier: Phase2VariantClassifier,
        visualDetector: VisualFeatureDetector,
        rarityCalculator: RarityCalculator,
        scanConfidenceGate: ScanConfidenceGate
    ): RegressionOutcome {
        val caseToken = opaqueCaseToken(case.id)
        val bitmap = decodeFixtureBitmap(fixtureContext, case.assetPath) ?: return RegressionOutcome(
            caseToken = caseToken,
            strict = case.strict,
            pass = false,
            failures = listOf("bitmap_decode_failed")
        )

        try {
            val ocrFrame = ocrProcessor.processImageWithDiagnostics(
                FrameOcrRequest(bitmap = bitmap, includeSecondaryFields = true)
            )
            val ocrData = ocrFrame.pokemon

            val refined = speciesRefiner.refine(ocrData, ocrFrame.diagnostic.fieldCandidates)
            val classified = variantDecisionEngine.classify(bitmap, refined)
            val classifiedPokemon = classified.pokemon
            val sizeTag = extractRawField(classifiedPokemon.rawOcrText, "SizeTag").ifBlank { null }
            val visualBase = visualDetector.detect(bitmap, classifiedPokemon.name, sizeTag)
            val visual = variantDecisionEngine.mergeVisualFeatures(
                applyOcrOverrides(classifiedPokemon, visualBase),
                classified.fullMatch,
                classified.resolvedMatch ?: classified.globalMatch
            )

            val cpCandidates = listOfNotNull(classifiedPokemon.cp)
            val fixedCp = rarityCalculator.validateAndFixCP(classifiedPokemon, cpCandidates, visual)
            val finalPokemon = if (fixedCp != null && fixedCp > 0 && fixedCp != classifiedPokemon.cp) {
                classifiedPokemon.copy(cp = fixedCp)
            } else {
                classifiedPokemon
            }
            val phase2Result = runCatching {
                phase2VariantClassifier.classify(bitmap, finalPokemon.realName ?: finalPokemon.name)
            }.getOrNull()
            val scoringVisual = Phase2VariantFeatureMerger.merge(visual, phase2Result)

            rarityCalculator.calculate(finalPokemon, scoringVisual)
            val speciesEvidence = ScanManager.deriveSpeciesEvidence(
                ocrFrame.diagnostic.fieldCandidates,
                finalPokemon,
                rarityCalculator
            )
            val scanDecision = scanConfidenceGate.evaluate(
                ScanConfidenceInput(
                    pokemon = finalPokemon,
                    frames = listOf(ocrFrame.diagnostic),
                    consistencyReason = "accepted",
                    visualSummary = VariantVisualSummary.from(scoringVisual, finalPokemon.variantDecisionTrace),
                    speciesEvidence = speciesEvidence
                )
            )

            val actual = ActualResult(
                species = finalPokemon.realName ?: finalPokemon.name,
                cp = finalPokemon.cp,
                hp = finalPokemon.hp,
                maxHp = finalPokemon.maxHp,
                shiny = scoringVisual.isShiny,
                lucky = scoringVisual.isLucky,
                costume = scoringVisual.hasCostume,
                locationCard = scoringVisual.hasLocationCard,
                datePresent = finalPokemon.caughtDate != null,
                screenType = ocrFrame.diagnostic.screenState,
                decision = scanDecision.decision.name,
                confidence = scanDecision.confidence,
                mayShowOverlay = scanDecision.mayShowOverlay,
                maySaveScan = scanDecision.maySaveScan
            )

            val failures = compare(case.expected, actual)
            return RegressionOutcome(
                caseToken = caseToken,
                strict = case.strict,
                pass = failures.isEmpty(),
                failures = failures
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun applyOcrOverrides(pokemon: PokemonData, visual: VisualFeatures): VisualFeatures {
        val ocrLucky = extractRawField(pokemon.rawOcrText, "LuckyDetected").equals("true", ignoreCase = true)
        return if (ocrLucky && !visual.isLucky) {
            visual.copy(
                isLucky = true,
                hasLocationCard = false,
                confidence = maxOf(visual.confidence, 0.75f)
            )
        } else {
            visual
        }
    }

    private fun compare(expected: ExpectedResult, actual: ActualResult): List<String> {
        val failures = mutableListOf<String>()
        expected.species?.let {
            if (!it.equals(actual.species, ignoreCase = true)) failures += "species_mismatch"
        }
        expected.cp?.let {
            if (it != actual.cp) failures += "cp_mismatch"
        }
        expected.hp?.let {
            if (it != actual.hp) failures += "hp_mismatch"
        }
        expected.maxHp?.let {
            if (it != actual.maxHp) failures += "max_hp_mismatch"
        }
        expected.shiny?.let {
            if (it != actual.shiny) failures += "shiny_mismatch"
        }
        expected.lucky?.let {
            if (it != actual.lucky) failures += "lucky_mismatch"
        }
        expected.costume?.let {
            if (it != actual.costume) failures += "costume_mismatch"
        }
        expected.locationCard?.let {
            if (it != actual.locationCard) failures += "location_card_mismatch"
        }
        expected.datePresent?.let {
            if (it != actual.datePresent) failures += "date_presence_mismatch"
        }
        expected.screenType?.let {
            if (!it.equals(actual.screenType, ignoreCase = true)) failures += "screen_type_mismatch"
        }
        expected.decision?.let {
            if (!it.equals(actual.decision, ignoreCase = true)) failures += "decision_mismatch"
        }
        expected.minConfidence?.let {
            if (actual.confidence < it) failures += "confidence_below_minimum"
        }
        expected.mayShowOverlay?.let {
            if (it != actual.mayShowOverlay) failures += "overlay_eligibility_mismatch"
        }
        expected.maySaveScan?.let {
            if (it != actual.maySaveScan) failures += "save_eligibility_mismatch"
        }
        return failures
    }

    private fun loadCases(context: Context): List<RegressionCase> {
        val raw = context.assets.open("scan_regression_cases.json").bufferedReader().use { it.readText() }
        val json = JSONArray(raw)
        return buildList {
            for (index in 0 until json.length()) {
                val obj = json.getJSONObject(index)
                val expectedObj = obj.optJSONObject("expected") ?: JSONObject()
                add(
                    RegressionCase(
                        id = obj.getString("id"),
                        assetPath = obj.getString("assetPath"),
                        strict = obj.optBoolean("strict", false),
                        expected = ExpectedResult(
                            species = expectedObj.optNullableString("species"),
                            cp = expectedObj.optNullableInt("cp"),
                            hp = expectedObj.optNullableInt("hp"),
                            maxHp = expectedObj.optNullableInt("maxHp"),
                            shiny = expectedObj.optNullableBoolean("shiny"),
                            lucky = expectedObj.optNullableBoolean("lucky"),
                            costume = expectedObj.optNullableBoolean("costume"),
                            locationCard = expectedObj.optNullableBoolean("locationCard"),
                            datePresent = expectedObj.optNullableBoolean("datePresent"),
                            screenType = expectedObj.optNullableString("screenType"),
                            decision = expectedObj.optNullableString("decision")
                                ?: expectedObj.optNullableString("confidenceDecision"),
                            minConfidence = expectedObj.optNullableFloat("minConfidence")
                                ?: expectedObj.optNullableFloat("expectedMinConfidence"),
                            mayShowOverlay = expectedObj.optNullableBoolean("mayShowOverlay")
                                ?: expectedObj.optNullableBoolean("expectedMayShowOverlay"),
                            maySaveScan = expectedObj.optNullableBoolean("maySaveScan")
                                ?: expectedObj.optNullableBoolean("expectedMaySaveScan")
                        )
                    )
                )
            }
        }
    }

    private fun writeReport(context: Context, outcomes: List<RegressionOutcome>) {
        val reportText = buildReport(outcomes).toString(2)

        val cacheReportFile = File(context.cacheDir, "scan_regression_report.json")
        cacheReportFile.writeText(reportText)

        val externalReportFile = context.getExternalFilesDir(null)?.let {
            File(it, "scan_regression_report.json").apply { writeText(reportText) }
        }
        check(cacheReportFile.exists() || externalReportFile?.exists() == true) { "Regression summary was not written" }
    }

    private fun buildReport(outcomes: List<RegressionOutcome>): JSONObject {
        val strictOutcomes = outcomes.filter { it.strict }
        val strictFailureSamples = strictOutcomes.filter { !it.pass }
        return JSONObject()
            .put("generatedAtMs", System.currentTimeMillis())
            .put("caseCount", outcomes.size)
            .put("passedCount", outcomes.count { it.pass })
            .put("failedCount", outcomes.count { !it.pass })
            .put("strictCaseCount", strictOutcomes.size)
            .put("strictFailedCount", strictOutcomes.count { !it.pass })
            .put("strictFailureSamplesTruncated", strictFailureSamples.size > MAX_STRICT_FAILURE_SAMPLES)
            .put("strictFailureSamples", JSONArray().apply {
                strictFailureSamples.take(MAX_STRICT_FAILURE_SAMPLES).forEach { outcome ->
                    put(
                        JSONObject()
                            .put("caseToken", outcome.caseToken.take(MAX_CASE_TOKEN_CHARS))
                            .put("failureCodes", JSONArray().apply {
                                outcome.failures.take(MAX_FAILURE_CODES_PER_CASE).forEach { code ->
                                    put(code.take(MAX_FAILURE_CODE_CHARS))
                                }
                            })
                    )
                }
            })
            .put("failureCategories", JSONObject().apply {
                outcomes.flatMap { it.failures }.groupingBy { it }.eachCount().toSortedMap().forEach { (category, count) ->
                    put(category.take(MAX_FAILURE_CODE_CHARS), count)
                }
            })
    }

    private fun opaqueCaseToken(caseId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(caseId.toByteArray(Charsets.UTF_8))
        .take(MAX_CASE_TOKEN_CHARS / 2)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun decodeFixtureBitmap(context: Context, assetPath: String): Bitmap? {
        val bytes = try {
            context.assets.open(assetPath).use { it.readBytes() }
        } catch (_: java.io.IOException) {
            return null
        }
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun extractRawField(rawOcrText: String, key: String): String {
        return rawOcrText.split("|")
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optNullableBoolean(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return optBoolean(key)
    }

    private fun JSONObject.optNullableFloat(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).toFloat()
    }

    private data class RegressionCase(
        val id: String,
        val assetPath: String,
        val strict: Boolean,
        val expected: ExpectedResult
    )

    private data class ExpectedResult(
        val species: String?,
        val cp: Int?,
        val hp: Int?,
        val maxHp: Int?,
        val shiny: Boolean?,
        val lucky: Boolean?,
        val costume: Boolean?,
        val locationCard: Boolean?,
        val datePresent: Boolean?,
        val screenType: String?,
        val decision: String?,
        val minConfidence: Float?,
        val mayShowOverlay: Boolean?,
        val maySaveScan: Boolean?
    )

    private data class ActualResult(
        val species: String?,
        val cp: Int?,
        val hp: Int?,
        val maxHp: Int?,
        val shiny: Boolean,
        val lucky: Boolean,
        val costume: Boolean,
        val locationCard: Boolean,
        val datePresent: Boolean,
        val screenType: String,
        val decision: String,
        val confidence: Float,
        val mayShowOverlay: Boolean,
        val maySaveScan: Boolean
    )

    private data class RegressionOutcome(
        val caseToken: String,
        val strict: Boolean,
        val pass: Boolean,
        val failures: List<String>
    )

    private companion object {
        const val MAX_STRICT_FAILURE_SAMPLES = 16
        const val MAX_FAILURE_CODES_PER_CASE = 8
        const val MAX_CASE_TOKEN_CHARS = 16
        const val MAX_FAILURE_CODE_CHARS = 48
        const val MAX_REPORT_CHARS = 12_000
    }
}
