@echo off
chcp 65001 >nul 2>&1
cls
echo ========================================
echo RecSync Complete Package Tool - Portable
echo ========================================
echo.
echo This script will package:
echo   1. RecSync Leader (Control)
echo   2. RecSync Client (Recording)
echo.
echo Package Type: Portable Green Version (Recommended)
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
echo Step 3: Package Leader (Control)
echo ========================================
call gradlew.bat :desktop-leader:jlink
if errorlevel 1 (
    echo [ERROR] Leader packaging failed
    pause
    exit /b 1
)
echo [OK] Leader packaged successfully!

echo.
echo ========================================
echo Step 4: Package Client (Recording)
echo ========================================
call gradlew.bat :desktop-client:jlink
if errorlevel 1 (
    echo [ERROR] Client packaging failed
    pause
    exit /b 1
)
echo [OK] Client packaged successfully!

echo.
echo ========================================
echo Package Complete!
echo ========================================
echo.
echo Time: from %START_TIME% to %time%
echo.
echo Leader portable location: desktop-leader\build\image\
echo   Start: desktop-leader\build\image\bin\RecSync-Leader.bat
echo.
echo Client portable location: desktop-client\build\image\
echo   Start: desktop-client\build\image\bin\RecSync-Client.bat
echo.
echo ========================================
echo Deployment Instructions
echo ========================================
echo.
echo 1. Leader deployment (control computer):
echo    - Copy desktop-leader\build\image\ folder
echo    - Run bin\RecSync-Leader.bat
echo.
echo 2. Client deployment (recording computers):
echo    - Copy desktop-client\build\image\ folder
echo    - Run bin\RecSync-Client.bat
echo.
echo 3. No Java installation required!
echo.
pause
