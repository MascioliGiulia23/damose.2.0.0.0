package com.rometransit.data.database;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * SQLite Database Manager with connection pooling
 * Replaces the old JSON-based cache system
 */
public class SQLiteDatabaseManager {
    private static SQLiteDatabaseManager instance;
    private static final Object lock = new Object();

    private final String databasePath;
    private final BlockingQueue<Connection> connectionPool;
    private static final int POOL_SIZE = 10;
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;

    private volatile boolean initialized = false;

    private SQLiteDatabaseManager() {
        // Database path: ~/.damose/damose.db
        String userHome = System.getProperty("user.home");
        Path damoseDir = Paths.get(userHome, ".damose");

        try {
            Files.createDirectories(damoseDir);
        } catch (Exception e) {
            System.err.println("⚠️  Failed to create .damose directory: " + e.getMessage());
        }

        this.databasePath = damoseDir.resolve("damose.db").toString();
        this.connectionPool = new ArrayBlockingQueue<>(POOL_SIZE);

        System.out.println("📊 SQLite Database Manager initializing...");
        System.out.println("   Database location: " + this.databasePath);

        initializeDatabase();
    }

    public static synchronized SQLiteDatabaseManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new SQLiteDatabaseManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize database and connection pool
     */
    private void initializeDatabase() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Create initial connections
            for (int i = 0; i < POOL_SIZE; i++) {
                Connection conn = createConnection();
                connectionPool.offer(conn);
            }

            // Initialize schema
            initializeSchema();

            initialized = true;
            System.out.println("✅ SQLite Database initialized successfully");
            System.out.println("   Connection pool size: " + POOL_SIZE);

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize SQLite database: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Create a new database connection
     */
    private Connection createConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("journal_mode", "WAL"); // Write-Ahead Logging for better concurrency
        props.setProperty("synchronous", "NORMAL"); // Balance between safety and performance
        props.setProperty("cache_size", "10000"); // 10MB cache
        props.setProperty("temp_store", "MEMORY"); // Use memory for temp tables
        props.setProperty("busy_timeout", "30000"); // 30 second timeout for locked database

        String url = "jdbc:sqlite:" + databasePath;
        Connection conn = DriverManager.getConnection(url, props);

