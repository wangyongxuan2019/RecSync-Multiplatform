package com.recsync.core.sync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Leader端服务发现 - 注册mDNS服务并发送UDP广播
 * 支持两种网络模式：
 * 1. WiFi局域网模式 - Leader和Client连接同一WiFi
 * 2. 热点模式 - Leader开启热点，Client连接到Leader的热点
 */
public class LeaderDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(LeaderDiscoveryService.class);

    // 常见热点IP地址（Windows移动热点通常使用这些地址）
    private static final String[] HOTSPOT_IP_PREFIXES = {
        "192.168.137.",  // Windows移动热点默认
        "192.168.43.",   // Android热点默认
        "172.20.10."     // iOS热点默认
    };

    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private DatagramSocket broadcastSocket;
    private ScheduledExecutorService broadcastScheduler;
    private InetAddress localAddress;
    private volatile boolean running = false;
    private boolean isHotspotMode = false;  // 是否为热点模式

    public void start() throws IOException {
        localAddress = getLocalIPAddress();
        logger.info("本地IP地址: {}", localAddress.getHostAddress());

        // 尝试启动mDNS，如果失败也继续（UDP广播是主要机制）
        try {
            startMDNS();
        } catch (Exception e) {
            logger.warn("⚠️ mDNS服务启动失败（将仅使用UDP广播）: {}", e.getMessage());
            logger.debug("mDNS错误详情", e);
        }

        startBroadcast();
        running = true;

        logger.info("✅ Leader服务发现已启动");
    }

    private void startMDNS() throws IOException {
        jmdns = JmDNS.create(localAddress);

        HashMap<String, String> txtRecord = new HashMap<>();
        txtRecord.put("version", "1.0");
        txtRecord.put("ip", localAddress.getHostAddress());
        txtRecord.put("rpc_port", String.valueOf(SyncConstants.RPC_PORT));
        txtRecord.put("transfer_port", String.valueOf(SyncConstants.FILE_TRANSFER_PORT));

        serviceInfo = ServiceInfo.create(
                SyncConstants.MDNS_SERVICE_TYPE,
                SyncConstants.MDNS_SERVICE_NAME,
                SyncConstants.RPC_PORT,
                0, 0,
                txtRecord
        );

        jmdns.registerService(serviceInfo);
        logger.info("mDNS服务已注册: {} @ {}:{}",
                SyncConstants.MDNS_SERVICE_TYPE,
                localAddress.getHostAddress(),
                SyncConstants.RPC_PORT);
    }

    private void startBroadcast() throws SocketException {
        broadcastSocket = new DatagramSocket();
        broadcastSocket.setBroadcast(true);

        broadcastScheduler = Executors.newScheduledThreadPool(1);
        broadcastScheduler.scheduleAtFixedRate(() -> {
            try {
                sendBroadcast();
            } catch (IOException e) {
                logger.error("广播发送失败", e);
            }
        }, 0, 1, TimeUnit.SECONDS);

        logger.info("UDP广播已启动，端口: {}", SyncConstants.DISCOVERY_BROADCAST_PORT);
    }

    private void sendBroadcast() throws IOException {
        String message = String.format("%s|%s|%d|%d|%s",
                SyncConstants.BROADCAST_MESSAGE_PREFIX,
                localAddress.getHostAddress(),
                SyncConstants.RPC_PORT,
                SyncConstants.FILE_TRANSFER_PORT,
                SyncConstants.AUTH_TOKEN
        );

        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName("255.255.255.255"),
                SyncConstants.DISCOVERY_BROADCAST_PORT
        );

        broadcastSocket.send(packet);
    }

    private InetAddress getLocalIPAddress() throws UnknownHostException, SocketException {
        InetAddress candidateAddress = null;
        InetAddress hotspotAddress = null;  // 热点地址候选

        var interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() || !iface.isUp()) {
                continue;
            }

            String ifaceName = iface.getDisplayName().toLowerCase();
            String ifaceNameShort = iface.getName().toLowerCase();
            logger.debug("检查网络接口: {} ({})", iface.getDisplayName(), iface.getName());

            // 跳过虚拟网卡（但不跳过热点虚拟适配器）
            boolean isHotspotInterface = ifaceName.contains("wi-fi direct") ||
                                         ifaceName.contains("mobile hotspot") ||
                                         ifaceName.contains("local area connection*") ||
                                         ifaceNameShort.startsWith("ap");

            if (!isHotspotInterface && (
                ifaceName.contains("vmware") || ifaceName.contains("virtualbox") ||
                ifaceName.contains("vboxnet") || ifaceName.contains("hyper-v") ||
                ifaceName.contains("virtual") || ifaceName.contains("docker"))) {
                logger.debug("  跳过虚拟网卡: {}", iface.getDisplayName());
                continue;
            }

            var addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) {
                    continue;
                }

                String ip = addr.getHostAddress();
                logger.debug("  发现IPv4地址: {} on {}", ip, iface.getDisplayName());

                // 检查是否为热点IP地址
                boolean isHotspotIP = false;
                for (String prefix : HOTSPOT_IP_PREFIXES) {
                    if (ip.startsWith(prefix)) {
                        isHotspotIP = true;
                        break;
                    }
                }

                if (isHotspotIP || isHotspotInterface) {
                    logger.info("🔥 检测到热点网络接口: {} - IP: {}", iface.getDisplayName(), ip);
                    hotspotAddress = addr;
                    // 继续检查是否有更优先的WiFi地址
                }

                // 优先选择常见局域网地址段（192.168.x.x 或 10.x.x.x）
                if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                    // 优先选择WiFi或以太网接口（非热点）
                    if (!isHotspotIP && !isHotspotInterface &&
                        (ifaceName.contains("wi-fi") || ifaceName.contains("wlan") ||
                         ifaceName.contains("wireless") || ifaceName.contains("ethernet") ||
                         ifaceName.contains("eth") || ifaceName.contains("en0"))) {
                        logger.info("✅ 选择WiFi/以太网接口: {} - IP: {}", iface.getDisplayName(), ip);
                        isHotspotMode = false;
                        return addr;
                    }
                    // 备选地址
                    if (candidateAddress == null && !isHotspotIP) {
                        candidateAddress = addr;
                    }
                }

                // 如果还没有候选地址，使用任何非回环IPv4地址
                if (candidateAddress == null && !isHotspotIP) {
                    candidateAddress = addr;
                }
            }
        }

        // 如果有热点地址但没有普通WiFi地址，使用热点地址
        if (hotspotAddress != null && candidateAddress == null) {
            logger.info("🔥 使用热点模式: IP: {}", hotspotAddress.getHostAddress());
            isHotspotMode = true;
            return hotspotAddress;
        }

        // 如果有普通WiFi地址，优先使用
        if (candidateAddress != null) {
            logger.info("✅ 选择网络接口IP: {}", candidateAddress.getHostAddress());
            isHotspotMode = false;
            return candidateAddress;
        }

        // 如果只有热点地址，使用热点
        if (hotspotAddress != null) {
            logger.info("🔥 使用热点模式（唯一可用）: IP: {}", hotspotAddress.getHostAddress());
            isHotspotMode = true;
            return hotspotAddress;
        }

        return InetAddress.getLocalHost();
    }

    public void stop() {
        running = false;

        if (broadcastScheduler != null) {
            broadcastScheduler.shutdown();
        }
        if (broadcastSocket != null) {
            broadcastSocket.close();
        }
        if (jmdns != null) {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
            } catch (IOException e) {
                logger.error("关闭JmDNS失败", e);
            }
        }

        logger.info("❌ Leader服务发现已停止");
    }

    public String getLeaderIP() {
        return localAddress != null ? localAddress.getHostAddress() : "未知";
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 是否为热点模式
     */
    public boolean isHotspotMode() {
        return isHotspotMode;
    }

    /**
     * 获取网络模式描述
     */
    public String getNetworkModeDescription() {
        if (isHotspotMode) {
            return "热点模式 (Client需连接到此热点)";
        } else {
            return "WiFi局域网模式";
        }
    }
}
