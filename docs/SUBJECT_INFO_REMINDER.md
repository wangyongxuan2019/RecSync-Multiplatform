# 测试者信息提醒功能说明

## 📋 功能概述

为了确保实验数据的完整性和可追溯性，系统在录制前会智能检查是否填写了测试者信息，并在必要时进行友好提醒。

---

## 🎯 设计理念

### 核心原则
1. **数据完整性优先**：鼓励用户填写完整的测试者信息
2. **用户体验友好**：不强制要求，允许用户灵活选择
3. **智能提醒**：首次提醒明确，后续不再打扰
4. **视觉引导**：通过UI提示引导用户填写信息

### 平衡设计
- ✅ **建议填写**但不强制
- ✅ **明确提示**填写的好处
- ✅ **记忆选择**避免重复打扰
- ✅ **视觉提示**在界面上引导

---

## 🔔 提醒机制

### 触发条件
当用户点击"开始录制"按钮时，系统会检查：
- **检查项**：测试者姓名字段是否为空
- **判断逻辑**：姓名为空 且 未选择"不再提示" = 触发提醒

### 提醒对话框

**标题**：测试者信息提醒
**级别**：⚠️ WARNING（警告级别，黄色图标）

**内容**：
```
未填写测试者信息

检测到未填写测试者基本信息（姓名、年龄、体重等）。

建议填写测试者信息，以便：
• 后期数据分析和追溯
• 自动生成完整的实验报告
• 关联测试者的多次录制数据

是否继续录制？

☐ 本次会话不再提示
```

**按钮选项**：
- **[取消，先填写信息]** - 默认按钮（按Enter键触发）
- **[继续录制]** - 次要按钮

---

## 📊 用户交互流程

### 场景1：首次录制且未填写信息

```
用户操作：点击"开始录制"
    ↓
系统检查：姓名字段为空？
    ↓ 是
弹出提醒对话框
    ↓
用户选择1：点击"取消，先填写信息"
    ↓
    • 取消录制
    • 状态栏显示："已取消录制，请先填写测试者信息"
    • 用户返回填写信息

用户选择2：点击"继续录制"
    ↓
    • 如果勾选"不再提示"：skipSubjectInfoWarning = true
    • 正常开始录制
    • 不保存测试者信息（因为未填写）
```

### 场景2：已选择"不再提示"

```
用户操作：点击"开始录制"
    ↓
系统检查：skipSubjectInfoWarning = true
    ↓
跳过提醒，直接开始录制
```

### 场景3：已填写测试者信息

```
用户操作：点击"开始录制"
    ↓
系统检查：姓名字段非空
    ↓
跳过提醒，直接开始录制
同时保存测试者信息到 batch_info.properties
```

---

## 🎨 UI视觉提示

### 1. 测试者信息面板提示

**位置**：测试者信息面板底部
**内容**：💡 建议录制前填写测试者信息（至少填写姓名），以便后续数据分析和追溯。可使用模板快速导入
**样式**：橙色 + 粗体 + 斜体（醒目）

### 2. 录制控制面板提示

**位置**：录制按钮下方
**内容**：
- 💡 快捷键：Enter键 开始/停止录制
- ⚠️ 建议：录制前请先填写测试者信息

**样式**：橙色 + 斜体

---

## ⚙️ 技术实现

### 关键变量

```java
// 用户是否选择跳过警告（会话级别，关闭应用后重置）
private boolean skipSubjectInfoWarning = false;
```

### 核心方法

#### 1. startRecording() - 录制启动检查
```java
private void startRecording() {
    // 检查客户端连接
    if (clientCount.get() == 0) {
        showWarning("没有连接的客户端");
        return;
    }

    // 检查测试者信息
    String name = subjectNameField.getText().trim();
    if (name.isEmpty() && !skipSubjectInfoWarning) {
        // 显示提醒对话框
        showSubjectInfoWarning();
        return;
    }

    // 开始录制
    doStartRecording();
}
```

#### 2. doStartRecording() - 实际录制逻辑
```java
private void doStartRecording() {
    generateBatchId();
    saveSubjectInfoForBatch();  // 如果已填写则保存
    // ... 录制逻辑
}
```

### 对话框实现细节

```java
Alert confirmDialog = new Alert(Alert.AlertType.WARNING);
confirmDialog.setTitle("测试者信息提醒");
confirmDialog.setHeaderText("未填写测试者信息");

// 自定义内容（包含复选框）
CheckBox dontAskAgain = new CheckBox("本次会话不再提示");
VBox dialogContent = new VBox(10);
dialogContent.getChildren().addAll(
    new Label(提醒文本),
    new Separator(),
    dontAskAgain
);
confirmDialog.getDialogPane().setContent(dialogContent);

// 设置按钮
ButtonType continueBtn = new ButtonType("继续录制", ButtonBar.ButtonData.OK_DONE);
ButtonType cancelBtn = new ButtonType("取消，先填写信息", ButtonBar.ButtonData.CANCEL_CLOSE);
confirmDialog.getButtonTypes().setAll(continueBtn, cancelBtn);

// 设置默认按钮为"取消"（引导用户填写）
Button defaultButton = (Button) confirmDialog.getDialogPane().lookupButton(cancelBtn);
defaultButton.setDefaultButton(true);
```

---

## 📈 数据影响

### 有测试者信息的录制

**目录结构**：
```
RecSyncArchive/
└── s01/
    ├── subject_info.properties      # 测试者基本信息
    └── m01/
        └── e1/
            └── r0000/
                ├── front_20260117143025.mp4
                ├── side_20260117143025.mp4
                └── batch_info.properties    # 包含完整的测试者状态
```

