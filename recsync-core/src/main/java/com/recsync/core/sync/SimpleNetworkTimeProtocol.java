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