# Scientific Data 论文框架

## 标题

**LESS-200: A Synchronized Dual-View Video Dataset with Pose Estimation and Expert Annotations for Automated Landing Error Scoring System Assessment**

备选：

- *A Large-Scale Dual-View Video Benchmark for Automated Landing Error Scoring System Evaluation*
- *DVJ-200: A Richly Annotated Dual-View Video Dataset for ACL Injury Risk Screening*

---

## 1. Background & Summary

### 1.1 ACL损伤预防筛查的临床需求

前交叉韧带（ACL）损伤是年轻运动人群中最常见的严重膝关节损伤。仅美国每年约发生10-20万例ACL损伤[1,2]，每例手术重建的直接医疗费用约为2-5万美元，而考虑康复、误工和长期关节退变等间接成本后，终生经济负担可达6-17万美元[3]——在单次运动损伤中，这一经济代价仅次于脊髓损伤等灾难性损伤。ACL重建术后的康复周期通常为6-12个月，约20-25%的年轻运动员在重返赛场后5年内发生对侧或同侧二次ACL损伤[4]，且50-70%的ACL损伤患者在10-20年内发展为影像学可见的膝骨关节炎[5]，无论是否接受手术重建。流行病学数据一致表明，ACL损伤集中于涉及跳跃着陆、急停变向的运动项目（篮球、足球、排球等），且女性发生率为男性的2-8倍[6,7]——这一性别差异被认为与神经肌肉控制策略的差异有关，而非单纯的解剖因素[8]。多项系统综述和meta分析已证实，针对性的神经肌肉训练干预可将ACL损伤风险降低约50%[9,10]，但干预的前提是筛查：在大量运动人群中识别出着陆生物力学模式不良的高风险个体。这就将问题聚焦到了筛查工具上——理想的筛查工具应同时满足评估准确性和大规模实施的可行性。

### 1.2 LESS评估体系：价值与瓶颈

Landing Error Scoring System（LESS）正是为回应上述需求而设计的。Padua et al.（2009）提出这一评估体系时[11]，其核心设计理念是**仅需双视角视频即可完成标准化的着陆生物力学评估**，无需力板、动捕系统等昂贵实验室设备。受试者从31cm跳箱完成Drop Vertical Jump（DVJ），评估者通过正面和侧面视频回放，对17项生物力学指标（涵盖膝关节、髋关节、躯干和足部在初始触地IC和最大屈膝MKF两个时刻的姿态特征）进行0/1或0/1/2评分，总分0-19分。该研究在2691名军校学员中验证了LESS的信效度：评分者间信度ICC=0.84，评分者内信度ICC=0.91，LESS总分与三维运动学和动力学指标之间存在显著相关性。后续前瞻性研究进一步验证了LESS与ACL损伤风险的预测关系：Padua et al.（2015）在1564名精英青少年足球运动员中发现，LESS总分≥5的个体ACL损伤风险显著升高[12]。

LESS的临床推广面临两个结构性瓶颈。第一是效率：尽管LESS的设计初衷是"简易筛查"，但实际操作中评估者仍需逐帧分析双视角视频、在IC和MKF帧上逐项判定17个指标，经过训练的评估者完成单次评估仍需约8分钟（Padua et al. 2009报告的平均时间[11]），面对校园体测或社区筛查等数百人规模的场景，人工评估在时间和人力上均不可行。第二是一致性：LESS的17项指标中，部分指标（如膝关节外翻程度、足旋前）的判定依赖评估者的主观视觉判断，不同评估者对同一动作的评分可能存在分歧，尤其是处于评分边界的样本——Onate et al.（2010）报告的逐项评分者间一致率在部分指标上低于70%[13]。这两个瓶颈本质上指向同一个解决方案：用计算机视觉和深度学习技术实现LESS的自动化评分——机器不会疲劳，且对相同输入始终产生相同输出。

### 1.3 自动化研究的数据瓶颈

然而，自动化LESS评估研究的推进受制于一个更基础的问题：缺乏公开的标准化基准数据集。回顾现有文献（Table 1），所有LESS相关研究均使用私有数据集，且存在以下共性不足：（1）多数仅提供LESS总分而非17项逐项评分，限制了细粒度自动评分研究；（2）部分仅使用单视角，与LESS的双视角评估标准不一致；（3）均未公开数据和采集工具，导致不同研究之间无法直接对比算法性能。对于深度学习方法而言，缺乏公开基准意味着无法建立可比较的benchmark，这与计算机视觉领域通过ImageNet[14]、COCO[15]等公开数据集推动算法进步的范式形成鲜明对比。

> Table 1需根据实际文献检索补充和修正。以下为已确认的代表性研究。

| 数据集 | 人数 | 视角 | 同步 | 骨骼点 | 标注粒度 | 工具链开源 | 数据公开 |
|--------|------|------|------|--------|---------|-----------|---------|
| Padua et al. 2009 [11] | ~2691 | 双 | 硬件 | 否 | 总分 | 否 | 否 |
| Onate et al. 2010 [13] | ~200 | 双 | 硬件 | 否 | 总分 | 否 | 否 |
| Mauntel et al. 2017 [16] | 320 | 单(正面) | — | 是(Kinect) | 逐项 | 否 | 否 |
| **LESS-200 (Ours)** | **200** | **双** | **软件** | **是** | **关键帧+逐项** | **是** | **是** |

