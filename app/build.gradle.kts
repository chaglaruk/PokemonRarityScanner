import java.util.Properties
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

val localProps = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

fun configValue(localKey: String, envKey: String): String =
    (localProps.getProperty(localKey)
        ?: (findProperty(localKey) as? String)
        ?: System.getenv(envKey)
        ?: "").trim()

val appVersionCode = configValue("pokerarity.versionCode", "POKERARITY_VERSION_CODE").toIntOrNull() ?: 2
val appVersionName = configValue("pokerarity.versionName", "POKERARITY_VERSION_NAME").ifBlank { "1.1.0" }
val releaseStoreFile = configValue("releaseStoreFile", "POKERARITY_RELEASE_STORE_FILE")
val releaseStorePassword = configValue("releaseStorePassword", "POKERARITY_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = configValue("releaseKeyAlias", "POKERARITY_RELEASE_KEY_ALIAS")
val releaseKeyPassword = configValue("releaseKeyPassword", "POKERARITY_RELEASE_KEY_PASSWORD")
val hasReleaseSigning =
    releaseStoreFile.isNotBlank() &&
        releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank()

android {
    namespace = "com.pokerarity.scanner"
    compileSdk = 35

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.pokerarity.scanner"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        val telemetryBaseUrl = localProps.getProperty("scanTelemetryBaseUrl", "").trim()
        val telemetryEnabled = telemetryBaseUrl.isNotBlank()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SCAN_TELEMETRY_BASE_URL", "\"$telemetryBaseUrl\"")
        buildConfigField("boolean", "SCAN_TELEMETRY_ENABLED", telemetryEnabled.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
}

android {
    applicationVariants.all {
        outputs.all {
            val apkName = "PokeRarityScanner-v${versionName ?: appVersionName}-${buildType.name}.apk"
            (this as BaseVariantOutputImpl).outputFileName = apkName
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    // Android Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // SQLCipher for database encryption
    implementation("net.zetetic:sqlcipher-android:4.5.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Tesseract OCR
    implementation("com.rmtheis:tess-two:9.1.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.opencv:opencv:4.10.0")

    // Image Processing
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    // Gson for JSON
    implementation("com.google.code.gson:gson:2.11.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.54")
    kapt("com.google.dagger:hilt-compiler:2.54")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}
