# Amac: GitHub'a cikmadan once hassas veri redaction kararlarini kayit altina almak.

# SECURITY_REDACTION

## GitHub'a alinmayan dosyalar
- `local.properties` (SDK yolu + telemetry API key + signing alanlari)
- `*.md` genel ignore kurali altindaki ozel notlar (`HANDOVER.md` gibi)
- Build ciktilari (`build/`, `app/build/`)
- Lokal cache/artifact klasorleri (`artifacts/`, `.gradle/`)
- Harici buyuk kaynak klasorleri (`external/game_masters`, `external/pogo_assets`)

## Hassas veri kontrol sonucu
- Yerelde `local.properties` dosyasinda gercek `scanTelemetryApiKey` ve signing password alanlari bulundu.
- Bu dosya **tracked degil** ve gitignore tarafindan disarida.
- Tracked dosyalarda dogrudan hardcoded secret patterni icin kritik leak BULUNMADI.
- Repo icinde endpoint referanslari var (`scanTelemetryBaseUrl`, export script query param kullanimi).
- 2026-05-22 itibariyla GitHub repo visibility `private` yapildi.

## .gitignore kararlari
- `local.properties` ignore edilmis kaldi.
- Build/cached klasor ignore kurallari aktif.
- Bu turde handoff dokumanlari icin secici allow-list eklenecek (global markdown ignore korunarak).

## Kalan guvenlik riskleri
- Private repo olmasina ragmen contributor erisimleri yanlis verilirse telemetry/domain ifsasi riski devam eder.
- `local.properties` yanlislikla force-add edilirse secret sizabilir.
- Eski commit gecmisinde gizli deger olup olmadigi bu turde derin history rewrite ile denetlenmedi (BELIRSIZ).

## Aksiyon onerisi
1. Repo visibility private yap.
2. Branch koruma + secret scanning aktif et.
3. Telemetry key rotasyonu yap ve key'i sadece local/env kaynakli tut.
4. Release signing icin debug key yerine ayrik release key politikasi uygula.
