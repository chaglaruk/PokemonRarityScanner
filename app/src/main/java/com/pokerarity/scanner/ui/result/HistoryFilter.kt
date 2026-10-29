package com.pokerarity.scanner.ui.result

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal enum class HistoryFilter {
    ALL,
    RARE,
    SHINY,
    LEGENDARY
}

/** Keeps exactly one selected History query active as the user changes filters. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> selectedHistoryScans(
    selection: Flow<HistoryFilter>,
    allScans: () -> Flow<List<T>>,
    rareScans: (minimumRarityScore: Int) -> Flow<List<T>>,
    isShiny: (T) -> Boolean
): Flow<List<T>> = selection.flatMapLatest { filter ->
    when (filter) {
        HistoryFilter.ALL -> allScans()
        HistoryFilter.RARE -> rareScans(MINIMUM_RARE_SCORE)
        HistoryFilter.SHINY -> allScans().map { scans -> scans.filter(isShiny) }
        HistoryFilter.LEGENDARY -> rareScans(MINIMUM_LEGENDARY_SCORE)
    }
}

private const val MINIMUM_RARE_SCORE = 40
private const val MINIMUM_LEGENDARY_SCORE = 80
