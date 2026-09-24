package gsb.model;

import java.util.List;
import java.util.Map;

/** Domain model for the FPGA static-timing / constraint-coverage workbench.
 *  All physical quantities carry an explicit unit (picoseconds) or a coordinate
 *  basis (clock domain, object name). Raw representations are preserved on the
 *  raw-input layer; normalization happens only in derived records. */
public final class Model {
    private Model() {}

    /** Primary clock definition. Waveform edges are absolute times in ps. */
    public record ClockDef(String id, String name, String port, long periodPs,
                           List<Long> waveformEdgesPs) {}

    /** Generated clock: derived from a master clock waveform. */
    public record GenClockDef(String id, String name, String sourceClock, String masterPin,
                              int divideBy, int multiplyBy, long phaseShiftPs) {}

    /** Explicit relation between two clock domains. Absence of a relation
     *  means UNCONSTRAINED -- synchrony is never assumed. */
    public record ClockRelation(String id, String fromClock, String toClock, String kind) {}

    /** One cell/net segment of a timing path. delayPs carries unit ps. */
    public record PathSegment(int index, String cell, String net, long delayPs) {}

    /** A timing path as parsed from the post-synthesis report. */
    public record TimingPath(String id, String startpoint, String endpoint,
                             String startClock, String endClock, String checkKind,
                             List<PathSegment> segments, long arrivalPs, long requiredPs) {}

    /** A timing exception constraint (false_path / multicycle_path).
     *  fromObjects/toObjects are concrete object sets; precedence is decided by
     *  set specificity, never by textual order. */
    public record ConstraintDef(String id, String type, List<String> fromObjects,
                                List<String> toObjects, Integer setupMultiplier,
                                Integer holdMultiplier, int sourceLine) {}

    /** EDA tool identity, preserved verbatim. */
    public record ToolInfo(String name, String version) {}

    /** A raw, immutable input submission. Revisions create new versions with
     *  parentVersion links; originals are never overwritten. */
    public record RawInput(String id, String kind, int version, String parentId,
                           String content, String submittedAt) {}

    /** A derived record: carries algorithm version, parameter fingerprint and
     *  direct sources so it can be replayed and invalidated precisely. */
    public record Derived(String id, String algoVersion, String fingerprint,
                          List<String> sources, String status, String payload) {}

    /** A human decision on a path or exception effect. */
    public record Decision(String id, String targetId, String action,
                           Map<String, String> params, String rationale, long atSeq) {}

    /** Candidate exception resolution kept as first-class data. */
    public record Candidate(String constraintId, int specificity, boolean chosen,
                            String rejectReason) {}
}
