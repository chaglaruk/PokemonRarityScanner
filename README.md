# PokeRarityScanner

A powerful Android application that analyzes Pokémon GO cards in real-time using OCR and machine learning to calculate rarity scores and identify valuable Pokémon instantly.

![Android](https://img.shields.io/badge/Android-13%2B-green?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-purple?style=flat-square)
![API](https://img.shields.io/badge/API-26%2B-blue?style=flat-square)
![Version](https://img.shields.io/badge/Version-1.0-orange?style=flat-square)

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [How It Works](#how-it-works)
4. [Technical Architecture](#technical-architecture)
5. [Project Structure](#project-structure)
6. [Installation & Setup](#installation--setup)
7. [Configuration](#configuration)
8. [Development](#development)
9. [Recent Fixes](#recent-fixes)
10. [API & Data Models](#api--data-models)
11. [Troubleshooting](#troubleshooting)
12. [Contributing](#contributing)

---

## 🎯 Overview

**PokeRarityScanner** is a real-time analysis tool for Pokémon GO players that:
- **Captures** active game screen using Android's MediaProjection API
- **Extracts** text from Pokémon cards using Tesseract OCR
- **Analyzes** visual properties (shiny, shadow, lucky status)
- **Calculates** rarity scores (1-100 scale)
- **Displays** comprehensive analysis cards in an overlay widget

### Target Users
- Casual & hardcore Pokémon GO players
- Collectors organizing their pokédex
- Tournament competitors preparing teams
- Players trading/selling Pokémon
- Strategy game analysts

### Use Cases
- **Quick Valuation:** Instantly determine if your catch is worth keeping
- **Collection Management:** Bulk-scan your collection to build inventory lists
- **Trade Decisions:** Compare rarity scores before trading with friends
- **Tournament Prep:** Identify optimal Pokémon for competitive play

---

## ✨ Features

### Core Pipeline Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Screen Capture** | MediaProjection API | Grab active game screen at 120ms intervals |
| **OCR Processing** | Tesseract OCR | Extract text: CP, HP, name, candy, catch date, stardust |
| **Visual Analysis** | Keras/TensorFlow, HSV Color Analysis | Detect shiny, shadow, lucky, costume variants |
| **Species Recognition** | Fuzzy Matching + Database | Map OCR'd name to Pokédex species |
| **IV Solver** | Cost-based algorithm with Arc Policy | Calculate IV ranges from CP/HP/stardust/stage |
| **Rarity Calculator** | Multi-factor scoring | Compute rarity score incorporating species, variant, event |
| **Variant Detection** | Classifier pipeline (global + species scopes) | Identify costume, form, event variants with confidence thresholds |
| **UI Overlay** | Android Compose + Overlay Manager | Floating widget for one-tap scanning |

### Key Features

- ✅ **Real-time OCR Processing** – Reads Pokémon stats in milliseconds
- ✅ **IV Range Calculation** – Intelligent IV solver with Arc Signal Policy
- ✅ **Shiny/Shadow Detection** – Pixel-perfect variant identification
- ✅ **Catch Date Parsing** – Extracts catch date from badge/card
- ✅ **Costume Recognition** – Identifies limited-edition variants
- ✅ **Confidence Scoring** – Multi-level confidence metrics for all predictions
- ✅ **Floating Widget** – Tap-to-scan overlay on top of game
- ✅ **Telemetry System** – Optional server-side logging for diagnostics
- ✅ **Comprehensive Logging** – Detailed OCR traces for debugging
- ✅ **False Exactness Prevention** – Arc Policy prevents overconfident IV claims

---

## 🔄 How It Works

### User Workflow

```
1. Start App
   ↓
2. Grant Overlay Permission (first run)
   ↓
3. Navigate to Pokémon Dashboard
   ↓
4. Return to Game → Pokéball Widget Appears
   ↓
5. Open Pokémon Card
   ↓
6. Tap Pokéball Widget
   ↓
7. Capture 3 frames (360ms total)
   ↓
8. OCR Pipeline
   • Detect regions (CP row, HP zone, name block)
   • Extract text via Tesseract
   • Parse: CP, HP, stardust, candy, name, date
   ↓
9. Visual Classifier
   • Analyze sprite colors (HSV)
   • Detect shiny/shadow/lucky badges
   • Identify costume/form variants
   ↓
10. Pokémon Identification
    • Map OCR name → Species ID
    • Fuzzy matching for nickname variants
    • Database lookup
    ↓
11. IV Solver
    • Apply stardust/candy cost signals
    • Use CP/HP for candidate range
    • Apply Arc Signal Policy for level refinement
    ↓
12. Rarity Calculation
    • Base species rarity
    • Event multipliers
    • Variant multipliers (shiny ×5, costume ×2)
    • Final score (1-100)
    ↓
13. Display Card
    • Species name & number
    • CP, HP, IV range
    • Rarity score & tier
    • Variant tags
    • Confidence metrics
```

### Technical Pipeline

```
ScreenCapture → ImagePreprocessor → OCRProcessor → TextParser
    ↓                                                  ↓
  Frame                                          CP, HP, Stardust,
  Buffer                                         Candy, Name, Date
    ↓                                                  ↓
    └──→ VariantClassifier ──→ VariantResolutionLogic
         (detect shiny/         (apply rescue rules,
          costume/form)         confidence gates)
                                    ↓
                                FullVariantMatch
                                    ↓
                        IvCostSolver (Arc Policy)
                                    ↓
                            RarityCalculator
                                    ↓
                            ScanResult Card
```

---

## 🏗️ Technical Architecture

### Platform & Languages
- **Target:** Android 13+ (API 26+)
- **Primary Language:** Kotlin
- **UI Framework:** Jetpack Compose + Material 3
- **Minimum SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)
- **Java Version:** 17

### Core Dependencies

#### UI & Composition
- **Jetpack Compose 2024.12.01** – Modern declarative UI
- **Material 3 UI** – Material Design components
- **Jetpack Navigation** – Navigation graph support

#### Data & Storage
- **Room Database 2.6.1** – SQLite ORM for local persistence
- **Protocol Buffers** – Serialization for diagnostic data

#### Image & Vision
- **Tesseract OCR** – Text extraction from screen
- **Firebase ML Kit** – Optional ML model support
- **Glide/Coil** – Image loading for Pokémon sprites

#### Networking & Async
- **Retrofit 2** – REST API client
- **OkHttp 4** – HTTP client with interceptors
- **Kotlinx Coroutines 1.9.0** – Async/await patterns
- **Kotlin Flows** – Reactive data streams

#### DI & Architecture
- **Dagger Hilt 2.54** – Dependency injection
- **ViewModel Architecture** – Lifecycle-aware state
- **LiveData** – Reactive state management

#### Testing
- **JUnit 4** – Unit test framework
- **Mockito** – Test mocking
- **Espresso** – UI testing

### Build System
- **Gradle 8.9** with Kotlin DSL
- **Android Gradle Plugin 8.7.3**
- **Kotlin Compiler 1.9.24**
- **ProGuard** – Code obfuscation (release builds)

---

## 📁 Project Structure

```
PokeRarityScanner/
├── app/                              # Main Android app module
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/pokerarity/scanner/
│   │   │   │   ├── Constants.kt       # App-wide constants
│   │   │   │   ├── PokeRarityApp.kt   # Application class
│   │   │   │   ├── data/              # Data layer (models, repositories)
│   │   │   │   │   ├── model/         # Data classes (ScanResult, FullVariantMatch, etc.)
│   │   │   │   │   ├── db/            # Room database entities & DAOs
│   │   │   │   │   └── repository/    # Repository pattern implementations
│   │   │   │   ├── di/                # Dependency injection (Hilt modules)
│   │   │   │   ├── service/           # Background services & workers
│   │   │   │   ├── ui/                # UI layer
│   │   │   │   │   ├── screens/       # Screen composables
│   │   │   │   │   ├── widgets/       # Reusable UI components
│   │   │   │   │   └── viewmodel/     # ViewModels for screens
│   │   │   │   └── util/
│   │   │   │       ├── ocr/           # OCR pipeline
│   │   │   │       │   ├── OCRProcessor.kt       # Main orchestrator
│   │   │   │       │   ├── TextParser.kt         # Text extraction & parsing
│   │   │   │       │   ├── ImagePreprocessor.kt  # Image prep (crop, resize)
│   │   │   │       │   ├── ScreenRegions.kt      # Region definitions
│   │   │   │       │   └── ScanConsistencyGate.kt # Validation
│   │   │   │       ├── vision/        # Vision & classification
│   │   │   │       │   ├── VariantPrototypeClassifier.kt    # Shiny/costume detection
│   │   │   │       │   ├── VariantResolutionLogic.kt        # Rescue logic
│   │   │   │       │   ├── FullVariantMatcher.kt            # Final variant matching
│   │   │   │       │   ├── IvCostSolver.kt                  # IV calculation (Arc Policy)
│   │   │   │       │   ├── RarityCalculator.kt              # Rarity score
│   │   │   │       │   ├── VariantDecisionEngine.kt         # Final diagnosis
│   │   │   │       │   └── FullVariantScoring.kt            # Ranking candidates
│   │   │   │       └── ScanErrorHandler.kt       # Error logger
│   │   │   └── resources/             # Images, strings, themes
│   │   ├── test/                      # Unit tests
│   │   │   └── java/.../... 
│   │   │       ├── IvCostSolverTest.kt
│   │   │       ├── TextParserTest.kt
│   │   │       ├── VariantClassifierTest.kt
│   │   │       └── ...
│   │   └── androidTest/               # Instrumented tests
│   ├── build.gradle.kts               # App-level build config
│   └── proguard-rules.pro             # Obfuscation rules
├── scripts/                           # Python/runtime utilities
│   ├── generate_pokemon_families.py
│   ├── extract_variant_forms_from_gm.py
│   └── ...
├── external/
│   ├── game_masters/                  # Pokémon GO game data
│   └── pogo_assets/
├── build.gradle.kts                   # Root-level Gradle config
├── settings.gradle.kts                # Module settings
├── gradle.properties                  # Global properties
├── local.properties                   # Local build config (not versioned)
├── gradlew / gradlew.bat              # Gradle wrapper
├── README.md                          # This file
├── DEPLOYMENT_GUIDE.md                # Deployment procedures
├── ARC_POLICY_SUMMARY.md              # IV solver policy details
└── schema.sql                         # Database schema documentation
```

### Key Architectural Layers

#### **Data Layer** (`data/`)
- **Models:** Immutable data classes (ScanResult, FullVariantMatch, FullVariantCandidate)
- **Database:** Room entities for persistence (ScanHistory)
- **Repository:** Data access abstraction (ScanRepository)

#### **OCR Layer** (`util/ocr/`)
Processes raw screen captures into structured Pokémon data:
- `OCRProcessor.kt` – Orchestrates full pipeline, manages frame buffers
- `TextParser.kt` – Regex-based extraction (CP, HP, stardust, candy, name)
- `ImagePreprocessor.kt` – Preprocessing (grayscale, contrast enhancement)
- `ScreenRegions.kt` – UI region mappings for 1080×2340 reference device

#### **Vision Layer** (`util/vision/`)
Analyzes visual properties and calculates rarity:
- `VariantPrototypeClassifier.kt` – ML classifier for shiny/costume/form
- `VariantResolutionLogic.kt` – Intelligent variant rescue logic
- `FullVariantMatcher.kt` – Final variant selection with confidence gates
- `IvCostSolver.kt` – IV calculation with **Arc Signal Policy**
- `RarityCalculator.kt` – Rarity score computation
- `VariantDecisionEngine.kt` – Final diagnosis pipeline

#### **UI Layer** (`ui/`)
- Compose-based screens (ScanResult, History, Settings)
- Floating widget service (overlay management)
- ViewModel for state & lifecycle

#### **DI Layer** (`di/`)
- Hilt modules for service/repository injection
- Singleton factories for ML models, database

---

## 🚀 Installation & Setup

### Prerequisites
- **Android Studio** 2023.1 or later (with Gradle plugin 8.7.3)
- **Java Development Kit (JDK) 17+**
- **Android SDK** with platforms 26–35 installed
- **Emulator or Physical Device** running Android 13+ (recommended: Android 14–15)

### Clone Repository

```bash
git clone https://github.com/chaglaruk/PokemonRarityScanner.git
cd PokeRarityScanner
```

### Build from Source

```bash
# Full clean build
./gradlew clean assembleDebug

# Fast build (dev)
./gradlew assembleDebug

# With test suite
./gradlew assembleDebug test

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew testDebugUnitTest
```

### Android Studio
1. Open project root in Android Studio
2. Wait for Gradle sync
3. Select Build → Rebuild Project
4. Run → Run 'app' (or press Shift+F10)

### Wireless Installation

```bash
# On your computer, connect to device
adb connect <device-ip>:5555

# Install
adb -s <device-ip>:5555 install app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ Configuration

### Mandatory Permissions

Add to `AndroidManifest.xml` (already included):

```xml
<!-- Screen capture for overlay -->
<uses-permission android:name="android.permission.MEDIA_PROJECTION" />

<!-- Camera (future expansion) -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Network for telemetry -->
<uses-permission android:name="android.permission.INTERNET" />
```

### Runtime Permissions

The app requests at runtime:
- **MEDIA_PROJECTION** – For overlay widget
- **POST_NOTIFICATIONS** – For scan notifications

### Build Configuration

Create/edit `local.properties`:

```properties
# Optional: Enable telemetry logging
scanTelemetryBaseUrl=https://your-server.com/api
scanTelemetryApiKey=your-api-key-here

# Gradle settings
sdk.dir=/path/to/android/sdk
ndk.dir=/path/to/android/ndk
```

If telemetry is disabled, all scans remain local (no network calls).

### Environment Variables (Optional)

```bash
export ANDROID_HOME=/path/to/android/sdk
export JAVA_HOME=/path/to/jdk17
export PATH=$JAVA_HOME/bin:$PATH
```

---

## 👨‍💻 Development

### Project Setup for Contributors

```bash
# Fork & clone
git clone https://github.com/<your-username>/PokemonRarityScanner.git
cd PokeRarityScanner

# Create feature branch
git checkout -b feature/my-feature

# Build & test
./gradlew assembleDebug test

# Commit with descriptive messages
git commit -am "feat: description of change"

# Push to your fork
git push origin feature/my-feature

# Create Pull Request on GitHub
```

### Running Tests

```bash
# Unit tests only
./gradlew testDebugUnitTest

# Specific test class
./gradlew testDebugUnitTest --tests "com.pokerarity.scanner.IvCostSolverTest"

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Coverage report
./gradlew testDebugUnitTest jacocoTestDebugUnitTestReport
```

### Code Quality

- **Ktlint** – Format Kotlin code
- **ProGuard** – Release builds obfuscated

```bash
# Format code
./gradlew ktlintFormat

# Check style
./gradlew ktlintCheck
```

### Debugging

#### Logcat Filtering
```bash
# All app logs
adb logcat | grep "PokeRarity"

# OCR pipeline
adb logcat | grep "OCRProcessor"

# Vision classification
adb logcat | grep "VariantClassifier"

# IV solver
adb logcat | grep "IvCostSolver"
```

#### Screen Recording
```bash
# 3 minutes @ 1080p
adb shell screenrecord --bit-rate 8000000 /sdcard/screen.mp4

# Pull to computer
adb pull /sdcard/screen.mp4
```

#### Performance Profiling
- Use Android Studio's **Profiler** (CPU, Memory, Network tabs)
- Monitor OCR frame capture latency
- Track GC pauses during classification

---

## 🔧 Recent Fixes

### Fix: Shiny/Costume Classification Accuracy (April 6, 2026)

**Commit:** `45c73bc`

**Problem:**
- Shiny Pokémon in base form were being reported as non-shiny
- Legitimate costume Pokémon were being reported as base form
- Example: Nidoqueen shiny (base form) showed `FullVariantShiny=false`

**Root Cause:**
Inverted suppression logic in `FullVariantMatcher.kt` was blocking valid classifications:

```kotlin
// BEFORE (Wrong)
val suppressClassifierBaseShiny = winner.isShiny && 
    winner.variantClass == "base" && 
    winner.source.startsWith("classifier")
// This suppressed ALL base shiny, even when correct!
```

**Solution:**
Removed `suppressClassifierBaseShiny` flag and now trust classifier decisions with reasonable confidence thresholds:

```kotlin
// AFTER (Fixed)
val suppressLowConfidenceClassifierNonBaseShiny =
    winner.isShiny &&
    winner.variantClass != "base" &&
    winner.source.startsWith("classifier") &&
    winner.classifierConfidence < shinyConfidenceGate(winner)
// Only suppress LOW-confidence non-base shiny
```

**Impact:**
- ✅ Base shiny variants now correctly identified
- ✅ Costume Pokémon properly classified
- ✅ False negatives eliminated

### Fix: Arc Signal Policy Implementation (April 1-5, 2026)

**Files Modified:** `IvCostSolver.kt`

**What Changed:**
- Arc level detection now requires strong primary signals (stardust/candy)
- Arc narrowing must exceed 20% threshold to be considered
- Confidence calculated and logged for transparency
- False exactness prevention: multiple→1 narrowing results downgraded to RANGE

**Benefit:** More honest IV confidence reporting, fewer false "EXACT" claims

---

## 📡 API & Data Models

### Main Data Classes

#### `ScanResult`
Complete result of a single scan:
```kotlin
data class ScanResult(
    val species: String,
    val cp: Int,
    val hp: Int?,
    val isShiny: Boolean,
    val isShadow: Boolean,
    val isLucky: Boolean,
    val hasCostume: Boolean,
    val rarityScore: Int,
    val rarityTier: String,  // COMMON, UNCOMMON, RARE, LEGENDARY
    val timestamp: Long,
    val rawOcrText: String,
    val pipelineMs: Int  // Execution time
)
```

#### `FullVariantMatch`
Final variant classification with confidence:
```kotlin
data class FullVariantMatch(
    val finalSpecies: String,
    val finalSpriteKey: String,
    val resolvedVariantClass: String,  // base, costume, form
    val resolvedShiny: Boolean,
    val resolvedCostume: Boolean,
    val explanationMode: String,  // generic_species_only, exact_authoritative
    val variantConfidence: Float,  // 0.0-1.0
    val shinyConfidence: Float,
    val eventConfidence: Float,
    val debugSummary: String
)
```

#### `IvCostSolver.Result`
IV solving result with Arc policy audit:
```kotlin
data class Result(
    val candidates: List<IvCandidate>,
    val selectedCandidate: IvCandidate?,
    val arcPolicyApplied: Boolean,
    val arcNarrowingPercent: Float,
    val arcConfidence: Float,
    val primarySignalsUsed: List<String>,
    val explanation: String
)
```

### REST API (Optional Telemetry)

#### Endpoint: `POST /api/scans`
Send scan results for server-side analysis:

```json
{
  "uploadId": "uuid-here",
  "device": {
    "manufacturer": "samsung",
    "model": "SM-S931B",
    "sdkInt": 35
  },
  "prediction": {
    "species": "Nidoqueen",
    "cp": 1722,
    "hp": 147,
    "isShiny": false,
    "isShadow": false,
    "isLucky": false,
    "hasCostume": false,
    "rarityScore": 28,
    "rarityTier": "UNCOMMON"
  },
  "debug": {
    "pipelineMs": 7124,
    "rawOcrText": "...",
    "breakdown": {
      "Base Score": 28,
      "Event Bonus": 0,
      "Variant Multiplier": 100
    }
  }
}
```

---

## 🐛 Troubleshooting

### Common Issues

#### Issue: "Overlay Permission Not Granted"
**Symptom:** App crashes on launch or widget doesn't appear

**Solution:**
1. Go to **Settings → Apps → PokeRarityScanner → Permissions**
2. Enable **Display over other apps**
3. Restart app

#### Issue: OCR Text Not Recognized
**Symptom:** CP/HP show as 0 or ??? in result

**Solution:**
- Ensure screen brightness is adequate
- Try different Pokémon card (some lighting causes issues)
- Check if overlays/filters are active in Pokémon GO settings
- Report issue with screenshot to developer

#### Issue: Shiny Detection Incorrect
**Symptom:** Non-shiny marked as shiny or vice versa

**Solution:**
- Verify device display color accuracy
- Check lighting conditions
- Disable any color filter apps
- If consistent, file bug report via GitHub Issues

#### Issue: Build Fails with "SDK Not Found"
**Symptom:** `ANDROID_HOME not set` error

**Solution:**
```bash
# Set Android SDK path
export ANDROID_HOME=$HOME/Android/Sdk
# Or in local.properties:
sdk.dir=/path/to/android/sdk

# Retry build
./gradlew clean assembleDebug
```

#### Issue: Gradle Sync Slow
**Symptom:** Takes >5 minutes to sync Gradle

**Solution:**
```bash
# Use Gradle daemon
echo "org.gradle.daemon=true" >> gradle.properties

# Increase heap
echo "org.gradle.jvmargs=-Xmx4g" >> gradle.properties

# Offline mode (faster if deps cached)
./gradlew --offline build
```

### Debug Logging

Enable verbose logging for diagnosis:

```kotlin
// In PokeRarityApp.kt or any logger
fun enableDebugLogging() {
    Log.isLoggable("PokeRarity", Log.DEBUG)
    Log.isLoggable("OCRProcessor", Log.DEBUG)
    Log.isLoggable("VariantClassifier", Log.DEBUG)
}
```

Then monitor:
```bash
adb logcat | grep -E "PokeRarity|OCRProcessor|VariantClassifier" > debug.log
```

---

## 🤝 Contributing

We welcome contributions! Please follow these guidelines:

### Before You Start
1. **Check existing issues** – Don't duplicate work
2. **Open an issue** – Describe what you plan to change
3. **Wait for feedback** – Maintainers will guide scope

### How to Contribute

1. **Fork** the repository
2. **Create a feature branch:** `git checkout -b feature/my-feature`
3. **Make commits** with clear messages:
   ```
   feat: Add new OCR region detection
   fix: Resolve shiny detection false negatives
   docs: Clarify IV solver Arc Policy
   ```
4. **Write tests:**
   - Add unit tests in `app/src/test/`
   - Run: `./gradlew testDebugUnitTest`
   - Aim for >80% coverage
5. **Update docs:**
   - Modify README if needed
   - Add comments to complex code
6. **Push** to your fork
7. **Create Pull Request** with:
   - Clear title & description
   - Link to related issue
   - Screenshots (if UI changes)
   - Test results

### Code Style
- **Language:** Kotlin (no Java)
- **Formatter:** Ktlint (run `./gradlew ktlintFormat`)
- **Naming:** camelCase for variables/functions, PascalCase for classes
- **Comments:** Explain WHY, not WHAT
- **Line length:** 120 characters max

### Commit Message Format
```
<type>: <subject>

<body>

<footer>
```

**Types:** feat, fix, docs, style, refactor, perf, test

**Example:**
```
fix: Prevent false exactness in IV solver with Arc Policy

- Arc narrowing now requires 20% threshold minimum
- Multiple→1 results downgraded from EXACT to RANGE
- Confidence calculated and logged for transparency
- Fixes issue #42

Closes #42
```

---

## 📄 License

This project is licensed under the **Apache License 2.0** – see [LICENSE](LICENSE) file for details.

---

## 📞 Contact & Support

- **Bug Reports:** [GitHub Issues](https://github.com/chaglaruk/PokemonRarityScanner/issues)
- **Feature Requests:** [GitHub Discussions](https://github.com/chaglaruk/PokemonRarityScanner/discussions)
- **Developer:** [@chaglaruk](https://github.com/chaglaruk)

---

## 🎓 Additional Resources

- [Arch. Decision Records](./DEPLOYMENT_GUIDE.md) – Technical decisions
- [Arc Policy Deep Dive](./ARC_POLICY_SUMMARY.md) – IV solver behavior
- [Android Jetpack Docs](https://developer.android.com/jetpack)
- [Compose Documentation](https://developer.android.com/jetpack/compose)
- [Tesseract OCR Guide](https://github.com/tesseract-ocr/tesseract)

---

**Last Updated:** April 6, 2026  
**Current Version:** 1.0  
**Status:** ✅ Production Ready
