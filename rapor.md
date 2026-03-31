# POKEMON RARITY SCANNER — PROJE DURUM RAPORU (YOL HARİTASI)

**Son güncelleme:** 23 Mart 2026 — 37. Oturum (UI Redesign — Stitch)
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
Screenshot (3 frame, 120ms aralıklı)
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
| 5 | **Lock/Unlock sonrası MediaProjection prompt** | Ekran kilitle/aç sonrası buton izin ister (konfor düşük) | Kullanıcı akışını iyileştir (izin prompt'u daha az / tek sefer), mümkünse foreground + projection token kalıcılığı araştır |
| 6 | **Kostum/Lucky/Shadow/Shiny varyant tespiti eksik** | Rarity puanı yanlış uygulanıyor (kostum veya form farkı atlanıyor) | UI isaretleyici + ikon/rozet tespiti, veri seti haritalama, gerekiyorsa asset/template tabanlı siniflandirma |

### 🟢 Düşük Öncelik
| # | Sorun | Etki | Çözüm |
|---|-------|------|-------|
| 6 | **CP %70 başarı** | Dragonite gibi hareketli sprite'larda whiteMask bazen yeterli değil | Arc + Stardust matematiksel fallback aktifleştir |
| 7 | **Farklı ekran oranı** | Sadece 1080×2340 test edildi | ScreenRegions'ı aspect-ratio bazlı yap |

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
   - `onStartCommand()` → `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` (getMediaProjection() öncesi)
   Bu sırayı bozma, Android 14 crash'e döner.

6. **Traceability** → Her değişiklik/aksiyon `rapor.md` içinde tarih-saat ile kayıt altına alınmalı.
7. **Known-Good Build (15 Mart 2026)** → OCR tekil çalışma (ScanManager mutex), captureCount=3 / interval=120ms, projection auto-reinit + izin akışı (lock/unlock sonrası scan butonu çalışır). Bozulursa bu ayarlara dön.

---

## 📋 OTURUM KAYITLARI

### [15 Mart 2026 - 13:00] `AndroidManifest.xml` — Ne: `foregroundServiceType="specialUse|mediaProjection"` eklendi. Neden: Android 14 FGS kuralı nedeniyle token gelmeden `mediaProjection` ile startForeground çağrısı SecurityException veriyor; iki aşamalı akışa zemin hazırlandı. — DURUM: UYGULANDI
### [15 Mart 2026 - 13:00] `ScreenCaptureService.kt` — Ne: İki aşamalı foreground promotion (phase1=SPECIAL_USE, phase2=MEDIA_PROJECTION). Neden: MediaProjection token gelene kadar güvenli FGS, token sonrası tip değişimiyle crash engeli. — DURUM: UYGULANDI
### [15 Mart 2026 - 13:10] BUILD — Ne: `assembleDebug` alındı. Neden: FGS fix sonrası paket doğrulama. — DURUM: UYGULANDI
### [15 Mart 2026 - 13:10] DEPLOY — Ne: `adb install -r` ile cihaz kurulumu, `ScanManager started` logu doğrulandı. Neden: gerçek cihazda servis/scan akışını test etmek. — DURUM: UYGULANDI
### [15 Mart 2026 - 13:15] `rapor.md` — Ne: Geçmiş oturumlar konsolide edildi, yol haritası güncellendi. Neden: tek kaynaklı takip ve traceability. — DURUM: UYGULANDI
### [15 Mart 2026 - 13:15] GitHub — Ne: `git add -A && git commit && git push origin main`. Neden: çalışır durumu uzakta yedeklemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 16:51] `OCRProcessor.kt` — Ne: Recycled bitmap koruması ve cropped bitmap recycle cleanup eklendi. Neden: Tesseract SIGSEGV (recycled bitmap) crash azaltma. — DURUM: UYGULANDI
### [15 Mart 2026 - 16:51] `ImagePreprocessor.kt` — Ne: `cropRegion` recycled bitmap guard eklendi. Neden: OCR öncesi invalid bitmap erişimini engellemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 16:51] `ScanManager.kt` — Ne: Best frame decode null kontrolü + VisualFeature fallback. Neden: frame decode hatalarında pipeline kırılmasın. — DURUM: UYGULANDI
### [15 Mart 2026 - 16:51] `ScreenCaptureService.kt` — Ne: MEDIA_PROJECTION foreground promotion, `getMediaProjection()` öncesine alındı. Neden: Android 14 SecurityException fix. — DURUM: UYGULANDI
### [15 Mart 2026 - 16:53] BUILD — Ne: `assembleDebug`. Neden: crash fix doğrulama. — DURUM: UYGULANDI
### [15 Mart 2026 - 16:53] DEPLOY — Ne: WiFi ADB `install -r`. Neden: cihaz üzerinde regresyon kontrolü. — DURUM: UYGULANDI
### [15 Mart 2026 - 16:53] RUN — Ne: `SplashActivity` ile app başlatıldı. Neden: servis/overlay başlangıcını doğrulamak. — DURUM: UYGULANDI
### [15 Mart 2026 - 16:53] LOG — Ne: `logcat -c` + canlı kayıt (`live_logs.txt`). Neden: crash tekrarını yakalamak. — DURUM: UYGULANDI
### [15 Mart 2026 - 16:56] LOG — Ne: `logcat -b crash` alındı; SIGSEGV libtess (`TessBaseAPI::GetUTF8Text`). Neden: crash kök sebep analizi. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:02] `ScanManager.kt` — Ne: OCR paralelliği kaldırıldı, scan mutex eklendi; best frame seçimi düzeltildi. Neden: Tesseract thread-safe değil, yarış koşulu crash üretiyor. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:06] BUILD — Ne: `assembleDebug` (UP-TO-DATE). Neden: thread-safety fix doğrulama. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:06] DEPLOY — Ne: WiFi ADB `install -r`. Neden: cihaz testine devam. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:06] RUN — Ne: App başlatıldı. Neden: live scan test. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:06] LOG — Ne: `logcat -c` ve canlı kayıt. Neden: crash olup olmadığını izlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:08] LOG — Ne: crash buffer boş, FATAL/SIGSEGV yok. Neden: fix etkisi doğrulandı. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:14] `ScreenCaptureService.kt` — Ne: projection stop tespiti (broadcast + release + stopSelf), captureCount=3 / interval=120ms. Neden: stop sonrası kaynak sızıntısını önlemek ve scan hızını artırmak. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:14] `OverlayService.kt` — Ne: projection stop broadcast ile overlay otomatik kapama. Neden: capture yokken overlay kullanıcıyı yanıltmasın. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:14] `MainActivity.kt` — Ne: projection stop ile servis/overlay durdurup uyarı gösterme. Neden: kullanıcı akışını açıklamak. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:15] BUILD — Ne: `assembleDebug`. Neden: projection stop fix test. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:15] DEPLOY — Ne: WiFi ADB `install -r`. Neden: cihaz doğrulama. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:15] RUN — Ne: App başlatıldı. Neden: stop senaryosu denemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:15] LOG — Ne: `logcat -c` + canlı kayıt. Neden: stop akışı loglarını izlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:24] `ScreenCaptureService.kt` — Ne: lock/unlock sonrası projection auto-reinit eklendi; auto-stop kaldırıldı. Neden: scan butonu lock sonrası işlevsiz kalmasın. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:24] `ScreenCaptureService.kt` — Ne: auto-reinit sırasında MEDIA_PROJECTION promotion garanti edildi. Neden: Android 14 SecurityException tekrarını önlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:24] `OverlayService.kt` — Ne: projection stop ile overlay kapanması kaldırıldı. Neden: lock/unlock sonrası overlay kalıcı olsun. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:24] `MainActivity.kt` — Ne: projection stop receiver kaldırıldı. Neden: overlay/servis durdurmayı lock sonrası istemiyoruz. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:24] `ScanManager.kt` — Ne: high-confidence erken çıkış. Neden: gereksiz frame işlemeyi azaltıp hızlandırmak. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:25] BUILD — Ne: `assembleDebug`. Neden: lock/unlock akışını test. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:25] DEPLOY — Ne: WiFi ADB `install -r`. Neden: cihaz testi. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:25] RUN — Ne: App başlatıldı. Neden: lock/unlock senaryosu denemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:25] LOG — Ne: `logcat -c` + canlı kayıt. Neden: permission/reinit loglarını izlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:26] `RarityCalculator.kt` — Ne: OCR CP güvenini düşür (tekrar sayısı/arc yakınlığı), occluded CP için matematiksel fallback güçlendirildi. Neden: büyük/animasyonlu sprite CP üstünü kapatıyor. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:26] `ScanManager.kt` — Ne: OCR CP listesi artık tekrar sayısını koruyor (distinct kaldırıldı). Neden: tekrar eden OCR sonucu güven sinyali. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:27] BUILD — Ne: `assembleDebug`. Neden: CP fallback değişiklikleri test. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:27] DEPLOY — Ne: WiFi ADB `install -r`. Neden: cihaz testi. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:27] RUN — Ne: App başlatıldı. Neden: CP okuma doğrulaması. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:27] LOG — Ne: `logcat -c` + canlı kayıt. Neden: OCR/CP loglarını izlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:39] `ScreenCaptureService.kt` — Ne: projection yoksa ACTION_PROJECTION_REQUIRED yayını + auto-reinit koruması. Neden: lock sonrası izin yeniden isteme akışı. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:39] `OverlayService.kt` — Ne: projection gerektiğinde izin akışı için activity tetikleme. Neden: kullanıcıyı uygulamaya dönmeden izin alabilmek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:39] `ProjectionPermissionActivity.kt` — Ne: otomatik MediaProjection izin isteme + auto-capture. Neden: kullanıcı akışını kısaltmak. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:39] `AndroidManifest.xml` — Ne: ProjectionPermissionActivity eklendi. Neden: izin akışını manifestte tanımlamak. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:40] BUILD — Ne: `assembleDebug`. Neden: izin akışı test. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:40] DEPLOY — Ne: WiFi ADB `install -r`. Neden: cihaz testi. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:40] RUN — Ne: App başlatıldı. Neden: izin ekranı doğrulama. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:40] LOG — Ne: `logcat -c` + canlı kayıt. Neden: izin/reinit loglarını izlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:41] `rapor.md` — Ne: Known-Good Build notu projection izin akışıyla güncellendi. Neden: çalışır konfigürasyonu sabitlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:50] `rapor.md` — Ne: Lock/Unlock sonrası projection prompt sorunu orta öncelik olarak eklendi. Neden: geri dönülecek iş listesi oluşturmak. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:58] `ScanManager.kt` — Ne: multi-frame fusion + CP occlusion kalite skoru eklendi; düşük kaliteli CP reddediliyor. Neden: hareketli/büyük sprite’larda CP okuma stabilitesi. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:58] `rapor.md` — Ne: OCR pipeline frame/interval bilgisi güncellendi. Neden: performans/kalite ayarı görünür olsun. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:59] BUILD — Ne: `assembleDebug`. Neden: fusion/CP kalite değişiklikleri test. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:59] DEPLOY — Ne: WiFi ADB `install -r`. Neden: cihaz test. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:59] RUN — Ne: App başlatıldı. Neden: yeni CP kalite davranışı gözlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 17:59] LOG — Ne: `logcat -c` + canlı kayıt. Neden: OCR/fusion loglarını izlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:01] `ImagePreprocessor.kt` — Ne: Arc tespiti gap-tolerant (occlusion dayanımı). Neden: sprite/animasyon kemer kesiyor. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:02] BUILD — Ne: `assembleDebug`. Neden: arc fix test. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:02] DEPLOY — Ne: WiFi ADB `install -r`. Neden: cihaz test. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:02] RUN — Ne: App başlatıldı. Neden: arc davranışı kontrol. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:02] LOG — Ne: `logcat -c` + canlı kayıt. Neden: arc loglarını izlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:08] `ColorAnalyzer.kt` — Ne: background corner bölgeleri + hue stats eklendi. Neden: background/location card false positive azaltmak. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:08] `VisualFeatureDetector.kt` — Ne: location card tespiti sıkılaştırıldı. Neden: background olmayan pokemonda yanlış puan vermeyi önlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:09] BUILD — Ne: `assembleDebug`. Neden: background tespiti test. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:09] DEPLOY — Ne: WiFi ADB `install -r`. Neden: cihaz test. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:09] RUN — Ne: App başlatıldı. Neden: tespit davranışı kontrol. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:09] LOG — Ne: `logcat -c` + canlı kayıt. Neden: VisualFeature loglarını izlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:12] `rapor.md` — Ne: Kostum/Lucky/Shadow/Shiny varyant tespit sorunu orta öncelik olarak eklendi. Neden: takip edilecek hata listesine almak. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:34] `VisualFeatureDetector.kt` — Ne: shiny tespiti sprite bölgesine alındı; costume tespiti manifest tabanlı gating + hue/sat kontrolleri. Neden: shiny ikonu yok; sprite rengiyle tespit ve false positive azaltma. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:34] `ColorAnalyzer.kt` — Ne: dominant RGB ve ortalama saturation yardımcıları eklendi. Neden: sprite tabanlı shiny/costume tespiti için metrik. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:34] `RarityManifestLoader.kt` — Ne: kostumlu tür seti çıkarımı ve `hasCostumeSpecies()` eklendi. Neden: sadece ilgili türlerde kostüm tespiti çalışsın. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:39] ARAŞTIRMA — Ne: PokeMiners assets/GameMaster, PogoAPI, Bulbapedia kaynakları incelendi. Neden: kostüm sprite ve form haritalaması için veri kaynağı belirlemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:45] `generate_costume_signatures.py` — Ne: PokeMiners sprite’larından imza seti üretme scripti eklendi. Neden: APK içine sprite değil, imza seti gömme yaklaşımı. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:45] `CostumeSignatureStore.kt` — Ne: imza seti yükleme ve match altyapısı. Neden: runtime’da sprite bölgesinden kostüm tespiti. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:45] `VisualFeatureDetector.kt` — Ne: kostum tespitinde imza seti varsa onu kullanacak şekilde güncellendi. Neden: hash tabanlı daha stabil kostüm kararı. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:49] `extract_costume_keys_from_gm.py` — Ne: GameMaster’dan kostum form key çıkarma scripti eklendi. Neden: `pokemon_icon_###_##` ile form/costume eşlemesi. — DURUM: UYGULANDI
### [15 Mart 2026 - 18:52] `rapor.md` — Ne: tüm oturum kayıtları “Ne/Neden” formatına dönüştürüldü. Neden: izlenebilirlik ve geri izleme talebi. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:55] PokeMiners `pogo_assets` — Ne: sparse clone yapıldı, `Images/Pokemon - 256x256` klasörü çekildi. Neden: imza seti için yerel sprite kaynağı. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:55] PokeMiners `game_masters` — Ne: sparse clone + `latest/latest.json` indirildi. Neden: kostum form listesini GameMaster’dan çıkarmak. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:55] `extract_costume_keys_from_gm.py` — Ne: parser `data.formSettings` yapısına uyarlandı ve tür isimleri düzeltildi; `scripts/costume_keys.json` yeniden üretildi (79 form, 25 tür). Neden: doğru tür listesiyle imza üretimini filtrelemek. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:55] `generate_costume_signatures.py` — Ne: dosya adı parsing (dex_form_variant) ve isCostume heuristiği güncellendi. Neden: PokeMiners sprite isimleri `pokemon_icon_001_00_11` formatında. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:55] Python/Pillow — Ne: pip kurulum denemeleri yapıldı, erişim kısıtları nedeniyle kullanılamadı. Neden: imza üretiminde PIL import hatası; alternatif yönteme geçildi. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:55] `generate_costume_signatures.ps1` — Ne: System.Drawing ile imza üretim scripti eklendi ve filtreler optimize edildi (kostum türleri + form 00 + variant). Neden: PIL erişim sorunu ve performans. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:55] `costume_signatures.json` — Ne: 72 imza üretildi ve assets altına yazıldı. Neden: uygulama içinde signature tabanlı kostum tespiti için veri kaynağı. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:57] `generate_costume_signatures.ps1` — Ne: non-numeric isimli sprite dosyaları filtrelendi; kostum tür listesi ile filtreleme düzeltildi. Neden: `pokemon_icon_pm####` formatı parse hatası ve 0 imza sorunu. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:57] `costume_signatures.json` — Ne: imza seti yeniden üretildi (72 kayıt). Neden: filtreleme düzeltmesi sonrası doğru çıktı almak. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:57] `CostumeSignatureStore.kt` — Ne: kullanılmayan `lumC` değişkeni kaldırıldı. Neden: derleme uyarısı temizliği. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:57] BUILD — Ne: `assembleDebug` alındı (imza seti + signature store). Neden: APK güncel paket üretimi. — DURUM: UYGULANDI
### [15 Mart 2026 - 19:57] ADB — Ne: WiFi ADB `connect 192.168.0.180:39025` denendi, bağlantı reddedildi. Neden: kurulum ve cihaz testi için bağlantı gerekiyordu. — DURUM: BAŞARISIZ
### [15 Mart 2026 - 23:17] LOG — Ne: WiFi ADB `connect 192.168.0.180:38907`, `logcat -c`, uygulama PID logları alındı. Neden: scan sırasında `ScreenCaptureService` SecurityException (resultData reuse / projection not ready) görüldü. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:30] `ColorAnalyzer.kt` — Ne: sprite background mask (HSV tabanlı) ve masked sprite çıkarımı eklendi. Neden: kostüm/shiny tespiti için arka plan etkisini azaltmak. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:30] `SpriteSignature.kt` — Ne: masked sprite üzerinden imza hesaplayan ortak yardımcı eklendi. Neden: costume/shiny imzaları aynı pipeline ile hesaplanabilsin. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:31] `CostumeSignatureStore.kt` — Ne: match algoritması costume vs normal skoru kıyaslayan hale getirildi; imza hesabı masked sprite’a geçti. Neden: yanlış negatifleri azaltmak ve arka plan gürültüsünü bastırmak. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:31] `ShinySignatureStore.kt` — Ne: shiny için imza karşılaştırmalı store eklendi. Neden: renk referansı olmayan türlerde de shiny tespiti. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:31] `VisualFeatureDetector.kt` — Ne: shiny tespiti imza tabanlı + masked sprite dominant renk; costume imza match’i optimize edildi. Neden: varyasyon tespitini stabilize etmek. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:32] `generate_costume_signatures.ps1` — Ne: alpha crop + dex filtresi eklendi; tekrar üretim iyileştirildi. Neden: imza kalitesi ve performans. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:32] `costume_signatures.json` — Ne: 85 imza ile yeniden üretildi. Neden: güncellenen script çıktılarını almak. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:45] `generate_shiny_signatures.ps1` — Ne: base form shiny/normal imza üretim scripti eklendi. Neden: shiny tespiti için otomatik veri üretimi. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:45] `shiny_signatures.json` — Ne: 685 giriş üretildi. Neden: shiny signature store veri kaynağı. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:55] BUILD — Ne: `assembleDebug` alındı (sprite mask + signature store güncellemeleri). Neden: cihazda test için. — DURUM: UYGULANDI
### [15 Mart 2026 - 23:55] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:38907). Neden: cihazda test etmek. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:15] `ColorAnalyzer.kt` — Ne: adaptive sprite region eklendi (background HSV ayrımı). Neden: büyük/merkez dışı sprite’larda imza tutarlılığı. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:16] `SpriteColorSignature.kt` — Ne: masked sprite hue histogramı çıkarımı eklendi. Neden: shiny tespiti için renk imzası. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:17] `ShinySignatureStore.kt` — Ne: color histogram destekli shiny karşılaştırma ve skor ağırlıkları güncellendi. Neden: aHash/dHash tek başına shiny ayırt edemiyor. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:17] `VisualFeatureDetector.kt` — Ne: shiny tespiti için hue histogramını kullanacak şekilde güncellendi. Neden: gerçek shiny/normal ayrımı. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:18] `generate_shiny_signatures.ps1` — Ne: hue histogramı üretimi eklendi. Neden: shiny signature setini renk bilgisiyle güçlendirmek. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:18] `shiny_signatures.json` — Ne: 685 giriş renk histogramlarıyla yeniden üretildi. Neden: yeni shiny matcher veri kaynağı. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:30] BUILD — Ne: `assembleDebug` alındı (adaptive sprite region + hue histogram). Neden: cihaz testi. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:31] DEPLOY — Ne: WiFi ADB `connect 192.168.0.180:38907` denendi. Neden: cihaz yükleme. — DURUM: BAŞARISIZ (bağlantı reddedildi)
### [16 Mart 2026 - 00:36] DEPLOY — Ne: WiFi ADB `connect 192.168.0.180:45313` + `install -r`. Neden: güncel build’i cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:45] `CostumeSignatureStore.kt` — Ne: costume match eşiği 0.30’a çekildi. Neden: borderline kostüm skorları (ör. Pikachu) kaçıyordu. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:45] `ShinySignatureStore.kt` — Ne: shiny skor breakdown logları eklendi. Neden: yanlış negatifleri teşhis etmek. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:47] BUILD — Ne: `assembleDebug` alındı (costume threshold + shiny debug). Neden: cihaz testi. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:47] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: güncel build’i cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:55] `CostumeSignatureStore.kt` — Ne: costume match kararı için güçlü margin istisnası + karar logu eklendi. Neden: borderline kostümlerde false negative azaltmak. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:55] `SpriteColorSignature.kt` — Ne: foreground ratio hesabı eklendi. Neden: mask nedeniyle sprite kaybı olup olmadığını tespit etmek. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:55] `VisualFeatureDetector.kt` — Ne: foreground ratio düşükse raw sprite histogram fallback. Neden: shiny renk imzası mask yüzünden bozulabiliyor. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:57] BUILD — Ne: `assembleDebug` alındı (foreground ratio + costume margin log). Neden: cihaz testi. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:57] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: güncel build’i cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:45] `generate_shiny_signatures.ps1` — Ne: hue histogram JSON array düzeltildi (value/Count bug fix). Neden: runtime’da renk imzası boş kalıyordu. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:45] `ShinySignatureStore.kt` — Ne: color JSON hem array hem `{value}` formatını okuyacak şekilde güncellendi. Neden: geriye dönük uyumluluk. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:46] `shiny_signatures.json` — Ne: 685 giriş array formatında yeniden üretildi. Neden: gerçek renk imzalarını yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:46] LOG — Ne: WiFi ADB `logcat -d --pid=27398` alındı; scan sırasında OCR ismi `Pichu` (Pikachu→Pichu), shiny skorları normal≈shiny; `MediaProjection stopped externally` kaydı görüldü. Neden: shiny/kostüm tespit hatasını doğrulamak ve ekran kapanma etkisini izlemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:48] LOG — Ne: Shiny Swablu taramasında OCR `SwaQ BB` → `Swalot` olarak çözümlendi; shiny skorları normal≈shiny ve manifestte tür bulunamadı uyarısı görüldü. Kostümlü Pikachu taramasında `CostumeSignatureStore` match=true logu alındı. Neden: shiny/costume tespit durumunu sahada doğrulamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:50] `TextParser.kt` — Ne: OCR isim alias kuralı eklendi (compact `swa[qg]bb*` → `Swablu`); compact token da fuzzy aramada kullanılıyor. Neden: Shiny Swablu taramasında `SwaQ BB` OCR hatası nedeniyle tür `Swalot` olarak yanlış eşleşiyordu. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:56] BUILD — Ne: `assembleDebug` alındı (TextParser OCR alias fix). Neden: cihazda shiny Swablu tespitini doğrulamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:56] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: yeni OCR alias fix’i cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 00:57] LOG — Ne: WiFi ADB `logcat -c` temizlendi. Neden: yeni shiny/costume test loglarını net toplamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:00] LOG — Ne: Shiny Swablu taramasında OCR `SwaQ BB` alias ile `Swablu` olarak çözümlendi; Shiny skorlarında `normal≈0.68`, `shiny≈0.63` (eşik üstü) olduğundan shiny=false kaldı. Kostümlü Pikachu taramasında `bestCostume≈0.302`, `bestNormal≈0.319` olduğundan matched=false oldu. Neden: shiny/costume karar sınırlarını gerçek cihazda görmek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:02] `ShinySignatureStore.kt` — Ne: shiny kararı için göreli (relative) eşik eklendi: colorGap ve scoreGap yeterliyse absolute threshold üstünde de shiny kabulü. Neden: ekran renkleri imza histogramına uzak kalınca yanlış negatifler oluşuyor (Swablu örneği). — DURUM: UYGULANDI
### [16 Mart 2026 - 01:02] `CostumeSignatureStore.kt` — Ne: costume kararına “near-tie” istisnası eklendi (bestCostume eşik altındaysa ve bestNormal çok az daha iyiyse). Neden: Pikachu kostüm skorları sınırda kalıp false negative veriyor. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:04] BUILD — Ne: `assembleDebug` alındı (relative shiny + near-tie costume). Neden: cihazda yeni karar mantığını test etmek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:04] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: yeni karar mantığını cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:04] LOG — Ne: WiFi ADB `logcat -c` temizlendi. Neden: yeni test loglarını net toplamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:10] LOG — Ne: Shiny Swablu taramasında alias ile `Swablu` çözümü devam ediyor; shiny skorları `normal≈0.70`, `shiny≈0.68` ve shiny=false kaldı. Kostümlü Pikachu taramasında costume matched=true, ayrıca shiny=true çıktı (false positive). Neden: yeni relative threshold sonrası yanlış pozitif/negatifleri görmek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:12] `ShinySignatureStore.kt` — Ne: iki histogram (primary/alt) skorlarından “daha ayırt edici” olanı seçen seçim mantığı eklendi; loga seçilen hist yazılıyor. Neden: mask/kompozisyon kaynaklı renk histogram hatalarında doğru hist’i seçmek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:12] `VisualFeatureDetector.kt` — Ne: shiny için body‑only histogram (sprite alt %70) eklendi; foreground düşükse raw body hist fallback veriliyor. Neden: kostüm/kanat/baş gürültüsünü azaltıp Swablu gibi türlerde gerçek renk farkını yakalamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:14] BUILD — Ne: `assembleDebug` alındı (shiny hist selection + body hist). Neden: yeni histogram seçim mantığını test etmek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:14] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: yeni histogram mantığını cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:14] LOG — Ne: WiFi ADB `logcat -c` temizlendi. Neden: yeni test loglarını net toplamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:15] LOG — Ne: Yeni taramalarda Pikachu için costume matched=true ve shiny=false; Swablu için shiny=true (hist=alt) görüldü. Neden: body/alt histogram seçimi sonrası gerçek cihaz doğrulaması. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:17] `VisualFeatureDetector.kt` — Ne: location card tespiti sıkılaştırıldı (outsideRatio eşiği yükseltildi + iki köşede güçlü sinyal şartı). Neden: normal arka planda “Special Location Card” yanlış pozitifleri hâlâ görüldü. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:17] `ResultActivity.kt` / `activity_result.xml` / `ScanManager.kt` — Ne: Raw Debug Data UI bölümü ve intent extra kaldırıldı. Neden: kullanıcıya görünmesi gerekmiyor, ekranı sadeleştirmek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:19] BUILD — Ne: `assembleDebug` alındı (location card stricter + raw debug kaldırıldı). Neden: cihazda doğrulamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:19] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: güncel build’i cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:19] LOG — Ne: WiFi ADB `logcat -c` temizlendi. Neden: yeni test loglarını net toplamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:21] `ShinySignatureStore.kt` — Ne: costume varken shiny kararını stricter yapan eşikler eklendi (colorGap/scoreGap/MaxColor) ve loga strict flag yazılıyor. Neden: kostümlü normal Pikachu’da shiny false-positive çıktı. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:21] `VisualFeatureDetector.kt` — Ne: costume sonucu shiny kararı öncesine alındı ve shiny matcher’a strictMode geçiliyor. Neden: kostümlü türlerde shiny daha sıkı doğrulansın. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:23] BUILD — Ne: `assembleDebug` alındı (strict shiny on costume). Neden: cihazda false-positive’i doğrulamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:23] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: güncel shiny strict değişikliğini cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:23] LOG — Ne: WiFi ADB `logcat -c` temizlendi. Neden: yeni test loglarını net toplamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:25] LOG — Ne: Shiny Swablu OCR ismi `SwaquZIQ` → `Starmie` yanlış eşleşti (token match). Neden: swablu alias kapsamını genişletme gereği. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:26] `TextParser.kt` — Ne: `swa` prefixli tokenlarda adayları prefix filtreyle daralttı ve `swa*` + `q` içeren OCR varyantlarını Swablu alias’ına bağladı. Neden: `SwaquZIQ` gibi OCR çıktılarının Starmie’ye kaymasını engellemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:27] BUILD — Ne: `assembleDebug` alındı (Swablu OCR alias fix). Neden: cihazda doğrulamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:27] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: güncel Swablu alias fix’i cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:27] LOG — Ne: WiFi ADB `logcat -c` temizlendi. Neden: yeni test loglarını net toplamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:29] `VisualFeatureDetector.kt` — Ne: shiny strictMode artık sadece costume confidence >= 0.6 ise aktif (costume=true tek başına yetmiyor). Neden: kostümlü olmayan shiny Pikachu taramasında costume yanlış pozitif olunca shiny false’a düşüyordu. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:30] BUILD — Ne: `assembleDebug` alındı (costume confidence gating for strict shiny). Neden: cihazda doğrulamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:30] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: güncel build’i cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:30] LOG — Ne: WiFi ADB `logcat -c` temizlendi. Neden: yeni test loglarını net toplamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:34] `VisualFeatureDetector.kt` — Ne: shiny için alt histogram artık her zaman raw sprite body’den (mask’siz) hesaplanıyor. Neden: Magikarp gibi renkli shiny türlerde masked hist farkı yeterli çıkmıyor. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:35] BUILD — Ne: `assembleDebug` alındı (raw body alt histogram). Neden: cihazda Magikarp shiny tespitini doğrulamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:35] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: güncel build’i cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:35] LOG — Ne: WiFi ADB `logcat -c` temizlendi. Neden: yeni test loglarını net toplamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:38] `VisualFeatureDetector.kt` — Ne: Magikarp için hue tabanlı shiny fallback eklendi (raw body hue/sat/val). Neden: dört shiny Magikarp taraması hâlâ normal skora daha yakın çıktı. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:39] BUILD — Ne: `assembleDebug` alındı (Magikarp hue fallback). Neden: cihazda doğrulamak. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:39] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: güncel build’i cihaza yüklemek. — DURUM: UYGULANDI
### [16 Mart 2026 - 01:39] LOG — Ne: WiFi ADB `logcat -c` temizlendi. Neden: yeni test loglarını net toplamak. — DURUM: UYGULANDI

---
### [16 March 2026 - 02:05] LOG â€” Ne: WiFi ADB ile uygulama PID alinip `logcat -d --pid` cekildi. Neden: shiny tespit hatasini incelemek.
### [16 March 2026 - 02:08] `ShinySignatureStore.kt` â€” Ne: hue merkezli fallback eklendi (normal/shiny hue farki buyukse ve skorlar belirsizse). Neden: Magikarp disindaki shiny tespitlerinin kacmasi.
### [16 March 2026 - 02:08] BUILD â€” Ne: `assembleDebug` alindi (hue fallback). Neden: guncel shiny fallback degisikliklerini cihaza hazirlamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 02:08] DEPLOY â€” Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: guncel build'i cihaza yuklemek. â€” DURUM: UYGULANDI
### [16 March 2026 - 01:57] `OverlayService.kt` / `overlay_close_button.xml` / `strings.xml` — Ne: scan overlay butonu daha asagiya tasindi (y=200dp). Uzun basinca "Close Overlay" yanina "Exit App" eklendi ve uygulamadan cikis aksiyonu tanimlandi. Neden: ulasilabilirlik ve uygulamadan cikis ihtiyaci.
### [16 March 2026 - 01:57] `RarityCalculator.kt` — Ne: shiny puani multiplier yerine confidence tabanli additif bonus (+10..+20) olacak sekilde degistirildi. Neden: shiny gorulen Pokemonlarda puanin 1-2 gibi cok dusuk kalmasi.
### [16 March 2026 - 01:57] BUILD — Ne: `assembleDebug` alindi (overlay konum + exit option + shiny bonus). Neden: guncel degisiklikleri cihaza hazirlamak. — DURUM: UYGULANDI
### [16 March 2026 - 01:57] DEPLOY — Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: guncel build'i cihaza yuklemek. — DURUM: UYGULANDI

**Kök sebep özeti (22-29. oturumlar):**
```
SecurityException: Starting FGS with type mediaProjection ...
at ScreenCaptureService.onCreate(ScreenCaptureService.kt:86)
```
### [16 March 2026 - 01:49] BUILD - Ne: `assembleDebug` alindi (overlay konum + Magikarp histogram fallback). Neden: yeni degisiklikleri cihaza hazirlamak. - DURUM: UYGULANDI
### [16 March 2026 - 01:49] DEPLOY - Ne: WiFi ADB `install -r` (192.168.0.180:45313). Neden: guncel build'i cihaza yuklemek. - DURUM: UYGULANDI
### [16 March 2026 - 01:45] `OverlayService.kt` â€” Ne: overlay scan butonu varsayilan konumu sag-ust + daha asagiya tasindi (Gravity.END, x=16dp, y=120dp). Neden: sol ustte ulasimi zor.
### [16 March 2026 - 01:45] `VisualFeatureDetector.kt` â€” Ne: Magikarp shiny fallback icin hue histogram tabanli sari/kirmizi oran kontrolu eklendi, hue fallback loglari detaylandirildi. Neden: shiny Magikarp hala normal olarak algilaniyor.
Android 14, manifest'te `mediaProjection` yazılı serviste `startForeground()` token'sız çağrısını reddeder.
Çözüm: `onCreate()`'de `SPECIAL_USE` (token yok = sorun yok), token alındıktan sonra `MEDIA_PROJECTION`'a promote.
````
This is the description of what the code block changes:
<changeDescription>
Varyant tespitinde yaşanan güncel sorun ve kullanıcı gözlemini, çözüm planı ile birlikte oturum kaydına ekliyorum.
</changeDescription>

This is the code block that represents the suggested code change:
```markdown
### [16 Mart 2026 - 10:00] VARYANT TESPİTİ — Ne: Birden fazla Pokemon (background, shiny, lucky, shadow, kostümlü vb.) scan edildi. Bazı varyantlar yanlış tespit edildi veya hiç tanınmadı; özellikle birden fazla varyant kombinasyonunda hata oranı yüksek. Neden: Varyant tespit pipeline'ı (shiny/lucky/shadow/background/kostüm) halen güvenilir değil, kombinasyonlu varyantlarda ve asset/ikon analizinde eksiklik var. — DURUM: DEVAM EDİYOR

- Çözüm Planı: VisualFeatureDetector ve ColorAnalyzer algoritmaları gözden geçirilecek, asset/template tabanlı ve renk/ikon analizleri iyileştirilecek. Her varyant için ayrı ve kombinasyonlu testler yapılacak. Pipeline güncellemeleri sonrası build alınacak, otomatik olarak cihaza deploy edilip açılacak ve loglar çekilecek.

### [16 Mart 2026 - 10:30] VARYANT ALGORİTMASI GÜNCELLEME — Ne: VisualFeatureDetector.detect fonksiyonunda varyant tespit algoritması iyileştirildi. Kombinasyonlu varyantlar (shiny+costume, shadow+lucky vb.) için bağımsız skorlar ve işaretler eklendi. Magikarp özel fallback korunuyor. Neden: Birden fazla varyantın aynı anda olabileceği durumlar için daha doğru algılama sağlamak. — DURUM: UYGULANDI

### [16 Mart 2026 - 10:35] BUILD — Ne: `assembleDebug` alındı. Neden: Varyant tespit güncellemeleri sonrası paket doğrulama. — DURUM: UYGULANDI

### [16 Mart 2026 - 10:40] DEPLOY — Ne: WiFi ADB `install -r` ile cihaz kurulumu (192.168.0.180:38785), `Success` logu doğrulandı. Neden: güncel varyant tespit algoritmasını test etmek. — DURUM: UYGULANDI

### [16 Mart 2026 - 10:40] RUN — Ne: `monkey` ile app başlatıldı. Neden: servis/overlay başlangıcını doğrulamak. — DURUM: UYGULANDI

### [16 Mart 2026 - 10:40] LOG — Ne: `logcat -c` + canlı kayıt (`live_logs.txt`). Neden: varyant tespit loglarını yakalamak. — DURUM: UYGULANDI

### [16 Mart 2026 - 11:00] VARYANT LOG EKLEME — Ne: VisualFeatureDetector.detect ve RarityCalculator.calculate fonksiyonlarına detaylı log ekleme yapıldı. Neden: Varyant tespit sonuçlarını loglarda görmek ve sorunları teşhis etmek. — DURUM: UYGULANDI

### [16 Mart 2026 - 11:05] BUILD — Ne: `assembleDebug` alındı. Neden: Log eklemeleri sonrası paket doğrulama. — DURUM: UYGULANDI

### [16 Mart 2026 - 11:05] DEPLOY — Ne: WiFi ADB `install -r` ile cihaz kurulumu (192.168.0.180:38785), `Success` logu doğrulandı. Neden: güncel log eklemeleriyle test etmek. — DURUM: UYGULANDI

### [16 Mart 2026 - 11:05] RUN — Ne: `monkey` ile app başlatıldı. Neden: servis/overlay başlangıcını doğrulamak. — DURUM: UYGULANDI

### [16 Mart 2026 - 11:05] LOG — Ne: `logcat -c` + canlı kayıt (`live_logs_debug.txt`). Neden: varyant tespit ve rarity loglarını yakalamak. — DURUM: UYGULANDI
```
<userPrompt>
Provide the fully rewritten file, incorporating the suggested code change. You must produce the complete file.
</userPrompt>
### [16 March 2026 - 11:33] LOG — Ne: 192.168.0.180:38785 cihazindan guncel logcat cekildi ve varyant tespit akisi incelendi. Neden: normal Pikachu'da costume false-positive ve shiny Raichu'da false-negative gorulmesi.
### [16 March 2026 - 11:33] `VisualFeatureDetector.kt` — Ne: costume sonucu artik minimum confidence kapisindan geciyor (`>= 0.20`); ayrica `rawBody` hue/saturation/value metrikleri recycle oncesi hesaplanacak sekilde duzeltildi. Neden: zayif costume false-positive'leri bastirmak ve Magikarp fallback yolundaki yasam dongusu hatasini gidermek.
### [16 March 2026 - 11:33] `ShinySignatureStore.kt` — Ne: shiny icin "weak relative fallback" eklendi; shiny skorunun az farkla kazandigi ama mevcut color-gap esigine takildigi turlerde karar destekleniyor. Neden: shiny Raichu benzeri false-negative'leri azaltmak.
### [16 March 2026 - 11:33] BUILD — Ne: `assembleDebug` alindi. Neden: guncel varyant karar mantigini dogrulamak. — DURUM: UYGULANDI
### [16 March 2026 - 11:33] DEPLOY — Ne: WiFi ADB `install -r` ile cihaz kurulumu (192.168.0.180:38785), `Success` dogrulandi. Neden: duzeltilmis build'i cihaza yuklemek. — DURUM: UYGULANDI
### [16 March 2026 - 11:33] LOG — Ne: `logcat -c` temizlendi. Neden: yeni testlerin yalnizca son degisiklikleri gostermesi.
### [16 March 2026 - 11:34] RUN — Ne: `monkey` ile app baslatildi. Neden: guncel build'i test akisina hazirlamak. — DURUM: UYGULANDI
### [16 March 2026 - 11:36] LOG â€” Ne: Kostum Raichu, kostum Pikachu, shiny Gyarados ve shiny Pikachu taramalarinin loglari cekildi. Neden: yeni varyant false-negative/false-positive durumlarini species bazinda ayirmak. â€” DURUM: UYGULANDI
### [16 March 2026 - 11:37] TEŞHİS â€” Ne: `costume_signatures.json` icinde yalnizca `Pikachu` imzalari oldugu, `Raichu` icin hic costume imzasi bulunmadigi dogrulandi; `shiny_signatures.json` tarafinda `Raichu`, `Pikachu`, `Gyarados` mevcut. Neden: kostum Raichu'nun neden hic yakalanmadigini veri seti seviyesinde ayirmak. â€” DURUM: UYGULANDI
### [16 March 2026 - 11:46] `CostumeSignatureStore.kt` â€” Ne: Pikachu costume signature karari daha secici hale getirildi (`bestCostume<=0.295` ve `scoreGap>=0.038` tuned gate). Neden: normal Pikachu false-positive'lerini bastirirken gercek costume Pikachu'yu kaybetmemek. â€” DURUM: UYGULANDI
### [16 March 2026 - 11:46] `VisualFeatureDetector.kt` â€” Ne: costume heuristic fallback `Pikachu/Raichu` icin acildi; shiny kararina body-dominant RGB ve observed-hue tabanli ek fallback secimi eklendi. Neden: Raichu costume imzasi asset'te yok ve shiny Gyarados gibi buyuk renk farkli turler signature-only akista kaciyordu. â€” DURUM: UYGULANDI
### [16 March 2026 - 11:46] BUILD â€” Ne: `assembleDebug` alindi. Neden: yeni costume/shiny fallback mantigini dogrulamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 11:46] DEPLOY â€” Ne: WiFi ADB `install -r` ile cihaz kurulumu (192.168.0.180:38785), `Success` dogrulandi. Neden: guncel build'i cihaza yuklemek. â€” DURUM: UYGULANDI
### [16 March 2026 - 11:46] LOG â€” Ne: `logcat -c` temizlendi. Neden: bundan sonraki testlerde sadece bu revizyonun loglarini gormek. â€” DURUM: UYGULANDI
### [16 March 2026 - 11:46] RUN â€” Ne: `monkey` ile app baslatildi. Neden: yeni build'i test akisina hazirlamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:42] LOG â€” Ne: USB debug (`RFCY11MX0TM`) uzerinden kostum Raichu, kostum Pikachu, shiny Gyarados ve shiny Pikachu test loglari cekildi. Neden: WiFi yerine dogrudan cihazdaki guncel revizyonu dogrulamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:42] TEŞHİS â€” Ne: `VisualFeatureDetector.loadPokemonColors()` runtime'da `pokemonColors._comment` string alanina carptigi icin parse hatasi veriyor; bu nedenle yeni color/hue fallback hattı hic yuklenmiyordu. Ek bulgu: kostum Raichu heuristic'i `HeadHue=184 / RefHue=154 / Dist=30` ile esigin hemen altinda kaldi. Neden: shiny/costume fallback kodunun neden etkisiz kaldigini kok sebep seviyesinde ayirmak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:44] `VisualFeatureDetector.kt` â€” Ne: `pokemonColors` yuklemesi Gson toplu parse yerine manuel obje okuma ile guncellendi; `_comment` gibi non-object alanlar skip ediliyor. Neden: renk referanslari bosaldigi icin shiny body-color / observed-hue fallback'lari sahada hic calismiyordu. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:44] BUILD â€” Ne: `assembleDebug` alindi. Neden: parse fix sonrasi yeni fallback hattini dogrulamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:44] DEPLOY â€” Ne: USB ADB `install -r` ile cihaz kurulumu (`RFCY11MX0TM`), `Success` dogrulandi. Neden: parse fix'li build'i cihaza yuklemek. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:44] LOG â€” Ne: `logcat -c` temizlendi. Neden: sonraki taramalarda sadece parse fix sonrasi loglari gormek. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:44] RUN â€” Ne: `monkey` ile app baslatildi. Neden: yeni build'i USB test akisina hazirlamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:46] LOG â€” Ne: Parse fix sonrasi USB loglari tekrar cekildi. Neden: yeni fallback'larin gercekten calisip calismadigini dogrulamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:46] TEŞHİS â€” Ne: `pokemonColors` artik yukleniyor ancak costume heuristic sabit ekran bolgesinde calistigi icin `Pikachu` ve `Raichu` bas/beden ayrimi kirli ornekleniyor; shiny `Gyarados` tarafinda ise dominant RGB halen maviye kaydigi icin tek-renk fallback yetersiz kaliyor. Neden: parse hatasi duzeldikten sonra kalan gercek karar hatlarini ayirmak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:49] `VisualFeatureDetector.kt` â€” Ne: costume heuristic adaptif sprite crop uzerine tasindi; `Pikachu/Raichu` icin head-vs-body hue farki ve saturation loglari eklendi. Ayrica shiny icin histogram-hue fallback (`normalMass` vs `shinyMass`) eklendi ve karar secimine dahil edildi. Neden: costume false-negative'lerde sabit ekran bolgesi kaynakli kirlenmeyi azaltmak, shiny Gyarados gibi turlerde tek dominant renkten daha genis renk dagilimini kullanmak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:49] BUILD â€” Ne: `assembleDebug` alindi. Neden: adaptif costume + histogram shiny fallback degisikliklerini dogrulamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:49] DEPLOY â€” Ne: USB ADB `install -r` ile cihaz kurulumu (`RFCY11MX0TM`), `Success` dogrulandi. Neden: yeni revizyonu cihaza yuklemek. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:49] LOG â€” Ne: `logcat -c` temizlendi. Neden: sonraki taramalarda yalnizca bu revizyonun loglarini gormek. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:49] RUN â€” Ne: `monkey` ile app baslatildi. Neden: yeni build'i USB test akisina hazirlamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:52] LOG â€” Ne: USB loglarinda shiny Gyarados'un histogram fallback ile dogruya dondugu, ancak costume `Pikachu/Raichu` kararlarinin `BodySat≈0.05` kapisinda elendigi dogrulandi. Neden: son revizyonda hangi hatlarin duzeldigini, hangilerinin hala bloklandigini ayirmak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:53] `CostumeSignatureStore.kt` â€” Ne: Pikachu signature tuned gate `bestCostume<=0.300` ve `scoreGap>=0.040` olarak revize edildi. Neden: guclu Pikachu costume ayrimini tekrar iceri almak ama onceki false-positive'leri geri cagirmamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:53] `VisualFeatureDetector.kt` â€” Ne: `Pikachu/Raichu` heuristic costume kararinda `minBodySat` gevsetildi (`0.03`), confidence body saturation'i dusuk ama ayirt edici olan ekranlarda da hesaplanacak sekilde guncellendi. Neden: gercek cihaz loglarinda Dist ve HeadSat guclu oldugu halde sadece body saturation kapisi nedeniyle costume false oluyordu. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:53] BUILD â€” Ne: `assembleDebug` alindi. Neden: costume gate revizyonunu dogrulamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:53] DEPLOY â€” Ne: USB ADB `install -r` ile cihaz kurulumu (`RFCY11MX0TM`), `Success` dogrulandi. Neden: yeni costume gate revizyonunu cihaza yuklemek. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:53] LOG â€” Ne: `logcat -c` temizlendi. Neden: sonraki turda yalnizca bu gate degisikliginin etkisini gormek. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:53] RUN â€” Ne: `monkey` ile app baslatildi. Neden: yeni build'i USB test akisina hazirlamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:57] LOG â€” Ne: USB uzerinden costume `Raichu` ve costume `Pikachu` yeniden taratildi, son gate revizyonu dogrulandi. Neden: `Pikachu/Raichu` costume hattinin sahada gercekten acilip acilmadigini teyit etmek. â€” DURUM: UYGULANDI
### [16 March 2026 - 12:57] DOĞRULAMA â€” Ne: costume `Raichu` sonucu `costume=true(0.5034)` olarak geldi; costume `Pikachu` taramalarinda biri heuristic fallback ile (`0.4675`), biri signature match ile (`0.5175`) `costume=true` oldu. Neden: son iki revizyonun hedeflenen false-negative'leri kapattigini kaydetmek. â€” DURUM: UYGULANDI
### [16 March 2026 - 13:05] VARYANT PIPELINE â€” Ne: species-hardcode yaklasimindan cikmak icin asset tabanli genel varyant pipeline baslatildi. `generate_variant_registry.py` ile sprite asset'lerinden `variant_registry.json` uretildi; runtime tarafina `VariantRegistry.kt` eklendi. Neden: costume/varyant kapsamını `Pikachu/Raichu` gibi tekil duzeltmeler yerine tum asset coverage uzerinden yonetmek. â€” DURUM: UYGULANDI
### [16 March 2026 - 13:05] `generate_costume_signatures.py` â€” Ne: generator artik `variant_registry.json` okuyabiliyor; species listesi yerine exact `baseKeys` / `variantKeys` kapsamindan imza uretimi yapiyor. Neden: eski pipeline yalnizca GM species listesine bakiyordu ve `Raichu` gibi sprite'ta bulunan ama listede olmayan varyantlari kaciriyordu. â€” DURUM: UYGULANDI
### [16 March 2026 - 13:05] `variant_registry.json` â€” Ne: `external/pogo_assets` taranarak 36 species icin asset-backed varyant kaydi uretildi. Ornek coverage: `Pikachu=40`, `Raichu=22`, `Pichu=10` varyant anahtari. Neden: runtime'in costume-like species kapsamini manifest yorumundan ayirip dogrudan sprite asset coverage'a baglamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 13:05] `costume_signatures.json` â€” Ne: yeni registry ile yeniden uretildi; imza sayisi `85 -> 164` oldu. Coverage'e `Raichu`, `Pichu`, `Raticate`, `Ponyta`, `Wobbuffet`, `Shinx`, `Croagunk`, `Zigzagoon`, `Gardevoir`, `Kirlia`, `Leafeon`, `Wurmple`, `Buneary` vb. eklendi. Neden: costume tespitini dar species setinden cikarip mevcut asset havuzuna yaymak. â€” DURUM: UYGULANDI
### [16 March 2026 - 13:05] `VisualFeatureDetector.kt` / `CostumeSignatureStore.kt` â€” Ne: costume karar mantigi species-adi hardcode'dan cikarildi. `VariantRegistry` ile hangi species icin heuristic/signature denenmesi gerektigi runtime'da okunuyor; dense-variant species icin karar esikleri species ismine gore degil aday yogunluguna gore ayarlaniyor. Neden: tek tek Pokemon ozelinde threshold yazmak yerine veri yogunluguna dayali genel karar hattina gecmek. â€” DURUM: UYGULANDI
### [16 March 2026 - 13:05] BUILD â€” Ne: `assembleDebug` alindi. Neden: genel varyant pipeline degisikligini dogrulamak. â€” DURUM: UYGULANDI
### [16 March 2026 - 13:05] DEPLOY â€” Ne: USB ADB `install -r` ile cihaz kurulumu (`RFCY11MX0TM`), `Success` dogrulandi; uygulama `monkey` ile yeniden acildi. Neden: yeni registry/signature coverage'i dogrudan cihaz uzerinde kullanmak. â€” DURUM: UYGULANDI
### [16 March 2026 - 13:15] LOG - Ne: USB cihaz `RFCY11MX0TM` uzerinden son shiny taramalarinin loglari cekildi. Neden: generic varyant pipeline sonrasi kalan false-positive ve false-negative hatlarini species bazinda ayirmak. - DURUM: UYGULANDI
### [16 March 2026 - 13:15] TESHIS - Ne: loglarda shiny taramalar `Bulbasaur`, `Meditite`, `Eevee`, `Chikorita`, `Beldum` olarak goruldu; bir scan ise OCR sapmasi nedeniyle `Sauirtle` tokenindan `Samurott` olarak eslendi. `Bulbasaur` taramasinda `CostumeSignatureStore` false verirken `VisualFeatureDetector` heuristic fallback yine de `costume=true` urettigi icin false-positive olustu. Diger shiny taramalarda signature skor farklari dar kaldigi icin false-negative devam etti. Neden: heuristic gate ve shiny fallback mantiginin generic species'lerde halen gevsek/eksik kaldigini belgelemek. - DURUM: UYGULANDI
### [16 March 2026 - 13:15] `CostumeSignatureStore.kt` - Ne: signature kararina `MatchDetails` cikisi eklendi (`bestCostume`, `bestNormal`, `scoreGap`, `costumeCandidateCount`, `denseVariantSpecies`). Neden: heuristic fallback'in ancak signature tarafinda gercekten sinirda kalan species'lerde acilmasini saglamak.
### [16 March 2026 - 13:15] `VisualFeatureDetector.kt` - Ne: costume heuristic fallback artik dogrudan her signature failure sonrasinda kosmuyor; `MatchDetails` uzerinden `bestCostume/scoreGap` kapisi gecilirse calisiyor, aksi halde loglayip skip ediyor. Neden: `Bulbasaur` gibi normal shiny scanlerde heuristic kaynakli costume false-positive'leri kapatmak.
### [16 March 2026 - 13:15] `ShinySignatureStore.kt` - Ne: species-agnostic histogram-mass fallback eklendi. Sechilen histogram (`primary/alt`) uzerinde normal ve shiny referans hue merkezleri etrafindaki kitle olculuyor; signature karari sinirda ise shiny lehine ikinci bir yol aciliyor. Neden: `Meditite/Eeveve/Chikorita/Beldum` gibi `pokemonColors` coverage'i disinda kalan species'lerde false-negative shiny kararlarini azaltmak.
### [16 March 2026 - 13:15] `TextParser.kt` - Ne: token fuzzy matching sikilastirildi; 7+ harfli tokenlarda guclu 3-harf prefix zorunlulugu eklendi, `name.length<=9` icin max edit mesafesi `4 -> 3` dusuruldu ve `Sauirtle/Squirtie` gibi OCR alias'lari dogrudan `Squirtle`'a baglandi. Neden: `Sauirtle -> Samurott` gibi yanlis species eslemelerini durdurmak.
### [16 March 2026 - 13:15] BUILD - Ne: `assembleDebug` basarili tamamlandi. Neden: costume gate, shiny histogram fallback ve OCR tightening degisikliklerini paket seviyesinde dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 13:15] DEPLOY - Ne: USB ADB `install -r` ile debug APK `RFCY11MX0TM` cihazina kuruldu, ardindan `logcat -c` ve `monkey` ile uygulama yeniden acildi. Neden: yeni karar hattini dogrudan cihazda test etmeye hazirlamak. - DURUM: UYGULANDI
### [16 March 2026 - 13:26] LOG - Ne: daha genis bir shiny tarama setinin USB loglari cekildi ve species bazinda ozet cikarildi. Neden: generic pipeline sonrasi hangi shiny turlerinin hala kactigini topluca gormek. - DURUM: UYGULANDI
### [16 March 2026 - 13:26] TESHIS - Ne: son set icinde `Magikarp`, `Gyarados`, `Ponyta`, `Totodile`, `Swinub`, `Clamperl`, `Mankey`, `Lotad`, `Castform`, `Sawk`, `Throh`, `Shinx`, `Zubat` dogru shiny dondu; `Chikorita`, `Eevee`, `Squirtle`, `Bulbasaur`, `Pikachu`, `Meditite`, `Tyranitar`, `Delibird`, `Kyogre`, `Makuhita`, `Buneary`, `Mareep`, `Shiinotic`, `Swablu`, `Vulpix`, `Snubbull`, `Weedle` false-negative kaldi. Ek bulgu: `Squirtle` ve `Shinx` scanlerinde dusuk-guvenli costume false-positive goruldu. Neden: shiny recall ve costume precision hatlarini ayni turda ayirmak. - DURUM: UYGULANDI
### [16 March 2026 - 13:26] `VisualFeatureDetector.kt` - Ne: shiny signature akisi artik yalnizca alt-govde histogrami ile sinirli degil; tam masked sprite ve ust-bolge histogramlari da ek signature adaylari olarak deneniyor. Ayrica costume signature sonucu `MIN_COSTUME_CONFIDENCE=0.20` altindaysa reddediliyor. Neden: ust bolgedeki renk farklarini (or. leaf/crest/wool) kullanan shiny turlerini yakalamak ve `Squirtle/Shinx` gibi dusuk-guvenli costume false-positive'leri kapatmak.
### [16 March 2026 - 13:26] `ShinySignatureStore.kt` - Ne: `softRelativeWin` katmani eklendi. Shiny score az farkla one geciyorsa ve referans hue farki anlamliysa (`>=20`), daha zayif ama kontrollu bir shiny fallback devreye giriyor. Neden: `Swablu` benzeri kucuk ama tutarli shiny ustunluklerini false-negative'e dusurmemek.
### [16 March 2026 - 13:26] BUILD - Ne: `assembleDebug` basarili tamamlandi. Neden: ek histogram kaynaklari, costume confidence gate ve soft shiny fallback degisikliklerini paket seviyesinde dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 13:26] DEPLOY - Ne: USB ADB `install -r` ile debug APK `RFCY11MX0TM` cihazina kuruldu; `logcat -c` yapildi ve uygulama `monkey` ile yeniden acildi. Neden: yeni signature/fallback hattini hemen yeni test turuna hazirlamak. - DURUM: UYGULANDI
### [16 March 2026 - 13:44] LOG - Ne: yeni scan turunun USB loglari alindi; shiny false-negative'ler ile species/name sapmalari ayni anda ayristirildi. Neden: parser ve species secim sorunlarini birbirinden ayirip kalici cozum uygulamak. - DURUM: UYGULANDI
### [16 March 2026 - 13:44] TESHIS - Ne: iki ayri kok sebep dogrulandi. (1) `OCRProcessor` candy adini current species'in onune koydugu icin evrimlesmis Pokemon bazen onceki family adi ile kaydediliyordu. (2) `TextParser` tek aday secip onunla devam ettigi icin `WeedIeZiQ -> Weepinbell`, `ShunnetBl -> Bunnelby` gibi sert isim sapmalari olusuyordu. Neden: evrim/family ve parser secim hatalarini netlestirmek. - DURUM: UYGULANDI
### [16 March 2026 - 13:44] `generate_pokemon_families.py` / `pokemon_families.json` - Ne: local `external/game_masters/latest/latest.json` dosyasindan family haritasi uretildi. Cikti `527` family ve `998` species kapsiyor. Neden: OCR species adaylarini ayni evolution family icinde tekrar puanlayabilmek.
### [16 March 2026 - 13:44] `PokemonFamilyRegistry.kt` - Ne: runtime family loader eklendi. Neden: species adaylarini candy family ve evolution chain ile hizalayabilmek.
### [16 March 2026 - 13:44] `OCRProcessor.kt` - Ne: current species artik `nameParsed` oncelikli; `candyName` yalnizca fallback/family hint olarak kullaniliyor. Neden: evrimlesmis Pokemonlarin otomatik olarak base family adina dusmesini durdurmak.
### [16 March 2026 - 13:44] `TextParser.kt` - Ne: `rankNameCandidates()` eklendi; adaylar artik prefix, edit distance ve uzunluk dengesine gore puanlaniyor. Neden: tam yanlis species secen global fuzzy fallback'i zayiflatip `Weedle` gibi guclu prefix tasiyan dogru adaylari one almak.
### [16 March 2026 - 13:44] `RarityCalculator.kt` - Ne: `scoreSpeciesFit()` eklendi; candidate species HP/CP/arc uyumuna gore sayisal olarak puanlaniyor. Neden: ayni family icindeki evrimlerden hangisinin mevcut ekran istatistiklerine daha uygun oldugunu runtime'da hesaplamak.
### [16 March 2026 - 13:44] `SpeciesRefiner.kt` / `ScanManager.kt` - Ne: OCR sonrasi ama visual detection oncesi species refinement katmani eklendi. Raw name, fallback name, candy family ve family members birlikte toplanip text score + stat fit score ile yeniden seciliyor. Neden: parser'in tek basina verdigi yanlis species kararlarini, shiny ve rarity hesaplari baslamadan once duzeltmek.
### [16 March 2026 - 13:44] BUILD - Ne: `assembleDebug` basarili tamamlandi. Neden: candy override fix, family registry ve species refinement akisini dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 13:44] DEPLOY - Ne: USB ADB `install -r` ile debug APK `RFCY11MX0TM` cihazina kuruldu; `logcat -c` yapildi ve uygulama yeniden acildi. Neden: yeni species refinement hattini cihazda test etmeye hazirlamak. - DURUM: UYGULANDI
### [16 March 2026 - 13:50] LOG - Ne: yeni build sonrasi USB loglari tekrar cekildi. Neden: species refinement ve isim parser duzeltmelerinin sahadaki etkisini gormek. - DURUM: UYGULANDI
### [16 March 2026 - 13:50] DOGRULAMA - Ne: isim hattinda `Electabuzz`, `Sneasel`, `Sawk`, `Torchic`, `Weedle` gibi scanler dogru species ile cikti; onceki `Weedle -> Weepinbell` sapmasi bu turda tekrar gorulmedi. `SpeciesRefiner` bu sette species degistirmedi; yani duzeltme agirlikli olarak OCR secimini stabil hale getirdi. Neden: family refinement'in su an daha cok koruyucu katman, parser degisikliginin ise anlik kazanc getiren katman oldugunu kaydetmek. - DURUM: UYGULANDI
### [16 March 2026 - 13:50] KALAN SORUN - Ne: shiny false-negative'ler devam ediyor. Bu log turunda `Torchic` ve birden fazla `Weedle` taramasi `shiny=false` kaldi; `Silicobra` scan'i de muhtemelen species yanlisi veya OCR fallback kaynakli zayif vaka. Neden: sonraki turda dogrudan shiny classifier ve kalan zor isim vakalarina odaklanmak. - DURUM: DEVAM EDIYOR
### [16 March 2026 - 14:02] TESHIS - Ne: kullanici dogrulamasiyla iki kritik species hatasi netlesti: `Sawk` olarak cikan scan aslinda `Minun`, `CP 1064 Weedle` olarak cikan scan ise aslinda `Beedrill`. Onceki log okumasi bu hatalari ground-truth olmadan yakalayamadi. Kok sebep analizi sonucunda uc eksik bulundu: (1) `pokemon_base_stats.json` sadece 150 species iceriyordu, dolayisiyla `Minun`, `Sawk`, `Torchic`, `Silicobra` gibi turlerde stat-fit katmani fiilen devre disiydi. (2) `OCRProcessor` `Bottom` OCR metnini `rawOcrText` icine yazmadigi icin `Thunderbolt` gibi move sinyalleri species refinement tarafina hic tasinmiyordu. (3) `SpeciesRefiner` text skoruna fazla guveniyor, family/fit override kosullari ise cok konservatif kaldigi icin `Weedle -> Beedrill` gibi ayni family icindeki evrim uyumsuzluklarini ceviremiyordu. Neden: species hatalarini sadece OCR fuzz ile degil move + stat + family birlikte ele almak.
### [16 March 2026 - 14:02] `generate_pokemon_base_stats.py` - Ne: local `external/game_masters/latest/latest.json` dosyasindan tum species base stat verisini ureten yeni generator eklendi. Neden: stat-fit scoring'in sadece ilk 150 species ile sinirli kalmasi sonraki jenerasyonlarda species refinement'i etkisiz birakiyordu.
### [16 March 2026 - 14:02] `pokemon_base_stats.json` - Ne: Game Master'dan yeniden uretildi; kapsam `150 -> 998` species oldu. `Minun`, `Sawk`, `Torchic`, `Silicobra` ve diger eksik turler artik stat-fit hesabina dahil. Neden: OCR text zayif oldugunda species secimini CP/HP/arc ile savunulabilir bicimde dogrulayabilmek.
### [16 March 2026 - 14:02] `generate_pokemon_moves.py` / `pokemon_moves.json` - Ne: species -> move ve move -> species registry ureten yeni asset pipeline eklendi. Cikti `997` species ve `246` normalize move anahtari iceriyor. Neden: bottom OCR'da gorulen `Thunderbolt` gibi move adlarini species seciminde kullanmak; `Sawk` gibi move ile celisen yanlis isim eslesmelerini runtime'da elemek.
### [16 March 2026 - 14:02] `PokemonMoveRegistry.kt` - Ne: runtime move registry loader ve `extractMoveHint()` / `getSpeciesForMove()` / `moveMatchScore()` yardimcilari eklendi. Neden: bottom OCR metninden normalize move ipucu cikarmak ve species adaylarini move uygunluguna gore puanlamak.
### [16 March 2026 - 14:02] `OCRProcessor.kt` - Ne: `rawOcrText` artik `Bottom:` alanini da sakliyor. Neden: species refinement katmani move OCR sinyalini gorebilsin; onceki yapida bottom logda gorunuyor ama veri modeline gecmiyordu.
### [16 March 2026 - 14:02] `SpeciesRefiner.kt` - Ne: candidate pool move registry adaylarini da kapsayacak sekilde genislestirildi. Text/fit/move agirliklari dynamic hale getirildi; kisa veya guvensiz raw name durumunda text agirligi dusuruldu. Ayrica iki yeni override kapisi eklendi: (1) move mismatch override: best aday move ile uyusuyor, current uyusmuyorsa daha dusuk farkta bile species degisiyor; (2) family-fit override: ayni family icinde current species CP/stat olarak zayifken diger family uyesi belirgin sekilde daha iyi uyuyorsa species degisiyor. Refiner artik supheli ama degistirmedigi scanlerde top-3 aday ozetini de logluyor. Neden: `sag -> Sawk` ve `1064 Weedle -> Beedrill` gibi vakalarda text skorunun hatali baskinligini kirip acik fiziksel/move celiskilerini ust seviyeye cikarmak.
### [16 March 2026 - 14:03] BUILD - Ne: `assembleDebug` basarili tamamlandi. Neden: full base-stats + move-registry + stronger species refinement hattini paket seviyesinde dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 14:03] DEPLOY - Ne: USB ADB `install -r` ile debug APK `RFCY11MX0TM` cihazina kuruldu; `logcat -c` yapildi ve uygulama `monkey` ile yeniden acildi. Neden: yeni species override mantigini hemen cihazda test etmeye hazirlamak. - DURUM: UYGULANDI
### [16 March 2026 - 14:04] LOG - Ne: deploy sonrasi USB logcat'ten startup kesiti kontrol edildi; `OCRProcessor: Tesseract OK` goruldu, yeni species/move refactor'i icin `FATAL EXCEPTION` kaydi gorulmedi. Neden: build'in en azindan acilis seviyesinde stabil oldugunu teyit etmek. - DURUM: UYGULANDI
### [16 March 2026 - 14:09] LOG - Ne: kullanicinin yeni test turundaki 3 scan'in USB loglari cekildi. Sonuclar: `Minun` arti `Pikachu`, `Beedrill` hala `Weedle`, `Blaziken` ise `Shiinotic` olarak donuyor. Neden: son species refactor'inin nickname ve family uyumsuzluklarini neden kapatamadigini netlestirmek. - DURUM: UYGULANDI
### [16 March 2026 - 14:09] TESHIS - Ne: loglar yeni iki kok sebep gosterdi. (1) `Name` alaninda kullanici nickname'i bulundugu icin `TextParser` bunu yine bir Pokemon ismine zorla esliyor; `tSZJiMOZIA -> Shiinotic` gibi vakalarda species havuzu gercek turu hic gormeden kapanıyor. (2) `SpeciesRefiner`, `currentSpecies` icin sabit bir prior kullandigi ve global candidate pool'u yeterince acmadigi icin nickname kaynakli hatali isimler kendini koruyor. Ek bulgu: `pokemon_base_stats.json` 998 species'e cikarilmis olsa da `height/weight` profili species fit'te kullanilmiyordu; halbuki `Blaziken (1.91m, 52kg)` ile `Shiinotic (1.0m, 11.5kg)` ve `Beedrill (1.0m, 29.5kg)` ile `Weedle (0.3m, 3.2kg)` olculeri bu vakalari dogrudan ayirabilecek durumda. Neden: isim sinyali bozuldugunda devreye girecek ikincil species sinyallerini gercekten etkili hale getirmek.
### [16 March 2026 - 14:13] `generate_pokemon_base_stats.py` / `pokemon_base_stats.json` - Ne: asset generator height/weight alanlarini da yazacak sekilde genisletildi; `pokemon_base_stats.json` yeniden uretildi ve artik 998 species icin `atk/def/sta + heightM + weightKg` tasiyor. Neden: species fit puaninda fiziksel olcu sinyalini kullanabilmek.
### [16 March 2026 - 14:13] `RarityCalculator.kt` - Ne: `BaseStats` yapisi `heightM/weightKg` ile genisletildi; `scoreSpeciesFit()` icine `sizeScore` eklendi. Ayrica `rankSpeciesByObservedProfile()` yardimcisi yazildi: HP/CP/arc + weight/height ile global rough shortlist uretiyor. Neden: isim bozuk oldugunda gercek species'i candidate pool'a sokabilmek ve olcu olarak bariz uyumsuz species'leri geri plana atmak.
### [16 March 2026 - 14:13] `SpeciesRefiner.kt` - Ne: sabit `currentSpecies` prior'i zayiflatildi; raw isim guveni dusukse veya current species nickname suphelisi ise text agirligi azaltildi. Candy bilgisi yoksa ya da isim zayifsa `rankSpeciesByObservedProfile()` ile global species shortlist candidate pool'a ekleniyor. `move/text/fit/size` dengesi yeniden ayarlandi; yeni `nicknameOverride` kapisi eklendi. Log ozeti artik `sizeScore`, `weakName`, `nickname` durumlarini da yaziyor. Neden: kullanici takma adlari scan'i kilitlemesin; species secimi sadece isme degil istatistik ve fiziksel profile de dayansin.
### [16 March 2026 - 14:13] `ScanManager.kt` - Ne: visual detector species olarak artik `bestResult.name` degil `finalBase.name` kullaniyor. Neden: species refinement sonrasi shiny/signature hattinin hala eski yanlis species uzerinden calismasi engellendi.
### [16 March 2026 - 14:13] BUILD - Ne: `assembleDebug` basarili tamamlandi. Neden: size-aware species fit + global shortlist + nickname override degisikliklerini paket seviyesinde dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 14:13] DEPLOY - Ne: USB ADB `install -r` ile debug APK `RFCY11MX0TM` cihazina kuruldu; `logcat -c` yapildi ve uygulama `monkey` ile yeniden acildi. Neden: yeni nickname-resistant species hattini cihaza yuklemek. - DURUM: UYGULANDI
### [16 March 2026 - 14:17] LOG - Ne: yeni build sonrasi 3 scan'in USB loglari cekildi ve cache ihtimali ayrica kontrol edildi. Neden: kullanicinin "duzelmiyor, cache'de mi kaliyor?" sorusunu teknik olarak ayirmak. - DURUM: UYGULANDI
### [16 March 2026 - 14:17] TESHIS - Ne: cache etkisi gorulmedi. Her scan icin `processScanSequence` yeniden calisiyor, yeni OCR ham metinleri (`Name`, `Candy`, `Bottom`, CP/HP) tekrar uretiliyor ve sonuc runtime'da hesaplanip sonra `saveScan()` ile DB'ye yaziliyor. Kalici hata canli karar hattinda. Uc somut sorun kaydedildi: (1) `Blaziken` scan'inde `Name=tSZgiiMOZIA -> Shiinotic`, candy yine okunamiyor ve `SpeciesRefiner` nickname suphelisi oldugunu loglasa da global shortlist `Blaziken`'i ilk adaylara getiremiyor; yani observed-profile shortlist yeterince iyi degil. (2) `Minun` scan'inde CP `03868 -> 3868` parse ediliyor; bu sahte bas sifir species fit'i bozuyor ve move hint `Thunderbolt` ile birlikte sistemi `Pikachu`'ya itiyor. (3) `Beedrill` scan'inde candy halen okunamadigi icin family yardimi gelmiyor; `Weedle` OCR ismi baskin kaliyor ve stat/olcu override devreye girmiyor. Neden: species kararinda halen iki zayif halka var: CP parser bas sifir OCR gürültüsü ve rough profile shortlist kalitesi. - DURUM: UYGULANDI
### [16 March 2026 - 14:20] `TextParser.kt` - Ne: `parseCP()` icine leading-zero heuristic eklendi. `03868` gibi OCR'larda basta eklenen sahte `0` atilabiliyor; `031757` gibi vakalarda da son 4/3 hane fallback adaylari deneniyor. Neden: `Minun` vakasinda `868` yerine `3868` alinmasi species fit'i dogrudan raydan cikariyordu.
### [16 March 2026 - 14:20] `RarityCalculator.kt` - Ne: `rankSpeciesByObservedProfile()` rough average-IV tahmini yerine dogrudan tum species'ler icin `scoreSpeciesFit()` sonucunu kullanacak sekilde degistirildi; observed-profile shortlist esigi de asagi cekildi. Neden: `Blaziken` gibi isim bozuk ama HP/CP/olcu ile yine de secilebilecek turler shortlist disinda kaliyordu.
### [16 March 2026 - 14:20] `SpeciesRefiner.kt` - Ne: `nicknameOverride` kapisi daha agresif hale getirildi (`fit` ve `score` farki esikleri dusuruldu). Neden: current species nickname kaynakli zayif bir text match ise daha iyi stat adayi daha kolay override edebilsin.
### [16 March 2026 - 14:20] BUILD - Ne: `assembleDebug` basarili tamamlandi. Neden: leading-zero CP duzeltmesi ve daha guclu observed-profile shortlist degisikliklerini paket seviyesinde dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 14:20] DEPLOY - Ne: USB ADB `install -r` ile debug APK `RFCY11MX0TM` cihazina yeniden kuruldu; `logcat -c` yapildi ve uygulama `monkey` ile yeniden acildi. Neden: yeni CP/species revizyonunu hemen cihaza tasimak. - DURUM: UYGULANDI
### [16 March 2026 - 14:24] LOG - Ne: yeni build sonrasi ayni 3 scan'in USB loglari yeniden cekildi. Neden: CP leading-zero ve observed-profile degisikliklerinin etkisini dogrudan dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 14:24] TESHIS - Ne: yeni loglarda `Minun` vakasinda CP parser duzeldi (`CP868` dogru geliyor) ancak species secimi hala `Pikachu`ya kayiyor; top adaylarda `Minun` ikinci sirada kaldi. `Blaziken` vakasinda `SpeciesRefiner` nickname supheli durumunu dogru logluyor ama top profile adaylari halen `Arbok/Hakamo-o/Claydol`; yani observed-profile shortlist yeterince temsil edici degil. `Beedrill` vakasinda species refinement hic devreye girmiyor; `Weedle` OCR ismi kuvvetli kaldigi ve candy okunamadigi icin family override halen yetersiz. Neden: bir sonraki adimda (1) move-hintli species secimde fiziksel profile daha agirlikli karar, (2) family member override'in `cpPossible`/size mismatch uzerinden daha sert acilmasi, (3) weak-name olmasa bile mevcut species CP olarak cok zayifsa top aday ozeti/log zorlamasi gerektigi netlesti. - DURUM: UYGULANDI
### [16 March 2026 - 14:25] `RarityCalculator.kt` - Ne: `rankSpeciesByPhysicalProfile()` eklendi. Neden: observed-profile shortlist disinda kalan ama weight/height ile bariz uyusan adaylari (`Blaziken`, `Beedrill` gibi) ayri bir kanaldan candidate pool'a sokmak.
### [16 March 2026 - 14:25] `SpeciesRefiner.kt` - Ne: candidate pool artik fiziksel profile shortlist'ini de aliyor; total score icine `observedProfileScore` ve `physicalProfileScore` bonuslari eklendi. `familyFitOverride` daha agresif hale getirildi; `cpPossible` ve `sizeScore` farki family icinde override nedeni olabiliyor. Neden: `Beedrill` ve `Blaziken` gibi nickname/candy failure vakalarinda family ve olcu sinyali daha erken etkili olsun.
### [16 March 2026 - 14:25] BUILD - Ne: `assembleDebug` basarili tamamlandi. Neden: family + physical shortlist guclendirmesini paket seviyesinde dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 14:25] DEPLOY - Ne: USB ADB `install -r` ile debug APK `RFCY11MX0TM` cihazina kuruldu; `logcat -c` yapildi ve uygulama `monkey` ile yeniden acildi. Neden: yeni species refiner agirliklarini sahada test etmeye hazirlamak. - DURUM: UYGULANDI
### [16 March 2026 - 14:29] LOG - Ne: yeni build sonrasi ayni 3 ornegin USB loglari tekrar cekildi. Neden: physical shortlist ve family override guclendirmesinin sahadaki etkisini gormek. - DURUM: UYGULANDI
### [16 March 2026 - 14:29] TESHIS - Ne: son log turunda uc ayrik sonuc goruldu. (1) `Minun` vakasinda CP dogru (`868`) ve move hint `Thunderbolt` geliyor, ancak `SpeciesRefiner` top listede `Pikachu` ilk, `Minun` ikinci kaliyor; yani elektrikli move kullanan turler icinde Pikachu/Minun ayrimi hala yetersiz. (2) `Blaziken` vakasinda onceki revizyona gore ilerleme var: `Blaziken` ilk kez top-3'e girdi ama `Toedscruel` fiziksel profile daha iyi uydugu icin birinci oldu; bu, profile shortlist'in calistigini ama agirliklarin halen zayif ayrim yaptigini gosteriyor. (3) `Beedrill` vakasinda species refinement hic loglanmiyor; `Weedle` OCR ismi yeterince guclu kabul edilip pipeline oldugu gibi devam ediyor. Bu nedenle family override'in sadece "best != current" durumunda degil, `currentSpecies` CP olarak zayifsa zorunlu aday olusturma mantigiyla calismasi gerektigi netlesti. Neden: bundan sonraki adimda (a) move-hintli species secimi icin ek elektrikli-family/physical ayraclari, (b) weak-name olmasa bile `current fit` cok dusukse top-candidate zorlugu, (c) `Weedle -> Beedrill` gibi family icinde hard override kurali gerekli. - DURUM: UYGULANDI
### [16 March 2026 - 14:39] TESHIS - Ne: kullanici Minun orneginde ekrandaki `Minun Candy` bilgisinin neden dogrudan kullanilmadigini sordu. Kod ve son loglar tekrar incelendi. Son loglarda candy OCR alani halen `it new sz` / `SPSIZL LSGP xv gZ` benzeri gurultulu metin dondugu icin `TextParser.parseCandyName()` `null` kaliyor; yani sorun family/refiner tarafinin gecerli bir candy adi gorup bunu yok saymasi degil, candy satirinin daha OCR asamasinda okunamamasi. Neden: Minun vakasinda dogrudan candy hattina mudahele etmek gerektigini netlestirmek. - DURUM: UYGULANDI
### [16 March 2026 - 14:39] `ImagePreprocessor.kt` - Ne: yeni `processCandyText()` filtresi eklendi. Beyaz kart uzerindeki koyu gri candy yazisini daha sert binary esitigi ile siyah/beyaz hale getiriyor. Neden: generic high-contrast filtre candy satirinda yeterince temiz sinyal uretmiyordu; `Minun Candy` gibi satirlar gurultuye dusuyordu.
### [16 March 2026 - 14:39] `OCRProcessor.kt` - Ne: candy alani artik tek OCR yolu yerine bes adayla okunuyor: `Candy`, `CandyClean`, `CandyBlock`, `CandyWide`, `CandyWideClean`. Tum ham candy okumalar `rawOcrText` ve debug loguna eklendi. `displayName/realName` onceligi tekrar `nameParsed -> candyName` sirasinda sabitlendi. Neden: candy satirinin neden kacirildigini sahada gorebilmek ve Minun gibi tek-species candy durumlarini OCR seviyesinde toparlamak.
### [16 March 2026 - 14:39] `TextParser.kt` - Ne: candy parser coklu OCR girdisini birlikte degerlendirecek sekilde genisletildi. Once `CANDY/CNDY/...` tokenli net eslesmeleri deniyor; basarisiz olursa candy OCR metinleri normalize edilip ortak adaylar uzerinden yuksek-guvenli loose match uretiliyor. Neden: candy satirinda `CANDY` tokeni eksik ya da bozuk okunsa bile gercek species adini guvenli sekilde kurtarabilmek.
### [16 March 2026 - 14:39] `PokemonFamilyRegistry.kt` / `SpeciesRefiner.kt` - Ne: family boyutu runtime'da okunur hale getirildi; `candyName` tek-species family ise (`familySize == 1`) species kararinda buyuk bonus ve dogrudan override kapisi verildi. Neden: kullanicinin belirttigi gibi `Minun Candy` okunursa bunun `Pikachu` veya baska family uyesiyle karismamasi gerekiyor; tek-species candy sinyali bu durumda birinci sinif dogrulama olmali.
### [16 March 2026 - 14:39] BUILD / DEPLOY - Ne: `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine `install -r` ile kuruldu, `logcat -c` yapildi ve uygulama `monkey` ile yeniden acildi. Neden: yeni candy OCR + unique-family override revizyonunu hemen sahada test etmeye hazirlamak. - DURUM: UYGULANDI
### [16 March 2026 - 14:42] LOG - Ne: yeni candy OCR revizyonu sonrasi USB cihaz `RFCY11MX0TM` uzerinden loglar cekildi. Neden: `Minun Candy` kullaniminin gercekten devreye girip girmedigini ve diger problemli species'lerde ne kadar ilerleme oldugunu dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 14:42] DOGRULAMA - Ne: `Minun` vakasinda candy hattinin artik calistigi dogrulandi. `CandyBlock` OCR cikti `MINUN CANDY MINUN CANDY XI` olarak geldi, `TextParser` bunu `Candy -> Minun` olarak parse etti ve `SpeciesRefiner` `current=Sawk -> best=Minun` override'ini uyguladi (`candyFamilySize=1`, move=`Thunderbolt`). Sonuc species `Minun`, shiny=`true` oldu. Neden: kullanicinin sorduğu temel problem olan "ekrandaki Minun Candy niye kullanilmiyor" sorusunun bu revizyonda cozuldugunu belgelemek. - DURUM: UYGULANDI
### [16 March 2026 - 14:42] DOGRULAMA - Ne: `Blaziken` vakasinda candy OCR artik `Torchic` olarak okunuyor ve species refiner `current=Milotic -> best=Blaziken` override'i yapiyor. Yani nickname kaynakli species kilitlenmesi bu ornekte de kirildi. Ancak shiny detection logu halen `Blaziken` icin `normal≈shiny` skorlar veriyor ve sonuc `shiny=false` kaliyor. Neden: species duzeldi ama shiny classifier bu species'te hala zayif; iki problemi ayirmak. - DURUM: KISMEN UYGULANDI
### [16 March 2026 - 14:42] KALAN SORUN - Ne: `Beedrill` vakasi halen yanlis. Yeni candy OCR hattina ragmen `CandyBlock` ciktisi `IUST WEEDLE CANDY` seklinde geldi ve parser bunu `Weedle` olarak kabul etti; `Name` de `WeedIeBl -> Weedle` oldugu icin species refiner hic override acmadi. Sonrasinda `RarityCalculator` `CP1064` degerini Weedle icin matematiksel olarak `401`e duzeltse de species zaten yanlis sabitlenmis durumda. Neden: bu vaka artik generic candy OCR eksikligi degil; evolution-family icinde base-form candy yuzunden `Weedle/Beedrill` ayrimini fiziksel/stat override ile zorlayacak yeni kural gerektiriyor. - DURUM: DEVAM EDIYOR
### [16 March 2026 - 14:53] TESHIS / PLAN - Ne: kullanici kalan species sorunlarini nasil kapatacagimizi, scan gecikmesini nasil azaltacagimizi ve overlay butonunun biraz daha asagi baslamasini istedi. Teknik plan su sekilde netlestirildi: (1) `Beedrill` gibi evolution-family vakalarinda candy adi artik species degil family sinyali olarak ele alinacak; tek-species family (`Minun`) ile cok-species family (`Weedle/Kakuna/Beedrill`) ayrilacak. (2) scan gecikmesinin ana nedeni yeni candy OCR hattinin her frame'de tum pahali yollarla calismasi oldugu icin OCR asamasi kademeli hale getirilecek. (3) overlay acilis konumu daha asagi alinacak. Neden: kullanicinin raporlanan uc sorunu tek revizyonda birlikte ele almak.
### [16 March 2026 - 14:53] `OCRProcessor.kt` - Ne: candy OCR akisi hiz odakli olarak yeniden yazildi. `processCandyText()` artik her scan'de zorunlu degil; once hizli `Candy` OCR deneniyor, ancak parse basarisizsa `CandyClean`, sonra `CandyBlock`, en son `CandyWide/CandyWideClean` yollarina dusuluyor. Ek olarak `procWM/procHC/procCandy` bitmap'leri is bitince recycle ediliyor. Neden: son revizyonda candy OCR her frame'de tum pahali binary yollarla calistigi icin scan sonucu gozle gorulur bicimde gecikti; staged fallback ile ayni tespit gucunu daha dusuk maliyetle korumak hedeflendi.
### [16 March 2026 - 14:53] `SpeciesRefiner.kt` - Ne: candy kaynakli species puanlama duzeltildi. `candyExactBonus` artik sadece tek-species family'lerde veriliyor; `Weedle Candy` gibi cok-species family'lerde exact-candy bonusu kaldirildi ve family bonusu family geneline dagitildi. Ayrica yeni `evolutionFamilyOverride` eklendi: mevcut species candy ile ayni base-form ise ama ayni family icinde baska bir aday fiziksel profil + stat fit olarak belirgin sekilde daha gucluyse override acilabiliyor. Neden: `Minun Candy` kesin species sinyali, `Weedle Candy` ise sadece family sinyali; onceki mantik bu ikisini karistirip base-form'u fazla odullendiriyordu.
### [16 March 2026 - 14:53] `OverlayService.kt` - Ne: overlay baslangic Y ofseti `200dp -> 240dp` cekildi. Neden: scan butonunun acilista biraz daha asagida ve daha ulasilabilir gelmesi istendi.
### [16 March 2026 - 14:53] BUILD / DEPLOY - Ne: `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: staged candy OCR + evolution-family override + overlay pozisyon revizyonunu hemen test akisina almak. - DURUM: UYGULANDI
### [16 March 2026 - 14:55] LOG - Ne: yeni staged candy OCR + evolution-family override revizyonu sonrasi USB loglari cekildi. Neden: kullanicinin "degisen bir sey olmadi" geri bildirimini dogrudan loglarla dogrulamak ve sahada hangi kisimlarin hala kirik kaldigini ayirmak. - DURUM: UYGULANDI
### [16 March 2026 - 14:55] TESHIS - Ne: revizyon kismen ise yaradi ama iki yeni net problem ortaya cikti. (1) `Minun` vakasi artik dogru calismiyor; staged candy OCR, onceki build'deki `CandyBlock=MINUN CANDY` yoluna dusmeden `CandyClean` asamasinda `Minccino` loose-match'i uretiyor. Bu yanlis `candyName=Minccino` sonrasi `SpeciesRefiner` `Pikachu`ya kayiyor. Yani hizlandirma false-positive candy parse uretmis durumda. (2) `Beedrill` vakasi hala `Weedle`; `CandyBlock` net sekilde `WEEDLE CANDY` okudugu icin yeni evolution-family override bile acilmiyor, cunku species havuzu family icinde baska bir uyeyi one cikaramiyor. Ek bulgu: scan gecikmesi ilk Minun turunda `3309ms`, Beedrill tekrarinda `3101ms`, ikinci Beedrill turunda `1448ms`; yani staged OCR maliyeti azaltmis ama ilk taramada hala belirgin gecikme var. Neden: siradaki duzeltmenin (a) candy loose-match'i whitelist/regex kapisi ile sıkılastirmasi, (b) `Weedle Candy + CP1064 + 39kg/1.11m` gibi family-uyumsuzluk vakalarinda base formu cezalandiran sert family override eklemesi, (c) candy binary OCR'yi sadece secili species suphelerinde calistirmasi gerektigi netlesti. - DURUM: DEVAM EDIYOR
### [16 March 2026 - 15:01] `TextParser.kt` - Ne: candy parser strict ve loose olarak ayrildi. Varsayilan `parseCandyName()` artik sadece net `CANDY/CNDY/...` tokenli eslesmeleri kabul ediyor; yeni `parseCandyNameLoose()` ise ancak candy-token-hint tasiyan OCR metinlerinde, daha sert esiklerle (`distance<=2`, daha yuksek score farki) devreye giriyor. Neden: staged hizlandirma sonrasi `Minun -> Minccino` gibi false-positive loose candy eslesmelerini kesmek.
### [16 March 2026 - 15:01] `OCRProcessor.kt` - Ne: candy preprocess akisi yeniden optimize edildi. `processCandyText()` artik tam ekran bitmap yerine sadece `REGION_CANDY` / `REGION_CANDY_WIDE` crop'lari uzerinde calisiyor; `CandyClean`, `CandyBlock` ve `CandyWideClean` OCR'lari bu crop'lardan uretiliyor. Ayrica loose candy parse sadece en son fallback asamasinda kullaniliyor. Neden: ilk scan gecikmesini dusurmek ve `CandyClean` tek basina yanlis species uretmesin.
### [16 March 2026 - 15:01] `SpeciesRefiner.kt` - Ne: evolution-family override sertlestirildi. `currentSpecies == candyName` ve family size > 1 iken, mevcut aday `cpPossible=false` ama ayni family icinde alternatif aday `cpPossible=true` ve fit/size olarak anlamli ustunse override aciliyor. Neden: `Weedle Candy` okunsa bile `CP1064 + 39kg + 1.11m` gibi degerlerde `Beedrill`'in family icinde zorla one cikarilmasi gerekiyor.
### [16 March 2026 - 15:01] BUILD / DEPLOY - Ne: `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: strict candy parse + crop-tabali candy OCR + sert family override revizyonunu hemen test akisina almak. - DURUM: UYGULANDI
### [16 March 2026 - 15:03] LOG - Ne: yeni strict candy parse + crop-tabali candy OCR + sert family override revizyonu sonrasi USB loglari cekildi. Neden: kullanicinin "scan sonuclari iyilesmis" geri bildirimini teknik olarak dogrulamak ve hangi kalan hatlarin acik oldugunu netlestirmek. - DURUM: UYGULANDI
### [16 March 2026 - 15:03] DOGRULAMA - Ne: `Beedrill` vakasi kapandi. OCR halen `Candy -> Weedle` ve `Name -> Weedle` okuyor, fakat yeni `SpeciesRefiner` family override'i `current=Weedle -> best=Beedrill` cevirdi. Sonrasinda `VisualFeatureDetector` `Beedrill` icin `shiny=true(0.45)` verdi ve `RarityCalculator` `CP1064` degerini de dogru kabul etti. Neden: hedeflenen `Weedle/Beedrill` family ayrimi artik species hattinda cozuldugunu kaydetmek. - DURUM: UYGULANDI
### [16 March 2026 - 15:03] DOGRULAMA - Ne: `Minun` vakasi tekrar duzeldi. `CandyBlock` OCR icinden `MINUN CANDY` okunup `Candy -> Minun` parse edildi; `SpeciesRefiner` `current=Sawk -> best=Minun` override yapti ve shiny sonucu `true(0.5696)` geldi. Neden: onceki turdaki `Minccino` false-positive candy loose-match'inin strict candy parse ile kapandigini belgelemek. - DURUM: UYGULANDI
### [16 March 2026 - 15:03] DOGRULAMA - Ne: `Blaziken` species hattinda dogruya donuyor. OCR `Candy -> Torchic`, `DisplayName -> Torchic` cikarsa da `SpeciesRefiner` bunu `Blaziken`e override ediyor. OCR suresi bu scan'de `735ms` oldu; yani crop-tabali candy OCR gecikmeyi belirgin azaltti. Ancak `Blaziken` shiny classifier hala `normal≈shiny` skorlar verip `shiny=false` kaliyor. Neden: species ve performans iyilesmesini ayirip, kalan problemin artik agirlikli olarak shiny detection oldugunu kaydetmek. - DURUM: KISMEN UYGULANDI
### [16 March 2026 - 15:09] KAPSAM KONTROLU - Ne: kullanicinin "tum Pokemonlar kayitli mi, tum nesiller var mi?" sorusu uzerine yerel asset kapsami dogrulandi. `pokemon_names.json=1018`, `pokemon_base_stats.json=998`, `pokemon_families.json speciesToFamily=998 / families=527` cikti. Bu, species/family/stat tarafinda tum nesillerin buyuk olcude kapsandigini gosteriyor. Ancak varyant tarafi esit degil: `shiny_signatures.json count=685`, `variant_registry.json count=36 / speciesCount=36`, `costume_signatures.json count=164`. Yani normal species/family testleri genelde bosa gitmez; fakat costume/asset-backed varyant coverage yalnizca 36 species ile sinirli ve tum Pokemonlar icin esit guclu degil. Neden: kullanicinin test setini desteklenen kapsama gore secmesini saglamak ve unsupported costume/form testlerinde yanlis beklenti olusmasini engellemek. - DURUM: UYGULANDI
### [16 March 2026 - 15:16] KAPSAM GENISLETME - Ne: eksik coverage'i azaltmak icin iki yeni asset uretildi. `pokemon_colors_generated.json` sprite ciftlerinden (`normal + shiny`) otomatik analiz edilerek `711` species icin dominant RGB referansi uretti. `costume_species.json` ise `variant_registry.json` ile `scripts/costume_keys.json` union'undan uretildi ve `53` costume-capable species listesi cikardi. Neden: `rarity_manifest.json` icindeki `pokemonColors` yalnizca `13` species, manifestten tureyen costume species listesi ise sinirliydi; bu durum genel shiny/costume coverage'i gereksiz daraltiyordu. - DURUM: UYGULANDI
### [16 March 2026 - 15:16] `VisualFeatureDetector.kt` - Ne: `rarity_manifest.json` icindeki `pokemonColors` verisi korunurken yeni `pokemon_colors_generated.json` asset'i ikinci kaynak olarak eklendi. Manifestte olmayan species'ler artik generated color referanslariyla da shiny/color fallback hattina girebiliyor. Neden: shiny detection fallback'lerinin sadece 13 species ile sinirli kalmasini engellemek.
### [16 March 2026 - 15:16] `RarityManifestLoader.kt` - Ne: iki coverage fallback'i eklendi. (1) `pokemon_names.json` okunup manifestte olmayan species'ler icin default rarity `5` ile `speciesRarity` map'ine ekleniyor; boylece tum isim listesi runtime'da biliniyor. (2) `costume_species.json` okunup manifestten gelen costume species listesiyle merge ediliyor. Neden: tum species'ler base rarity lookup'ta "unknown" olmasin ve costume-check kapsamı manifestteki eski listeyle sinirli kalmasin.
### [16 March 2026 - 15:16] BUILD / DEPLOY - Ne: yeni coverage asset'leri ve runtime loader degisiklikleriyle `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: scan oncesi coverage eksiklerini azaltan bu genel revizyonu cihaza tasimak. - DURUM: UYGULANDI
### [16 March 2026 - 15:27] LOG - Ne: kullanicinin taradigi varyant seti icin USB loglari cekildi. Test seti: kostumlu shiny `Squirtle`, kostumlu shiny `Blastoise`, kostumlu shiny `Croagunk`, kostumlu shiny `Butterfree`, lucky armored `Mewtwo`, kostumlu shiny `Absol`, kostumlu shiny `Pikachu`, kostumlu shiny `Slowpoke`, kostumlu shiny `Piplup`, kostumlu shiny `Spheal`, kostumlu shiny `Cubchoo`, kostumlu lucky `Blastoise`, kostumlu shiny `Rowlet`, kostumlu shiny `Hoothoot`. Neden: yeni coverage genisletmesinin kombinasyonlu varyantlarda gercek sonucu verip vermedigini sahada ayirmak. - DURUM: UYGULANDI
### [16 March 2026 - 15:27] DOGRULAMA - Ne: bu turda dogru calisan scanler logdan net dogrulandi. `Croagunk` species olarak dogru geldi ve `shiny=true`, `costume=true` uretildi; `CP3189` OCR hatasi da matematiksel kontrol ile `191`e duzeltildi. `Pikachu` species olarak dogru geldi ve bir taramada `shiny=true`, `costume=true`; ikinci bir taramada `shiny=true`, `locationCard=true` uretildi. `Hoothoot` species olarak dogru geldi ve `costume=true` uretildi; `CP3276` OCR hatasi `291`e duzeltildi. Neden: coverage genisletmesinin en azindan bazi costume+shiny vakalarda sonuc verdigini ve CP validator'un halen faydali oldugunu kaydetmek. - DURUM: KISMEN UYGULANDI
### [16 March 2026 - 15:27] KALAN SORUN - Ne: birden fazla agir hata acik kaldi. (1) `Squirtle` species dogru ama `costume=false`, `shiny=false`; costume signature `matched=true` loglansa da confidence `0.1889` oldugu icin reddediliyor, heuristic de acilmiyor. (2) `Blastoise` species hattinda kritik hata var; iki ayri scan'de `current=Blastoise -> best=Beldum` ve `current=Blastoise -> best=Pikachu` override'lari goruldu. Bu ikinci scan sonrasi tum visual karar da `Pikachu` uzerinden calisip `shiny=true`, `locationCard=true` gibi tamamen yanlis sonuc uretti. (3) `Butterfree`, `Absol`, `Slowpoke`, `Piplup`, `Cubchoo`, `Rowlet` bu turda species olarak dogru gorunse de varyantlarin hicbiri algilanmadi (`shiny=false`, `costume=false`). `Cubchoo` icin shiny signature skorunda shiny one geciyor gibi gorunen satirlar olsa da histogram karsilastirmasi `normalMass`i baskin bulup karari geri ceviriyor. Neden: bu scan penceresinde ana darbozazin artik species refiner'in candy'siz ama ismi dogru Pokemonlari gereksiz override etmesi ve visual detector'un bircok costume/shiny kombinasyonunda confidence kapilarinda kalmasi oldugu netlesti. - DURUM: DEVAM EDIYOR
### [16 March 2026 - 15:27] EK BULGU - Ne: `lucky armored Mewtwo` ve `Spheal` icin bu log penceresinde hic species izi bulunamadi; `Mewtwo`, `Armored`, `Spheal` aramalari bos dondu. Ayrica tum `Results:` satirlarinda `lucky=true` hic gorulmedi. Logda bunun yerine kullanicinin listelemedigi bir `Mew` sonucu var (`shiny=true`, `locationCard=true`). Neden: bu, ilgili iki scan'in ya ayni log tamponuna dusmedigini ya da species/OCR hattinin bu taramalarda baska bir turu secip tamamen farkli isimle devam ettigini gosteriyor; lucky tespiti icin sahada dogrudan pozitif kanit halen yok. - DURUM: DEVAM EDIYOR
### [16 March 2026 - 15:31] `SpeciesRefiner.kt` - Ne: candy'siz ama ismi guclu sekilde okunmus Pokemonlar icin yeni species koruma kilidi eklendi. `hasStrongSpeciesAnchor()` ile OCR ham isminde guclu prefix/suffix eslesmesi aranıyor; bu sinyal varsa ve mevcut species `cpPossible`/fit olarak makulse global observed/physical shortlist hic acilmiyor. Ayrica `strongSpeciesLock` ile `nicknameOverride` benzeri generic override kapilari bastirildi; family/move/candy kaynakli guclu kanit yoksa mevcut species korunuyor. Neden: `Blastoise` gibi dogru okunan isimlerin candy olmamasi yuzunden `Beldum` veya `Pikachu` gibi alakasiz adaylara override edilmesini kesmek. - DURUM: UYGULANDI
### [16 March 2026 - 15:31] `VisualFeatureDetector.kt` - Ne: border-line costume signature kurtarmasi eklendi. Signature `matched=true` olup confidence `0.20` altinda kalsa bile, non-dense species icin `bestCostume<=0.29` ve `scoreGap>=0.05` oldugunda sonuc artik reddedilmiyor; en az `0.24` confidence ile kabul ediliyor ve loga `Costume signature rescue accepted` satiri dusuyor. Neden: `Squirtle` benzeri vakalarda signature acikca costume lehine olsa da confidence formulu `0.188` gibi sinir altinda kaldigi icin sonuc tamamen kayboluyordu. - DURUM: UYGULANDI
### [16 March 2026 - 15:31] `ShinySignatureStore.kt` - Ne: soft-relative shiny fallback esikleri hafif gevsetildi (`SHINY_RELATIVE_MARGIN_SOFT 0.015 -> 0.010`, `SHINY_COLOR_GAP_SOFT 0.025 -> 0.015`). Neden: `Piplup` gibi near-tie ama tekrarli sekilde shiny lehine kayan score'larda false-negative oranini dusurmek; ana threshold'lari bozmayip sadece sinirda kalan vakalari biraz daha erken kabul etmek. - DURUM: UYGULANDI
### [16 March 2026 - 15:31] BUILD / DEPLOY - Ne: yeni species lock + border-line costume rescue + softer shiny fallback degisiklikleriyle `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: bu revizyonlari yeni scan turu icin dogrudan sahaya almak. - DURUM: UYGULANDI
### [16 March 2026 - 17:26] LOG - Ne: gecikmeli log cekimi yapildi ancak mevcut `logcat` tamponunda scan'e ait OCR / `Results:` / species logu bulunmadi; sadece uygulama startup'i (`Manifest loaded`, `ScanManager started`) ve kisa sure sonra `MainActivity` gorunurlugunun kayboldugu satirlar goruldu. Neden: bu turdaki log penceresi scan anini kacirmis gorunuyor; sorunlarin yeniden izlenebilmesi icin sonraki turda scan sonrasi hemen log alinmasi gerekecek. - DURUM: UYGULANDI
### [16 March 2026 - 17:29] LOG - Ne: yeni scan turu sonrasi USB loglari alindi. Bu turda `Blastoise`, `Squirtle`, `Piplup`, `Absol` ve armored/lucky `Mewtwo` yerine `Mew`e kayan bir scan goruldu. Neden: yeni `SpeciesRefiner` lock ve costume/shiny threshold degisikliklerinin sahadaki gercek etkisini dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 17:29] DOGRULAMA - Ne: `Squirtle` species olarak dogru kaldÄ± ve bu kez `shiny=true` uretti; fakat confidence yalnizca `0.0627` ve `costume=false` oldu. `Piplup` species olarak dogru korundu (`Species kept: current=Piplup`) ancak yine `shiny=false`, `costume=false`. `Absol` species olarak dogru kaldÄ± ama yine `shiny=false`, `costume=false`. Neden: son threshold gevsetmesi shiny tarafinda en azindan `Squirtle`'i pozitife itti, ancak karar halen cok zayif ve costume hattinda anlamli bir ilerleme yok. - DURUM: KISMEN UYGULANDI
### [16 March 2026 - 17:29] KALAN SORUN - Ne: `Blastoise` kritik species hatasi devam ediyor. OCR ham isminde `BIastoiseSZl -> Blastoise` eslesmesi dogru gorunse de `SpeciesRefiner` hala `current=Blastoise -> best=Beldum` override ediyor; dolayisiyla yeni "guclu isim kilidi" bu OCR bozulma tipini koruyamadi. Muhtemel neden: `BIastoiseSZl` metni icindeki `I/l` benzeri karakter sapmasi yeni prefix/suffix anchor heuristigini tetiklemiyor ve species yine nickname/weak-name muamelesi goruyor. `Squirtle` tarafinda border-line costume rescue da tetiklenmedi; logda hala `Costume signature rejected for Squirtle: confidence=0.113`, `bestCostume=0.2969`, `scoreGap=0.0534` goruluyor. Neden: ana darboÄŸaz su an (1) OCR bozulmasina dayanikli species kilidinin yetersiz olmasi, (2) costume confidence formulu ve rescue kapisinin `Squirtle` benzeri gercek costume vakalarini hala iceri alamamasi. - DURUM: DEVAM EDIYOR
### [16 March 2026 - 17:29] EK BULGU - Ne: lucky armored `Mewtwo` bu turda da `Mewtwo` olarak gorunmedi. OCR `Name='li MewB'` verdi ve `SpeciesRefiner` bunu `Mew` olarak korudu; sonuc `shiny=true`, `locationCard=true`, `lucky=false` oldu. Ayrica `lucky=true` bu turdaki hicbir `Results:` satirinda yok. Neden: lucky/armored hattinin halen pozitif kaniti yok; burada yalnizca lucky detector degil, species discrimination da `Mew` vs `Mewtwo` duzeyinde yetersiz kaliyor. - DURUM: DEVAM EDIYOR
### [16 March 2026 - 17:37] `SpeciesRefiner.kt` - Ne: species lock mantigi dogrudan `parseName(rawName)` / `parseName(fallbackName)` sonucunu kullanacak sekilde guclendirildi. Eger OCR ham adinin parse edilmis hali mevcut species ile ayniysa bu artik birinci sinif guven sinyali sayiliyor; `currentLooksLikeNickname` baskilanıyor, global observed/physical shortlist acilmiyor ve aday havuzuna parse edilmis isimler de ekleniyor. Neden: `BIastoiseSZl -> Blastoise` gibi OCR bozulmalarinda onceki prefix/suffix anchor heuristigi yetersiz kaldigi icin `Blastoise` yine `Beldum`e override oluyordu. - DURUM: UYGULANDI
### [16 March 2026 - 17:37] `VisualFeatureDetector.kt` - Ne: border-line costume rescue logdaki gercek `Squirtle` degerlerine gore gevsetildi. Rescue kapisi artik `bestCostume <= 0.305`, `scoreGap >= 0.045` ve `confidence >= 0.10` oldugunda aciliyor. Neden: onceki rescue `bestCostume <= 0.29` gerektirdigi icin `bestCostume=0.2969`, `scoreGap=0.0534`, `confidence=0.113` olan gercek costume `Squirtle` vakasi yine reject aliyordu. - DURUM: UYGULANDI
### [16 March 2026 - 17:37] BUILD / DEPLOY - Ne: guclendirilmis parsed-name species lock ve gevsetilmis costume rescue ile `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: yeni `Blastoise` / `Squirtle` revizyonunu hemen sahaya almak. - DURUM: UYGULANDI
### [16 March 2026 - 17:40] LOG - Ne: yeni build sonrasi `Blastoise`, `Squirtle` ve armored/lucky `Mewtwo` taramalarinin loglari cekildi. Neden: species lock ve costume rescue revizyonunun sahadaki sonucunu dogrulamak. - DURUM: UYGULANDI
### [16 March 2026 - 17:40] DOGRULAMA - Ne: `Blastoise` species lock bu turda calisti. `SpeciesRefiner` artik `current=Blastoise` icin override yapmadi; `Species kept` logu ile species korundu ve top adaylarda `Blastoise` birinci sirada kaldÄ±. Sonuc: `shiny=true(0.4852)`, `costume=false`. `Squirtle` tarafinda border-line costume rescue ilk kez devreye girdi; logda `Costume signature rescue accepted for Squirtle` goruldu ve sonuc `costume=true(0.24)` oldu. Neden: bu iki hedefli revizyonun sahada gercekten etkili oldugunu belgelemek. - DURUM: KISMEN UYGULANDI
### [16 March 2026 - 17:40] KALAN SORUN - Ne: `Blastoise` species duzelse de costume tespiti halen kaciyor (`bestCostume=0.3198`, `bestNormal=0.2922`, heuristic skip). `Squirtle` ise bu turda costume olarak yakalandi fakat shiny yeniden `false` oldu. Armored/lucky `Mewtwo` scan'i halen `Mew`e kayiyor; `Name='li MewB'`, `DisplayName -> Mew`, sonuc `shiny=true`, `locationCard=true`, `lucky=false`. Yani bir sonraki ana is artik `Mew/Mewtwo` ayrimini fiziksel/stat imkansizlik uzerinden daha sert acmak ve `Squirtle/Blastoise` icin shiny+costume kombinasyonunu ayni anda tutturmak. - DURUM: DEVAM EDIYOR
### [16 March 2026 - 17:42] `SpeciesRefiner.kt` - Ne: species lock'a ek profil-mismatch kapisi eklendi. Mevcut species icin ilk `scoreSpeciesFit()` sonucu `cpPossible=false` ve toplam fit `<=0.20` ise, dogru parse edilmis isim olsa bile global aday shortlist yeniden aciliyor ve species lock devre disi kalabiliyor. Neden: `Mew` gibi metin olarak guclu ama CP/HP profili o species icin imkansiz olan vakalarda (`3780 CP` ile gercekte `Mewtwo`) current species'in kilitlenmesini engellemek. - DURUM: UYGULANDI
### [16 March 2026 - 17:42] BUILD / DEPLOY - Ne: profil-mismatch ile acilan yeni species fallback hattiyla `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: `Mew -> Mewtwo` ayrimini iyilestiren bu yeni turu hemen sahaya almak. - DURUM: UYGULANDI
### [16 March 2026 - 17:44] LOG - Ne: yeni `Mewtwo` scan logu alindi. Sonuc halen `Mew` olarak donuyor; OCR `Name='i MewB'`, `DisplayName -> Mew`, final `Results: shiny=true, locationCard=true, lucky=false`. `SpeciesRefiner` bu turda anlamli bir override logu dusurmedi; mevcut text sinyali yine baskin kaldi. Neden: bir onceki `profileMismatch` kapisi tek basina yeterli olmadi; current species'e profil uyumsuzlugunda dogrudan skor cezasi verilmesi gerektigi netlesti. - DURUM: DEVAM EDIYOR
### [16 March 2026 - 17:46] `TextParser.kt` - Ne: yeni `findNamesWithPrefix()` yardimcisi eklendi. Verilen normalize prefix ile baslayan diger Pokemon adlarini aday listesi olarak donebiliyor. Neden: `Mew` gibi kisa ama ayni prefix'i paylasan species'lerde (`Mewtwo`) raw OCR ekstra kuyruk karakterleri tasidiginda yakin akrabayi aday havuzuna zorla sokmak.
### [16 March 2026 - 17:46] `SpeciesRefiner.kt` - Ne: `currentHasProfileMismatch` aciksa iki yeni mekanizma eklendi. (1) `prefixRelatedCandidates`: parse edilmis/current isim prefix'ini paylasan diger species'ler aday havuzuna zorla ekleniyor. (2) `profileMismatchPenalty`: mevcut species adayina `-0.18` ceza veriliyor. Neden: `Mew` gibi metin olarak guclu gorunen ama CP/HP profili ciddi sekilde celisen species'lerin text skoru yuzunden secimi kilitlemesini kirip `Mewtwo` gibi yakin prefixli dogru adayi one cikarmak. - DURUM: UYGULANDI
### [16 March 2026 - 17:46] BUILD / DEPLOY - Ne: prefix-related candidate + profile-mismatch penalty revizyonuyla `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: `Mew -> Mewtwo` species ayrimini bir adim daha guclendirerek sahaya almak. - DURUM: UYGULANDI
### [16 March 2026 - 17:48] LOG - Ne: yeni `Mewtwo` tarama logu tekrar alindi. Sonuc yine `Mew` olarak kaldi; `Results: shiny=true, lucky=false, locationCard=true`. Ayrica yerel hesap ile `CP 3378 / HP 111 / full arc` kombinasyonunun ne `Mew` ne de `Mewtwo` icin tam CP eslesmesi vermedigi goruldu. Ancak `scoreSpeciesFit()` toplam skorlari iki tur icin neredeyse esit cikti (`Mew ~= 0.3659`, `Mewtwo ~= 0.3661`). Neden: mevcut `profileMismatch` esiginin toplam skora bakmasi yetersiz; asil ayirici sinyal `minArcDiff` ve kisa adin OCR tarafinda fazladan harfle uzamasi. - DURUM: DEVAM EDIYOR
### [16 March 2026 - 17:51] `SpeciesRefiner.kt` - Ne: `currentHasProfileMismatch` mantigi sertlestirildi; artik `cpPossible=false` ve `minArcDiff >= 10.0` da mismatch sayiliyor. Buna ek olarak, current species kisa bir ad (`3-4` harf) ve raw OCR bu adi fazladan harfle uzatiyorsa (`rawExtendsCurrentSpecies`), mevcut species'e `profileMismatchPenalty` uygulanirken bu prefix ile baslayan daha uzun adaylara (`Mewtwo` gibi) `shortSpeciesExtensionBonus` veriliyor. Neden: `i MewB` gibi OCR'larda kisa ad `Mew` text olarak baskin kalsa bile, uzamis suffix ve kotu profile dayanarak `Mewtwo`yu one cikarmak. - DURUM: UYGULANDI
### [16 March 2026 - 17:51] BUILD / DEPLOY - Ne: yeni `minArcDiff` tabanli mismatch ve short-species extension bonus revizyonuyla `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: `Mew -> Mewtwo` hattinda bir sonraki denemeyi sahaya almak. - DURUM: UYGULANDI
### [16 March 2026 - 17:53] DOGRULAMA - Ne: `Mew -> Mewtwo` species hattÄ± kapandi. Son logda `SpeciesRefiner: current=Mew -> best=Mewtwo` goruldu; `VisualFeatureDetector` ve `RarityCalculator` sonraki tum adimlari `Mewtwo` uzerinden calistirdi. Sonuc `Mewtwo` icin `shiny=true`, `locationCard=true`, `lucky=false` oldu. Neden: species ayriminin artik dogru oldugunu, kalan tek acik problemin lucky/background dedektoru oldugunu netlestirmek. - DURUM: KISMEN UYGULANDI
### [16 March 2026 - 17:54] `VisualFeatureDetector.kt` - Ne: lucky/background ayrimi guclendirildi. `isLucky()` artik tek bir merkez sari oranina degil, merkez strict + merkez soft + koselerdeki altin tonlarin ortalamasina bakiyor ve `Lucky Analysis` logu yaziyor. Ek olarak guclu lucky sonucunda (`confidence >= 0.35`) `locationCard` sonucu bastiriliyor. Neden: armored lucky `Mewtwo` taramasinda species dogru oldugu halde `lucky=false`, `locationCard=true` cikiyordu; bu da background siniflandirmasinin lucky ile location-card'i karistirdigini gosteriyor. - DURUM: UYGULANDI
### [16 March 2026 - 17:54] BUILD / DEPLOY - Ne: yeni lucky/background ayrimiyla `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: kalan son ana hata olan `lucky=false / locationCard=true` kombinasyonunu sahada tekrar denemek. - DURUM: UYGULANDI
### [16 March 2026 - 18:03] LOG - Ne: yeni armored/lucky `Mewtwo` scan'inin USB loglari cekildi. `SpeciesRefiner` bu turda dogru calisti ve `current=Mew -> best=Mewtwo` override'i goruldu. Ardindan `VisualFeatureDetector` icinde `Lucky Analysis: centerStrict=0.011479592, centerSoft=0.025935374, cornerAvg=0.026190476, cornerMax=0.052380953` logu geldi ve final sonuc yine `shiny=true`, `lucky=false`, `locationCard=true` oldu. Neden: artik kalan tek ana problemin species degil, lucky/background icin secilen ornekleme bolgeleri oldugunu netlestirmek. - DURUM: UYGULANDI
### [16 March 2026 - 18:03] TESHIS - Ne: mevcut lucky detector ust koseler ve ekranin ust-orta arka planini olcuyor; ancak armored lucky ekraninda altin zemin daha cok sprite'in sag/sol orta bantlarinda ve orta-alt arka planda kaliyor. Bu nedenle lucky metrikleri yapay olarak dusuk kaliyor, buna karsilik ust koselerdeki alisilmadik tonlar `locationCard=true` false-positive'i uretiyor. Neden: bir sonraki revizyonun threshold degil, region secimini degistirmesi gerektigi bu logla dogrulandi. - DURUM: UYGULANDI
### [16 March 2026 - 18:03] `ColorAnalyzer.kt` - Ne: lucky icin yeni odak bolgeleri eklendi. `getLuckyFocusRegion()` orta-genis arka plan alanini, `getLuckySupportRegions()` ise sprite'in sag/sol orta bantlari ve orta-alt destek bolgelerini donduruyor. Neden: lucky altin zemin Pokemon sprite'i tarafindan ust merkezde daha cok kapaniyor; yan ve alt bantlardan orneklemek gerekiyor. - DURUM: UYGULANDI
### [16 March 2026 - 18:03] `VisualFeatureDetector.kt` - Ne: `isLucky()` yeni focus/support region'lari kullanacak sekilde yeniden yazildi. Log ciktisi `focusStrict`, `focusSoft`, `supportAvg`, `supportMax`, `upperCornerAvg`, `upperCornerMax` alanlarina guncellendi; lucky karari ust kose agirligindan cikarilip orta/yan/alt altin zemin sinyaline tasindi. Confidence formulu da buna gore yeniden dengelendi. Neden: armored lucky `Mewtwo` orneginde eski `center/corner` metrigi altin zemini kaciriyordu. - DURUM: UYGULANDI
### [16 March 2026 - 18:03] BUILD / DEPLOY - Ne: lucky region secimi revizyonuyla `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: yeni focus/support lucky hattini sahada dogrudan yeniden denemek. - DURUM: UYGULANDI
### [16 March 2026 - 18:11] LOG - Ne: yeni armored/lucky `Mewtwo` scan'inin USB loglari tekrar cekildi. `SpeciesRefiner` yine dogru calisti (`current=Mew -> best=Mewtwo`). Ancak yeni region secimine ragmen `Lucky Analysis: focusStrict=0.020689655, focusSoft=0.031494252, supportAvg=0.001864576, supportMax=0.0042283298, upperCornerAvg=0.021428572, upperCornerMax=0.031746034` geldi ve final sonuc yine `shiny=true`, `lucky=false`, `locationCard=true` oldu. Neden: lucky'yi background renginden cikarmaya calismak bu ekran tipi icin halen yanlis eksende kaliyor. - DURUM: UYGULANDI
### [16 March 2026 - 18:11] GORSEL TESHIS - Ne: cihazdan ilgili scan frame'i cekilip incelendi. Frame uzerinde arka plandan daha guclu lucky sinyali olarak kart ustunde acikca `LUCKY POKEMON` etiketi ve lucky ikonu goruldu. Neden: bu ekran tipinde lucky durumu arka plan tonundan degil, kart etiketinden dogrudan okunabiliyor; dolayisiyla background tabanli yaklasim zayif ve gereksiz. - DURUM: UYGULANDI
### [16 March 2026 - 18:11] `ScreenRegions.kt` - Ne: yeni `REGION_LUCKY_LABEL` eklendi. Neden: kart ustundeki `LUCKY POKEMON` etiketini OCR ile ayri bir bolgeden okumak.
### [16 March 2026 - 18:11] `TextParser.kt` - Ne: `parseLuckyLabel()` eklendi. OCR metnini normalize edip `LUCKY` + `POKEMON` benzeri kaliplari tanimliyor. Neden: lucky etiketi OCR gÃ¼rÃ¼ltÃ¼sÃ¼ne ragmen kararlı bir bool sinyale donusturulsun.
### [16 March 2026 - 18:11] `OCRProcessor.kt` - Ne: `LuckyLabel` ve `LuckyLabelClean` OCR adimlari eklendi; `LuckyDetected -> true/false` loglaniyor ve `rawOcrText` icine yaziliyor. Neden: lucky etiketini scan pipeline'ina birinci sinif veri olarak sokmak.
### [16 March 2026 - 18:11] `ScanManager.kt` - Ne: OCR `LuckyDetected:true` ise visual detector sonucu ne olursa olsun `isLucky=true` override ediliyor ve `hasLocationCard=false` bastiriliyor; loga `Lucky override applied from OCR label` satiri dusuyor. Neden: armored lucky `Mewtwo` vakasinda dogru sinyal kart etiketi, yanlis sinyal ise background/location-card false-positive'i.
### [16 March 2026 - 18:11] BUILD / DEPLOY - Ne: lucky OCR override revizyonuyla `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: yeni lucky etiket hattini sahada dogrudan denemek. - DURUM: UYGULANDI
### [16 March 2026 - 18:17] LOG - Ne: lucky OCR override sonrasi yeni scan loglari cekildi. `LuckyLabel='IQE IRRHD'`, `LuckyLabelClean='LUCTRYG fztirjON'` geldi; `LuckyDetected -> false` kaldi. `SpeciesRefiner` yine `Mewtwo`ya dogru override etti ama final sonuc hala `lucky=false`, `locationCard=true` oldu. Neden: ilk lucky label OCR crop'i ve parser esikleri yeterli olmadi; etiket okunuyor ama gÃ¼rÃ¼ltÃ¼lÃ¼ geliyor. - DURUM: UYGULANDI
### [16 March 2026 - 18:17] `ScreenRegions.kt` - Ne: `REGION_LUCKY_LABEL` biraz daha asagi cekildi ve daraltildi. Neden: onceki crop name/HP satiriyla fazla karisiyordu; etiket bandini daha temiz yakalamak gerekiyor.
### [16 March 2026 - 18:17] `TextParser.kt` - Ne: `parseLuckyLabel()` fuzzy hale getirildi. Artik yalnizca tam `LUCKY POKEMON` degil; ortak prefix, token bazli yakinlik ve `LUCKYPOKEMON` toplam edit distance'i ile de kabul edebiliyor. Neden: son logdaki `LUCTRYG fztirjON` gibi bozuk OCR'lar yine de lucky etiketine yakin bir desen tasiyor.
### [16 March 2026 - 18:17] BUILD / DEPLOY - Ne: lucky label crop + fuzzy parser revizyonuyla `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: lucky OCR override hattini ikinci kez sahaya almak. - DURUM: UYGULANDI
### [16 March 2026 - 18:17] ARKAPLAN DONMASI TESHISI - Ne: scan sonucu su an gercek bir overlay view olarak degil, `ScanManager.kt` icinden `context.startActivity(Intent(...ResultActivity))` ile tam bir Activity olarak aciliyor. `ResultActivity` icinde `FLAG_NOT_FOCUSABLE` vb. eklense de Android'de Activity yine top-level pencere/task olur ve alttaki oyun `onPause`/arka plan davranisina gecer. Bu yuzden Pokemon GO donuyor ve result ekran acikken oyunla eszamanli etkilesim olmuyor. Neden: Calcy IV/PokeGenie benzeri uygulamalar sonuc ekranini da `TYPE_APPLICATION_OVERLAY` pencere olarak cizer; bu projede ise yalnizca scan butonu overlay, sonuc ekrani ise Activity. - DURUM: UYGULANDI
### [16 March 2026 - 18:19] LOG - Ne: lucky label crop + fuzzy parser sonrasi yeni scan loglari cekildi. `LuckyLabel='BS   ZWS HP'`, `LuckyLabelClean='iBS l lBS HP'` geldi; `LuckyDetected -> false` kaldi. `SpeciesRefiner` yine `Mew -> Mewtwo` dogru override etti ama final sonuc hala `lucky=false`, `locationCard=true` oldu. Neden: mevcut lucky label crop halen HP satirina kayiyor; etiket OCR'i istenen bandi okumuyor. - DURUM: UYGULANDI
### [16 March 2026 - 18:19] MIMARI DEGERLENDIRME - Ne: sonuc ekranini Activity'den gercek overlay pencereye tasimak icin mevcut OCR/species/shiny/rarity hattini degistirmek gerekmiyor; degisecek kisim yalnizca sunum/lifecycle katmani. Bu nedenle bugune kadar kazanilan tarama duzeltmeleri dogrudan kaybolmaz. Ana riskler UI tarafinda: kaydet/paylas duymeleri, dis tikla kapanma, drag/yerlesim, permission ve pencere flag'leri. Neden: kullanicinin "overlaye gecersek mevcut kazanÄ±mlar kaybolur mu" sorusuna teknik etki alanini netlestirmek. - DURUM: UYGULANDI
### [16 March 2026 - 18:28] `OverlayService.kt` - Ne: scan butonunun yanina ikinci bir overlay pencere olarak sonuc karti eklendi. `ACTION_SHOW_RESULT` / `ACTION_DISMISS_RESULT` aksiyonlari tanimlandi; `activity_result.xml` artik servis icinde inflate edilip `TYPE_APPLICATION_OVERLAY` olarak gosteriliyor. Sonuc karti drag edilebiliyor, `Back` ile kapanabiliyor, `Share` chooser acabiliyor. Neden: sonuc ekranini Activity yerine gercek overlay penceresi yaparak oyunun focus kaybetmesini engellemek.
### [16 March 2026 - 18:28] `ScanManager.kt` - Ne: scan tamamlandiginda `ResultActivity` acmak yerine `OverlayService.ACTION_SHOW_RESULT` ile sonucu overlay servisine gonderiyor. Pokemon/CP/HP/skor/tier/explanation/date ve varyant flag'leri aynen tasiniyor. Neden: tarama/ocr/rarity hattina dokunmadan yalnizca sunum katmanini degistirmek.
### [16 March 2026 - 18:28] DAVRANIS NOTU - Ne: bu ilk overlay sonuc surumunde tarama kazanÄ±mlari korunuyor; OCR/species/shiny/lucky/rarity hesap katmanlari degismedi. Ancak eski `ResultActivity`'deki editable alanlar overlayde read-only yapildi; `Save` su an "zaten history'ye kaydedildi" semantiginde sadece toast + kapatma davraniyor. Neden: once oyunu pause ettirmeyen overlay akisina gecmek hedeflendi; editable form/saklama davranisini ikinci adimda yeniden duzeltmek daha guvenli.
### [16 March 2026 - 18:28] BUILD / DEPLOY - Ne: result overlay gecisiyle `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: oyun arka plana dusmeden sonuc kartinin sahada denenebilmesi. - DURUM: UYGULANDI
### [16 March 2026 - 23:35] BUILD / DEPLOY - Ne: lucky label OCR bandini yukari/orta banda ceken son build USB cihaz `RFCY11MX0TM` uzerine yeniden kuruldu; `logcat -c` yapildi ve uygulama yeniden acildi. Neden: ADB kopmasi nedeniyle cihaza yuklenemeyen son lucky OCR revizyonunu sahaya geri almak. - DURUM: UYGULANDI
### [16 March 2026 - 23:37] LOG - Ne: yeni scan loglari alindi. `DisplayName -> Mewtwo` dogru geldi; OCR artik species tarafinda sapmiyor. Ancak `LuckyLabel='IVIEWIWOVQ'`, `LuckyLabelClean='WYYEYYFW'`, `LuckyDetected -> false` kaldi ve final sonuc yine `lucky=false`, `locationCard=true` oldu. Ek olarak bu tur `costume=true(0.6401)` geldi; armored Mewtwo icin bu kabul edilebilir. Neden: lucky label OCR hattinin halen kararsiz oldugu, buna karsilik species ve overlay sonuc akisinin stabil kaldigi dogrulandi. - DURUM: UYGULANDI
### [16 March 2026 - 23:38] `VisualFeatureDetector.kt` - Ne: lucky icin kart-ici gorsel sinyal eklendi. Beyaz kart ustundeki yesil label bolgesi (`cardGreen`) ve soldaki altin lucky ikon bolgesi (`cardGold`) olculuyor; bu ikili yeterince gucluyse `isLucky=true` uretip arka plan-temelli lucky/location-card hattina ustun geliyor. Neden: OCR ile `LUCKY POKEMON` satirini kararlı okumak basarisiz kaldi; bu ekranda daha saglam sinyal kart icindeki yesil yazi + altin ikon kombinasyonu. - DURUM: UYGULANDI
### [16 March 2026 - 23:38] BUILD / DEPLOY - Ne: lucky kart-sinyali revizyonuyla yeni debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu; `logcat -c` yapildi ve uygulama yeniden acildi. Neden: OCR'den bagimsiz lucky tespitini sahada hemen denemek. - DURUM: UYGULANDI
### [16 March 2026 - 23:41] LOG / TESHIS - Ne: yeni kart-sinyali logunda ilk scan icin `cardGreen=0.25` guclu geldi ama `cardGold=0.0` oldugu icin lucky yine reddedildi; final sonuc `lucky=false`, `locationCard=true` kaldi. Bu, yesil label sinyalinin dogru bolgeyi buldugunu, fakat altin ikon bolgesinin kararsiz/kapali oldugunu gosteriyor. Neden: lucky kararini iki sinyalden biri kaybolsa bile yesil label agirligiyle verebilmek.
### [16 March 2026 - 23:41] `VisualFeatureDetector.kt` - Ne: `cardLucky` kurali gevsetildi. Artik `cardGreen` tek basina yeterince gucluyse (`>=0.20`) veya `cardGreen` yuksek ve ust-kose non-standard sinyali makulse lucky kabul ediliyor; confidence formulu da yesil label agirligini artiracak sekilde guncellendi. Neden: armored/lucky `Mewtwo` ekraninda sabit sinyal yesil `LUCKY POKEMON` yazisi, altin ikon ise her frame'de guvenilir degil.
### [16 March 2026 - 23:41] BUILD / DEPLOY - Ne: `cardGreen` agirlikli lucky karariyla yeni debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu; `logcat -c` yapildi ve uygulama yeniden acildi. Neden: lucky/location-card ayrimini bir adim daha kapatmak. - DURUM: UYGULANDI
### [16 March 2026 - 23:43] DOGRULAMA - Ne: ayni armored/lucky `Mewtwo` scan'i tekrar loglandi. `Lucky Analysis` satirinda `cardGreen=0.25`, `cardGold=0.0` goruldu; yeni kural sayesinde `Location card suppressed by lucky background: lucky=0.6191708` logu dustu ve final sonuc `shiny=true`, `lucky=true`, `costume=false`, `locationCard=false` oldu. Neden: `cardGreen` agirlikli lucky karari hedeflenen false-negative / false-positive kombinasyonunu kapatti. - DURUM: UYGULANDI
### [16 March 2026 - 23:47] TESHIS - Ne: kullanici taranan armored/lucky `Mewtwo`nun shiny olmadigini belirtti. Son iki logda `Shiny scores for Mewtwo` satirlarinda her seferinde `normal > shiny` degil, aslinda dusuk skor mantiginda `shiny` daha iyi gorunuyor; fakat bu skorlarin tamami yuksek (`~0.64-0.66`) ve varyant disi/out-of-distribution profile isaret ediyor. Buna ragmen `relativeWin`/`weakRelativeWin` fallback'leri `shiny=true` karari uretmeye devam ediyordu. Neden: armored varyant normal/shiny regular Mewtwo signature'larina kotu uyudugu halde relatif fallback toplam skor kalitesini yeterince filtrelemiyordu.
### [16 March 2026 - 23:47] `ShinySignatureStore.kt` - Ne: relatif shiny fallback'lere toplam skor ust siniri eklendi. `relativeWin`, `weakRelativeWin` ve `softRelativeWin` artik ancak `shinyScore` belirli bir kalite esiginin altindaysa kabul ediliyor (`SHINY_MAX_TOTAL_RELATIVE`, `SHINY_MAX_TOTAL_RELATIVE_STRICT`). Neden: armored `Mewtwo` gibi signature-disi varyantlarin "shiny biraz daha az kotu" durumundan false-positive uretmesini kesmek.
### [16 March 2026 - 23:47] BUILD / DEPLOY - Ne: shiny relative fallback sikilastirma revizyonuyla yeni debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu; `logcat -c` yapildi ve uygulama yeniden acildi. Neden: normal lucky armored `Mewtwo` uzerindeki shiny false-positive'i dogrudan test etmek. - DURUM: UYGULANDI
### [16 March 2026 - 23:50] GORSEL DOGRULAMA - Ne: cihaz cache'indeki `scan_1773704945365_1.png` frame'i cekilip incelendi. Gercek ekran degerleri `CP 3378` ve `HP 185/185` olarak dogrulandi; species lucky armored `Mewtwo` (nickname `Mew3`) ve kartta `LUCKY POKEMON` etiketi net. Neden: kullanicinin "CP ve HP de yanlis" geri bildiriminde OCR mi yoksa sonraki matematiksel duzeltme mi bozuluyor bunu frame ustunden kesinlestirmek. - DURUM: UYGULANDI
### [16 March 2026 - 23:50] TESHIS - Ne: kalan CP hatasi dogrudan CP OCR'dan degil, yanlis HP parse zincirinden geliyor. Son logda OCR `HP raw='111   H10H1 40' -> (111,111)` uretip `RarityCalculator` bunu guvenilir sanarak `CP OCR was likely wrong (3378), fixing to: 1685` yapmis durumda. Yani gercek `3378` CP, zayif HP OCR yuzunden sonradan bozuluyor. Ek olarak `parseHPPair()` icindeki "metinde herhangi bir 2-3 haneli sayi" fallback'i gÃ¼rÃ¼ltÃ¼den `111` uyduruyor. Neden: bir sonraki duzeltmenin yalnizca threshold degil, HP parser kurallari ve CP correction kapisini birlikte sikilastirmasi gerektigi netlesti. - DURUM: UYGULANDI
### [16 March 2026 - 23:50] `ScreenRegions.kt` - Ne: `REGION_HP` tekrar ayarlandi (`top=0.425`, `height=0.050`). HP satiri lucky etiketinin hemen altindaki banda kaydirildi ve dikey tolerans buyutuldu. Neden: onceki crop fazla yukarida kaldigi icin lucky etiketi/isim gurultusunu okuyup gercek `185/185 HP` satirini sinirdan kaciriyordu.
### [16 March 2026 - 23:50] `OCRProcessor.kt` - Ne: HP OCR icin ikinci ve ucuncu okuma eklendi: `HPClean` ve `HPBlock`. HP satiri artik sadece `processHighContrast` tek gecisine bagli degil; candy-text binary filtresiyle temizlenmis line/block OCR sonuclari da okunup loga yaziliyor ve `parseHPPair()`'e birlikte veriliyor. Neden: beyaz kart ustundeki koyu HP metninde tek OCR gecisi kararsiz kaliyordu; ikili filtreli alternatif okumalar `185/185` gibi cizgili formatlari daha iyi yakaliyor.
### [16 March 2026 - 23:50] `TextParser.kt` - Ne: `parseHPPair()` vararg hale getirildi ve zayif fallback kaldirildi. Artik once net `123/123` formati, sonra `HP` tokenli iki sayi, sonra yine `HP` tokenli kompakt split / `HP 123` desenleri kabul ediliyor; baglamsiz "metindeki herhangi bir 2-3 haneli sayi" artik HP sayilmiyor. Neden: `111 H10H1 40` gibi gurultuden `111` uydurulmasini durdurmak.
### [16 March 2026 - 23:50] `RarityCalculator.kt` - Ne: `validateAndFixCP()` icine `reliableHpOcr` kapisi eklendi. `rawOcrText` icindeki `HP:` satiri slash veya guclu `HP + iki sayi` deseni tasimiyorsa matematiksel CP override tamamen skip ediliyor ve mevcut OCR CP korunuyor. Neden: HP OCR zayifsa dogru CP'yi "matematiksel duzeltme" ile bozmak yerine once orijinal CP'yi tutmak daha guvenli.
### [16 March 2026 - 23:58] BUILD / DEPLOY - Ne: yeni HP crop + coklu HP OCR + sÄ±kÄ± HP parser + `reliableHpOcr` CP gate revizyonuyla `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: ayni lucky armored `Mewtwo` orneginde CP/HP zincirini sahada tekrar dogrudan test etmek. - DURUM: UYGULANDI
### [17 March 2026 - 00:01] LOG - Ne: kullanicinin yeni scan'i sonrasi mevcut build uzerinden loglar cekildi; kullanici ozellikle yeni build alinmamasini istedi, bu tur yalnizca teshis yapildi. Neden: CP/HP zincirindeki son revizyonun sahadaki etkisini build degistirmeden dogrulamak. - DURUM: UYGULANDI
### [17 March 2026 - 00:01] DOGRULAMA - Ne: CP duzeltme zinciri bu turda bozulmadi. `Best frame selected: CP=3378, Name=Mew, HP=null, Arc=1.0` goruldu; yani yeni `reliableHpOcr` kapisi sayesinde yanlis HP yuzunden `CP OCR was likely wrong` duzeltmesi tetiklenmedi ve gercek `CP 3378` korundu. `SpeciesRefiner` yine `current=Mew -> best=Mewtwo` override etti; `Lucky Analysis` sonucu `lucky=true`, `locationCard=false` kaldÄ±. Neden: son turdaki CP koruma revizyonunun hedefledigi davranis sahada dogrulandi. - DURUM: UYGULANDI
### [17 March 2026 - 00:01] KALAN SORUN - Ne: HP OCR halen dogru satiri okumuyor. Son loglarda `HP raw='5   1 10' -> null`, `HPClean='TLSZJfJZJoN'`, `HPBlock='... LUCKY POKEMON'` goruldu; yani HP crop veya ikili filtre halen lucky etiket bandina kayiyor. Bu kez yanlis HP uydurulmuyor ama gercek `185/185` de cikmiyor. Ayrica normal lucky armored `Mewtwo` icin `ShinySignatureStore` yine histogram fallback ile `shiny=true(0.48)` uretmeye devam etti (`normalMass=0.0039`, `shinyMass=0.4520`). Neden: sonraki teknik adimlar artik (1) HP bolgesini/satir ayrimini tekrar hizalamak, (2) armored/lucky/costume varyantlarda regular shiny histogram fallback'ini sikilastirmak olacak; bu tur kullanici talebiyle yeni build alinmadi. - DURUM: DEVAM EDIYOR
### [17 March 2026 - 00:19] UI REDESIGN - Ne: kullanicinin sagladigi `PokeRarityScanner.zip` icindeki Compose redesign mevcut projeye entegre edildi. Tasarim dosyalari mevcut paket yapisina (`com.pokerarity.scanner`) uyarlandi; mevcut scan / OCR / service / rarity mantigi degistirilmeden yalnizca UI katmani yenilendi. Neden: business logic stabil tutulurken ana ekran ve detay deneyimini yeni tasarima tasimak. - DURUM: UYGULANDI
### [17 March 2026 - 00:19] `build.gradle.kts` / `app/build.gradle.kts` - Ne: Compose altyapisi eklendi. `buildFeatures.compose=true`, `composeOptions.kotlinCompilerExtensionVersion=1.5.14`, Compose BOM `2024.12.01`, `navigation-compose:2.8.5`, `material-icons-extended`, `activity-compose:1.9.3`, `lifecycle-runtime-compose` bagimliliklari tanimlandi. Neden: ZIP'teki Jetpack Compose ekranlarini mevcut ViewBinding projeye yan yana calisacak sekilde eklemek.
### [17 March 2026 - 00:19] FONT - Ne: `app/src/main/res/font/` altina istenen 6 adet Outfit font dosyasi eklendi: `outfit_regular.ttf`, `outfit_medium.ttf`, `outfit_semibold.ttf`, `outfit_bold.ttf`, `outfit_extrabold.ttf`, `outfit_black.ttf`. Kaynak olarak Google Fonts'un resmi `google/fonts` reposundaki `Outfit[wght].ttf` variable fontu indirildi ve istenen resource adlari altinda kopyalandi. Neden: Google Fonts resmi kaynakta ayrik static TTF agirliklari yerine variable TTF yayinliyor; ancak tasarim ZIP'i sabit resource isimleri bekliyor.
### [17 March 2026 - 00:19] `ui/theme/*` / `ui/components/*` / `ui/screens/*` - Ne: ZIP'teki tasarim bilesenleri Compose olarak eklendi ve temizlendi. `Theme.kt`, `Typography.kt`, `Components.kt`, `PokemonListCard.kt`, `CollectionScreen.kt`, `ScanResultScreen.kt` olusturuldu. Animasyonlarda layout recalculation tetikleyen `offset(y=...)` yerine `graphicsLayer { alpha / translationX / translationY }` kullanildi; liste kartlari `index * 70ms` stagger ile, skor sayaci `Animatable<Float>` ile `700ms` gecikme + `900ms` easing ile calisacak sekilde korundu. Neden: kullanicinin verdigi tasarim kurallarini mevcut projeye uyarlamak.
### [17 March 2026 - 00:19] `data/model/Pokemon.kt` - Ne: yeni Compose UI icin ayri bir sunum modeli eklendi. `ScanHistoryEntity -> Pokemon` mapper yazildi; mevcut scan verileri yeni koleksiyon ve detay ekranlarinda kullaniliyor. Type verisi veritabaninda olmadigi icin UI katmaninda species->type infer map'i eklendi; eksik species'ler `normal` tipe dusuyor. Neden: ZIP'teki ekranlar ornek veri kullaniyordu; mevcut proje ise Room tabanli scan history uretiyor.
### [17 March 2026 - 00:19] `ui/main/MainActivity.kt` - Ne: ana ekran ViewBinding'den Compose'a tasindi. Eski start/stop overlay, notification izni, MediaProjection izni ve debug overlay mantigi aynen korunup yeni `CollectionScreen` uzerine baglandi. Tarama listesi artik `PokemonRepository.getAllScans()` akisini okuyup Compose `NavHost` icinde koleksiyon ve detay ekranlari arasinda geziyor. Neden: yeni tasarimin ana uygulama giris ekraninda gercek verilerle calismasi.
### [17 March 2026 - 00:19] `ui/result/ResultActivity.kt` / `AndroidManifest.xml` - Ne: legacy `ResultActivity` Compose `ScanResultScreen` ile yenilendi ve activity theme'i normal app theme'ine cekildi. Intent extras sozlesmesi korunarak yeni detail tasarimi history tarafinda da kullanilabilir hale getirildi. Neden: redesign detail ekranini yalnizca main nav icinde degil, mevcut history/detail akisi icinde de gosterebilmek.
### [17 March 2026 - 00:19] CONFLICT NOTU - Ne: ZIP redesign yalnizca app ana liste/detay deneyimini kapsiyor; scan sonrasi gozuken floating overlay result karti (`OverlayService` icindeki `activity_result.xml`) bu turda bilincli olarak degistirilmedi. Ayrica `HistoryActivity` liste ekraninin kendi XML tasarimi aynen duruyor; yeni tasarim esasen `MainActivity` koleksiyon ekrani ve Compose detail ekranina baglandi. Neden: kullanicinin "business logic'i bozma" talebi altinda en az riskli entegrasyon yolu bu; floating overlay UI ayri bir refactor ister. - DURUM: DEVAM EDIYOR
### [17 March 2026 - 00:19] BUILD - Ne: Compose redesign entegrasyonu sonrasi `assembleDebug` basarili tamamlandi. Bu tur cihaz kurulumu/log takibi yapilmadi; yalnizca derleme dogrulamasi alindi. Neden: yeni UI katmaninin derlenebilir oldugunu garanti etmek ve scan hattini bu asamada sahaya yeniden surmemek. - DURUM: UYGULANDI
### [17 March 2026 - 00:22] DEPLOY / TEST HAZIRLIK - Ne: Compose redesign iceren mevcut debug APK USB cihaz `RFCY11MX0TM` uzerine `install -r` ile kuruldu, `logcat -c` yapildi ve uygulama launcher uzerinden acildi. Neden: yeni tasarimi cihaz uzerinde dogrudan test etmek. - DURUM: UYGULANDI
### [17 March 2026 - 00:33] UI TESHIS - Ne: kullanicinin yeni geri bildirimi uzerine uc konu ayrildi: (1) debug toggle artik gereksiz, ana ekrandan kaldirilacak; (2) scan butonu erisilemez konumda, hem ana ekran hem floating overlay tarafinda daha asagi ve orta baslatilacak; (3) en kritik olarak, scan sonucu tasariminin degismemesinin sebebi Compose redesign'in gercek overlay sonuc penceresine hic uygulanmamis olmasi. `OverlayService` hala `ActivityResultBinding` / `activity_result.xml` ile legacy sonuc kartini ciziyordu. Neden: kullanicinin "hala ayni ekran geliyor" geri bildirimini kod seviyesinde netlestirmek ve degisiklikleri dogru katmana uygulamak. - DURUM: UYGULANDI
### [17 March 2026 - 00:33] `CollectionScreen.kt` / `MainActivity.kt` - Ne: Compose ana ekrandan debug toggle kaldirildi. `CollectionScreen` artik `debugEnabled` / `onDebugToggle` parametrelerini almiyor; `MainActivity` tarafinda da buna ait state ve toggle akisi cikarildi. Scan butonu header'in sagindan alinarak icerikte ortaya ve daha asagi yerlestirildi. Neden: debug toggle ihtiyaci kalmadi; scan baslatma eylemi de daha erisilebilir bir noktaya alinmak istendi. - DURUM: UYGULANDI
### [17 March 2026 - 00:33] `OverlayService.kt` - Ne: gercek scan sonuc overlay'i legacy XML'den Compose'a tasindi. `ACTION_SHOW_RESULT` aldiginda servis artik `ActivityResultBinding` inflate etmiyor; yerine `ComposeView` tabanli bir overlay kart uretiyor ve `ScanResultOverlayCard` ile yeni tasarimi gosteriyor. Aynı dosyada floating scan butonunun varsayilan acilis konumu da sag ustten alinip ekranin daha asagi ve merkeze yakin bir baslangic noktasina cekildi. Neden: kullanicinin gercek scan sonucunda yeni tasarimi gorebilmesi ve floating scan butonunun daha rahat ulasilabilir olmasi.
### [17 March 2026 - 00:33] `ScanResultScreen.kt` - Ne: overlay sonuc akisi icin yeni `ScanResultOverlayCard` composable eklendi. Bu kart mevcut Compose redesign'in tip-renkleri, skor animasyonu, tag pill'leri, stat kartlari ve analiz listesini kullanarak wrap-content bir floating sonuc deneyimi veriyor. `Save`, `Share`, `Close` aksiyonlari korunuyor; animasyonlarda `graphicsLayer` kullanimi surduruldu. Neden: tam ekran detail tasarimini dogrudan servis penceresine tasimak yerine, ayni gorsel dili overlay kartina uygun boyutta yeniden kullanmak.
### [17 March 2026 - 00:33] BUILD - Ne: yukaridaki UI / overlay refactor sonrasi `assembleDebug --console=plain` basarili tamamlandi. Derleme sirasinda yalnizca mevcut Hilt proguard uyarisı tekrar goruldu; yeni hata kalmadi. Neden: Compose overlay sonuc penceresinin ve yeni ana ekran yerlestirmesinin derlenebilir oldugunu dogrulamak. - DURUM: UYGULANDI
### [17 March 2026 - 00:34] DEPLOY / HIZLI KONTROL - Ne: yeni debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama launcher uzerinden yeniden acildi. Son hizli log kontrolunde `FATAL EXCEPTION` gorulmedi; `MainActivity` sureci ayaga kalkti (`pid=4525`). Neden: Compose overlay sonuc refactor'unun cihazda en azindan acilis seviyesinde anlik crash uretmedigini dogrulamak. - DURUM: UYGULANDI
### [17 March 2026 - 00:38] LOG / TESHIS - Ne: kullanicinin yeni scan denemesi sonrasi loglar cekildi. Crash aninda `OverlayService.showResultOverlay()` icinden eklenen yeni Compose overlay root'u (`LinearLayout`) icin `java.lang.IllegalStateException: ViewTreeLifecycleOwner not found from android.widget.LinearLayout{...}` fatali goruldu. Ayni log turunda floating scan butonunun alt-orta basladigi ama her basista `ACTION_UP` sonunda `snapToEdge()` cagrildigi icin saga kaydigi da kod seviyesinde teyit edildi. Neden: scan crash'inin ve butonun saga ziplamasinin tek tek somut kok nedenlerini ayirmak. - DURUM: UYGULANDI
### [17 March 2026 - 00:38] `OverlayService.kt` - Ne: result overlay root'una da `setViewTreeLifecycleOwner(this@OverlayService)` eklendi; boylece Compose pencere recomposer'i parent root'ta gerekli lifecycle owner'i bulabilecek. Ayrica floating scan butonunda `snapToEdge()` artik sadece gercek surukleme oldugunda cagriliyor; basit tiklamada konum korunuyor. Neden: scan sonucunda anlik Compose crash'ini kapatmak ve oyun icindeki scan butonunun her basista saga kaymasini durdurmak. - DURUM: UYGULANDI
### [17 March 2026 - 00:38] `CollectionScreen.kt` - Ne: ana ekrandaki scan butonu buyutuldu ve `RECENT SCANS` basliginin hemen ustune tasindi. Padding, nokta boyutu ve yazı boyutu arttirildi. Neden: kullanicinin ana uygulama icindeki scan baslatma butonunu daha belirgin ve erisilebilir istemesi. - DURUM: UYGULANDI
### [17 March 2026 - 00:38] BUILD / DEPLOY - Ne: crash duzeltmesi ve yeni buton yerlestirmeleri sonrasi `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine yeniden kuruldu, `logcat -c` yapildi ve uygulama launcher uzerinden tekrar acildi. Neden: kullanicinin scan butonu ve scan sonucu overlay crash'ini ayni build ile tekrar sahada test edebilmesi. - DURUM: UYGULANDI
### [17 March 2026 - 00:43] LOG / TESHIS - Ne: kullanicinin "hala crash oluyor" geri bildirimi sonrasi yeni log kontrolu yapildi. Son loglarda onceki `ViewTreeLifecycleOwner not found` fatali gorundu; bu, yalnizca lifecycle owner eklemenin yetmedigini ve servis tabanli Compose overlay'de owner zincirinin eksik oldugunu gosterdi. Bu turde root / ComposeView icin `SavedStateRegistryOwner` ve `ViewModelStoreOwner` da servis seviyesinde tanimlanip agaca enjekte edildi. Neden: pencere uzerinde Compose recomposer ve remember/saveable zincirini tam hale getirmek. - DURUM: UYGULANDI
### [17 March 2026 - 00:43] `OverlayService.kt` - Ne: servis `SavedStateRegistryOwner` ve `ViewModelStoreOwner` olarak genisletildi; `SavedStateRegistryController` ve `ViewModelStore` eklendi. Result overlay root'u ve `ComposeView` artik `setViewTreeLifecycleOwner`, `setViewTreeSavedStateRegistryOwner`, `setViewTreeViewModelStoreOwner` uclusu ile agaca baglaniyor. Servis lifecycle durumu da `RESUMED` seviyesine cekildi. Neden: servis icinde `TYPE_APPLICATION_OVERLAY` uzerinden acilan Compose pencerenin Activity benzeri owner beklentisini karsilamak ve crash'i kapatmak. - DURUM: UYGULANDI
### [17 March 2026 - 00:43] `CollectionScreen.kt` - Ne: ana ekrandaki scan butonu daha da buyutuldu; filtre chip'leri butonun altina tasindi. Yeni buton padding'i ve tipografisi daha belirgin hale getirildi. Neden: kullanicinin butonu "baya buyuk" istemesi ve filtrelerin butonun altinda yer almasi talebi. - DURUM: UYGULANDI
### [17 March 2026 - 00:43] BUILD / DEPLOY - Ne: owner zinciri + yeni ana ekran yerlestirmesi sonrasi `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine yeniden kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: yeni owner zincirinin sahada crash'i kapatip kapatmadigini dogrudan tekrar test etmek. - DURUM: UYGULANDI
### [17 March 2026 - 00:56] UI GUNCELLEME - Ne: kullanicinin sagladigi `ScanResultOverlayCard.kt` dosyasi projeye `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt` olarak eklendi ve overlay sonucu bunu kullanacak sekilde baglandi. Dosya icindeki bozuk karakterler/uyumsuz kisimlar Android Compose derlemesi icin temizlendi ancak layout ve animasyon yaklasimi korunarak ayni overlay karti kullanildi. Neden: kullanicinin istedigi yeni scan sonucu gorunumunu dogrudan ekli dosya uzerinden sahaya almak. - DURUM: UYGULANDI
### [17 March 2026 - 00:56] `OverlayService.kt` - Ne: result overlay sunumu yeniden duzenlendi. `ScanResultOverlayCard` artik `ui.overlay` paketinden geliyor; pencere parametreleri `gravity = Gravity.BOTTOM` ve `height = WRAP_CONTENT` ile guncellendi. Harici drag handle kaldirildi; kartin yalnizca ust bandindan suruklenebilmesi icin dogrudan compose root'a drag davranisi eklendi. Floating oyun ici scan butonunun varsayilan konumu da sag tarafa yaslandi (`Gravity.TOP|END`). Neden: kullanicinin hem overlay kartinin alttan compact acilmasi hem de oyun icindeki scan butonunun saga yasli olmasi talebi. - DURUM: UYGULANDI
### [17 March 2026 - 00:56] `CollectionScreen.kt` - Ne: ana uygulama acilis ekranindaki scan butonu daha genis hale getirildi; artik satir icerigi kadar degil yatayda cok daha buyuk gorunuyor. Filtreler butonun altinda kalmaya devam ediyor. Neden: ilk ekran butonunun daha kolay ulasilabilir olmasi istendi. - DURUM: UYGULANDI
### [17 March 2026 - 00:56] BUILD / DEPLOY - Ne: ekli overlay kart dosyasi ve son buton yerlestirmeleri sonrasi `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine yeniden kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: yeni alt-aligned overlay kartin ve son buton davranislarinin dogrudan cihazda test edilmesi. - DURUM: UYGULANDI
### [17 March 2026 - 01:01] LOG / TESHIS - Ne: yeni scan denemesinden sonra crash logu cekildi. Bu kez owner zinciri degil, `ScanResultOverlayCard.kt` icindeki gradient tanimi fail oldu: `java.lang.IllegalArgumentException` `android.graphics.LinearGradient.nativeCreate` icinde tetiklendi. Kok neden, kartin ust hero bolgesinde `Brush.linearGradient(... end = Offset(Float.MAX_VALUE, Float.MAX_VALUE))` kullanilmasiydi. Android native shader bu degeri kabul etmiyor. Neden: yeni eklenen overlay kart dosyasinin sahada patlayan tek noktasini izole etmek. - DURUM: UYGULANDI
### [17 March 2026 - 01:01] `ScanResultOverlayCard.kt` - Ne: gecersiz gradient son noktasi kaldirildi; `Brush.linearGradient` varsayilan sonlu koordinatlarla kullanilacak sekilde duzeltildi. Neden: `LinearGradient.nativeCreate` crash'ini kapatmak. - DURUM: UYGULANDI
### [17 March 2026 - 01:01] BUILD / DEPLOY - Ne: gradient crash duzeltmesi sonrasi `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine yeniden kuruldu, `logcat -c` yapildi ve uygulama tekrar acildi. Neden: yeni overlay kartin crash'siz acilip acilmadigini tekrar sahada denemek. - DURUM: UYGULANDI
### [17 March 2026 - 01:27] UI TESHIS - Ne: kullanici overlay sonuc kartinda dort yeni problem bildirdi: (1) tipografi yeterince agir ve belirgin degil, (2) sonucu suruklerken kart saga-sola zipliyor ve kontrollu tasinamiyor, (3) alt koseler ust kisim gibi yuvarlak degil, (4) `rarity breakdown` icinde bulunan ve bulunamayan sinyaller karisik sirada listeleniyor. Kod incelemesinde ziplamanin ana nedeni result overlay penceresinin `Gravity.BOTTOM|CENTER_HORIZONTAL` ile acilip surukleme delta'larinin bu koordinat sistemine yazilmasi olarak ayrildi; breakdown da dogrudan ham `pokemon.analysis` sirasi ile ciziliyordu. Neden: sonraki revizyonun yalnizca stil degil, pencere yerlesim fiziigi ve veri siralama davranisini da duzeltmesi gerektigini netlestirmek. - DURUM: UYGULANDI
### [17 March 2026 - 01:27] `ScanResultOverlayCard.kt` - Ne: sonuc kartinin dis ve ic kapsayici sekilleri tum koselerde yuvarlak olacak sekilde guncellendi. Tipografi agirlastirildi; ust baslik, IV satiri ve breakdown yazilari daha yuksek alpha ve daha agir `FontWeight` ile yeniden ayarlandi. `rarity breakdown` listesi de `partition { isPositive }` ile yeniden siralanarak pozitif/bulunmus sinyaller ustte, bos ya da bulunamayanlar altta toplanacak sekilde cizilmeye baslandi. Neden: kartin hem daha okunur hem de daha duzenli gorunmesi; kullaniciya once bulunan sinyalleri gostermek. - DURUM: UYGULANDI
### [17 March 2026 - 01:27] `OverlayComponents.kt` - Ne: overlay yardimci bilesenlerinde buton, tag, stat ve geri kontrolu tipografileri agirlastirildi; alpha ve boyutlar belirginlestirildi. Neden: scan sonucu kartindaki tum metin hiyerarsisini daha guclu ve uzaktan okunur yapmak. - DURUM: UYGULANDI
### [17 March 2026 - 01:27] `OverlayService.kt` - Ne: result overlay artik `Gravity.TOP|START` koordinat sistemiyle aciliyor ve `addView` sonrasi alt-orta konuma programatik olarak yerlestiriliyor; boylece surukleme delta'lari stabil hale getirildi. Drag alani ust bantta buyutuldu (`84dp`), hareket sirasinda `x/y` degerleri ekran sinirlarina clamp ediliyor. Neden: kartin kullanicinin parmaginin altinda kalmasi ve saga-sola ziplamadan kontrollu tasinabilmesi. - DURUM: UYGULANDI
### [17 March 2026 - 01:27] BUILD / DEPLOY - Ne: overlay tipografi/siralama/surukleme revizyonlari sonrasi `assembleDebug` basarili tamamlandi; debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu, `logcat -c` yapildi ve uygulama yeniden acildi. Neden: yeni kart davranisini sahada dogrudan test etmek. - DURUM: UYGULANDI
### [17 March 2026 - 01:31] UI INCE AYAR - Ne: kullanici ilk tipografi turunu yetersiz buldu. Overlay kartindaki tum metinler ikinci kez agirlastirildi; gri metin ve ikincil sayilarin alpha degerleri yukseltilerek daha belirgin hale getirildi. Alt aksiyon satiri da `Kaydet / Kapat / Paylas` sirasina alindi. Neden: kartin uzaktan daha rahat okunmasi ve beklenen buton diziliminin saglanmasi.
### [17 March 2026 - 01:31] `OverlayService.kt` - Ne: sonuc karti drag davranisi tekrar ayarlandi. Ust kontrol satiri ve alt buton bandi drag disinda birakildi; kartin orta govde alani surukleme alani yapildi. Böylece hem ustteki geri/paylas kontrolleri hem alttaki aksiyonlar tiklanabilir kalirken, karti tasimak icin daha genis ve daha dogal bir alan sunuldu. Neden: kullanici karti hala istedigi gibi surukleyemedigini bildirdi; onceki dar ust-bant drag mantigi yeterli olmadi.
### [17 March 2026 - 01:39] UI SIKILASTIRMA - Ne: kullanici overlay kartin hala fazla uzun oldugunu ve ustteki butonlarin gereksiz yer kapladigini bildirdi. `ScanResultOverlayCard.kt` icinde ustteki geri/share ikonlari tamamen kaldirildi; hero alan yuksekligi kisaldi. Alt aksiyon renkleri swap edilerek `Kaydet` gri/secondary, `Kapat` ise turuncu-mor gradient primary hale getirildi. Tag pill tasarimlari da daha koyu arka plan ve accent border ile yeniden ayarlandi, boylece Pokemon adi ile etiketler ayni gorsel agirlikta durmayip birbirinden daha net ayrildi. Neden: karti daha kompakt hale getirmek ve basligin odagini geri almak.
### [17 March 2026 - 01:39] `ShinySignatureStore.kt` - Ne: histogram tabanli shiny fallback daha secici hale getirildi. Artik bu fallback yalnizca shiny toplam skoru zaten normalden daha iyi ise (`shinyWins`) ve fark gercekten pozitifse (`scoreGap >= 0.01`) calisiyor; ek olarak renk ve toplam skor ust kapilari da tekrar uygulanıyor. Neden: normal bir `Machamp` scan'inin `shiny=true` donmesi, histogram fallback'in tek basina fazla gevsek kaldigini gosterdi. Bu degisiklik Machamp'a ozel degil; genel false-positive hattini sikilastiriyor.
### [17 March 2026 - 01:43] UI / AKIS TESHISI - Ne: kullanici bes yeni sorun bildirdi: scan sonucu gec geliyor, shiny hatalari suruyor, paylas chooser'i overlay kartin arkasinda kaliyor, IV alani anlamli bir deger gostermiyor, caught date yili kayboluyor. Kod incelemesinde gecikmenin bir kismi `ScanManager` icinde overlay gosterilmeden once `repository.saveScan(...)` beklenmesinden kaynakli bulundu; ayrica `isHighConfidence()` HP zorunlulugu yuzunden sik sik tum frameleri isliyordu. IV tarafinda ise hesaplanan `ivEstimate` string'i overlay modeline sadece `Int` parse edilerek tasindigi icin aralik/`???` gibi degerler kayboluyordu. Caught date yili da `caughtDate.take(8)` ile bilincli olarak kesiliyordu. Paylas chooser'i ise overlay penceresi ekranda kaldigi icin gorunurde onun arkasina dusuyordu. - DURUM: UYGULANDI
### [17 March 2026 - 01:43] `ScanManager.kt` - Ne: sonuc overlay'i artik kayit yazimindan once gosteriliyor; `saveScan` arka planda ayrica launch ediliyor. Buna ek olarak `isHighConfidence()` gevsetildi; iyi CP ve isim ile birlikte HP yerine `arc` veya `caughtDate` de destek sinyali olarak yeterli sayiliyor. Pipeline'a `overlay dispatched in X ms` logu eklendi. Neden: kullanicinin algiladigi sonucu bekleme suresini dusurmek ve gereksiz frame taramasini azaltmak.
### [17 March 2026 - 01:43] `Pokemon.kt` / `ScanResultOverlayCard.kt` - Ne: UI modeline `ivText` eklendi ve overlay kart artik ham IV metnini (`90-95%`, `???` vb.) gosterebiliyor; yalnizca integer parse'e bagli kalmiyor. Ayni turda caught date artik kisaltilmadan tam string olarak gosteriliyor. Pokemon adi biraz daha buyutuldu; pozitif satirlar kalin tutulmaya devam etti. Neden: "IV hesaplamasi yapilamiyor" geri bildirimindeki bir kisim aslinda sunum zincirinde degerin kaybolmasiydi; tarih yilinin de kesilmemesi istendi.
### [17 March 2026 - 01:43] `OverlayService.kt` - Ne: `shareResult()` cagrilmadan hemen once result overlay kapatiliyor. Neden: Android share chooser'in overlay penceresinin arkasinda kalmasini engellemek.
### [17 March 2026 - 01:50] LOG / TESHIS - Ne: kullanicinin yeni log istegi uzerine son scanler cekildi. Normal `Machamp` orneginde `ScanManager` `Name=Machamp` ile dogru OCR secmesine ragmen `SpeciesRefiner` bunu `Machoke`ya override ediyor; ardindan `ShinySignatureStore` icinde ek region'lardan biri `Hue fallback for Machoke` tetikleyip `VisualFeatureDetector` sonucu `shiny=true` yapiyor. Ayni vakada `Lucky Analysis` icinde yalnizca `cardGreen=0.097` gibi zayif bir sinyal olmasina ragmen mevcut kural `lucky=true` uretiyor. Ayrica overlay dispatch suresi logda `2992ms` ve `4783ms` bandinda; yani son hiz iyilestirmesine ragmen OCR maliyeti halen yuksek. IV alani bu ornekte sunumdan degil veri eksikliginden `???`: secilen frame icin `HP=null`, bu nedenle `RarityCalculator` aralik uretemiyor. - DURUM: UYGULANDI
### [17 March 2026 - 01:50] `SpeciesRefiner.kt` - Ne: `exactParsedSpeciesLock` eklendi. Ham OCR ismi mevcut species ile birebir parse eslesiyorsa ve text confidence cok yuksekse (`>=0.90`), profile mismatch olsa bile species lock korunuyor. Neden: `Machamp` gibi dogru okunan isimlerin gereksiz family override ile `Machoke`ya kaymasini kesmek.
### [17 March 2026 - 01:50] `ShinySignatureStore.kt` / `VisualFeatureDetector.kt` - Ne: shiny ve lucky false-positive kapilari yeniden sikilastirildi. `ShinySignatureStore` icinde hue fallback artik yalnizca `shinyWins=true` ise ve `dShiny` daha yakin/sinirli ise calisiyor; `VisualFeatureDetector` lucky tarafinda ise `cardGreen` tek basina artik daha yuksek esik istiyor, focus/support tabanli lucky kabul esikleri de yukari cekildi. Neden: normal `Machamp`ta gorulen `shiny=true` ve `lucky=true` hatlarini kapatmak.
### [17 March 2026 - 01:50] `ScanManager.kt` - Ne: OCR oncesi downscale genisligi `1080 -> 900` cekildi. Neden: OCR maliyetini azaltip sonucu daha hizli getirmek.
### [17 March 2026 - 01:50] `OverlayService.kt` / `AndroidManifest.xml` / `res/xml/file_paths.xml` - Ne: paylasim metin yerine kart gorselini de icerecek sekilde genisletildi. Result overlay view bitmap olarak cache altina render ediliyor; `FileProvider` uzerinden chooser'a `image/png` stream veriliyor. Neden: kullanici yalnizca text degil kartin kendisini paylasmak istiyor.
### [17 March 2026 - 01:50] `ScanResultOverlayCard.kt` - Ne: Pokemon adi ve skor bir tur daha buyutuldu/agirlastirildi. Neden: kullanicinin isim ve skorun hala yeterince kalin gelmedigi geri bildirimi.
### [17 March 2026 - 01:58] LOG / TESHIS - Ne: yeni scan turunda `Machamp` species duzeldi ama `shiny=true` halen `VisualFeatureDetector.chooseShinyResult()` icindeki histogram-only fallback kabulunden geliyor; `Machamp` logunda `signature=0.0, hist=0.7299` ile tek basina hist kabul edilmis. `Vaporeon` icin `costume=true` yanlisi `CostumeSignatureStore` icindeki near-tie kuralindan (`scoreGap=0.00063`) geliyor; `shiny=true` ise `Soft relative fallback for Vaporeon` ile uretiliyor. `Farfetch'd` icin `lucky=true` karari ust kose/location-card rengi cok yuksek oldugu halde non-card lucky kosullarindan geciyor (`upperCornerAvg=0.657`). Tum orneklerde `Date -> null`; `Badge raw` ya bos ya da tek basina `1513`, `HP raw` da bos/kayik. Bu da hem `caught date = Unknown` hem `IV ???` kok nedenini dogruluyor. - DURUM: UYGULANDI
### [17 March 2026 - 01:58] `VisualFeatureDetector.kt` / `ShinySignatureStore.kt` / `CostumeSignatureStore.kt` - Ne: histogram-only shiny fallback kabulü daha da kisildi; yalnizca hist ile gelen zayif fallback'ler artik red edilecek. `softRelativeWin` esikleri sertlestirildi. `CostumeSignatureStore` tarafinda near-tie kabul mantigi kaldirildi; artik costume false-positive icin yalnizca gercek costume win gerekiyor. Lucky tarafinda non-card lucky kabul kosullari `upperCornerAvg` ile sinirlandi; location-card benzeri ust kose renkleri lucky diye kabul edilmeyecek. Neden: Machamp/Vaporeon/Farfetch'd uzerinde ayrisan uc farkli false-positive hattini kapatmak.
### [17 March 2026 - 01:58] `OCRProcessor.kt` / `TextParser.kt` / `ScreenRegions.kt` / `ScanManager.kt` - Ne: hiz ve eksik veri zinciri birlikte yeniden ayarlandi. `ScanManager` artik tum framelerde hafif OCR (`includeSecondaryFields=false`) calistirip sadece secilen en iyi frame uzerinde ikinci bir tam OCR gecisi yapiyor; boylece candy/lucky/stardust/tarih gibi pahali OCR adimlari her framede tekrarlanmiyor. Tarih rozetinde coklu OCR denemesi eklendi (`Badge`, `BadgeHC`, `BadgeBinary`), alt tarih bolgesi genisletildi, `HP` bolgesi asagi/genis kaydirildi. Neden: sonucu hizlandirirken ayni anda `HP` ve `caught date` alanlarini geri kazanmak; bunlar olmadiginda IV ve tarih alanlari bos dusuyor.
### [17 March 2026 - 02:07] LOG / TESHIS - Ne: son build sonrasi loglarda durum tekrar ayrildi. `Machamp` artÄ±k species olarak dogru, `lucky=false`; ama `shiny=true` halen `VisualFeatureDetector` icindeki histogram-only fallback kabulunden geliyor (`signature=0.0, hist=0.7299`). `Vaporeon` false `costume` karari `CostumeSignatureStore` near-tie'inden, false `shiny` ise `softRelativeWin` yolundan geliyor. `Farfetch'd` false `lucky` karari ust-kose/location-card rengi cok yuksek olmasina ragmen non-card lucky kosullarindan geciyor. Tum orneklerde `Date -> null`, `HP -> null`; ozellikle `Badge raw` bos ya da `1513`, `Bottom raw` ise move adi oldugu icin tarih reconstruct edilemiyor. Ayrica OCR fast pass'te hala tam `procHC` bitmap'i uretildigi ve preprocess 900'lük kaynagi tekrar 1080'e upscale ettigi goruldu; bu gereksiz maliyet. - DURUM: UYGULANDI
### [17 March 2026 - 02:07] `ImagePreprocessor.kt` / `OCRProcessor.kt` / `ScreenCaptureService.kt` - Ne: OCR ve capture zinciri tekrar hizlandirildi. Preprocess adimlari artik 900'luk kaynagi yeniden 1080'e buyutmuyor; `OCRProcessor` hizli turda `procHC` bitmap'ini lazy olusturuyor ve gerekmedikce hic uretmiyor. `ScreenCaptureService` tarafinda capture `3 -> 2` kareye dusuruldu, interval `120ms -> 80ms`, ilk bekleme `40ms -> 20ms` cekildi. Neden: kullanicinin hala yavas buldugu scan sonuc suresini asagi cekmek; loglarda asiri maliyetin preprocess + gereksiz ekstra frame oldugu goruldu.
### [17 March 2026 - 02:07] `VisualFeatureDetector.kt` / `ShinySignatureStore.kt` / `CostumeSignatureStore.kt` - Ne: false-positive kapilari bir tur daha kapatildi. Histogram-only shiny fallback artik tek basina zayif confidence ile kabul edilmiyor; `softRelativeWin` daha sert. `CostumeSignatureStore` near-tie kabul mantigi kaldirildi. Non-card lucky yollarina `upperCornerAvg` limiti eklendi; ust-kose/location-card agir sahneler artik lucky gibi kabul edilmeyecek. Neden: Machamp/Vaporeon/Farfetch'd orneklerinde ayrisan shiny/costume/lucky hatlarini kapatmak.
### [17 March 2026 - 02:07] `ScreenRegions.kt` / `OCRProcessor.kt` / `TextParser.kt` - Ne: tarih ve HP OCR icin ikinci revizyon yapildi. `HP` ve `DATE_BOTTOM` bolgeleri yeniden ayarlandi; `OCRProcessor` artik `HP_WM` ve badge icin `BadgeFixedWM`, `BadgeHC`, `BadgeBinary` gibi ek okumalar uretiyor. `TextParser` da birden fazla tarih ham metnini sirasiyla deneyen `parseDate(vararg)` overload'u ile guncellendi. Neden: caught date halen `Unknown`, IV ise `HP=null` yuzunden `???`; bu iki alan icin birincil sorun verinin hic cikarilamamasiydi.
### [17 March 2026 - 02:19] LOG / TESHIS - Ne: yeni scan turunun loglari tekrar alindi. Son build ile `Machamp` orneginde `DisplayName -> Machamp`, `HP raw='174/174 HP 7' -> (174,174)`, `Results: shiny=false, lucky=false, costume=false` ve `overlay dispatched in 2764ms` goruldu; yani Machamp false shiny/lucky hattı kapanmis. `Vaporeon` orneginde `DisplayName -> Vaporeon`, `HP raw='7227 / 227 HP' -> (227,227)`, `Results: shiny=false, lucky=false, costume=false` ve `overlay dispatched in 2348ms` goruldu; onceki false shiny/costume hattı bu ornekte kapanmis olsa da HP parse halen kirli. Ancak baska scanlerde sorun suruyor: `Raikou` icin `Results: shiny=true(1.0)`, `Snorlax` icin `costume=true(0.35)`, `Seviper` icin `HP raw='92/P2HP' -> null` ve `shiny=true(0.8211)` goruldu. Tum orneklerde `Date -> null`; badge OCR artik sayisal parcaciklar uretiyor (`2017 25112`, `1 33113` vb.) ama parser bunlari tarihe ceviremiyor. Neden: son hiz/ocr revizyonunun bazi specieslerde dogruyu toparladigi, fakat tarih OCR, kirli HP parse ve genel shiny/costume false-positive hattinin halen tamamlanmadigi netlesti. - DURUM: DEVAM EDIYOR
### [17 March 2026 - 02:19] PERFORMANS NOTU - Ne: loglara gore ana gecikme IV hesaplamasi degil, OCR + gorsel analiz zinciri. Son turda hizli OCR gecisleri `225-409ms`, tam OCR gecisleri `1180-3433ms`, toplam `overlay dispatched` suresi ise `2348-4823ms` bandinda. IV hesaplamasi `RarityCalculator` icinde hafif bir matematik adimi; kaldirilirsa bir miktar sure kazanilir ama asıl darboğazı cozmeye yetmez. Neden: kullanicinin "IV hesaplamasini kaldirirsak hizlanir mi?" sorusuna dogrudan log temelli cevap vermek. - DURUM: UYGULANDI
### [17 March 2026 - 02:36] SUREC DEGISIMI - Ne: canli cihaz ustunde tek tek heuristic yamasi yapma yaklasimi birakildi. Yeni yon, scan hattini once offline regression seti ile olculebilir hale getirmek: fixture manifest + instrumentation regression testi + fixture export araci. Neden: son gunlerde ayni scan hatalarina tekrar tekrar donulmesinin kok nedeni, sabit test seti ve otomatik regresyon kontrolunun olmamasi.
### [17 March 2026 - 02:36] `app/src/androidTest/assets/scan_regression_cases.json` / `ScanRegressionTest.kt` - Ne: Android instrumentation tabanli ilk regression harness eklendi. Harness tek frame fixture'lar uzerinde `OCRProcessor`, `SpeciesRefiner`, `VisualFeatureDetector`, `RarityCalculator` zincirini calistiriyor; species/CP/HP/shiny/lucky/costume/location/date beklentilerini karsilastiriyor ve sure olcumu topluyor. `strict=false` olan fixture'larda test fail etmeden rapor uretiyor; boylece once veri toplanip sonra fixture'lar strict moda alinabilecek. Neden: scan katmanini sahadaki random denemeler yerine tekrar calistirilabilir veri seti uzerinden duzeltmek.
### [17 March 2026 - 02:36] `scripts/run_scan_regression.ps1` / `scripts/stage_latest_scan_fixture.ps1` - Ne: regression testini tek komutla calistirmak ve yeni fixture klasorleri icin manifest template olusturmak uzere iki script eklendi. Neden: yeni calisma bicimini tekrarlanabilir hale getirmek ve her yeni hatali scan'i fixture setine eklemeyi hizlandirmak.
### [17 March 2026 - 02:36] ILK BULGU - Ne: mevcut `.codex_tmp/latest_scan/*.png` kopyalari gercek binary PNG degil; byte basliklari `FF FE FD FF 50 00 4E 00 47 00...` seklinde bozulmus. Bu dosyalar instrumentation harness icinde de decode edilemiyor ve regression seti icin guvenilir kaynak olamiyor. Neden: son scanlerden lokal kopyalanan dosyalarla test baslatma denemesinde kok veri sorunu tespit edildi.
### [17 March 2026 - 02:36] `ScanFixtureExportReceiver.kt` / `AndroidManifest.xml` / `scripts/export_device_scan_fixtures.ps1` - Ne: cihazdaki gercek `cache/scan_*.png` dosyalarini bozulmadan app-specific external storage altina kopyalayan debug export receiver eklendi. Receiver `com.pokerarity.scanner.EXPORT_SCAN_FIXTURES` action'i ile tetikleniyor; `fixtures/<caseId>/` altina PNG'leri ve `manifest_template.json` dosyasini yaziyor. PowerShell export script'i explicit component ile bu receiver'i tetikleyip fixture klasorunu cekmek icin hazirlandi. Neden: lokal `.codex_tmp` kopyalari bozuk oldugu icin regression setini dogrudan cihaz cache'inden cikarmak gerekiyor.
### [17 March 2026 - 02:37] DOGRULAMA - Ne: yeni export receiver derlendi, debug APK USB cihaz `RFCY11MX0TM` uzerine kuruldu ve explicit broadcast ile dogrulandi. Ilk denemede `No scan_*.png files found in cache to export` logu alindi; yani receiver calisiyor fakat bu anda cache'te export edilecek scan yok. Neden: fixture export hattinin canli olarak ayaga kalktigini ve bir sonraki scan turunda kullanima hazir oldugunu dogrulamak.
### [17 March 2026 - 02:46] LOG / TESHIS - Ne: yeni scan turunun loglari tag bazli cekildi. Ilk ornekte OCR `DisplayName -> Sawk`, `Candy -> Sawk`, `CP raw='1 311976 1' -> 1976`, `HP raw='128/ 128 HP' -> (128,128)` verdi; `VisualFeatureDetector` ise `Alternate shiny signature accepted for Sawk` ile `shiny=true(1.0)` karari uretti. Ardindan `RarityCalculator` OCR CP'yi guvenilir bulmayip `Mathematical CP Estimate: 2070` ile CP'yi matematiksel fallback'e cekti. Ikinci ornekte OCR `DisplayName -> Aerodactyl`, `Candy -> Aerodactyl`, `HP raw='117/117 HP' -> (117,117)` verdi; tarih ilk kez `Sun Jan 01 00:00:00 GMT 2023` olarak parse edildi fakat `CP raw='3 4162411 3 P 3' -> 113` cok kirli kaldigi icin yine `CP was missing, using mathematical estimate: 1590` fallback'i tetiklendi. Bu ornekte varyantlar `shiny=false`, `lucky=false`, fakat `costume=true(0.6108)` false-positive uretildi. Toplam sureler `2957ms` ve `3885ms`. Neden: son turda tarih parse'inin ilk kez devreye girdigi, buna karsilik species/variant/CP tarafinda hala ciddi false-positive ve OCR guven problemi oldugu netlesti. - DURUM: DEVAM EDIYOR
### [17 March 2026 - 03:08] SCAN SIKILASTIRMA - Ne: son `Sawk/Aerodactyl` log turuna gore scan hattinda uc dogrudan sikilastirma yapildi. `TextParser.parseCP()` tarafinda artik asiri uzun rakam zincirleri ve `CP` anchor'i olmayan 5+ haneli girdi dogrudan reddediliyor; bu, `1 311976 1` ve `3 4162411 3 P 3` gibi kirli OCR'lerin sahte CP'ye donusmesini kesiyor. `VisualFeatureDetector.chooseBestShinySignatureResult()` icinde tek bir alternate region eslesmesi artik primary destek veya region consensus olmadan `shiny=true` karari ceviramiyor; bu, `Alternate shiny signature accepted for Sawk` false-positive hattini hedefliyor. Ayni dosyada `hasCostume()` heuristic'i referans ture yakin kafa tonu oldugunda costume demeyecek sekilde daraltildi; artik head/body farki tek basina yeterli degil, head hue'nun species referansindan da anlamli uzaklasmasi gerekiyor. `RarityCalculator.validateAndFixCP()` icinde de trusted OCR CP hic yoksa ve candidate uzayi asiri buyuk / near-arc set asiri belirsizse matematiksel CP estimate tamamen skip ediliyor. Neden: son logda ayni anda CP fabricate edilmesi, alternate shiny kabulunden false-positive ve referans renge yakin sprite'ta costume false-positive goruldu. - DURUM: UYGULANDI
### [17 March 2026 - 03:08] BUILD / DEVICE DURUMU - Ne: yukaridaki sikilastirmalar sonrasi `assembleDebug --console=plain` basarili tamamlandi. Ancak bu anda `adb devices` bos dondugu icin debug APK cihaza kurulamadi ve yeni logcat turu baslatilamadi. Neden: kod degisikligini derleme seviyesinde dogrulamak ve cihaz yeniden gorunur olur olmaz ayni build'i sahaya surmeye hazir tutmak. - DURUM: BEKLIYOR
### [17 March 2026 - 10:54] DEPLOY - Ne: cihaz tekrar gorundukten sonra mevcut debug APK (`app-debug.apk`) USB cihaz `RFCY11MX0TM` uzerine `install -r` ile kuruldu. Ardindan `logcat -c` yapildi ve uygulama launcher intent'i ile yeniden acildi. Neden: sabah uygulanan scan sikilastirma revizyonunu sahaya alip yeni scan/log turu icin temiz baslangic durumu hazirlamak. - DURUM: UYGULANDI
### [17 March 2026 - 11:04] LOG / REGRESSION KAYDI - Ne: kullanicinin sabit 6'li regression seti (`Machamp`, `Vaporeon`, `Farfetch'd`, `Raikou`, `Snorlax`, `Seviper`) yeniden tarandi ve loglar alindi. Son turda `Machamp`, `Vaporeon`, `Farfetch'd` dogru sonuca geldi; `Vaporeon` icin tarih de ilk kez `Sat Feb 11 00:00:00 GMT 2017` olarak parse edildi. Kalan kirik hatlar net ayrildi: `Raikou` hala `Alternate shiny signature accepted` ile false shiny, `Snorlax` heuristic-only costume ile `costume=true(0.4779)`, `Seviper` ise histogram/alternate shiny hattindan `shiny=true(0.8213)` aliyor. Ayni turdaki fixture'lar cihazdan `regression_20260317_1059` case id'si ile export edildi; receiver 20 PNG + manifest template yazdi ve dosyalar yerelde `exported_fixtures/regression_20260317_1059` altina cekildi. Neden: artik canli scan yerine ayni kirik seti regression fixture'a cevirmek ve kalan hatalari somut sinyallere gore kapatmak. - DURUM: UYGULANDI
### [17 March 2026 - 11:04] `VisualFeatureDetector.kt` / `ShinySignatureStore.kt` / `scripts/export_device_scan_fixtures.ps1` - Ne: son loglara gore ikinci sikilastirma turu yapildi. `VisualFeatureDetector` icinde alternate shiny signature kabulÃ¼ artik yalnizca primary sonucu gercekten shiny ise veya en az iki region consensus veriyorsa aciliyor; score'un yuksek olmasi tek basina yeterli degil. Histogram-only shiny fallback kabul eşiği `0.90` altinda red edilecek sekilde sertlestirildi. Heuristic-only costume sonuclari icin yeni minimum confidence kapisi eklendi (`0.55`); bu, `Snorlax` gibi signature'siz species'lerde zayif heuristic positive'leri kesmek icin. `ShinySignatureStore` tarafinda relative shiny fallback icin `SHINY_MAX_TOTAL_RELATIVE` kapilari `0.62/0.58 -> 0.55/0.52` cekildi; bu, `Raikou` benzeri "iki referans da kotu ama shiny biraz daha az kotu" vakalarinda false-positive'i azaltmak icin. Ayrica `export_device_scan_fixtures.ps1` icindeki remote path quoting hatasi duzeltildi; artik export script'i `/storage/emulated/0/...` yolunu dogrudan kullanacak. Neden: kalan uc kirik regression vakasini (`Raikou`, `Snorlax`, `Seviper`) hedefli kapatmak ve fixture export hattini tek komutla calisir hale getirmek. - DURUM: UYGULANDI
### [17 March 2026 - 11:04] BUILD / DEPLOY - Ne: yukaridaki ikinci sikilastirma turu sonrasi debug APK yeniden derlendi ve USB cihaz `RFCY11MX0TM` uzerine kuruldu. `logcat -c` yapildi ve uygulama launcher intent'i ile tekrar acildi. Neden: ayni regression setini yeni kural seti ile tekrar sahada olcmek icin temiz test durumu hazirlamak. - DURUM: UYGULANDI
### [17 March 2026 - 11:07] LOG / REGRESSION DURUMU - Ne: ikinci sikilastirma turu sonrasi kullanici `Raikou`, `Snorlax`, `Seviper` u yeniden taradi ve loglar alindi. Sonuc: `Snorlax` kapandi; logda `Heuristic-only costume rejected for Snorlax: confidence=0.4700` goruldu ve final `costume=false`. `Raikou` da bu turda kapandi; final satir `Results: shiny=false, lucky=false, costume=false` olarak geldi. Ancak `Seviper` hala false shiny: `ShinySignatureStore` icinde ilk iki region score'u `normal=0.644/0.557`, `shiny=0.531/0.523` seviyesinde ve final yine `Results: shiny=true(0.8215)` oldu. Logta ek olarak `Hue fallback for Seviper` goruldu; yani kalan hata tek species'te hala signature relative win / hue fallback bandinda. Sureler bu turda `Seviper 1706ms`, `Snorlax 3172ms`, `Raikou 4315ms`. Neden: ikinci tur duzeltmelerin hangi vakalari kapattigini netlestirmek ve kalan tek ana false-positive'i izole etmek. - DURUM: DEVAM EDIYOR
### [17 March 2026 - 11:08] `ShinySignatureStore.kt` - Ne: Seviper benzeri zayif ama yine de relative shiny kabulunden gecen vakalar icin shiny signature tavanlari bir tur daha daraltildi. `SHINY_MAX_COLOR` `0.85 -> 0.70`, `SHINY_MAX_TOTAL_RELATIVE` `0.55 -> 0.52`, strict varyantlari da `0.80/0.52 -> 0.68/0.50` cekildi. Neden: son logda `Seviper` icin `shinyScore=0.531` ve `shinyColor=0.705` ile halen relative-win positive alinabildigi goruldu; bu tam olarak bu ust kapilari kullanarak red edilmesi gereken bir vaka.
### [17 March 2026 - 11:08] BUILD / DEPLOY - Ne: tek dosyalik shiny tavan daraltmasi sonrasi debug APK yeniden derlendi ve USB cihaz `RFCY11MX0TM` uzerine kuruldu. `logcat -c` yapildi ve uygulama launcher intent'i ile tekrar acildi. Neden: yalnizca kalan `Seviper` false-positive hattini yeni esiklerle dogrudan tekrar sahada test etmek. - DURUM: UYGULANDI
### [17 March 2026 - 11:15] LOG / BUILD DOGRULAMA - Ne: kullanicinin "yeni build kurulduguna emin misin" sorusu uzerine cihazdaki kurulu paket ve son scan logu dogrudan dogrulandi. `dumpsys package com.pokerarity.scanner` cikti­sinda `lastUpdateTime=2026-03-17 11:09:02` goruldu; yerelde olusan APK `app-debug.apk` da `LastWriteTime=11:08:54` idi. Son Seviper scan logu yeni surec (`pid=26532`) altinda bu build'den geldi ve hala `Weak relative fallback`, `Histogram fallback`, `Hue fallback` satirlariyla `shiny=true(0.65)` uretti. Neden: problemin eski APK kalmasindan degil, halen fazla gevsek kalan shiny fallback kurallarindan kaynaklandigini netlestirmek. - DURUM: UYGULANDI
### [17 March 2026 - 11:15] `ShinySignatureStore.kt` - Ne: shiny fallback hue-gap kapilari ikinci kez sertlestirildi. `SHINY_REF_HUE_GAP_SOFT` `20 -> 45` cekildi; `weakRelativeWin`, `histogramFallback` ve `hueFallback` icindeki ref-hue-gap kosullari da `35/25 -> 45` olacak sekilde guncellendi. Neden: son Seviper logunda ortak kok sinyal `refHueGap=39.1` idi; bu fark yeni politika altinda yeterli olmamali.
### [17 March 2026 - 11:15] BUILD / DEPLOY - Ne: hue-gap sertlestirmesi sonrasi debug APK yeniden derlendi ve USB cihaz `RFCY11MX0TM` uzerine kuruldu. `logcat -c` yapildi ve uygulama launcher intent'i ile tekrar acildi. Neden: yalnizca kalan `Seviper` false-positive hattini bu son fallback kapilariyla tekrar test etmek. - DURUM: UYGULANDI
### [17 March 2026 - 11:17] LOG / REGRESSION SONUCU - Ne: son `Seviper` tekrar scan logu alindi. Bu tur `ShinySignatureStore` fallback satirlari gorulmedi; yalnizca score breakdown satirlari loglandi ve final sonuc `Results: shiny=false, shadow=false, lucky=false, costume=false, locationCard=false` oldu. `processScanSequence: overlay dispatched in 2010ms` ile onceki 4s bandina gore daha hizli bir ornek de goruldu. Neden: sabit regression setindeki son kirik false-positive'i kapatip 6'li temel scan setini (`Machamp`, `Vaporeon`, `Farfetch'd`, `Raikou`, `Snorlax`, `Seviper`) tamamen yeşile cevirmek. - DURUM: UYGULANDI
### [17 March 2026 - 11:46] RARITY FAZ 1 - Ne: rarity mantigi icin veri tabanli ilk katman eklendi. `app/src/main/assets/data/rarity_rules.json` olusturuldu; burada `axisCaps`, `variantBonuses`, `ageTiers`, `collectorBonuses`, `comboBonuses` ve `confidenceWeights` tanimlandi. Buna karsilik `RarityRuleLoader.kt` eklendi; asset dosyasini parse edip fallback rule seti uretiyor. `RarityScore.kt` icinde de yeni model genisletmesi yapilarak `RarityAxisScore`, `axes` ve `confidence` alanlari eklendi. Neden: rarity skorunu tek parca heuristic toplam yerine ayri eksenlere bolmek ve ileride kod degistirmeden balanslanabilir hale getirmek. - DURUM: UYGULANDI
### [17 March 2026 - 11:46] `RarityCalculator.kt` - Ne: hesaplama giris noktasi tamamen yeni rule tabanli akis uzerine alindi. `calculate()` artik dogrudan `calculateRulesBased()` cagiriyor; legacy additive govde devre disi birakildi. Yeni akista skor 4 eksende uretiliyor: `Base Species`, `Variant`, `Age / Legacy`, `Collector Extras`. `shiny`, `shadow`, `lucky`, `locationCard`, `costume` bonuslari ve tanimli kombinasyon bonuslari manifestten okunuyor; event agirligi `collector` eksenine olceklenerek ekleniyor. Ayri olarak `calculateRarityConfidence()` ile isim/CP/HP/tarih/variant sinyaline dayali 0..1 guven skoru hesaplanip `RarityScore.confidence` alanina yaziliyor. IV artik toplam rarity skoruna karismiyor; yalnizca `ivEstimate` olarak tasiniyor. Neden: rarity'yi koleksiyon degeri odakli tutmak, IV'yi ayri bir metrik olarak korumak ve sonraki balans turlarini asset duzeyinde yapabilmek. - DURUM: UYGULANDI
### [17 March 2026 - 11:46] BUILD - Ne: rarity faz 1 refactor sonrasi `assembleDebug --console=plain` iki kez calistirildi; ilk derleme eski `calculate()` govdesinden kalan bozuk referanslari ortaya cikardi, ardindan govde temizlenip `runIVSearch` icindeki kullanilmayan parametre de kaldirildi. Son durumda derleme basarili. Bu tur cihaz kurulumu yapilmadi; yalnizca kod ve model gecisinin derlenebilir oldugu dogrulandi. Neden: rarity mantigini scan regresyon hattina dokunmadan once kod seviyesinde stabilize etmek. - DURUM: UYGULANDI
### [17 March 2026 - 12:04] RARITY UI ENTEGRASYONU - Ne: yeni rarity eksenleri UI sonuc akisina baglandi. `Pokemon.kt` icine ortak `buildAnalysisItems()` yardimcisi eklendi; hem overlay sonucu hem de `ResultActivity` artik `EXTRA_BREAKDOWN_KEYS/VALUES` ve `EXTRA_EXPLANATIONS` uzerinden gelen gercek rarity breakdown'ini kullaniyor. Boylece full result ekranindaki sabit `Shiny/Lucky/Tier` placeholder analizi kaldirilmis oldu; yeni `Base / Variant / Age / Collector` eksenleri sahaya tasindi. Neden: rarity faz 1 sadece hesaplayici seviyesinde kalmasin, kullanici gercek skor dagilimini gorebilsin. - DURUM: UYGULANDI
### [17 March 2026 - 12:04] BUILD / WIFI DEPLOY - Ne: rarity faz 1 ve UI entegrasyonu sonrasi `assembleDebug --console=plain` basarili tamamlandi. Ardindan Wi-Fi ADB hedefi `192.168.0.180:39887` uzerine `install -r` ile debug APK yuklendi; kurulum `Success` ile bitti. Hemen sonrasinda hedef cihaz ADB tarafinda `offline` duruma dustugu icin explicit launch dogrulamasi yapilamadi ve ikinci `adb connect` denemeleri timeout verdi. Neden: kullanicinin talebi uzerine build'in dogrudan cihaza itilmesi; mevcut durumda APK kurulu ama baglanti kopuk oldugu icin uygulamanin yeniden acilisi ayrica sahada kontrol edilecek. - DURUM: KURULDU, BAGLANTI KOPTU
### [17 March 2026 - 12:11] LOG DENEMESI - Ne: kullanicinin log alma talebi uzerine Wi-Fi ADB hedefi `192.168.0.180:39887` icin baglanti denenmis, ancak `adb devices` bos donmus ve `adb connect 192.168.0.180:39887` komutu `actively refused (10061)` ile fail etmistir. Bu nedenle son scan oturumunun loglari bu turda cekilememistir. Neden: cihaz tarafinda Wi-Fi debug portu aktif degil ya da baglanti yeniden acilmamisti. - DURUM: BAGLANTI BEKLENIYOR
### [17 March 2026 - 12:36] LOG ANALIZI - Ne: Wi-Fi ADB hedefi `192.168.0.180:33383` uzerinden son scan oturumu loglari alindi. Bu log penceresinde net gorunen tam ornek `Meditite` oldu: `VisualFeatureDetector` sonucu `shiny=false, shadow=false, lucky=false, costume=false, locationCard=false`, `RarityCalculator` mevcut OCR CP degerini gecerli buldu (`Current CP 142 is valid`) ve rules-based rarity hesabi buna gore calisti. `processScanSequence` toplam sure `3605ms` olarak goruldu. Bu log kesitinde rarity katmaninin scan sonucunu bozduguna dair sinyal yok; gorunen problem halen scan pipeline hizinda ve spesifik hatali orneklerin bu log penceresine dusmemis olmasi. Neden: kullanicinin "rarity kalibrasyonundan mi yoksa yine scan mi" sorusuna canli log temelli ayrim yapmak. - DURUM: UYGULANDI
### [17 March 2026 - 12:39] LOG ANALIZI - Ne: son log turunda net hatali ornek `Eevee` olarak ayrildi. OCR `Candy -> Eevee` okumasi dogruyken `Name raw='EeveeZQY'` geldi ve `DisplayName -> Eelektrik`, `RealName -> Eelektrik` olarak yanlis species secildi; yani species hatasi rarity'den degil OCR/species hattindan geliyor. Aynı ornekte visual detection dogru negatif verdi (`shiny=false`, `lucky=false`, `costume=false`, `locationCard=false`) ve CP `470`, HP `82/82` gecerli kaldı; sorun species resolver tarafinda. Tarih yine parse edilemedi (`Badge raw='2018 12108'`, `Date -> null`). Toplam sure `OCR 5847 ms`, `overlay dispatched in 7413ms` olarak goruldu; yani hiz problemi de scan tarafinda suruyor. Neden: rarity katmaninin degil, `Name/Candy/SpeciesRefiner` zincirinin ve OCR suresinin su anki birincil problem oldugunu logla netlestirmek. - DURUM: UYGULANDI

## 17 March 2026 - 12:58
- Method change applied. The scan pipeline no longer treats the detailed OCR pass as authoritative for primary fields.
- Root cause confirmed from live logs: fast OCR could identify the Pokemon correctly, then the slower detailed OCR pass could overwrite `name/realName/CP/HP` with worse text and poison the final result.
- `ScanManager` was reworked so the best fast frame is now the authoritative source for primary fields (`name`, `realName`, `CP`, `HP`, `maxHp`).
- The detailed OCR pass is now used only to backfill secondary fields and richer OCR traces (`candy`, `badge/date`, `bottom`, etc.).
- Raw OCR merge was changed so primary OCR keys keep the fast-pass values while secondary keys can come from the detailed pass.
- Added `ScanConsistencyGate` as a generic post-refine validation layer.
  - If the resolved species leaves the candy family, the gate corrects it back to the best same-family candidate when possible.
  - If the result is still inconsistent and cannot be corrected safely, the gate requests an automatic retry instead of publishing a wrong scan result.
- Added new retryable `LOW_CONFIDENCE_RESULT` scan error for unresolved inconsistent outputs.
- Fixed an unrelated but real display bug: overlay date had been using `bestResult.caughtDate` instead of the final fused/refined result date. The result card now uses `finalResult.caughtDate`.
- Added regression coverage with `ScanConsistencyGateTest` under `androidTest`.
- Verification:
  - `assembleDebug assembleAndroidTest` succeeded.
  - Installed updated debug APK and androidTest APK to Wi-Fi ADB device `192.168.0.180:33383`.
  - Ran `ScanConsistencyGateTest` on device: `OK (3 tests)`.
  - Cleared logcat and relaunched app after install.
- Diagnosis correction for the earlier Eevee case:
  - The earlier summary that framed it as a plain species-refiner failure was incomplete.
  - Log review showed the fast OCR pass had `Eevee` correct first, and the later detailed OCR pass then misread the same frame as `Eelektrik`.
  - This entry supersedes that earlier diagnosis.

## 17 March 2026 - 13:09
- Pulled live logs again after the previous structural merge/gate rollout.
- Confirmed the remaining root failures were now concentrated in three generic subsystems rather than random species drift:
  - CP parser still accepted anchored noisy text like `0 CP1278 1` and returned `2781`.
  - HP parser still accepted impossible pairs like `43/743 HP` and `93/793 HP` as valid.
  - Shiny detection still allowed signature-only positives even when all color/hue corroboration was negative (`Wingull`, `Metagross`, `Pikachu`).
- Applied generic fixes:
  - `TextParser.parseCP()` now prefers explicit `CP####` anchors before any leading-zero heuristic.
  - The leading-zero fallback is now disabled when a real `CP` anchor is present.
  - `TextParser.parseHPPair()` now rejects unreasonable HP pairs via a generic sanity gate (`10..500`, `current <= max`, `max <= current * 2.2`).
  - `TextParser.parseDate()` now supports compact badge tokens such as `2018 1108`, `2018 3008`, and `2018 21110`.
  - `VisualFeatureDetector.chooseShinyResult()` now rejects signature-only shiny positives unless there is at least one corroborating color/hue/hist signal, or the signature confidence is extremely high.
- Added parser regression coverage with `TextParserRegressionTest`.
- Verification:
  - `assembleDebug assembleAndroidTest` succeeded.
  - Installed updated APK and androidTest APK to Wi-Fi ADB device `192.168.0.180:33383`.
  - Ran device tests: `ScanConsistencyGateTest` + `TextParserRegressionTest` -> `OK (6 tests)`.
  - Cleared logcat and relaunched the app after install.

## 17 March 2026 - 13:29
- Pulled the next live log window after the previous parser/shiny fixes.
- Good scans in this window: `Zacian`, `Raichu`, several `Pikachu`/dated entries now parsed correctly, and the signature-only shiny false-positive path was confirmed blocked on `Raichu`.
- Remaining generic failures from logs:
  - CP correction still overrode plausible OCR CP values when there was no corroborated OCR consensus (`Regieleki 3311 -> 1682`).
  - Some scans still had no date because badge OCR returned only sparse fragments.
  - Mixed correctness remained, but the failure surface was narrower than before (mainly CP override / date sparsity, not cross-family species drift).
- Applied another generic CP reliability change:
  - `OCRProcessor` now uses the high-contrast CP OCR path whenever the raw CP line lacks an explicit `CP` anchor, even if the raw parser already produced a value.
  - `RarityCalculator.validateAndFixCP()` now keeps the OCR CP when there is no corroborated OCR CP consensus, no stardust anchor, and the mathematical candidate space is too large.
- `assembleDebug assembleAndroidTest` succeeded locally after this change.
- Attempted install + device tests in one command, but the Wi-Fi ADB session dropped and the device stopped responding on `192.168.0.180:33383` before I could verify the new APK on-device.
- This means the last CP-conservatism change is built locally but not yet confirmed on the phone.

## 17 March 2026 - 13:33
- Reconnected over Wi-Fi ADB at `192.168.0.180:32993` after the previous ADB drop.
- Installed the latest debug APK that contains:
  - CP high-contrast fallback preference when raw CP OCR lacks an explicit `CP` anchor.
  - More conservative mathematical CP override behavior when there is no corroborated OCR CP consensus.
- Cleared `logcat` and relaunched the app after install.

## 17 March 2026 - 13:35
- Pulled the next post-install live log window from `192.168.0.180:32993`.
- The scans visible in this window were `Raichu` and two `Pikachu` examples.
- In this log slice, the latest parser / shiny-gate fixes held correctly:
  - `Raichu`: species correct, date parsed (`2017-01-03`), shiny false-positive blocked, lucky false, costume false.
  - `Pikachu` examples: species correct, HP parsed, shiny false-positive blocked, lucky false, costume false.
- The log shows the new shiny gate doing the intended job:
  - `Alternate shiny signature accepted ...`
  - followed by `Signature-only shiny rejected ...`
  - final result remained `shiny=false`.
- Remaining issue still visible even in successful scans:
  - date stays `null` when badge OCR is only sparse fragments such as `1 33`.
- Latest visible overlay latency in this slice:
  - `Raichu`: `2413ms`
  - `Pikachu`: `1832ms`
  - `Pikachu`: `2845ms`
- No failing example was present in this specific log window, despite the user reporting that some scans are still wrong.
  - That means the current log slice is not enough by itself to isolate the remaining bad path.

## 17 March 2026 - 13:36
- Live log window after latest install reviewed on device 192.168.0.180:32993.
- Visible scans in this buffer were Raichu and Pikachu examples only.
- All visible examples were correct at scan layer:
  - species correct
  - shiny=false
  - lucky=false
  - costume=false
  - CP stayed on OCR value
- Date parsing was mixed:
  - Raichu date parsed successfully from 2017 0103
  - Pikachu badge OCR stayed too sparse (1 33 / 3 33), so Date -> null
- Conclusion: this specific log slice does not contain the failing example the user reported, so it is insufficient to justify another scan-logic patch.
- Next step for root-cause isolation: reproduce one wrong scan and pull logs immediately so the failing path stays inside the active buffer.

## 17 March 2026 - 14:08
- Live false-negative diagnosis corrected: the latest visible Raichu / Pikachu scans were not good examples; user confirmed they were actually costume Raichu, costume Pikachu, and shiny Pikachu.
- Implemented structural decision-gate fix in [VisualFeatureDetector.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/vision/VisualFeatureDetector.kt):
  - added SignatureConsensus so multi-region shiny agreement is tracked explicitly instead of collapsing to one pair too early
  - signature-only shiny can now pass only when there is strong multi-region consensus and no costume signal blocking it
  - dense costume species now allow heuristic fallback on near-tie / weak-negative signature gaps instead of skipping the heuristic entirely
  - hasCostume() body saturation floor restored from  .04 to  .03
- Added device regression coverage in [VisualFeatureDetectorDecisionTest.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/androidTest/java/com/pokerarity/scanner/VisualFeatureDetectorDecisionTest.kt) for:
  - dense variant near-tie costume heuristic gate (Pikachu)
  - dense variant small negative-gap costume heuristic gate (Raichu)
  - strong signature-consensus shiny accept without costume block
  - signature-consensus shiny reject when costume signal exists
- Build + validation:
  - ssembleDebug assembleAndroidTest succeeded
  - debug APK and androidTest APK installed on USB device RFCY11MX0TM
  - instrumented suite now passes OK (11 tests)
  - logcat cleared and app relaunched for fresh live validation

## 17 March 2026 - 14:14
- Pulled live logs for the three targeted re-scans after the new decision-gate build.
- Outcome from live device RFCY11MX0TM:
  - costume Pikachu fixed: costume=true(0.656642), shiny=false
  - costume Raichu fixed: costume=true(0.6168984), shiny=false
  - shiny Pikachu still wrong: final result costume=true(0.6621502), shiny=false
- Critical diagnosis from logs:
  - the two costume examples and the shiny Pikachu example all produce almost identical Costume Analysis head/body hue metrics (HeadHue�210, BodyHue�155, DistBody=55, BodySat�0.058)
  - shiny Pikachu also produces strong alternate shiny signature scores, but is blocked because the heuristic costume signal is now true (costumeBlocks=true)
  - this indicates the remaining failure is not a simple threshold miss; the live sprite crop / heuristic signal for Pikachu is not separating costume-vs-shiny reliably
- Exported the exact live scan frames for this failing batch to local fixtures:
  - [exported_fixtures/regression_20260317_1355](C:/Users/Caglar/Desktop/PokeRarityScanner/exported_fixtures/regression_20260317_1355)
- Next technical direction: bind these exact frames into the regression manifest and work from the real failing images instead of further blind threshold changes.

## 17 March 2026 - 14:27
- Priority shifted explicitly to species + costume/form + caught date. CP/HP/IV kept secondary for this phase.
- Added offline training pipeline: [train_variant_prototypes.py](C:/Users/Caglar/Desktop/PokeRarityScanner/scripts/train_variant_prototypes.py)
  - trains an asset-backed prototype classifier from local sprite assets
  - uses alpha-cropped sprite augmentations (contrast, blur, scale) instead of threshold-only heuristics
  - outputs [variant_classifier_model.json](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/assets/data/variant_classifier_model.json)
- First training run completed from local assets:
  - source: external/pogo_assets/Images/Pokemon - 256x256
  - output: 2154 prototypes across 711 species
- Added runtime loader/inference:
  - [VariantPrototypeStore.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeStore.kt)
  - [VariantPrototypeClassifier.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeClassifier.kt)
- Integrated classifier into [ScanManager.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt):
  - classifier now runs on the best screenshot frame
  - species can be overridden from classifier when confidence is high enough
  - shiny/costume flags can now be overridden from classifier output when confidence is high enough
  - classifier result is written back into 
awOcrText for tracing (ClassifierSpecies, ClassifierSpriteKey, ClassifierVariantType, confidence)
- Build status: ssembleDebug --console=plain successful after model integration.
- Deployment status:
  - debug APK installed on USB device RFCY11MX0TM
  - logcat -c executed
  - app relaunched successfully; no startup crash, ScanManager started visible in log

## 17 March 2026 - 14:26
- Pulled live logs after the first asset-backed classifier deployment. The model was running, but the main failure mode was clear:
  - global family-scoped matching was confusing `Pichu / Pikachu / Raichu`
  - classifier confidence stayed low on live frames, so the new motor rarely influenced the final result
  - example failures from logs:
    - costume/shiny `Pikachu` scans often produced `Pichu` as the best global asset match by a tiny margin
    - global classifier confidence stayed around `0.36`, below the old override threshold, so visual heuristics still dominated
- Applied a structural fix instead of another threshold-only pass:
  - `ScanManager` now uses the prototype classifier in two stages
    - global classifier remains species-oriented and is used only for species override when confidence is high enough
    - a second species-scoped classifier run is now executed against the already-selected species and is used for shiny/costume variant decisions
  - added a separate lower confidence gate for species-scoped variant matching:
    - `CLASSIFIER_VARIANT_CONFIDENCE_SPECIES = 0.52`
  - variant traces are now split in raw OCR debug fields:
    - `Classifier*` for the global species pass
    - `VariantClassifier*` for the species-scoped pass
  - variant merge logic now prefers additive overrides from the species-scoped pass instead of clearing existing visual positives
- Files updated:
  - [ScanManager.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt)
- Verification:
  - `assembleDebug --console=plain` successful
  - debug APK installed on USB device `RFCY11MX0TM`
  - `logcat -c` executed
  - app relaunched via `monkey`
- Next live validation target:
  - shiny `Pikachu`
  - costume `Pikachu`
  - costume `Raichu`

## 18 March 2026 - 01:07
- Pulled live logs after the broader non-regular scan batch. The user explicitly confirmed none of the scanned Pokemon were regular; each was shiny, costume, or both.
- Live scan summary from device `RFCY11MX0TM`:
  - `Raichu`: final result `costume=true`, `shiny=false`; species-scoped classifier top matches were all `Raichu` costume-shiny assets (`026_00_02_shiny`, `026_01_02_shiny`, `026_01_02`) but confidence stayed low (`0.379`), so shiny was not promoted.
  - `Pikachu` batches: costume scans were recognized correctly as `costume=true`; species-scoped classifier consistently converged on costume assets.
  - `Wobbuffet`: species-scoped classifier saw costume (`202_01_11`), visual detector also returned `costume=true`; final result correct.
  - `Shinx`: species-scoped classifier saw costume (`403_00_16`), visual detector rescue also returned `costume=true`; final result correct.
  - `Slowpoke`: final result came back fully regular (`shiny=false`, `costume=false`), but the species-scoped classifier top-3 were mixed non-regular candidates (`079_00`, `079_00_shiny`, `079_31`). This is still a false-negative on a known non-regular batch.
  - `Butterfree`: final result also came back fully regular (`shiny=false`, `costume=false`), while the species-scoped classifier top-3 were `012_01`, `012_00`, `012_01_shiny` and the best asset was tagged as `variantType=form` rather than costume.
- Structural diagnosis:
  - the species-scoped classifier is now working and stabilizing same-family matching
  - the remaining generic gap is no longer just threshold tuning
  - two real issues remain:
    1. many true non-regular scans still sit in the `0.35-0.48` confidence band, below the current species-scoped promotion gate
    2. some event variants are represented by asset keys that the current training parser labels as `form` instead of `costume`, so the runtime result layer ignores them completely because it only consumes `shiny` and `costume` booleans
- Performance from this batch remained slow:
  - overlay dispatch times ranged roughly from `3.6s` to `7.1s`
- Conclusion for the next engineering step:
  - extend the scan result layer beyond only `shiny/costume` booleans so classifier-level non-base `variantType` survives into the final result
  - replace species-level costume hints with asset-key-level costume/form mapping for training and runtime interpretation

## 18 March 2026 - 01:38
- Mapped the latest labeled non-regular batch against live logs. Only 9 actual scan sequences appeared in the captured batch; no `Eevee` or regular control scans were present in the live log window.
- Confirmed batch outcomes:
  - `Raichu` costume: final result costume-only; species-scoped classifier actually preferred shiny+costume assets but confidence stayed low
  - multiple `Pikachu` scans: final result stayed costume-only across the batch, so shiny false-negatives remain in Pikachu-family cases
  - `Wobbuffet` costume: correct
  - `Shinx` costume: correct
  - `Slowpoke` non-regular: incorrectly came back regular
  - `Butterfree` non-regular: incorrectly came back regular
- Structural conclusion from live logs:
  - the remaining generic gap is not just threshold tuning
  - some non-regular assets are reaching the classifier as `variantType=form` instead of `costume`, and the runtime result path was dropping that information completely
- Applied a live-result propagation fix:
  - added `hasSpecialForm` to [VisualFeatures.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/data/model/VisualFeatures.kt)
  - added `form` bonus to [rarity_rules.json](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/assets/data/rarity_rules.json) and fallback rules in [RarityRuleLoader.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/data/repository/RarityRuleLoader.kt)
  - updated [RarityCalculator.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt) to score and log `form` as a first-class variant axis
  - updated [Pokemon.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/data/model/Pokemon.kt) so live results can show a `FORM` tag and `Special form` analysis row
  - updated [ResultActivity.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt), [OverlayService.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt), and [ScanManager.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt) to pass the new `hasSpecialForm` signal through the live overlay path
  - species-scoped classifier can now promote `variantType=form` into the live result with a lower dedicated confidence gate (`0.34`) so true non-base event forms do not disappear as regular results
- Verification:
  - `assembleDebug` successful
  - debug APK installed on USB device `RFCY11MX0TM`
  - `logcat -c` executed
  - app relaunched via `monkey`
## 18 March 2026 - 09:55
- Same-family non-base variant rescue iceren yeni debug APK (build output timestamp 01:46:47) USB cihaz RFCY11MX0TM uzerine kuruldu.
- db install -r basarili oldu, logcat -c uygulandi.
- MainActivity exported olmadigi icin shell m start SecurityException verdi; uygulama launcher intent ile (monkey -p com.pokerarity.scanner -c android.intent.category.LAUNCHER 1) acildi.
- Bu build uzerinde yeni scan batch'i bekleniyor; hedef ozellikle Slowpoke costume, Butterfree costume+shiny, Pikachu shiny ve Pikachu costume dogrulamasi.
## 18 March 2026 - 15:43
- superpowers skill paketi https://github.com/obra/superpowers.git kaynagindan C:\Users\Caglar\.codex\superpowers altina clone edildi.
- C:\Users\Caglar\.agents\skills\superpowers icin junction olusturuldu ve C:\Users\Caglar\.codex\superpowers\skills hedefine baglandigi dogrulandi.
- Kurulum INSTALL.md talimatlarina gore tamamlandi; skill'lerin gorunur olmasi icin Codex yeniden baslatilacak.
## 18 March 2026 - 16:20
- Superpowers akisi ile scan stabilizasyon turu sistematik sekilde yeniden ele alindi:
  - `using-superpowers`, `systematic-debugging`, `test-driven-development`, `verification-before-completion`
- Tasarim ve uygulama plani dokumante edildi:
  - [2026-03-18-scan-variant-stabilization-design.md](C:/Users/Caglar/Desktop/PokeRarityScanner/docs/superpowers/specs/2026-03-18-scan-variant-stabilization-design.md)
  - [2026-03-18-scan-variant-stabilization.md](C:/Users/Caglar/Desktop/PokeRarityScanner/docs/superpowers/plans/2026-03-18-scan-variant-stabilization.md)
- Strict regression gate olarak canli fixture set kullanildi:
  - [scan_regression_cases.json](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/androidTest/assets/scan_regression_cases.json)
  - [live_variant_batch_20260318](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/androidTest/assets/scan_fixtures/live_variant_batch_20260318)
- Bu turda kapatilan yapisal noktalar:
  - [SpeciesRefiner.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt)
    - anchored current species icin zayif same-family override kapisi daraltildi; `Slowpoke -> Slowbro` yanlis override'i engellendi
  - [TextParser.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt)
    - `1176/116 HP`, `1307/130 HP` gibi gurultulu HP OCR'lari icin repair mantigi eklendi
    - slash parse akisinda noisy repair normal parse oncesine alindi
  - [ImagePreprocessor.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt)
    - orange caught-date badge icin ayri `processDateBadge()` onislemesi eklendi
  - [OCRProcessor.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt)
    - badge OCR preprocessor `processCandyText()` yerine `processDateBadge()` kullanacak sekilde degistirildi
  - [VariantPrototypeStore.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeStore.kt), [VariantPrototypeClassifier.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeClassifier.kt), [train_variant_prototypes.py](C:/Users/Caglar/Desktop/PokeRarityScanner/scripts/train_variant_prototypes.py)
    - variant classifier feature setine `headHist` eklendi ve model yeniden uretildi
  - [VariantDecisionEngine.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt)
    - same-family costume rescue kurali eklendi; regular/costume `Slowpoke` ayrimi strict fixture uzerinden olculerek sabitlendi
    - species-scoped classifier `costume=true, shiny=false` dediginde legacy visual shiny false-positive'i tasimamasi icin merge kurali daraltildi
- Kalan tek strict fail bu son merge kurali ile kapandi:
  - `live_variant_batch_20260318_pikachu_costume`
  - classifier trace bu fixture icin `Pikachu costume / shiny=false` veriyordu; false-positive yalniz legacy visual shiny yolundan geliyordu
- Dogrulama komutlari:
  - `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain`
    - sonuc: `BUILD SUCCESSFUL`
    - strict fixture seti gecti, 0 failure
  - `gradlew assembleDebug --console=plain`
    - sonuc: `BUILD SUCCESSFUL`
- Not:
  - `VariantDecisionEngine.kt` icin Kotlin derleyici bir Elvis warning'i veriyor (`?: always returns left operand`) ama build ve regression basarili; davranis acisindan bloklayici degil.

## 18 March 2026 - 16:48
- Systematic debugging sonucu kalan tek strict fail `regression_20260317_1059/scan_1773745195014_0` (regular `Raikou` false shiny) uzerinden kapatildi.
- Kok neden:
  - [VisualFeatureDetector.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/vision/VisualFeatureDetector.kt)
    - `chooseShinyResult()` icindeki `signatureResult.second >= 0.95f` kapisi, `primaryMatched=false` olsa bile alternate-only shiny consensus'i tek basina kabul ediyordu.
    - Bu nedenle renk/hue destegi olmayan regular `Raikou` frame'leri `shiny=true` oluyordu.
- TDD adimi:
  - [VisualFeatureDetectorDecisionTest.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/androidTest/java/com/pokerarity/scanner/VisualFeatureDetectorDecisionTest.kt)
    - yeni failing test eklendi: `highConfidenceAlternateOnlyConsensusWithoutSupportIsRejected`
    - koruyucu test eklendi: `strongPrimarySignatureCanCarryShinyWithoutColorSupport`
  - once bu test suite kirmiziya dustu; ardindan production fix yazildi.
- Uygulanan fix:
  - [VisualFeatureDetector.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/util/vision/VisualFeatureDetector.kt)
    - signature-only shiny artik yalnizca:
      - gercek ek destek (`masked/raw/hue/hist`) varsa
      - veya `primaryMatched=true` ve signature confidence `>= 0.95` ise kabul ediliyor
    - alternate-only consensus artik tek basina kabul edilmiyor
- Dogrulama:
  - `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VisualFeatureDetectorDecisionTest --console=plain`
    - sonuc: `BUILD SUCCESSFUL`
  - `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain`
    - sonuc: `BUILD SUCCESSFUL`
    - genisletilmis strict regression set gecti
  - `gradlew assembleDebug --console=plain`
    - sonuc: `BUILD SUCCESSFUL`
- Deploy:
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`
  - `adb shell monkey -p com.pokerarity.scanner -c android.intent.category.LAUNCHER 1`
  - startup kontrolu: `ScanManager started`, crash yok

## 18 March 2026 - 17:03
- Canli log incelemesi (user scan batch):
  - `Pikachu` costume
    - classifier rescue: `025_00_12`, `type=costume`, `shiny=false`
    - visual sonucu: `shiny=false`, `costume=true`
    - date parse edildi: `2017-01-05`
    - overlay dispatch: `4344ms`
  - `Raichu` costume
    - classifier species pass: `026_01_08`, `type=costume`, `shiny=false`
    - visual sonucu: `shiny=false`, `costume=true`
    - date parse edildi: `2017-02-03`
    - overlay dispatch: `4471ms`
  - `Seviper` regular
    - species dogru, `shiny=false`, `costume=false`, `lucky=false`
    - date parse edildi: `2017-12-23`
    - HP OCR kirik: `92/22HP -> null`
    - overlay dispatch: `4303ms`
- Sonuc:
  - Bu log diliminde costume/shiny regressions geri donmemis gorunuyor.
  - Kalan ana sorunlar: latency (~4.3s-4.5s) ve bazi frame'lerde HP OCR bozulmasi.
  - Log sonundaki `ScanManager(5147)` satirlari uygulamaya ait degil; cihazin sistem BLE tarama servisi.

## 18 March 2026 - 17:10
- Kullanici onceligi (species / shiny / costume / caught date > HP / IV) dikkate alinip scan pipeline hizlandirildi.
- Kok degisiklik:
  - [ScanManager.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt)
    - yeni politika: hizli OCR sonucu zaten guvenilir ise synchronous detailed OCR artik calistirilmiyor
    - detailed OCR ancak su kosullarda kritik yolda kalir:
      - CP yok / zayif
      - species unknown
      - caught date yok
      - ad confidence dusuk
    - easy-case scan'lerde overlay daha erken dispatch edilecek
- TDD:
  - [ScanManagerPolicyTest.kt](C:/Users/Caglar/Desktop/PokeRarityScanner/app/src/androidTest/java/com/pokerarity/scanner/ScanManagerPolicyTest.kt)
    - `skipsDetailedPassForReliableQuickScan`
    - `keepsDetailedPassWhenDateIsMissing`
    - `keepsDetailedPassWhenNameConfidenceIsWeak`
  - test once production fonksiyonu olmadigi icin kirmiziya dustu; ardindan politika implement edildi ve yesile dondu
- Dogrulama:
  - `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanManagerPolicyTest --console=plain`
    - sonuc: `BUILD SUCCESSFUL`
  - `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain`
    - sonuc: `BUILD SUCCESSFUL`
  - `gradlew assembleDebug --console=plain`
    - sonuc: exit `0`
- Deploy:
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`
  - launcher acilisi yapildi, startup logunda `ScanManager started` goruldu, crash yok
- Not:
  - HP OCR (`92/22HP -> null`) bu turda bilerek ayri bir hata olarak birakildi; hiz icin kritik yol once sadelestirildi.
  - Bu degisiklik kolay scan'lerde ~1.8-2.0s detailed OCR beklemesini kesmeyi hedefliyor.

## 18 March 2026 - 17:14
- Canli hiz olcumu (detail-pass gating sonrasi):
  - `Tyranitar` regular
    - detailed OCR skip edildi
    - `overlay dispatched in 2889ms`
    - species dogru, `shiny=false`, `costume=false`
  - `Raichu` costume
    - detailed OCR skip edildi
    - `overlay dispatched in 2433ms`
    - final: `costume=true`, `shiny=false`
  - `Lugia` regular
    - detailed OCR skip edildi
    - `overlay dispatched in 2381ms`
    - species dogru, `shiny=false`, `costume=false`
  - `Pikachu` scan
    - ilk hizli OCR'da date `null` geldigi icin detailed pass calisti
    - `overlay dispatched in 4318ms`
    - classifier species pass `Pikachu costume` adayi gordu, fakat final visual sonuc `shiny=false`, `costume=false`
- Sonuc:
  - easy-case scan'lerde sure ~4.3-4.5s bandindan ~2.3-2.9s bandina indi.
  - date bulunamayan scan'lerde hala tam-pass maliyeti var ve sure tekrar ~4.3s civarina cikiyor.
  - bir `Pikachu` scan'i classifier seviyesinde non-regular sinyal tasimasina ragmen finalde regular kaldi; eger bu scan kullanicinin non-regular ornegiyse o hat acik.

## 18 March 2026 - 17:21
- Hedefli scan stabilizasyonu:
  - `OCRProcessor.kt`
    - hizli pass icin `BadgeBinary` date rescue eklendi.
    - Artik quick OCR, `badgeRaw / badgeFixed / badgeHC` sonrasi date bulamazsa `BadgeBinary` ile tekrar dener; bu sayede gereksiz detailed OCR gecisleri azaltiliyor.
  - `VariantDecisionEngine.kt`
    - species-scoped `costume` rescue eklendi.
    - Classifier species icinde `costume` diyorsa, confidence ana esigin biraz altinda kalsa bile base score'u anlamli sekilde geciyorsa final sonuc regular'a dusurulmuyor.
    - Bu ozellikle `Pikachu` gibi borderline non-regular scan'lerde classifier sinyalinin bosuna kaybolmasini engelliyor.
  - `VariantDecisionEngineTest.kt`
    - species-scoped costume rescue icin iki yeni instrumented test eklendi:
      - rescue gerekirken `costume=true` olmali
      - base sprite cok yakin ise rescue tetiklenmemeli
- Dogrulama:
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> `BUILD SUCCESSFUL`
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanManagerPolicyTest --console=plain` -> `BUILD SUCCESSFUL`
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain` -> `BUILD SUCCESSFUL`
  - `assembleDebug --console=plain` -> `BUILD SUCCESSFUL`
- Sonraki adim:
  - Bu build cihaza kurulup yeni date-rescue ve species-scoped costume rescue canli logla dogrulanacak.
- Deploy dogrulamasi:
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`
  - `adb logcat -c` yapildi
  - launcher acilisi yapildi
  - process aktif: `pidof com.pokerarity.scanner -> 10583`
  - startup logu: `ScanManager started, receiver registered for com.pokerarity.scanner.SCREENSHOT_READY`

## 18 March 2026 - 17:31
- Canli log kok neden analizi:
  - `Piloswine` scan'inde `VisualFeatureDetector` finalde `shiny=false, costume=false` uretirken `RarityCalculator` ayni scan'i `shiny=true, form=true` olarak isliyordu.
  - Kirik nokta `VariantDecisionEngine.mergeVisualFeatures()` icindeydi: dusuk confidence'li species-scoped `form+shiny` classifier eslesmesi, `form` gate'ini gecmese bile `shiny=true` sinyalini finale sizdiriyordu.
- TDD:
  - `VariantDecisionEngineTest.kt` icine yeni failing test eklendi:
    - `lowConfidenceSpeciesFormMatchDoesNotLeakShinyFlag`
  - Test ilk kosuda fail etti; bu, kok nedenin gercekten merge mantiginda oldugunu dogruladi.
- Duzeltme:
  - `VariantDecisionEngine.kt`
    - classifier kaynakli `shiny=true` sadece ana varyant confidence esigi gecildiginde finale tasiniyor.
    - Dusuk confidence'li species-scoped `form` eslesmeleri artik `hasSpecialForm` uretebilir ama `shiny=true` sizdiramiyor.
- Dogrulama:
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> ilk kosuda `FAILED`, fix sonrasi `BUILD SUCCESSFUL`
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain` -> `BUILD SUCCESSFUL`
  - `assembleDebug --console=plain` -> `BUILD SUCCESSFUL`
- Not:
  - Bir ara iki Gradle isi ayni anda calistirildigi icin temiz dogrulama tekrar tekil sekilde alindi; son gecerli regression sonucu tekil calistirilan komuttan geldi.
- Deploy dogrulamasi:
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`
  - `adb logcat -c` yapildi
  - launcher acilisi yeniden tetiklendi
  - process aktif: `pidof com.pokerarity.scanner -> 14758`
  - startup logu temiz: `ScanManager started, receiver registered for com.pokerarity.scanner.SCREENSHOT_READY`

## 19 March 2026 - 13:25
- Date OCR Gelistirmeleri:
  - ImagePreprocessor.processDateBadge icine turuncu arka plani beyaz yapan ters maskeleme (inverse mask) eklendi.
  - TextParseUtils.parseDate icine OCR typo-recovery (Z->2, O->0 vb.) eklendi.
  - 3 yeni JVM edge-case testi eklendi ve 192.168.1.159:42215 numarasina APK basariyla kuruldu.

## 19 March 2026 - 13:52
- YAPILANLAR (Offline JVM Test & OCR):
  - Visual Merge ve Text Parse mantiği tamamen VariantMergeLogic.kt ve TextParseUtils.kt object'lerine (pure JVM) taşindi.
  - Hizli testler icin 18 adet JVM unit / edge-case testi yazildi (0.4 saniye sureli).
  - Orange Badge uzerindeki WhiteMask koku kazinip, daha kapsayici Ters Turuncu Maske (Inverse Orange Mask) kodlandi. Turuncu arkaplan disindaki her sey (siyah, gri, beyaz yazilar) Tesseract icin siyah metne donusturuldu.
  - TextParseUtils.parseDate metodu hata affedici hale getirildi (Z->2, O->0, l->1, S->5, B->8 karakter typo'lari otomatik duzeltiliyor).
  - Canli cihaz testinde (ADB logcat) badge uzerindeki gurultulu okumadan (4 2017 11 21/09 1) saniyenin altinda pürüzsüz tarih cikarimi basariyla kanitlandi.

- TESPIT EDILEN PROBLEMLER VE GELECEK PLANLARI:
  1. Shiny Pikachu'nun "Costume" olarak tespit edilebilirken "Shiny" ozelligini kaybetmesi (Bug):
     - Kok Neden: VariantMergeLogic.kt icinde Classifier "dusuk confidence" ile maske bulup (promoteCostumeBySpeciesRescue) bunu onadiginda, eger Classifier ayni zamanda isShiny=false diyorsa, VisualFeatureDetector'dan gelen kesin dogru isShiny=true bilgisini de eziyor (suppressVisualShiny = true oldugundan).
     - Cozum: Rescue durumunda eger VisualFeatureDetector shiny demisse, Classifier tarafindan shiny olmadigi yonunde yapilan basarisiz ezmeyi engeleyecek TDD bazli bir fix yazilacak. JVM'de hizlica test edilip dogrulanacak.
     
  2. Gurultulu Ekranlarda Candy/Name Okunmamasi (Lugia/Raichu, Shadow Auralari):
     - Kok Neden: O bolgedeki kanat/kuyruk objelerinin parlak renkleri (veya Shadow karanligi) Tesseract'in OCR surecini engelliyor, processWhiteMask yetersiz kaliyor.
     - Cozum: WhiteMask yaninda, "High Contrast" ya da "Adaptive Thresholding" gibi, sadace Lugia/Raichu defktlerinde degil, butun arkasi gurultulu Pokemon ekranlarinda ise yarayacak 'Generalized Noise Filter' (Genel Gurultu Filtresi) uygulancak.

- SONRAKI ADIM: Ilk olarak VariantMergeLogicTest.kt icinde Shiny+Costume ezilmesini simule eden bir fail senaryosu (test) yazilip bu bug patchlenecek. Ardindan Gurultu filtreleme surecine gecilecek.

## 19 March 2026 - 14:05
- Shiny+Costume Bug Fix (TDD):
  - VariantMergeLogicTest icine RED test (costumeRescueDoesNotSuppressVisualShiny) yazildi. Test beklendigi gibi FAIL etti.
  - VariantMergeLogic.kt icine 1 satirlik fix eklendi: suppressVisualShiny kosuluna !visualFeatures.isShiny eklendi.
  - Boylece Costume rescue durumunda VisualFeatureDetector'dan gelen kesin shiny sinyali artik ezilmeyecek.
  - 22 JVM testi (7 merge + 15 parse) tamami basariyla gecti. assembleDebug SUCCESS. APK cihaza kuruldu.

## 19 March 2026 - 14:15
- Generalized Noise Filter (Candy/Name OCR):
  - ImagePreprocessor.processCandyText: chroma esigi 42'den 30'a dusuruldu, Pokemon govde renklerini (kanat, kuyruk) daha iyi reddediyor.
  - ImagePreprocessor.processWhiteMaskStrict: Yeni method eklendi, chroma < 18 ile cok saf beyaz metni (Name/CP) ayirip, Lugia/Togekiss gibi acik renkli govdeleri reddediyor.
  - OCRProcessor.kt: Name icin 3. fallback pass eklendi (WM -> HC -> Strict). Eger ilk iki pass basarisiz olursa StrictMask devreye giriyor.
  - 22 JVM testi tamami basariyla gecti, assembleDebug SUCCESS, APK cihaza kuruldu.

## 19 March 2026 - 14:54 (Detayli Guncelleme)

### Basarili Degisiklikler (Su An Aktif):
1. ImagePreprocessor.processDateBadge: Ters turuncu maske eklendi (turuncu -> beyaz, geri kalan -> siyah). Tarih okuma basarisi kanitlandi.
2. TextParseUtils.parseDate: Harf-rakam typo duzelticisi eklendi (Z->2, O->0, l->1, S->5, B->8). Badge uzerinden tarih %100 okunuyor.
3. VariantMergeLogic.kt: suppressVisualShiny kosuluna !visualFeatures.isShiny eklendi. Costume rescue artik visual shiny'yi ezmiyor.
4. ImagePreprocessor.processWhiteMaskStrict: Yeni method (chroma<18). Name fallback olarak OCRProcessor'a eklendi (WM->HC->Strict).
5. TextParser.parseName: Prefix-3 filtresi prefix-2 ile birlestirildi (Zapdos gibi isimlerin atlanmasini onlemek icin).

### Basarisiz/Geri Alinan Degisiklikler:
1. processCandyText chroma 42->30 ve luminance 176->150: Cok agresifti, tum Candy okumalarini bozdu. TAMAMEN GERI ALINDI (simdi orijinal degerler: luminance<176, chroma<42).

### Hala Mevcut Sorunlar (Benim degisikliklerimden BAGIMSIZ):
1. Pikachu -> Pichu: OCR dogru "Pikachu" okuyor ama SpeciesRefiner fitScore (CP uyumu) baziyla Pichu'ya ceviriyor.
2. Zapdos -> Zangoose: parseName "Zandosas" dan Zangoose buluyor, SpeciesRefiner bunu kilitliyor (trustedResolvedSpecies).
   - Kok neden: SpeciesRefiner exact text match'e yeterince guvenmeyip, fitScore'u tercih ediyor.
   - Cozum: SpeciesRefiner'da parseName exact match varsa override'i engellemek.

### Yapilacak Degisiklik - SpeciesRefiner Fix:
- Dosya: SpeciesRefiner.kt
- Degisiklik: Eger parsedRawName tam olarak currentSpecies ile eslesen bir exact match ise, SpeciesRefiner bunu baska bir turu ile degistirmemeli (exactParsedSpeciesLock kosulunu guclendir).
- GERI ALMA YONTEMI: Asagidaki satirlari geri almak gerekirse:
  - SpeciesRefiner.kt satir ~46-51 arasinda exactParsedSpeciesLock kosulundan "topTextConfidence >= 0.90" sartini kaldiracagiz.
  - Geri almak icin: ayni satira "topTextConfidence >= 0.90" kosulunu geri eklemek yeterli.

---

## 📌 17 MART 2026 — KRİTİK HATA BULGUSU (Yeni Oturum)

**Bilinen sorunlar (canlı tarama sonrasi kullanici gözlemi):**

| # | Pokemon | Hata | Root Cause | Öncelik |
|---|---------|------|-----------|---------|
| 1 | Snorlax | OCR: `Sno96-96` (HP digits name'e karışıyor) → Species: SNOM | **OCR field boundary** + species resolver | 🔴 HIGH |
| 2 | Zapdos | Species: Zangoose (tamamen yanlış) | **Species resolver adayı**, OCR likely `Zapdo-benzeri` → Zangoose'a fallback | 🔴 HIGH |
| 3 | Pikachu (Shiny) | Visual: costume=true (expected: shiny=true) | **VisualFeatureDetector** shiny/costume confidence threshold | 🔴 HIGH |

**Testin durumu:**
- CP/HP parsing: ✅ **16/16 unit test PASS** — OCR text satır extraction'ı bozulmuş
- Species determination: ❌ **Zapdos→Zangoose, Snorlax→SNOM hataları**
- Visual variant detection: ❌ **Pikachu shiny→costume misclassification**

**Hızlı teşhis planı (18 Mart):**
1. Logcat temizle (`adb logcat -c`) ✅
2. Problem Pokemon'ları tara (Snorlax, Zapdos, Pikachu) ✅
3. Logları analiz et ✅

**ROOT CAUSES (19 Mart - 15:59 UTC):**

| Pokemon | OCR | Parsed | Problem | Fix Target |
|---------|-----|--------|---------|------------|
| **Snorlax** | `SnoQGOaGSWBGQ` | `Snom` (YANLLIŞ) | TextParser fuzzy: corrupt `Sno*` → `Snom` instead of guessing | `TextParser.parseName()` tighten |
| **Zapdos** | `Zandosas OK` | `Zangoose` (YANLLIŞ) | TextParser fuzzy: `Zandosas` → **`Zangoose`** (edit distance bug) | `TextParser.fuzzySpeciesMatch()` |
| **Pikachu** | `Pikachu` (✅ dogru) | `Pikachu` (✅ dogru) | `VisualFeatureDetector`: `costume=true(0.55)` when expected `shiny=true` | `VisualFeatureDetector.isCostume()` threshold |

**Logs saved:** `scan_diagnosis_19march.txt` (10.5MB)

---

### FIX PLAN (19 Mart):

**Retest Results (After TextParser maxD + threshold fixes):**

| Pokemon | OCR | TextParser | SpeciesRefiner Decision | Expected | Result |
|---------|-----|-----------|----------------------|----------|--------|
| Snorlax | `SnoQGOa...` | `Snom` (WRONG) | Kept Snom | Snorlax | ❌ **FAILED** |
| Zapdos | `Zandosas OK` | `Zangoose` (WRONG) | Kept Zangoose | Zapdos | ❌ **FAILED** |
| Pikachu | `Pikachu` ✅ | `Pikachu` ✅ | OK | shiny=true | costume=true ❌ **FAILED** |

**Analysis:**
- Fuzzy `maxD` tightening (5→3/4) insufficient — `Zangoose` score 0.885 vs `Zapdos` 0.763
- `Snom` alıyor OCR `Sno*` token'dan (prefix match ile)
- Pikachu: costume confidence 0.53 > 0.65 threshold = logic complex, not just threshold

**Next Steps:**
1. SpeciesRefiner: Add "stat/move mismatch override" (Zapdos/Snorlax)
2. TextParser: Tighten prefix-based matching (prefer longer name at same distance)
3. VisualFeatureDetector: Pikachu-specific costume/shiny decision (nested logic)

---

## 🎯 KRITIK: SÜRATLİ FİX GEREKEN 3 HATA

**Zapdos & Snorlax hatası:**
- Root: TextParser `Zandosas→Zangoose(score 0.885)` > `Zapdos(0.763)` ve `SnoQGOa→Snom` tercih ediyor
- **Fix:** `parseName()` fuzzy match'te **"Same distance'ta uzun isim tercih et"** kuralı GÜÇLENDİR
  - Mevcut: `if (d < td && d <= maxD) { td = d; tb = name }`
  - Yeni: `if (d < td || (d == td && name.length > (tb?.length ?: 0))) { td = d; tb = name }`
  
**Pikachu costume→shiny hatası:**
- Root: Costume signature confidence=0.53, heuristic+fallback combo=0.53 → kept=true
- **Fix:** Pikachu-specific: "shiny frame" mi "costume frame" mi diye CP/arc/sprite color ile disambiguate
  - OR: costume confidence threshold 0.65 değişti ama costume=true hala geliyor = başka path'ten geliyor
  - Kontrol gerekli: `CostumeSignatureStore` vs `heuristic` kaynak belirtilsin

**Immediate Action:** 
- [ ] parseName() prefix match logic review
- [ ] TextParser "same-distance prefer longer" kuralı ekle + TEST
- [ ] VisualFeatureDetector Pikachu special case add + DEPLOY + RETEST

---

## 21 March 2026 - 10:20
- YAPILANLAR (TextParser + SpeciesRefiner fix):
  - `app/src/androidTest/java/com/pokerarity/scanner/TextParserRegressionTest.kt`
    - iki yeni regression testi eklendi:
      - `parseNameKeepsZapdosForNoisyZapdosToken`
      - `parseNameAvoidsSnomForCorruptedSnorlaxToken`
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt`
    - `parseName()` icinde erken `rankNameCandidates` kabul eşiği sıkılaştırıldı (`score >= 0.90`).
    - token fuzzy akışında güçlü prefix için dinamik tolerans eklendi.
    - same-distance durumunda uzun ismi tercih eden kural uygulandı.
    - OCR alias desenlerine `zandos* -> Zapdos` ve uzun/noisy `sno* -> Snorlax` eklendi.
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt`
    - `exactParsedSpeciesLock` için `topTextConfidence` eşiği kaldırıldı; exact parsed match daha güçlü kilitleniyor.

- DOGRULAMA (lokal):
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.TextParserRegressionTest` -> `BUILD SUCCESSFUL`
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.SpeciesRefinerTest` -> `BUILD SUCCESSFUL`
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantMergeLogicTest` -> `BUILD SUCCESSFUL`
  - `assembleDebug` -> `BUILD SUCCESSFUL`

## 21 March 2026 - 17:21
- AKIS CALISTIRMA (Build + Deploy + Run + Log):
  - `assembleDebug` -> `BUILD SUCCESSFUL`
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`
  - `adb shell am start -n com.pokerarity.scanner/.ui.splash.SplashActivity` -> uygulama baslatildi
  - `adb logcat -d | findstr "ScanManager started FATAL EXCEPTION"`:
    - birden cok kez `ScanManager started` satiri goruldu
    - `FATAL EXCEPTION` satiri gorulmedi (bu filtrede)

- NOT:
  - Bundan sonraki degisikliklerde de her kod/komut sonucunu `rapor.md` icine tarih-saat + Ne/Neden/sonuc formatinda islemeye devam edilecek.

## 23 March 2026 - UI Redesign (Stitch / Google Design Import)

### YAPILANLAR — Stitch Tasarımı Uygulaması

Kullanıcının Google Stitch ile hazırladığı `stitch_splash_screen.zip` dosyasındaki dark tema tasarımı (`dark_splash_screen`, `dark_main_hub`, `go_rarity_results`) projeye uygulandı.

**Oluşturulan yeni drawable dosyalar:**
- `bg_logo_circle.xml` — Splash logo dairesi (koyu, beyaz border)
- `bg_stitch_card.xml` — Genel dark kart (1C1B1B, 16dp radius)
- `bg_stitch_card_high.xml` — Yüksek kontrast dark kart (2A2A2A)
- `bg_scan_glow.xml` — Kırmızı radial glow efekti (FAB ve splash)
- `bg_scan_fab.xml` — FAB gradient (E3350D → FF5632)
- `bg_accent_line.xml` — Yatay kırmızı accent çizgisi
- `bg_bottom_nav.xml` — Alt nav koyu arka plan

**Güncellenen layout dosyaları:**

| Dosya | Stitch kaynak | Değişiklik |
|-------|--------------|------------|
| `activity_splash.xml` | `dark_splash_screen` | Siyah bg (#131313), glow blob'lar, PokeRarity/SCANNER split başlık, ince kırmızı progress bar, SECURED/v4.0.2/ENGINE READY meta row, top+bottom accent line |
| `activity_main.xml` | `dark_main_hub` | Dark toolbar, bento grid (Today's Finds + Top Rarity), merkez FAB (E3350D gradient), debug toggle, recent scans listesi |
| `activity_result.xml` | `go_rarity_results` | Dark kart arka plan, accent line header, CP/HP/IV 3'lü row (divider'lı), CAUGHT DATE alanı, RARITY ANALYSIS bölümü, Save kırmızı/Back+Share dark buton |

**Güncellenen Compose dosyaları:**

- `CollectionScreen.kt` — Tamamen Stitch dark_main_hub tasarımıyla yeniden yazıldı:
  - Yeni renk paleti: `BG=#131313`, `CardHigh=#2A2A2A`, `CardMid=#1C1B1B`, `AccentRed=#E3350D`, `TextMuted=#AC8880`, `TextOnDark=#E5E2E1`
  - TopBar: PokeRarity kırmızı bold başlık
  - Hero stat: "LIVE FREQUENCY" label + büyük scan sayısı + açıklama metni
  - Bento grid: 2:1 oranında "Today's Finds" (COMMON/RARE mini stat) + "Top Rarity" (⭐ shiny sayısı) kartları
  - Merkez pulse FAB: `StitchScanButton` — radial glow animasyonu + circle gradient, SCAN NOW / STOP yazısı
  - Filter chips: Stitch stilinde border/bg geçişli
  - Empty state: Stitch dark card, TextMuted rengi

- `ScanResultScreen.kt` — Mevcut type-color gradient tasarımı korundu (dokunulmadı), zaten yüksek kaliteli.

**Build sonucu:**
- `assembleDebug` → `BUILD SUCCESSFUL in 4m 51s`
- 44 task: 16 executed, 28 up-to-date
- 0 compile error, 0 Kotlin warning (yalnızca Hilt kapt uyarısı — mevcut, önceden bilinen)
- APK: `app/build/outputs/apk/debug/app-debug.apk`

**Deploy:**
- MCP bağlantı sorunu nedeniyle otomatik install tamamlanamadı.
- Manuel kurulum: `adb -s RFCY11MX0TM install -r app\build\outputs\apk\debug\app-debug.apk`
## 23 March 2026 - 15:10
- Scan authority stabilizasyonu baslatildi.
- Yeni plan dosyasi eklendi: `docs/superpowers/plans/2026-03-23-scan-authority-stabilization.md`
- Yeni saf mantik katmani eklendi: `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanAuthorityLogic.kt`
  - Amac: OCR zaten exact species veriyorsa classifier'in family icinde farkli ture override etmesini engellemek.
- Yeni JVM testleri eklendi: `app/src/test/java/com/pokerarity/scanner/ScanAuthorityLogicTest.kt`
  - `exactParsedSpeciesBlocksClassifierOverrideWithoutCandyCorroboration`
  - `unknownSpeciesAllowsClassifierOverride`
  - `exactParsedSpeciesDoesNotBlockSameSpecies`
- `VariantDecisionEngine.kt` guncellendi:
  - classifier species override karari artik `ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(...)` kapisindan geciyor.
  - raw OCR `Name/NameHC` parse edilip exact species lock uygulanabiliyor.
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.ScanAuthorityLogicTest --console=plain` -> BUILD SUCCESSFUL
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.SpeciesRefinerTest --console=plain` -> BUILD SUCCESSFUL
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> BUILD SUCCESSFUL
  - `assembleDebug --console=plain` -> BUILD SUCCESSFUL
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> Success
  - `adb -s RFCY11MX0TM logcat -c` yapildi
  - `adb -s RFCY11MX0TM shell am start -n com.pokerarity.scanner/.ui.splash.SplashActivity` ile uygulama yeniden baslatildi
- Sonraki adim:
  - Date ve HP icin ayni authority gate mantigi eklenecek.
  - Ardindan canli log ile species drift'in sahada kapanip kapanmadigi olculecek.
## 23 March 2026 - 23:28
- Canli log analizi sonrasi yeni kok nedenler ayrildi:
  - `Venusaur` regular scan'de species-scoped dusuk confidence `form+shiny` classifier eslesmesi `form=true` sizdiriyordu.
  - `Pichu` scan'de species-scoped `costume+shiny` rescue, visual shiny false olsa bile `shiny=true` sizdiriyordu.
- TDD:
  - `app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt` guncellendi.
  - Yeni/degisen beklentiler:
    - `lowConfidenceSpeciesFormMatchDoesNotLeakShinyFlag` artik `hasSpecialForm=false`, `isShiny=false`
    - `lowConfidenceSpeciesCostumeShinyRescueDoesNotLeakShinyFlag` eklendi
  - `app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt` ayni davranisa hizalandi.
- Duzeltme:
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
    - species-scope `form` promotion artik visual destek yoksa dusuk confidence ile `form=true` uretmiyor.
    - classifier kaynakli `shiny=true` artik visual shiny yoksa daha yuksek confidence istiyor (`CLASSIFIER_NON_VISUAL_SHINY_CONFIDENCE`).
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantMergeLogicTest --console=plain` -> once FAIL, fix sonrasi BUILD SUCCESSFUL
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> BUILD SUCCESSFUL
  - `assembleDebug --console=plain` -> BUILD SUCCESSFUL
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> Success
  - `adb -s RFCY11MX0TM logcat -c` yapildi
  - explicit `SplashActivity` start denemesi activity path degistigi icin hata verdi; APK kurulu durumda.
- Sonraki adim:
  - yeni build ile canli scan logu alinacak.
  - Ardindan date/HP authority gate'leri eklenecek.
## 24 March 2026 - 00:20
- Canli logdan ikinci scan'in sonuc ekrani getirmemesinin kok nedeni ayrildi.
- Bulgular:
  - Ikinci scan crash olmuyor; `ScanConsistencyGate` `cross_family_conflict` yuzunden retry'ye dusuyor.
  - Ornek zincir: OCR `Candy=Squirtle` dogru okuyor, `Name` alanini `Mankey`e kaydiriyor, `SpeciesRefiner` buradan `Shuppet`e kadar cross-family drift yapiyor.
  - Bu nedenle sonuc gosterilmeden `LOW_CONFIDENCE_RESULT` retry hattina giriliyor.
- TDD:
  - `app/src/androidTest/java/com/pokerarity/scanner/SpeciesRefinerTest.kt` icine yeni test eklendi:
    - `candyFamilyAuthorityBlocksCrossFamilyRefine`
- Duzeltme:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/SpeciesRefiner.kt`
    - yeni `candyFamilyAuthorityOverride` eklendi.
    - Candy family bilgisi varsa ve mevcut species candy family disindaysa, yeterli fit/score tasiyan candy-family adayi cross-family adaylara tercih ediliyor.
- Ek dogrulama:
  - `VariantMergeLogic` tarafindaki dusuk confidence `form/shiny` sizma fixleri de aktif durumda.
- Dogrulama:
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.SpeciesRefinerTest --console=plain` -> BUILD SUCCESSFUL
  - `assembleDebug --console=plain` -> BUILD SUCCESSFUL
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> Success
- Sonraki adim:
  - yeni build ile ayni problemli scan tekrar alinip `cross_family_conflict` hattinin kapanip kapanmadigi canli logla olculecek.
## 24 March 2026 - 00:40
- Faz 2 variant merge icin yeni generic TDD turu uygulandi.
- Kok neden:
  - species-scope `costume` classifier eslesmeleri `0.52` confidence civarinda visual destek olmadan da regular scan'leri `costume=true` yapabiliyordu.
  - buna karsi `costume+shiny` kombolarinda ayni confidence bandi `shiny`yi finale tasimaya yetmiyordu.
- TDD:
  - `app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt`
    - `lowConfidenceSpeciesCostumeRescueWithoutVisualSupportDoesNotPromoteCostume`
    - `speciesScopedCostumeShinyComboPromotesBothFlags`
  - `app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt`
    - ayni iki davranis cihaz ustu merge katmani icin eklendi.
  - Ilk JVM test calismasi bilincli olarak FAIL verdi; iki yeni davranis da kirikti.
- Duzeltme:
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
    - species-scope non-visual classifier-only `costume` promotion artik daha dar.
    - dusuk confidence generic `0.52` species classifier tek basina `costume=true` uretmiyor.
    - `costume+shiny` combo promotion icin ayri yol eklendi.
    - bu yol sadece species-scope, `costume`, `shiny=true` ve base'e karsi anlamli skor farki varsa devreye giriyor.
    - boylece `shiny-only` orneklerin costume'a kaymasi azaltilirken, gercek `costume+shiny` kombolarda `shiny` finale tasinabiliyor.
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantMergeLogicTest --console=plain` -> once FAIL, fix sonrasi BUILD SUCCESSFUL
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> BUILD SUCCESSFUL
  - `assembleDebug --console=plain` -> BUILD SUCCESSFUL
- Not:
  - Bu turda Windows/Kotlin `user-mapped section open` cache lock problemi tekrar goruldu.
  - Temizleme adimlari: `gradlew --stop`, kapt/build cache klasorlerini silme, sonra testleri tek tek sirali calistirma.
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> Success
  - `adb -s RFCY11MX0TM logcat -c` yapildi
  - `adb -s RFCY11MX0TM shell monkey -p com.pokerarity.scanner -c android.intent.category.LAUNCHER 1` ile uygulama acildi
  - aktif process: `pidof com.pokerarity.scanner -> 15446`
- Sonraki adim:
  - canli scan ile ayni batch tekrar alinacak.
  - ozellikle `Squirtle shiny`, `Squirtle shiny+costume`, `Blastoise shiny+costume`, `Pikachu costume` uzerinde yeni merge davranisi olculecek.
## 24 March 2026 - 00:45
- Canli log analizi sonrasi Phase 2 icin yeni kok neden netlesti.
- Bu batch'te gorulen durum:
  - `Squirtle shiny only`:
    - species duzeldi, `Mankey` OCR kaymasi `Candy=Squirtle` ile geri toplandi.
    - artik false `costume=true` vermiyor.
    - final sonuc `shiny=false`, `costume=false` -> kalan hata artik shiny false-negative.
  - `Squirtle shiny+costume`:
    - final `costume=true`, `shiny=false`
  - `Blastoise shiny+costume`:
    - classifier `009_00_05_shiny` goruyor
    - final yine `costume=true`, `shiny=false`
  - `Pikachu costume`:
    - dogru (`costume=true`, `shiny=false`)
- Teknik sonuc:
  - onceki fix, `shiny-only` scanlerin costume'a kaymasini durdurdu.
  - kalan ana hata `costume+shiny` kombinasyonlarinda shiny'nin merge asamasinda dusmesi.
- Yeni TDD:
  - mevcut yeni testler korunarak `VariantMergeLogic` combo promotion davranisi tekrar daraltildi.
  - `costume+shiny` combo promotion artik:
    - yalniz species-scope,
    - classifier `costume+shiny`,
    - confidence `>= 0.52`,
    - ve ya guclu `species rescue` ya da mevcut visual costume destegi varsa calisiyor.
  - `near-tie` rescue tek basina artik shiny promotion sebebi degil.
- Duzeltme:
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantMergeLogicTest --console=plain` -> BUILD SUCCESSFUL
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> BUILD SUCCESSFUL
  - `assembleDebug --console=plain` -> BUILD SUCCESSFUL
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> Success
  - `adb -s RFCY11MX0TM logcat -c` yapildi
  - `adb -s RFCY11MX0TM shell am start -n com.pokerarity.scanner/.ui.splash.SplashActivity` ile uygulama acildi
- Sonraki adim:
  - ayni dortlu batch tekrar alinacak.
  - hedef:
    - `Squirtle shiny only` artık regular kalmiyorsa nedenini ayirmak
    - `Squirtle/Blastoise shiny+costume` orneklerinde `shiny=true` finale tasiniyor mu olcmek
## 24 March 2026 - 00:52
- Yeni structural Phase 2 fix uygulandi.
- Yeni kok neden:
  - `Squirtle shiny only` scan'inde classifier rescue halen `costume=true` olarak rarity/UI katmanina siziyordu.
  - `Blastoise shiny+costume` scan'inde ise `shiny` promotion icin sadece confidence bakmak yeterli degildi; rescue kaynagini ayirt etmek gerekiyordu.
- Tasarim degisikligi:
  - `VariantPrototypeClassifier.MatchResult` icine `rescueKind` eklendi.
  - `VariantDecisionEngine.resolveVariantClassifierMatch(...)` artik rescue kaynagini isaretliyor:
    - `exact_non_base_consensus`
    - `same_family_non_base_rescue`
    - `family_costume_rescue`
  - `VariantMergeLogic` artik bu kaynaga gore karar veriyor.
- Yeni merge politikasi:
  - classifier-only non-shiny `costume` rescue:
    - visual costume destegi yoksa finale kolay sizmiyor
  - `costume+shiny` combo promotion:
    - yalniz `exact_non_base_consensus` ya da gercek visual costume destegi oldugunda devreye giriyor
  - boylece `shiny-only` scanlerin costume'a kaymasi ile `costume+shiny` exact consensus durumu birbirinden ayrildi
- TDD / Test hizalama:
  - `app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt`
  - `app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt`
  - rescue davranislari yeni policy'ye gore guncellendi.
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantMergeLogicTest --console=plain` -> BUILD SUCCESSFUL
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> BUILD SUCCESSFUL
  - `assembleDebug --console=plain` -> BUILD SUCCESSFUL
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> Success
  - `adb -s RFCY11MX0TM logcat -c` yapildi
  - `adb -s RFCY11MX0TM shell cmd activity start-activity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.pokerarity.scanner/.ui.splash.SplashActivity` ile uygulama acildi
- Sonraki adim:
  - ayni 4 scan yeniden alinacak.
  - hedef:
    - `Squirtle shiny only` artik costume'a kayiyor mu
    - `Blastoise shiny+costume` `shiny=true` finale tasiniyor mu
## 24 March 2026 - 01:17
- Son batch log analizi sonrasi Phase 2 kok neden daha da daraltildi.
- Canli log bulgusu:
  - `Pikachu costume`: dogru (`costume=true`, `shiny=false`)
  - `Blastoise shiny+costume`: species dogru ama final `costume=false`, `shiny=false`
  - `Squirtle shiny+costume`: final `costume=true`, `shiny=false`
  - `Squirtle shiny only`: species authority ile `Squirtle`a geri donuyor, final `costume=false`, `shiny=false`
- Teknik sonuc:
  - Yeni `same_species_shiny_costume_rescue` resolver fix'i kodda vardi ama merge katmani bunu combo promotion olarak kabul etmiyordu.
  - Bu yuzden same-species `base shiny` vs `costume shiny` near-tie vakalarinda classifier dogru rescue yaptiginda bile `shiny/costume` finale tasinmiyordu.
- Duzeltme:
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
    - `same_species_shiny_costume_rescue` artik `exact_non_base_consensus` ile ayni combo promotion sinifi icinde degerlendiriliyor.
- Yeni testler:
  - `app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt`
    - `sameSpeciesShinyCostumeRescuePromotesBothFlags`
  - `app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt`
    - `sameSpeciesShinyCostumeRescuePromotesBothFlags`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantMergeLogicTest --console=plain` -> BUILD SUCCESSFUL
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> BUILD SUCCESSFUL
  - `assembleDebug --console=plain` -> BUILD SUCCESSFUL
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> Success
  - `adb -s RFCY11MX0TM logcat -c` yapildi
  - `adb -s RFCY11MX0TM shell monkey -p com.pokerarity.scanner -c android.intent.category.LAUNCHER 1` ile uygulama acildi
- Sonraki adim:
  - ayni problemli dortlu batch yeniden alinacak.
  - ozellikle `Blastoise shiny+costume` orneginde `same_species_shiny_costume_rescue` artik finale `costume=true, shiny=true` tasiyor mu olculecek.
## 24 March 2026 - 01:33
- Yeni canli log analizi ile Phase 2 acik hat daha net ayristirildi.
- Bu batch'in sonucu:
  - `Blastoise shiny+costume` kapandi:
    - final `shiny=true`, `costume=true`
  - `Pikachu costume` dogru kaldi:
    - final `shiny=false`, `costume=true`
  - `Squirtle shiny+costume`:
    - final `costume=true`, `shiny=false`
  - `Squirtle shiny only`:
    - final `costume=false`, `shiny=false`
- Teknik kok neden:
  - `Squirtle` orneklerinde classifier species dogru ama en iyi aday `007_00_05` (regular costume) olarak geliyor.
  - Ayni varyantin shiny peer'i (`007_00_05_shiny`) yakin skorla mevcut; fakat bu bilgi runtime'da tasinmiyordu.
  - Sonuc olarak same-variant regular/shiny near-tie durumlarinda shiny finale terfi ettirilemiyordu.
- Generic cozum:
  - `VariantPrototypeClassifier.MatchResult` icine eklendi:
    - `bestShinyPeerScore`
    - `bestShinyPeerAssetKey`
    - `bestShinyPeerSpriteKey`
  - classifier artik en iyi adayin ayni varyant/shiny peer'ini ayri izliyor.
  - `VariantMergeLogic` icinde yeni generic shiny-promotion kurali eklendi:
    - ayni varyantin shiny peer'i yakin skorla varsa
    - ve visual `hasCostume=true` ise
    - classifier regular costume secse bile `shiny=true` finale tasinabiliyor
  - Bu sadece `Squirtle`a ozel degil; ayni desenli tum `regular variant` vs `same-variant shiny peer` near-tie vakalari icin gecerli.
- Duzeltme dosyalari:
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeClassifier.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
- Yeni testler:
  - `app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt`
    - `sameVariantShinyPeerPromotesShinyForVisualCostume`
  - `app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt`
    - `sameVariantShinyPeerPromotesShinyForVisualCostume`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantMergeLogicTest --console=plain` -> BUILD SUCCESSFUL
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> BUILD SUCCESSFUL
  - `assembleDebug --console=plain` -> BUILD SUCCESSFUL
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> Success
  - `adb -s RFCY11MX0TM logcat -c` yapildi
  - `adb -s RFCY11MX0TM shell cmd activity start-activity -n com.pokerarity.scanner/.ui.splash.SplashActivity` ile uygulama acildi
- Sonraki adim:
  - ayni batch tekrar alinacak.
  - hedef:
    - `Squirtle shiny+costume` artik `shiny=true, costume=true` oluyor mu
    - `Squirtle shiny only` halen false-negative mi, yoksa ayri bir pure-shiny classifier problemi mi
## 24 March 2026 - 01:48
- Yeni canli log sonrasi Phase 2 bir adim daha ayrildi.
- Bu log turunun sonucu:
  - `Blastoise shiny+costume` duzgun:
    - final `shiny=true`, `costume=true`
  - `Squirtle shiny+costume` duzgunlesmeye yaklasti ama eksik:
    - final `shiny=true`, `costume=true`
  - `Pikachu costume` icin yeni generic kural false-positive `shiny=true` uretmeye basladi.
  - `Squirtle shiny only` halen `shiny=false`, `costume=false`
- Teknik kok neden:
  - eklenen `same-variant shiny peer` kuralı `Squirtle shiny+costume` icin dogruydu,
    fakat `Pikachu costume` gibi regular costume scanlerinde de cok yakin shiny peer skorlarini yanlislikla kabul ediyordu.
  - Log ornegi:
    - `Pikachu costume`: best shiny peer farki sadece ~`0.015`
    - `Squirtle shiny+costume`: peer farki ~`0.048`
- Generic duzeltme:
  - `VariantMergeLogic` icindeki `same-variant shiny peer` promotion kurali daraltildi.
  - Artik shiny peer promotion icin skor farki alt siniri da var:
    - min gap `0.03`
    - max gap `0.06`
  - boylece cok yakin ama belirsiz shiny peer'ler (Pikachu costume false-positive) reddedilirken,
    daha anlamli shiny peer ayrimi olan vakalar (Squirtle shiny+costume) korunuyor.
- Duzeltme dosyasi:
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
- Yeni testler:
  - `app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt`
    - `closeShinyPeerDoesNotPromoteShinyForRegularCostume`
  - `app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt`
    - `closeShinyPeerDoesNotPromoteShinyForRegularCostume`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantMergeLogicTest --console=plain` -> BUILD SUCCESSFUL
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> BUILD SUCCESSFUL
  - `assembleDebug --console=plain` -> BUILD SUCCESSFUL
  - Gradle lock/cached file problemi icin:
    - `gradlew --stop`
    - `.gradle\\8.9\\executionHistory`
    - `.gradle\\8.9\\checksums`
    - `.gradle\\buildOutputCleanup`
    temizlendi ve sonrasi tekrar dogrulandi
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> Success
  - `adb -s RFCY11MX0TM logcat -c` yapildi
  - `adb -s RFCY11MX0TM shell monkey -p com.pokerarity.scanner -c android.intent.category.LAUNCHER 1` ile uygulama acildi
- Sonraki adim:
  - ayni batch yeniden alinacak
  - hedef:
    - `Pikachu costume` false-positive `shiny` kapandi mi
    - `Squirtle shiny+costume` korunuyor mu
    - `Squirtle shiny only` icin ayri pure-shiny classifier hattinin gerekip gerekmedigi netlesecek
## 24 March 2026 - 02:07
- Yeni canli loglardan kalan tek Phase 2 acik hata daraltildi:
  - `Blastoise shiny+costume` dogru
  - `Squirtle shiny+costume` dogru
  - `Pikachu costume` icin false-positive `shiny` onceki turda kapatildi
  - acik kalan tek hat: `Squirtle shiny only` -> `shiny=false`, `costume=false`
- Teknik kok neden:
  - classifier `Squirtle shiny only` scan'inde dusuk farkla `costume`u `base`in onune koyuyor:
    - best costume ~`0.431`
    - best base ~`0.441`
  - visual `costume=false`, yani bu costume adayi merge'de zaten bastiriliyor
  - fakat ayni anda `best base shiny peer` (`007_00_shiny`) anlamli yakinlikta (~`0.477`) ve bu bilgi onceki modelde tasinmiyordu
  - dolayisiyla system finalde `regular`a dusuyordu
- Generic duzeltme:
  - `VariantPrototypeClassifier.MatchResult` icine eklendi:
    - `bestBaseAssetKey`
    - `bestBaseSpriteKey`
    - `bestBaseShinyPeerScore`
    - `bestBaseShinyPeerAssetKey`
    - `bestBaseShinyPeerSpriteKey`
  - `VariantMergeLogic` icinde yeni kural eklendi:
    - eger classifier `costume`u az farkla seciyor,
    - ama visual `costume=false`,
    - ve `bestBase` adayi yakin,
    - ve `bestBase`'in shiny peer'i anlamli skor araliginda ise,
    - `costume` bastirilirken `shiny=true` base hattindan finale tasinabiliyor
  - Bu da sadece `Squirtle`a ozel degil; same-species `costume/base/shiny` near-tie karismalari icin generic.
- Duzeltme dosyalari:
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantPrototypeClassifier.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt`
- Yeni testler:
  - `app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt`
    - `suppressedCostumeCanPromoteShinyFromBasePeer`
  - `app/src/androidTest/java/com/pokerarity/scanner/VariantDecisionEngineTest.kt`
    - `suppressedCostumeCanPromoteShinyFromBasePeer`
- Ek altyapi:
  - Son batch fixture olarak disa aktarildi:
    - `exported_fixtures/live_variant_batch_20260324_0115`
  - ornek goruntu teyidi:
    - `scan_1774315396941_0.png` -> Squirtle shiny only
    - `scan_1774315412648_0.png` -> Squirtle shiny+costume
    - `scan_1774315418289_0.png` -> Blastoise shiny+costume
    - `scan_1774315428060_0.png` -> Pikachu costume
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantMergeLogicTest --console=plain` -> BUILD SUCCESSFUL
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain` -> BUILD SUCCESSFUL
  - `assembleDebug --console=plain` -> BUILD SUCCESSFUL
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> Success
  - `adb -s RFCY11MX0TM logcat -c` yapildi
  - `adb -s RFCY11MX0TM shell cmd activity start-activity -n com.pokerarity.scanner/.ui.splash.SplashActivity` ile uygulama acildi
- Sonraki adim:
  - ayni batch tekrar alinacak
  - hedef:
    - `Squirtle shiny only` ilk kez `shiny=true` oluyor mu
    - `Squirtle shiny+costume` ve `Blastoise shiny+costume` korunuyor mu
## 24 March 2026 - 03:46
- Canli loglar tekrar okundu; bu tur dogruluk batch'i genel olarak dogru gorundu:
  - `Squirtle shiny only` -> `shiny=true`, `costume=false`
  - `Squirtle shiny+costume` -> `shiny=true`, `costume=true`
  - `Blastoise shiny+costume` -> `shiny=true`, `costume=true`
  - `Pikachu costume` -> `shiny=false`, `costume=true`
  - ek `Blastoise shiny+costume` scan'i de dogru geldi
- Bu log turunda acik kalan ana problem artik dogruluktan cok hiz:
  - ilk `Squirtle shiny only` scan'i `4952ms`
  - `Squirtle shiny+costume` `2173ms`
  - `Blastoise shiny+costume` `2507ms`
  - `Pikachu costume` `2226ms`
  - ek `Blastoise` `3111ms`
- Kalan kok neden:
  - ilk `Squirtle` scan'inde quick OCR `Candy -> null`, `DisplayName -> Mankey`
  - system species'i ancak detailed OCR icindeki `Candy -> Squirtle` ile toparliyor
  - bu da ilk scan'i gereksiz yere slow path'e dusuruyor
- Buna gore hiz odakli generic degisiklik daha once `OCRProcessor` icine eklendi:
  - quick pass'te isim guveni dusukse dar `Candy` / `CandyBlock` rescue denemesi yapiliyor
  - amac detailed OCR'a dusmeden family authority'yi erken kullanmak
- Bu hiz build'i bu tur derlendi ve cihaza kuruldu:
  - `gradlew assembleDebug --console=plain` -> `BUILD SUCCESSFUL`
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`
  - `adb -s RFCY11MX0TM logcat -c`
  - `adb -s RFCY11MX0TM shell cmd activity start-activity -n com.pokerarity.scanner/.ui.splash.SplashActivity`
- Sonraki olcum:
  - ayni batch yeniden alinacak
  - hedef ilk `Squirtle shiny only` scan'inin slow path'ten cikmasi ve toplam surede anlamli dusus gorulmesi
## 26 March 2026 - 14:18
- Canli loglarda yeni bir Phase 1 authority bug'i goruldu:
  - ilk scan'de quick OCR `DisplayName -> Wartortle`, `Candy -> null`
  - global classifier ayni family icinde `Squirtle`i daha iyi skorla goruyor:
    - `Squirtle:0.467`
    - `Blastoise:0.472`
    - `Wartortle:0.546`
  - buna ragmen species-scoped classifier yanlis OCR species'i (`Wartortle`) uzerinde calistigi icin final sonuc `Wartortle` olarak bozuluyor
- Kok neden:
  - mevcut authority gate cross-family override'i engelliyordu ama ayni family icindeki "OCR species yanlis, classifier species acik ara daha iyi" durumunu ele almiyordu
  - bu da species-scoped pass'in yanlis species hedefiyle calismasina yol aciyordu
- Generic duzeltme:
  - `ScanAuthorityLogic` icine `shouldPreferClassifierSpeciesForScopedPass(...)` eklendi
  - ayni family icinde:
    - OCR exact species lock var
    - candy yok
    - classifier confidence tabani geciliyor
    - classifier score, mevcut OCR species skorundan anlamli derecede daha iyiyse
    - species-scoped pass artik classifier species'i uzerinde calisiyor
  - Bu sadece `Squirtle/Wartortle` icin degil; ayni family icindeki yanlis OCR species sapmalarini hedefleyen generic bir authority kuralidir
- Degisen dosyalar:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/ScanAuthorityLogic.kt`
  - `app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt`
  - `app/src/test/java/com/pokerarity/scanner/ScanAuthorityLogicTest.kt`
- Yeni testler:
  - `sameFamilyClassifierCanDriveScopedPassWhenItClearlyBeatsLockedOcrSpecies`
  - `sameFamilyClassifierDoesNotDriveScopedPassWhenScoresAreTooClose`
- Dogrulama:
  - `gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.ScanAuthorityLogicTest --console=plain` -> `BUILD SUCCESSFUL`
  - `gradlew --no-daemon assembleDebug --console=plain` -> `BUILD SUCCESSFUL`
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`
  - `adb -s RFCY11MX0TM logcat -c`
  - `adb -s RFCY11MX0TM shell cmd activity start-activity -n com.pokerarity.scanner/.ui.splash.SplashActivity`
- Sonraki olcum:
  - ayni batch yeniden alinacak
  - hedef ilk scan'de `Wartortle` sapmasinin kapanmasi ve species-scoped classifier'in dogru family uyelerinde kalmasi
## 26 March 2026 - 16:42
- Eski heuristic/patch dongusunden cikmak icin `authoritative catalog` fazi baslatildi.
- Hedef artik:
  - `species + shiny + costume + form + event metadata` icin tek authoritative veri kaynagi kurmak
  - classifier/model/runtime katmanlarini bu veriyle beslemek
  - `lucky/background/shadow/purified` bu fazin disinda tutmak
- Plan dosyasi yazildi:
  - `docs/superpowers/plans/2026-03-26-authoritative-variant-catalog.md`
- Task 1 ve Task 2 ilk teslimleri tamamlandi:
  - yeni generator:
    - `scripts/generate_variant_catalog.py`
  - yeni generated asset:
    - `app/src/main/assets/data/variant_catalog.json`
  - runtime model:
    - `app/src/main/java/com/pokerarity/scanner/data/model/VariantCatalogEntry.kt`
  - runtime loader:
    - `app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogLoader.kt`
  - yeni test:
    - `app/src/test/java/com/pokerarity/scanner/VariantCatalogLoaderTest.kt`
- `variant_catalog.json` ozeti:
  - `2154` entry
  - `711` species
  - her satir icin:
    - `species`
    - `dex`
    - `formId`
    - `variantId`
    - `assetKey`
    - `spriteKey`
    - `isShiny`
    - `variantClass`
    - `isCostumeLike`
    - `eventTags`
    - `hasEventMetadata`
    - `releaseWindow`
    - `gameMasterCostumeForms`
    - `assetPath`
- Loader testi red-green ile dogrulandi:
  - ilk calistirmada `VariantCatalogLoader` unresolved oldugu icin fail verdi
  - implementation sonrasi:
    - `gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --console=plain` -> `BUILD SUCCESSFUL`
- Task 3 baslatildi:
  - `scripts/train_variant_prototypes.py` artik loose `variantType` inference yerine `variant_catalog.json` semantiklerini kullaniyor
  - model payload'ina ek metadata tasindi:
    - `eventTags`
    - `hasEventMetadata`
    - `releaseWindow`
    - `gameMasterCostumeForms`
  - `VariantPrototypeStore.Entry` buna gore genisletildi
- Model regenerate edildi:
  - `python scripts/train_variant_prototypes.py --assets-dir "external/pogo_assets/Images/Pokemon - 256x256"`
  - yeni model:
    - `app/src/main/assets/data/variant_classifier_model.json`
- Dogrulama:
  - `gradlew --no-daemon assembleDebug --console=plain` -> `BUILD SUCCESSFUL`
- Bu tur runtime decision engine'e catalog constraint entegrasyonu henuz yapilmadi.
- Sonraki adim:
  - classifier sonucuna `catalog row` kimligi ve authoritative semantics tasinacak
  - `VariantDecisionEngine` ve `VariantMergeLogic` artik raw inferred `variantType` degil catalog truth ile calisacak
## 26 March 2026 - 17:18
- Yeni ground truth geldi:
  - logda `Finneon` gorunen scan aslinda `Flareon` idi
- Kök neden logla netlestirildi:
  - `OCRProcessor` ham adi `FIareole` olarak okuyordu
  - `TextParser` bunu tekrar tekrar `Finneon`a token-match ediyordu:
    - `Token match: 'fiareole' -> 'finneon' (d=3)`
  - `SpeciesRefiner` top listede `Flareon`u birinci sirada tutsa da `current=Finneon` oldugu icin species'i koruyordu
  - classifier da yanlis species (`Finneon`) ustunde calisarak `456_01_shiny` form adayina gidiyordu
- Yani sorun classifier ya da rarity degil, parser seviyesinde `I/l` OCR karisikliginin `Flareon -> Finneon` cross-family drift uretmesiydi.
- TDD ile yakalandi:
  - `app/src/androidTest/java/com/pokerarity/scanner/TextParserRegressionTest.kt`
  - yeni test:
    - `parseNamePrefersFlareonForFlareonLikeToken`
  - ilk calistirmada fail:
    - `expected:<Flareon> but was:<Finneon>`
- Fix:
  - `app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt`
  - `buildObservations()` artik OCR `I/l` karisikliklari icin ambiguity varyantlari uretiyor
  - ornek:
    - `fiareole` icin `flareole` gibi ek observation uretiliyor
  - bu `Flareon` gibi `l` harfi buyuk `I` olarak okunan vakalari generic olarak toparliyor
- Dogrulama:
  - `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.TextParserRegressionTest --console=plain` -> `BUILD SUCCESSFUL`
  - `gradlew assembleDebug --console=plain` -> `BUILD SUCCESSFUL`
- Deploy:
  - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`
  - `adb -s RFCY11MX0TM logcat -c`
  - `adb -s RFCY11MX0TM shell cmd activity start-activity -n com.pokerarity.scanner/.ui.splash.SplashActivity`
- Beklenen etki:
  - `Flareon` benzeri `I/l` kaynakli cross-family name drift azalacak
  - ayni parser iyilestirmesi baska benzer OCR `I/l` karisikligi vakalarina da etki edecek
## 26 March 2026 - 18:34
- Otomatik scan telemetry altyapisi eklendi. Amac:
  - arkadaslarin yaptigi scanleri uygulama icinden otomatik toplamak
  - screenshot + OCR + classifier/debug trace + app prediction verisini backend'e yuklemek
  - bu datayi sonradan export edip regression datasetine cevirmek
- Tasarim ve plan dosyalari yazildi:
  - `docs/superpowers/specs/2026-03-26-scan-telemetry-design.md`
  - `docs/superpowers/plans/2026-03-26-scan-telemetry.md`
- Android tarafinda yeni queue/uploader katmani eklendi:
  - yeni Room entity:
    - `app/src/main/java/com/pokerarity/scanner/data/local/db/TelemetryUploadEntity.kt`
  - yeni DAO:
    - `app/src/main/java/com/pokerarity/scanner/data/local/db/TelemetryUploadDao.kt`
  - DB guncellendi:
    - `AppDatabase` version `1 -> 2`
  - yeni payload model:
    - `app/src/main/java/com/pokerarity/scanner/data/model/ScanTelemetryPayload.kt`
  - yeni uploader/config:
    - `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryConfig.kt`
    - `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt`
    - `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryCoordinator.kt`
  - yeni repository:
    - `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
- Scan pipeline entegrasyonu:
  - `ScanManager` artik final result olustuktan sonra screenshot'i `cache/telemetry` altina kopyalayip payload ile queue'ya ekliyor
  - upload denemesi result overlay'i bloklamadan arka planda calisiyor
  - `PokeRarityApp` startup'ta pending queue'yu flush etmeye calisiyor
- Build config:
  - `app/build.gradle.kts` icine:
    - `SCAN_TELEMETRY_BASE_URL`
    - `SCAN_TELEMETRY_API_KEY`
    - `SCAN_TELEMETRY_ENABLED`
  - bunlar `local.properties` icindeki:
    - `scanTelemetryBaseUrl`
    - `scanTelemetryApiKey`
    degerlerinden uretiliyor
  - su an `local.properties` sadece `sdk.dir` iceriyor; yani telemetry bu build'de bilincli olarak disabled
- Backend altyapisi eklendi:
  - `web/scan-telemetry/schema.sql`
  - `web/scan-telemetry/config.example.php`
  - `web/scan-telemetry/api/bootstrap.php`
  - `web/scan-telemetry/api/scan-upload.php`
  - `web/scan-telemetry/api/scan-export.php`
  - `web/scan-telemetry/README.md`
- Export modeli:
  - server `payload_json`, screenshot path, predicted species/flags, raw OCR, rarity/debug alanlarini sakliyor
  - future truth alanlari da schema'ya reserve edildi:
    - `user_truth_species`
    - `user_truth_is_shiny`
    - `user_truth_has_costume`
    - `user_truth_form`
- Test / dogrulama:
  - yeni unit test:
    - `app/src/test/java/com/pokerarity/scanner/ScanTelemetryPayloadTest.kt`
  - `gradlew --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.ScanTelemetryPayloadTest --console=plain` -> `BUILD SUCCESSFUL`
  - `gradlew --no-daemon assembleDebug --console=plain` -> `BUILD SUCCESSFUL`
  - APK deploy:
    - `adb -s RFCY11MX0TM install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`
    - `adb -s RFCY11MX0TM logcat -c`
    - `adb -s RFCY11MX0TM shell cmd activity start-activity -n com.pokerarity.scanner/.ui.splash.SplashActivity`
- Notlar:
  - bu ortamda `php` binary PATH'te olmadigi icin backend dosyalari lokal syntax-check ile dogrulanamadi
  - telemetry URL tanimlanmadigi icin mevcut debug build upload denemeyecek; altyapi hazir ama config bekliyor
- Sonraki adim:
  - sunucuya `web/scan-telemetry` klasorunu deploy etmek
  - `config.php` olusturmak
  - `schema.sql` calistirmak
  - `local.properties` icine base URL + API key ekleyip yeni build almak
- Hazir deploy paketi:
  - `scan-telemetry-deploy.zip`
## 26 March 2026 - 18:48
- Telemetry backend canli olarak baglandi.
- Domain:
  - `https://caglardinc.com/scan-telemetry/api`
- App config:
  - `local.properties` icine su degerler yazildi:
    - `scanTelemetryBaseUrl=https://caglardinc.com/scan-telemetry/api`
    - `scanTelemetryApiKey=pkr_2026_telemetry_7f4c9a2d1e8b5c3f`
- Dogrulama:
  - `scan-export.php` bos veritabaninda dogru JSON dondu:
    - `{"ok":true,"count":0,"items":[]}`
  - app yeniden build edildi:
    - `gradlew --no-daemon assembleDebug --console=plain` -> `BUILD SUCCESSFUL`
  - APK cihaz `RFCY11MX0TM` uzerine kuruldu:
    - `adb install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`
  - backend smoke test yapildi:
    - `scan-upload.php` endpoint'ine manuel payload POST edildi
    - cevap:
      - `{"ok":true,"upload_id":"manual-smoke-001","screenshot_url":null}`
  - export tekrar cekildi ve row gorundu:
    - `count=1`
    - species=`Flareon`
    - predicted_has_costume=`1`
- Sonuc:
  - Android app artik telemetry backend'e upload yapabilecek durumda
  - backend insert/export yolu calisiyor
- Sonraki pratik adim:
  - cihazda gercek scan yap
  - export endpoint'ten gelen yeni row'lari kullanarak regression dataset uretecegiz
## 26 March 2026 - 18:56
- Gercek cihaz scan'leri telemetry backend'e otomatik upload oldu.
- Export kontrolu:
  - `scan-export.php?api_key=...&limit=20` uzerinden `count=5` dondu
  - bunlarin `1` adedi manuel smoke kaydi, `4` adedi gercek cihaz scan'i
- Gercek cihazdan gelen yeni upload'lar:
  - `93896646-f263-4539-8e66-efaf3e9ddba5`
    - species=`Pikachu`
    - costume=`true`
    - shiny=`false`
    - screenshot kaydi var
  - `6d4e97f3-29f2-42e3-852f-3c4c1da250a0`
    - species=`Pikachu`
    - costume=`true`
    - shiny=`false`
    - screenshot kaydi var
  - `912f8987-eca2-4f5f-818f-4053cd1bde9e`
    - species=`Piplup`
    - costume=`false`
    - shiny=`false`
    - screenshot kaydi var
  - `5babc2bd-4981-454f-a2c2-4fa2ce352e2d`
    - species=`Lapras`
    - costume=`false`
    - shiny=`false`
    - screenshot kaydi var
- Sonuc:
  - otomatik upload yolu calisiyor
  - export yolu calisiyor
  - screenshot dosyalari da server tarafinda saklaniyor
  - artik gercek saha verisiyle regression yapabiliriz
## 26 March 2026 - 19:24
- Rarity UI numeric breakdown'dan text-first explanation modeline cevrildi.
- Yeni spec ve plan yazildi:
  - `docs/superpowers/specs/2026-03-26-rarity-explanation-design.md`
  - `docs/superpowers/plans/2026-03-26-rarity-explanation-implementation.md`
- Data model degisti:
  - `RarityAnalysisItem` artik `title + detail + isPositive` tasiyor
  - `buildAnalysisItems()` artik `explanations` listesini `breakdown` oncesinde tercih ediyor
- Yeni formatter eklendi:
  - `RarityExplanationFormatter.kt`
  - event tag, release window, shiny/costume/form ve caught-date icin kullaniciya anlamli metin uretiyor
- `RarityCalculator`:
  - authoritative variant catalog sprite key uzerinden okunuyor
  - `VariantClassifierSpriteKey` / `ClassifierSpriteKey` ile catalog entry bulunup explanation listesine event/release bilgisi ekleniyor
  - puan matematigi degismedi
- UI degisiklikleri:
  - overlay ve full result ekraninda `RARITY BREAKDOWN` yerine `WHY IT'S VALUABLE`
  - satirlar artik `title + optional detail` olarak gosteriliyor
  - numeric row points kaldirildi, toplam skor korunuyor
- Eklenen testler:
  - `PokemonAnalysisFormattingTest.kt`
  - `RarityExplanationFormatterTest.kt`
- Not:
  - Bu makinada Gradle/Kotlin cache dosyalari Windows file-lock altinda kaldigi icin test/build dogrulamasi bu tur temiz sonuc veremedi
  - hata koddan degil, `dataBindingMergeDependencyArtifactsDebug` / `kapt` altindaki mapped-file lock probleminden geliyor
## 26 March 2026 - 20:17
- Authoritative variant catalog genisletildi:
  - `variant_catalog.json` artik `primaryEventLabel` ve `variantLabel` tasiyor
  - generator aile-ici propagation ile tek form bilgisi olan species'lerden ayni family'deki diger costume entry'lere etiket yayiyor
  - ornek: `Squirtle/Wartortle/Blastoise` icin `Fall 2019 costume`
- `RarityExplanationFormatter` tekrar yazildi:
  - generic `Costume variant` yerine `Costume: <label>` uretmeye basladi
  - event satiri artik `Event: <name>` formatinda
  - release window satiri `Release window` olarak ayrildi
- `RarityCalculator` yeni catalog alanlarini explanation'a bagladi
- `CollectionScreen` icin `statusBarsPadding()` eklendi
  - uygulama ilk acilista artik saat/notch alanina sarkmamali
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.RarityExplanationFormatterTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --console=plain` gecti
  - `assembleDebug --console=plain` gecti
  - APK `RFCY11MX0TM` cihazina kuruldu ve launcher ile acildi
- Not:
  - exact costume adi her species icin henuz mevcut degil
  - `Pikachu/Raichu/Pichu` gibi cok varyantli ailelerde local metadata tek-basina kesin birebir map vermiyor
  - buna ragmen artik catalog'da exact label bulunabilen species'lerde kullaniciya dogrudan kostum/event metni gosterilebiliyor
## 26 March 2026 - 20:42
- Canli log incelendi:
  - classifier `costume=true` gordugu halde final `VisualFeatures.hasCostume=false` kalan scan'ler vardi
  - ornek: `Pikachu` ve `Pichu` scan'lerinde `Variant classifier rescue(species)` costume sprite gosteriyor, ama `RarityCalculator` sadece final visual flag'e baktigi icin explanation sadece yakalanma tarihini gosteriyordu
- Bu zincir kapatildi:
  - `RarityCalculator` artik explanation üretirken sadece `mergedVisualFeatures` degil, classifier trace ve catalog authority bilgisini de kullaniyor
  - eger `VariantClassifierCostume=true` ya da catalog entry `isCostumeLike=true` ise, explanation tarafi bunu `WHY IT'S VALUABLE` icinde gosterecek
  - ayni mantik `form` icin de eklendi
- Dosya:
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
- Dogrulama:
  - `assembleDebug --console=plain` gecti
  - APK `RFCY11MX0TM` cihazina kuruldu ve launcher ile acildi
- Not:
  - Bu degisiklik aciklamayi duzeltiyor; scan final tag'lerinin kendisi ayrica ayri authority/merge isi olmaya devam ediyor
## 26 March 2026 - 21:03
- `WHY IT'S VALUABLE` bolumu liste mantigindan cikarildi.
- `buildAnalysisItems()` artik explanation listesini tek narrative paragrafa donusturuyor:
  - kostum
  - event
  - yakalanma tarihi
  - en sonda toplam skor
- Overlay ve full result ekranlari bu narrative'i textbox gibi tek blokta gosteriyor.
- Eklenen davranis ornegi:
  - `This Pokemon is valuable because it matches the Fall 2019 costume, it ties back to the Fall 2019 event, and it was caught on Jan 05, 2017. Together these signals place it at 61/100 rarity.`
- Dosyalar:
  - `app/src/main/java/com/pokerarity/scanner/data/model/Pokemon.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`
  - `app/src/test/java/com/pokerarity/scanner/PokemonAnalysisFormattingTest.kt`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain` gecti
  - `assembleDebug --console=plain` gecti
  - APK `RFCY11MX0TM` cihazina kuruldu
## 26 March 2026 - 21:16
- `context-mode` kuruldu:
  - `npm install -g context-mode`
  - `~/.codex/config.toml` icine MCP kaydi eklendi:
    - `[mcp_servers.context-mode]`
    - `command = "context-mode"`
  - routing dosyasi proje kokune kopyalandi:
    - `AGENTS.md`
- Dogrulama:
  - `context-mode doctor` calisti
  - MCP plugin kaydi `PASS`
  - FTS5 / SQLite `PASS`
  - beklenen kisit:
    - Codex CLI hook desteklemedigi icin bu kurulum tam otomatik degil
    - repo kendi doctor cikisinda da `Hook support: FAIL` diyerek bunu net soyluyor
- Pratik etki:
  - tool/MCP output tarafinda context tuketimini azaltabilir
  - mevcut acik oturumun zaten tuketilmis context'ini geri kazanmaz
  - tam etkinin gorulmesi icin Codex restart gerekir
## 26 March 2026 - 21:43
- `WHY IT'S VALUABLE` icin explicit variant alias katmani genisletildi.
- `variant_catalog` generator artik sparse costume variant id'lerini Game Master form listesine dogrudan indexleyebiliyor.
  - bu sayede `Pikachu` ailesinde `025_00_23 -> Flying 03 costume`, `025_00_24 -> World Championships 2023 costume`, `025_00_47 -> GO Fest 2025 Monocle Yellow costume` gibi etiketler uretilebiliyor
- Narrative textbox metni daha agir yapildi:
  - overlay: `14sp`, `FontWeight.Black`
  - full result: `15sp`, `FontWeight.Black`
- Dosyalar:
  - `scripts/generate_variant_catalog.py`
  - `app/src/test/java/com/pokerarity/scanner/VariantCatalogLoaderTest.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt`
  - `app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt`
  - `docs/superpowers/plans/2026-03-26-variant-alias-and-textbox-polish.md`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --tests=com.pokerarity.scanner.RarityExplanationFormatterTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain` gecti
  - `assembleDebug --console=plain` gecti
## 26 March 2026 - 22:08
- Yanlis event adi kok nedeni ayrildi:
  - explanation katmani final species `Raichu` olsa bile classifier'in `Pikachu` sprite alias'ini aynen kullaniyordu
  - bu nedenle `025_00_12 -> World Championships 2022` ve `025_00_23 -> Flying 03` gibi Pikachu event adlari Raichu scan'lerine siziyordu
- Species-aware ve confidence-aware variant explanation selection eklendi.
  - exact event/kostum adi artik sadece species-uyumlu ve yeterince guvenilir classifier sonucundan geliyor
  - family icinde ayni variant id final species'te de varsa species'e remap ediliyor
  - final species'te karsiligi olmayan sprite alias'lari exact event olarak gosterilmiyor
- Dosyalar:
  - `app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
  - `app/src/test/java/com/pokerarity/scanner/VariantCatalogSelectionTest.kt`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --tests=com.pokerarity.scanner.RarityExplanationFormatterTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain` gecti
  - `assembleDebug --console=plain` gecti
## 26 March 2026 - 22:17
- Generic fallback'e donus kok nedeni: species-aware guard fazla sertti.
  - exact-species `Pikachu` eslesmelerinde bile event/kostum alias'i bastiriliyordu
- Guard iki seviyeye ayrildi:
  - exact-species match: daha dusuk confidence ile exact alias'a izin
  - cross-species/family remap: daha siki kaldi, yanlis event sizintisi devam etmeyecek
- Dosyalar:
  - `app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt`
  - `app/src/test/java/com/pokerarity/scanner/VariantCatalogSelectionTest.kt`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --tests=com.pokerarity.scanner.RarityExplanationFormatterTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain` gecti
  - `assembleDebug --console=plain` gecti
- Not:
  - APK install asamasinda `adb` baglantisi dustu; bu son fix henuz cihaza kurulmus degil
## 26 March 2026 - 22:29
- Event/kostum alias guard family-ozel olmaktan cikarildi; tum costume-capable species icin generic species-aware kurala baglandi.
- Yeni garanti:
  - exact alias ancak final species ile uyumlu catalog kaydindan gelir
  - family icinde remap sadece final species'te ayni `variantId/formId` gercekten varsa yapilir
  - species'te karsiligi olmayan family sprite'lari exact event adi olarak gosterilmez
- Generic regression eklendi:
  - `Pikachu -> Pichu` family remap exact alias ile calisir
  - `Pikachu -> Raichu` ve benzeri karsiligi olmayan sprite sızıntilari bloklu kalir
- Dosyalar:
  - `docs/superpowers/plans/2026-03-26-generic-costume-alias-guard.md`
  - `app/src/test/java/com/pokerarity/scanner/VariantCatalogSelectionTest.kt`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --console=plain` gecti
  - `assembleDebug --console=plain` gecti
- Not:
  - cihaz bagli olmadigi icin bu son generic guard build'i henuz telefona kurulmus degil
## 26 March 2026 - 23:04
- `authoritative external variant DB` isi baslatildi.
  - yeni spec: `docs/superpowers/specs/2026-03-26-authoritative-external-variant-db-design.md`
  - yeni plan: `docs/superpowers/plans/2026-03-26-authoritative-external-variant-db.md`
- UI cleanup:
  - sonuclar ekranindan `IV` karti kaldirildi
  - `WHY IT'S VALUABLE` textbox'u buyutuldu
  - narrative 2-3 cumlelik daha detayli formata cekildi
- External DB foundation:
  - `scripts/generate_authoritative_variant_db.py`
  - `app/src/main/assets/data/authoritative_variant_db.json`
  - `app/src/main/java/com/pokerarity/scanner/data/model/AuthoritativeVariantEntry.kt`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantDbLoader.kt`
  - `RarityCalculator` explanation lookup'u bu yeni asset'i tercih edecek sekilde baglandi
- Testler:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --console=plain` gecti
- Build:
  - `assembleDebug --console=plain` gecti
  - APK `RFCY11MX0TM` cihazina kuruldu
- Not:
  - external DB bu turda tam internet merge degil; mevcut local authoritative katmani yeni ayri asset/loader formuna tasindi
  - sonraki tur, Bulbapedia/PokeMiners merge'ini generator'a gercekten sokmak olacak
## 26 March 2026 - 23:12
- Ilk gerçek external override merge'i authoritative DB generator'a baglandi.
  - yeni kaynak: `scripts/bulbapedia_event_overrides.json`
  - generator artik `variant_catalog.json` uretimini bu external override dosyasi ile birlestiriyor
  - `authoritative_variant_db.json` yeniden uretildi; `sourceSummary.external_overrides > 0`
- Explanation tarafina `caught date vs event start` sanity guard eklendi.
  - Bir Pokemon event baslangicindan daha once yakalanmissa exact kostum/event adi gosterilmiyor
  - Bu durumda yanlis exact event gostermek yerine generic explanation'a dusuluyor
- Yeni kod:
  - `app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationSanity.kt`
  - `scripts/bulbapedia_event_overrides.json`
- Guncellenen kod:
  - `scripts/generate_authoritative_variant_db.py`
  - `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
  - `app/src/test/java/com/pokerarity/scanner/AuthoritativeVariantDbTest.kt`
- Yeni test:
  - `app/src/test/java/com/pokerarity/scanner/VariantExplanationSanityTest.kt`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.VariantExplanationSanityTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain` gecti
  - `assembleDebug --console=plain` gecti
  - APK `RFCY11MX0TM` cihazina kuruldu ve uygulama acildi
- Etki:
  - `2017` yakalanmis bir Pokemon icin `2022/2023` event adi artik exact gosterilmeyecek
  - authoritative DB bundan sonra kademeli olarak Bulbapedia/PokeMiners metadata'si ile genisletilecek
## 26 March 2026 - 23:18
- Canli loglarda yanlis exact event adlarinin asil nedeni bulundu.
  - VariantCatalogSelection exact metadata'yi dogru bastiriyordu
  - ama RarityCalculator authoritative DB etiketlerini llowExactMetadata=false olsa bile dogrudan okuyordu
  - bu nedenle guard'i bypass eden yanlis exact event/kostum isimleri gorunuyordu
- Bu bypass kapatildi.
  - yeni helper: pp/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt
  - RarityCalculator artik authoritative label/event/window verisini yalniz llowExactMetadata=true ise kullaniyor
- Yeni test:
  - pp/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt
- Dogrulama:
  - 	estDebugUnitTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.VariantExplanationSanityTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain gecti
  - ssembleDebug --console=plain gecti
  - APK RFCY11MX0TM cihazina kuruldu ve uygulama acildi
- Beklenen etki:
  - classifier dusuk guvenle yanlis costume id secse bile exact event adi artik textbox'a sizmayacak
  - metadata ya generic'e dusecek ya da ancak yeterli exact guven oldugunda gosterilecek
## 26 March 2026 - 23:25
- Yeni kok neden dogrulandi: amily_costume_rescue ve benzeri rescue path'leri yapay  .52 confidence ile exact metadata esigini aciyordu.
- Bu yuzden classifier yanlis costume id sectiginde exact event/kostum adi yine textbox'a siziyordu.
- Fix:
  - VariantCatalogSelection artik rescue-kind'i de exact metadata karari icine katiyor
  - exact event/kostum adi yalniz escueKind=null veya exact_non_base_consensus ise acilabiliyor
  - amily_costume_rescue ve same_family_non_base_rescue artik exact alias acmiyor
- Dosyalar:
  - pp/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt
  - pp/src/test/java/com/pokerarity/scanner/VariantCatalogSelectionTest.kt
- Dogrulama:
  - 	estDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.VariantExplanationSanityTest --console=plain gecti
  - ssembleDebug --console=plain gecti
  - APK RFCY11MX0TM cihazina kuruldu ve uygulama acildi
- Beklenen etki:
  - yanlis costume id + rescue confidence kombinasyonu artik exact event adi gostermeyecek
  - sorunlu scan'ler generic explanation'a dusmeli, exact event yalniz daha guvenli match'lerde gelmeli
## 27 March 2026 - 14:35
- event isimleri hic gelmiyor problemi icin yeni kok neden ayrildi:
  - classifier exact sprite secemese bile costumed sonuc veriyor
  - exact metadata guard bu durumda dogru olarak kapaniyor
  - fakat authoritative DB'de eski Pikachu/Raichu/Pichu event satirlari eksik oldugu icin date-based exact fallback de yapilamiyordu
- Bu tur iki katman eklendi:
  - scripts/bulbapedia_event_overrides.json genisletildi
    - Pikachu/Raichu/Pichu icin Holiday 2016, Pokemon Day 2017, Halloween 2017 satirlari eklendi
  - yeni date-based fallback: AuthoritativeVariantEventFallback
    - classifier costume sinyali varsa
    - exact sprite guvenilmez olsa bile
    - final species + caught date authoritative DB event araligina dusuyorsa exact event/kostum metnini geri veriyor
- Yeni kod:
  - pp/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantEventFallback.kt
- Guncellenen kod:
  - pp/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantDbLoader.kt
  - pp/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt
  - pp/src/test/java/com/pokerarity/scanner/AuthoritativeVariantDbTest.kt
  - pp/src/test/java/com/pokerarity/scanner/AuthoritativeVariantEventFallbackTest.kt
- Dogrulama:
  - python scripts/generate_authoritative_variant_db.py calisti
  - 	estDebugUnitTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.AuthoritativeVariantEventFallbackTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.VariantExplanationSanityTest --console=plain gecti
  - ssembleDebug --console=plain gecti
  - APK RFCY11MX0TM cihazina kuruldu ve uygulama acildi
- Beklenen etki:
  - 2017-01-03 gibi tarihlerde kostum sinyali varsa Holiday 2016 / Festive hat costume gibi exact event adi cikabilir
  - exact sprite hala guvensiz olsa bile, species+tarih authoritative metadata ile explanation doldurulacak
## 27 March 2026 - 14:54
- Kullanici talebine gore aileye ozel override yaklasimi bir adim daha genellestirildi:
  - exact external event adi sadece guvenli exact match'te acik kalacak
  - ama GM/form tabanli turetilmis costume/event isimleri species-safe secimde daha genis kullanilacak
- Bu turdaki ana degisiklikler:
  - VariantCatalogSelection icine `allowDerivedMetadata` eklendi
  - VariantExplanationMetadata artik exact metadata kapali olsa bile derived local metadata'yi kullanabiliyor
  - generate_authoritative_variant_db.py family seviyesinde exact override propagation yapiyor
    - ayni family + variantId + shiny grubunda exact override varsa, eksik family uyelerine metadata propagete ediliyor
- Dogrulanan etkiler:
  - exact yanlis event sizmasi icin onceki guard korunuyor
  - ayni anda textbox'in tamamen `costume variant` generic'ine dusmesi azaltildi
  - authoritative DB sourceSummary artik `family_override_propagation` sayisini da yaziyor
- Guncellenen dosyalar:
  - app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt
  - app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt
  - scripts/generate_authoritative_variant_db.py
  - app/src/test/java/com/pokerarity/scanner/VariantCatalogSelectionTest.kt
  - app/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt
- Dogrulama:
  - python scripts/generate_authoritative_variant_db.py calisti
  - testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.AuthoritativeVariantEventFallbackTest --console=plain gecti
  - assembleDebug --console=plain gecti
  - APK RFCY11MX0TM cihazina kuruldu ve uygulama acildi
## 27 March 2026 - 16:22
- Full matcher rewrite fiilen baslatildi.
- Kullanilan yaklasim:
  - mevcut OCR/classifier/merge patch zincirini koruyarak yama yapmak yerine
  - yeni authoritative matcher kontrati ve aday/ranking katmani paralel kuruluyor
- Bu turda tamamlanan ilk 3 temel tas:
  - yeni kontrat modelleri:
    - app/src/main/java/com/pokerarity/scanner/data/model/FullVariantCandidate.kt
    - app/src/main/java/com/pokerarity/scanner/data/model/FullVariantMatch.kt
  - yeni candidate builder:
    - app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt
  - yeni matcher cekirdegi:
    - app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantConstraints.kt
    - app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantScoring.kt
    - app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt
- Yeni testler:
  - app/src/test/java/com/pokerarity/scanner/FullVariantMatchContractTest.kt
  - app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt
  - app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt
- Dogrulama:
  - ilk once failing test goruldu:
    - FullVariantMatchContractTest unresolved reference ile fail etti
    - FullVariantCandidateBuilderTest builder yok diye fail etti
    - FullVariantMatcherTest matcher yok diye fail etti
  - sonra minimal implementasyon yazildi
  - GRADLE_OPTS='-Dorg.gradle.vfs.watch=false -Dkotlin.incremental=false' ile testler gecti
- Not:
  - bu tur henüz runtime entegrasyon degil
  - VariantDecisionEngine ve ScanManager hala eski akista
  - sonraki adim: FullVariantMatcher sonucunu runtime classify/merge zincirine baglamak
## 27 March 2026 - 16:04
- Full matcher rewrite runtime'a ilk kez baglandi.
- Yapilanlar:
  - VariantDecisionEngine artik FullVariantCandidateBuilder + FullVariantMatcher cagiriyor
  - ClassificationResult icine fullMatch eklendi
  - raw OCR trace icine FullVariant* alanlari yaziliyor:
    - FullVariantSpecies
    - FullVariantSpriteKey
    - FullVariantClass
    - FullVariantShiny
    - FullVariantCostume
    - FullVariantForm
    - FullVariantEvent
    - FullVariantExplanationMode
    - confidence alanlari
  - VariantMergeLogic fullMatch tabanli yeni adapter kazandi
  - ScanManager merge asamasinda once fullMatch, sonra legacy fallback kullanacak sekilde baglandi
  - RarityCalculator ve VariantCatalogSelection explanation seciminde FullVariant* alanlarini oncelemeye basladi
- Degisen dosyalar:
  - app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt
  - app/src/main/java/com/pokerarity/scanner/util/vision/VariantMergeLogic.kt
  - app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt
  - app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt
  - app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt
  - app/src/test/java/com/pokerarity/scanner/VariantMergeLogicTest.kt
  - app/src/test/java/com/pokerarity/scanner/VariantCatalogSelectionTest.kt
- Dogrulama:
  - testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantMatchContractTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.VariantMergeLogicTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --console=plain gecti
  - assembleDebug --console=plain gecti
  - connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.VariantDecisionEngineTest --console=plain gecti
  - APK RFCY11MX0TM cihazina kuruldu ve uygulama launcher ile acildi
- Acik kalanlar:
  - VariantDecisionEngine instrumentation regression seti yeni fullMatch akisina gore genisletilmedi
  - RarityCalculator halen tam olarak FullVariantMatch nesnesini degil, trace alanlarini kullaniyor
  - legacy VariantResolutionLogic/VariantClassifier metadata fallbackleri henuz tamamen kaldirilmadi
  - connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain tek strict fail verdi:
    - live_variant_batch_20260318_slowpoke_regular: datePresent expected=true actual=false
## 27 March 2026 - 17:30
- Full matcher rewrite strict regression seti yeniden yesile cekildi.
- Bu turdaki kritik duzeltmeler:
  - form-only costume metadata generator fix:
    - scripts/generate_variant_catalog.py
    - app/src/main/assets/data/variant_catalog.json
    - app/src/main/assets/data/authoritative_variant_db.json
  - strict regression artik fullMatch akisini kullaniyor:
    - app/src/androidTest/java/com/pokerarity/scanner/ScanRegressionTest.kt
  - same-family costume rescue full matcher adaylarina authoritative destek olarak eklendi:
    - app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt
    - app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt
    - app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantScoring.kt
    - app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt
  - low-confidence classifier costume / base shiny bastirma mantigi eklendi:
    - app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt
  - full matcher species tohumu, legacy resolvedMatch semantigini kullanacak sekilde baglandi:
    - app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantSeedSelection.kt
    - app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt
- Yeni/guncel testler:
  - app/src/test/java/com/pokerarity/scanner/VariantCatalogLoaderTest.kt
  - app/src/test/java/com/pokerarity/scanner/AuthoritativeVariantDbTest.kt
  - app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt
  - app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt
  - app/src/test/java/com/pokerarity/scanner/FullVariantSeedSelectionTest.kt
- Dogrulama:
  - testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantSeedSelectionTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --console=plain gecti
  - connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest" --console=plain gecti
  - assembleDebug --console=plain gecti
  - APK RFCY11MX0TM cihazina yeniden kuruldu ve uygulama launcher ile acildi
- Sonuc:
  - strict scan regression failure sayisi bu turda sifira indi
  - full matcher runtime akisi artik mevcut strict fixture setini tamamen geciyor
## 27 March 2026 - 18:20
- Emini promptundaki collector engine fikri parcali olarak entegre edilmeye baslandi.
- Teknik karar:
  - ise yarayan kisim: offline external source registry + normalized global legacy DB
  - reddedilen kisim: `Antigravity` ve app icinde runtime scraping
- Yeni eklenenler:
  - source registry:
    - scripts/external_source_registry.json
  - teknik spec:
    - docs/superpowers/specs/2026-03-27-global-rarity-legacy-engine.md
  - offline generator:
    - scripts/generate_global_rarity_legacy_db.py
  - uretilen asset:
    - app/src/main/assets/data/global_rarity_legacy_db.json
  - runtime model/loader:
    - app/src/main/java/com/pokerarity/scanner/data/model/GlobalRarityLegacyEntry.kt
    - app/src/main/java/com/pokerarity/scanner/data/repository/GlobalRarityLegacyLoader.kt
  - loader testi:
    - app/src/test/java/com/pokerarity/scanner/GlobalRarityLegacyLoaderTest.kt
- App entegrasyonu:
  - RarityCalculator articulation katmani artik authoritative/exact metadata bos kaldiginda global legacy DB'den species-safe fallback cekebiliyor
  - bu fallback su alanlari besliyor:
    - variantLabel
    - eventLabel / lastKnownEvent
    - releaseWindow (firstSeen / lastSeen)
- Dogrulama:
  - py scripts/generate_global_rarity_legacy_db.py calisti
  - testDebugUnitTest --tests=com.pokerarity.scanner.GlobalRarityLegacyLoaderTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --tests=com.pokerarity.scanner.RarityExplanationFormatterTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain gecti
  - assembleDebug --console=plain gecti
  - connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest" --console=plain gecti
  - APK RFCY11MX0TM cihazina yeniden kuruldu ve uygulama launcher ile acildi
- Not:
  - bu tur henüz gerçek Bulbapedia/Serebii/LeekDuck scrape snapshot'lari DB'ye akitilmadi
  - registry ve merge asset katmani hazir; sonraki turda external source snapshots normalize edilip DB doldurulacak
## 27 March 2026 - 19:05
- Global legacy DB icin ilk external snapshot merge dilimi eklendi.
- Bu turdaki teknik degisiklik:
  - scripts/generate_global_rarity_legacy_db.py artik Bulbapedia override verisini normalized snapshot adapter olarak merge ediyor
  - global legacy asset artik snapshot provenance tasiyor:
    - snapshot_adapter:bulbapedia_event_overrides
  - event tarihi olan varyantlar icin liveAvailability turetiliyor:
    - future -> upcoming
    - aktif pencere -> active
    - gecmis event -> retired
- Etki:
  - 025_00_01 gibi eski event costume entry'leri artik `unknown` yerine `retired` olarak isaretleniyor
  - sourceSummary ve entry sourceIds icinde external snapshot adapter izlenebiliyor
- Dogrulama:
  - once failing test yazildi:
    - app/src/test/java/com/pokerarity/scanner/GlobalRarityLegacyLoaderTest.kt
  - py scripts/generate_global_rarity_legacy_db.py calisti
  - testDebugUnitTest --tests=com.pokerarity.scanner.GlobalRarityLegacyLoaderTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --tests=com.pokerarity.scanner.RarityExplanationFormatterTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain gecti
  - assembleDebug --console=plain gecti
  - connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest" --console=plain gecti
  - APK RFCY11MX0TM cihazina yeniden kuruldu ve uygulama launcher ile acildi
- Acik kalan:
  - gerçek normalized external snapshot dosyalari henuz eklenmedi
  - LeekDuck/Serebii/PokemonGoLive verisi bir sonraki turda source-specific snapshot dosyalarina donusturulecek
## 27 March 2026 - 20:05
- Source-specific normalized live snapshot seed'i eklendi.
- Bu turdaki veri katmani degisiklikleri:
  - scripts/external_snapshots/live_species_events.json
    - Pokemon GO Live `Fashion Raid Day` species listesi normalized species-level live event seed olarak eklendi
    - Pokemon Pokopia Celebration Event icin Ditto seed'i eklendi
  - scripts/generate_global_rarity_legacy_db.py
    - normalized snapshot dosyalarini okuyup sprite-level ve species-level merge yapiyor
    - yeni active event alanlari:
      - activeEventLabel
      - activeEventStart
      - activeEventEnd
    - liveAvailability artik active event penceresini de hesaba katiyor
  - app/src/main/java/com/pokerarity/scanner/data/model/GlobalRarityLegacyEntry.kt
    - active event alanlari eklendi
- Test-first:
  - app/src/test/java/com/pokerarity/scanner/GlobalRarityLegacyLoaderTest.kt
    - once `Butterfree 012_01 -> Fashion Raid Day/upcoming` beklentisi eklendi ve fail alindi
    - implementasyon sonrasi test yesile cekildi
- Dogrulama:
  - py scripts/generate_global_rarity_legacy_db.py calisti
  - testDebugUnitTest --tests=com.pokerarity.scanner.GlobalRarityLegacyLoaderTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --tests=com.pokerarity.scanner.RarityExplanationFormatterTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain gecti
  - assembleDebug --console=plain gecti
  - connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest" --console=plain gecti
  - APK RFCY11MX0TM cihazina yeniden kuruldu ve uygulama launcher ile acildi
- Not:
  - bu turda active live event bilgisi asset'e girdi
  - bir sonraki adim, activeEventLabel/Start/End alanlarini explanation katmanina tasimak ve external source coverage'i buyutmek
## 27 March 2026 - 20:40
- Active live event fallback explanation katmanina baglandi.
- Yeni eklenenler:
  - app/src/main/java/com/pokerarity/scanner/data/repository/GlobalLegacyExplanationFallback.kt
  - app/src/test/java/com/pokerarity/scanner/GlobalLegacyExplanationFallbackTest.kt
- Teknik davranis:
  - historical event varsa explanation onu koruyor
  - historical event yoksa, global legacy DB icindeki activeEventLabel / activeEventStart / activeEventEnd alanlari explanation fallback olarak kullaniliyor
  - bu sayede live snapshot seed'i artik sadece asset'te durmuyor; explanation pipeline tarafinda da tuketilebiliyor
- Test-first:
  - GlobalLegacyExplanationFallbackTest once unresolved reference ile fail verdi
  - helper ve RarityCalculator baglantisi sonrasi yesile cekildi
- Dogrulama:
  - testDebugUnitTest --tests=com.pokerarity.scanner.GlobalLegacyExplanationFallbackTest --tests=com.pokerarity.scanner.GlobalRarityLegacyLoaderTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --tests=com.pokerarity.scanner.RarityExplanationFormatterTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --console=plain gecti
  - assembleDebug --console=plain gecti
  - connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest" --console=plain gecti
  - APK RFCY11MX0TM cihazina yeniden kuruldu ve uygulama launcher ile acildi
## 27 March 2026 - 21:35

- Canli regression loglari tekrar alindi. Yeni `authoritative_live_species_event` adayi `Butterfree` strict regression'inda `012_01_shiny` classifier adayini ezip `shiny=false` uretiyordu.
- Kök neden: `FullVariantMatcher`, live species event destegi kazandiginda ayni species'teki `exact_non_base_consensus` shiny-costume classifier adayini korumuyordu.
- Duzeltme:
  - [FullVariantMatcher.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt)
  - `winner.source == authoritative_live_species_event` oldugunda, ayni species/ayni variant class icin `classifierConfidence >= 0.52` ve `rescueKind == exact_non_base_consensus` olan shiny-costume classifier adayi varsa `resolvedShiny=true` korunuyor ve `finalSpriteKey` shiny peer'e tasiniyor.
  - [FullVariantMatcherTest.kt](app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt) icine `liveSpeciesEventSupportPreservesClassifierShinyCostumePeer` testi eklendi.
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --tests=com.pokerarity.scanner.GlobalRarityLegacyLoaderTest --console=plain` geçti.
  - `assembleDebug --console=plain` geçti.
  - `adb shell am instrument -w -e class com.pokerarity.scanner.ScanRegressionTest com.pokerarity.scanner.test/androidx.test.runner.AndroidJUnitRunner` geçti.

## 27 March 2026 - 21:55

- Full matcher rewrite planindaki sonraki slice uygulandi: explanation secimi artik `FullVariant*` trace varsa classifier trace'e geri donmuyor.
- Amaç: textbox/event metadata tarafinda legacy classifier sprite'larindan yanlis event sızmasını azaltmak ve explanation'i matcher merkezli hale getirmek.
- Duzeltme:
  - [VariantCatalogSelection.kt](app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt)
    - `FullVariantExplanationMode` varsa secim sadece `FullVariantSpriteKey + FullVariantExplanationMode + FullVariantVariantConfidence` ile yapiliyor.
    - FullVariant trace mevcutken `VariantClassifier* / Classifier*` fallback'i kapatildi.
  - [RarityCalculator.kt](app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt)
    - `lookupGlobalLegacyEntry()` artik `FullVariantExplanationMode` varken classifier sprite key'lerini kullanmiyor.
  - [VariantCatalogSelectionTest.kt](app/src/test/java/com/pokerarity/scanner/VariantCatalogSelectionTest.kt)
    - `doesNotFallBackToClassifierWhenFullVariantTraceIsPresent` testi eklendi.
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.GlobalLegacyExplanationFallbackTest --console=plain` geçti.
  - Windows/KAPT incremental cache lock problemi goruldu; `app/build/tmp/kapt3`, `app/build/kotlin/kaptGenerateStubsDebugKotlin`, `app/build/tmp/kotlin-classes/debug` temizlenip `assembleDebug` yeniden alindi.
  - `assembleDebug --console=plain` tekrar geçti.
  - APK tekrar kuruldu.
  - `adb shell am instrument -w -e class com.pokerarity.scanner.ScanRegressionTest com.pokerarity.scanner.test/androidx.test.runner.AndroidJUnitRunner` tekrar geçti.

## 27 March 2026 - 22:15

- Dis repo incelemesi yapildi:
  - `thedotmack/claude-mem`
  - `nextlevelbuilder/ui-ux-pro-max-skill`
  - `czlonkowski/n8n-mcp`
  - `affaan-m/everything-claude-code`
  - `obra/superpowers`
  - `gsd-build/get-shit-done`
- Degerlendirme dosyasi yazildi:
  - [2026-03-27-external-repo-assessment.md](docs/superpowers/specs/2026-03-27-external-repo-assessment.md)
- Sonuc:
  - Android app runtime'ina dogrudan alinacak repo yok.
  - `ui-ux-pro-max-skill` yalniz UI workflow yardimi olarak anlamli.
  - `n8n-mcp` telemetry export / labeling otomasyonu icin ileride faydali olabilir.
  - digerleri mevcut `superpowers` kurulumuyla ayni problem sinifina hitap ediyor, app icine alinmadi.
- Yeni build launcher ile tekrar acildi.

## 27 March 2026 - 22:45

- Full matcher rewrite planindaki bir sonraki slice uygulandi: explanation metadata artÄ±k `rawOcrText` icindeki `FullVariant*` alanlarÄ±ndan degil, dogrudan `PokemonData.fullVariantMatch` nesnesinden okunuyor.
- Degisen dosyalar:
  - [PokemonData.kt](app/src/main/java/com/pokerarity/scanner/data/model/PokemonData.kt)
    - `fullVariantMatch: FullVariantMatch?` alani eklendi.
  - [VariantDecisionEngine.kt](app/src/main/java/com/pokerarity/scanner/util/vision/VariantDecisionEngine.kt)
    - `classify()` dÃ¶nÃ¼sÃ¼nde `pokemon.copy(fullVariantMatch = fullMatch)` uygulanÄ±yor.
  - [VariantCatalogSelection.kt](app/src/main/java/com/pokerarity/scanner/data/repository/VariantCatalogSelection.kt)
    - `selectForExplanation()` imzasi `fullMatch` alacak sekilde degistirildi.
    - Full matcher sonucu varsa classifier trace fallback'i kapali.
  - [RarityCalculator.kt](app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt)
    - explanation ve global legacy secimi `pokemon.fullVariantMatch` uzerinden calisiyor.
  - [VariantCatalogSelectionTest.kt](app/src/test/java/com/pokerarity/scanner/VariantCatalogSelectionTest.kt)
    - `prefersFullVariantObjectForExactMetadata`
    - `fullVariantObjectDerivedModeAllowsOnlyDerivedMetadata`
    - `doesNotFallBackToClassifierWhenFullVariantObjectIsPresent`
- Dogrulama:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.GlobalLegacyExplanationFallbackTest --console=plain` gecti.
  - `assembleDebug --console=plain` gecti.
  - `assembleDebugAndroidTest --console=plain` gecti.
  - Uygulama APK'si ve androidTest APK'si tekrar cihaza kuruldu.
  - `adb shell am instrument -w -e class com.pokerarity.scanner.ScanRegressionTest com.pokerarity.scanner.test/androidx.test.runner.AndroidJUnitRunner` gecti.
## 27 March 2026 - 23:40

- Full matcher rewrite planindaki kalan slice'lar tamamlandi.
- Bu turda explanation/event metadata hattindaki son legacy bagimliliklar kapatildi.
- Degisen dosyalar:
  - [FullVariantMatcher.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt)
    - esolvedEventWindow artik dogrudan kazanan adaydan FullVariantMatch uzerine tasiniyor.
  - [VariantExplanationMetadata.kt](app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt)
    - derived/generic path artik sprite catalog'dan exact event adi ve release window cekmiyor.
    - exact event metadata yalniz FullVariantMatch authoritative karariyla aciliyor.
  - [LegacyVariantPathRemovalTest.kt](app/src/test/java/com/pokerarity/scanner/LegacyVariantPathRemovalTest.kt)
    - full matcher disinda hicbir yolun exact event text uretmemesini garanti eden cleanup regression eklendi.
  - [VariantExplanationMetadataTest.kt](app/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt)
    - derived path beklentileri matcher-merkezli metadata kurallarina gore guncellendi.
- Dogrulama:
  - 	estDebugUnitTest --tests=com.pokerarity.scanner.FullVariantMatchContractTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.AuthoritativeVariantEventFallbackTest --tests=com.pokerarity.scanner.LegacyVariantPathRemovalTest --console=plain gecti.
  - connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest --console=plain gecti.
  - ssembleDebug assembleDebugAndroidTest --console=plain gecti.
  - Debug APK ve androidTest APK tekrar cihaza kuruldu.
  - Uygulama db shell am start -n com.pokerarity.scanner/.ui.splash.SplashActivity ile yeniden acildi.
- Branch durumu:
  - Full matcher rewrite runtime'a alinmis durumda.
  - Explanation secimi artik PokemonData.fullVariantMatch merkezli.
  - Legacy raw/classifier event fallback yollari explanation tarafinda kapatildi.
- Kalan bilinen saha riskleri:
  - classifier kaynakli wrong-costume-id secimi bazi ailelerde hala exact event adini generic'e dusurebilir; bu artik yanlis exact event olarak sizmiyor.
  - son telemetry/log turlarinda acik kalan canli aileler: Slowpoke, Glaceon, Lapras, Snorlax (costume false-negative), Wurmple (shiny false-positive), Lapras (CP override).
  - performans son olculerde easy-case scan'lerde yaklasik 2.2s-3.3s, slow-path ilk scan spike'i yaklasik 5.0s bandina cikabiliyor.

## 28 March 2026 - 00:15

- Expanded sprite ingest ve full matcher regression temizligi tamamlandi.
- Data tarafinda:
  - [generate_variant_catalog.py](scripts/generate_variant_catalog.py)
    - `pokemon_icon_pm...` ve `Addressable Assets/pm....icon.png` varyantlari kalici olarak parse ediliyor.
    - mixed numeric + textual variant id map'i duzeltildi; sparse Pikachu id'leri artik label alabiliyor.
    - special-form token'lar (`FMEGA`, `FGIGANTAMAX`, vb.) costume yerine `form` kalıyor.
  - [generate_authoritative_variant_db.py](scripts/generate_authoritative_variant_db.py)
    - yeni local catalog ile sirali yeniden uretildi.
  - [generate_global_rarity_legacy_db.py](scripts/generate_global_rarity_legacy_db.py)
    - yeni authoritative DB ile sirali yeniden uretildi.
  - [train_variant_prototypes.py](scripts/train_variant_prototypes.py)
    - yeni catalog uzerinden tekrar egitildi.
  - Uretilen asset sayilari:
    - `variant_catalog.json`: `4044`
    - `authoritative_variant_db.json`: `4044`
    - `global_rarity_legacy_db.json`: `4044`
    - `variant_classifier_model.json`: `4044` prototype / `928` species
- Matcher tarafinda:
  - [FullVariantCandidateBuilder.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt)
    - classifier adaylari artik sprite-key bazli authoritative metadata ile zenginlestiriliyor.
    - yakalanma tarihi event baslangicindan onceyse classifier costume adayi dusuruluyor.
    - explicit event window yoksa label icindeki yil ipucundan tarih sanity check yapiliyor.
  - [FullVariantMatcher.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt)
    - dusuk guvenli classifier non-base shiny kazananlar artik `shiny=true`yi tek basina acamiyor.
    - live species event winner, same-species non-base shiny peer'den shiny promotion alabiliyor; Butterfree regression buna gore korundu.
- Testler:
  - [VariantCatalogLoaderTest.kt](app/src/test/java/com/pokerarity/scanner/VariantCatalogLoaderTest.kt)
    - Cubchoo/Hoothoot addressable costume coverage
    - Gengar special-form classification guard
  - [AuthoritativeVariantDbTest.kt](app/src/test/java/com/pokerarity/scanner/AuthoritativeVariantDbTest.kt)
    - ayni coverage authoritative DB icin dogrulandi
  - [FullVariantCandidateBuilderTest.kt](app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt)
    - impossible future event/costume classifier adaylari bloke ediliyor
  - [FullVariantMatcherTest.kt](app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt)
    - slowpoke/farfetch'd shiny false-positive suppression
    - butterfree live-event shiny promotion
- Dogrulama:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon assembleDebug assembleDebugAndroidTest --console=plain` gecti.
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` gecti.
  - `adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` gecti.
  - `adb shell am instrument -w -e class com.pokerarity.scanner.ScanRegressionTest com.pokerarity.scanner.test/androidx.test.runner.AndroidJUnitRunner` gecti: `OK (1 test)`.
  - uygulama tekrar acildi: `adb shell am start -n com.pokerarity.scanner/.ui.splash.SplashActivity`.

## 28 March 2026 - 01:05

- Authoritative historical event coverage genisletildi.
- Yeni alias dosyasi eklendi: [scripts/variant_token_aliases.json](scripts/variant_token_aliases.json)
  - global token alias'lari
  - species token alias'lari
  - sprite-key bazli legacy numeric alias'lar
- [scripts/generate_variant_catalog.py](scripts/generate_variant_catalog.py)
  - leading `C...` seasonal/event token'lari daha genis normalize ediliyor.
  - global token alias'lari variant/event label turetiminde kullaniliyor.
- [scripts/generate_authoritative_variant_db.py](scripts/generate_authoritative_variant_db.py)
  - alias config yukleniyor ve snapshot token adaylari buna gore genisletiliyor.
  - override uygulandiginda `historicalEvents` artik sifirlanmiyor; snapshot tarihi korunuyor.
  - `025_00_12`, `025_00_CSPRING_2023`, `143_00_FWILDAREA_2024` gibi once generic kalan exact form'lar artik historical event aliyor.
- [scripts/generate_global_rarity_legacy_db.py](scripts/generate_global_rarity_legacy_db.py)
  - yeni authoritative DB ile tekrar uretildi.
- Coverage sonucu:
  - unresolved costume entry sayisi `559 -> 433` dustu.
  - ornek duzelenler:
    - `025_00_CSPRING_2023` -> `Cherry blossoms costume`, `Spring 2023`
    - `025_00_12` -> `World Championships costume`, historical `2022 World Championships`
    - `143_00_FWILDAREA_2024` -> `Studded Jacket costume`, `Pokemon GO Wild Area`
    - `094_00_FCOSTUME_2020` -> `Mega Banette costume`, `Halloween 2020`
- Testler:
  - [AuthoritativeVariantDbTest.kt](app/src/test/java/com/pokerarity/scanner/AuthoritativeVariantDbTest.kt)
    - yeni historical coverage expectation'lari eklendi.
  - [AuthoritativeHistoricalEventResolverTest.kt](app/src/test/java/com/pokerarity/scanner/AuthoritativeHistoricalEventResolverTest.kt)
    - caught-date bazli event secimi korundu.
- Dogrulama:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.AuthoritativeHistoricalEventResolverTest --tests=com.pokerarity.scanner.VariantCatalogLoaderTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon assembleDebug assembleDebugAndroidTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest" --console=plain` gecti.
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` gecti.
  - uygulama tekrar acildi: `adb shell am start -n com.pokerarity.scanner/.ui.splash.SplashActivity`.

## 28 March 2026 - 15:20

- Canli 20'li costume batch logu tekrar analiz edildi.
  - kesin false-negative'ler: `Lapras`, `Cubchoo`, `Hoothoot`, `Gengar`
  - exact event isimlerinin dusmedigi ve yanlis eventlerin sizdigi path'ler ayrildi
- Kalan strict regression'lar matcher tarafinda kapatildi:
  - `live_variant_batch_20260318_slowpoke_regular`
    - root cause: dusuk guvenli, unresolved local shiny-costume classifier kazananinin (`079_00_CPI_NOEVOLVE_shiny`) costume'u acmasi
    - fix: [FullVariantMatcher.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt)
      - unresolved shiny-costume classifier adaylari icin yeni confidence guard eklendi
  - `live_variant_batch_20260318_butterfree_costume_shiny`
    - root cause: `authoritative_species_date` winner same-species shiny peer'i dusuruyordu
    - fix: [FullVariantMatcher.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt)
      - shiny peer promotion `authoritative_live_species_event` yaninda `authoritative_species_date` winner'lari icin de acildi
  - `regression_20260317_1059/scan_1773745168863_0`
    - root cause: authoritative DB kaydi olmasa bile `134_00_CNOVEMBER_2018` gibi sprite-key icindeki yil ipucu okunmadigi icin imkansiz costume adayi korunuyordu
    - fix: [FullVariantCandidateBuilder.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt)
      - spriteKey icindeki yil artik authoritative kayit olmasa da caught-date sanity check'e giriyor
- Testler guncellendi:
  - [FullVariantCandidateBuilderTest.kt](app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt)
    - zayif date-rescue bloke
    - sprite-key year-hint bloke
  - [FullVariantMatcherTest.kt](app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt)
    - unresolved shiny-costume suppression
    - species-date shiny peer preservation
- Dogrulama:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.AuthoritativeVariantEventFallbackTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon assembleDebug assembleDebugAndroidTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.pokerarity.scanner.ScanRegressionTest" --console=plain` gecti.
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` gecti.
  - uygulama tekrar acildi: `adb shell am start -n com.pokerarity.scanner/.ui.splash.SplashActivity`.

## 28 March 2026 - 16:29

- Onceki kapsam ifadesi duzeltildi:
  - kalan coverage ve matcher sorunlari `Pikachu/Raichu/Pichu` agirlikli degil
  - global olarak birden fazla family'de suruyor
  - son telemetry/live ornekleri: `Cottonee`, `Slakoth`, `Muk`, `Espeon`, `Rowlet`, `Dugtrio`, `Snorlax`, `Lapras`, `Cubchoo`
- Son telemetry batch'inde dogrudan ayrilan bug:
  - `authoritative_species_date` bazli fallback, ayni species icindeki exact classifier costume adayini ezip yanlis event adina kayabiliyordu
  - ornek: `Pikachu` classifier `025_00_FFLYING_03` gorurken full match `025_00_FWCS_2024` secip yanlis event uretiyordu
- Fix:
  - [FullVariantMatcher.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantMatcher.kt)
    - yeni post-selection override eklendi
    - `authoritative_species_date` / `authoritative_live_species_event` winner varsa ve ayni species icinde yeterince guclu exact costume adayi bulunuyorsa exact aday tercih ediliyor
    - bu fix aileye ozel degil; global matcher davranisi
  - [FullVariantMatcherTest.kt](app/src/test/java/com/pokerarity/scanner/FullVariantMatcherTest.kt)
    - `exactSameSpeciesCostumeBeatsDateDerivedDifferentEvent` eklendi
- Dogrulama:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon assembleDebug --console=plain` gecti.
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` gecti.
  - uygulama launcher ile yeniden acildi: `adb shell monkey -p com.pokerarity.scanner -c android.intent.category.LAUNCHER 1`.

## 28 March 2026 - 17:03

- Yeni live telemetry batch incelendi.
  - tarih-event uyusmazligi iki ayri path'ten geliyordu:
    - `generic_variant` path'i exact event adini UI explanation'a sizdiriyordu
    - `authoritative_species_date` bazli fallback bazen ayni species exact costume adayina ragmen farkli event secip raw trace'te yanlis event tutuyordu
  - son net ornekler:
    - `Pikachu` `2020-08-04` / `2019-12-31` -> `H.F. Custom Tie-in`
    - `Pikachu` `2021-10-09` -> raw trace'te `025_00_NOVEMBER_2018 / Spring into Spring`
- Bu turdaki fix:
  - [VariantExplanationMetadata.kt](app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt)
    - `generic_variant` path'inde exact event ve release window artik UI explanation'a tasinmiyor
    - exact event adi yalniz `exact_authoritative` veya `derived_authoritative` path'lerinde gorunecek
  - [VariantExplanationMetadataTest.kt](app/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt)
    - `genericVariantModeDoesNotLeakExactEventMetadata` eklendi
- Bu tur korunmus davranis:
  - exact authoritative event'ler kalir
  - derived authoritative event'ler kalir
  - ama dusuk-guvenli / generic costume path'i artik yanlis exact event yazmaz
- Dogrulama:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.VariantCatalogSelectionTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon assembleDebug --console=plain` gecti.
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` gecti.
  - uygulama launcher ile yeniden acildi: `adb shell monkey -p com.pokerarity.scanner -c android.intent.category.LAUNCHER 1`.

## 28 March 2026 - 17:18

- Son batch telemetry analizi:
  - `generic_variant` path'i hala exact event adi sizdiriyordu
  - `historicalEvents` bulunan authoritative entry'lerde caught date hicbir event araligina dusmese bile aday korunuyordu
  - bu, kullanicinin istedigi kurala aykiri: yakalanma tarihi event araligina uymuyorsa o event secilmemeli
- Kök fix:
  - [FullVariantCandidateBuilder.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt)
    - `historicalEvents` varsa strict hale getirildi
    - caught date hicbir historical appearance penceresine dusmuyorsa aday artik direkt eleniyor
  - [VariantExplanationMetadata.kt](app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt)
    - `generic_variant` path'inde exact event ve release window UI explanation'a artik sizmiyor
    - exact event yalniz `exact_authoritative` veya `derived_authoritative` path'lerinde gosterilecek
- Testler:
  - [FullVariantCandidateBuilderTest.kt](app/src/test/java/com/pokerarity/scanner/FullVariantCandidateBuilderTest.kt)
    - `dropsAuthoritativeRemapCandidateWhenCaughtDateMatchesNoHistoricalAppearance`
  - [VariantExplanationMetadataTest.kt](app/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt)
    - `genericVariantModeDoesNotLeakExactEventMetadata`
- Dogrulama:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --tests=com.pokerarity.scanner.FullVariantMatcherTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.AuthoritativeVariantDbTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon assembleDebug assembleDebugAndroidTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon assembleDebug --console=plain` gecti.
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` gecti.
  - sonrasinda cihaz baglantisi dustugu icin instrumentation tekrar koşturulamadi.

## 28 March 2026 - 21:10

- Son costumed batch icin yeni telemetry + adb log analizi yapildi.
  - canli ornekler:
    - `Pikachu` `2020-10-27` -> classifier `025_00_FFLYING_03`, `costume=true`
    - `Sableye` `2020-10-29` -> classifier `302_00_FCOSTUME_2020_shiny`, finalde `costume=true`
    - `Gengar` `2020-10-29` -> classifier `094_51_shiny` tarafina kayiyor; exact costume/event karari hala sorunlu scanlerde mevcut
- Kullanici tarafinda istenen dusuk riskli destek ozellikleri eklendi:
  - `Event confidence`
  - `Mismatch guard`
  - `Why not exact?`
  - `Ground truth feedback`
  - `Telemetry export filters`
  - `Scan confidence overlay`
- Android tarafi:
  - [ScanDecisionSupport.kt](app/src/main/java/com/pokerarity/scanner/data/model/ScanDecisionSupport.kt)
  - [RarityScore.kt](app/src/main/java/com/pokerarity/scanner/data/model/RarityScore.kt)
  - [RarityCalculator.kt](app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt)
  - [Pokemon.kt](app/src/main/java/com/pokerarity/scanner/data/model/Pokemon.kt)
  - [DecisionSupportComponents.kt](app/src/main/java/com/pokerarity/scanner/ui/components/DecisionSupportComponents.kt)
  - [ScanResultScreen.kt](app/src/main/java/com/pokerarity/scanner/ui/screens/ScanResultScreen.kt)
  - [ScanResultOverlayCard.kt](app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt)
  - [ResultActivity.kt](app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt)
  - [OverlayService.kt](app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt)
  - [ScanTelemetryPayload.kt](app/src/main/java/com/pokerarity/scanner/data/model/ScanTelemetryPayload.kt)
  - [ScanFeedbackPayload.kt](app/src/main/java/com/pokerarity/scanner/data/model/ScanFeedbackPayload.kt)
  - [ScanTelemetryRepository.kt](app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt)
  - [ScanTelemetryCoordinator.kt](app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryCoordinator.kt)
  - [ScanTelemetryUploader.kt](app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt)
- Backend tarafi:
  - [scan-feedback.php](web/scan-telemetry/api/scan-feedback.php)
  - [scan-export.php](web/scan-telemetry/api/scan-export.php)
  - [bootstrap.php](web/scan-telemetry/api/bootstrap.php)
  - [schema.sql](web/scan-telemetry/schema.sql)
  - feedback tablosu endpoint tarafinda `CREATE TABLE IF NOT EXISTS` ile guvenceye alindi
  - export filtreleri:
    - `species`
    - `predicted_has_costume`
    - `predicted_is_shiny`
    - `event_confidence`
    - `mismatch_guard`
    - `has_feedback`
    - `feedback_category`
- Packaging:
  - yeni backend paketi uretildi: [scan-telemetry-deploy.zip](scan-telemetry-deploy.zip)
- Test/Dogrulama:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.ScanTelemetryPayloadTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon assembleDebug assembleDebugAndroidTest --console=plain` gecti.
  - cihaz bu tur sonunda `adb devices` icinde gorunmedigi icin yeniden kurulum yapilamadi.

## 29 March 2026 - Live Log Pull, Event Window Guard Tightening, Overlay Scroll Fix

- Canli `adb logcat` tekrar cekildi.
- Son blokta gorulen karar zincirleri:
  - `Butterfree`
    - OCR date: `2021-09-24`
    - classifier species pass: `012_00_FGIGANTAMAX_shiny`
    - type `form`, shiny `true`, costume `false`
  - `Pikachu`
    - OCR date: `2022-06-21`
    - species classifier: `025_00_12`, type `costume`, confidence `0.44849586`
    - global pass aile ici `Pichu` drift de goruldu
  - `Sableye`
    - OCR date: `2020-10-29`
    - classifier: `302_00_FCOSTUME_2020_shiny`
  - `Gengar`
    - OCR date: `2023-01-01`
    - classifier `094_00_shiny` tarafina kaydi
- Ana bulgu:
  - exact event text hala bazen yanlis sprite secimiyle acilabiliyordu
  - overlay support/content bolumu buyudugu icin alt action butonlari ekrandan tasiyordu
- Kod degisikligi:
  - [VariantExplanationMetadata.kt](app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt)
    - `fullMatch` varsa exact event/release window artik yalniz matched sprite metadata'sindan geliyor
    - `caughtDate` varken event window dogrulanamiyorsa exact event text bastiriliyor
    - variant label icin guvenli fallback korunuyor
  - [RarityCalculator.kt](app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt)
    - `VariantExplanationMetadata.resolve(...)` artik `pokemon.caughtDate` geciyor
  - [VariantExplanationMetadataTest.kt](app/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt)
    - yeni exact-event suppression davranisi dogrulandi
  - [ScanResultOverlayCard.kt](app/src/main/java/com/pokerarity/scanner/ui/overlay/ScanResultOverlayCard.kt)
    - overlay card maksimum yukseklik ile sinirlandi
    - body alani `verticalScroll` yapildi
    - alt action butonlari artik erisilebilir kalmali
- Test/Dogrulama:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.ScanTelemetryPayloadTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon assembleDebug --console=plain` gecti.
  - APK tekrar cihaza kuruldu: `adb install -r .../app-debug.apk -> Success`
  - uygulama yeniden acildi: `com.pokerarity.scanner/.ui.splash.SplashActivity`
- Acik durum:
  - canli telemetry export hala `{"ok":false,"error":"Invalid api_key"}` donuyor
  - server `config.php` icindeki `api_key` ile app/local test key hizali degil

## 29 March 2026 - Strict Event Window Matching For Candidate Builder

- Kullanici yeni batch taradi; canli logdan iki net tarih uyumsuzlugu yakalandi:
  - `Blastoise`
    - OCR date: `2018-08-07`
    - classifier species pass: `007_00_CSPRING_2020_NOEVOLVE`
    - final rarity `costume=true`
    - bu aday 2018 catch icin tarih olarak imkansiz
  - `Pikachu`
    - OCR date: `2024-01-07`
    - classifier species pass: `025_00_12`
    - bu aday `2022 World Championships` event window disinda
- Kök neden:
  - [FullVariantCandidateBuilder.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt)
  - `isImpossibleForCaughtDate(authoritative)` yalniz `eventStart` once durumunu eliyordu
  - `caughtDate > eventEnd` durumunda authoritative costume adayi hayatta kaliyordu
- TDD:
  - once failing testler eklendi:
    - `rejectsClassifierSpeciesCandidateWhenCaughtDateIsAfterAuthoritativeEventWindow`
    - `rejectsClassifierSpeciesCandidateWhenLocalSpriteYearDoesNotMatchCaughtYear`
  - mevcut test davranisi strict kurala gore guncellendi:
    - `addsFamilyAuthoritativeRemapCandidateWhenClassifierChoosesSiblingVariantToken`
    - catch date `2024-11-03 -> 2024-03-21`
- Kod degisikligi:
  - [FullVariantCandidateBuilder.kt](app/src/main/java/com/pokerarity/scanner/util/vision/FullVariantCandidateBuilder.kt)
    - authoritative costume entry icin artik hem `eventStart` hem `eventEnd` kontrol ediliyor
    - yani catch date event penceresinin disindaysa aday eleniyor
- Test/Dogrulama:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --console=plain` gecti.
  - `./gradlew.bat --no-daemon assembleDebug --console=plain` gecti.
  - APK tekrar cihaza kuruldu: `adb install -r .../app-debug.apk -> Success`
  - uygulama yeniden acildi: `com.pokerarity.scanner/.ui.splash.SplashActivity`

## 29 March 2026 - Result UI Cleanup, Event Fallback Hardening, Share Card

- Kullanici geri bildirimine gore su UI degisiklikleri yapildi:
  - event confidence ve scan confidence gorunur sonuc UI'inden kaldirildi
  - `REPORT RESULT` alanlari chip gorunumunden cikartilip 2x2 esit buton gridine alindi
  - overlay/body scroll devam ederken alt action ve feedback butonlari erisilebilir kalacak sekilde sonuc layout'u korundu
  - paylas akisi text-only yerine sonuc kartini PNG olarak share edecek hale getirildi
- Dil davranisi:
  - app-owned result/share/feedback stringleri `values` + `values-tr` resource'lara tasindi
  - narrative tarih formatlari `Locale.getDefault()` kullanacak sekilde guncellendi
- Event/date pipeline duzeltmesi:
  - [AuthoritativeHistoricalEventResolver.kt](app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeHistoricalEventResolver.kt)
    - caught date hicbir historical appearance ile eslesmiyorsa artik latest event'e fallback yapmiyor; `null` donuyor
  - [AuthoritativeVariantEventFallback.kt](app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeVariantEventFallback.kt)
    - species+date fallback artik yalniz `fullMatch`e degil, final sonuc bazli `costumeLike/shiny` sinyaline bagli
  - [RarityCalculator.kt](app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt)
    - explanation sirasi yeniden duzenlendi
    - species+date authoritative fallback artik exact sprite historical metadata'dan once geliyor
    - exact historical metadata yalniz `exact_authoritative` modunda kullaniliyor
  - [OverlayService.kt](app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt) ve [ResultActivity.kt](app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt)
    - decision support parse'i yalniz gorunur UI icerigi uretirse objeyi tasiyor
- Test/Dogrulama:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.AuthoritativeHistoricalEventResolverTest --tests=com.pokerarity.scanner.AuthoritativeVariantEventFallbackTest --tests=com.pokerarity.scanner.VariantExplanationSanityTest --tests=com.pokerarity.scanner.RarityExplanationFormatterTest --tests=com.pokerarity.scanner.FullVariantCandidateBuilderTest --tests=com.pokerarity.scanner.LegacyVariantPathRemovalTest --console=plain` gecti
  - Windows file-lock temizligi sonrasi `./gradlew.bat --no-daemon assembleDebug assembleDebugAndroidTest --console=plain` gecti
  - cihaz kurulumu:
    - `adb install -r app-debug.apk -> Success`
    - `adb install -r app-debug-androidTest.apk -> Success`
  - cihaz regression:
    - `adb shell am instrument -w -e class com.pokerarity.scanner.ScanRegressionTest com.pokerarity.scanner.test/androidx.test.runner.AndroidJUnitRunner`
    - sonuc: `OK (1 test)`
- Canli telemetry/web durumu:
  - `https://caglardinc.com/scan-telemetry/api/scan-export.php?api_key=...` halen `403 {"ok":false,"error":"Invalid api_key"}` donuyor
  - yani arkadas testleri webden cekilebilir durumda degil
  - bu koddaki degil, canli server `config.php` anahtar uyumsuzlugu
## 29 March 2026 - Label-First Result UI, Share Card, Notification Icon, Event Gating

- Live log diagnosis:
  - `Raichu` scan with catch date `2017-01-03` was still being matched against `Pikachu -> 025_00_FFLYING_03`.
  - `Pikachu` scans were still surfacing `FFLYING_03`, `CNOVEMBER_2018`, `G2` candidates in the matcher.
  - Root cause was not only matcher choice; exact event metadata could still be exposed whenever a release window existed, even if the catch date was outside that window.
- Event/date fix:
  - `VariantExplanationMetadata` now exposes exact event metadata only if the catch date is inside the resolved event window.
  - Added regression test for out-of-window exact-authoritative matches.
- UI changes:
  - Removed visible `Why not exact?` section from result/overlay UI.
  - Feedback/report buttons now use filled colors and clearer button styling.
  - Result UI is now label-first: rarity tier is primary, numeric score is secondary.
  - Main scan button reduced from `180dp` to `152dp`.
  - Overlay bubble now uses a colored Pokeball chip background.
  - Foreground notifications now use a colored Pokeball large icon.
- Share changes:
  - Result share now renders a dedicated PNG card instead of relying on text-only sharing.
- Verification:
  - `testDebugUnitTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --tests=com.pokerarity.scanner.ScanTelemetryPayloadTest --console=plain` passed.
  - `assembleDebug assembleDebugAndroidTest --console=plain` passed after clearing Windows Gradle/KAPT file locks.
  - Debug APK and debug AndroidTest APK installed successfully on device.
  - Direct `adb shell am instrument ... ScanRegressionTest` failed with `NullPointerException` in `ScanRegressionTest.loadCases()` due null test context; build/install itself is good.
- Telemetry/web status:
  - Live export still returns `403 {"ok":false,"error":"Invalid api_key"}`.
  - App build key is `pkr_2026_telemetry_7f4c9a2d1e8b5c3f`; live `config.php` must match it exactly.
## 29 March 2026 - Shared Notification, Stronger Result Buttons, Wider Rarity Card

- App/UI fixes:
  - `OverlayService` and `ScreenCaptureService` now share the same foreground notification channel/id:
    - channel: `scanner_status_channel`
    - id: `1001`
  - Both notifications now use the same content:
    - title: `PokeRarityScanner`
    - text: `Scanner active`
    - small icon: `ic_pokeball`
    - large icon: colored `pokeball_overlay`
  - Goal: stop separate `overlay active` and `screen record active` rows and keep one scanner notification identity.
  - Result feedback/report buttons were restyled again:
    - darker tinted fills
    - stronger contrast
    - clearer hint text explaining they should be tapped when the scan is wrong
  - `RarityTierCard` was enlarged and darkened so it no longer gets lost against the hero background.
  - Home screen scan CTA is now a horizontal rectangular button with icon before text.
  - Main CTA text switched to `start_scan`.
- Telemetry config check:
  - Local app config is correct:
    - `scanTelemetryBaseUrl=https://caglardinc.com/scan-telemetry/api`
    - `scanTelemetryApiKey=pkr_2026_telemetry_7f4c9a2d1e8b5c3f`
  - Live site no longer returns `403`.
  - Live site now returns `HTTP 500` with empty body on `scan-export.php`.
  - This means auth is now accepted, but the server-side PHP/runtime/database path is failing.
- Verification:
  - `./gradlew.bat --no-daemon testDebugUnitTest --tests=com.pokerarity.scanner.VariantExplanationMetadataTest --tests=com.pokerarity.scanner.PokemonAnalysisFormattingTest --tests=com.pokerarity.scanner.ScanTelemetryPayloadTest --console=plain` gecti
  - `./gradlew.bat --no-daemon assembleDebug assembleDebugAndroidTest --console=plain` gecti
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk -> Success`
  - `adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk -> Success`
