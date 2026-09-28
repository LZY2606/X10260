package com.fpgasta.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fpgasta.model.Json;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small helpers to bind JSON payloads and read ordered rows. */
final class RowMappers {

    private RowMappers() {
    }

    static void bind(PreparedStatement statement, int index, String value) throws SQLException {
        statement.setString(index, value);
    }

    static ObjectNode readEvent(ResultSet rs) throws SQLException {
        ObjectNode node = Json.object();
        node.put("seq", rs.getLong("seq"));
        node.put("eventId", rs.getString("event_id"));
        node.put("atInstant", rs.getString("at_instant"));
        node.put("kind", rs.getString("kind"));
        node.put("subjectId", rs.getString("subject_id"));
        node.put("parentEventId", rs.getString("parent_event_id"));
        node.set("payload", readPayload(rs.getString("payload")));
        return node;
    }

    static JsonNode readPayload(String json) {
        try {
            return Json.MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Stored payload is not JSON", e);
        }
    }

    static Map<String, Object> eventInsertColumns(ObjectNode event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("seq", event.path("seq").asLong());
        map.put("event_id", event.path("eventId").asText());
        map.put("at_instant", event.path("atInstant").asText());
        map.put("kind", event.path("kind").asText());
        map.put("subject_id", event.path("subjectId").asText());
        map.put("payload", Json.canonical(event.path("payload")));
        map.put("parent_event_id", textOrNull(event, "parentEventId"));
        return map;
    }

    private static String textOrNull(ObjectNode node, String key) {
        return node.path(key).isMissingNode() || node.path(key).isNull() ? null : node.path(key).asText();
    }
}
