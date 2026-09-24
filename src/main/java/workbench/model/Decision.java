package workbench.model;

import java.util.Map;

public record Decision(String candidateId, String action, Map<String, Object> parameters, String rationale,
                       String eventId, long seq, String supersedesDecisionId, String status) {}