### 1.4 本数据集的定位与贡献

LESS-200旨在填补上述空白。它是首个同时满足以下条件的LESS数据集：公开可获取、大规模（200名受试者×5次试跳）、完整双视角同步、提供帧级关键帧标注和17项逐项专家评分、附带2D骨骼点估计数据。受试者覆盖篮球、排球、羽毛球等运动项目，涵盖一级运动员、二级运动员、未达级体育生和普通学生，两性别均衡分布。这一分层设计确保了LESS评分在0-19分范围内的充分分布，避免了单一群体导致的评分集中问题。

区别于依赖专业动捕设备的实验室数据集，LESS-200的全部采集和标注流程基于普通高清摄像头和自研开源工具链——RecSync多设备同步录制系统和LESS-Annotator专家标注系统。这一选择与LESS评估体系"仅需视频即可完成"的临床设计理念一致[11]：如果数据集的采集本身依赖Vicon等实验室级设备，就与LESS的低成本定位相矛盾，也使其他团队难以复现和扩展。数据集、采集工具和标注工具的全部代码均已开源，构成从采集到标注到使用的端到端可复现流程。

---

## 2. Methods

### 2.1 Participants

约200名受试者从XX大学体育学院及校内招募。样本设计的核心考量不是最大化样本量，而是确保LESS评分分布的广度——这直接决定了数据集对自动评分模型训练的支撑能力。运动等级的分层是实现这一目标的关键：长期系统训练的高水平运动员通常具有更优的着陆生物力学模式（更深的膝关节和髋关节屈曲、更好的躯干控制），对应较低的LESS分数；而缺乏专项训练的普通学生更可能表现出僵硬着陆、膝外翻等高风险特征，对应较高的LESS分数。通过纳入从一级运动员到普通学生的完整运动等级谱，数据集中"优秀"到"差"的评分均有充足样本，避免了评分分布集中于某一区间导致的类别不平衡问题。多运动项目的设计则提供了着陆模式的多样性——篮球运动员和排球运动员的着陆策略存在项目特异性差异，这种多样性有利于模型的泛化能力。

**人群构成（Table 2）**：

| 项目 | 一级运动员 | 二级运动员 | 未达级体育生 | 普通学生 | 小计 |
|------|-----------|-----------|-------------|---------|------|
| 篮球 | x | x | x | - | xx |
| 排球 | x | x | x | - | xx |
| 羽毛球 | x | x | x | - | xx |
| 其他项目 | x | x | x | - | xx |
| 普通学生 | - | - | - | x | xx |
| **合计** | **xx** | **xx** | **xx** | **xx** | **~200** |

> 各单元格按实际招募填写，行内需呈现性别分布（M/F）。

**纳入标准**：18-30岁；能独立完成DVJ；近6个月保持规律运动/训练；无下肢急性损伤（近6个月）；自愿签署知情同意书。

**排除标准**：既往ACL损伤/重建术史；近12个月内下肢骨折、韧带撕裂、半月板损伤；心血管/神经肌肉系统疾病；BMI > 30；测试当日身体不适。

伦理审批编号：____。

### 2.2 Experimental Setup

设备选型遵循两个原则。第一是与LESS的临床定位一致：LESS被设计为仅需标准视频即可实施的筛查工具，其临床推广价值正在于摆脱实验室级设备的依赖，因此数据集的采集设备应反映这一定位，而非退回到Vicon动捕或工业级同步摄像系统——如果复现数据集需要数十万元的设备投入，就违背了LESS本身的设计初衷，也使数据集的扩展性大打折扣。第二是可复现性：使用消费级高清摄像头和开源同步软件，使任何研究团队可以相同的成本在任意场地复制完整采集流程。

同步方案选择自研RecSync纯软件方案而非硬件genlock，同样基于上述考量：genlock需要专用同步信号发生器和同轴线缆，部署成本高且对场地布线有严格要求，而RecSync仅需普通局域网连接，实现了帧级同步精度（见4.2节验证）。标注工具选择自研LESS-Annotator而非通用视频标注软件（如ELAN、BORIS），是因为LESS评分流程有其特殊性——评分员需要在双视角同步回放中精确定位IC和MKF帧，并在这两个时刻分别完成对应指标的评分，涉及频繁的视角切换和帧级导航。通用标注工具缺乏这一工作流的内置支持，评分员需要大量手动操作来模拟上述流程，既降低效率也增加出错概率。LESS-Annotator将双视角同步回放、关键帧定位、逐项评分集成为标准化流程，从工具层面保障了标注的规范性和一致性。

**相机配置（Table 3）**：

| 参数 | 正面相机 (front) | 侧面相机 (side) |
|------|-----------------|-----------------|
| 型号 | xxx | xxx |
| 分辨率 | 1920×1080 | 1920×1080 |
| 帧率 | 60fps | 60fps |
| 距跳箱距离 | 3-4m | 3-4m |
| 架设高度 | 0.8-1.0m | 0.8-1.0m |
| 拍摄平面 | 额状面 | 矢状面 |

