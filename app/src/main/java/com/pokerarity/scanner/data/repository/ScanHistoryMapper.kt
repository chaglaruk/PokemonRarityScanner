package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityAnalysisHistoryCodec
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RecognitionIdentityHistoryCodec
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.DateParseUtils.toLocalDate
import java.time.LocalDate
import java.util.Date

/**
 * Pure mapper for converting in-memory scan results into database entities.
 * This allows safe unit testing of serialization/mapping logic without SQLCipher constraints.
 */
object ScanHistoryMapper {

    /**
     * Converts a completed scan into a persistable ScanHistoryEntity.
     *
     * Note on structured fields:
     * - `ocrConfidenceReasons` is explicitly EXCLUDED from persistence. It is transient debug
     *   telemetry logic and not needed for user scan history display.
     * - `variantDecisionTrace` is explicitly EXCLUDED from persistence. It is meant for
     *   telemetry and debug tracing, while users only care about the final rarity/flags.
     */
    fun toEntity(
        pokemonData: PokemonData,
        features: VisualFeatures,
        rarityScore: RarityScore,
        scanTimestamp: Date = Date(),
        caughtDateLocalDate: LocalDate? = pokemonData.caughtDateLocalDate ?: pokemonData.caughtDate?.toLocalDate()
    ): ScanHistoryEntity {
        // Prevent accidental leaking of full local path strings if they ever appear in rawOcrText.
        // The telemetry layer already strips these, but history should be safe too.
        val safeOcrText = pokemonData.rawOcrText
            .split("\n")
            .filterNot { it.contains("C:/Users", ignoreCase = true) || it.contains("/tmp", ignoreCase = true) }
            .joinToString("\n")

        val confidence = rarityScore.decisionSupport?.takeIf {
            it.eventConfidenceCode.isNotBlank() ||
                it.eventConfidenceLabel.isNotBlank() ||
                it.eventConfidenceDetail.isNotBlank() ||
                !it.mismatchGuardTitle.isNullOrBlank() ||
                !it.mismatchGuardDetail.isNullOrBlank() ||
                !it.recognitionSummary.isNullOrBlank() ||
                it.scanConfidenceLabel.isNotBlank() ||
                it.scanConfidenceDetail.isNotBlank()
        }
        return ScanHistoryEntity(
            timestamp = Date(scanTimestamp.time),
            pokemonName = pokemonData.name,
            cp = pokemonData.cp,
            hp = pokemonData.hp,
            caughtDate = pokemonData.caughtDate,
            caughtDateLocalDate = caughtDateLocalDate,
            rawOcrText = safeOcrText,
            isShiny = features.isShiny,
            isShadow = features.isShadow,
            isLucky = features.isLucky,
            hasCostume = features.hasCostume,
            rarityScore = rarityScore.totalScore,
            rarityTier = rarityScore.tier.name,
            shinyTruth = pokemonData.recognitionIdentity?.shiny?.name,
            scanConfidenceScore = confidence?.scanConfidenceScore,
            scanConfidenceLabel = confidence?.scanConfidenceLabel?.takeIf { it.isNotBlank() },
            scanConfidenceDetail = confidence?.scanConfidenceDetail?.takeIf { it.isNotBlank() },
            eventConfidenceCode = confidence?.eventConfidenceCode?.takeIf { it.isNotBlank() },
            eventConfidenceLabel = confidence?.eventConfidenceLabel?.takeIf { it.isNotBlank() },
            eventConfidenceDetail = confidence?.eventConfidenceDetail?.takeIf { it.isNotBlank() },
            mismatchGuardTitle = confidence?.mismatchGuardTitle?.takeIf { it.isNotBlank() },
            mismatchGuardDetail = confidence?.mismatchGuardDetail?.takeIf { it.isNotBlank() },
            recognitionSummary = confidence?.recognitionSummary?.takeIf { it.isNotBlank() },
            ivMin = rarityScore.ivSolve?.ivMin,
            ivMax = rarityScore.ivSolve?.ivMax,
            ivCandidateCount = rarityScore.ivSolve?.ivCandidateCount,
            ivSolveMode = rarityScore.ivSolve?.ivSolveMode?.name,
            ivLevelMin = rarityScore.ivSolve?.levelMin,
            ivLevelMax = rarityScore.ivSolve?.levelMax,
            recognitionIdentityJson = RecognitionIdentityHistoryCodec.encode(pokemonData.recognitionIdentity),
            rarityAnalysisJson = RarityAnalysisHistoryCodec.encode(
                rarityScore.explanation,
                rarityScore.breakdown
            )
        )
    }
}
