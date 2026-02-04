# RecSync 打包说明

本文档说明如何将RecSync Leader和Client打包成可执行的exe文件。

## 前提条件

1. **JDK 17或更高版本**
   - jpackage工具从JDK 16开始包含
   - 推荐使用JDK 17或21

2. **WiX Toolset**（仅Windows打包为EXE安装程序时需要）
   - 下载地址: https://wixtoolset.org/releases/
   - 安装后需要将WiX的bin目录添加到系统PATH环境变量中
   - 验证安装: 运行 `candle -?` 应该能看到帮助信息

---

## 快速开始 - 一键打包（推荐）

### 方式1：交互式打包菜单

**最简单！** 双击根目录的 `build.bat`，根据菜单选择：

```
1. 绿色免安装版（推荐）- 同时打包Leader和Client
2. Windows安装包（EXE）- 同时打包Leader和Client
3. 只打包Leader（控制端）- 绿色版
4. 只打包Client（录制端）- 绿色版
```

### 方式2：直接运行打包脚本

**打包全部（绿色版）：**
```bash
双击运行: build-all-portable.bat
```

**打包全部（安装包）：**
```bash
双击运行: build-all-installer.bat
```

---

## 分别打包单个模块

### Leader（控制端）打包

#### 绿色免安装版
```bash
cd desktop-leader
双击运行: build-portable.bat

# 或使用gradle命令
..\gradlew :desktop-leader:jlink
```

**输出位置：** `desktop-leader/build/image/`
**启动方式：** `build/image/bin/RecSync-Leader.bat`

#### Windows安装包
```bash
cd desktop-leader
双击运行: build-installer.bat

# 或使用gradle命令
..\gradlew :desktop-leader:jpackage
```

**输出位置：** `desktop-leader/build/installer/RecSync-Leader-1.0.0.exe`

---

### Client（录制端）打包

#### 绿色免安装版
```bash
cd desktop-client
双击运行: build-portable.bat

# 或使用gradle命令
..\gradlew :desktop-client:jlink
```

**输出位置：** `desktop-client/build/image/`
**启动方式：** `build/image/bin/RecSync-Client.bat`

#### Windows安装包
```bash
cd desktop-client
双击运行: build-installer.bat

# 或使用gradle命令
..\gradlew :desktop-client:jpackage
```

**输出位置：** `desktop-client/build/installer/RecSync-Client-1.0.0.exe`

---

## 打包方式对比

| 特性 | 绿色免安装版 | Windows安装包 |
|------|------------|--------------|
| **安装时间** | 无需安装，解压即用 | 需要运行安装程序 |
| **部署速度** | ⚡ 极快（复制文件夹） | 🐢 较慢（安装过程） |
| **多台电脑** | ✅ 可直接复制 | ❌ 每台都要安装 |
| **卸载** | 直接删除文件夹 | 需要卸载程序 |
| **更新** | 直接替换文件夹 | 需要卸载后重装 |
| **开始菜单** | ❌ 无 | ✅ 自动创建 |
| **桌面快捷方式** | 手动创建 | ✅ 自动创建 |
| **推荐度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

**推荐：** 对于RecSync这类工具软件，建议使用**绿色免安装版**，便于快速部署和更新。

---

## 典型部署场景

### 场景1：会议室多机位录制系统

**需求：**
- 1台控制台电脑（Leader）
- 3-5台录制电脑（Client）
- 需要快速部署和更新

**推荐方案：绿色版**

**部署步骤：**
1. 在开发机上运行 `build-all-portable.bat`
2. 准备一个U盘，创建以下目录结构：
   ```
   U:\RecSync\
   ├── Leader\          (复制 desktop-leader\build\image\)
   └── Client\          (复制 desktop-client\build\image\)
   ```
