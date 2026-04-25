# Amaç: Sonraki ajanın projeyi geçmişiyle birlikte devralması için detaylı handover kaydı.

# PokeRarityScanner Handover

Son güncelleme: 2026-04-25

Bu dosya her anlamlı proje değişikliğinden sonra güncellenmelidir. Yeni ajan bu dosyayı
okuduğunda projenin ne olduğu, hangi kararlarla bugüne geldiği, hangi parçaların hassas
olduğu ve bir sonraki güvenli adımın ne olduğu konusunda bağlam kaybetmemelidir.

## Aktif Çalışma Kuralları

- Türkçe konuş; kod, sınıf, fonksiyon ve değişken isimleri İngilizce kalabilir.
- CLAUDE.md önceliklidir: varsayım yapma, belirsizlik varsa dur ve netleştir, cerrahi değişiklik yap.
- AGENTS.md kuralları geçerlidir: değişiklikten önce etkilenen dosyaları belirt, küçük adımlarla ilerle.
- Tek seferde en fazla 1 dosya oluştur veya değiştir; sonra durum raporla ve devam için açık ol.
- `.env` dosyalarına dokunma. Paket kurma veya secret dosyalarina dokunma. 2026-04-25 itibariyla kullanici fix commit/push icin kalici izin verdi.
- Kirli çalışma ağacında kullanıcı değişikliklerini geri alma. Yalnızca açıkça istenirse ve hedef netse revert/temizlik yap.
- Bu dosya yeni kararlar, fixler, ortam sonuçları ve kalan risklerle güncel tutulmalıdır.

## Proje Nedir?

PokeRarityScanner, Pokemon GO ekranındaki görünür Pokemon kartını Android üzerinde canlı
tarayan bir uygulamadır. Amaç, kullanıcının ekrandaki Pokemon'un türünü, varyantını ve
koleksiyon nadirliğini hızlıca görmesidir.

Uygulama şu ana akışa sahiptir:

1. `ScreenCaptureService`, Android `MediaProjection` ile ekran görüntüsü alır.
2. `ScanManager`, gelen kareleri decode eder, OCR ve görsel analiz akışını yönetir.
3. `OCRProcessor`, ML Kit OCR ile CP, HP, ad ve gerekirse yakalanma tarihini okur.
4. `TextParser`, OCR metnini Pokemon adı, CP, HP ve tarih gibi alanlara dönüştürür.
5. `VariantDecisionEngine`, classifier ve metadata sinyallerini birleştirir.
6. `VisualFeatureDetector`, shiny/shadow/lucky/costume/form gibi görsel sinyalleri çıkarır.
7. `PokemonRepository`, event ve canlı event bağlamını çözer.
8. `RarityCalculator`, skor, tier, açıklama ve karar destek bilgisini üretir.
9. `OverlayService` sonucu overlay olarak gösterir; sonuç ayrıca history DB'ye yazılır.
10. Telemetry açıksa `ScanTelemetryCoordinator` sonucu ve tanı bilgisini sunucuya yollar.

## Teknoloji Haritası

- Dil: Kotlin.
- Platform: Android, minSdk 26, targetSdk 35, compileSdk 35.
- Build: Gradle wrapper 8.9, Android Gradle Plugin 8.7.3, Kotlin Android/KAPT 1.9.24.
- JVM: Java 17 hedefleniyor. Yerelde wrapper `C:\jdk-17.0.11+9` kullanıyor; `java` PATH'te görünmeyebilir.
- UI: Jetpack Compose + bazı klasik Android bileşenleri.
- OCR: Google ML Kit Text Recognition.
- Görsel analiz: OpenCV 4.10.0, perceptual hash, renk histogramları, imza tabanlı matcher.
- DB: Room + SQLCipher (`net.zetetic:sqlcipher-android:4.5.4`).
- DI: Hilt.
- JSON: Gson ve `org.json`.
- Telemetry: Uygulama içi encrypted preferences + offline queue + uzak upload endpoint.
- Metadata: `app/src/main/assets/data/` altındaki JSON katalogları ve runtime `RemoteMetadataSyncManager`.

## Ana Dizinler

- `app/src/main/java/com/pokerarity/scanner/service/`: capture, overlay ve scan orkestrasyonu.
- `app/src/main/java/com/pokerarity/scanner/util/ocr/`: ML Kit OCR, crop bölgeleri, preprocessing, text parsing.
- `app/src/main/java/com/pokerarity/scanner/util/vision/`: shiny/costume/form/lucky/shadow, classifier, matcher, pHash.
- `app/src/main/java/com/pokerarity/scanner/data/repository/`: rarity, event, metadata, telemetry repository katmanı.
- `app/src/main/java/com/pokerarity/scanner/data/local/db/`: SQLCipher Room database, DAO ve entity'ler.
- `app/src/main/assets/data/`: authoritative DB, variant catalog, rarity manifest, master pokedex, classifier modelleri.
- `.github/workflows/`: release ve living DB refresh otomasyonları.
- `scripts/`: release publish ve metadata üretim scriptleri.
- `artifacts/`: logcat, diagnostics, agent worklog ve rollback notları.

## Kronolojik Gelişim Özeti

### 2026-03-15 İlk dönem

İlk yedekler ve manuel toparlama ile proje repo halinde korunmaya başladı. O dönemde
Android/Kotlin uygulama iskeleti, UI fikirleri ve scanner yaklaşımı şekilleniyordu.

### 2026-03-31 / 2026-04-01 IV dönemi

İlk büyük fonksiyonlardan biri IV analizi ve rarity skoruna IV katkısıydı.
PR/issue geçmişinde üç kapalı iş var:

- #1: IV analizini rarity score içine dahil etme.
- #2: IV display normalizasyonu ve "Hesaplanamadı" fallback'i.
- #3: IV integration testleri ve IV bonus wiring.

Bu dönemden kalan bazı model alanları hala var:

- `Pokemon.ivText`
- `IvSolveDetails`
- `IvSolveMode`
- `RarityScore.ivEstimate`
- `RarityCalculator.runIVSearch`

