// Purpose: Verify safe scan pipeline summaries never expose raw OCR or local paths.
package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineDecisionSummaryTest {
    @Test
    fun logLineOmitsRawOcrPathsDiagnosticsAndSecrets() {
        val summary = PipelineDecisionSummary.build(
            pokemon = pokemon(
                rawOcrText = "Name:Pikachu|C:/Users/Player/private.png|apiKey=not-real|Authorization: Bearer token|/tmp/scan|secret",
                ocrDiagnosticsDir = "C:/Users/Player/diagnostics",
                ocrDiagnosticsFiles = mapOf("summary" to "/tmp/diagnostics/summary.json")
            ),
            features = VisualFeatures(isShiny = true, hasCostume = true),
            rarityScore = rarityScore(),
            phase2Result = phase2Result("hasCostume", "C:/Users/Player/private-target"),
            screenshotPath = "C:/Users/Player/screenshot.png",
            pipelineMs = 1234
        )

        val line = summary.toLogLine()
        val normalized = line.lowercase(Locale.US)

        listOf(
            "pikachu",
            "c:/users",
            "/tmp",
            "apikey",
            "authorization",
            "bearer",
            "token",
            "secret",
            "private"
        ).forEach { forbidden ->
            assertFalse("Summary leaked $forbidden: $line", normalized.contains(forbidden))
        }
        assertTrue(normalized.contains("screenshot=present"))
        assertTrue(normalized.contains("diagnostics=present"))
        assertTrue(normalized.contains("flags=shiny,costume"))
        assertTrue(normalized.contains("phase2=hascostume,unsupported"))
    }

    @Test
    fun metadataOnlySummaryRemainsValidWithoutScreenshotOrDecisionSupport() {
        val summary = PipelineDecisionSummary.build(
            pokemon = pokemon(
                name = "Unknown",
                cp = null,
                hp = null,
                maxHp = null,
                caughtDate = null,
                rawOcrText = "Name:Unknown|C:/Users/Player/scan.png"
            ),
            features = VisualFeatures(),
            rarityScore = rarityScore(decisionSupport = null),
            phase2Result = null,
            screenshotPath = null,
            pipelineMs = -20
        )

        val line = summary.toLogLine()

        assertTrue(line.contains("species=unknown"))
        assertTrue(line.contains("cp=missing"))
        assertTrue(line.contains("hp=missing"))
        assertTrue(line.contains("date=missing"))
        assertTrue(line.contains("screenshot=absent"))
        assertTrue(line.contains("diagnostics=absent"))
        assertTrue(line.contains("phase2=none"))
        assertTrue(line.contains("scanConfidence=unknown"))
        assertTrue(line.contains("pipelineMs=0"))
    }

    private fun pokemon(
        name: String? = "Pikachu",
        cp: Int? = 500,
        hp: Int? = 80,
        maxHp: Int? = 80,
        caughtDate: Date? = Date(1_700_000_000_000),
        rawOcrText: String = "",
        ocrDiagnosticsDir: String? = null,
        ocrDiagnosticsFiles: Map<String, String> = emptyMap()
    ) = PokemonData(
        cp = cp,
        hp = hp,
        maxHp = maxHp,
        name = name,
        realName = name,
        candyName = null,
        megaEnergy = null,
        weight = null,
        height = null,
        stardust = null,
        caughtDate = caughtDate,
        rawOcrText = rawOcrText,
        ocrDiagnosticsDir = ocrDiagnosticsDir,
        ocrDiagnosticsFiles = ocrDiagnosticsFiles
    )

    private fun rarityScore(
        decisionSupport: ScanDecisionSupport? = ScanDecisionSupport(
            eventConfidenceCode = "LIVE_EVENT",
            eventConfidenceLabel = "Live event",
            eventConfidenceDetail = "Live event metadata matched.",
            scanConfidenceScore = 87,
            scanConfidenceLabel = "High confidence",
            scanConfidenceDetail = "Stable CP, HP, and date.",
            mismatchGuardTitle = null,
            mismatchGuardDetail = null,
            whyNotExact = null,
            recognitionSummary = "Internal summary should not be copied into pipeline log."
        )
    ) = RarityScore(
        totalScore = 42,
        tier = RarityTier.RARE,
        recognitionSummary = "Do not log this raw summary.",
        breakdown = emptyMap(),
        explanation = emptyList(),
        decisionSupport = decisionSupport
    )

    private fun phase2Result(
        vararg appliedTargets: String
    ) = Phase2VariantClassifier.Result(
        species = "Pikachu",
        supportedTargets = appliedTargets.toList(),
        predictions = emptyList(),
        appliedTargets = appliedTargets.toList(),
        minConfidence = 0.5f,
        minMargin = 0.001f,
        modelType = "test"
    )
}
