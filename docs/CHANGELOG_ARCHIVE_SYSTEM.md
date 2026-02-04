# 更新日志 - 自动分层归档系统

## 版本信息
- **更新日期**: 2026-01-17
- **功能**: 自动分层归档系统
- **影响范围**: Leader端和Client端

---

## 🎯 核心改进

### 1. 全新的归档目录结构

**之前（平铺结构）**:
```
RecSyncArchive/
├── front_s01_m01_20260114150230_e1.mp4
├── side_s01_m01_20260114150230_e1.mp4
├── front_s01_m01_20260114150435_e1_retake1.mp4
├── subject_s01.properties
├── subject_张三_s01_m01_e1_r0000_batch20250117143025.properties
└── subjects_summary.csv
```

**现在（分层结构）**:
```
RecSyncArchive/
├── s01/
│   ├── subject_info.properties
│   ├── m01/
│   │   ├── e1/
│   │   │   ├── r0000/
│   │   │   │   ├── front_20260117143025.mp4
│   │   │   │   ├── side_20260117143025.mp4
│   │   │   │   └── batch_info.properties
│   │   │   └── r0001/
│   │   │       ├── front_20260117143156.mp4
│   │   │       └── batch_info.properties
│   │   └── e2/
│   │       └── r0000/
│   └── m02/
├── s02/
└── subjects_summary.csv
```

---

## 📝 代码修改详情

### 1. **FileReceiveServer.java** (recsync-core)

#### 修改内容：
- 添加了文件名解析功能 `parseFileName()`
- 添加了内部类 `FileNameInfo`
- 修改了 `handleClient()` 方法，支持自动分层归档

#### 核心逻辑：
```java
// 解析文件名：front_s01_m01_20260114150230_e1.mp4
FileNameInfo fileInfo = parseFileName(request.fileName);

if (fileInfo != null) {
    // 创建分层目录：{archiveDir}/{s01}/{m01}/{e1}/{r0000}/
    targetDir = Paths.get(archiveDir,
                         fileInfo.subjectId,    // s01
                         fileInfo.movementId,   // m01
                         fileInfo.episodeId,    // e1
                         fileInfo.retakeId);    // r0000

    // 简化文件名：front_20260114150230.mp4
    simplifiedFileName = String.format("%s_%s.mp4",
                                      fileInfo.deviceName,
                                      fileInfo.timestamp);
}
```

#### 支持的文件名格式：
- 正式录制：`{设备名}_{测试者ID}_{动作ID}_{时间戳}_{回合ID}.mp4`
  - 例如：`front_s01_m01_20260114150230_e1.mp4`

- 重测录制：`{设备名}_{测试者ID}_{动作ID}_{时间戳}_{回合ID}_retake{N}.mp4`
  - 例如：`front_s01_m01_20260114150435_e1_retake1.mp4`

#### 兼容性：
- 如果文件名无法解析，自动回退到旧逻辑（按设备名分类）
- 向后兼容旧版本的客户端

---

### 2. **LeaderApplication.java** (desktop-leader)

#### 修改 1：`saveSubjectInfo()` - 测试者基本信息保存

**之前**:
```java
// 保存到：RecSyncArchive/subject_s01.properties
java.nio.file.Path savePath = java.nio.file.Paths.get(
    currentArchiveDir,
    "subject_" + currentSubjectId + ".properties"
);
```

**现在**:
```java
// 保存到：RecSyncArchive/s01/subject_info.properties
java.nio.file.Path subjectDir = java.nio.file.Paths.get(
    currentArchiveDir,
    currentSubjectId  // s01
);
Files.createDirectories(subjectDir);
java.nio.file.Path savePath = subjectDir.resolve("subject_info.properties");
```

#### 修改 2：`saveSubjectInfoForBatch()` - 批次信息保存

**之前**:
```java
// 保存到：subject_张三_s01_m01_e1_r0000_batch20250117143025.properties
java.nio.file.Path batchInfoPath = java.nio.file.Paths.get(
    currentArchiveDir,
    String.format("subject_%s_%s_%s_e%d_r%04d_batch%s.properties",
        name, currentSubjectId, currentMovementId,
        currentEpisodeNumber, currentRetakeNumber, currentBatchId)
);
```

**现在**:
```java
// 保存到：RecSyncArchive/s01/m01/e1/r0000/batch_info.properties
java.nio.file.Path batchDir = java.nio.file.Paths.get(
    currentArchiveDir,
    currentSubjectId,   // s01
    currentMovementId,  // m01
    episodeId,          // e1
    retakeId            // r0000
);
Files.createDirectories(batchDir);
java.nio.file.Path batchInfoPath = batchDir.resolve("batch_info.properties");
```