场地选择平整防滑运动地板，浅色纯色背景墙以最大化人体-背景对比度（这对后续骨骼点提取的精度有直接影响），均匀照明（500-1000 lux）避免阴影干扰。跳箱高度31cm（LESS标准）。两台相机分别架设于额状面（正面）和矢状面（侧面），与LESS标准中两视角的定义完全一致。场地布置图见Figure 1（俯视图+侧视图，标注所有关键尺寸和相机视野范围）。

### 2.3 Dual-View Synchronization (RecSync)

LESS的17项指标中，多项需要在同一时刻综合正面和侧面视角的信息：例如评估IC时刻的膝关节状态，需要侧面视角判断屈曲角度（Item 1），同时需要正面视角判断是否存在外翻（Item 5-7）。如果两视角不同步，评分员或自动算法看到的将是不同时刻的姿态，导致评分错误。此外，IC帧和MKF帧的标注也依赖双视角的一致性——侧面用于判断关节角度变化，正面用于确认足部触地时机，两者必须对应同一物理时刻。因此双视角的帧级同步不是可选项，而是数据质量的前提条件。

RecSync采用三个机制协同实现纯软件帧级同步：

**SNTP时钟同步**。系统采用Leader-Client架构，通过局域网有线连接。Client周期性地与Leader进行SNTP时间交换，基于四时间戳模型计算时钟偏移：$Offset = [(t_2 - t_1) + (t_3 - t_4)] / 2$。网络传输的非对称性和瞬时抖动会导致单次Offset估计存在误差，因此系统连续采集30个SNTP样本，按往返时延（RTT）升序排序后取前30%的最优样本求Offset均值——RTT最小的样本意味着网络路径最对称、抖动最小，其Offset估计最可靠。每10分钟自动触发重同步以补偿晶振频率差异导致的时钟漂移。

**预设触发时间**。传统方案中Leader发送"立即开始"命令后，各Client收到命令的时间因网络延迟而不同，直接引入同步误差。RecSync改为：Leader计算一个未来时间点 $triggerTime = currentTime + 200ms$ 并广播给所有Client，各Client将此时间转换为本地时域后等待触发。200ms的预留量远大于局域网的单程延迟（通常<1ms），确保所有Client都能在触发时刻之前收到命令。这一机制将网络传输延迟的不确定性从同步误差中完全消除，使同步精度仅取决于时钟同步的质量。

**软录制模式**。相机在系统启动后即持续采集帧并附加同步时间戳，录制器预先初始化并完成编码器预热。当帧时间戳 ≥ triggerTime 时才开始写入文件。传统"硬录制"模式在收到录制命令时才启动录制器，不同设备的录制器初始化耗时存在毫秒到数十毫秒的差异，这种差异在帧级同步中不可忽略。软录制通过将"采集何时开始"与"写入何时开始"解耦，使首帧时间的精度仅取决于时钟同步和帧采集间隔，而非硬件初始化速度。

三个机制协同工作，理论同步精度为：$|t_{start}^{A} - t_{start}^{B}| \leq 1/fps + \sigma_{SNTP} \approx 18\text{-}22ms$（60fps）。实际验证见4.2节。RecSync系统架构与同步时序图见Figure 2。

**开源地址**：GitHub [RecSync仓库链接]

### 2.4 Data Acquisition Protocol

严格遵循LESS标准化DVJ测试流程：受试者站上31cm跳箱，双脚与肩同宽，向前跳下（非跳远），双脚同时落地后立即进行最大努力垂直跳跃，垂直跳跃后自然落地。关键指令是"落地后立即反跳"和"尽全力向上跳"——前者确保着陆动作的连贯性和反应性（这是LESS评估的核心观察窗口），后者确保受试者以最大努力完成动作而非刻意控制着陆姿态（刻意控制会导致评分偏低，不反映真实的神经肌肉控制模式）。

每人完成5次有效试跳，间隔30秒。5次重复的设计既提供了试次内变异信息（同一受试者不同试次的评分差异反映了动作的稳定性），也为后续研究提供了更多训练样本。出现以下情况时该次不计并重新测试：单脚先落地、落地后无反跳或反跳延迟明显、录制设备故障或同步异常、动作明显失误。

录制操作：Leader端设置受试者ID → 确认两Client已连接且同步 → 主试口令 → Leader端点击"开始录制" → 受试者完成动作（约3-5秒）→ Leader端点击"停止录制" → 回放检查 → 回合号自动递增。文件命名：`{view}_{subjectID}_{taskID}_{timestamp}_{epoch}.mp4`。

### 2.5 Pose Estimation

使用MediaPipe BlazePose（或OpenPose / MMPose，根据实际确认）对所有视频逐帧提取2D骨骼点，输出33个关键点的归一化2D坐标及置信度。提供骨骼点数据的目的是降低后续研究的技术门槛：姿态估计本身是一个独立的技术环节，涉及模型选择、参数调优和部署，如果每个使用数据集的研究者都需要自行完成这一步骤，不仅增加了重复劳动，还因不同研究者使用不同姿态估计方案而导致结果不可比。预提取并统一发布骨骼点数据后，研究者可直接使用骨骼点序列训练LESS评分模型，将精力集中于评分算法本身。

