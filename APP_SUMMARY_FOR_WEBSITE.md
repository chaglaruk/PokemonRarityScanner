# PokeRarityScanner — Kapsamlı App Özeti (Web Sitesi İçin)

---

## 1. APP NEDİR? (Temel Tanım)

**PokeRarityScanner**, Pokémon GO oyununda oyuncuların yakaladığı Pokémonların **nadirlik ve değerini** gerçek zamanlı olarak analiz eden **Android uygulamasıdır**.

Uygulama, oyuncu ekranını yakalayarak:
- Pokémon kartı üzerindeki **metinleri (OCR ile) okur** (CP, HP, isim, şeker, tarih vb.)
- Pokémonun **görsel özelliklerini analiz eder** (şiny, gölge, şanslı vb.)
- Otomatik olarak **nadirlik puanı hesaplar**
- Sonuçları **kart formatında kullanıcıya gösterir**

---

## 2. AMAÇ VE HEDEF KULLANICI

### Ana Amaç
Pokémon GO oyuncularının değerli ve nadir Pokémonları **hızlıca tespit etmelerini sağlamak**.

### Hedef Kullanıcı
- Pokémon GO casual/hardcore oyuncuları
- Koleksiyoncu oyuncular
- Turnuva hazırlığı yapanlar
- Pokémon takas/satış yapanlar
- Oyun stratejistleri

### Kullanım Senaryoları
1. **Hızlı Değerlendirme:** Yokedi 100'ü buldum → Açarım → Nadirlik hesabumuyor → Değerli mi değilse mi anlıyorum
2. **Koleksiyon Yönetimi:** Tüm Pokémonlarımı hızlıca scanlayarak listeyi oluşturuyorum
3. **Takas Kararı:** Arkadaşımla değiş tokuş etmeden önce her iki Pokémonun değerini kontrol ediyorum
4. **Turnuva Hazırlığı:** Ligimdeki uygun Pokémonları bulup hazırlıyorum

---

## 3. TEMEL ÖZELLİKLER

### 🎯 Çekirdek Özellikler

| Özellik | Açıklama | Teknoloji |
|---------|----------|-----------|
| **Ekran Taraması** | Aktif oyun ekranını gerçek zamanlı yakalar | MediaProjection API |
| **OCR Text Okuma** | Pokémon kartındaki tüm metinleri otomatik okur | Tesseract OCR |
| **CP/HP Analizi** | Saldırı/Savunma/Dayanıklılık puanlarını çıkartır | Regex + ML |
| **Pokémon Tanıma** | Okunan isim üzerinden video oyunundaki doğru Pokémonı tespit eder | Fuzzy Matching + Database |
| **Şiny Tespiti** | Pokémonun şiny varyantı olup olmadığını renk analizi ile belirler | HSV Color Analysis |
| **Gölge/Şanslı Tespiti** | Gölge ve şanslı Pokémon işaretlerini görsel olarak tanır | Feature Detection |
| **Nadirlik Hesaplaması** | Tüm veriler toplanarak 1-100 arası nadirlik puanı hesaplar | Rarity Algorithm |
| **Tarih Taraması** | Pokémonun ne zaman yakalandığını tarihinden çıkartır | OCR + Badge Detection |
| **Sonuç Kartı** | Tüm bilgileri güzel bir kart olarak ekranda gösterir | Android UI |
| **Overlay Widget** | Oyunun üzerine yüzen Pokéball ile istediği zaman scan yapabilir | Android Overlay |

---

## 4. NASIL ÇALIŞIR? (Kullanıcı Akışı)

```
1. Uygulama Başlatılır
        ↓
2. Splash Ekranı → MainActivity
        ↓
3. Pokéball Overlay Görünür (Ekranın Sağında, Yüzen)
        ↓
4. Oyuncu Pokémon Kartını Açar
        ↓
5. Oyuncu Pokéball'ı Tıklar
        ↓
6. Ekran Capture Başlar (3 frame × 120ms aralıklı)
        ↓
7. OCR Pipeline Çalışır:
   • Ekran kesitlenir
   • Tesseract ile metin okunur
   • CP, HP, İsim, Tarih, Şeker, Toz vs. çıkartılır
        ↓
8. Görsel Analiz:
   • HSV renk analizi yapılır
   • Şiny/Gölge/Şanslı tespiti yapılır
        ↓
9. Pokémon Tanıması:
   • Okunan isim → Pokémon ID'sine dönüştürülür
   • Database'de eşleşme bulunur
        ↓
10. Nadirlik Hesaplaması:
    • Tüm veriler toplanır
    • Algoritma ile 1-100 arasında skor hesaplanır
        ↓
11. Sonuç Kartı Gösterilir
    (Pokémon resmi, İsim, CP/HP, Şiny durumu, Nadirlik Skor vb.)
        ↓
12. Kullanıcı İşini Bitirip Oyuna Dönebilir
```

