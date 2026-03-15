# POKEMON RARITY SCANNER — PROJE DURUM RAPORU (YOL HARİTASI)

**Son güncelleme:** 15 Mart 2026 — 29. Oturum
**Repo:** https://github.com/chaglaruk/PokemonRarityScanner
**Cihaz:** Samsung RFCY11MX0TM · 1080×2340 · 450dpi · USB debug ✅

---

## 🗺️ MEVCUT DURUM (15 Mart 2026 - 13:10)

| Bileşen | Durum | Not |
|---|---|---|
| App açılıyor | ✅ | Crash yok |
| Overlay (Pokeball) | ✅ | Görünüyor, sürüklenebilir |
| ScreenCaptureService | ✅ | SecurityException fix tamamlandı |
| OCR Pipeline | ✅ | Tesseract çalışıyor |
| CP okuma | ⚠️ | %70 başarı, whiteMask ile iyileşti |
| HP okuma | ✅ | %100 başarı |
| İsim (Candy) | ⚠️ | %80 başarı, Lugia/Raichu gibi bozuk OCR'larda başarısız |
| Tarih | ❌ | Badge bölgesi gürültülü, bottom fallback çalışıyor |
| GitHub sync | ✅ | 15 Mart 2026 13:15 |

---

## 🏗️ MİMARİ

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   ├── data/
│   │   ├── pokemon_names.json       ← Gen 1-3 (386 Pokemon) — Gen 4+ EKLENMELİ
│   │   ├── rarity_manifest.json     ← Tür bazlı nadirlik skoru
│   │   ├── shiny_references.json    ← Renk referansları
│   │   └── event_history.json       ← Event tarihleri
│   └── tessdata/
│       └── eng.traineddata
└── java/com/pokerarity/scanner/
    ├── service/
    │   ├── OverlayService.kt        ← Floating Pokeball (specialUse FGS)
    │   ├── ScreenCaptureService.kt  ← MediaProjection + VirtualDisplay ← KRİTİK
    │   ├── ScreenCaptureManager.kt  ← Permission yönetimi
    │   └── ScanManager.kt           ← Ana pipeline orkestratörü
    ├── util/ocr/
    │   ├── OCRProcessor.kt          ← Tesseract, bölge kesme, dual preprocessing
    │   ├── TextParser.kt            ← Regex + Levenshtein parsing
    │   ├── ImagePreprocessor.kt     ← whiteMask / highContrast / detectArcLevel
    │   └── ScreenRegions.kt         ← Ekran koordinatları (%)
    ├── util/vision/
    │   ├── VisualFeatureDetector.kt ← Shiny/Shadow/Lucky renk analizi
    │   └── ColorAnalyzer.kt
    ├── data/
    │   ├── model/PokemonData.kt
    │   ├── model/RarityScore.kt
    │   ├── repository/RarityCalculator.kt
    │   ├── repository/PokemonRepository.kt
    │   └── local/db/                ← Room DB
    └── ui/
        ├── splash/SplashActivity.kt
        ├── main/MainActivity.kt
        └── result/ResultActivity.kt
```

---

## 📐 EKRAN KOORDİNATLARI (1080×2340px — Samsung)

```kotlin
// ScreenRegions.kt güncel değerleri
REGION_CP          = Region(top=0.055, left=0.10, w=0.80,  h=0.040)
REGION_NAME        = Region(top=0.410, left=0.10, w=0.65,  h=0.070)
REGION_HP          = Region(top=0.440, left=0.20, w=0.60,  h=0.050)
REGION_CANDY       = Region(top=0.640, left=0.28, w=0.65,  h=0.045)
REGION_CANDY_WIDE  = Region(top=0.620, left=0.20, w=0.75,  h=0.085)
REGION_DATE_BADGE  = Region(top=0.357, left=0.776,w=0.217, h=0.047)  // turuncu oval
REGION_DATE_BOTTOM = Region(top=0.880, left=0.05, w=0.90,  h=0.070)
REGION_STARDUST    = Region(top=0.620, left=0.45, w=0.40,  h=0.070)
REGION_WEIGHT      = Region(top=0.520, left=0.03, w=0.40,  h=0.050)
REGION_HEIGHT      = Region(top=0.520, left=0.55, w=0.40,  h=0.050)
```

---

## ⚙️ OCR PIPELINE (ScanManager → OCRProcessor)

```
Screenshot (4 frame, 150ms aralıklı)
    ↓
processWhiteMask()    ← CP, Name için (beyaz metin)
processHighContrast() ← HP, Candy, Stardust için (gri metin)
    ↓
Bölge kesme (region/regionBlock/regionFromRect)
    ↓
Tesseract (PSM_SINGLE_LINE veya PSM_SINGLE_BLOCK)
    ↓
TextParser: parseCP / parseHPPair / parseName / parseCandyName / parseDate
    ↓
En iyi frame seç (CP+Name+HP+Arc skoru)
    ↓
