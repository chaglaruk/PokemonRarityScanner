# Purpose: Track autonomous development tasks, risk, and verification.

# Autonomous Development Log

## Avoid local screenshot path exposure in ScanManager logs

- Why it matters: failed OCR/decode logs currently include local screenshot paths,
  which can expose user folder names or diagnostic file locations.
- Files touched: `ScanManager.kt`, safe debug log helper, helper unit test.
- Risk level: low. The change affects log text only and does not alter scan
  pipeline decisions.
- Verification: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
  and `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`.
