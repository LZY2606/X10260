package com.gsb.workbench.util;

import java.time.Instant;

/**
 * 虚拟时间：默认走系统时钟；测试可固定/推进，保证记录与重放可复现。
 */
public final class VirtualClock {
    private static volatile Instant fixed = null;
    private static volatile long shiftMillis = 0L;

    private VirtualClock() {
    }

    public static Instant now() {
        Instant base = fixed != null ? fixed : Instant.now();
        return shiftMillis == 0L ? base : base.plusMillis(shiftMillis);
    }

    public static long epochMilli() {
        return now().toEpochMilli();
    }

    public static void setFixed(Instant instant) {
        fixed = instant;
        shiftMillis = 0L;
    }

    public static void advanceMillis(long millis) {
        shiftMillis += millis;
    }

    public static void reset() {
        fixed = null;
        shiftMillis = 0L;
    }
}
