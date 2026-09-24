package workbench.store;

import com.fasterxml.jackson.databind.JsonNode;
import workbench.model.Event;
import workbench.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Repository implements AutoCloseable {
    private final Connection connection;

    public Repository(Path sqliteFile, boolean reset) {
        try {
            Path parent = sqliteFile.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            if (reset) Files.deleteIfExists(sqliteFile);
            String url = "jdbc:sqlite:" + sqliteFile.toAbsolutePath().normalize();
            connection = DriverManager.getConnection(url);
            init();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open SQLite database " + sqliteFile, e);
        }
    }

    public Connection connection() {
        return connection;
    }

    private void init() throws SQLException {
        boolean fresh;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select name from sqlite_master where type='table' and name='metadata'")) {
            fresh = !rs.next();
        }
        if (!fresh) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                create table metadata (
                    key text primary key,
                    value text not null
                )""");
            statement.executeUpdate("""
                create table events (
                    seq integer primary key autoincrement,
                    event_id text not null unique,
                    parent_event_id text,
                    type text not null,
                    virtual_time text not null,
                    actor text not null,
                    payload text not null,
                    input_hash text not null,
                    previous_hash text not null,
                    created_at text not null default (datetime('now'))
                )""");
            statement.executeUpdate("""
                create table source_versions (
                    logical_key text not null,
                    version_id text primary key,
                    kind text not null,
                    ref_id text not null,
                    ref_version integer not null,
                    parent_version_id text,
                    source_payload text not null,
                    first_event_seq integer not null,
                    latest_event_seq integer not null,
                    current integer not null
                )""");
            statement.executeUpdate("""
                create table result_versions (
                    result_id text primary key,
                    result_key text not null,
                    path_id text not null,
                    check_kind text not null,
                    event_seq integer not null,
                    row_status text not null,
                    superseded_seq integer,
                    created_virtual_time text not null,
                    result_payload text not null
                )""");
            statement.executeUpdate("""
                create table result_dependencies (
                    result_id text not null,
                    logical_key text not null,
                    primary key (result_id, logical_key)
                )""");
            statement.executeUpdate("""
                create table verdict_snapshots (
                    counterexample_id text primary key,
                    event_seq integer not null,
                    event_id text not null,
                    payload text not null
                )""");
            statement.executeUpdate("insert into metadata(key, value) values ('schema_version', '1')");
        }
    }

    public synchronized long appendEvent(Event event, JsonNode payload) {
        String sql = """
            insert into events(event_id, parent_event_id, type, virtual_time, actor, payload, input_hash, previous_hash)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, event.eventId());
            ps.setString(2, event.parentEventId());
            ps.setString(3, event.type());
            ps.setString(4, event.virtualTime());
            ps.setString(5, event.actor());
            ps.setString(6, Json.write(payload));
            ps.setString(7, event.inputHash());
            ps.setString(8, event.previousHash());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("missing generated event sequence");
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to append event", e);
        }
    }

    public synchronized List<Event> loadEvents() {
        List<Event> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("select * from events order by seq");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Event(
                    rs.getLong("seq"),
                    rs.getString("event_id"),
                    rs.getString("parent_event_id"),
                    rs.getString("type"),
                    rs.getString("virtual_time"),
                    rs.getString("actor"),
                    Json.read(rs.getString("payload"), JsonNode.class),
                    rs.getString("input_hash"),
                    rs.getString("previous_hash")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load events", e);
        }
        return result;
    }

    public synchronized void transaction(Runnable runnable) {
        try {
            connection.setAutoCommit(false);
            runnable.run();
            connection.commit();
            connection.setAutoCommit(true);
        } catch (RuntimeException | SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            throw e instanceof RuntimeException re ? re : new SQLException(e);
        }
    }

    @Override public void close() {
        try { connection.close(); } catch (SQLException ignored) {}
    }
}
