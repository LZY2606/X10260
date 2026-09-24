package io.fpgawb.model;

import java.util.Optional;

/**
 * One element of the data path / clock path breakdown as preserved from the report.
 * rawObject is the exact netlist object name; delta is the raw delay token (may be unparseable).
 */
public record PathSegment(
        int index,
        String rawObject,      // e.g. u_alu/U123/Q, net(n_42), rclke/CLK
        String objectKind,     // CELL / NET / PORT / UNKNOWN as seen in the report
        Physical delta,        // incremental delay token, raw retained
        Physical cumulative,   // cumulative delay token, raw retained
        String note            // verbatim annotation (e.g. "(r)")
) {
}
