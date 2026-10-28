// Purpose: Render scan results into shareable image assets.
package com.pokerarity.scanner.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.ivEstimateDisplayText
import com.pokerarity.scanner.data.model.ivLevelDisplayText
import com.pokerarity.scanner.data.model.ivLevelSummaryText
import com.pokerarity.scanner.data.model.scanReviewPresentation
import com.pokerarity.scanner.data.model.valuableSummary
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

internal data class ShareConfidenceCardContent(
    val title: String,
    val label: String,
    val detail: String
)

object ResultShareRenderer {
    private const val CARD_WIDTH = 1200
    private const val CARD_HEIGHT = 1600
    private const val STAT_BOX_WIDTH = 236f
    private const val STAT_LEVEL_X = 876f
    private const val FORM_LABEL_TEXT_SIZE = 30f
    private const val FORM_LABEL_TEXT_ALPHA = 220
    private const val FORM_LABEL_MIN_TEXT_SIZE = 18f
    private const val FORM_LABEL_MAX_WIDTH = 1008f
    private const val FORM_LABEL_X = 96f
    private const val FORM_LABEL_BASELINE = 420f
    private const val CHIP_ROW_START_Y = 455f
    private const val CAUGHT_BOX_WIDTH_WITH_CONFIDENCE = 420f
    private const val CONFIDENCE_BOX_X = 536f
    private const val CONFIDENCE_BOX_WIDTH = 568f
    private const val CONFIDENCE_BOX_HEIGHT = 116f
    private const val CONFIDENCE_BOX_RADIUS = 28f
    private const val CONFIDENCE_HORIZONTAL_INSET = 28f
    private const val CONFIDENCE_TITLE_TEXT_SIZE = 19f
    private const val CONFIDENCE_TITLE_MIN_TEXT_SIZE = 15f
    private const val CONFIDENCE_TITLE_BASELINE = 30f
    private const val CONFIDENCE_LABEL_TEXT_SIZE = 26f
    private const val CONFIDENCE_LABEL_MIN_TEXT_SIZE = 17f
    private const val CONFIDENCE_LABEL_BASELINE = 63f
    private const val CONFIDENCE_DETAIL_TEXT_SIZE = 18f
    private const val CONFIDENCE_DETAIL_MIN_TEXT_SIZE = 14f
    private const val CONFIDENCE_DETAIL_BASELINE = 94f
    private const val CONFIDENCE_DETAIL_TEXT_ALPHA = 205

    internal fun buildShareText(context: Context, pokemon: Pokemon): String {
        val baseText = context.getString(R.string.share_result_text, pokemon.name, pokemon.rarityScore)
        val identityText = appendFormPresentationToShareText(baseText, pokemon.formPresentationText)
        val estimateText = appendEstimateToShareText(identityText, pokemon.ivLevelSummaryText)
        return appendScanConfidenceToShareText(estimateText, pokemon.decisionSupport)
    }

    internal fun appendFormPresentationToShareText(baseText: String, formPresentationText: String?): String {
        val formText = formPresentationText?.trim()?.takeIf { it.isNotEmpty() } ?: return baseText
        return "${baseText.trimEnd().removeSuffix(".")} ($formText)."
    }

    internal fun appendEstimateToShareText(baseText: String, estimateSummary: String?): String {
        val summary = estimateSummary?.takeIf { it.isNotBlank() } ?: return baseText
        val sentence = baseText.trimEnd().removeSuffix(".")
        return sentence + " (" + summary + ")."
    }

    internal fun appendScanConfidenceToShareText(
        baseText: String,
        decisionSupport: ScanDecisionSupport?
    ): String = decisionSupport
        ?.takeIf { it.scanConfidenceLabel.isNotBlank() || it.scanConfidenceDetail.isNotBlank() }
        ?.scanReviewPresentation()
        ?.let { review ->
            val body = listOf(review.label, review.detail).filter { it.isNotBlank() }.joinToString(" • ")
            if (body.isBlank()) {
                baseText
            } else {
                val sentence = baseText.trimEnd().removeSuffix(".")
                "$sentence (${review.title}: $body)."
            }
        } ?: baseText

