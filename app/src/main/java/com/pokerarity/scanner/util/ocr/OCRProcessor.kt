package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.pokerarity.scanner.data.model.PokemonData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class OCRProcessor(private val context: Context) {

    private val textParser = TextParser(context)
    private var isInitialized = false
    private var tess: TessBaseAPI? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        try {
            val dataPath = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
            val tessDataDir = File(dataPath, "tessdata")
            if (!tessDataDir.exists()) tessDataDir.mkdirs()
            val trainedDataFile = File(tessDataDir, "eng.traineddata")
            if (!trainedDataFile.exists()) {
                context.assets.open("tessdata/eng.traineddata").use { i ->
                    FileOutputStream(trainedDataFile).use { o -> i.copyTo(o) }
                }
            }
            val t = TessBaseAPI()
            if (t.init(dataPath, "eng")) {
                tess = t
                isInitialized = true
                Log.d("OCRProcessor", "Tesseract OK")
            } else {
                Log.e("OCRProcessor", "Tesseract init FAILED")
            }
        } catch (e: Exception) {
            Log.e("OCRProcessor", "init error", e)
        }
    }

    suspend fun ensureInitialized() = initialize()

    fun release() {
        tess?.end()
        tess = null
        isInitialized = false
    }

    suspend fun processImage(bitmap: Bitmap, includeSecondaryFields: Boolean = true): PokemonData = withContext(Dispatchers.Default) {
        if (!isInitialized || tess == null) initialize()
        val t0 = System.currentTimeMillis()
        val procWM = ImagePreprocessor.processWhiteMask(bitmap)
        var procHC: Bitmap? = null
        fun hc(): Bitmap {
            if (procHC == null) {
                procHC = ImagePreprocessor.processHighContrast(bitmap)
            }
            return procHC!!
        }

        try {
            val cpRaw = regionBlock(procWM, ScreenRegions.REGION_CP, "CP", "CP0123456789 ")
            var cpParsed = textParser.parseCP(cpRaw)
            val shouldUseCpHighContrast = cpParsed == null || !cpRaw.uppercase().contains("CP")
            if (shouldUseCpHighContrast) {
                val cpRawHC = regionBlock(hc(), ScreenRegions.REGION_CP, "CP_HC", "CP0123456789 ")
                val cpParsedHC = textParser.parseCP(cpRawHC)
                if (cpParsed == null || cpParsedHC != null) {
                    cpParsed = cpParsedHC ?: cpParsed
                }
            }

            val hpRaw = if (includeSecondaryFields) region(hc(), ScreenRegions.REGION_HP, "HP", "HP0123456789/ ") else ""
            val hpRawWM = if (includeSecondaryFields) region(procWM, ScreenRegions.REGION_HP, "HP_WM", "HP0123456789/ ") else ""
            val hpCleanRaw = if (includeSecondaryFields) candyRegionProcessed(bitmap, ScreenRegions.REGION_HP, "HPClean", false) else ""
            val hpBlockRaw = if (includeSecondaryFields) candyRegionProcessed(bitmap, ScreenRegions.REGION_HP, "HPBlock", true) else ""
            val hpRawAlt = if (includeSecondaryFields) region(hc(), ScreenRegions.REGION_HP_ALT, "HP_ALT", "HP0123456789/ ") else ""
            val hpRawWMAlt = if (includeSecondaryFields) region(procWM, ScreenRegions.REGION_HP_ALT, "HP_ALT_WM", "HP0123456789/ ") else ""
            val hpCleanAlt = if (includeSecondaryFields) candyRegionProcessed(bitmap, ScreenRegions.REGION_HP_ALT, "HPCleanAlt", false) else ""
            val hpBlockAlt = if (includeSecondaryFields) candyRegionProcessed(bitmap, ScreenRegions.REGION_HP_ALT, "HPBlockAlt", true) else ""
            val hpParsed = textParser.parseHPPair(hpRaw, hpRawWM, hpCleanRaw, hpBlockRaw, hpRawAlt, hpRawWMAlt, hpCleanAlt, hpBlockAlt)

            val luckyLabelRaw = if (includeSecondaryFields) {
                region(hc(), ScreenRegions.REGION_LUCKY_LABEL, "LuckyLabel", "ABCDEFGHIJKLMNOPQRSTUVWXYZ ")
            } else ""
            val luckyLabelCleanRaw = if (includeSecondaryFields) {
                candyRegionProcessed(bitmap, ScreenRegions.REGION_LUCKY_LABEL, "LuckyLabelClean", false)
            } else ""
            val luckyDetected = if (includeSecondaryFields) {
                textParser.parseLuckyLabel(luckyLabelRaw, luckyLabelCleanRaw)
            } else false

            val nameRaw = region(procWM, ScreenRegions.REGION_NAME, "Name", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
            var nameFallbackRaw = if (textParser.parseName(nameRaw) == null) {
                region(hc(), ScreenRegions.REGION_NAME, "NameHC", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
            } else {
                ""
            }
            // Strict mask fallback: rejects colored Pokemon body pixels (Lugia, Shadow etc.)
            if (textParser.parseName(nameRaw) == null && textParser.parseName(nameFallbackRaw) == null) {
                val procStrict = ImagePreprocessor.processWhiteMaskStrict(bitmap)
                try {
                    val nameStrictRaw = region(procStrict, ScreenRegions.REGION_NAME, "NameStrict", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
                    if (textParser.parseName(nameStrictRaw) != null) {
                        nameFallbackRaw = nameStrictRaw
                    }
                } finally {
                    if (!procStrict.isRecycled) procStrict.recycle()
                }
            }

            val parsedName = textParser.parseName(nameRaw) ?: textParser.parseName(nameFallbackRaw)
            val topNameScore = maxOf(
                textParser.rankNameCandidates(nameRaw, limit = 1).firstOrNull()?.score ?: 0.0,
                textParser.rankNameCandidates(nameFallbackRaw, limit = 1).firstOrNull()?.score ?: 0.0
            )
            val shouldRunFastCandyRescue = !includeSecondaryFields && (
                parsedName == null || topNameScore < 0.78
            )

            val candyRaw = if (includeSecondaryFields) {
                region(hc(), ScreenRegions.REGION_CANDY, "Candy", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
            } else if (shouldRunFastCandyRescue) {
                region(hc(), ScreenRegions.REGION_CANDY, "Candy", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
            } else ""
            var candyCleanRaw = ""
            var candyBlockRaw = ""
            var candyFallbackRaw = ""
            var candyFallbackCleanRaw = ""
            var candyName = if (includeSecondaryFields || shouldRunFastCandyRescue) textParser.parseCandyName(candyRaw) else null

            if (shouldRunFastCandyRescue && candyName == null) {
                candyBlockRaw = candyRegionProcessed(bitmap, ScreenRegions.REGION_CANDY, "CandyBlock", true)
                candyName = textParser.parseCandyName(candyRaw, candyBlockRaw)
            }

            if (includeSecondaryFields && candyName == null) {
                candyCleanRaw = candyRegionProcessed(bitmap, ScreenRegions.REGION_CANDY, "CandyClean", false)
                candyName = textParser.parseCandyName(candyRaw, candyCleanRaw)
            }
            if (includeSecondaryFields && candyName == null) {
                candyBlockRaw = candyRegionProcessed(bitmap, ScreenRegions.REGION_CANDY, "CandyBlock", true)
                candyName = textParser.parseCandyName(candyRaw, candyCleanRaw, candyBlockRaw)
            }
            if (includeSecondaryFields && candyName == null) {
                candyFallbackRaw = region(hc(), ScreenRegions.REGION_CANDY_WIDE, "CandyWide", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
                candyName = textParser.parseCandyName(candyRaw, candyCleanRaw, candyBlockRaw, candyFallbackRaw)
            }
            if (includeSecondaryFields && candyName == null) {
                candyFallbackCleanRaw = candyRegionProcessed(bitmap, ScreenRegions.REGION_CANDY_WIDE, "CandyWideClean", false)
                candyName = textParser.parseCandyNameLoose(
                    candyRaw,
                    candyCleanRaw,
                    candyBlockRaw,
                    candyFallbackRaw,
                    candyFallbackCleanRaw
                )
            }

            val dynamicBadgeRect = ImagePreprocessor.detectOrangeBadge(bitmap)
            val badgeRaw = if (dynamicBadgeRect != null) {
                regionFromRect(procWM, dynamicBadgeRect, "BadgeDynamic", "0123456789/. \n")
            } else {
                region(procWM, ScreenRegions.REGION_DATE_BADGE, "BadgeFixed", "0123456789/. \n")
            }
            val badgeHighContrastRaw = if (dynamicBadgeRect != null) {
                regionFromRect(hc(), dynamicBadgeRect, "BadgeDynamicHC", "0123456789/. \n")
            } else {
                region(hc(), ScreenRegions.REGION_DATE_BADGE, "BadgeFixedHC", "0123456789/. \n")
            }
            val badgeFixedRaw = region(procWM, ScreenRegions.REGION_DATE_BADGE, "BadgeFixedWM", "0123456789/. \n")
            var badgeBinaryRaw = ""
            var badgeDirectRaw = ""
            val bottomRaw = if (includeSecondaryFields) region(hc(), ScreenRegions.REGION_DATE_BOTTOM, "Bottom", "") else ""

            val weightRaw = if (includeSecondaryFields) region(hc(), ScreenRegions.REGION_WEIGHT, "Weight", "") else ""
            val heightRaw = if (includeSecondaryFields) region(hc(), ScreenRegions.REGION_HEIGHT, "Height", "") else ""

            val powerUpStardustRaw = if (includeSecondaryFields) {
                region(hc(), ScreenRegions.REGION_POWER_UP_STARDUST, "PowerUpStardustRaw", "0123456789, ")
            } else ""
            val powerUpStardustClean = if (includeSecondaryFields) {
                numericRegionProcessed(bitmap, ScreenRegions.REGION_POWER_UP_STARDUST, "PowerUpStardustClean")
            } else ""
            val powerUpRowRaw = if (includeSecondaryFields) {
                region(hc(), ScreenRegions.REGION_POWER_UP_ROW, "PowerUpRowRaw", "0123456789, ")
            } else ""
            val powerUpRowClean = if (includeSecondaryFields) {
                numericRegionProcessed(bitmap, ScreenRegions.REGION_POWER_UP_ROW, "PowerUpRowClean")
            } else ""
            val powerUpStardustFallbackRaw = if (includeSecondaryFields) {
                region(hc(), ScreenRegions.REGION_STARDUST, "PowerUpStardustFallbackRaw", "0123456789, ")
            } else ""
            val powerUpStardustFallbackClean = if (includeSecondaryFields) {
                numericRegionProcessed(bitmap, ScreenRegions.REGION_STARDUST, "PowerUpStardustFallbackClean")
            } else ""

            val powerUpCandyRaw = if (includeSecondaryFields) {
                region(hc(), ScreenRegions.REGION_POWER_UP_CANDY, "PowerUpCandyRaw", "0123456789 ")
            } else ""
            val powerUpCandyClean = if (includeSecondaryFields) {
                numericRegionProcessed(bitmap, ScreenRegions.REGION_POWER_UP_CANDY, "PowerUpCandyClean")
            } else ""
            val powerUpCandyFallbackRaw = powerUpStardustFallbackRaw
            val powerUpCandyFallbackClean = powerUpStardustFallbackClean
            val parsedDedicatedCandy = textParser.parsePowerUpCandyCost(powerUpCandyRaw, powerUpCandyClean)
            val parsedDedicatedStardust = textParser.parsePowerUpStardust(powerUpStardustRaw, powerUpStardustClean)
            val parsedRowPair = textParser.parsePowerUpCostPair(powerUpRowRaw, powerUpRowClean)
            val parsedFallbackPair = textParser.parsePowerUpCostPairStrict(powerUpStardustFallbackRaw, powerUpStardustFallbackClean)
            val allowFallbackPair = parsedRowPair == null && parsedDedicatedStardust == null && parsedDedicatedCandy == null
            val parsedFallbackStardust = parsedFallbackPair?.first?.takeIf { allowFallbackPair && parsedFallbackPair.second != null }
            val parsedFallbackCandy = parsedFallbackPair?.second?.takeIf { allowFallbackPair }
            val parsedStardust = parsedRowPair?.first ?: parsedDedicatedStardust ?: parsedFallbackStardust
            val powerUpStardustSource = when {
                parsedRowPair?.first != null -> "row_pair"
                parsedDedicatedStardust != null -> "dedicated"
                parsedFallbackStardust != null -> "fallback_broad_pair"
                else -> null
            }
            val powerUpCandyCost = parsedRowPair?.second ?: parsedDedicatedCandy ?: parsedFallbackCandy
            val powerUpCandySource = when {
                parsedRowPair?.second != null -> "row_pair"
                parsedDedicatedCandy != null -> "dedicated"
                parsedFallbackCandy != null -> "fallback_broad_pair"
                else -> null
            }

            val arcLevel = ImagePreprocessor.detectArcLevel(bitmap)
            val nameParsed = textParser.parseName(nameRaw) ?: textParser.parseName(nameFallbackRaw)
            val displayName = nameParsed ?: candyName ?: "Unknown"
            val realName = nameParsed ?: candyName
            val genderParsed = textParser.parseGender(nameRaw) ?: textParser.parseGender(nameFallbackRaw)
            val sizeTag = textParser.parseSizeTag(weightRaw) ?: textParser.parseSizeTag(heightRaw)
            var dateParsed = textParser.parseDate(badgeRaw, badgeFixedRaw, badgeHighContrastRaw)
            if (dateParsed == null) {
                badgeBinaryRaw = badgeRegionProcessed(bitmap, dynamicBadgeRect, "BadgeBinary")
                dateParsed = textParser.parseDate(badgeBinaryRaw)
            }
            if (dateParsed == null) {
                badgeDirectRaw = badgeRegionDirect(bitmap, dynamicBadgeRect, "BadgeDirect")
                dateParsed = textParser.parseDate(badgeDirectRaw)
            }
            if (dateParsed == null && includeSecondaryFields) {
                dateParsed = textParser.parseBottomDate(bottomRaw)
                    ?: textParser.parseDate(bottomRaw)
            }

            val elapsed = System.currentTimeMillis() - t0
            Log.d(
                "OCRProcessor",
                """
|=== OCR $elapsed ms ===
|CP raw='$cpRaw' -> ${cpParsed}
|HP raw='$hpRaw' -> $hpParsed
|HPWM raw='$hpRawWM'
|HPClean raw='$hpCleanRaw'
|HPBlock raw='$hpBlockRaw'
|HPAlt raw='$hpRawAlt'
|HPAltWM raw='$hpRawWMAlt'
|HPCleanAlt raw='$hpCleanAlt'
|HPBlockAlt raw='$hpBlockAlt'
|LuckyLabel raw='$luckyLabelRaw'
|LuckyLabelClean raw='$luckyLabelCleanRaw'
|LuckyDetected -> $luckyDetected
|Candy raw='$candyRaw'
|CandyClean raw='$candyCleanRaw'
|CandyBlock raw='$candyBlockRaw'
|CandyWide raw='$candyFallbackRaw'
|CandyWideClean raw='$candyFallbackCleanRaw'
|Candy -> $candyName
|Name raw='$nameRaw'
|NameHC raw='$nameFallbackRaw'
|DisplayName -> $displayName
|Badge raw='$badgeRaw'
|BadgeWM raw='$badgeFixedRaw'
|BadgeHC raw='$badgeHighContrastRaw'
|BadgeBinary raw='$badgeBinaryRaw'
|BadgeDirect raw='$badgeDirectRaw'
|Bottom raw='$bottomRaw'
|PowerUpStardustRaw raw='$powerUpStardustRaw'
|PowerUpStardustClean raw='$powerUpStardustClean'
|PowerUpRowRaw raw='$powerUpRowRaw'
|PowerUpRowClean raw='$powerUpRowClean'
|PowerUpRowParsed -> $parsedRowPair
|PowerUpStardustFallbackRaw raw='$powerUpStardustFallbackRaw'
|PowerUpStardustFallbackClean raw='$powerUpStardustFallbackClean'
|PowerUpStardustParsed -> $parsedStardust
|PowerUpStardustSource -> $powerUpStardustSource
|PowerUpCandyRaw raw='$powerUpCandyRaw'
|PowerUpCandyClean raw='$powerUpCandyClean'
|PowerUpCandyFallbackRaw raw='$powerUpCandyFallbackRaw'
|PowerUpCandyFallbackClean raw='$powerUpCandyFallbackClean'
|PowerUpCandyParsed -> $powerUpCandyCost
|PowerUpCandySource -> $powerUpCandySource
|Date -> $dateParsed
|RealName -> $realName
|NameRaw -> $nameRaw
|NameFallback -> $nameFallbackRaw
                """.trimMargin()
            )

            PokemonData(
                cp = cpParsed,
                hp = hpParsed?.first,
                maxHp = hpParsed?.second,
                name = displayName,
                realName = realName,
                candyName = candyName,
                megaEnergy = textParser.parseMegaEnergy(candyFallbackRaw),
                weight = textParser.parseWeight(weightRaw),
                height = textParser.parseHeight(heightRaw),
                gender = genderParsed,
                stardust = parsedStardust,
                arcLevel = arcLevel,
                caughtDate = dateParsed,
                rawOcrText = buildString {
                    append("CP:").append(cpRaw)
                    append("|HP:").append(hpRaw)
                    append("|HPWM:").append(hpRawWM)
                    append("|HPClean:").append(hpCleanRaw)
                    append("|HPBlock:").append(hpBlockRaw)
                    append("|HPAlt:").append(hpRawAlt)
                    append("|HPAltWM:").append(hpRawWMAlt)
                    append("|HPCleanAlt:").append(hpCleanAlt)
                    append("|HPBlockAlt:").append(hpBlockAlt)
                    append("|LuckyLabel:").append(luckyLabelRaw)
                    append("|LuckyLabelClean:").append(luckyLabelCleanRaw)
                    append("|LuckyDetected:").append(luckyDetected)
                    append("|Candy:").append(candyRaw)
                    append("|CandyClean:").append(candyCleanRaw)
                    append("|CandyBlock:").append(candyBlockRaw)
                    append("|CandyWide:").append(candyFallbackRaw)
                    append("|CandyWideClean:").append(candyFallbackCleanRaw)
                    append("|Name:").append(nameRaw)
                    append("|NameHC:").append(nameFallbackRaw)
                    append("|Badge:").append(badgeRaw)
                    append("|BadgeWM:").append(badgeFixedRaw)
                    append("|BadgeHC:").append(badgeHighContrastRaw)
                    append("|BadgeBinary:").append(badgeBinaryRaw)
                    append("|BadgeDirect:").append(badgeDirectRaw)
                    append("|Bottom:").append(bottomRaw)
                    append("|PowerUpStardustRaw:").append(powerUpStardustRaw)
                    append("|PowerUpStardustClean:").append(powerUpStardustClean)
                    append("|PowerUpRowRaw:").append(powerUpRowRaw)
                    append("|PowerUpRowClean:").append(powerUpRowClean)
                    append("|PowerUpRowParsed:").append(parsedRowPair)
                    append("|PowerUpStardustFallbackRaw:").append(powerUpStardustFallbackRaw)
                    append("|PowerUpStardustFallbackClean:").append(powerUpStardustFallbackClean)
                    append("|PowerUpStardustParsed:").append(parsedStardust)
                    append("|PowerUpStardustSource:").append(powerUpStardustSource)
                    append("|Stardust:").append(parsedStardust)
                    append("|PowerUpCandyRaw:").append(powerUpCandyRaw)
                    append("|PowerUpCandyClean:").append(powerUpCandyClean)
                    append("|PowerUpCandyFallbackRaw:").append(powerUpCandyFallbackRaw)
                    append("|PowerUpCandyFallbackClean:").append(powerUpCandyFallbackClean)
                    append("|PowerUpCandy:").append(powerUpCandyCost)
                    append("|PowerUpCandySource:").append(powerUpCandySource)
                    append("|BadgeType:").append(if (dynamicBadgeRect != null) "Dynamic" else "Fixed")
                    append("|SizeTag:").append(sizeTag)
                },
                powerUpCandyCost = powerUpCandyCost,
                powerUpCandySource = powerUpCandySource,
                powerUpStardustSource = powerUpStardustSource
            )
        } finally {
            if (!procWM.isRecycled) procWM.recycle()
            procHC?.let {
                if (!it.isRecycled) it.recycle()
            }
        }
    }

    private fun candyRegionProcessed(bitmap: Bitmap, region: ScreenRegions.Region, label: String, block: Boolean): String {
        if (bitmap.isRecycled) {
            Log.e("OCRProcessor", "candyRegionProcessed $label skipped: source bitmap is recycled")
            return ""
        }
        val rawCrop = try {
            ImagePreprocessor.cropRegion(bitmap, region)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "crop candy $label", e)
            return ""
        }
        val processed = try {
            ImagePreprocessor.processCandyText(rawCrop)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "process candy $label", e)
            if (!rawCrop.isRecycled) rawCrop.recycle()
            return ""
        }
        if (!rawCrop.isRecycled) rawCrop.recycle()

        return try {
            if (block) {
                readBitmap(processed, label, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
            } else {
                readBitmap(processed, label, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
            }
        } finally {
            if (!processed.isRecycled) processed.recycle()
        }
    }

    private fun numericRegionProcessed(bitmap: Bitmap, region: ScreenRegions.Region, label: String): String {
        if (bitmap.isRecycled) {
            Log.e("OCRProcessor", "numericRegionProcessed $label skipped: source bitmap is recycled")
            return ""
        }
        val rawCrop = try {
            ImagePreprocessor.cropRegion(bitmap, region)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "crop numeric $label", e)
            return ""
        }
        val processed = try {
            ImagePreprocessor.processStardust(rawCrop)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "process numeric $label", e)
            if (!rawCrop.isRecycled) rawCrop.recycle()
            return ""
        }
        if (!rawCrop.isRecycled) rawCrop.recycle()

        return try {
            var text = readBitmap(processed, label, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE, "0123456789, ")
            if (text.isBlank()) {
                text = readBitmap(processed, "${label}Block", TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, "0123456789, ")
            }
            if (text.isBlank()) {
                val scaled = Bitmap.createScaledBitmap(processed, processed.width * 2, processed.height * 2, true)
                try {
                    text = readBitmap(scaled, "${label}2x", TessBaseAPI.PageSegMode.PSM_SINGLE_LINE, "0123456789, ")
                } finally {
                    if (!scaled.isRecycled) scaled.recycle()
                }
            }
            text
        } finally {
            if (!processed.isRecycled) processed.recycle()
        }
    }

    private fun badgeRegionProcessed(bitmap: Bitmap, dynamicRect: android.graphics.Rect?, label: String): String {
        val rawCrop = try {
            dynamicRect?.let { cropBadgeRect(bitmap, it, focusTextOnly = true) }
                ?: ImagePreprocessor.cropRegion(bitmap, ScreenRegions.REGION_DATE_BADGE)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "crop badge $label", e)
            null
        } ?: return ""

        val processed = try {
            ImagePreprocessor.processDateBadge(rawCrop)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "process badge $label", e)
            if (!rawCrop.isRecycled) rawCrop.recycle()
            return ""
        }
        if (!rawCrop.isRecycled) rawCrop.recycle()

        return try {
            var text = readBitmap(processed, label, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, "0123456789/. ")
            if (text.isBlank()) {
                text = readBitmap(processed, "${label}Auto", TessBaseAPI.PageSegMode.PSM_AUTO, "0123456789/. ")
            }
            if (text.isBlank()) {
                val scaled = Bitmap.createScaledBitmap(processed, processed.width * 2, processed.height * 2, true)
                try {
                    text = readBitmap(scaled, "${label}Auto2x", TessBaseAPI.PageSegMode.PSM_AUTO, "0123456789/. ")
                } finally {
                    if (!scaled.isRecycled) scaled.recycle()
                }
            }
            text
        } finally {
            if (!processed.isRecycled) processed.recycle()
        }
    }

    private fun badgeRegionDirect(bitmap: Bitmap, dynamicRect: android.graphics.Rect?, label: String): String {
        val rawCrop = try {
            dynamicRect?.let { cropBadgeRect(bitmap, it, focusTextOnly = true) }
                ?: ImagePreprocessor.cropRegion(bitmap, ScreenRegions.REGION_DATE_BADGE)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "crop direct badge $label", e)
            null
        } ?: return ""

        val scaled = try {
            Bitmap.createScaledBitmap(rawCrop, rawCrop.width * 2, rawCrop.height * 2, true)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "scale direct badge $label", e)
            rawCrop
        }
        if (scaled !== rawCrop && !rawCrop.isRecycled) rawCrop.recycle()

        return try {
            readBitmap(scaled, label, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, "0123456789/. ")
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
    }

    private fun cropBadgeRect(
        bitmap: Bitmap,
        rect: android.graphics.Rect,
        focusTextOnly: Boolean
    ): Bitmap? {
        val safeLeft = rect.left.coerceIn(0, bitmap.width - 1)
        val safeTop = rect.top.coerceIn(0, bitmap.height - 1)
        val safeWidth = rect.width().coerceAtMost(bitmap.width - safeLeft)
        val safeHeight = rect.height().coerceAtMost(bitmap.height - safeTop)
        if (safeWidth <= 0 || safeHeight <= 0) return null

        return if (focusTextOnly && safeWidth >= 40) {
            val textLeft = safeLeft + (safeWidth * 0.34f).toInt()
            val textWidth = (safeWidth - (safeWidth * 0.34f).toInt()).coerceAtLeast(1)
            Bitmap.createBitmap(bitmap, textLeft.coerceAtMost(bitmap.width - 1), safeTop, textWidth.coerceAtMost(bitmap.width - textLeft), safeHeight)
        } else {
            Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
        }
    }

    private fun readBitmap(bitmap: Bitmap, label: String, pageSegMode: Int, whitelist: String? = null): String {
        if (bitmap.isRecycled) {
            Log.e("OCRProcessor", "readBitmap $label skipped: bitmap is recycled")
            return ""
        }
        val t = tess ?: return ""
        return synchronized(t) {
            t.setPageSegMode(pageSegMode)
            t.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, whitelist ?: "")
            t.setImage(bitmap)
            val txt = t.utF8Text?.trim() ?: ""
            Log.d("OCRProcessor", "[$label] '$txt'")
            txt
        }
    }

    private fun region(bitmap: Bitmap, r: ScreenRegions.Region, label: String, wl: String? = null): String {
        if (bitmap.isRecycled) {
            Log.e("OCRProcessor", "region $label skipped: source bitmap is recycled")
            return ""
        }
        val t = tess ?: return ""
        val cropped = try {
            ImagePreprocessor.cropRegion(bitmap, r)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "crop $label", e)
            return ""
        }
        return try {
            synchronized(t) {
                t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE)
                t.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, wl ?: "")
                t.setImage(cropped)
                val txt = t.utF8Text?.trim() ?: ""
                Log.d("OCRProcessor", "[$label] '$txt'")
                txt
            }
        } finally {
            if (cropped != bitmap && !cropped.isRecycled) {
                cropped.recycle()
            }
        }
    }

    private fun regionBlock(bitmap: Bitmap, r: ScreenRegions.Region, label: String, wl: String? = null): String {
        if (bitmap.isRecycled) {
            Log.e("OCRProcessor", "regionBlock $label skipped: source bitmap is recycled")
            return ""
        }
        val t = tess ?: return ""
        val cropped = try {
            ImagePreprocessor.cropRegion(bitmap, r)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "crop $label", e)
            return ""
        }
        return try {
            synchronized(t) {
                t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
                t.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, wl ?: "")
                t.setImage(cropped)
                val txt = t.utF8Text?.trim() ?: ""
                Log.d("OCRProcessor", "[$label] '$txt'")
                txt
            }
        } finally {
            if (cropped != bitmap && !cropped.isRecycled) {
                cropped.recycle()
            }
        }
    }

    private fun regionFromRect(bitmap: Bitmap, rect: android.graphics.Rect, label: String, wl: String? = null): String {
        if (bitmap.isRecycled) {
            Log.e("OCRProcessor", "regionFromRect $label skipped: source bitmap is recycled")
            return ""
        }
        val t = tess ?: return ""
        val cropped = try {
            val safeLeft = rect.left.coerceIn(0, bitmap.width - 1)
            val safeTop = rect.top.coerceIn(0, bitmap.height - 1)
            val safeWidth = rect.width().coerceAtMost(bitmap.width - safeLeft)
            val safeHeight = rect.height().coerceAtMost(bitmap.height - safeTop)
            if (safeWidth > 0 && safeHeight > 0) {
                Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("OCRProcessor", "crop rect $label", e)
            null
        } ?: return ""

        return try {
            synchronized(t) {
                t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
                t.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, wl ?: "")
                t.setImage(cropped)
                val txt = t.utF8Text?.trim() ?: ""
                Log.d("OCRProcessor", "[$label] '$txt'")
                txt
            }
        } finally {
            if (cropped != bitmap && !cropped.isRecycled) {
                cropped.recycle()
            }
        }
    }
}
