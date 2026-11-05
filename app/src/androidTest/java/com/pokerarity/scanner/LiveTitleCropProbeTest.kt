package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.ExtractionContext
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.SpeciesNameDecision
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.acceptedSpeciesOrNull
import com.pokerarity.scanner.util.ocr.status
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QA-only, argument-gated replay probe for one locally staged title crop. Reports only
 * bounded geometry, symbol presence and parser decisions; it never writes OCR text.
 */
@RunWith(AndroidJUnit4::class)
class LiveTitleCropProbeTest {

    @Test
    fun reportsSanitizedOcrStructureForStagedCrop() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fileName = InstrumentationRegistry.getArguments().getString(INPUT_FILE_ARGUMENT)
        assumeTrue("Supply a single staged PNG name", !fileName.isNullOrBlank())
        assumeTrue("Input must be a bounded filename", File(fileName!!).name == fileName)
        val externalFiles = context.getExternalFilesDir(null)
            ?: error("External files storage is unavailable")
        val input = File(externalFiles, fileName)
        assumeTrue("Staged title crop is missing", input.isFile && input.length() in 1..MAX_INPUT_BYTES)

        val bitmap = BitmapFactory.decodeFile(input.absolutePath)
            ?: error("Staged title crop could not be decoded")
        bitmap.useBitmap { source ->
            val provider = MLKitOcrProvider(context)
            try {
                val started = android.os.SystemClock.elapsedRealtime()
                val layout = provider.recognizeLayout(source)
                val ocrMs = android.os.SystemClock.elapsedRealtime() - started
                val parser = TextParser(context)
                val titleLines = layout.lines.filter { line ->
                    line.text.contains(TITLE_FAMILY_TOKEN, ignoreCase = true) ||
                        decisionSummary(parser, line.text).getJSONArray("candidates").let { candidates ->
                            (0 until candidates.length()).any { candidates.getString(it).startsWith(TITLE_FAMILY_TOKEN) }
                        }
                }
                val lines = JSONArray(layout.lines.take(MAX_REPORTED_BLOCKS).map { line ->
                    blockSummary(parser, line.text, line.bounds, titleLines.any { it == line })
                })
                val elements = JSONArray(layout.elements.take(MAX_REPORTED_BLOCKS).map { element ->
                    val relatedLine = titleLines.firstOrNull { it.bounds != null && element.bounds != null }
                    elementSummary(parser, element.text, element.bounds, relatedLine?.bounds)
                })
                val contextForCrop = ExtractionContext(
                    anchoredNameFallbackBand = Rect(0, 0, source.width, source.height)
                )
                val extracted = AnchoredScreenText.extract(
                    layout, parser, source.width, source.height, contextForCrop
                )
                val extractedDecision = extracted.name
                val report = JSONObject()
                    .put("schema", 1)
                    .put("inputKind", "locally_staged_title_crop_replay")
                    .put("imageWidth", source.width)
                    .put("imageHeight", source.height)
                    .put("documentStatus", layout.documentStatus.name)
                    .put("lineCount", layout.lines.size)
                    .put("elementCount", layout.elements.size)
                    .put("ocrMs", ocrMs)
                    .put("titleCandidateLineCount", titleLines.size.coerceAtMost(MAX_REPORTED_BLOCKS))
                    .put("lines", lines)
                    .put("elements", elements)
                    .put("anchoredExtraction", JSONObject()
                        .put("status", extractedDecision?.status() ?: "missing")
                        .put("acceptedSpecies", extractedDecision?.acceptedSpeciesOrNull())
                        .put("gender", extracted.nameRaw?.let(parser::parseGender))
                        .put("reasonCodes", JSONArray(extractedDecision?.diagnostics?.reasonCodes.orEmpty().take(4)))
                    )
                val output = File(externalFiles, OUTPUT_FILE)
                output.writeText(report.toString(2))
                assertTrue("Sanitized probe report was not written", output.length() in 1..MAX_OUTPUT_BYTES)
            } finally {
                provider.close()
            }
        }
    }

    private fun blockSummary(
        parser: TextParser,
        text: String,
        bounds: Rect?,
        isTitleCandidate: Boolean
    ): JSONObject {
        val decision = decisionSummary(parser, text)
        return JSONObject()
            .put("isTitleCandidate", isTitleCandidate)
            .put("codePointCount", text.codePointCount(0, text.length))
            .put("containsTargetFamilyToken", text.contains(TITLE_FAMILY_TOKEN, ignoreCase = true))
            .put("containsMaleGlyph", text.contains(MALE_GLYPH))
            .put("containsFemaleGlyph", text.contains(FEMALE_GLYPH))
            .put("gender", parser.parseGender(text))
            .put("decision", decision)
            .put("bounds", bounds?.let(::boundsSummary) ?: JSONObject.NULL)
    }

    private fun elementSummary(
        parser: TextParser,
        text: String,
        bounds: Rect?,
        titleBounds: Rect?
    ): JSONObject {
        val gap = bounds?.let { rect -> titleBounds?.let { title -> horizontalGap(rect, title) } }
        val maxGap = titleBounds?.height()?.times(MAX_GAP_HEIGHTS)
        return JSONObject()
            .put("codePointCount", text.codePointCount(0, text.length))
            .put("gender", parser.parseGender(text))
            .put("containsMaleGlyph", text.contains(MALE_GLYPH))
            .put("containsFemaleGlyph", text.contains(FEMALE_GLYPH))
            .put("adjacentToTitleCandidate", gap != null && maxGap != null &&
                abs((bounds!!.centerY()) - titleBounds!!.centerY()) <= maxGap && gap <= maxGap)
            .put("bounds", bounds?.let(::boundsSummary) ?: JSONObject.NULL)
    }

    private fun decisionSummary(parser: TextParser, text: String): JSONObject {
        val decision = parser.decideSpeciesName(text)
        val candidates = when (decision) {
            is SpeciesNameDecision.Accepted -> listOf(decision.species)
            is SpeciesNameDecision.Uncertain -> decision.candidates.take(MAX_CANDIDATES).map { it.name }
            is SpeciesNameDecision.NoMatch -> emptyList()
        }
        return JSONObject()
            .put("status", decision.status())
            .put("acceptedSpecies", decision.acceptedSpeciesOrNull())
            .put("candidates", JSONArray(candidates))
            .put("reasonCodes", JSONArray(decision.diagnostics.reasonCodes.take(MAX_REASON_CODES)))
    }

    private fun boundsSummary(rect: Rect): JSONObject = JSONObject()
        .put("left", rect.left)
        .put("top", rect.top)
        .put("right", rect.right)
        .put("bottom", rect.bottom)

    private fun horizontalGap(first: Rect, second: Rect): Int = when {
        first.right < second.left -> second.left - first.right
        first.left > second.right -> first.left - second.right
        else -> 0
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) {
        try {
            block(this)
        } finally {
            recycle()
        }
    }

    private companion object {
        const val INPUT_FILE_ARGUMENT = "live_title_probe_file"
        const val OUTPUT_FILE = "live_title_probe_report.json"
        const val MAX_INPUT_BYTES = 4L * 1024 * 1024
        const val MAX_OUTPUT_BYTES = 24L * 1024
        const val MAX_REPORTED_BLOCKS = 32
        const val MAX_CANDIDATES = 3
        const val MAX_REASON_CODES = 4
        const val MAX_GAP_HEIGHTS = 2
        const val TITLE_FAMILY_TOKEN = "Nidoran"
        const val MALE_GLYPH = '\u2642'
        const val FEMALE_GLYPH = '\u2640'
    }
}