检测失败帧标记置信度为0，不做插值，保留原始状态。这一设计是有意为之：不同的插值策略会引入不同的偏差，由数据集提供者选择某种插值方法会限制使用者的自由度。保留原始检测结果，让研究者根据自身需求选择处理策略，是更负责任的做法。骨骼点精度验证见4.3节。

关键点与LESS评估的对应关系（Table 4A）：列出与LESS 17项指标直接相关的关节点（膝、髋、踝、肩、足尖等），说明每个关键点用于计算哪些LESS指标。

### 2.6 Expert Annotation (LESS-Annotator)

专家标注使用自研开源的LESS-Annotator系统完成。该系统专为LESS评分工作流设计，集成双视角同步视频回放、逐帧/逐秒导航、关键帧一键标记和17项评分面板。与通用标注工具相比，其核心优势在于将LESS评分的完整流程（定位IC帧 → 评估IC时刻指标 → 定位MKF帧 → 评估MKF时刻指标 → 评估IC→MKF变化指标 → 整体印象评分）固化为引导式操作流程，确保每位评分员按相同顺序完成所有评分项，减少遗漏和顺序效应。

**标注人员**：x名，运动医学/运动科学背景。培训流程：LESS标准系统讲解 → 标准视频示例讨论 → 20例试标注 → 一致性测试（通过率>80%方可参与正式标注）。培训的关键不是让评分员"记住规则"，而是通过边界案例的反复讨论建立一致的判断标准——特别是对于膝外翻程度、躯干屈曲角度等连续量被离散化为0/1或0/1/2时，评分边界的认定需要在评分员之间达成共识。

**关键帧标注**：

| 标注项 | 定义 | 说明 |
|--------|------|------|
| 起始帧 (Start) | 受试者开始跳离跳箱的帧 | 用于界定分析窗口起点 |
| 触地帧 (IC) | 脚首次接触地面的帧 | LESS评分的核心参考时刻 |
| 最大屈膝帧 (MKF) | 膝关节屈曲角度最大的帧 | LESS评分的第二参考时刻 |

**LESS 17项评分（Table 4B）**：

| 项目 | 指标 | 分值 | 视角 | 时刻 |
|------|------|------|------|------|
| 1 | 膝关节屈曲 | 0/1/2 | 侧面 | IC |
| 2 | 髋关节屈曲 | 0/1 | 侧面 | IC |
| 3 | 躯干屈曲 | 0/1/2 | 侧面 | IC |
| 4 | 足跟先落地 | 0/1 | 侧面 | IC |
| 5 | 膝关节内翻 | 0/1 | 正面 | IC |
| 6 | 膝关节外翻-内侧移动 | 0/1 | 正面 | IC |
| 7 | 膝关节外翻-足趾位置 | 0/1 | 正面 | IC |
| 8 | 足旋前 | 0/1 | 正面/侧面 | IC |
| 9 | 站立宽度 | 0/1 | 正面 | IC |
| 10 | 膝关节屈曲 | 0/1/2 | 侧面 | MKF |
| 11 | 躯干屈曲 | 0/1/2 | 侧面 | MKF |
| 12 | 膝关节内翻 | 0/1 | 正面 | MKF |
| 13 | 膝关节外翻-内侧移动 | 0/1 | 正面 | MKF |
| 14 | 膝关节外翻-足趾位置 | 0/1 | 正面 | MKF |
| 15 | 足旋前位移 | 0/1 | 正面/侧面 | IC→MKF |
| 16 | 膝关节位移 | 0/1 | 正面 | IC→MKF |
| 17 | 整体印象 | 0/1/2 | 综合 | 综合 |

> 总分0-19。风险分级：Excellent (≤4) / Good (5-6) / Moderate (7-9) / Poor (≥10)。

**双人标注与仲裁**：两名评分员独立标注同一视频，结果一致则直接采用，不一致项由第三方专家仲裁。双人独立标注而非讨论后共同标注，是为了获得真实的评分者间信度数据——如果两人讨论后标注，信度数据会被人为抬高，无法反映LESS评分的固有主观性。所有标注保留原始双人评分、一致性判定和仲裁记录，使用者可据此分析哪些指标的主观性更强，为自动评分模型的设计提供先验信息。

**开源地址**：GitHub [LESS-Annotator仓库链接]

---

## 3. Data Records

数据集托管于Figshare/Zenodo（获取永久DOI），许可证CC BY 4.0。

### 3.1 数据集概览（Table 5）

| 数据类型 | 文件数量 | 格式 | 单文件大小 |
|---------|---------|------|-----------|
| 正面视频 | ~1000 | MP4 (H.264) | ~50-100MB |
| 侧面视频 | ~1000 | MP4 (H.264) | ~50-100MB |
| 正面骨骼点 | ~1000 | JSON | ~1-5MB |
| 侧面骨骼点 | ~1000 | JSON | ~1-5MB |
| 关键帧标注 | 1 | CSV | ~100KB |
| LESS评分 | 1 | CSV | ~200KB |
| 标注一致性 | 1 | CSV | ~50KB |
| 受试者信息 | 1 | CSV | ~20KB |
| 录制日志 | 1 | CSV | ~50KB |
| 同步验证 | 1 | CSV | ~10KB |

