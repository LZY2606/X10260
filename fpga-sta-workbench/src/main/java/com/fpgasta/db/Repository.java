package com.fpgasta.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fpgasta.model.Json;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SQL access for all workbench tables. The repository knows no domain logic. */
public final class Repository {

    private final Database database;

    public Repository(Database database) {
        this.database = database;
    }

    public long nextSeq() {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT COALESCE(MAX(seq), 0) + 1 FROM events");
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot allocate event sequence", e);
        }
    }

    public void insertEvent(ObjectNode event) {
        Map<String, Object> values = RowMappers.eventInsertColumns(event);
        String sql = "INSERT INTO events(seq, event_id, at_instant, kind, subject_id, payload, parent_event_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            int index = 1;
            for (Object value : values.values()) {
                statement.setObject(index++, value);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot insert event", e);
        }
    }

    public List<ObjectNode> events() {
        String sql = "SELECT * FROM events ORDER BY seq ASC";
        List<ObjectNode> events = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                events.add(RowMappers.readEvent(rs));
            }
            return events;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read events", e);
        }
    }
}

    private static final Map<String, String[]> TABLE_COLUMNS = Map.of(
            "entities", new String[]{"id", "kind", "created_seq", "payload"},
            "revisions", new String[]{"id", "entity_id", "parent_rev_id", "seq", "kind", "status", "payload"},
            "aliases", new String[]{"id", "entity_id", "old_name", "new_name", "seq", "payload"},
            "derived_values", new String[]{"id", "kind", "subject_id", "algorithm_version",
                    "param_fingerprint", "sources", "status", "produced_seq", "payload"},
            "decisions", new String[]{"id", "subject_id", "kind", "verdict", "supersedes_id",
                    "seq", "payload"},
            "counterexamples", new String[]{"id", "scenario", "accepted", "seq", "payload"}
    );

    public void upsert(String table, Map<String, Object> values) {
        String[] columns = TABLE_COLUMNS.get(table);
        if (columns == null) {
            throw new IllegalArgumentException("Unknown table " + table);
        }
        StringBuilder sql = new StringBuilder("INSERT OR REPLACE INTO " + table + "(");
        sql.append(String.join(", ", columns)).append(") VALUES (");
        sql.append("?,".repeat(columns.length() - 1)).append("?)");
        try (PreparedStatement statement = database.connection().prepareStatement(sql.toString())) {
            for (int i = 0; i < columns.length; i++) {
                Object value = values.get(columns[i]);
                statement.setObject(i + 1, value);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot upsert into " + table, e);
        }
    }

    public List<Map<String, Object>> allRows(String table) {
        String[] columns = TABLE_COLUMNS.get(table);
        if (columns == null) {
            throw new IllegalArgumentException("Unknown table " + table);
        }
        String orderColumn = switch (table) {
            case "entities" -> "created_seq";
            case "counterexamples" -> "seq";
            default -> "seq";
        };
        String sql = "SELECT " + String.join(", ", columns) + " FROM " + table + " ORDER BY "
                + orderColumn + " ASC, id ASC";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String column : columns) {
                    Object value = rs.getObject(column);
                    row.put(column, value);
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read table " + table, e);
        }
        return rows;
    }

    public void clearAll() {
        for (String table : new String[]{"counterexamples", "decisions", "derived_values",
                "aliases", "revisions", "entities", "events"}) {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "DELETE FROM " + table)) {
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot clear table " + table, e);
            }
        }
    }

    public void putMeta(String key, String value) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot write meta " + key, e);
        }
    }

    public Map<String, String> meta() {
        Map<String, String> map = new LinkedHashMap<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT key, value FROM meta ORDER BY key ASC");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read meta", e);
        }
        return map;
    }

    public JsonNode currentDerived(String subjectId) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT payload FROM derived_values WHERE subject_id = ? ORDER BY produced_seq DESC, id ASC")) {
            statement.setString(1, subjectId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? RowMappers.readPayload(rs.getString(1)) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read derived value for " + subjectId, e);
        }
    }