    internal fun buildShareConfidenceCardContent(
        decisionSupport: ScanDecisionSupport?,
        locale: Locale = Locale.getDefault()
    ): ShareConfidenceCardContent? {
        val support = decisionSupport
            ?.takeIf { it.scanConfidenceLabel.isNotBlank() || it.scanConfidenceDetail.isNotBlank() }
            ?: return null
        val review = support.scanReviewPresentation(locale)
        return ShareConfidenceCardContent(review.title, review.label, review.detail)
            .takeIf { it.label.isNotBlank() || it.detail.isNotBlank() }
    }

    fun captureViewToImageUri(
        context: Context,
        view: View?,
        fileName: String = "scan_result.png"
    ): Uri? {
        if (view == null || view.width <= 0 || view.height <= 0) return null

        return runCatching {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            val uri = writeBitmap(context, bitmap, fileName)
            bitmap.recycle()
            uri
        }.getOrNull()
    }

    fun renderPokemonCardToImageUri(
        context: Context,
        pokemon: Pokemon,
        fileName: String = "scan_result_card.png"
    ): Uri? {
        return runCatching {
            val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawBackground(canvas, pokemon)
            drawHeader(canvas, context, pokemon)
            drawIdentity(canvas, pokemon)
            drawStats(canvas, pokemon)
            drawValueSection(canvas, pokemon)
            drawFooter(canvas)
            val uri = writeBitmap(context, bitmap, fileName)
            bitmap.recycle()
            uri
        }.getOrNull()
    }

    private fun drawBackground(canvas: Canvas, pokemon: Pokemon) {
        val accent = tierColor(pokemon.rarityTierCode)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                CARD_WIDTH.toFloat(),
                CARD_HEIGHT.toFloat(),
                intArrayOf(Color.parseColor("#07080A"), Color.parseColor("#111827"), Color.parseColor("#050506")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), backgroundPaint)

