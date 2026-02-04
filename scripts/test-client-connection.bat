@echo off
REM Client Connection Test Tool
REM Run this script on the CLIENT machine

setlocal enabledelayedexpansion

echo ========================================
echo RecSync Client Connection Test
echo ========================================
echo.

REM Get Leader IP from user
set /p LEADER_IP="Enter the Leader IP address (e.g., 192.168.1.100): "

if "%LEADER_IP%"=="" (
    echo [ERROR] Leader IP is required!
    pause
    exit /b 1
)

echo.
echo Testing connection to Leader: %LEADER_IP%
echo.

REM Test 1: Ping
echo [Test 1] Pinging Leader...
ping -n 2 %LEADER_IP% >nul 2>&1
if %errorLevel% equ 0 (
    echo [OK] Can ping Leader
) else (
    echo [FAIL] Cannot ping Leader
    echo        This indicates network isolation (AP isolation or different subnets)
    echo        Check:
    echo        1. Both machines are on the same WiFi
    echo        2. Router does not have AP isolation enabled
)

echo.
pause

REM Test 2: Send UDP test packet
echo [Test 2] Sending UDP test packets to %LEADER_IP%:8244...
echo.
echo Make sure the Leader is running "diagnose-network-leader.bat"
echo.

powershell -Command ^
    "$udpClient = New-Object System.Net.Sockets.UdpClient;" ^
    "$leaderIP = '%LEADER_IP%';" ^
    "$port = 8244;" ^
    "for($i=1; $i -le 5; $i++) {" ^
    "    $message = \"TEST_PACKET_$i from $env:COMPUTERNAME at $(Get-Date -Format 'HH:mm:ss')\";" ^
    "    $bytes = [System.Text.Encoding]::ASCII.GetBytes($message);" ^
    "    try {" ^
    "        $sent = $udpClient.Send($bytes, $bytes.Length, $leaderIP, $port);" ^
    "        Write-Host \"[SENT] Packet $i sent: $sent bytes\";" ^
    "        Start-Sleep -Milliseconds 500;" ^
    "    } catch {" ^
    "        Write-Host \"[ERROR] Failed to send packet $i : $_\";" ^
    "    }" ^
    "};" ^
    "$udpClient.Close();" ^
    "Write-Host '';" ^
    "Write-Host 'Test packets sent. Check Leader window for received messages.';"

echo.
echo.
echo Test complete!
echo.
echo If Leader received the packets, the network is working.
echo If Leader did NOT receive anything:
echo   1. Check firewall on Leader (run setup-firewall.bat)
echo   2. Check WiFi is set to "Private" (not "Public")
echo   3. Check router AP isolation settings
echo.
pause
