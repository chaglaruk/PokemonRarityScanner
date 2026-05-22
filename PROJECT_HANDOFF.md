# Amac: Sonraki ajanin projeyi kayipsiz devralmasi icin teknik devir ozeti.

# PROJECT_HANDOFF

## Projenin amaci
PokeRarityScanner, Pokemon GO kart ekranini cihazda tarayip OCR + goruntu sinyalleri ile Pokemon adi, varyant sinyalleri (shiny/costume/form/shadow/lucky) ve rarity skorunu uretir. Sonuc overlay karti ve paylasim karti olarak gosterilir, telemetry aciksa sunucuya gider.

## Teknik stack
- Kotlin + Android SDK (minSdk 26, targetSdk 35)
- Gradle 8.9, AGP 8.7.3, Kotlin 1.9.24
- Jetpack Compose + klasik Android bilesenleri
- ML Kit OCR
- OpenCV tabanli gorsel karar katmani
- Room + SQLCipher
- Hilt DI
- Gson

## Dosya yapisi (kritik)
- `app/src/main/java/com/pokerarity/scanner/service/`: capture, scan, overlay orkestrasyonu
- `app/src/main/java/com/pokerarity/scanner/util/ocr/`: OCR preprocess, parse, species refiner
- `app/src/main/java/com/pokerarity/scanner/util/vision/`: classifier/matcher/feature merger
- `app/src/main/java/com/pokerarity/scanner/data/repository/`: rarity/event/metadata mantigi
- `app/src/main/assets/data/`: varyant ve rarity metadata JSON dosyalari
- `scripts/`: metadata uretim, telemetry export, release scriptleri

## Calisan ozellikler
- Ekran capture ve scan pipeline'i
- OCR ile ad/CP/HP/tarih parse
- Varyant karar engine (phase1 + phase2)
- Event/costume aciklama ve rarity skorlama
- Scan sonucu overlay karti
- Sonucun gorsel olarak paylasimi
- Telemetry upload ve feedback endpoint entegrasyonu
- Living DB asset yenileme workflow dosyalari

## Yarim kalan / izlenecek ozellikler
- Varyant ayristirma dogrulugunun telemetry batchleri ile surekli kalibrasyonu
- Global phase2 modelin species-level modele gore daha dengeli hale getirilmesi
- Daha iyi otomatik regression dataset bakimi

## Bilinen hatalar / riskler
- Scan dogrulugu cihaz, ekran parlakligi ve scan turune gore degisebiliyor.
- Event adi atamada tarih penceresi kritik; metadata sapmasi yanlis event aciklamasi uretebilir.
- Phase2 classifier veri kalitesine bagimli; dengesiz truth label false-positive/false-negative uretebilir.

## Kritik kararlar
- Recognition-first yaklasim: IV/PvP hesaplarini merkezden cikartip scan guvenilirligine odaklanma
- Event adini sadece tarih penceresi uyusuyorsa gosterme
- Telemetry screenshotlarini kuyruga alinmadan kalici kopyalama
- Share akisinda text yerine image-first yaklasim

## Sonraki ajan nereden baslamali
1. `RUNBOOK.md` ile test/build komutlarini temiz bir ortamda dogrula.
2. `KNOWN_ISSUES.md` listesindeki scan kalite maddelerine gore telemetry batch cikart.
3. `scripts/train_phase2_from_telemetry.py` ile model guncellemesi yapmadan once truth kalitesini denetle.
4. Overlay sonuc metninde event aciklama dogrulugunu cihaz uzerinde tekrar kontrol et.
5. Release cikisinda secret hijyenini `SECURITY_REDACTION.md` adimlariyla tekrar tara.

## BELIRSIZ kalan bilgiler
- Canli telemetry sunucusunun uzun vadeli retention/purge politikasi BELIRSIZ.
- Uretim release signing key rotasyonu ve kalici policy BELIRSIZ.
- Tum cihaz varyasyonlari icin canonical scan benchmark setinin son kapsam durumu BELIRSIZ.
