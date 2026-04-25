---
paths:
  - "src/components/**"
  - "src/pages/**"
  - "src/app/**"
  - "src/hooks/**"
  - "**/*.tsx"
  - "**/*.jsx"
---

# Frontend Kuralları (React / Next.js)

- Server Component mi Client Component mi? Her yeni dosyada belirle.
- `"use client"` direktifini sadece gerçekten gerektiğinde ekle.
- Props için TypeScript interface tanımla — inline type kullanma.
- `useEffect` içinde async kullanmak istiyorsan ayrı fonksiyon tanımla.
- State mümkün olduğunca yukarıda tutulsun (lift state up).
- Tailwind class sırasını tutarlı tut: layout → spacing → color → typography.
- Component 100 satırı geçiyorsa alt componentlere böl.
- `any` kullanma — `unknown` veya doğru tip bul.
