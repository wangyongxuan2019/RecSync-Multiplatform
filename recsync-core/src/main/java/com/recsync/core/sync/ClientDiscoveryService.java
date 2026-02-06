package com.recsync.core.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client端服务发现 - 自动发现Leader
 * 支持多种发现方式（并行执行）：
 * 1. mDNS发现（局域网模式）
 * 2. UDP广播监听（局域网模式）
 * 3. 子网扫描（主动探测局域网内的Leader）
 * 4. 网关发现（热点模式）
 */
public class ClientDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(ClientDiscoveryService.class);
    private static final int DISCOVERY_TIMEOUT_MS = 10000;  // 10秒超时

    // 常见热点网关地址
    private static final String[] HOTSPOT_GATEWAY_IPS = {
        "192.168.137.1",  // Windows移动热点默认网关
        "192.168.43.1",   // Android热点默认网关
        "172.20.10.1"     // iOS热点默认网关
    };

    private JmDNS jmdns;
    private CompletableFuture<LeaderInfo> discoveryFuture;
    private String manualLeaderIP = null;
    private ExecutorService executorService;
    private AtomicBoolean found = new AtomicBoolean(false);

    public CompletableFuture<LeaderInfo> discoverLeader() {
        discoveryFuture = new CompletableFuture<>();
        found.set(false);
        executorService = Executors.newFixedThreadPool(4);

        // 如果设置了手动IP，直接使用
        if (manualLeaderIP != null && !manualLeaderIP.isEmpty()) {
            logger.info("使用手动设置的Leader IP: {}", manualLeaderIP);
            return tryConnectToLeader(manualLeaderIP, "手动设置");
        }

        logger.info("开始自动发现Leader...");

        // 并行启动所有发现方式
        executorService.submit(this::startMDNSDiscovery);
        executorService.submit(this::startBroadcastDiscovery);
        executorService.submit(this::startSubnetScan);
        executorService.submit(this::startGatewayDiscovery);

        // 设置超时 - 使用 completeOnTimeout 避免抛出异常
        return discoveryFuture
                .completeOnTimeout(null, DISCOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((result, ex) -> {
                    if (result == null && ex == null) {
                        logger.warn("Leader发现超时，请检查网络连接或手动配置IP");
                    }
                    // 清理资源
                    stop();
                });
    }

    /**
     * 设置手动Leader IP
     */
    public void setManualLeaderIP(String ip) {
        this.manualLeaderIP = ip;
        logger.info("已设置手动Leader IP: {}", ip);
    }

    public String getManualLeaderIP() {
        return manualLeaderIP;
    }

    /**
     * 尝试连接到指定IP的Leader
     */
    private CompletableFuture<LeaderInfo> tryConnectToLeader(String ip, String method) {
        CompletableFuture<LeaderInfo> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                Socket testSocket = new Socket();
                testSocket.connect(new InetSocketAddress(ip, SyncConstants.RPC_PORT), 2000);
                testSocket.close();

                LeaderInfo info = new LeaderInfo(ip, SyncConstants.RPC_PORT,
                        SyncConstants.FILE_TRANSFER_PORT, method);
                logger.info("✅ 通过{}发现Leader: {}", method, ip);
                future.complete(info);
            } catch (IOException e) {
                logger.warn("无法连接到 {}: {}", ip, e.getMessage());
                future.complete(null);
            }
        });

        return future;
    }

    /**
     * 快速检测指定IP是否为Leader（使用UDP RPC探测）
     * 发送 METHOD_PROBE 请求，等待 Leader 响应
     */
    private boolean quickProbe(String ip, int timeoutMs) {
        if (found.get()) return false;

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);

            // 构造 RPC 探测消息: [4字节 method ID] + [payload]
            byte[] payload = "PING".getBytes();
            byte[] sendData = new byte[4 + payload.length];
            // METHOD_PROBE = 0
            sendData[0] = 0;
            sendData[1] = 0;
            sendData[2] = 0;
            sendData[3] = 0;
            System.arraycopy(payload, 0, sendData, 4, payload.length);

            InetAddress address = InetAddress.getByName(ip);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                    address, SyncConstants.RPC_PORT);
            socket.send(sendPacket);

            // 等待响应
            byte[] receiveData = new byte[256];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);

            // 验证响应是否来自 Leader（检查 method ID 为 0）
            if (receivePacket.getLength() >= 4) {
                int method = java.nio.ByteBuffer.wrap(receivePacket.getData()).getInt();
                if (method == SyncConstants.METHOD_PROBE) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 完成发现
     */
    private synchronized void completeDiscovery(String ip, String method) {
        if (found.compareAndSet(false, true)) {
            LeaderInfo info = new LeaderInfo(ip, SyncConstants.RPC_PORT,
                    SyncConstants.FILE_TRANSFER_PORT, method);
            logger.info("✅ 通过{}发现Leader: {}", method, ip);
            discoveryFuture.complete(info);
        }
    }

    private void startMDNSDiscovery() {
        if (found.get()) return;

        try {
            jmdns = JmDNS.create();

            jmdns.addServiceListener(SyncConstants.MDNS_SERVICE_TYPE, new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    logger.debug("检测到mDNS服务: {}", event.getName());
                    jmdns.requestServiceInfo(event.getType(), event.getName());
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    if (found.get()) return;

                    InetAddress[] addresses = event.getInfo().getInetAddresses();
                    if (addresses.length > 0) {
                        String ip = addresses[0].getHostAddress();
                        completeDiscovery(ip, "mDNS");
                    }
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    logger.debug("mDNS服务已移除: {}", event.getName());
                }
            });

            logger.info("mDNS发现已启动");

        } catch (IOException e) {
            logger.warn("mDNS启动失败: {}", e.getMessage());
        }
    }

    private void startBroadcastDiscovery() {
        if (found.get()) return;

        logger.info("UDP广播发现已启动，监听端口: {}", SyncConstants.DISCOVERY_BROADCAST_PORT);

        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(SyncConstants.DISCOVERY_BROADCAST_PORT));
            socket.setSoTimeout(1000);  // 1秒超时，便于检查found状态

            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (!found.get() && !discoveryFuture.isDone()) {
                try {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());

                    if (message.startsWith(SyncConstants.BROADCAST_MESSAGE_PREFIX)) {
                        String[] parts = message.split("\\|");
                        if (parts.length >= 5) {
                            String token = parts[4];
                            if (!SyncConstants.AUTH_TOKEN.equals(token)) {
                                logger.warn("认证令牌不匹配，忽略广播");
                                continue;
                            }

                            String ip = parts[1];
                            completeDiscovery(ip, "UDP广播");
                            break;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // 继续监听
                }
            }
        } catch (BindException e) {
            logger.warn("UDP广播端口已被占用: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("UDP广播监听失败: {}", e.getMessage());
        }
    }

    /**
     * 子网扫描 - 主动探测局域网内的Leader
     */
    private void startSubnetScan() {
        if (found.get()) return;

        logger.info("子网扫描发现已启动...");

        try {
            // 获取本机IP，确定子网
            String localIP = getLocalIP();
            if (localIP == null) {
                logger.warn("无法获取本机IP，跳过子网扫描");
                return;
            }

            String subnet = localIP.substring(0, localIP.lastIndexOf('.') + 1);
            logger.info("扫描子网: {}x", subnet);

            // 优先扫描常见的 IP 地址（.1, .100, .101, .2, .10 等）
            int[] priorityHosts = {1, 2, 100, 101, 102, 10, 11, 50, 200, 254};

            // 先扫描优先 IP（常见的 DHCP 分配地址）
            for (int host : priorityHosts) {
                if (found.get()) return;
                String ip = subnet + host;
                if (!ip.equals(localIP) && quickProbe(ip, 500)) {
                    completeDiscovery(ip, "子网扫描");
                    return;
                }
            }

            // 并行扫描其他 IP
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 1; i <= 254; i++) {
                if (found.get()) break;

                final String ip = subnet + i;
                if (ip.equals(localIP)) continue;

                // 跳过已扫描的优先 IP
                boolean skip = false;
                for (int p : priorityHosts) {
                    if (i == p) { skip = true; break; }
                }
                if (skip) continue;

                futures.add(CompletableFuture.runAsync(() -> {
                    if (!found.get() && quickProbe(ip, 300)) {
                        completeDiscovery(ip, "子网扫描");
                    }
                }));

                // 限制并发数，每批 20 个
                if (futures.size() >= 20) {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .orTimeout(2, TimeUnit.SECONDS)
                            .exceptionally(ex -> null)
                            .join();
                    futures.clear();
                }
            }

            // 等待剩余的扫描完成
            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .orTimeout(2, TimeUnit.SECONDS)
                        .exceptionally(ex -> null)
                        .join();
            }

        } catch (Exception e) {
            logger.error("子网扫描失败: {}", e.getMessage());
        }
    }

    /**
     * 获取本机IP地址
     */
    private String getLocalIP() {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                var addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            logger.error("获取本机IP失败", e);
        }
        return null;
    }

    /**
     * 网关发现（热点模式）
     */
    private void startGatewayDiscovery() {
        if (found.get()) return;

        logger.info("热点网关发现已启动...");

        // 尝试系统默认网关
        String systemGateway = getSystemDefaultGateway();
        if (systemGateway != null && !found.get()) {
            logger.debug("检测到系统网关: {}", systemGateway);
            if (quickProbe(systemGateway, 500)) {
                completeDiscovery(systemGateway, "系统网关");
                return;
            }
        }

        // 尝试常见热点网关
        for (String gatewayIP : HOTSPOT_GATEWAY_IPS) {
            if (found.get()) return;
            if (quickProbe(gatewayIP, 300)) {
                completeDiscovery(gatewayIP, "热点网关");
                return;
            }
        }
    }

    /**
     * 获取系统默认网关
     */
    private String getSystemDefaultGateway() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "route", "print", "0.0.0.0");
            } else {
                pb = new ProcessBuilder("sh", "-c",
                        "ip route | grep default | awk '{print $3}' || netstat -rn | grep default | awk '{print $2}'");
            }

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (os.contains("win")) {
                        if (line.contains("0.0.0.0") && !line.trim().startsWith("0.0.0.0")) {
                            String[] parts = line.trim().split("\\s+");
                            for (String part : parts) {
                                if (part.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") &&
                                    !part.equals("0.0.0.0") && !part.equals("255.255.255.255")) {
                                    if (part.startsWith("192.168.") || part.startsWith("10.") || part.startsWith("172.")) {
                                        return part;
                                    }
                                }
                            }
                        }
                    } else {
                        String trimmed = line.trim();
                        if (trimmed.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                            return trimmed;
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            logger.debug("获取系统网关失败: {}", e.getMessage());
        }
        return null;
    }

    public void stop() {
        found.set(true);

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            executorService = null;
        }
        if (jmdns != null) {
            try {
                jmdns.close();
                jmdns = null;
            } catch (IOException e) {
                logger.debug("关闭JmDNS: {}", e.getMessage());
            }
        }
    }

    /**
     * Leader信息
     */
    public static class LeaderInfo {
        public final String ip;
        public final int rpcPort;
        public final int transferPort;
        public final String discoveryMethod;

        public LeaderInfo(String ip, int rpcPort, int transferPort, String method) {
            this.ip = ip;
            this.rpcPort = rpcPort;
            this.transferPort = transferPort;
            this.discoveryMethod = method;
        }

        @Override
        public String toString() {
            return String.format("Leader[%s:%d, 传输端口:%d, 发现方式:%s]",
                    ip, rpcPort, transferPort, discoveryMethod);
        }
    }
}