Ancak 2026-04-13 recognition-first pivot sonrasında IV ürün yolu fiilen kapandı.
`RarityCalculator.analyzeIV()` şu an sıfır dönen stub:

```kotlin
private fun analyzeIV(pokemon: PokemonData, features: VisualFeatures): IVResult {
    return IVResult(0, null, null, null)
}
```

Bu alanlara dokunurken legacy uyumluluğu düşünülmeli.

### 2026-04-06 Güvenlik ve stabilite dönemi

Öne çıkan işler:

- SQLCipher ile Room database şifreleme.
- Telemetry consent dialog ve encrypted preferences.
- Telemetry API key/base URL'nin encrypted local config'e taşınması.
- Exported component ve overlay güvenliği.
- Rate limiting.
- Startup crash fixleri.

Önemli fix: `SplashActivity` launcher activity olarak `exported=true` oldu. SQLCipher native
init sırası düzeltildi. Eski plaintext DB için recover/delete-recreate yolu eklendi.

`AppDatabase` şu prensiple çalışır:

```kotlin
SqlCipherInitializer.ensureLoaded()
val passphrase = DatabasePassphraseStore.getOrCreate(appContext)
val factory = SupportOpenHelperFactory(passphrase)
var instance = buildDatabase(appContext, factory)
runCatching { instance.openHelper.writableDatabase }
    .onFailure { error ->
        if (isRecoverableDatabaseError(error)) {
            instance.close()
            DatabasePassphraseStore.deleteDatabaseFiles(appContext)
            instance = buildDatabase(appContext, factory)
            instance.openHelper.writableDatabase
        } else {
            throw error
        }
    }
```

Bu alan güvenlik açısından hassastır. DB recovery mantığı değiştirilirken veri kaybı riski
özellikle not edilmelidir.

### 2026-04-07 ile 2026-04-12 OCR ve canlı tarama sertleştirme

Bu dönemde canlı scan sonuçlarında hatalı shiny/costume/form ve IV belirsizliği sorunları
yoğundu. Yapılan ana değişiklikler:

- OCR locked species bilgisini classifier'ın ezmesi azaltıldı.
- Weak live event rescue false costume/form üretmesin diye gate'ler sıkılaştırıldı.
- Shiny fallback için hue support, çoklu sinyal ve confidence eşiği eklendi.
- HP/stardust/candy crop bölgeleri genişletildi.
- Power-up row parsing için noise repair yapıldı.
- Capture/overlay latency azaltıldı.
- `BitmapPool` ve pooled capture path eklendi.
- Android 14 foreground service/mediaProjection iki aşamalı promotion uygulandı.

`ScreenCaptureService` Android 14 için iki aşamalı foreground modelini kullanır:

1. `onCreate()` içinde `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.
2. Projection token geldikten sonra `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`.

Bu sırayı bozmak Android 14+ cihazlarda `SecurityException` üretebilir.

### 2026-04-09 UI ve release süreci

Stitch benzeri dashboard/result UI adaptasyonu yapıldı. Local release publish scriptleri
eklendi:

- `scripts/publish_github_release.ps1`
- `scripts/build_and_publish_release.ps1`

GitHub Actions billing/asset upload sorunlarına karşı local publish fallback geliştirildi.

### 2026-04-13 Recognition-first pivot

Bu projenin en önemli mimari kararıdır. Canlı telemetri IV/power-up pipeline'ının
yavaş ve gürültülü olduğunu gösterdi. Ürün yönü IV/PvP yerine hızlı tanıma ve nadirlik
yorumuna kaydı.

Kaldırılan veya ürün yolundan düşürülen parçalar:

- Tesseract/tessdata yolu.
- `IvCostSolver`.
- `PvPStatEngine`.
- Arc/appraisal analizörleri.
- IV integration testlerinin bir kısmı.

Eklenen/güçlenen parçalar:

- ML Kit-first OCR.
- Dynamic name ROI.
- `RemoteMetadataSyncManager`.
- Recognition summary / decision support.
- Living DB metadata yaklaşımı.

Bu karar nedeniyle yeni özellikler tasarlanırken "IV kesinliği" yerine "recognition-first,
evidence-backed, düşük false-positive" hedeflenmelidir.

### 2026-04-15 Evidence-based recognition ve Living DB

Bu dönemde speculative costume/event hallucination sorunları temizlendi:

- Base/normal sonuç, güçlü kanıt yoksa nadir etikete tercih edilmeli.
- Costume/event label için exact species support, tarih/event window ve güçlü accessory/signature kanıtı gerekir.
- Weak family remap ve speculative live event remap bastırılır.
- `master_pokedex.json`, `costume_signatures.json`, `variant_catalog.json` gibi metadata dosyaları living DB yaklaşımına taşındı.
- `.github/workflows/refresh-living-pokedex.yml` planlandı/eklendi.

Önemli kural: Bir event label gösterilecekse yakalanma tarihi ile release window uyumlu
olmalıdır. `VariantExplanationSanity`, `AuthoritativeHistoricalEventResolver`,
`VariantExplanationMetadata` ve repository fallback'leri bu yüzden hassastır.

### 2026-04-18 / 2026-04-20 son durum

GitHub API'ye göre remote `main` şu anda `4cbf7b96` seviyesine gelmiş görünüyor:

- `80cbdbcc`: high-priority code quality refactor.
- `4cbf7b96`: version bump to 1.8.4.

Yerel repo ise `44d61776` commit'inde:

- Phase2 `hasCostume` threshold'ları gevşetildi.
- `minConfidence: 0.38 -> 0.30`.
- `minMargin: 0.08 -> -0.15`.
- Telemetry consent setter private yapıldı.
- Android compatibility fixleri eklendi.
- Bu commit `v1.8.5` tag'iyle işaretli.

Dikkat: Yerel `HEAD` ve GitHub `main` arasında branch/tag uyumsuzluğu var. Remote tag
`v1.8.5` mevcut görünse de remote `main` yerel `44d61776` commit'inde görünmüyor.

## Güncel Skor Hesabı

README'de eski 0-100 eksenli bir açıklama görülebilir, fakat güncel kodda skor daha
geniş aralıklı ve çarpanlıdır.

Ana denklem:

```kotlin
val resolvedBaseRarity = maxOf(RarityManifestLoader.getSpeciesRarity(speciesName), baseRarity)
val baseScore = (resolvedBaseRarity.coerceAtLeast(0) * 4).coerceAtLeast(0)
val eventBonus = eventWeight.coerceAtLeast(0)

