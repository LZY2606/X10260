package workbench;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Entity and payload records. All domain quantities carry ns units and t0-based coordinates. */
public final class Model {
    private Model() {}

    public record SourceDoc(String id, int versionNo, Integer parentVersion, String kind, String rawText,
                            String contentHash, long createdSeq, String submittedAt) {}

    public record NetlistObject(String id, int versionNo, Integer parentVersion, String name, String objType,
                                String renamedFrom, long createdSeq) {}

    public record ClockDef(String id, int versionNo, Integer parentVersion, String name, Double periodNs,
                           List<Double> waveformNs, String sourceDocId, long createdSeq) {}

    public record GeneratedClock(String id, int versionNo, Integer parentVersion, String name, String masterClockId,
                                 Integer divideBy, Integer multiplyBy, Double phaseShiftNs, List<Integer> edges,
                                 String sourcePin, String sourceDocId, long createdSeq) {}

    public record PathSegment(String objectId, String label, Double delayNs) {}

    public record TimingPath(String id, int versionNo, Integer parentVersion, int pathNo, String launchClockId,
                             String captureClockId, String fromObjectId, String toObjectId,
                             List<PathSegment> segments, Double arrivalNs, Double requiredNs,
                             String sourceDocId, long createdSeq) {}

    /** Object set as written in the constraint text: exact names and glob patterns. */
    public record ObjSet(List<String> exact, List<String> glob) {
        public boolean isEmpty() {
            return exact.isEmpty() && glob.isEmpty();
        }
    }

    public record ExceptionConstraint(String id, int versionNo, Integer parentVersion, String kind,
                                      ObjSet from, ObjSet to, ObjSet through, Integer setupMult, Integer holdMult,
                                      int constraintVersion, int seqInFile, String sourceDocId, long createdSeq) {}

    /** Direct source of a derived value: entity id plus the exact version used. */
    public record SourceRef(String entityId, int versionNo) {}

    public record DerivedResult(String id, String kind, String algoVersion, String paramFingerprint,
                                List<SourceRef> sources, String status, JsonNode payload, long createdSeq) {}

    public record Event(long seq, String id, String ts, String type, JsonNode payload) {}

    public record Decision(String id, int versionNo, Integer parentVersion, String pathId, String verdict,
                           String rationale, JsonNode params, long createdSeq) {}

    public record Counterexample(String id, String baselineId, String mutation, JsonNode payload, long createdSeq) {}
}
