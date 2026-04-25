# Cline Ana Kurallar
# Dosya yolu: projen/.clinerules/00-core.md
# Cline bu klasördeki tüm .md dosyalarını otomatik yükler

## Temel Davranış

- AGENTS.md dosyası varsa önce onu oku, bu dosya onu tamamlar.
- Her görev başında hangi dosyaları değiştireceğini listele.
- Onay almadan dosya silme, git commit/push yapma, package ekleme.
- Hata alırsan aynı yaklaşımı tekrar deneme — farklı bir yol dene.

## Token Verimliliği

- Tüm dosyayı okuma — sadece ilgili satırları oku.
- Büyük dosyalarda önce dosya başını ve fonksiyon isimlerini tara.
- Tek seferde fazla araç çağırma — adım adım git.
- Gereksiz açıklama üretme, kodu konuştur.

## Güvenlik Sınırları

- .env dosyalarına dokunma.
- Kullanıcının açıkça söylemediği hiçbir dosyayı silme.
- Dış ağa istek atmadan önce belirt.

## Hata Yönetimi

- Hata mesajının tamamını göster.
- 3 farklı yaklaşımı denedikten sonra çözemediysen kullanıcıya danış.
- Sonsuz döngüye girme — maksimum 3 deneme, sonra dur ve raporla.

## Raporlama

- Görev bitince: ne yaptığını, hangi dosyaları değiştirdiğini, dikkat edilmesi gereken noktaları söyle.
- "Bitti" demeden önce mantıksal olarak gözden geçir.