var variantMultiplier = 1.0
if (features.isShadow) variantMultiplier *= 1.5
if (features.isPurified) variantMultiplier *= 1.1
if (features.isLucky) variantMultiplier *= 2.0
if (resolvedShiny) variantMultiplier *= 5.0

val totalScore = ((baseScore + eventBonus) * variantMultiplier)
    .roundToInt()
    .coerceAtLeast(0)
```

Tier eşikleri:

```kotlin
COMMON("Common", 0)
UNCOMMON("Uncommon", 21)
RARE("Rare", 51)
EPIC("Epic", 101)
LEGENDARY("Legendary", 201)
MYTHICAL("Mythical", 401)
GOD_TIER("God Tier", 800)
```

Costume ve form doğrudan çarpan değildir. Bunlar:

- variant/event açıklamasını etkiler,
- event bonus çözümünü etkileyebilir,
- decision support ve UI taglerini etkiler,
- matcher sonucunda `resolvedVariantClass`, `resolvedCostume`, `resolvedForm` olarak taşınır.

## Recognition / Variant Karar Mantığı

Temel ilke: base/normal, kanıtsız nadir etikete tercih edilir.

`VariantDecisionEngine` şunları yapar:

- OCR'dan güçlü species lock var mı bakar.
- Gereksiz global classifier'ı atlayabilir.
- Global ve species-scoped classifier sonucu alır.
- Costume signature evidence hesaplar.
- `FullVariantCandidateBuilder` ile adayları üretir.
- `FullVariantMatcher` ile final varyantı seçer.
- Görsel feature'ları matcher sonucu ile merge eder.

`FullVariantMatcher` içinde costume için güvenlik kapıları özellikle önemlidir:

- Signature match varsa düşük eşikle kabul edilebilir.
- Dated authoritative costume varsa kabul edilebilir.
- High confidence dated classifier remap varsa kabul edilebilir.
- Windowless/speculative/live-event-only costume genelde bastırılır.

Örnek suppression mantığı:

```kotlin
val suppressUnsupportedCostume =
    winner.isCostumeLike &&
        !signatureMatchedWinner &&
        !datedAuthoritativeCostume &&
        !highConfidenceDatedClassifierCostume &&
        winner.source != "signature_species_costume"
