package io.fpgawb.model;

/**
 * Explicit relationship between two clock domains. Without one, a crossing path is
 * UNCONSTRAINED rather than assumed synchronous.
 */
public record ClockRelation(String id, String fromClock, String toClock, Kind kind, String text) {
    public enum Kind { LOGICALLY_EXCLUSIVE, PHYSICALLY_EXCLUSIVE, ASYNCHRONOUS, SYNCHRONOUS, UNRELATED }
}
