# POKEMON RARITY SCANNER - HANDOVER & PROJECT STATUS REPORT

## 1. Projenin Amacı
Pokemon GO overlay tarayıcısı. Ekrandaki Pokemon'un nadirlik skorunu çevrimdışı hesaplar.
Teknoloji: Kotlin, Tesseract OCR, dHash, TFLite.

## 2. Çalışma Dizini
`C:\Users\Caglar\Desktop\PokeRarityScanner`

## 3. Ekran Düzeni (1080x2340px - debug_regions.png ile doğrulandı)
- **CP**: top ~5.5-9.5%, orta — büyük beyaz bold metin, gradient arka plan
- **Kullanıcı ismi**: top ~41-48%, orta — GÜVENİLMEZ (kullanıcı "100" yazmış)
- **HP**: top ~44-49%, "257 / 257 HP"
- **Candy**: top ~62-72% — "SNORLAX CANDY" / "SNORLAX CANDY XL"
- **Tarih rozeti (turuncu oval)**: **x=848-1062px, y=840-940px** → left=77.6%, top=35.7%, width=21.7%, height=4.7%
  - GÖRSELDEKİ YER: Beyaz kartın sağ üst köşesi, Pokemon modelinin sağ alt bölümü hizası
  - Turuncu piksel analizi ile tam koordinat belirlendi

## 4. Mimari
- **MainActivity** → izinler
- **OverlayService** → floating Pokeball butonu
- **ScreenCaptureService** → ekran PNG cache
- **ScanManager** → broadcast pipeline
- **OCRProcessor** → Tesseract bölge OCR
- **TextParser** → regex + Levenshtein
- **RarityCalculator** → 6 faktörlü skor
- **ImagePreprocessor** → greyscale / highContrast / **whiteMask** (yeni)

## 5. Çözülen Sorunlar

### ✅ 12-13 Mart 2026 - Temel Altyapı + Rarity 2.0 + Hibrit OCR
(önceki oturumdan, detay için git log)

### ✅ 13 Mart 2026 ~17:00 - Koordinat Düzeltmeleri (2. oturum)
**debug_regions.png görseli ile koordinatlar görsel olarak doğrulandı.**

| Bölge | Eski | Yeni | Kaynak |
|---|---|---|---|
| REGION_CP | top=0.02, h=0.09 | top=0.055, h=0.04 | piksel analizi y=138-198 |
| REGION_NAME | top=0.40 | top=0.41 | görsel |
| REGION_DATE_BADGE | top=0.23, left=0.56 | top=0.357, left=0.776, w=0.217, h=0.047 | turuncu piksel analizi |
| REGION_CANDY | h=0.08 | h=0.10 | log analizi |

### ✅ 13 Mart 2026 ~17:00 - processWhiteMask() eklendi
**Sorun:** 3D hareketli arka plan OCR'u bozuyordu (CP `'7  1'` okuyordu)
**Çözüm:** `ImagePreprocessor.processWhiteMask()` — RGB>200 ve renk farkı<50 olan pikseller (beyaz metin) → siyah, geri kalan → beyaz. 3D animasyona karşı dayanıklı.
- CP ve Name bölgeleri artık `processWhiteMask` + `PSM_SINGLE_BLOCK` kullanıyor
- `regionBlock()` fonksiyonu eklendi OCRProcessor'a

## 6. Son Log Sonuçları (17:00 öncesi build)

