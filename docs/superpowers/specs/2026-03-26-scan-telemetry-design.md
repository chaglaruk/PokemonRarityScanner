# Scan Telemetry Design

**Goal**

Collect real-world scan attempts from multiple testers automatically so scan regressions can be diagnosed with actual screenshots, raw OCR, classifier traces, and app predictions instead of manual log interpretation.

**Scope**

This phase covers:
- automatic scan upload from the Android app
- local queue/retry when network upload fails
- a simple deployable PHP/MySQL backend
- an export endpoint so collected scans can be pulled back into the repo and turned into regression fixtures

This phase does **not** cover:
- user truth/feedback UI
- lucky/background/shadow-specific telemetry fields beyond the predicted flags already produced by the app
- remote model training

## Architecture

The app will create a telemetry record immediately after a scan result is produced. That record contains:
- a structured JSON payload
- the captured screenshot copied into an upload queue directory

The app stores telemetry records in a Room table so uploads survive app restarts and offline usage. A small uploader component posts pending records to a backend over HTTPS using multipart upload. On success, the queued image is deleted and the row is marked uploaded. On failure, the row stays pending with attempt count and last error.

The backend is intentionally simple:
- `POST /api/scan-upload.php`
- `GET /api/scan-export.php`

The backend writes uploaded images to disk and scan metadata to MySQL. The export endpoint returns recent scans as JSON for offline analysis and regression generation.

## Data Model

Each scan upload contains:
- app metadata: version, build, package, device model, SDK, scan timestamp
- prediction metadata: species, shiny, costume, form, lucky, shadow, location/background flags
- OCR/debug metadata: raw OCR text, classifier trace fields, caught date, CP, HP
- runtime metadata: pipeline duration, source screenshot filename
- binary attachment: original scan screenshot

Server-side rows also include:
- upload UUID
- upload receive timestamp
- stored screenshot path
- optional truth fields reserved for future feedback flow

## Android Flow

1. Scan finishes.
2. `ScanManager` builds a telemetry payload from final result, merged visual features, rarity/debug fields, and best screenshot path.
3. Payload + screenshot copy are enqueued in Room.
4. Uploader coroutine attempts immediate delivery.
5. Failed uploads stay pending for retry on next app launch / next successful scan.

The upload path must never block showing the result overlay. Telemetry is fire-and-forget.

## Backend Flow

1. Client sends multipart request with:
   - `payload_json`
   - `screenshot`
   - optional `api_key`
2. Backend validates required fields.
3. Backend stores screenshot under `storage/scans/YYYY/MM/`.
4. Backend inserts metadata row into MySQL.
5. Backend returns JSON `{ ok: true, upload_id: "...", screenshot_url: "..." }`.

The export endpoint returns recent rows with optional filters by date, species, or upload status.

## Configuration

The Android app reads telemetry config from `BuildConfig` fields:
- `SCAN_TELEMETRY_BASE_URL`
- `SCAN_TELEMETRY_API_KEY`
- `SCAN_TELEMETRY_ENABLED`

If telemetry is disabled or base URL is blank, the queue remains inactive and uploads are skipped.

## Error Handling

- Screenshot copy failure: queue payload without image and record the failure in payload metadata.
- Network failure: keep pending row, increment attempts, store last error.
- Backend validation failure: mark row failed with last error but keep it exportable locally.
- Queue corruption: uploader ignores malformed rows and logs them.

## Security

- HTTPS only
- optional shared API key header / form field
- server stores uploads outside public root where possible, or under non-indexed path
- export endpoint should require API key

## Testing

Android:
- payload builder unit test
- queue DAO / repository unit test
- uploader integration test with fake HTTP server if feasible, otherwise logic-level unit test

Backend:
- upload handler smoke test with sample multipart payload
- export endpoint smoke test

Manual:
- perform scan on device
- verify queued row exists
- verify upload succeeds
- verify export returns uploaded scan

