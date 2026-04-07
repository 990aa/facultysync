package edu.facultysync.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Manages SQLite connections with enterprise-grade PRAGMAs.
 * <ul>
 *   <li>PRAGMA foreign_keys = ON – enforces relational integrity</li>
 *   <li>PRAGMA journal_mode = WAL – concurrent reads during writes</li>
 * </ul>
 */
public class DatabaseManager {

    private static final String DEFAULT_URL = "jdbc:sqlite:facultysync.db";
     private final String url;
     private final boolean sharedMemory;
     private Connection sharedMemoryKeepAlive;

    public DatabaseManager() {
        this(DEFAULT_URL);
    }

    public DatabaseManager(String inputUrl) {
        this.sharedMemory = "jdbc:sqlite::memory:".equals(inputUrl);
        if (sharedMemory) {
            String dbName = "facultysync_mem_" + UUID.randomUUID();
            this.url = "jdbc:sqlite:file:" + dbName + "?mode=memory&cache=shared";
        } else {
            this.url = inputUrl;
        }
    }

    /**
     * Opens a new connection with PRAGMAs applied.
     */
    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        applyPragmas(connection);
        return connection;
    }

    private void applyPragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private synchronized Connection getSchemaConnection() throws SQLException {
        if (!sharedMemory) {
            return getConnection();
        }

        if (sharedMemoryKeepAlive == null || sharedMemoryKeepAlive.isClosed()) {
            sharedMemoryKeepAlive = DriverManager.getConnection(url);
            applyPragmas(sharedMemoryKeepAlive);
        }
        return sharedMemoryKeepAlive;
    }

    /**
     * Initializes the full schema (idempotent – uses IF NOT EXISTS).
     */
    public void initializeSchema() throws SQLException {
        if (sharedMemory) {
            Connection conn = getSchemaConnection();
            try (Statement stmt = conn.createStatement()) {
                createSchema(stmt);
            }
        } else {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                createSchema(stmt);
            }
        }
    }

    private void createSchema(Statement stmt) throws SQLException {

            stmt.execute("CREATE TABLE IF NOT EXISTS departments ("
                    + "dept_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL UNIQUE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS professors ("
                    + "prof_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "dept_id INTEGER NOT NULL, "
                    + "FOREIGN KEY (dept_id) REFERENCES departments(dept_id) ON DELETE CASCADE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS courses ("
                    + "course_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "course_code TEXT NOT NULL UNIQUE, "
                    + "prof_id INTEGER NOT NULL, "
                    + "enrollment_count INTEGER DEFAULT 0, "
                    + "FOREIGN KEY (prof_id) REFERENCES professors(prof_id) ON DELETE CASCADE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS locations ("
                    + "loc_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "building TEXT NOT NULL, "
                    + "room_number TEXT NOT NULL, "
                    + "capacity INTEGER, "
                    + "has_projector INTEGER DEFAULT 0, "
                    + "UNIQUE(building, room_number))");

            stmt.execute("CREATE TABLE IF NOT EXISTS scheduled_events ("
                    + "event_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "course_id INTEGER NOT NULL, "
                    + "loc_id INTEGER, "
                    + "event_type TEXT NOT NULL CHECK(event_type IN ('Lecture','Exam','Office Hours')), "
                    + "start_epoch INTEGER NOT NULL, "
                    + "end_epoch INTEGER NOT NULL, "
                    + "FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE, "
                    + "FOREIGN KEY (loc_id) REFERENCES locations(loc_id) ON DELETE SET NULL, "
                    + "CHECK(end_epoch > start_epoch))");

            // Index for fast overlap queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_time ON scheduled_events(start_epoch, end_epoch)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_loc ON scheduled_events(loc_id)");
    }

    /**
     * Closes the connection.
     */
    public synchronized void close() {
        if (sharedMemoryKeepAlive != null) {
            try {
                sharedMemoryKeepAlive.close();
            } catch (SQLException ignored) {
                // no-op
            }
            sharedMemoryKeepAlive = null;
        }
    }
}
