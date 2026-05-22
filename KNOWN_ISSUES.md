# Amac: Bilinen bug ve riskleri tek dosyada tutarak tekrar eden hatalari azaltmak.

# KNOWN_ISSUES

## Bilinen buglar
- Bazi scan batchlerinde species OCR sapmasi gorulebiliyor.
- Bazi scanlerde shiny/costume ayrimi halen false-positive/false-negative verebiliyor.
- Event adi aciklamasi metadata ve tarih penceresi kalitesine bagli olarak eksik cikabiliyor.

## Donan / kirilgan alanlar
- Release build signing akisi `local.properties` veya env degiskenlerine bagimli.
- Telemetry pipeline canli endpoint erisimi yoksa yalnizca local sonucla sinirli kaliyor.
- Phase2 model kalite artisi, truth label kalitesine asiri bagimli.

## Calismayan veya zaman zaman bozulan komutlar
- `assembleRelease` signing degerleri yoksa bilincli olarak fail olur.
- ADB baglantisi cihaz tarafinda `offline` durumuna dusebilir.

## Daha once basarisiz olan denemeler
- Global classifier esikleri asiri gevsetildiginde base scanlerde costume false-positive artisi.
- Event adini tarih gate olmadan gosteren denemelerde yanlis yil/event eslesmesi.

## Riskli alanlar
- `local.properties` icindeki telemetry/release degerleri hassas.
- `scripts/export_telemetry_training_set.ps1` endpoint query icinde `api_key` tasiyor.
- `app/src/main/assets/data/variant_phase2_model.json` buyuk dosya; commit churn ve merge riski yuksek.
- Public repo kullaniminda telemetry endpoint/domain ifsasi riski.
