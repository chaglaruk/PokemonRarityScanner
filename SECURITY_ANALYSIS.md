# PokeRarityScanner Security Analysis Report

**Date:** April 6, 2025  
**App:** PokeRarityScanner (Android 13+, targetSdk 35)  
**Analysis Scope:** Complete codebase security audit  
**Status:** 🔴 **CRITICAL ISSUES FOUND - IMMEDIATE ACTION REQUIRED**

---

## Executive Summary

PokeRarityScanner is a Kotlin/Jetpack Compose Android application that uses OCR to scan Pokémon GO inventory and calculate rarity scores. The app captures screenshots via MediaProjection and processes them through Tesseract OCR. While the core architecture follows modern Android best practices (proper foreground service lifecycle, Jetpack libraries, type-safe Kotlin), **critical security vulnerabilities exist** that could lead to:

- **Data breach:** Unencrypted storage of full Pokémon inventory data (CP, candy, stardust, caught dates)
- **Man-in-the-middle attacks:** No SSL certificate pinning for GitHub update checks
- **Privacy violation:** Screen capture data contains PII from entire device, telemetry uploads without explicit consent
- **Intent manipulation:** Exported components allow malicious intent injection
- **Unauthorized operations:** Debug receivers publicly accessible

**Risk Level:** 🔴 **CRITICAL** — Should not be released in current state

---

## 1. Threats & Attack Surface

### 1.1 Threat Model

```
┌─────────────────────────────────────────────────────────────────────┐
│                    PokeRarityScanner Threat Model                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  EXTERNAL ATTACKER (No app installation)                           │
│  ├─ TLS MITM attack on update check (GitHub URL, no pinning)       │
│  ├─ Broadcast receiver injection (ACTION_CAPTURE_REQUESTED)        │
│  └─ Intent spoofing (SplashActivity exported)                      │
│                                                                     │
│  MALICIOUS APP (Installed on device)                               │
│  ├─ Send fake CAPTURE_REQUESTED broadcasts → drain battery         │
│  ├─ Redirect deep links → SplashActivity hijacking                 │
│  ├─ Read unencrypted database → full Pokémon inventory             │
│  ├─ Intercept telemetry uploads → capture API keys                 │
│  └─ Overlay attacks (SYSTEM_ALERT_WINDOW)                          │
│                                                                     │
│  PHYSICAL ATTACKER (Device theft/root access)                      │
│  ├─ Extract unencrypted Pokémon stats from Room DB                 │
│  ├─ Read cached screenshot files                                   │
│  ├─ Access OCR diagnostics with raw text                           │
│  └─ Extract API keys from BuildConfig                              │
│                                                                     │
│  USER PRIVACY VIOLATION                                            │
│  ├─ Telemetry sent WITHOUT explicit opt-in consent                 │
│  ├─ Full device screenshots uploaded (contains all app data)       │
│  ├─ Raw OCR text includes sensitive Pokémon details                │
│  └─ GDPR non-compliance (no data disclosure, no retention policy)   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 Data Flow Security Analysis

```
USER SCANS POKÉMON
        ↓
ScreenCaptureService captures full screen (ImageReader)
        ↓ [🔴 RISK: Full screenshop contains PII from all apps]
OCRProcessor extracts text (Tesseract)
        ↓ [🟡 RISK: Raw OCR text stored, contains Pokémon stats]
FullVariantMatcher classifies Pokémon
        ↓
PokemonData object created (CP, HP, candy, stardust, caught date)
        ↓ [🟡 RISK: User inventory data, no encryption]
ScanManager generates telemetry upload ID
        ↓ [🔴 RISK: Telemetry ALWAYS queued if enabled at build time]
TelemetryRepository saves to Room Database
        ↓ [🔴 RISK: Unencrypted database, accessible if rooted]
ScanTelemetryCoordinator.enqueueAndFlush() called
        ↓ [🔴 RISK: No user consent check, automatic upload]
Screenshots copied to cache directory
        ↓ [🟡 RISK: App cache may be readable depending on API level]
ScanTelemetryUploader sends via HttpURLConnection
        ↓ [🔴 RISK: No certificate pinning, HTTP url schemes possible]
To: config.baseUrl (BuildConfig value, unvalidated)
        ↓ [🔴 RISK: If baseUrl points to attacker server, data leaked]
Remote server receives:
  - Full Pokémon stats + family tree
  - Raw OCR text (player name, date, amounts)
  - Full device screenshot (build.MANUFACTURER, build.MODEL)
  - Device API version
  - Package version
  - App installation state
  ↓ [🟠 RISK: No data retention/deletion policy mentioned]
