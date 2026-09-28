package com.fpgasta.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fpgasta.db.Repository;
import com.fpgasta.model.Ids;
import com.fpgasta.model.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the review state by replaying the event log and computes derived
 * layers. Derived values keep their algorithm version, parameter fingerprint
 * and direct sources; only dependents of a local change are replaced.
 */
public final class Projector {

    private final Repository repository;

    public Projector(Repository repository) {
        this.repository = repository;
    }

    public ObjectNode state(Long atSeq) {
        Snapshot snapshot = replay(atSeq);
        ObjectNode state = Json.object();
        state.put("algorithmVersion", Algorithm.VERSION);
        state.put("headSeq", repository.events().isEmpty() ? 0
                : repository.events().get(repository.events().size() - 1).path("seq").asLong());
        state.put("viewSeq", atSeq == null ? state.path("headSeq").asLong() : atSeq);
        state.set("submissions", snapshot.submissions);
        state.set("tools", snapshot.tools);
        state.set("clocks", snapshot.clocks);
        state.set("relations", snapshot.relations);
        state.set("exceptions", snapshot.exceptions);
        state.set("paths", snapshot.paths);
        state.set("events", snapshot.events);
        state.set("aliases", snapshot.aliases);
        state.set("decisions", snapshot.decisions);
        state.set("counterexamples", snapshot.counterexamples);
        state.set("derived", snapshot.derived);
        state.set("view", snapshot.viewInfo);
        return state;
    }

    /** Full derived recomputation after a commit, limited to affected subjects. */
    public void recompute(List<ObjectNode> producedEvents) {
        long headSeq = headSeq();
        Snapshot snapshot = replay(null);
        Derivation derivation = new Derivation(repository, snapshot, headSeq);
        derivation.run(producedEvents);
    }

    public ObjectNode latestRevision(String entityId) {
        Snapshot snapshot = replay(null);
        return snapshot.byEntity.get(entityId);
    }

    public List<ObjectNode> pathsUsingObjectName(String name) {
        Snapshot snapshot = replay(null);
        List<ObjectNode> matches = new ArrayList<>();
        for (ObjectNode path : snapshot.paths) {
            if (usesObject(path, name)) {
                matches.add(path);
            }
        }
        return matches;
    }

    public boolean usesObject(ObjectNode path, String name) {
        JsonNode segments = path.path("record").path("segments");
        for (JsonNode segment : segments) {
            if (name.equals(segment.path("object").asText())
                    || name.equals(segment.path("fromObject").asText())
                    || name.equals(segment.path("toObject").asText())) {
                return true;
            }
        }
        return false;
    }

