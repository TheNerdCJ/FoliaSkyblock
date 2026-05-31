package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple versioned database migration system.
 * This provides a foundation for schema evolution without external libraries like Flyway.
 */
public class DatabaseMigration {

    private final FoliaSkyblock plugin;
    private final DatabaseManager dbManager;

    private static final int CURRENT_SCHEMA_VERSION = 3; // Increment when adding new migrations

    public DatabaseMigration(FoliaSkyblock plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    public void runMigrations() {
        try (Connection conn = dbManager.getConnection()) {
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

    private void createSchemaVersionTable(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at INTEGER)")) {
            ps.executeUpdate();
        }
    }

    private int getCurrentVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT version FROM schema_version ORDER BY version DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("version");
            }
            return 0;
        }
    }

    private void runMigrationForVersion(Connection conn, int version) throws SQLException {
        plugin.getLogger().info("§e[DB] Running migration to version " + version + "...");

        switch (version) {
            case 1:
                // Example: Add new column if needed
                executeIfNotExists(conn, "ALTER TABLE island_prestige ADD COLUMN last_prestiged INTEGER DEFAULT 0");
                break;
            case 2:
                // Add mission booster columns (already in createTables, but here for migration path)
                executeIfNotExists(conn, "ALTER TABLE island_missions ADD COLUMN reward_booster_type TEXT");
                executeIfNotExists(conn, "ALTER TABLE island_missions ADD COLUMN reward_booster_duration INTEGER");
                break;
            case 3:
                // Future: Add worth persistence table improvements, etc.
                executeIfNotExists(conn, "CREATE TABLE IF NOT EXISTS island_worth_history (id INTEGER PRIMARY KEY, island_key TEXT, worth DOUBLE, recorded_at INTEGER)");
                break;
            default:
                plugin.getLogger().warning("[DB] Unknown migration version: " + version);
        }
    }

    private void executeIfNotExists(Connection conn, String sql) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            // Column or table likely already exists - safe to ignore for migrations
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
}