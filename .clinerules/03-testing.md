---
paths:
  - "**/*.test.ts"
  - "**/*.test.js"
  - "**/*.spec.ts"
  - "**/*.spec.js"
  - "**/__tests__/**"
  - "tests/**"
  - "test/**"
---

# Test Yazım Kuralları

- Test ismi açıklayıcı olsun: `should return null when user not found`
- Her test tek bir şeyi test etsin.
- External dependency'leri mock'la, internal logic'i değil.
- Test verisi için factory fonksiyon kullan, hardcoded nesne değil.
- Edge case'leri kapsasın: null, boş string, negatif sayı, büyük veri.
- Başarısız test yazmadan implementation yazmaya başlama (TDD mümkünse).
- `describe` blokları mantıksal gruplama için kullan.
