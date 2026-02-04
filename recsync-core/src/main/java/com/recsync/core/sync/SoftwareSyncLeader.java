package com.recsync.core.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Leader端同步控制
 */
public class SoftwareSyncLeader extends SoftwareSyncBase {
    private static final Logger logger = LoggerFactory.getLogger(SoftwareSyncLeader.class);

    private final Map<InetAddress, ClientInfo> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService staleClientChecker;
    private final SimpleNetworkTimeProtocol sntp;

    public SoftwareSyncLeader(Integer rpcPort, RpcCallback userCallback) throws IOException {
        super(rpcPort, createCallbacks(userCallback));

        this.sntp = new SimpleNetworkTimeProtocol(this);
        this.staleClientChecker = Executors.newScheduledThreadPool(1);

        // 添加心跳处理器
        addHeartbeatHandler();

        // 添加客户端名称更新处理器
        addClientNameUpdateHandler();

        startStaleClientChecker();
        logger.info("✅ SoftwareSyncLeader已启动");
    }

    /**
     * 添加心跳处理器
     */
    private void addHeartbeatHandler() {
        rpcMap.put(SyncConstants.METHOD_HEARTBEAT, (method, payload, fromAddress) -> {
            try {
                logger.info("📥 收到心跳请求: payload='{}', from={}", payload, fromAddress.getHostAddress());

                // 解析: clientName,clientIP,synced
                String[] parts = payload.split(",");
                if (parts.length >= 3) {
                    String clientName = parts[0];
                    String clientIP = parts[1];
                    boolean synced = Boolean.parseBoolean(parts[2]);

                    logger.info("   解析结果: 客户端名称='{}', 客户端IP='{}', synced={}", clientName, clientIP, synced);

                    // 检查是否是新客户端
                    boolean isNewClient = !clients.containsKey(fromAddress);

                    // 如果是新客户端且已达到最大数量，拒绝连接
                    if (isNewClient && clients.size() >= SyncConstants.MAX_CLIENTS) {
                        logger.warn("❌ 已达到最大客户端数({})，拒绝新连接: {} ({})",
                                SyncConstants.MAX_CLIENTS, clientName, fromAddress.getHostAddress());
                        sendRpc(SyncConstants.METHOD_MSG_MAX_CLIENTS_REACHED,
                                String.format("服务器已达到最大客户端数量限制(%d台)", SyncConstants.MAX_CLIENTS),
                                fromAddress,
                                SyncConstants.CLIENT_RPC_PORT);
                        return;
                    }

                    // 检查名称是否与其他客户端冲突
                    boolean nameConflict = clients.entrySet().stream()
                            .anyMatch(entry ->
                                    !entry.getKey().equals(fromAddress) &&  // 不是同一个地址
                                            entry.getValue().name().equals(clientName)  // 但名称相同
                            );

                    if (nameConflict) {
                        logger.warn("❌ 客户端名称冲突: {} 来自 {}", clientName, fromAddress.getHostAddress());
                        // 发送名称冲突消息
                        sendRpc(SyncConstants.METHOD_MSG_NAME_CONFLICT,
                                "设备名称 '" + clientName + "' 已被其他客户端使用",
                                fromAddress,
                                SyncConstants.CLIENT_RPC_PORT);
                        return;
                    }

                    // 更新或添加客户端
                    ClientInfo info = new ClientInfo(
                            clientName,
                            fromAddress,
                            System.nanoTime(),
                            synced,
                            0
                    );

                    clients.put(fromAddress, info);

                    if (isNewClient) {
                        logger.info("✅ *** 新客户端已连接 ***: {} ({}) - 当前客户端数: {}/{}",
                                clientName, fromAddress.getHostAddress(),
                                clients.size(), SyncConstants.MAX_CLIENTS);
                        logger.info("   客户端详细信息: 名称={}, 远程IP={}, 本地报告IP={}, 同步状态={}",
                                clientName, fromAddress.getHostAddress(), clientIP, synced);
                    } else {
                        logger.debug("💓 收到心跳: {} ({}), synced={}", clientName, fromAddress.getHostAddress(), synced);
                    }

                    // 发送心跳确认
                    logger.debug("📤 发送心跳确认到 {}:{}", fromAddress.getHostAddress(), SyncConstants.CLIENT_RPC_PORT);
                    sendRpc(SyncConstants.METHOD_HEARTBEAT_ACK, "", fromAddress, SyncConstants.CLIENT_RPC_PORT);
                } else {
                    logger.error("❌ 心跳消息格式错误: payload='{}', parts.length={}", payload, parts.length);
                }
            } catch (Exception e) {
                logger.error("❌ 处理心跳失败: payload='{}'", payload, e);
            }
        });
    }