```

---

## 2. Critical Vulnerabilities (Must Fix Immediately)

### 🔴 CRITICAL-1: Unencrypted Database with Sensitive Pokémon Inventory

**Location:** [app/src/main/java/com/pokerarity/scanner/data/local/db/](app/src/main/java/com/pokerarity/scanner/data/local/db/)

**Severity:** 🔴 **CRITICAL**

**Issue:**
- Room database stores full Pokémon inventory without encryption
- Data includes CP, HP, candy cost, stardust cost, caught date, rarity tier, IV estimates
- Accessible if device is rooted or physical backup is extracted

**Risk:**
- Complete reconstruction of user's Pokémon GO inventory
- Privacy violation (timestamps + location inference from "caught date")
- Linked to user identity if database filename contains user ID

**Proof of Concept:**
```bash
# On rooted device:
adb shell
su
cat /data/data/com.pokerarity.scanner/databases/poke_rarity.db | sqlite3
SELECT pokemon_name, cp, hp, stardust, candy, caught_date FROM pokemon_table;
# Returns all scan history with sensitive stats
```

**Recommendation:**
- **Priority:** IMMEDIATE (this week)
- Integrate SQLCipher for Room database encryption
- Encrypt using Android Keystore-managed key
- Implementation:
  ```kotlin
  // Add to build.gradle.kts
  dependencies {
      implementation("net.zetetic:android-database-sqlcipher:4.5.7")
  }

  // In DatabaseModule.kt
  val password = getEncryptionKey() // From Android Keystore
  val db = Room.databaseBuilder(context, AppDatabase::class.java, "poke_rarity.db")
      .openHelperFactory(SupportSQLiteOpenHelper.Configuration.Builder(context)
          .name("poke_rarity.db")
          .callback(object : RoomDatabase.Callback() {
              override fun onOpen(db: SupportSQLiteDatabase) {
                  db.passphrase(password)
              }
          }).build())
      .build()
  ```

---

### 🔴 CRITICAL-2: Hardcoded GitHub Update URL Without SSL Certificate Pinning

**Location:** [Constants.kt](app/src/main/kotlin/Constants.kt#L5)

**Severity:** 🔴 **CRITICAL**

**Issue:**
```kotlin
const val GITHUB_UPDATE_URL = 
    "https://raw.githubusercontent.com/chaglaruk/PokemonRarityScanner/main/updates.json"
