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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Client端服务发现 - 自动发现Leader
 * 支持三种发现方式：
 * 1. mDNS发现（局域网模式）
 * 2. UDP广播发现（局域网模式）
 * 3. 网关发现（热点模式 - Leader作为热点时，Leader就是网关）
 */
public class ClientDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(ClientDiscoveryService.class);
    private static final int DISCOVERY_TIMEOUT_MS = 8000;  // 增加超时时间以支持更多发现方式

    // 常见热点网关地址
    private static final String[] HOTSPOT_GATEWAY_IPS = {
        "192.168.137.1",  // Windows移动热点默认网关
        "192.168.43.1",   // Android热点默认网关
        "172.20.10.1"     // iOS热点默认网关
    };

    private JmDNS jmdns;
    private CompletableFuture<LeaderInfo> discoveryFuture;
    private String manualLeaderIP = null;  // 手动设置的Leader IP

    public CompletableFuture<LeaderInfo> discoverLeader() {
        discoveryFuture = new CompletableFuture<>();

        // 如果设置了手动IP，直接使用
        if (manualLeaderIP != null && !manualLeaderIP.isEmpty()) {
            logger.info("使用手动设置的Leader IP: {}", manualLeaderIP);
            return tryConnectToLeader(manualLeaderIP, "手动设置");
        }

        // 方法1: mDNS发现
        startMDNSDiscovery();

        // 方法2: UDP广播发现（1秒后启动）
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                if (!discoveryFuture.isDone()) {
                    startBroadcastDiscovery();
                }
            } catch (InterruptedException e) {
                logger.error("广播发现启动失败", e);
            }
        });

        // 方法3: 网关发现（热点模式，2秒后启动）
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
                if (!discoveryFuture.isDone()) {
                    startGatewayDiscovery();
                }
            } catch (InterruptedException e) {
                logger.error("网关发现启动失败", e);
            }
        });

        // 设置超时
        discoveryFuture.orTimeout(DISCOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    logger.warn("Leader发现超时，请检查网络连接或手动配置IP");
                    return null;
                });

        return discoveryFuture;
    }

    /**
     * 设置手动Leader IP（用于无法自动发现的情况）
     */
    public void setManualLeaderIP(String ip) {
        this.manualLeaderIP = ip;
        logger.info("已设置手动Leader IP: {}", ip);
    }

    /**
     * 获取手动设置的Leader IP
     */
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
                // 尝试连接到Leader的RPC端口验证是否可达
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

    private void startMDNSDiscovery() {
        try {
            jmdns = JmDNS.create();

            jmdns.addServiceListener(SyncConstants.MDNS_SERVICE_TYPE, new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    logger.debug("检测到服务: {}", event.getName());
                    jmdns.requestServiceInfo(event.getType(), event.getName());
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    logger.info("✅ 通过mDNS发现Leader");

                    InetAddress[] addresses = event.getInfo().getInetAddresses();
                    if (addresses.length > 0) {
                        String ip = addresses[0].getHostAddress();
                        int rpcPort = event.getInfo().getPort();
                        int transferPort = Integer.parseInt(
                                event.getInfo().getPropertyString("transfer_port")
                        );

                        LeaderInfo info = new LeaderInfo(ip, rpcPort, transferPort, "mDNS");
                        discoveryFuture.complete(info);

                        logger.info("   IP: {}, RPC端口: {}, 传输端口: {}",
                                ip, rpcPort, transferPort);
                    }
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    logger.info("Leader服务已移除: {}", event.getName());
                }
            });

        } catch (IOException e) {
            logger.warn("mDNS启动失败（可能在同一台机器上），将使用UDP广播发现: {}", e.getMessage());
            // 立即启动UDP广播发现作为备选
            CompletableFuture.runAsync(this::startBroadcastDiscovery);
        }
    }

    private void startBroadcastDiscovery() {
        try (DatagramSocket socket = new DatagramSocket(SyncConstants.DISCOVERY_BROADCAST_PORT)) {
            socket.setSoTimeout(3000);
            // 允许地址重用，以便在同一台机器上运行
            socket.setReuseAddress(true);

            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            logger.info("正在监听UDP广播...");

            while (!discoveryFuture.isDone()) {
                try {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());

                    // 解析: LEADER_ANNOUNCE|IP|RPC_PORT|TRANSFER_PORT|TOKEN
                    if (message.startsWith(SyncConstants.BROADCAST_MESSAGE_PREFIX)) {
                        String[] parts = message.split("\\|");
                        if (parts.length >= 5) {
                            String token = parts[4];
                            if (!SyncConstants.AUTH_TOKEN.equals(token)) {
                                logger.warn("认证令牌不匹配，忽略广播");
                                continue;
                            }

                            String ip = parts[1];
                            int rpcPort = Integer.parseInt(parts[2]);
                            int transferPort = Integer.parseInt(parts[3]);

                            logger.info("✅ 通过UDP广播发现Leader");
                            logger.info("   IP: {}, RPC端口: {}, 传输端口: {}",
                                    ip, rpcPort, transferPort);

                            LeaderInfo info = new LeaderInfo(ip, rpcPort, transferPort, "UDP广播");
                            discoveryFuture.complete(info);
                            break;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // 继续监听
                }
            }

        } catch (IOException e) {
            logger.error("UDP广播监听失败", e);
        }
    }

    /**
     * 网关发现（热点模式）
     * 当Client连接到Leader的热点时，Leader就是网关
     */
    private void startGatewayDiscovery() {
        if (discoveryFuture.isDone()) {
            return;
        }

        logger.info("🔥 尝试热点模式发现（检测网关）...");

        // 方法1：尝试获取系统默认网关
        String systemGateway = getSystemDefaultGateway();
        if (systemGateway != null && !discoveryFuture.isDone()) {
            logger.info("检测到系统网关: {}", systemGateway);
            if (tryConnectToGateway(systemGateway)) {
                return;
            }
        }

        // 方法2：尝试常见的热点网关地址
        for (String gatewayIP : HOTSPOT_GATEWAY_IPS) {
            if (discoveryFuture.isDone()) {
                return;
            }

            logger.debug("尝试热点网关: {}", gatewayIP);
            if (tryConnectToGateway(gatewayIP)) {
                return;
            }
        }

        logger.info("热点模式发现未找到Leader");
    }

    /**
     * 尝试连接到网关作为Leader
     */
    private boolean tryConnectToGateway(String gatewayIP) {
        try {
            // 尝试连接到网关的RPC端口
            Socket testSocket = new Socket();
            testSocket.connect(new InetSocketAddress(gatewayIP, SyncConstants.RPC_PORT), 1500);
            testSocket.close();

            logger.info("✅ 通过热点网关发现Leader: {}", gatewayIP);
            LeaderInfo info = new LeaderInfo(gatewayIP, SyncConstants.RPC_PORT,
                    SyncConstants.FILE_TRANSFER_PORT, "热点网关");
            discoveryFuture.complete(info);
            return true;

        } catch (IOException e) {
            logger.debug("网关 {} 不是Leader: {}", gatewayIP, e.getMessage());
            return false;
        }
    }

    /**
     * 获取系统默认网关（跨平台）
     */
    private String getSystemDefaultGateway() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                // Windows: 使用 route print 命令
                pb = new ProcessBuilder("cmd", "/c", "route", "print", "0.0.0.0");
            } else {
                // Linux/Mac: 使用 ip route 或 netstat
                pb = new ProcessBuilder("sh", "-c", "ip route | grep default | awk '{print $3}' || netstat -rn | grep default | awk '{print $2}'");
            }

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (os.contains("win")) {
                        // Windows route print 输出格式解析
                        // 找包含 0.0.0.0 的行，提取网关地址
                        if (line.contains("0.0.0.0") && !line.trim().startsWith("0.0.0.0")) {
                            String[] parts = line.trim().split("\\s+");
                            for (String part : parts) {
                                if (part.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") &&
                                    !part.equals("0.0.0.0") && !part.equals("255.255.255.255")) {
                                    // 验证是否为有效的局域网地址
                                    if (part.startsWith("192.168.") || part.startsWith("10.") || part.startsWith("172.")) {
                                        return part;
                                    }
                                }
                            }
                        }
                    } else {
                        // Linux/Mac 输出直接是IP地址
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
        if (jmdns != null) {
            try {
                jmdns.close();
            } catch (IOException e) {
                logger.error("关闭JmDNS失败", e);
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
