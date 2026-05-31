# Gradle & SDK Warning Audit

## Current Warning State

During `testDebugUnitTest` and `assembleDebug`, three primary classes of warnings are emitted. They are currently non-fatal but pollute the build logs and indicate future compatibility cliffs.

### 1. SDK XML Version Mismatch
**Warning:** 
`Warning: SDK processing. This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered. This can happen if you use versions of Android Studio and the command-line tools that were released at different times.`

**Root Cause:** 
The Android SDK Command-line Tools installed on the build environment are slightly older than the repository/SDK packages downloaded by a newer Android Studio. Specifically, `repository2-3.xsd` vs `repository2-4.xsd` schemas.

**Safe Next Steps:** 
- Low priority. Does not affect the final compiled artifact.
- Fix requires updating `cmdline-tools` via `sdkmanager` on the local machine/CI environment. No repository `build.gradle` changes needed.

### 2. Gradle 9.0 Deprecation Warnings
**Warning:** 
`Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.`

**Root Cause:** 
The project is currently running Gradle 8.9. Various plugins (likely older versions of Hilt, Kapt, or Android Gradle Plugin) are using APIs that will be removed in 9.0.

**Safe Next Steps:** 
- Run `./gradlew testDebugUnitTest --warning-mode all` to explicitly identify the offending plugins.
- Before considering a Gradle 9.0 upgrade, `AGP` (Android Gradle Plugin) and `KSP`/`Kapt` must be audited and updated.
- Currently, NO action is required. Upgrading major Gradle versions poses high risk for legacy codebases and should be planned independently.

### 3. Kapt Unrecognized Options
**Warning:** 
`warning: The following options were not recognized by any processor: '[dagger.fastInit, dagger.hilt.android.internal.disableAndroidSuperclassValidation, dagger.hilt.android.internal.projectType, dagger.hilt.internal.useAggregatingRootProcessor, kapt.kotlin.generated]'`

**Root Cause:** 
Kapt passes Hilt/Dagger compiler arguments to all annotation processors, including those that do not understand Dagger flags (like Room or Glide).

**Safe Next Steps:** 
- Harmless warning. 
- Long-term fix is migrating from `kapt` to `ksp` (Kotlin Symbol Processing), which handles processor arguments much more cleanly and compiles significantly faster.

## Conclusion
No immediate Gradle or `build.gradle.kts` changes are recommended. The build is successful and stable. Major version bumps (Gradle 9, KSP migration) should be treated as isolated, high-risk refactor tickets.
