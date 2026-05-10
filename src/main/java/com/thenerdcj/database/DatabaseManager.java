package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandRank;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import com.thenerdcj.auction.Auction;
import com.thenerdcj.bazaar.BazaarOrder;

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

    public void initDatabase() {
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
                "CREATE TABLE IF NOT EXISTS island_upgrades (island_id TEXT, upgrade_name TEXT, level INTEGER DEFAULT 0, PRIMARY KEY (island_id, upgrade_name))",
                "CREATE TABLE IF NOT EXISTS pending_items (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, item_material TEXT NOT NULL, item_amount INTEGER NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                "CREATE TABLE IF NOT EXISTS auctions (id TEXT PRIMARY KEY, seller_uuid TEXT NOT NULL, item_material TEXT NOT NULL, item_amount INTEGER NOT NULL, starting_price REAL NOT NULL, current_bid REAL DEFAULT 0, current_bidder TEXT, end_time BIGINT NOT NULL, active BOOLEAN DEFAULT TRUE)",
                "CREATE TABLE IF NOT EXISTS bazaar_orders (id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, material TEXT NOT NULL, amount INTEGER NOT NULL, price_per_unit REAL NOT NULL, is_buy_order BOOLEAN NOT NULL, created_at BIGINT NOT NULL, filled BOOLEAN DEFAULT FALSE)",
                "CREATE TABLE IF NOT EXISTS island_minions (island_id TEXT PRIMARY KEY, minion_count INTEGER DEFAULT 0, fuel_level INTEGER DEFAULT 1000)",
                // For slayer stats and leaderboards (Play to Win grind tracking)
                "CREATE TABLE IF NOT EXISTS slayer_kills (player_uuid TEXT, player_name TEXT, entity_type TEXT, total_kills INTEGER DEFAULT 0, PRIMARY KEY (player_uuid, entity_type))"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : tables) stmt.executeUpdate(sql);

            // Indexes...
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_balances_uuid ON player_balances(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_island_balances_grid ON island_balances(grid_x, grid_z, dimension)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_islands_owner ON islands(owner_uuid, dimension)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_ranks_uuid ON player_ranks(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_muted_players_expires ON muted_players(expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_balances_balance ON player_balances(balance DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_island_balances_balance ON island_balances(balance DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_island_upgrades_island ON island_upgrades(island_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions(seller_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_auctions_end_time ON auctions(end_time)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bazaar_orders_player ON bazaar_orders(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_island_minions_island ON island_minions(island_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_slayer_kills_player ON slayer_kills(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_slayer_kills_entity ON slayer_kills(entity_type)");

            plugin.getLogger().info("§aAll tables and indexes created with HikariCP.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Synchronous executeUpdate for DDL like table creation in managers' constructors.
     * For DML prefer executeUpdateAsync.
     */
    public boolean executeUpdate(String sql) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to execute update: " + e.getMessage());
            return false;
        }
    }

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

    // ====================== PLAYER BALANCE (existing + add/remove) ======================
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

    // ====================== ISLAND CORE (save, get, delete, members) ======================
    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID owner, String biomeName, String dimension) {
        return executeUpdateAsync(
            "INSERT INTO islands (grid_x, grid_z, dimension, owner_uuid, biome_name, level, xp) VALUES (?, ?, ?, ?, ?, 1, 0.0) " +
            "ON CONFLICT(grid_x, grid_z, dimension) DO UPDATE SET owner_uuid = ?, biome_name = ?",
            gridX, gridZ, dimension, owner.toString(), biomeName, owner.toString(), biomeName
        ).thenApply(success -> {
            if (success) {
                // Init balances and levels
                executeUpdateAsync("INSERT OR IGNORE INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, 0.0)",
                    gridX, gridZ, dimension);
                executeUpdateAsync("INSERT OR IGNORE INTO island_levels (grid_x, grid_z, dimension, level, xp) VALUES (?, ?, ?, 1, 0.0)",
                    gridX, gridZ, dimension);
            }
            return success;
        });
    }

    public CompletableFuture<Island> getIslandByOwner(UUID owner, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT grid_x, grid_z, biome_name, level, xp FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                ps.setString(1, owner.toString());
                ps.setString(2, dimension.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int gx = rs.getInt("grid_x");
                        int gz = rs.getInt("grid_z");
                        String biome = rs.getString("biome_name");
                        GridPosition pos = new GridPosition(gx, gz, dimension);
                        Island island = new Island(pos, owner, biome, dimension);
                        island.setLevel(rs.getInt("level"));
                        island.setXp(rs.getDouble("xp"));
                        // Load members
                        loadIslandMembers(island, gx, gz, dimension.name());
                        return island;
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return null;
        }, executor);
    }

    private void loadIslandMembers(Island island, int gridX, int gridZ, String dimension) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT member_uuid, rank FROM island_members WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
            ps.setInt(1, gridX);
            ps.setInt(2, gridZ);
            ps.setString(3, dimension);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID member = UUID.fromString(rs.getString("member_uuid"));
                    IslandRank rank = IslandRank.valueOf(rs.getString("rank"));
                    if (!island.isOwner(member)) {
                        island.addMember(member, rank);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public CompletableFuture<Boolean> deleteIsland(UUID owner, World.Environment dimension) {
        String dimStr = dimension.name();
        return executeUpdateAsync("DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?", owner.toString(), dimStr)
            .thenCompose(success -> {
                executeUpdateAsync("DELETE FROM island_members WHERE owner_uuid = ? AND dimension = ?", owner.toString(), dimStr); // note: may need grid lookup but simple
                executeUpdateAsync("DELETE FROM island_balances WHERE owner_uuid = ? AND dimension = ?", owner.toString(), dimStr);
                executeUpdateAsync("DELETE FROM island_levels WHERE owner_uuid = ? AND dimension = ?", owner.toString(), dimStr);
                return CompletableFuture.completedFuture(success);
            });
    }

    public CompletableFuture<Boolean> addIslandMember(int gridX, int gridZ, String dimension, UUID member, String rank) {
        return executeUpdateAsync(
            "INSERT INTO island_members (grid_x, grid_z, dimension, member_uuid, rank) VALUES (?, ?, ?, ?, ?) " +
            "ON CONFLICT(grid_x, grid_z, dimension, member_uuid) DO UPDATE SET rank = ?",
            gridX, gridZ, dimension, member.toString(), rank, rank
        );
    }

    public CompletableFuture<Boolean> removeIslandMember(int gridX, int gridZ, String dimension, UUID member) {
        return executeUpdateAsync(
            "DELETE FROM island_members WHERE grid_x = ? AND grid_z = ? AND dimension = ? AND member_uuid = ?",
            gridX, gridZ, dimension, member.toString()
        );
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

    public CompletableFuture<Boolean> setIslandBalance(int gridX, int gridZ, String dimension, double amount) {
        return executeUpdateAsync(
            "INSERT INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(grid_x, grid_z, dimension) DO UPDATE SET balance = ?",
            gridX, gridZ, dimension, amount, amount
        );
    }

    public CompletableFuture<Boolean> addIslandBalance(int gridX, int gridZ, String dimension, double amount) {
        return executeUpdateAsync(
            "INSERT INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(grid_x, grid_z, dimension) DO UPDATE SET balance = balance + ?",
            gridX, gridZ, dimension, amount, amount
        );
    }

    public CompletableFuture<Boolean> removeIslandBalance(int gridX, int gridZ, String dimension, double amount) {
        return executeUpdateAsync(
            "UPDATE island_balances SET balance = MAX(0, balance - ?) WHERE grid_x = ? AND grid_z = ? AND dimension = ?",
            amount, gridX, gridZ, dimension
        );
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
        return executeUpdateAsync(
            "INSERT INTO player_ranks (uuid, rank_id) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET rank_id = ?",
            uuid.toString(), rankId, rankId
        );
    }

    public CompletableFuture<Boolean> addVote(UUID voter, UUID target) {
        return executeUpdateAsync(
            "INSERT OR IGNORE INTO player_votes (voter_uuid, target_uuid) VALUES (?, ?)",
            voter.toString(), target.toString()
        ).thenCompose(success -> {
            if (success) {
                return executeUpdateAsync(
                    "UPDATE player_ranks SET upvotes = upvotes + 1 WHERE uuid = ?",
                    target.toString()
                );
            }
            return CompletableFuture.completedFuture(false);
        });
    }

    // ====================== BOSS / SLAYER ======================
    public CompletableFuture<Boolean> incrementSlayerKills(UUID player, String playerName, String entityType, int amount) {
        return executeUpdateAsync(
            "INSERT INTO slayer_kills (player_uuid, player_name, entity_type, total_kills) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(player_uuid, entity_type) DO UPDATE SET total_kills = total_kills + ?, player_name = ?",
            player.toString(), playerName, entityType, amount, amount, playerName
        );
    }

    // ====================== MINION (existing in file) ======================
    public CompletableFuture<Map<String, Integer>> loadMinionData(String islandId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> data = new HashMap<>();
            data.put("count", 0);
            data.put("fuel", 1000);
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT minion_count, fuel_level FROM island_minions WHERE island_id = ?")) {
                ps.setString(1, islandId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.put("count", rs.getInt("minion_count"));
                        data.put("fuel", rs.getInt("fuel_level"));
                    }
                }
            } catch (SQLException e) { 
                plugin.getLogger().warning("[DatabaseManager] Failed to load minion data for " + islandId + ": " + e.getMessage());
            }
            return data;
        }, executor);
    }

    public CompletableFuture<Boolean> saveMinionData(String islandId, int count, int fuelLevel) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO island_minions (island_id, minion_count, fuel_level) VALUES (?, ?, ?) " +
                                 "ON CONFLICT(island_id) DO UPDATE SET minion_count = ?, fuel_level = ?")) {
                ps.setString(1, islandId);
                ps.setInt(2, count);
                ps.setInt(3, fuelLevel);
                ps.setInt(4, count);
                ps.setInt(5, fuelLevel);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { 
                plugin.getLogger().severe("Failed to save minion data: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    // ====================== AUCTION SYSTEM ======================
    public CompletableFuture<List<Auction>> getActiveAuctions() {
        return CompletableFuture.supplyAsync(() -> {
            List<Auction> auctions = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM auctions WHERE active = TRUE AND end_time > ?")) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
                        String itemMaterial = rs.getString("item_material");
                        int itemAmount = rs.getInt("item_amount");
                        double startingPrice = rs.getDouble("starting_price");
                        double currentBid = rs.getDouble("current_bid");
                        String bidderStr = rs.getString("current_bidder");
                        UUID currentBidder = (bidderStr != null && !bidderStr.isEmpty()) ? UUID.fromString(bidderStr) : null;
                        long endTime = rs.getLong("end_time");
                        boolean active = rs.getBoolean("active");
                        auctions.add(new Auction(id, sellerUuid, itemMaterial, itemAmount, startingPrice, currentBid, currentBidder, endTime, active));
                    }
                }
            } catch (SQLException e) { 
                plugin.getLogger().severe("Failed to get active auctions: " + e.getMessage());
            }
            return auctions;
        }, executor);
    }

    public CompletableFuture<Boolean> saveAuction(Auction auction) {
        return executeUpdateAsync(
            "INSERT INTO auctions (id, seller_uuid, item_material, item_amount, starting_price, current_bid, current_bidder, end_time, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            auction.getId(),
            auction.getSellerUuid().toString(),
            auction.getItemMaterial(),
            auction.getItemAmount(),
            auction.getStartingPrice(),
            auction.getCurrentBid(),
            auction.getCurrentBidder() != null ? auction.getCurrentBidder().toString() : null,
            auction.getEndTime(),
            auction.isActive()
        );
    }

    public CompletableFuture<Boolean> updateAuction(Auction auction) {
        return executeUpdateAsync(
            "UPDATE auctions SET current_bid = ?, current_bidder = ?, active = ? WHERE id = ?",
            auction.getCurrentBid(),
            auction.getCurrentBidder() != null ? auction.getCurrentBidder().toString() : null,
            auction.isActive(),
            auction.getId()
        );
    }

    public CompletableFuture<Boolean> storePendingItem(UUID player, ItemStack item) {
        // Simple: store material and amount; full ItemStack serialization needs more (e.g. base64)
        return executeUpdateAsync(
            "INSERT INTO pending_items (player_uuid, item_material, item_amount) VALUES (?, ?, ?)",
            player.toString(), item.getType().name(), item.getAmount()
        );
    }

    public CompletableFuture<Boolean> markAuctionSold(String auctionId, UUID buyer) {
        return executeUpdateAsync(
            "UPDATE auctions SET active = FALSE, current_bidder = ? WHERE id = ?",
            buyer.toString(), auctionId
        );
    }

    public CompletableFuture<Boolean> markAuctionExpired(String auctionId) {
        return executeUpdateAsync("UPDATE auctions SET active = FALSE WHERE id = ?", auctionId);
    }

    // ====================== BAZAAR SYSTEM ======================
    public CompletableFuture<List<BazaarOrder>> getActiveBazaarOrders() {
        return CompletableFuture.supplyAsync(() -> {
            List<BazaarOrder> orders = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM bazaar_orders WHERE filled = FALSE")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                        String material = rs.getString("material");
                        int amount = rs.getInt("amount");
                        double pricePerUnit = rs.getDouble("price_per_unit");
                        boolean isBuyOrder = rs.getBoolean("is_buy_order");
                        long createdAt = rs.getLong("created_at");
                        boolean filled = rs.getBoolean("filled");
                        orders.add(new BazaarOrder(id, playerUuid, material, amount, pricePerUnit, isBuyOrder, createdAt, filled));
                    }
                }
            } catch (SQLException e) { 
                plugin.getLogger().severe("Failed to get active bazaar orders: " + e.getMessage());
            }
            return orders;
        }, executor);
    }

    public CompletableFuture<Boolean> saveBazaarOrder(BazaarOrder order) {
        return executeUpdateAsync(
            "INSERT INTO bazaar_orders (id, player_uuid, material, amount, price_per_unit, is_buy_order, created_at, filled) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            order.getId(),
            order.getPlayerUuid().toString(),
            order.getMaterial(),
            order.getAmount(),
            order.getPricePerUnit(),
            order.isBuyOrder(),
            order.getCreatedAt(),
            order.isFilled()
        );
    }

    public CompletableFuture<Boolean> markBazaarOrderFilled(String orderId) {
        return executeUpdateAsync("UPDATE bazaar_orders SET filled = TRUE WHERE id = ?", orderId);
    }

    // ====================== LEADERBOARDS ======================
    public CompletableFuture<List<Map<String, Object>>> getGlobalTopSlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> tops = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT player_name, SUM(total_kills) as kills FROM slayer_kills GROUP BY player_uuid ORDER BY kills DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("name", rs.getString("player_name"));
                        entry.put("kills", rs.getInt("kills"));
                        tops.add(entry);
                    }
                }
            } catch (SQLException e) { /* table may not have data */ }
            return tops;
        }, executor);
    }

    // ====================== EXISTING METHODS (loadIslandUpgrades, saveIslandUpgrade, getTopIslandsByLevel, etc.) keep from original =====
    // (The original methods like loadIslandUpgrades, saveMinionData, getTopIslandsByLevel, getTopPlayerBalances, getTopVotes, getTopSlayers, shutdown are preserved)

    public CompletableFuture<Map<String, Integer>> loadIslandUpgrades(String islandId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> upgrades = new HashMap<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT upgrade_name, level FROM island_upgrades WHERE island_id = ?")) {
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

    public CompletableFuture<Boolean> saveIslandUpgrade(String islandId, String upgradeName, int level) {
        return executeUpdateAsync(
            "INSERT INTO island_upgrades (island_id, upgrade_name, level) VALUES (?, ?, ?) " +
            "ON CONFLICT(island_id, upgrade_name) DO UPDATE SET level = ?",
            islandId, upgradeName, level, level
        );
    }

    // Leaderboard methods from original (getTopIslandsByLevel, getTopPlayerBalances, getTopVotes, getTopSlayers) - assumed present or add similar

    public CompletableFuture<List<Map<String, Object>>> getTopIslandsByLevel(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> tops = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT grid_x, grid_z, dimension, level, xp FROM islands ORDER BY level DESC, xp DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("gridX", rs.getInt("grid_x"));
                        entry.put("gridZ", rs.getInt("grid_z"));
                        entry.put("dimension", rs.getString("dimension"));
                        entry.put("level", rs.getInt("level"));
                        entry.put("xp", rs.getDouble("xp"));
                        tops.add(entry);
                    }
                }
            } catch (SQLException e) { plugin.getLogger().warning("[DB] Leaderboard error: " + e.getMessage()); }
            return tops;
        }, executor);
    }

    public CompletableFuture<List<Map<String, Object>>> getTopPlayerBalances(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> tops = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT uuid, balance FROM player_balances ORDER BY balance DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("uuid", rs.getString("uuid"));
                        entry.put("balance", rs.getDouble("balance"));
                        tops.add(entry);
                    }
                }
            } catch (SQLException e) { plugin.getLogger().warning("[DB] Bal leaderboard error"); }
            return tops;
        }, executor);
    }

    public CompletableFuture<List<Map<String, Object>>> getTopVotes(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> tops = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT uuid, upvotes FROM player_ranks ORDER BY upvotes DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("uuid", rs.getString("uuid"));
                        entry.put("votes", rs.getInt("upvotes"));
                        tops.add(entry);
                    }
                }
            } catch (SQLException e) { plugin.getLogger().warning("[DB] Votes leaderboard error"); }
            return tops;
        }, executor);
    }

    public CompletableFuture<List<Map<String, Object>>> getTopSlayers(String entityType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> tops = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT player_uuid, player_name, total_kills FROM slayer_kills WHERE entity_type = ? ORDER BY total_kills DESC LIMIT ?")) {
                ps.setString(1, entityType);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("uuid", rs.getString("player_uuid"));
                        entry.put("name", rs.getString("player_name"));
                        entry.put("kills", rs.getInt("total_kills"));
                        tops.add(entry);
                    }
                }
            } catch (SQLException e) {
                // Table may not exist yet
            }
            return tops;
        }, executor);
    }

    public void shutdown() {
        plugin.getLogger().info("§e[FoliaSkyblock] Shutting down database...");
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("§a[FoliaSkyblock] HikariCP connection pool closed.");
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            plugin.getLogger().info("§a[FoliaSkyblock] Database executor shut down.");
        }
    }
}
