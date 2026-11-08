package com.pokerarity.scanner

import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Bounded, local-only challenger for the existing 2D prototype matcher on real GO frames. */
@RunWith(AndroidJUnit4::class)
class VariantPrototypeChallengerInstrumentedTest {

    @Test
    fun globallyRanksLocalRealGameScreensAgainstTheCurrent2dPrototypeCatalog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val inputDirectory = File(context.getExternalFilesDir(null), INPUT_DIRECTORY)
        val manifest = File(inputDirectory, INPUT_MANIFEST)
        assumeTrue("No private visual challenger manifest supplied", manifest.isFile)
        require(manifest.length() in 1..MAX_MANIFEST_BYTES) { "Manifest exceeds the bounded input size" }

        val cases = manifest.readText(Charsets.UTF_8)
            .lineSequence()
            .filter(String::isNotBlank)
            .map { line ->
                val fields = line.split('\t')
                require(fields.size == 4) { "Each case must have four tab-separated fields" }
                require(fields[0].matches(CASE_ID_PATTERN)) { "Invalid case ID" }
                require(fields[1].matches(FILE_NAME_PATTERN)) { "Invalid image filename" }
                require(fields[2].matches(SPECIES_PATTERN) && fields[3].matches(BASELINE_PATTERN)) {
                    "Species labels exceed the bounded report fields"
                }
                fields
            }
            .toList()
        assertTrue("Use 12 to 20 independent Pokémon specimens", cases.size in MIN_CASES..MAX_CASES)
        assertTrue("Case IDs must be unique", cases.map { it[0] }.toSet().size == cases.size)
        assertTrue("Each specimen must be a distinct species", cases.map { it[2] }.toSet().size == cases.size)

        val classifier = VariantPrototypeClassifier(context)
        cases.forEach { (caseId, filename, groundTruth, baselineSpecies) ->
            val imageFile = File(inputDirectory, filename)
            assertTrue("Missing local image for $caseId", imageFile.isFile)
            require(imageFile.length() in 1..MAX_IMAGE_BYTES) { "Image input exceeds the bounded size" }
            val bounds = Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
            require(bounds.outWidth in 1..MAX_IMAGE_WIDTH && bounds.outHeight in 1..MAX_IMAGE_HEIGHT) {
                "Image dimensions exceed the bounded input size"
            }
            require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_IMAGE_PIXELS) {
                "Image pixel count exceeds the bounded input size"
            }
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            assertNotNull("Could not decode image for $caseId", bitmap)
            val ownedBitmap = requireNotNull(bitmap)
            try {
                val started = SystemClock.elapsedRealtimeNanos()
                // An empty hint intentionally evaluates the whole packaged prototype set.
                val result = classifier.classify(ownedBitmap, emptyList())
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L
                assertNotNull("The catalog returned no global candidate for $caseId", result)
                val match = requireNotNull(result)
                Log.i(
                    TAG,
                    "case=$caseId truth=$groundTruth baseline=$baselineSpecies " +
                        "visual=${match.species} score=${match.score} confidence=${match.confidence} " +
                        "top=${match.topSpecies.take(TOP_MATCHES).joinToString(",")} elapsedMs=$elapsedMs"
                )
            } finally {
                ownedBitmap.recycle()
            }
        }
    }

    private companion object {
        const val TAG = "VariantPrototypeChallenge"
        const val INPUT_DIRECTORY = "visual-challenger"
        const val INPUT_MANIFEST = "cases.tsv"
        const val MIN_CASES = 12
        const val MAX_CASES = 20
        const val MAX_MANIFEST_BYTES = 4096L
        const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
        const val MAX_IMAGE_WIDTH = 3000
        const val MAX_IMAGE_HEIGHT = 5000
        const val MAX_IMAGE_PIXELS = 12_000_000L
        const val TOP_MATCHES = 3
        val CASE_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,24}")
        val FILE_NAME_PATTERN = Regex("[A-Za-z0-9._-]{1,96}\\.png")
        val SPECIES_PATTERN = Regex("[\\p{L}\\p{N} _.'-]{1,40}")
        val BASELINE_PATTERN = Regex("[\\p{L}\\p{N} _.'-]{0,40}")
    }
}
