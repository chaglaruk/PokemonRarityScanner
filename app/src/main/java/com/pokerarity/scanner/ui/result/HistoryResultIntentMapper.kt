package com.pokerarity.scanner.ui.result

import android.content.Context
import android.content.Intent
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.formatIvEstimateText
import com.pokerarity.scanner.data.model.parseIvSolveMode
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.recognizedShiny

/** Builds the same result navigation used when a user taps a persisted history row. */
internal object HistoryResultIntentMapper {
    fun create(context: Context, scan: ScanHistoryEntity): Intent =
        Intent(context, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_POKEMON_NAME, scan.pokemonName)
            putScanCp(scan.cp)
            putExtra(ResultActivity.EXTRA_HP, scan.hp ?: 0)
            putExtra(ResultActivity.EXTRA_SCORE, scan.rarityScore)
            putExtra(ResultActivity.EXTRA_TIER, scan.rarityTier)
            putExtra(ResultActivity.EXTRA_IS_SHINY, scan.recognizedShiny == RecognitionTruth.TRUE)
            putExtra(ResultActivity.EXTRA_SHINY_TRUTH, scan.recognizedShiny.name)
            putExtra(ResultActivity.EXTRA_IS_SHADOW, scan.isShadow)
            putExtra(ResultActivity.EXTRA_IS_LUCKY, scan.isLucky)
            putExtra(ResultActivity.EXTRA_HAS_COSTUME, scan.hasCostume)
            scan.scanConfidenceScore?.let {
                putExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_SCORE, it)
            }
            scan.scanConfidenceLabel?.let {
                putExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_LABEL, it)
            }
            scan.scanConfidenceDetail?.let {
                putExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_DETAIL, it)
            }
            ScanIvEstimateExtras.fromHistory(scan).writeTo(this)
        }
}

/** Typed view of the IV values shared by History -> Result -> Save. */
internal data class ScanIvEstimateExtras(
    val text: String?,
    val solveModeCode: String?,
    val ivMin: Int?,
    val ivMax: Int?,
    val candidateCount: Int?,
    val levelMin: Float?,
    val levelMax: Float?
) {
    val solveMode get() = parseIvSolveMode(solveModeCode)

    fun writeTo(intent: Intent) {
        intent.putOptionalString(ResultActivity.EXTRA_IV_ESTIMATE, text)
        intent.putOptionalString(ResultActivity.EXTRA_IV_SOLVE_MODE, solveModeCode)
        intent.putOptionalInt(ResultActivity.EXTRA_IV_MIN, ivMin)
        intent.putOptionalInt(ResultActivity.EXTRA_IV_MAX, ivMax)
        intent.putOptionalInt(ResultActivity.EXTRA_IV_CANDIDATE_COUNT, candidateCount)
        intent.putOptionalFloat(ResultActivity.EXTRA_IV_LEVEL_MIN, levelMin)
        intent.putOptionalFloat(ResultActivity.EXTRA_IV_LEVEL_MAX, levelMax)
    }

    fun applyTo(entity: ScanHistoryEntity): ScanHistoryEntity = entity.copy(
        ivMin = ivMin,
        ivMax = ivMax,
        ivCandidateCount = candidateCount,
        ivSolveMode = solveModeCode,
        ivLevelMin = levelMin,
        ivLevelMax = levelMax
    )

    companion object {
        fun fromHistory(scan: ScanHistoryEntity) = ScanIvEstimateExtras(
            text = formatIvEstimateText(scan.ivSolveMode, scan.ivMin, scan.ivMax),
            solveModeCode = scan.ivSolveMode,
            ivMin = scan.ivMin,
            ivMax = scan.ivMax,
            candidateCount = scan.ivCandidateCount,
            levelMin = scan.ivLevelMin,
            levelMax = scan.ivLevelMax
        )

        fun fromIntent(intent: Intent) = ScanIvEstimateExtras(
            text = intent.getStringExtra(ResultActivity.EXTRA_IV_ESTIMATE),
            solveModeCode = intent.getStringExtra(ResultActivity.EXTRA_IV_SOLVE_MODE),
            ivMin = intent.getOptionalInt(ResultActivity.EXTRA_IV_MIN),
            ivMax = intent.getOptionalInt(ResultActivity.EXTRA_IV_MAX),
            candidateCount = intent.getOptionalInt(ResultActivity.EXTRA_IV_CANDIDATE_COUNT),
            levelMin = intent.getOptionalFloat(ResultActivity.EXTRA_IV_LEVEL_MIN),
            levelMax = intent.getOptionalFloat(ResultActivity.EXTRA_IV_LEVEL_MAX)
        )
    }
}

private fun Intent.putOptionalString(key: String, value: String?) {
    if (value == null) removeExtra(key) else putExtra(key, value)
}

private fun Intent.putOptionalInt(key: String, value: Int?) {
    if (value == null || value < 0) removeExtra(key) else putExtra(key, value)
}

private fun Intent.putOptionalFloat(key: String, value: Float?) {
    if (value == null || value < 0f) removeExtra(key) else putExtra(key, value)
}

private fun Intent.getOptionalInt(key: String): Int? =
    if (hasExtra(key)) getIntExtra(key, -1).takeIf { it >= 0 } else null

private fun Intent.getOptionalFloat(key: String): Float? =
    if (hasExtra(key)) getFloatExtra(key, -1f).takeIf { it >= 0f } else null
