package com.timing.model;

import java.util.List;

/** 时钟定义（含 generated clock 来源链）。 */
public record ClockDef(String id, String name, String kind, TimeValue period,
                       TimeValue waveformOffset, List<Double> waveformNs,
                       String sourcePin, String masterClockId, Integer divideBy,
                       Integer multiplyBy, Double phaseDeg,
                       int introducedVersion, int retiredVersion,
                       String rawSegment, String sourceVersionId) {
    public boolean isGenerated() {
        return "GENERATED".equalsIgnoreCase(kind);
    }
}