```
- Endpoint is hardcoded with no certificate pinning
- Uses HttpURLConnection (manual SSL/TLS handling)
- No validation of certificate chain or public key

**Risk:**
- **Man-in-the-middle attack:** Attacker on same network (WiFi, ISP) can intercept HTTPS traffic
- **Malicious update injection:** Attacker serves fake `updates.json` with modified download URL
- **App downgrade:** Force user to downgrade to vulnerable version
- **Credential theft:** If update URL changed to attacker server, credentials could be exposed

**Attack Scenario:**
```
User connects to public WiFi (attacker's hotspot or compromised AP)
  ↓
User opens PokeRarityScanner
  ↓
AppStartup → calls update check → fetches GITHUB_UPDATE_URL
  ↓
Attacker intercepts via MITM proxy (Burp, mitmproxy, arpspoof)
  ↓
Attacker returns fake JSON:
{
  "latest_version": "1.0",
  "download_url": "https://attacker.com/malicious.apk",
  "forced_update": true
}
  ↓
User forced to install attacker's APK with backdoor
```

**Recommendation:**
- **Priority:** IMMEDIATE (this week)
- Implement Network Security Configuration with certificate pinning
- Create [res/xml/network_security_config.xml](res/xml/network_security_config.xml):
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <network-security-config>
      <domain-config cleartextTrafficPermitted="false">
          <domain includeSubdomains="true">raw.githubusercontent.com</domain>
          <pin-set expiration="2026-04-06">
              <!-- GitHub's certificate public key hash (SHA-256) -->
              <pin digest="SHA-256">
                  WoiWtaAvMSd51L+FwBbHolIkVcKOrZLsV3OwOsswYL0=
              </pin>
          </pin-set>
      </domain-config>
  </network-security-config>
  ```
- Reference in [AndroidManifest.xml](AndroidManifest.xml):
  ```xml
  <application android:networkSecurityConfig="@xml/network_security_config">
  ```

---

### 🔴 CRITICAL-3: Telemetry Uploads Without Explicit User Consent (GDPR Violation)

**Location:** [ScanTelemetryCoordinator.kt](app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryCoordinator.kt), [ScanManager.kt](app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt#L372)

**Severity:** 🔴 **CRITICAL**

**Issue:**
- Telemetry enabled if `SCAN_TELEMETRY_BASE_URL` configured at build time
- **NO user-facing UI to enable/disable telemetry**
- User is not informed data is being collected
- Every scan automatically queues telemetry upload
- Data includes raw OCR text, full Pokémon stats, device fingerprint

**Proof:**
```kotlin
// ScanManager.kt line 372-430
val telemetryUploadId = telemetryCoordinator.newUploadIdOrNull()
// ... later ...
telemetryCoordinator.enqueueAndFlush(
    uploadId = telemetryUploadId,
    pokemonData = pokemon,
    features = visualFeatures,
    rarityScore = rarityScore,
    screenshotPath = screenshotPath,
    pipelineMs = endTime - startTime,
    phase2Result = phase2Result
)
// enqueueAndFlush() does NOT check user preferences
// Data is ALWAYS queued if build-time flag is true
```

**Data Uploaded (Sensitive Fields):**
```kotlin
ScanTelemetryPayload(
    uploadId = uploadId,
    prediction = PredictionInfo(
        species = pokemonData.realName,  // "Mareep"
        cp = pokemonData.cp,              // 742 (identifies specific Pokémon)
        hp = pokemonData.hp,              // 147/147
        stardustCost = pokemonData.stardust,  // 10000 (level inference)
        candyCost = pokemonData.powerUpCandyCost,  // 15000
        caughtDateEpochMs = pokemonData.caughtDate?.time,  // Timestamp
        isShiny = features.isShiny,
        isShadow = features.isShadow,
        isLucky = features.isLucky,
        hasCostume = features.hasCostume
    ),
    debug = DebugInfo(
        rawOcrText = pokemonData.rawOcrText,  // 🔴 FULL RAW TEXT
        diagnosticFiles = pokemonData.ocrDiagnosticsFiles  // Diagnostic images
    ),
    device = DeviceInfo(
        manufacturer = Build.MANUFACTURER,  // "samsung"
        model = Build.MODEL,                // "SM-G991B" (unique identifier)
        sdkInt = Build.VERSION.SDK_INT
    )
)
```

**Risk:**
- **Privacy violation:** User's complete Pokémon inventory uploaded without knowledge
- **GDPR non-compliance:** No valid consent, no privacy notice, no opt-in mechanism
- **CCPA violation:** (if US residents) Lack of opt-out mechanism
- **Data linking:** Device fingerprint + timestamp allows identity linking
- **Regulatory fines:** Up to 4% of global revenue (GDPR) or $7,500 per violation (CCPA)

**Recommendation:**
- **Priority:** IMMEDIATE (critical compliance issue)
- **Implementation:**
  1. Create first-run onboarding screen asking for telemetry consent
  2. Store user preference in encrypted SharedPreferences
  3. Only generate uploadId if user opts in
  4. Add Settings screen to toggle telemetry on/off
  5. Document privacy policy in README

```kotlin
// New: TelemetryPreferences.kt
class TelemetryPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("telemetry_prefs", Context.MODE_PRIVATE)
    
    var userConsent: Boolean
        get() = prefs.getBoolean("user_consent", false)
        set(value) = prefs.edit().putBoolean("user_consent", value).apply()
    
    var consentTimestamp: Long
        get() = prefs.getLong("consent_timestamp", 0L)
        set(value) = prefs.edit().putLong("consent_timestamp", value).apply()
}

// Modified: ScanTelemetryCoordinator
class ScanTelemetryCoordinator(context: Context) {
    private val telemetryPrefs = TelemetryPreferences(context)
    private val repository = ScanTelemetryRepository(context)
    
    fun newUploadIdOrNull(): String? {
        // 🔴 FIX: Check user consent BEFORE creating upload ID
        if (!repository.isEnabled()) return null
        if (!telemetryPrefs.userConsent) return null  // NEW
        return repository.newUploadId()
    }
}

// New: Onboarding dialog (MainActivity)
if (!telemetryPrefs.hasSeenOnboarding) {
    TelemetryConsentDialog(
        onAccept = {
            telemetryPrefs.userConsent = true
            telemetryPrefs.consentTimestamp = System.currentTimeMillis()
            telemetryPrefs.hasSeenOnboarding = true
        },
        onReject = {
            telemetryPrefs.userConsent = false
            telemetryPrefs.hasSeenOnboarding = true
        }
    )
}
```

---

### 🔴 CRITICAL-4: Exported Debug Broadcast Receiver Publicly Accessible

**Location:** [AndroidManifest.xml](AndroidManifest.xml)

**Severity:** 🔴 **CRITICAL**

**Issue:**
```xml
<receiver 
    android:name=".ui.debug.ScanFixtureExportReceiver"
    android:exported="true" />  <!-- 🔴 EXPORTED WITHOUT PERMISSION CHECK -->
```

- Broadcast receiver is exported without permission guard
- Any app can send broadcasts to trigger fixture export
- Can be used to trigger unintended data export

**Risk:**
- **Information disclosure:** Malicious app triggers export of all scanned Pokémon data
- **DOS attack:** Spam fixture exports to fill storage
- **Data leakage:** Exported fixtures contain full inventory

**Proof of Concept:**
```kotlin
// Malicious app can do this:
val intent = Intent("com.pokerarity.scanner.ui.debug.EXPORT_FIXTURES")
    .setPackage("com.pokerarity.scanner")
sendBroadcast(intent)  // NO PERMISSION REQUIRED
// Receiver exports all Pokémon data
```

**Recommendation:**
- **Priority:** IMMEDIATE (this week)
- **Option 1 (Recommended for release):** Remove debug receiver from release build
- **Option 2:** Add permission guard

```kotlin
// Option 1: Build variant exclusion
// In build.gradle.kts, add sourceSet filter
android {
    sourceSets {
        getByName("release") {
            // Don't include debug receiver in release builds
            manifest.srcFile("src/release/AndroidManifest.xml")
        }
    }
}

// Option 2: Permission guard (if debug features needed)
// AndroidManifest.xml
<uses-permission android:name="com.pokerarity.scanner.DEBUG_EXPORT" />

<receiver 
    android:name=".ui.debug.ScanFixtureExportReceiver"
    android:exported="true"
    android:permission="com.pokerarity.scanner.DEBUG_EXPORT" />

// In Hilt module
@Module
@InstallIn(SingletonComponent::class)
object DebugModule {
    @Provides
    fun provideIsDebugBuild(): Boolean = BuildConfig.DEBUG
}

// In ScanFixtureExportReceiver
class ScanFixtureExportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return  // Fail silently in release
        if (context.checkCallingPermission("com.pokerarity.scanner.DEBUG_EXPORT") 
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("ScanFixtureExportReceiver", "Permission denied for export")
            return
        }
        // ... export logic ...
    }
}
```

---

### 🔴 CRITICAL-5: Full Screen Capture Contains PII From All Apps

**Location:** [ScreenCaptureService.kt](app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureService.kt)

**Severity:** 🔴 **CRITICAL**

**Issue:**
- App captures entire device screen using MediaProjection
- Screenshot contains data from ALL apps visible (Pokémon GO, messages, contacts, etc.)
- Screenshots are copied to cache and uploaded via telemetry

**Data at Risk:**
- Player name, friend list, gift sender info (from Pokémon GO)
- Personal information visible in app multitasking
- Banking/payment info if finance app was open
- Location data from maps if scrolling
- Private messages if notification bar visible

**Risk:**
- **Privacy violation:** Full device data exposure without user awareness
- **PII leakage:** Screenshots contain personally identifiable information
- **Compliance risk:** GDPR/CCPA requires explicit consent for recording biometric/screen data

**Current Flow (Dangerous):**
```kotlin
// ScreenCaptureService.kt
val surface = virtualDisplay.surface  // Full device screen
val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
imageReader.acquireLatestImage()  // 🔴 Captures EVERYTHING
val plane = image.planes[0]
val pixelBuffer = plane.buffer
// ... later in ScanManager ...
val screenshotPath = captureScreenshot()  // Cached
enqueueAndFlush(..., screenshotPath)  // Uploaded to server
```

**Recommendation:**
- **Priority:** IMMEDIATE (critical privacy issue)
- **Implementation:**
  1. Crop screenshots to only Pokémon GO window
  2. Delete screenshots immediately after OCR processing
  3. Never persist raw screenshots
  4. Never upload screenshots to remote server

```kotlin
// ScreenCaptureManager.kt - MODIFIED
class ScreenCaptureManager {
    fun captureAndProcessScreenshot(): ByteArray? {
        // Step 1: Capture full screen (if permission allows)
        val fullScreenshot = captureFullScreen()  // ImageReader → Bitmap
        
        // Step 2: IMMEDIATELY crop to Pokémon GO window bounds
        // Detect Pokémon GO UI elements (name area, CP area, type labels)
        val croppedBitmap = cropToPokemonGoWindow(fullScreenshot)
        
        // Step 3: Extract bytes for OCR
        val ocr_ready_bytes = bitmapToJpeg(croppedBitmap)
        
        // Step 4: 🔴 DELETE raw screenshot
        fullScreenshot?.recycle()
        
        // Step 5: Return ONLY the bytes to OCR processor
        return ocr_ready_bytes
    }
    
    fun saveForOcr(bytes: ByteArray): String {
        // Save to TEMP file (not cache that persists)
        val tempFile = File.createTempFile("scan_", ".png", context.noBackupFilesDir)
        tempFile.writeBytes(bytes)
        // File auto-deleted after OCR completes
        return tempFile.absolutePath
    }
}

// ScanTelemetryRepository.kt - MODIFIED
class ScanTelemetryRepository {
    suspend fun enqueueScan(...) {
        if (!uploader.isEnabled()) return null
        // 🔴 FIX: Do NOT copy/upload screenshots
        // Remove this line:
        // val copiedScreenshot = screenshotPath?.let { copyScreenshot(uploadId, it) }
        
        val payload = buildPayload(
            // ... other fields ...
            screenshotPath = null  // Don't include screenshot in telemetry
        )
        return dao.insert(
            TelemetryUploadEntity(
                uploadId = uploadId,
                payloadJson = gson.toJson(payload),
                screenshotPath = null  // 🔴 No screenshot upload
            )
        )
    }
}
```

---

## 3. High-Risk Vulnerabilities (Fix Before Release)

### 🟡 HIGH-1: Exported Launcher Activity Allows Intent Spoofing

**Location:** [AndroidManifest.xml](AndroidManifest.xml)

**Severity:** 🟡 **HIGH**

**Issue:**
```xml
<activity 
    android:name=".ui.splash.SplashActivity"
    android:exported="true">  <!-- 🟡 EXPORTED, entry point vulnerable -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

**Risk:**
- Malicious app can send intents to SplashActivity with crafted extras
- Could redirect user flow, inject data, trigger unintended actions

**Recommendation:**

```kotlin
// AndroidManifest.xml - FIX
<activity 
    android:name=".ui.splash.SplashActivity"
    android:exported="false">  <!-- Changed to false -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

// Alternative if deep linking is needed:
<activity 
    android:name=".ui.splash.SplashActivity"
    android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" 
              android:host="pokerarity.app" />
    </intent-filter>
</activity>

// In SplashActivity: validate intent data
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val intent = intent
    if (intent.action == Intent.ACTION_VIEW) {
        // Only handle verified deep links
        val uri = intent.data
        if (uri?.host != "pokerarity.app") {
            Log.w("SplashActivity", "Invalid deep link: $uri")
            finish()
            return
        }
    } else if (intent.action == Intent.ACTION_MAIN) {
        // Normal app launch, OK
    } else {
        // Unknown action, reject
        Log.w("SplashActivity", "Unexpected intent action: ${intent.action}")
        finish()
        return
    }
    
    // Continue with normal flow
}
```

---

### 🟡 HIGH-2: No Network Security Configuration (Cleartext HTTP Possible)

**Location:** Project-wide (missing file)

**Severity:** 🟡 **HIGH**

**Issue:**
- No `res/xml/network_security_config.xml` file
- HttpURLConnection calls lack explicit TLS enforcement
- App could send data over unencrypted HTTP

**Risk:**
- Telemetry data sent in plaintext on compromised networks
- API keys exposed over HTTP

**Recommendation:**

Create [res/xml/network_security_config.xml](res/xml/network_security_config.xml):
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Disable cleartext traffic for all domains -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">.</domain>
    </domain-config>

    <!-- If telemetry backend defined, pin its certificate -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">your-telemetry-server.com</domain>
        <pin-set expiration="2026-04-06">
            <!-- Your server's public key hash -->
            <pin digest="SHA-256">YOUR_CERT_HASH_HERE</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

Reference in [AndroidManifest.xml](AndroidManifest.xml):
```xml
<application 
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
</application>
```

---

### 🟡 HIGH-3: SYSTEM_ALERT_WINDOW Permission Enables Overlay Attacks

**Location:** [AndroidManifest.xml](AndroidManifest.xml)

**Severity:** 🟡 **HIGH**

**Issue:**
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

- Permission allows overlaying content on top of all apps
- Could display fake dialogs over Pokémon GO to trick users
- Malicious overlay could intercept gestures

**Risk:**
- Phishing attack: Overlay fake login dialog
- Theft: Fake "confirm purchase" dialog
- Credential interception

**Recommendation:**

```kotlin
// OverlayService.kt - SECURITY HARDENING
class OverlayService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 🔴 SECURITY: Only show overlay if app is in foreground
        val isForeground = isAppInForeground()
        if (!isForeground) {
            Log.w("OverlayService", "Overlay blocked: app not in foreground")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Only show non-interactive overlay (results, not UI)
        showReadOnlyResultOverlay()
        
        return super.onStartCommand(intent, flags, startId)
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcesses = activityManager.runningAppProcesses ?: return false
        val myUid = android.os.Process.myUid()
        for (appProcess in runningAppProcesses) {
            if (appProcess.uid == myUid && appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true
            }
        }
        return false
    }
}
```

---

### 🟡 HIGH-4: Unguarded Broadcast Receivers Enable DOS/Privacy Attacks

**Location:** [ScreenCaptureService.kt](app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureService.kt)

**Severity:** 🟡 **HIGH**

**Issue:**
```kotlin
val captureReceiver = BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 🟡 NO PERMISSION CHECK, NO RATE LIMITING
        requestCapture()
    }
}
// Registered with ACTION_CAPTURE_REQUESTED
```

- Any app can send ACTION_CAPTURE_REQUESTED
- No rate limiting causes DOS (battery drain)

**Recommendation:**

```kotlin
// ScreenCaptureService.kt - SECURITY HARDENING
class ScreenCaptureService : Service() {
    private val captureRateLimiter = RateLimiter(maxRequestsPerMinute = 10)
    
