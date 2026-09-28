package com.fpgasta.engine;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fpgasta.db.Database;
import com.fpgasta.db.Repository;
import com.fpgasta.model.Ids;
import com.fpgasta.model.Json;
import com.fpgasta.model.VirtualClock;
import com.fpgasta.parse.ParseResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only store.
 *
 * Raw submissions are immutable: every change becomes a new revision with a
 * parent pointer and a new event. The store is the only component allowed to
 * mutate database tables, and it does so in a single transaction per command.
 */
public final class Store {

    public static final List<String> TABLE_EXPORT_ORDER = List.of(
            "entities", "revisions", "aliases", "derived_values", "decisions",
            "counterexamples", "events");

    private final Database database;
    private final Repository repository;
    private final Projector projector;

    public Store(Database database) {
        this.database = database;
        this.repository = new Repository(database);
        this.projector = new Projector(repository);
    }

    public Repository repository() {
        return repository;
    }

    public Projector projector() {
        return projector;
    }

    /** Imports a parsed immutable submission and returns the resulting events. */
    public synchronized List<ObjectNode> importSubmission(String sourceName, String rawText,
                                                          ParseResult parsed) {
        long seq = repository.nextSeq();
        String eventId = eventId(seq, "SUBMISSION", sourceName, rawText);
        ObjectNode payload = Json.object();
        payload.put("sourceName", sourceName);
        payload.put("rawText", rawText);
        payload.put("rawSha256Short", Ids.sha8(rawText));
        payload.set("diagnostics", Json.MAPPER.valueToTree(parsed.diagnostics()));
        ObjectNode submission = appendEvent(seq, eventId, "SUBMISSION",
                "submission:" + sourceName, payload);

        List<ObjectNode> events = new ArrayList<>();
        events.add(submission);
        int counter = 0;
        counter = addEntities(events, seq, counter, "tool", parsed.tools(), sourceName);
        counter = addEntities(events, seq, counter, "clock", parsed.clocks(), sourceName);
        counter = addEntities(events, seq, counter, "exception", parsed.exceptions(), sourceName);
        counter = addEntities(events, seq, counter, "path", parsed.paths(), sourceName);
        for (ObjectNode clock : parsed.clocks()) {
            if ("clock_groups".equals(clock.path("kind").asText())) {
                counter = addClockRelation(events, seq, counter, clock, sourceName);
            }
        }
        recomputeDerived(events);
        database.commit();
        return events;
    }

    private int addEntities(List<ObjectNode> events, long baseSeq, int counter, String kind,
                            List<ObjectNode> records, String sourceName) {
        for (ObjectNode record : records) {
            String entityKey = kind + ":" + record.path("id").asText();
            String entityId = Ids.of("ent", entityKey);
            long seq = baseSeq + counter;
            String revId = Ids.of("rev", entityId + "|v1|" + seq);
            ObjectNode revPayload = Json.object();
            revPayload.put("version", 1);
            revPayload.put("sourceName", sourceName);
            revPayload.set("record", record);
            String eventId = eventId(seq, "ENTITY_REVISION", entityId, revId);
            ObjectNode payload = Json.object();
            payload.put("entityId", entityId);
            payload.put("revisionId", revId);
            payload.put("kind", kind);
            payload.putNull("parentRevisionId");
            payload.put("version", 1);
            payload.put("sourceName", sourceName);
            payload.set("record", record);
            events.add(appendEvent(seq, eventId, "ENTITY_REVISION", entityId, payload));
            counter++;
        }
        return counter;
    }

