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
                "CREATE TABLE IF NOT EXISTS player_balances (uuid TEXT PRIMARY KEY, balance REAL DEFAULT 0.0)",
                "CREATE TABLE IF NOT EXISTS island_upgrades (island_id TEXT, upgrade_name TEXT, level INTEGER DEFAULT 0, PRIMARY KEY (island_id, upgrade_name))",
                "CREATE TABLE IF NOT EXISTS island_settings (grid_x INTEGER, grid_z INTEGER, dimension TEXT, pvp_enabled BOOLEAN DEFAULT 0, visitors_allowed BOOLEAN DEFAULT 1, explosions_enabled BOOLEAN DEFAULT 0, fire_spread_enabled BOOLEAN DEFAULT 0, mob_spawning_enabled BOOLEAN DEFAULT 1, crop_trampling_enabled BOOLEAN DEFAULT 1, animal_spawning_enabled BOOLEAN DEFAULT 1, leaf_decay_enabled BOOLEAN DEFAULT 1, border_color TEXT DEFAULT 'BLUE', border_size INTEGER DEFAULT 100, warp_enabled BOOLEAN DEFAULT 0, warp_description TEXT DEFAULT '', PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS challenges (id TEXT PRIMARY KEY, island_id TEXT, type TEXT, category TEXT, title TEXT, description TEXT, target INTEGER, progress INTEGER DEFAULT 0, completed BOOLEAN DEFAULT 0, reward_xp INTEGER, reward_money INTEGER, created_at INTEGER, expires_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS muted_players (uuid TEXT PRIMARY KEY, muted BOOLEAN, muted_by TEXT, reason TEXT, duration INTEGER)",
                "CREATE TABLE IF NOT EXISTS player_ranks (uuid TEXT PRIMARY KEY, rank_id TEXT, upvotes INTEGER DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS rank_votes (voter_uuid TEXT, target_uuid TEXT, PRIMARY KEY (voter_uuid, target_uuid))"
        };

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : tables) stmt.execute(sql);
            plugin.getLogger().info("§aAll tables created with HikariCP.");
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

    // ====================== ISLAND METHODS ======================
    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID ownerUuid, String biomeName, org.bukkit.World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO islands (grid_x, grid_z, dimension, owner_uuid, biome_name, level, xp) VALUES (?, ?, ?, ?, ?, 1, 0.0)")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension.name());
                ps.setString(4, ownerUuid.toString());
                ps.setString(5, biomeName);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
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
                        GridPosition pos = new GridPosition(rs.getInt("grid_x"), rs.getInt("grid_z"), dimension);
                        return new com.thenerdcj.island.Island(pos, owner, rs.getString("biome_name"), dimension);
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return null;
        }, executor);
    }

    public CompletableFuture<Boolean> deleteIsland(UUID owner, org.bukkit.World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                ps.setString(1, owner.toString());
                ps.setString(2, dimension.name());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
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
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO player_balances (uuid, balance) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, balance);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return getPlayerBalance(uuid).thenCompose(current -> setPlayerBalance(uuid, current + amount));
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return getPlayerBalance(uuid).thenCompose(current -> {
            if (current >= amount) return setPlayerBalance(uuid, current - amount);
            return CompletableFuture.completedFuture(false);
        });
    }

    // ====================== ISLAND BALANCE ======================
    public CompletableFuture<Double> getIslandBalance(GridPosition pos) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT balance FROM island_balances WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                ps.setInt(1, pos.getX());
                ps.setInt(2, pos.getZ());
                ps.setString(3, pos.getDimension().name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("balance");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0.0;
        }, executor);
    }

    public CompletableFuture<Boolean> setIslandBalance(GridPosition pos, double balance) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, pos.getX());
                ps.setInt(2, pos.getZ());
                ps.setString(3, pos.getDimension().name());
                ps.setDouble(4, balance);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> addIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, current + amount));
    }

    public CompletableFuture<Boolean> removeIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> {
            if (current >= amount) return setIslandBalance(pos, current - amount);
            return CompletableFuture.completedFuture(false);
        });
    }

    // ====================== ISLAND SETTINGS ======================
    public CompletableFuture<Boolean> saveIslandSettings(GridPosition pos, com.thenerdcj.island.IslandSettings settings) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT OR REPLACE INTO island_settings 
                     (grid_x, grid_z, dimension, pvp_enabled, visitors_allowed, explosions_enabled, 
                      fire_spread_enabled, mob_spawning_enabled, crop_trampling_enabled, animal_spawning_enabled,
                      leaf_decay_enabled, border_color, border_size, warp_enabled, warp_description)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
                ps.setInt(1, pos.getX());
                ps.setInt(2, pos.getZ());
                ps.setString(3, pos.getDimension().name());
                ps.setBoolean(4, settings.isPvpEnabled());
                ps.setBoolean(5, settings.isVisitorsAllowed());
                ps.setBoolean(6, settings.isExplosionsEnabled());
                ps.setBoolean(7, settings.isFireSpreadEnabled());
                ps.setBoolean(8, settings.isMobSpawningEnabled());
                ps.setBoolean(9, settings.isCropTramplingEnabled());
                ps.setBoolean(10, settings.isAnimalSpawningEnabled());
                ps.setBoolean(11, settings.isLeafDecayEnabled());
                ps.setString(12, settings.getBorderColor());
                ps.setInt(13, settings.getBorderSize());
                ps.setBoolean(14, settings.isWarpEnabled());
                ps.setString(15, settings.getWarpDescription());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<com.thenerdcj.island.IslandSettings> loadIslandSettings(GridPosition pos) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM island_settings WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                ps.setInt(1, pos.getX());
                ps.setInt(2, pos.getZ());
                ps.setString(3, pos.getDimension().name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        com.thenerdcj.island.IslandSettings settings = new com.thenerdcj.island.IslandSettings(pos);
                        settings.setPvpEnabled(rs.getBoolean("pvp_enabled"));
                        settings.setVisitorsAllowed(rs.getBoolean("visitors_allowed"));
                        settings.setExplosionsEnabled(rs.getBoolean("explosions_enabled"));
                        settings.setFireSpreadEnabled(rs.getBoolean("fire_spread_enabled"));
                        settings.setMobSpawningEnabled(rs.getBoolean("mob_spawning_enabled"));
                        settings.setCropTramplingEnabled(rs.getBoolean("crop_trampling_enabled"));
                        settings.setAnimalSpawningEnabled(rs.getBoolean("animal_spawning_enabled"));
                        settings.setLeafDecayEnabled(rs.getBoolean("leaf_decay_enabled"));
                        settings.setBorderColor(rs.getString("border_color"));
                        settings.setBorderSize(rs.getInt("border_size"));
                        settings.setWarpEnabled(rs.getBoolean("warp_enabled"));
                        settings.setWarpDescription(rs.getString("warp_description"));
                        return settings;
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return new com.thenerdcj.island.IslandSettings(pos);
        }, executor);
    }

    // ====================== MUTED PLAYERS ======================
    public CompletableFuture<Set<UUID>> loadMutedPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> muted = new HashSet<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM muted_players WHERE muted = 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        muted.add(UUID.fromString(rs.getString("uuid")));
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return muted;
        }, executor);
    }

    public CompletableFuture<Boolean> setMuted(UUID uuid, boolean muted, UUID mutedBy, String reason, long durationSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO muted_players (uuid, muted, muted_by, reason, duration) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setBoolean(2, muted);
                ps.setString(3, mutedBy != null ? mutedBy.toString() : null);
                ps.setString(4, reason);
                ps.setLong(5, durationSeconds);
                ps.executeUpdate();
                return true;
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
            return "GUEST";
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
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO player_ranks (uuid, rank_id, upvotes) VALUES (?, ?, COALESCE((SELECT upvotes FROM player_ranks WHERE uuid = ?), 0))")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankId);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    public CompletableFuture<Boolean> voteForPlayer(UUID voter, UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO rank_votes (voter_uuid, target_uuid) VALUES (?, ?)")) {
                ps.setString(1, voter.toString());
                ps.setString(2, target.toString());
                int inserted = ps.executeUpdate();
                if (inserted > 0) {
                    try (PreparedStatement ps2 = conn.prepareStatement("UPDATE player_ranks SET upvotes = upvotes + 1 WHERE uuid = ?")) {
                        ps2.setString(1, target.toString());
                        ps2.executeUpdate();
                    }
                    return true;
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }, executor);
    }

    // ====================== TOP BALANCES ======================
    public static class TopBalanceEntry {
        public final UUID uuid;
        public final double balance;

        public TopBalanceEntry(UUID uuid, double balance) {
            this.uuid = uuid;
            this.balance = balance;
        }

        public UUID uuid() { return uuid; }
        public double balance() { return balance; }
    }

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

    // ====================== CHALLENGES ======================
    public CompletableFuture<Boolean> saveChallenge(String id, String islandId, String type, String category, String title, int target, int progress, int rewardXp, boolean completed) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT OR REPLACE INTO challenges 
                     (id, island_id, type, category, title, target, progress, reward_xp, completed)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
                ps.setString(1, id);
                ps.setString(2, islandId);
                ps.setString(3, type);
                ps.setString(4, category);
                ps.setString(5, title);
                ps.setInt(6, target);
                ps.setInt(7, progress);
                ps.setInt(8, rewardXp);
                ps.setBoolean(9, completed);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
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
                        Map<String, Object> challenge = new HashMap<>();
                        challenge.put("id", rs.getString("id"));
                        challenge.put("type", rs.getString("type"));
                        challenge.put("category", rs.getString("category"));
                        challenge.put("title", rs.getString("title"));
                        challenge.put("target", rs.getInt("target"));
                        challenge.put("progress", rs.getInt("progress"));
                        challenge.put("completed", rs.getBoolean("completed"));
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
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO island_upgrades (island_id, upgrade_name, level) VALUES (?, ?, ?)")) {
                ps.setString(1, islandId);
                ps.setString(2, upgradeName);
                ps.setInt(3, level);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
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