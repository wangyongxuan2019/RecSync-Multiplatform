# RecSync 快速参考 - 分层归档系统

## 📁 目录结构速查

```
RecSyncArchive/
│
├── {测试者ID}/                          # 例如：s01, s02, s03
│   │
│   ├── subject_info.properties          # 测试者基本信息
│   │
│   └── {动作ID}/                        # 例如：m01, m02, m03
│       │
│       └── {回合ID}/                    # 例如：e1, e2, e3
│           │
│           └── {重测ID}/                # r0000=正式, r0001=第1次重测
│               │
│               ├── {设备名}_{时间戳}.mp4   # 视频文件
│               └── batch_info.properties   # 批次信息
│
├── subject_info_template.properties     # 全局模板
└── subjects_summary.csv                 # 汇总CSV
```

---

## 🎯 ID格式规范

| 类型 | 格式 | 示例 | 说明 |
|------|------|------|------|
| 测试者ID | `s` + 数字(2位) | `s01`, `s02`, `s15` | Subject |
| 动作ID | `m` + 数字(2位) | `m01`, `m02`, `m10` | Movement |
| 回合ID | `e` + 数字(任意) | `e1`, `e2`, `e15` | Episode |
| 重测ID | `r` + 数字(4位) | `r0000`, `r0001` | Retake |

---

## 📹 文件命名规范

### 客户端生成（上传前）

**正式录制**:
```
{设备名}_{测试者ID}_{动作ID}_{时间戳}_{回合ID}.mp4

示例：
front_s01_m01_20260117143025_e1.mp4
side_s01_m01_20260117143025_e1.mp4
top_s01_m01_20260117143025_e1.mp4
```

**重测录制**:
```
{设备名}_{测试者ID}_{动作ID}_{时间戳}_{回合ID}_retake{N}.mp4

示例：
front_s01_m01_20260117143156_e1_retake1.mp4  ← 第1次重测
front_s01_m01_20260117143320_e1_retake2.mp4  ← 第2次重测
```

### Leader归档后（最终保存）

**简化为**:
```
{设备名}_{时间戳}.mp4

示例：
RecSyncArchive/s01/m01/e1/r0000/front_20260117143025.mp4
RecSyncArchive/s01/m01/e1/r0001/front_20260117143156.mp4
```

---

## 📄 配置文件内容

### subject_info.properties（测试者基本信息）
```properties
name=张三                    # 姓名
age=25                      # 年龄（岁）
gender=男                   # 性别（男/女/其他）
weight=70.0                 # 体重（kg）
height=175.0                # 身高（cm）
bmi=22.86                   # BMI（自动计算）
bmi_category=正常           # BMI分类
record_time=2026-01-17 14:30:00  # 记录时间
```

### batch_info.properties（批次信息）
```properties
batch_id=20260117_143025    # 批次ID（时间戳）
subject_id=s01              # 测试者ID
movement_id=m01             # 动作ID
episode_id=e1               # 回合ID
retake_id=r0000             # 重测ID
name=张三                   # 测试者姓名
age=25                      # 年龄
gender=男                   # 性别
weight=70.0                 # 体重
height=175.0                # 身高
bmi=22.86                   # BMI
bmi_category=正常           # BMI分类
record_time=2026-01-17 14:30:25  # 录制时间
```

### subjects_summary.csv（汇总CSV）
```csv
姓名,年龄,性别,体重(kg),身高(cm),BMI,BMI分类,记录时间,批次ID,测试者ID,动作ID,回合号,重测号
张三,25,男,70.00,175.00,22.86,正常,2026-01-17 14:30:00,20260117_143025,s01,m01,e1,r0000
张三,25,男,70.00,175.00,22.86,正常,2026-01-17 14:31:56,20260117_143156,s01,m01,e1,r0001
```

---

## 🔄 常见操作路径示例

### 示例1：测试者 s01 的动作 m01 回合 e1 正式录制

**目录路径**:
```
RecSyncArchive/s01/m01/e1/r0000/
```

**包含文件**:
```
front_20260117143025.mp4     ← 前置摄像头
side_20260117143025.mp4      ← 侧面摄像头
top_20260117143025.mp4       ← 顶部摄像头
batch_info.properties        ← 批次信息
```

### 示例2：测试者 s01 的动作 m01 回合 e1 第1次重测

**目录路径**:
```
RecSyncArchive/s01/m01/e1/r0001/
```

**包含文件**:
```
front_20260117143156.mp4
side_20260117143156.mp4
batch_info.properties
```

### 示例3：测试者 s02 的动作 m02 回合 e3 正式录制

**目录路径**:
```
RecSyncArchive/s02/m02/e3/r0000/
```

---

## 🎬 典型录制流程

### 场景：测试者 s01 完成3个动作，每个动作5个回合

```
RecSyncArchive/
└── s01/
    ├── subject_info.properties
    ├── m01/
    │   ├── e1/r0000/  ← 动作1, 回合1
    │   ├── e2/r0000/  ← 动作1, 回合2
    │   ├── e3/r0000/  ← 动作1, 回合3
    │   ├── e4/r0000/  ← 动作1, 回合4
    │   └── e5/r0000/  ← 动作1, 回合5
    ├── m02/
    │   ├── e1/r0000/  ← 动作2, 回合1
    │   ├── e2/r0000/
    │   ├── e3/r0000/
    │   ├── e4/r0000/
    │   └── e5/r0000/
    └── m03/
        ├── e1/r0000/  ← 动作3, 回合1
        ├── e2/r0000/
        ├── e3/r0000/
        ├── e4/r0000/
        └── e5/r0000/
```

