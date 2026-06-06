// Purpose: Show a full-screen scan result and dispatch result actions.
package com.pokerarity.scanner.ui.result

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.lifecycleScope
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.CollectionAxisScore
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.buildAnalysisItems
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.data.repository.CatalogProvider
import com.pokerarity.scanner.data.repository.EditDetailsScoring
import com.pokerarity.scanner.data.repository.EditedScanScorePreview
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.ui.main.MainActivity
import com.pokerarity.scanner.ui.screens.ScanResultScreen
import com.pokerarity.scanner.ui.share.ResultShareRenderer
import com.pokerarity.scanner.ui.theme.PokeRarityTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        const val EXTRA_IS_PURIFIED = "extra_is_purified"
        const val EXTRA_HAS_LOCATION_CARD = "extra_has_location_card"
        const val EXTRA_IV_ESTIMATE = "extra_iv_estimate"
        const val EXTRA_IV_SOLVE_MODE = "extra_iv_solve_mode"
        const val EXTRA_IV_SIGNALS = "extra_iv_signals"
        const val EXTRA_IV_CANDIDATE_COUNT = "extra_iv_candidate_count"
        const val EXTRA_IV_LEVEL_MIN = "extra_iv_level_min"
        const val EXTRA_IV_LEVEL_MAX = "extra_iv_level_max"
        const val EXTRA_HAS_ARC = "extra_has_arc"
        const val EXTRA_SCAN_ID = "extra_scan_id"
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
        const val EXTRA_RECOGNITION_SUMMARY = "extra_recognition_summary"
        const val EXTRA_COLLECTION_AXES_JSON = "extra_collection_axes_json"
        const val EXTRA_IS_EDITED = "extra_is_edited"

        private val collectionAxesType = object : TypeToken<List<CollectionAxisScore>>() {}.type
    }

    private val telemetryCoordinator by lazy { ScanTelemetryCoordinator.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val catalog = CatalogProvider(this).loadCatalog()
        val initialPokemon = pokemonFromScanExtras(
            sourceId = intent.getLongExtra(EXTRA_SCAN_ID, 0L),
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
            isPurified = intent.getBooleanExtra(EXTRA_IS_PURIFIED, false),
            hasLocationCard = intent.getBooleanExtra(EXTRA_HAS_LOCATION_CARD, false),
            dateText = intent.getStringExtra(EXTRA_DATE),
            analysisOverride = buildAnalysisItems(
                breakdownKeys = intent.getStringArrayListExtra(EXTRA_BREAKDOWN_KEYS).orEmpty(),
                breakdownValues = intent.getIntegerArrayListExtra(EXTRA_BREAKDOWN_VALUES).orEmpty(),
                explanations = intent.getStringArrayListExtra(EXTRA_EXPLANATIONS).orEmpty(),
                fallbackScore = intent.getIntExtra(EXTRA_SCORE, 0),
            ),
            decisionSupport = parseDecisionSupport(),
            telemetryUploadId = intent.getStringExtra(EXTRA_TELEMETRY_UPLOAD_ID),
            collectionAxes = parseCollectionAxes(),
            isEdited = intent.getBooleanExtra(EXTRA_IS_EDITED, false),
        )

        setContent {
            PokeRarityTheme(darkTheme = isSystemInDarkTheme()) {
                var pokemon by remember { mutableStateOf(initialPokemon) }
                var latestPreview by remember { mutableStateOf<EditedScanScorePreview?>(null) }
                val catalogOptions = remember(pokemon.name) {
                    EditDetailsScoring.catalogOptionsFor(catalog, pokemon.name)
                }
                ScanResultScreen(
                    pokemon = pokemon,
                    onBack = { finish() },
                    onShare = { shareResult(pokemon) },
                    onSave = { saveSnapshot(pokemon, latestPreview) },
                    onFeedback = { category -> submitFeedback(category) },
                    onHome = { openMain() },
                    onHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                    onScan = { openMain() },
                    onCollection = { openMain() },
                    onSettings = { openMain(openSettings = true) },
                    catalogOptions = catalogOptions,
                    onEditDetails = { edits ->
                        val preview = EditDetailsScoring.preview(
                            basePokemon = pokemon,
                            editedDetails = edits,
                            catalog = catalog
                        )
                        pokemon = preview.pokemon
                        latestPreview = preview
                        persistEditedPreview(preview)
                    },
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
            recognitionSummary = intent.getStringExtra(EXTRA_RECOGNITION_SUMMARY),
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

    private fun saveSnapshot(
        pokemon: com.pokerarity.scanner.data.model.Pokemon,
        latestPreview: EditedScanScorePreview?
    ) {
        val date = latestPreview?.pokemonData?.caughtDate ?: intent.getStringExtra(EXTRA_DATE)?.let {
            runCatching {
                val localDate = java.time.LocalDate.parse(it, com.pokerarity.scanner.util.DateParseUtils.MMM_DD_YYYY_FORMATTER)
                java.util.Date.from(localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())
            }.getOrNull()
        }

        lifecycleScope.launch {
            runCatching {
                repository.insertScanHistory(
                    ScanHistoryEntity(
                        pokemonName = pokemon.name,
                        cp = pokemon.cp.takeIf { it > 0 },
                        hp = pokemon.hp,
                        caughtDate = date,
                        rawOcrText = "Recognition Save Snapshot",
                        isShiny = latestPreview?.visualFeatures?.isShiny
                            ?: intent.getBooleanExtra(EXTRA_IS_SHINY, false),
                        isShadow = latestPreview?.visualFeatures?.isShadow
                            ?: intent.getBooleanExtra(EXTRA_IS_SHADOW, false),
                        isLucky = latestPreview?.visualFeatures?.isLucky
                            ?: intent.getBooleanExtra(EXTRA_IS_LUCKY, false),
                        hasCostume = latestPreview?.visualFeatures?.hasCostume
                            ?: intent.getBooleanExtra(EXTRA_HAS_COSTUME, false),
                        isPurified = latestPreview?.visualFeatures?.isPurified
                            ?: intent.getBooleanExtra(EXTRA_IS_PURIFIED, false),
                        hasLocationCard = latestPreview?.visualFeatures?.hasLocationCard
                            ?: intent.getBooleanExtra(EXTRA_HAS_LOCATION_CARD, false),
                        rarityScore = pokemon.collectionScore,
                        rarityTier = pokemon.collectionTierCode,
                        collectionScore = pokemon.collectionScore,
                        collectionTier = pokemon.collectionTierCode,
                        originalCollectionScore = intent.getIntExtra(EXTRA_SCORE, 0),
                        hasSpecialForm = latestPreview?.visualFeatures?.hasSpecialForm
                            ?: intent.getBooleanExtra(EXTRA_HAS_SPECIAL_FORM, false),
                        editedDetailsJson = latestPreview?.editedDetailsJson,
                        isEdited = latestPreview != null || intent.getBooleanExtra(EXTRA_IS_EDITED, false),
                        axisBreakdownJson = latestPreview?.axisBreakdownJson
                            ?: intent.getStringExtra(EXTRA_COLLECTION_AXES_JSON),
                    )
                )
            }.onSuccess {
                Toast.makeText(this@ResultActivity, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@ResultActivity, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun persistEditedPreview(preview: EditedScanScorePreview) {
        val scanId = intent.getLongExtra(EXTRA_SCAN_ID, 0L)
        if (scanId <= 0L) return
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.updateEditedScan(scanId, preview)
                }
            }.onFailure {
                Toast.makeText(this@ResultActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
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

    private fun parseCollectionAxes(): List<CollectionAxisScore> {
        val payload = intent.getStringExtra(EXTRA_COLLECTION_AXES_JSON) ?: return emptyList()
        return runCatching {
            Gson().fromJson<List<CollectionAxisScore>>(payload, collectionAxesType)
        }.getOrDefault(emptyList())
    }
}
