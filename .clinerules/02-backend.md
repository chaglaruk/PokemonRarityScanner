---
paths:
  - "**/*.py"
  - "src/api/**"
  - "src/services/**"
  - "src/db/**"
  - "server/**"
  - "backend/**"
---

# Backend / Python Kuralları

## Python
- Type hint zorunlu: `def func(x: int) -> str:`
- `logging` kullan, `print` değil.
- Exception: spesifik tür yakala (`except ValueError`, `except KeyError`).
- Dataclass veya Pydantic model kullan, ham dict yerine.
- Fonksiyon 40 satırı geçerse böl.

## Node.js / API
- Route handler iş mantığı içermemeli → service katmanına taşı.
- Input validation her endpoint'te zorunlu (zod, joi, pydantic).
- HTTP kodları doğru kullan: 400 client, 401 auth, 403 forbidden, 500 server.
- Database sorguları parameterized olmalı — string concat kullanma.

## Ortak
- Environment variable'ları doğrudan `process.env` / `os.environ` ile değil, config modülünden al.
- Secret, token, şifre asla koda yazılmaz.
