package com.pokerarity.scanner.ui.theme

import androidx.compose.ui.graphics.Color

// ── Overlay şerit renkleri ────────────────────────────────────
// Hero gradient her zaman bu renklerden oluşur.
// Pokémon tipinden bağımsız → oyun arka planıyla çakışma yok.
val StripeStart = Color(0xFFFF5500)   // turuncu
val StripeMid   = Color(0xFFE0003C)   // kırmızı
val StripeEnd   = Color(0xFF9900CC)   // mor

// Mevcut Theme.kt'ye sadece bu 3 satırı ekle.
// AccentGold ve AccentGreen zaten Theme.kt'de tanımlı, tekrar ekleme.
