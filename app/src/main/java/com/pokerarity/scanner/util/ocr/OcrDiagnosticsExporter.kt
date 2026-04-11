package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.PokemonData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object OcrDiagnosticsExporter {

    data class Bundle(
        val directory: String,
        val files: Map<String, String>
    )

    fun export(
        context: Context,
        screenshotPath: String?,
        diagnosticId: String,
        pokemon: PokemonData,
        solve: IvSolveDetails?,
        whyNotExact: String?
    ): Bundle? {
        if (screenshotPath.isNullOrBlank()) return null
        val source = File(screenshotPath)
        if (!source.exists() || !source.isFile) return null
        val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return null
        return try {
            val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "iv_diagnostics")
            val dir = File(root, diagnosticId).apply { mkdirs() }
            val files = linkedMapOf<String, String>()
            val crops = listOf(
                "cp" to ScreenRegions.REGION_CP,
                "hp" to ScreenRegions.REGION_HP,
                "hp_alt" to ScreenRegions.REGION_HP_ALT,
                "hp_lower" to ScreenRegions.REGION_HP_LOWER,
                "power_up_row" to ScreenRegions.REGION_POWER_UP_ROW,
                "power_up_row_alt" to ScreenRegions.REGION_POWER_UP_ROW_ALT,
                "power_up_row_wide" to ScreenRegions.REGION_POWER_UP_ROW_WIDE,
                "power_up_stardust" to ScreenRegions.REGION_POWER_UP_STARDUST,
                "power_up_stardust_alt" to ScreenRegions.REGION_POWER_UP_STARDUST_ALT,
                "power_up_stardust_wide" to ScreenRegions.REGION_POWER_UP_STARDUST_WIDE,
                "power_up_candy" to ScreenRegions.REGION_POWER_UP_CANDY,
                "power_up_candy_alt" to ScreenRegions.REGION_POWER_UP_CANDY_ALT,
                "power_up_candy_wide" to ScreenRegions.REGION_POWER_UP_CANDY_WIDE,
                "power_up_fallback" to ScreenRegions.REGION_STARDUST
            )
            crops.forEach { (name, region) ->
                runCatching {
                    val crop = ImagePreprocessor.cropRegion(bitmap, region)
                    try {
                        val file = File(dir, "$name.png")
                        FileOutputStream(file).use { output ->
                            crop.compress(Bitmap.CompressFormat.PNG, 100, output)
                        }
                        files[name] = file.absolutePath
                    } finally {
                        crop.recycle()
                    }
                }.onFailure { error ->
                    Log.w("OcrDiagnosticsExporter", "Failed to export $name crop", error)
                }
            }

            val summary = JSONObject().apply {
                val rawFields = JSONObject()
                pokemon.rawOcrText.split("|").forEach { part ->
                    val separator = part.indexOf(':')
                    if (separator > 0) {
                        rawFields.put(part.substring(0, separator), part.substring(separator + 1))
                    }
                }
                put("screenshotPath", source.absolutePath)
                put("cp", pokemon.cp)
                put("hp", pokemon.hp)
                put("maxHp", pokemon.maxHp)
                put("stardust", pokemon.stardust)
                put("powerUpCandyCost", pokemon.powerUpCandyCost)
                put("powerUpCandySource", pokemon.powerUpCandySource)
                put("powerUpStardustSource", pokemon.powerUpStardustSource)
                put("arcLevel", pokemon.arcLevel)
                put("cpOcrStatus", if (pokemon.cp != null) "parsed" else "missing")
                put(
                    "hpOcrStatus",
                    when {
                        pokemon.maxHp != null -> "max_hp_parsed"
                        pokemon.hp != null -> "current_hp_only"
                        else -> "missing"
                    }
                )
                put("rawOcrText", pokemon.rawOcrText)
                put("ivSolveMode", solve?.ivSolveMode?.name)
                put("ivCandidateCount", solve?.ivCandidateCount)
                put("levelMin", solve?.levelMin)
                put("levelMax", solve?.levelMax)
                put("signalsUsed", JSONArray(solve?.ivSolveSignalsUsed ?: emptyList<String>()))
                put("whyNotExact", whyNotExact)
                put("ocrFields", rawFields)
                put("selectedSources", JSONObject().apply {
                    put("powerUpStardust", pokemon.powerUpStardustSource)
                    put("powerUpCandy", pokemon.powerUpCandySource)
                })
            }
            val summaryFile = File(dir, "summary.json")
            summaryFile.writeText(summary.toString(2))
            files["summary"] = summaryFile.absolutePath

            Bundle(directory = dir.absolutePath, files = files)
        } finally {
            bitmap.recycle()
        }
    }
}
