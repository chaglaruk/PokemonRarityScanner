package com.pokerarity.scanner.ui.result

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.recognizedShiny
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.databinding.ActivityHistoryBinding
import com.pokerarity.scanner.ui.main.ScanHistoryAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    @Inject lateinit var repository: PokemonRepository

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: ScanHistoryAdapter
    private val selectedFilter = MutableStateFlow(HistoryFilter.ALL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        setupRecyclerView()
        setupFilters()
        observeScans()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = ScanHistoryAdapter { scan ->
            startActivity(HistoryResultIntentMapper.create(this, scan))
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun setupFilters() {
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedFilter.value = when {
                checkedIds.contains(R.id.chipAll) -> HistoryFilter.ALL
                checkedIds.contains(R.id.chipRare) -> HistoryFilter.RARE
                checkedIds.contains(R.id.chipShiny) -> HistoryFilter.SHINY
                checkedIds.contains(R.id.chipLegendary) -> HistoryFilter.LEGENDARY
                else -> HistoryFilter.ALL
            }
        }
    }

    private fun observeScans() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                selectedHistoryScans(
                    selection = selectedFilter,
                    allScans = repository::getAllScans,
                    rareScans = repository::getRareScans,
                    isShiny = { it.recognizedShiny == RecognitionTruth.TRUE }
                ).collect { scans ->
                    adapter.submitList(scans)
                    binding.layoutEmpty.visibility = if (scans.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvHistory.visibility = if (scans.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }
}
