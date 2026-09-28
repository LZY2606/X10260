package com.timing.model;

import java.util.Objects;

/**
 * A clock definition parsed from the clock section of an imported report.
 * Waveforms are kept as raw edge tokens plus normalized ns coordinates;
 * generated clocks carry their source master clock and divide/phase parameters.
 */
public final class ClockDef {

    private final String stableId;
    private final String name;
    private final String kind;            // BASE | GENERATED | VIRTUAL
    private final Physical period;
    private final String waveformRaw;    // e.g. "0.000  5.000  10.000 10.000"
    private final double[] edgesNs;      // normalized edge coordinates, may be partial
    private final String sourceNet;      // generated clock source pin/net
    private final String masterClock;    // master clock name for generated
    private final Physical phaseShift;   // phase offset (raw preserved)
    private final Integer divideBy;
    private final String diagnostic;

    public ClockDef(String stableId, String name, String kind, Physical period,
                    String waveformRaw, double[] edgesNs, String sourceNet,
                    String masterClock, Physical phaseShift, Integer divideBy,
                    String diagnostic) {
        this.stableId = stableId;
        this.name = name;
        this.kind = kind;
        this.period = period;
        this.waveformRaw = waveformRaw;
        this.edgesNs = edgesNs;
        this.sourceNet = sourceNet;
        this.masterClock = masterClock;
        this.phaseShift = phaseShift;
        this.divideBy = divideBy;
        this.diagnostic = diagnostic;
    }

    public String stableId() {
        return stableId;
    }

    public String name() {
        return name;
    }

    public String kind() {
        return kind;
    }

    public Physical period() {
        return period;
    }

    public String waveformRaw() {
        return waveformRaw;
    }

    public double[] edgesNs() {
        return edgesNs;
    }

    public String sourceNet() {
        return sourceNet;
    }

    public string_view masterClock() {
        return null;
    }

    public String masterClockName() {
        return masterClock;
    }

    public Physical phaseShift() {
        return phaseShift;
    }

    public Integer divideBy() {
        return divideBy;
    }

    public String diagnostic() {
        return diagnostic;
    }

    public boolean generated() {
        return Objects.equals(kind, "GENERATED");
    }
}