**batch_info.properties 内容**：
```properties
batch_id=20260117_143025
subject_id=s01
movement_id=m01
episode_id=e1
retake_id=r0000
name=张三          ← 完整的测试者信息
age=25
gender=男
weight=70.0
height=175.0
bmi=22.86
bmi_category=正常
record_time=2026-01-17 14:30:25
```

### 无测试者信息的录制

**目录结构**：
```
RecSyncArchive/
└── s01/
    └── m01/
        └── e1/
            └── r0000/
                ├── front_20260117143025.mp4
                ├── side_20260117143025.mp4
                └── batch_info.properties    # 只包含基本信息
```

**batch_info.properties 内容**（简化）：
```properties
batch_id=20260117_143025
subject_id=s01
movement_id=m01
episode_id=e1
retake_id=r0000
# 其他测试者字段缺失
```

---

## 🔍 日志记录

### 用户选择"继续录制"
```
INFO  - 用户选择本次会话不再提示测试者信息警告
INFO  - 📹 广播开始录制命令 - 测试者:s01, 动作:m01, 回合:e1, 重测:r0000, 批次ID:20260117_143025
INFO  - 未填写测试者信息，跳过保存
```

### 用户选择"取消"
```
INFO  - 用户取消录制，选择先填写测试者信息
```

### 已填写信息
```
INFO  - 📹 广播开始录制命令 - 测试者:s01, 动作:m01, 回合:e1, 重测:r0000, 批次ID:20260117_143025
INFO  - ✅ 测试者信息已关联到批次: 20260117_143025 - 测试者[张三, 25岁, 男, 70.0kg, 175.0cm, BMI=22.86(正常)] -> RecSyncArchive\s01\m01\e1\r0000\batch_info.properties
```

---

## 💡 使用建议

### 推荐工作流程

1. **启动应用**
2. **填写测试者信息**（姓名、年龄、性别、体重、身高）
3. **点击"保存测试者信息"**
4. **设置实验参数**（测试者ID、动作ID）
5. **开始录制**（此时不会有提醒）

### 快速测试流程

如果只是快速测试系统功能，不需要完整数据：
1. 点击"开始录制"
2. 在弹出的对话框中勾选"本次会话不再提示"
3. 点击"继续录制"
4. 后续录制都不会再提醒

### 数据分析场景

如果需要后期进行数据分析：
1. **务必填写测试者信息**
2. 特别是：姓名、年龄、体重、身高（用于BMI计算）
3. 这些信息将自动关联到每次录制的 batch_info.properties
4. 便于后期统计分析和报告生成

---

## ⚠️ 注意事项

### 1. "不再提示"的作用域
- **仅在当前会话有效**
- 关闭应用重新打开后，会重置为提示状态
- 这是为了避免用户永久关闭提醒后遗忘填写信息

### 2. 姓名是关键检查项
- 系统只检查姓名字段是否为空
- 其他字段（年龄、体重等）可选，但建议填写
- 原因：姓名是最基本的标识信息

### 3. 批次信息的保存逻辑
```java
// saveSubjectInfoForBatch() 方法中
String name = subjectNameField.getText().trim();
if (name.isEmpty()) {
    logger.info("未填写测试者信息，跳过保存");
    return;  // 静默跳过，不报错
}
```
- 未填写信息时，batch_info.properties 不会创建
- 但录制仍会正常进行
- 视频文件正常保存到分层目录

### 4. 数据完整性影响
未填写测试者信息的影响：
- ❌ 无法通过姓名快速识别测试者
- ❌ 缺少BMI等生理指标数据
- ❌ subjects_summary.csv 不会记录该次录制
- ✅ 但视频文件正常保存，目录结构正常

---

## 🎯 设计优势

### ✅ 用户友好
- 不强制要求，尊重用户选择
- 明确说明填写的好处
- 提供"不再提示"选项，避免重复打扰

### ✅ 数据质量
- 通过提醒引导用户填写信息
- 默认按钮为"取消"，鼓励填写
- UI上多处提示，持续引导

### ✅ 灵活性
- 支持快速测试场景（跳过提醒）
- 支持正式实验场景（填写完整信息）
- 会话级别的记忆，平衡提醒频率

### ✅ 可追溯
- 完整的日志记录用户选择
- 清晰的数据状态（有/无测试者信息）
- batch_info.properties 明确标识数据来源

---

## 🔧 未来可选增强

### 可选功能 1：必填字段标记
在UI上用星号(*)标记必填字段：
```
姓名: * [输入框]  ← 红色星号标记
年龄:   [输入框]
```

### 可选功能 2：字段验证
增强验证逻辑：
- 年龄范围检查（0-120）
- 体重范围检查（20-200kg）
- 身高范围检查（50-250cm）

### 可选功能 3：分级提醒
根据填写完整度分级提醒：
- 未填写任何信息：红色警告
- 只填写姓名：黄色提醒（建议补充其他信息）
- 完整填写：绿色通过

### 可选功能 4：历史记录提示
检测到同名测试者的历史记录时：
- 提示："检测到测试者'张三'的历史记录，是否加载？"
- 自动填充上次的信息

---

## 📊 统计数据建议

在未来版本中，可以在设置中添加统计选项：
- 显示有/无测试者信息的录制占比
- 提示用户数据完整性情况
- 生成数据质量报告

---

**实现日期**: 2026-01-17
**版本**: v1.1 - 智能测试者信息提醒系统
**编译状态**: ✅ BUILD SUCCESSFUL