```

Bu gate'leri gevşetmek false costume üretme riskini artırır. Son yerel v1.8.5 değişikliği
Phase2 costume kaçırma sorununu çözmek için threshold gevşetmiştir; regression test şarttır.

## OCR Akışı

`OCRProcessor.processImage()` paralel olarak şu işleri başlatır:

- `recognizeCp(bitmap)`
- `collectHpRaws(bitmap)`
- `recognizeName(bitmap)`
- `recognizeDate(bitmap, includeSecondaryFields)`

CP için white mask, high contrast ve adaptive threshold denenir. HP için birden fazla
bölge okunur ve CP-aware HP pair seçimi yapılır:

```kotlin
val pair = TextParseUtils.selectBestHPPairForCp(cp, *raws.toTypedArray())
```

İsim tespiti dynamic ML Kit blocks ile yapılır. Skor >= 0.72 ise dynamic candidate kabul
edilir; yoksa static name crop fallback kullanılır.

Tarih yalnızca `includeSecondaryFields=true` olduğunda okunur. Fast pass çoğu durumda
tarih okumaz; detailed pass gerektiğinde tarih ve daha zengin raw trace üretebilir.

## Capture ve Pipeline Detayları

`ScreenCaptureService.captureSequence()` iki kare alır:

```kotlin
val captureCount = 2
val intervalMs = 80L
```

Kareler cache altına `scan_<timestamp>_<index>.png` olarak yazılır.

`ScanManager.processScanSequence()`:

- Kareleri paralel decode eder.
- OCR'ı sırayla çalıştırır.
- High-confidence frame bulursa erken çıkar.
- Multi-frame fusion yapar.
- Species refiner ve consistency gate uygular.
- Classifier ve visual detection'ı paralel başlatır.
- CP matematiksel doğrulaması yapar.
- Phase2 classifier çalıştırır.
- Rarity hesaplar.
- Önce overlay gösterir, sonra DB save ve telemetry yapar.

Overlay'in önce gösterilmesi latency için bilinçli karardır.

## Metadata ve Living DB

Ana asset dosyaları:

- `authoritative_variant_db.json`
- `bulbapedia_event_pokemon_go.json`
- `costume_signatures.json`
- `event_history.json`
- `global_rarity_legacy_db.json`
- `master_pokedex.json`
- `pokemon_base_stats.json`
- `pokemon_families.json`
- `pokemon_moves.json`
- `rarity_manifest.json`
- `rarity_rules.json`
- `shiny_signatures.json`
- `variant_catalog.json`
- `variant_classifier_model.json`
- `variant_phase2_model.json`
- `variant_registry.json`

Runtime sync:

- `RemoteMetadataSyncManager` manifest URL'ini `Constants.GITHUB_METADATA_MANIFEST_URL` üzerinden indirir.
- Yalnızca HTTPS + trusted GitHub raw host/path kabul eder.
- SHA-256 verilmişse checksum doğrular.
- Dosyaları `context.filesDir/remote_metadata` altına atomik yazar.

Güvenlik kontrolü:

```kotlin
require(scheme == "https")
require(uri.host.equals(Constants.GITHUB_RAW_HOST, ignoreCase = true))
require(uri.path.startsWith(Constants.GITHUB_REPO_PATH_PREFIX))
```

Living DB workflow çalışmıyorsa ilk bakılacak yerler:

- `.github/workflows/refresh-living-pokedex.yml`
- `scripts/generate_master_pokedex.py`
- `metadata_manifest.json`
- GitHub Actions permissions.
- Submodule/external asset path'leri.
- GitHub Actions billing/quota/policy email'leri.

## Telemetry Sistemi

Android tarafındaki parçalar:

- `TelemetryPreferences`: kullanıcı consent durumu.
- `TelemetryConfigPreferences`: base URL ve API key local encrypted config.
- `ScanTelemetryConfig`: runtime telemetry config.
- `ScanTelemetryRepository`: upload kuyruğu ve offline kayıt yönetimi.
- `ScanTelemetryUploader`: HTTP upload client.
- `ScanTelemetryCoordinator`: scan sonrası enqueue/flush orkestrasyonu.
- `TelemetryUploadDao`, `TelemetryUploadEntity`, `OfflineTelemetryDao`, `OfflineTelemetryEntity`: Room queue.

Bilinen geçmiş sorunlar:

- Legacy endpoint 404/403 denemeleri queue'yu bozuyordu.
- Metadata-only upload screenshot zorunluluğuna takılıyordu.
- Eski queued row'lar 422 ile sürekli retry ediyordu.
- API key configured ise unauthenticated probe atlanacak şekilde düzeltilmişti.

Sunucu tarafı kullanıcı notuna göre `caglardinc.com` domaininde, kendi serverında cPanel
üstünde duruyor. Bu repo içinde cPanel kodu henüz kesin konumlandırılmadı; araştırılacak
adaylar:

- `GmailSheetsManager/`
- `scripts/`
- telemetry base URL geçen dosyalar
- `local.properties` içindeki `scanTelemetryBaseUrl` ve `scanTelemetryApiKey` değerleri

Sunucu kodu bulununca şu kontrat doğrulanmalı:

- Android hangi endpoint'e POST atıyor?
- Header/API key formatı ne?
- Multipart mı JSON mu?
- Screenshot opsiyonel mi?
- Metadata-only upload destekleniyor mu?
- Response body `screenshot_url` gibi alanları nasıl dönüyor?
- cPanel PHP/Node/Python runtime sürümü ne?
- Loglar nereye düşüyor?

## Repo ve GitHub Durumu

Remote:

```text
origin https://github.com/chaglaruk/PokemonRarityScanner.git
```

Yerel branch:

```text
main -> local HEAD 44d61776, origin/main'den ahead 1 görünüyor
```

GitHub API'de görülen branch'ler:

- `main`
- `codex/add-new-test-class-for-iv-calculation`
- `codex/update-calculate-method-in-raritycalculator`
- `codex/update-iv-display-and-labeling`

Yerelde ayrıca website worktree branch'leri görünüyor:

- `feat/web-localized-branding`
- `feature/website-jeton-redesign`

Kapalı PR/issue'lar üç adet ve IV dönemiyle ilgili.

## Mevcut Çalışma Ağacı Notu

2026-04-25 kontrolünde çalışma ağacı kirlidir. Kirin çoğu build/Gradle çıktısıdır, fakat
kaynak dosyalarda da değişiklik vardır.

Kaynakta görülen dirty dosyalar:

- `app/src/main/assets/data/variant_phase2_model.json`
- `app/src/main/java/com/pokerarity/scanner/data/local/TelemetryPreferences.kt`
- `app/src/main/java/com/pokerarity/scanner/data/local/db/AppDatabase.kt`
- `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt`
- `app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeHistoricalEventResolver.kt`
- `app/src/main/java/com/pokerarity/scanner/data/repository/RarityManifestLoader.kt`
- `app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt`
- `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- `app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureService.kt`
- `app/src/main/java/com/pokerarity/scanner/util/RateLimiter.kt`

Untracked ama önemli görünen dosyalar:

- `.clinerules/`
- `AGENTS1.md`
- `CLAUDE.md`
- `GmailSheetsManager/`
- `app/src/main/assets/data/pokedex_types.json`
- `app/src/main/java/com/pokerarity/scanner/util/DateParseUtils.kt`
- `copilot-instructions.md`
- `scripts/extract_urls.ps1`

Bu dosyalar kullanıcı işi olabilir. "Temizle" istense bile kaynak dosyaları körlemesine
revert etme. Önce sınıflandır:

1. Build output/cache: temizlenebilir.
2. Generated ama tracked asset: commit/revert kararı gerekir.
3. Source code değişikliği: diff okunmadan temizlenmez.
4. Untracked proje kuralı/doküman/servis kodu: saklanmalı ve kullanıcıya raporlanmalı.

## Doğrulama Sonuçları

