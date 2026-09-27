package workbench;

import java.util.List;

public final class Model {
    private Model() {}

    public enum InputKind {
        TOOL_VERSION, CLOCK_DEF, CONSTRAINTS, TIMING_REPORT, NETLIST_RENAME
    }

    /** Immutable raw input. Once submitted it is never overwritten; revisions are new versions with a parent link. */
    public record RawInput(String id, InputKind kind, int version, String parentId, String raw,
                           String sha, long seq, long createdAt) {}

    /** Derived value. Always carries algorithm version, parameter fingerprint and direct sources. */
    public record Derived(String id, String type, String subject, String algoVersion, String paramsFp,
                          List<String> sources, String payload, long seq, boolean stale, Long staleSeq) {}

    /** Machine-readable journal event. seq is the stable ordering identity. */
    public record Event(long seq, String id, String type, String payload, long createdAt) {}

    /** Human decision (ACCEPT / REJECT / ADJUST / UNDO). Never deleted; conflicts are kept. */
    public record Decision(String id, String target, String action, String params, String rationale,
                           String supersedes, long seq, long createdAt) {}
}
