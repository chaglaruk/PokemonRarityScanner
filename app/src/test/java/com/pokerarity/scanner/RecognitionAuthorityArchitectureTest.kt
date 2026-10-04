package com.pokerarity.scanner

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2D source-level architecture assertion: recognition-critical production code must
 * not reference the legacy [PokemonFamilyRegistry]. Family/species relations used for
 * recognition decisions come exclusively from the revisioned recognition snapshot.
 *
 * Comments that mention the legacy registry (documentation of the boundary) are stripped
 * before matching, so only real code references fail this test.
 */
class RecognitionAuthorityArchitectureTest {

    private val recognitionCriticalSources = listOf(
        "app/src/main/java/com/pokerarity/scanner/util/ocr/FamilySpeciesResolver.kt",
        "app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt",
        "app/src/main/java/com/pokerarity/scanner/util/ocr/ScanConsistencyGate.kt",
        "app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesFormResolver.kt",
        "app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt",
        "app/src/main/java/com/pokerarity/scanner/service/ProfileFitReconciliation.kt",
        "app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt",
        "app/src/main/java/com/pokerarity/scanner/util/ocr/AnchoredScreenRecognizer.kt",
        "app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt",
        "app/src/main/java/com/pokerarity/scanner/util/ocr/RecognitionSnapshot.kt",
        "app/src/main/java/com/pokerarity/scanner/util/ocr/AnchoredScreenText.kt"
    )

    @Test
    fun recognitionCriticalCodeContainsNoLegacyFamilyRegistryReference() {
        val offenders = recognitionCriticalSources.mapNotNull { path ->
            val file = resolve(path) ?: return@mapNotNull path to "file missing"
            val code = stripComments(file.readText())
            if (code.contains("PokemonFamilyRegistry")) path to "code reference found" else null
        }
        assertTrue(
            "recognition-critical code must not use the legacy family registry: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun recognitionFamilyRelationsFlowThroughTheSnapshot() {
        recognitionCriticalSources
            .filter { it.endsWith("SpeciesRefiner.kt") || it.endsWith("ScanConsistencyGate.kt") ||
                it.endsWith("SpeciesFormResolver.kt") || it.endsWith("VariantDecisionEngine.kt") }
            .forEach { path ->
                val code = stripComments(resolve(path)!!.readText())
                assertTrue(
                    "$path must route family relations through the snapshot index",
                    code.contains("RecognitionSnapshotHolder") && code.contains("recognitionFamilyIndex")
                )
            }
    }

    private fun resolve(relative: String): java.io.File =
        // Unit tests may run with the working directory at the repo root or at app/.
        listOf(File(relative), File(relative.removePrefix("app/")))
            .firstOrNull { it.isFile }
            ?: throw AssertionError("cannot locate $relative from ${File(".").absolutePath}")

    private fun stripComments(source: String): String {
        val noBlockComments = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlockComments.lines().joinToString("\n") { line ->
            val index = line.indexOf("//")
            if (index >= 0) line.substring(0, index) else line
        }
    }
}
