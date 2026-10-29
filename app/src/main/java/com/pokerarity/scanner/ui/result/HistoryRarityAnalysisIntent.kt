package com.pokerarity.scanner.ui.result

import android.content.Intent
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.RarityAnalysisHistoryCodec

internal fun Intent.putRarityAnalysisExtras(scan: ScanHistoryEntity) {
    RarityAnalysisHistoryCodec.decode(scan.rarityAnalysisJson)?.let { analysis ->
        putStringArrayListExtra(ResultActivity.EXTRA_EXPLANATIONS, ArrayList(analysis.explanations))
        putStringArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_KEYS, ArrayList(analysis.breakdown.keys))
        putIntegerArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_VALUES, ArrayList(analysis.breakdown.values))
    }
}

internal fun ScanHistoryEntity.applyRarityAnalysis(intent: Intent): ScanHistoryEntity {
    val hasAnalysisExtras = intent.hasExtra(ResultActivity.EXTRA_EXPLANATIONS) ||
        intent.hasExtra(ResultActivity.EXTRA_BREAKDOWN_KEYS) ||
        intent.hasExtra(ResultActivity.EXTRA_BREAKDOWN_VALUES)
    if (!hasAnalysisExtras) return this

    val explanations = intent.getStringArrayListExtra(ResultActivity.EXTRA_EXPLANATIONS).orEmpty()
    val keys = intent.getStringArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_KEYS).orEmpty()
    val values = intent.getIntegerArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_VALUES).orEmpty()
    val breakdown = linkedMapOf<String, Int>()
    if (keys.size == values.size) keys.indices.forEach { breakdown[keys[it]] = values[it] }
    val serialized = RarityAnalysisHistoryCodec.encode(explanations, breakdown) ?: rarityAnalysisJson
    return if (serialized == rarityAnalysisJson) this else copy(rarityAnalysisJson = serialized)
}