| Alan | Sonuç | Durum |
|---|---|---|
| HP | 257/257 | ✅ |
| RealName | snorlax | ✅ (candy full-scan'den) |
| Date | Oct 13 2017 | ✅ (full-scan'den, badge hâlâ yanlıştı) |
| CP | 1310 | ❌ (doğru: 2880) |
| Candy bölgesi | 'sass g zen gs zsi' | ❌ (full-scan fallback ile snorlax bulundu) |

**Beklenen iyileşme (build sonrası):**
- CP: whiteMask + doğru koordinat → "CP2880" okunmalı
- Badge: piksel analizi koordinat → "2017 13/10" okunmalı
- Candy: koordinat zaten kapsamlıydı, whiteMask olmadan HC çalışıyor

## 7. Yapılacaklar

### 🔴 Kalan (build sonrası test gerekli)
1. CP ve Badge test — beklenen: 2880, 2017/13/10
2. Candy bölgesi: hâlâ gürültü geliyorsa top=0.645, h=0.04 ile daralt

### 🟡 Orta Öncelik
3. Name bölgesi: kullanıcı "100" yazmış — name artık candy'den geliyor (OK)
4. Candy preprocessing: whitelist'e sayı ekleme (XL için)

### 🟢 Düşük Öncelik
5. Advanced Rarity: CP/Level/IV faktörleri
6. Farklı ekran oranı desteği

## 8. Performans
- OCR: ~577ms (hedef <500ms, kabul edilebilir)
- HP doğruluğu: ✅ %100
- CP: ❌ düzeltme bekleniyor
- Candy/Name: ⚠️ fallback çalışıyor, bölge hâlâ gürültülü

## 9. Önemli Notlar
- **Candy is King**: realName her zaman candy bölgesinden geliyor
- **WhiteMask stratejisi**: Pokemon GO beyaz bold metin her zaman RGB>200, renk farkı<50
- **Build**: `.\gradlew.bat assembleDebug` ~26sn incremental
- **ADB**: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`
- **Cihaz**: RFCY11MX0TM (Samsung, 1080x2340, 450dpi)

---
*Son güncelleme: 13 Mart 2026 ~17:15 — 2. oturum koordinat + whiteMask*

## 13 Mart 2026 ~21:00 - İsim, Tarih ve Candy İyileştirmeleri (3. oturum)

### ✅ Yapılan Değişiklikler

1. **İsim Eşleşme Toleransı (Levenshtein) Artırıldı**:
   - `TextParser.kt`'de 5-7 karakterli isimler (Raichu, Espeon vb.) için tolerans `2`'den `3`'e çıkarıldı. Bu, `Raichufiz` gibi hatalı OCR çıktılarının `raichu` olarak eşleşmesini sağlar.
   
2. **Date Parse Esnekliği Artırıldı**:
   - `parseDate` içindeki regex'ler güncellendi. Artık ayırıcı olarak sadece `/` ve `.` değil, boşluk ve diğer karakterler de kabul ediliyor (`[\D\s]{1,3}`).
   - Yıl (2016-2026) tek başına yakalanırsa, ay ve gün bilinmese bile yılın ilk gününü (`01/01/YIL`) dönecek bir fallback eklendi.

3. **İsim OCR Modu Değiştirildi**:
   - `OCRProcessor.kt`'de `REGION_NAME` için `PSM_SINGLE_BLOCK` yerine `PSM_SINGLE_LINE` moduna geçildi. Tek satırlık isimlerin daha temiz okunması hedeflendi.

4. **Candy Regex Esnetildi**:
   - `parseCandyName` fonksiyonuna `CANDY` kelimesinin bozuk hallerini (`CNDY`, `CANOY`, `CANY` vb.) yakalayacak esnek regex eklendi.

### 🔴 Beklenen Sonuçlar (Test Edilmeli)
- **Raichu/Lugia**: Daha yüksek başarı oranı.
- **Date**: Rozet bölgesindeki gürültülü metinlerden en azından yılın yakalanması.
- **Candy**: "CANDY" kelimesi bozuk okunsa bile ismin candy bölgesinden çekilebilmesi.

---
*Son güncelleme: 13 Mart 2026 ~21:00 — 3. oturum iyileştirmeleri*

## 13 Mart 2026 ~21:30 - Log Analizi ve Yeni Bulgular (4. oturum)

### 📊 Son Tarama Sonuçları (Logcat Analizi)

| Pokemon | CP | HP | RealName | Badge | Durum |
|---------|----|----|----------|-------|-------|
| Cleffa? | null | 123/123 | cleffa | '3 5315' | ⚠️ (CP bozuk, isim NameRaw'dan geldi) |
| ? (3266) | 3266 | null | null | '3 3315' | ⚠️ (CP OK, diğerleri null) |
| ? (3834) | 3834 | 194/194 | null | '3 5315' | ⚠️ (CP/HP OK, isim bulunamadı) |

### 🔍 Tespit Edilen Sorunlar

1. **CP Okuma Hatası**: `1552373 5 75` gibi çok uzun veya gürültülü metinler geliyor. `whiteMask` CP bölgesinde bazen fazla gürültü üretiyor olabilir.
2. **İsim Bulma Sorunu**: `Hpolfiff` (muhtemelen bir Pokemon) ve `Lifiifm` gibi çıktılar `Unknown` dönüyor. Levenshtein toleransı 3 olmasına rağmen eşleşmiyor.
3. **Badge (Tarih) Gürültüsü**: Badge raw çıktıları hep `3 5315`, `3 3315` gibi geliyor. Bu bölge ya çok küçük ya da `whiteMask` burada iyi çalışmıyor.
4. **Candy Bölgesi**: Candy hala çok gürültülü (`o s mos Q lZQ`), isim oradan hiç gelmiyor.

### ✅ Yapılan/Yapılacak Aksiyonlar
- **Tarih Fallback**: Badge okunamadığında `Bottom` bölgesinden tarih çekilmesi için regex iyileştirildi.
- **İsim Toleransı**: `parseName` içindeki token bazlı arama zaten 3 toleransla çalışıyor, ancak gürültü (`Hpolfiff`) çok yüksek.

---
## 13 Mart 2026 ~22:00 - İsim Tanıma ve Doğrulama İyileştirmeleri (5. oturum)

### ✅ Yapılan Değişiklikler

1. **Tighter Levenshtein (Yanlış Eşleşme Önleme)**:
   - `TextParser.kt`'de kısa isimler için tolerans düşürüldü.
   - 4 karakter ve altı: Sadece tam eşleşme (0 hata).
   - 5-6 karakter: En fazla 1 hata (`cleffa` artık `iffffa` ile eşleşmez).
   - 7-9 karakter: En fazla 2 hata.
   - İsim uzunluğu ile OCR çıktısı arasındaki fark en fazla 2 karakter olabilir.

2. **Title Case (Görsel İyileştirme)**:
   - Tüm Pokemon isimleri artık kullanıcıya "Pikachu", "Raichu" şeklinde baş harfi büyük olarak gösterilecek.

3. **Name OCR Fallback (Doğruluk Artırma)**:
   - `whiteMask` ile isim okunamadığında `highContrast` (gri metin modu) ile tekrar deneme eklendi. Bu, farklı arka planlarda ismin daha iyi yakalanmasını sağlar.

4. **Pokemon Listesi Bilgilendirmesi**:
   - Mevcut `pokemon_names.json` listesinin Gen 1-3 (386 Pokemon) ile sınırlı olduğu tespit edildi. Yeni nesil Pokemonlar (Gen 4+) için liste güncellenmelidir.

### 🔴 Beklenen Sonuçlar
- Yanlış isim eşleşmelerinde (false positive) ciddi azalma.
- İsimlerin daha profesyonel (Title Case) görünümü.
- Farklı arka planlarda isim yakalama oranında artış.

---
---
## 14 Mart 2026 ~03:30 - Derin Matematiksel CP Tahmin Motoru (20. oturum)

### 📊 Log Analizi Sonrası Tespitler
1. **OCR Tamamen Kör Kalabiliyor**: Bazı durumlarda animasyon veya renk değişimi CP metnini tamamen görünmez kılıyor. Bu durumda sadece OCR ile sonuç almak mümkün değil.
2. **Eksik Veri Seti**: Stardust ve Arc Level verileri CP tahminini daraltmak için kritik öneme sahip.

### ✅ Yapılan Değişiklikler

1. **Stardust-Level Eşleşme Tablosu**:
   - Pokemon GO'daki 200 ile 10.000 arası tüm **Stardust maliyetleri** ve bunlara karşılık gelen **Level aralıkları** (1-40) sisteme eklendi.

2. **Derin Doğrulama Motoru (Deep Validation)**:
   - `validateAndFixCP` fonksiyonu, CP tamamen `null` olsa bile çalışacak şekilde yeniden yazıldı.
   - **Strateji**:
     - OCR ile okunan **Stardust** değerinden olası Level aralığı belirleniyor.
     - Bu aralıktaki her seviye için, okunan **HP** değerini verebilecek IV kombinasyonları taranıyor.
     - Bulunan adaylar arasından, görsel olarak tespit edilen **Arc Level**'a (% doluluk) en yakın olan CP değeri "En İyi Tahmin" olarak seçiliyor.

3. **Placeholder Zekası**:
   - Eğer OCR `??80` gibi bir ipucu yakaladıysa, matematiksel motor sadece bu kalıba uyan CP'leri filtreleyerek doğruluğu %100'e yaklaştırıyor.

### 🔴 Beklenen Sonuçlar
- CP metni hiç okunmasa bile, HP + Stardust + Arc üçlüsü üzerinden gerçek CP'nin çok yüksek doğrulukla bulunması.
- Hareketli Pokemonlarda "Unknown CP" sorununun tamamen ortadan kalkması.

---
*Son güncelleme: 14 Mart 2026 ~03:30 — 20. oturum iyileştirmeleri*

# PokeRarityScanner — Oturum Özeti (13 Mart 2026, 18:35)

## Proje
Pokemon GO overlay uygulaması. Ekranda yüzen Pokeball butonuna basınca ekran görüntüsü alınır, Tesseract OCR ile CP/isim/HP/tarih okunur, rarity_manifest.json'dan nadirlik skoru hesaplanır, şeffaf ResultActivity'de gösterilir.

**Konum:** `C:\Users\Caglar\Desktop\PokeRarityScanner`
**Cihaz:** Samsung RFCY11MX0TM, 1080x2340px, 450dpi, USB debug açık
**ADB:** `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`
**Build:** `gradlew.bat assembleDebug` → `adb install -r app\build\outputs\apk\debug\app-debug.apk`

---

## Mimari (önemli dosyalar)
```
util/ocr/OCRProcessor.kt       ← OCR pipeline, bölge kesme, Tesseract
util/ocr/ScreenRegions.kt      ← Ekran koordinatları
util/ocr/TextParser.kt         ← Regex + Levenshtein parse
util/ocr/ImagePreprocessor.kt  ← processWhiteMask() / processHighContrast()
service/ScanManager.kt
service/OverlayService.kt
service/ScreenCaptureService.kt
ui/result/ResultActivity.kt
data/repository/RarityCalculator.kt
assets/data/rarity_manifest.json
```

---

## Son Çalışan Kod Durumu (18:35 build)

### TextParser.kt — parseName() — MEVCUT HAL
```kotlin
fun parseName(ocrText: String): String? {
    if (ocrText.isBlank()) return null
    val clean = ocrText.replace(Regex("[^A-Za-z\\s\\-\\.]"), "").trim().lowercase()
    if (clean.length < 3) return null
    pokemonNames.find { it == clean }?.let { return it }
    // TOKEN BAZLI: exact match + Levenshtein
    for (token in clean.split(Regex("\\s+"))) {
        if (token.length < 3) continue
        pokemonNames.find { it == token }?.let { return it }
        var tb: String? = null; var td = Int.MAX_VALUE
        for (name in pokemonNames) {
            if (abs(name.length - token.length) > 3) continue
            val d = levenshtein(token, name)
            val maxD = when { name.length <= 4 -> 1; name.length <= 7 -> 2; else -> 3 }
            if (d < td && d <= maxD) { td = d; tb = name }
        }
        tb?.let { return it }
    }
    // GLOBAL clean Levenshtein (fallback)
    var best: String? = null; var bd = Int.MAX_VALUE
    for (name in pokemonNames) {
        if (abs(name.length - clean.length) > 4) continue
        val d = levenshtein(clean, name)
        val maxD = when { name.length <= 4 -> 1; name.length <= 7 -> 2; else -> 3 }
        if (d < bd && d <= maxD) { bd = d; best = name }
    }
    return best
}
```

### parseCandyName() — MEVCUT HAL
Token bazlı loop KALDIRILDI. Sadece "X CANDY" formatı kabul ediliyor:
```kotlin
fun parseCandyName(text: String): String? {
    if (text.isBlank()) return null
    val upper = text.uppercase().trim()
    val m = Regex("""([A-Z][A-Z\s\-]{1,20}?)\s+CANDY""").find(upper)
    if (m != null) {
        val raw = m.groupValues[1].trim()
        parseName(raw)?.let { return it }
    }
    return null
}
```

### OCRProcessor.kt — candyName pipeline
```kotlin
val candyName = textParser.parseCandyName(candyRaw)
    ?: textParser.parseCandyName(candyFallbackRaw)
// parseName(candyRaw) ve parseName(candyFallbackRaw) KALDIRILDI (gürültü üretiyordu)

val displayName = candyName ?: textParser.parseName(nameRaw) ?: "Unknown"
val realName = candyName ?: textParser.parseName(nameRaw)
```

---

## Kritik BOM Uyarısı
PowerShell `Set-Content` UTF-8 BOM ekliyor → compile hatası. DAIMA:
```powershell
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($file, ($lines -join "`n"), $utf8NoBom)
```

---

## Son Log Sonuçları (18:35 build — YENİ)

| Pokemon | CP | HP | RealName | Sonuç |
|---------|----|----|----------|-------|
| Gyarados | 3064 | 173/173 | gyarados | ✅ |
| Vaporeon | 2524 | 209/209 | vaporeon | ✅ |
| Scizor | 2511 | 136/136 | scizor | ✅ |
| Raichu | 764 | 122/122 | raichu | ✅ |
| Lugia | 2522 | 162/162 | null | ❌ (nameRaw='Luglajl', d çok yüksek) |
| Raichu(2) | 388 | 106/106 | null | ❌ (nameRaw='Raichufiz', d=3 > maxD=2) |

**Date: hepsi null** — Badge raw gürültülü ('3 35', '3 31', '23 553313'), parseDate eşleştiremiyor.

---

## Kalan Sorunlar ve Önerilen Çözümler

### 1. İsim Başarısızlıkları
- **'Luglajl'** (Lugia) → `luglajl` len=7, lugia len=5, diff=2, d=4 > maxD=2 → BAŞARISIZ
  - Çözüm: Levenshtein maxD'yi token bazlı artır VEYA OCR önişleme iyileştir
- **'Raichufiz'** (Raichu) → len=9, raichu len=6, diff=3 ≤ 4 OK; d=3, maxD=2 → BAŞARISIZ
  - Çözüm: maxD eşiğini `name.length <= 7 -> 3` yap (şu an 2)

### 2. Date Null
- Badge raw örnekleri: `'3 35'`, `'23 52/151'`, `'83 2315'`, `'3 3311'`
- parseDate regex ayarlanmalı veya badge bölge koordinatı düzeltilmeli

### 3. Candy OCR Gürültüsü
- Candy bölgesi hâlâ gürültülü (`'masts cg ayoa'`, `'s am jlbll'`)
- CANDY kelimesi hiç okunmuyor → parseCandyName her zaman null
- İsim tamamen nameRaw'a bağımlı — bu iyi çalışıyor

---

## ScreenRegions.kt — Güncel Koordinatlar
```kotlin
REGION_CP        = top=0.055, left=0.10, w=0.80, h=0.04
REGION_NAME      = top=0.41,  left=0.10, w=0.65, h=0.07
REGION_HP        = top=0.44,  left=0.20, w=0.60, h=0.05
REGION_CANDY     = top=0.640, left=0.28, w=0.65, h=0.045
REGION_CANDY_WIDE= top=0.620, left=0.20, w=0.75, h=0.085
REGION_DATE_BADGE= top=0.357, left=0.776, w=0.217, h=0.047
REGION_DATE_BOTTOM= top=0.88, left=0.05, w=0.90, h=0.07
REGION_STARDUST  = top=0.62,  left=0.02, w=0.30, h=0.07
REGION_WEIGHT    = top=0.52,  left=0.03, w=0.40, h=0.05
REGION_HEIGHT    = top=0.52,  left=0.55, w=0.40, h=0.05
```

---

## Sonraki Oturumda Yapılacaklar (Öncelik Sırası)

1. **parseName maxD artır**: `name.length <= 7 -> 3` (şu an 2) → Raichu, Espeon gibi 6-7 harfli isimlerde 3 karakter tolerans
2. **Lugia sorunu**: `luglajl` çok bozuk — name bölge koordinatını veya preprocessor'ı iyileştir
3. **Date parse**: Badge raw `'3 35'` → parseDate regex'ini gözden geçir
4. **Test**: Daha fazla farklı pokemon ile scan yapıp başarı oranını ölç