    private fun setupBroadcastReceiver() {
        val captureReceiver = BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // 🟡 FIX 1: Rate limiting
                if (!captureRateLimiter.canCapture()) {
                    Log.w("ScanCaptureService", "Capture request rate-limited")
                    return
                }
                
                // 🟡 FIX 2: Permission check (requires custom permission)
                if (context.checkCallingPermission("com.pokerarity.scanner.INTERNAL_CAPTURE") 
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w("ScreenCaptureService", "Capture request denied: no permission")
                    return
                }
                
                requestCapture()
            }
        }
        
        // 🟡 FIX 3: Use internal broadcast only, not global
        LocalBroadcastManager.getInstance(this).registerReceiver(
            captureReceiver,
            IntentFilter(ACTION_CAPTURE_REQUESTED)
        )
    }
}

// Define internal permission in AndroidManifest.xml
<permission 
    android:name="com.pokerarity.scanner.INTERNAL_CAPTURE"
    android:protectionLevel="signature" />

// RateLimiter helper
class RateLimiter(private val maxRequestsPerMinute: Int = 10) {
    private val requestTimestamps = mutableListOf<Long>()
    
    fun canCapture(): Boolean {
        val now = System.currentTimeMillis()
        // Remove timestamps older than 1 minute
        requestTimestamps.removeAll { now - it > 60_000 }
        
        return if (requestTimestamps.size < maxRequestsPerMinute) {
            requestTimestamps.add(now)
            true
        } else {
            false
        }
    }
}
```

---

### 🟡 HIGH-5: Unencrypted Local Database Accessible on Rooted Devices

**See CRITICAL-1 above** (Unencrypted Database)

---

### 🟡 HIGH-6: API Key Stored in BuildConfig (Accessible via Reflection)

**Location:** [app/build.gradle.kts](app/build.gradle.kts#L28)

**Severity:** 🟡 **HIGH**

**Issue:**
```kotlin
val telemetryApiKey = localProps.getProperty("scanTelemetryApiKey", "").trim()
buildConfigField("String", "SCAN_TELEMETRY_API_KEY", "\"$telemetryApiKey\"")
```

- API key stored in compiled APK (reversible)
- Attacker can extract via decompilation or reflection

```kotlin
// Attacker can extract via reflection:
val cls = Class.forName("com.pokerarity.scanner.BuildConfig")
val apiKeyField = cls.getField("SCAN_TELEMETRY_API_KEY")
val apiKey = apiKeyField.get(null) as String  // API key extracted!
```

**Recommendation:**
- Rotate API key frequently
- Implement API key validation on server (rate limiting, IP whitelisting)
- Consider server-side API key generation instead of hardcoding
- For production: Use device-level key management (Android Keystore)

```kotlin
// ScanTelemetryCoordinator - API key rotation
class ScanTelemetryCoordinator {
    fun rotateApiKeyIfNeeded() {
        val lastRotation = preferences.getLong("api_key_rotation", 0)
        if (System.currentTimeMillis() - lastRotation > 30 * 24 * 3600_000L) {
            // Request new API key from server (requires server endpoint)
            repository.requestNewApiKey()
            preferences.edit().putLong("api_key_rotation", System.currentTimeMillis()).apply()
        }
    }
}
```

---

## 4. Medium-Risk Vulnerabilities

### 🟠 MEDIUM-1: Raw OCR Text in Telemetry Contains Sensitive Player Data

**Location:** [ScanTelemetryRepository.kt](app/src/main/java/com/pokerarity/scanner/data/repository/ScanTelemetryRepository.kt#L145)

**Severity:** 🟠 **MEDIUM**

**Issue:**
```kotlin
debug = DebugInfo(
    rawOcrText = pokemonData.rawOcrText,  // 🟠 Full Pokémon stats as text
    diagnosticFiles = pokemonData.ocrDiagnosticsFiles  // Diagnostic images
)
```

- Raw OCR text includes: Pokémon name, CP, HP, candy cost, date text
- Could be used to reconstruct inventory

**Recommendation:**
- Strip PII before transmission
- Remove raw OCR from telemetry payload

```kotlin
private fun scrubPII(rawOcrText: String): String? {
    // Remove Pokémon stats patterns
    var scrubbed = rawOcrText
        .replaceAll("\\d{3,4}", "[STAT]")  // CP, HP values
        .replaceAll("\\d+/\\d+", "[FRACTION]")  // HP/maxHP
        .replaceAll("\\b20\\d{2}-\\d{2}-\\d{2}\\b", "[DATE]")  // Caught date
    
    if (scrubbed.isBlank()) return null
    return scrubbed
}