### 场景：回合2需要重测2次

```
RecSyncArchive/s01/m01/e2/
├── r0000/  ← 正式录制
├── r0001/  ← 第1次重测
└── r0002/  ← 第2次重测
```

---

## 🔍 快速查找技巧

### 查找某个测试者的所有数据
```bash
cd RecSyncArchive/s01
tree
```

### 查找某个动作的所有回合
```bash
cd RecSyncArchive/s01/m01
ls -R
```

### 查找某个回合的所有重测
```bash
cd RecSyncArchive/s01/m01/e1
ls
# 输出：r0000  r0001  r0002
```

### 统计某个测试者的视频文件数
```bash
find RecSyncArchive/s01 -name "*.mp4" | wc -l
```

### 查找特定设备的所有视频
```bash
find RecSyncArchive/s01 -name "front_*.mp4"
```

---

## ⚠️ 注意事项

### ✅ 正确的文件名格式
```
front_s01_m01_20260117143025_e1.mp4          ← ✅ 正确
side_s02_m03_20260117150030_e5_retake2.mp4   ← ✅ 正确（重测）
```

### ❌ 错误的文件名格式
```
video.mp4                                     ← ❌ 缺少所有信息
front_subject1_m01_20260117143025_e1.mp4     ← ❌ 测试者ID格式错误（应为s01）
front_s01_move1_20260117143025_e1.mp4        ← ❌ 动作ID格式错误（应为m01）
front_s01_m01_143025_e1.mp4                  ← ❌ 时间戳格式错误
front_s01_m01_20260117143025_episode1.mp4   ← ❌ 回合ID格式错误（应为e1）
```

---

## 🛠️ 批量操作示例

### 导出特定测试者的CSV数据
```bash
grep "s01" RecSyncArchive/subjects_summary.csv > s01_data.csv
```

### 批量转换某个动作的视频
```bash
cd RecSyncArchive/s01/m01
for dir in */*/; do
    for video in "$dir"*.mp4; do
        # 转换命令
        ffmpeg -i "$video" -c:v libx264 "${video%.mp4}_converted.mp4"
    done
done
```

### 备份某个测试者的所有数据
```bash
tar -czf s01_backup_$(date +%Y%m%d).tar.gz RecSyncArchive/s01/
```

### 统计各测试者的录制次数
```bash
for subject in RecSyncArchive/s*/; do
    count=$(find "$subject" -name "batch_info.properties" | wc -l)
    echo "$(basename $subject): $count 次录制"
done
```

---

## 📊 数据分析示例

### Python脚本：读取批次信息
```python
import os
from pathlib import Path
import configparser

def read_batch_info(archive_dir, subject_id, movement_id, episode_id, retake_id):
    """读取批次信息"""
    batch_file = Path(archive_dir) / subject_id / movement_id / episode_id / retake_id / "batch_info.properties"

    if batch_file.exists():
        config = configparser.ConfigParser()
        config.read(batch_file, encoding='utf-8')

        return {
            'batch_id': config['DEFAULT']['batch_id'],
            'name': config['DEFAULT']['name'],
            'age': config['DEFAULT']['age'],
            'bmi': config['DEFAULT']['bmi'],
            # ... 其他字段
        }
    return None

# 示例使用
info = read_batch_info('RecSyncArchive', 's01', 'm01', 'e1', 'r0000')
print(f"测试者: {info['name']}, BMI: {info['bmi']}")
```

### Python脚本：遍历所有录制
```python
from pathlib import Path

def scan_archive(archive_dir):
    """扫描归档目录，返回所有录制信息"""
    archive_path = Path(archive_dir)
    recordings = []

    for subject_dir in archive_path.glob('s*'):
        subject_id = subject_dir.name

        for movement_dir in subject_dir.glob('m*'):
            movement_id = movement_dir.name

            for episode_dir in movement_dir.glob('e*'):
                episode_id = episode_dir.name

                for retake_dir in episode_dir.glob('r*'):
                    retake_id = retake_dir.name

                    # 检查是否有视频文件
                    videos = list(retake_dir.glob('*.mp4'))
                    if videos:
                        recordings.append({
                            'subject': subject_id,
                            'movement': movement_id,
                            'episode': episode_id,
                            'retake': retake_id,
                            'video_count': len(videos),
                            'path': str(retake_dir)
                        })

    return recordings

# 示例使用
recordings = scan_archive('RecSyncArchive')
print(f"共找到 {len(recordings)} 次录制")
for rec in recordings[:5]:  # 显示前5个
    print(f"{rec['subject']}/{rec['movement']}/{rec['episode']}/{rec['retake']} - {rec['video_count']} 个视频")
```

---

## 🔗 相关文档

- **ARCHIVE_STRUCTURE.md** - 归档结构详细说明
- **CHANGELOG_ARCHIVE_SYSTEM.md** - 更新日志
- **README.md** - 项目说明

---

**最后更新**: 2026-01-17
**版本**: v1.0 - 自动分层归档系统
