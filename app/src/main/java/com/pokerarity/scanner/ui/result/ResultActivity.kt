// Purpose: Show a full-screen scan result and dispatch result actions.
package com.pokerarity.scanner.ui.result

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.lifecycleScope
import com.pokerarity.scanner.R

import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.ui.main.MainActivity
import com.pokerarity.scanner.ui.screens.ScanResultScreen
import com.pokerarity.scanner.ui.share.ResultShareRenderer
import com.pokerarity.scanner.ui.theme.PokeRarityTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class ResultActivity : ComponentActivity() {

    @Inject
    lateinit var repository: PokemonRepository

    companion object {
        const val EXTRA_POKEMON_NAME = "extra_pokemon_name"
        const val EXTRA_CP = "extra_cp"
        const val EXTRA_SHINY_TRUTH = "extra_shiny_truth"
        const val EXTRA_HP = "extra_hp"
        const val EXTRA_HP_KNOWN = "extra_hp_known"
        const val EXTRA_SCORE = "extra_score"
        const val EXTRA_TIER = "extra_tier"
        const val EXTRA_IS_SHINY = "extra_is_shiny"
        const val EXTRA_IS_SHADOW = "extra_is_shadow"
        const val EXTRA_IS_LUCKY = "extra_is_lucky"
        const val EXTRA_HAS_COSTUME = "extra_has_costume"
        const val EXTRA_HAS_SPECIAL_FORM = "extra_has_special_form"
        const val EXTRA_IV_ESTIMATE = "extra_iv_estimate"
        const val EXTRA_IV_MIN = "extra_iv_min"
        const val EXTRA_IV_MAX = "extra_iv_max"
        const val EXTRA_IV_SOLVE_MODE = "extra_iv_solve_mode"
        const val EXTRA_IV_SIGNALS = "extra_iv_signals"
        const val EXTRA_IV_CANDIDATE_COUNT = "extra_iv_candidate_count"
        const val EXTRA_IV_LEVEL_MIN = "extra_iv_level_min"
        const val EXTRA_IV_LEVEL_MAX = "extra_iv_level_max"
        const val EXTRA_IV_LEVEL_ALTERNATIVES = "extra_iv_level_alternatives"
        const val EXTRA_HAS_ARC = "extra_has_arc"
        const val EXTRA_PVP_SUMMARY = "extra_pvp_summary"
        const val EXTRA_EXPLANATIONS = "extra_explanations"
        const val EXTRA_BREAKDOWN_KEYS = "extra_breakdown_keys"
        const val EXTRA_BREAKDOWN_VALUES = "extra_breakdown_values"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_DATE_ISO = "extra_date_iso"
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
        const val EXTRA_RECOGNITION_SUMMARY = "extra_recognition_summary"
        const val EXTRA_RECOGNITION_IDENTITY = "extra_recognition_identity"
        const val EXTRA_HISTORY_ENTRY_ID = "extra_history_entry_id"
    }

    private val telemetryCoordinator by lazy { ScanTelemetryCoordinator.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pokemon = ResultIntentPokemonMapper.create(intent)


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
                    onScan = { openMain() },
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
        val date = intent.getStringExtra(EXTRA_DATE_ISO)?.let {
            runCatching {
                val localDate = java.time.LocalDate.parse(
                    it,
                    com.pokerarity.scanner.util.DateParseUtils.ISO_DATE_FORMATTER
                )
                java.util.Date.from(localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())
            }.getOrNull()
        } ?: intent.getStringExtra(EXTRA_DATE)?.let {
            runCatching {
                val localDate = java.time.LocalDate.parse(it, com.pokerarity.scanner.util.DateParseUtils.MMM_DD_YYYY_FORMATTER)
                java.util.Date.from(localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())
            }.getOrNull()
        }

        lifecycleScope.launch {
            runCatching {
                val historyId = intent.getLongExtra(EXTRA_HISTORY_ENTRY_ID, 0L).takeIf { it > 0L }
                val existing = historyId?.let { repository.getScanById(it) }
                val snapshot = ResultHistorySaveMapper.fromIntent(intent, existing, date)
                if (existing == null || !repository.updateScanHistory(snapshot)) {
                    repository.insertScanHistory(snapshot.copy(id = 0L))
                }
            }.onSuccess {
                Toast.makeText(this@ResultActivity, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@ResultActivity, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareResult(pokemon: com.pokerarity.scanner.data.model.Pokemon) {
        val shareText = ResultShareRenderer.buildShareText(this, pokemon)
        val imageUri = ResultShareRenderer.renderPokemonCardToImageUri(
            context = this,
            pokemon = pokemon,
            fileName = "scan_result_activity.png"
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            imageUri?.let {
                putExtra(Intent.EXTRA_STREAM, it)
                clipData = ClipData.newUri(contentResolver, "scan_result_activity", it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } ?: putExtra(Intent.EXTRA_TEXT, shareText)
            if (imageUri != null) {
                type = "image/png"
            } else {
                type = "text/plain"
            }
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
    }
}