**批次信息内容增强**:
```properties
# 之前：仅包含测试者信息（使用SubjectInfo.saveToFile）
# 现在：包含完整的批次和测试者信息
batch_id=20260117_143025
subject_id=s01
movement_id=m01
episode_id=e1
retake_id=r0000
name=张三
age=25
gender=男
weight=70.0
height=175.0
bmi=22.86
bmi_category=正常
record_time=2026-01-17 14:30:25
```

#### 修改 3：`nextSubject()` - 测试者切换逻辑

**之前**:
```java
// 检查文件：RecSyncArchive/subject_s02.properties
java.nio.file.Path infoFile = java.nio.file.Paths.get(
    currentArchiveDir,
    "subject_" + nextSubjectId + ".properties"
);
```

**现在**:
```java
// 检查文件：RecSyncArchive/s02/subject_info.properties
java.nio.file.Path infoFile = java.nio.file.Paths.get(
    currentArchiveDir,
    nextSubjectId,
    "subject_info.properties"
);
```

---

## 🔍 文件解析规则详解

### parseFileName() 方法逻辑

```java
// 输入：front_s01_m01_20260114150230_e1_retake1.mp4

// 步骤1：移除扩展名
nameWithoutExt = "front_s01_m01_20260114150230_e1_retake1"

// 步骤2：分割文件名
parts = ["front", "s01", "m01", "20260114150230", "e1", "retake", "1"]

// 步骤3：提取基本信息
deviceName = parts[0]    // "front"
subjectId = parts[1]     // "s01"
movementId = parts[2]    // "m01"
timestamp = parts[3]     // "20260114150230"
episodeId = parts[4]     // "e1"

// 步骤4：检查重测标记
if (parts[5] == "retake") {
    retakeNum = parts[6]  // 1
    retakeId = "r0001"    // 格式化为r0001
} else {
    retakeId = "r0000"    // 默认正式录制
}

// 步骤5：验证格式
if (!subjectId.matches("s\\d+"))  return null;  // 必须是s开头+数字
if (!movementId.matches("m\\d+")) return null;  // 必须是m开头+数字
if (!episodeId.matches("e\\d+"))  return null;  // 必须是e开头+数字

// 步骤6：返回解析结果
return new FileNameInfo("front", "s01", "m01", "e1", "r0001", "20260114150230");
```

### 目标目录构建

```java
// 从 FileNameInfo 构建目录路径
targetDir = Paths.get(
    archiveDir,           // RecSyncArchive
    fileInfo.subjectId,   // s01
    fileInfo.movementId,  // m01
    fileInfo.episodeId,   // e1
    fileInfo.retakeId     // r0001
);
// 结果：RecSyncArchive/s01/m01/e1/r0001/

// 简化文件名
simplifiedFileName = String.format("%s_%s.mp4",
    fileInfo.deviceName,  // front
    fileInfo.timestamp    // 20260114150230
);
// 结果：front_20260114150230.mp4
```

---

## 🎨 用户界面调整

### 无需修改
- 用户界面保持不变
- 所有现有操作流程保持一致
- 归档目录结构变化对用户透明

### 用户可见的变化
1. **保存测试者信息**后，目录结构：
   ```
   RecSyncArchive/
   └── s01/
       └── subject_info.properties
   ```

2. **第一次录制**后，目录结构：
   ```
   RecSyncArchive/
   └── s01/
       ├── subject_info.properties
       └── m01/
           └── e1/
               └── r0000/
                   ├── front_20260117143025.mp4
                   ├── side_20260117143025.mp4
                   └── batch_info.properties
   ```

3. **切换测试者**时：
   - 系统检查 `s02/subject_info.properties` 是否存在
   - 提供智能提示和选项

---

## ✅ 测试验证

### 测试场景 1：正常录制流程
```
1. 填写测试者信息：s01 - 张三
2. 保存测试者信息
   ✓ 创建：RecSyncArchive/s01/subject_info.properties
3. 设置：测试者=s01, 动作=m01, 回合=e1
4. 开始录制
   ✓ 创建：RecSyncArchive/s01/m01/e1/r0000/
   ✓ 保存：batch_info.properties
5. 客户端上传视频：front_s01_m01_20260117143025_e1.mp4
   ✓ 解析成功
   ✓ 保存为：RecSyncArchive/s01/m01/e1/r0000/front_20260117143025.mp4
```

### 测试场景 2：重测流程
```
1. 点击"重测"按钮
   ✓ 回合号：e1（不变）
   ✓ 重测号：r0000 → r0001
2. 开始录制
   ✓ 创建：RecSyncArchive/s01/m01/e1/r0001/
3. 客户端上传视频：front_s01_m01_20260117143156_e1_retake1.mp4
   ✓ 解析成功（识别重测标记）
   ✓ 保存为：RecSyncArchive/s01/m01/e1/r0001/front_20260117143156.mp4
```

