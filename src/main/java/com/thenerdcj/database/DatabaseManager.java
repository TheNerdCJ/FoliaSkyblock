package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {

    private final FoliaSkyblock plugin;
    private HikariDataSource dataSource;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public DatabaseManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        initDatabase();
        createTables();
    }

    private void initDatabase() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + "/skyblock.db");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("§aHikariCP connection pool initialized!");
        } catch (Exception e) {
            plugin.getLogger().severe("§cFailed to initialize HikariCP!");
            e.printStackTrace();
        }
    }

    private void createTables() {
        String[] tables = {
                "CREATE TABLE IF NOT EXISTS islands (grid_x INTEGER, grid_z INTEGER, dimension TEXT, owner_uuid TEXT, biome_name TEXT, level INTEGER DEFAULT 1, xp REAL DEFAULT 0.0, PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS island_members (grid_x INTEGER, grid_z INTEGER, dimension TEXT, member_uuid TEXT, rank TEXT, PRIMARY KEY (grid_x, grid_z, dimension, member_uuid))",
                "CREATE TABLE IF NOT EXISTS island_balances (grid_x INTEGER, grid_z INTEGER, dimension TEXT, balance REAL DEFAULT 0.0, PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS island_levels (grid_x INTEGER, grid_z INTEGER, dimension TEXT, level INTEGER DEFAULT 1, xp REAL DEFAULT 0.0, PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS player_balances (uuid TEXT PRIMARY KEY, balance REAL DEFAULT 0.0)",
                "CREATE TABLE IF NOT EXISTS player_ranks (uuid TEXT PRIMARY KEY, rank_id TEXT DEFAULT 'member', upvotes INTEGER DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS player_votes (voter_uuid TEXT, target_uuid TEXT, PRIMARY KEY (voter_uuid, target_uuid))",
                "CREATE TABLE IF NOT EXISTS muted_players (uuid TEXT PRIMARY KEY, muted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMP, muted_by TEXT, reason TEXT)",
                "CREATE TABLE IF NOT EXISTS challenges (id TEXT PRIMARY KEY, island_id TEXT, type TEXT, category TEXT, description TEXT, target INTEGER, progress INTEGER DEFAULT 0, reward_xp INTEGER, completed BOOLEAN DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                "CREATE TABLE IF NOT EXISTS island_upgrades (island_id TEXT, upgrade_name TEXT, level INTEGER DEFAULT 0, PRIMARY KEY (island_id, upgrade_name))"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : tables) stmt.executeUpdate(sql);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_balances_uuid ON player_balances(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_island_balances_grid ON island_balances(grid_x, grid_z, dimension)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_islands_owner ON islands(owner_uuid, dimension)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_ranks_uuid ON player_ranks(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_muted_players_expires ON muted_players(expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_balances_balance ON player_balances(balance DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_island_balances_balance ON island_balances(balance DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_island_upgrades_island ON island_upgrades(island_id)");

            plugin.getLogger().info("§aAll tables and indexes created with HikariCP.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public CompletableFuture<Boolean> executeUpdate(String sql) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to execute update: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Execute update with parameters (for ChestShop and other systems)
     */
    public CompletableFuture<Boolean> executeUpdateAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to execute update: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    /**
     * Execute query with parameters and callback
     */
    public void executeQueryAsync(String sql, java.util.function.Consumer<ResultSet> callback, Object... params) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    callback.accept(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to execute query: " + e.getMessage());
            }
        }, executor);
    }

    // ====================== PLAYER BALANCE ======================
    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT balance FROM player_balances WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("balance");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0.0;
        }, executor);
    }

    public CompletableFuture<Boolean> setPlayerBalance(UUID uuid, double balance) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO player_balances (uuid, balance) VALUES (?, ?) " +
                                 "ON CONFLICT(uuid) DO UPDATE SET balance = ?")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, balance);
                ps.setDouble(3, balance);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO player_balances (uuid, balance) VALUES (?, ?) " +
                                 "ON CONFLICT(uuid) DO UPDATE SET balance = balance + ?")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, amount);
                ps.setDouble(3, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE player_balances SET balance = MAX(0, balance - ?) WHERE uuid = ?")) {
                ps.setDouble(1, amount);
                ps.setString(2, uuid.toString());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    // ====================== ISLAND BALANCE ======================
    public CompletableFuture<Double> getIslandBalance(int gridX, int gridZ, String dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT balance FROM island_balances WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("balance");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0.0;
        }, executor);
    }

    public CompletableFuture<Boolean> setIslandBalance(int gridX, int gridZ, String dimension, double balance) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?) " +
                                 "ON CONFLICT(grid_x, grid_z, dimension) DO UPDATE SET balance = ?")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension);
                ps.setDouble(4, balance);
                ps.setDouble(5, balance);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> addIslandBalance(int gridX, int gridZ, String dimension, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?) " +
                                 "ON CONFLICT(grid_x, grid_z, dimension) DO UPDATE SET balance = balance + ?")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension);
                ps.setDouble(4, amount);
                ps.setDouble(5, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> removeIslandBalance(int gridX, int gridZ, String dimension, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE island_balances SET balance = MAX(0, balance - ?) WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                ps.setDouble(1, amount);
                ps.setInt(2, gridX);
                ps.setInt(3, gridZ);
                ps.setString(4, dimension);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    // ====================== ISLAND DATA ======================
    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID owner, String biome, String dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO islands (grid_x, grid_z, dimension, owner_uuid, biome_name) VALUES (?, ?, ?, ?, ?) " +
                                 "ON CONFLICT(grid_x, grid_z, dimension) DO UPDATE SET owner_uuid = ?, biome_name = ?")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension);
                ps.setString(4, owner.toString());
                ps.setString(5, biome);
                ps.setString(6, owner.toString());
                ps.setString(7, biome);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> updateIslandLevel(int gridX, int gridZ, String dimension, int level, double xp) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO island_levels (grid_x, grid_z, dimension, level, xp) VALUES (?, ?, ?, ?, ?) " +
                                 "ON CONFLICT(grid_x, grid_z, dimension) DO UPDATE SET level = ?, xp = ?")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension);
                ps.setInt(4, level);
                ps.setDouble(5, xp);
                ps.setInt(6, level);
                ps.setDouble(7, xp);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    // ====================== ISLAND MEMBERS ======================
    public CompletableFuture<Boolean> addIslandMember(int gridX, int gridZ, String dimension, UUID member, String rank) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO island_members (grid_x, grid_z, dimension, member_uuid, rank) VALUES (?, ?, ?, ?, ?) " +
                                 "ON CONFLICT(grid_x, grid_z, dimension, member_uuid) DO UPDATE SET rank = ?")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension);
                ps.setString(4, member.toString());
                ps.setString(5, rank);
                ps.setString(6, rank);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> removeIslandMember(int gridX, int gridZ, String dimension, UUID member) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM island_members WHERE grid_x = ? AND grid_z = ? AND dimension = ? AND member_uuid = ?")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension);
                ps.setString(4, member.toString());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    // ====================== RANK SYSTEM ======================
    public CompletableFuture<String> getCurrentRankId(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT rank_id FROM player_ranks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("rank_id");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return "member";
        }, executor);
    }

    public CompletableFuture<Boolean> setRank(UUID uuid, String rankId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO player_ranks (uuid, rank_id) VALUES (?, ?) " +
                                 "ON CONFLICT(uuid) DO UPDATE SET rank_id = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankId);
                ps.setString(3, rankId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Integer> getUpvoteCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT upvotes FROM player_ranks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("upvotes");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0;
        }, executor);
    }

    public CompletableFuture<Boolean> addVote(UUID voterUuid, UUID targetUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO player_votes (voter_uuid, target_uuid) VALUES (?, ?)")) {
                ps.setString(1, voterUuid.toString());
                ps.setString(2, targetUuid.toString());
                boolean success = ps.executeUpdate() > 0;
                if (success) {
                    try (PreparedStatement updatePs = conn.prepareStatement(
                            "UPDATE player_ranks SET upvotes = upvotes + 1 WHERE uuid = ?")) {
                        updatePs.setString(1, targetUuid.toString());
                        updatePs.executeUpdate();
                    }
                }
                return success;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    // ====================== MUTE SYSTEM ======================
    public CompletableFuture<Boolean> setMuted(UUID uuid, boolean muted, UUID mutedBy, String reason, long expiresAt) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                if (muted) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO muted_players (uuid, muted_by, reason, expires_at) VALUES (?, ?, ?, ?) " +
                                    "ON CONFLICT(uuid) DO UPDATE SET muted_by = ?, reason = ?, expires_at = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, mutedBy != null ? mutedBy.toString() : null);
                        ps.setString(3, reason);
                        ps.setLong(4, expiresAt);
                        ps.setString(5, mutedBy != null ? mutedBy.toString() : null);
                        ps.setString(6, reason);
                        ps.setLong(7, expiresAt);
                        return ps.executeUpdate() > 0;
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM muted_players WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        return ps.executeUpdate() > 0;
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> isMuted(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT 1 FROM muted_players WHERE uuid = ? AND (expires_at IS NULL OR expires_at > ?)")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    // ====================== CHALLENGE SYSTEM ======================
    public CompletableFuture<Boolean> saveChallenge(String id, String islandId, String type, String category,
                                                    String description, int target, int rewardXp) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO challenges (id, island_id, type, category, description, target, reward_xp) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, islandId);
                ps.setString(3, type);
                ps.setString(4, category);
                ps.setString(5, description);
                ps.setInt(6, target);
                ps.setInt(7, rewardXp);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> updateChallengeProgress(String id, int progress, boolean completed) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE challenges SET progress = ?, completed = ? WHERE id = ?")) {
                ps.setInt(1, progress);
                ps.setBoolean(2, completed);
                ps.setString(3, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<List<Map<String, Object>>> getActiveChallenges(String islandId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> challenges = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM challenges WHERE island_id = ? AND completed = FALSE")) {
                ps.setString(1, islandId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> challenge = new HashMap<>();
                        challenge.put("id", rs.getString("id"));
                        challenge.put("type", rs.getString("type"));
                        challenge.put("category", rs.getString("category"));
                        challenge.put("description", rs.getString("description"));
                        challenge.put("target", rs.getInt("target"));
                        challenge.put("progress", rs.getInt("progress"));
                        challenge.put("reward_xp", rs.getInt("reward_xp"));
                        challenges.add(challenge);
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return challenges;
        }, executor);
    }

    // ====================== ISLAND UPGRADES ======================
    public CompletableFuture<Boolean> saveIslandUpgrade(String islandId, String upgradeName, int level) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO island_upgrades (island_id, upgrade_name, level) VALUES (?, ?, ?) " +
                                 "ON CONFLICT(island_id, upgrade_name) DO UPDATE SET level = ?")) {
                ps.setString(1, islandId);
                ps.setString(2, upgradeName);
                ps.setInt(3, level);
                ps.setInt(4, level);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Integer> getIslandUpgradeLevel(String islandId, String upgradeName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT level FROM island_upgrades WHERE island_id = ? AND upgrade_name = ?")) {
                ps.setString(1, islandId);
                ps.setString(2, upgradeName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("level");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0;
        }, executor);
    }

    // ====================== SLAYER LEADERBOARD ======================
    public CompletableFuture<Boolean> incrementSlayerKills(UUID playerUuid, String playerName,
                                                           String entityType, int tier) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO slayer_kills (player_uuid, player_name, entity_type, tier, total_kills, last_kill) " +
                                 "VALUES (?, ?, ?, ?, 1, ?) " +
                                 "ON CONFLICT(player_uuid, entity_type) DO UPDATE SET " +
                                 "total_kills = total_kills + 1, last_kill = ?, tier = MAX(tier, ?), player_name = ?")) {
                long now = System.currentTimeMillis();
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, entityType);
                ps.setInt(4, tier);
                ps.setLong(5, now);
                ps.setLong(6, now);
                ps.setInt(7, tier);
                ps.setString(8, playerName);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to increment slayer kills: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<List<Map<String, Object>>> getTopSlayers(String entityType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> leaders = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT player_uuid, player_name, entity_type, tier, total_kills, last_kill " +
                                 "FROM slayer_kills WHERE entity_type = ? ORDER BY total_kills DESC, tier DESC LIMIT ?")) {
                ps.setString(1, entityType);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> leader = new HashMap<>();
                        leader.put("playerUuid", UUID.fromString(rs.getString("player_uuid")));
                        leader.put("playerName", rs.getString("player_name"));
                        leader.put("entityType", rs.getString("entity_type"));
                        leader.put("tier", rs.getInt("tier"));
                        leader.put("totalKills", rs.getInt("total_kills"));
                        leader.put("lastKill", rs.getLong("last_kill"));
                        leaders.add(leader);
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return leaders;
        }, executor);
    }

    public CompletableFuture<List<Map<String, Object>>> getGlobalTopSlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> leaders = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT player_uuid, player_name, MAX(tier) as tier, SUM(total_kills) as total_kills " +
                                 "FROM slayer_kills GROUP BY player_uuid ORDER BY total_kills DESC, tier DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> leader = new HashMap<>();
                        leader.put("playerUuid", UUID.fromString(rs.getString("player_uuid")));
                        leader.put("playerName", rs.getString("player_name"));
                        leader.put("tier", rs.getInt("tier"));
                        leader.put("totalKills", rs.getInt("total_kills"));
                        leaders.add(leader);
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return leaders;
        }, executor);
    }

    // ====================== SLAYER TABLES ======================
    public void createSlayerTables() {
        String sql = """
            CREATE TABLE IF NOT EXISTS slayer_kills (
                player_uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                tier INTEGER NOT NULL DEFAULT 0,
                total_kills INTEGER NOT NULL DEFAULT 0,
                last_kill BIGINT NOT NULL,
                UNIQUE(player_uuid, entity_type)
            )
            """;
        executeUpdate(sql);
    }
}