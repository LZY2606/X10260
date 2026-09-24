package stawb;

import java.util.List;

/** Domain model records. All records serialize to JSON for derived-result payloads. */
public final class Model {
    private Model() {}

    public record ClockDef(String id, String name, String port,
                           Quantity period, List<Quantity> waveform) {}

    public record GeneratedClock(String id, String name, String sourceClock,
                                 int divideBy, int multiplyBy, Quantity phaseShift) {}

    public enum ConstraintKind { FALSE_PATH, MULTICYCLE_SETUP, MULTICYCLE_HOLD, CLOCK_RELATION }

    public record ConstraintCmd(String id, int seq, ConstraintKind kind,
                                List<String> from, List<String> to, List<String> through,
                                Integer value, String relType, String rawLine) {}

    public record Segment(String object, Quantity delay) {}

    public record TimingPath(String id, String startpoint, String endpoint,
                             String launchClock, String captureClock, String corner,
                             Quantity arrival, Quantity required,
                             List<Segment> segments, String rawBlock) {}

    public record RenameEntry(String oldName, String newName) {}
}