### 3.2 目录结构（Figure 3）

```
LESS-200/
├── videos/
│   ├── front/
│   │   ├── front_s001_m01_e1.mp4
│   │   └── ...
│   └── side/
│       ├── side_s001_m01_e1.mp4
│       └── ...
├── keypoints/
│   ├── front/
│   │   ├── front_s001_m01_e1.json
│   │   └── ...
│   └── side/
│       ├── side_s001_m01_e1.json
│       └── ...
├── annotations/
│   ├── keyframes.csv
│   ├── less_scores.csv
│   └── annotator_agreement.csv
├── metadata/
│   ├── subjects.csv
│   ├── recording_log.csv
│   └── sync_validation.csv
├── splits/
│   └── recommended_splits.json
├── code/
│   ├── dataloader.py
│   ├── visualization.py
│   └── requirements.txt
└── README.md
```

### 3.3 视频数据

编码H.264，容器MP4，分辨率1920×1080，帧率60fps，每段约3-8秒（覆盖从跳离跳箱到反跳落地的完整动作周期）。每组包含正面和侧面两个同步视频。选择60fps而非30fps的原因是：DVJ着陆阶段的关键事件（足触地、膝关节最大屈曲）发生在极短时间内，60fps提供约16.7ms的时间分辨率，使IC帧和MKF帧的定位精度更高，也为后续基于帧间差异的自动关键帧检测算法提供更细粒度的输入。

### 3.4 骨骼点数据

JSON格式，逐帧存储33个关键点的归一化2D坐标和置信度。数据结构：

```json
{
  "video_id": "front_s001_m01_e1",
  "fps": 60,
  "resolution": [1920, 1080],
  "pose_model": "mediapipe_blazepose_v2",
  "num_keypoints": 33,
  "frames": [
    {
      "frame_idx": 0,
      "timestamp_ms": 0.0,
      "keypoints": [
        {"id": 0, "name": "nose", "x": 0.52, "y": 0.15, "confidence": 0.98},
        {"id": 25, "name": "left_knee", "x": 0.48, "y": 0.62, "confidence": 0.96}
      ]
    }
  ]
}
```

### 3.5 标注数据

**keyframes.csv**：

| 字段 | 类型 | 说明 |
|------|------|------|
| video_id | string | 视频标识（s001_m01_e1） |
| start_frame | int | 起始帧 |
| ic_frame | int | 触地帧 |
| mkf_frame | int | 最大屈膝帧 |
| annotator_1 / annotator_2 | string | 标注员编号 |
| ic_diff / mkf_diff | int | 两标注员帧差值 |
| final_method | string | consensus / arbitration |

**less_scores.csv**：

| 字段 | 类型 | 说明 |
|------|------|------|
| video_id | string | 视频标识 |
| item_01 ~ item_17 | int | 各项LESS评分 |
| total_score | int | 总分（0-19） |
| risk_level | string | excellent/good/moderate/poor |
| annotator_1 / annotator_2 | string | 标注员编号 |
| agreement_count | int | 一致项数（0-17） |
| final_method | string | 确定方式 |

### 3.6 元数据

**subjects.csv**：subject_id, gender, age, height_cm, weight_kg, bmi, dominant_leg, sport_type, sport_level (national_1/national_2/sub_level/general), training_years, training_freq, injury_history (脱敏), test_date。不含姓名、联系方式等可识别个人身份的信息。

**recording_log.csv**：video_id, subject_id, epoch, is_retake, sync_status, sync_offset_ms, recording_duration_s, quality_check (pass/fail/marginal), notes。

---

## 4. Technical Validation

本节需回答四个问题：视频采集质量是否达标？双视角同步是否满足LESS评估的精度要求？骨骼点估计是否可靠？专家标注是否一致？审稿人期望看到的不是定性结论，而是可复现的验证方法和量化证据。

### 4.1 Video Quality Assessment

对所有视频进行程序化质量检查：分辨率一致性（1080p）、帧率一致性（60fps）、文件完整性。统计有效数据率：总采集组数、有效组数、剔除组数及剔除率。按原因对剔除数据分类（动作不规范、画面问题、同步异常等），剔除原因分布见Figure 4。报告剔除率的意义在于让使用者评估采集流程的成熟度——剔除率越低，说明标准化流程执行越到位，也间接反映了RecSync系统在实际采集中的稳定性。

### 4.2 Synchronization Accuracy Validation

