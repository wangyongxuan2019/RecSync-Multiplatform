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