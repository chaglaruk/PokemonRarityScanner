# Scan History Repository Regression Test Plan

## Overview
The `ScanHistoryRepository` handles persisting processed scan results into an encrypted local Room database. Validating this save flow is essential to ensure that private data constraints are maintained at rest and that saved scan structures align with the ongoing migrations (e.g., from raw OCR texts to structured data models like `VariantDecisionTrace` and `OcrConfidenceReasons`).

## Why Testing is Critical
1. **Privacy & Data Security:** We must guarantee that `ScanHistoryEntity` instances correctly map the in-memory privacy policies onto disk, avoiding unintentional data leakage or plaintext dumping of restricted values.
2. **Migration Stability:** As `PokemonData` is evolving to replace pipe-delimited markers with structured data, the history mapper must properly encode and decode these new models when converting backwards-compatible structures into JSON payloads.
3. **Data Loss Prevention:** Catching serialization or save-flow anomalies in automated regression tests helps prevent users from losing scan data across updates.

## Technical Challenge: SQLCipher & Device-Only Testing
The main blocker for running pure JVM unit tests on `ScanHistoryRepository` is the application's reliance on SQLCipher for encrypting the Room Database. 
- **Exact SQLCipher Native-Library Blocker:** SQLCipher relies on native `libsqlcipher.so` binaries built for specific target architectures (`arm64-v8a`, `x86`, `x86_64`). 
- **Risks of Plain JVM Room Tests with SQLCipher:** If instantiated inside a plain JVM `testDebugUnitTest` run, the JVM attempts to load these native libraries for the host OS (e.g., Windows/macOS) and immediately crashes with an `UnsatisfiedLinkError`.

## Testing Strategy & Outline

To securely and reliably test the repository without needing a physical device or emulator, we propose the following steps:

1. **In-Memory Fake Database Configuration:**
   Create an explicit test module overriding the database injection to provide an in-memory Room database. Instead of a SQLCipher encrypted database builder (`SupportFactory`), we can swap in a standard plaintext in-memory `Room.inMemoryDatabaseBuilder` during test injection.

2. **Whether Robolectric Can Solve It:**
   Yes, but only if the SQLCipher `SupportFactory` is completely mocked out or bypassed. Robolectric provides an Android Context and SQLite simulation, but it *cannot* magically execute the `libsqlcipher.so` ARM binaries. The solution is to use Robolectric to provide the `Context` to Room, while instructing Room to build a plaintext in-memory database.

3. **First Safe Future Implementation Step:**
   Create a pure Kotlin unit test for the mapper functions (e.g., `ScanHistoryMapperTest.kt`). Since mapping `PokemonData` to JSON strings doesn't require Room or Android Context, this completely bypasses SQLCipher and allows us to verify `VariantDecisionTrace` and `OcrConfidenceReasons` serialization safely today.

4. **What Should Remain Android Instrumentation / Device-Only:**
   Testing the actual SQLCipher encryption boundary, database migrations, and password-based key derivation must remain in the `androidTest` source set. These tests inherently require the native `libsqlcipher.so` to be loaded and executed on a real device or emulator.

## Coverage Targets
- **Insertion & Retrieval:** Test that `ScanHistoryRepository.saveScan()` persists all `PokemonData` fields (via Robolectric + Plaintext Room).
- **Serialization Integrity:** Test that `VariantDecisionTrace` and `OcrConfidenceReasons` survive the serialization round-trip (via pure JVM Mapper tests).
- **Clean-up Rules:** Test history eviction logic (e.g., deleting oldest scans over the threshold) (via Robolectric).
