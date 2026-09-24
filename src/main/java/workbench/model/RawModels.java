package workbench.model;

import java.util.List;

public final class RawModels {
    private RawModels() {}

    public record RawQty(String raw, Double value, String unit, String coordinateBasis) {}
    public record ClockEdge(String id, String edge, RawQty at, String sourceReportSegment) {}
    public record ClockDef(String id, String name, String kind, String sourceObjectId, RawQty period,
                           List<ClockEdge> waveform, String sourceReportSegment) {}
    public record GeneratedClockDef(String id, String name, String masterClockId, String sourceObjectId,
                                    Integer multiplyBy, Integer divideBy, RawQty phase,
                                    String sourceReportSegment, Integer version, String parentVersionId) {}
    public record NetObjectDef(String id, String name, String kind, String originalName,
                               String sourceReportSegment, Integer version, String parentVersionId) {}
    public record PathSegment(String id, String fromObjectId, String toObjectId, RawQty delay,
                              String sourceReportSegment) {}
    public record PathDef(String id, String name, String reportSegment, String startpointObjectId,
                          String startpointClockId, String endpointObjectId, String endpointClockId,
                          List<PathSegment> segments, RawQty reportedSetupSlack, RawQty reportedHoldSlack,
                          Integer version, String parentVersionId) {}
    public record ConstraintDef(String id, String name, Integer version, String parentVersionId, String kind,
                                String sourceText, Integer ordinal, List<String> appliesTo, String pairId,
                                Integer setupMultiplier, Integer holdMultiplier, String fromClockId,
                                String toClockId, List<String> objectIds, String sourceReportSegment) {}
    public record ClockRelationDef(String id, String fromClockId, String toClockId, String relationType,
                                   String sourceObjectId, String sourceReportSegment, Integer version,
                                   String parentVersionId) {}
    public record CounterexampleSpec(String id, String title, String directMutationEventSeq,
                                     List<String> baselineSourceVersionIds, String mutatedSourceVersionId,
                                     List<String> expectedAffectedResultKeys, String pathId, String checkKind,
                                     String expectedStatus, String expectedEffectiveConstraintId,
                                     String expectedRejectedConstraintId, String expectedRejectionReason,
                                     String expectedValueId, Double expectedNormalizedValue, String unit) {}
    public record RawBundle(String formatVersion, String caseId, String caseName, String toolVersion,
                            long seed, String coordinateBasis, String reportSegment, List<ClockDef> clocks,
                            List<GeneratedClockDef> generatedClocks, List<NetObjectDef> objects,
                            List<PathDef> paths, List<ConstraintDef> constraints,
                            List<ClockRelationDef> clockRelations, List<CounterexampleSpec> counterexamples) {}
}
