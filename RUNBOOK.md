# Amac: Projeyi kurma, calistirma, test etme ve paketleme adimlarini standartlastirmak.

# RUNBOOK

## Kurulum
1. JDK 17 kurulu olmali.
2. Android SDK 35 kurulu olmali.
3. ADB erisimi olmali.
4. Repo clone:

```powershell
git clone https://github.com/chaglaruk/PokemonRarityScanner.git
cd PokemonRarityScanner
```

5. `local.properties` dosyasinda en az `sdk.dir` tanimli olmali.

## Development calistirma
Android Studio ile `app` modulu debug calistirilabilir. CLI derleme:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## Test
Tum unit testler:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

## Build
Debug APK:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Release APK:

```powershell
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

## Package alma
Debug cikti:
- `app/build/outputs/apk/debug/`

Release cikti:
- `app/build/outputs/apk/release/`

## APK yukleme (cihaz)
```powershell
adb devices
adb install -r app\build\outputs\apk\debug\PokeRarityScanner-v<version>-debug.apk
adb shell monkey -p com.pokerarity.scanner -c android.intent.category.LAUNCHER 1
```

## Sorun giderme
- `Release signing credentials are required`: `local.properties` veya env signing degerleri eksik.
- `adb device offline`: `adb reconnect offline`, gerekirse `adb kill-server` + `adb start-server`.
- OCR kalite dusukse ekran parlakligi/zoom ve crop bolgeleri kontrol edilmeli.

## Son calistirma durumu
- Tarih: 2026-05-22
- `:app:testDebugUnitTest --no-daemon --console=plain`:
  basarili (BUiLD SUCCESSFUL).
- `:app:assembleDebug --no-daemon --console=plain`:
  basarili (BUiLD SUCCESSFUL).
- `:app:assembleRelease --no-daemon --console=plain`:
  basarisiz.
  Hata: `:app:lintVitalRelease` -> `Instantiatable` (Manifest/layout siniflari icin 11 lint error).
- Not:
  ilk `assembleDebug` denemesi paralel gradle surecinde `packageDebugResources` hatasi verdi; komut tek basina tekrarlandiginda basarili oldu.
