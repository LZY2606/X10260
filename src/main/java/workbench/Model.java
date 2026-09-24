package workbench;

import java.util.List;
import java.util.Map;

/** Domain model records. All raw inputs keep their original text; derived values carry
 *  algorithm version, parameter fingerprint and direct source ids. */
public final class Model {
    private Model() {}

    // ---------- raw documents (immutable, versioned) ----------
    public static final String KIND_TOOL_ENV = "TOOL_ENV";
    public static final String KIND_CLOCKS = "CLOCKS";
    public static final String KIND_CONSTRAINTS = "CONSTRAINTS";
    public static final String KIND_REPORT = "REPORT";
    public static final String KIND_RENAMES = "RENAMES";

    public record RawDoc(String id, String kind, String name, String rawText,
                         String parentId, long logicalTime, String contentHash) {
        public String family() { return kind + "/" + name; }
    }

    public record Diagnostic(String severity, String where, String message, String rawFragment) {}

    // ---------- parsed clock definitions ----------
    public record ClockDef(String name, String kind /*BASE|GENERATED*/, String targetObject,
                           Double periodNs, List<Double> waveformNs, String sourceObject,
                           Integer divideBy, String raw, int lineNo) {}

    public record ExceptionRule(String id, String type /*FALSE_PATH|MULTICYCLE_SETUP|MULTICYCLE_HOLD|CLOCK_GROUPS*/,
                                int multiplier, String fromKind, String fromPattern,
                                String toKind, String toPattern, List<String> groups,
                                int lineNo, String raw) {}

    public record PathRec(String label, String startpoint, String endpoint,
                          String launchClock, String captureClock,
                          Double arrivalNs, Double requiredNs, String rawSegment, int lineNo) {}

    public record RenameEntry(String oldName, String newName, int lineNo) {}

    public record ToolEnv(String tool, String version, String build) {}

    // ---------- derived results ----------
    public record DerivedResult(String id, String algorithmId, String algorithmVersion,
                                String paramsFingerprint, List<String> sourceIds,
                                String status, Object payload, long logicalTime) {}

    // clock graph payload
    public record ClockInfo(String name, String kind, String targetObject, Double periodNs,
                            List<Double> edgesNs, String sourceClock, Double phaseNs,
                            String unit, String coordinateBase, String status, String raw) {}
    public record ClockGraph(List<ClockInfo> clocks, List<String[]> relatedPairs,
                             List<Diagnostic> diagnostics) {}

    // exception resolution payload
    public record ExceptionMatch(String exceptionId, String exceptionRaw, int lineNo,
                                 List<String> matchedPaths, List<String> matchedObjects,
                                 List<String> matchedViaRename) {}
    public record PathResolution(String pathLabel, String effectiveExceptionId,
                                 List<RejectedCandidate> rejected, boolean ambiguous,
                                 List<String> ambiguousExceptionIds) {}
    public record RejectedCandidate(String exceptionId, String reason) {}
    public record ExceptionResolution(List<ExceptionMatch> matches,
                                      Map<String, PathResolution> byPath,
                                      List<Diagnostic> diagnostics) {}

    // path analysis payload
    public record Segment(String name, String detail, Double valueNs, String unit) {}
    public record Candidate(String id, String kind, String pathLabel, String summary,
                            Map<String, Object> params) {}
    public record PathAnalysis(String pathLabel, String pathKey, String startpoint, String endpoint,
                               String launchClock, String captureClock, String status,
                               Double arrivalNs, Double requiredNs, Double slackNs,
                               Integer setupMultiplier, Integer holdMultiplier, String holdPairing,
                               List<Segment> segments, List<Candidate> candidates,
                               String effectiveExceptionId, List<String> notes) {}
    public record PathAnalysisSet(List<PathAnalysis> paths, List<Diagnostic> diagnostics) {}

    // ---------- decisions / counterexamples ----------
    public record Decision(String id, String targetId, String action /*ACCEPT|REJECT|ADJUST*/,
                           Map<String, Object> params, String rationale, long logicalTime,
                           boolean reverted) {}

    public record Counterexample(String id, String kind, String title,
                                 Map<String, Object> evidence, long logicalTime) {}
}
