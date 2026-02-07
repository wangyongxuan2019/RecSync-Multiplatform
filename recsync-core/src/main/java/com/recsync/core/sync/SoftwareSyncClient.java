package com.recsync.core.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * Client端同步控制 - 实现SNTP时钟同步算法
 */
public class SoftwareSyncClient extends SoftwareSyncBase {
    private static final Logger logger = LoggerFactory.getLogger(SoftwareSyncClient.class);

    // SNTP 同步参数
    private static final int SYNC_SAMPLE_COUNT = 30;        // 每轮同步的样本数
    private static final int SYNC_BEST_PERCENT = 30;        // 取最优的前30%样本
    private static final long RESYNC_INTERVAL_NS = TimeUnit.MINUTES.toNanos(10);  // 每10分钟重新同步

    private final InetAddress leaderAddress;
    private final int leaderRpcPort;
    private final String clientName;
    private final ScheduledExecutorService heartbeatScheduler;

    // SNTP 同步状态
    private volatile boolean synced = false;
    private final ConcurrentLinkedQueue<SntpSample> sntpSamples = new ConcurrentLinkedQueue<>();
    private volatile long lastSyncTimeNs = 0;
    private volatile int sampleCount = 0;

    // SNTP 样本记录
    private static class SntpSample {
        final long rtt;      // 往返时延
        final long offset;   // 时钟偏移

        SntpSample(long rtt, long offset) {
            this.rtt = rtt;
            this.offset = offset;
        }
    }

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
        super(clientRpcPort, new HashMap<>());

        this.leaderAddress = leaderAddress;
        this.leaderRpcPort = leaderRpcPort;
        this.clientName = clientName;
        this.heartbeatScheduler = Executors.newScheduledThreadPool(1);

        // 注册系统RPC回调
        registerSystemCallbacks(userCallback);

