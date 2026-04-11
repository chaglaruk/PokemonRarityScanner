package com.pokerarity.scanner.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.pokerarity.scanner.data.model.Pokemon

class ClipboardService(private val context: Context) {

    fun copyScanResult(pokemon: Pokemon) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val payload = buildString {
            append(pokemon.name)
            append(" | CP ").append(pokemon.cp)
            pokemon.hp?.let { append(" | HP ").append(it) }
            pokemon.ivText?.let { append(" | IV ").append(it) }
            append(" | ").append(pokemon.rarityTierLabel)
            append(" | Score ").append(pokemon.rarityScore)
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("scan_result", payload))
    }
}