同步精度直接决定双视角数据在同一时刻的对应关系是否成立。验证采用手机秒表法：将显示毫秒的手机秒表放置于两相机视野重叠区域中心，启动RecSync系统进行同步录制，录制10次，每次10-15秒。事后逐帧回放两路视频，人工标注秒表显示相同毫秒数字的对应帧号，计算帧差。该方法的优势在于：（1）验证装置零成本，任何团队可复现；（2）秒表毫秒显示提供了亚帧级的视觉参考，虽然最终精度受限于帧率（60fps下最小分辨率16.7ms），但足以验证帧级同步是否成立；（3）结果直观可理解，审稿人和读者无需理解复杂的信号处理流程。为增强统计可靠性，10次录制分布在系统运行的不同阶段（初始同步后、运行30分钟后、重同步后），以覆盖时钟漂移和网络状态变化的影响。

**结果（Table 6）**：

| 指标 | 值 |
|------|------|
| 验证次数 | 10 |
| 平均误差 | x.x帧 (xx.xms) |
| 标准差 | x.x帧 (xx.xms) |
| 最大误差 | x帧 (xxms) |
| 95%置信区间 | [x, x]ms |
| 误差=0帧的比例 | xx% |
| 误差≤1帧的比例 | xx% |

**误差对LESS评估的影响分析**：LESS评估中IC到MKF的时间间隔通常为200-500ms（60fps下12-30帧），同步误差约1帧（~17ms）仅占该时间窗口的3-8%。17项LESS指标均为离散评分（0/1或0/1/2），对应的姿态变化发生在数十毫秒到数百毫秒的时间尺度上，远大于同步误差。因此帧级同步精度对LESS评分不构成实质影响。

### 4.3 Pose Estimation Quality

骨骼点数据作为派生数据发布，其精度决定了基于骨骼点的下游研究是否可信。验证方法：随机抽取x组视频，由研究人员在关键帧（IC帧和MKF帧）上人工标注LESS相关关节位置（膝、髋、踝、肩、足尖），与算法估计结果对比。

报告指标：检测成功率（全身关键点完整检测的帧占比）、PCK@0.05和PCK@0.1（各关键点）。Table 7重点展示与LESS评估直接相关的关节点精度——这些关节点的精度比全身平均精度更有意义，因为LESS评分仅依赖特定关节的角度和位置关系。同时分析典型失败案例（肢体交叉遮挡、运动模糊、宽松着装覆盖关节等），为使用者提供对骨骼点可靠性的合理预期。需要强调的是，骨骼点数据定位为"预计算的便利数据"而非"ground truth"，使用者可根据4.3节的精度数据决定是直接使用还是自行提取。

### 4.4 Inter-Rater and Intra-Rater Reliability

标注质量是数据集能否作为监督学习"金标准"的核心依据。

**关键帧一致性**：报告两标注员在IC帧和MKF帧上的平均帧差及标准差。IC帧的一致性通常较高，因为足触地的视觉信号（足与地面接触的瞬间）相对明确；MKF帧的一致性可能稍低，因为膝关节屈曲角度在最大值附近的变化较为平缓，存在数帧的模糊区间，不同标注员可能选择该区间内的不同帧。这一差异本身就是有价值的信息，应如实报告而非试图消除。

**LESS逐项评分者间信度（Table 8）**：对每一项指标单独报告Cohen's Kappa、一致率和仲裁率。预期规律：（1）二值指标（0/1）的Kappa通常高于三值指标（0/1/2），因为后者多了一个中间状态的判定边界；（2）侧面视角指标（膝关节屈曲角度、躯干屈曲等）的一致性通常优于正面视角指标（膝外翻、足旋前等），因为矢状面上的角度变化幅度较大、视觉判断更容易，而额状面上的微妙偏移更依赖主观判断；（3）整体印象评分（Item 17）的主观性最强，Kappa可能最低。LESS总分的ICC值反映17项评分汇总后的整体可靠性。

**评分者内信度**：随机抽取10%样本（约100组），间隔2周重复标注，报告各项Kappa和ICC。评分者内信度反映标注的时间稳定性——如果同一评分员在不同时间对同一视频给出不同评分，说明该指标的判定标准存在内在不确定性。

信度可视化见Figure 5（17项指标的Kappa柱状图及95%CI）。

### 4.5 Dataset Statistics

**受试者特征（Table 9）**：按全体、性别、运动等级分组报告年龄、身高、体重、BMI、训练年限的均值和标准差，组间差异用独立样本t检验或单因素ANOVA。这些数据帮助使用者判断数据集对其目标人群的适用性——例如，如果某研究者关注青少年运动员，本数据集（18-30岁）的适用性就需要审慎评估。

**LESS评分分布（Figure 6）**：(A) 总分直方图；(B) 各指标异常率柱状图（得分≥1的比例，反映该项指标在样本中被触发的频率）；(C) 不同群体总分箱线图（按性别、项目、等级分组）。评分分布的形态对下游任务有直接影响：如果总分高度集中在4-6分区间（即大多数受试者着陆模式中等偏好），则"差"和"优秀"两端的样本量不足，模型在极端值区间的预测能力将受限。分层设计在一定程度上缓解了这一问题，但如果实际分布仍然偏态，应在本节中如实报告并讨论对模型训练的潜在影响。

**风险等级分布**：Excellent (≤4) / Good (5-6) / Moderate (7-9) / Poor (≥10) 各占比。

---

## 5. Usage Notes

