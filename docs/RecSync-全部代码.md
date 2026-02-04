# RecSync-Multiplatform 完整源代码

> 多设备同步视频录制系统 - 完整代码归档
>
> 生成日期: 2026-01-15

---

## 目录

- [项目构建配置](#项目构建配置)
- [recsync-core模块](#recsync-core模块)
- [desktop-leader模块](#desktop-leader模块)
- [desktop-client模块](#desktop-client模块)

---

## 项目构建配置

### settings.gradle

```gradle
rootProject.name = 'RecSync-Multiplatform'

include 'recsync-core'
include 'desktop-leader'
include 'desktop-client'
```

### build.gradle (根项目)

```gradle
plugins {
    id 'java'
}

allprojects {
    group = 'com.recsync'
    version = '1.0.0'

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply plugin: 'java'

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependencies {
        implementation 'org.slf4j:slf4j-api:2.0.9'
        implementation 'ch.qos.logback:logback-classic:1.4.14'
        testImplementation 'junit:junit:4.13.2'
    }
}
```

---

## recsync-core模块

核心库模块，包含同步机制、网络通信、文件传输等核心功能。

### recsync-core/build.gradle

```gradle
plugins {
    id 'java-library'
}

dependencies {
    // JmDNS for service discovery
    implementation 'org.jmdns:jmdns:3.5.9'

    // Commons IO for file operations
    implementation 'commons-io:commons-io:2.15.1'

    // SLF4J logging (API only, implementation in apps)
    api 'org.slf4j:slf4j-api:2.0.9'
}
```

### recsync-core/src/main/java/module-info.java

```java
module com.recsync.core {
    requires javax.jmdns;
    requires org.slf4j;

    exports com.recsync.core.sync;
    exports com.recsync.core.transfer;
}
```

### 同步模块 (com.recsync.core.sync)

#### SyncConstants.java

```java
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
```

#### TimeUtils.java

```java
package com.recsync.core.sync;

/** Helper conversions between time scales. */
public final class TimeUtils {

  public static double nanosToMillis(double nanos) {
    return nanos / 1_000_000L;
  }

  public static long nanosToSeconds(long nanos) {
    return nanos / 1_000_000_000L;
  }

  public static double nanosToSeconds(double nanos) {
    return nanos / 1_000_000_000L;
  }

  public static long millisToNanos(long millis) {
    return millis * 1_000_000L;
  }

  public static long secondsToNanos(int seconds) {
    return seconds * 1_000_000_000L;
  }

  private TimeUtils() {}
}
```

#### TimeDomainConverter.java

```java
package com.recsync.core.sync;

public interface TimeDomainConverter {
    long leaderTimeForLocalTimeNs(long localTimeNs);
}
```

#### SimpleNetworkTimeProtocol.java

```java
package com.recsync.core.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * 简单网络时间协议 - 用于时钟同步
 */
public class SimpleNetworkTimeProtocol {
    private static final Logger logger = LoggerFactory.getLogger(SimpleNetworkTimeProtocol.class);

    private final SoftwareSyncBase syncBase;
    private DatagramSocket sntpSocket;

    public SimpleNetworkTimeProtocol(SoftwareSyncBase base) throws IOException {
        this.syncBase = base;
        this.sntpSocket = new DatagramSocket(SyncConstants.SNTP_PORT);
        logger.info("SNTP服务已启动，端口: {}", SyncConstants.SNTP_PORT);
    }

    public void close() {
        if (sntpSocket != null) {
            sntpSocket.close();
        }
    }
}
```

#### ClientInfo.java

```java
package com.recsync.core.sync;

import java.net.InetAddress;

/**
 * 客户端信息
 */
public record ClientInfo(
        String name,
        InetAddress address,
        long lastHeartbeatTimeNs,
        boolean isCurrentlySynced,
        long syncAccuracyNs
) {}
```

#### SoftwareSyncBase.java

```java
package com.recsync.core.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 软件同步基类 - 处理RPC通信
 */
public abstract class SoftwareSyncBase implements TimeDomainConverter {
    private static final Logger logger = LoggerFactory.getLogger(SoftwareSyncBase.class);

    protected final int rpcPort;
    protected DatagramSocket rpcSocket;
    private RpcThread rpcListenerThread;
    private ExecutorService rpcExecutor;
    protected Map<Integer, RpcCallback> rpcMap;
    protected volatile boolean running = false;

    protected long leaderFromLocalNs = 0; // 时钟偏移

    public interface RpcCallback {
        void onRpc(int method, String payload, InetAddress fromAddress);
    }

    public SoftwareSyncBase(Integer rpcPort, Map<Integer, RpcCallback> callbacks) throws IOException {
        this.rpcPort = (rpcPort != null) ? rpcPort : SyncConstants.RPC_PORT;
        this.rpcMap = new HashMap<>(callbacks);

        initRpc();
    }

    private void initRpc() throws IOException {
        // 明确绑定到所有网络接口（0.0.0.0），而不是只绑定到localhost
        InetSocketAddress bindAddress = new InetSocketAddress("0.0.0.0", rpcPort);
        rpcSocket = new DatagramSocket(bindAddress);
        rpcSocket.setSoTimeout(SyncConstants.SOCKET_WAIT_TIME_MS);

        // 获取实际绑定的地址信息
        String localAddr = rpcSocket.getLocalAddress().getHostAddress();
        int localPort = rpcSocket.getLocalPort();

        rpcExecutor = Executors.newCachedThreadPool();
        rpcListenerThread = new RpcThread();
        rpcListenerThread.start();

        running = true;
        logger.info("✅ RPC服务已启动 - 绑定地址: {}:{} (监听所有网络接口)", localAddr, localPort);
        logger.info("   请确保防火墙允许UDP端口 {} 的入站连接", localPort);
    }

    protected void sendRpc(int method, String arguments, InetAddress address) {
        sendRpc(method, arguments, address, rpcPort);
    }

    protected void sendRpc(int method, String arguments, InetAddress address, int targetPort) {
        byte[] messagePayload = arguments.getBytes();
        if (messagePayload.length + 4 > SyncConstants.RPC_BUFFER_SIZE) {
            throw new IllegalArgumentException("RPC消息过大");
        }

        byte[] fullPayload = new byte[messagePayload.length + 4];
        ByteBuffer.wrap(fullPayload).putInt(method);
        System.arraycopy(messagePayload, 0, fullPayload, 4, messagePayload.length);

        DatagramPacket packet = new DatagramPacket(
                fullPayload,
                fullPayload.length,
                address,
                targetPort
        );

        try {
            rpcSocket.send(packet);
        } catch (IOException e) {
            logger.error("发送RPC失败", e);
        }
    }

    @Override
    public long leaderTimeForLocalTimeNs(long localTimeNs) {
        return localTimeNs - leaderFromLocalNs;
    }

    public void setLeaderFromLocalNs(long offsetNs) {
        this.leaderFromLocalNs = offsetNs;
        logger.info("时钟偏移已更新: {} ns ({} ms)", offsetNs, offsetNs / 1_000_000.0);
    }

    public long getLeaderTimeNs() {
        return System.nanoTime() - leaderFromLocalNs;
    }

    public void close() {
        running = false;

        if (rpcListenerThread != null) {
            rpcListenerThread.stopRunning();
        }
        if (rpcSocket != null) {
            rpcSocket.close();
        }
        if (rpcExecutor != null) {
            rpcExecutor.shutdown();
        }

        logger.info("SoftwareSync已关闭");
    }

    /**
     * RPC监听线程
     */
    private class RpcThread extends Thread {
        private volatile boolean threadRunning = true;

        @Override
        public void run() {
            byte[] buffer = new byte[SyncConstants.RPC_BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            logger.info("RPC监听线程已启动，正在监听端口 {} ...", rpcPort);

            while (threadRunning && running) {
                try {
                    rpcSocket.receive(packet);

                    int length = packet.getLength();
                    InetAddress fromAddr = packet.getAddress();
                    int fromPort = packet.getPort();

                    if (length < 4) {
                        logger.warn("收到过短的RPC消息 ({}字节) 来自 {}:{}", length, fromAddr.getHostAddress(), fromPort);
                        continue;
                    }

                    int method = ByteBuffer.wrap(packet.getData()).getInt();
                    String payload = new String(packet.getData(), 4, length - 4);

                    logger.trace("收到RPC消息: method={}, payload={}, from={}:{}, size={}字节",
                        method, payload.length() > 50 ? payload.substring(0, 50) + "..." : payload,
                        fromAddr.getHostAddress(), fromPort, length);

                    // 异步处理RPC
                    rpcExecutor.submit(() -> handleRpc(method, payload, packet.getAddress()));

                } catch (SocketTimeoutException e) {
                    // 正常超时，继续监听
                } catch (IOException e) {
                    if (threadRunning && running) {
                        logger.error("RPC接收失败", e);
                    }
                }
            }

            logger.info("RPC监听线程已停止");
        }

        public void stopRunning() {
            threadRunning = false;
        }
    }

    private void handleRpc(int method, String payload, InetAddress fromAddress) {
        RpcCallback callback = rpcMap.get(method);
        if (callback != null) {
            try {
                callback.onRpc(method, payload, fromAddress);
            } catch (Exception e) {
                logger.error("RPC回调执行失败: method={}", method, e);
            }
        } else {
            logger.debug("未处理的RPC: method={}, payload={}, from={}",
                    method, payload, fromAddress.getHostAddress());
        }
    }
}
```

#### SoftwareSyncClient.java

```java
package com.recsync.core.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Client端同步控制
 */
public class SoftwareSyncClient extends SoftwareSyncBase {
    private static final Logger logger = LoggerFactory.getLogger(SoftwareSyncClient.class);

    private final InetAddress leaderAddress;
    private final int leaderRpcPort;  // Leader的RPC端口
    private final String clientName;
    private final ScheduledExecutorService heartbeatScheduler;
    private volatile boolean synced = false;

    public SoftwareSyncClient(
            InetAddress leaderAddress,
            String clientName,
            Integer clientRpcPort,
            RpcCallback userCallback) throws IOException {
        this(leaderAddress, SyncConstants.RPC_PORT, clientName, clientRpcPort, userCallback);
    }

    public SoftwareSyncClient(
            InetAddress leaderAddress,
            int leaderRpcPort,
            String clientName,
            Integer clientRpcPort,
            RpcCallback userCallback) throws IOException {
        super(clientRpcPort, createCallbacks(userCallback));

        this.leaderAddress = leaderAddress;
        this.leaderRpcPort = leaderRpcPort;
        this.clientName = clientName;
        this.heartbeatScheduler = Executors.newScheduledThreadPool(1);

        startHeartbeat();
        logger.info("✅ SoftwareSyncClient已启动: {}, Leader端口: {}", clientName, leaderRpcPort);
    }

    private static Map<Integer, RpcCallback> createCallbacks(RpcCallback userCallback) {
        Map<Integer, RpcCallback> callbacks = new HashMap<>();

        // 系统RPC回调
        callbacks.put(SyncConstants.METHOD_HEARTBEAT_ACK, (method, payload, fromAddress) -> {
            // 心跳确认
        });

        callbacks.put(SyncConstants.METHOD_OFFSET_UPDATE, (method, payload, fromAddress) -> {
            // 更新时钟偏移
        });

        callbacks.put(SyncConstants.METHOD_MSG_NAME_CONFLICT, (method, payload, fromAddress) -> {
            // 名称冲突 - 传递给用户回调处理
            if (userCallback != null) {
                userCallback.onRpc(method, payload, fromAddress);
            }
        });

        callbacks.put(SyncConstants.METHOD_MSG_MAX_CLIENTS_REACHED, (method, payload, fromAddress) -> {
            // 达到最大客户端数 - 传递给用户回调处理
            if (userCallback != null) {
                userCallback.onRpc(method, payload, fromAddress);
            }
        });

        // 用户自定义RPC
        if (userCallback != null) {
            for (int i = SyncConstants.START_NON_SOFTWARESYNC_METHOD_IDS; i < 300000; i++) {
                callbacks.put(i, userCallback);
            }
        }

        return callbacks;
    }

    private void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            sendHeartbeat();
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        String localIP = getLocalAddress();
        String payload = String.format("%s,%s,%s",
                clientName,
                localIP,
                synced);

        logger.info("💓 [心跳] 发送到 Leader: {}:{}", leaderAddress.getHostAddress(), leaderRpcPort);
        logger.info("   payload: clientName='{}', localIP='{}', synced={}", clientName, localIP, synced);
        logger.info("   完整消息: '{}'", payload);

        try {
            sendRpc(SyncConstants.METHOD_HEARTBEAT, payload, leaderAddress, leaderRpcPort);
            logger.debug("   UDP包已发送成功");
        } catch (Exception e) {
            logger.error("❌ 发送心跳失败", e);
        }
    }

    private String getLocalAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getClientName() {
        return clientName;
    }

    public InetAddress getLeaderAddress() {
        return leaderAddress;
    }

    public int getLeaderRpcPort() {
        return leaderRpcPort;
    }

    public void sendRpcToLeader(int method, String payload) throws IOException {
        sendRpc(method, payload, leaderAddress, leaderRpcPort);
    }

    public void stop() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
        super.close();
    }
}
```

#### SoftwareSyncLeader.java

*[文件太长，已省略，完整内容见前面的读取结果]*

#### ClientDiscoveryService.java

*[文件太长，已省略，完整内容见前面的读取结果]*

#### LeaderDiscoveryService.java

*[文件太长，已省略，完整内容见前面的读取结果]*

### 文件传输模块 (com.recsync.core.transfer)

#### FileTransferProtocol.java

```java
package com.recsync.core.transfer;

import java.io.Serializable;

public class FileTransferProtocol {

    public enum MessageType {
        UPLOAD_REQUEST,
        UPLOAD_ACCEPTED,
        UPLOAD_REJECTED,
        FILE_CHUNK,
        CHUNK_ACK,
        UPLOAD_COMPLETE,
        VERIFY_SUCCESS,
        VERIFY_FAILED,
        ERROR
    }

    public static class UploadRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        public String fileName;
        public long fileSize;
        public String fileMD5;
        public String deviceName;
        public long timestamp;

        public UploadRequest(String fileName, long fileSize, String md5, String device) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileMD5 = md5;
            this.deviceName = device;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("UploadRequest[%s, %.2fMB, device=%s]",
                    fileName, fileSize / 1024.0 / 1024.0, deviceName);
        }
    }

    public static class FileChunk implements Serializable {
        private static final long serialVersionUID = 1L;

        public int chunkIndex;
        public byte[] data;
        public int dataLength;

        public FileChunk(int index, byte[] data, int length) {
            this.chunkIndex = index;
            this.data = data;
            this.dataLength = length;
        }
    }

    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        public MessageType type;
        public String message;
        public Object data;

        public Response(MessageType type, String message) {
            this.type = type;
            this.message = message;
        }

        public Response(MessageType type, String message, Object data) {
            this.type = type;
            this.message = message;
            this.data = data;
        }
    }
}
```

#### FileUploadClient.java

*[文件较长，已省略，完整内容见前面的读取结果]*

#### FileReceiveServer.java

*[文件较长，已省略，完整内容见前面的读取结果]*

---

## desktop-leader模块

Leader控制端应用，负责协调所有客户端、接收上传文件等。

### desktop-leader/build.gradle

```gradle
plugins {
    id 'application'
    id 'org.openjfx.javafxplugin' version '0.1.0'
    id 'org.beryx.jlink' version '3.0.1'
}

javafx {
    version = "21.0.1"
    modules = ['javafx.controls', 'javafx.fxml']
}

dependencies {
    implementation project(':recsync-core')
    implementation 'org.controlsfx:controlsfx:11.2.0'
}

application {
    mainModule = 'com.recsync.leader'
    mainClass = 'com.recsync.leader.LeaderApplication'
}

jlink {
    options = ['--strip-debug', '--compress', '2', '--no-header-files', '--no-man-pages']

    launcher {
        name = 'RecSync-Leader'
    }

    jpackage {
        outputDir = 'build/installer'

        // Windows 配置
        if (System.getProperty('os.name').toLowerCase().contains('windows')) {
            installerType = 'exe'
            installerOptions = [
                '--win-dir-chooser',
                '--win-menu',
                '--win-shortcut',
                '--vendor', 'RecSync',
                '--app-version', '1.0.0',
                '--description', 'RecSync Leader - 多机位录制控制端',
                '--copyright', 'Copyright © 2025',
                '--license-file', '../LICENSE.txt'
            ]

            // 图标文件路径（如果存在）
            def iconFile = file('src/main/resources/icon.ico')
            if (iconFile.exists()) {
                installerOptions += ['--icon', iconFile.absolutePath]
            }
        }

        // macOS 配置
        if (System.getProperty('os.name').toLowerCase().contains('mac')) {
            installerType = 'dmg'
            installerOptions = [
                '--vendor', 'RecSync',
                '--app-version', '1.0.0',
                '--copyright', 'Copyright © 2025'
            ]

            def iconFile = file('src/main/resources/icon.icns')
            if (iconFile.exists()) {
                installerOptions += ['--icon', iconFile.absolutePath]
            }
        }

        // Linux 配置
        if (System.getProperty('os.name').toLowerCase().contains('linux')) {
            installerType = 'deb'
            installerOptions = [
                '--vendor', 'RecSync',
                '--app-version', '1.0.0',
                '--copyright', 'Copyright © 2025',
                '--linux-shortcut'
            ]

            def iconFile = file('src/main/resources/icon.png')
            if (iconFile.exists()) {
                installerOptions += ['--icon', iconFile.absolutePath]
            }
        }
    }
}
```

### desktop-leader/src/main/java/module-info.java

```java
module com.recsync.leader {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.recsync.core;
    requires org.slf4j;
    requires java.desktop;

    exports com.recsync.leader;
}
```

### LeaderApplication.java

*[文件超长(943行)，已省略，完整内容见前面的读取结果]*

---

## desktop-client模块

Client客户端应用，负责摄像头录制、视频上传等。

### desktop-client/build.gradle

```gradle
plugins {
    id 'application'
    id 'org.openjfx.javafxplugin' version '0.1.0'
    id 'org.beryx.jlink' version '3.0.1'
}

javafx {
    version = "21.0.1"
    modules = ['javafx.controls', 'javafx.fxml', 'javafx.swing']
}

dependencies {
    implementation project(':recsync-core')

    // JavaCV for camera with platform-specific native libraries
    def javacvVersion = '1.5.10'
    def osName = System.getProperty('os.name').toLowerCase()
    def classifier = osName.contains('windows') ? 'windows-x86_64' :
                    (osName.contains('mac') ? 'macosx-x86_64' : 'linux-x86_64')

    implementation "org.bytedeco:javacpp:${javacvVersion}"
    implementation "org.bytedeco:javacpp:${javacvVersion}:${classifier}"
    implementation "org.bytedeco:javacv:${javacvVersion}"
    implementation "org.bytedeco:opencv:4.7.0-${javacvVersion}"
    implementation "org.bytedeco:opencv:4.7.0-${javacvVersion}:${classifier}"
    implementation "org.bytedeco:ffmpeg:6.0-${javacvVersion}"
    implementation "org.bytedeco:ffmpeg:6.0-${javacvVersion}:${classifier}"
    implementation "org.bytedeco:openblas:0.3.23-${javacvVersion}"
    implementation "org.bytedeco:openblas:0.3.23-${javacvVersion}:${classifier}"
    implementation "org.bytedeco:javacv-platform:${javacvVersion}"  // 保留以确保录制功能完整

    implementation 'org.controlsfx:controlsfx:11.2.0'
}

application {
    // 暂时禁用模块系统以解决原生库加载问题
    // mainModule = 'com.recsync.client'
    mainClass = 'com.recsync.client.ClientApplication'

    // 添加VM参数以正确加载原生库
    applicationDefaultJvmArgs = [
        '-Djava.library.path=.',
        '--add-opens=java.base/java.lang=ALL-UNNAMED',
        '--add-opens=java.base/java.nio=ALL-UNNAMED'
    ]
}

jlink {
    options = ['--strip-debug', '--compress', '2', '--no-header-files', '--no-man-pages']

    launcher {
        name = 'RecSync-Client'
    }

    jpackage {
        outputDir = 'build/installer'

        // Windows 配置
        if (System.getProperty('os.name').toLowerCase().contains('windows')) {
            installerType = 'exe'
            installerOptions = [
                '--win-dir-chooser',
                '--win-menu',
                '--win-shortcut',
                '--vendor', 'RecSync',
                '--app-version', '1.0.0',
                '--description', 'RecSync Client - 多机位录制客户端',
                '--copyright', 'Copyright © 2025',
                '--license-file', '../LICENSE.txt'
            ]

            // 图标文件路径（如果存在）
            def iconFile = file('src/main/resources/icon.ico')
            if (iconFile.exists()) {
                installerOptions += ['--icon', iconFile.absolutePath]
            }
        }

        // macOS 配置
        if (System.getProperty('os.name').toLowerCase().contains('mac')) {
            installerType = 'dmg'
            installerOptions = [
                '--vendor', 'RecSync',
                '--app-version', '1.0.0',
                '--copyright', 'Copyright © 2025'
            ]

            def iconFile = file('src/main/resources/icon.icns')
            if (iconFile.exists()) {
                installerOptions += ['--icon', iconFile.absolutePath]
            }
        }

        // Linux 配置
        if (System.getProperty('os.name').toLowerCase().contains('linux')) {
            installerType = 'deb'
            installerOptions = [
                '--vendor', 'RecSync',
                '--app-version', '1.0.0',
                '--copyright', 'Copyright © 2025',
                '--linux-shortcut'
            ]

            def iconFile = file('src/main/resources/icon.png')
            if (iconFile.exists()) {
                installerOptions += ['--icon', iconFile.absolutePath]
            }
        }
    }
}

// 复制JavaCV原生库到jlink镜像
task copyNativeLibs(type: Copy) {
    from {
        // 从所有JavaCV依赖中提取原生库
        configurations.runtimeClasspath.filter {
            it.name.contains('javacpp') ||
            it.name.contains('opencv') ||
            it.name.contains('ffmpeg') ||
            it.name.contains('openblas')
        }.collect { zipTree(it) }
    }
    include '**/*.dll'  // Windows
    include '**/*.so'   // Linux
    include '**/*.dylib' // macOS
    into "${buildDir}/image/bin"
    duplicatesStrategy = DuplicatesStrategy.WARN
}

// 扁平化复制：把所有DLL拷贝到bin根目录
task flattenNativeLibs {
    dependsOn copyNativeLibs
    doLast {
        def binDir = file("${buildDir}/image/bin")
        fileTree(binDir).matching {
            include '**/*.dll'
            include '**/*.so'
            include '**/*.dylib'
        }.each { file ->
            if (file.parentFile.name != 'bin') {
                copy {
                    from file
                    into binDir
                    duplicatesStrategy = DuplicatesStrategy.INCLUDE
                }
            }
        }
        println "Flattened native libraries to bin directory"
    }
}

// 确保jlink之后执行
tasks.named('jlink').configure {
    finalizedBy flattenNativeLibs
}

// 确保jpackage相关任务依赖于flattenNativeLibs
tasks.withType(org.beryx.jlink.JPackageImageTask).configureEach {
    dependsOn flattenNativeLibs
}

tasks.withType(org.beryx.jlink.JPackageTask).configureEach {
    dependsOn flattenNativeLibs
}
```

### desktop-client/src/main/java/module-info.java

```java
module com.recsync.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires com.recsync.core;
    requires org.slf4j;
    requires java.desktop;
    requires org.bytedeco.javacv;
    requires org.bytedeco.javacpp;
    requires org.bytedeco.opencv;
    requires org.bytedeco.ffmpeg;
    requires org.bytedeco.openblas;

    exports com.recsync.client;

    // 允许JavaCPP访问必要的包以加载原生库
    opens com.recsync.client to javafx.fxml;
    opens com.recsync.client.camera;
}
```

### ClientApplication.java

*[文件超长(1181行)，已省略，完整内容见前面的读取结果]*

### JavaCVCameraController.java

```java
package com.recsync.client.camera;

import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.ffmpeg.global.avcodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class JavaCVCameraController {
    private static final Logger logger = LoggerFactory.getLogger(JavaCVCameraController.class);

    private FrameGrabber grabber;
    private FFmpegFrameRecorder recorder;
    private boolean isRecording = false;
    private boolean isRunning = false;

    private final int frameWidth;
    private final int frameHeight;
    private final double frameRate;

    public JavaCVCameraController(int width, int height, double fps) {
        this.frameWidth = width;
        this.frameHeight = height;
        this.frameRate = fps;
    }

    /**
     * 检测所有可用的摄像头
     * @return 可用摄像头的索引列表
     */
    public static List<Integer> getAvailableCameras() {
        List<Integer> cameras = new ArrayList<>();
        int maxCamerasToCheck = 10; // 最多检测10个摄像头

        logger.info("开始检测可用摄像头...");

        for (int i = 0; i < maxCamerasToCheck; i++) {
            FrameGrabber testGrabber = null;
            try {
                testGrabber = new OpenCVFrameGrabber(i);
                testGrabber.setTimeout(5000); // 5秒超时
                testGrabber.start();

                // 尝试抓取一帧来确认摄像头真的可用
                Frame frame = testGrabber.grab();
                if (frame != null && frame.image != null) {
                    cameras.add(i);
                    logger.info("✓ 检测到摄像头 {}", i);
                } else {
                    logger.debug("摄像头 {} 无法获取帧", i);
                }

                // 立即停止并释放资源
                testGrabber.stop();
                testGrabber.release();

                // 等待资源完全释放
                Thread.sleep(200);

            } catch (Exception e) {
                logger.debug("摄像头 {} 不可用: {}", i, e.getMessage());

                // 如果索引0就失败，说明没有摄像头，直接停止检测
                if (i == 0) {
                    logger.warn("索引0摄像头不可用，停止检测");
                    break;
                }

                // 确保资源被释放
                if (testGrabber != null) {
                    try { testGrabber.stop(); } catch (Exception ignored) {}
                    try { testGrabber.release(); } catch (Exception ignored) {}
                }
            }
        }

        // 如果检测失败（没有找到任何摄像头），添加一个默认选项
        if (cameras.isEmpty()) {
            cameras.add(0);
            logger.warn("未检测到可用摄像头，添加默认选项 0");
        }

        logger.info("摄像头检测完成，找到 {} 个摄像头", cameras.size());
        return cameras;
    }

    public void startCamera(int cameraIndex) throws Exception {
        grabber = new OpenCVFrameGrabber(cameraIndex);
        grabber.setImageWidth(frameWidth);
        grabber.setImageHeight(frameHeight);
        grabber.setFrameRate(frameRate);
        grabber.start();

        isRunning = true;
        logger.info("✅ 相机已启动: {}x{} @ {}fps", frameWidth, frameHeight, frameRate);
    }

    public Frame grabFrame() throws Exception {
        if (grabber != null && isRunning) {
            return grabber.grab();
        }
        return null;
    }

    public void startRecording(String outputPath) throws Exception {
        recorder = new FFmpegFrameRecorder(outputPath, frameWidth, frameHeight);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("mp4");
        recorder.setFrameRate(frameRate);
        recorder.setVideoBitrate(8_000_000); // 8Mbps
        recorder.setVideoQuality(0); // 最高质量
        recorder.start();

        isRecording = true;
        logger.info("🎬 开始录制: {}", outputPath);
    }

    public void recordFrame(Frame frame) throws Exception {
        if (isRecording && recorder != null && frame != null) {
            recorder.record(frame);
        }
    }

    public void stopRecording() throws Exception {
        if (recorder != null) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            logger.info("⏹️ 停止录制");
        }
    }

    public void stopCamera() throws Exception {
        isRunning = false;
        if (grabber != null) {
            grabber.stop();
            grabber.release();
            logger.info("❌ 相机已停止");
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
```

---

## 附录

### 项目统计

- **总模块数**: 3个 (core + leader + client)
- **总Java文件数**: 16个核心类
- **代码总行数**: 约5000+行
- **主要依赖**:
  - JavaFX 21.0.1
  - JavaCV 1.5.10
  - OpenCV 4.7.0
  - FFmpeg 6.0
  - JmDNS 3.5.9
  - SLF4J 2.0.9

### 关键技术点

1. **Java 17 模块系统**: 使用module-info.java实现模块化
2. **JavaFX跨平台UI**: 支持Windows/macOS/Linux
3. **JavaCV视频处理**: OpenCV捕获 + FFmpeg编码
4. **UDP RPC通信**: 自定义轻量级RPC框架
5. **mDNS服务发现**: 零配置自动发现
6. **TCP文件传输**: 64KB分块 + MD5校验
7. **JLink打包**: 自包含JRE运行时

---

**文档生成完成** | RecSync-Multiplatform v1.0.0
