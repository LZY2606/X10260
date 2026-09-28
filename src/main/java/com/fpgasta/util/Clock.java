package com.fpgasta.util;

/** Virtual time source. Tests inject a fixed sequence; production uses a seeded logical counter. */
public interface Clock {
    /** Returns next logical timestamp in milliseconds (virtual, deterministic). */
    long nextMillis();

    static Clock fixed(long startMillis) {
        return new Clock() {
            private long t = startMillis;
            @Override public synchronized long nextMillis() { return t += 1000; }
        };
    }

    static Clock seeded(long seed) {
        return new Clock() {
            private long t = 1_700_000_000_000L + (seed % 1000) * 1000;
            @Override public synchronized long nextMillis() { return t += 1000; }
        };
    }
}
