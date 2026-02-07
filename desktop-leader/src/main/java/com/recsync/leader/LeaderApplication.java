package com.recsync.leader;

import com.recsync.core.sync.LeaderDiscoveryService;
import com.recsync.core.sync.SoftwareSyncLeader;
import com.recsync.core.sync.SyncConstants;
import com.recsync.core.transfer.FileReceiveServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LeaderApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(LeaderApplication.class);

    // 服务
    private LeaderDiscoveryService discoveryService;
    private SoftwareSyncLeader syncLeader;
    private FileReceiveServer fileServer;

    // UI组件
    private Label ipLabel;
    private Label statusLabel;
    private ListView<String> clientListView;
    private ListView<String> receivedFilesListView;
    private Label uploadStatusLabel;
    private ProgressBar uploadProgressBar;
    private Label clientCountLabel;
    private AtomicInteger clientCount = new AtomicInteger(0);
    private Button recordToggleBtn;
    private boolean isRecording = false;
    private Label statusBarLabel; // 底部状态栏
    private TextField archiveDirField; // 归档目录设置
    private String currentArchiveDir; // 当前归档目录
    private String currentBatchId = null; // 当前录制批次ID
    private boolean skipSubjectInfoWarning = false; // 是否跳过测试者信息警告

    // 视频参数配置
    private ComboBox<String> fpsComboBox;
    private ComboBox<String> resolutionComboBox;
    private int currentFps = SyncConstants.DEFAULT_VIDEO_FPS;
    private int currentWidth = SyncConstants.DEFAULT_VIDEO_WIDTH;
    private int currentHeight = SyncConstants.DEFAULT_VIDEO_HEIGHT;

    // 实验数据管理
    private TextField subjectIdField;     // 测试者ID输入框
    private TextField movementIdField;    // 动作ID输入框
    private Label episodeLabel;           // 回合ID显示标签
    private int currentEpisodeNumber = 1; // 当前回合号（自动递增）
    private String currentSubjectId = "s01";   // 当前测试者ID
    private String currentMovementId = "m01";  // 当前动作ID
    private TextField maxEpisodesField;   // 最大回合数
    private TextField maxMovementsField;  // 最大动作数
    private int maxEpisodes = 3;          // 默认最大回合数
    private int maxMovements = 2;         // 默认最大动作数
    private int currentRetakeNumber = 0;  // 当前重测次数（0=正式录制，1+=重测）

    // 测试者信息管理
    private TextField subjectNameField;   // 测试者姓名
    private TextField subjectAgeField;    // 测试者年龄
    private ComboBox<String> subjectGenderBox; // 测试者性别
    private TextField subjectWeightField; // 测试者体重
    private TextField subjectHeightField; // 测试者身高
    private Label subjectBMILabel;        // BMI显示标签
    private SubjectInfo currentSubjectInfo; // 当前测试者信息

    // 客户端状态管理
    private ConcurrentHashMap<String, Integer> clientCameraStatus = new ConcurrentHashMap<>();  // 设备名 -> 摄像头状态
    private ConcurrentHashMap<String, Boolean> clientSyncStatus = new ConcurrentHashMap<>();    // 设备名 -> SNTP同步状态

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("RecSync Leader - 多设备同步录制控制中心");

        // 初始化测试者信息
        currentSubjectInfo = new SubjectInfo();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 顶部：连接信息状态栏
        VBox topSection = createTopSection();
        root.setTop(topSection);

        // 中央：左右分栏布局
        HBox centerContainer = new HBox(15);
        centerContainer.setPadding(new Insets(15, 15, 0, 15));

        // 左侧：控制面板区域
        VBox leftPanel = createLeftControlPanel();
        leftPanel.setPrefWidth(650);
        leftPanel.setMinWidth(550);
        leftPanel.setMaxWidth(750);

        // 右侧：信息展示区域
        VBox rightPanel = createRightInfoPanel();
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        centerContainer.getChildren().addAll(leftPanel, rightPanel);
        root.setCenter(centerContainer);

        // 底部：状态栏
        HBox bottomBar = createStatusBar();
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add("data:text/css," + getCustomCSS());

        // 添加Enter键快捷键控制录制启停
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                toggleRecording();
                event.consume();
            }
        });

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(750);
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();

        // 启动服务
        startServices();
    }

    private VBox createTopSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.setStyle(
            "-fx-background-color: linear-gradient(to right, #667eea 0%, #764ba2 100%);" +
            "-fx-background-radius: 0;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);"
        );

        HBox statusBox = new HBox(30);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        ipLabel = new Label("正在初始化...");
        ipLabel.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: white;"
        );

        statusLabel = new Label("状态: 初始化中");
        statusLabel.setStyle(
            "-fx-font-size: 13px;" +
            "-fx-text-fill: #f0f0f0;"
        );

        // 客户端计数显示
        clientCountLabel = new Label("(0/10台)");
        clientCountLabel.setStyle(
            "-fx-font-size: 13px;" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-background-color: rgba(255,255,255,0.2);" +
            "-fx-padding: 5 12;" +
            "-fx-background-radius: 15;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBox.getChildren().addAll(ipLabel, statusLabel, spacer, clientCountLabel);
        section.getChildren().add(statusBox);
        return section;
    }

    /**
     * 创建左侧控制面板
     */
    private VBox createLeftControlPanel() {
        VBox panel = new VBox(15);

        // 1. 实验数据管理面板（移到最上面）
        VBox experimentPanel = createExperimentDataPanel();

        // 2. 测试者信息面板
        VBox subjectPanel = createSubjectInfoPanel();

        // 3. 视频参数配置面板
        VBox videoParamsPanel = createVideoParamsPanel();

        // 4. 录制控制按钮
        VBox recordingPanel = createRecordingControlPanel();

        // 将所有面板添加到滚动面板中
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox content = new VBox(15);
        content.getChildren().addAll(experimentPanel, subjectPanel, videoParamsPanel, recordingPanel);
        scrollPane.setContent(content);

        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        panel.getChildren().add(scrollPane);

        return panel;
    }

    /**
     * 创建右侧信息展示面板
     */
    private VBox createRightInfoPanel() {
        VBox panel = new VBox(15);
        panel.setMinWidth(400);
        panel.setPrefWidth(500);

        // 1. 客户端列表
        VBox clientsPanel = createClientsPanel();
        VBox.setVgrow(clientsPanel, Priority.ALWAYS);

        // 2. 文件接收面板
        VBox filesPanel = createFileReceivePanel();
        VBox.setVgrow(filesPanel, Priority.ALWAYS);

        panel.getChildren().addAll(clientsPanel, filesPanel);
        return panel;
    }

    /**
     * 创建客户端列表面板
     */
    private VBox createClientsPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(15));
        panel.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);"
        );

        Label title = new Label("📱 已连接客户端");
        title.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2c3e50;"
        );

        clientListView = new ListView<>();
        clientListView.setStyle("-fx-font-size: 12px;");
        clientListView.setPlaceholder(new Label("等待客户端连接..."));
        clientListView.setPrefHeight(150);
        clientListView.setMinHeight(100);
        clientListView.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(clientListView, Priority.ALWAYS);

        panel.getChildren().addAll(title, clientListView);
        return panel;
    }

    /**
     * 创建录制控制面板
     */
    private VBox createRecordingControlPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);"
        );

        Label title = new Label("🎬 录制控制");
        title.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2c3e50;"
        );

        // 主录制按钮
        recordToggleBtn = new Button("🎬 开始录制");
        recordToggleBtn.setPrefWidth(Double.MAX_VALUE);
        recordToggleBtn.setPrefHeight(45);
        recordToggleBtn.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-color: #4CAF50;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
        );
        recordToggleBtn.setOnMouseEntered(e -> {
            if (!isRecording) {
                recordToggleBtn.setStyle(
                    "-fx-font-size: 16px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-color: #45a049;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 8;" +
                    "-fx-cursor: hand;"
                );
            }
        });
        recordToggleBtn.setOnMouseExited(e -> {
            if (!isRecording) {
                recordToggleBtn.setStyle(
                    "-fx-font-size: 16px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-color: #4CAF50;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 8;" +
                    "-fx-cursor: hand;"
                );
            }
        });
        recordToggleBtn.setOnAction(e -> toggleRecording());

        // 辅助按钮行
        HBox auxButtonsBox = new HBox(10);
        auxButtonsBox.setAlignment(Pos.CENTER);

        Button phaseAlignBtn = new Button("🔄 相位对齐");
        phaseAlignBtn.setPrefWidth(150);
        phaseAlignBtn.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-color: #2196F3;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 5;" +
            "-fx-padding: 10 20;" +
            "-fx-cursor: hand;"
        );
        phaseAlignBtn.setOnAction(e -> doPhaseAlign());

        Button retakeBtn = new Button("↩️ 重测");
        retakeBtn.setPrefWidth(150);
        retakeBtn.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-color: #FF9800;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 5;" +
            "-fx-padding: 10 20;" +
            "-fx-cursor: hand;"
        );
        retakeBtn.setOnAction(e -> retakeLastEpisode());

        auxButtonsBox.getChildren().addAll(phaseAlignBtn, retakeBtn);

        // 提示文本
        VBox hintsBox = new VBox(5);
        hintsBox.setAlignment(Pos.CENTER);

        Label hintLabel = new Label("💡 快捷键：Enter键 开始/停止录制");
        hintLabel.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-text-fill: #7f8c8d;" +
            "-fx-font-style: italic;"
        );

        Label infoHintLabel = new Label("⚠️ 建议：录制前请先填写测试者信息");
        infoHintLabel.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-text-fill: #e67e22;" +  // 橙色
            "-fx-font-style: italic;"
        );

        hintsBox.getChildren().addAll(hintLabel, infoHintLabel);

        panel.getChildren().addAll(title, recordToggleBtn, auxButtonsBox, hintsBox);
        return panel;
    }

    /**
     * 创建测试者信息面板
     */
    private VBox createSubjectInfoPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(15));
        panel.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);"
        );

        // 标题栏
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("👤 测试者信息");
        title.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2c3e50;"
        );

        titleBox.getChildren().add(title);

        // 第一行：姓名、年龄、性别
        HBox row1 = new HBox(15);
        row1.setAlignment(Pos.CENTER_LEFT);

        // 姓名
        VBox nameBox = new VBox(5);
        Label nameLabel = new Label("姓名:");
        nameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        subjectNameField = new TextField();
        subjectNameField.setPromptText("请输入姓名");
        subjectNameField.setPrefWidth(150);
        subjectNameField.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-radius: 5;" +
            "-fx-border-radius: 5;"
        );
        // 添加失去焦点时自动保存
        subjectNameField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // 失去焦点时
                autoSaveSubjectInfo();
            }
        });
        nameBox.getChildren().addAll(nameLabel, subjectNameField);

        // 年龄
        VBox ageBox = new VBox(5);
        Label ageLabel = new Label("年龄:");
        ageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        subjectAgeField = new TextField();
        subjectAgeField.setPromptText("岁");
        subjectAgeField.setPrefWidth(80);
        subjectAgeField.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-radius: 5;" +
            "-fx-border-radius: 5;"
        );
        // 只允许输入数字
        subjectAgeField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                subjectAgeField.setText(oldVal);
            }
        });
        // 添加失去焦点时自动保存
        subjectAgeField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // 失去焦点时
                autoSaveSubjectInfo();
            }
        });
        ageBox.getChildren().addAll(ageLabel, subjectAgeField);

        // 性别
        VBox genderBox = new VBox(5);
        Label genderLabel = new Label("性别:");
        genderLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        subjectGenderBox = new ComboBox<>();
        subjectGenderBox.getItems().addAll("男", "女", "其他");
        subjectGenderBox.setValue("男");
        subjectGenderBox.setPrefWidth(100);
        subjectGenderBox.setStyle("-fx-font-size: 12px;");
        // 添加值改变时自动保存
        subjectGenderBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            autoSaveSubjectInfo();
        });
        genderBox.getChildren().addAll(genderLabel, subjectGenderBox);

        row1.getChildren().addAll(nameBox, ageBox, genderBox);

        // 第二行：体重、身高、BMI
        HBox row2 = new HBox(15);
        row2.setAlignment(Pos.CENTER_LEFT);

        // 体重
        VBox weightBox = new VBox(5);
        Label weightLabel = new Label("体重 (kg):");
        weightLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        subjectWeightField = new TextField();
        subjectWeightField.setPromptText("千克");
        subjectWeightField.setPrefWidth(100);
        subjectWeightField.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-radius: 5;" +
            "-fx-border-radius: 5;"
        );
        // 只允许输入数字和小数点
        subjectWeightField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*\\.?\\d*")) {
                subjectWeightField.setText(oldVal);
            } else {
                updateBMI(); // 自动计算BMI
            }
        });
        // 添加失去焦点时自动保存
        subjectWeightField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // 失去焦点时
                autoSaveSubjectInfo();
            }
        });
        weightBox.getChildren().addAll(weightLabel, subjectWeightField);

        // 身高
        VBox heightBox = new VBox(5);
        Label heightLabel = new Label("身高 (cm):");
        heightLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        subjectHeightField = new TextField();
        subjectHeightField.setPromptText("厘米");
        subjectHeightField.setPrefWidth(100);
        subjectHeightField.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-radius: 5;" +
            "-fx-border-radius: 5;"
        );
        // 只允许输入数字和小数点
        subjectHeightField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*\\.?\\d*")) {
                subjectHeightField.setText(oldVal);
            } else {
                updateBMI(); // 自动计算BMI
            }
        });
        // 添加失去焦点时自动保存
        subjectHeightField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // 失去焦点时
                autoSaveSubjectInfo();
            }
        });
        heightBox.getChildren().addAll(heightLabel, subjectHeightField);

        // BMI显示
        VBox bmiBox = new VBox(5);
        Label bmiTitleLabel = new Label("BMI:");
        bmiTitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        subjectBMILabel = new Label("--");
        subjectBMILabel.setStyle(
            "-fx-font-size: 20px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #3498db;" +
            "-fx-background-color: #ecf0f1;" +
            "-fx-padding: 5 15;" +
            "-fx-background-radius: 5;"
        );
        bmiBox.getChildren().addAll(bmiTitleLabel, subjectBMILabel);

        row2.getChildren().addAll(weightBox, heightBox, bmiBox);

        // 第三行：操作按钮
        HBox row3 = new HBox(10);
        row3.setAlignment(Pos.CENTER_LEFT);
        row3.setPadding(new Insets(5, 0, 0, 0));

        Button clearBtn = new Button("🗑️ 清空");
        clearBtn.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-color: #95a5a6;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 5;" +
            "-fx-padding: 8 16;" +
            "-fx-cursor: hand;"
        );
        clearBtn.setOnMouseEntered(e -> clearBtn.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-color: #7f8c8d;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 5;" +
            "-fx-padding: 8 16;" +
            "-fx-cursor: hand;"
        ));
        clearBtn.setOnMouseExited(e -> clearBtn.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-color: #95a5a6;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 5;" +
            "-fx-padding: 8 16;" +
            "-fx-cursor: hand;"
        ));
        clearBtn.setOnAction(e -> clearSubjectInfo());

        row3.getChildren().addAll(clearBtn);

        // 帮助文本
        Label helpText = new Label("💡 测试者信息会自动保存到 {测试者ID}/subject_info.properties，方便后续数据分析和追溯");
        helpText.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-text-fill: #27ae60;" +  // 绿色，表示自动保存
            "-fx-font-style: italic;" +
            "-fx-font-weight: bold;"
        );

        panel.getChildren().addAll(titleBox, row1, row2, row3, helpText);
        return panel;
    }

    private VBox createExperimentDataPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(8, 0, 8, 0));
        panel.setStyle("-fx-background-color: #e8f4f8; -fx-background-radius: 5; -fx-padding: 10;");

        Label sectionTitle = new Label("📋 实验数据管理");
        sectionTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // 第一行：测试者ID、动作ID、当前回合
        HBox row1 = new HBox(15);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label subjectLabel = new Label("测试者:");
        subjectLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: black;");
        subjectIdField = new TextField(currentSubjectId);
        subjectIdField.setPrefWidth(80);
        subjectIdField.setPromptText("如: s01");
        subjectIdField.setStyle("-fx-font-size: 12px;");

        Label movementLabel = new Label("动作:");
        movementLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: black;");
        movementIdField = new TextField(currentMovementId);
        movementIdField.setPrefWidth(80);
        movementIdField.setPromptText("如: m01");
        movementIdField.setStyle("-fx-font-size: 12px;");

        Label episodeLabelText = new Label("当前回合:");
        episodeLabelText.setStyle("-fx-font-size: 12px; -fx-text-fill: black;");
        episodeLabel = new Label("e" + currentEpisodeNumber);
        episodeLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e74c3c; " +
                             "-fx-background-color: white; -fx-padding: 3 10; -fx-background-radius: 3;");

        row1.getChildren().addAll(subjectLabel, subjectIdField, movementLabel, movementIdField,
                                 episodeLabelText, episodeLabel);

        // 第二行：动作数、回合数、应用按钮
        HBox row2 = new HBox(15);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label maxMovementsLabel = new Label("动作数:");
        maxMovementsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: black;");
        maxMovementsField = new TextField(String.valueOf(maxMovements));
        maxMovementsField.setPrefWidth(60);
        maxMovementsField.setStyle("-fx-font-size: 12px;");

        Label maxEpisodesLabel = new Label("回合数:");
        maxEpisodesLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: black;");
        maxEpisodesField = new TextField(String.valueOf(maxEpisodes));
        maxEpisodesField.setPrefWidth(60);
        maxEpisodesField.setStyle("-fx-font-size: 12px;");

        Button applyBtn = new Button("✓ 应用");
        applyBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #2980b9; " +
                        "-fx-text-fill: white; -fx-background-radius: 3; -fx-padding: 4 12; -fx-cursor: hand;");
        applyBtn.setOnAction(e -> applyExperimentData());

        row2.getChildren().addAll(maxMovementsLabel, maxMovementsField, maxEpisodesLabel, maxEpisodesField, applyBtn);

        // 第三行：快捷按钮
        HBox row3 = new HBox(8);
        row3.setAlignment(Pos.CENTER_LEFT);

        Button retakeBtn = new Button("↻ 重测");
        retakeBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #d35400; " +
                         "-fx-text-fill: white; -fx-background-radius: 3; -fx-padding: 4 12; -fx-cursor: hand;");
        retakeBtn.setOnAction(e -> retakeLastEpisode());

        Button resetEpisodeBtn = new Button("⟲ 重置回合");
        resetEpisodeBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #7f8c8d; " +
                               "-fx-text-fill: white; -fx-background-radius: 3; -fx-padding: 4 12; -fx-cursor: hand;");
        resetEpisodeBtn.setOnAction(e -> resetEpisode());

        Button nextSubjectBtn = new Button("→ 下一测试者");
        nextSubjectBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #27ae60; " +
                              "-fx-text-fill: white; -fx-background-radius: 3; -fx-padding: 4 12; -fx-cursor: hand;");
        nextSubjectBtn.setOnAction(e -> nextSubject());

        row3.getChildren().addAll(retakeBtn, resetEpisodeBtn, nextSubjectBtn);

        Label helpText = new Label("💡 录制完成后自动递增。重测会回退到上一回合并覆盖原有数据。快捷键: Enter=启停录制");
        helpText.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        panel.getChildren().addAll(sectionTitle, row1, row2, row3, helpText);
        return panel;
    }

    /**
     * 创建视频参数配置面板
     */
    private VBox createVideoParamsPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));
        panel.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);"
        );

        Label title = new Label("🎥 视频参数");
        title.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2c3e50;"
        );

        // 分辨率和帧率在同一行
        HBox paramsRow = new HBox(15);
        paramsRow.setAlignment(Pos.CENTER_LEFT);

        // 分辨率选择
        VBox resolutionBox = new VBox(5);
        Label resLabel = new Label("分辨率:");
        resLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        resolutionComboBox = new ComboBox<>();
        resolutionComboBox.getItems().addAll(
                "1920x1080 (FHD)",
                "1280x720 (HD)",
                "640x480 (SD)"
        );
        resolutionComboBox.setValue("1280x720 (HD)");
        resolutionComboBox.setPrefWidth(220);
        resolutionComboBox.setStyle("-fx-font-size: 12px;");
        resolutionComboBox.setOnAction(e -> updateResolution());
        resolutionBox.getChildren().addAll(resLabel, resolutionComboBox);

        // 帧率选择
        VBox fpsBox = new VBox(5);
        Label fpsLabel = new Label("帧率:");
        fpsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        fpsComboBox = new ComboBox<>();
        fpsComboBox.getItems().addAll("15 fps", "24 fps", "30 fps", "60 fps", "120 fps");
        fpsComboBox.setValue("30 fps");
        fpsComboBox.setPrefWidth(120);
        fpsComboBox.setStyle("-fx-font-size: 12px;");
        fpsComboBox.setOnAction(e -> updateFps());
        fpsBox.getChildren().addAll(fpsLabel, fpsComboBox);

        paramsRow.getChildren().addAll(resolutionBox, fpsBox);

        panel.getChildren().addAll(title, paramsRow);
        return panel;
    }

    private void updateResolution() {
        String selected = resolutionComboBox.getValue();
        if (selected.startsWith("1920x1080")) {
            currentWidth = 1920;
            currentHeight = 1080;
        } else if (selected.startsWith("1280x720")) {
            currentWidth = 1280;
            currentHeight = 720;
        } else if (selected.startsWith("640x480")) {
            currentWidth = 640;
            currentHeight = 480;
        }
        updateStatusBar(String.format("分辨率已设置为: %dx%d", currentWidth, currentHeight));
        logger.info("分辨率已设置为: {}x{}", currentWidth, currentHeight);
    }

    private void updateFps() {
        String selected = fpsComboBox.getValue();
        currentFps = Integer.parseInt(selected.split(" ")[0]);
        updateStatusBar(String.format("帧率已设置为: %d fps", currentFps));
        logger.info("帧率已设置为: {} fps", currentFps);
    }

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private VBox createFileReceivePanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(15));
        panel.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);"
        );

        // 标题栏
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("📦 文件接收");
        title.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2c3e50;"
        );

        Button openDirBtn = new Button("📁 打开归档目录");
        openDirBtn.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-background-color: #3498db;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 5;" +
            "-fx-padding: 5 10;" +
            "-fx-cursor: hand;"
        );
        openDirBtn.setOnAction(e -> openArchiveDirectory());

        header.getChildren().addAll(title, openDirBtn);

        // 归档目录设置
        VBox archiveDirBox = new VBox(5);
        Label dirLabel = new Label("归档目录:");
        dirLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");

        // 初始化默认归档目录
        currentArchiveDir = System.getProperty("user.home") +
                File.separator + SyncConstants.DEFAULT_ARCHIVE_DIR;

        HBox dirInputBox = new HBox(10);
        archiveDirField = new TextField(currentArchiveDir);
        archiveDirField.setEditable(false);
        archiveDirField.setStyle("-fx-font-size: 11px;");
        HBox.setHgrow(archiveDirField, Priority.ALWAYS);

        Button chooseDirBtn = new Button("选择目录...");
        chooseDirBtn.setStyle("-fx-font-size: 11px;");
        chooseDirBtn.setOnAction(e -> chooseArchiveDirectory());

        dirInputBox.getChildren().addAll(archiveDirField, chooseDirBtn);
        archiveDirBox.getChildren().addAll(dirLabel, dirInputBox);

        // 文件列表
        Label filesLabel = new Label("已接收文件:");
        filesLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");

        receivedFilesListView = new ListView<>();
        receivedFilesListView.setStyle("-fx-font-size: 11px;");
        receivedFilesListView.setPlaceholder(new Label("暂无接收的文件"));
        receivedFilesListView.setPrefHeight(300);
        receivedFilesListView.setMinHeight(200);
        receivedFilesListView.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(receivedFilesListView, Priority.ALWAYS);

        uploadProgressBar = new ProgressBar(0);
        uploadProgressBar.setPrefWidth(Double.MAX_VALUE);

        uploadStatusLabel = new Label("文件接收: 空闲");
        uploadStatusLabel.setStyle("-fx-font-size: 11px;");

        panel.getChildren().addAll(header, archiveDirBox, filesLabel, receivedFilesListView, uploadProgressBar, uploadStatusLabel);
        return panel;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setStyle("-fx-background-color: #e0e0e0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        statusBarLabel = new Label("就绪");
        statusBarLabel.setStyle("-fx-font-size: 11px;");

        statusBar.getChildren().add(statusBarLabel);
        return statusBar;
    }

    private void chooseArchiveDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择归档目录");

        File currentDir = new File(currentArchiveDir);
        if (currentDir.exists()) {
            chooser.setInitialDirectory(currentDir.getParentFile());
        }

        File selectedDir = chooser.showDialog(archiveDirField.getScene().getWindow());
        if (selectedDir != null) {
            currentArchiveDir = selectedDir.getAbsolutePath();
            archiveDirField.setText(currentArchiveDir);

            // 重启文件接收服务以使用新目录
            if (fileServer != null) {
                fileServer.stop();
                try {
                    fileServer = new FileReceiveServer(currentArchiveDir);
                    fileServer.start(createFileReceiveListener());
                    updateStatusBarSuccess("归档目录已更新: " + currentArchiveDir);
                    logger.info("归档目录已更新: {}", currentArchiveDir);
                } catch (IOException e) {
                    logger.error("重启文件服务失败", e);
                    showError("更新归档目录失败", e.getMessage());
                }
            }
        }
    }

    private void startServices() {
        new Thread(() -> {
            try {
                // 1. 启动服务发现
                discoveryService = new LeaderDiscoveryService();
                discoveryService.start();

                String leaderIP = discoveryService.getLeaderIP();
                boolean isHotspot = discoveryService.isHotspotMode();
                String networkMode = discoveryService.getNetworkModeDescription();

                Platform.runLater(() -> {
                    if (isHotspot) {
                        // 热点模式 - 显示醒目的提示
                        ipLabel.setText(String.format("🔥 Leader地址: %s:%d [热点模式]",
                                leaderIP, SyncConstants.RPC_PORT));
                        ipLabel.setStyle(
                            "-fx-font-size: 16px;" +
                            "-fx-font-weight: bold;" +
                            "-fx-text-fill: #ffeb3b;"  // 黄色高亮
                        );
                        statusLabel.setText("状态: " + networkMode);
                    } else {
                        // WiFi局域网模式
                        ipLabel.setText(String.format("Leader地址: %s:%d (自动发现已启用)",
                                leaderIP, SyncConstants.RPC_PORT));
                        statusLabel.setText("状态: 服务发现已启动 - " + networkMode);
                    }
                });

                // 2. 启动同步服务
                syncLeader = new SoftwareSyncLeader(null, this::handleRpcCallback);

                Platform.runLater(() ->
                        statusLabel.setText("状态: 同步服务已启动，等待客户端连接")
                );

                // 3. 启动文件接收服务
                fileServer = new FileReceiveServer(currentArchiveDir);
                fileServer.start(createFileReceiveListener());

                Platform.runLater(() -> {
                    if (isHotspot) {
                        statusLabel.setText("状态: 🔥 热点模式就绪 - Client请连接此热点后启动");
                    } else {
                        statusLabel.setText("状态: 所有服务已启动，系统就绪 ✅");
                    }
                });

                // 4. 启动客户端列表更新
                startClientListUpdater();

                logger.info("✅ Leader所有服务已启动 (网络模式: {})", networkMode);

            } catch (IOException e) {
                logger.error("服务启动失败", e);
                Platform.runLater(() ->
                        showError("服务启动失败", e.getMessage())
                );
            }
        }).start();
    }

    private void handleRpcCallback(int method, String payload, InetAddress fromAddress) {
        logger.debug("收到RPC回调: method={}, payload={}, from={}", method, payload, fromAddress.getHostAddress());

        if (method == SyncConstants.METHOD_CLIENT_STATUS) {
            // 处理客户端状态上报: deviceName|cameraStatus|synced
            try {
                String[] parts = payload.split("\\|");
                if (parts.length >= 3) {
                    String deviceName = parts[0];
                    int cameraStatus = Integer.parseInt(parts[1]);
                    boolean synced = Boolean.parseBoolean(parts[2]);
                    clientCameraStatus.put(deviceName, cameraStatus);
                    clientSyncStatus.put(deviceName, synced);
                    logger.trace("客户端状态更新: {} -> 摄像头:{}, 同步:{}", deviceName, cameraStatus, synced);
                } else if (parts.length >= 2) {
                    // 兼容旧格式
                    String deviceName = parts[0];
                    int cameraStatus = Integer.parseInt(parts[1]);
                    clientCameraStatus.put(deviceName, cameraStatus);
                    logger.debug("客户端状态更新(旧格式): {} -> {}", deviceName, cameraStatus);
                }
            } catch (Exception e) {
                logger.error("解析客户端状态失败: {}", payload, e);
            }
        }
    }

    private FileReceiveServer.FileReceiveListener createFileReceiveListener() {
        return new FileReceiveServer.FileReceiveListener() {
            public void onFileReceiveStarted(String fileName, String deviceName) {
                Platform.runLater(() -> {
                    uploadStatusLabel.setText(
                            String.format("正在接收: %s (来自 %s)", fileName, deviceName)
                    );
                    uploadProgressBar.setProgress(0);
                });
            }

            public void onFileReceiveProgress(String fileName, long bytesReceived, long totalBytes) {
                Platform.runLater(() -> {
                    double progress = (double) bytesReceived / totalBytes;
                    uploadProgressBar.setProgress(progress);

                    uploadStatusLabel.setText(
                            String.format("正在接收: %s - %.1f%% (%.2f/%.2f MB)",
                                    fileName,
                                    progress * 100,
                                    bytesReceived / 1024.0 / 1024.0,
                                    totalBytes / 1024.0 / 1024.0)
                    );
                });
            }

            public void onFileReceiveCompleted(String fileName, String savedPath) {
                Platform.runLater(() -> {
                    receivedFilesListView.getItems().add(0,
                            String.format("✅ %s → %s", fileName, savedPath)
                    );
                    uploadProgressBar.setProgress(1.0);
                    uploadStatusLabel.setText("文件接收: 空闲");

                    // 使用状态栏显示，不再弹出提示
                    updateStatusBarSuccess("文件接收完成: " + fileName);
                });
            }

            public void onFileReceiveFailed(String fileName, String error) {
                Platform.runLater(() -> {
                    receivedFilesListView.getItems().add(0,
                            String.format("❌ %s - 失败: %s", fileName, error)
                    );
                    uploadProgressBar.setProgress(0);
                    uploadStatusLabel.setText("文件接收: 空闲");

                    // 错误仍然弹出对话框
                    showError("文件接收失败", fileName + " - " + error);
                });
            }
        };
    }

    private void startClientListUpdater() {
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(
                        javafx.util.Duration.seconds(1),
                        e -> updateClientList()
                )
        );
        timeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        timeline.play();
    }

    private void updateClientList() {
        if (syncLeader != null) {
            var clients = syncLeader.getClients();
            Platform.runLater(() -> {
                clientListView.getItems().clear();
                clients.forEach((addr, info) -> {
                    // 获取SNTP时钟同步状态
                    Boolean synced = clientSyncStatus.get(info.name());
                    String syncStatus;
                    if (synced != null && synced) {
                        syncStatus = "✅同步";  // 已同步 - 绿色勾
                    } else {
                        syncStatus = "⏳同步中";  // 同步中 - 显眼的等待状态
                    }

                    // 获取摄像头状态
                    Integer camStatus = clientCameraStatus.get(info.name());
                    String camStatusText;
                    if (camStatus == null) {
                        camStatusText = "❓未知";
                    } else if (camStatus == SyncConstants.CLIENT_STATUS_RECORDING) {
                        camStatusText = "🔴录制中";
                    } else if (camStatus == SyncConstants.CLIENT_STATUS_CAMERA_READY) {
                        camStatusText = "📷就绪";
                    } else {
                        camStatusText = "⚫未就绪";
                    }

                    // 格式: [同步状态] [摄像头状态] 设备名 (IP)
                    clientListView.getItems().add(
                            String.format("[%s] [%s] %s (%s)",
                                    syncStatus,
                                    camStatusText,
                                    info.name(),
                                    addr.getHostAddress())
                    );
                });

                int count = clients.size();
                clientCountLabel.setText(String.format("(%d/10台)", count));
                clientCount.set(count);
            });
        }
    }

    private void generateBatchId() {
        currentBatchId = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        logger.info("生成新批次ID: ", currentBatchId);
    }

    private void startRecording() {
        if (syncLeader != null) {
            if (clientCount.get() == 0) {
                showWarning("没有连接的客户端");
                return;
            }

            // 检查是否填写了测试者信息（姓名是最基本的必填项）
            String name = subjectNameField.getText().trim();
            if (name.isEmpty() && !skipSubjectInfoWarning) {
                // 显示确认对话框
                Alert confirmDialog = new Alert(Alert.AlertType.WARNING);
                confirmDialog.setTitle("测试者信息提醒");
                confirmDialog.setHeaderText("未填写测试者信息");
                confirmDialog.setContentText(
                    "检测到未填写测试者基本信息（姓名、年龄、体重等）。\n\n" +
                    "建议填写测试者信息，以便：\n" +
                    "• 后期数据分析和追溯\n" +
                    "• 自动生成完整的实验报告\n" +
                    "• 关联测试者的多次录制数据\n\n" +
                    "是否继续录制？"
                );

                // 添加复选框："不再提示"
                CheckBox dontAskAgain = new CheckBox("本次会话不再提示");
                dontAskAgain.setStyle("-fx-font-size: 12px;");

                VBox dialogContent = new VBox(10);
                dialogContent.getChildren().addAll(
                    new Label(confirmDialog.getContentText()),
                    new Separator(),
                    dontAskAgain
                );
                confirmDialog.getDialogPane().setContent(dialogContent);

                ButtonType continueBtn = new ButtonType("继续录制", ButtonBar.ButtonData.OK_DONE);
                ButtonType cancelBtn = new ButtonType("取消，先填写信息", ButtonBar.ButtonData.CANCEL_CLOSE);
                confirmDialog.getButtonTypes().setAll(continueBtn, cancelBtn);

                // 设置默认按钮为"取消"（引导用户填写信息）
                Button defaultButton = (Button) confirmDialog.getDialogPane().lookupButton(cancelBtn);
                defaultButton.setDefaultButton(true);

                confirmDialog.showAndWait().ifPresent(response -> {
                    if (response == continueBtn) {
                        // 用户选择继续录制
                        if (dontAskAgain.isSelected()) {
                            skipSubjectInfoWarning = true;
                            logger.info("用户选择本次会话不再提示测试者信息警告");
                        }
                        // 继续执行录制逻辑
                        doStartRecording();
                    } else {
                        // 用户选择取消，不执行录制
                        updateStatusBar("已取消录制，请先填写测试者信息");
                        logger.info("用户取消录制，选择先填写测试者信息");
                    }
                });

                return; // 等待用户选择后再继续
            }

            // 如果已填写信息或用户选择跳过警告，直接开始录制
            doStartRecording();
        }
    }

    /**
     * 执行实际的录制启动逻辑
     */
    private void doStartRecording() {
        if (syncLeader == null) {
            return;
        }

        var clients = syncLeader.getClients();
        java.util.List<String> notSyncedClients = new java.util.ArrayList<>();
        java.util.List<String> cameraNotReadyClients = new java.util.ArrayList<>();

        for (var entry : clients.entrySet()) {
            String clientName = entry.getValue().name();

            // 检查SNTP同步状态
            Boolean synced = clientSyncStatus.get(clientName);
            if (synced == null || !synced) {
                notSyncedClients.add(clientName);
            }

            // 检查摄像头状态
            Integer camStatus = clientCameraStatus.get(clientName);
            if (camStatus == null || camStatus == SyncConstants.CLIENT_STATUS_CAMERA_NOT_READY) {
                cameraNotReadyClients.add(clientName);
            }
        }

        // 强制检查：时钟同步必须完成
        if (!notSyncedClients.isEmpty()) {
            String message = "以下客户端时钟同步未完成：\n" + String.join(", ", notSyncedClients) +
                            "\n\n请等待所有客户端完成时钟同步后再开始录制。\n（需要约30秒收集同步样本）";
            Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
            alert.setTitle("无法开始录制");
            alert.setHeaderText("⏳ 时钟同步未完成");
            alert.showAndWait();
            updateStatusBar("录制已取消 - 请等待所有客户端完成时钟同步");
            return;
        }

        // 强制检查：摄像头必须就绪
        if (!cameraNotReadyClients.isEmpty()) {
            String message = "以下客户端摄像头未就绪：\n" + String.join(", ", cameraNotReadyClients) +
                            "\n\n请确保所有客户端的摄像头已开启。";
            Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
            alert.setTitle("无法开始录制");
            alert.setHeaderText("📷 摄像头未就绪");
            alert.showAndWait();
            updateStatusBar("录制已取消 - 请等待所有客户端摄像头就绪");
            return;
        }

        // 所有检查通过，开始录制
        logger.info("✅ 所有客户端已就绪：时钟同步完成 + 摄像头就绪，开始录制");

        // 生成新的批次ID
        generateBatchId();

        // 保存测试者信息（如果已填写）
        try {
            saveSubjectInfoForBatch();
        } catch (Exception e) {
            logger.error("保存测试者信息失败", e);
            // 继续录制，不阻塞流程
        }

        // 获取当前实验数据
        String episodeId = "e" + currentEpisodeNumber;

        // 计算触发时间：当前时间 + 200ms（给所有客户端足够的准备时间）
        long triggerTimeNs = System.nanoTime() + 200_000_000L;  // 200ms in nanoseconds

        // 构造包含触发时间、视频参数和实验数据的payload
        // 格式: triggerTimeNs|batchId|width|height|fps|subjectId|movementId|episodeId
        String payload = String.format("%d|%s|%d|%d|%d|%s|%s|%s",
            triggerTimeNs, currentBatchId, currentWidth, currentHeight, currentFps,
            currentSubjectId, currentMovementId, episodeId);

        // 广播批次ID、视频参数和实验数据给所有客户端
        syncLeader.broadcastRpc(SyncConstants.METHOD_START_RECORDING, payload);
        isRecording = true;

        // 更新按钮状态
        recordToggleBtn.setText("⏹️ 停止录制");
        recordToggleBtn.setStyle("-fx-base: #f44336;"); // 红色背景

        String retakeInfo = currentRetakeNumber > 0 ? String.format(" [重测%d]", currentRetakeNumber) : "";
        statusLabel.setText(String.format("状态: 录制中 🔴 - %s %s %s%s",
            currentSubjectId, currentMovementId, episodeId, retakeInfo));
        updateStatusBarSuccess(String.format(
            "开始录制 - 测试者:%s 动作:%s 回合:%s%s | %dx%d @ %dfps",
            currentSubjectId, currentMovementId, episodeId, retakeInfo,
            currentWidth, currentHeight, currentFps));
        logger.info("📹 广播开始录制命令 - 测试者:{}, 动作:{}, 回合:{}{}, 批次ID:{}, 参数:{}x{} @ {}fps",
            currentSubjectId, currentMovementId, episodeId, retakeInfo,
            currentBatchId, currentWidth, currentHeight, currentFps);
    }

    private void stopRecording() {
        if (syncLeader != null) {
            syncLeader.broadcastRpc(SyncConstants.METHOD_STOP_RECORDING, "0");
            isRecording = false;

            // 更新按钮状态
            recordToggleBtn.setText("🎬 开始录制");
            recordToggleBtn.setStyle("-fx-base: #4CAF50;"); // 绿色背景

            statusLabel.setText("状态: 系统就绪 ✅");

            String retakeInfo = currentRetakeNumber > 0 ? String.format("（重测%d完成）", currentRetakeNumber) : "";
            updateStatusBarSuccess("录制已停止" + retakeInfo + " - 批次ID: " + currentBatchId);
            logger.info("⏹️ 广播停止录制命令 - 批次ID: {}{}", currentBatchId, retakeInfo);
            currentBatchId = null;

            // 重测完成后，重置重测计数
            if (currentRetakeNumber > 0) {
                currentRetakeNumber = 0;
            }

            // 正常录制完成，自动递增回合号，并检查是否需要切换
            currentEpisodeNumber++;

            // 检查是否达到最大回合数
            if (currentEpisodeNumber > maxEpisodes) {
                // 达到最大回合数，切换到下一动作
                logger.info("达到回合数上限({})，切换动作", maxEpisodes);
                nextMovement();
            } else {
                // 正常递增回合号
                episodeLabel.setText("e" + currentEpisodeNumber);
                updateStatusBarSuccess(String.format("录制完成 - 回合号已自动递增至 e%d", currentEpisodeNumber));
                logger.info("回合号自动递增至: e{}", currentEpisodeNumber);
            }
        }
    }

    /**
     * 重测上一回合（录制完成后回合号会自动递增，此功能用于回退并重测，覆盖原有数据）
     */
    private void retakeLastEpisode() {
        if (isRecording) {
            showWarning("请先停止当前录制");
            return;
        }

        // 检查是否有可重测的回合（回合号必须大于1，因为完成后已经递增了）
        if (currentEpisodeNumber <= 1) {
            showWarning("当前没有已完成的回合可以重测");
            return;
        }

        // 回退到上一回合（因为录制完成后已经自动递增了）
        currentEpisodeNumber--;
        episodeLabel.setText("e" + currentEpisodeNumber);

        // 递增重测计数（仅用于日志，不影响路径）
        currentRetakeNumber++;

        updateStatusBarSuccess(String.format("准备重测回合 e%d（第%d次重测，将覆盖原有数据）",
            currentEpisodeNumber, currentRetakeNumber));
        logger.info("准备重测回合 e{}（第{}次重测，覆盖模式）", currentEpisodeNumber, currentRetakeNumber);
    }

    private void nextMovement() {
        // 从当前动作ID中提取数字并递增
        try {
            String prefix = currentMovementId.replaceAll("\\d+", "");
            String numberPart = currentMovementId.replaceAll("\\D+", "");
            int currentNum = Integer.parseInt(numberPart);
            int nextNum = currentNum + 1;

            // 检查是否达到最大动作数
            if (nextNum > maxMovements) {
                // 达到最大动作数，切换到下一测试者
                logger.info("达到动作数上限({})，切换测试者", maxMovements);
                nextSubjectAfterMovements();
            } else {
                // 正常递增动作号
                String nextMovementId = String.format("%s%02d", prefix, nextNum);
                movementIdField.setText(nextMovementId);
                currentMovementId = nextMovementId;

                // 重置回合号
                currentEpisodeNumber = 1;
                episodeLabel.setText("e" + currentEpisodeNumber);

                updateStatusBarSuccess(String.format("已切换到下一动作: %s，回合号已重置为 e1", nextMovementId));
                logger.info("自动切换到下一动作: {}, 回合号重置为 e1", nextMovementId);
            }
        } catch (Exception e) {
            logger.error("解析动作ID失败", e);
            currentEpisodeNumber = 1;
            episodeLabel.setText("e" + currentEpisodeNumber);
        }
    }

    private void nextSubjectAfterMovements() {
        // 切换到下一测试者，并重置动作和回合
        try {
            String prefix = currentSubjectId.replaceAll("\\d+", "");
            String numberPart = currentSubjectId.replaceAll("\\D+", "");
            int currentNum = Integer.parseInt(numberPart);
            int nextNum = currentNum + 1;

            String nextSubjectId = String.format("%s%02d", prefix, nextNum);
            subjectIdField.setText(nextSubjectId);
            currentSubjectId = nextSubjectId;

            // 重置动作号为01
            String movementPrefix = currentMovementId.replaceAll("\\d+", "");
            currentMovementId = String.format("%s01", movementPrefix);
            movementIdField.setText(currentMovementId);

            // 重置回合号
            currentEpisodeNumber = 1;
            episodeLabel.setText("e" + currentEpisodeNumber);

            updateStatusBarSuccess(String.format("已自动切换到下一测试者: %s，动作和回合已重置", nextSubjectId));
            logger.info("自动切换到下一测试者: {}, 动作={}, 回合=e1", nextSubjectId, currentMovementId);
        } catch (Exception e) {
            logger.error("解析测试者ID失败", e);
            currentEpisodeNumber = 1;
            episodeLabel.setText("e" + currentEpisodeNumber);
        }
    }

    // 实验数据管理方法
    private void applyExperimentData() {
        String newSubjectId = subjectIdField.getText().trim();
        String newMovementId = movementIdField.getText().trim();

        if (newSubjectId.isEmpty() || newMovementId.isEmpty()) {
            showWarning("测试者ID和动作ID不能为空");
            return;
        }

        // 更新最大值设置
        try {
            int newMaxEpisodes = Integer.parseInt(maxEpisodesField.getText().trim());
            int newMaxMovements = Integer.parseInt(maxMovementsField.getText().trim());

            if (newMaxEpisodes <= 0 || newMaxMovements <= 0) {
                showWarning("回合数和动作数必须大于0");
                return;
            }

            maxEpisodes = newMaxEpisodes;
            maxMovements = newMaxMovements;
        } catch (NumberFormatException e) {
            showWarning("回合数和动作数必须是有效数字");
            return;
        }

        // 检查是否发生变化
        boolean changed = !newSubjectId.equals(currentSubjectId) ||
                         !newMovementId.equals(currentMovementId);

        currentSubjectId = newSubjectId;
        currentMovementId = newMovementId;

        if (changed) {
            // 如果测试者或动作发生变化，重置回合号
            currentEpisodeNumber = 1;
            episodeLabel.setText("e" + currentEpisodeNumber);
            updateStatusBarSuccess(String.format("已应用实验数据 - %s %s，回合号已重置为 e1 (回合数:%d, 动作数:%d)",
                currentSubjectId, currentMovementId, maxEpisodes, maxMovements));
        } else {
            updateStatusBar(String.format("实验数据已应用 (回合数:%d, 动作数:%d)", maxEpisodes, maxMovements));
        }

        logger.info("实验数据已应用: 测试者={}, 动作={}, 回合=e{}, 回合数={}, 动作数={}",
            currentSubjectId, currentMovementId, currentEpisodeNumber, maxEpisodes, maxMovements);
    }

    private void resetEpisode() {
        currentEpisodeNumber = 1;
        currentRetakeNumber = 0;
        episodeLabel.setText("e" + currentEpisodeNumber);
        updateStatusBarSuccess("回合号已重置");
        logger.info("回合号已重置为 e1");
    }

    private void nextSubject() {
        // 从当前测试者ID中提取数字并递增
        try {
            String prefix = currentSubjectId.replaceAll("\\d+", "");
            String numberPart = currentSubjectId.replaceAll("\\D+", "");
            int currentNum = Integer.parseInt(numberPart);
            int nextNum = currentNum + 1;

            String nextSubjectId = String.format("%s%02d", prefix, nextNum);

            // 检查是否存在测试者信息文件（新路径：{测试者ID}/subject_info.properties）
            java.nio.file.Path infoFile = java.nio.file.Paths.get(
                currentArchiveDir,
                nextSubjectId,
                "subject_info.properties"
            );

            if (java.nio.file.Files.exists(infoFile)) {
                // 文件存在，询问是否加载
                Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                confirmDialog.setTitle("切换测试者");
                confirmDialog.setHeaderText("发现测试者 " + nextSubjectId + " 的信息");
                confirmDialog.setContentText(
                    "是否加载已保存的测试者信息？\n\n" +
                    "• 加载信息 - 使用保存的测试者信息\n" +
                    "• 跳过 - 清空当前输入框（避免误用上一个测试者的信息）"
                );

                ButtonType loadBtn = new ButtonType("✅ 加载信息", ButtonBar.ButtonData.YES);
                ButtonType skipBtn = new ButtonType("⏭️ 跳过并清空", ButtonBar.ButtonData.NO);
                ButtonType cancelBtn = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

                confirmDialog.getButtonTypes().setAll(loadBtn, skipBtn, cancelBtn);

                confirmDialog.showAndWait().ifPresent(response -> {
                    if (response == loadBtn) {
                        try {
                            // 加载信息文件
                            currentSubjectInfo = SubjectInfo.loadFromFile(infoFile);

                            // 填充UI字段
                            subjectNameField.setText(currentSubjectInfo.getName());
                            subjectAgeField.setText(String.valueOf(currentSubjectInfo.getAge()));
                            subjectGenderBox.setValue(currentSubjectInfo.getGender());
                            subjectWeightField.setText(String.format("%.1f", currentSubjectInfo.getWeight()));
                            subjectHeightField.setText(String.format("%.1f", currentSubjectInfo.getHeight()));
                            updateBMI();

                            // 切换ID
                            doSubjectSwitch(nextSubjectId);
                            updateStatusBarSuccess(String.format("已切换到测试者 %s 并加载信息: %s",
                                nextSubjectId, currentSubjectInfo.getName()));
                            logger.info("切换到测试者: {}, 已加载信息: {}", nextSubjectId, currentSubjectInfo.getName());
                        } catch (Exception ex) {
                            logger.error("加载测试者信息失败", ex);
                            showError("加载失败", "无法加载测试者信息: " + ex.getMessage());
                        }
                    } else if (response == skipBtn) {
                        // 仅切换ID - 清空输入框，避免误用上一个测试者的信息
                        clearSubjectInfo();
                        doSubjectSwitch(nextSubjectId);
                        updateStatusBarSuccess(String.format("已切换到测试者 %s，信息已清空", nextSubjectId));
                        logger.info("切换到测试者: {}, 信息已清空（避免误用）", nextSubjectId);
                    }
                });
            } else {
                // 文件不存在，提示录入新信息
                Alert infoDialog = new Alert(Alert.AlertType.INFORMATION);
                infoDialog.setTitle("切换测试者");
                infoDialog.setHeaderText("切换到新测试者 " + nextSubjectId);
                infoDialog.setContentText(
                    "未找到该测试者的信息文件。\n\n" +
                    "请选择操作：\n" +
                    "• 新建信息 - 清空输入框，手动填写新测试者信息（信息会自动保存）\n" +
                    "• 继续 - 清空输入框，暂不填写（可稍后填写）"
                );

                ButtonType newBtn = new ButtonType("✏️ 新建信息", ButtonBar.ButtonData.YES);
                ButtonType continueBtn = new ButtonType("▶️ 继续（清空）", ButtonBar.ButtonData.NO);
                ButtonType cancelBtn = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

                infoDialog.getButtonTypes().setAll(newBtn, continueBtn, cancelBtn);

                infoDialog.showAndWait().ifPresent(response -> {
                    if (response == newBtn) {
                        // 清空信息，准备录入
                        clearSubjectInfo();
                        doSubjectSwitch(nextSubjectId);
                        updateStatusBarSuccess(String.format("已切换到测试者 %s，请在下方填写信息（自动保存）", nextSubjectId));
                        logger.info("切换到新测试者: {}, 等待录入信息", nextSubjectId);
                    } else if (response == continueBtn) {
                        // 仅切换 - 清空输入框，避免误用上一个测试者的信息
                        clearSubjectInfo();
                        doSubjectSwitch(nextSubjectId);
                        updateStatusBarSuccess(String.format("已切换到测试者 %s，信息已清空", nextSubjectId));
                        logger.info("切换到测试者: {}, 信息已清空（避免误用）", nextSubjectId);
                    }
                });
            }

        } catch (Exception e) {
            logger.error("解析测试者ID失败", e);
            showWarning("无法自动递增测试者ID，请手动修改");
        }
    }

    /**
     * 执行测试者切换操作（内部方法）
     */
    private void doSubjectSwitch(String nextSubjectId) {
        subjectIdField.setText(nextSubjectId);
        currentSubjectId = nextSubjectId;

        // 重置回合号和重测次数
        currentEpisodeNumber = 1;
        currentRetakeNumber = 0;
        episodeLabel.setText("e" + currentEpisodeNumber);
    }

    private void doPhaseAlign() {
        if (syncLeader != null) {
            if (clientCount.get() == 0) {
                showWarning("没有连接的客户端");
                return;
            }

            syncLeader.broadcastRpc(SyncConstants.METHOD_DO_PHASE_ALIGN, "");
            updateStatusBarSuccess("已启动相位对齐过程");
            logger.info("🔄 广播相位对齐命令");
        }
    }

    private void calculatePeriod() {
        showInfo("周期计算", "周期计算功能需要在客户端实现");
    }

    private void openArchiveDirectory() {
        if (fileServer != null) {
            try {
                File archiveDir = new File(fileServer.getArchiveDirectory());
                if (!archiveDir.exists()) {
                    archiveDir.mkdirs();
                }

                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(archiveDir);
                }
            } catch (IOException e) {
                logger.error("打开归档目录失败", e);
                showError("无法打开目录", e.getMessage());
            }
        }
    }

    private void shutdown() {
        logger.info("正在关闭Leader应用...");

        if (discoveryService != null) {
            discoveryService.stop();
        }
        if (syncLeader != null) {
            syncLeader.close();
        }
        if (fileServer != null) {
            fileServer.stop();
        }

        Platform.exit();
        System.exit(0);
    }

    /**
     * 更新状态栏消息（用于非关键信息）
     */
    private void updateStatusBar(String message) {
        Platform.runLater(() -> {
            statusBarLabel.setText(message);
            statusBarLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #333333;");
        });
    }

    /**
     * 更新状态栏消息（用于成功信息，绿色显示）
     */
    private void updateStatusBarSuccess(String message) {
        Platform.runLater(() -> {
            statusBarLabel.setText("✓ " + message);
            statusBarLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: green;");
        });
    }

    /**
     * 仅用于关键信息，已废弃，改用状态栏
     * @deprecated 使用 updateStatusBar 或 updateStatusBarSuccess 替代
     */
    @Deprecated
    private void showNotification(String title, String message) {
        // 非关键信息，显示在状态栏
        updateStatusBarSuccess(message);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("警告");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 更新BMI显示
     */
    private void updateBMI() {
        try {
            String weightStr = subjectWeightField.getText().trim();
            String heightStr = subjectHeightField.getText().trim();

            if (weightStr.isEmpty() || heightStr.isEmpty()) {
                subjectBMILabel.setText("--");
                subjectBMILabel.setStyle(
                    "-fx-font-size: 20px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-text-fill: #3498db;" +
                    "-fx-background-color: #ecf0f1;" +
                    "-fx-padding: 5 15;" +
                    "-fx-background-radius: 5;"
                );
                return;
            }

            double weight = Double.parseDouble(weightStr);
            double height = Double.parseDouble(heightStr);

            if (weight > 0 && height > 0) {
                double bmi = SubjectInfo.calculateBMI(weight, height);
                String bmiText = String.format("%.1f", bmi);

                // 根据BMI值改变颜色
                String color = "#3498db"; // 默认蓝色
                if (bmi < 18.5) {
                    color = "#f39c12"; // 偏瘦 - 橙色
                } else if (bmi >= 18.5 && bmi < 24.0) {
                    color = "#27ae60"; // 正常 - 绿色
                } else if (bmi >= 24.0 && bmi < 28.0) {
                    color = "#e67e22"; // 偏胖 - 深橙色
                } else {
                    color = "#e74c3c"; // 肥胖 - 红色
                }

                subjectBMILabel.setText(bmiText);
                subjectBMILabel.setStyle(
                    "-fx-font-size: 20px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-text-fill: " + color + ";" +
                    "-fx-background-color: #ecf0f1;" +
                    "-fx-padding: 5 15;" +
                    "-fx-background-radius: 5;"
                );

                // 更新当前测试者信息
                currentSubjectInfo.setWeight(weight);
                currentSubjectInfo.setHeight(height);
            }
        } catch (NumberFormatException e) {
            // 输入无效，不更新BMI
        }
    }

    /**
     * 清空测试者信息
     */
    private void clearSubjectInfo() {
        subjectNameField.clear();
        subjectAgeField.clear();
        subjectGenderBox.setValue("男");
        subjectWeightField.clear();
        subjectHeightField.clear();
        subjectBMILabel.setText("--");
        subjectBMILabel.setStyle(
            "-fx-font-size: 20px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #3498db;" +
            "-fx-background-color: #ecf0f1;" +
            "-fx-padding: 5 15;" +
            "-fx-background-radius: 5;"
        );
        currentSubjectInfo = new SubjectInfo();
        updateStatusBarSuccess("测试者信息已清空");
    }

    /**
     * 自动保存测试者信息
     */
    private void autoSaveSubjectInfo() {
        try {
            // 获取输入值
            String name = subjectNameField.getText().trim();

            // 如果姓名为空，不保存
            if (name.isEmpty()) {
                return;
            }

            int age = subjectAgeField.getText().trim().isEmpty() ? 0 :
                     Integer.parseInt(subjectAgeField.getText().trim());
            String gender = subjectGenderBox.getValue();
            double weight = subjectWeightField.getText().trim().isEmpty() ? 0.0 :
                           Double.parseDouble(subjectWeightField.getText().trim());
            double height = subjectHeightField.getText().trim().isEmpty() ? 0.0 :
                           Double.parseDouble(subjectHeightField.getText().trim());

            // 创建测试者信息对象
            currentSubjectInfo = new SubjectInfo(name, age, gender, weight, height);

            // 保存到分层目录：{归档目录}/{测试者ID}/subject_info.properties
            java.nio.file.Path subjectDir = java.nio.file.Paths.get(
                currentArchiveDir,
                currentSubjectId
            );

            // 确保目录存在
            if (!java.nio.file.Files.exists(subjectDir)) {
                java.nio.file.Files.createDirectories(subjectDir);
            }

            java.nio.file.Path savePath = subjectDir.resolve("subject_info.properties");

            currentSubjectInfo.saveToFile(savePath);

            logger.info("测试者信息已自动保存: {} (ID: {})", currentSubjectInfo, currentSubjectId);
            updateStatusBarSuccess("测试者信息已自动保存 - " + currentSubjectId + ": " + name);

        } catch (Exception e) {
            logger.error("自动保存测试者信息失败", e);
            // 自动保存失败不弹出错误对话框，只记录日志
        }
    }

    /**
     * 为当前批次保存测试者信息
     */
    private void saveSubjectInfoForBatch() throws IOException {
        // 检查是否有填写测试者信息
        String name = subjectNameField.getText().trim();
        if (name.isEmpty()) {
            logger.info("未填写测试者信息，跳过保存");
            return;
        }

        // 获取当前所有信息
        int age = subjectAgeField.getText().trim().isEmpty() ? 0 :
                 Integer.parseInt(subjectAgeField.getText().trim());
        String gender = subjectGenderBox.getValue();
        double weight = subjectWeightField.getText().trim().isEmpty() ? 0.0 :
                       Double.parseDouble(subjectWeightField.getText().trim());
        double height = subjectHeightField.getText().trim().isEmpty() ? 0.0 :
                       Double.parseDouble(subjectHeightField.getText().trim());

        // 更新当前测试者信息
        currentSubjectInfo = new SubjectInfo(name, age, gender, weight, height);

        // 获取当前实验数据
        String episodeId = "e" + currentEpisodeNumber;

        // 创建分层目录：{archiveDir}/{测试者ID}/{动作ID}_{回合ID}/
        java.nio.file.Path trialDir = java.nio.file.Paths.get(
            currentArchiveDir,
            currentSubjectId,
            currentMovementId + "_" + episodeId
        );

        // 确保目录存在
        if (!java.nio.file.Files.exists(trialDir)) {
            java.nio.file.Files.createDirectories(trialDir);
        }

        // 保存元信息文件：meta.json
        java.nio.file.Path metaPath = trialDir.resolve("meta.json");

        // 创建JSON格式的元信息
        String metaJson = String.format(
            "{\n" +
            "  \"batch_id\": \"%s\",\n" +
            "  \"subject_id\": \"%s\",\n" +
            "  \"movement_id\": \"%s\",\n" +
            "  \"episode_id\": \"%s\",\n" +
            "  \"record_time\": \"%s\",\n" +
            "  \"fps\": %d,\n" +
            "  \"resolution\": \"%dx%d\",\n" +
            "  \"subject\": {\n" +
            "    \"name\": \"%s\",\n" +
            "    \"age\": %d,\n" +
            "    \"gender\": \"%s\",\n" +
            "    \"weight\": %.1f,\n" +
            "    \"height\": %.1f,\n" +
            "    \"bmi\": %.2f\n" +
            "  }\n" +
            "}",
            currentBatchId, currentSubjectId, currentMovementId, episodeId,
            currentSubjectInfo.getRecordTime(),
            currentFps, currentWidth, currentHeight,
            name, age, gender, weight, height, currentSubjectInfo.getBmi()
        );

        java.nio.file.Files.writeString(metaPath, metaJson);

        // 同时保存CSV格式用于批量数据分析
        java.nio.file.Path csvPath = java.nio.file.Paths.get(
            currentArchiveDir,
            "dataset.csv"
        );

        boolean csvExists = java.nio.file.Files.exists(csvPath);
        try (java.io.FileWriter fw = new java.io.FileWriter(csvPath.toFile(), true)) {
            // 如果是新文件，先写入表头
            if (!csvExists) {
                fw.write("subject_id,movement_id,episode_id,batch_id,record_time,name,age,gender,weight,height,bmi\n");
            }
            // 写入数据
            fw.write(String.format("%s,%s,%s,%s,%s,%s,%d,%s,%.1f,%.1f,%.2f\n",
                currentSubjectId, currentMovementId, episodeId,
                currentBatchId, currentSubjectInfo.getRecordTime(),
                name, age, gender, weight, height, currentSubjectInfo.getBmi()
            ));
        }

        logger.info("✅ 元信息已保存: {}", metaPath);
    }

    /**
     * 自定义CSS样式
     */
    private String getCustomCSS() {
        return java.net.URLEncoder.encode(
            ".button:hover { -fx-cursor: hand; }" +
            ".text-field:focused { -fx-border-color: #3498db; -fx-border-width: 2px; }" +
            ".combo-box:focused { -fx-border-color: #3498db; }",
            java.nio.charset.StandardCharsets.UTF_8
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}