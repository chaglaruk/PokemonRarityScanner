# Scan Telemetry Backend

Minimal PHP/MySQL backend for collecting scan telemetry from the Android app.

## Files

- `config.example.php`
- `schema.sql`
- `api/scan-upload.php`
- `api/scan-export.php`

## Deploy

1. Copy `web/scan-telemetry` to your host as `public_html/scan-telemetry` or equivalent.
2. Open `config.php`.
3. Replace only these placeholders:
   - `CHANGE_DB_NAME`
   - `CHANGE_DB_USER`
   - `CHANGE_DB_PASS`
   - `CHANGE_SHARED_SECRET`
   - `https://CHANGE-DOMAIN/scan-telemetry`
4. Create the MySQL table with `schema.sql`.
5. Ensure PHP can write to `storage/`.
6. Test these URLs:
   - `https://YOUR-DOMAIN/scan-telemetry/api/scan-upload.php`
   - `https://YOUR-DOMAIN/scan-telemetry/api/scan-export.php?api_key=YOUR_SECRET`

## Android config

Set in `local.properties` before building, or start from `local.properties.telemetry.example`:

```properties
scanTelemetryBaseUrl=https://your-domain.example/scan-telemetry/api
scanTelemetryApiKey=your-shared-secret
```

If `scanTelemetryBaseUrl` is blank, telemetry is disabled.

## Endpoints

### `POST /api/scan-upload.php`

Multipart fields:
- `payload_json`
- `screenshot` (optional)
- `api_key` (optional if config leaves API key blank)

### `GET /api/scan-export.php`

Query params:
- `api_key`
- `limit`
- `species`

Example:

```bash
curl "https://your-domain.example/scan-telemetry/api/scan-export.php?api_key=secret&limit=50"
```
