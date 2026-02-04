@echo off
chcp 65001 >nul 2>&1
cls
echo ========================================
echo RecSync Quick Package Tool
echo ========================================
echo.
echo Please select package type:
echo.
echo 1. Portable Version (Recommended)
echo    - No installation required
echo    - Portable for USB drive, quick deployment
echo    - Fast packaging
echo.
echo 2. Windows Installer (EXE)
echo    - Professional installer
echo    - Auto-create start menu shortcuts
echo    - Requires WiX Toolset
echo.
echo 3. Package Leader only (Control) - Portable
echo 4. Package Client only (Recording) - Portable
echo.
echo 0. Exit
echo.
echo ========================================
set /p choice="Enter option (1-4 or 0): "

if "%choice%"=="1" (
    echo.
    echo Starting portable version packaging...
    call build-all-portable.bat
    goto end
)

if "%choice%"=="2" (
    echo.
    echo Starting installer version packaging...
    call build-all-installer.bat
    goto end
)

if "%choice%"=="3" (
    echo.
    echo Starting Leader portable packaging...
    call desktop-leader\build-portable.bat
    goto end
)

if "%choice%"=="4" (
    echo.
    echo Starting Client portable packaging...
    call desktop-client\build-portable.bat
    goto end
)

if "%choice%"=="0" (
    echo Cancelled
    goto end
)

echo Invalid option, please re-run
pause

:end
