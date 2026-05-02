package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.World;

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
                "CREATE TABLE IF NOT EXISTS player_balances (uuid TEXT PRIMARY KEY, balance REAL DEFAULT 0.0)",
                "CREATE TABLE IF NOT EXISTS island_warps (grid_x INTEGER, grid_z INTEGER, dimension TEXT, world TEXT, x REAL, y REAL, z REAL, yaw REAL, pitch REAL, enabled BOOLEAN DEFAULT 0, PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS island_ratings (grid_x INTEGER, grid_z INTEGER, dimension TEXT, player_uuid TEXT, rating INTEGER, timestamp INTEGER, PRIMARY KEY (grid_x, grid_z, dimension, player_uuid))",
                "CREATE TABLE IF NOT EXISTS island_upgrades (island_id TEXT, upgrade_name TEXT, level INTEGER DEFAULT 0, PRIMARY KEY (island_id, upgrade_name))",
                "CREATE TABLE IF NOT EXISTS player_ranks (uuid TEXT PRIMARY KEY, rank TEXT, votes INTEGER DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS muted_players (uuid TEXT PRIMARY KEY, muted_at TIMESTAMP, expires_at INTEGER, muted_by TEXT, reason TEXT)",
                "CREATE TABLE IF NOT EXISTS challenges (id TEXT PRIMARY KEY, island_id TEXT, type TEXT, category TEXT, description TEXT, target INTEGER, progress INTEGER, reward_xp INTEGER, completed BOOLEAN)"
        };
        for (String sql : tables) executeUpdate(sql);
    }

    public Connection getConnection() throws SQLException { return dataSource.getConnection(); }

    public void executeUpdate(String sql) {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
    }

    // ====================== ISLAND METHODS ======================
    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID ownerUuid, String biomeName, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO islands (grid_x, grid_z, dimension, owner_uuid, biome_name, level, xp) VALUES (?, ?, ?, ?, ?, 1, 0.0)")) {
                ps.setInt(1, gridX); ps.setInt(2, gridZ); ps.setString(3, dimension.name());
                ps.setString(4, ownerUuid.toString()); ps.setString(5, biomeName);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { plugin.getLogger().severe("Failed to save island: " + e.getMessage()); return false; }
        }, executor);
    }

    public CompletableFuture<com.thenerdcj.island.Island> getIslandByOwner(UUID ownerUuid, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                ps.setString(1, ownerUuid.toString()); ps.setString(2, dimension.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        GridPosition pos = new GridPosition(rs.getInt("grid_x"), rs.getInt("grid_z"), dimension);
                        return new com.thenerdcj.island.Island(pos, UUID.fromString(rs.getString("owner_uuid")), rs.getString("biome_name"), dimension);
                    }
                }
            } catch (SQLException e) { plugin.getLogger().severe("Failed to load island: " + e.getMessage()); }
            return null;
        }, executor);
    }

    public CompletableFuture<List<com.thenerdcj.island.Island>> getTopIslands(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<com.thenerdcj.island.Island> islands = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM islands ORDER BY level DESC, xp DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        GridPosition pos = new GridPosition(rs.getInt("grid_x"), rs.getInt("grid_z"), World.Environment.valueOf(rs.getString("dimension")));
                        islands.add(new com.thenerdcj.island.Island(pos, UUID.fromString(rs.getString("owner_uuid")), rs.getString("biome_name"), pos.getDimension()));
                    }
                }
            } catch (SQLException e) { plugin.getLogger().severe("Failed to load top islands: " + e.getMessage()); }
            return islands;
        }, executor);
    }

    public CompletableFuture<Boolean> deleteIsland(UUID ownerUuid, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                ps.setString(1, ownerUuid.toString()); ps.setString(2, dimension.name());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { plugin.getLogger().severe("Failed to delete island: " + e.getMessage()); return false; }
        }, executor);
    }

    // ====================== PLAYER BALANCE ======================
    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT balance FROM player_balances WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getDouble("balance"); }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0.0;
        }, executor);
    }

    public CompletableFuture<Boolean> setPlayerBalance(UUID uuid, double balance) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO player_balances (uuid, balance) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString()); ps.setDouble(2, balance);
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
    public CompletableFuture<Double> getIslandBalance(GridPosition pos) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT balance FROM island_balances WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                ps.setInt(1, pos.x()); ps.setInt(2, pos.z()); ps.setString(3, pos.dimension().name());
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getDouble("balance"); }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0.0;
        }, executor);
    }

    public CompletableFuture<Boolean> setIslandBalance(GridPosition pos, double balance) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, pos.x()); ps.setInt(2, pos.z()); ps.setString(3, pos.dimension().name()); ps.setDouble(4, balance);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> addIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, current + amount));
    }

    public CompletableFuture<Boolean> removeIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, Math.max(0, current - amount)));
    }

    // ====================== TOP BALANCES ======================
    public static class TopBalanceEntry {
        public final UUID uuid; public final double balance;
        public TopBalanceEntry(UUID uuid, double balance) { this.uuid = uuid; this.balance = balance; }
        public UUID uuid() { return uuid; } public double balance() { return balance; }
    }

    public CompletableFuture<List<TopBalanceEntry>> getTopBalances(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<TopBalanceEntry> entries = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid, balance FROM player_balances ORDER BY balance DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) entries.add(new TopBalanceEntry(UUID.fromString(rs.getString("uuid")), rs.getDouble("balance")));
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return entries;
        }, executor);
    }

    // ====================== CHALLENGE METHODS ======================
    public CompletableFuture<Boolean> saveChallenge(String id, String islandId, String type, String category, String title, int target, int progress, int rewardXp, boolean completed) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO challenges (id, island_id, type, category, description, target, progress, reward_xp, completed) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id); ps.setString(2, islandId); ps.setString(3, type); ps.setString(4, category);
                ps.setString(5, title); ps.setInt(6, target); ps.setInt(7, progress); ps.setInt(8, rewardXp); ps.setBoolean(9, completed);
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
                        data.put("id", rs.getString("id")); data.put("type", rs.getString("type"));
                        data.put("category", rs.getString("category")); data.put("description", rs.getString("description"));
                        data.put("target", rs.getInt("target")); data.put("progress", rs.getInt("progress"));
                        data.put("reward_xp", rs.getInt("reward_xp")); data.put("completed", rs.getBoolean("completed"));
                        challenges.add(data);
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return challenges;
        }, executor);
    }

    // ====================== RANK/VOTING METHODS ======================
    public CompletableFuture<Boolean> voteForPlayer(UUID voterUuid, UUID targetUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE player_ranks SET votes = votes + 1 WHERE uuid = ?")) {
                ps.setString(1, targetUuid.toString()); return ps.executeUpdate() > 0;
            } catch (SQLException e) { plugin.getLogger().severe("Failed to vote: " + e.getMessage()); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> setRank(UUID playerUuid, String rank) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO player_ranks (uuid, rank) VALUES (?, ?)")) {
                ps.setString(1, playerUuid.toString()); ps.setString(2, rank); return ps.executeUpdate() > 0;
            } catch (SQLException e) { plugin.getLogger().severe("Failed to set rank: " + e.getMessage()); return false; }
        }, executor);
    }

    public String getCurrentRankId(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT rank FROM player_ranks WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString("rank"); }
        } catch (SQLException e) { e.printStackTrace(); }
        return "DEFAULT";
    }

    public int getUpvoteCount(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT votes FROM player_ranks WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt("votes"); }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    // ====================== MUTE METHODS ======================
    public CompletableFuture<Boolean> setMuted(UUID uuid, boolean muted, UUID mutedBy, String reason, long durationSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                if (muted) {
                    long expiresAt = durationSeconds > 0 ? System.currentTimeMillis() + (durationSeconds * 1000L) : 0;
                    try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO muted_players (uuid, muted_at, expires_at, muted_by, reason) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)")) {
                        ps.setString(1, uuid.toString()); ps.setLong(2, expiresAt);
                        ps.setString(3, mutedBy != null ? mutedBy.toString() : "console");
                        ps.setString(4, reason != null ? reason : ""); ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM muted_players WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString()); ps.executeUpdate();
                    }
                }
                return true;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Set<UUID>> loadMutedPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> muted = new HashSet<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM muted_players WHERE expires_at = 0 OR expires_at > ?")) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) muted.add(UUID.fromString(rs.getString("uuid"))); }
            } catch (SQLException e) { e.printStackTrace(); }
            return muted;
        }, executor);
    }

    // ====================== ISLAND UPGRADE METHODS ======================
    public Map<String, Integer> loadIslandUpgrades(String islandId) {
        Map<String, Integer> upgrades = new HashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT upgrade_name, level FROM island_upgrades WHERE island_id = ?")) {
            ps.setString(1, islandId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) upgrades.put(rs.getString("upgrade_name"), rs.getInt("level")); }
        } catch (SQLException e) { e.printStackTrace(); }
        return upgrades;
    }

    public void saveIslandUpgrade(String islandId, String upgrade, int level) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO island_upgrades (island_id, upgrade_name, level) VALUES (?, ?, ?)")) {
            ps.setString(1, islandId); ps.setString(2, upgrade); ps.setInt(3, level); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void saveIslandUpgrade(String islandId, String upgrade, Integer level) {
        saveIslandUpgrade(islandId, upgrade, level != null ? level : 0);
    }
    public CompletableFuture<Boolean> addVote(UUID targetUuid, UUID voterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO player_votes (target_uuid, voter_uuid, vote_time) VALUES (?, ?, ?)")) {
                ps.setString(1, targetUuid.toString());
                ps.setString(2, voterUuid.toString());
                ps.setLong(3, System.currentTimeMillis());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    // ====================== SHUTDOWN ======================
    public void close() {
        executor.shutdown();
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

}