2026-04-25 çalıştırılan komutlar:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.pokerarity.scanner.ScanStartupPolicyTest --no-daemon --console=plain
```

Sonuç: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Sonuç: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

Sonuç: exit code 0, release APK üretildi.

Üretilen APK:

```text
app/build/outputs/apk/release/PokeRarityScanner-v1.8.3-release.apk
```

Dikkat: Commit/tag mesajları `v1.8.5` derken `gradle.properties` halen
`pokerarity.versionName=1.8.3` ve `versionCode=19`. Bu sürüm uyumsuzluğu düzeltilmeli.

İlk paralel Gradle denemesinde Kotlin daemon / incremental KAPT geçici dosya hatası
görüldü. `.\gradlew.bat --stop` sonrası tek başına debug build geçti. Gradle komutlarını
bu repo için paralel koşturma; build output state çakışabiliyor.

## Bilinen Uyarılar

Derleme sırasında çok sayıda warning var. Bazıları teknik borç:

- Unused variables: `DataRetentionManager`, `TelemetryPreferences`, `RemoteMetadataSyncManager`.
- Unused params: `RarityCalculator.analyzeIV`, `FullVariantCandidateBuilder`, `VariantDecisionEngine`.
- `RateLimiter.kt` içinde warning: `Type mismatch: inferred type is Long? but Long was expected`.
- Deprecated Gradle features Gradle 9 ile uyumsuz olabilir.
- OpenCV `initDebug()` deprecated.

Bunlar build'i kırmıyor, ama temizlik/refactor işleri ayrı küçük task olarak ele alınmalı.

## Sonraki Güvenli Adımlar

1. Çalışma ağacını temizlemeden önce dirty kaynak diff'lerini sınıflandır.
2. Build/cache çıktıları için güvenli temizlik yap; source değişiklikleri için revert/commit kararı ayrı alın.
3. `gradle.properties` sürümünü tag/branch gerçeğiyle uyumlu hale getir.
4. Remote `main`, local `HEAD`, `v1.8.5` tag ve GitHub Actions durumunu netleştir.
5. Telemetry server kodunu bul, Android upload kontratıyla karşılaştır, cPanel endpoint'i canlı test et.
6. Living DB workflow logs/email nedenini bul; permissions, path, script dependency ve generated asset commit adımlarını kontrol et.
7. Her fix için en az bir hedefli test çalıştır; Gradle build'leri paralel çalıştırma.

## Hızlı Komutlar

Durum:

```powershell
git status --short --branch
git diff --name-status -- app/src/main/java app/src/main/assets/data .github scripts gradle.properties
git log --oneline --decorate --graph --all -n 40
```

Build/test:

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:testDebugUnitTest --tests com.pokerarity.scanner.ScanStartupPolicyTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

GitHub remote kontrol:

```powershell
git fetch --dry-run origin main
git ls-remote --heads --tags origin
```

Telemetry arama:

```powershell
rg -n "scanTelemetry|telemetry|caglardinc|upload|apiKey|baseUrl" .
```

Living DB arama:

```powershell
rg -n "living|metadata|master_pokedex|refresh|workflow|pokedex" .github scripts app/src/main/java metadata_manifest.json
```

## 2026-04-25 Devam Teshisi

Bu bolum, handover olusturulduktan sonra yapilan salt-okuma kontrolleri ve guvenli
temizligi kaydeder.

### Calisma Agaci Temizligi

Kullanici "calisma agaci kirli, temizle" dedi. Once dirty state siniflandirildi.
567 adet `.gradle` ve `app/build` altindaki derleme ciktisi kirliydi. Bunlar
kaynak kod degil, build/cache uretimi oldugu icin su komutla guvenli bicimde
repo durumuna donduruldu:

```powershell
git restore -- .gradle app/build
```

Bu temizlikten sonra kalan kirler gercek kaynak/konfigurasyon degisiklikleri ve
untracked dosyalardir. Bunlar korundu; cunku onceki ajan/kullanici degisikligi
olabilir ve korlemesine revert veri kaybi yaratabilir.

Kalan izlenecek kaynak degisiklikleri:

```text
M  AGENTS.md
M  app/proguard-rules.pro
M  app/src/main/assets/data/variant_phase2_model.json
M  app/src/main/java/com/pokerarity/scanner/data/local/TelemetryPreferences.kt
M  app/src/main/java/com/pokerarity/scanner/data/local/db/AppDatabase.kt
M  app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt
M  app/src/main/java/com/pokerarity/scanner/data/repository/AuthoritativeHistoricalEventResolver.kt
M  app/src/main/java/com/pokerarity/scanner/data/repository/RarityManifestLoader.kt
M  app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt
M  app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt
M  app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureService.kt
M  app/src/main/java/com/pokerarity/scanner/util/RateLimiter.kt
?? .clinerules/
?? AGENTS1.md
?? CLAUDE.md
?? GmailSheetsManager/
?? HANDOVER.md
?? app/src/main/assets/data/pokedex_types.json
?? app/src/main/java/com/pokerarity/scanner/util/DateParseUtils.kt
?? copilot-instructions.md
?? scripts/extract_urls.ps1
```

Not: `GmailSheetsManager/credentials.json` ve `GmailSheetsManager/token.json`
hassas OAuth dosyalari gibi gorunuyor. Bunlari loglama, commit etme veya
otomatik temizleme. Ayrica `GmailSheetsManager/dist/`, `__pycache__/`, `.db`,
`.log` gibi uretim dosyalari da var.

### Telemetry Servisi

Repo icinde PHP backend kaynaklari bulunmadi. Aramada yalnizca Android istemci,
`schema.sql`, plan/spec dokumanlari ve endpoint referanslari bulundu. Beklenen
server dosyalari dokumana gore cPanel tarafinda su yapida olmali:

```text
public_html/scan-telemetry/config.php
public_html/scan-telemetry/api/scan-upload.php
public_html/scan-telemetry/api/scan-feedback.php
public_html/scan-telemetry/api/scan-export.php
```

`local.properties` icindeki `scanTelemetryBaseUrl` ve `scanTelemetryApiKey`
kullanilarak endpoint canli kontrol edildi. Secret deger loglanmadi.

Sonuc:

```text
GET scan-export.php?api_key=[REDACTED]&limit=1 -> HTTP 200
Response: {"ok":true,"count":1,"items":[...]}

