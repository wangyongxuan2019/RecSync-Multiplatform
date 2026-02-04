# 网络连接问题故障排除指南

## 问题描述

**症状：** 其他电脑的Client显示"已连接到Leader"，但Leader端未显示已连接的客户端。

**原因：** 这是典型的防火墙阻止UDP通信问题。

---

## 快速修复方案

### 方案1：自动配置（推荐）

1. **在Leader机器上**，右键点击 `配置防火墙.bat`
2. 选择 **"以管理员身份运行"**
3. 按照提示完成配置
4. 重启Leader应用和Client应用

### 方案2：手动配置

#### Leader端（运行Leader应用的电脑）

打开 PowerShell（管理员权限），执行以下命令：

```powershell
# Leader RPC端口 (UDP 8244)
New-NetFirewallRule -DisplayName "RecSync Leader - RPC" -Direction Inbound -Protocol UDP -LocalPort 8244 -Action Allow -Profile Private,Domain

# Leader文件传输端口 (TCP 8246)
New-NetFirewallRule -DisplayName "RecSync Leader - File Transfer" -Direction Inbound -Protocol TCP -LocalPort 8246 -Action Allow -Profile Private,Domain

# Leader服务发现端口 (UDP 8245)
New-NetFirewallRule -DisplayName "RecSync Leader - Discovery" -Direction Inbound -Protocol UDP -LocalPort 8245 -Action Allow -Profile Private,Domain
```

#### Client端（运行Client应用的电脑）

```powershell
# Client RPC端口 (UDP 8247)
New-NetFirewallRule -DisplayName "RecSync Client - RPC" -Direction Inbound -Protocol UDP -LocalPort 8247 -Action Allow -Profile Private,Domain
```

---

## 端口说明

| 端口 | 协议 | 用途 | 需要在哪台机器开放 |
|------|------|------|-------------------|
| 8244 | UDP | Leader端RPC通信（接收客户端心跳） | Leader |
| 8245 | UDP | Leader端服务发现（广播） | Leader |
| 8246 | TCP | Leader端文件传输（接收视频文件） | Leader |
| 8247 | UDP | Client端RPC通信（接收录制命令） | Client |

---

## 完整故障排除步骤

### 步骤1：确认网络连接

```bash
# 在Client机器上，ping Leader的IP地址
ping 192.168.1.100

# 应该能收到回复
```

### 步骤2：检查网络类型

1. 打开 **设置 → 网络和Internet → WiFi**
2. 点击已连接的WiFi网络
3. 确认网络配置文件为 **"专用"**（而不是"公用"）

**为什么？** Windows防火墙规则通常只在"专用网络"下生效。

### 步骤3：配置防火墙

使用上面的 **方案1（自动配置）** 或 **方案2（手动配置）**

### 步骤4：验证防火墙规则

打开 PowerShell，执行：

```powershell
# 查看RecSync相关的防火墙规则
Get-NetFirewallRule | Where-Object {$_.DisplayName -like "*RecSync*"} | Select-Object DisplayName, Enabled, Direction, Action
```

应该看到类似输出：
```
DisplayName                          Enabled Direction Action
-----------                          ------- --------- ------
RecSync Leader - RPC (UDP 8244)      True    Inbound   Allow
RecSync Leader - File Transfer       True    Inbound   Allow
RecSync Leader - Discovery           True    Inbound   Allow
RecSync Client - RPC (UDP 8247)      True    Inbound   Allow
```

### 步骤5：测试UDP连接

#### 在Leader机器上测试UDP端口监听：

```powershell
# 查看UDP 8244端口是否在监听
netstat -an | findstr "8244"

# 应该看到类似输出：
# UDP    0.0.0.0:8244           *:*
```

#### 从Client机器测试UDP连接：

安装 `Test-NetConnection` 工具（Windows 10+自带）：

```powershell
# 测试Leader的UDP 8244端口（注意：UDP测试不如TCP准确）
Test-NetConnection -ComputerName 192.168.1.100 -Port 8244
```

### 步骤6：查看应用日志

#### Leader端日志检查：

启动Leader应用后，查看日志输出：

