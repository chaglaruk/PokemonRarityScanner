package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.acceptedSpeciesOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Controlled grouping experiment. Truth is used only to score outputs, never to select a line. */
@RunWith(AndroidJUnit4::class)
class OcrLineExperimentTest {
    @Test fun compareGrouping() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val parser = TextParser(instrumentation.targetContext)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val fixtures = JSONObject(assets.open("scan_fixtures/pr06_1080_development/fixture_manifest.json")
            .bufferedReader().use { it.readText() }).getJSONArray("fixtures")
        val rows = JSONArray()
        val localLines = JSONArray()
        try {
            for (index in 0 until fixtures.length()) {
                val fixture = fixtures.getJSONObject(index)
                val truth = fixture.getJSONObject("truth")
                val original = assets.open(fixture.getString("relativePath")).use(BitmapFactory::decodeStream)
                try {
                    for (width in listOf(900, original.width)) {
                        val input = if (width == original.width) original else Bitmap.createScaledBitmap(
                            original, width, (original.height * width.toDouble() / original.width).toInt(), true)
                        try {
                            val started = SystemClock.elapsedRealtime()
                            val document = Tasks.await(recognizer.process(InputImage.fromBitmap(input, 0)), 30, TimeUnit.SECONDS)
                            val blocks = document.textBlocks
                            val lines = blocks.flatMap { it.lines }
                            localLines.put(JSONObject().put("id", fixture.getString("id")).put("width", width)
                                .put("lines", JSONArray(lines.map { line -> JSONObject()
                                    .put("text", line.text).put("left", line.boundingBox?.left)
                                    .put("top", line.boundingBox?.top).put("right", line.boundingBox?.right)
                                    .put("bottom", line.boundingBox?.bottom) })))
                            val hp = lines.firstOrNull { Regex("\\d{1,3}\\s*/\\s*\\d{2,3}\\s*HP", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
                            val hpBounds = hp?.boundingBox
                            val nameLine = hpBounds?.let { bounds -> lines.filter { line ->
                                val r = line.boundingBox
                                r != null && r.bottom <= bounds.top && r.top >= bounds.top - bounds.height() * 7 &&
                                    r.centerX() in (input.width * .2).toInt()..(input.width * .8).toInt()
                            }.maxByOrNull { it.boundingBox?.height() ?: 0 } }
                            val name = nameLine?.text?.let { parser.decideSpeciesName(it).acceptedSpeciesOrNull() }
                            val expected = truth.getString("canonicalSpecies")
                            rows.put(JSONObject().put("id", fixture.getString("id")).put("width", width)
                                .put("blockContainsAcceptedTruth", blocks.any { parser.decideSpeciesName(it.text).acceptedSpeciesOrNull() == expected })
                                .put("lineContainsAcceptedTruth", lines.any { parser.decideSpeciesName(it.text).acceptedSpeciesOrNull() == expected })
                                .put("anchoredName", name ?: JSONObject.NULL).put("anchoredNameCorrect", name == expected)
                                .put("hpLineFound", hp != null)
                                .put("candyLineFound", lines.any { it.text.contains("CANDY", true) })
                                .put("powerUpLineFound", lines.any { it.text.contains("POWER UP", true) })
                                .put("latencyMs", SystemClock.elapsedRealtime() - started))
                        } finally { if (input !== original) input.recycle() }
                    }
                } finally { original.recycle() }
                File(instrumentation.targetContext.filesDir, "ocr_line_experiment.json").writeText(rows.toString(2))
                // Private diagnostic evidence only. Never log or publish this file.
                File(instrumentation.targetContext.filesDir, "ocr_line_text_local.json").writeText(localLines.toString(2))
            }
        } finally { recognizer.close() }
    }
}