        startHeartbeat();
        logger.info("✅ SoftwareSyncClient已启动: {}, Leader端口: {}", clientName, leaderRpcPort);
    }

    /**
     * 注册系统RPC回调
     */
    private void registerSystemCallbacks(RpcCallback userCallback) {
        // 心跳确认回调 - SNTP核心算法
        rpcMap.put(SyncConstants.METHOD_HEARTBEAT_ACK, (method, payload, fromAddress) -> {
            // 记录收到响应的时间 t4
            long t4 = System.nanoTime();

            try {
                if (payload == null || payload.isEmpty()) {
                    // 兼容旧版本Leader（无SNTP数据）
                    logger.debug("收到旧版心跳确认（无SNTP数据）");
                    return;
                }

                String[] parts = payload.split(",");
                if (parts.length >= 3) {
                    long t1 = Long.parseLong(parts[0]);  // Client发送时间
                    long t2 = Long.parseLong(parts[1]);  // Leader收到时间
                    long t3 = Long.parseLong(parts[2]);  // Leader发送时间

                    // 计算RTT和Offset
                    // RTT = (t4 - t1) - (t3 - t2) = 网络往返时间（不含Leader处理时间）
                    long rtt = (t4 - t1) - (t3 - t2);

                    // Offset = [(t2 - t1) + (t3 - t4)] / 2
                    // 表示 Leader时间 = Client时间 + Offset
                    long offset = ((t2 - t1) + (t3 - t4)) / 2;

                    // 过滤异常值（RTT太小或为负数说明数据有问题）
                    if (rtt < SyncConstants.MIN_ROUND_TRIP_LATENCY_NS) {
                        logger.trace("丢弃异常样本: RTT={}ns < 最小阈值{}ns", rtt, SyncConstants.MIN_ROUND_TRIP_LATENCY_NS);
                        return;
                    }

                    // 添加样本
                    sntpSamples.add(new SntpSample(rtt, offset));
                    sampleCount++;

                    logger.trace("SNTP样本 #{}: RTT={}ms, Offset={}ms",
                            sampleCount, rtt / 1_000_000.0, offset / 1_000_000.0);

                    // 收集够样本后计算最优偏移
                    if (sampleCount >= SYNC_SAMPLE_COUNT) {
                        calculateOptimalOffset();
                    }
                }
            } catch (Exception e) {
                logger.error("处理心跳确认失败: payload='{}'", payload, e);
            }
        });

        // 偏移更新回调（Leader主动推送）
        rpcMap.put(SyncConstants.METHOD_OFFSET_UPDATE, (method, payload, fromAddress) -> {
            try {
                long offset = Long.parseLong(payload);
                setLeaderFromLocalNs(offset);
                synced = true;
                logger.info("收到Leader推送的偏移更新: {}ms", offset / 1_000_000.0);
            } catch (Exception e) {
                logger.error("处理偏移更新失败: payload='{}'", payload, e);
            }
        });

        // 名称冲突
        rpcMap.put(SyncConstants.METHOD_MSG_NAME_CONFLICT, (method, payload, fromAddress) -> {
            if (userCallback != null) {
                userCallback.onRpc(method, payload, fromAddress);
            }
        });

        // 达到最大客户端数
        rpcMap.put(SyncConstants.METHOD_MSG_MAX_CLIENTS_REACHED, (method, payload, fromAddress) -> {
            if (userCallback != null) {
                userCallback.onRpc(method, payload, fromAddress);
            }
        });

        // 用户自定义RPC（200000+）
        if (userCallback != null) {
            for (int i = SyncConstants.START_NON_SOFTWARESYNC_METHOD_IDS; i < 300000; i++) {
                rpcMap.put(i, userCallback);
            }
        }
    }

    /**
     * 计算最优时钟偏移 - 从样本中筛选RTT最小的前N%，取平均值
     */
    private void calculateOptimalOffset() {
        // 取出所有样本
        List<SntpSample> samples = new ArrayList<>();
        SntpSample sample;
        while ((sample = sntpSamples.poll()) != null) {
            samples.add(sample);
        }

        if (samples.isEmpty()) {
            return;
        }

        // 按RTT排序（升序）
        samples.sort(Comparator.comparingLong(s -> s.rtt));

        // 取前30%的最优样本
        int bestCount = Math.max(1, samples.size() * SYNC_BEST_PERCENT / 100);
        List<SntpSample> bestSamples = samples.subList(0, bestCount);

        // 计算平均偏移
        long sumOffset = 0;
        long minRtt = Long.MAX_VALUE;
        long maxRtt = Long.MIN_VALUE;

        for (SntpSample s : bestSamples) {
            sumOffset += s.offset;
            minRtt = Math.min(minRtt, s.rtt);
            maxRtt = Math.max(maxRtt, s.rtt);
        }

        long avgOffset = sumOffset / bestCount;

        // 更新时钟偏移
        setLeaderFromLocalNs(avgOffset);
        synced = true;
        lastSyncTimeNs = System.nanoTime();
        sampleCount = 0;

        logger.info("🕐 SNTP同步完成: 偏移={}ms, 样本数={}/{}, RTT范围=[{}ms, {}ms]",
                String.format("%.3f", avgOffset / 1_000_000.0),
                bestCount, samples.size(),
                String.format("%.2f", minRtt / 1_000_000.0),
                String.format("%.2f", maxRtt / 1_000_000.0));
    }

    private void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            sendHeartbeat();

            // 检查是否需要重新同步
            if (synced && (System.nanoTime() - lastSyncTimeNs) > RESYNC_INTERVAL_NS) {
                logger.info("🔄 触发定期重新同步...");
                synced = false;
                sampleCount = 0;
                sntpSamples.clear();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        // 记录发送时间 t1
        long t1 = System.nanoTime();

        String localIP = getLocalAddress();
        // payload格式: clientName,clientIP,synced,t1
        String payload = String.format("%s,%s,%s,%d",
                clientName,
                localIP,
                synced,
                t1);

        logger.trace("💓 发送心跳: t1={}, synced={}", t1, synced);

        try {
            sendRpc(SyncConstants.METHOD_HEARTBEAT, payload, leaderAddress, leaderRpcPort);
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

    /**
     * 是否已完成时钟同步
     */
    public boolean isSynced() {
        return synced;
    }

    /**
     * 获取当前时钟偏移（纳秒）
     */
    public long getClockOffsetNs() {
        return leaderFromLocalNs;
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