### 5.1 Access and License

数据集：Figshare/Zenodo（DOI：xxx），CC BY 4.0。采集工具RecSync：GitHub [链接]。标注工具LESS-Annotator：GitHub [链接]。提供BibTeX引用格式。

### 5.2 Recommended Applications

| 研究方向 | 任务 | 使用的数据 |
|---------|------|-----------|
| 自动化LESS评分 | 从视频/骨骼点预测17项评分 | 视频+骨骼点+LESS评分 |
| 关键帧检测 | 自动定位IC帧和MKF帧 | 视频+骨骼点+关键帧标注 |
| 运动姿态估计 | 运动场景下的姿态基准 | 视频+骨骼点 |
| 损伤风险分级 | LESS风险等级分类 | 全部标注数据 |
| 双视角3D重建 | 利用双视角进行3D姿态估计 | 双视角视频+骨骼点 |

### 5.3 Data Splits

按受试者ID划分（非按视频），避免同一受试者的不同试次分别出现在训练集和测试集中——这是运动分析领域常见的数据泄漏来源，因为同一人的不同试次在动作模式上高度相似。推荐70%/15%/15%，按性别×运动等级交叉分层抽样，确保各子集的人群分布一致。预定义划分见 `splits/recommended_splits.json`。

### 5.4 Baseline Results

提供基线模型结果作为后续研究的对比标准。基线的价值不在于性能最优，而在于可复现——使用数据集的研究者可以首先复现基线结果以验证数据加载和评估流程的正确性，然后在此基础上改进。

| 方法 | 输入 | 任务 | 指标 | 结果 |
|------|------|------|------|------|
| 骨骼点+LSTM | 骨骼点序列 | LESS总分回归 | MAE | x.xx |
| 骨骼点+Transformer | 骨骼点序列 | 逐项分类 | Accuracy | xx.x% |

### 5.5 Known Limitations

1. **受试者来源**：单一高校，地域和人群代表性有限；但群体内部的项目、等级和性别多样性保证了评分分布的广度
2. **测试环境**：单一场地和相机型号，环境泛化性未验证；但RecSync和LESS-Annotator均已开源，其他团队可在不同场地复现并扩展数据集
3. **同步精度**：软件同步非硬件genlock级别，但4.2节验证表明帧级精度满足LESS评估需求
4. **骨骼点**：算法估计而非光学动捕金标准，4.3节提供精度参考，定位为便利数据而非ground truth
5. **LESS版本**：基于Padua et al. (2009) 原始版本

---

## 6. Code Availability

本数据集的完整工具链均已开源：

| 工具 | 功能 | 地址 |
|------|------|------|
| RecSync | 多设备同步视频录制 | GitHub [链接] |
| LESS-Annotator | LESS专家标注系统 | GitHub [链接] |
| LESS-200 Code | 数据读取、可视化、基线模型 | GitHub [链接] |

三个工具覆盖了从数据采集、标注到使用的端到端流程。这意味着本数据集不仅是一个静态的数据资源，更是一套可复现、可扩展的数据生产流水线——其他团队可使用RecSync在不同场地、不同人群中采集新数据，使用LESS-Annotator进行标准化标注，然后将新数据与LESS-200合并以构建更大规模的基准。这种"工具链+数据集"的开源模式是LESS-200区别于现有LESS研究的核心差异。

---

## References