```
✅ RPC服务已启动 - 绑定地址: 0.0.0.0:8244 (监听所有网络接口)
   请确保防火墙允许UDP端口 8244 的入站连接
RPC监听线程已启动，正在监听端口 8244 ...
```

**关键点：** 应该看到"绑定地址: 0.0.0.0:8244"，说明监听所有网络接口。

#### Client端日志检查：

启动Client应用后，查看日志输出：

```
✅ SoftwareSyncClient已启动: Client-XXX, Leader端口: 8244
发送心跳到Leader: 192.168.1.100:8244 - 客户端: Client-XXX, IP: 192.168.1.101, synced: false
```

**如果Leader收到心跳，会看到：**

```
✅ 新客户端连接: Client-XXX (192.168.1.101) - 当前客户端数: 1/10
```

**如果Leader未收到心跳，检查：**
- 防火墙规则是否正确
- 网络是否为"专用"模式
- IP地址是否正确

---

## 常见问题

### Q1: 为什么本机Client可以连接，但其他机器不行？

**A:** 本机通信使用loopback（127.0.0.1），不经过防火墙。跨机器通信需要防火墙允许。

### Q2: 已经配置了防火墙，但还是无法连接？

**A:** 检查以下几点：
1. WiFi是否设置为"专用网络"（不是"公用网络"）
2. 是否有其他安全软件（360、腾讯管家等）阻止
3. 路由器是否启用了AP隔离（某些公共WiFi会启用）

### Q3: 如何检查是否有AP隔离？

**A:** 在Client机器上ping Leader的IP：
```bash
ping 192.168.1.100
```

- **能ping通**：网络连接正常，问题在防火墙
- **不能ping通**：可能是AP隔离，需要联系网络管理员

### Q4: Leader显示的IP地址不对怎么办？

**A:** 如果Leader机器有多个网卡（如有线+WiFi），可能选择了错误的IP。

**解决方法：**
1. 在Leader机器上运行 `ipconfig`
2. 找到连接到WiFi的网卡的IP地址
3. 确认Client使用这个IP连接

### Q5: 公司网络环境无法使用怎么办？

**A:** 公司网络可能有严格的防火墙策略：
- **联系IT部门**：请求开放所需端口
- **使用独立WiFi**：购买便携式路由器创建独立网络
- **使用有线连接**：通过交换机连接所有设备

---

## 验证连接成功的标志

### Leader端界面：

```
已连接客户端 (3/10台)
┌─────────────────────────────────────────┐
│✅ Client-Front (192.168.1.101) - 2.3ms  │
│✅ Client-Side  (192.168.1.102) - 1.8ms  │
│✅ Client-Back  (192.168.1.103) - 2.1ms  │
└─────────────────────────────────────────┘
```

### Client端界面：

```
状态: 已连接到Leader ✅
```

### Leader端日志：

```
✅ 新客户端连接: Client-Front (192.168.1.101) - 当前客户端数: 1/10
✅ 新客户端连接: Client-Side (192.168.1.102) - 当前客户端数: 2/10
✅ 新客户端连接: Client-Back (192.168.1.103) - 当前客户端数: 3/10
```

---

## 进阶调试

### 启用详细日志

编辑 `logback.xml` 配置文件，将日志级别改为 `TRACE`：

```xml
<logger name="com.recsync" level="TRACE"/>
```

重启应用后，会看到更详细的网络通信日志：

```
收到RPC消息: method=1, payload=Client-XXX,192.168.1.101,false, from=192.168.1.101:45678, size=48字节
```

### 使用Wireshark抓包

1. 下载安装 [Wireshark](https://www.wireshark.org/)
2. 选择WiFi网卡
3. 过滤器输入：`udp.port == 8244`
4. 查看是否有UDP数据包从Client到Leader

---

## 联系支持

如果按照以上步骤仍然无法解决问题，请提供以下信息：

1. Leader和Client的完整日志
2. 网络拓扑（路由器型号、是否有交换机等）
3. `ipconfig /all` 输出（Leader和Client机器）
4. 防火墙规则截图：`Get-NetFirewallRule | Where-Object {$_.DisplayName -like "*RecSync*"}`

---

**文档版本：** V1.0
**更新日期：** 2024-01-15