// In buildPayload():
debug = DebugInfo(
    rawOcrText = null,  // 🔴 Don't send
    // or:
    rawOcrText = scrubPII(pokemonData.rawOcrText),  // Scrubbed version
    diagnosticFiles = null  // 🔴 Don't send diagnostic images
)
```

---

### 🟠 MEDIUM-2: No Input Validation on Update JSON Response

**Location:** [ScanTelemetryRepository.kt](app/src/main/java/com/pokerarity/scanner/data/remote/ScanTelemetryUploader.kt#L160)

**Severity:** 🟠 **MEDIUM**

**Issue:**
```kotlin
val json = JsonParser.parseString(body).asJsonObject
val screenshotUrl = json.get("screenshot_url")?.asString
```

- No validation of JSON structure size
- No validation of URL format before using
- Could cause crash or unintended behavior

**Recommendation:**

```kotlin
companion object {
    private const val MAX_RESPONSE_SIZE = 1024 * 100  // 100 KB
    private const val MAX_URL_LENGTH = 2048
    
    internal fun parseScanUploadResponse(code: Int, body: String?): UploadResult {
        if (code !in 200..299) {
            return UploadResult(success = false, error = "HTTP $code")
        }
        if (body.isNullOrBlank() || body.length > MAX_RESPONSE_SIZE) {
            return UploadResult(success = false, error = if (body?.length ?: 0 > MAX_RESPONSE_SIZE) "Response too large" else "Empty response")
        }
        
        return runCatching {
            val json = JsonParser.parseString(body).asJsonObject
            val ok = json.get("ok")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
            
            val screenshotUrl = json.get("screenshot_url")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()
                ?.ifBlank { null }
                ?.takeIf { isValidUrl(it) }  // NEW: Validate URL
                
            // ... rest of parsing ...
        }.getOrElse {
            UploadResult(success = false, error = "Invalid JSON: ${it.message}")
        }
    }
    
    private fun isValidUrl(url: String): Boolean {
        return runCatching {
            val uri = java.net.URI(url)
            uri.scheme in listOf("https", "http") &&
            url.length <= MAX_URL_LENGTH &&
            !url.contains("javascript:") &&  // No JS URLs
            !url.contains("..") &&  // No path traversal
            uri.host?.isNotBlank() == true
        }.getOrDefault(false)
    }
}
```

---

### 🟠 MEDIUM-3: No Retention/Deletion Policy for Telemetry Data

**Location:** Project-wide

**Severity:** 🟠 **MEDIUM**

**Issue:**
- No documented retention policy for uploaded telemetry data
- No deletion reminders
- Local database grows indefinitely

**Risk:**
- GDPR requires data deletion rights
- Stale data poses security risk

**Recommendation:**
- Implement auto-deletion in code
- Document retention in README

```kotlin
// ScanTelemetryRepository.kt - Add cleanup
suspend fun deleteOldScans(olderThanDays: Int = 30) {
    val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 3600_000L)
    dao.deleteOlderThan(cutoffTime)
    Log.i("ScanTelemetryRepository", "Deleted scans older than $olderThanDays days")
}

