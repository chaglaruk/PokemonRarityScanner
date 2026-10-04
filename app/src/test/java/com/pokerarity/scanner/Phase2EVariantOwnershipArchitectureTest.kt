// Purpose: Phase 2E source-level ownership boundary tests for the recognition identity contract.
package com.pokerarity.scanner

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2E source-level architecture assertions:
 * - the Phase 2 variant classifier still runs only behind the species authority gate
 *   and its output can only update variant states, never species identity;
 * - the pipeline builds the explicit [com.pokerarity.scanner.data.model.RecognitionIdentity]
 *   from the locked authority and maps legacy compatibility values FROM it;
 * - the variant layer (pure VisualFeatures merge) cannot rewrite PokemonData species.
 *
 * Comments are stripped before matching, so only real code references count.
 */
class Phase2EVariantOwnershipArchitectureTest {

    @Test
    fun phase2ClassifierRunsOnlyBehindTheSpeciesAuthorityGate() {
        val code = stripComments(source("app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt"))
        val gatePattern = Regex(
            """phase2AuthorityGate\.mayRunSpeciesScopedPhase2\s*&&""",
            RegexOption.IGNORE_CASE
        )
        val acceptedSpeciesGuard = code.contains("phase2AuthorityGate.acceptedSpecies") &&
            code.contains("phase2VariantClassifier.classify(bestBitmap, acceptedSpecies)")
        assertTrue(
            "species-scoped Phase 2 classifier must be guarded by mayRunSpeciesScopedPhase2",
            gatePattern.containsMatchIn(code)
        )
        assertTrue(
            "species-scoped Phase 2 classifier must receive the gate-accepted species",
            acceptedSpeciesGuard
        )
    }

    @Test
    fun pipelineBuildsExplicitIdentityAndMapsCompatibilityFromIt() {
        val code = stripComments(source("app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt"))
        assertTrue(
            "pipeline must build the explicit recognition identity",
            code.contains("RecognitionIdentityFactory.build(")
        )
        assertTrue(
            "identity must be attached to the runtime result",
            code.contains("recognitionIdentity = recognitionIdentity")
        )
        assertTrue(
            "legacy extras must map from the contract's compatibility view",
            code.contains("compatFeatures.isShiny") && code.contains("compatFeatures.hasSpecialForm")
        )
        assertTrue(
            "history persistence must consume the contract's compatibility view",
            code.contains("repository.saveScan(finalResult, compatFeatures, rarityScore)")
        )
    }

    @Test
    fun variantFeatureMergerCannotRewriteSpeciesIdentity() {
        val code = stripComments(
            source("app/src/main/java/com/pokerarity/scanner/util/vision/Phase2VariantFeatureMerger.kt")
        )
        assertTrue(
            "variant merge layer must stay a pure VisualFeatures transformation",
            code.contains("VisualFeatures") && !code.contains("PokemonData")
        )
    }

    @Test
    fun identityFactoryIsTheOnlyContractBuilder() {
        val factory = stripComments(
            source("app/src/main/java/com/pokerarity/scanner/util/ocr/RecognitionIdentityFactory.kt")
        )
        assertTrue(
            "factory must derive species authority from hard SpeciesEvidence only",
            factory.contains("hasHardAuthority")
        )
        assertTrue(
            "factory must accept classifier species as mismatch diagnostics, never authority",
            factory.contains("MISMATCH_REASON_CLASSIFIER") && factory.contains("MISMATCH_REASON_FULL_VARIANT")
        )
    }

    private fun source(relative: String): String =
        listOf(File(relative), File(relative.removePrefix("app/")))
            .firstOrNull { it.isFile }
            ?.readText()
            ?: throw AssertionError("cannot locate $relative from ${File(".").absolutePath}")

    private fun stripComments(source: String): String {
        val noBlockComments = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlockComments.lines().joinToString("\n") { line ->
            val index = line.indexOf("//")
            if (index >= 0) line.substring(0, index) else line
        }
    }
}
