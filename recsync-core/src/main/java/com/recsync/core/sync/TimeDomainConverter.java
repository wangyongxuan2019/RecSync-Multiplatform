package com.recsync.core.sync;

public interface TimeDomainConverter {
    long leaderTimeForLocalTimeNs(long localTimeNs);
}