// In PokeRarityApp onCreate:
lifecycleScope.launch {
    withContext(Dispatchers.IO) {
        ScanTelemetryCoordinator.getInstance(this@PokeRarityApp)
            .deleteOldScans(olderThanDays = 30)
    }
}
```

---

### 🟠 MEDIUM-4: Foreground Service Phase 2 Upgrade Missing Error Handling

**Location:** [ScreenCaptureService.kt](app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureService.kt#L100)

**Severity:** 🟠 **MEDIUM**

**Issue:**
- Two-phase foreground service startup could crash if phase 2 fails
- No graceful degradation if projection token not available

**Recommendation:**

```kotlin
// ScreenCaptureService.kt - Error handling for phase 2
private fun upgradeToMediaProjectionForeground() {
    try {
        val notification = buildProjectionNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 🟠 FIX: Wrap in try-catch
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else {
                    0
                }
            )
        }
    } catch (e: Exception) {
        Log.e("ScreenCaptureService", "Failed to upgrade to media projection foreground", e)
        // 🟠 FIX: Graceful degradation - stay in special use mode
        if (!isInSpecialUseForeground) {
            startSpecialUseForeground()
        }
    }
}
```

---

## 5. Security Recommendations Summary

### Phase 1: CRITICAL (This Week)

| # | Issue | Effort | Criticality |
|---|-------|--------|------------|
| 1 | Encrypt database with SQLCipher | 2 hours | 🔴 CRITICAL |
| 2 | Add certificate pinning for GitHub | 1.5 hours | 🔴 CRITICAL |
| 3 | Implement telemetry opt-in dialog | 3 hours | 🔴 CRITICAL |
| 4 | Remove exported debug receiver | 30 min | 🔴 CRITICAL |
| 5 | Disable screenshot persistence/upload | 1 hour | 🔴 CRITICAL |

**Estimated Time: 8 hours**

### Phase 2: HIGH (Before Release)

| # | Issue | Effort | Criticality |
|---|-------|--------|------------|
| 6 | Add Network Security Config | 1 hour | 🟡 HIGH |
| 7 | Validate/restrict exported activities | 1 hour | 🟡 HIGH |
| 8 | Rate-limit broadcast receivers | 1.5 hours | 🟡 HIGH |
| 9 | Add foreground detection for overlay | 1 hour | 🟡 HIGH |

**Estimated Time: 4.5 hours**

### Phase 3: MEDIUM (Next Release)

| # | Issue | Effort | Criticality |
|---|-------|--------|------------|
| 10 | Scrub PII from telemetry | 1 hour | 🟠 MEDIUM |
| 11 | Input validation on JSON responses | 1.5 hours | 🟠 MEDIUM |
| 12 | Add data retention/deletion policy | 1.5 hours | 🟠 MEDIUM |
| 13 | Error handling for foreground upgrade | 1 hour | 🟠 MEDIUM |

**Estimated Time: 5 hours**

---

## 6. Compliance & Privacy Assessment

### GDPR Compliance

**Current Status:** ❌ **NON-COMPLIANT**

**Issues:**
- [ ] No explicit opt-in for data collection (Article 4(11), 5(1)(a))
- [ ] No privacy policy displayed in app (Article 13, 14)
- [ ] No documented data retention policy (Article 5(1)(e))
- [ ] No mechanism to delete user data (Article 17)
- [ ] No Data Processing Agreement with server operators
- [ ] No privacy-by-default settings (Article 25(1))

**Required Actions:**
```markdown
1. Create Privacy Policy document (must address):
   - What data is collected (Pokémon stats, screenshots, device info)
   - Why it's collected (OCR processing, quality improvement)
   - How long it's retained (default: 30 days)
   - User rights (access, correction, deletion)
   - Server location and data protection measures

