// Purpose: Build non-sensitive scan pipeline summaries for debug logging.
package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier

internal data class PipelineDecisionSummary(
    val speciesStatus: String,
    val cpStatus: String,
    val hpStatus: String,
    val dateStatus: String,
    val screenshotStatus: String,
    val diagnosticsStatus: String,
    val visualFlags: List<String>,
    val phase2AppliedTargets: List<String>,
    val scanConfidence: String,
    val rarityClassification: String,
    val rarityWarnings: Int,
    val eventConfidence: String,
    val mismatchGuard: Boolean,
    val pipelineMs: Long?
) {
    fun toLogLine(): String {
        val flags = visualFlags.ifEmpty { listOf("none") }.joinToString(",")
        val phase2Targets = phase2AppliedTargets.ifEmpty { listOf("none") }.joinToString(",")
        return listOf(
            "PipelineDecisionSummary",
            "species=$speciesStatus",
            "cp=$cpStatus",
            "hp=$hpStatus",
            "date=$dateStatus",
            "screenshot=$screenshotStatus",
            "diagnostics=$diagnosticsStatus",
            "flags=$flags",
            "phase2=$phase2Targets",
            "scanConfidence=$scanConfidence",
            "rarity=$rarityClassification",
            "rarityWarnings=$rarityWarnings",
            "event=$eventConfidence",
            "mismatchGuard=$mismatchGuard",
            pipelineMs?.let { "pipelineMs=$it" } ?: "pipelineMs=unknown"
        ).joinToString(" ")
    }

    companion object {
        private val knownPhase2Targets = setOf(
            "isShiny",
            "hasCostume",
            "hasSpecialForm",
            "hasLocationCard"
        )

        fun build(
            pokemon: PokemonData,
            features: VisualFeatures,
            rarityScore: RarityScore,
            phase2Result: Phase2VariantClassifier.Result?,
            screenshotPath: String?,
            pipelineMs: Long?
        ): PipelineDecisionSummary {
            return PipelineDecisionSummary(
                speciesStatus = if (pokemon.name.isNullOrBlank() || pokemon.name == "Unknown") "unknown" else "known",
                cpStatus = if ((pokemon.cp ?: 0) > 0) "parsed" else "missing",
                hpStatus = if ((pokemon.hp ?: 0) > 0 || (pokemon.maxHp ?: 0) > 0) "parsed" else "missing",
                dateStatus = if (pokemon.caughtDate != null) "parsed" else "missing",
                screenshotStatus = if (screenshotPath.isNullOrBlank()) "absent" else "present",
                diagnosticsStatus = if (hasDiagnostics(pokemon)) "present" else "absent",
                visualFlags = visualFlags(features),
                phase2AppliedTargets = safePhase2Targets(phase2Result),
                scanConfidence = safeConfidence(rarityScore),
                rarityClassification = safeRarityClassification(rarityScore),
                rarityWarnings = rarityScore.rarityResult?.warnings.orEmpty().size,
                eventConfidence = safeEventConfidence(rarityScore),
                mismatchGuard = rarityScore.decisionSupport?.mismatchGuardTitle != null,
                pipelineMs = pipelineMs?.coerceAtLeast(0)
            )
        }

        private fun hasDiagnostics(pokemon: PokemonData): Boolean =
            !pokemon.ocrDiagnosticsDir.isNullOrBlank() || pokemon.ocrDiagnosticsFiles.isNotEmpty()

        private fun visualFlags(features: VisualFeatures): List<String> = buildList {
            if (features.isShiny) add("shiny")
            if (features.hasCostume) add("costume")
            if (features.hasSpecialForm) add("special_form")
            if (features.hasLocationCard) add("location_card")
            if (features.isShadow) add("shadow")
            if (features.isPurified) add("purified")
            if (features.isLucky) add("lucky")
            if (features.isXXS) add("xxs")
            if (features.isXXL) add("xxl")
        }

        private fun safePhase2Targets(result: Phase2VariantClassifier.Result?): List<String> {
            return result?.appliedTargets
                .orEmpty()
                .map { target -> if (target in knownPhase2Targets) target else "unsupported" }
                .distinct()
                .sorted()
        }

        private fun safeConfidence(rarityScore: RarityScore): String {
            val support = rarityScore.decisionSupport ?: return "unknown"
            val label = support.scanConfidenceLabel.ifBlank { "unknown" }
                .replace(Regex("[^A-Za-z0-9_-]+"), "_")
                .trim('_')
                .ifBlank { "unknown" }
            return "${support.scanConfidenceScore.coerceIn(0, 100)}:$label"
        }

        private fun safeEventConfidence(rarityScore: RarityScore): String {
            return rarityScore.decisionSupport
                ?.eventConfidenceCode
                ?.replace(Regex("[^A-Za-z0-9_-]+"), "_")
                ?.trim('_')
                ?.ifBlank { null }
                ?: "unknown"
        }

        private fun safeRarityClassification(rarityScore: RarityScore): String {
            return rarityScore.collectionResult
                ?.tier
                ?.name
                ?: rarityScore.rarityResult
                ?.tier
                ?.name
                ?: rarityScore.tier.name
        }
    }
}
