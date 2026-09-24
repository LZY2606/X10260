package workbench.store;

import workbench.model.Decision;
import workbench.model.DerivedModels.StoredResult;
import workbench.model.DerivedModels.VerdictCheck;
import workbench.model.Event;
import workbench.model.RawModels.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectState {
    public boolean initialized;
    public String caseId;
    public String caseName;
    public String toolVersion;
    public Long seed;
    public String coordinateBasis;
    public String rawReportSegment;
    public final Map<String, ClockDef> clocks = new LinkedHashMap<>();
    public final Map<String, GeneratedClockDef> generatedClocks = new LinkedHashMap<>();
    public final Map<String, NetObjectDef> objects = new LinkedHashMap<>();
    public final Map<String, PathDef> paths = new LinkedHashMap<>();
    public final Map<String, ConstraintDef> constraints = new LinkedHashMap<>();
    public final Map<String, ClockRelationDef> clockRelations = new LinkedHashMap<>();
    public final Map<String, CounterexampleSpec> counterexamples = new LinkedHashMap<>();
    public final Map<String, SourceVersion> sourceVersions = new LinkedHashMap<>();
    public final Map<String, StoredResult> currentResults = new LinkedHashMap<>();
    public final Map<String, StoredResult> latestResultByKey = new LinkedHashMap<>();
    public final Map<String, Decision> activeDecisions = new LinkedHashMap<>();
    public final Map<String, Decision> decisionsById = new LinkedHashMap<>();
    public final Map<String, VerdictCheck> verdictChecks = new LinkedHashMap<>();
    public final Map<Long, Event> eventsBySeq = new LinkedHashMap<>();
    public final Map<String, Event> eventsById = new LinkedHashMap<>();

    public record SourceVersion(String logicalKey, String versionId, String kind, String refId, int refVersion,
                                String parentVersionId, Object payload, long firstEventSeq,
                                long latestEventSeq, boolean current) {}

    public ClockRelationDef findRelation(String fromClock, String toClock) {
        for (ClockRelationDef relation : clockRelations.values()) {
            if (relation.fromClockId().equals(fromClock) && relation.toClockId().equals(toClock)) return relation;
        }
        return null;
    }

    public List<ClockDef> allClocksInWaveformOrder() {
        Map<String, ClockDef> all = new LinkedHashMap<>();
        clocks.forEach(all::put);
        generatedClocks.forEach(all::put);
        return List.copyOf(all.values());
    }

    public boolean clockExists(String id) {
        return clocks.containsKey(id) || generatedClocks.containsKey(id);
    }
}