RarityCalculator.calculate()
    ↓
ResultActivity (şeffaf overlay)
```

**İsim kaynak önceliği:** `parseCandyName(candyRaw)` → `parseCandyName(candyWide)` → `parseName(nameRaw)` → `parseName(nameHC)`

---

## 🐛 BİLİNEN SORUNLAR VE ÇÖZÜM PLANI

### 🔴 Yüksek Öncelik
| # | Sorun | Etki | Çözüm |
|---|-------|------|-------|
| 1 | **Tarih okunamıyor** | Badge raw hep gürültülü (`'3 35'`, `'83 2315'`) | Badge koordinatını debug_regions ile yeniden doğrula; `detectOrangeBadge()` dinamik tespiti zorunlu kıl |
| 2 | **Lugia gibi bozuk OCR** | `'Luglajl'` → Levenshtein d=4 > maxD=2 → null | `name.length <= 7 → maxD=3` yap; VEYA name bölgesi preprocessor'ını iyileştir |

### 🟡 Orta Öncelik
| # | Sorun | Etki | Çözüm |
|---|-------|------|-------|
| 3 | **Pokemon listesi Gen 1-3** | Gen 4+ (Lucario, Garchomp vb.) tanınmıyor | `pokemon_names.json`'a Gen 4-9 ekle (807 → 1025 Pokemon) |
| 4 | **Candy bölgesi gürültüsü** | `CANDY` kelimesi okunmuyor, parseCandyName null | `REGION_CANDY` koordinatını daralt veya özel whitelist uygula |

### 🟢 Düşük Öncelik
| # | Sorun | Etki | Çözüm |
|---|-------|------|-------|
| 5 | **CP %70 başarı** | Dragonite gibi hareketli sprite'larda whiteMask bazen yeterli değil | Arc + Stardust matematiksel fallback aktifleştir |
| 6 | **Farklı ekran oranı** | Sadece 1080×2340 test edildi | ScreenRegions'ı aspect-ratio bazlı yap |

---

## 📦 BUILD & DEPLOY

```batch
cd C:\Users\Caglar\Desktop\PokeRarityScanner
gradlew.bat assembleDebug
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

**Logcat izleme:**
```batch
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe logcat -d 2>&1 | findstr /i "ScanManager OCRProcessor ScreenCapture FATAL"
```

---

## ⚠️ KRİTİK NOTLAR (Her ajana hatırlatma)

1. **PowerShell UTF-8 BOM** → `Set-Content` BOM ekler, compile hatası olur.
   Dosya yazmak için **Windows-MCP FileSystem `write` modunu kullan** (otomatik UTF-8 NoBOM).

2. **Build timeout** → `gradlew assembleDebug` ~4 dakika sürer.
   `Start-Process` ile arka planda başlat, `build_log.txt`'i poll et.

3. **Git push** → `git push` ağ gerektirdiğinden Windows-MCP'de zaman aşımı olabilir.
   `Start-Process git -ArgumentList "push"` ile arka planda çalıştır.

4. **Candy is King** → Pokemon ismi için önce candy bölgesini kullan.
   `candyName ?: parseName(nameRaw)` zinciri bozma.

5. **İki aşamalı FGS promotion** → `ScreenCaptureService`:
   - `onCreate()` → `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`
   - `setupProjection()` → `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`
   Bu sırayı bozma, Android 14 crash'e döner.

---

## 📋 OTURUM KAYITLARI

### [15 Mart 2026 - 13:00] `AndroidManifest.xml` — ScreenCaptureService'e `foregroundServiceType="specialUse|mediaProjection"` eklendi — DURUM: UYGULANDI
### [15 Mart 2026 - 13:00] `ScreenCaptureService.kt` — İki aşamalı foreground promotion (phase1=SPECIAL_USE, phase2=MEDIA_PROJECTION) — DURUM: UYGULANDI
### [15 Mart 2026 - 13:10] BUILD — `assembleDebug` BAŞARILI (4m 4s) — DURUM: UYGULANDI
### [15 Mart 2026 - 13:10] DEPLOY — `adb install -r` SUCCESS, `ScanManager started` logu doğrulandı — DURUM: UYGULANDI
### [15 Mart 2026 - 13:15] `rapor.md` — Tüm geçmiş oturumlar konsolide edildi, yol haritası güncellendi — DURUM: UYGULANDI
### [15 Mart 2026 - 13:15] GitHub — `git add -A && git commit && git push origin main` — DURUM: UYGULANDI

---

**Kök sebep özeti (22-29. oturumlar):**
```
SecurityException: Starting FGS with type mediaProjection ...
at ScreenCaptureService.onCreate(ScreenCaptureService.kt:86)
```
Android 14, manifest'te `mediaProjection` yazılı serviste `startForeground()` token'sız çağrısını reddeder.
Çözüm: `onCreate()`'de `SPECIAL_USE` (token yok = sorun yok), token alındıktan sonra `MEDIA_PROJECTION`'a promote.
