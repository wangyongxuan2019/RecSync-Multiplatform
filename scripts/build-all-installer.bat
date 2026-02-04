@echo off
chcp 65001 >nul 2>&1
cls
echo ========================================
echo RecSync Complete Package Tool - Installer
echo ========================================
echo.
echo This script will package:
echo   1. RecSync Leader - EXE Installer
echo   2. RecSync Client - EXE Installer
echo.
echo Package Type: Windows Installer (Requires WiX Toolset)
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

set START_TIME=%time%

echo ========================================
echo Step 1: Clean old builds
echo ========================================
call gradlew.bat clean
if errorlevel 1 (
    echo [ERROR] Clean failed
    pause
    exit /b 1
)

echo.
echo ========================================
echo Step 2: Build all modules
echo ========================================
call gradlew.bat build
if errorlevel 1 (
    echo [ERROR] Build failed
    pause
    exit /b 1
)

echo.
echo ========================================
echo Step 3: Package Leader installer
echo ========================================
call gradlew.bat :desktop-leader:jpackage
if errorlevel 1 (
    echo [ERROR] Leader packaging failed
    pause
    exit /b 1
)
echo [OK] Leader installer packaged successfully!

echo.
echo ========================================
echo Step 4: Package Client installer
echo ========================================
call gradlew.bat :desktop-client:jpackage
if errorlevel 1 (
    echo [ERROR] Client packaging failed
    pause
    exit /b 1
)
echo [OK] Client installer packaged successfully!

echo.
echo ========================================
echo Package Complete!
echo ========================================
echo.
echo Time: from %START_TIME% to %time%
echo.
echo Leader installer: desktop-leader\build\installer\RecSync-Leader-1.0.0.exe
echo Client installer: desktop-client\build\installer\RecSync-Client-1.0.0.exe
echo.
echo ========================================
echo Usage Instructions
echo ========================================
echo.
echo 1. Run RecSync-Leader-1.0.0.exe on control computer
echo 2. Run RecSync-Client-1.0.0.exe on recording computers
echo 3. Follow installation wizard
echo.
pause
