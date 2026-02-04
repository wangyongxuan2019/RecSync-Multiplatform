@echo off
REM RecSync Firewall Configuration Script
REM Run this script on the LEADER machine with Administrator privileges

echo ========================================
echo RecSync Firewall Configuration
echo ========================================
echo.
echo This script will configure Windows Firewall for RecSync
echo Administrator privileges required
echo.
pause

REM Check for Administrator privileges
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [ERROR] Administrator privileges required!
    echo Please right-click this file and select "Run as administrator"
    pause
    exit /b 1
)

echo [Step 1] Removing old firewall rules (if any)...
netsh advfirewall firewall delete rule name="RecSync Leader - RPC (UDP 8244)" >nul 2>&1
netsh advfirewall firewall delete rule name="RecSync Leader - File Transfer (TCP 8246)" >nul 2>&1
netsh advfirewall firewall delete rule name="RecSync Leader - Discovery (UDP 8245)" >nul 2>&1
netsh advfirewall firewall delete rule name="RecSync Client - RPC (UDP 8247)" >nul 2>&1
netsh advfirewall firewall delete rule name="RecSync - UDP All Ports" >nul 2>&1
echo Done

echo.
echo [Step 2] Adding Leader firewall rules...

REM Leader RPC port (UDP 8244)
netsh advfirewall firewall add rule ^
    name="RecSync Leader - RPC (UDP 8244)" ^
    dir=in ^
    action=allow ^
    protocol=UDP ^
    localport=8244 ^
    profile=private,domain ^
    description="RecSync Multi-Device Sync System - Leader RPC Port"

if %errorLevel% equ 0 (
    echo [SUCCESS] UDP 8244 - Leader RPC Port
) else (
    echo [FAILED] UDP 8244 configuration failed
)

REM Leader File Transfer port (TCP 8246)
netsh advfirewall firewall add rule ^
    name="RecSync Leader - File Transfer (TCP 8246)" ^
    dir=in ^
    action=allow ^
    protocol=TCP ^
    localport=8246 ^
    profile=private,domain ^
    description="RecSync Multi-Device Sync System - Leader File Transfer Port"

if %errorLevel% equ 0 (
    echo [SUCCESS] TCP 8246 - Leader File Transfer Port
) else (
    echo [FAILED] TCP 8246 configuration failed
)

REM Leader Discovery port (UDP 8245)
netsh advfirewall firewall add rule ^
    name="RecSync Leader - Discovery (UDP 8245)" ^
    dir=in ^
    action=allow ^
    protocol=UDP ^
    localport=8245 ^
    profile=private,domain ^
    description="RecSync Multi-Device Sync System - Leader Discovery Broadcast Port"

if %errorLevel% equ 0 (
    echo [SUCCESS] UDP 8245 - Leader Discovery Port
) else (
    echo [FAILED] UDP 8245 configuration failed
)

echo.
echo [Step 3] Adding Client firewall rules...

REM Client RPC port (UDP 8247)
netsh advfirewall firewall add rule ^
    name="RecSync Client - RPC (UDP 8247)" ^
    dir=in ^
    action=allow ^
    protocol=UDP ^
    localport=8247 ^
    profile=private,domain ^
    description="RecSync Multi-Device Sync System - Client RPC Port"

if %errorLevel% equ 0 (
    echo [SUCCESS] UDP 8247 - Client RPC Port
) else (
    echo [FAILED] UDP 8247 configuration failed
)

echo.
echo ========================================
echo Configuration Complete!
echo ========================================
echo.
echo Firewall rules added:
echo   - UDP 8244: Leader RPC Communication
echo   - UDP 8245: Leader Service Discovery
echo   - TCP 8246: Leader File Transfer
echo   - UDP 8247: Client RPC Communication
echo.
echo IMPORTANT NOTES:
echo 1. These rules only apply to "Private" and "Domain" network profiles
echo 2. If your WiFi is set to "Public", change it to "Private" in network settings
echo 3. All devices must be on the same local network
echo.
echo If connection still fails, check:
echo 1. All devices connected to the same WiFi
echo 2. WiFi is set to "Private Network" mode
echo 3. No other security software (antivirus) is blocking network
echo.
pause
