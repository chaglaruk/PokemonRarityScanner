# AGENTS.md — Universal AI Coding Rules
# Copilot · Cline · RooCode · Codex · Gemini destekli
# Kopyala → proje köküne at → tüm ajanlar otomatik okur

---

## 🧠 TEMEL DAVRANIŞ

- Görev başlamadan önce **ne yapacağını kısa özetle**, sonra başla.
- Emin olmadığın her şeyi **sormadan yazmaya başlama**. Bağlamı önce anla.
- Tek seferde **en fazla 1 dosya oluştur veya değiştir**, sonra bekle.
- Bir değişiklik yapmadan önce: "Bu değişiklik mevcut kodu bozar mı?" diye kontrol et.
- **Asla varsayım yaparak devam etme.** Belirsiz durumlarda sor.

---

## 🚫 TOKEN İSRAFINI ÖNLE

- Gereksiz yorum satırı yazma. Kod zaten açıklayıcıysa yorum ekleme.
- Tüm dosyayı yeniden yazma — sadece değişen kısmı yaz.
- Uzun açıklama blokları yerine **kısa, doğrudan cevap** ver.
- Görevle ilgisiz dosyaları açma veya okuma.
- Aynı işlemi birden fazla kez tekrarlama (loop kontrolü yap).
- Bağlam penceresini temizle veya yeni konuşma aç: uzun oturumlar bağlamı kirletir.

---

## 📁 DOSYA VE KOD YAZIM KURALLARI

### Genel
- Her dosyanın en üstüne **kısa bir amaç satırı** yaz: `# Amaç: ...`
- Fonksiyonlar 40 satırı geçmesin. Geçiyorsa böl.
- Değişken isimleri açıklayıcı olsun: `userData` değil `d`, `userList` değil `l`.
- Magic number kullanma — sabitlerle tanımla.
- Dosya sonunda boş satır bırak.

### Python
- Python 3.10+ syntax kullan.
- Type hint kullan: `def get_user(id: int) -> User:`
- `print()` yerine `logging` kullan.
- Exception yakalarken mutlaka spesifik türü belirt: `except ValueError` değil `except Exception`.
- Dependency: standart kütüphane tercih et, dış paket gerekliyse `requirements.txt`'e ekle.

### JavaScript / TypeScript
- TypeScript varsa `any` kullanma — doğru tipi bul veya `unknown` kullan.
- `var` kullanma — `const` önce, gerekirse `let`.
- `async/await` kullan, `.then()` zinciri kurma.
- Arrow function tercih et.
- Dosya başına `"use strict"` veya `"use client"` gerekiyorsa ekle.

### React / Next.js
- Server Component vs Client Component ayrımını her zaman yap.
- `useEffect` içinde async fonksiyon tanımla, doğrudan `async () =>` verme.
- State'i mümkün olduğunca yukarı taşı (lift state up).
- Component isimleri PascalCase olsun.
- Props için TypeScript interface tanımla.

### Node.js / Backend
- Route handler'lar iş mantığı içermemeli — service katmanına taşı.
- Environment variable'ları `process.env` ile değil, merkezi bir config dosyasından oku.
- Her API endpoint'inde input validation yap.
- HTTP hata kodlarını doğru kullan: 400 (client hatası), 500 (server hatası).

---

## 🧪 TEST VE DOĞRULAMA

- Yeni fonksiyon yazdıktan sonra **en az 1 birim test örneği** ekle veya öner.
- Edge case'leri düşün: boş input, null, sınır değerleri.
- Değişiklik sonrası etkilenen test dosyalarını belirt.
- "Çalışıyor" demeden önce: mantıksal olarak adım adım gözden geçir.
- Console/terminal çıktısını göstermeden "tamamlandı" deme.

---

## 🔍 BAĞLAM YÖNETİMİ (Hallüsinasyon Önleme)

- Proje yapısını bilmiyorsan önce `tree` veya `ls` çalıştır, kod yazmaya başlama.
- Import ettiğin modülün gerçekten var olduğunu doğrula.
- Fonksiyon çağırmadan önce o fonksiyonun signature'ını kontrol et.
- API endpoint yazarken mevcut route'ları kontrol et — çakışma olmamalı.
- Kütüphane versiyonunu bilmiyorsan `package.json` veya `requirements.txt`'e bak.
- **Uydurma.** Bilmiyorsan "bilmiyorum, kontrol etmem lazım" de.

---

## 🔄 GÖREV AKIŞI (Ajanın izlemesi gereken sıra)

```
1. ANLA     → Görevi tam anla. Belirsizlik varsa sor.
2. PLANLA   → Hangi dosyaları değiştireceğini listele.
3. KONTROL  → O dosyaların mevcut halini oku.
4. UYGULA   → Değişikliği yap (küçük adımlarla).
5. DOĞRULA  → Sonucu mantıksal olarak gözden geçir.
6. RAPORLA  → Ne yaptığını 3-5 cümleyle özetle.
```

---

## 🛑 YAPMA LİSTESİ

- ❌ Tüm codebase'i tek seferde yeniden yazma
- ❌ Kullanıcı onayı olmadan dosya silme
- ❌ `.env` dosyasına dokunma
- ❌ `git push` veya `git commit` komutu çalıştırma (onay iste)
- ❌ `npm install` veya `pip install` yapmadan önce onay almadan paket ekleme
- ❌ Hata aldığında aynı şeyi tekrar deneme — farklı yaklaşım dene
- ❌ Görev tamamlanmadan "bitti" deme

---

## ✅ HER ZAMAN YAP

- ✅ Değişiklik öncesi etkilenen dosyaları listele
- ✅ Büyük değişikliği küçük adımlara böl
- ✅ Hata mesajının tamamını göster, kırpma
- ✅ Yeni bağımlılık eklerken neden gerektiğini açıkla
- ✅ Güvenlik açığı gördüğünde işaret et (injection, XSS, hardcoded secret)
- ✅ Oturum uzadıkça bağlam özetini güncelle (aşağıya bak)

---

## 📝 OTURUM HAFIZASI (Her oturum başında güncelle)

> Bu bölümü ajan veya geliştirici manuel olarak günceller.
> Kısa tut — max 15 satır.

```
Proje: [proje adı]
Stack: [kullanılan teknolojiler]
Son değişiklik: [tarih + ne yapıldı]
Aktif görev: [şu an ne üzerinde çalışılıyor]
Bilinen sorunlar: [varsa mevcut bug/kısıtlamalar]
Kritik dosyalar: [dokunulmaması gereken / dikkat edilmesi gereken]
```

---

## 🗣️ İLETİŞİM TARZI

- Türkçe konuş (kod ve değişken isimleri İngilizce kalabilir).
- Kısa cevap ver — kullanıcı kodu okuyabilir, her satırı açıklama.
- Alternatif yaklaşım varsa belirt ama tercih et.
- Emin olmadığın önerileri "Bu deneysel, test et" diye işaretle.
