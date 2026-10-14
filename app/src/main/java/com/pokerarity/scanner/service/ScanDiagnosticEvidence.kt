package com.pokerarity.scanner.service

import android.util.Log
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.util.ocr.OcrDiagnosticsExporter
import kotlinx.coroutines.CancellationException

/** Optional PNG/crop diagnostics cannot veto publication, history or a retry decision. */
internal object ScanDiagnosticEvidence {
    data class Result(
        val bundle: OcrDiagnosticsExporter.Bundle?,
        val status: OwnedFrameHandoff.EvidenceStatus?
    )

    @Suppress("TooGenericExceptionCaught") // Optional exporter; cancellation remains control flow.
    fun export(path: String?, ownedFrames: Boolean, write: () -> OcrDiagnosticsExporter.Bundle?): Result {
        val status = if (ownedFrames) OwnedFrameHandoff.evidenceStatus(path) else null
        val result = if (status != null && status != OwnedFrameHandoff.EvidenceStatus.WRITTEN) {
            Result(null, status)
        } else {
            try {
                Result(write(), status)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Result(null, OwnedFrameHandoff.EvidenceStatus.EXPORT_FAILED)
            }
        }
        // Bounded enum only. No paths, screenshots, OCR or exception messages.
        if (BuildConfig.DEBUG && result.status != null) {
            Log.d("ScanDiagnosticEvidence", "optional_png_evidence=" + result.status.name)
        }
        return result
    }
}
