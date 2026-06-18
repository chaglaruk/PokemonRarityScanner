# Build And Test

Use PowerShell from the repository root.

Focused unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

Debug build:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Do not run release builds unless explicitly requested.
