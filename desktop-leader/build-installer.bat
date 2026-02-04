@echo off
chcp 65001 >nul 2>&1
cls
echo ========================================
echo RecSync Leader - EXE Installer Package Tool
echo ========================================
echo.

:: Check JAVA_HOME
if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME environment variable not found
    echo.
    echo Please install JDK 17 or higher and set JAVA_HOME
    echo Download: https://adoptium.net/
    echo.
    pause
    exit /b 1
)

echo [INFO] WiX Toolset is required for creating Windows installers
echo [INFO] If jpackage fails, please install WiX Toolset from:
echo [INFO] https://wixtoolset.org/releases/
echo.

echo [1/3] Cleaning old builds...
call ..\gradlew.bat clean

echo.
echo [2/3] Building project...
call ..\gradlew.bat build
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed, please check error messages
    pause
    exit /b 1
)

echo.
echo [3/3] Creating installer...
call ..\gradlew.bat jpackage
if errorlevel 1 (
    echo.
    echo [ERROR] Packaging failed, please check error messages
    pause
    exit /b 1
)

echo.
echo ========================================
echo Package Complete!
echo ========================================
echo.
echo Installer location: build\installer\RecSync-Leader-1.0.0.exe
echo.
echo You can distribute this EXE file to control computer users
echo.
pause
