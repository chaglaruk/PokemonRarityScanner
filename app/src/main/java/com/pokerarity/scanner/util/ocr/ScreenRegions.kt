package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Referans ekran: 1080x2340px (Samsung, 450dpi)
 * 13 Mart 2026 - koordinatlar log analizine gore duzeltildi
 */
object ScreenRegions {

    data class Region(
        val topPercent: Float,
        val leftPercent: Float,
        val widthPercent: Float,
        val heightPercent: Float
    )

    data class Anchor(
        val top: Int,
        val bottom: Int
    )

    // CP: "CP 2880" - gradient arkaplan, beyaz metin
    // Piksel analizi: beyaz metin y=138-198px (5.9-8.5%) - whiteMask ile okunacak
    // PSM_SINGLE_BLOCK ile daha iyi sonuc
    val REGION_CP = Region(
        topPercent    = 0.055f,
        leftPercent   = 0.10f,
        widthPercent  = 0.80f,
        heightPercent = 0.04f
    )

    // Kullanici ismi - HP ile cakismamasi icin yukari cekildi
    val REGION_NAME = Region(
        topPercent    = 0.385f,
        leftPercent   = 0.10f,
        widthPercent  = 0.80f,
        heightPercent = 0.05f
    )

    // HP satiri lucky etiketinin hemen altinda; onceki crop biraz yukarida kalip
    // etiket/isim gurultusunu okuyordu.
    val REGION_HP = Region(
        topPercent    = 0.456f,
        leftPercent   = 0.16f,
        widthPercent  = 0.68f,
        heightPercent = 0.055f
    )

    // HP fallback: same device layout but slightly lower/wider to catch slash pairs when
    // the primary crop clips the left digit or the trailing "HP" text.
    val REGION_HP_ALT = Region(
        topPercent    = 0.450f,
        leftPercent   = 0.14f,
        widthPercent  = 0.72f,
        heightPercent = 0.060f
    )

    // HP tertiary sweep: catches slightly lower cards where the left digit clips in the
    // primary crops but the slash pair is still fully visible.
    val REGION_HP_LOWER = Region(
        topPercent    = 0.464f,
        leftPercent   = 0.13f,
        widthPercent  = 0.74f,
        heightPercent = 0.062f
    )

    // Lucky etiketi: kart ustundeki "LUCKY POKEMON" satiri
    val REGION_LUCKY_LABEL = Region(
        topPercent    = 0.405f,
        leftPercent   = 0.22f,
        widthPercent  = 0.44f,
        heightPercent = 0.035f
    )

    // Candy: BIRINCIL tur kaynagi - "SNORLAX CANDY" / "SNORLAX CANDY XL"
    // Piksel analizi: metin y=1505-1540 (64.5-65.8%) - sol=ikon, metin ortada
    val REGION_CANDY = Region(
        topPercent    = 0.640f,
        leftPercent   = 0.28f,
        widthPercent  = 0.65f,
        heightPercent = 0.045f
    )

    // Candy fallback: biraz daha genis - tam isabetle gelmezse
    val REGION_CANDY_WIDE = Region(
        topPercent    = 0.620f,
        leftPercent   = 0.20f,
        widthPercent  = 0.75f,
        heightPercent = 0.085f
    )

    val REGION_MEGA_ENERGY = Region(
        topPercent    = 0.70f,
        leftPercent   = 0.20f,
        widthPercent  = 0.75f,
        heightPercent = 0.07f
    )

    val REGION_WEIGHT = Region(
        topPercent    = 0.52f,
        leftPercent   = 0.03f,
        widthPercent  = 0.40f,
        heightPercent = 0.05f
    )
    val REGION_HEIGHT = Region(
        topPercent    = 0.52f,
        leftPercent   = 0.55f,
        widthPercent  = 0.40f,
        heightPercent = 0.05f
    )

    // Power Up maliyeti (Stardust) - Ekranın en altındaki buton hizası
    val REGION_STARDUST = Region(
        topPercent    = 0.715f,
        leftPercent   = 0.44f,
        widthPercent  = 0.38f,
        heightPercent = 0.075f
    )

    val REGION_POWER_UP_STARDUST = Region(
        topPercent    = 0.730f,
        leftPercent   = 0.48f,
        widthPercent  = 0.23f,
        heightPercent = 0.045f
    )

    val REGION_POWER_UP_STARDUST_ALT = Region(
        topPercent    = 0.758f,
        leftPercent   = 0.46f,
        widthPercent  = 0.25f,
        heightPercent = 0.048f
    )

    val REGION_POWER_UP_STARDUST_WIDE = Region(
        topPercent    = 0.736f,
        leftPercent   = 0.44f,
        widthPercent  = 0.28f,
        heightPercent = 0.052f
    )