        val card = RectF(54f, 54f, CARD_WIDTH - 54f, CARD_HEIGHT - 54f)
        canvas.drawRoundRect(card, 54f, 54f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#101216")
        })
        canvas.drawRoundRect(card, 54f, 54f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(52, 255, 255, 255)
        })
        canvas.drawCircle(980f, 240f, 260f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(32, Color.red(accent), Color.green(accent), Color.blue(accent))
        })
    }

    private fun drawHeader(canvas: Canvas, context: Context, pokemon: Pokemon) {
        val accent = tierColor(pokemon.rarityTierCode)
        val bar = RectF(82f, 82f, CARD_WIDTH - 82f, 248f)
        canvas.drawRoundRect(bar, 38f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                bar.left,
                bar.top,
                bar.right,
                bar.bottom,
                intArrayOf(accent, Color.parseColor("#E3350D")),
                null,
                Shader.TileMode.CLAMP
            )
        })
        canvas.drawText("POKERARITY SCANNER", 116f, 152f, textPaint(38f, Color.WHITE, true))
        canvas.drawText("Pokemon GO rarity readout", 116f, 202f, textPaint(28f, Color.argb(210, 255, 255, 255), true))
        ContextCompat.getDrawable(context, R.drawable.pokeball_overlay)
            ?.toBitmap(132, 132)
            ?.let { canvas.drawBitmap(it, CARD_WIDTH - 236f, 98f, null) }
    }

    private fun drawIdentity(canvas: Canvas, pokemon: Pokemon) {
        val namePaint = textPaint(76f, Color.WHITE, true)
        fitText(namePaint, pokemon.name, 640f, 48f)
        canvas.drawText(pokemon.name.ifBlank { "Unknown Pokemon" }, 96f, 370f, namePaint)
        drawScorePanel(canvas, pokemon)

        pokemon.formPresentationText?.let { formText ->
            val formPaint = textPaint(FORM_LABEL_TEXT_SIZE, Color.argb(FORM_LABEL_TEXT_ALPHA, 255, 255, 255), true)
            fitText(formPaint, formText, FORM_LABEL_MAX_WIDTH, FORM_LABEL_MIN_TEXT_SIZE)
            canvas.drawText(formText, FORM_LABEL_X, FORM_LABEL_BASELINE, formPaint)
        }

        var x = 96f
        var y = CHIP_ROW_START_Y
        val chips = (pokemon.tags.ifEmpty { listOf("SCAN RESULT") } + pokemon.shinyDisplayText.uppercase(Locale.US) +
            pokemon.rarityTierLabel.uppercase(Locale.US))
            .distinct()
            .take(7)
        chips.forEach { chip ->
            if (x + chipWidth(chip) > CARD_WIDTH - 96f) {
                x = 96f
                y += 58f
            }
            x = drawChip(canvas, chip, x, y) + 12f
        }
    }

    private fun drawScorePanel(canvas: Canvas, pokemon: Pokemon) {
        val panel = RectF(830f, 286f, 1104f, 510f)
        canvas.drawRoundRect(panel, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#171A20")
        })
        canvas.drawRoundRect(panel, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(50, 255, 255, 255)
        })
        drawCenteredText(canvas, "SCORE", panel, 354f, textPaint(24f, Color.argb(180, 255, 255, 255), true))
        drawCenteredText(canvas, pokemon.rarityScore.coerceAtLeast(0).toString(), panel, 436f, textPaint(78f, Color.WHITE, true))
        drawCenteredText(canvas, pokemon.rarityTierLabel, panel, 482f, textPaint(28f, tierColor(pokemon.rarityTierCode), true))
    }

    private fun drawStats(canvas: Canvas, pokemon: Pokemon) {
        val top = 590f
        drawStatBox(canvas, "CP", pokemon.cpDisplayText, 96f, top, STAT_BOX_WIDTH)
        drawStatBox(canvas, "HP", pokemon.hp?.takeIf { it > 0 }?.toString() ?: "-", 356f, top, STAT_BOX_WIDTH)
        drawStatBox(canvas, "IV", pokemon.ivEstimateDisplayText ?: "-", 616f, top, STAT_BOX_WIDTH)
        drawStatBox(canvas, "LEVEL", pokemon.ivLevelDisplayText ?: "-", STAT_LEVEL_X, top, STAT_BOX_WIDTH)
        val caughtTop = top + 158f
        val confidence = buildShareConfidenceCardContent(pokemon.decisionSupport)
        if (confidence == null) {
            drawStatBox(canvas, "CAUGHT", pokemon.caughtDate.ifBlank { "Unknown" }, 96f, caughtTop, 756f)
        } else {
            drawStatBox(
                canvas,
                "CAUGHT",
                pokemon.caughtDate.ifBlank { "Unknown" },
                96f,
                caughtTop,
                CAUGHT_BOX_WIDTH_WITH_CONFIDENCE
            )
            drawConfidenceBox(canvas, confidence, CONFIDENCE_BOX_X, caughtTop, CONFIDENCE_BOX_WIDTH)
        }
    }

    private fun drawConfidenceBox(
        canvas: Canvas,
        content: ShareConfidenceCardContent,
        x: Float,
        y: Float,
        width: Float
    ) {
        val rect = RectF(x, y, x + width, y + CONFIDENCE_BOX_HEIGHT)
        canvas.drawRoundRect(
            rect,
            CONFIDENCE_BOX_RADIUS,
            CONFIDENCE_BOX_RADIUS,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#171A20") }
        )

        val title = content.title.uppercase(Locale.getDefault())
        val availableTextWidth = width - CONFIDENCE_HORIZONTAL_INSET * 2f
        val titlePaint = textPaint(CONFIDENCE_TITLE_TEXT_SIZE, Color.argb(150, 255, 255, 255), true)
        fitText(titlePaint, title, availableTextWidth, CONFIDENCE_TITLE_MIN_TEXT_SIZE)
        canvas.drawText(title, x + CONFIDENCE_HORIZONTAL_INSET, y + CONFIDENCE_TITLE_BASELINE, titlePaint)

        val labelPaint = textPaint(CONFIDENCE_LABEL_TEXT_SIZE, Color.WHITE, true)
        fitText(labelPaint, content.label, availableTextWidth, CONFIDENCE_LABEL_MIN_TEXT_SIZE)
        canvas.drawText(content.label, x + CONFIDENCE_HORIZONTAL_INSET, y + CONFIDENCE_LABEL_BASELINE, labelPaint)

        val detailPaint = textPaint(
            CONFIDENCE_DETAIL_TEXT_SIZE,
            Color.argb(CONFIDENCE_DETAIL_TEXT_ALPHA, 255, 255, 255),
            true
        )
        fitText(detailPaint, content.detail, availableTextWidth, CONFIDENCE_DETAIL_MIN_TEXT_SIZE)
        if (detailPaint.measureText(content.detail) > availableTextWidth) {
            val visibleDetail = ellipsize(content.detail, detailPaint, availableTextWidth)
            canvas.drawText(
                visibleDetail,
                x + CONFIDENCE_HORIZONTAL_INSET,
                y + CONFIDENCE_DETAIL_BASELINE,
                detailPaint
            )
        } else if (content.detail.isNotBlank()) {
            canvas.drawText(
                content.detail,
                x + CONFIDENCE_HORIZONTAL_INSET,
                y + CONFIDENCE_DETAIL_BASELINE,
                detailPaint
            )
        }
    }

    private fun drawValueSection(canvas: Canvas, pokemon: Pokemon) {
        val section = RectF(96f, 930f, CARD_WIDTH - 96f, 1410f)
        canvas.drawRoundRect(section, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0C0E12")
        })
        canvas.drawText("WHY IT'S VALUABLE", section.left + 36f, section.top + 72f, textPaint(30f, tierColor(pokemon.rarityTierCode), true))
        val bodyPaint = textPaint(40f, Color.argb(238, 255, 255, 255), true)
        val lines = wrapText(pokemon.valuableSummary(), bodyPaint, section.width() - 72f).take(8).toMutableList()
        if (lines.size == 8) {
            lines[7] = ellipsize(lines[7], bodyPaint, section.width() - 72f)
        }
        var y = section.top + 138f
        lines.forEach { line ->
            canvas.drawText(line, section.left + 36f, y, bodyPaint)
            y += 54f
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val paint = textPaint(26f, Color.argb(150, 255, 255, 255), true)
        canvas.drawText("Generated by PokeRarityScanner", 96f, 1490f, paint)
    }

    private fun drawStatBox(canvas: Canvas, label: String, value: String, x: Float, y: Float, width: Float) {
        val rect = RectF(x, y, x + width, y + 116f)
        canvas.drawRoundRect(rect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#171A20")
        })
        canvas.drawText(label, x + 28f, y + 42f, textPaint(22f, Color.argb(150, 255, 255, 255), true))
        val valuePaint = textPaint(38f, Color.WHITE, true)
        fitText(valuePaint, value, width - 56f, 24f)
        canvas.drawText(value, x + 28f, y + 88f, valuePaint)
    }

    private fun drawChip(canvas: Canvas, text: String, x: Float, y: Float): Float {
        val paint = textPaint(26f, Color.WHITE, true)
        val width = chipWidth(text)
        val rect = RectF(x, y, x + width, y + 42f)
        canvas.drawRoundRect(rect, 21f, 21f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, 255, 255, 255)
        })
        canvas.drawText(text, x + 21f, y + 29f, paint)
        return rect.right
    }

    private fun chipWidth(text: String): Float =
        textPaint(26f, Color.WHITE, true).measureText(text) + 42f

    private fun drawCenteredText(canvas: Canvas, text: String, rect: RectF, baseline: Float, paint: Paint) {
        val x = rect.left + (rect.width() - paint.measureText(text)) / 2f
        canvas.drawText(text, x, baseline, paint)
    }

    private fun writeBitmap(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(shareDir, fileName)
        FileOutputStream(outFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    private fun textPaint(size: Float, colorValue: Int, bold: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorValue
            textSize = size
            isFakeBoldText = bold
        }

    private fun fitText(paint: Paint, text: String, maxWidth: Float, minSize: Float) {
        while (paint.textSize > minSize && paint.measureText(text) > maxWidth) {
            paint.textSize -= 2f
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        var candidate = text.trimEnd()
        while (candidate.length > 3 && paint.measureText("$candidate...") > maxWidth) {
            candidate = candidate.dropLast(1).trimEnd()
        }
        return "$candidate..."
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        var current = ""
        text.split(Regex("\\s+")).forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) lines += current
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

    private fun tierColor(tierCode: String): Int {
        return when (tierCode.uppercase(Locale.US)) {
            "GOD_TIER" -> Color.parseColor("#FF5FBF")
            "MYTHICAL" -> Color.parseColor("#C084FC")
            "LEGENDARY" -> Color.parseColor("#F59E0B")
            "EPIC" -> Color.parseColor("#7C3AED")
            "RARE" -> Color.parseColor("#2563EB")
            "UNCOMMON" -> Color.parseColor("#10B981")
            else -> Color.parseColor("#E3350D")
        }
    }
}
