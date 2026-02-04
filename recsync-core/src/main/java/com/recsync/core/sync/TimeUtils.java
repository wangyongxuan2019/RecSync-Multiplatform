package com.recsync.core.sync;

/** Helper conversions between time scales. */
public final class TimeUtils {

  public static double nanosToMillis(double nanos) {
    return nanos / 1_000_000L;
  }

  public static long nanosToSeconds(long nanos) {
    return nanos / 1_000_000_000L;
  }

  public static double nanosToSeconds(double nanos) {
    return nanos / 1_000_000_000L;
  }

  public static long millisToNanos(long millis) {
    return millis * 1_000_000L;
  }

  public static long secondsToNanos(int seconds) {
    return seconds * 1_000_000_000L;
  }

  private TimeUtils() {}
}
