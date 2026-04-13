# data/

Data layer for the scanner.

## Subareas

- `local/` — preferences, retention, database helpers
- `local/db/` — Room entities and DAOs
- `model/` — DTOs used by OCR, vision, IV solver, telemetry, and UI
- `remote/` — uploaders, config, telemetry coordination
- `repository/` — app-facing data access, event sync, rarity calculations

Rule: persistence and network details stay here. UI code should consume repositories and models, not database internals.
