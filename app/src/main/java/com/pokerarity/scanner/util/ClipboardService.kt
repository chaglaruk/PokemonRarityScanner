package com.pokerarity.scanner.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.pokerarity.scanner.data.model.Pokemon

class ClipboardService(private val context: Context) {

    fun copyScanResult(pokemon: Pokemon) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val payload = buildNicknameSafePayload(pokemon)
        clipboard.setPrimaryClip(ClipData.newPlainText("scan_result", payload))
    }

    companion object {
        internal const val MAX_POKEMON_GO_NICKNAME_LENGTH = 12

        internal fun buildNicknameSafePayload(pokemon: Pokemon): String {
            val scoreText = pokemon.rarityScore.coerceIn(0, 100).toString()
            val flags = buildString {
                if ("SHINY" in pokemon.tags) append('S')
                if ("COSTUME" in pokemon.tags) append('C')
                if ("FORM" in pokemon.tags) append('F')
                if ("SHADOW" in pokemon.tags) append('X')
                if ("LUCKY" in pokemon.tags) append('L')
                if (pokemon.caughtDate != "Unknown") append('O')
            }
            val budget = (MAX_POKEMON_GO_NICKNAME_LENGTH - scoreText.length - flags.length).coerceAtLeast(3)
            val safeName = pokemon.name
                .replace(Regex("[^A-Za-z0-9]"), "")
                .ifBlank { "Poke" }
                .take(budget)
            return (safeName + scoreText + flags)
                .take(MAX_POKEMON_GO_NICKNAME_LENGTH)
        }
    }
}
