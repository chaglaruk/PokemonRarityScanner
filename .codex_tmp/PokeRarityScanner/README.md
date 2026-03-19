# PokeRarity Scanner — Android (Jetpack Compose)

## Proje Yapısı

```
app/src/main/java/com/pokerarity/scanner/
├── MainActivity.kt                    ← NavHost + enter/exit transitions
├── data/model/
│   └── Pokemon.kt                     ← Data class + samplePokemon listesi
└── ui/
    ├── theme/
    │   ├── Theme.kt                   ← MaterialTheme, renk paleti, TypeColors
    │   └── Typography.kt             ← Outfit font family, text stilleri
    ├── components/
    │   ├── Components.kt             ← ScoreRing, StatCard, IvCard, Badge'ler
    │   └── PokemonListCard.kt        ← Liste kartı + entry animasyonu
    └── screens/
        ├── CollectionScreen.kt       ← Ana liste + filtre + stat header
        └── ScanResultScreen.kt       ← Detay ekranı + tüm animasyonlar
```

---

## Kurulum

### 1. Font dosyaları
`app/src/main/res/font/` klasörüne ekle:
- `outfit_regular.ttf`
- `outfit_medium.ttf`
- `outfit_semibold.ttf`
- `outfit_bold.ttf`
- `outfit_extrabold.ttf`
- `outfit_black.ttf`

İndir: https://fonts.google.com/specimen/Outfit → "Download family"

### 2. Edge-to-edge (AMOLED tam siyah)
`MainActivity.kt` içinde `enableEdgeToEdge()` zaten çağrılıyor.
`AndroidManifest.xml`'e şunu ekle:
```xml
<activity
    android:windowSoftInputMode="adjustResize"
    android:exported="true">
```

---

## Animasyon Mimarisi — Performans Notları

Tüm animasyonlar yalnızca **`alpha`** ve **`translationX/Y`** property'lerini kullanır.
Bu değerler `graphicsLayer {}` bloğu içinde set edilir — Compose bunu GPU'ya offload eder,
CPU veya layout pass tetiklenmez.

```kotlin
// ✅ GPU-safe — layout tetiklemez
.graphicsLayer {
    alpha        = fadeAnim.value
    translationY = slideAnim.value
}

// ❌ Kaçın — her frame'de layout yeniden hesaplar
.offset(y = slideAnim.value.dp)     // dp conversion → layout pass
.padding(top = paddingAnim.value.dp)
```

### Animasyon özeti

| Eleman              | Tip              | Easing              | Süre  | Delay |
|---------------------|------------------|---------------------|-------|-------|
| Renk şeridi         | fade + translateY| FastOutSlowIn       | 700ms | 100ms |
| Nav bar             | fade             | Linear              | 500ms | 300ms |
| Hero (ad + eyebrow) | fade + translateY| FastOutSlowIn       | 500ms | 450ms |
| Tag pill'ler        | fade             | Linear              | 400ms | 600ms |
| Skor sayacı         | Animatable float | FastOutSlowIn       | 900ms | 700ms |
| Bottom sheet        | fade + translateY| FastOutSlowIn       | 600ms | 550ms |
| Stat kartları       | fade + translateY| FastOutSlowIn       | 400ms | 750ms |
| Analiz satırları    | fade + translateX| FastOutSlowIn       | 350ms | stagger 80ms |
| Liste kartları      | fade + translateY| FastOutSlowIn       | 350ms | stagger 70ms |
| Score ring arc      | drawArc sweep    | FastOutSlowIn       | 900ms | per-card delay |

### InfiniteTransition (Scan butonu)
Pulse animasyonu `rememberInfiniteTransition` ile yapılıyor — `RepeatMode.Reverse`.
Bu sadece scale değiştirir, hiç layout hesabı yok.

---

## Tür → Renk Eşlemesi

```kotlin
// Theme.kt içinde PokemonType object'ini genişletebilirsin
"poison"  -> TypeColors(Color(0xFF9B59B6), ...)
"ground"  -> TypeColors(Color(0xFFE8C97A), ...)
"flying"  -> TypeColors(Color(0xFF89A4E2), ...)
// vs.
```

Her Pokémon kartında sol kenar çizgisi, detay ekranındaki şerit,
"Save" butonu ve analiz nokta rengi otomatik olarak bu renkten türetilir.

---

## Yeni Pokémon Eklemek

`Pokemon.kt` içindeki `samplePokemon` listesine ekle:

```kotlin
Pokemon(
    id          = 6,
    name        = "Gengar",
    cp          = 2878,
    hp          = 160,
    iv          = 82,
    rarityScore = 44,
    rarity      = Rarity.RARE,
    type        = "ghost",        // → Theme.kt'de TypeColors tanımla
    displayDate = "Mar 17, 2026",
    caughtDate  = "Mar 17, 2026",
    tags        = listOf("RARE"),
    analysis    = listOf(
        RarityAnalysisItem("Ghost type bonus", "+15", true),
        RarityAnalysisItem("Good IVs", "+18", true),
    )
)
```

---

## Gereksinimler

- Android Studio Ladybug (2024.2.1+)
- Kotlin 2.0+
- minSdk 26 (Android 8.0)
- Compose BOM 2024.12.01
