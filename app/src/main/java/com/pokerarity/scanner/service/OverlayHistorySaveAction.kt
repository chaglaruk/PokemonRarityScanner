package com.pokerarity.scanner.service

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pokerarity.scanner.R

@Composable
internal fun rememberHistorySaveAction(
    context: Context,
    requestToken: ScanRequestToken?,
    saveStatus: OverlayHistorySaveStatus,
    onSaved: () -> Unit
): () -> Unit {
    var saveRequested by remember(requestToken) { mutableStateOf(false) }
    LaunchedEffect(saveRequested, saveStatus) {
        if (!saveRequested || saveStatus == OverlayHistorySaveStatus.PENDING) return@LaunchedEffect
        when (saveStatus) {
            OverlayHistorySaveStatus.SAVED -> {
                Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
                onSaved()
            }
            OverlayHistorySaveStatus.FAILED ->
                Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
            OverlayHistorySaveStatus.UNKNOWN,
            OverlayHistorySaveStatus.UNTRACKED ->
                Toast.makeText(context, R.string.save_status_unavailable, Toast.LENGTH_SHORT).show()
            OverlayHistorySaveStatus.PENDING -> Unit
        }
        saveRequested = false
    }
    return {
        if (!saveRequested) {
            if (saveStatus == OverlayHistorySaveStatus.FAILED) {
                val retryScheduled = requestToken?.let(OverlayStateStore::requestHistorySaveRetry) == true
                if (retryScheduled) {
                    saveRequested = true
                    Toast.makeText(context, R.string.saving, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.save_status_unavailable, Toast.LENGTH_SHORT).show()
                }
            } else {
                saveRequested = true
                if (saveStatus == OverlayHistorySaveStatus.PENDING) {
                    Toast.makeText(context, R.string.saving, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
