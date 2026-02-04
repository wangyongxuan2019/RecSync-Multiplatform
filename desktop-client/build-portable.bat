@echo off
chcp 65001 >nul 2>&1
cls
echo ========================================
echo RecSync Client - Portable Package Tool
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
echo [3/3] Creating portable version...
call ..\gradlew.bat jlink
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
echo Portable location: build\image\
echo Start program: build\image\bin\RecSync-Client.bat
echo.
echo You can copy the entire build\image folder to other computers
echo No Java installation required!
echo.
pause
