package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
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

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
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
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO player_balances (uuid, balance) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, balance);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return getPlayerBalance(uuid).thenCompose(current -> setPlayerBalance(uuid, current + amount));
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return getPlayerBalance(uuid).thenCompose(current -> setPlayerBalance(uuid, Math.max(0, current - amount)));
    }

    // ====================== ISLAND BALANCE ======================
    public CompletableFuture<Double> getIslandBalance(com.thenerdcj.database.GridPosition pos) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT balance FROM island_balances WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                ps.setInt(1, pos.x());
                ps.setInt(2, pos.z());
                ps.setString(3, pos.dimension().name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("balance");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0.0;
        }, executor);
    }

    public CompletableFuture<Boolean> setIslandBalance(com.thenerdcj.database.GridPosition pos, double balance) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, pos.x());
                ps.setInt(2, pos.z());
                ps.setString(3, pos.dimension().name());
                ps.setDouble(4, balance);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> addIslandBalance(com.thenerdcj.database.GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, current + amount));
    }

    public CompletableFuture<Boolean> removeIslandBalance(com.thenerdcj.database.GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, Math.max(0, current - amount)));
    }

    // ====================== ISLAND SYSTEM ======================
    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID ownerUuid, String biome, org.bukkit.World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO islands (grid_x, grid_z, dimension, owner_uuid, biome_name) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension.name());
                ps.setString(4, ownerUuid.toString());
                ps.setString(5, biome);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<com.thenerdcj.island.Island> getIslandByOwner(UUID owner, org.bukkit.World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                ps.setString(1, owner.toString());
                ps.setString(2, dimension.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new com.thenerdcj.island.Island(
                                new GridPosition(rs.getInt("grid_x"), rs.getInt("grid_z"), dimension),
                                owner,
                                rs.getString("biome_name"),
                                dimension
                        );
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return null;
        }, executor);
    }

    public CompletableFuture<Boolean> deleteIsland(UUID owner, org.bukkit.World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                    ps.setString(1, owner.toString());
                    ps.setString(2, dimension.name());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM island_members WHERE dimension = ? AND member_uuid = ?")) {
                    ps.setString(1, dimension.name());
                    ps.setString(2, owner.toString());
                    ps.executeUpdate();
                }
                return true;
            } catch (SQLException e) { e.printStackTrace(); return false; }
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

    public CompletableFuture<Boolean> setRank(UUID uuid, String rankId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO player_ranks (uuid, rank_id) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> voteForPlayer(UUID voter, UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO player_votes (voter_uuid, target_uuid) VALUES (?, ?)")) {
                    ps.setString(1, voter.toString());
                    ps.setString(2, target.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE player_ranks SET upvotes = upvotes + 1 WHERE uuid = ?")) {
                    ps.setString(1, target.toString());
                    ps.executeUpdate();
                }
                return true;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    // ====================== TOP BALANCES ======================
    public CompletableFuture<List<TopBalanceEntry>> getTopBalances(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<TopBalanceEntry> top = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid, balance FROM player_balances ORDER BY balance DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        top.add(new TopBalanceEntry(UUID.fromString(rs.getString("uuid")), rs.getDouble("balance")));
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return top;
        }, executor);
    }

    public record TopBalanceEntry(UUID uuid, double balance) {}

    // ====================== MUTE SYSTEM ======================
    public CompletableFuture<Set<UUID>> loadMutedPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> muted = new HashSet<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM muted_players WHERE expires_at = 0 OR expires_at > ?")) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) muted.add(UUID.fromString(rs.getString("uuid")));
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return muted;
        }, executor);
    }

    public CompletableFuture<Boolean> setMuted(UUID uuid, boolean muted, UUID mutedBy, String reason, long durationSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                if (muted) {
                    long expiresAt = durationSeconds > 0 ? System.currentTimeMillis() + (durationSeconds * 1000L) : 0;
                    try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO muted_players (uuid, muted_at, expires_at, muted_by, reason) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setLong(2, expiresAt);
                        ps.setString(3, mutedBy != null ? mutedBy.toString() : "console");
                        ps.setString(4, reason != null ? reason : "");
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM muted_players WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    }
                }
                return true;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    // ====================== CHALLENGE SYSTEM (Persistent) ======================
    public CompletableFuture<Boolean> saveChallenge(String id, String islandId, String type, String category,
                                                    String description, int target, int progress, int rewardXP, boolean completed) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO challenges (id, island_id, type, category, description, target, progress, reward_xp, completed) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, islandId);
                ps.setString(3, type);
                ps.setString(4, category);
                ps.setString(5, description);
                ps.setInt(6, target);
                ps.setInt(7, progress);
                ps.setInt(8, rewardXP);
                ps.setBoolean(9, completed);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<List<Map<String, Object>>> loadChallengesForIsland(String islandId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> challenges = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM challenges WHERE island_id = ?")) {
                ps.setString(1, islandId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", rs.getString("id"));
                        data.put("type", rs.getString("type"));
                        data.put("category", rs.getString("category"));
                        data.put("description", rs.getString("description"));
                        data.put("target", rs.getInt("target"));
                        data.put("progress", rs.getInt("progress"));
                        data.put("reward_xp", rs.getInt("reward_xp"));
                        data.put("completed", rs.getBoolean("completed"));
                        challenges.add(data);
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
                         "INSERT OR REPLACE INTO island_upgrades (island_id, upgrade_name, level) VALUES (?, ?, ?)")) {
                ps.setString(1, islandId);
                ps.setString(2, upgradeName);
                ps.setInt(3, level);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Map<String, Integer>> loadIslandUpgrades(String islandId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> upgrades = new HashMap<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT upgrade_name, level FROM island_upgrades WHERE island_id = ?")) {
                ps.setString(1, islandId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        upgrades.put(rs.getString("upgrade_name"), rs.getInt("level"));
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return upgrades;
        }, executor);
    }

    public CompletableFuture<Integer> getIslandUpgradeLevel(String islandId, String upgradeName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT level FROM island_upgrades WHERE island_id = ? AND upgrade_name = ?")) {
                ps.setString(1, islandId);
                ps.setString(2, upgradeName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("level");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0;
        }, executor);
    }

    // ====================== SHUTDOWN ======================
    public void close() {
        executor.shutdown();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}