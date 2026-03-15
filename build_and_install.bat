@echo off
cd /d C:\Users\Caglar\Desktop\PokeRarityScanner
echo [BUILD] Starting...
call gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo [BUILD] FAILED
    exit /b 1
)
echo [BUILD] Success. Installing...
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% EQU 0 (
    echo [INSTALL] Success!
) else (
    echo [INSTALL] Failed
)
exit /b 0
