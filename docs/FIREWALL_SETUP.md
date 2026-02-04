# Firewall Configuration Guide

## Quick Answer: Run on LEADER Machine

**The firewall script must be run on the LEADER machine** (the computer running the Leader application).

---

## Why Only on Leader?

The Leader acts as a **server** that needs to:
- **Listen** for incoming connections from Clients
- **Receive** heartbeat messages from Clients
- **Accept** file uploads from Clients

**Windows Firewall blocks incoming connections by default**, so we need to allow these ports on the Leader machine.

---

## Step-by-Step Instructions

### On the LEADER Machine

1. Locate the file: `setup-firewall.bat`

2. **Right-click** on `setup-firewall.bat`

3. Select **"Run as administrator"**

4. Wait for the configuration to complete

5. You should see:
   ```
   [SUCCESS] UDP 8244 - Leader RPC Port
   [SUCCESS] TCP 8246 - Leader File Transfer Port
   [SUCCESS] UDP 8245 - Leader Discovery Port
   [SUCCESS] UDP 8247 - Client RPC Port
   ```

### On CLIENT Machines

**Usually NO firewall configuration needed** because:
- Clients initiate **outgoing** connections (not blocked by default)
- Windows Firewall only blocks **incoming** connections

**Exception:** If Clients still cannot connect after configuring the Leader, you can optionally run `setup-firewall.bat` on Client machines as well (but this is rarely needed).

---

## What Ports Are Configured?

| Port | Protocol | Direction | Purpose | Required On |
|------|----------|-----------|---------|-------------|
| 8244 | UDP | Inbound | Leader RPC (receives heartbeats) | **Leader** |
| 8245 | UDP | Inbound | Leader Discovery (receives queries) | **Leader** |
| 8246 | TCP | Inbound | Leader File Transfer (receives files) | **Leader** |
| 8247 | UDP | Inbound | Client RPC (receives commands) | Client (optional) |

---

## Verify Firewall Rules

After running the script, verify the rules were added:

```powershell
Get-NetFirewallRule | Where-Object {$_.DisplayName -like "*RecSync*"} | Select-Object DisplayName, Enabled, Direction, Action
```

Expected output:
```
DisplayName                              Enabled Direction Action
-----------                              ------- --------- ------
RecSync Leader - RPC (UDP 8244)          True    Inbound   Allow
RecSync Leader - File Transfer (TCP 8246) True    Inbound   Allow
RecSync Leader - Discovery (UDP 8245)    True    Inbound   Allow
RecSync Client - RPC (UDP 8247)          True    Inbound   Allow
```

---

## If Script Fails

### Error: "not recognized as internal or external command"

**Cause:** The old script had encoding issues with Chinese characters.

**Solution:** Use the new script `setup-firewall.bat` (English version).

### Error: "Administrator privileges required"

**Cause:** Script was not run with admin privileges.

**Solution:**
1. **Right-click** on `setup-firewall.bat`
2. Select **"Run as administrator"**

### Error: Specific port configuration failed

**Cause:** Port might already be in use or conflicting rule exists.

**Solution:**
1. Remove all RecSync rules manually:
   ```powershell
   Get-NetFirewallRule | Where-Object {$_.DisplayName -like "*RecSync*"} | Remove-NetFirewallRule
   ```

2. Run the script again

---

## Manual Configuration (Alternative)

If the script doesn't work, you can configure manually:

### Open Windows Firewall

1. Press `Win + R`
2. Type: `wf.msc`
3. Press Enter

### Add Inbound Rules

For each port, create a new inbound rule:

**UDP 8244 (Leader RPC):**
1. Click "New Rule..." in the right panel
2. Rule Type: **Port**
3. Protocol: **UDP**, Specific local ports: **8244**
4. Action: **Allow the connection**
5. Profile: Check **Domain** and **Private**
6. Name: **RecSync Leader - RPC (UDP 8244)**

Repeat for:
- UDP 8245 (Leader Discovery)
- TCP 8246 (Leader File Transfer)
- UDP 8247 (Client RPC) - optional

---

## Additional Network Settings

### Ensure WiFi is "Private Network"

1. Open **Settings → Network & Internet → WiFi**
2. Click on the connected WiFi network
3. Under "Network profile", select **Private**

**Important:** Firewall rules only apply to "Private" and "Domain" profiles.

---

## Testing Connection

### On Leader Machine

Check if ports are listening:

```powershell
netstat -an | findstr "8244"
```

Expected output:
```
UDP    0.0.0.0:8244           *:*
```

If you see `127.0.0.1:8244` instead of `0.0.0.0:8244`, the code fix was not applied.

### From Client Machine

Test connectivity:

```bash
# Ping the Leader
ping 192.168.1.100

# Should receive replies
```

---

## Summary

✅ **Run `setup-firewall.bat` on the LEADER machine**
✅ **Run as Administrator**
✅ **WiFi must be set to "Private Network"**
❌ **Usually NOT needed on Client machines**

---

## Need Help?

See `TROUBLESHOOTING_NETWORK.md` for detailed troubleshooting steps.
