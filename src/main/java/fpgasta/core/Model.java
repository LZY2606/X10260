package fpgasta.core;

import java.util.List;

/** Immutable domain records. Every derived value references direct sources by stable id. */
public final class Model {
    private Model() {}

    /** A clock waveform edge in absolute nanoseconds, tied back to the raw segment text. */
    public record Edge(double timeNs, String kind, String rawLine) {}

    /** Base or generated clock definition as parsed from a raw report/constraint segment. */
    public record ClockDef(
            String id,
            String name,
            boolean generated,
            String parentClockId,
            List<String> sourcePins,          // netlist object names (object ids resolved later)
            String periodRaw, String waveformRaw,
            List<Edge> edges,
            int rawDocId, int lineFrom, int lineTo,
            String toolVersion
    ) {}

    /** Hierarchical netlist object (pin/cell/port). identity survives a rename via renamedFrom. */
    public record NetlistObject(
            String id,
            String name,
            String kind,           // pin | cell | port
            String renamedFrom,    // previous canonical name, if this revision is a rename
            int rawDocId
    ) {}

    /** One segment point of a timing path: startpoint, through cells/pins, endpoint. */
    public record PathPoint(String objectId, String name, String kind,
                            double arrivalNs, String arrivalRaw, boolean isEndpoint) {}

    public record TimingPath(
            String id,
            String label,
            String startObjectId, String endObjectId,
            String launchClockId, String captureClockId,
            List<PathPoint> points,
            String slackRaw,
            String pathType,       // setup | hold
            int rawDocId, int lineFrom, int lineTo
    ) {}

    public enum RuleKind { FALSE_PATH, MULTICYCLE_PATH, CLOCK_GROUPS, SET_CLOCK_UNCERTAINTY }

    /**
     * Constraint rule. Revisions keep the same rule number; parentRule links the prior
     * revision. objectRefs are *names as written*; resolved sets are derived.
     */
    public record ConstraintRule(
            String id,
            int ruleNumber,
            int revision,
            String parentRuleId,
            RuleKind kind,
            String subType,        // setup | hold | "" (for multicycle)
            List<String> objectRefs, // -through/-from/-to/get_ports... names as written
            int multiplier,
            String valueRaw,       // e.g. uncertainty "0.250ns"
            int rawDocId, int lineFrom, int lineTo,
            String constraintVersion
    ) {}

    /** Explicit relationship between two clocks. Absence across domains means unconstrained. */
    public record ClockRelation(String id, String fromClockId, String toClockId,
                                String relation, // asynchronous | logically_exclusive
                                int rawDocId) {}

    /** Raw, immutable input document. Never overwritten; revisions are new docs. */
    public record RawDoc(int id, String kind, String version, String parentVersion,
                         String toolVersion, String checksum, String text) {}

    public record ParseDiagnostic(String level, String code, String message,
                                  int rawDocId, Integer line) {}
}