2. Implement Privacy Settings UI showing:
   - Toggle for telemetry with explanations
   - Link to full privacy policy
   - Option to delete local data
   - Data retention timeline

3. Add startup consent dialog:
   - "This app collects Pokémon scan data to improve accuracy"
   - "Accept" → enable telemetry
   - "Reject" → app works fully without telemetry
   - Must be opt-in (not pre-checked)
```

### CCPA Compliance

**Current Status:** ⚠️ **PARTIALLY NON-COMPLIANT** (if US residents use app)

**Issues:**
- No opt-out mechanism for data collection
- No right-to-know disclosures

**Required Actions (for US users):**
- Provide "Do Not Sell My Personal Information" button
- Honor opt-out signals (GPC header if telemetry backend sends)

### Recommended Privacy Policy Template

```markdown
# Privacy Policy - PokeRarityScanner

## Data Collection
- **What:** Pokémon stats (CP, HP, species, caught date), device info, OCR diagnostic data
- **Why:** To provide accurate rarity scoring and improve detection algorithms
- **How:** Automatic telemetry if user enables it during first launch

## User Control
- Telemetry is **opt-in** (requires user acceptance)
- Users can disable anytime in Settings
- App fully functional without telemetry (local-only mode)

## Data Retention
- Telemetry data deleted after **30 days**
- Local scan history can be manually cleared in app
- Device data (local database) deleted when app uninstalled