        // Enable foreign keys
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }

        return conn;
    }

    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        try {
            Connection conn = connectionPool.poll(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (conn == null) {
                System.err.println("⚠️  Connection pool timeout, creating new connection");
                return createConnection();
            }

            // Check if connection is still valid
            if (conn.isClosed() || !conn.isValid(5)) {
                System.out.println("🔄 Connection invalid, creating new one");
                conn = createConnection();
            }

            return conn;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for connection", e);
        }
    }

    /**
     * Return a connection to the pool
     */
    public void releaseConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed() && conn.isValid(1)) {
                    // Roll back any uncommitted transaction
                    if (!conn.getAutoCommit()) {
                        conn.rollback();
                        conn.setAutoCommit(true);
                    }

                    if (!connectionPool.offer(conn)) {
                        // Pool is full, close the connection
                        conn.close();
                    }
                } else {
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("⚠️  Error releasing connection: " + e.getMessage());
                try {
                    conn.close();
                } catch (SQLException ex) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Execute a query with automatic connection management
     */
    public <T> T executeQuery(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);

            // Set parameters
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            rs = stmt.executeQuery();
            return handler.handle(rs);

        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
            releaseConnection(conn);
        }
    }

    /**
     * Execute an update with automatic connection management
     */
    public int executeUpdate(String sql, Object... params) throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);

            // Set parameters
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            return stmt.executeUpdate();

        } finally {
            closeQuietly(stmt);
            releaseConnection(conn);
        }
    }

    /**
     * Execute a batch update with automatic connection management
     */
    public int[] executeBatch(String sql, BatchParameterSetter paramSetter) throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Start transaction

            stmt = conn.prepareStatement(sql);

            int batchCount = paramSetter.getBatchSize();
            for (int i = 0; i < batchCount; i++) {
                paramSetter.setValues(stmt, i);
                stmt.addBatch();
            }

            int[] result = stmt.executeBatch();
            conn.commit();

            return result;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // Ignore
                }
            }
            throw e;
        } finally {
            closeQuietly(stmt);
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    // Ignore
                }
            }
            releaseConnection(conn);
        }
    }

    /**
     * Execute within a transaction
     */
    public <T> T executeInTransaction(TransactionCallback<T> callback) throws SQLException {
        Connection conn = null;

        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            T result = callback.execute(conn);

            conn.commit();
            return result;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("⚠️  Error rolling back transaction: " + ex.getMessage());
                }
            }
            throw new SQLException("Transaction failed", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    // Ignore
                }
            }
            releaseConnection(conn);
        }
    }

    /**
     * Initialize database schema from SQL file
     */
    private void initializeSchema() throws SQLException {
        System.out.println("📋 Initializing database schema...");

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("db/schema.sql")) {
            if (is == null) {
                throw new SQLException("Schema file not found: db/schema.sql");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sql = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                // Skip comments and empty lines
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }

                sql.append(line).append("\n");

                // Execute statement if it ends with semicolon
                if (line.endsWith(";")) {
                    executeUpdate(sql.toString());
                    sql.setLength(0);
                }
            }

            System.out.println("✅ Database schema initialized");

        } catch (Exception e) {
            throw new SQLException("Failed to initialize schema", e);
        }
    }

    /**
     * Clear all data from database (keep schema)
     */
    public void clearAllData() throws SQLException {
        System.out.println("🗑️  Clearing all data from database...");

        executeInTransaction(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM trip_updates;");
                stmt.execute("DELETE FROM vehicle_positions;");
                stmt.execute("DELETE FROM stop_times;");
                stmt.execute("DELETE FROM shapes;");
                stmt.execute("DELETE FROM trips;");
                stmt.execute("DELETE FROM stops;");
                stmt.execute("DELETE FROM routes;");
                stmt.execute("DELETE FROM agencies;");

                // Keep metadata
                stmt.execute("DELETE FROM metadata WHERE key NOT IN ('version', 'schema_version', 'created_at');");

                // Update metadata
                stmt.execute("INSERT OR REPLACE INTO metadata (key, value, updated_at) VALUES ('last_cleared', strftime('%s', 'now'), strftime('%s', 'now'));");
            }
            return null;
        });

        System.out.println("✅ All data cleared");
    }

    /**
     * Optimize database (VACUUM)
     */
    public void optimize() throws SQLException {
        System.out.println("🔧 Optimizing database...");

        Connection conn = null;
        try {
            conn = getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("VACUUM;");
                stmt.execute("ANALYZE;");
            }
            System.out.println("✅ Database optimized");
        } finally {
            releaseConnection(conn);
        }
    }

    /**
     * Get database statistics
     */
    public DatabaseStats getStats() throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs;
                DatabaseStats stats = new DatabaseStats();

                rs = stmt.executeQuery("SELECT COUNT(*) FROM agencies;");
                if (rs.next()) stats.agenciesCount = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM routes;");
                if (rs.next()) stats.routesCount = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM stops;");
                if (rs.next()) stats.stopsCount = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM trips;");
                if (rs.next()) stats.tripsCount = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM stop_times;");
                if (rs.next()) stats.stopTimesCount = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM shapes;");
                if (rs.next()) stats.shapesCount = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM vehicle_positions;");
                if (rs.next()) stats.vehiclePositionsCount = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM trip_updates;");
                if (rs.next()) stats.tripUpdatesCount = rs.getInt(1);

                // Database file size
                rs = stmt.executeQuery("SELECT page_count * page_size as size FROM pragma_page_count(), pragma_page_size();");
                if (rs.next()) stats.databaseSizeBytes = rs.getLong(1);

                return stats;
            }
        } finally {
            releaseConnection(conn);
        }
    }

    /**
     * Close all connections and shutdown
     */
    public void shutdown() {
        System.out.println("🛑 Shutting down SQLite Database Manager...");

        Connection conn;
        while ((conn = connectionPool.poll()) != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("⚠️  Error closing connection: " + e.getMessage());
            }
        }

        initialized = false;
        System.out.println("✅ Database shutdown complete");
    }

    /**
     * Check if database is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get database path
     */
    public String getDatabasePath() {
        return databasePath;
    }

    // ===== Helper Methods =====

    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    // ===== Functional Interfaces =====

    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }

    public interface BatchParameterSetter {
        void setValues(PreparedStatement stmt, int batchIndex) throws SQLException;
        int getBatchSize();
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection conn) throws Exception;
    }

    // ===== Database Statistics =====

    public static class DatabaseStats {
        public int agenciesCount;
        public int routesCount;
        public int stopsCount;
        public int tripsCount;
        public int stopTimesCount;
        public int shapesCount;
        public int vehiclePositionsCount;
        public int tripUpdatesCount;
        public long databaseSizeBytes;

        @Override
        public String toString() {
            return String.format(
                "Database Statistics:\n" +
                "  Agencies: %,d\n" +
                "  Routes: %,d\n" +
                "  Stops: %,d\n" +
                "  Trips: %,d\n" +
                "  Stop Times: %,d\n" +
                "  Shapes: %,d\n" +
                "  Vehicle Positions: %,d\n" +
                "  Trip Updates: %,d\n" +
                "  Database Size: %.2f MB",
                agenciesCount, routesCount, stopsCount, tripsCount,
                stopTimesCount, shapesCount, vehiclePositionsCount,
                tripUpdatesCount, databaseSizeBytes / (1024.0 * 1024.0)
            );
        }
    }
}
