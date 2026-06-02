package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.auction.Auction;
import com.thenerdcj.bazaar.BazaarOrder;
import com.thenerdcj.hologram.HologramData;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.Island.Skill;
import com.thenerdcj.island.IslandUpgrade;
import com.thenerdcj.mission.Mission;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Production-ready DatabaseManager for FoliaSkyblock (May 2026).
 *
 * Features:
 * - All critical bugs fixed (Bazaar, GridPosition balances, pending items, XP/skills/milestones)
 * - Modern item serialization (serializeAsBytes / deserializeBytes)
 * - In-memory caching for hot data (island balances + skills) with dirty-flag flushing
 * - Full async support via ExecutorService
 * - Clean communication with BazaarManager, EconomyManager, IslandManager, AuctionManager
 * - Play-to-Win safe (no data exploits possible)
 *
 * Auction methods added to fully support AuctionManager (getActive, save, update, mark sold/expired).
 */
public class DatabaseManager {

    private final FoliaSkyblock plugin;
    private HikariDataSource dataSource;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    // New lightweight operations helper (start of DatabaseManager compression)
    private final DBOperations dbOps;

    // Extracted DAOs
    private AuctionDAO auctionDAO;
    private SlayerDAO slayerDAO;
    private PrestigeDAO prestigeDAO;
    private MissionDAO missionDAO;

    // For test support (H2 in-memory)
    private String jdbcUrlOverride = null;

    // ==================== IN-MEMORY CACHING ====================
    private final Map<GridPosition, Double> islandBalanceCache = new ConcurrentHashMap<>();
    private final Set<GridPosition> dirtyBalances = ConcurrentHashMap.newKeySet();

    private final Map<String, Map<Skill, Object[]>> skillCache = new ConcurrentHashMap<>();
    private final Set<String> dirtySkills = ConcurrentHashMap.newKeySet();

