# ScanResultOverlayCard — Tasarım Notu v3

## Ne değişti (önceki versiyona göre)

### Sorun 1 — Şerit arka planla kaynaşıyordu
Önceki: Şerit rengi `pokemon.typeColors`'dan geliyordu.
Gyarados (water = mavi) + Pokémon GO'nun mavi arka planı = görünmez şerit.

Çözüm: Şerit artık **sabit turuncu → kırmızı → mor** gradient.
Her Pokémon tipi için, her arka plan için okunabilir.

### Sorun 2 — Yazılar okunmuyordu
Şerit renginin arka planla kaynaşması hero alanındaki tüm metni
görünmez kılıyordu. Sabit şeridle birlikte + `rgba(0,0,0,0.18)` kaplama
eklendi → tüm metinler net.

### Tip rengi nerede kullanılıyor (v3)
| Eleman              | Kaynak            |
|---------------------|-------------------|
| Hero şerit          | StripeStart/Mid/End (sabit) |
| IV sol aksan çizgisi| `tc.primary`       |
| IV değeri rengi     | `tc.primary`       |
| Breakdown nokta     | StripeStart (sabit)|
| Puan rengi (+18 vb) | StripeStart (sabit)|
| Kaydet butonu       | StripeStart→Mid    |

## Dosyalar

```
ui/overlay/ScanResultOverlayCard.kt     ← ana composable
ui/components/overlay/OverlayComponents.kt  ← helper composable'lar
ui/theme/OverlayColors.kt               ← 3 yeni Color sabiti
```

## Pokemon model notu
`pokemon.hp` ve `pokemon.iv` artık nullable (`Int?`) olarak bekleniyor.
Değer null veya boş gelirse "—" gösterilir, uygulama çökmez.

Eğer mevcut modelin nullable değilse ScanResultOverlayCard.kt içindeki
şu satırları düzelt:
```kotlin
// nullable versiyon (önerilen):
OverlayStatCell("HP", pokemon.hp?.toString() ?: "—", ...)
Text(pokemon.iv?.let { "$it%" } ?: "—", ...)

// non-nullable versiyon:
OverlayStatCell("HP", pokemon.hp.toString(), ...)
Text("${pokemon.iv}%", ...)
```
