package com.fpgasta.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Owns the single SQLite database file and applies the schema migration. */
public final class Database implements AutoCloseable {

    private final Connection connection;

    public Database(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
            }
            migrate();
            connection.commit();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open database at " + path, e);
        }
    }

    public Connection connection() {
        return connection;
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS meta (
                      key TEXT PRIMARY KEY,
                      value TEXT NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS entities (
                      id TEXT PRIMARY KEY,
                      kind TEXT NOT NULL,
                      created_seq INTEGER NOT NULL,
                      payload TEXT NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS revisions (
                      id TEXT PRIMARY KEY,
                      entity_id TEXT NOT NULL REFERENCES entities(id),
                      parent_rev_id TEXT,
                      seq INTEGER NOT NULL,
                      kind TEXT NOT NULL,
                      status TEXT NOT NULL,
                      payload TEXT NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS aliases (
                      id TEXT PRIMARY KEY,
                      entity_id TEXT NOT NULL REFERENCES entities(id),
                      old_name TEXT NOT NULL,
                      new_name TEXT NOT NULL,
                      seq INTEGER NOT NULL,
                      payload TEXT NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS derived_values (
                      id TEXT PRIMARY KEY,
                      kind TEXT NOT NULL,
                      subject_id TEXT NOT NULL,
                      algorithm_version TEXT NOT NULL,
                      param_fingerprint TEXT NOT NULL,
                      sources TEXT NOT NULL,
                      status TEXT NOT NULL,
                      produced_seq INTEGER NOT NULL,
                      payload TEXT NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS decisions (
                      id TEXT PRIMARY KEY,
                      subject_id TEXT NOT NULL,
                      kind TEXT NOT NULL,
                      verdict TEXT NOT NULL,
                      supersedes_id TEXT,
                      seq INTEGER NOT NULL,
                      payload TEXT NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS events (
                      seq INTEGER PRIMARY KEY,
                      event_id TEXT NOT NULL UNIQUE,
                      at_instant TEXT NOT NULL,
                      kind TEXT NOT NULL,
                      subject_id TEXT NOT NULL,
                      payload TEXT NOT NULL,
                      parent_event_id TEXT
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS counterexamples (
                      id TEXT PRIMARY KEY,
                      scenario TEXT NOT NULL,
                      accepted INTEGER NOT NULL,
                      seq INTEGER NOT NULL,
                      payload TEXT NOT NULL
                    )""");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_rev_entity ON revisions(entity_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_dv_subject ON derived_values(subject_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_dec_subject ON decisions(subject_id)");
        }
    }

    public void commit() {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Commit failed", e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot close database", e);
        }
    }
}
