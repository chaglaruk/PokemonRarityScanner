package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanManager
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
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScanRegressionTest {

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
            fail(
                buildString {
                    appendLine("Strict scan regression failures:")
                    strictFailures.forEach { outcome ->
                        appendLine("- ${outcome.id}: ${outcome.failures.joinToString("; ")}")
                    }
                }
            )
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
        val bitmap = decodeFixtureBitmap(fixtureContext, case.assetPath) ?: return RegressionOutcome(
            id = case.id,
            strict = case.strict,
            pass = false,
            failures = listOf("Bitmap decode failed: ${case.assetPath}"),
            summary = JSONObject().put("assetPath", case.assetPath)
        )

        try {
            val ocrStart = SystemClock.elapsedRealtime()
            val ocrFrame = ocrProcessor.processImageWithDiagnostics(bitmap, includeSecondaryFields = true)
            val ocrData = ocrFrame.pokemon
            val ocrMs = SystemClock.elapsedRealtime() - ocrStart

            val refined = speciesRefiner.refine(ocrData, ocrFrame.diagnostic.fieldCandidates)
            val classified = variantDecisionEngine.classify(bitmap, refined)
            val classifiedPokemon = classified.pokemon
            val sizeTag = extractRawField(classifiedPokemon.rawOcrText, "SizeTag").ifBlank { null }
            val visualStart = SystemClock.elapsedRealtime()
            val visualBase = visualDetector.detect(bitmap, classifiedPokemon.name, sizeTag)
            val visualMs = SystemClock.elapsedRealtime() - visualStart
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

            val rarityStart = SystemClock.elapsedRealtime()
            val rarity = rarityCalculator.calculate(finalPokemon, scoringVisual)
            val rarityMs = SystemClock.elapsedRealtime() - rarityStart
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
                ivText = rarity.ivEstimate,
                datePresent = finalPokemon.caughtDate != null,
                screenType = ocrFrame.diagnostic.screenState,
                decision = scanDecision.decision.name,
                confidence = scanDecision.confidence,
                mayShowOverlay = scanDecision.mayShowOverlay,
                maySaveScan = scanDecision.maySaveScan
            )

            val failures = compare(case.expected, actual)
            val summary = JSONObject()
                .put("assetPath", case.assetPath)
                .put("species", actual.species)
                .put("cp", actual.cp)
                .put("hp", actual.hp)
                .put("maxHp", actual.maxHp)
                .put("shiny", actual.shiny)
                .put("lucky", actual.lucky)
                .put("costume", actual.costume)
                .put("locationCard", actual.locationCard)
                .put("ivText", actual.ivText ?: JSONObject.NULL)
                .put("datePresent", actual.datePresent)
                .put("screenType", actual.screenType)
                .put("decision", actual.decision)
                .put("confidence", actual.confidence.toDouble())
                .put("mayShowOverlay", actual.mayShowOverlay)
                .put("maySaveScan", actual.maySaveScan)
                .put("ocrMs", ocrMs)
                .put("visualMs", visualMs)
                .put("phase2AppliedTargets", JSONArray(phase2Result?.appliedTargets.orEmpty()))
                .put("phase2SupportedTargets", JSONArray(phase2Result?.supportedTargets.orEmpty()))
                .put("rarityMs", rarityMs)
                .put("rawOcrText", finalPokemon.rawOcrText)
                .put("rarityBreakdown", JSONObject(rarity.breakdown))

            Log.i(
                "ScanRegressionTest",
                "Case ${case.id}: pass=${failures.isEmpty()} species=${actual.species} cp=${actual.cp} hp=${actual.hp}/${actual.maxHp} shiny=${actual.shiny} lucky=${actual.lucky} costume=${actual.costume} datePresent=${actual.datePresent} phase2Applied=${phase2Result?.appliedTargets.orEmpty()} ocrMs=$ocrMs visualMs=$visualMs rarityMs=$rarityMs"
            )
            if (failures.isNotEmpty()) {
                Log.w("ScanRegressionTest", "Case ${case.id} failures: ${failures.joinToString("; ")}")
            }

            return RegressionOutcome(
                id = case.id,
                strict = case.strict,
                pass = failures.isEmpty(),
                failures = failures,
                summary = summary
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
            if (!it.equals(actual.species, ignoreCase = true)) failures += "species expected=$it actual=${actual.species}"
        }
        expected.cp?.let {
            if (it != actual.cp) failures += "cp expected=$it actual=${actual.cp}"
        }
        expected.hp?.let {
            if (it != actual.hp) failures += "hp expected=$it actual=${actual.hp}"
        }
        expected.maxHp?.let {
            if (it != actual.maxHp) failures += "maxHp expected=$it actual=${actual.maxHp}"
        }
        expected.shiny?.let {
            if (it != actual.shiny) failures += "shiny expected=$it actual=${actual.shiny}"
        }
        expected.lucky?.let {
            if (it != actual.lucky) failures += "lucky expected=$it actual=${actual.lucky}"
        }
        expected.costume?.let {
            if (it != actual.costume) failures += "costume expected=$it actual=${actual.costume}"
        }
        expected.locationCard?.let {
            if (it != actual.locationCard) failures += "locationCard expected=$it actual=${actual.locationCard}"
        }
        expected.datePresent?.let {
            if (it != actual.datePresent) failures += "datePresent expected=$it actual=${actual.datePresent}"
        }
        expected.screenType?.let {
            if (!it.equals(actual.screenType, ignoreCase = true)) failures += "screenType expected=$it actual=${actual.screenType}"
        }
        expected.decision?.let {
            if (!it.equals(actual.decision, ignoreCase = true)) failures += "decision expected=$it actual=${actual.decision}"
        }
        expected.minConfidence?.let {
            if (actual.confidence < it) failures += "confidence expected>=$it actual=${actual.confidence}"
        }
        expected.mayShowOverlay?.let {
            if (it != actual.mayShowOverlay) failures += "mayShowOverlay expected=$it actual=${actual.mayShowOverlay}"
        }
        expected.maySaveScan?.let {
            if (it != actual.maySaveScan) failures += "maySaveScan expected=$it actual=${actual.maySaveScan}"
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
                        notes = obj.optString("notes"),
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
        val root = JSONObject()
            .put("generatedAtMs", System.currentTimeMillis())
            .put("cases", JSONArray().apply {
                outcomes.forEach { outcome ->
                    put(
                        JSONObject()
                            .put("id", outcome.id)
                            .put("strict", outcome.strict)
                            .put("pass", outcome.pass)
                            .put("failures", JSONArray(outcome.failures))
                            .put("summary", outcome.summary)
                    )
                }
            })
        val reportText = root.toString(2)

        val cacheReportFile = File(context.cacheDir, "scan_regression_report.json")
        cacheReportFile.writeText(reportText)

        val externalReportFile = context.getExternalFilesDir(null)?.let {
            File(it, "scan_regression_report.json").apply { writeText(reportText) }
        }

        Log.d(
            "ScanRegressionTest",
            "Regression report written: cache=${cacheReportFile.absolutePath}, external=${externalReportFile?.absolutePath}"
        )
        Log.i("ScanRegressionTest", "Regression report payload: $reportText")
    }

    private fun decodeFixtureBitmap(context: Context, assetPath: String): Bitmap? {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
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
        val notes: String,
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
        val ivText: String?,
        val datePresent: Boolean,
        val screenType: String,
        val decision: String,
        val confidence: Float,
        val mayShowOverlay: Boolean,
        val maySaveScan: Boolean
    )

    private data class RegressionOutcome(
        val id: String,
        val strict: Boolean,
        val pass: Boolean,
        val failures: List<String>,
        val summary: JSONObject
    )
}
