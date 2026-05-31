# Scan History Repository Regression Test Plan

## Overview
The `ScanHistoryRepository` handles persisting processed scan results into an encrypted local Room database. Validating this save flow is essential to ensure that private data constraints are maintained at rest and that saved scan structures align with the ongoing migrations (e.g., from raw OCR texts to structured data models like `VariantDecisionTrace` and `OcrConfidenceReasons`).

## Why Testing is Critical
1. **Privacy & Data Security:** We must guarantee that `ScanHistoryEntity` instances correctly map the in-memory privacy policies onto disk, avoiding unintentional data leakage or plaintext dumping of restricted values.
2. **Migration Stability:** As `PokemonData` is evolving to replace pipe-delimited markers with structured data, the history mapper must properly encode and decode these new models when converting backwards-compatible structures into JSON payloads.
3. **Data Loss Prevention:** Catching serialization or save-flow anomalies in automated regression tests helps prevent users from losing scan data across updates.

## Technical Challenge: SQLCipher & Device-Only Testing
The main blocker for running pure JVM unit tests on `ScanHistoryRepository` is the application's reliance on SQLCipher for encrypting the Room Database. SQLCipher is heavily dependent on native C++ libraries (`.so` files) that are specific to device architecture (e.g., `arm64-v8a`, `x86_64`). As a result:
- It crashes when run directly within the standard JVM test environment (`testDebugUnitTest`).
- It requires either an emulator (`androidTest`) or complex native library loading hooks to run locally.

## Testing Strategy & Outline

To securely and reliably test the repository without needing a physical device or emulator, we propose the following steps:

1. **In-Memory Fake Database Configuration:**
   Create an explicit test module overriding the database injection to provide an in-memory Room database. Instead of a SQLCipher encrypted database builder, we can swap in a standard plaintext in-memory `Room.inMemoryDatabaseBuilder` during test injection.

2. **Robolectric Test Runner:**
   Leverage Robolectric for Android Context mocking. Since we are avoiding the SQLCipher native dependency by using an unencrypted in-memory fallback, Robolectric will successfully spin up the database engine inside a mock Android environment.

3. **Coverage Targets:**
   - **Insertion & Retrieval:** Test that `ScanHistoryRepository.saveScan()` persists all `PokemonData` fields.
   - **Serialization Integrity:** Test that `VariantDecisionTrace` and `OcrConfidenceReasons` survive the serialization round-trip.
   - **Clean-up Rules:** Test history eviction logic (e.g., deleting oldest scans over the threshold).
