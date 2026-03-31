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
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.model.Pokemon
import java.io.File
import java.io.FileOutputStream

object ResultShareRenderer {
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

            val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
            val outFile = File(shareDir, fileName)
            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            bitmap.recycle()

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        }.getOrNull()
    }

    fun renderPokemonCardToImageUri(
        context: Context,
        pokemon: Pokemon,
        fileName: String = "scan_result_card.png"
    ): Uri? {
        return runCatching {
            val width = 1080
            val height = 1350
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            canvas.drawColor(Color.parseColor("#07080A"))

            val cardRect = RectF(54f, 54f, width - 54f, height - 54f)
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    intArrayOf(Color.parseColor("#12141A"), Color.parseColor("#0B0D12")),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

            val accentRect = RectF(cardRect.left, cardRect.top, cardRect.right, cardRect.top + 140f)
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    accentRect.left,
                    accentRect.top,
                    accentRect.right,
                    accentRect.bottom,
                    intArrayOf(Color.parseColor("#E3350D"), Color.parseColor("#F97316")),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(accentRect, 48f, 48f, accentPaint)
            canvas.drawRect(accentRect.left, accentRect.top + 92f, accentRect.right, accentRect.bottom, accentPaint)

            val tierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 34f
                isFakeBoldText = true
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#DCE2EB")
                textSize = 30f
                isFakeBoldText = true
            }
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 68f
                isFakeBoldText = true
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E5E7EB")
                textSize = 34f
                isFakeBoldText = true
            }
            val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 26f
                isFakeBoldText = true
            }

            canvas.drawText(pokemon.rarityTierLabel, 96f, 140f, tierPaint)
            canvas.drawText("Score ${pokemon.rarityScore}", 96f, 188f, metaPaint)
            canvas.drawText(pokemon.name, 96f, 305f, namePaint)

            val stats = buildString {
                append("CP ${pokemon.cp}")
                pokemon.hp?.let { append("  •  HP $it") }
                append("  •  ${pokemon.caughtDate}")
            }
            canvas.drawText(stats, 96f, 360f, smallPaint)

            val explanation = pokemon.analysis.firstOrNull()?.title ?: ""
            val lines = wrapText(explanation, bodyPaint, width - 192f).take(10)
            var y = 460f
            lines.forEach { line ->
                canvas.drawText(line, 96f, y, bodyPaint)
                y += 46f
            }

            ContextCompat.getDrawable(context, R.drawable.pokeball_overlay)
                ?.toBitmap(160, 160)
                ?.let { canvas.drawBitmap(it, width - 260f, 110f, null) }

            val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
            val outFile = File(shareDir, fileName)
            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        }.getOrNull()
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }
}
