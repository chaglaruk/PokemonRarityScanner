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
import com.pokerarity.scanner.data.model.OcrConfidenceReasons
import com.pokerarity.scanner.data.model.PokemonData
import java.io.File
import java.io.FileOutputStream

object OcrDiagnosticsExporter {
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

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
        whyNotExact: String?,
        scanReport: ScanDiagnosticReport? = null,
        confidenceReasons: OcrConfidenceReasons? = null
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
                "name" to ScreenRegions.REGION_NAME,
                "lucky_label" to ScreenRegions.REGION_LUCKY_LABEL,
                "candy" to ScreenRegions.REGION_CANDY,
                "candy_wide" to ScreenRegions.REGION_CANDY_WIDE,
                "date_badge" to ScreenRegions.REGION_DATE_BADGE,
                "date_bottom" to ScreenRegions.REGION_DATE_BOTTOM,
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
            ScreenRegions.detectAppraisalBox(bitmap)?.let { anchor ->
                runCatching {
                    val crop = Bitmap.createBitmap(
                        bitmap,
                        0,
                        anchor.top.coerceIn(0, bitmap.height - 1),
                        bitmap.width,
                        (anchor.bottom - anchor.top).coerceAtLeast(1).coerceAtMost(bitmap.height - anchor.top)
                    )
                    try {
                        val file = File(dir, "appraisal_box.png")
                        FileOutputStream(file).use { output ->
                            crop.compress(Bitmap.CompressFormat.PNG, 100, output)
                        }
                        files["appraisal_box"] = file.absolutePath
                    } finally {
                        crop.recycle()
                    }
                }.onFailure { error ->
                    Log.w("OcrDiagnosticsExporter", "Failed to export appraisal box crop", error)
                }
            }