GET scan-upload.php -> HTTP 403 {"ok":false,"error":"Invalid api_key"}
GET scan-feedback.php -> HTTP 403 {"ok":false,"error":"Invalid api_key"}
HEAD scan-upload.php -> HTTP 403
HEAD scan-feedback.php -> HTTP 403
```

Bu tablo serverin ayakta oldugunu ve API key korumasinin calistigini gosteriyor.
Export son kayitta `app_version_name=1.8.3`, `device_model=SM-S931B`,
`predicted_species=Slowpoke`, `screenshot_relative_path=scans/2026/04/...png`
gibi alanlar dondurdu; yani veritabani ve export endpoint canli.

Dikkat edilmesi gereken Android tarafli telemetri riski:
`ScanTelemetryRepository.flushPending()` upload oncesinde
`normalizedScreenshotFile(screenshotPath)` zorunlu tutuyor. `screenshotPath`
bos/null veya dosya kayipsa kaydi 422 offline olarak stage edip siliyor. Oysa
`ScanTelemetryUploader.upload()` metadata-only modunu destekliyor. Bu nedenle
screenshotsuz telemetry satirlari servere metadata-only gidemeyebilir. Telemetry
"calismiyor" sikayeti belirli cihazlarda screenshot dosyasi eksilmesi ise fix
muhtemelen burada: repository, null/missing screenshot icin uploader'a
metadata-only entity gonderebilmeli veya enqueue asamasinda screenshot zorunlu
politika netlestirilmeli.

### Living DB GitHub Actions

GitHub API'den son nightly run incelendi:

```text
Workflow: Refresh Living Pokedex
Run id: 24922845691
Run number: 10
Created: 2026-04-25T04:44:36Z
Conclusion: failure
Job id: 72987456060
Job duration: 2026-04-25T04:44:37Z - 2026-04-25T04:44:39Z
```

Check-run annotation asil nedeni verdi:

```text
The job was not started because your account is locked due to a billing issue.
```

Yani gece gelen emailin birincil nedeni kod degil, GitHub hesabi billing lock.
Bu durum GitHub billing/settings tarafindan duzeltilmeden workflow calismaz.

Billing duzeldikten sonra ikinci latent repo sorunu var:

```powershell
git submodule status --recursive
```

komutu su hatayi verdi:

```text
fatal: no submodule mapping found in .gitmodules for path 'external/game_masters'
```

`git ls-files -s external` iki gitlink gosteriyor:

```text
160000 af135a16b7f179996eddbc4dd9ed33f8777a0420 external/game_masters
160000 4b298599e21facbf3f40e3798cd4cf777a4632ee external/pogo_assets
```

Ama repo kokunde `.gitmodules` yok. Remote URL'ler lokal submodule calisma
agacindan dogrulandi:

```text
external/game_masters -> https://github.com/PokeMiners/game_masters.git
external/pogo_assets  -> https://github.com/PokeMiners/pogo_assets.git
```

Workflow `actions/checkout@v4` icinde `submodules: true` kullaniyor. Billing
lock kalkinca `.gitmodules` eksikligi checkout/submodule asamasinda workflow'u
kirmaya aday. Mantikli repo fix'i `.gitmodules` eklemek veya workflow'daki
`submodules: true` ayarini kaldirip ihtiyac olan kaynaklari script ile indirmek.
Bu projede `pogo_assets` imza uretimi icin gerekli oldugundan `.gitmodules`
eklemek daha dogrudan cozumdur.

Ucuncu latent Living DB sorunu: workflow `metadata_manifest.json` icinde yalniz
`version` alanini guncelliyor; sha256 alanlarini yeniden hesaplamiyor. Metadata
dosyalari degisirse `RemoteMetadataSyncManager.validateSha256` runtime'da yeni
metadata'yi reddedebilir. Workflow fix'inde manifest hash'leri de yeniden
hesaplanmali.

## 2026-04-25 Uygulanan Fixler

Kullanici GitHub billing/account lock sorununu cozdurgunu soyledi ve kod
tarafindaki sorunlarin tamamini duzeltmemi istedi.

Uygulanan degisiklikler:

```text
A .gitmodules
M .github/workflows/refresh-living-pokedex.yml
M app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt
M app/src/test/java/com/pokerarity/scanner/ScanTelemetryRepositoryTest.kt
M HANDOVER.md
```

Telemetry fix:

- `ScanTelemetryRepository.flushPending()` artik screenshot dosyasi yok diye
  telemetry satirini 422 offline'a atip silmiyor.
- `prepareUploadEntity()` eklendi. Screenshot dosyasi mevcutsa normal upload
  korunuyor; dosya yoksa `screenshotPath = null` ile metadata-only upload'a
  dusuluyor.
- Basarili metadata-only upload sonrasinda satir kuyruktan silinir; mevcut
  screenshot varsa yine temizlenir.
- `ScanTelemetryRepositoryTest` icine iki test eklendi:
  - missing screenshot path metadata-only icin strip edilir
  - mevcut screenshot path korunur

Living DB fix:

- `.gitmodules` eklendi:
  - `external/game_masters` -> `https://github.com/PokeMiners/game_masters.git`
  - `external/pogo_assets` -> `https://github.com/PokeMiners/pogo_assets.git`
- `git submodule status --recursive` artik fatal hata vermiyor. Cikti `-` ile
  basliyor; bu submodule'lerin tanimli ama lokal olarak init edilmemis oldugunu
  gosterir. GitHub Actions checkout `submodules: true` ile bunlari init eder.
- `.github/workflows/refresh-living-pokedex.yml` icindeki manifest adimi
  `Refresh metadata manifest version and hashes` olarak guncellendi.
- Workflow artik `metadata_manifest.json` icinde hem `version` alanini hem de
  listedeki tum asset dosyalarinin `sha256` alanlarini yeniden hesapliyor.
- Missing manifest dosyasi varsa workflow bilerek fail eder; bozuk metadata'nin
  sessizce yayinlanmasini engeller.

Yerel dogrulama:

