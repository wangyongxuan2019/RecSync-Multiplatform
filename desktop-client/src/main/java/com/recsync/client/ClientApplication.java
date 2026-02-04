package com.recsync.client;

import com.recsync.client.camera.JavaCVCameraController;
import com.recsync.core.sync.ClientDiscoveryService;
import com.recsync.core.sync.SoftwareSyncClient;
import com.recsync.core.sync.SyncConstants;
import com.recsync.core.transfer.FileUploadClient;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ClientApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(ClientApplication.class);

    // 服务
    private ClientDiscoveryService discoveryService;
    private SoftwareSyncClient syncClient;
    private JavaCVCameraController cameraController;
    private FileUploadClient uploadClient;

    // UI组件
    private TextField deviceNameField;
    private TextField manualLeaderIPField;  // 手动输入Leader IP
    private Label connectionStatusLabel;
    private ImageView previewView;
    private Label recordingStatusLabel;
    private ComboBox<String> cameraComboBox;  // 摄像头选择下拉框
    private Button switchCameraBtn;          // 摄像头切换按钮
    private ListView<String> localFilesListView;
    private ProgressBar uploadProgressBar;
    private Label uploadStatusLabel;
    private Label statusBarLabel; // 底部状态栏

    // 状态
    private volatile boolean running = false;
    private volatile boolean isConnected = false;
    private String currentRecordingPath = null;
    private String deviceName;
    private int selectedCameraIndex = 0;    // 当前选中的摄像头索引
    private List<Integer> availableCameras; // 可用摄像头列表
    private String leaderIP = "";            // Leader IP地址

    // 当前视频参数（由Leader设置）
    private int currentVideoWidth = SyncConstants.DEFAULT_VIDEO_WIDTH;
    private int currentVideoHeight = SyncConstants.DEFAULT_VIDEO_HEIGHT;
    private int currentVideoFps = SyncConstants.DEFAULT_VIDEO_FPS;

    @Override
    public void start(Stage primaryStage) {
        // 设置全局未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("线程 {} 发生未捕获异常", thread.getName(), throwable);
            Platform.runLater(() -> {
                showError("程序异常",
                    "发生了未捕获的异常\n\n" +
                    "线程: " + thread.getName() + "\n" +
                    "错误: " + throwable.getClass().getSimpleName() + "\n" +
                    "消息: " + throwable.getMessage());
            });
        });

        // 从配置文件加载或生成设备名称
        deviceName = loadOrGenerateDeviceName();
        primaryStage.setTitle("RecSync Client - " + deviceName);

        // 使用HBox作为根布局（左右分栏）
        HBox root = new HBox(15);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #fafafa;");

        // 左侧面板：信息和控制
        VBox leftPanel = createLeftPanel();
        leftPanel.setMinWidth(400);
        leftPanel.setMaxWidth(450);

        // 右侧面板：摄像头预览（可自适应窗口大小）
        VBox rightPanel = createCameraPreview();
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        root.getChildren().addAll(leftPanel, rightPanel);

        Scene scene = new Scene(root, 1100, 750);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(650);
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();

        // 启动相机
        initCamera();

        // 自动发现并连接Leader
        Platform.runLater(this::autoDiscoverAndConnect);
    }

    /**
     * 创建左侧面板：设备信息、控制、文件管理
     */
    private VBox createLeftPanel() {
        VBox panel = new VBox(12);

        // 顶部：设备信息面板
        VBox deviceInfoPanel = createDeviceInfoPanel();

        // 中间：录制状态
        VBox statusPanel = new VBox(8);
        statusPanel.setPadding(new Insets(10, 0, 0, 0));
        recordingStatusLabel = new Label("⚫ 未录制");
        recordingStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        statusPanel.getChildren().add(recordingStatusLabel);

        // 底部：文件上传面板和状态栏
        VBox uploadPanel = createUploadPanel();
        HBox statusBar = createStatusBar();

        // 组装左侧面板
        VBox.setVgrow(uploadPanel, Priority.ALWAYS);
        panel.getChildren().addAll(
                deviceInfoPanel,
                statusPanel,
                uploadPanel,
                statusBar
        );

        return panel;
    }

    private VBox createDeviceInfoPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                      "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");

        Label title = new Label("设备信息");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        HBox deviceNameBox = new HBox(10);
        deviceNameBox.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label("设备名称:");
        nameLabel.setStyle("-fx-font-size: 12px;");
        deviceNameField = new TextField(deviceName);
        deviceNameField.setPrefWidth(200);
        deviceNameField.setStyle("-fx-font-size: 12px;");

        Button saveNameBtn = new Button("💾 保存");
        saveNameBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #3498db; " +
                           "-fx-text-fill: white; -fx-background-radius: 4;");
        saveNameBtn.setOnAction(e -> saveDeviceName());

        deviceNameBox.getChildren().addAll(nameLabel, deviceNameField, saveNameBtn);

        // 手动连接Leader IP（支持热点模式）
        HBox manualConnectBox = new HBox(10);
        manualConnectBox.setAlignment(Pos.CENTER_LEFT);

        Label ipLabel = new Label("Leader IP:");
        ipLabel.setStyle("-fx-font-size: 12px;");
        manualLeaderIPField = new TextField();
        manualLeaderIPField.setPrefWidth(140);
        manualLeaderIPField.setPromptText("自动发现或输入IP");
        manualLeaderIPField.setStyle("-fx-font-size: 12px;");

        Button connectBtn = new Button("🔗 连接");
        connectBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #27ae60; " +
                          "-fx-text-fill: white; -fx-background-radius: 4;");
        connectBtn.setOnAction(e -> manualConnect());

        Button autoDiscoverBtn = new Button("🔍 自动");
        autoDiscoverBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #9b59b6; " +
                                "-fx-text-fill: white; -fx-background-radius: 4;");
        autoDiscoverBtn.setOnAction(e -> autoDiscoverAndConnect());

        manualConnectBox.getChildren().addAll(ipLabel, manualLeaderIPField, connectBtn, autoDiscoverBtn);

        // 连接模式提示
        Label modeHint = new Label("💡 热点模式: Leader开热点后，Client连接热点可自动发现");
        modeHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        connectionStatusLabel = new Label("状态: 正在连接...");
        connectionStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");

        panel.getChildren().addAll(title, deviceNameBox, manualConnectBox, modeHint, connectionStatusLabel);
        return panel;
    }

    private VBox createCameraPreview() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                      "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");

        // 标题和摄像头选择区域
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("📹 相机预览");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        titleBox.getChildren().add(title);

        // 摄像头选择控件
        HBox cameraSelectBox = new HBox(8);
        cameraSelectBox.setAlignment(Pos.CENTER_LEFT);
        cameraSelectBox.setPadding(new Insets(0, 0, 5, 0));

        Label cameraLabel = new Label("摄像头:");
        cameraLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        cameraComboBox = new ComboBox<>();
        cameraComboBox.setPrefWidth(130);
        cameraComboBox.setPromptText("检测中...");
        cameraComboBox.setDisable(true);
        cameraComboBox.setStyle("-fx-font-size: 11px;");

        switchCameraBtn = new Button("切换");
        switchCameraBtn.setDisable(true);
        switchCameraBtn.setOnAction(e -> switchCamera());
        switchCameraBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #95a5a6; " +
                                "-fx-text-fill: white; -fx-background-radius: 4;");

        cameraSelectBox.getChildren().addAll(cameraLabel, cameraComboBox, switchCameraBtn);

        // 预览图像容器（使用StackPane支持自适应）
        StackPane previewPane = new StackPane();
        previewPane.setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 6;");
        previewPane.setMinHeight(300);
        VBox.setVgrow(previewPane, Priority.ALWAYS);

        previewView = new ImageView();
        previewView.setPreserveRatio(true);
        previewView.setSmooth(true);

        // 双向绑定预览图像大小到容器大小（修复缩小bug）
        previewPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            previewView.setFitWidth(newVal.doubleValue() - 20);
        });
        previewPane.heightProperty().addListener((obs, oldVal, newVal) -> {
            previewView.setFitHeight(newVal.doubleValue() - 20);
        });

        previewPane.getChildren().add(previewView);

        panel.getChildren().addAll(titleBox, cameraSelectBox, previewPane);

        // 后台检测可用摄像头
        new Thread(() -> {
            try {
                logger.info("开始检测可用摄像头...");
                Platform.runLater(() -> updateStatusBar("正在检测摄像头..."));

                availableCameras = JavaCVCameraController.getAvailableCameras();
                logger.info("检测到 {} 个摄像头", availableCameras.size());

                Platform.runLater(() -> {
                    // 填充摄像头下拉框
                    cameraComboBox.getItems().clear();
                    for (int index : availableCameras) {
                        cameraComboBox.getItems().add("摄像头 " + index);
                    }

                    // 智能单/多摄像头模式
                    if (availableCameras.size() == 1) {
                        // 单摄像头模式：禁用切换功能
                        cameraComboBox.getSelectionModel().selectFirst();
                        cameraComboBox.setDisable(true);
                        switchCameraBtn.setDisable(true);
                        updateStatusBar("检测完成：单摄像头模式");
                    } else if (availableCameras.size() > 1) {
                        // 多摄像头模式：启用切换功能
                        cameraComboBox.getSelectionModel().selectFirst();
                        cameraComboBox.setDisable(false);
                        switchCameraBtn.setDisable(false);
                        updateStatusBar(String.format("检测完成：找到 %d 个摄像头", availableCameras.size()));
                    } else {
                        // 检测失败
                        cameraComboBox.setPromptText("摄像头 0（自动）");
                        cameraComboBox.setDisable(true);
                        switchCameraBtn.setDisable(true);
                        updateStatusBar("摄像头检测失败，将使用默认摄像头");
                        logger.warn("未检测到任何摄像头，将尝试使用默认摄像头0");
                    }
                });

            } catch (Exception e) {
                logger.error("检测摄像头时发生异常", e);
                Platform.runLater(() -> {
                    cameraComboBox.setPromptText("检测失败");
                    cameraComboBox.setDisable(true);
                    switchCameraBtn.setDisable(true);
                    updateStatusBar("摄像头检测异常：" + e.getMessage());
                });
            } catch (Throwable t) {
                logger.error("检测摄像头时发生严重错误", t);
                Platform.runLater(() -> {
                    cameraComboBox.setPromptText("检测出错");
                    cameraComboBox.setDisable(true);
                    switchCameraBtn.setDisable(true);
                    updateStatusBar("摄像头检测严重错误");
                });
            }
        }, "Camera-Detection-Thread").start();

        return panel;
    }

    private VBox createUploadPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                      "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");

        Label title = new Label("📁 本地文件管理");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #95a5a6; " +
                          "-fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 5 12;");
        refreshBtn.setOnAction(e -> refreshLocalFiles());

        Button uploadBtn = new Button("📤 上传");
        uploadBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #3498db; " +
                         "-fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 5 12;");
        uploadBtn.setOnAction(e -> uploadSelectedFile());

        Button uploadAllBtn = new Button("📤 全部");
        uploadAllBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #2ecc71; " +
                            "-fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 5 12;");
        uploadAllBtn.setOnAction(e -> uploadAllFiles());

        Button openDirBtn = new Button("📁 目录");
        openDirBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #e67e22; " +
                          "-fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 5 12;");
        openDirBtn.setOnAction(e -> openRecordingDirectory());

        buttonBox.getChildren().addAll(refreshBtn, uploadBtn, uploadAllBtn, openDirBtn);

        localFilesListView = new ListView<>();
        localFilesListView.setPrefHeight(120);
        localFilesListView.setStyle("-fx-font-size: 11px;");
        localFilesListView.setPlaceholder(new Label("暂无录制文件"));
        VBox.setVgrow(localFilesListView, Priority.ALWAYS);

        uploadProgressBar = new ProgressBar(0);
        uploadProgressBar.setPrefWidth(Double.MAX_VALUE);
        uploadProgressBar.setStyle("-fx-accent: #3498db;");

        uploadStatusLabel = new Label("上传状态: 无");
        uploadStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        panel.getChildren().addAll(title, buttonBox, localFilesListView,
                uploadProgressBar, uploadStatusLabel);
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

    private void initCamera() {
        // 使用默认摄像头（索引0）
        initCamera(0);
    }

    private void startPreviewLoop() {
        Java2DFrameConverter converter = new Java2DFrameConverter();

        while (running) {
            try {
                Frame frame = cameraController.grabFrame();
                if (frame != null && frame.image != null) {
                    // 录制帧
                    if (cameraController.isRecording()) {
                        cameraController.recordFrame(frame);
                    }

                    // 更新预览
                    BufferedImage bufferedImage = converter.convert(frame);
                    if (bufferedImage != null) {
                        Platform.runLater(() ->
                                previewView.setImage(
                                        SwingFXUtils.toFXImage(bufferedImage, null)
                                )
                        );
                    }
                }

                Thread.sleep(33); // ~30fps
            } catch (Exception e) {
                if (running) {
                    logger.error("帧处理失败", e);
                }
            }
        }
    }

    private String loadOrGenerateDeviceName() {
        try {
            Path configFile = Paths.get(System.getProperty("user.home"), "client_config.txt");
            if (Files.exists(configFile)) {
                String savedName = Files.readString(configFile).trim();
                if (!savedName.isEmpty()) {
                    logger.info("加载设备名称: {}", savedName);
                    return savedName;
                }
            }
        } catch (IOException e) {
            logger.warn("无法加载配置文件", e);
        }

        // 生成默认设备名称
        String defaultName = "Client-" + getHostName();
        saveDeviceNameToFile(defaultName);
        return defaultName;
    }

    private void saveDeviceName() {
        String newName = deviceNameField.getText().trim();
        if (newName.isEmpty()) {
            showWarning("设备名称不能为空");
            return;
        }

        // 检查名称是否已修改
        if (newName.equals(deviceName)) {
            updateStatusBar("设备名称未改变");
            return;
        }

        String oldName = deviceName;

        // 保存到文件
        if (saveDeviceNameToFile(newName)) {
            deviceName = newName;

            // 如果已连接，通知Leader更新设备名称（无需重新连接）
            if (isConnected && syncClient != null) {
                try {
                    // 发送名称更新RPC: oldName|newName
                    String payload = oldName + "|" + newName;
                    syncClient.sendRpcToLeader(SyncConstants.METHOD_UPDATE_CLIENT_NAME, payload);
                    updateStatusBarSuccess("设备名称已更新: " + deviceName);
                    logger.info("已通知Leader更新设备名称: {} -> {}", oldName, deviceName);
                } catch (Exception e) {
                    logger.error("通知Leader更新名称失败", e);
                    showWarning("设备名称已保存，但通知Leader失败，建议重新连接");
                }
            } else {
                updateStatusBarSuccess("设备名称已保存: " + deviceName);
            }
        }
    }

    private boolean saveDeviceNameToFile(String name) {
        try {
            Path configFile = Paths.get(System.getProperty("user.home"), "client_config.txt");
            Files.writeString(configFile, name);
            logger.info("设备名称已保存: {}", name);
            return true;
        } catch (IOException e) {
            logger.error("保存设备名称失败", e);
            showError("保存失败", "无法保存设备名称: " + e.getMessage());
            return false;
        }
    }

    private void reconnectWithNewName() {
        // 断开当前连接
        if (syncClient != null) {
            syncClient.stop();
        }
        if (discoveryService != null) {
            discoveryService.stop();
        }

        isConnected = false;

        // 重新自动发现并连接
        Platform.runLater(this::autoDiscoverAndConnect);
    }

    /**
     * 手动连接到指定的Leader IP
     */
    private void manualConnect() {
        String ip = manualLeaderIPField.getText().trim();

        if (ip.isEmpty()) {
            showWarning("请输入Leader IP地址");
            return;
        }

        // 验证IP格式
        if (!ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            showWarning("IP地址格式不正确，请输入如 192.168.137.1 格式的地址");
            return;
        }

        connectionStatusLabel.setText("状态: 正在连接到 " + ip + "...");
        connectionStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #3498db;");

        // 断开之前的连接
        if (syncClient != null) {
            syncClient.stop();
            syncClient = null;
        }
        if (discoveryService != null) {
            discoveryService.stop();
        }
        isConnected = false;

        // 连接到指定IP
        connectToLeader(ip);
    }

    private void autoDiscoverAndConnect() {
        connectionStatusLabel.setText("状态: 正在自动发现Leader...");
        connectionStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");

        // 停止之前的发现服务（如果存在）
        if (discoveryService != null) {
            discoveryService.stop();
        }

        discoveryService = new ClientDiscoveryService();
        discoveryService.discoverLeader()
                .thenAccept(leaderInfo -> {
                    if (leaderInfo != null) {
                        Platform.runLater(() -> {
                            // 更新IP输入框显示发现的IP
                            manualLeaderIPField.setText(leaderInfo.ip);
                            String modeText = leaderInfo.discoveryMethod.contains("热点") ?
                                    " (热点模式)" : "";
                            connectionStatusLabel.setText("状态: 发现Leader - " + leaderInfo.ip + modeText);
                            connectToLeader(leaderInfo.ip);
                        });
                    } else {
                        Platform.runLater(() -> {
                            connectionStatusLabel.setText("状态: 未发现Leader，可尝试手动输入IP");
                            connectionStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e67e22;");
                        });
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        connectionStatusLabel.setText("状态: 发现失败 - " + ex.getMessage());
                        connectionStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e74c3c;");
                    });
                    return null;
                });
    }

    private void connectToLeader(String leaderIP) {
        new Thread(() -> {
            try {
                InetAddress leaderAddr = InetAddress.getByName(leaderIP);
                this.leaderIP = leaderIP;  // 保存Leader IP

                // Client使用独立端口，避免与Leader冲突
                syncClient = new SoftwareSyncClient(
                        leaderAddr,
                        deviceName,
                        SyncConstants.CLIENT_RPC_PORT,  // Client使用8247端口
                        this::handleRpcCallback
                );

                // 初始化上传客户端
                uploadClient = new FileUploadClient(leaderIP, deviceName);
                uploadClient.setProgressListener(createUploadProgressListener());

                isConnected = true;
                Platform.runLater(() -> {
                    connectionStatusLabel.setText(String.format("✅ 已连接到 Leader (%s)", leaderIP));
                    connectionStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
                });

                logger.info("✅ 已连接到Leader: {}", leaderIP);

            } catch (Exception e) {
                logger.error("连接Leader失败", e);
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("❌ 连接失败: " + e.getMessage());
                    connectionStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e74c3c;");
                    showError("连接失败", e.getMessage());
                });
            }
        }).start();
    }

    private void handleRpcCallback(int method, String payload, InetAddress fromAddress) {
        logger.debug("收到RPC: method={}, payload={}, from={}", method, payload, fromAddress.getHostAddress());

        switch (method) {
            case SyncConstants.METHOD_START_RECORDING:
                // payload 格式: batchId|width|height|fps|subjectId|movementId|episodeId|retakeId
                try {
                    String[] parts = payload.split("\\|");
                    if (parts.length >= 8) {
                        // 新格式：包含受试者、动作、回合、重测信息
                        String batchId = parts[0];
                        int width = Integer.parseInt(parts[1]);
                        int height = Integer.parseInt(parts[2]);
                        int fps = Integer.parseInt(parts[3]);
                        String subjectId = parts[4];
                        String movementId = parts[5];
                        String episodeId = parts[6];
                        String retakeId = parts[7];
                        Platform.runLater(() -> startRecording(batchId, width, height, fps,
                                                               subjectId, movementId, episodeId, retakeId));
                    } else if (parts.length >= 7) {
                        // 兼容格式：无重测信息
                        String batchId = parts[0];
                        int width = Integer.parseInt(parts[1]);
                        int height = Integer.parseInt(parts[2]);
                        int fps = Integer.parseInt(parts[3]);
                        String subjectId = parts[4];
                        String movementId = parts[5];
                        String episodeId = parts[6];
                        Platform.runLater(() -> startRecording(batchId, width, height, fps,
                                                               subjectId, movementId, episodeId, "r0000"));
                    } else if (parts.length == 4) {
                        // 旧格式：仅视频参数
                        String batchId = parts[0];
                        int width = Integer.parseInt(parts[1]);
                        int height = Integer.parseInt(parts[2]);
                        int fps = Integer.parseInt(parts[3]);
                        Platform.runLater(() -> startRecording(batchId, width, height, fps,
                                                               "", "", "", "r0000"));
                    } else {
                        // 兼容最旧格式（只有batchId）
                        Platform.runLater(() -> startRecording(payload,
                            SyncConstants.DEFAULT_VIDEO_WIDTH,
                            SyncConstants.DEFAULT_VIDEO_HEIGHT,
                            SyncConstants.DEFAULT_VIDEO_FPS,
                            "", "", "", "r0000"));
                    }
                } catch (Exception e) {
                    logger.error("解析录制参数失败: {}", payload, e);
                    Platform.runLater(() ->
                        showError("参数错误", "无法解析录制参数: " + e.getMessage())
                    );
                }
                break;
            case SyncConstants.METHOD_STOP_RECORDING:
                Platform.runLater(this::stopRecording);
                break;
            case SyncConstants.METHOD_DO_PHASE_ALIGN:
                logger.info("收到相位对齐命令");
                updateStatusBar("收到相位对齐命令");
                break;
            case SyncConstants.METHOD_MSG_NAME_CONFLICT:
                logger.error("设备名称冲突: {}", payload);
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("状态: 连接被拒绝 - 设备名称冲突 ❌");
                    connectionStatusLabel.setStyle("-fx-text-fill: red;");
                    showError("设备名称冲突", payload + "\n\n请修改设备名称后重试。");

                    // 断开连接
                    if (syncClient != null) {
                        syncClient.stop();
                        syncClient = null;
                    }
                    isConnected = false;
                });
                break;
            case SyncConstants.METHOD_MSG_MAX_CLIENTS_REACHED:
                logger.error("服务器已达到最大客户端数: {}", payload);
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("状态: 连接被拒绝 - 服务器已满 ❌");
                    connectionStatusLabel.setStyle("-fx-text-fill: red;");
                    showError("无法连接", payload);

                    // 断开连接
                    if (syncClient != null) {
                        syncClient.stop();
                        syncClient = null;
                    }
                    isConnected = false;
                });
                break;
        }
    }

    private void startRecording(String batchId, int width, int height, int fps,
                               String subjectId, String movementId, String episodeId, String retakeId) {
        if (cameraController.isRecording()) {
            logger.warn("已经在录制中");
            return;
        }

        new Thread(() -> {
            try {
                // 检查视频参数是否改变
                boolean paramsChanged = (width != currentVideoWidth ||
                                        height != currentVideoHeight ||
                                        fps != currentVideoFps);

                if (paramsChanged) {
                    logger.info("视频参数已改变: {}x{} @ {}fps -> {}x{} @ {}fps",
                        currentVideoWidth, currentVideoHeight, currentVideoFps,
                        width, height, fps);

                    // 更新当前参数
                    currentVideoWidth = width;
                    currentVideoHeight = height;
                    currentVideoFps = fps;

                    // 重新初始化摄像头
                    Platform.runLater(() ->
                        updateStatusBar(String.format("正在应用新视频参数: %dx%d @ %dfps...",
                            width, height, fps))
                    );

                    // 停止预览循环
                    running = false;
                    Thread.sleep(500);

                    // 停止当前摄像头
                    if (cameraController != null) {
                        cameraController.stopCamera();
                    }
                    Thread.sleep(300);

                    // 使用新参数重新初始化摄像头
                    cameraController = new JavaCVCameraController(width, height, fps);
                    cameraController.startCamera(selectedCameraIndex);
                    running = true;

                    // 重启预览循环
                    new Thread(this::startPreviewLoop, "Camera-Preview-Thread").start();

                    Platform.runLater(() ->
                        updateStatusBarSuccess(String.format("视频参数已更新: %dx%d @ %dfps",
                            width, height, fps))
                    );
                }

                // 生成时间戳
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

                // 文件命名格式:
                // 正常录制: {deviceName}_{subjectId}_{movementId}_{timestamp}_{episodeId}.mp4
                // 重测录制: {deviceName}_{subjectId}_{movementId}_{timestamp}_{episodeId}_retake{N}.mp4
                // 例如: front_s01_m01_20260114150230_e1.mp4（正式）
                // 例如: front_s01_m01_20260114150435_e1_retake1.mp4（第1次重测）
                String filename;
                if (!subjectId.isEmpty() && !movementId.isEmpty() && !episodeId.isEmpty()) {
                    // 解析重测号（r0000 -> 0, r0001 -> 1, r0002 -> 2）
                    int retakeNumber = Integer.parseInt(retakeId.substring(1));
                    if (retakeNumber == 0) {
                        // 正常录制：不添加重测标记
                        filename = String.format("%s_%s_%s_%s_%s.mp4",
                            deviceName, subjectId, movementId, timestamp, episodeId);
                    } else {
                        // 重测：添加retake标记
                        filename = String.format("%s_%s_%s_%s_%s_retake%d.mp4",
                            deviceName, subjectId, movementId, timestamp, episodeId, retakeNumber);
                    }
                } else {
                    // 回退到旧格式
                    filename = String.format("%s_%s_batch%s.mp4",
                        deviceName, timestamp, batchId.replace(":", "").replace("-", ""));
                }

                Path recSyncDir = Paths.get(System.getProperty("user.home"),
                        SyncConstants.DEFAULT_RECORDING_DIR);
                Files.createDirectories(recSyncDir);

                currentRecordingPath = recSyncDir.resolve(filename).toString();

                cameraController.startRecording(currentRecordingPath);

                String finalFilename = filename;
                boolean isRetake = !retakeId.equals("r0000");
                Platform.runLater(() -> {
                    String retakeInfo = isRetake ? String.format(" [重测%d]", Integer.parseInt(retakeId.substring(1))) : "";
                    String displayInfo = !episodeId.isEmpty() ?
                        String.format("🔴 录制中 - %s (受试者:%s 动作:%s 回合:%s%s)",
                            finalFilename, subjectId, movementId, episodeId, retakeInfo) :
                        String.format("🔴 录制中 - %s (%dx%d @ %dfps)",
                            finalFilename, width, height, fps);

                    recordingStatusLabel.setText(displayInfo);
                    recordingStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");

                    String statusMsg = !episodeId.isEmpty() ?
                        String.format("开始录制 - 受试者:%s 动作:%s 回合:%s %s",
                            subjectId, movementId, episodeId, retakeId) :
                        String.format("开始录制 - 批次: %s | %dx%d @ %dfps", batchId, width, height, fps);
                    updateStatusBar(statusMsg);
                });

                logger.info("🎬 开始录制: {} (参数: {}x{} @ {}fps, 受试者:{}, 动作:{}, 回合:{}, 重测:{})",
                    currentRecordingPath, width, height, fps, subjectId, movementId, episodeId, retakeId);

            } catch (Exception e) {
                logger.error("开始录制失败", e);
                Platform.runLater(() ->
                        showError("录制失败", e.getMessage())
                );
            }
        }).start();
    }

    private void stopRecording() {
        if (!cameraController.isRecording()) {
            logger.warn("当前未在录制");
            return;
        }

        new Thread(() -> {
            try {
                cameraController.stopRecording();

                Platform.runLater(() -> {
                    recordingStatusLabel.setText("⚫ 未录制");
                    recordingStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d; -fx-font-weight: normal;");
                    refreshLocalFiles();
                    updateStatusBarSuccess("录制完成 - 视频已保存");
                });

                logger.info("⏹️ 录制已停止: {}", currentRecordingPath);
                currentRecordingPath = null;

            } catch (Exception e) {
                logger.error("停止录制失败", e);
                Platform.runLater(() ->
                        showError("停止录制失败", e.getMessage())
                );
            }
        }).start();
    }

    private void refreshLocalFiles() {
        localFilesListView.getItems().clear();

        Path recSyncDir = Paths.get(System.getProperty("user.home"),
                SyncConstants.DEFAULT_RECORDING_DIR);
        if (!Files.exists(recSyncDir)) {
            return;
        }

        try {
            Files.list(recSyncDir)
                    .filter(p -> p.toString().endsWith(".mp4"))
                    .sorted((p1, p2) -> {
                        try {
                            return Files.getLastModifiedTime(p2)
                                    .compareTo(Files.getLastModifiedTime(p1));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .forEach(path -> {
                        try {
                            long size = Files.size(path);
                            String sizeStr = String.format("%.2f MB", size / 1024.0 / 1024.0);
                            localFilesListView.getItems().add(
                                    String.format("%s (%s)", path.getFileName(), sizeStr)
                            );
                        } catch (IOException e) {
                            logger.error("读取文件信息失败", e);
                        }
                    });
        } catch (IOException e) {
            logger.error("列出文件失败", e);
        }
    }

    private void uploadSelectedFile() {
        if (!isConnected) {
            showWarning("请先连接到Leader");
            return;
        }

        String selected = localFilesListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("请先选择要上传的文件");
            return;
        }

        String fileName = selected.split(" \\(")[0];
        Path filePath = Paths.get(System.getProperty("user.home"),
                SyncConstants.DEFAULT_RECORDING_DIR,
                fileName);

        new Thread(() -> uploadClient.uploadFile(filePath)).start();
    }

    private void uploadAllFiles() {
        if (!isConnected) {
            showWarning("请先连接到Leader");
            return;
        }

        if (localFilesListView.getItems().isEmpty()) {
            showWarning("没有可上传的文件");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认上传");
        alert.setHeaderText("上传所有文件");
        alert.setContentText(
                String.format("确定要上传 %d 个文件吗？上传成功后将删除本地副本。",
                        localFilesListView.getItems().size())
        );

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                uploadAllFilesSequentially();
            }
        });
    }

    private void uploadAllFilesSequentially() {
        new Thread(() -> {
            Path recSyncDir = Paths.get(System.getProperty("user.home"),
                    SyncConstants.DEFAULT_RECORDING_DIR);
            try {
                Files.list(recSyncDir)
                        .filter(p -> p.toString().endsWith(".mp4"))
                        .forEach(path -> {
                            uploadClient.uploadFile(path);
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                logger.error("Sleep interrupted", e);
                            }
                        });

                Platform.runLater(() ->
                        updateStatusBarSuccess("批量上传完成 - 所有文件已上传到Leader")
                );

            } catch (IOException e) {
                logger.error("批量上传失败", e);
            }
        }).start();
    }

    private void openRecordingDirectory() {
        try {
            Path recSyncDir = Paths.get(System.getProperty("user.home"),
                    SyncConstants.DEFAULT_RECORDING_DIR);
            Files.createDirectories(recSyncDir);

            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(recSyncDir.toFile());
            }
        } catch (IOException e) {
            logger.error("打开目录失败", e);
            showError("无法打开目录", e.getMessage());
        }
    }

    private FileUploadClient.UploadProgressListener createUploadProgressListener() {
        return new FileUploadClient.UploadProgressListener() {
            public void onUploadStarted(String fileName) {
                Platform.runLater(() -> {
                    uploadStatusLabel.setText("正在上传: " + fileName);
                    uploadProgressBar.setProgress(0);
                });
            }

            public void onUploadProgress(long bytesUploaded, long totalBytes, double percentage) {
                Platform.runLater(() -> {
                    uploadProgressBar.setProgress(percentage / 100.0);
                    uploadStatusLabel.setText(
                            String.format("正在上传: %.1f%% (%.2f/%.2f MB)",
                                    percentage,
                                    bytesUploaded / 1024.0 / 1024.0,
                                    totalBytes / 1024.0 / 1024.0)
                    );
                });
            }

            public void onUploadCompleted(String fileName) {
                Platform.runLater(() -> {
                    uploadProgressBar.setProgress(1.0);
                    uploadStatusLabel.setText("上传完成: " + fileName);
                    refreshLocalFiles();
                    updateStatusBarSuccess("上传成功 - " + fileName + " 已上传并删除本地副本");
                });
            }

            public void onUploadFailed(String fileName, String error) {
                Platform.runLater(() -> {
                    uploadProgressBar.setProgress(0);
                    uploadStatusLabel.setText("上传失败: " + error);
                    showError("上传失败", fileName + " - " + error);
                });
            }
        };
    }

    private void shutdown() {
        logger.info("正在关闭Client应用...");
        running = false;

        try {
            if (cameraController != null) {
                if (cameraController.isRecording()) {
                    cameraController.stopRecording();
                }
                cameraController.stopCamera();
            }
        } catch (Exception e) {
            logger.error("关闭相机失败", e);
        }

        if (syncClient != null) {
            syncClient.stop();
        }
        if (discoveryService != null) {
            discoveryService.stop();
        }

        Platform.exit();
        System.exit(0);
    }

    /**
     * 切换摄像头
     */
    private void switchCamera() {
        // 检查是否正在录制
        if (cameraController != null && cameraController.isRecording()) {
            showWarning("请先停止录制，然后再切换摄像头");
            return;
        }

        // 获取选中的摄像头索引
        int selectedIndex = cameraComboBox.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= availableCameras.size()) {
            showWarning("请先选择要切换的摄像头");
            return;
        }

        int newCameraIndex = availableCameras.get(selectedIndex);

        // 检查是否与当前摄像头相同
        if (newCameraIndex == selectedCameraIndex) {
            updateStatusBar("已经在使用摄像头 " + newCameraIndex);
            return;
        }

        // 切换摄像头
        new Thread(() -> {
            try {
                Platform.runLater(() -> updateStatusBar("正在切换到摄像头 " + newCameraIndex + "..."));

                // 1. 停止预览循环
                running = false;

                // 2. 等待预览循环完全结束 (增加到500ms)
                Thread.sleep(500);

                // 3. 停止当前摄像头
                if (cameraController != null) {
                    cameraController.stopCamera();
                }

                // 4. 等待资源完全释放 (增加到300ms)
                Thread.sleep(300);

                // 5. 清空引用
                cameraController = null;

                // 6. 启动新摄像头
                selectedCameraIndex = newCameraIndex;
                initCamera(newCameraIndex);

                Platform.runLater(() -> updateStatusBarSuccess("已切换到摄像头 " + newCameraIndex));
                logger.info("✅ 已切换到摄像头: {}", newCameraIndex);

            } catch (Exception e) {
                logger.error("切换摄像头失败", e);
                Platform.runLater(() -> {
                    showError("切换失败", "无法切换到摄像头 " + newCameraIndex + ": " + e.getMessage());
                    // 尝试恢复到默认摄像头
                    try {
                        initCamera(0);
                        selectedCameraIndex = 0;
                        cameraComboBox.getSelectionModel().select(0);
                    } catch (Exception ex) {
                        logger.error("恢复默认摄像头失败", ex);
                    }
                });
            }
        }).start();
    }

    /**
     * 初始化指定索引的摄像头
     */
    private void initCamera(int cameraIndex) {
        // 立即给用户反馈
        Platform.runLater(() -> updateStatusBar("正在启动摄像头 " + cameraIndex + "..."));

        cameraController = new JavaCVCameraController(
            currentVideoWidth,
            currentVideoHeight,
            currentVideoFps
        );

        new Thread(() -> {
            try {
                logger.info("开始启动摄像头: {} (参数: {}x{} @ {}fps)",
                    cameraIndex, currentVideoWidth, currentVideoHeight, currentVideoFps);
                cameraController.startCamera(cameraIndex);
                running = true;

                Platform.runLater(() -> updateStatusBarSuccess(
                    String.format("摄像头已启动 (%dx%d @ %dfps)",
                        currentVideoWidth, currentVideoHeight, currentVideoFps)
                ));
                logger.info("摄像头启动成功");

                startPreviewLoop();
            } catch (Exception e) {
                logger.error("相机启动失败", e);
                Platform.runLater(() -> {
                    updateStatusBar("摄像头启动失败");
                    showError("相机错误",
                        "无法启动相机 " + cameraIndex + "\n\n" +
                        "错误信息: " + e.getMessage() + "\n\n" +
                        "请检查：\n" +
                        "1. Windows隐私设置 → 相机 → 允许桌面应用访问相机\n" +
                        "2. 摄像头未被其他程序占用（微信、QQ、Zoom等）\n" +
                        "3. 摄像头在设备管理器中正常工作\n\n" +
                        "如果问题持续，请尝试重启电脑或更换摄像头");
                });
            } catch (Throwable t) {
                // 捕获所有错误，包括UnsatisfiedLinkError
                logger.error("相机启动发生严重错误", t);
                Platform.runLater(() -> {
                    updateStatusBar("摄像头启动失败（严重错误）");
                    showError("相机严重错误",
                        "摄像头启动时发生严重错误\n\n" +
                        "错误类型: " + t.getClass().getSimpleName() + "\n" +
                        "错误信息: " + t.getMessage() + "\n\n" +
                        "可能原因：\n" +
                        "1. 原生库加载失败\n" +
                        "2. 摄像头驱动损坏\n" +
                        "3. 系统权限不足\n\n" +
                        "建议：重新安装程序或联系技术支持");
                });
            }
        }, "Camera-Init-Thread").start();
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "Unknown";
        }
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

    /**
     * 显示错误对话框（仅用于关键错误）
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示警告对话框（仅用于需要用户注意的警告）
     */
    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("警告");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
