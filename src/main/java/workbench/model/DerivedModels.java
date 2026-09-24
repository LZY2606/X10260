package workbench.model;

import java.util.List;
import java.util.Map;

public final class DerivedModels {
    private DerivedModels() {}

    public record DisplayValue(String id, String raw, Double normalized, String unit, String coordinateBasis,
                               String sourceVersionId, String diagnostic) {}
    public record Candidate(String id, String constraintId, Integer constraintVersion, String checkKind,
                            String status, String reasonCode, String reason, Integer specificity,
                            Integer requestedMultiplier, List<String> matchedObjectIds,
                            List<String> sourceVersionIds, Map<String, Object> decisionEvidence,
                            String pairId) {}
    public record DerivedResult(String key, String id, String pathId, String checkKind, String status,
                                DisplayValue launchTime, DisplayValue captureTime, DisplayValue requirement,
                                DisplayValue arrival, DisplayValue slack, DisplayValue reportedSlack,
                                List<Candidate> candidates, List<String> effectiveCandidateIds,
                                List<String> directSources, List<String> diagnostics,
                                String algorithmVersion, String parameterFingerprint,
                                Map<String, String> dependencyVersionByLogicalSource,
                                List<String> activeDecisionIds) {}
    public record StoredResult(DerivedResult result, long eventSeq, String rowStatus, long supersededSeq,
                               List<String> dependencyLogicalKeys, String createdVirtualTime) {}
    public record VerdictCheck(String counterexampleId, String title, boolean passed, String reason,
                               String expected, String actual, List<String> evidenceEventIds,
                               String algorithmVersion, String parameterFingerprint) {}
}