    private long headSeq() {
        List<ObjectNode> events = repository.events();
        return events.isEmpty() ? 0 : events.get(events.size() - 1).path("seq").asLong();
    }
}

    static final class Snapshot {
        final ArrayNode submissions = Json.NODE_FACTORY.arrayNode();
        final ArrayNode tools = Json.NODE_FACTORY.arrayNode();
        final ArrayNode clocks = Json.NODE_FACTORY.arrayNode();
        final ArrayNode relations = Json.NODE_FACTORY.arrayNode();
        final ArrayNode exceptions = Json.NODE_FACTORY.arrayNode();
        final ArrayNode paths = Json.NODE_FACTORY.arrayNode();
        final ArrayNode events = Json.NODE_FACTORY.arrayNode();
        final ArrayNode aliases = Json.NODE_FACTORY.arrayNode();
        final ArrayNode decisions = Json.NODE_FACTORY.arrayNode();
        final ArrayNode counterexamples = Json.NODE_FACTORY.arrayNode();
        final ArrayNode derived = Json.NODE_FACTORY.arrayNode();
        final ObjectNode viewInfo = Json.object();
        final Map<String, ObjectNode> byEntity = new LinkedHashMap<>();
    }

    private Snapshot replay(Long atSeq) {
        Snapshot snapshot = new Snapshot();
        List<ObjectNode> storedDerived = new ArrayList<>();
        for (Map<String, Object> row : repository.allRows("derived_values")) {
            ObjectNode node = Json.object();
            node.put("id", String.valueOf(row.get("id")));
            node.put("kind", String.valueOf(row.get("kind")));
            node.put("subjectId", String.valueOf(row.get("subject_id")));
            node.put("algorithmVersion", String.valueOf(row.get("algorithm_version")));
            node.put("paramFingerprint", String.valueOf(row.get("param_fingerprint")));
            node.put("sources", String.valueOf(row.get("sources")));
            node.put("status", String.valueOf(row.get("status")));
            node.put("producedSeq", ((Number) row.get("produced_seq")).longValue());
            node.set("payload", Json.MAPPER.valueToTree(
                    com.fpgasta.db.RowMappers.readPayload((String) row.get("payload"))));
            storedDerived.add(node);
        }
        List<ObjectNode> allEvents = repository.events();
        long effectiveAt = atSeq == null
                ? (allEvents.isEmpty() ? 0 : allEvents.get(allEvents.size() - 1).path("seq").asLong())
                : atSeq;
        for (ObjectNode event : allEvents) {
            if (event.path("seq").asLong() > effectiveAt) {
                break;
            }
            snapshot.events.add(event);
            applyEvent(snapshot, event);
        }
        Map<String, JsonNode> revoked = new LinkedHashMap<>();
        for (JsonNode event : snapshot.events) {
            if (event.path("kind").asText().equals("DECISION_REVOKED")) {
                revoked.put(event.path("payload").path("decisionId").asText(), event);
            }
        }
        for (ObjectNode derived : storedDerived) {
            if (derived.path("producedSeq").asLong() > effectiveAt) {
                continue;
            }
            boolean current = derived.path("producedSeq").asLong() == effectiveAt
                    || isCurrent(derived, snapshot, effectiveAt);
            derived.put("current", current);
            snapshot.derived.add(derived);
        }
        for (JsonNode decision : snapshot.decisions) {
            ((ObjectNode) decision).put("revoked",
                    revoked.containsKey(decision.path("decisionId").asText()));
        }
        snapshot.viewInfo.put("atSeq", effectiveAt);
        snapshot.viewInfo.put("mode", atSeq == null ? "head" : "historical");
        return snapshot;
    }

    private boolean isCurrent(ObjectNode derived, Snapshot snapshot, long effectiveAt) {
        long produced = derived.path("producedSeq").asLong();
        String kind = derived.path("kind").asText();
        for (JsonNode event : snapshot.events) {
            long seq = event.path("seq").asLong();
            if (seq <= produced) {
                continue;
            }
            if (eventAffects(kind, derived.path("subjectId").asText(), event)) {
                return false;
            }
        }
        return true;
    }

    private boolean eventAffects(String kind, String subjectId, JsonNode event) {
        String eventKind = event.path("kind").asText();
        String eventSubject = event.path("subjectId").asText();
        if (eventSubject.equals(subjectId)) {
            return switch (eventKind) {
                case "ENTITY_REVISION", "CLOCK_PARAM_REVISION", "CONSTRAINT_REVISION",
                        "PATH_ENDPOINT_REVISION", "OBJECT_RENAMED", "DECISION",
                        "DECISION_REVOKED" -> true;
                default -> false;
            };
        }
        if (kind.equals("path_slack") && (eventKind.equals("CLOCK_PARAM_REVISION")
                || eventKind.equals("ENTITY_REVISION") && event.path("payload").path("kind").asText().equals("clock"))) {
            return true;
        }
        return false;
    }

    private void applyEvent(Snapshot snapshot, ObjectNode event) {
        ObjectNode payload = event.with("payload");
        switch (event.path("kind").asText()) {
            case "SUBMISSION" -> snapshot.submissions.add(submissionView(event));
            case "ENTITY_REVISION", "CLOCK_PARAM_REVISION", "CONSTRAINT_REVISION",
                    "PATH_ENDPOINT_REVISION" -> applyRevision(snapshot, event);
            case "OBJECT_RENAMED" -> snapshot.aliases.add(aliasView(event));
            case "DECISION" -> snapshot.decisions.add(decisionView(event));
            case "COUNTEREXAMPLE_RUN" -> snapshot.counterexamples.add(counterexampleView(event));
            default -> {
            }
        }
    }

    private ObjectNode submissionView(ObjectNode event) {
        ObjectNode view = Json.object();
        view.put("eventId", event.path("eventId").asText());
        view.put("seq", event.path("seq").asLong());
        view.put("atInstant", event.path("atInstant").asText());
        view.setAll(event.with("payload").deepCopy());
        return view;
    }

    private void applyRevision(Snapshot snapshot, ObjectNode event) {
        ObjectNode payload = event.with("payload");
        String entityId = payload.path("entityId").asText();
        ObjectNode view = Json.object();
        view.put("entityId", entityId);
        view.put("revisionId", payload.path("revisionId").asText());
        view.put("parentRevisionId", payload.path("parentRevisionId").asText(null));
        view.put("version", payload.path("version").asInt());
        view.put("kind", payload.path("kind").asText());
        view.put("revisionEventSeq", event.path("seq").asLong());
        view.put("revisionEventId", event.path("eventId").asText());
        view.put("sourceName", payload.path("sourceName").asText());
        view.set("record", payload.path("record").deepCopy());
        snapshot.byEntity.put(entityId, view);
        String kind = payload.path("kind").asText();
        switch (kind) {
            case "tool" -> replaceById(snapshot.tools, entityId, view);
            case "clock" -> {
                replaceById(snapshot.clocks, entityId, view);
                if ("clock_groups".equals(view.path("record").path("kind").asText())) {
                    replaceById(snapshot.relations, entityId, view);
                }
            }
            case "relation" -> replaceById(snapshot.relations, entityId, view);
            case "exception" -> replaceById(snapshot.exceptions, entityId, view);
            case "path" -> replaceById(snapshot.paths, entityId, view);
            default -> {
            }
        }
    }

    private void replaceById(ArrayNode array, String entityId, ObjectNode view) {
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).path("entityId").asText().equals(entityId)) {
                array.set(i, view);
                return;
            }
        }
        array.add(view);
    }

    private ObjectNode aliasView(ObjectNode event) {
        ObjectNode view = Json.object();
        view.put("eventId", event.path("eventId").asText());
        view.put("seq", event.path("seq").asLong());
        view.setAll(event.with("payload").deepCopy());
        return view;
    }

    private ObjectNode decisionView(ObjectNode event) {
        ObjectNode view = Json.object();
        view.put("eventId", event.path("eventId").asText());
        view.put("seq", event.path("seq").asLong());
        view.put("atInstant", event.path("atInstant").asText());
        view.setAll(event.with("payload").deepCopy());
        return view;
    }

    private ObjectNode counterexampleView(ObjectNode event) {
        ObjectNode view = Json.object();
        view.put("eventId", event.path("eventId").asText());
        view.put("seq", event.path("seq").asLong());
        view.setAll(event.with("payload").deepCopy());
        return view;
    }
