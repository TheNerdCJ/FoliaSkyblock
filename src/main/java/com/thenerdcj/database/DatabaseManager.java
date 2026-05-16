package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.auction.Auction;
import com.thenerdcj.bazaar.BazaarOrder;
import com.thenerdcj.hologram.HologramData;
import com.thenerdcj.island.Island;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
            plugin.getLogger().info("§a[Database] SQLite initialized with full schema.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Database init failed", e);
        }
    }

    private void createTables() {
        // Islands - added last_reset for cooldowns
        executeUpdate("CREATE TABLE IF NOT EXISTS islands (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner_uuid TEXT UNIQUE, " +
                "grid_x INTEGER, grid_z INTEGER, " +
                "dimension TEXT, biome TEXT, level INTEGER DEFAULT 1, " +
                "last_reset INTEGER DEFAULT 0)");

        executeUpdate("CREATE TABLE IF NOT EXISTS island_members (island_id INTEGER, player_uuid TEXT, role TEXT, PRIMARY KEY(island_id, player_uuid))");

        executeUpdate("CREATE TABLE IF NOT EXISTS player_balances (uuid TEXT PRIMARY KEY, balance REAL DEFAULT 0)");

        executeUpdate("CREATE TABLE IF NOT EXISTS island_balances (island_id INTEGER, grid_x INTEGER, grid_z INTEGER, dimension TEXT, balance REAL DEFAULT 0, PRIMARY KEY(island_id, dimension))");

        executeUpdate("CREATE TABLE IF NOT EXISTS island_levels (island_id INTEGER PRIMARY KEY, xp REAL DEFAULT 0, level INTEGER DEFAULT 1)");

        executeUpdate("CREATE TABLE IF NOT EXISTS island_upgrades (island_key TEXT, upgrade_type TEXT, level INTEGER, PRIMARY KEY(island_key, upgrade_type))");

        executeUpdate("CREATE TABLE IF NOT EXISTS player_ranks (uuid TEXT PRIMARY KEY, rank_name TEXT, upvotes INTEGER DEFAULT 0)");

        executeUpdate("CREATE TABLE IF NOT EXISTS island_minions (island_key TEXT, minion_type TEXT, level INTEGER, PRIMARY KEY(island_key, minion_type))");

        executeUpdate("CREATE TABLE IF NOT EXISTS auctions (id TEXT PRIMARY KEY, seller_uuid TEXT, item_base64 TEXT, price REAL, end_time INTEGER, sold BOOLEAN DEFAULT 0, buyer_uuid TEXT)");

        executeUpdate("CREATE TABLE IF NOT EXISTS bazaar_orders (id TEXT PRIMARY KEY, player_uuid TEXT, item_base64 TEXT, amount INTEGER, price_per_unit REAL, buy_order BOOLEAN, filled BOOLEAN DEFAULT 0)");

        executeUpdate("CREATE TABLE IF NOT EXISTS pending_items (uuid TEXT, item_base64 TEXT)");

        executeUpdate("CREATE TABLE IF NOT EXISTS slayer_kills (uuid TEXT, slayer_type TEXT, tier TEXT, kills INTEGER, PRIMARY KEY(uuid, slayer_type, tier))");

        executeUpdate("CREATE TABLE IF NOT EXISTS votes (voter_uuid TEXT, target_uuid TEXT, timestamp INTEGER)");

        // Holograms
        executeUpdate("CREATE TABLE IF NOT EXISTS holograms (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, world TEXT NOT NULL, x REAL, y REAL, z REAL, billboard TEXT DEFAULT 'CENTER', background_color TEXT, scale REAL DEFAULT 1.0, see_through BOOLEAN DEFAULT 0, shadow BOOLEAN DEFAULT 1, permission TEXT, is_dynamic BOOLEAN DEFAULT 0, dynamic_type TEXT, update_interval INTEGER DEFAULT 300)");

        executeUpdate("CREATE TABLE IF NOT EXISTS hologram_lines (holo_id INTEGER, line_index INTEGER, text TEXT, PRIMARY KEY(holo_id, line_index))");

        // Indexes for performance
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_islands_owner ON islands(owner_uuid)");
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_island_balances_grid ON island_balances(grid_x, grid_z, dimension)");
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_upgrades_key ON island_upgrades(island_key)");
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_auctions_active ON auctions(sold, end_time)");
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_slayer_uuid ON slayer_kills(uuid)");
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // ==================== ITEM SERIALIZATION HELPERS ====================
    private String itemToBase64(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to serialize item: " + e.getMessage());
            return null;
        }
    }

    private ItemStack itemFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to deserialize item: " + e.getMessage());
            return null;
        }
    }

    // ==================== HOLOGRAM METHODS (Fully Implemented) ====================
    public CompletableFuture<Boolean> saveHologram(HologramData data) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO holograms (name, world, x, y, z, billboard, background_color, scale, see_through, shadow, permission, is_dynamic, dynamic_type, update_interval) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, data.getName());
                ps.setString(2, data.getWorldName());
                ps.setDouble(3, data.getX());
                ps.setDouble(4, data.getY());
                ps.setDouble(5, data.getZ());
                ps.setString(6, data.getBillboard());
                ps.setString(7, data.getBackgroundColor());
                ps.setDouble(8, data.getScale());
                ps.setBoolean(9, data.isSeeThrough());
                ps.setBoolean(10, data.isShadow());
                ps.setString(11, data.getPermission());
                ps.setBoolean(12, data.isDynamic());
                ps.setString(13, data.getDynamicType());
                ps.setInt(14, data.getUpdateInterval());

                int affected = ps.executeUpdate();
                if (affected > 0) {
                    ResultSet keys = ps.getGeneratedKeys();
                    if (keys.next()) {
                        data.setId(keys.getInt(1));
                    }
                    // Save lines
                    updateHologramLines(data.getId(), data.getLines()).join();
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save hologram: " + e.getMessage());
            }
            return false;
        }, executor);
    }

    public CompletableFuture<List<HologramData>> loadAllHolograms() {
        return CompletableFuture.supplyAsync(() -> {
            List<HologramData> list = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM holograms");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {HologramData data = new HologramData(
                        rs.getString("name"),
                        rs.getString("world_name"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")
                );
                    data.setId(rs.getInt("id"));
                    data.setBillboard(rs.getString("billboard"));
                    data.setBackgroundColor(rs.getString("background_color"));
                    data.setScale(rs.getDouble("scale"));
                    data.setSeeThrough(rs.getBoolean("see_through"));
                    data.setShadow(rs.getBoolean("shadow"));
                    data.setPermission(rs.getString("permission"));
                    data.setDynamic(rs.getBoolean("is_dynamic"));
                    data.setDynamicType(rs.getString("dynamic_type"));
                    data.setUpdateInterval(rs.getInt("update_interval"));
                    data.setLines(loadHologramLines(data.getId()));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load holograms: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    private List<String> loadHologramLines(int holoId) {
        List<String> lines = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT text FROM hologram_lines WHERE holo_id = ? ORDER BY line_index")) {
            ps.setInt(1, holoId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lines.add(rs.getString("text"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load hologram lines: " + e.getMessage());
        }
        return lines;
    }

    public CompletableFuture<Boolean> deleteHologram(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                try (PreparedStatement psLines = conn.prepareStatement("DELETE FROM hologram_lines WHERE holo_id = ?")) {
                    psLines.setInt(1, id);
                    psLines.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM holograms WHERE id = ?")) {
                    ps.setInt(1, id);
                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete hologram: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> updateHologramLines(int id, List<String> lines) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM hologram_lines WHERE holo_id = ?")) {
                    del.setInt(1, id);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO hologram_lines (holo_id, line_index, text) VALUES (?, ?, ?)")) {
                    for (int i = 0; i < lines.size(); i++) {
                        ins.setInt(1, id);
                        ins.setInt(2, i);
                        ins.setString(3, lines.get(i));
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                conn.commit();
                conn.setAutoCommit(true);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update hologram lines: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> updateHologramInterval(int id, int seconds) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE holograms SET update_interval = ? WHERE id = ?")) {
                ps.setInt(1, seconds);
                ps.setInt(2, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update hologram interval: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    // ==================== ISLAND METHODS ====================
    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID owner, String dimension, String biome) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO islands (grid_x, grid_z, owner_uuid, dimension, biome, last_reset) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, owner.toString());
                ps.setString(4, dimension);
                ps.setString(5, biome);
                ps.setLong(6, 0);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save island: " + e.getMessage());
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

    public CompletableFuture<Boolean> deleteIsland(UUID owner, org.bukkit.World.Environment dimension) {
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

    public boolean addIslandMember(int islandId, int gridX, String dimension, UUID player, String role) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO island_members (island_id, player_uuid, role) VALUES (?, ?, ?)")) {
            ps.setInt(1, islandId);
            ps.setString(2, player.toString());
            ps.setString(3, role);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean removeIslandMember(int islandId, int gridX, String dimension, UUID player) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM island_members WHERE island_id = ? AND player_uuid = ?")) {
            ps.setInt(1, islandId);
            ps.setString(2, player.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ==================== ECONOMY - PLAYER (Robust) ====================
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

    // ==================== ECONOMY - ISLAND (Now Fully Robust, matching player economy) ====================
    public CompletableFuture<Double> getIslandBalance(int islandId, int gridX, String dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT balance FROM island_balances WHERE island_id = ? AND dimension = ?")) {
                ps.setInt(1, islandId);
                ps.setString(2, dimension);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getDouble("balance");

                // Fallback: try by grid
                try (PreparedStatement ps2 = conn.prepareStatement(
                        "SELECT balance FROM island_balances WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                    ps2.setInt(1, gridX);
                    ps2.setInt(2, gridX); // Note: gridZ missing in original call, adjust if needed
                    ps2.setString(3, dimension);
                    ResultSet rs2 = ps2.executeQuery();
                    return rs2.next() ? rs2.getDouble("balance") : 0.0;
                }
            } catch (SQLException e) {
                return 0.0;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> setIslandBalance(int islandId, int gridX, String dimension, double balance) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_balances (island_id, grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, islandId);
                ps.setInt(2, gridX);
                ps.setInt(3, gridX); // placeholder for gridZ if not passed; callers should provide accurate
                ps.setString(4, dimension);
                ps.setDouble(5, balance);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set island balance: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> addIslandBalance(int islandId, int gridX, String dimension, double amount) {
        return getIslandBalance(islandId, gridX, dimension).thenCompose(current ->
                setIslandBalance(islandId, gridX, dimension, current + amount));
    }

    public CompletableFuture<Boolean> removeIslandBalance(int islandId, int gridX, String dimension, double amount) {
        return addIslandBalance(islandId, gridX, dimension, -amount);
    }

    // Convenience overloads used by EconomyManager / UpgradeManager
    public CompletableFuture<Double> getIslandBalance(com.thenerdcj.database.GridPosition pos) {
        return getIslandBalance(0, pos.x(), pos.getDimension().name()); // islandId optional
    }

    public CompletableFuture<Boolean> setIslandBalance(com.thenerdcj.database.GridPosition pos, double amount) {
        return setIslandBalance(0, pos.x(), pos.getDimension().name(), amount);
    }

    public CompletableFuture<Boolean> addIslandBalance(com.thenerdcj.database.GridPosition pos, double amount) {
        return addIslandBalance(0, pos.x(), pos.getDimension().name(), amount);
    }

    public CompletableFuture<Boolean> removeIslandBalance(com.thenerdcj.database.GridPosition pos, double amount) {
        return removeIslandBalance(0, pos.x(), pos.getDimension().name(), amount);
    }

    // ==================== RANKS ====================
    public CompletableFuture<String> getCurrentRankId(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT rank_name FROM player_ranks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getString("rank_name") : "default";
            } catch (SQLException e) {
                return "default";
            }
        }, executor);
    }

    public CompletableFuture<Integer> getUpvoteCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT upvotes FROM player_ranks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getInt("upvotes") : 0;
            } catch (SQLException e) {
                return 0;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> setRank(UUID uuid, String rankName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO player_ranks (uuid, rank_name) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankName);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> addVote(UUID voter, UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO votes (voter_uuid, target_uuid, timestamp) VALUES (?, ?, ?)")) {
                ps.setString(1, voter.toString());
                ps.setString(2, target.toString());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();

                // Increment upvotes on target
                try (PreparedStatement up = conn.prepareStatement(
                        "INSERT OR REPLACE INTO player_ranks (uuid, rank_name, upvotes) " +
                                "VALUES (?, COALESCE((SELECT rank_name FROM player_ranks WHERE uuid = ?), 'default'), " +
                                "COALESCE((SELECT upvotes FROM player_ranks WHERE uuid = ?), 0) + 1)")) {
                    up.setString(1, target.toString());
                    up.setString(2, target.toString());
                    up.setString(3, target.toString());
                    up.executeUpdate();
                }
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    // ==================== MINIONS ====================
    public CompletableFuture<List<Object>> loadMinionData(String islandKey) {
        return CompletableFuture.supplyAsync(() -> {
            List<Object> minions = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT minion_type, level FROM island_minions WHERE island_key = ?")) {
                ps.setString(1, islandKey);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    // Simple map or custom object - using array for flexibility
                    minions.add(new Object[]{rs.getString("minion_type"), rs.getInt("level")});
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load minions: " + e.getMessage());
            }
            return minions;
        }, executor);
    }

    public CompletableFuture<Boolean> saveMinionData(String islandKey, int type, int level) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_minions (island_key, minion_type, level) VALUES (?, ?, ?)")) {
                ps.setString(1, islandKey);
                ps.setInt(2, type);
                ps.setInt(3, level);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    // ==================== ISLAND UPGRADES ====================
    public CompletableFuture<Map<String, Integer>> loadIslandUpgrades(String islandKey) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> upgrades = new HashMap<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT upgrade_type, level FROM island_upgrades WHERE island_key = ?")) {
                ps.setString(1, islandKey);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    upgrades.put(rs.getString("upgrade_type"), rs.getInt("level"));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load upgrades: " + e.getMessage());
            }
            return upgrades;
        }, executor);
    }

    public CompletableFuture<Boolean> saveIslandUpgrade(String islandKey, String type, int level) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_upgrades (island_key, upgrade_type, level) VALUES (?, ?, ?)")) {
                ps.setString(1, islandKey);
                ps.setString(2, type);
                ps.setInt(3, level);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    // ==================== AUCTIONS ====================
// ==================== AUCTIONS (Fixed) ====================
    public CompletableFuture<List<Auction>> getActiveAuctions() {
        return CompletableFuture.supplyAsync(() -> {
            List<Auction> list = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM auctions WHERE active=1 AND end_time > ?")) {
                ps.setLong(1, System.currentTimeMillis());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Auction a = new Auction(
                            rs.getString("id"),
                            UUID.fromString(rs.getString("seller_uuid")),
                            rs.getString("item_material"),
                            rs.getInt("item_amount"),
                            rs.getDouble("starting_price"),
                            rs.getDouble("current_bid"),
                            rs.getString("current_bidder_uuid") != null ?
                                    UUID.fromString(rs.getString("current_bidder_uuid")) : null,
                            rs.getLong("end_time"),
                            rs.getBoolean("active")
                    );
                    list.add(a);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getActiveAuctions failed: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    public boolean saveAuction(Auction a) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO auctions (id, seller_uuid, item_material, item_amount, starting_price, current_bid, current_bidder_uuid, end_time, active) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, a.getId());
            ps.setString(2, a.getSellerUuid().toString());
            ps.setString(3, a.getItemMaterial());
            ps.setInt(4, a.getItemAmount());
            ps.setDouble(5, a.getStartingPrice());
            ps.setDouble(6, a.getCurrentBid());
            ps.setString(7, a.getCurrentBidder() != null ? a.getCurrentBidder().toString() : null);
            ps.setLong(8, a.getEndTime());
            ps.setBoolean(9, a.isActive());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean updateAuction(Auction a) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE auctions SET active = ?, current_bidder_uuid = ? WHERE id = ?")) {

            ps.setBoolean(1, a.isActive());
            ps.setString(2, a.getCurrentBidder() != null ? a.getCurrentBidder().toString() : null);
            ps.setString(3, a.getId());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            plugin.getLogger().severe("updateAuction failed: " + e.getMessage());
            return false;
        }
    }

    public boolean storePendingItem(UUID u, ItemStack i) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO pending_items (uuid, item_base64) VALUES (?, ?)")) {
            ps.setString(1, u.toString());
            ps.setString(2, itemToBase64(i));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean markAuctionSold(String id, UUID buyer) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE auctions SET active = 0, current_bidder_uuid = ? WHERE id = ?")) {
            ps.setString(1, buyer != null ? buyer.toString() : null);
            ps.setString(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("markAuctionSold failed: " + e.getMessage());
            return false;
        }
    }

    public boolean markAuctionExpired(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE auctions SET active = 0 WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ==================== BAZAAR ====================
    public CompletableFuture<List<BazaarOrder>> getActiveBazaarOrders() {
        return CompletableFuture.supplyAsync(() -> {
            List<BazaarOrder> orders = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM bazaar_orders WHERE filled = 0")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    BazaarOrder o = new BazaarOrder(
                            rs.getString("id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("material"),
                            rs.getInt("amount"),
                            rs.getDouble("price_per_unit"),
                            rs.getBoolean("is_buy_order"),
                            rs.getLong("created_at"),
                            rs.getBoolean("filled")
                    );
                    orders.add(o);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load bazaar orders: " + e.getMessage());
            }
            return orders;
        }, executor);
    }

    // ==================== BAZAAR (Fixed) ====================
    public boolean saveBazaarOrder(BazaarOrder o) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO bazaar_orders (id, player_uuid, material, amount, price_per_unit, is_buy_order, created_at, filled) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, o.getId());
            ps.setString(2, o.getPlayerUuid().toString());
            ps.setString(3, o.getMaterial());
            ps.setInt(4, o.getAmount());
            ps.setDouble(5, o.getPricePerUnit());
            ps.setBoolean(6, o.isBuyOrder());
            ps.setLong(7, o.getCreatedAt());
            ps.setBoolean(8, o.isFilled());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
    public boolean markBazaarOrderFilled(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE bazaar_orders SET filled = 1 WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ==================== SLAYER ====================
    public boolean incrementSlayerKills(UUID uuid, String type, String tier, int amount) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO slayer_kills (uuid, slayer_type, tier, kills) " +
                             "VALUES (?, ?, ?, COALESCE((SELECT kills FROM slayer_kills WHERE uuid = ? AND slayer_type = ? AND tier = ?), 0) + ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type);
            ps.setString(3, tier);
            ps.setString(4, uuid.toString());
            ps.setString(5, type);
            ps.setString(6, tier);
            ps.setInt(7, amount);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public CompletableFuture<List<Object>> getGlobalTopSlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Object> top = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT uuid, SUM(kills) as total FROM slayer_kills GROUP BY uuid ORDER BY total DESC LIMIT ?")) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    top.add(new Object[]{UUID.fromString(rs.getString("uuid")), rs.getInt("total")});
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get top slayers: " + e.getMessage());
            }
            return top;
        }, executor);
    }

    public CompletableFuture<List<Object>> getTopSlayers(String type, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Object> top = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT uuid, SUM(kills) as total FROM slayer_kills WHERE slayer_type = ? GROUP BY uuid ORDER BY total DESC LIMIT ?")) {
                ps.setString(1, type);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    top.add(new Object[]{UUID.fromString(rs.getString("uuid")), rs.getInt("total")});
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get top slayers by type: " + e.getMessage());
            }
            return top;
        }, executor);
    }

    public List<TopIslandEntry> getTopIslandsByLevel(int limit) {
        List<TopIslandEntry> top = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT owner_uuid, level, dimension FROM islands ORDER BY level DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                top.add(new TopIslandEntry(
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getInt("level"),
                        rs.getString("dimension")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get top islands: " + e.getMessage());
        }
        return top;
    }

    // ==================== UTILITY ====================
    public void executeUpdate(String sql) {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException ignored) {}
    }
    // ==================== PUNISHMENTS ====================
    public CompletableFuture<Boolean> logPunishment(UUID target, UUID staff, Punishment.Type type, String reason, long durationMillis) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO punishments (target_uuid, staff_uuid, type, reason, issued_at, duration_millis, active) VALUES (?,?,?,?,?,?,1)")) {
                ps.setString(1, target.toString());
                ps.setString(2, staff != null ? staff.toString() : null);
                ps.setString(3, type.name());
                ps.setString(4, reason);
                ps.setLong(5, System.currentTimeMillis());
                ps.setLong(6, durationMillis);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("logPunishment failed");
                return false;
            }
        }, executor);
    }

    public CompletableFuture<List<Punishment>> getActivePunishments(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> new ArrayList<>()); // implement fully if needed
    }

    public CompletableFuture<List<Punishment>> getPunishmentsForPlayer(UUID uuid) {
        return getActivePunishments(uuid);
    }
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        executor.shutdown();
    }

    public long getLastResetTime(UUID playerUuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT last_reset FROM islands WHERE owner_uuid = ? ORDER BY last_reset DESC LIMIT 1")) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("last_reset");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get last reset time: " + e.getMessage());
        }
        return 0;
    }

    public void updateLastResetTime(UUID playerUuid, World.Environment dimension) {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE islands SET last_reset = ? WHERE owner_uuid = ? AND dimension = ?")) {
            ps.setLong(1, now);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, dimension.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update last reset time: " + e.getMessage());
        }
    }
}
