package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.PokemonData
import java.io.File
import java.io.FileOutputStream

object OcrDiagnosticsExporter {
    private val gson = GsonBuilder().setPrettyPrinting().create()

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

            val summaryFile = File(dir, "summary.json")
            summaryFile.writeText(buildSummaryJson(source.absolutePath, pokemon, solve, whyNotExact))
            files["summary"] = summaryFile.absolutePath

            Bundle(directory = dir.absolutePath, files = files)
        } finally {
            bitmap.recycle()
        }
    }

    internal fun buildSummaryJsonForTest(
        screenshotPath: String,
        pokemon: PokemonData,
        solve: IvSolveDetails?,
        whyNotExact: String?
    ): String = buildSummaryJson(screenshotPath, pokemon, solve, whyNotExact)

    private fun buildSummaryJson(
        screenshotPath: String,
        pokemon: PokemonData,
        solve: IvSolveDetails?,
        whyNotExact: String?
    ): String {
        val rawFields = rawFieldMap(pokemon.rawOcrText)
        val species = pokemon.realName ?: pokemon.name ?: rawFields["FullVariantSpecies"] ?: rawFields["ClassifierSpecies"]
        return JsonObject().apply {
            addProperty("screenshotPath", screenshotPath)
            addProperty("species", species)
            addProperty("classifierSpecies", rawFields["ClassifierSpecies"] ?: species)
            addProperty("fullVariantSpecies", rawFields["FullVariantSpecies"] ?: species)
            addProperty("shiny", rawFields["FullVariantShiny"]?.toBooleanStrictOrNull() ?: false)
            addProperty("costume", rawFields["FullVariantCostume"]?.toBooleanStrictOrNull() ?: false)
            addProperty("form", rawFields["FullVariantForm"]?.toBooleanStrictOrNull() ?: false)
            addNullableInt("cp", pokemon.cp)
            addNullableInt("hp", pokemon.hp)
            addNullableInt("maxHp", pokemon.maxHp)
            addNullableInt("stardust", pokemon.stardust)
            addNullableInt("powerUpCandyCost", pokemon.powerUpCandyCost)
            addNullableString("powerUpCandySource", pokemon.powerUpCandySource)
            addNullableString("powerUpStardustSource", pokemon.powerUpStardustSource)
            addNullableFloat("arcLevel", pokemon.arcLevel)
            addNullableFloat("arcEstimatedLevel", pokemon.arcEstimatedLevel)
            addNullableString("arcSource", pokemon.arcSource)
            addNullableInt("appraisalAttack", pokemon.appraisalAttack)
            addNullableInt("appraisalDefense", pokemon.appraisalDefense)
            addNullableInt("appraisalStamina", pokemon.appraisalStamina)
            addNullableFloat("appraisalConfidence", pokemon.appraisalConfidence)
            addProperty("cpOcrStatus", if (pokemon.cp != null) "parsed" else "missing")
            addProperty(
                "hpOcrStatus",
                when {
                    pokemon.maxHp != null -> "max_hp_parsed"
                    pokemon.hp != null -> "current_hp_only"
                    else -> "missing"
                }
            )
            addProperty("rawOcrText", pokemon.rawOcrText)
            addNullableString("ivSolveMode", solve?.ivSolveMode?.name)
            addNullableInt("ivCandidateCount", solve?.ivCandidateCount)
            addNullableFloat("levelMin", solve?.levelMin)
            addNullableFloat("levelMax", solve?.levelMax)
            add("signalsUsed", JsonArray().apply { (solve?.ivSolveSignalsUsed ?: emptyList()).forEach(::add) })
            addNullableString("whyNotExact", whyNotExact)
            add("ocrFields", JsonObject().apply { rawFields.forEach { (key, value) -> addProperty(key, value) } })
            add("selectedSources", JsonObject().apply {
                addNullableString("powerUpStardust", pokemon.powerUpStardustSource)
                addNullableString("powerUpCandy", pokemon.powerUpCandySource)
                addNullableString("arc", pokemon.arcSource)
            })
        }.let(gson::toJson)
    }

    private fun rawFieldMap(rawOcrText: String): LinkedHashMap<String, String> {
        val rawFields = linkedMapOf<String, String>()
        rawOcrText.split("|").forEach { part ->
            val separator = part.indexOf(':')
            if (separator > 0) {
                rawFields[part.substring(0, separator)] = part.substring(separator + 1)
            }
        }
        return rawFields
    }

    private fun JsonObject.addNullableString(key: String, value: String?) {
        if (value == null) add(key, JsonNull.INSTANCE) else addProperty(key, value)
    }

    private fun JsonObject.addNullableInt(key: String, value: Int?) {
        if (value == null) add(key, JsonNull.INSTANCE) else addProperty(key, value)
    }

    private fun JsonObject.addNullableFloat(key: String, value: Float?) {
        if (value == null) add(key, JsonNull.INSTANCE) else addProperty(key, value)
    }
}