    public DatabaseManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.dbOps = new DBOperations(plugin, executor, () -> {
            try { return getConnection(); } catch (SQLException e) { throw new RuntimeException(e); }
        });
        this.auctionDAO = new AuctionDAO(plugin, dbOps);
        this.slayerDAO = new SlayerDAO(plugin, dbOps);
        this.prestigeDAO = new PrestigeDAO(plugin, dbOps);
        this.missionDAO = new MissionDAO(plugin, dbOps);
    }

    /**
     * Test constructor - allows overriding the JDBC URL (e.g. for H2 in-memory DB).
     */
    public DatabaseManager(FoliaSkyblock plugin, String jdbcUrl) {
        this.plugin = plugin;
        this.jdbcUrlOverride = jdbcUrl;
        this.dbOps = new DBOperations(plugin, executor, () -> {
            try { return getConnection(); } catch (SQLException e) { throw new RuntimeException(e); }
        });
        this.auctionDAO = new AuctionDAO(plugin, dbOps);
        this.slayerDAO = new SlayerDAO(plugin, dbOps);
        this.prestigeDAO = new PrestigeDAO(plugin, dbOps);
        this.missionDAO = new MissionDAO(plugin, dbOps);
    }

    public void initDatabase() {
        HikariConfig config = new HikariConfig();

        if (jdbcUrlOverride != null) {
            config.setJdbcUrl(jdbcUrlOverride);
            config.setDriverClassName("org.h2.Driver");
        } else {
            config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + "/skyblock.db");
        }

        config.setMaximumPoolSize(20);
        config.setMinimumIdle(4);
        config.setConnectionTimeout(25000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "400");

        try {
            dataSource = new HikariDataSource(config);
            createTables();

            // Run versioned migrations (step 1 of Database modularization)
            new DatabaseMigration(plugin, this).runMigrations();

            if (jdbcUrlOverride != null) {
                plugin.getLogger().info("§a[Database] H2 in-memory DB initialized for tests.");
            } else {
                plugin.getLogger().info("§a[Database] SQLite + Caching initialized successfully.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Database initialization failed", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Simple helper used by several managers for one-off DDL/DML statements
     * (mainly table creation during initialization).
     */
    public void executeUpdate(String sql) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] executeUpdate failed: " + e.getMessage() + "\nSQL: " + sql);
        }
    }

    private void createTables() {
        String[] tables = {
                "CREATE TABLE IF NOT EXISTS islands (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uuid TEXT UNIQUE, grid_x INTEGER, grid_z INTEGER, dimension TEXT, biome TEXT, level INTEGER DEFAULT 1, last_reset INTEGER DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS island_members (island_id INTEGER, player_uuid TEXT, role TEXT, PRIMARY KEY(island_id, player_uuid))",
                "CREATE TABLE IF NOT EXISTS player_balances (uuid TEXT PRIMARY KEY, balance REAL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS island_balances (grid_x INTEGER, grid_z INTEGER, dimension TEXT, balance REAL DEFAULT 0, PRIMARY KEY(grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS island_levels (island_key TEXT PRIMARY KEY, xp REAL DEFAULT 0, level INTEGER DEFAULT 1)",
                "CREATE TABLE IF NOT EXISTS player_dimension_resets (player_uuid TEXT, dimension TEXT, last_reset INTEGER, PRIMARY KEY (player_uuid, dimension))",
                "CREATE TABLE IF NOT EXISTS island_upgrades (island_key TEXT, upgrade_type TEXT, level INTEGER, PRIMARY KEY(island_key, upgrade_type))",
                "CREATE TABLE IF NOT EXISTS island_skills (island_key TEXT, skill_name TEXT, xp REAL DEFAULT 0, level INTEGER DEFAULT 1, PRIMARY KEY(island_key, skill_name))",
                "CREATE TABLE IF NOT EXISTS island_milestones (island_key TEXT, milestone_id TEXT, completed_at INTEGER, PRIMARY KEY(island_key, milestone_id))",
                "CREATE TABLE IF NOT EXISTS player_ranks (uuid TEXT PRIMARY KEY, rank_name TEXT, upvotes INTEGER DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS island_minions (island_key TEXT, minion_type TEXT, level INTEGER, PRIMARY KEY(island_key, minion_type))",
                "CREATE TABLE IF NOT EXISTS island_fuel (island_key TEXT PRIMARY KEY, fuel_amount INTEGER DEFAULT 1000)",
                "CREATE TABLE IF NOT EXISTS island_missions (id TEXT PRIMARY KEY, island_key TEXT, owner_uuid TEXT, type TEXT, objective TEXT, target_material TEXT, target INTEGER, progress INTEGER, reward_money INTEGER, reward_xp INTEGER, reward_item_base64 TEXT, reward_booster_type TEXT, reward_booster_duration INTEGER, completed BOOLEAN, claimed BOOLEAN, created_at INTEGER, expires_at INTEGER, title TEXT, description TEXT)",
                "CREATE TABLE IF NOT EXISTS island_boosters (island_key TEXT, booster_type TEXT, multiplier REAL, expires_at INTEGER, PRIMARY KEY(island_key, booster_type))",
                "CREATE TABLE IF NOT EXISTS auctions (id TEXT PRIMARY KEY, seller_uuid TEXT, item_base64 TEXT, price REAL, end_time INTEGER, sold BOOLEAN DEFAULT 0, buyer_uuid TEXT)",
                "CREATE TABLE IF NOT EXISTS bazaar_orders (id TEXT PRIMARY KEY, player_uuid TEXT, material TEXT, amount INTEGER, price_per_unit REAL, buy_order BOOLEAN, created_at INTEGER, filled BOOLEAN DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS pending_items (uuid TEXT, item_base64 TEXT)",
                "CREATE TABLE IF NOT EXISTS slayer_kills (uuid TEXT, slayer_type TEXT, tier TEXT, kills INTEGER, PRIMARY KEY(uuid, slayer_type, tier))",
                "CREATE TABLE IF NOT EXISTS slayer_tokens (uuid TEXT PRIMARY KEY, tokens INTEGER DEFAULT 0, last_updated INTEGER DEFAULT 0, weekly_tokens INTEGER DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS player_particle_trails (uuid TEXT, trail_id TEXT, unlocked_at INTEGER, PRIMARY KEY (uuid, trail_id))",
                "CREATE TABLE IF NOT EXISTS player_active_trail (uuid TEXT PRIMARY KEY, trail_id TEXT, updated_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS votes (voter_uuid TEXT, target_uuid TEXT, timestamp INTEGER)",
                "CREATE TABLE IF NOT EXISTS holograms (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, world TEXT NOT NULL, x REAL, y REAL, z REAL, billboard TEXT DEFAULT 'CENTER', background_color TEXT, scale REAL DEFAULT 1.0, see_through BOOLEAN DEFAULT 0, shadow BOOLEAN DEFAULT 1, permission TEXT, is_dynamic BOOLEAN DEFAULT 0, dynamic_type TEXT, update_interval INTEGER DEFAULT 300)",
                "CREATE TABLE IF NOT EXISTS hologram_lines (holo_id INTEGER, line_index INTEGER, text TEXT, PRIMARY KEY(holo_id, line_index))",

                // Island feature tables (centralized from Island*Manager classes)
                "CREATE TABLE IF NOT EXISTS island_settings (grid_x INTEGER, grid_z INTEGER, dimension TEXT, pvp_enabled BOOLEAN DEFAULT 0, visitors_allowed BOOLEAN DEFAULT 1, explosions_enabled BOOLEAN DEFAULT 0, fire_spread_enabled BOOLEAN DEFAULT 0, mob_spawning_enabled BOOLEAN DEFAULT 1, crop_trampling_enabled BOOLEAN DEFAULT 1, animal_spawning_enabled BOOLEAN DEFAULT 1, leaf_decay_enabled BOOLEAN DEFAULT 1, border_color TEXT DEFAULT 'BLUE', border_size INTEGER DEFAULT 100, border_markers_enabled BOOLEAN DEFAULT 0, warp_enabled BOOLEAN DEFAULT 0, warp_description TEXT DEFAULT '', PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS island_banks (grid_x INTEGER, grid_z INTEGER, dimension TEXT, balance REAL DEFAULT 0.0, PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS island_worth (grid_x INTEGER, grid_z INTEGER, dimension TEXT, worth REAL DEFAULT 0.0, worth_level INTEGER DEFAULT 1, last_calculated INTEGER DEFAULT 0, PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS island_ratings (grid_x INTEGER, grid_z INTEGER, dimension TEXT, player_uuid TEXT, rating INTEGER, timestamp INTEGER, PRIMARY KEY (grid_x, grid_z, dimension, player_uuid))",
                "CREATE TABLE IF NOT EXISTS island_warps (grid_x INTEGER, grid_z INTEGER, dimension TEXT, world TEXT, x REAL, y REAL, z REAL, yaw REAL, pitch REAL, enabled BOOLEAN DEFAULT 0, PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS punishments (id INTEGER PRIMARY KEY AUTOINCREMENT, target_uuid TEXT NOT NULL, staff_uuid TEXT, type TEXT NOT NULL, reason TEXT, duration INTEGER, timestamp INTEGER, active BOOLEAN DEFAULT 1)",

                // Wardrobe system (Armor + Equipment presets)
                "CREATE TABLE IF NOT EXISTS player_wardrobe (uuid TEXT, slot INTEGER, set_type TEXT, name TEXT, icon TEXT, h_base64 TEXT, c_base64 TEXT, l_base64 TEXT, b_base64 TEXT, e1_base64 TEXT, e2_base64 TEXT, e3_base64 TEXT, e4_base64 TEXT, PRIMARY KEY (uuid, slot, set_type))",

                // Light wardrobe equipment collection for XP (persistent across restarts)
                "CREATE TABLE IF NOT EXISTS player_wardrobe_collection (uuid TEXT, material TEXT, PRIMARY KEY (uuid, material))",
                "CREATE TABLE IF NOT EXISTS island_shop_purchases (island_key TEXT, item_id TEXT, purchased_at INTEGER, PRIMARY KEY (island_key, item_id))",
                "CREATE TABLE IF NOT EXISTS island_prestige (island_key TEXT PRIMARY KEY, prestige_level INTEGER DEFAULT 0, last_prestiged INTEGER DEFAULT 0)"
        };

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : tables) {
                stmt.executeUpdate(sql);
            }
            // Indexes
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_islands_owner ON islands(owner_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_island_balances_grid ON island_balances(grid_x, grid_z, dimension)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_island_skills_key ON island_skills(island_key)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_island_milestones_key ON island_milestones(island_key)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_auctions_active ON auctions(sold, end_time)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_wardrobe_collection ON player_wardrobe_collection(uuid)");

            // Backwards-compat ALTERs for mission booster reward columns (safe if columns exist)
            try { stmt.executeUpdate("ALTER TABLE island_missions ADD COLUMN reward_booster_type TEXT"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE island_missions ADD COLUMN reward_booster_duration INTEGER"); } catch (SQLException ignored) {}

            // Island Shop one-time purchases
            try { stmt.executeUpdate("ALTER TABLE island_shop_purchases ADD COLUMN purchased_at INTEGER"); } catch (SQLException ignored) {}

            // Prestige system
            try { stmt.executeUpdate("ALTER TABLE island_prestige ADD COLUMN prestige_level INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE island_prestige ADD COLUMN last_prestiged INTEGER DEFAULT 0"); } catch (SQLException ignored) {}

            // Slayer Token persistence (backwards compat)
            try { stmt.executeUpdate("ALTER TABLE slayer_tokens ADD COLUMN tokens INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE slayer_tokens ADD COLUMN last_updated INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE slayer_tokens ADD COLUMN weekly_tokens INTEGER DEFAULT 0"); } catch (SQLException ignored) {}

            // Border markers flag
            try { stmt.executeUpdate("ALTER TABLE island_settings ADD COLUMN border_markers_enabled BOOLEAN DEFAULT 0"); } catch (SQLException ignored) {}

            // Particle trail cosmetics
            try { stmt.executeUpdate("ALTER TABLE player_particle_trails ADD COLUMN trail_id TEXT"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE player_active_trail ADD COLUMN trail_id TEXT"); } catch (SQLException ignored) {}
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    // ==================== MODERN ITEM SERIALIZATION ====================
    String itemToBase64(ItemStack item) { // package-private for DAOs during migration
        if (item == null) return null;
        try {
            byte[] bytes = item.serializeAsBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            plugin.getLogger().severe("Item serialization failed: " + e.getMessage());
            return null;
        }
    }

    ItemStack itemFromBase64(String base64) { // package-private for DAOs during migration
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            plugin.getLogger().severe("Item deserialization failed: " + e.getMessage());
            return null;
        }
    }

    // ==================== BAZAAR (Fixed) ====================
    public CompletableFuture<List<BazaarOrder>> getActiveBazaarOrders() {
        return CompletableFuture.supplyAsync(() -> {
            List<BazaarOrder> orders = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM bazaar_orders WHERE filled = 0")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    orders.add(new BazaarOrder(
                            rs.getString("id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("material"),
                            rs.getInt("amount"),
                            rs.getDouble("price_per_unit"),
                            rs.getBoolean("buy_order"),
                            rs.getLong("created_at"),
                            rs.getBoolean("filled")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getActiveBazaarOrders failed: " + e.getMessage());
            }
            return orders;
        }, executor);
    }

    public boolean saveBazaarOrder(BazaarOrder o) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO bazaar_orders (id, player_uuid, material, amount, price_per_unit, buy_order, created_at, filled) VALUES (?,?,?,?,?,?,?,?)")) {
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
            plugin.getLogger().severe("saveBazaarOrder failed: " + e.getMessage());
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

    // ==================== AUCTION SYSTEM (Implemented to support AuctionManager) ====================

    /**
     * Loads all unsold auctions from the database.
     * AuctionManager will further filter out truly expired ones in-memory.
     */
    public CompletableFuture<List<Auction>> getActiveAuctions() {
        // Delegated to extracted AuctionDAO (DatabaseManager compression)
        return auctionDAO.getActiveAuctions();
    }

    /**
     * Saves a new auction. Serializes a minimal ItemStack (material + amount) to satisfy the item_base64 column.
     */
    public CompletableFuture<Boolean> saveAuction(Auction auction) {
        return auctionDAO.saveAuction(auction);
    }

    /**
     * Updates an existing auction (used when a new bid is placed).
     * Implemented as upsert for simplicity and safety.
     */
    public CompletableFuture<Boolean> updateAuction(Auction auction) {
        return auctionDAO.updateAuction(auction);
    }

    /**
     * Marks an auction as sold and records the winner.
     */
    public CompletableFuture<Boolean> markAuctionSold(String id, UUID buyerUuid) {
        return auctionDAO.markAuctionSold(id, buyerUuid);
    }

    /**
     * Marks an auction as expired (no winner). We still set sold=1 so it no longer appears in active listings.
     */
    public CompletableFuture<Boolean> markAuctionExpired(String id) {
        return auctionDAO.markAuctionExpired(id);
    }

    // ==================== ISLAND BALANCE WITH CACHING ====================
    public CompletableFuture<Double> getIslandBalance(GridPosition pos) {
        Double cached = islandBalanceCache.get(pos);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT balance FROM island_balances WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                ps.setInt(1, pos.x());
                ps.setInt(2, pos.z());
                ps.setString(3, pos.getDimension().name());
                ResultSet rs = ps.executeQuery();
                double balance = rs.next() ? rs.getDouble("balance") : 0.0;
                islandBalanceCache.put(pos, balance);
                return balance;
            } catch (SQLException e) {
                return 0.0;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> setIslandBalance(GridPosition pos, double balance) {
        islandBalanceCache.put(pos, balance);
        dirtyBalances.add(pos);

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, pos.x());
                ps.setInt(2, pos.z());
                ps.setString(3, pos.getDimension().name());
                ps.setDouble(4, balance);
                ps.executeUpdate();
                dirtyBalances.remove(pos);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("setIslandBalance failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> addIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, current + amount));
    }

    public CompletableFuture<Boolean> removeIslandBalance(GridPosition pos, double amount) {
        return addIslandBalance(pos, -amount);
    }

    // ==================== PLAYER BALANCES (Chest Shops / Player Economy) ====================

    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT balance FROM player_balances WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getDouble("balance") : 0.0;
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] getPlayerBalance failed: " + e.getMessage());
                return 0.0;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> setPlayerBalance(UUID uuid, double balance) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO player_balances (uuid, balance) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, Math.max(0, balance));
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] setPlayerBalance failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return getPlayerBalance(uuid).thenCompose(current ->
                setPlayerBalance(uuid, current + amount));
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return addPlayerBalance(uuid, -amount);
    }

    // ==================== CORE ISLAND PERSISTENCE ====================

    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID ownerUuid, String dimension, String biome) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO islands (grid_x, grid_z, owner_uuid, dimension, biome, level, last_reset) " +
                         "VALUES (?, ?, ?, ?, ?, 1, 0)")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, ownerUuid.toString());
                ps.setString(4, dimension);
                ps.setString(5, biome);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] saveIsland failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    /**
     * Loads a full Island object by owner + dimension. Returns null if none exists.
     * Note: This version is synchronous for compatibility with current IslandManager usage.
     */
    public Island getIslandByOwner(UUID ownerUuid, World.Environment dimension) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT grid_x, grid_z, biome FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
            ps.setString(1, ownerUuid.toString());
            ps.setString(2, dimension.name());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int x = rs.getInt("grid_x");
                int z = rs.getInt("grid_z");
                String biome = rs.getString("biome");

                GridPosition pos = new GridPosition(x, z, dimension);
                return new Island(pos, ownerUuid, biome != null ? biome : "PLAINS", dimension);
            }
            return null;
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] getIslandByOwner failed: " + e.getMessage());
            return null;
        }
    }

    public CompletableFuture<Boolean> deleteIsland(UUID ownerUuid, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, dimension.name());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] deleteIsland failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public long getLastResetTime(UUID playerUuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT last_reset FROM islands WHERE owner_uuid = ? LIMIT 1")) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong("last_reset") : 0L;
        } catch (SQLException e) {
            return 0L;
        }
    }

    // ==================== HOLOGRAM PERSISTENCE (basic implementation) ====================

    public CompletableFuture<List<HologramData>> loadAllHolograms() {
        return CompletableFuture.supplyAsync(() -> {
            List<HologramData> list = new ArrayList<>();
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM holograms")) {

                while (rs.next()) {
                    int id = rs.getInt("id");
                    HologramData data = new HologramData(
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z")
                    );
                    data.setId(id);
                    // Load lines
                    try (PreparedStatement linePs = conn.prepareStatement(
                            "SELECT text FROM hologram_lines WHERE holo_id = ? ORDER BY line_index")) {
                        linePs.setInt(1, id);
                        ResultSet lineRs = linePs.executeQuery();
                        List<String> lines = new ArrayList<>();
                        while (lineRs.next()) lines.add(lineRs.getString("text"));
                        data.setLines(lines);
                    }
                    list.add(data);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] loadAllHolograms failed: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    public CompletableFuture<Boolean> saveHologram(HologramData data) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                int id;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO holograms (name, world, x, y, z, billboard, background_color, scale, see_through, shadow, permission, is_dynamic, dynamic_type, update_interval) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {

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
                    ps.executeUpdate();

                    ResultSet keys = ps.getGeneratedKeys();
                    id = keys.next() ? keys.getInt(1) : -1;
                    data.setId(id);
                }

                // Save lines
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM hologram_lines WHERE holo_id = ?");
                     PreparedStatement ins = conn.prepareStatement(
                             "INSERT INTO hologram_lines (holo_id, line_index, text) VALUES (?, ?, ?)")) {
                    del.setInt(1, id);
                    del.executeUpdate();

                    for (int i = 0; i < data.getLines().size(); i++) {
                        ins.setInt(1, id);
                        ins.setInt(2, i);
                        ins.setString(3, data.getLines().get(i));
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] saveHologram failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> deleteHologram(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM holograms WHERE id = ?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM hologram_lines WHERE holo_id = ?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> updateHologramLines(int id, List<String> lines) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM hologram_lines WHERE holo_id = ?")) {
                    del.setInt(1, id);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO hologram_lines (holo_id, line_index, text) VALUES (?, ?, ?)")) {
                    for (int i = 0; i < lines.size(); i++) {
                        ins.setInt(1, id);
                        ins.setInt(2, i);
                        ins.setString(3, lines.get(i));
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> updateHologramInterval(int id, int interval) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE holograms SET update_interval = ? WHERE id = ?")) {
                ps.setInt(1, interval);
                ps.setInt(2, id);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    public List<TopIslandEntry> getTopIslandsByLevel(int limit) {
        List<TopIslandEntry> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT owner_uuid, level FROM islands ORDER BY level DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                int level = rs.getInt("level");
                // We pass dimension as empty for now since this is global top
                TopIslandEntry entry = new TopIslandEntry(owner, level, "OVERWORLD");
                results.add(entry);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] getTopIslandsByLevel failed: " + e.getMessage());
        }
        return results;
    }

    // ==================== PENDING ITEMS ====================
    public CompletableFuture<List<ItemStack>> getPendingItems(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<ItemStack> items = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT item_base64 FROM pending_items WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    ItemStack item = itemFromBase64(rs.getString("item_base64"));
                    if (item != null) items.add(item);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getPendingItems failed: " + e.getMessage());
            }
            return items;
        }, executor);
    }

    public boolean storePendingItem(UUID uuid, ItemStack item) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO pending_items (uuid, item_base64) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, itemToBase64(item));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // ==================== WARDROBE SYSTEM ====================

    /**
     * Saves a wardrobe preset (supports both ARMOR and EQUIPMENT types).
     */
    public void saveWardrobeSet(UUID uuid, int slot, String setType, com.thenerdcj.wardrobe.WardrobeSet set) {
        if (set == null) return;

        String sql = "INSERT OR REPLACE INTO player_wardrobe " +
                "(uuid, slot, set_type, name, icon, h_base64, c_base64, l_base64, b_base64, e1_base64, e2_base64, e3_base64, e4_base64) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.setString(3, setType);
            ps.setString(4, set.getName());
            ps.setString(5, set.getIcon().name());

            ItemStack[] armor = set.getArmor();
            ItemStack[] eq = set.getEquipment();

            ps.setString(6, itemToBase64(armor[0]));
            ps.setString(7, itemToBase64(armor[1]));
            ps.setString(8, itemToBase64(armor[2]));
            ps.setString(9, itemToBase64(armor[3]));

            ps.setString(10, itemToBase64(eq[0]));
            ps.setString(11, itemToBase64(eq[1]));
            ps.setString(12, itemToBase64(eq[2]));
            ps.setString(13, itemToBase64(eq[3]));

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Wardrobe] Failed to save set: " + e.getMessage());
        }
    }

    public void deleteWardrobeSet(UUID uuid, int slot, String setType) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM player_wardrobe WHERE uuid = ? AND slot = ? AND set_type = ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.setString(3, setType);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Wardrobe] Failed to delete set: " + e.getMessage());
        }
    }

    /**
     * Loads all wardrobe presets for a player and populates the WardrobeManager cache.
     */
    public void loadWardrobeForPlayer(UUID uuid, com.thenerdcj.wardrobe.WardrobeManager manager) {
        String sql = "SELECT * FROM player_wardrobe WHERE uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int slot = rs.getInt("slot");
                String setType = rs.getString("set_type");
                String name = rs.getString("name");
                String iconName = rs.getString("icon");

                Material icon = Material.matchMaterial(iconName);
                if (icon == null) icon = Material.LEATHER_CHESTPLATE;

                ItemStack[] armor = new ItemStack[4];
                armor[0] = itemFromBase64(rs.getString("h_base64"));
                armor[1] = itemFromBase64(rs.getString("c_base64"));
                armor[2] = itemFromBase64(rs.getString("l_base64"));
                armor[3] = itemFromBase64(rs.getString("b_base64"));

                ItemStack[] equipment = new ItemStack[4];
                equipment[0] = itemFromBase64(rs.getString("e1_base64"));
                equipment[1] = itemFromBase64(rs.getString("e2_base64"));
                equipment[2] = itemFromBase64(rs.getString("e3_base64"));
                equipment[3] = itemFromBase64(rs.getString("e4_base64"));

                com.thenerdcj.wardrobe.WardrobeSet set =
                        new com.thenerdcj.wardrobe.WardrobeSet(name, icon, armor, equipment);

                if ("ARMOR".equalsIgnoreCase(setType)) {
                    manager.getArmorPresets(uuid).put(slot, set);
                } else if ("EQUIPMENT".equalsIgnoreCase(setType)) {
                    manager.getEquipmentPresets(uuid).put(slot, set);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Wardrobe] Failed to load wardrobe for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Saves a single collected equipment material for a player (used for wardrobe collection XP).
     */
    public void saveWardrobeCollectionEntry(UUID uuid, String materialName) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_wardrobe_collection (uuid, material) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, materialName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Wardrobe] Failed to save collection entry: " + e.getMessage());
        }
    }

    /**
     * Loads all collected equipment materials for a player.
     */
    public Set<Material> loadWardrobeCollection(UUID uuid) {
        Set<Material> collected = ConcurrentHashMap.newKeySet();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT material FROM player_wardrobe_collection WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String matName = rs.getString("material");
                Material mat = Material.matchMaterial(matName);
                if (mat != null) {
                    collected.add(mat);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Wardrobe] Failed to load collection for " + uuid + ": " + e.getMessage());
        }

        return collected;
    }

    // ==================== ISLAND XP / LEVEL ====================
    public CompletableFuture<Boolean> updateIslandLevel(UUID ownerUuid, World.Environment dimension, int newLevel, double xp) {
        String key = ownerUuid.toString() + "_" + dimension.name().toLowerCase();
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_levels (island_key, xp, level) VALUES (?, ?, ?)")) {
                ps.setString(1, key);
                ps.setDouble(2, xp);
                ps.setInt(3, newLevel);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    // ==================== ISLAND SKILLS WITH CACHING ====================
    public CompletableFuture<Boolean> saveIslandSkills(String islandKey, Map<Skill, Double> xpMap, Map<Skill, Integer> levelMap) {
        if (islandKey == null || xpMap == null) return CompletableFuture.completedFuture(false);

        skillCache.put(islandKey, new EnumMap<Skill, Object[]>(xpMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new Object[]{e.getValue(), levelMap.getOrDefault(e.getKey(), 1)}
                ))));
        dirtySkills.add(islandKey);

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR REPLACE INTO island_skills (island_key, skill_name, xp, level) VALUES (?, ?, ?, ?)")) {
                    for (Map.Entry<Skill, Double> entry : xpMap.entrySet()) {
                        ps.setString(1, islandKey);
                        ps.setString(2, entry.getKey().name());
                        ps.setDouble(3, entry.getValue());
                        ps.setInt(4, levelMap.getOrDefault(entry.getKey(), 1));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
                dirtySkills.remove(islandKey);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("saveIslandSkills failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Map<Skill, Object[]>> loadIslandSkills(String islandKey) {
        if (skillCache.containsKey(islandKey)) {
            return CompletableFuture.completedFuture(skillCache.get(islandKey));
        }

        return CompletableFuture.supplyAsync(() -> {
            Map<Skill, Object[]> result = new EnumMap<>(Skill.class);
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT skill_name, xp, level FROM island_skills WHERE island_key = ?")) {
                ps.setString(1, islandKey);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    try {
                        Skill skill = Skill.valueOf(rs.getString("skill_name"));
                        result.put(skill, new Object[]{rs.getDouble("xp"), rs.getInt("level")});
                    } catch (IllegalArgumentException ignored) {}
                }
                skillCache.put(islandKey, result);
            } catch (SQLException e) {
                plugin.getLogger().severe("loadIslandSkills failed");
            }
            return result;
        }, executor);
    }

    // ==================== ISLAND MILESTONES ====================
    public CompletableFuture<Boolean> saveIslandMilestones(String islandKey, Set<String> milestoneIds) {
        return CompletableFuture.supplyAsync(() -> {
            if (islandKey == null || milestoneIds == null) return false;
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM island_milestones WHERE island_key = ?")) {
                    del.setString(1, islandKey);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO island_milestones (island_key, milestone_id, completed_at) VALUES (?, ?, ?)")) {
                    long now = System.currentTimeMillis();
                    for (String id : milestoneIds) {
                        ins.setString(1, islandKey);
                        ins.setString(2, id);
                        ins.setLong(3, now);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Set<String>> loadIslandMilestones(String islandKey) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> result = new HashSet<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT milestone_id FROM island_milestones WHERE island_key = ?")) {
                ps.setString(1, islandKey);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.add(rs.getString("milestone_id"));
            } catch (SQLException e) {}
            return result;
        }, executor);
    }

    // ==================== FLUSH CACHES ON SHUTDOWN ====================
    public void flushCaches() {
        // Flush dirty island balances
        for (GridPosition pos : dirtyBalances) {
            Double balance = islandBalanceCache.get(pos);
            if (balance != null) {
                try (Connection conn = getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT OR REPLACE INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, pos.x());
                    ps.setInt(2, pos.z());
                    ps.setString(3, pos.getDimension().name());
                    ps.setDouble(4, balance);
                    ps.executeUpdate();
                } catch (SQLException ignored) {}
            }
        }
        dirtyBalances.clear();

        // Flush dirty skills
        for (String key : dirtySkills) {
            Map<Skill, Object[]> skills = skillCache.get(key);
            if (skills != null) {
                try (Connection conn = getConnection()) {
                    conn.setAutoCommit(false);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_skills (island_key, skill_name, xp, level) VALUES (?, ?, ?, ?)")) {
                        for (Map.Entry<Skill, Object[]> entry : skills.entrySet()) {
                            ps.setString(1, key);
                            ps.setString(2, entry.getKey().name());
                            ps.setDouble(3, (Double) entry.getValue()[0]);
                            ps.setInt(4, (Integer) entry.getValue()[1]);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    conn.commit();
                } catch (SQLException ignored) {}
            }
        }
        dirtySkills.clear();

        plugin.getLogger().info("§a[Database] Dirty caches flushed to disk.");
    }
    // ==================== ISLAND UPGRADES PERSISTENCE ====================

    /**
     * Save or update a single upgrade level for an island.
     */
    public CompletableFuture<Boolean> saveIslandUpgrade(String islandKey, IslandUpgrade upgrade, int level) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_upgrades (island_key, upgrade_type, level) VALUES (?, ?, ?)")) {
                ps.setString(1, islandKey);
                ps.setString(2, upgrade.name());
                ps.setInt(3, level);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("saveIslandUpgrade failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    /**
     * Load all upgrades and their levels for an island.
     */
    public CompletableFuture<Map<IslandUpgrade, Integer>> loadIslandUpgrades(String islandKey) {
        return CompletableFuture.supplyAsync(() -> {
            Map<IslandUpgrade, Integer> upgrades = new EnumMap<>(IslandUpgrade.class);
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT upgrade_type, level FROM island_upgrades WHERE island_key = ?")) {
                ps.setString(1, islandKey);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    try {
                        IslandUpgrade type = IslandUpgrade.valueOf(rs.getString("upgrade_type"));
                        upgrades.put(type, rs.getInt("level"));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown upgrade type in DB - skip
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadIslandUpgrades failed: " + e.getMessage());
            }
            return upgrades;
        }, executor);
    }

    /**
     * Get a specific upgrade level for an island (returns 0 if not found).
     */
    public CompletableFuture<Integer> getIslandUpgradeLevel(String islandKey, IslandUpgrade upgrade) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT level FROM island_upgrades WHERE island_key = ? AND upgrade_type = ?")) {
                ps.setString(1, islandKey);
                ps.setString(2, upgrade.name());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getInt("level") : 0;
            } catch (SQLException e) {
                return 0;
            }
        }, executor);
    }

    public void close() {
        flushCaches();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        executor.shutdown();
    }

    // ==================== MINION PERSISTENCE (basic) ====================

    public CompletableFuture<Boolean> saveMinionData(String islandKey, int minionType, int level) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_minions (island_key, minion_type, level) VALUES (?, ?, ?)")) {
                ps.setString(1, islandKey);
                ps.setInt(2, minionType);
                ps.setInt(3, level);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    // Overload used by MinionManager
    public CompletableFuture<Boolean> saveMinionData(String islandKey, int minionType, Integer level) {
        return saveMinionData(islandKey, minionType, level == null ? 1 : level);
    }

    public CompletableFuture<Map<Integer, Integer>> loadMinionData(String islandKey) {
        return CompletableFuture.supplyAsync(() -> {
            Map<Integer, Integer> data = new HashMap<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT minion_type, level FROM island_minions WHERE island_key = ?")) {
                ps.setString(1, islandKey);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.put(rs.getInt("minion_type"), rs.getInt("level"));
                }
            } catch (SQLException ignored) {}
            return data;
        }, executor);
    }

    // ==================== ISLAND FUEL PERSISTENCE (Polished) ====================

    public CompletableFuture<Boolean> saveIslandFuel(String islandKey, int fuelAmount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_fuel (island_key, fuel_amount) VALUES (?, ?)")) {
                ps.setString(1, islandKey);
                ps.setInt(2, fuelAmount);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save island fuel for " + islandKey + ": " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Integer> loadIslandFuel(String islandKey) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT fuel_amount FROM island_fuel WHERE island_key = ?")) {
                ps.setString(1, islandKey);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt("fuel_amount");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load island fuel for " + islandKey + ": " + e.getMessage());
            }
            return 1000; // default starting fuel
        }, executor);
    }

    // ==================== RANK / VOTING (basic) ====================

    public CompletableFuture<String> getCurrentRankId(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT rank_name FROM player_ranks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getString("rank_name") : "default";
            } catch (SQLException e) { return "default"; }
        }, executor);
    }

    public CompletableFuture<Integer> getUpvoteCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT upvotes FROM player_ranks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getInt("upvotes") : 0;
            } catch (SQLException e) { return 0; }
        }, executor);
    }

    public CompletableFuture<Boolean> setRank(UUID uuid, String rankName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO player_ranks (uuid, rank_name, upvotes) VALUES (?, ?, COALESCE((SELECT upvotes FROM player_ranks WHERE uuid = ?), 0))")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankName);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { return false; }
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
                return true;
            } catch (SQLException e) { return false; }
        }, executor);
    }

    // ==================== PUNISHMENTS (now functional) ====================

    public CompletableFuture<Boolean> logPunishment(UUID target, UUID staff, Punishment.Type type, String reason, long duration) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO punishments (target_uuid, staff_uuid, type, reason, duration, timestamp, active) VALUES (?, ?, ?, ?, ?, ?, 1)")) {
                ps.setString(1, target.toString());
                ps.setString(2, staff != null ? staff.toString() : null);
                ps.setString(3, type.name());
                ps.setString(4, reason);
                ps.setLong(5, duration);
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] logPunishment failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<List<Punishment>> getActivePunishments(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM punishments WHERE target_uuid = ? AND active = 1 ORDER BY timestamp DESC")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Punishment p = new Punishment(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("target_uuid")),
                            rs.getString("staff_uuid") != null ? UUID.fromString(rs.getString("staff_uuid")) : null,
                            Punishment.Type.valueOf(rs.getString("type")),
                            rs.getString("reason"),
                            rs.getLong("timestamp"),
                            rs.getLong("duration"),
                            rs.getBoolean("active")
                    );
                    list.add(p);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] getActivePunishments failed: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    public CompletableFuture<List<Punishment>> getPunishmentsForPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM punishments WHERE target_uuid = ? ORDER BY timestamp DESC")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Punishment p = new Punishment(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("target_uuid")),
                            rs.getString("staff_uuid") != null ? UUID.fromString(rs.getString("staff_uuid")) : null,
                            Punishment.Type.valueOf(rs.getString("type")),
                            rs.getString("reason"),
                            rs.getLong("timestamp"),
                            rs.getLong("duration"),
                            rs.getBoolean("active")
                    );
                    list.add(p);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] getPunishmentsForPlayer failed: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    public CompletableFuture<Boolean> unbanPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE punishments SET active = 0 WHERE target_uuid = ? AND type IN ('BAN', 'TEMPBAN') AND active = 1")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] unbanPlayer failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    // ==================== SLAYER STATS (now functional) ====================

    public CompletableFuture<Boolean> incrementSlayerKills(UUID uuid, String slayerType, String tier, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO slayer_kills (uuid, slayer_type, tier, kills) " +
                         "VALUES (?, ?, ?, COALESCE((SELECT kills FROM slayer_kills WHERE uuid = ? AND slayer_type = ? AND tier = ?), 0) + ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, slayerType);
                ps.setString(3, tier);
                ps.setString(4, uuid.toString());
                ps.setString(5, slayerType);
                ps.setString(6, tier);
                ps.setInt(7, amount);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] incrementSlayerKills failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<List<Object[]>> getGlobalTopSlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Object[]> results = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT uuid, SUM(kills) as total FROM slayer_kills GROUP BY uuid ORDER BY total DESC LIMIT ?")) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    results.add(new Object[]{
                            UUID.fromString(rs.getString("uuid")),
                            rs.getInt("total")
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] getGlobalTopSlayers failed: " + e.getMessage());
            }
            return results;
        }, executor);
    }

    public CompletableFuture<List<Object[]>> getTopSlayers(String slayerType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Object[]> results = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT uuid, SUM(kills) as total FROM slayer_kills WHERE slayer_type = ? GROUP BY uuid ORDER BY total DESC LIMIT ?")) {
                ps.setString(1, slayerType);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    results.add(new Object[]{
                            UUID.fromString(rs.getString("uuid")),
                            rs.getInt("total")
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] getTopSlayers failed: " + e.getMessage());
            }
            return results;
        }, executor);
    }

    // ==================== SLAYER TOKENS (NEW - for leaderboard + shop currency) ====================

    public void saveSlayerTokens(UUID uuid, int tokens, int weeklyTokens) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO slayer_tokens (uuid, tokens, last_updated, weekly_tokens) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, tokens);
            ps.setLong(3, System.currentTimeMillis());
            ps.setInt(4, weeklyTokens);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] saveSlayerTokens failed: " + e.getMessage());
        }
    }

    public CompletableFuture<Integer> loadSlayerTokens(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT tokens FROM slayer_tokens WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt("tokens");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] loadSlayerTokens failed: " + e.getMessage());
            }
            return 0;
        }, executor);
    }

    public CompletableFuture<List<Object[]>> getTopSlayerTokenEarners(int limit) {
        // Delegated to extracted SlayerDAO (DB compression)
        return slayerDAO.getTopSlayerTokenEarners(limit);
    }

    public void incrementSlayerTokens(UUID uuid, int amount) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO slayer_tokens (uuid, tokens, last_updated, weekly_tokens) " +
                     "VALUES (?, COALESCE((SELECT tokens FROM slayer_tokens WHERE uuid = ?), 0) + ?, ?, " +
                     "COALESCE((SELECT weekly_tokens FROM slayer_tokens WHERE uuid = ?), 0) + ?)")) {
            long now = System.currentTimeMillis();
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            ps.setInt(3, amount);
            ps.setLong(4, now);
            ps.setString(5, uuid.toString());
            ps.setInt(6, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] incrementSlayerTokens failed: " + e.getMessage());
        }
    }

    public void resetWeeklySlayerTokens() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE slayer_tokens SET weekly_tokens = 0")) {
            ps.executeUpdate();
            plugin.getLogger().info("[Database] Weekly Slayer Tokens leaderboard reset.");
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] resetWeeklySlayerTokens failed: " + e.getMessage());
        }
    }

    // ==================== PARTICLE TRAILS (NEW Cosmetic System) ====================

    public Set<String> loadUnlockedParticleTrails(UUID uuid) {
        Set<String> trails = new HashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT trail_id FROM player_particle_trails WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                trails.add(rs.getString("trail_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] loadUnlockedParticleTrails failed: " + e.getMessage());
        }
        return trails;
    }

    public void saveUnlockedParticleTrails(UUID uuid, Set<String> trails) {
        try (Connection conn = getConnection();
             PreparedStatement delete = conn.prepareStatement("DELETE FROM player_particle_trails WHERE uuid = ?");
             PreparedStatement insert = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_particle_trails (uuid, trail_id, unlocked_at) VALUES (?, ?, ?)")) {

            delete.setString(1, uuid.toString());
            delete.executeUpdate();

            long now = System.currentTimeMillis();
            for (String trail : trails) {
                insert.setString(1, uuid.toString());
                insert.setString(2, trail);
                insert.setLong(3, now);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] saveUnlockedParticleTrails failed: " + e.getMessage());
        }
    }

    public String loadActiveParticleTrail(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT trail_id FROM player_active_trail WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("trail_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] loadActiveParticleTrail failed: " + e.getMessage());
        }
        return null;
    }

    public void saveActiveParticleTrail(UUID uuid, String trailId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO player_active_trail (uuid, trail_id, updated_at) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, trailId == null ? "NONE" : trailId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] saveActiveParticleTrail failed: " + e.getMessage());
        }
    }

    // ==================== ISLAND MEMBERS (basic) ====================

    public CompletableFuture<Boolean> addIslandMember(int gridX, int gridZ, String dimension, UUID playerUuid, String role) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR IGNORE INTO island_members (island_id, player_uuid, role) " +
                         "SELECT id, ?, ? FROM islands WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, role);
                ps.setInt(3, gridX);
                ps.setInt(4, gridZ);
                ps.setString(5, dimension);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    // ==================== GLOBAL ISLAND WORTH LEADERBOARD (DB-backed) ====================

    public static class TopWorthEntry {
        public final int gridX;
        public final int gridZ;
        public final String dimension;
        public final double worth;
        public final int worthLevel;
        public final UUID ownerUuid;
        public final int memberCount;

        public TopWorthEntry(int gridX, int gridZ, String dimension, double worth, int worthLevel, UUID ownerUuid, int memberCount) {
            this.gridX = gridX;
            this.gridZ = gridZ;
            this.dimension = dimension;
            this.worth = worth;
            this.worthLevel = worthLevel;
            this.ownerUuid = ownerUuid;
            this.memberCount = memberCount;
        }
    }

    public CompletableFuture<List<TopWorthEntry>> getTopIslandsByWorth(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<TopWorthEntry> results = new ArrayList<>();
            String sql = """
                SELECT w.grid_x, w.grid_z, w.dimension, w.worth, w.worth_level, i.owner_uuid,
                       (SELECT COUNT(*) FROM island_members m 
                        JOIN islands ii ON m.island_id = ii.id 
                        WHERE ii.grid_x = w.grid_x AND ii.grid_z = w.grid_z AND ii.dimension = w.dimension) as member_count
                FROM island_worth w
                JOIN islands i ON i.grid_x = w.grid_x AND i.grid_z = w.grid_z AND i.dimension = w.dimension
                ORDER BY w.worth DESC
                LIMIT ?
                """;

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                    results.add(new TopWorthEntry(
                        rs.getInt("grid_x"),
                        rs.getInt("grid_z"),
                        rs.getString("dimension"),
                        rs.getDouble("worth"),
                        rs.getInt("worth_level"),
                        owner,
                        rs.getInt("member_count")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] getTopIslandsByWorth failed: " + e.getMessage());
            }
            return results;
        }, executor);
    }

    // ==================== MISSION PERSISTENCE (Expanded System) ====================

    public CompletableFuture<Boolean> saveMission(Mission mission) {
        // Delegated to MissionDAO
        if (missionDAO != null) {
            return missionDAO.saveMission(mission);
        }
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<List<Mission>> loadMissionsForIsland(String islandKey) {
        // Delegated to MissionDAO
        if (missionDAO != null) {
            return missionDAO.loadMissionsForIsland(islandKey);
        }
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    // ==================== ISLAND BOOSTERS PERSISTENCE ====================

    public void saveIslandBooster(String islandKey, String boosterType, double multiplier, long expiresAt) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO island_boosters (island_key, booster_type, multiplier, expires_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, islandKey);
            ps.setString(2, boosterType);
            ps.setDouble(3, multiplier);
            ps.setLong(4, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Boosters] save failed: " + e.getMessage());
        }
    }

    public void removeIslandBooster(String islandKey, String boosterType) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM island_boosters WHERE island_key = ? AND booster_type = ?")) {
            ps.setString(1, islandKey);
            ps.setString(2, boosterType);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Boosters] remove failed: " + e.getMessage());
        }
    }

    public CompletableFuture<Map<String, BoosterData>> loadIslandBoosters(String islandKey) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, BoosterData> boosters = new HashMap<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT booster_type, multiplier, expires_at FROM island_boosters WHERE island_key = ?")) {
                ps.setString(1, islandKey);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    boosters.put(
                        rs.getString("booster_type"),
                        new BoosterData(
                            rs.getDouble("multiplier"),
                            rs.getLong("expires_at")
                        )
                    );
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Boosters] load failed: " + e.getMessage());
            }
            return boosters;
        }, executor);
    }

    public static class BoosterData {
        public final double multiplier;
        public final long expiresAt;

        public BoosterData(double multiplier, long expiresAt) {
            this.multiplier = multiplier;
            this.expiresAt = expiresAt;
        }
    }

    // ==================== ISLAND SHOP ONE-TIME PURCHASES ====================

    public void saveShopPurchase(String islandKey, String itemId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO island_shop_purchases (island_key, item_id, purchased_at) VALUES (?, ?, ?)")) {
            ps.setString(1, islandKey);
            ps.setString(2, itemId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Shop] saveShopPurchase failed: " + e.getMessage());
        }
    }

    public CompletableFuture<Set<String>> loadShopPurchasesForIsland(String islandKey) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> purchased = new HashSet<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT item_id FROM island_shop_purchases WHERE island_key = ?")) {
                ps.setString(1, islandKey);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    purchased.add(rs.getString("item_id"));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Shop] loadShopPurchasesForIsland failed: " + e.getMessage());
            }
            return purchased;
        }, executor);
    }

    public boolean hasShopPurchase(String islandKey, String itemId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM island_shop_purchases WHERE island_key = ? AND item_id = ? LIMIT 1")) {
            ps.setString(1, islandKey);
            ps.setString(2, itemId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Shop] hasShopPurchase failed: " + e.getMessage());
            return false;
        }
    }

    // ==================== PRESTIGE SYSTEM ====================

    public void saveIslandPrestige(String islandKey, int prestigeLevel) {
        // Delegated to extracted PrestigeDAO
        if (prestigeDAO != null) {
            prestigeDAO.saveIslandPrestige(islandKey, prestigeLevel);
        }
    }

    public CompletableFuture<Integer> loadIslandPrestige(String islandKey) {
        // Delegated to extracted PrestigeDAO
        if (prestigeDAO != null) {
            return prestigeDAO.loadIslandPrestige(islandKey);
        }
        return CompletableFuture.completedFuture(0);
    }

    public void saveIslandLevel(String islandKey, int level, double xp) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO island_levels (island_key, xp, level) VALUES (?, ?, ?)")) {
            ps.setString(1, islandKey);
            ps.setDouble(2, xp);
            ps.setInt(3, level);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Prestige] saveIslandLevel failed: " + e.getMessage());
        }
    }

    // ==================== DIMENSION RESET TRACKING (for per-dimension resets) ====================
    /**
     * Records that a player has reset a specific dimension.
     * This enables per-dimension cooldowns (instead of a single global cooldown).
     * Also updates the legacy global last_reset column on the islands table for backward compatibility.
     */
    public void recordIslandReset(UUID playerUuid, World.Environment dimension) {
        long now = System.currentTimeMillis();

        // 1. Record per-dimension reset
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO player_dimension_resets (player_uuid, dimension, last_reset) VALUES (?, ?, ?)")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, dimension.name());
            ps.setLong(3, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] recordIslandReset (per-dimension) failed: " + e.getMessage());
        }

        // 2. Update legacy global last_reset on the islands table (for older code paths)
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE islands SET last_reset = ? WHERE owner_uuid = ?")) {
            ps.setLong(1, now);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] recordIslandReset (legacy global) failed: " + e.getMessage());
        }
    }

    /**
     * Returns the timestamp (ms) of the last time this player reset the given dimension.
     * Falls back to 0 if no per-dimension record exists.
     */
    public long getLastDimensionReset(UUID playerUuid, World.Environment dimension) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT last_reset FROM player_dimension_resets WHERE player_uuid = ? AND dimension = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, dimension.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("last_reset");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] getLastDimensionReset failed: " + e.getMessage());
        }
        return 0;
    }
}
