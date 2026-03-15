package com.pokerarity.scanner.ui.result

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.databinding.ActivityHistoryBinding
import com.pokerarity.scanner.ui.main.ScanHistoryAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: ScanHistoryAdapter
    private lateinit var repository: PokemonRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PokemonRepository(AppDatabase.getInstance(this))
        setupToolbar()
        setupRecyclerView()
        setupFilters()
        loadScans()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = ScanHistoryAdapter { scan ->
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_POKEMON_NAME, scan.pokemonName)
                putExtra(ResultActivity.EXTRA_CP, scan.cp ?: 0)
                putExtra(ResultActivity.EXTRA_HP, scan.hp ?: 0)
                putExtra(ResultActivity.EXTRA_SCORE, scan.rarityScore)
                putExtra(ResultActivity.EXTRA_TIER, scan.rarityTier)
                putExtra(ResultActivity.EXTRA_IS_SHINY, scan.isShiny)
                putExtra(ResultActivity.EXTRA_IS_SHADOW, scan.isShadow)
                putExtra(ResultActivity.EXTRA_IS_LUCKY, scan.isLucky)
                putExtra(ResultActivity.EXTRA_HAS_COSTUME, scan.hasCostume)
            }
            startActivity(intent)
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun setupFilters() {
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipAll) -> loadScans()
                checkedIds.contains(R.id.chipRare) -> loadRareScans(40)
                checkedIds.contains(R.id.chipShiny) -> loadShinyScans()
                checkedIds.contains(R.id.chipLegendary) -> loadRareScans(80)
                else -> loadScans()
            }
        }
    }

    private fun loadScans() {
        lifecycleScope.launch {
            repository.getAllScans().collectLatest { scans ->
                adapter.submitList(scans)
                binding.layoutEmpty.visibility = if (scans.isEmpty()) View.VISIBLE else View.GONE
                binding.rvHistory.visibility = if (scans.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun loadRareScans(minScore: Int) {
        lifecycleScope.launch {
            repository.getRareScans(minScore).collectLatest { scans ->
                adapter.submitList(scans)
                binding.layoutEmpty.visibility = if (scans.isEmpty()) View.VISIBLE else View.GONE
                binding.rvHistory.visibility = if (scans.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun loadShinyScans() {
        lifecycleScope.launch {
            repository.getAllScans().collectLatest { scans ->
                val shinyScans = scans.filter { it.isShiny }
                adapter.submitList(shinyScans)
                binding.layoutEmpty.visibility = if (shinyScans.isEmpty()) View.VISIBLE else View.GONE
                binding.rvHistory.visibility = if (shinyScans.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}
