package com.pokerarity.scanner.ui.result

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.databinding.ActivityResultBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
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
        const val EXTRA_IV_ESTIMATE = "extra_iv_estimate"
        const val EXTRA_RAW_DEBUG = "extra_raw_debug"
        const val EXTRA_EXPLANATIONS = "extra_explanations"
        const val EXTRA_BREAKDOWN_KEYS = "extra_breakdown_keys"
        const val EXTRA_BREAKDOWN_VALUES = "extra_breakdown_values"
        const val EXTRA_DATE = "extra_date"
    }

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set as overlay style - keep Pokemon GO in background
        setTheme(android.R.style.Theme_Translucent_NoTitleBar)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Make it a true overlay that doesn't take focus
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make window compact and centered
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
        
        // Set gravity to center
        window.attributes.gravity = android.view.Gravity.CENTER
        
        // Add drag capability using drag handle
        binding.dragHandle.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Initial touch position
                    view.tag = Pair(event.rawX, event.rawY)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    // Drag window
                    val tag = view.tag as? Pair<*, *>
                    if (tag != null) {
                        val initialX = tag.first as? Float ?: event.rawX
                        val initialY = tag.second as? Float ?: event.rawY
                        
                        val deltaX = event.rawX - initialX
                        val deltaY = event.rawY - initialY
                        
                        val params = window.attributes
                        params.x += deltaX.toInt()
                        params.y += deltaY.toInt()
                        window.attributes = params
                        
                        view.tag = Pair(event.rawX, event.rawY)
                    }
                    true
                }
                else -> false
            }
        }

        setupUI()
        loadData()
    }

    private fun setupUI() {
        // Load data into editable fields
        loadEditableData()
        
        binding.btnBack.setOnClickListener { finish() }
        
        binding.etDate.setOnClickListener {
            showDatePicker()
        }
        
        binding.btnSave.setOnClickListener {
            saveEditableData()
        }

        binding.btnShare.setOnClickListener {
            shareResult()
        }
        
        // No hidden states for animations
        binding.etPokemonName.alpha = 1f
        binding.etCP.alpha = 1f
        binding.etHP.alpha = 1f
        binding.etDate.alpha = 1f
        binding.btnSave.alpha = 1f
        binding.btnBack.alpha = 1f
        binding.btnShare.alpha = 1f
    }
    
    private fun showDatePicker() {
        val calendar = java.util.Calendar.getInstance()
        val currentText = binding.etDate.text.toString()
        if (currentText.isNotBlank()) {
            try {
                val date = SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(currentText)
                if (date != null) calendar.time = date
            } catch (_: Exception) {}
        }

        android.app.DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                binding.etDate.setText(SimpleDateFormat("MMM dd, yyyy", Locale.US).format(calendar.time))
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadEditableData() {
        // Load existing data from intent extras
        intent?.let { intent ->
            binding.etPokemonName.setText(intent.getStringExtra(EXTRA_POKEMON_NAME) ?: "")
            binding.etCP.setText(intent.getIntExtra(EXTRA_CP, 0).toString())
            binding.etHP.setText(intent.getIntExtra(EXTRA_HP, 0).toString())
            binding.etDate.setText(intent.getStringExtra(EXTRA_DATE) ?: "")
            // Notes removed
        }
    }
    
    private fun saveEditableData() {
        // Get data from UI
        val pokemonName = binding.etPokemonName.text.toString()
        val cp = binding.etCP.text.toString().toIntOrNull()
        val hp = binding.etHP.text.toString().toIntOrNull()
        val dateStr = binding.etDate.text.toString()
        
        // Parse date if possible
        val date = if (dateStr.isNotBlank()) {
            try {
                SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(dateStr)
            } catch (e: Exception) {
                null
            }
        } else null

        // Save to database using Repository
        lifecycleScope.launch {
            try {
                val entity = ScanHistoryEntity(
                    pokemonName = pokemonName,
                    cp = cp,
                    hp = hp,
                    caughtDate = date,
                    rawOcrText = "User Edited", // Mark as edited
                    isShiny = intent.getBooleanExtra(EXTRA_IS_SHINY, false),
                    isShadow = intent.getBooleanExtra(EXTRA_IS_SHADOW, false),
                    isLucky = intent.getBooleanExtra(EXTRA_IS_LUCKY, false),
                    hasCostume = intent.getBooleanExtra(EXTRA_HAS_COSTUME, false),
                    rarityScore = intent.getIntExtra(EXTRA_SCORE, 0),
                    rarityTier = intent.getStringExtra(EXTRA_TIER) ?: "COMMON"
                )
                
                repository.insertScanHistory(entity)
                
                runOnUiThread {
                    Toast.makeText(this@ResultActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                    finish() // Close overlay after saving
                }
            } catch (e: Exception) {
                android.util.Log.e("ResultActivity", "Failed to save scan", e)
                runOnUiThread {
                    Toast.makeText(this@ResultActivity, "Save failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareResult() {
        val score = intent.getIntExtra(EXTRA_SCORE, 0)
        val name = binding.etPokemonName.text.toString()
        val tier = binding.tvTier.text.toString()
        
        val shareText = "I found a $tier $name with a Rarity Score of $score! #PokeRarityScanner"
        
        val intent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(android.content.Intent.createChooser(intent, "Share via"))
    }

    private fun loadData() {
        val score = intent.getIntExtra(EXTRA_SCORE, 0)
        val tierName = intent.getStringExtra(EXTRA_TIER) ?: "COMMON"
        val ivEstimate = intent.getStringExtra(EXTRA_IV_ESTIMATE) ?: "???"
        val rawDebug = intent.getStringExtra(EXTRA_RAW_DEBUG) ?: "No debug data"
        val explanations = intent.getStringArrayListExtra(EXTRA_EXPLANATIONS) ?: arrayListOf()

        val tier = try { RarityTier.valueOf(tierName) } catch (e: Exception) { RarityTier.COMMON }
        val tierColor = getTierColor(tier)

        // Tier label
        binding.tvTier.text = tier.label
        binding.tvTier.setTextColor(tierColor)
        
        // IV Estimate
        binding.tvIVEstimate.text = ivEstimate
        
        // Raw Debug Data
        binding.tvRawDebugData.text = rawDebug

        // Explanations list
        if (explanations.isNotEmpty()) {
            binding.tvExplanations.text = explanations.joinToString("\n")
        } else {
            binding.tvExplanations.text = "No specific rarity factors detected."
        }

        // Score circle border color
        val circleDrawable = binding.layoutScoreCircle.background as? GradientDrawable
        circleDrawable?.setStroke(
            (6 * resources.displayMetrics.density).toInt(),
            tierColor
        )

        // All animations removed for speed
        binding.tvScore.text = score.toString()
        binding.tvScore.setTextColor(tierColor)
        
        // Hide/Show tiers immediately
        binding.layoutScoreCircle.alpha = 1f
    }

    private fun animateScore(score: Int, color: Int) {
        binding.tvScore.text = score.toString()
        binding.tvScore.setTextColor(color)

        val animator = ValueAnimator.ofInt(0, score).apply {
            duration = 1200
            interpolator = OvershootInterpolator(1.05f)
            addUpdateListener { anim ->
                binding.tvScore.text = (anim.animatedValue as Int).toString()
            }
        }
        animator.start()

        binding.tvScore.setTextColor(color)
    }

    private fun getTierColor(tier: RarityTier): Int {
        return when (tier) {
            RarityTier.COMMON -> ContextCompat.getColor(this, R.color.tier_common)
            RarityTier.UNCOMMON -> ContextCompat.getColor(this, R.color.tier_uncommon)
            RarityTier.RARE -> ContextCompat.getColor(this, R.color.tier_rare)
            RarityTier.EPIC -> ContextCompat.getColor(this, R.color.tier_epic)
            RarityTier.LEGENDARY -> ContextCompat.getColor(this, R.color.tier_legendary)
            RarityTier.MYTHICAL -> ContextCompat.getColor(this, R.color.tier_mythical)
        }
    }
}
