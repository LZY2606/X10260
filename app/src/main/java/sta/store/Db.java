package sta.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class Db {
    private final Connection conn;

    public Db(String file) {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + file);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
            migrate();
        } catch (Exception e) { throw new RuntimeException("open db " + file, e); }
    }

    public Connection conn() { return conn; }

    private void migrate() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS projects(
                  id TEXT PRIMARY KEY, name TEXT NOT NULL, created_tick INTEGER NOT NULL)""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS counters(
                  project_id TEXT NOT NULL, name TEXT NOT NULL, value INTEGER NOT NULL,
                  PRIMARY KEY(project_id, name))""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS inputs(
                  id TEXT PRIMARY KEY, project_id TEXT NOT NULL, version_no INTEGER NOT NULL,
                  parent_id TEXT, kind TEXT NOT NULL, raw_text TEXT NOT NULL,
                  tool_version TEXT, note TEXT, seq INTEGER NOT NULL)""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS events(
                  id TEXT PRIMARY KEY, project_id TEXT NOT NULL, seq INTEGER NOT NULL,
                  type TEXT NOT NULL, payload TEXT NOT NULL, tick INTEGER NOT NULL)""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS derived(
                  id TEXT PRIMARY KEY, project_id TEXT NOT NULL, seq INTEGER NOT NULL,
                  kind TEXT NOT NULL, algo_version TEXT NOT NULL, fingerprint TEXT NOT NULL,
                  sources TEXT NOT NULL, payload TEXT NOT NULL, status TEXT NOT NULL)""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS decisions(
                  id TEXT PRIMARY KEY, project_id TEXT NOT NULL, seq INTEGER NOT NULL,
                  target_id TEXT NOT NULL, action TEXT NOT NULL, params TEXT NOT NULL,
                  note TEXT, undone INTEGER NOT NULL DEFAULT 0)""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS counterexamples(
                  id TEXT PRIMARY KEY, project_id TEXT NOT NULL, seq INTEGER NOT NULL,
                  scenario TEXT NOT NULL, baseline_seq INTEGER NOT NULL, mutation TEXT NOT NULL,
                  input_fragment TEXT NOT NULL, verdict TEXT NOT NULL, affected TEXT NOT NULL,
                  adopted TEXT NOT NULL, rejected TEXT NOT NULL)""");
        }
    }

    public void close() {
        try { conn.close(); } catch (Exception ignored) { }
    }
}
