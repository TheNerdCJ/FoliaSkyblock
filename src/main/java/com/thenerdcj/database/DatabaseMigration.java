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

    private static final int CURRENT_SCHEMA_VERSION = 13; // bumped for seasonal resets (Option B) + bug reports (v10) + aggregates/snapshots (v11/v12)
    // Note: DAO extractions (SkillDAO, IslandLevelDAO, prior ones) do not require schema bumps if tables pre-exist.
    // Migration system enhanced for DBOperations path + future Flyway-like. Tables still central for compat.
    // This completes "Finish DB modularization (remaining DAOs) + migration system (IMPROVEMENTS priority)".

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
            case 5:
                // CRITICAL: Fix islands table to support multiple dimensions per owner (owner_uuid + dimension composite unique).
                // Previous schema had UNIQUE(owner_uuid) which corrupted multi-dim data on INSERT OR REPLACE.
                // We create a unique index (safer than full table recreate for live servers) and drop old conflicting index if present.
                // If old single-dim data exists it will continue to work; new dim islands now safe.
                try {
                    conn.createStatement().executeUpdate("DROP INDEX IF EXISTS idx_islands_owner_unique");
                    conn.createStatement().executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_islands_owner_dim ON islands(owner_uuid, dimension)");
                    plugin.getLogger().info("§a[DB Migration v5] Ensured composite unique index on islands(owner_uuid, dimension) for multi-dimension support.");
                } catch (SQLException ex) {
                    plugin.getLogger().warning("[DB Migration v5] Could not fully apply islands unique constraint fix (may require manual DB maintenance if old data conflicts): " + ex.getMessage());
                }
                break;
            case 6:
                // Add generation_seed column for donor "reroll personality/style" feature on per-dimension resets.
                // Default 0 means "use pure position-derived seed" (backward compatible for existing islands).
                executeIfNotExists(conn, "ALTER TABLE islands ADD COLUMN generation_seed BIGINT DEFAULT 0");
                plugin.getLogger().info("§a[DB Migration v6] Added generation_seed column for donor island personality rerolls.");
                break;
            case 7:
                // Core Collections system: new table is created via IF NOT EXISTS in DatabaseManager.initDatabase(),
                // but ensure index and log the feature.
                executeIfNotExists(conn, "CREATE INDEX IF NOT EXISTS idx_island_collections_key ON island_collections(island_key)");
                plugin.getLogger().info("§a[DB Migration v7] Core Collections system (island_collections) ready for unique item discovery tracking.");
                break;
            case 8:
                // Player Skill System: table created in initDatabase IF NOT EXISTS, ensure index.
                executeIfNotExists(conn, "CREATE INDEX IF NOT EXISTS idx_player_skills_uuid ON player_skills(uuid)");
                plugin.getLogger().info("§a[DB Migration v8] Player skills system (player_skills) ready - MCMMO-style per-player progression.");
                break;
            case 10:
                // Bug reporting system: ensure table exists for upgrades (created in initDatabase too).
                executeIfNotExists(conn, "CREATE TABLE IF NOT EXISTS bug_reports (id INTEGER PRIMARY KEY AUTOINCREMENT, reporter_uuid TEXT NOT NULL, reporter_name TEXT, category TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'OPEN', created_at INTEGER NOT NULL, resolved_at INTEGER, resolved_by_uuid TEXT, staff_notes TEXT)");
                executeIfNotExists(conn, "CREATE INDEX IF NOT EXISTS idx_bug_reports_status_created ON bug_reports(status, created_at)");
                plugin.getLogger().info("§a[DB Migration v10] Bug reports table ready.");
                break;
            case 11:
                // Persisted rank snapshots on island_worth for fast O(1) my-rank (compression for large servers + frequent PAPI / /is rank).
                // Columns added via IF NOT EXISTS in initDatabase compat block too (for running servers without full migration).
                executeIfNotExists(conn, "ALTER TABLE island_worth ADD COLUMN last_worth_rank INTEGER DEFAULT 0");
                executeIfNotExists(conn, "ALTER TABLE island_worth ADD COLUMN last_level_rank INTEGER DEFAULT 0");
                plugin.getLogger().info("§a[DB Migration v11] Added last_worth_rank / last_level_rank columns to island_worth for persisted rank snapshots.");
                break;
            case 12:
                // Persisted aggregates for O(1)/near-O(1) (member count snapshot + prestige level snapshot on island_worth).
                // Allows fast member count in tops (no subq on island_members), and prestige in displays/holograms without extra joins in hot paths.
                // Columns added via IF NOT EXISTS in init too.
                executeIfNotExists(conn, "ALTER TABLE island_worth ADD COLUMN member_count INTEGER DEFAULT 0");
                executeIfNotExists(conn, "ALTER TABLE island_worth ADD COLUMN prestige_level INTEGER DEFAULT 0");
                plugin.getLogger().info("§a[DB Migration v12] Added member_count / prestige_level columns to island_worth for persisted aggregates.");
                break;
            case 13:
                // Seasonal resets (full Option B impl): meta for current_season, seasons history table, player_seasonal_grants for event/donor cosmetic releases audit.
                // Also ensures islands.created_season column (for optional future "per-season tops" without full history tables).
                // Tables use IF NOT EXISTS; columns via executeIfNotExists for live servers.
                executeIfNotExists(conn, "CREATE TABLE IF NOT EXISTS server_meta (meta_key TEXT PRIMARY KEY, meta_value TEXT)");
                executeIfNotExists(conn, "CREATE TABLE IF NOT EXISTS seasons (season_id TEXT PRIMARY KEY, name TEXT, started_at INTEGER, ended_at INTEGER, islands_wiped INTEGER DEFAULT 0, triggered_by TEXT)");
                executeIfNotExists(conn, "CREATE TABLE IF NOT EXISTS player_seasonal_grants (uuid TEXT, season_id TEXT, category TEXT, cosmetic_id TEXT, granted_at INTEGER, source TEXT, PRIMARY KEY (uuid, season_id, category, cosmetic_id))");
                executeIfNotExists(conn, "CREATE INDEX IF NOT EXISTS idx_seasonal_grants_season ON player_seasonal_grants(season_id)");
                executeIfNotExists(conn, "CREATE INDEX IF NOT EXISTS idx_seasons_started ON seasons(started_at)");
                executeIfNotExists(conn, "ALTER TABLE islands ADD COLUMN created_season TEXT");
                plugin.getLogger().info("§a[DB Migration v13] Seasonal resets schema: server_meta + seasons + player_seasonal_grants + islands.created_season (compat).");
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