---

## 5. TEKNİK MİMARİ

### Stack Teknolojileri
- **Platform:** Android 13+ (API 26+)
- **Dil:** Kotlin
- **UI Framework:** Android Jetpack (Lifecycle, ViewModel, LiveData, Room)
- **Veritabanı:** SQLite (Room ORM)
- **Dependency Injection:** Dagger Hilt
- **OCR Engine:** Tesseract
- **Görsel İşleme:** Android Image API, HSV Color Analysis
- **Screen Capture:** MediaProjection API

### Modüler Yapı

```
app/
├── ui/              (UI Katmanı - Jetpack Compose/XML)
├── data/            (Veri Katmanı - Repository, DAO, Database)
├── service/         (Servis Katmanı - Background Tasks)
└── util/
    ├── ocr/         (OCR İşlemleri)
    └── vision/      (Görsel Analiz)
```

### OCR Pipeline (Teknik Detay)

```
Screenshot Kapatılır (3x 120ms aralıklı)
         ↓
Preprocessing (White Mask / High Contrast)
         ↓
Bölgesel Kesit (Region Based Cropping)
    • CP Bölgesi (Üst Alan)
    • İsim Bölgesi (Orta Alan)
    • HP Bölgesi (Orta-Alt Alan)
    • Tarih Bölgesi (Sağ/Alt Alan)
         ↓
Tesseract OCR (per-region)
         ↓
Regex + ML Text Parsing
         ↓
Best Frame Selection (Puanlama)
         ↓
Nadirlik Hesaplama
```

---

## 6. VERİ AKIŞI VE ÇIKTı

### Girdi (Input)
- Real-time ekran görüntüleri
- Pokémon kartı görsel verileri
- Metin ve renk bilgileri

### İşleme (Processing)
1. **Text Extraction:** CP, HP, Pokémon ismi, şeker, tarih
2. **Visual Analysis:** Renk, şiny durumu, gölge, şanslı işaretleri
3. **Species Recognition:** İsimden Pokémon ID'sine dönüşüm
4. **Rarity Calculation:** Kompleks algoritma ile skor hesabı

### Çıktı (Output)
```
{
  "pokemonName": "Charizard",
  "pokemonId": 6,
  "cp": 2847,
  "hp": 155,
  "level": 40,
  "ivPercentage": 96,
  "captureDate": "2026-03-20",
  "candy": 312,
  "stardust": 150000,
  "isShiny": true,
  "isShadow": false,
  "isLucky": false,
  "rarityScore": 92,
  "recommendation": "Savaş için ideal, şiny varyantı ekstrema değerli"
}
```

---

## 7. MEVCUT DURUM (Geliştirme Aşaması)

### ✅ Tamamlanan
- ✅ Temel UI ve Navigation
- ✅ OCR Pipeline (Tesseract entegrasyonu)
- ✅ CP/HP Okuma (%95+ başarı)
- ✅ İsim Tanıma (Fuzzy Matching)
- ✅ Şiny Tespiti (Renk analizi)
- ✅ Overlay Widget (Pokéball)
- ✅ Database Mimarisi (Room)
- ✅ Android Manifest ve Permissions

### 🔧 Geliştirilmekte
- 🔄 Tarih Taraması (Badge bölgesi iyileştirilmesi)
- 🔄 Pokémon Listesi (Gen 4+ eklenmesi)
- 🔄 Hata Toleransı (Edge Cases işlenmesi)
- 🔄 Performance Optimizasyonu

### 📋 Planlanan Özellikler
- 📝 Scan Geçmişi ve İstatistikler
- 📝 Rarity Trendi (Zaman içinde en nadir Pokémonlar)
- 📝 Karşılaştırma Modu (İki Pokémon arasında karşılaştırma)
- 📝 CSV Export (Tüm taranmış Pokémonları dışa aktarma)
- 📝 Cloud Sync (Birden fazla cihazda tarama geçmişi)
- 📝 AI Öneriler (Stratejik tavsiyeler)
- 📝 Multi-Language Destek

