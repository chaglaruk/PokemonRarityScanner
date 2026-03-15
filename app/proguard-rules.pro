# Tesseract (tess-two)
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keepclassmembers class com.googlecode.tesseract.android.** { *; }
-dontwarn com.googlecode.tesseract.android.**

# Gson
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

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *

# Keep data model classes (used with Gson serialization)
-keep class com.pokerarity.scanner.data.model.** { *; }
-keep class com.pokerarity.scanner.data.local.db.** { *; }

# Kotlin data classes (avoid stripping equals/hashCode/copy)
-keepclassmembers class com.pokerarity.scanner.** extends java.lang.Object {
    public <methods>;
}

# Debug info for crash reports
-keepattributes SourceFile,LineNumberTable
-renameSourceFileAttribute SourceFile
