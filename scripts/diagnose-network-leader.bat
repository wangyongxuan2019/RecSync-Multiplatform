@echo off
REM Network Connection Diagnostic Tool for RecSync
REM Run this script on the LEADER machine

setlocal enabledelayedexpansion

echo ========================================
echo RecSync Network Diagnostic Tool
echo ========================================
echo.
echo This script will help diagnose network connection issues
echo.

REM Get Leader IP address
echo [Step 1] Detecting Leader IP address...
echo.

for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /C:"IPv4"') do (
    set "ip=%%a"
    set "ip=!ip:~1!"
    echo Found IP: !ip!
)

echo.
echo Please note your Leader IP address from above.
echo.
pause

REM Check firewall rules
echo.
echo [Step 2] Checking firewall rules...
echo.

netsh advfirewall firewall show rule name="RecSync Leader - RPC (UDP 8244)" >nul 2>&1
if %errorLevel% equ 0 (
    echo [OK] UDP 8244 firewall rule exists
) else (
    echo [FAIL] UDP 8244 firewall rule NOT found
    echo        Please run setup-firewall.bat first!
)

netsh advfirewall firewall show rule name="RecSync Leader - File Transfer (TCP 8246)" >nul 2>&1
if %errorLevel% equ 0 (
    echo [OK] TCP 8246 firewall rule exists
) else (
    echo [FAIL] TCP 8246 firewall rule NOT found
)

netsh advfirewall firewall show rule name="RecSync Leader - Discovery (UDP 8245)" >nul 2>&1
if %errorLevel% equ 0 (
    echo [OK] UDP 8245 firewall rule exists
) else (
    echo [FAIL] UDP 8245 firewall rule NOT found
)

echo.
pause

REM Check if ports are listening
echo.
echo [Step 3] Checking if Leader application is listening on ports...
echo.
echo Checking UDP 8244 (Leader RPC)...
netstat -an | findstr "8244" | findstr "UDP"
if %errorLevel% equ 0 (
    echo [OK] Port 8244 is listening
) else (
    echo [FAIL] Port 8244 is NOT listening
    echo        Is the Leader application running?
)

echo.
echo Checking UDP 8245 (Leader Discovery)...
netstat -an | findstr "8245" | findstr "UDP"
if %errorLevel% equ 0 (
    echo [OK] Port 8245 is listening
) else (
    echo [FAIL] Port 8245 is NOT listening
)

echo.
echo Checking TCP 8246 (File Transfer)...
netstat -an | findstr "8246" | findstr "LISTENING"
if %errorLevel% equ 0 (
    echo [OK] Port 8246 is listening
) else (
    echo [FAIL] Port 8246 is NOT listening
)

echo.
pause

REM Check network profile
echo.
echo [Step 4] Checking network profile...
echo.
powershell -Command "Get-NetConnectionProfile | Select-Object Name, NetworkCategory"
echo.
echo IMPORTANT: Network must be "Private" (not "Public")
echo If it shows "Public", change it in Settings -^> Network -^> WiFi
echo.
pause

REM UDP Test Listener
echo.
echo [Step 5] Starting UDP test listener on port 8244...
echo.
echo This will listen for UDP packets from Client machines.
echo.
echo Instructions:
echo 1. Leave this window open
echo 2. On the CLIENT machine, run: test-client-connection.bat
echo 3. You should see messages appear below when Client sends packets
echo.
echo Press Ctrl+C to stop listening
echo.
pause

REM Create a simple UDP listener using PowerShell
powershell -Command ^
    "$endpoint = New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Any, 8244);" ^
    "$udpClient = New-Object System.Net.Sockets.UdpClient 8244;" ^
    "Write-Host '[LISTENING] Waiting for UDP packets on port 8244...';" ^
    "Write-Host '';" ^
    "while($true) {" ^
    "    try {" ^
    "        $receivedData = $udpClient.Receive([ref]$endpoint);" ^
    "        $message = [System.Text.Encoding]::ASCII.GetString($receivedData);" ^
    "        $timestamp = Get-Date -Format 'HH:mm:ss';" ^
    "        Write-Host \"[$timestamp] Received from $($endpoint.Address):$($endpoint.Port)\";" ^
    "        Write-Host \"    Length: $($receivedData.Length) bytes\";" ^
    "        Write-Host \"    Data: $message\";" ^
    "        Write-Host '';" ^
    "    } catch {" ^
    "        if($_.Exception.Message -notmatch 'interrupted') {" ^
    "            Write-Host \"[ERROR] $_\";" ^
    "        }" ^
    "        break;" ^
    "    }" ^
    "};" ^
    "$udpClient.Close();"

echo.
echo Listener stopped.
echo.
pause
