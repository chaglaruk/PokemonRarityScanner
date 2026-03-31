# Scan Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add automatic scan upload so tester devices can send screenshots and scan diagnostics to a backend for later regression analysis.

**Architecture:** The Android app will enqueue telemetry rows in Room and upload them asynchronously via a small HTTP client. A minimal PHP/MySQL backend will accept multipart uploads and expose a JSON export endpoint. Uploading must not block the result overlay.

**Tech Stack:** Kotlin, Room, coroutines, HttpURLConnection, PHP 8, MySQL, JSON, multipart/form-data

---

### Task 1: Add telemetry queue schema

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/data/local/db/TelemetryUploadEntity.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/data/local/db/TelemetryUploadDao.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/data/local/db/AppDatabase.kt`

- [ ] **Step 1: Write failing DB tests or compile-path assertions**
- [ ] **Step 2: Add Room entity for pending uploads**
- [ ] **Step 3: Add DAO for enqueue/list/update/delete**
- [ ] **Step 4: Wire DAO into `AppDatabase`**
- [ ] **Step 5: Run database-related tests / compile verification**

### Task 2: Add telemetry payload model and queue repository

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/data/model/ScanTelemetryPayload.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt`
- Test: `app/src/test/java/com/pokerarity/scanner/ScanTelemetryPayloadTest.kt`

- [ ] **Step 1: Write failing payload serialization test**
- [ ] **Step 2: Add telemetry payload model**
- [ ] **Step 3: Implement queue repository helpers**
- [ ] **Step 4: Run unit tests**

### Task 3: Add uploader and config

**Files:**
- Create: `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt`
- Create: `app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryConfig.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/pokerarity/scanner/ScanTelemetryUploaderTest.kt`

- [ ] **Step 1: Write failing uploader logic test**
- [ ] **Step 2: Add BuildConfig telemetry fields**
- [ ] **Step 3: Implement multipart uploader**
- [ ] **Step 4: Run unit tests**

### Task 4: Hook telemetry into scan pipeline

**Files:**
- Modify: `app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt`
- Modify: `app/src/main/java/com/pokerarity/scanner/PokeRarityApp.kt`

- [ ] **Step 1: Enqueue telemetry after result is ready**
- [ ] **Step 2: Trigger non-blocking upload attempt**
- [ ] **Step 3: Retry pending uploads on app start**
- [ ] **Step 4: Verify build**

### Task 5: Add backend

**Files:**
- Create: `web/scan-telemetry/api/bootstrap.php`
- Create: `web/scan-telemetry/api/scan-upload.php`
- Create: `web/scan-telemetry/api/scan-export.php`
- Create: `web/scan-telemetry/config.example.php`
- Create: `web/scan-telemetry/schema.sql`
- Create: `web/scan-telemetry/README.md`

- [ ] **Step 1: Add SQL schema**
- [ ] **Step 2: Add upload endpoint**
- [ ] **Step 3: Add export endpoint**
- [ ] **Step 4: Document deployment**

### Task 6: Verify end-to-end and document

**Files:**
- Modify: `rapor.md`

- [ ] **Step 1: Run unit tests**
- [ ] **Step 2: Run `assembleDebug`**
- [ ] **Step 3: Install APK to device**
- [ ] **Step 4: Verify app starts**
- [ ] **Step 5: Document result in `rapor.md`**
