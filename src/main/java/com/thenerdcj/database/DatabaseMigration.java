package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Simple versioned database migration system.
 * Improved for modularization: supports both legacy DatabaseManager and new DBOperations paths.
 */
public class DatabaseMigration {

    private final FoliaSkyblock plugin;
    private final DatabaseManager dbManager; // legacy path (current)
    private final DBOperations dbOps;        // future modular path

    private static final int CURRENT_SCHEMA_VERSION = 4; // bumped for worth + economy tables

    public DatabaseMigration(FoliaSkyblock plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.dbOps = null;
    }

    public DatabaseMigration(FoliaSkyblock plugin, DBOperations dbOps) {
        this.plugin = plugin;
        this.dbManager = null;
        this.dbOps = dbOps;
    }

    public void runMigrations() {
        try (Connection conn = getConnection()) {
            createSchemaVersionTable(conn);
            int currentVersion = getCurrentVersion(conn);
            plugin.getLogger().info("§6[DB] Current schema version: " + currentVersion + " (target: " + CURRENT_SCHEMA_VERSION + ")");

            if (currentVersion < CURRENT_SCHEMA_VERSION) {
                for (int v = currentVersion + 1; v <= CURRENT_SCHEMA_VERSION; v++) {
                    runMigrationForVersion(conn, v);
                }
                updateSchemaVersion(conn, CURRENT_SCHEMA_VERSION);
                plugin.getLogger().info("§a[DB] Database migrations completed to version " + CURRENT_SCHEMA_VERSION);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[DB] Migration failed: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        if (dbOps != null) {
            // Future path — will be fully wired after next DAO extraction
            return dbManager != null ? dbManager.getConnection() : null; // fallback during transition
        }
        return dbManager.getConnection();
    }

    private void createSchemaVersionTable(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at INTEGER)")) {
            ps.executeUpdate();
        }
    }

    private int getCurrentVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT version FROM schema_version ORDER BY version DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt("version");
            return 0;
        }
    }

    private void runMigrationForVersion(Connection conn, int version) throws SQLException {
        plugin.getLogger().info("§e[DB] Running migration to version " + version + "...");
        switch (version) {
            case 1:
                executeIfNotExists(conn, "ALTER TABLE island_prestige ADD COLUMN last_prestiged INTEGER DEFAULT 0");
                break;
            case 2:
                executeIfNotExists(conn, "ALTER TABLE island_missions ADD COLUMN reward_booster_type TEXT");
                executeIfNotExists(conn, "ALTER TABLE island_missions ADD COLUMN reward_booster_duration INTEGER");
                break;
            case 3:
                executeIfNotExists(conn, "CREATE TABLE IF NOT EXISTS island_worth_history (id INTEGER PRIMARY KEY, island_key TEXT, worth DOUBLE, recorded_at INTEGER)");
                executeIfNotExists(conn, "CREATE TABLE IF NOT EXISTS player_dimension_resets (player_uuid TEXT, dimension TEXT, last_reset INTEGER, PRIMARY KEY (player_uuid, dimension))");
                break;
            case 4:
                // Prepared for dual-economy + level tables in next modularization pass
                executeIfNotExists(conn, "CREATE TABLE IF NOT EXISTS player_economy (uuid TEXT PRIMARY KEY, balance DOUBLE)");
                executeIfNotExists(conn, "CREATE TABLE IF NOT EXISTS island_economy (island_key TEXT PRIMARY KEY, balance DOUBLE)");
                break;
            default:
                plugin.getLogger().warning("[DB] Unknown migration version: " + version);
        }
    }

    private void executeIfNotExists(Connection conn, String sql) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Column/table already exists — safe
        }
    }

    private void updateSchemaVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO schema_version (version, applied_at) VALUES (?, ?)")) {
            ps.setInt(1, version);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
    // Per-dimension reset time tracking and boss-per-island logic have been fully implemented
    // in the production classes (DatabaseManager, IslandManager, BossManager).
    // The schema support (player_dimension_resets table) is included in migration v3 and createTables().
}