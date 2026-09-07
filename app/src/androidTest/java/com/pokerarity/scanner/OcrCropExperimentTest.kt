package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pokerarity.scanner.util.ocr.HealthBarLocator
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.TextParseUtils
import com.pokerarity.scanner.util.ocr.acceptedSpeciesOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OcrCropExperimentTest {
    @Test fun compareAnchoredCrops() {
        val i = InstrumentationRegistry.getInstrumentation()
        val fixtures = JSONObject(i.context.assets.open("scan_fixtures/pr06_1080_development/fixture_manifest.json")
            .bufferedReader().use { it.readText() }).getJSONArray("fixtures")
        val parser = TextParser(i.targetContext)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val rows = JSONArray()
        try {
            for (index in 0 until fixtures.length()) {
                val fixture = fixtures.getJSONObject(index)
                val truth = fixture.getJSONObject("truth")
                val bitmap = i.context.assets.open(fixture.getString("relativePath")).use(BitmapFactory::decodeStream)
                try {
                    val bar = HealthBarLocator.locate(bitmap)
                    if (bar == null) { rows.put(JSONObject().put("id", fixture.getString("id")).put("barMissing", true)); continue }
                    val name = Rect((bitmap.width * .15).toInt(), (bar.top - bitmap.height * .07).toInt().coerceAtLeast(0),
                        (bitmap.width * .85).toInt(), (bar.top - bitmap.height * .005).toInt().coerceAtLeast(1))
                    val hp = Rect((bitmap.width * .2).toInt(), bar.bottom,
                        (bitmap.width * .8).toInt(), (bar.bottom + bitmap.height * .04).toInt().coerceAtMost(bitmap.height))
                    for ((field, rect) in listOf("name" to name, "hp" to hp)) {
                        if (rect.height() <= 0) continue
                        val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
                        try {
                            for (scale in listOf(.5, 1.0, 1.5)) {
                                if (crop.width * scale < 32 || crop.height * scale < 32) continue
                                val input = if (scale == 1.0) crop else Bitmap.createScaledBitmap(crop,
                                    (crop.width * scale).toInt(), (crop.height * scale).toInt(), true)
                                try {
                                    val document = Tasks.await(recognizer.process(InputImage.fromBitmap(input, 0)), 30, TimeUnit.SECONDS)
                                    val names = document.textBlocks.flatMap { it.lines }.mapNotNull { parser.decideSpeciesName(it.text).acceptedSpeciesOrNull() }.distinct()
                                    val hpPair = TextParseUtils.parseHPPair(document.text)
                                    rows.put(JSONObject().put("id", fixture.getString("id")).put("field", field).put("scale", scale)
                                        .put("nameCorrect", names == listOf(truth.getString("canonicalSpecies")))
                                        .put("hpCorrect", hpPair?.second == truth.getInt("hp"))
                                        .put("names", JSONArray(names)).put("left", rect.left).put("top", rect.top)
                                        .put("right", rect.right).put("bottom", rect.bottom))
                                } finally { if (input !== crop) input.recycle() }
                            }
                        } finally { crop.recycle() }
                    }
                } finally { bitmap.recycle() }
                File(i.targetContext.filesDir, "ocr_crop_experiment.json").writeText(rows.toString(2))
            }
        } finally { recognizer.close() }
    }
}