## Your Rights
- **Access:** Request copy of collected data
- **Deletion:** Delete account and associated data
- **Correction:** Update inaccurate information
- Contact: [dev email]

## Data Security
- Data encrypted in transit (HTTPS + certificate pinning)
- Database encrypted with SQLCipher
- No third-party sharing without consent
```

---

## 7. Testing Checklist

Before releasing any updates, verify:

### Security Testing
- [ ] Certificate pinning working (test with intercepting proxy)
- [ ] Database encrypted (verify using `adb shell` + sqlite3)
- [ ] No cleartext HTTP calls (use Burp Suite HTTPS proxy)
- [ ] Debug receiver not exported in release build
- [ ] Exported components properly defending input
- [ ] Rate limiting preventing DOS
- [ ] Screenshots not persisted after OCR
- [ ] Telemetry opt-in working
- [ ] Encryption keys handled by Android Keystore

### Privacy Testing
- [ ] Consent dialog appears on first launch
- [ ] Can toggle telemetry off
- [ ] App fully functional without telemetry
- [ ] Data deletion works
- [ ] No PII in logs/telemetry

### Functional Testing
- [ ] Screen capture still works after phase 2 error handling
- [ ] Update check succeeds with pinning
- [ ] Telemetry upload succeeds (if enabled)
- [ ] No battery drain from rate limiting
- [ ] Database query performance still acceptable with encryption

---

## 8. Files Requiring Changes

```
Priority | File | Changes | Complexity
---------|------|---------|----------
🔴 | AndroidManifest.xml | Remove exported="true" from ScanFixtureExportReceiver | Low
🔴 | build.gradle.kts | Add SQLCipher dependency, Network Security Config | Low
🔴 | Constants.kt | Add cert pinning constants | Low
🔴 | res/xml/network_security_config.xml | Create new file | Low
🔴 | ScreenCaptureService.kt | Remove screenshot upload, add deletion | Medium
🔴 | ScanTelemetryCoordinator.kt | Add user consent check | Medium
🟡 | ScanTelemetryRepository.kt | Encrypt database, validate JSON | High
🟡 | OverlayService.kt | Add foreground check | Low
🟡 | ScanManager.kt | Extract telemetry consent logic | Low
🟠 | ResultActivity.kt | Add consent dialog | Medium
🟠 | New: TelemetryPreferences.kt | Manage user settings | Low
🟠 | New: PrivacyPolicy.md | Document policy | Low
```

---

## 9. Conclusion

PokeRarityScanner exhibits solid architectural practices but requires **immediate security hardening** before production deployment. The combination of unencrypted sensitive data, automatic telemetry without consent, and exported debug components creates significant privacy and compliance risks.

**Estimated Effort to Production-Ready:** 16-18 hours across 3 phases

**Recommendation:** 
1. ✅ Complete all CRITICAL fixes (Phase 1) before any public release
2. ✅ Complete HIGH fixes (Phase 2) before distributing APK widely
3. ✅ Plan MEDIUM fixes (Phase 3) for next stable release when GDPR/CCPA compliance is needed

---

**Report Generated:** April 6, 2025  
**Analyzed By:** GitHub Copilot Security Audit  
**Codebase Version:** Latest (commit 0b1b905)