1. Sanders, T. L., Maradit Kremers, H., Stuart, M. J., et al. (2016). Incidence of anterior cruciate ligament tears and reconstruction: a 21-year population-based study. *The American Journal of Sports Medicine*, 44(6), 1502-1507.
2. Montalvo, A. M., Schneider, D. K., Yut, L., et al. (2019). "What's my risk of sustaining an ACL injury while playing sports?" A systematic review with meta-analysis. *British Journal of Sports Medicine*, 53(19), 1003-1012.
3. Herzog, M. M., Marshall, S. W., Lund, J. L., et al. (2017). Cost of outpatient arthroscopic anterior cruciate ligament reconstruction among commercially insured patients in the United States, 2005-2013. *Orthopaedic Journal of Sports Medicine*, 5(1), 2325967116684776.
4. Wiggins, A. J., Grandhi, R. K., Schneider, D. K., et al. (2016). Risk of secondary injury in younger athletes after anterior cruciate ligament reconstruction: a systematic review and meta-analysis. *The American Journal of Sports Medicine*, 44(7), 1861-1876.
5. Luc, B., Gribble, P. A., & Pietrosimone, B. G. (2014). Osteoarthritis prevalence following anterior cruciate ligament reconstruction: a systematic review and numbers-needed-to-treat analysis. *Journal of Athletic Training*, 49(6), 806-819.
6. Prodromos, C. C., Han, Y., Rogowski, J., et al. (2007). A meta-analysis of the incidence of anterior cruciate ligament tears as a function of gender, sport, and a knee injury-reduction regimen. *Arthroscopy*, 23(12), 1320-1325.
7. Arendt, E., & Dick, R. (1995). Knee injury patterns among men and women in collegiate basketball and soccer: NCAA data and review of literature. *The American Journal of Sports Medicine*, 23(6), 694-701.
8. Hewett, T. E., Myer, G. D., Ford, K. R., et al. (2005). Biomechanical measures of neuromuscular control and valgus loading of the knee predict anterior cruciate ligament injury risk in female athletes: a prospective study. *The American Journal of Sports Medicine*, 33(4), 492-501.
9. Sugimoto, D., Myer, G. D., Foss, K. D., et al. (2015). Specific exercise effects of preventive neuromuscular training intervention on anterior cruciate ligament injury risk reduction in young females: meta-analysis and subgroup analysis. *British Journal of Sports Medicine*, 49(5), 282-289.
10. Webster, K. E., & Hewett, T. E. (2018). Meta-analysis of meta-analyses of anterior cruciate ligament injury reduction training programs. *Journal of Orthopaedic Research*, 36(10), 2696-2708.
11. Padua, D. A., Marshall, S. W., Boling, M. C., et al. (2009). The Landing Error Scoring System (LESS) is a valid and reliable clinical assessment tool of jump-landing biomechanics. *The American Journal of Sports Medicine*, 37(10), 1996-2002.
12. Padua, D. A., DiStefano, L. J., Beutler, A. I., et al. (2015). The Landing Error Scoring System as a screening tool for an anterior cruciate ligament injury-prevention program in elite-youth soccer athletes. *Journal of Athletic Training*, 50(6), 589-595.
13. Onate, J. A., Cortes, N., Welch, C., et al. (2010). Expert versus novice interrater reliability and criterion validity of the Landing Error Scoring System. *Journal of Sport Rehabilitation*, 19(1), 41-56.
14. Deng, J., Dong, W., Socher, R., et al. (2009). ImageNet: a large-scale hierarchical image database. *Proceedings of the IEEE Conference on Computer Vision and Pattern Recognition*, 248-255.
15. Lin, T. Y., Maire, M., Belongie, S., et al. (2014). Microsoft COCO: common objects in context. *Proceedings of the European Conference on Computer Vision*, 740-755.
16. Mauntel, T. C., Padua, D. A., Stanley, L. E., et al. (2017). Automated quantification of the Landing Error Scoring System with a markerless motion-capture system. *Journal of Athletic Training*, 52(11), 1002-1009.
17. Lugaresi, C., Tang, J., Nash, H., et al. (2019). MediaPipe: A framework for building perception pipelines. *arXiv preprint arXiv:1906.08172*.
18. Mills, D. L. (1991). Internet time synchronization: the network time protocol. *IEEE Transactions on Communications*, 39(10), 1482-1493.

---

## 图表汇总

| 编号 | 类型 | 内容 | 章节 |
|------|------|------|------|
| Fig.1 | Figure | 实验场地布置图（俯视+侧视） | 2.2 |
| Fig.2 | Figure | RecSync系统架构与同步时序图 | 2.3 |
| Fig.3 | Figure | 数据集目录结构 | 3.2 |
| Fig.4 | Figure | 数据剔除原因分布 | 4.1 |
| Fig.5 | Figure | 17项指标评分者间信度 | 4.4 |
| Fig.6 | Figure | LESS评分分布与群体对比 | 4.5 |
| Table 1 | Table | 现有数据集对比 | 1.3 |
| Table 2 | Table | 受试者人群构成 | 2.1 |
| Table 3 | Table | 相机配置参数 | 2.2 |
| Table 4A | Table | 骨骼点关键点与LESS指标对应 | 2.5 |
| Table 4B | Table | LESS 17项指标定义 | 2.6 |
| Table 5 | Table | 数据集文件统计 | 3.1 |
| Table 6 | Table | 同步精度验证结果 | 4.2 |
| Table 7 | Table | 骨骼点检测精度 | 4.3 |
| Table 8 | Table | LESS逐项评分者间信度 | 4.4 |
| Table 9 | Table | 受试者描述性统计 | 4.5 |

---

## 审稿人可能质疑及应对

| 质疑 | 应对 |
|------|------|
| 为什么不用Vicon动捕做金标准 | LESS的临床价值建立在"仅需视频"的前提上，采集依赖实验室设备与LESS定位矛盾；且本数据集的ground truth是专家评分而非运动学参数 |
| 为什么不用硬件同步 | genlock成本高、部署受限，与低成本可复现的设计原则冲突；4.2节验证帧级精度满足LESS需求 |
| 为什么自研标注工具而非用ELAN/BORIS | LESS评分流程的特殊性（双视角同步回放+关键帧定位+17项评分）在通用工具中需大量手动操作，专用工具从流程层面保障一致性 |
| 软件同步精度够吗 | 4.2节量化验证 + 误差占IC-MKF时间窗口比例分析 |
| 受试者来源单一 | 承认局限，强调项目/等级/性别多样性保证评分分布广度；开源工具链使扩展到其他人群成为可能 |
| 骨骼点非金标准 | 明确定位为派生便利数据，4.3节报告精度，使用者可自行决定是否采用 |
| 样本量是否足够 | Table 1对比现有研究，200人×5次在LESS领域属大规模；分层设计保证评分分布覆盖完整 |
