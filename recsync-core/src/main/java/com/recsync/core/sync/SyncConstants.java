package com.recsync.core.sync;

public class SyncConstants {
    // Network ports
    public static final int RPC_PORT = 8244;  // Leader RPC port
    public static final int CLIENT_RPC_PORT = 8247;  // Client RPC port (when on same machine)
    public static final int SNTP_PORT = 9428;
    public static final int FILE_TRANSFER_PORT = 8246;
    public static final int DISCOVERY_BROADCAST_PORT = 8245;

    // Buffer sizes
    public static final int RPC_BUFFER_SIZE = 1024;
    public static final int SNTP_BUFFER_SIZE = 512;
    public static final int FILE_CHUNK_SIZE = 64 * 1024; // 64KB

    // Timing
    public static final long HEARTBEAT_PERIOD_NS = TimeUtils.secondsToNanos(1);
    public static final long STALE_TIME_NS = 2 * HEARTBEAT_PERIOD_NS;
    public static final long STALE_OFFSET_TIME_NS = TimeUtils.secondsToNanos(60 * 60);
    public static final int SOCKET_WAIT_TIME_MS = 500;
    public static final int NUM_SNTP_CYCLES = 300;
    public static final long MIN_ROUND_TRIP_LATENCY_NS = TimeUtils.millisToNanos(1);

    // RPC Method IDs (0-999: System, 1000+: User)
    public static final int METHOD_PROBE = 0;  // 探测请求/响应
    public static final int METHOD_HEARTBEAT = 1;
    public static final int METHOD_HEARTBEAT_ACK = 2;
    public static final int METHOD_OFFSET_UPDATE = 3;

    // Messages
    public static final int METHOD_MSG_ADDED_CLIENT = 1_101;
    public static final int METHOD_MSG_REMOVED_CLIENT = 1_102;
    public static final int METHOD_MSG_WAITING_FOR_LEADER = 1_103;
    public static final int METHOD_MSG_SYNCING = 1_104;
    public static final int METHOD_MSG_OFFSET_UPDATED = 1_105;
    public static final int METHOD_MSG_NAME_CONFLICT = 1_106;  // 名称冲突
    public static final int METHOD_MSG_MAX_CLIENTS_REACHED = 1_107;  // 达到最大客户端数

    // Limits
    public static final int MAX_CLIENTS = 10;  // 最大客户端数量

    // User RPC Methods (200_000+)
    public static final int START_NON_SOFTWARESYNC_METHOD_IDS = 1_000;
    public static final int METHOD_SET_TRIGGER_TIME = 200_000;
    public static final int METHOD_DO_PHASE_ALIGN = 200_001;
    public static final int METHOD_SET_2A = 200_002;
    public static final int METHOD_START_RECORDING = 200_003;
    public static final int METHOD_STOP_RECORDING = 200_004;
    public static final int METHOD_UPDATE_CLIENT_NAME = 200_005;  // 更新客户端名称
    public static final int METHOD_CLIENT_STATUS = 200_006;       // 客户端状态上报

    // Client Status Codes
    public static final int CLIENT_STATUS_CAMERA_NOT_READY = 0;   // 摄像头未就绪
    public static final int CLIENT_STATUS_CAMERA_READY = 1;       // 摄像头就绪
    public static final int CLIENT_STATUS_RECORDING = 2;          // 正在录制

    // Service Discovery
    public static final String MDNS_SERVICE_TYPE = "_recsync-leader._tcp.local.";
    public static final String MDNS_SERVICE_NAME = "RecSync-Leader";
    public static final String BROADCAST_MESSAGE_PREFIX = "LEADER_ANNOUNCE";
    public static final String AUTH_TOKEN = "RecSync-Secret-2024"; // 简单认证

    // File paths
    public static final String DEFAULT_ARCHIVE_DIR = "RecSync-Archive";
    public static final String DEFAULT_RECORDING_DIR = "RecSync";

    // Video parameters
    public static final int DEFAULT_VIDEO_WIDTH = 1280;
    public static final int DEFAULT_VIDEO_HEIGHT = 720;
    public static final int DEFAULT_VIDEO_FPS = 30;

    private SyncConstants() {}
}