### ⚙️ Bilinen Sorunlar (Düzeltme İçinde)
- Tarih okunması bazı cihazlarda %70 başarı (İyileştiriliyor)
- Edge case Pokemon isimleri (Nidoran♂/♀ gibi) (Fuzzy matching güçlendirilmesi)
- Bazı regional varyantlar tanınmıyor (Database genişletilmesi)

---

## 8. HEDEF PLATFORM VE CİHAZLAR

### Desteklenen Platform
- **Android 13+** (API Level 26+)
- Mimic Android 12+ ile çalışabilir (uyumluluk testi gerekli)

### Test Edilen Cihazlar
- Samsung Galaxy S10/S20/S21 (FHD+ ve AMOLED)
- Recommended Min: 4GB RAM, SD 888+ processor
- Screen Pattern: 19.5:9 aspect ratio (Optimize: 1080×2340)

### Permissions Gerekli
- `RECORD_AUDIO` (Overlay)
- `MANAGE_EXTERNAL_STORAGE` (Scan Geçmişi)
- `SYSTEM_ALERT_WINDOW` (Overlay Widget)
- `CAPTURE_SCREEN_CONTENT` (Screen Recording)
- `READ_PHONE_STATE` (Durabilitas)

---

## 9. WEB SİTESİ İÇİN TEMEL BÖLÜMLER

Ajan bu web sitesini oluştururken şu bölümleri içermeli:

1. **Hero Section** - Uygulamanın videolu tanıtımı ve büyük bir "İndir" butonu
2. **Problem/Çözüm** - Oyuncu ne zorluğu yaşıyor, uygulama bunu nasıl çözüyor
3. **Özellikler Galerisi** - Her özelliğin ekran görüntüsü ve açıklaması
4. **Nasıl Çalışır** - Adım adım rehber (gif/video ile)
5. **Teknik Bilgiler** - Stack, gereksinimler, desteği yapılan cihazlar
6. **Kullanım Örnekleri** - Gerçek kullanım senaryoları
7. **FAQ** - Sıkça sorulan sorular
8. **Download Section** - APK indir, Google Play (planlanıyor)
9. **Changelog** - Sürüm geçmişi ve güncellemeler
10. **İletişim** - Feedback, bug report, feature request

---

## 10. KİŞİLİKLENDİRME VE BRAND

### Renk Paleti
- **Primary:** Pokémon Red (#E3350D)
- **Secondary:** Pokéball White (#FFFFFF) + Black (#000000)
- **Accent:** Electric Yellow (#FFDE00) - Pikachu theme

### Typography
- **Heading Font:** Bold, Modern (Montserrat, Roboto Bold)
- **Body Font:** Clean, Readable (Roboto, Open Sans)

### Tone of Voice
- **Casual ama Professional**
- **Pokémon fan mentality**
- **Teknik detaylar accessible kılma**
- **Humor eklemek ama brand'ı korumak**

---

## 11. BAŞARI METRİKLERİ (Hedefler)

- OCR Accuracy: %95+
- Scan Speed: < 2 saniye per card
- Species Recognition: %98+
- App Crash Rate: < 0.1%
- User Retention: 7-günlük %40+
- Daily Active Users (DAU): 10K+ (1. yıl hedefi)

---

## 12. ROADMAPl (Yakın Gelecek)

**v1.1 (Nisan 2026)**
- ✅ Tarih taraması %99 accuracy
- ✅ Gen 4-9 Pokémon desteği
- ✅ Batch scanning (10+ Pokémon ard arda)

**v1.2 (Mayıs 2026)**
- ✅ Cloud Sync
- ✅ Scan Geçmişi
- ✅ CSV Export

**v1.3 (Haziran 2026)**
- ✅ AI Öneriler
- ✅ Sosyal Paylaşma
- ✅ Leaderboard

**v2.0 (Q3 2026)**
- ✅ iOS Uyarlaması
- ✅ Web Dashboard
- ✅ API Gateway

---

---

> 💡 **BU BELGE, AJANAA VERİLEBİLİR**
>
> Ajan bu bilgileri kullanarak **responsive, modern, işlevsel** bir web sitesi oluşturabilir.
> İhtiyacına göre bölümleri ekleyebilir veya çıkarabilirsin.
>
> **Ajan Talimatı Örneği:**
> *"Bana PokeRarityScanner adlı Android uygulaması için modern, responsive bir web sitesi yap. İşte uygulama hakkında detaylı bilgiler [Bu belgeyi yapıştır]. Siteyi Nextjs/Tailwind/Framer Motion vs. ile yap, modern, hızlı, SEO-uyumlu olsun."*