            val summaryFile = File(dir, "summary.json")
            summaryFile.writeText(buildSummaryJson(source.absolutePath, pokemon, solve, whyNotExact, scanReport, confidenceReasons))
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
        whyNotExact: String?,
        scanReport: ScanDiagnosticReport? = null,
        confidenceReasons: OcrConfidenceReasons? = null
    ): String = buildSummaryJson(screenshotPath, pokemon, solve, whyNotExact, scanReport, confidenceReasons)

    private fun buildSummaryJson(
        screenshotPath: String,
        pokemon: PokemonData,
        solve: IvSolveDetails?,
        whyNotExact: String?,
        scanReport: ScanDiagnosticReport? = null,
        confidenceReasons: OcrConfidenceReasons? = null
    ): String {
        val rawFields = rawFieldMap(pokemon.rawOcrText)
        val trace = pokemon.variantDecisionTrace
        val species = pokemon.realName ?: pokemon.name ?: trace?.fullVariantSpecies ?: rawFields["FullVariantSpecies"] ?: trace?.classifierSpecies ?: rawFields["ClassifierSpecies"]
        val exportedReport = scanReport?.let {
            if (confidenceReasons == null || it.confidenceReasons.isNotEmpty()) {
                it
            } else {
                it.copy(confidenceReasons = ConfidenceReasonDiagnostic.from(confidenceReasons))
            }
        }
        return JsonObject().apply {
            addProperty("screenshotPath", screenshotPath)
            addProperty("species", species)
            addProperty("classifierSpecies", trace?.classifierSpecies ?: rawFields["ClassifierSpecies"] ?: species)
            addProperty("fullVariantSpecies", trace?.fullVariantSpecies ?: rawFields["FullVariantSpecies"] ?: species)
            addProperty("shiny", trace?.fullVariantShiny ?: rawFields["FullVariantShiny"]?.toBooleanStrictOrNull() ?: false)
            addProperty("costume", trace?.fullVariantCostume ?: rawFields["FullVariantCostume"]?.toBooleanStrictOrNull() ?: false)
            addProperty("form", trace?.fullVariantForm ?: rawFields["FullVariantForm"]?.toBooleanStrictOrNull() ?: false)
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
            add("stableOcrFields", stableOcrFields(rawFields, pokemon))
            add("resolverTrace", pokemon.speciesResolverTrace?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
            add("scanDecision", pokemon.scanDecision?.let { gson.toJsonTree(it) } ?: JsonNull.INSTANCE)
            if (exportedReport == null) {
                add("scanDiagnostics", JsonNull.INSTANCE)
            } else {
                add("scanDiagnostics", gson.toJsonTree(exportedReport))
            }
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

    private fun stableOcrFields(rawFields: Map<String, String>, pokemon: PokemonData): JsonObject {
        return JsonObject().apply {
            add("CP", stableField(rawFields["CP"], pokemon.cp?.toString(), detectorRun = true))
            add("HP", stableField(rawFields["HP"], hpValue(pokemon), detectorRun = true))
            add("Name", stableField(rawFields["Name"], pokemon.name, detectorRun = true))
            add("NameDynamic", stableField(rawFields["NameDynamic"], rawFields["NameDynamic"], detectorRun = rawFields.containsKey("NameDynamic")))
            add("NameHC", stableField(rawFields["NameHC"], rawFields["NameHC"], detectorRun = rawFields.containsKey("NameHC")))
            add("Candy", stableField(rawFields["Candy"], pokemon.candyName, detectorRun = rawFields.containsKey("Candy") || pokemon.candyName != null))
            add("Date", stableField(rawFields["Date"], pokemon.caughtDate?.time?.toString(), detectorRun = rawFields.containsKey("Date") || pokemon.caughtDate != null))
            add("SizeTag", stableField(rawFields["SizeTag"], rawFields["SizeTag"], detectorRun = rawFields.containsKey("SizeTag")))
            add("Stardust", stableField(rawFields["Stardust"], pokemon.stardust?.toString(), detectorRun = rawFields.containsKey("Stardust") || pokemon.stardust != null))
            add("Arc", stableField(rawFields["Arc"], pokemon.arcLevel?.toString(), detectorRun = rawFields.containsKey("Arc") || pokemon.arcLevel != null))
            add("AppraisalAttack", stableField(rawFields["AppraisalAttack"], pokemon.appraisalAttack?.toString(), detectorRun = rawFields.containsKey("AppraisalAttack") || pokemon.appraisalAttack != null))
            add("AppraisalDefense", stableField(rawFields["AppraisalDefense"], pokemon.appraisalDefense?.toString(), detectorRun = rawFields.containsKey("AppraisalDefense") || pokemon.appraisalDefense != null))
            add("AppraisalStamina", stableField(rawFields["AppraisalStamina"], pokemon.appraisalStamina?.toString(), detectorRun = rawFields.containsKey("AppraisalStamina") || pokemon.appraisalStamina != null))
            add("LuckyDetected", stableField(rawFields["LuckyDetected"], rawFields["LuckyDetected"], detectorRun = rawFields.containsKey("LuckyDetected")))
            add("RawText", stableField(pokemon.rawOcrText, pokemon.rawOcrText, detectorRun = true))
        }
    }

    private fun hpValue(pokemon: PokemonData): String? {
        val hp = pokemon.hp ?: return null
        return pokemon.maxHp?.let { "$hp/$it" } ?: hp.toString()
    }

    private fun stableField(raw: String?, value: String?, detectorRun: Boolean): JsonObject {
        val marker = raw?.trim()
        val cleanValue = value?.takeUnless(::isBlankOrMarker)
        val cleanRaw = raw?.takeUnless(::isBlankOrMarker)
        val status = when {
            marker.equals("not-run", ignoreCase = true) || marker.equals("skipped", ignoreCase = true) -> "not-run"
            cleanValue != null -> "found"
            detectorRun -> "missing"
            else -> "not-run"
        }
        return JsonObject().apply {
            addProperty("status", status)
            addNullableString("value", cleanValue)
            addNullableString("raw", cleanRaw)
        }
    }

    private fun isBlankOrMarker(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isBlank() ||
            trimmed.equals("missing", ignoreCase = true) ||
            trimmed.equals("skipped", ignoreCase = true) ||
            trimmed.equals("not-run", ignoreCase = true)
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