    val REGION_POWER_UP_CANDY = Region(
        topPercent    = 0.726f,
        leftPercent   = 0.68f,
        widthPercent  = 0.22f,
        heightPercent = 0.045f
    )

    val REGION_POWER_UP_CANDY_ALT = Region(
        topPercent    = 0.756f,
        leftPercent   = 0.66f,
        widthPercent  = 0.24f,
        heightPercent = 0.048f
    )

    val REGION_POWER_UP_CANDY_WIDE = Region(
        topPercent    = 0.736f,
        leftPercent   = 0.63f,
        widthPercent  = 0.28f,
        heightPercent = 0.052f
    )

    // Shared power-up cost row. This gives the parser one stable OCR pass for
    // "1,900 2" style rows when the narrow dedicated crops miss a leading digit.
    val REGION_POWER_UP_ROW = Region(
        topPercent    = 0.724f,
        leftPercent   = 0.48f,
        widthPercent  = 0.42f,
        heightPercent = 0.055f
    )

    val REGION_POWER_UP_ROW_ALT = Region(
        topPercent    = 0.752f,
        leftPercent   = 0.45f,
        widthPercent  = 0.46f,
        heightPercent = 0.060f
    )

    val REGION_POWER_UP_ROW_WIDE = Region(
        topPercent    = 0.736f,
        leftPercent   = 0.43f,
        widthPercent  = 0.50f,
        heightPercent = 0.064f
    )

    // Tarih rozeti (turuncu oval): piksel analizi x=848-1062, y=840-940
    // Normalize: left=77.6%, top=35.7%, width=21.7%, height=4.7%
    val REGION_DATE_BADGE = Region(
        topPercent    = 0.357f,
        leftPercent   = 0.776f,
        widthPercent  = 0.217f,
        heightPercent = 0.047f
    )

    val REGION_DATE_BOTTOM = Region(
        topPercent    = 0.82f,
        leftPercent   = 0.05f,
        widthPercent  = 0.90f,
        heightPercent = 0.12f
    )

    fun detectAppraisalBox(bitmap: Bitmap): Anchor? {
        val width = bitmap.width
        val height = bitmap.height
        val searchTop = (height * 0.62f).toInt()
        val searchBottom = (height * 0.86f).toInt()
        val left = (width * 0.14f).toInt()
        val right = (width * 0.94f).toInt()
        var bestTop = -1
        var bestBottom = -1
        var inBrightBand = false

        fun rowBrightness(y: Int): Float {
            var total = 0f
            var samples = 0
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                total += (0.299f * r + 0.587f * g + 0.114f * b)
                samples++
                x += 12
            }
            return if (samples == 0) 0f else total / samples
        }

        for (y in searchTop until searchBottom step 4) {
            val brightness = rowBrightness(y)
            val looksLikeCard = brightness in 145f..235f
            if (looksLikeCard && !inBrightBand) {
                bestTop = y
                inBrightBand = true
            } else if (!looksLikeCard && inBrightBand) {
                bestBottom = y
                break
            }
        }

        if (bestTop < 0 || bestBottom <= bestTop) return null
        return Anchor(bestTop, bestBottom)
    }

    fun getRectForRegion(bitmap: Bitmap, region: Region): Rect {
        val w = bitmap.width
        val h = bitmap.height
        val left   = (w * region.leftPercent).toInt()
        var top    = (h * region.topPercent).toInt()
        val right  = left + (w * region.widthPercent).toInt()
        val bottom = top  + (h * region.heightPercent).toInt()
        val anchor = detectAppraisalBox(bitmap)
        if (anchor != null && isAnchorSensitive(region)) {
            val expectedAnchorTop = (h * 0.70f).toInt()
            val delta = anchor.top - expectedAnchorTop
            val shiftFactor = if (region === REGION_HP || region === REGION_HP_ALT || region === REGION_HP_LOWER) 0.45f else 1.0f
            top += (delta * shiftFactor).toInt()
        }
        return Rect(
            left.coerceIn(0, w), top.coerceIn(0, h),
            right.coerceIn(0, w), (top + (h * region.heightPercent).toInt()).coerceIn(0, h)
        )
    }

    private fun isAnchorSensitive(region: Region): Boolean {
        return region === REGION_HP ||
            region === REGION_HP_ALT ||
            region === REGION_HP_LOWER ||
            region === REGION_STARDUST ||
            region === REGION_POWER_UP_STARDUST ||
            region === REGION_POWER_UP_STARDUST_ALT ||
            region === REGION_POWER_UP_STARDUST_WIDE ||
            region === REGION_POWER_UP_CANDY ||
            region === REGION_POWER_UP_CANDY_ALT ||
            region === REGION_POWER_UP_CANDY_WIDE ||
            region === REGION_POWER_UP_ROW ||
            region === REGION_POWER_UP_ROW_ALT ||
            region === REGION_POWER_UP_ROW_WIDE
    }
}
