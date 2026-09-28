package com.fpgasta.model;

import java.time.Instant;

/**
 * Deterministic virtual time-line used by the whole workbench.
 *
 * Timestamps are derived from the event sequence and a fixed epoch, never from
 * {@code System.currentTimeMillis()}, so replays, tests and imports always
 * observe identical timestamps.
 */
public final class VirtualClock {

    public static final long BASE_EPOCH_SECOND = 1_788_268_800L; // 2026-09-01T00:00:00Z
    public static final long STEP_NANOS = 37_000_000_000L;

    private VirtualClock() {
    }

    public static long atNanos(long seq) {
        return java.util.concurrent.TimeUnit.SECONDS.toNanos(BASE_EPOCH_SECOND) + seq * STEP_NANOS;
    }

    public static String instant(long seq) {
        long nanos = atNanos(seq);
        long seconds = Math.floorDiv(nanos, 1_000_000_000L);
        long rest = Math.floorMod(nanos, 1_000_000_000L);
        return Instant.ofEpochSecond(seconds, rest).toString();
    }
}
