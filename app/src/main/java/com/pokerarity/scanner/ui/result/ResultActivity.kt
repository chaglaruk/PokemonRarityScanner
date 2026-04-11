package com.pokerarity.scanner.ui.result

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.buildAnalysisItems
import com.pokerarity.scanner.data.model.normalizeIvText
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.ui.main.MainActivity
import com.pokerarity.scanner.ui.screens.ScanResultScreen
import com.pokerarity.scanner.ui.share.ResultShareRenderer
import com.pokerarity.scanner.ui.theme.PokeRarityTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ResultActivity : ComponentActivity() {

    @Inject
    lateinit var repository: PokemonRepository

    companion object {
        const val EXTRA_POKEMON_NAME = "extra_pokemon_name"
        const val EXTRA_CP = "extra_cp"
        const val EXTRA_HP = "extra_hp"
        const val EXTRA_SCORE = "extra_score"
        const val EXTRA_TIER = "extra_tier"
        const val EXTRA_IS_SHINY = "extra_is_shiny"
        const val EXTRA_IS_SHADOW = "extra_is_shadow"
        const val EXTRA_IS_LUCKY = "extra_is_lucky"
        const val EXTRA_HAS_COSTUME = "extra_has_costume"
        const val EXTRA_HAS_SPECIAL_FORM = "extra_has_special_form"
        const val EXTRA_IV_ESTIMATE = "extra_iv_estimate"
        const val EXTRA_IV_SOLVE_MODE = "extra_iv_solve_mode"
        const val EXTRA_IV_SIGNALS = "extra_iv_signals"
        const val EXTRA_IV_CANDIDATE_COUNT = "extra_iv_candidate_count"
        const val EXTRA_IV_LEVEL_MIN = "extra_iv_level_min"
        const val EXTRA_IV_LEVEL_MAX = "extra_iv_level_max"
        const val EXTRA_HAS_ARC = "extra_has_arc"
        const val EXTRA_PVP_SUMMARY = "extra_pvp_summary"
        const val EXTRA_EXPLANATIONS = "extra_explanations"
        const val EXTRA_BREAKDOWN_KEYS = "extra_breakdown_keys"
        const val EXTRA_BREAKDOWN_VALUES = "extra_breakdown_values"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_TELEMETRY_UPLOAD_ID = "extra_telemetry_upload_id"
        const val EXTRA_EVENT_CONFIDENCE_CODE = "extra_event_confidence_code"
        const val EXTRA_EVENT_CONFIDENCE_LABEL = "extra_event_confidence_label"
        const val EXTRA_EVENT_CONFIDENCE_DETAIL = "extra_event_confidence_detail"
        const val EXTRA_SCAN_CONFIDENCE_SCORE = "extra_scan_confidence_score"
        const val EXTRA_SCAN_CONFIDENCE_LABEL = "extra_scan_confidence_label"
        const val EXTRA_SCAN_CONFIDENCE_DETAIL = "extra_scan_confidence_detail"
        const val EXTRA_MISMATCH_GUARD_TITLE = "extra_mismatch_guard_title"
        const val EXTRA_MISMATCH_GUARD_DETAIL = "extra_mismatch_guard_detail"
        const val EXTRA_WHY_NOT_EXACT = "extra_why_not_exact"
    }

    private val telemetryCoordinator by lazy { ScanTelemetryCoordinator.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pokemon = pokemonFromScanExtras(
            name = intent.getStringExtra(EXTRA_POKEMON_NAME).orEmpty(),
            cp = intent.getIntExtra(EXTRA_CP, 0),
            hp = intent.getIntExtra(EXTRA_HP, 0).takeIf { it > 0 },
            score = intent.getIntExtra(EXTRA_SCORE, 0),
            tier = intent.getStringExtra(EXTRA_TIER).orEmpty(),
            isShiny = intent.getBooleanExtra(EXTRA_IS_SHINY, false),
            isLucky = intent.getBooleanExtra(EXTRA_IS_LUCKY, false),
            hasCostume = intent.getBooleanExtra(EXTRA_HAS_COSTUME, false),
            hasSpecialForm = intent.getBooleanExtra(EXTRA_HAS_SPECIAL_FORM, false),
            isShadow = intent.getBooleanExtra(EXTRA_IS_SHADOW, false),
            dateText = intent.getStringExtra(EXTRA_DATE),
            ivText = normalizeIvText(intent.getStringExtra(EXTRA_IV_ESTIMATE)) ?: "Hesaplanamadı",
            ivSolveMode = intent.getStringExtra(EXTRA_IV_SOLVE_MODE)?.let {
                runCatching { IvSolveMode.valueOf(it) }.getOrNull()
            },
            ivSignalsUsed = intent.getStringArrayListExtra(EXTRA_IV_SIGNALS).orEmpty(),
            ivCandidateCount = intent.getIntExtra(EXTRA_IV_CANDIDATE_COUNT, -1).takeIf { it >= 0 },
            ivLevelMin = intent.getFloatExtra(EXTRA_IV_LEVEL_MIN, -1f).takeIf { it >= 0f },
            ivLevelMax = intent.getFloatExtra(EXTRA_IV_LEVEL_MAX, -1f).takeIf { it >= 0f },
            hasArcSignal = intent.getBooleanExtra(EXTRA_HAS_ARC, false),
            pvpSummary = intent.getStringExtra(EXTRA_PVP_SUMMARY),
            analysisOverride = buildAnalysisItems(
                breakdownKeys = intent.getStringArrayListExtra(EXTRA_BREAKDOWN_KEYS).orEmpty(),
                breakdownValues = intent.getIntegerArrayListExtra(EXTRA_BREAKDOWN_VALUES).orEmpty(),
                explanations = intent.getStringArrayListExtra(EXTRA_EXPLANATIONS).orEmpty(),
                fallbackScore = intent.getIntExtra(EXTRA_SCORE, 0),
            ),
            decisionSupport = parseDecisionSupport(),
            telemetryUploadId = intent.getStringExtra(EXTRA_TELEMETRY_UPLOAD_ID),
        )

        setContent {
            PokeRarityTheme(darkTheme = isSystemInDarkTheme()) {
                ScanResultScreen(
                    pokemon = pokemon,
                    onBack = { finish() },
                    onShare = { shareResult(pokemon) },
                    onSave = { saveSnapshot() },
                    onFeedback = { category -> submitFeedback(category) },
                    onHome = { openMain() },
                    onHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                    onScan = { openMain(autoStartScan = true) },
                    onCollection = { openMain() },
                    onSettings = { openMain(openSettings = true) },
                )
            }
        }
    }

    private fun openMain(
        openSettings: Boolean = false,
        autoStartScan: Boolean = false,
    ) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_TELEMETRY_SETTINGS, openSettings)
                putExtra(MainActivity.EXTRA_AUTO_START_SCAN, autoStartScan)
            }
        )
    }

    private fun parseDecisionSupport(): ScanDecisionSupport? {
        return ScanDecisionSupport(
            eventConfidenceCode = intent.getStringExtra(EXTRA_EVENT_CONFIDENCE_CODE).orEmpty(),
            eventConfidenceLabel = intent.getStringExtra(EXTRA_EVENT_CONFIDENCE_LABEL).orEmpty(),
            eventConfidenceDetail = intent.getStringExtra(EXTRA_EVENT_CONFIDENCE_DETAIL).orEmpty(),
            scanConfidenceScore = intent.getIntExtra(EXTRA_SCAN_CONFIDENCE_SCORE, 0),
            scanConfidenceLabel = intent.getStringExtra(EXTRA_SCAN_CONFIDENCE_LABEL).orEmpty(),
            scanConfidenceDetail = intent.getStringExtra(EXTRA_SCAN_CONFIDENCE_DETAIL).orEmpty(),
            mismatchGuardTitle = intent.getStringExtra(EXTRA_MISMATCH_GUARD_TITLE),
            mismatchGuardDetail = intent.getStringExtra(EXTRA_MISMATCH_GUARD_DETAIL),
            whyNotExact = intent.getStringExtra(EXTRA_WHY_NOT_EXACT),
        ).takeIf { it.hasVisibleUiContent() }
    }

    private fun submitFeedback(category: String) {
        val uploadId = intent.getStringExtra(EXTRA_TELEMETRY_UPLOAD_ID)
        if (uploadId.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.feedback_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        telemetryCoordinator.submitFeedback(uploadId, category)
        Toast.makeText(this, getString(R.string.feedback_sent, category), Toast.LENGTH_SHORT).show()
    }

    private fun saveSnapshot() {
        val date = intent.getStringExtra(EXTRA_DATE)?.let {
            runCatching { SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(it) }.getOrNull()
        }

        lifecycleScope.launch {
            runCatching {
                repository.insertScanHistory(
                    ScanHistoryEntity(
                        pokemonName = intent.getStringExtra(EXTRA_POKEMON_NAME),
                        cp = intent.getIntExtra(EXTRA_CP, 0).takeIf { it > 0 },
                        hp = intent.getIntExtra(EXTRA_HP, 0).takeIf { it > 0 },
                        caughtDate = date,
                        rawOcrText = "Compose Save Snapshot",
                        isShiny = intent.getBooleanExtra(EXTRA_IS_SHINY, false),
                        isShadow = intent.getBooleanExtra(EXTRA_IS_SHADOW, false),
                        isLucky = intent.getBooleanExtra(EXTRA_IS_LUCKY, false),
                        hasCostume = intent.getBooleanExtra(EXTRA_HAS_COSTUME, false),
                        rarityScore = intent.getIntExtra(EXTRA_SCORE, 0),
                        rarityTier = intent.getStringExtra(EXTRA_TIER) ?: "COMMON",
                    )
                )
            }.onSuccess {
                Toast.makeText(this@ResultActivity, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@ResultActivity, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareResult(pokemon: com.pokerarity.scanner.data.model.Pokemon) {
        val shareText = getString(R.string.share_result_text, pokemon.name, pokemon.rarityScore)
        val imageUri = ResultShareRenderer.renderPokemonCardToImageUri(
            context = this,
            pokemon = pokemon,
            fileName = "scan_result_activity.png"
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            imageUri?.let {
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            type = if (imageUri != null) "image/png" else "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
    }
}