```powershell
git submodule status --recursive
py - <manifest hash dry-run>
.\gradlew.bat :app:testDebugUnitTest --tests com.pokerarity.scanner.ScanTelemetryRepositoryTest --tests com.pokerarity.scanner.ScanTelemetryUploaderTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Sonuclar:

```text
Submodule fatal hatasi yok.
Manifest hash dry-run tum dosyalari buldu.
Telemetry repository/uploader unit testleri gecti.
assembleDebug BUILD SUCCESSFUL.
```

Not: Yerel `python` PATH'te yok; `py` launcher mevcut. GitHub workflow bundan
etkilenmez, cunku `actions/setup-python@v5` ile runner'a Python 3.11 kuruyor.

## 2026-04-26 Telemetry, README ve Repo Hijyeni

Kullanici telemetry servisinin asil amacini netlestirdi: test APK'sini baska
telefonlar yuklediginde scan payload ve screenshot'lari servera otomatik
yuklenecek, yerel `adb logcat` ile server export'u `upload_id` uzerinden
karsilastirilacak.

Yeni calisma kurali:

- Kullanici "loglari cek", "scan bitti" veya benzeri dediginde iki kaynak
  birlikte kontrol edilecek:
  - ADB/logcat: bagli cihazdan `adb logcat -d --pid $(adb shell pidof com.pokerarity.scanner) -v time -t 300`
  - Telemetry export: `scan-export.php?api_key=[REDACTED]&limit=25`
- Bagli cihaz kimligi once alinacak:
  - manufacturer: `samsung`
  - model: `SM-S931B`
  - sdk: `36`
- Telemetry kayitlari `device_manufacturer + device_model + device_sdk_int`
  ile filtrelenecek. Bu cihazdan gelenleri ayir, diger tester telefonlarini
  ayrica raporla.
- Karsilastirmada ana anahtar `upload_id`; zaman icin `created_at` ve
  `uploadedAtEpochMs`, screenshot icin `screenshot_relative_path` kullan.

Son telemetry kontrolu:

```text
Telemetry export limit=25 -> ok=true, count=25
Bu cihaza ait kayit: 25
Diger cihaz kaydi: 0
Son kayitlar:
530 Bulbasaur  CP 619 HP 84  upload_id=9b2ffe26-0e49-4f73-abdc-5c4c36ffac46
529 Croagunk   CP 189 HP 50  upload_id=fa61ee30-4413-4392-a2e6-2b034a2f065c
528 Blastoise  CP 1039 HP 103 upload_id=1069daa9-c4bb-4307-ad2e-0139ceabb5d1
527 Squirtle   CP 381 HP 73  upload_id=f4686a9e-5e8d-474c-9c69-aaaf3097882b
```

ADB logcat ile server export eslesti. Ornek:

```text
Upload response uploadId=1069daa9-c4bb-4307-ad2e-0139ceabb5d1 code=200
Telemetry upload success uploadId=1069daa9-c4bb-4307-ad2e-0139ceabb5d1
```

Server export'ta ayni `upload_id` Blastoise kaydi olarak var. `409 upload_id
already exists` loglari idempotent retry/duplicate durumudur; mevcut uploader
fix'i bunlari basari sayar ve kuyruk tekrar denemeye takilmaz.

GitHub Actions durumu:

- Son 10 `Refresh Living Pokedex` run'u halen failure gorunuyor.
- En son run: 2026-04-25, run_number=10, head_sha=`4cbf7b9...`.
- Bu run billing lock doneminden kalma; yeni local workflow fixleri GitHub
  `main` branch'e push edilmeden Actions tarafinda kullanilamaz.
- Local workflow fixleri hazir: `.gitmodules` eklendi ve manifest hash adimi
  guncellendi. Push/commit kullanici onayi gerektirir.

README:

- `README.md` GitHub ana sayfasi icin bastan yazildi.
- Yeni README telemetry amacini, Living DB'yi, rarity skorunu, Mermaid pipeline
  diyagramini, build/test/ADB/debug rutinlerini anlatiyor.
- Dosya basina amac satiri HTML comment olarak eklendi.

Repo hijyeni:

- `.gitignore` guncellendi:
  - `__pycache__/`, `*.pyc`, `*.log`, `*.db`, `*.sqlite*`
  - `all_logs*.txt`, `scan_logs*.txt`
  - `scan_screenshots/`, `tmp_scans/`, `GmailSheetsManager/`
- Yanlislikla takip edilen generated dosyalar `git rm -r` ile repodan
  kaldirildi:
  - `.gradle/`
  - `app/build/`
  - `scripts/__pycache__/`
  - `.github/prompts/ui-ux-pro-max/scripts/__pycache__/`
  - `tmp_scans/`
- Yerelden silinen proje disi/uretilmis dosyalar:
  - `all_logs.txt`
  - `all_logs_raw.txt`
  - `scan_logs.txt`
  - `scan_logs_recent.txt`
  - `scan_logs_recent_utf8.txt`
  - `scan_screenshots/`
- `tmp_scans/`
- `GmailSheetsManager/`

Dikkat: `GmailSheetsManager` silindi; bu klasor PokeRarityScanner ile ilgili
degildi ve hassas OAuth dosyalari/yerel DB/build ciktisi iceriyordu.

Dogrulama:

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:testDebugUnitTest --tests com.pokerarity.scanner.ScanTelemetryRepositoryTest --tests com.pokerarity.scanner.ScanTelemetryUploaderTest --tests com.pokerarity.scanner.data.repository.RemoteMetadataSyncManagerTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Sonuc:

```text
Telemetry tests: BUILD SUCCESSFUL
Remote metadata sync tests: BUILD SUCCESSFUL
assembleDebug: BUILD SUCCESSFUL
```

## 2026-04-25 Skor Sistemi, Event Tarihi ve README Duzeltmesi

Kullanici yeni istek verdi:

- Rarity skoru sacma yukseliyor; hesaplama dosyalarini oku, internetten daha iyi model arastir ve uygula.
- App kostumlu Pokemon'u anlasa bile event adini yanlis yazabiliyor; ornek olarak 2018 yakalanmis Pokemon ayni kostum nedeniyle 2023 eventine baglanabiliyor.
- README onceki haliyle yeterince guzel degil; GitHub ana sayfasi daha gorsel ve aciklayici olmali.
- Fixler bundan sonra sorulmadan commit/push edilebilir.
- Telefon bagliyken yeni build alinirse APK telefona kurulup acilmali.

Arastirma notu:

- Niantic event duyurulari costumed Pokemon ve event shiny boost'larinin belirli zaman pencerelerine bagli oldugunu gosteriyor.
- Bulbapedia Event Pokemon GO sayfasi costumed/event Pokemon'larin sinirli eventlerde cikabildigini ve ayni kostumun farkli event kayitlarina denk gelebilecegini belirtiyor.
- Pokemon GO Hub CP dokumani CP'nin base stats + IV + level CPM formuluyle hesaplandigini acikliyor; bu nedenle CP rarity puaninda carpan olarak degil, scan/IV guven baglaminda kalmali.

Kod degisiklikleri:

1. `app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt`
   - Eski carpanli model kaldirildi:

```text
old total = round((baseScore + eventBonus) * variantMultiplier)
```

   - Yeni model 0-100 arasi capped additive eksenlere tasindi:

```text
totalScore =
  baseSpeciesAxis 0..35