3. 在控制台电脑：
   - 复制 `U:\RecSync\Leader\` 到 `C:\RecSync-Leader\`
   - 运行 `C:\RecSync-Leader\bin\RecSync-Leader.bat`
   - 创建桌面快捷方式
4. 在各录制电脑：
   - 复制 `U:\RecSync\Client\` 到 `C:\RecSync-Client\`
   - 运行 `C:\RecSync-Client\bin\RecSync-Client.bat`
   - 创建桌面快捷方式

**更新方式：**
- 直接替换整个文件夹即可

---

### 场景2：教学录制系统（长期固定使用）

**需求：**
- 长期固定部署
- 需要规范的软件管理
- 普通用户使用

**推荐方案：Windows安装包**

**部署步骤：**
1. 运行 `build-all-installer.bat` 生成安装包
2. 分发给各电脑用户
3. 双击 `RecSync-Leader-1.0.0.exe` 安装控制端
4. 双击 `RecSync-Client-1.0.0.exe` 安装录制端
5. 安装程序会自动创建：
   - 开始菜单快捷方式
   - 桌面快捷方式
   - 注册表项（用于卸载）

---

## 图标设置

### Leader和Client图标区分建议

为了便于用户区分Leader和Client，建议使用不同颜色的图标：

**Leader图标：**
- 颜色：蓝色或绿色（代表控制端、管理）
- 图案：可以包含"指挥"、"控制"相关的元素

**Client图标：**
- 颜色：橙色或红色（代表录制端、执行）
- 图案：可以包含"录制"、"摄像机"相关的元素

### 添加图标步骤

1. **准备图标文件**
   - Windows: 256x256 ICO格式
   - macOS: 512x512 ICNS格式
   - Linux: 512x512 PNG格式

2. **放置到对应位置**
   - Leader: `desktop-leader/src/main/resources/icon.ico`
   - Client: `desktop-client/src/main/resources/icon.ico`

3. **重新打包**
   - 重新运行打包脚本即可

**在线图标制作工具：**
- https://www.icoconverter.com/
- https://cloudconvert.com/
- https://www.favicon-generator.org/

---

## 常见问题

### Q1: 打包时提示找不到WiX工具
**A:**
- 只有打包Windows安装包（EXE）时才需要WiX
- 如果打包绿色版，不需要WiX
- 安装WiX：https://wixtoolset.org/releases/
- 将WiX的bin目录添加到PATH环境变量

### Q2: 打包后的文件很大（200-400MB）
**A:** 这是正常的，包含：
- 完整JRE：60-80MB
- OpenCV原生库：100MB
- FFmpeg原生库：80MB（仅Client）
- 应用程序：20MB

### Q3: 如何修改版本号
**A:** 编辑对应的 `build.gradle`：
```gradle
'--app-version', '1.0.0'  // 修改这里
```

### Q4: 能否减小打包体积
**A:** jlink已经做了优化：
- `--strip-debug`: 移除调试信息
- `--compress 2`: 压缩级别2
- `--no-header-files`: 移除C头文件
- `--no-man-pages`: 移除手册页

进一步减小需要移除某些功能模块，不推荐。

### Q5: 可以同时安装Leader和Client吗？
**A:**
- 绿色版：可以，复制到不同文件夹
- 安装版：可以，安装到不同目录
- 建议分别部署到不同的电脑

---

## 总结和推荐

### 推荐打包方式

**默认推荐：绿色免安装版**
- ✅ 部署快速
- ✅ 更新方便
- ✅ 适合工具型软件
- ✅ 便于多台电脑部署

**适用场景：**
- 多机位录制系统
- 临时活动录制
- 需要频繁更新
- U盘携带使用

### 一键打包命令

**最简单的方式：**
```bash
# 在项目根目录
双击运行: build.bat

# 选择: 1. 绿色免安装版（推荐）
```

**5分钟后：**
- Leader绿色版: `desktop-leader/build/image/`
- Client绿色版: `desktop-client/build/image/`

**部署：**
- 复制到目标电脑
- 双击 `.bat` 文件运行
- 完成！

---

**更多信息：**
- 快速入门: [QUICKSTART.md](QUICKSTART.md)
- 摄像头问题: [TROUBLESHOOTING_CAMERA.md](TROUBLESHOOTING_CAMERA.md)
- 更新日志: [CHANGELOG.md](CHANGELOG.md)
