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
