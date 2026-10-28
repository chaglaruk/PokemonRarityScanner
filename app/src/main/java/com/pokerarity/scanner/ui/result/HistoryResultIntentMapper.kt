package com.pokerarity.scanner.ui.result

import android.content.Context
import android.content.Intent
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.formatIvEstimateText
import com.pokerarity.scanner.data.model.parseIvSolveMode
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.RecognitionIdentityHistoryCodec
import com.pokerarity.scanner.data.model.buildAnalysisItems
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import com.pokerarity.scanner.data.model.recognizedShiny
import com.pokerarity.scanner.util.DateParseUtils
import java.util.Date

/** Builds the same result navigation used when a user taps a persisted history row. */
internal object HistoryResultIntentMapper {
    fun create(context: Context, scan: ScanHistoryEntity): Intent =
        Intent(context, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_HISTORY_ENTRY_ID, scan.id)
            scan.recognitionIdentityJson?.let {
                putExtra(ResultActivity.EXTRA_RECOGNITION_IDENTITY, it)
            }
            putExtra(ResultActivity.EXTRA_POKEMON_NAME, scan.pokemonName)
            putScanCp(scan.cp)
            putHistoryScanHp(scan)
            putExtra(ResultActivity.EXTRA_SCORE, scan.rarityScore)
            putExtra(ResultActivity.EXTRA_TIER, scan.rarityTier)
            putExtra(ResultActivity.EXTRA_IS_SHINY, scan.recognizedShiny == RecognitionTruth.TRUE)
            putExtra(ResultActivity.EXTRA_SHINY_TRUTH, scan.recognizedShiny.name)
            putExtra(ResultActivity.EXTRA_IS_SHADOW, scan.isShadow)
            putExtra(ResultActivity.EXTRA_IS_LUCKY, scan.isLucky)
            putExtra(ResultActivity.EXTRA_HAS_COSTUME, scan.hasCostume)
            scan.eventConfidenceCode?.let {
                putExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_CODE, it)
            }
            scan.eventConfidenceLabel?.let {
                putExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_LABEL, it)
            }
            scan.eventConfidenceDetail?.let {
                putExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_DETAIL, it)
            }
            scan.mismatchGuardTitle?.let {
                putExtra(ResultActivity.EXTRA_MISMATCH_GUARD_TITLE, it)
            }
            scan.mismatchGuardDetail?.let {
                putExtra(ResultActivity.EXTRA_MISMATCH_GUARD_DETAIL, it)
            }
            scan.recognitionSummary?.let {
                putExtra(ResultActivity.EXTRA_RECOGNITION_SUMMARY, it)
            }
            val specialFormKnown = RecognitionIdentityHistoryCodec.decode(
                scan.recognitionIdentityJson
            )?.specialForm == RecognitionTruth.TRUE
            putExtra(
                ResultActivity.EXTRA_HAS_SPECIAL_FORM,
                specialFormKnown
            )
            scan.caughtDate?.let {
                putExtra(
                    ResultActivity.EXTRA_DATE,
                    DateParseUtils.formatDate(it, DateParseUtils.MMM_DD_YYYY_FORMATTER)
                )
            }
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

/** Converts the actual ResultActivity navigation Intent into the model shown by its result card. */
internal object ResultIntentPokemonMapper {
    fun create(intent: Intent): Pokemon {
        val identity = RecognitionIdentityHistoryCodec.decode(
            intent.getStringExtra(ResultActivity.EXTRA_RECOGNITION_IDENTITY)
        )
        val ivEstimate = ScanIvEstimateExtras.fromIntent(intent)
        return pokemonFromScanExtras(
            name = intent.getStringExtra(ResultActivity.EXTRA_POKEMON_NAME).orEmpty(),
            cp = intent.readScanCp(),
            hp = intent.readScanHp(),
            ivText = ivEstimate.text,
            ivSolveMode = ivEstimate.solveMode,
            ivSignalsUsed = intent.getStringArrayListExtra(ResultActivity.EXTRA_IV_SIGNALS).orEmpty(),
            ivCandidateCount = ivEstimate.candidateCount,
            ivLevelMin = ivEstimate.levelMin,
            ivLevelMax = ivEstimate.levelMax,
            score = intent.getIntExtra(ResultActivity.EXTRA_SCORE, 0),
            tier = intent.getStringExtra(ResultActivity.EXTRA_TIER).orEmpty(),
            isShiny = intent.getBooleanExtra(ResultActivity.EXTRA_IS_SHINY, false),
            shinyTruth = intent.readScanShinyTruth(),
            isLucky = intent.getBooleanExtra(ResultActivity.EXTRA_IS_LUCKY, false),
            hasCostume = intent.getBooleanExtra(ResultActivity.EXTRA_HAS_COSTUME, false),
            hasSpecialForm = intent.getBooleanExtra(ResultActivity.EXTRA_HAS_SPECIAL_FORM, false) ||
                identity?.specialForm == RecognitionTruth.TRUE,
            isShadow = intent.getBooleanExtra(ResultActivity.EXTRA_IS_SHADOW, false),
            dateText = intent.getStringExtra(ResultActivity.EXTRA_DATE),
            analysisOverride = buildAnalysisItems(
                breakdownKeys = intent.getStringArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_KEYS).orEmpty(),
                breakdownValues = intent.getIntegerArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_VALUES).orEmpty(),
                explanations = intent.getStringArrayListExtra(ResultActivity.EXTRA_EXPLANATIONS).orEmpty(),
                fallbackScore = intent.getIntExtra(ResultActivity.EXTRA_SCORE, 0),
            ),
            decisionSupport = decisionSupportFrom(intent),
            telemetryUploadId = intent.getStringExtra(ResultActivity.EXTRA_TELEMETRY_UPLOAD_ID),
            recognitionIdentity = identity,
        )
    }

    private fun decisionSupportFrom(intent: Intent): ScanDecisionSupport? = ScanDecisionSupport(
        eventConfidenceCode = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_CODE).orEmpty(),
        eventConfidenceLabel = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_LABEL).orEmpty(),
        eventConfidenceDetail = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_DETAIL).orEmpty(),
        scanConfidenceScore = intent.getIntExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_SCORE, 0),
        scanConfidenceLabel = intent.getStringExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_LABEL).orEmpty(),
        scanConfidenceDetail = intent.getStringExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_DETAIL).orEmpty(),
        mismatchGuardTitle = intent.getStringExtra(ResultActivity.EXTRA_MISMATCH_GUARD_TITLE),
        mismatchGuardDetail = intent.getStringExtra(ResultActivity.EXTRA_MISMATCH_GUARD_DETAIL),
        recognitionSummary = intent.getStringExtra(ResultActivity.EXTRA_RECOGNITION_SUMMARY),
    ).takeIf { it.hasVisibleUiContent() }
}

