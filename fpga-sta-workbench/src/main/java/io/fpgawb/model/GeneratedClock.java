package io.fpgawb.model;

import java.util.List;

/**
 * A generated (derived) clock. Provenance of the phase is fully retained: the master clock,
 * the source object, the divide-by/multiply-by factors and the raw phase shift token.
 */
public record GeneratedClock(
        String name,
        String sourceObject,
        String masterClock,       // direct parent clock name
        String masterPin,         // optional source pin qualifier
        int divideBy,
        int multiplyBy,
        Physical phaseShift,      // raw token e.g. "-2.500ns"
        String waveformRaw,       // verbatim edges token
        String rawBlock
) {
    public boolean fullyConvertible() {
        return phaseShift.convertible();
    }
}
