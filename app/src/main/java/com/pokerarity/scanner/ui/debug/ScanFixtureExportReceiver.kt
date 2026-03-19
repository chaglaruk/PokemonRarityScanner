package com.pokerarity.scanner.ui.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanFixtureExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val caseId = intent.getStringExtra(EXTRA_CASE_ID)?.trim().orEmpty()
            .ifBlank { defaultCaseId() }

        val cacheScans = context.cacheDir
            .listFiles { file -> file.name.startsWith("scan_") && file.name.endsWith(".png") }
            ?.sortedBy { it.name }
            .orEmpty()

        if (cacheScans.isEmpty()) {
            Log.w(TAG, "No scan_*.png files found in cache to export")
            return
        }

        val exportRoot = File(
            context.getExternalFilesDir(null),
            "fixtures/$caseId"
        ).apply { mkdirs() }

        val exportedFiles = cacheScans.map { file ->
            val target = File(exportRoot, file.name)
            file.copyTo(target, overwrite = true)
            target
        }

        val manifest = JSONArray().apply {
            exportedFiles.forEach { file ->
                put(
                    JSONObject()
                        .put("id", "$caseId/${file.nameWithoutExtension}")
                        .put("assetPath", "scan_fixtures/$caseId/${file.name}")
                        .put("strict", false)
                        .put("notes", "Fill expected values after manual label verification.")
                        .put(
                            "expected",
                            JSONObject()
                                .put("species", JSONObject.NULL)
                                .put("cp", JSONObject.NULL)
                                .put("hp", JSONObject.NULL)
                                .put("maxHp", JSONObject.NULL)
                                .put("shiny", JSONObject.NULL)
                                .put("lucky", JSONObject.NULL)
                                .put("costume", JSONObject.NULL)
                                .put("locationCard", JSONObject.NULL)
                                .put("datePresent", JSONObject.NULL)
                        )
                )
            }
        }

        File(exportRoot, "manifest_template.json").writeText(manifest.toString(2))

        Log.i(
            TAG,
            "Exported ${exportedFiles.size} fixture files to ${exportRoot.absolutePath}"
        )
    }

    private fun defaultCaseId(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    companion object {
        private const val TAG = "ScanFixtureExport"
        const val EXTRA_CASE_ID = "case_id"
    }
}