    /**
     * 添加客户端名称更新处理器
     */
    private void addClientNameUpdateHandler() {
        rpcMap.put(SyncConstants.METHOD_UPDATE_CLIENT_NAME, (method, payload, fromAddress) -> {
            try {
                logger.info("📝 收到客户端名称更新请求: payload='{}', from={}", payload, fromAddress.getHostAddress());

                // 解析: oldName|newName
                String[] parts = payload.split("\\|");
                if (parts.length == 2) {
                    String oldName = parts[0];
                    String newName = parts[1];

                    logger.info("   解析结果: 旧名称='{}', 新名称='{}'", oldName, newName);

                    // 检查新名称是否与其他客户端冲突
                    boolean nameConflict = clients.entrySet().stream()
                            .anyMatch(entry ->
                                    !entry.getKey().equals(fromAddress) &&  // 不是同一个地址
                                            entry.getValue().name().equals(newName)  // 但新名称相同
                            );

                    if (nameConflict) {
                        logger.warn("❌ 客户端名称更新失败 - 名称冲突: {} (来自 {})", newName, fromAddress.getHostAddress());
                        // 发送名称冲突消息
                        sendRpc(SyncConstants.METHOD_MSG_NAME_CONFLICT,
                                "设备名称 '" + newName + "' 已被其他客户端使用",
                                fromAddress,
                                SyncConstants.CLIENT_RPC_PORT);
                        return;
                    }

                    // 更新客户端名称
                    ClientInfo oldInfo = clients.get(fromAddress);
                    if (oldInfo != null) {
                        ClientInfo newInfo = new ClientInfo(
                                newName,  // 使用新名称
                                oldInfo.address(),
                                System.nanoTime(),
                                oldInfo.isCurrentlySynced(),
                                oldInfo.syncAccuracyNs()
                        );
                        clients.put(fromAddress, newInfo);
                        logger.info("✅ 客户端名称已更新: '{}' -> '{}' ({})",
                                oldName, newName, fromAddress.getHostAddress());
                    }
                } else {
                    logger.error("❌ 名称更新消息格式错误: payload='{}', parts.length={}", payload, parts.length);
                }
            } catch (Exception e) {
                logger.error("❌ 处理名称更新失败: payload='{}'", payload, e);
            }
        });
    }

    private static Map<Integer, RpcCallback> createCallbacks(RpcCallback userCallback) {
        Map<Integer, RpcCallback> callbacks = new HashMap<>();

        // 注意：心跳处理在构造函数后通过addHeartbeatHandler添加

        if (userCallback != null) {
            // 用户自定义RPC（200000+）
            for (int i = SyncConstants.START_NON_SOFTWARESYNC_METHOD_IDS; i < 300000; i++) {
                callbacks.put(i, userCallback);
            }
        }
        return callbacks;
    }

    private void startStaleClientChecker() {
        staleClientChecker.scheduleAtFixedRate(() -> {
            long now = System.nanoTime();
            clients.entrySet().removeIf(entry -> {
                boolean isStale = (now - entry.getValue().lastHeartbeatTimeNs()) > SyncConstants.STALE_TIME_NS;
                if (isStale) {
                    logger.info("移除过期客户端: {}", entry.getValue().name());
                }
                return isStale;
            });
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 广播RPC到所有客户端
     */
    public void broadcastRpc(int method, String payload) {
        clients.forEach((addr, info) -> {
            // 发送到Client的RPC端口
            sendRpc(method, payload, addr, SyncConstants.CLIENT_RPC_PORT);
        });
        logger.debug("广播RPC: method={}, 客户端数={}", method, clients.size());
    }

    /**
     * 添加或更新客户端
     */
    public void addClient(String name, InetAddress address) {
        ClientInfo info = new ClientInfo(
                name,
                address,
                System.nanoTime(),
                false,
                0
        );
        clients.put(address, info);
        logger.info("✅ 添加客户端: {} ({})", name, address.getHostAddress());
    }

    public Map<InetAddress, ClientInfo> getClients() {
        return new HashMap<>(clients);
    }

    public void stop() {
        if (staleClientChecker != null) {
            staleClientChecker.shutdown();
        }
        if (sntp != null) {
            sntp.close();
        }
        super.close();
    }
}