package io.fpgawb.model;

import java.util.List;

/**
 * One false_path / multicycle exception statement with its exact target object sets.
 * Matching is always performed against these concrete object collections, never by
 * "last text line wins".
 */
public record ExceptionConstraint(
        String id,                 // stable within its constraint version
        Kind kind,
        CheckType checkType,       // SETUP, HOLD, BOTH
        int multiplier,            // multicycle multiplier; 0 for false path
        List<String> through,      // ordered THROUGH pin/cell sets
        List<String> from,
        List<String> to,
        List<String> fromClock,
        List<String> toClock,
        String text,               // verbatim source text
        int orderLine              // source line (tie-break / diagnostics only)
) {
    public enum Kind { FALSE_PATH, MULTICYCLE }
    public enum CheckType { SETUP, HOLD, BOTH }

    public boolean paired() {
        // A setup multicycle without an explicit HOLD mate is auto-paired (hold = setup-1);
        // a hold-only constraint is always explicit.
        return true;
    }
}