/** Applies ResultActivity's Save action to an existing History row when one was reopened. */
internal object ResultHistorySaveMapper {
    fun fromIntent(intent: Intent, existing: ScanHistoryEntity?, caughtDate: Date?): ScanHistoryEntity {
        val base = existing ?: ScanHistoryEntity(
            pokemonName = null,
            cp = null,
            hp = null,
            caughtDate = null,
            rawOcrText = "Recognition Save Snapshot"
        )
        val updated = base
            .applyScanVitals(intent, caughtDate)
            .applyVariantFlags(intent)
            .applyConfidence(intent)
            .applyRarity(intent)
        return ScanIvEstimateExtras.fromIntent(intent).applyTo(updated)
    }
}

private fun ScanHistoryEntity.applyScanVitals(intent: Intent, caughtDate: Date?): ScanHistoryEntity = copy(
    pokemonName = intent.getStringExtra(ResultActivity.EXTRA_POKEMON_NAME) ?: pokemonName,
    cp = intent.readScanCp() ?: cp,
    hp = intent.readScanHpOr(hp),
    caughtDate = caughtDate ?: this.caughtDate,
    recognitionIdentityJson = intent.getStringExtra(ResultActivity.EXTRA_RECOGNITION_IDENTITY)
        ?: recognitionIdentityJson
)

private fun ScanHistoryEntity.applyVariantFlags(intent: Intent): ScanHistoryEntity = copy(
    isShiny = intent.booleanExtraOrExisting(ResultActivity.EXTRA_IS_SHINY, isShiny),
    shinyTruth = if (intent.hasExtra(ResultActivity.EXTRA_SHINY_TRUTH)) {
        intent.readScanShinyTruth().name
    } else shinyTruth,
    isShadow = intent.booleanExtraOrExisting(ResultActivity.EXTRA_IS_SHADOW, isShadow),
    isLucky = intent.booleanExtraOrExisting(ResultActivity.EXTRA_IS_LUCKY, isLucky),
    hasCostume = intent.booleanExtraOrExisting(ResultActivity.EXTRA_HAS_COSTUME, hasCostume)
)

private fun ScanHistoryEntity.applyConfidence(intent: Intent): ScanHistoryEntity = copy(
    eventConfidenceCode = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_CODE)
        ?: eventConfidenceCode,
    eventConfidenceLabel = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_LABEL)
        ?: eventConfidenceLabel,
    eventConfidenceDetail = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_DETAIL)
        ?: eventConfidenceDetail,
    mismatchGuardTitle = intent.getStringExtra(ResultActivity.EXTRA_MISMATCH_GUARD_TITLE)
        ?: mismatchGuardTitle,
    mismatchGuardDetail = intent.getStringExtra(ResultActivity.EXTRA_MISMATCH_GUARD_DETAIL)
        ?: mismatchGuardDetail,
    recognitionSummary = intent.getStringExtra(ResultActivity.EXTRA_RECOGNITION_SUMMARY)
        ?: recognitionSummary,
    scanConfidenceScore = if (intent.hasExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_SCORE)) {
        intent.getIntExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_SCORE, 0)
    } else scanConfidenceScore,
    scanConfidenceLabel = intent.getStringExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_LABEL) ?: scanConfidenceLabel,
    scanConfidenceDetail = intent.getStringExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_DETAIL) ?: scanConfidenceDetail
)

private fun ScanHistoryEntity.applyRarity(intent: Intent): ScanHistoryEntity = copy(
    rarityScore = if (intent.hasExtra(ResultActivity.EXTRA_SCORE)) {
        intent.getIntExtra(ResultActivity.EXTRA_SCORE, rarityScore)
    } else rarityScore,
    rarityTier = intent.getStringExtra(ResultActivity.EXTRA_TIER) ?: rarityTier
)

private fun Intent.booleanExtraOrExisting(key: String, existing: Boolean): Boolean =
    if (hasExtra(key)) getBooleanExtra(key, false) else existing

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
