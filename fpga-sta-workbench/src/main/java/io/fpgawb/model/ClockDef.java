package io.fpgawb.model;

import java.util.Optional;

/**
 * A base clock definition. Waveform is stored as the raw report/cmd token; the derived layer
 * turns it into absolute edges (ps). Coordinate basis: time 0 is the master clock launch edge.
 */
public record ClockDef(
        String name,
        String sourceObject,        // netlist object the waveform is attached to
        Physical period,            // raw e.g. "10.000ns"
        Physical waveformOffset,    // raw first edge offset token, may be unparseable
        String waveformRaw,         // verbatim waveform text, e.g. "{0.000 5.000}"
        String toolVersion,
        String rawBlock             // verbatim report/constraint block
) {
    public boolean waveformConvertible() {
        return period.convertible() && waveformOffset.convertible();
    }
}