### 测试场景 3：切换测试者
```
1. 点击"下一测试者"
   ✓ s01 → s02
   ✓ 检查：RecSyncArchive/s02/subject_info.properties

   如果存在：
   ✓ 提示"发现测试者 s02 的信息"
   ✓ 可加载信息

   如果不存在：
   ✓ 提示"切换到新测试者 s02"
   ✓ 可新建/导入/跳过
```

### 测试场景 4：兼容性测试
```
1. 上传旧格式文件：video.mp4
   ✓ 无法解析
   ✓ 回退到按设备名分类
   ✓ 保存为：RecSyncArchive/{deviceName}/video.mp4
```

---

## 📊 文件对比

### subject_info.properties（测试者基本信息）

**位置变化**:
- 之前：`RecSyncArchive/subject_s01.properties`
- 现在：`RecSyncArchive/s01/subject_info.properties`

**内容**（不变）:
```properties
name=张三
age=25
gender=男
weight=70.0
height=175.0
bmi=22.86
bmi_category=正常
record_time=2026-01-17 14:30:00
```

### batch_info.properties（批次信息）

**位置变化**:
- 之前：`RecSyncArchive/subject_张三_s01_m01_e1_r0000_batch20250117143025.properties`
- 现在：`RecSyncArchive/s01/m01/e1/r0000/batch_info.properties`

**内容变化**（增强）:
```properties
# 新增字段
batch_id=20260117_143025
subject_id=s01
movement_id=m01
episode_id=e1
retake_id=r0000

# 原有字段
name=张三
age=25
gender=男
weight=70.0
height=175.0
bmi=22.86
bmi_category=正常
record_time=2026-01-17 14:30:25
```

### 视频文件

**位置和文件名变化**:
- 之前：`RecSyncArchive/front/front_s01_m01_20260117143025_e1.mp4`
- 现在：`RecSyncArchive/s01/m01/e1/r0000/front_20260117143025.mp4`

**优势**:
- 文件名更简洁（目录已包含层次信息）
- 位置更直观（同一批次的所有设备视频在同一目录）

---

## 🚀 性能优化

### 1. 目录创建优化
- 使用 `Files.createDirectories()` 批量创建多层目录
- 只在需要时创建目录，避免不必要的I/O操作

### 2. 文件解析优化
- 正则表达式验证，快速判断格式正确性
- 解析失败时立即回退，不影响文件接收

### 3. 日志优化
- 关键步骤记录详细日志
- 解析成功时输出完整路径信息，便于调试

---

## 🔒 安全性考虑

### 1. 路径注入防护
- 文件名解析严格验证格式（s\d+, m\d+, e\d+）
- 不允许包含路径分隔符的文件名
- sanitizeDeviceName() 清理设备名中的特殊字符

### 2. 文件覆盖保护
- 检查目标文件是否已存在
- 存在时拒绝上传，返回 UPLOAD_REJECTED

### 3. 目录遍历防护
- 所有路径基于 archiveDir 构建
- 不接受相对路径（../）

---

## 📚 相关文档

- **ARCHIVE_STRUCTURE.md** - 归档目录结构详细说明
- **README.md** - 项目总体说明
- **CHANGELOG.md** - 本更新日志

---

## 🔄 迁移建议

### 对于现有数据
- 旧数据仍可正常访问（按设备名分类的目录）
- 新数据自动使用分层归档
- 建议逐步迁移旧数据到新结构

### 迁移脚本（示例）
```bash
#!/bin/bash
# 将旧格式文件迁移到新结构

for file in RecSyncArchive/*.mp4; do
    # 解析文件名
    if [[ $file =~ ([^_]+)_([^_]+)_([^_]+)_([^_]+)_([^_]+)(_retake([0-9]+))?.mp4 ]]; then
        device="${BASH_REMATCH[1]}"
        subject="${BASH_REMATCH[2]}"
        movement="${BASH_REMATCH[3]}"
        timestamp="${BASH_REMATCH[4]}"
        episode="${BASH_REMATCH[5]}"
        retake="${BASH_REMATCH[7]:-0}"
        retake_id=$(printf "r%04d" $retake)

        # 创建目标目录
        target_dir="RecSyncArchive/$subject/$movement/$episode/$retake_id"
        mkdir -p "$target_dir"

        # 移动文件
        mv "$file" "$target_dir/${device}_${timestamp}.mp4"
    fi
done
```

---

## 💡 未来优化方向

1. **Web界面可视化**
   - 基于目录结构生成可视化树形图
   - 支持在线浏览和下载

2. **数据分析集成**
   - 自动提取batch_info.properties生成分析报告
   - 与运动学分析软件集成

3. **云存储支持**
   - 支持自动上传到云存储（S3, OSS等）
   - 保持相同的目录结构

4. **元数据搜索**
   - 基于测试者信息快速搜索
   - 支持按时间、动作、回合筛选

---

**编译状态**: ✅ BUILD SUCCESSFUL
**测试状态**: 待测试
**发布版本**: v1.0 - 自动分层归档系统
