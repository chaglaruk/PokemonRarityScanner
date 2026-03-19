package com.pokerarity.scanner.ui.result

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.buildAnalysisItems
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.ui.screens.ScanResultScreen
import com.pokerarity.scanner.ui.theme.PokeRarityTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ResultActivity : AppCompatActivity() {

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
        const val EXTRA_EXPLANATIONS = "extra_explanations"
        const val EXTRA_BREAKDOWN_KEYS = "extra_breakdown_keys"
        const val EXTRA_BREAKDOWN_VALUES = "extra_breakdown_values"
        const val EXTRA_DATE = "extra_date"
    }

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
            ivText = intent.getStringExtra(EXTRA_IV_ESTIMATE),
            analysisOverride = buildAnalysisItems(
                breakdownKeys = intent.getStringArrayListExtra(EXTRA_BREAKDOWN_KEYS).orEmpty(),
                breakdownValues = intent.getIntegerArrayListExtra(EXTRA_BREAKDOWN_VALUES).orEmpty(),
                explanations = intent.getStringArrayListExtra(EXTRA_EXPLANATIONS).orEmpty(),
                fallbackScore = intent.getIntExtra(EXTRA_SCORE, 0),
            ),
        )

        setContent {
            PokeRarityTheme {
                ScanResultScreen(
                    pokemon = pokemon,
                    onBack = { finish() },
                    onShare = { shareResult(pokemon.name, pokemon.rarityScore) },
                    onSave = { saveSnapshot() },
                )
            }
        }
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
                Toast.makeText(this@ResultActivity, "Saved", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@ResultActivity, "Save failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareResult(name: String, score: Int) {
        val shareText = "I found $name with a rarity score of $score in PokeRarityScanner."
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }
}
