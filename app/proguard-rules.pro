# ============================================
# PokeRarityScanner - R8/ProGuard Configuration
# Security & Optimization Rules
# ============================================

# --------------------------------------------
# SECURITY: Obfuscate critical classes
# --------------------------------------------

# Obfuscate package names for security (except Hilt/Room need keep)
-obfuscationfilter:
  -keeppackagenames com.pokerarity.scanner.data.remote.**
  -keeppackagenames com.pokerarity.scanner.service.**
  -keeppackagenames com.pokerarity.scanner.util.**

# Encrypt string constants (API keys, URLs) - makes reverse engineering harder
# Note: Not true encryption, but adds layer of obfuscation
-encryptstrings:
  -encryptnomethodwarning

# --------------------------------------------
# Tesseract (tess-two)
# --------------------------------------------
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keepclassmembers class com.googlecode.tesseract.android.** { *; }
-dontwarn com.googlecode.tesseract.android.**

# --------------------------------------------
# SQLCipher - Keep encryption classes
# --------------------------------------------
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.**

# --------------------------------------------
# AndroidX Security - EncryptedSharedPreferences
# --------------------------------------------
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.**

# --------------------------------------------
# Gson - JSON serialization
# --------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# --------------------------------------------
# Room Database
# --------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# --------------------------------------------
# Hilt Dependency Injection
# --------------------------------------------
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keepclassmembers class * {
    @dagger.hilt.* <methods>;
}

# --------------------------------------------
# Kotlin Serialization
# --------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --------------------------------------------
# Keep data model classes
# --------------------------------------------
-keep class com.pokerarity.scanner.data.model.** { *; }
-keep class com.pokerarity.scanner.data.local.db.** { *; }
-keep class com.pokerarity.scanner.data.local.** { *; }

# --------------------------------------------
# Kotlin data classes
# --------------------------------------------
-keepclassmembers class com.pokerarity.scanner.** extends java.lang.Object {
    public <methods>;
}
-keepclassmembers class * {
    @kotlin.Metadata *;
}

# --------------------------------------------
# Security: Remove logging in release
# --------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-allowaccessmodification

# --------------------------------------------
# Debug info for crash reports
# --------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renameSourceFileAttribute SourceFile
-repackageclasses 'sg'

# --------------------------------------------
# Optimization: Shrink & optimize
# --------------------------------------------
-optimizationpasses 5
-dontpreverify
-verbose

# --------------------------------------------
# Whitelist: Cannot be obfuscated
# --------------------------------------------
-keep class com.pokerarity.scanner.ui.main.MainActivity { *; }
-keep class com.pokerarity.scanner.ui.splash.SplashActivity { *; }
-keep class com.pokerarity.scanner.BootReceiver { *; }
-keep class com.pokerarity.scanner.ScanFixtureExportReceiver { *; }
-keep class com.pokerarity.scanner.service.OverlayService { *; }
-keep class com.pokerarity.scanner.service.ScreenCaptureService { *; }