+ variantAxis     0..35
+ ageAxis         0..20
+ collectorAxis   0..10
```

   - `RarityRuleLoader.get(context)` ve `rarity_rules.json` artik gercek skor aksiyonunda kullaniliyor.
   - Base rarity manifestteki 0..25 degeri 0..35 eksenine normalize ediliyor.
   - Shiny, shadow, lucky, location card, costume, form ve purified sinyalleri variant eksenine puan olarak ekleniyor.
   - Combo bonuslari (`shiny+costume`, `shiny+locationCard`, vb.) variant ekseninde tavanlanarak uygulanir.
   - Age skoru `rarity_rules.json` icindeki 1/3/5/7 yil tierlerine gore hesaplanir.
   - Collector skoru XXL/XXS/rare female ve eventWeight'i kapsar.
   - Event collector bonusu sadece `pokemon.caughtDate != null` ve repository tarafindan date-backed `eventWeight > 0` ise eklenir.
   - Event etiketi yoksa skor metni generic kalir: yanlis 2023/2030 event adi puana veya ozet metne tasinmaz.

2. `app/src/main/java/com/pokerarity/scanner/data/repository/VariantExplanationMetadata.kt`
   - Event metadata artik top-level authoritative event label'a dogrudan dusmez.
   - `AuthoritativeHistoricalEventResolver.resolve(entry, caughtDate)` exact metadata icin ana kaynak oldu.
   - `fullMatch.resolvedEventWindow` yoksa event label sadece historical appearance yakalanma tarihiyle eslesirse acilir.
   - Caught date event window disindaysa event label ve release window null kalir; variant/costume label korunur.

3. `app/src/main/java/com/pokerarity/scanner/data/model/RarityScore.kt`
   - Tier esikleri yeni 0-100 modele uyarlandi:

```text
Common    0
Uncommon 20
Rare     40
Epic     60
Legendary75
Mythical 88
God Tier 96
```

4. `app/src/test/java/com/pokerarity/scanner/VariantExplanationMetadataTest.kt`
   - Ayni kostumun 2019 ve 2023 eventlerinde tekrarlandigi senaryo eklendi.
   - 2019 caughtDate -> 2019 event label acilir.
   - 2018 caughtDate -> 2023 event label reuse edilmez.

5. `README.md`
   - Bastan yazildi.
   - GitHub ilk ekraninda daha guclu gorunen centered title, shields, ASCII pipeline/card ve Mermaid sistem haritasi eklendi.
   - Rarity formulu yeni 0-100 eksenli modele gore guncellendi.
   - Costume/event tarih kapi mantigi, telemetry, Living DB, build/test/ADB ve debug rutini daha net anlatildi.

Dogrulama:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.pokerarity.scanner.VariantExplanationMetadataTest --no-daemon --console=plain
```

Sonuc: komut exit code 0 ile bitti.

Devamda yapilacaklar:

- Tum unit testleri ve `assembleDebug` calistir.
- Telefon bagliysa APK'yi kur ve launcher activity'yi ac.
- `.gitmodules` fix'i, telemetry fix'i, README ve skor/event fixlerini commit/push et.
- Push sonrasi GitHub Actions tekrar calistiginda eski hata (`No url found for submodule path 'external/game_masters'`) remote main'e `.gitmodules` geldigi icin cozulmeli.

Ek dogrulama ve test fixleri:

- Full unit suite ilk denemede Windows/Robolectric Conscrypt native kutuphanesi aradigi icin bazi Robolectric testlerinde fail verdi:

```text
java.lang.UnsatisfiedLinkError: no conscrypt_openjdk_jni-windows-x86_64 in java.library.path
```

- Bu testler TLS/native Conscrypt kullanmadigi icin su test siniflarinda Conscrypt kapatildi:
  - `ScreenCaptureManagerTest`
  - `TextParserNameRecoveryTest`
  - `TextParserPowerUpCostTest`
- Locale'e bagli format testleri deterministik olsun diye `PokemonAnalysisFormattingTest` ve `RarityExplanationFormatterTest` icinde test boyunca `Locale.US` set edildi.
- Son durumda `git diff --check` exit code 0.
- Son durumda full unit suite:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

Sonuc: exit code 0.

- Debug build:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Sonuc: exit code 0.

- Bagli telefon:

```text
adb device: RFCY11MX0TM
install: Success
pidof com.pokerarity.scanner: 6639
```

Rebase/push sonrasi son durum:

- Remote main iki commit ilerlemisti; local degisiklikler `origin/main` ustune rebase edildi.
- Tek rebase conflict `RarityManifestLoader.kt` icindeki whitespace farkiydi, kod davranisi degismeden cozuldu.
- Rebase sonrasi full unit test tekrar exit code 0 verdi.
- Rebase sonrasi `assembleDebug` tekrar BUILD SUCCESSFUL verdi.
- Rebase sonrasi uretilen APK `PokeRarityScanner-v1.8.4-debug.apk`.
- APK bagli telefona tekrar kuruldu ve acildi:

```text
install: Success
pidof com.pokerarity.scanner: 8781
```

- Push tamamlandi:

```text
origin/main -> 716b4800 Fix rarity scoring and metadata automation
```

- GitHub API kontrolunde yeni commit icin henuz workflow run olusmamisti; `Refresh Living Pokedex` workflow'u `schedule` ve `workflow_dispatch` tetikleyicilerine sahip. Bir sonraki manuel/scheduled run remote main'deki `.gitmodules` fix'ini kullanacak.
