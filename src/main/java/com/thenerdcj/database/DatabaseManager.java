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

    // Extracted DAOs (continuing modularization - highest technical priority)
    private AuctionDAO auctionDAO;
    private SlayerDAO slayerDAO;
    private PrestigeDAO prestigeDAO;
    private MissionDAO missionDAO;
    private IslandDAO islandDAO; // Core island persistence extracted as part of full modularization
    private HologramDAO hologramDAO; // Extracted as continuation of DB modularization (hologram persistence moved out of god class)
    private BalanceDAO balanceDAO;
    private PunishmentDAO punishmentDAO;
    private CosmeticDAO cosmeticDAO; // Player cosmetic ownership/active/collections (many player_* tables)
    private PendingItemsDAO pendingItemsDAO; // For pending_items table (misc step)

    public IslandDAO getIslandDAO() {
        return islandDAO;
    }

    public HologramDAO getHologramDAO() {
        return hologramDAO;
    }

    public MissionDAO getMissionDAO() {
        return missionDAO;
    }

    public PrestigeDAO getPrestigeDAO() {
        return prestigeDAO;
    }

    public BalanceDAO getBalanceDAO() {
        return balanceDAO;
    }

    public PunishmentDAO getPunishmentDAO() {
        return punishmentDAO;
    }

    public CosmeticDAO getCosmeticDAO() {
        return cosmeticDAO;
    }

    public PendingItemsDAO getPendingItemsDAO() {
        return pendingItemsDAO;
    }

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
        this.islandDAO = new IslandDAO(plugin, dbOps);
        this.hologramDAO = new HologramDAO(plugin, dbOps);
        this.balanceDAO = new BalanceDAO(plugin, dbOps);
        this.punishmentDAO = new PunishmentDAO(plugin, dbOps);
        this.cosmeticDAO = new CosmeticDAO(plugin, dbOps);
        this.pendingItemsDAO = new PendingItemsDAO(plugin, dbOps);
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
        this.islandDAO = new IslandDAO(plugin, dbOps);
        this.hologramDAO = new HologramDAO(plugin, dbOps);
        this.balanceDAO = new BalanceDAO(plugin, dbOps);
        this.punishmentDAO = new PunishmentDAO(plugin, dbOps);
        this.cosmeticDAO = new CosmeticDAO(plugin, dbOps);
        this.pendingItemsDAO = new PendingItemsDAO(plugin, dbOps);
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
                "CREATE TABLE IF NOT EXISTS islands (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uuid TEXT NOT NULL, grid_x INTEGER, grid_z INTEGER, dimension TEXT NOT NULL, biome TEXT, level INTEGER DEFAULT 1, last_reset INTEGER DEFAULT 0, generation_seed BIGINT DEFAULT 0, UNIQUE(owner_uuid, dimension))",
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

                // Cosmetic Pets (vanity followers) - tied to Wardrobe system
                "CREATE TABLE IF NOT EXISTS player_pets (uuid TEXT, pet_type TEXT, custom_name TEXT, variant TEXT DEFAULT '', skin TEXT DEFAULT 'NONE', PRIMARY KEY (uuid, pet_type))",
                "CREATE TABLE IF NOT EXISTS player_active_pet (uuid TEXT PRIMARY KEY, pet_type TEXT, custom_name TEXT, skin TEXT DEFAULT 'NONE')",
                // Pet collection / rarity tracking (parallel to player_wardrobe_collection for XP)
                "CREATE TABLE IF NOT EXISTS player_pet_collection (uuid TEXT, pet_type TEXT, PRIMARY KEY (uuid, pet_type))",
                // Pet Skin collection (separate from equipped skin on a specific pet)
                "CREATE TABLE IF NOT EXISTS player_pet_skins (uuid TEXT, skin_id TEXT, PRIMARY KEY (uuid, skin_id))",

                // Player cosmetic Tags (new system - chat/tab display, prestige/slayer gated, collection XP)
                // variant column added for Tag Variants support (like PetVariant)
                "CREATE TABLE IF NOT EXISTS player_tags (uuid TEXT, tag_id TEXT, variant TEXT DEFAULT 'NONE', PRIMARY KEY (uuid, tag_id))",
                "CREATE TABLE IF NOT EXISTS player_active_tag (uuid TEXT PRIMARY KEY, tag_id TEXT, variant TEXT DEFAULT 'NONE')",
                "CREATE TABLE IF NOT EXISTS player_tag_collection (uuid TEXT, tag_id TEXT, PRIMARY KEY (uuid, tag_id))",

                // Elytra Wing Cosmetics (new advanced visual system for gliding)
                "CREATE TABLE IF NOT EXISTS player_elytra_wings (uuid TEXT, wing_id TEXT, PRIMARY KEY (uuid, wing_id))",
                "CREATE TABLE IF NOT EXISTS player_active_elytra_wing (uuid TEXT PRIMARY KEY, wing_id TEXT)",
                "CREATE TABLE IF NOT EXISTS player_elytra_wing_collection (uuid TEXT, wing_id TEXT, PRIMARY KEY (uuid, wing_id))",

                // Cosmetic Runes (applied to weapons/tools for particle effects)
                "CREATE TABLE IF NOT EXISTS player_runes (uuid TEXT, rune_id TEXT, PRIMARY KEY (uuid, rune_id))",
                "CREATE TABLE IF NOT EXISTS player_rune_collection (uuid TEXT, rune_id TEXT, PRIMARY KEY (uuid, rune_id))",

                // Helmet Skins (cosmetic overrides for helmets - Play-to-Win)
                "CREATE TABLE IF NOT EXISTS player_helmet_skins (uuid TEXT, skin_id TEXT, PRIMARY KEY (uuid, skin_id))",

                // Death / Kill Effects (cosmetic on-death and on-kill visuals)
                "CREATE TABLE IF NOT EXISTS player_death_effects (uuid TEXT, effect_id TEXT, PRIMARY KEY (uuid, effect_id))",

                // Backpack Skins (cosmetic overrides for backpacks/storage - exploration started)
                "CREATE TABLE IF NOT EXISTS player_backpack_skins (uuid TEXT, skin_id TEXT, PRIMARY KEY (uuid, skin_id))",

                // Power Orb Skins (cosmetic overrides for Power Orbs - new system)
                "CREATE TABLE IF NOT EXISTS player_power_orb_skins (uuid TEXT, skin_id TEXT, PRIMARY KEY (uuid, skin_id))",

                // Minion Skins (cosmetic head/appearance overrides for island minions - Play-to-Win)
                "CREATE TABLE IF NOT EXISTS player_minion_skins (uuid TEXT, skin_id TEXT, PRIMARY KEY (uuid, skin_id))",

                // Minion Skin Assignments (per-minion cosmetic skins - polish for per-assignment)
                "CREATE TABLE IF NOT EXISTS minion_skin_assignments (island_key TEXT, minion_num INTEGER, skin_id TEXT, PRIMARY KEY (island_key, minion_num))",

                // Island Furniture / Housing Cosmetics (Play-to-Win decorative placement)
                "CREATE TABLE IF NOT EXISTS player_island_furniture (uuid TEXT, furniture_id TEXT, PRIMARY KEY (uuid, furniture_id))",
                "CREATE TABLE IF NOT EXISTS island_placed_furniture (island_key TEXT, furniture_id TEXT, pos_x REAL, pos_y REAL, pos_z REAL, yaw REAL, data TEXT, placed_at INTEGER)",

                // Island Music & Ambient Cosmetics (Play-to-Win island-wide sounds)
                "CREATE TABLE IF NOT EXISTS player_island_music (uuid TEXT, music_id TEXT, PRIMARY KEY (uuid, music_id))",
                "CREATE TABLE IF NOT EXISTS island_active_music (island_key TEXT PRIMARY KEY, music_id TEXT)",

                // Advanced Overhead Cosmetics (TextDisplay floating effects above players)
                "CREATE TABLE IF NOT EXISTS player_overhead_cosmetics (uuid TEXT, cosmetic_id TEXT, PRIMARY KEY (uuid, cosmetic_id))",

                // Cosmetic Emotes (visual/chat flair emotes)
                "CREATE TABLE IF NOT EXISTS player_emote_cosmetics (uuid TEXT, emote_id TEXT, PRIMARY KEY (uuid, emote_id))",
                // Per-player emote triggers (e.g. "kill" -> some emote; persisted, not global)
                "CREATE TABLE IF NOT EXISTS player_emote_triggers (uuid TEXT, trigger_key TEXT, emote_id TEXT, PRIMARY KEY (uuid, trigger_key))",

                // Island Structure Decorations (larger cosmetic clusters)
                "CREATE TABLE IF NOT EXISTS player_island_structures (uuid TEXT, structure_id TEXT, PRIMARY KEY (uuid, structure_id))",
                "CREATE TABLE IF NOT EXISTS island_placed_structures (island_key TEXT, structure_id TEXT, pos_x REAL, pos_y REAL, pos_z REAL, yaw REAL, data TEXT, placed_at INTEGER)",

                // Chat Bubble Cosmetics (floating chat message visuals)
                "CREATE TABLE IF NOT EXISTS player_chat_bubble_cosmetics (uuid TEXT, bubble_id TEXT, PRIMARY KEY (uuid, bubble_id))",

                // Island Weather Cosmetics (cosmetic particle weather/ambience on islands)
                "CREATE TABLE IF NOT EXISTS player_island_weather (uuid TEXT, weather_id TEXT, PRIMARY KEY (uuid, weather_id))",
                "CREATE TABLE IF NOT EXISTS island_active_weather (island_key TEXT PRIMARY KEY, weather_id TEXT)",

                // Light Accessories (floating cosmetic items around player)
                "CREATE TABLE IF NOT EXISTS player_accessories (uuid TEXT, accessory_id TEXT, PRIMARY KEY (uuid, accessory_id))",

                "CREATE TABLE IF NOT EXISTS island_shop_purchases (island_key TEXT, item_id TEXT, purchased_at INTEGER, PRIMARY KEY (island_key, item_id))",
                "CREATE TABLE IF NOT EXISTS island_prestige (island_key TEXT PRIMARY KEY, prestige_level INTEGER DEFAULT 0, last_prestiged INTEGER DEFAULT 0)",

                // Core Collections System (Hypixel-style unique item discovery per island for progression + cosmetic rewards)
                "CREATE TABLE IF NOT EXISTS island_collections (island_key TEXT, item_key TEXT, discovered_by TEXT, discovered_at INTEGER, PRIMARY KEY (island_key, item_key))",

                // Cosmetic Death Messages (new system - text on kill/death)
                "CREATE TABLE IF NOT EXISTS player_death_messages (uuid TEXT, message_id TEXT, PRIMARY KEY (uuid, message_id))",

                // Player Skill System (MCMMO-like per-player skills: Mining, Woodcutting, etc. with levels/XP/abilities)
                "CREATE TABLE IF NOT EXISTS player_skills (uuid TEXT, skill TEXT, xp DOUBLE DEFAULT 0, level INTEGER DEFAULT 1, PRIMARY KEY (uuid, skill))"
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
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_island_collections_key ON island_collections(island_key)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_death_messages ON player_death_messages(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_skills_uuid ON player_skills(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_wardrobe_collection ON player_wardrobe_collection(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pet_collection ON player_pet_collection(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pet_skins ON player_pet_skins(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tag_collection ON player_tag_collection(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_elytra_wing_collection ON player_elytra_wing_collection(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_runes ON player_runes(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_rune_collection ON player_rune_collection(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_helmet_skins ON player_helmet_skins(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_death_effects ON player_death_effects(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_backpack_skins ON player_backpack_skins(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_power_orb_skins ON player_power_orb_skins(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_minion_skins ON player_minion_skins(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_minion_skin_assign ON minion_skin_assignments(island_key)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_island_furniture ON player_island_furniture(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_placed_furniture ON island_placed_furniture(island_key)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_island_music ON player_island_music(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_active_music ON island_active_music(island_key)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_overhead_cosmetics ON player_overhead_cosmetics(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_emote_cosmetics ON player_emote_cosmetics(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_emote_triggers ON player_emote_triggers(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_island_structures ON player_island_structures(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_placed_structures ON island_placed_structures(island_key)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_chat_bubbles ON player_chat_bubble_cosmetics(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_island_weather ON player_island_weather(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_active_weather ON island_active_weather(island_key)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_accessories ON player_accessories(uuid)");

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

    // ==================== MODERN ITEM SERIALIZATION (delegated to polished ItemSerializer utility) ====================
    // ItemSerializer extracted/polished as part of DB modularization (ItemSerializer now canonical, modern bytes + legacy fallback).
    String itemToBase64(ItemStack item) { // package-private for DAOs during migration (bridge)
        return com.thenerdcj.util.ItemSerializer.itemToBase64(item);
    }

    ItemStack itemFromBase64(String base64) { // package-private for DAOs during migration (bridge)
        return com.thenerdcj.util.ItemSerializer.itemFromBase64(base64);
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

    // ==================== ISLAND/PLAYER BALANCES (delegated to BalanceDAO) ====================
    // Caching/dirty flags for island balances remain in DatabaseManager (hot path optimization).

    public CompletableFuture<Double> getIslandBalance(GridPosition pos) {
        Double cached = islandBalanceCache.get(pos);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (balanceDAO != null) {
            return balanceDAO.getIslandBalance(pos).thenApply(b -> {
                islandBalanceCache.put(pos, b);
                return b;
            });
        }
        // legacy fallback
        return CompletableFuture.completedFuture(0.0);
    }

    public CompletableFuture<Boolean> setIslandBalance(GridPosition pos, double balance) {
        islandBalanceCache.put(pos, balance);
        dirtyBalances.add(pos);
        if (balanceDAO != null) {
            return balanceDAO.setIslandBalance(pos, balance).thenApply(success -> {
                if (success) dirtyBalances.remove(pos);
                return success;
            });
        }
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> addIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, current + amount));
    }

    public CompletableFuture<Boolean> removeIslandBalance(GridPosition pos, double amount) {
        return addIslandBalance(pos, -amount);
    }

    // Player balances (no cache for now, direct delegation)
    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        if (balanceDAO != null) return balanceDAO.getPlayerBalance(uuid);
        return CompletableFuture.completedFuture(0.0);
    }

    public CompletableFuture<Boolean> setPlayerBalance(UUID uuid, double balance) {
        if (balanceDAO != null) return balanceDAO.setPlayerBalance(uuid, balance);
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        if (balanceDAO != null) return balanceDAO.addPlayerBalance(uuid, amount);
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        if (balanceDAO != null) return balanceDAO.removePlayerBalance(uuid, amount);
        return CompletableFuture.completedFuture(false);
    }

    // ==================== CORE ISLAND PERSISTENCE ====================

    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID ownerUuid, String dimension, String biome) {
        return islandDAO.saveIsland(gridX, gridZ, ownerUuid, dimension, biome);
    }

    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID ownerUuid, String dimension, String biome, long generationSeed) {
        return islandDAO.saveIsland(gridX, gridZ, ownerUuid, dimension, biome, generationSeed);
    }

    /**
     * Loads a full Island object by owner + dimension. Returns null if none exists.
     * Note: This version is synchronous for compatibility with current IslandManager usage.
     */
    public Island getIslandByOwner(UUID ownerUuid, World.Environment dimension) {
        // Delegated to IslandDAO as part of modularization (kept for API compatibility)
        return islandDAO.getIslandByOwner(ownerUuid, dimension);
    }

    public CompletableFuture<Boolean> deleteIsland(UUID ownerUuid, World.Environment dimension) {
        return islandDAO.deleteIsland(ownerUuid, dimension);
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

    // ==================== HOLOGRAM PERSISTENCE (delegated to HologramDAO as part of DB modularization) ====================

    public CompletableFuture<List<HologramData>> loadAllHolograms() {
        if (hologramDAO != null) {
            return hologramDAO.loadAllHolograms();
        }
        // Fallback during partial migration (should not normally hit)
        plugin.getLogger().warning("[DatabaseManager] HologramDAO not available, falling back to legacy path (deprecated).");
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    public CompletableFuture<Boolean> saveHologram(HologramData data) {
        if (hologramDAO != null) {
            return hologramDAO.saveHologram(data);
        }
        plugin.getLogger().warning("[DatabaseManager] HologramDAO not available for saveHologram.");
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> deleteHologram(int id) {
        if (hologramDAO != null) {
            return hologramDAO.deleteHologram(id);
        }
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> updateHologramLines(int id, List<String> lines) {
        if (hologramDAO != null) {
            return hologramDAO.updateHologramLines(id, lines);
        }
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> updateHologramInterval(int id, int interval) {
        if (hologramDAO != null) {
            return hologramDAO.updateHologramInterval(id, interval);
        }
        return CompletableFuture.completedFuture(false);
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

    // ==================== PENDING ITEMS (delegated to PendingItemsDAO) ====================
    public CompletableFuture<List<ItemStack>> getPendingItems(UUID uuid) {
        if (pendingItemsDAO != null) return pendingItemsDAO.getPendingItems(uuid);
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    public boolean storePendingItem(UUID uuid, ItemStack item) {
        if (pendingItemsDAO != null) return pendingItemsDAO.storePendingItem(uuid, item);
        return false;
    }

    // ==================== WARDROBE SYSTEM (delegated to CosmeticDAO) ====================

    public void saveWardrobeSet(UUID uuid, int slot, String setType, com.thenerdcj.wardrobe.WardrobeSet set) {
        if (cosmeticDAO != null) {
            cosmeticDAO.saveWardrobeSet(uuid, slot, setType, set);
            return;
        }
    }

    public void deleteWardrobeSet(UUID uuid, int slot, String setType) {
        if (cosmeticDAO != null) {
            cosmeticDAO.deleteWardrobeSet(uuid, slot, setType);
            return;
        }
    }

    public void loadWardrobeForPlayer(UUID uuid, com.thenerdcj.wardrobe.WardrobeManager manager) {
        if (cosmeticDAO != null) {
            cosmeticDAO.loadWardrobeForPlayer(uuid, manager);
            return;
        }
    }

    public void saveWardrobeCollectionEntry(UUID uuid, String materialName) {
        if (cosmeticDAO != null) {
            cosmeticDAO.saveWardrobeCollectionEntry(uuid, materialName);
            return;
        }
    }

    public Set<Material> loadWardrobeCollection(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadWardrobeCollection(uuid);
        return java.util.Collections.emptySet();
    }

    // ==================== PET PERSISTENCE (delegated to CosmeticDAO) ====================

    public void savePlayerPets(UUID uuid, List<com.thenerdcj.pets.CosmeticPet> pets, com.thenerdcj.pets.CosmeticPet activePet) {
        if (cosmeticDAO != null) {
            cosmeticDAO.savePlayerPets(uuid, pets, activePet);
            cosmeticDAO.saveActivePet(uuid, activePet);
            return;
        }
    }

    public void loadPlayerPets(UUID uuid, com.thenerdcj.pets.PetManager manager) {
        if (cosmeticDAO != null) {
            cosmeticDAO.loadPlayerPets(uuid, manager);
            com.thenerdcj.pets.CosmeticPet active = cosmeticDAO.loadActivePet(uuid);
            if (active != null) {
                manager.addPet(uuid, active);
            }
            return;
        }
    }

    public void savePlayerPetSkins(UUID uuid, Set<String> skinIds) {
        if (cosmeticDAO != null) {
            cosmeticDAO.savePlayerPetSkins(uuid, skinIds);
            return;
        }
    }

    public Set<String> loadPlayerPetSkins(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadPlayerPetSkins(uuid);
        return java.util.Collections.emptySet();
    }

    /**
     * Saves a single collected pet type for a player (used for pet collection/rarity XP).
     * INSERT OR IGNORE so first-time only awards are safe.
     */
    public void savePetCollectionEntry(UUID uuid, String petTypeName) {
        if (cosmeticDAO != null) {
            cosmeticDAO.savePetCollectionEntry(uuid, petTypeName);
            return;
        }
    }

    /**
     * Loads all collected pet types (by name) for a player.
     */
    public Set<String> loadPetCollection(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadPetCollection(uuid);
        return java.util.Collections.emptySet();
    }

    // ==================== PLAYER TAG PERSISTENCE ====================

    public void savePlayerTags(UUID uuid, Set<String> tagEntries, String activeTagEntry) {
        if (cosmeticDAO != null) {
            cosmeticDAO.savePlayerTags(uuid, tagEntries, activeTagEntry);
            return;
        }
    }

    public Set<String> loadPlayerTags(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadPlayerTags(uuid);
        return java.util.Collections.emptySet();
    }

    public String loadActivePlayerTag(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadActivePlayerTag(uuid);
        return null;
    }

    public void savePlayerTagCollectionEntry(UUID uuid, String tagId) {
        if (cosmeticDAO != null) {
            cosmeticDAO.savePlayerTagCollectionEntry(uuid, tagId);
            return;
        }
    }

    public Set<String> loadPlayerTagCollection(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadPlayerTagCollection(uuid);
        return java.util.Collections.emptySet();
    }

    // ==================== ELYTRA WING PERSISTENCE ====================

    public void savePlayerElytraWings(UUID uuid, Set<String> wingIds, String activeWingId) {
        if (cosmeticDAO != null) {
            cosmeticDAO.savePlayerElytraWings(uuid, wingIds, activeWingId);
            return;
        }
    }

    public Set<String> loadPlayerElytraWings(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadPlayerElytraWings(uuid);
        return java.util.Collections.emptySet();
    }

    public String loadActiveElytraWing(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadActiveElytraWing(uuid);
        return null;
    }

    public void saveElytraWingCollectionEntry(UUID uuid, String wingId) {
        if (cosmeticDAO != null) {
            cosmeticDAO.saveElytraWingCollectionEntry(uuid, wingId);
            return;
        }
    }

    public Set<String> loadElytraWingCollection(UUID uuid) {
        Set<String> coll = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT wing_id FROM player_elytra_wing_collection WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) coll.add(rs.getString("wing_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("[Wings] Failed to load elytra wing collection for " + uuid + ": " + e.getMessage());
        }
        return coll;
    }

    // ==================== COSMETIC RUNES PERSISTENCE ====================

    public void savePlayerRunes(UUID uuid, Set<String> runeIds) {
        if (cosmeticDAO != null) {
            cosmeticDAO.savePlayerRunes(uuid, runeIds);
            return;
        }
    }

    public Set<String> loadPlayerRunes(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadPlayerRunes(uuid);
        return java.util.Collections.emptySet();
    }

    // ==================== HELMET SKINS PERSISTENCE ====================

    public void savePlayerHelmetSkins(UUID uuid, Set<String> skinIds) {
        if (cosmeticDAO != null) {
            cosmeticDAO.savePlayerHelmetSkins(uuid, skinIds);
            return;
        }
    }

    public Set<String> loadPlayerHelmetSkins(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadPlayerHelmetSkins(uuid);
        return java.util.Collections.emptySet();
    }

    // ==================== DEATH EFFECTS PERSISTENCE ====================

    public void savePlayerDeathEffects(UUID uuid, Set<String> effectIds) {
        if (cosmeticDAO != null) {
            cosmeticDAO.savePlayerDeathEffects(uuid, effectIds);
            return;
        }
    }

    public Set<String> loadPlayerDeathEffects(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadPlayerDeathEffects(uuid);
        return java.util.Collections.emptySet();
    }

    // Death Messages persistence (new cosmetic system)
    public void savePlayerDeathMessages(UUID uuid, Set<String> messageIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_death_messages WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_death_messages (uuid, message_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
            for (String id : messageIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[DeathMessages] Failed to save death messages for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerDeathMessages(UUID uuid) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT message_id FROM player_death_messages WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getString("message_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("[DeathMessages] Failed to load death messages for " + uuid + ": " + e.getMessage());
        }
        return ids;
    }

    // ==================== BACKPACK SKINS PERSISTENCE (Exploration) ====================

    public void savePlayerBackpackSkins(UUID uuid, Set<String> skinIds) {
        if (cosmeticDAO != null) {
            cosmeticDAO.savePlayerBackpackSkins(uuid, skinIds);
            return;
        }
    }

    public Set<String> loadPlayerBackpackSkins(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadPlayerBackpackSkins(uuid);
        return java.util.Collections.emptySet();
    }

    // ==================== POWER ORB SKINS PERSISTENCE ====================

    public void savePlayerPowerOrbSkins(UUID uuid, Set<String> skinIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_power_orb_skins WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_power_orb_skins (uuid, skin_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();

            for (String id : skinIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[PowerOrbs] Failed to save power orb skins for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerPowerOrbSkins(UUID uuid) {
        Set<String> skins = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT skin_id FROM player_power_orb_skins WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                skins.add(rs.getString("skin_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[PowerOrbs] Failed to load power orb skins for " + uuid + ": " + e.getMessage());
        }
        return skins;
    }

    // ==================== MINION SKINS PERSISTENCE ====================

    public void savePlayerMinionSkins(UUID uuid, Set<String> skinIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_minion_skins WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_minion_skins (uuid, skin_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();

            for (String id : skinIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[MinionSkins] Failed to save minion skins for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerMinionSkins(UUID uuid) {
        Set<String> skins = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT skin_id FROM player_minion_skins WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                skins.add(rs.getString("skin_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[MinionSkins] Failed to load minion skins for " + uuid + ": " + e.getMessage());
        }
        return skins;
    }

    // Minion Skin Assignments (per-minion for assignment polish)
    public void saveMinionSkinAssignment(String islandKey, int minionNum, String skinId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO minion_skin_assignments (island_key, minion_num, skin_id) VALUES (?, ?, ?)")) {
            ps.setString(1, islandKey);
            ps.setInt(2, minionNum);
            ps.setString(3, skinId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[MinionSkins] Failed to save assignment for " + islandKey + " #" + minionNum + ": " + e.getMessage());
        }
    }

    /** Delete a specific per-minion skin assignment (for clear UX polish). */
    public void deleteMinionSkinAssignment(String islandKey, int minionNum) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM minion_skin_assignments WHERE island_key = ? AND minion_num = ?")) {
            ps.setString(1, islandKey);
            ps.setInt(2, minionNum);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[MinionSkins] Failed to delete assignment for " + islandKey + " #" + minionNum + ": " + e.getMessage());
        }
    }

    public Map<Integer, String> loadMinionSkinAssignments(String islandKey) {
        Map<Integer, String> map = new ConcurrentHashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT minion_num, skin_id FROM minion_skin_assignments WHERE island_key = ?")) {
            ps.setString(1, islandKey);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getInt("minion_num"), rs.getString("skin_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[MinionSkins] Failed to load assignments for " + islandKey + ": " + e.getMessage());
        }
        return map;
    }

    // ==================== ISLAND FURNITURE / HOUSING PERSISTENCE (placed data full support) ====================

    /** Lightweight data records for loading placed furniture/structures from DB (no new files). */
    public static record PlacedFurnitureData(String furnitureId, double x, double y, double z, double yaw, String data) {}
    public static record PlacedStructureData(String structureId, double x, double y, double z, double yaw, String data) {}

    // ==================== CORE COLLECTIONS SYSTEM (per-island unique item discovery) ====================

    /**
     * Records a new unique item discovery for an island (e.g. "MINING:DIAMOND_ORE", "FARMING:WHEAT", "COMBAT:ZOMBIE").
     * Uses INSERT OR IGNORE for idempotency (first discovery wins).
     */
    public void saveIslandCollection(String islandKey, String itemKey, UUID discoveredBy) {
        islandDAO.saveIslandCollection(islandKey, itemKey, discoveredBy);
    }

    /**
     * Loads all discovered item keys for an island.
     */
    public Set<String> loadIslandCollections(String islandKey) {
        return islandDAO.loadIslandCollections(islandKey);
    }

    /**
     * Returns total unique collected for island (convenience, used by GUI/manager).
     */
    public int getIslandCollectionCount(String islandKey) {
        return islandDAO.getIslandCollectionCount(islandKey);
    }

    // ==================== PLAYER SKILL SYSTEM (MCMMO-style per-player) ====================

    public void savePlayerSkill(UUID uuid, String skill, double xp, int level) {
        if (uuid == null || skill == null) return;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO player_skills (uuid, skill, xp, level) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, skill);
            ps.setDouble(3, xp);
            ps.setInt(4, level);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Skills] Failed to save skill " + skill + " for " + uuid + ": " + e.getMessage());
        }
    }

    public Map<String, Object[]> loadPlayerSkills(UUID uuid) {
        Map<String, Object[]> skills = new ConcurrentHashMap<>();
        if (uuid == null) return skills;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT skill, xp, level FROM player_skills WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String skill = rs.getString("skill");
                double xp = rs.getDouble("xp");
                int level = rs.getInt("level");
                skills.put(skill, new Object[]{xp, level});
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Skills] Failed to load skills for " + uuid + ": " + e.getMessage());
        }
        return skills;
    }

    public void savePlayerSkills(UUID uuid, Map<String, Object[]> skillData) {
        if (uuid == null || skillData == null) return;
        try (Connection conn = getConnection()) {
            // Delete old for clean replace
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_skills WHERE uuid = ?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO player_skills (uuid, skill, xp, level) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<String, Object[]> entry : skillData.entrySet()) {
                    ins.setString(1, uuid.toString());
                    ins.setString(2, entry.getKey());
                    ins.setDouble(3, (Double) entry.getValue()[0]);
                    ins.setInt(4, (Integer) entry.getValue()[1]);
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Skills] Failed to save skills for " + uuid + ": " + e.getMessage());
        }
    }

    public void savePlayerIslandFurniture(UUID uuid, Set<String> furnitureIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_island_furniture WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_island_furniture (uuid, furniture_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
            for (String id : furnitureIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandFurniture] Failed to save furniture unlocks for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerIslandFurniture(UUID uuid) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT furniture_id FROM player_island_furniture WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("furniture_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandFurniture] Failed to load furniture unlocks for " + uuid + ": " + e.getMessage());
        }
        return ids;
    }

    // Full placed furniture persistence (positions + data + load/delete for runtime + restarts)
    public void savePlacedFurniture(String islandKey, String furnitureId, double x, double y, double z, double yaw, String data) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO island_placed_furniture (island_key, furniture_id, pos_x, pos_y, pos_z, yaw, data, placed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, islandKey);
            ps.setString(2, furnitureId);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.setDouble(6, yaw);
            ps.setString(7, data);
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandFurniture] Failed to save placed furniture: " + e.getMessage());
        }
    }

    // ==================== ISLAND STRUCTURE DECORATIONS PERSISTENCE ====================

    public void savePlayerIslandStructures(UUID uuid, Set<String> structureIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_island_structures WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_island_structures (uuid, structure_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
            for (String id : structureIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandStructures] Failed to save structure unlocks for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerIslandStructures(UUID uuid) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT structure_id FROM player_island_structures WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getString("structure_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandStructures] Failed to load structure unlocks for " + uuid + ": " + e.getMessage());
        }
        return ids;
    }

    public void savePlacedStructure(String islandKey, String structureId, double x, double y, double z, double yaw, String data) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO island_placed_structures (island_key, structure_id, pos_x, pos_y, pos_z, yaw, data, placed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, islandKey);
            ps.setString(2, structureId);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.setDouble(6, yaw);
            ps.setString(7, data);
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandStructures] Failed to save placed structure: " + e.getMessage());
        }
    }

    // ==================== PLACED FURNITURE / STRUCTURES LOAD + DELETE (full persistence polish) ====================

    public java.util.List<PlacedFurnitureData> loadPlacedFurniture(String islandKey) {
        java.util.List<PlacedFurnitureData> list = new java.util.ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT furniture_id, pos_x, pos_y, pos_z, yaw, data FROM island_placed_furniture WHERE island_key = ? ORDER BY placed_at")) {
            ps.setString(1, islandKey);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new PlacedFurnitureData(
                        rs.getString("furniture_id"),
                        rs.getDouble("pos_x"),
                        rs.getDouble("pos_y"),
                        rs.getDouble("pos_z"),
                        rs.getDouble("yaw"),
                        rs.getString("data")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandFurniture] Failed to load placed furniture for " + islandKey + ": " + e.getMessage());
        }
        return list;
    }

    public void deletePlacedFurniture(String islandKey, String furnitureId, double x, double y, double z) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM island_placed_furniture WHERE island_key = ? AND furniture_id = ? AND ABS(pos_x - ?) < 0.01 AND ABS(pos_y - ?) < 0.01 AND ABS(pos_z - ?) < 0.01")) {
            ps.setString(1, islandKey);
            ps.setString(2, furnitureId);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandFurniture] Failed to delete placed furniture: " + e.getMessage());
        }
    }

    public java.util.List<PlacedStructureData> loadPlacedStructures(String islandKey) {
        java.util.List<PlacedStructureData> list = new java.util.ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT structure_id, pos_x, pos_y, pos_z, yaw, data FROM island_placed_structures WHERE island_key = ? ORDER BY placed_at")) {
            ps.setString(1, islandKey);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new PlacedStructureData(
                        rs.getString("structure_id"),
                        rs.getDouble("pos_x"),
                        rs.getDouble("pos_y"),
                        rs.getDouble("pos_z"),
                        rs.getDouble("yaw"),
                        rs.getString("data")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandStructures] Failed to load placed structures for " + islandKey + ": " + e.getMessage());
        }
        return list;
    }

    public void deletePlacedStructure(String islandKey, String structureId, double x, double y, double z) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM island_placed_structures WHERE island_key = ? AND structure_id = ? AND ABS(pos_x - ?) < 0.01 AND ABS(pos_y - ?) < 0.01 AND ABS(pos_z - ?) < 0.01")) {
            ps.setString(1, islandKey);
            ps.setString(2, structureId);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandStructures] Failed to delete placed structure: " + e.getMessage());
        }
    }

    // ==================== ISLAND MUSIC & AMBIENT PERSISTENCE ====================

    public void savePlayerIslandMusic(UUID uuid, Set<String> musicIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_island_music WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_island_music (uuid, music_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
            for (String id : musicIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandMusic] Failed to save music unlocks for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerIslandMusic(UUID uuid) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT music_id FROM player_island_music WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("music_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandMusic] Failed to load music unlocks for " + uuid + ": " + e.getMessage());
        }
        return ids;
    }

    // ==================== OVERHEAD COSMETICS PERSISTENCE ====================

    public void savePlayerOverheadCosmetics(UUID uuid, Set<String> cosmeticIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_overhead_cosmetics WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_overhead_cosmetics (uuid, cosmetic_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
            for (String id : cosmeticIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Overhead] Failed to save overhead cosmetics for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerOverheadCosmetics(UUID uuid) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT cosmetic_id FROM player_overhead_cosmetics WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getString("cosmetic_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("[Overhead] Failed to load overhead cosmetics for " + uuid + ": " + e.getMessage());
        }
        return ids;
    }

    // ==================== EMOTE COSMETICS PERSISTENCE ====================

    public void savePlayerEmoteCosmetics(UUID uuid, Set<String> emoteIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_emote_cosmetics WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_emote_cosmetics (uuid, emote_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
            for (String id : emoteIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Emotes] Failed to save emote cosmetics for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerEmoteCosmetics(UUID uuid) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT emote_id FROM player_emote_cosmetics WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getString("emote_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("[Emotes] Failed to load emote cosmetics for " + uuid + ": " + e.getMessage());
        }
        return ids;
    }

    // ==================== EMOTE TRIGGERS (per-player, persisted) ====================

    public void savePlayerEmoteTriggers(UUID uuid, java.util.Map<String, String> triggers) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_emote_triggers WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR REPLACE INTO player_emote_triggers (uuid, trigger_key, emote_id) VALUES (?, ?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
            if (triggers != null) {
                for (java.util.Map.Entry<String, String> e : triggers.entrySet()) {
                    ins.setString(1, uuid.toString());
                    ins.setString(2, e.getKey());
                    ins.setString(3, e.getValue());
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Emotes] Failed to save emote triggers for " + uuid + ": " + e.getMessage());
        }
    }

    public java.util.Map<String, String> loadPlayerEmoteTriggers(UUID uuid) {
        java.util.Map<String, String> map = new java.util.concurrent.ConcurrentHashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT trigger_key, emote_id FROM player_emote_triggers WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getString("trigger_key"), rs.getString("emote_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Emotes] Failed to load emote triggers for " + uuid + ": " + e.getMessage());
        }
        return map;
    }

    // ==================== Chat Bubble Cosmetics ====================

    public void savePlayerChatBubbleCosmetics(UUID uuid, Set<String> bubbleIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_chat_bubble_cosmetics WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_chat_bubble_cosmetics (uuid, bubble_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
            for (String id : bubbleIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ChatBubbles] Failed to save chat bubble cosmetics for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerChatBubbleCosmetics(UUID uuid) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT bubble_id FROM player_chat_bubble_cosmetics WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getString("bubble_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("[ChatBubbles] Failed to load chat bubble cosmetics for " + uuid + ": " + e.getMessage());
        }
        return ids;
    }

    // ==================== Island Weather Cosmetics ====================

    public void savePlayerIslandWeather(UUID uuid, Set<String> weatherIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_island_weather WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_island_weather (uuid, weather_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
            for (String id : weatherIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandWeather] Failed to save island weather cosmetics for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerIslandWeather(UUID uuid) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT weather_id FROM player_island_weather WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getString("weather_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandWeather] Failed to load island weather cosmetics for " + uuid + ": " + e.getMessage());
        }
        return ids;
    }

    public void saveIslandActiveWeather(String islandKey, String weatherId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO island_active_weather (island_key, weather_id) VALUES (?, ?)")) {
            ps.setString(1, islandKey);
            ps.setString(2, weatherId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandWeather] Failed to save active weather for " + islandKey + ": " + e.getMessage());
        }
    }

    public String loadIslandActiveWeather(String islandKey) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT weather_id FROM island_active_weather WHERE island_key = ?")) {
            ps.setString(1, islandKey);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("weather_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[IslandWeather] Failed to load active weather for " + islandKey + ": " + e.getMessage());
        }
        return null;
    }

    // ==================== Accessories ====================

    public void savePlayerAccessories(UUID uuid, Set<String> accessoryIds) {
        try (Connection conn = getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM player_accessories WHERE uuid = ?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_accessories (uuid, accessory_id) VALUES (?, ?)")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
            for (String id : accessoryIds) {
                ins.setString(1, uuid.toString());
                ins.setString(2, id);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Accessories] Failed to save accessories for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> loadPlayerAccessories(UUID uuid) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT accessory_id FROM player_accessories WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getString("accessory_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("[Accessories] Failed to load accessories for " + uuid + ": " + e.getMessage());
        }
        return ids;
    }

    public void saveIslandActiveMusic(String islandKey, String musicId) {
        islandDAO.saveIslandActiveMusic(islandKey, musicId);
    }

    public String loadIslandActiveMusic(String islandKey) {
        return islandDAO.loadIslandActiveMusic(islandKey);
    }

    public void saveRuneCollectionEntry(UUID uuid, String runeId) {
        if (cosmeticDAO != null) {
            cosmeticDAO.saveRuneCollectionEntry(uuid, runeId);
            return;
        }
    }

    public Set<String> loadRuneCollection(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadPlayerRunes(uuid); // note: may need specific loadRuneCollection in DAO, using load for now
        return java.util.Collections.emptySet();
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
        // Flush dirty island balances (delegated to BalanceDAO)
        for (GridPosition pos : dirtyBalances) {
            Double balance = islandBalanceCache.get(pos);
            if (balance != null) {
                if (balanceDAO != null) {
                    balanceDAO.flushIslandBalance(pos, balance);
                } else {
                    // fallback legacy
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
        return islandDAO.saveIslandUpgrade(islandKey, upgrade, level);
    }

    /**
     * Load all upgrades and their levels for an island.
     */
    public CompletableFuture<Map<IslandUpgrade, Integer>> loadIslandUpgrades(String islandKey) {
        return islandDAO.loadIslandUpgrades(islandKey);
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

    // ==================== PUNISHMENTS (delegated to PunishmentDAO) ====================

    public CompletableFuture<Boolean> logPunishment(UUID target, UUID staff, Punishment.Type type, String reason, long duration) {
        if (punishmentDAO != null) return punishmentDAO.logPunishment(target, staff, type, reason, duration);
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<List<Punishment>> getActivePunishments(UUID uuid) {
        if (punishmentDAO != null) return punishmentDAO.getActivePunishments(uuid);
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    public CompletableFuture<List<Punishment>> getPunishmentsForPlayer(UUID uuid) {
        if (punishmentDAO != null) return punishmentDAO.getPunishmentsForPlayer(uuid);
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    public CompletableFuture<Boolean> unbanPlayer(UUID uuid) {
        if (punishmentDAO != null) return punishmentDAO.unbanPlayer(uuid);
        return CompletableFuture.completedFuture(false);
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

    // ==================== PARTICLE TRAILS (delegated to CosmeticDAO) ====================

    public Set<String> loadUnlockedParticleTrails(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadUnlockedParticleTrails(uuid);
        return java.util.Collections.emptySet();
    }

    public void saveUnlockedParticleTrails(UUID uuid, Set<String> trails) {
        if (cosmeticDAO != null) {
            cosmeticDAO.saveUnlockedParticleTrails(uuid, trails);
            return;
        }
        // legacy no-op if no dao
    }

    public String loadActiveParticleTrail(UUID uuid) {
        if (cosmeticDAO != null) return cosmeticDAO.loadActiveParticleTrail(uuid);
        return null;
    }

    public void saveActiveParticleTrail(UUID uuid, String trailId) {
        if (cosmeticDAO != null) {
            cosmeticDAO.saveActiveParticleTrail(uuid, trailId);
            return;
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
        return getTopIslandsByWorth(limit, 0);
    }

    /**
     * DB-paginated top islands by worth for large scale (1000+ islands).
     * Supports LIMIT + OFFSET for server-side pagination (data compression, no full load in mem for leaderboards/tops).
     * Complements event-driven and per-island Region notes.
     * See IMPROVEMENTS "For 1000+ islands: make leaderboard/top queries fully DB paginated".
     */
    public CompletableFuture<List<TopWorthEntry>> getTopIslandsByWorth(int limit, int offset) {
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
                LIMIT ? OFFSET ?
                """;

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ps.setInt(2, Math.max(0, offset));
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
        // Delegated to IslandDAO for per-dimension reset + related state cleanup.
        // Legacy global last_reset update kept for older code paths during modularization.
        islandDAO.recordIslandReset(playerUuid, dimension);
        long now = System.currentTimeMillis();
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