    private int addClockRelation(List<ObjectNode> events, long baseSeq, int counter,
                                 ObjectNode record, String sourceName) {
        String entityId = Ids.of("ent", "relation:" + record.path("id").asText());
        long seq = baseSeq + counter;
        String revId = Ids.of("rev", entityId + "|v1|" + seq);
        ObjectNode payload = Json.object();
        payload.put("entityId", entityId);
        payload.put("revisionId", revId);
        payload.put("kind", "relation");
        payload.putNull("parentRevisionId");
        payload.put("version", 1);
        payload.put("sourceName", sourceName);
        payload.set("record", record);
        events.add(appendEvent(seq, eventId(seq, "ENTITY_REVISION", entityId, revId),
                "ENTITY_REVISION", entityId, payload));
        return counter + 1;
    }
}

    /** Parameter revision: e.g. change generated-clock edge shift (phase). */
    public synchronized ObjectNode reviseClockParam(String clockEntityId, String reason,
                                                    ObjectNode patch) {
        ClockRef ref = requireLatestRevision(clockEntityId, "clock");
        ObjectNode record = ref.payload().with("record").deepCopy();
        applyClockPatch(record, patch);
        ObjectNode event = newRevisionEvent(clockEntityId, "clock", ref,
                "CLOCK_PARAM_REVISION", record, Map.of("reason", reason, "patch", patch));
        List<ObjectNode> events = new ArrayList<>(List.of(event));
        recomputeDerived(events);
        database.commit();
        return event;
    }

    /** Rename a netlist object: stable entity id, new alias + endpoint revision. */
    public synchronized List<ObjectNode> renameObject(String oldName, String newName, String reason) {
        if (oldName == null || newName == null || oldName.isBlank() || newName.isBlank()) {
            throw new IllegalArgumentException("oldName and newName are required");
        }
        if (oldName.equals(newName)) {
            throw new IllegalArgumentException("newName must differ from oldName");
        }
        ObjectNode aliasPayload = Json.object();
        aliasPayload.put("oldName", oldName);
        aliasPayload.put("newName", newName);
        aliasPayload.put("reason", reason);
        String aliasId = Ids.of("alias", oldName + "->" + newName + "|" + repository.nextSeq());
        List<ObjectNode> events = new ArrayList<>();
        long seq = repository.nextSeq();
        aliasPayload.put("aliasId", aliasId);
        ObjectNode aliasEvent = appendEvent(seq,
                eventId(seq, "OBJECT_RENAMED", aliasId, oldName + newName),
                "OBJECT_RENAMED", "object:" + Ids.sha8(oldName), aliasPayload);
        events.add(aliasEvent);
        List<ObjectNode> touched = projector.pathsUsingObjectName(oldName);
        for (ObjectNode path : touched) {
            String pathEntityId = path.path("entityId").asText();
            ClockRef ref = requireLatestRevision(pathEntityId, "path");
            ObjectNode record = ref.payload().with("record").deepCopy();
            renameInPath(record, oldName, newName);
            events.add(newRevisionEvent(pathEntityId, "path", ref, "PATH_ENDPOINT_REVISION",
                    record, Map.of("reason", reason, "oldName", oldName, "newName", newName)));
        }
        recomputeDerived(events);
        database.commit();
        return events;
    }

    /** Constraint version revision for one exception entity (false/multicycle). */
    public synchronized ObjectNode reviseConstraint(String exceptionEntityId, String reason,
                                                    ObjectNode patch) {
        ClockRef ref = requireLatestRevision(exceptionEntityId, "exception");
        ObjectNode record = ref.payload().with("record").deepCopy();
        applyExceptionPatch(record, patch);
        ObjectNode event = newRevisionEvent(exceptionEntityId, "exception", ref,
                "CONSTRAINT_REVISION", record, Map.of("reason", reason, "patch", patch));
        List<ObjectNode> events = new ArrayList<>(List.of(event));
        recomputeDerived(events);
        database.commit();
        return event;
    }

    /** Human adjudication on a candidate set. */
    public synchronized ObjectNode decide(String subjectId, String kind, String verdict,
                                          ObjectNode detail, String supersedesId) {
        if (!verdict.equals("accept") && !verdict.equals("reject") && !verdict.equals("parameterize")) {
            throw new IllegalArgumentException("verdict must be accept/reject/parameterize");
        }
        long seq = repository.nextSeq();
        String decisionId = Ids.of("dec", subjectId + "|" + kind + "|" + verdict + "|" + seq);
        ObjectNode payload = Json.object();
        payload.put("decisionId", decisionId);
        payload.put("subjectId", subjectId);
        payload.put("kind", kind);
        payload.put("verdict", verdict);
        payload.put("supersedesId", supersedesId);
        payload.set("detail", detail == null ? Json.object() : detail);
        ObjectNode event = appendEvent(seq, eventId(seq, "DECISION", decisionId, verdict),
                "DECISION", subjectId, payload);
        List<ObjectNode> events = new ArrayList<>(List.of(event));
        recomputeDerived(events);
        database.commit();
        return event;
    }

    /** Undo (revoke) a decision: recorded as an explicit historical event. */
    public synchronized ObjectNode revokeDecision(String decisionId, String reason) {
        long seq = repository.nextSeq();
        ObjectNode payload = Json.object();
        payload.put("decisionId", decisionId);
        payload.put("reason", reason);
        ObjectNode event = appendEvent(seq, eventId(seq, "DECISION_REVOKED", decisionId, reason),
                "DECISION_REVOKED", "decision:" + decisionId, payload);
        List<ObjectNode> events = new ArrayList<>(List.of(event));
        recomputeDerived(events);
        database.commit();
        return event;
    }

    /** Runs a counterexample scenario and records accepted and rejected outcomes. */
    public synchronized ObjectNode runCounterexample(String scenario, ObjectNode input) {
        ObjectNode outcome = projector.evaluateCounterexample(scenario,
                input == null ? Json.object() : input);
        long seq = repository.nextSeq();
        String counterId = Ids.of("cx", scenario + "|" + seq + "|" + Ids.sha8(Json.canonical(outcome)));
        outcome.put("counterexampleId", counterId);
        ObjectNode payload = Json.object();
        payload.put("counterexampleId", counterId);
        payload.put("scenario", scenario);
        payload.set("outcome", outcome);
        ObjectNode event = appendEvent(seq, eventId(seq, "COUNTEREXAMPLE_RUN", counterId, scenario),
                "COUNTEREXAMPLE_RUN", "counterexample:" + counterId, payload);
        database.commit();
        return event;
    }

    private ObjectNode payloadOf(String id, ObjectNode extra) {
        ObjectNode payload = Json.object();
        payload.put("aliasId", id);
        payload.setAll(extra);
        return payload;
    }

    private ObjectNode appendEvent(long seq, String eventId, String kind, String subjectId,
                                   ObjectNode payload) {
        List<ObjectNode> events = repository.events();
        String parentId = events.isEmpty() ? null : events.get(events.size() - 1).path("eventId").asText();
        ObjectNode event = Json.object();
        event.put("seq", seq);
        event.put("eventId", eventId);
        event.put("atInstant", VirtualClock.instant(seq));
        event.put("kind", kind);
        event.put("subjectId", subjectId);
        if (parentId != null) {
            event.put("parentEventId", parentId);
        }
        event.set("payload", payload);
        repository.insertEvent(event);
        return event;
    }

    private String eventId(long seq, String kind, String a, String b) {
        return "evt_" + Ids.sha8(seq + "|" + kind + "|" + a + "|" + b);
    }

    private ClockRef requireLatestRevision(String entityId, String expectedKind) {
        ObjectNode ref = projector.latestRevision(entityId);
        if (ref == null) {
            throw new IllegalArgumentException("Unknown entity " + entityId);
        }
        if (!expectedKind.equals(ref.path("kind").asText())) {
            throw new IllegalArgumentException("Entity " + entityId + " is "
                    + ref.path("kind").asText() + ", expected " + expectedKind);
        }
        return new ClockRef(ref.path("revisionId").asText(), ref.path("version").asInt(),
                ref.with("payload"));
    }

    private ObjectNode newRevisionEvent(String entityId, String kind, ClockRef parent,
                                        String eventKind, ObjectNode newRecord,
                                        Map<String, Object> extra) {
        long seq = repository.nextSeq();
        int version = parent.version() + 1;
        String revId = Ids.of("rev", entityId + "|v" + version + "|" + seq);
        ObjectNode recordPayload = Json.object();
        recordPayload.put("version", version);
        recordPayload.put("sourceName", "revision");
        recordPayload.set("record", newRecord);
        ObjectNode payload = Json.object();
        payload.put("entityId", entityId);
        payload.put("revisionId", revId);
        payload.put("kind", kind);
        payload.put("parentRevisionId", parent.revisionId());
        payload.put("version", version);
        payload.put("sourceName", "revision");
        payload.set("record", newRecord);
        for (Map.Entry<String, Object> entry : extra.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                payload.putNull(entry.getKey());
            } else if (value instanceof ObjectNode node) {
                payload.set(entry.getKey(), node);
            } else {
                payload.put(entry.getKey(), String.valueOf(value));
            }
        }
        return appendEvent(seq, eventId(seq, eventKind, entityId, revId),
                eventKind, entityId, payload);
    }

    private void applyClockPatch(ObjectNode record, ObjectNode patch) {
        if (patch.has("edgeShift")) {
            ObjectNode generated = record.has("generatedSource")
                    ? (ObjectNode) record.get("generatedSource") : Json.object();
            ArrayNode shifts = Json.NODE_FACTORY.arrayNode();
            patch.withArray("edgeShift").forEach(node ->
                    shifts.add(com.fpgasta.parse.TimeValue.parse(node.asText())));
            generated.set("edgeShift", shifts);
            generated.put("edgeShiftParam", patch.path("reason").asText("manual revision"));
            record.set("generatedSource", generated);
        }
        if (patch.has("period")) {
            record.set("period", com.fpgasta.parse.TimeValue.parse(patch.path("period").asText()));
        }
        if (patch.has("waveform")) {
            ArrayNode wave = Json.NODE_FACTORY.arrayNode();
            patch.withArray("waveform").forEach(node ->
                    wave.add(com.fpgasta.parse.TimeValue.parse(node.asText())));
            record.set("waveform", wave);
        }
    }

    private void applyExceptionPatch(ObjectNode record, ObjectNode patch) {
        for (String key : new String[]{"cycles", "checkType"}) {
            if (patch.has(key)) {
                if (patch.get(key).isNumber()) {
                    record.put(key, patch.get(key).asInt());
                } else {
                    record.put(key, patch.get(key).asText());
                }
            }
        }
        for (String key : new String[]{"from", "through", "to"}) {
            if (patch.has(key)) {
                ArrayNode array = Json.NODE_FACTORY.arrayNode();
                patch.withArray(key).forEach(array::add);
                record.set(key, array);
            }
        }
        if (patch.has("command")) {
            record.put("command", patch.path("command").asText());
        }
    }

    private void renameInPath(ObjectNode record, String oldName, String newName) {
        for (com.fasterxml.jackson.databind.JsonNode segmentNode : record.withArray("segments")) {
            ObjectNode segment = (ObjectNode) segmentNode;
            if (oldName.equals(segment.path("object").asText())) {
                segment.put("previousObject", oldName);
                segment.put("object", newName);
            }
            if (oldName.equals(segment.path("fromObject").asText())) {
                segment.put("previousFromObject", oldName);
                segment.put("fromObject", newName);
            }
            if (oldName.equals(segment.path("toObject").asText())) {
                segment.put("previousToObject", oldName);
                segment.put("toObject", newName);
            }
        }
    }

    private void recomputeDerived(List<ObjectNode> producedEvents) {
        projector.recompute(producedEvents);
    }

    private record ClockRef(String revisionId, int version, ObjectNode payload) {
    }
}
