package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.auction.Auction;
import com.thenerdcj.bazaar.BazaarOrder;
import com.thenerdcj.island.Island;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class DatabaseManager {

    private final FoliaSkyblock plugin;
    private HikariDataSource dataSource;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public DatabaseManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    public void initDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + "/skyblock.db");
        config.setMaximumPoolSize(15);
        config.setMinimumIdle(3);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "300");

        try {
            dataSource = new HikariDataSource(config);
            createTables();
            plugin.getLogger().info("§a[Database] SQLite initialized successfully.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Database initialization failed", e);
        }
    }

    private void createTables() {
        // Core tables
        executeUpdate("CREATE TABLE IF NOT EXISTS islands (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uuid TEXT UNIQUE, grid_x INTEGER, grid_z INTEGER, dimension TEXT, biome TEXT, level INTEGER DEFAULT 1)");
        executeUpdate("CREATE TABLE IF NOT EXISTS island_members (island_id INTEGER, player_uuid TEXT, role TEXT, PRIMARY KEY(island_id, player_uuid))");
        executeUpdate("CREATE TABLE IF NOT EXISTS player_balances (uuid TEXT PRIMARY KEY, balance REAL DEFAULT 0)");
        executeUpdate("CREATE TABLE IF NOT EXISTS island_balances (island_id INTEGER, dimension TEXT, balance REAL DEFAULT 0, PRIMARY KEY(island_id, dimension))");
        executeUpdate("CREATE TABLE IF NOT EXISTS island_levels (island_id INTEGER PRIMARY KEY, xp INTEGER DEFAULT 0, level INTEGER DEFAULT 1)");
        executeUpdate("CREATE TABLE IF NOT EXISTS island_upgrades (island_id INTEGER, upgrade_type TEXT, level INTEGER, PRIMARY KEY(island_id, upgrade_type))");
        executeUpdate("CREATE TABLE IF NOT EXISTS player_ranks (uuid TEXT PRIMARY KEY, rank_name TEXT)");
        executeUpdate("CREATE TABLE IF NOT EXISTS island_minions (island_id INTEGER, minion_type TEXT, level INTEGER)");
        executeUpdate("CREATE TABLE IF NOT EXISTS auctions (id TEXT PRIMARY KEY, seller_uuid TEXT, item TEXT, price REAL, end_time INTEGER, sold BOOLEAN DEFAULT 0)");
        executeUpdate("CREATE TABLE IF NOT EXISTS bazaar_orders (id TEXT PRIMARY KEY, player_uuid TEXT, item TEXT, amount INTEGER, price_per_unit REAL, buy_order BOOLEAN)");
        executeUpdate("CREATE TABLE IF NOT EXISTS pending_items (uuid TEXT, item_base64 TEXT)");
        executeUpdate("CREATE TABLE IF NOT EXISTS slayer_kills (uuid TEXT, slayer_type TEXT, tier TEXT, kills INTEGER, PRIMARY KEY(uuid, slayer_type, tier))");
        executeUpdate("CREATE TABLE IF NOT EXISTS votes (voter_uuid TEXT, target_uuid TEXT, timestamp INTEGER)");

        // Hologram tables
        executeUpdate("CREATE TABLE IF NOT EXISTS holograms (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, world TEXT NOT NULL, x REAL, y REAL, z REAL, billboard TEXT DEFAULT 'CENTER', background_color TEXT, scale REAL DEFAULT 1.0, see_through BOOLEAN DEFAULT 0, shadow BOOLEAN DEFAULT 1, permission TEXT, is_dynamic BOOLEAN DEFAULT 0, dynamic_type TEXT, update_interval INTEGER DEFAULT 300)");
        executeUpdate("CREATE TABLE IF NOT EXISTS hologram_lines (holo_id INTEGER, line_index INTEGER, text TEXT, PRIMARY KEY(holo_id, line_index))");

        // ==================== PUNISHMENTS TABLE ====================
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS punishments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                target_uuid TEXT NOT NULL,
                staff_uuid TEXT,
                type TEXT NOT NULL,
                reason TEXT,
                timestamp INTEGER NOT NULL,
                duration INTEGER DEFAULT 0,
                active BOOLEAN DEFAULT 1
            )
        """);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void executeUpdate(String sql) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to execute update: " + sql + " - " + e.getMessage());
        }
    }

    // ==================== PUNISHMENT METHODS ====================

    public CompletableFuture<Boolean> logPunishment(UUID target, UUID staff, Punishment.Type type,
                                                    String reason, long durationMillis) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO punishments (target_uuid, staff_uuid, type, reason, timestamp, duration, active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """;
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, target.toString());
                ps.setString(2, staff != null ? staff.toString() : null);
                ps.setString(3, type.name());
                ps.setString(4, reason);
                ps.setLong(5, System.currentTimeMillis());
                ps.setLong(6, durationMillis);
                ps.executeUpdate();
                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log punishment: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<List<Punishment>> getPunishmentsForPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            String sql = "SELECT * FROM punishments WHERE target_uuid = ? ORDER BY timestamp DESC";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    list.add(mapResultSetToPunishment(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load punishments: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    public CompletableFuture<List<Punishment>> getActivePunishments(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            String sql = "SELECT * FROM punishments WHERE target_uuid = ? AND active = 1 ORDER BY timestamp DESC";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    Punishment p = mapResultSetToPunishment(rs);
                    if (!p.isExpired()) {
                        list.add(p);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load active punishments: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    public CompletableFuture<Boolean> expirePunishment(int punishmentId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE punishments SET active = 0 WHERE id = ?")) {
                ps.setInt(1, punishmentId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    private Punishment mapResultSetToPunishment(ResultSet rs) throws SQLException {
        return new Punishment(
                rs.getInt("id"),
                UUID.fromString(rs.getString("target_uuid")),
                rs.getString("staff_uuid") != null ? UUID.fromString(rs.getString("staff_uuid")) : null,
                Punishment.Type.valueOf(rs.getString("type")),
                rs.getString("reason"),
                rs.getLong("timestamp"),
                rs.getLong("duration"),
                rs.getBoolean("active")
        );
    }

    // ==================== ISLAND METHODS ====================

    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID owner, String dimension, String biome) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO islands (grid_x, grid_z, owner_uuid, dimension, biome) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, owner.toString());
                ps.setString(4, dimension);
                ps.setString(5, biome);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    public Island getIslandByOwner(UUID owner, World.Environment dimension) {
        String sql = "SELECT id, grid_x, grid_z, biome, level FROM islands WHERE owner_uuid = ? AND dimension = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, owner.toString());
            ps.setString(2, dimension.name());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int gridX = rs.getInt("grid_x");
                int gridZ = rs.getInt("grid_z");
                String biome = rs.getString("biome");
                int level = rs.getInt("level");

                GridPosition pos = new GridPosition(gridX, gridZ, dimension);
                Island island = new Island(pos, owner, biome, dimension);
                island.setLevel(level);
                return island;
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load island: " + e.getMessage());
        }

        return null;
    }

    public CompletableFuture<Boolean> deleteIsland(UUID owner, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                ps.setString(1, owner.toString());
                ps.setString(2, dimension.name());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    // ==================== ECONOMY ====================

    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT balance FROM player_balances WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getDouble("balance") : 0.0;
            } catch (SQLException e) {
                return 0.0;
            }
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
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return getPlayerBalance(uuid).thenCompose(current -> setPlayerBalance(uuid, current + amount));
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return addPlayerBalance(uuid, -amount);
    }

    // ==================== RANKS ====================

    public CompletableFuture<String> getCurrentRankId(UUID uuid) {
        return CompletableFuture.completedFuture("default");
    }

    public CompletableFuture<Integer> getUpvoteCount(UUID uuid) {
        return CompletableFuture.completedFuture(0);
    }

    public CompletableFuture<Boolean> setRank(UUID uuid, String rankName) {
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> addVote(UUID voter, UUID target) {
        return CompletableFuture.completedFuture(true);
    }

    // ==================== PLACEHOLDER METHODS (to be implemented) ====================

    public CompletableFuture<List<Object>> loadMinionData(String islandKey) {
        return CompletableFuture.supplyAsync(ArrayList::new, executor);
    }

    public CompletableFuture<Boolean> saveMinionData(String islandKey, int type, int level) {
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Map<String, Integer>> loadIslandUpgrades(String islandKey) {
        return CompletableFuture.supplyAsync(HashMap::new, executor);
    }

    public CompletableFuture<Boolean> saveIslandUpgrade(String islandKey, String type, int level) {
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<List<Auction>> getActiveAuctions() {
        return CompletableFuture.supplyAsync(ArrayList::new, executor);
    }

    public boolean saveAuction(Auction a) { return true; }
    public boolean updateAuction(Auction a) { return true; }
    public boolean storePendingItem(UUID u, ItemStack i) { return true; }
    public boolean markAuctionSold(String id, UUID buyer) { return true; }
    public boolean markAuctionExpired(String id) { return true; }

    public CompletableFuture<List<BazaarOrder>> getActiveBazaarOrders() {
        return CompletableFuture.supplyAsync(ArrayList::new, executor);
    }

    public boolean saveBazaarOrder(BazaarOrder o) { return true; }
    public boolean markBazaarOrderFilled(String id) { return true; }

    public boolean incrementSlayerKills(UUID uuid, String type, String tier, int amount) { return true; }

    public CompletableFuture<List<Object>> getGlobalTopSlayers(int limit) {
        return CompletableFuture.supplyAsync(ArrayList::new, executor);
    }

    public CompletableFuture<List<Object>> getTopSlayers(String type, int limit) {
        return CompletableFuture.supplyAsync(ArrayList::new, executor);
    }

    public List<TopIslandEntry> getTopIslandsByLevel(int limit) {
        return new ArrayList<>();
    }
    public void close() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                plugin.getLogger().info("§a[Database] HikariCP connection pool closed.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error closing database pool: " + e.getMessage());
        }

        try {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                plugin.getLogger().info("§a[Database] ExecutorService shut down.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error shutting down database executor: " + e.getMessage());
        }
    }
}
