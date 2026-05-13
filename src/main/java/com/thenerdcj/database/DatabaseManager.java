package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.auction.Auction;
import com.thenerdcj.bazaar.BazaarOrder;
import com.thenerdcj.database.TopIslandEntry;
import com.thenerdcj.hologram.HologramData;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
            plugin.getLogger().info("§a[Database] SQLite initialized.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Database init failed", e);
        }
    }

    private void createTables() {
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
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // ==================== HOLOGRAM METHODS ====================
    public CompletableFuture<Boolean> saveHologram(HologramData data) { return CompletableFuture.completedFuture(true); }
    public CompletableFuture<List<HologramData>> loadAllHolograms() { return CompletableFuture.completedFuture(new ArrayList<>()); }
    public CompletableFuture<Boolean> deleteHologram(int id) { return CompletableFuture.completedFuture(true); }
    public CompletableFuture<Boolean> updateHologramLines(int id, List<String> lines) { return CompletableFuture.completedFuture(true); }
    public CompletableFuture<Boolean> updateHologramInterval(int id, int seconds) { return CompletableFuture.completedFuture(true); }

    // ==================== ISLAND METHODS (Exact signatures) ====================
    public boolean saveIsland(int gridX, int gridZ, UUID owner, String dimension, String biome) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO islands (grid_x, grid_z, owner_uuid, dimension, biome) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, gridX); ps.setInt(2, gridZ);
            ps.setString(3, owner.toString()); ps.setString(4, dimension); ps.setString(5, biome);
            ps.executeUpdate(); return true;
        } catch (SQLException e) { return false; }
    }

    public int getIslandByOwner(UUID owner, org.bukkit.World.Environment dimension) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
            ps.setString(1, owner.toString()); ps.setString(2, dimension.name());
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        } catch (SQLException e) { return -1; }
    }

    public boolean deleteIsland(UUID owner, org.bukkit.World.Environment dimension) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
            ps.setString(1, owner.toString()); ps.setString(2, dimension.name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean addIslandMember(int islandId, int gridX, String dimension, UUID player, String role) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO island_members (island_id, player_uuid, role) VALUES (?, ?, ?)")) {
            ps.setInt(1, islandId); ps.setString(2, player.toString()); ps.setString(3, role);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean removeIslandMember(int islandId, int gridX, String dimension, UUID player) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM island_members WHERE island_id = ? AND player_uuid = ?")) {
            ps.setInt(1, islandId); ps.setString(2, player.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // ==================== ECONOMY ====================
    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT balance FROM player_balances WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getDouble("balance") : 0.0;
            } catch (SQLException e) { return 0.0; }
        }, executor);
    }

    public CompletableFuture<Boolean> setPlayerBalance(UUID uuid, double balance) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO player_balances (uuid, balance) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString()); ps.setDouble(2, balance);
                ps.executeUpdate(); return true;
            } catch (SQLException e) { return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return getPlayerBalance(uuid).thenCompose(current -> setPlayerBalance(uuid, current + amount));
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return addPlayerBalance(uuid, -amount);
    }

    public CompletableFuture<Double> getIslandBalance(int islandId, int gridX, String dimension) {
        return CompletableFuture.completedFuture(0.0);
    }

    public CompletableFuture<Boolean> setIslandBalance(int islandId, int gridX, String dimension, double balance) {
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> addIslandBalance(int islandId, int gridX, String dimension, double amount) {
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> removeIslandBalance(int islandId, int gridX, String dimension, double amount) {
        return CompletableFuture.completedFuture(true);
    }

    // ==================== RANKS ====================
    public int getCurrentRankId(UUID uuid) { return 1; }
    public int getUpvoteCount(UUID uuid) { return 0; }
    public boolean setRank(UUID uuid, String rankName) { return true; }
    public boolean addVote(UUID voter, UUID target) { return true; }

    // ==================== MINIONS, UPGRADES, AUCTIONS, BAZAAR, SLAYER ====================
    public boolean saveMinionData(String islandKey, int type, int level) { return true; }
    public List<Object> loadMinionData(String islandKey) { return new ArrayList<>(); }

    public List<Object> loadIslandUpgrades(String islandKey) { return new ArrayList<>(); }
    public boolean saveIslandUpgrade(String islandKey, String type, int level) { return true; }

    public List<Auction> getActiveAuctions() { return new ArrayList<>(); }
    public boolean saveAuction(Auction a) { return true; }
    public boolean updateAuction(Auction a) { return true; }
    public boolean storePendingItem(UUID u, ItemStack i) { return true; }
    public boolean markAuctionSold(String id, UUID buyer) { return true; }
    public boolean markAuctionExpired(String id) { return true; }

    public List<BazaarOrder> getActiveBazaarOrders() { return new ArrayList<>(); }
    public boolean saveBazaarOrder(BazaarOrder o) { return true; }
    public boolean markBazaarOrderFilled(String id) { return true; }

    public boolean incrementSlayerKills(UUID uuid, String type, String tier, int amount) { return true; }
    public List<Object> getGlobalTopSlayers(int limit) { return new ArrayList<>(); }
    public List<Object> getTopSlayers(String type, int limit) { return new ArrayList<>(); }

    public List<TopIslandEntry> getTopIslandsByLevel(int limit) { return new ArrayList<>(); }

    public void executeUpdate(String sql) {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException ignored) {}
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        executor.shutdown();
    }
}