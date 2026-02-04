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