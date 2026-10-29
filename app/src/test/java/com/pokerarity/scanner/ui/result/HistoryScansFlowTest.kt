package com.pokerarity.scanner.ui.result

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryScansFlowTest {

    @Test
    fun changingFilterCancelsPreviousDatabaseFlowAndAppliesShinyFilter() = runBlocking {
        val selection = MutableStateFlow(HistoryFilter.ALL)
        val allRows = MutableSharedFlow<List<String>>()
        val rareRows = MutableSharedFlow<List<String>>()
        val allCancelled = CompletableDeferred<Unit>()
        val rareCancelled = CompletableDeferred<Unit>()
        val output = Channel<List<String>>(Channel.UNLIMITED)
        val collector = launch {
            selectedHistoryScans<String>(
                selection = selection,
                allScans = { allRows.onCompletion { allCancelled.complete(Unit) } },
                rareScans = { rareRows.onCompletion { rareCancelled.complete(Unit) } },
                isShiny = { it.startsWith("shiny:") }
            ).collect(output::send)
        }

        try {
            allRows.subscriptionCount.first { it == 1 }
            allRows.emit(listOf("normal: pikipek"))
            assertEquals(listOf("normal: pikipek"), output.receive())

            selection.value = HistoryFilter.RARE
            allCancelled.await()
            allRows.subscriptionCount.first { it == 0 }
            rareRows.subscriptionCount.first { it == 1 }
            allRows.emit(listOf("stale all result"))
            rareRows.emit(listOf("rare: torchic"))
            assertEquals(listOf("rare: torchic"), output.receive())

            selection.value = HistoryFilter.SHINY
            rareCancelled.await()
            rareRows.subscriptionCount.first { it == 0 }
            allRows.subscriptionCount.first { it == 1 }
            rareRows.emit(listOf("stale rare result"))
            allRows.emit(listOf("normal: skwovet", "shiny: chikorita"))
            assertEquals(listOf("shiny: chikorita"), output.receive())
        } finally {
            collector.cancel()
            output.close()
        }
    }

    @Test
    fun legendaryFilterUsesTheExistingMinimumRarityThreshold() = runBlocking {
        val selection = MutableStateFlow(HistoryFilter.LEGENDARY)
        val requestedThreshold = CompletableDeferred<Int>()
        val output = Channel<List<String>>(Channel.UNLIMITED)
        val collector = launch {
            selectedHistoryScans<String>(
                selection = selection,
                allScans = { error("All scans should not be queried") },
                rareScans = { threshold ->
                    requestedThreshold.complete(threshold)
                    MutableSharedFlow<List<String>>()
                },
                isShiny = { false }
            ).collect(output::send)
        }

        try {
            assertEquals(80, requestedThreshold.await())
        } finally {
            collector.cancel()
            output.close()
        }
    }
}
