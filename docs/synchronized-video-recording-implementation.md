# 多设备视频同步录制系统实现方法

## 1. 系统概述

本系统实现了一种基于软件时钟同步的多设备视频同步录制方案，采用Leader-Client架构，通过SNTP时钟同步算法和软录制模式，实现多台设备在分布式环境下的精确同步录制。

## 2. 核心技术原理

### 2.1 SNTP时钟同步算法

#### 2.1.1 四时间戳模型

系统采用简化网络时间协议（SNTP）实现设备间的时钟同步，基于经典的四时间戳模型：

```
Client                              Leader
  |                                   |
  |-------- 心跳请求 (t1) ----------->|
  |                                   | t2 (收到请求)
  |                                   | t3 (发送响应)
  |<------- 心跳确认 (t1,t2,t3) ------|
  | t4 (收到响应)                      |
```

其中：
- **t1**: Client发送心跳请求的本地时间戳
- **t2**: Leader收到心跳请求的本地时间戳
- **t3**: Leader发送心跳确认的本地时间戳
- **t4**: Client收到心跳确认的本地时间戳

#### 2.1.2 RTT与时钟偏移计算

**往返时延（RTT）计算：**

$$RTT = (t_4 - t_1) - (t_3 - t_2)$$

该公式通过减去Leader的处理时间$(t_3 - t_2)$，得到纯网络传输的往返时延。

**时钟偏移（Offset）计算：**

$$Offset = \frac{(t_2 - t_1) + (t_3 - t_4)}{2}$$

该偏移量表示：$Leader时间 = Client本地时间 + Offset$

#### 2.1.3 最优样本筛选算法

为提高同步精度，系统采用多样本统计方法：

1. **样本收集**：连续收集30个SNTP样本
2. **异常值过滤**：丢弃RTT小于阈值的异常样本
3. **最优样本筛选**：按RTT升序排序，选取前30%的样本（RTT最小意味着网络抖动最小）
4. **偏移量计算**：对最优样本的Offset取平均值

```java
// 按RTT排序（升序）
samples.sort(Comparator.comparingLong(s -> s.rtt));

// 取前30%的最优样本
int bestCount = Math.max(1, samples.size() * SYNC_BEST_PERCENT / 100);
List<SntpSample> bestSamples = samples.subList(0, bestCount);

// 计算平均偏移
long avgOffset = bestSamples.stream()
    .mapToLong(s -> s.offset)
    .sum() / bestCount;
```

#### 2.1.4 周期性重同步

系统每10分钟自动触发一次重新同步，以补偿时钟漂移：

$$漂移补偿周期 = 10\ minutes$$

### 2.2 预设触发时间同步机制

#### 2.2.1 工作原理

传统方案中，Leader发送"开始录制"命令后，各Client立即开始录制。由于网络延迟的不确定性，各设备的实际录制起始时间存在差异。

本系统采用**预设触发时间**机制：

1. Leader计算一个**未来时间点**作为触发时间：
   $$triggerTime = currentTime + \Delta t$$
   其中$\Delta t$为预留的网络传输和处理时间（默认200ms）

2. Leader将触发时间（Leader时域）广播给所有Client

3. 各Client将触发时间转换为本地时域：
   $$localTriggerTime = leaderTriggerTime + offset$$

4. 所有设备在各自的本地时钟到达转换后的触发时间时，同时开始录制

#### 2.2.2 时域转换

```java
// Leader端：计算触发时间（Leader时域）
long triggerTimeNs = System.nanoTime() + 200_000_000L; // 200ms后

// Client端：转换为本地时域
long localTriggerTimeNs = syncClient.localTimeForLeaderTimeNs(triggerTimeNs);
// 等价于: localTriggerTimeNs = triggerTimeNs + leaderFromLocalNs
```

### 2.3 软录制模式

#### 2.3.1 传统硬录制的问题

传统的"硬录制"模式在收到录制命令时才启动录制器（FFmpegFrameRecorder），存在以下问题：

1. **冷启动延迟**：录制器初始化需要时间，导致各设备实际录制起始时间不一致
2. **首帧不确定性**：首帧采集时间难以精确控制
3. **同步精度受限**：即使时钟完美同步，硬件启动时间的差异仍会影响同步效果

#### 2.3.2 软录制模式原理

软录制模式的核心思想是**将采集与录制解耦**：

```
┌─────────────────────────────────────────────────────────────────┐
│                        相机始终运行                              │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐│
│  │Frame │ │Frame │ │Frame │ │Frame │ │Frame │ │Frame │ │Frame ││
│  │ ts1  │ │ ts2  │ │ ts3  │ │ ts4  │ │ ts5  │ │ ts6  │ │ ts7  ││
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘│
│                              ↑                                  │
│                         触发时间点                               │
│                              │                                  │
│  ←─────── 丢弃 ───────→│←─────── 写入文件 ───────→              │
└─────────────────────────────────────────────────────────────────┘
```

**关键设计：**

