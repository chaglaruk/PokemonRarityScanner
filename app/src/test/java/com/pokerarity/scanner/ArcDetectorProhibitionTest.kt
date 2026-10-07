package com.pokerarity.scanner

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3C architecture guard: the historical degenerate arc detector
 * [ImagePreprocessor.detectArcLevel] must never be called from production recognition.
 * It survives only as its own historical definition; the Phase 3C fitter is an
 * independent implementation with no shared constants, ratios or thresholds.
 */
class ArcDetectorProhibitionTest {

    @Test
    fun productionRecognitionPathNeverInvokesTheHistoricalDetector() {
        val mainRoots = listOf(File("src/main/java"), File("app/src/main/java"))
            .filter { it.isDirectory }
        val root = mainRoots.firstOrNull()
            ?: error("main source root not found")
        val offenders = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                if ("detectArcLevel" in text && !file.name.endsWith("ImagePreprocessor.kt")) {
                    offenders += file.name
                }
            }
        assertTrue(
            "historical arc detector referenced outside its own definition: $offenders",
            offenders.isEmpty())
    }

    @Test
    fun historicalDetectorHasNoProductionCallSites() {
        val preprocessor = listOf(
            File("src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt"),
            File("app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt")
        ).first { it.isFile }
        val text = preprocessor.readText()
        // The definition itself stays (historical evidence); it must not be invoked.
        assertTrue(text.contains("fun detectArcLevel"))
        val callPattern = Regex("""(?<!fun )(?<!private fun )detectArcLevel\(""")
        val calls = callPattern.findAll(text).count()
        assertTrue("unexpected direct invocations of the historical detector: $calls", calls == 0)
    }

    @Test
    fun phase3CFitterDoesNotReuseHistoricalConstants() {
        // The old detector's fixed screen ratios (center y 0.402, radius 0.33-0.37,
        // white threshold 220, gap 12) must not appear in the new fitter.
        val fitter = listOf(
            File("src/main/java/com/pokerarity/scanner/util/ocr/ArcSignalFitter.kt"),
            File("app/src/main/java/com/pokerarity/scanner/util/ocr/ArcSignalFitter.kt")
        ).first { it.isFile }
        val text = fitter.readText()
        listOf("0.402f", "0.33f", "0.37f", "gapMax").forEach { constant ->
            assertTrue("fitter reuses historical constant: $constant", constant !in text)
        }
    }
}
