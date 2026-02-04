# RecSync 多平台同步录制系统

## 项目简介

RecSync是一个支持多设备同步录制的系统，可实现：
- 亚毫秒级时钟同步
- 跨平台支持（Windows/macOS/Linux/Android）
- 自动服务发现
- 集中式文件管理
- **一键打包部署**

  ## 系统架构

  ┌──────────────┐
  │Desktop Leader│ ← 控制中心
  └──────┬───────┘
         │
  ┌──────┼──────┬──────────┐
  │      │      │          │
  Desktop Desktop  Android  Android
  Client  Client  Client   Client

  ## 快速开始

  ### 1. 环境要求

  - **JDK**: 17或更高版本
  - **Gradle**: 8.x
  - **操作系统**: Windows 10+, macOS 11+, Ubuntu 20.04+

  ### 2. 构建项目

  ```bash
  # 克隆项目
  git clone https://github.com/your-repo/RecSync-Multiplatform.git
  cd RecSync-Multiplatform

  # 构建所有模块
  ./gradlew build

  # 或分别构建
  ./gradlew :desktop-leader:build
  ./gradlew :desktop-client:build

  3. 运行Leader

  cd desktop-leader
  ./gradlew run

  # 或直接运行jar
  java -jar build/libs/desktop-leader-1.0.0.jar

  Leader启动后将：
  - 自动获取本机IP地址
  - 启动mDNS服务（_recsync-leader._tcp.local）
  - 启动UDP广播（端口8245）
  - 监听RPC连接（端口8244）
  - 监听文件传输（端口8246）

### 4. 运行Client

```bash
cd desktop-client
./gradlew run
```

Client启动后将：
- 自动搜索Leader（mDNS + UDP广播）
- 显示相机预览
- 等待Leader的录制命令

### 5. Android Client部署

```bash
cd android-client
./gradlew assembleRelease

# APK位于: app/build/outputs/apk/release/app-release.apk
```

## 打包为独立应用

详细的打包说明请参考: [PACKAGING.md](PACKAGING.md)

### 一键打包全部（最简单）

**交互式菜单（推荐）：**
```bash
双击运行: build.bat

选项：
1. 绿色免安装版（推荐）- 同时打包Leader和Client
2. Windows安装包（EXE）- 同时打包Leader和Client
3. 只打包Leader（控制端）
4. 只打包Client（录制端）
```

**或直接运行脚本：**
```bash
# 绿色版（推荐）
双击运行: build-all-portable.bat

# 安装包版
双击运行: build-all-installer.bat
```

**输出位置：**
- Leader: `desktop-leader/build/image/` 或 `build/installer/`
- Client: `desktop-client/build/image/` 或 `build/installer/`

---

### 分别打包单个模块

#### Leader（控制端）

**绿色版：**
```bash
cd desktop-leader
双击运行: build-portable.bat
# 输出: build/image/bin/RecSync-Leader.bat
```

**安装包：**
```bash
cd desktop-leader
双击运行: build-installer.bat
# 输出: build/installer/RecSync-Leader-1.0.0.exe
```

#### Client（录制端）

**绿色版：**
```bash
cd desktop-client
双击运行: build-portable.bat
# 输出: build/image/bin/RecSync-Client.bat
```

**安装包：**
```bash
cd desktop-client
双击运行: build-installer.bat
# 输出: build/installer/RecSync-Client-1.0.0.exe
```

---

### 打包特点

- 🎯 自包含JRE，无需用户安装Java
- 📦 包含所有依赖（JavaCV, OpenCV, FFmpeg）
- 🔧 绿色版可直接复制到其他电脑使用
- 🎨 支持自定义图标
- ⚡ 建议使用绿色版，便于快速部署

  使用流程

  典型场景

  1. 启动Leader
    - 在控制电脑上启动Desktop Leader
    - 记下显示的IP地址（如192.168.1.100）
  2. 连接Client
    - 在录制设备上启动Client（Desktop或Android）
    - 应用自动发现Leader并连接
    - 如果自动发现失败，手动输入Leader IP
  3. 开始录制
    - 在Leader端点击"🎬 开始录制"
    - 所有连接的Client同步开始录制
  4. 停止录制
    - 在Leader端点击"⏹️ 停止录制"
    - 所有Client停止录制并保存视频到本地
  5. 上传视频
    - 在Client端点击"📤 上传选中"或"📤 上传全部"
    - 视频自动上传到Leader并删除本地副本
  6. 查看归档
    - 在Leader端点击"📁 打开归档目录"
    - 所有视频按设备分类存储

  网络配置

  端口要求

  | 端口 | 协议 | 用途             |
  |------|------|------------------|
  | 8244 | UDP  | RPC通信          |
  | 9428 | UDP  | SNTP时钟同步     |
  | 8246 | TCP  | 文件传输         |
  | 8245 | UDP  | 服务发现（广播） |

  防火墙配置

  Windows:
  netsh advfirewall firewall add rule name="RecSync RPC" dir=in action=allow protocol=UDP localport=8244
  netsh advfirewall firewall add rule name="RecSync SNTP" dir=in action=allow protocol=UDP localport=9428
  netsh advfirewall firewall add rule name="RecSync Transfer" dir=in action=allow protocol=TCP localport=8246
  netsh advfirewall firewall add rule name="RecSync Discovery" dir=in action=allow protocol=UDP localport=8245

  Linux (ufw):
  sudo ufw allow 8244/udp comment 'RecSync RPC'
  sudo ufw allow 9428/udp comment 'RecSync SNTP'
  sudo ufw allow 8246/tcp comment 'RecSync Transfer'
  sudo ufw allow 8245/udp comment 'RecSync Discovery'

  故障排除

  Client无法发现Leader

  1. 检查是否在同一WiFi网络
  2. 检查防火墙设置
  3. 尝试手动输入Leader IP
  4. 检查路由器是否支持组播（mDNS）

  时钟同步精度低

  1. 确保网络延迟稳定
  2. 减少网络中其他流量
  3. 检查SNTP端口（9428）是否畅通

  文件上传失败

  1. 检查传输端口（8246）是否开放
  2. 确认Leader有足够存储空间
  3. 检查文件权限

### 相机无法启动（Desktop Client）

1. 确认摄像头未被其他应用占用（微信、QQ、Zoom等）
2. 检查摄像头驱动
3. 检查应用是否有摄像头访问权限：
   - Windows 设置 → 隐私 → 相机
   - 允许应用访问相机：开启
   - **允许桌面应用访问相机：开启**（重要）
4. 重新插拔USB摄像头后重启应用

  目录结构

  Leader归档目录：
  ~/RecSync-Archive/
  ├── Device-Pixel-7/
  │   ├── VID_20260107_143022.mp4
  │   └── VID_20260107_143022.csv
  ├── Device-Desktop-Win10/
  │   └── ...
  └── Device-MacBook-Pro/
      └── ...

  Client录制目录：
  ~/RecSync/
  ├── VID_20260107_143022.mp4
  └── VID_20260107_143022.csv

  性能优化

  - 网络带宽: 建议使用5GHz WiFi，避免2.4GHz拥塞
  - 视频码率: 默认8Mbps，可根据需要调整
  - 相机分辨率: 默认1280x720@30fps，可修改为1920x1080

  开发者文档

  - API文档: docs/API.md
  - 架构设计: docs/ARCHITECTURE.md
  - 贡献指南: CONTRIBUTING.md

  许可证

  Apache License 2.0

  致谢

  基于Google Research的CaptureSync项目改造