1. **持续采集**：相机始终运行，持续采集视频帧
2. **时间戳标记**：每帧附加同步时钟时间戳（使用SNTP对齐后的时钟）
3. **录制器预热**：收到录制命令时预先初始化录制器，但不写入帧
4. **逻辑切片**：当帧时间戳 ≥ 触发时间时，从该帧开始写入文件

```java
public boolean processFrame(TimestampedFrame tsFrame) {
    switch (recordingState) {
        case WAITING:
            // 检查是否到达触发时间
            if (tsFrame.timestampNs >= triggerTimeNs) {
                recordingState = RecordingState.RECORDING;
                recorder.record(tsFrame.frame);  // 从这一帧开始写入
                return true;
            }
            break;
        case RECORDING:
            recorder.record(tsFrame.frame);  // 继续写入
            return true;
    }
    return false;
}
```

#### 2.3.3 状态机模型

```
        prepareRecording()              timestampNs >= triggerTime
┌────┐ ───────────────────> ┌─────────┐ ────────────────────────> ┌───────────┐
│IDLE│                      │ WAITING │                           │ RECORDING │
└────┘ <─────────────────── └─────────┘ <──────────────────────── └───────────┘
              stopRecording()                  stopRecording()
```

## 3. 同步录制流程

### 3.1 完整时序图

```
Leader                                    Client A                    Client B
  │                                          │                           │
  │  ←───────── 心跳 + SNTP同步 ───────────→ │                           │
  │  ←───────── 心跳 + SNTP同步 ─────────────────────────────────────────→│
  │                                          │                           │
  │ [用户点击开始录制]                         │                           │
  │                                          │                           │
  │  检查：所有Client时钟同步完成?             │                           │
  │  检查：所有Client摄像头就绪?               │                           │
  │                                          │                           │
  │  计算触发时间: now + 200ms                │                           │
  │                                          │                           │
  │ ─────── START_RECORDING(triggerTime) ───→│                           │
  │ ─────── START_RECORDING(triggerTime) ────────────────────────────────→│
  │                                          │                           │
  │                               转换为本地时域                转换为本地时域
  │                               prepareRecording()          prepareRecording()
  │                                          │                           │
  │                                    [等待触发时间]               [等待触发时间]
  │                                          │                           │
  │                              ════════════╪═══════════════════════════╪════
  │                                    触发时间到达                  触发时间到达
  │                              ════════════╪═══════════════════════════╪════
  │                                          │                           │
  │                                    开始写入帧                    开始写入帧
  │                                          │                           │
```

### 3.2 录制前置条件检查

系统在开始录制前执行严格的前置条件检查：

1. **时钟同步状态检查**：所有Client必须完成SNTP时钟同步
2. **摄像头就绪检查**：所有Client的摄像头必须处于就绪状态

```java
// 检查时钟同步状态
if (!notSyncedClients.isEmpty()) {
    showWarning("以下客户端时钟未同步: " + notSyncedClients);
    return;
}

// 检查摄像头状态
if (!cameraNotReadyClients.isEmpty()) {
    showWarning("以下客户端摄像头未就绪: " + cameraNotReadyClients);
    return;
}
```

## 4. 技术优势

### 4.1 高精度时钟同步

| 特性 | 说明 |
|------|------|
| 同步精度 | 局域网环境下可达毫秒级 |
| 抗抖动能力 | 通过最优样本筛选算法消除网络抖动影响 |
| 漂移补偿 | 周期性重同步补偿时钟漂移 |

### 4.2 软录制模式优势

| 对比项 | 硬录制模式 | 软录制模式 |
|--------|-----------|-----------|
| 录制器启动 | 收到命令时启动 | 预先启动并预热 |
| 首帧时间 | 受启动延迟影响 | 精确控制 |
| 同步精度 | 受硬件差异影响 | 仅受时钟同步精度影响 |
| 资源利用 | 按需分配 | 预先分配（略高） |

### 4.3 系统特点

1. **纯软件方案**：无需专用硬件同步信号，降低部署成本
2. **可扩展性**：支持动态添加/移除录制设备
3. **容错性**：单设备故障不影响其他设备录制
4. **实时监控**：Leader可实时监控各Client的同步状态和摄像头状态

## 5. 同步精度分析

### 5.1 理论精度

在理想条件下，同步精度主要受以下因素影响：

$$\sigma_{sync} = \sqrt{\sigma_{SNTP}^2 + \sigma_{frame}^2}$$

其中：
- $\sigma_{SNTP}$：SNTP同步误差（局域网环境下约1-5ms）
- $\sigma_{frame}$：帧采集间隔（30fps时约33ms）

### 5.2 实际同步精度

在软录制模式下，各设备的录制起始帧满足：

$$|t_{start}^A - t_{start}^B| \leq \frac{1}{fps} + \sigma_{SNTP}$$

对于30fps视频，理论最大偏差约为35-40ms，实际测试中可达到更高精度。

## 6. 应用场景

- 多角度运动捕捉与分析
- 多机位视频录制
- 分布式监控系统
- 科研数据采集

## 7. 参考文献

1. Mills, D. L. (1991). Internet time synchronization: the network time protocol. IEEE Transactions on communications.
2. RFC 4330 - Simple Network Time Protocol (SNTP) Version 4


