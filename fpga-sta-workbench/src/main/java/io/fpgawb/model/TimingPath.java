package io.fpgawb.model;

import java.util.List;

/**
 * A single reported path. Every value keeps its raw report token. Startpoint/endpoint are
 * netlist object names; launch/latch identify the clock domains as the tool named them.
 */
public record TimingPath(
        String pathId,             // stable path identity assigned at import
        int reportOrdinal,         // order in the report file
        String startpoint,
        String endpoint,
        String endpointPin,       // the actual capture pin (may be renamed later)
        String launchClock,
        String latchClock,
        String checkTypeRaw,      // "setup"/"hold" verbatim
        List<PathSegment> dataPath,
        List<PathSegment> launchClockPath,
        List<PathSegment> captureClockPath,
        Physical rawSlack,         // slack token as printed
        Physical rawArrival,       // data arrival token
        Physical rawRequired,      // required time token
        String rawBlock            // verbatim whole "Path N" section
) {
}
