# Amac: Bir sonraki sprintte uygulanacak isleri oncelik sirasiyla listelemek.

# TODO_NEXT

## Oncelikli yapilacaklar
1. Son scan batchlerinden species/shiny/costume truth setini normalize et.
2. Phase2 modeli sadece dogrulanmis truth ile yeniden egit.
3. Event label dogrulugunu caught-date window ile tekrar regression test et.
4. Overlay ve share kartinda aciklama metnini scan sonuclariyla birebir dogrula.
5. Release pipeline'i private repo politikasina gore sabitle.

## Bug fix listesi
- Bazi scanlerde species yanlis okunuyor; OCR + species refiner esitlikleri denetlenmeli.
- Bazi scanlerde shiny/costume false-positive bildirimi devam ediyor.
- Event ismi bazen eksik veya generic gorunuyor; metadata kaynaklari karsilastirilmali.

## Test / build isleri
- `:app:testDebugUnitTest` her model degisikliginden sonra zorunlu.
- Kritik parser testleri: `DateParseUtilsTest`, `TextParser*`, `Variant*`.
- `:app:assembleRelease` release oncesi zorunlu.
- ADB uzerinde en az bir fiziksel cihaz smoke test.

## UI/UX isleri
- Why valuable metninde tekrar eden/uzayan ifade olursa sadelestirme.
- Share kartinda farkli ekran boyutlarinda metin tasmasi kontrolu.
- Overlay kart kapanis/etkilesim akisinin kullanici testinden gecirilmesi.

## Release / package isleri
- Release APK adlandirma standardini koru (`PokeRarityScanner-vX.Y.Z.apk`).
- GitHub release notlarina telemetry/model degisikligini ozetle.
- Release oncesi secret ve hassas dosya taramasini tekrar calistir.
