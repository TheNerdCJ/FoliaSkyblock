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
                "CREATE TABLE IF NOT EXISTS player_ranks (uuid TEXT PRIMARY KEY, rank_id TEXT, votes INTEGER DEFAULT 0, last_vote TIMESTAMP)",
                "CREATE TABLE IF NOT EXISTS muted_players (uuid TEXT PRIMARY KEY, muted_by TEXT, reason TEXT, expires_at TIMESTAMP)",
                "CREATE TABLE IF NOT EXISTS challenges (id TEXT PRIMARY KEY, island_id TEXT, type TEXT, description TEXT, target INTEGER, progress INTEGER DEFAULT 0, reward_xp INTEGER, completed BOOLEAN DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                "CREATE TABLE IF NOT EXISTS island_upgrades (island_id TEXT, upgrade_name TEXT, level INTEGER DEFAULT 0, PRIMARY KEY (island_id, upgrade_name))",
                "CREATE TABLE IF NOT EXISTS auctions (id TEXT PRIMARY KEY, seller_uuid TEXT, item_material TEXT, item_amount INTEGER, starting_price REAL, current_bid REAL, current_bidder TEXT, end_time INTEGER, active BOOLEAN, sold_to TEXT)",
                "CREATE TABLE IF NOT EXISTS bazaar_orders (id TEXT PRIMARY KEY, player_uuid TEXT, material TEXT, amount INTEGER, price_per_unit REAL, is_buy_order BOOLEAN, created_at INTEGER, filled BOOLEAN)",
                "CREATE TABLE IF NOT EXISTS pending_items (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT, item_material TEXT, item_amount INTEGER)"
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
                         "INSERT OR REPLACE INTO player_balances (uuid, balance) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, balance);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO player_balances (uuid, balance) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET balance = balance + ?")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, amount);
                ps.setDouble(3, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE player_balances SET balance = balance - ? WHERE uuid = ? AND balance >= ?")) {
                ps.setDouble(1, amount);
                ps.setString(2, uuid.toString());
                ps.setDouble(3, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
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
                         "INSERT OR REPLACE INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension);
                ps.setDouble(4, balance);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> addIslandBalance(int gridX, int gridZ, String dimension, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?) ON CONFLICT(grid_x, grid_z, dimension) DO UPDATE SET balance = balance + ?")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension);
                ps.setDouble(4, amount);
                ps.setDouble(5, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> removeIslandBalance(int gridX, int gridZ, String dimension, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE island_balances SET balance = balance - ? WHERE grid_x = ? AND grid_z = ? AND dimension = ? AND balance >= ?")) {
                ps.setDouble(1, amount);
                ps.setInt(2, gridX);
                ps.setInt(3, gridZ);
                ps.setString(4, dimension);
                ps.setDouble(5, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    // ====================== ISLAND DATA ======================
    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID ownerUuid, String biomeName, org.bukkit.World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO islands (grid_x, grid_z, dimension, owner_uuid, biome_name) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension.name());
                ps.setString(4, ownerUuid.toString());
                ps.setString(5, biomeName);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<com.thenerdcj.island.Island> getIslandByOwner(UUID ownerUuid, org.bukkit.World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, dimension.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        GridPosition pos = new GridPosition(
                                rs.getInt("grid_x"),
                                rs.getInt("grid_z"),
                                org.bukkit.World.Environment.valueOf(rs.getString("dimension"))
                        );
                        return new com.thenerdcj.island.Island(
                                pos,
                                UUID.fromString(rs.getString("owner_uuid")),
                                rs.getString("biome_name"),
                                org.bukkit.World.Environment.valueOf(rs.getString("dimension"))
                        );
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return null;
        }, executor);
    }

    public CompletableFuture<Boolean> deleteIsland(UUID ownerUuid, org.bukkit.World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, dimension.name());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    // ====================== RANKS ======================
    public String getCurrentRankId(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT rank_id FROM player_ranks WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("rank_id");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return "MEMBER";
    }

    public int getUpvoteCount(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT votes FROM player_ranks WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("votes");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public CompletableFuture<Boolean> setRank(UUID uuid, String rankId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO player_ranks (uuid, rank_id) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
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

    // ====================== CHALLENGES ======================
    public CompletableFuture<Boolean> saveChallenge(String id, String islandId, String type, String description, int target, int progress, int rewardXp, boolean completed) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO challenges (id, island_id, type, description, target, progress, reward_xp, completed) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, islandId);
                ps.setString(3, type);
                ps.setString(4, description);
                ps.setInt(5, target);
                ps.setInt(6, progress);
                ps.setInt(7, rewardXp);
                ps.setBoolean(8, completed);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<List<Map<String, Object>>> getIslandChallenges(String islandId) {
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

    // ====================== TOP BALANCES ======================
    public List<TopBalanceEntry> getTopBalances(int limit) {
        List<TopBalanceEntry> top = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid, balance FROM player_balances ORDER BY balance DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    top.add(new TopBalanceEntry(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getDouble("balance")
                    ));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return top;
    }

    public static class TopBalanceEntry {
        private final UUID uuid;
        private final double balance;

        public TopBalanceEntry(UUID uuid, double balance) {
            this.uuid = uuid;
            this.balance = balance;
        }

        public UUID uuid() { return uuid; }
        public double balance() { return balance; }
    }

    // ====================== AUCTION SYSTEM ======================
    public CompletableFuture<Boolean> saveAuction(com.thenerdcj.auction.Auction auction) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO auctions (id, seller_uuid, item_material, item_amount, starting_price, current_bid, current_bidder, end_time, active) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, auction.getId());
                ps.setString(2, auction.getSellerUuid().toString());
                ps.setString(3, auction.getItemMaterial());
                ps.setInt(4, auction.getItemAmount());
                ps.setDouble(5, auction.getStartingPrice());
                ps.setDouble(6, auction.getCurrentBid());
                ps.setString(7, auction.getCurrentBidder() != null ? auction.getCurrentBidder().toString() : null);
                ps.setLong(8, auction.getEndTime());
                ps.setBoolean(9, auction.isActive());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<List<com.thenerdcj.auction.Auction>> getActiveAuctions() {
        return CompletableFuture.supplyAsync(() -> {
            List<com.thenerdcj.auction.Auction> auctions = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM auctions WHERE active = 1 AND end_time > ?")) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID currentBidder = rs.getString("current_bidder") != null ?
                                UUID.fromString(rs.getString("current_bidder")) : null;
                        auctions.add(new com.thenerdcj.auction.Auction(
                                rs.getString("id"),
                                UUID.fromString(rs.getString("seller_uuid")),
                                rs.getString("item_material"),
                                rs.getInt("item_amount"),
                                rs.getDouble("starting_price"),
                                rs.getDouble("current_bid"),
                                currentBidder,
                                rs.getLong("end_time"),
                                rs.getBoolean("active")
                        ));
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return auctions;
        }, executor);
    }

    public CompletableFuture<Boolean> updateAuction(com.thenerdcj.auction.Auction auction) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE auctions SET current_bid = ?, current_bidder = ? WHERE id = ?")) {
                ps.setDouble(1, auction.getCurrentBid());
                ps.setString(2, auction.getCurrentBidder() != null ? auction.getCurrentBidder().toString() : null);
                ps.setString(3, auction.getId());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> markAuctionSold(String auctionId, UUID buyerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE auctions SET active = 0, sold_to = ? WHERE id = ?")) {
                ps.setString(1, buyerUuid.toString());
                ps.setString(2, auctionId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> markAuctionExpired(String auctionId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE auctions SET active = 0 WHERE id = ?")) {
                ps.setString(1, auctionId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    // ====================== BAZAAR SYSTEM ======================
    public CompletableFuture<Boolean> saveBazaarOrder(com.thenerdcj.bazaar.BazaarOrder order) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO bazaar_orders (id, player_uuid, material, amount, price_per_unit, is_buy_order, created_at, filled) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, order.getId());
                ps.setString(2, order.getPlayerUuid().toString());
                ps.setString(3, order.getMaterial());
                ps.setInt(4, order.getAmount());
                ps.setDouble(5, order.getPricePerUnit());
                ps.setBoolean(6, order.isBuyOrder());
                ps.setLong(7, order.getCreatedAt());
                ps.setBoolean(8, order.isFilled());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<List<com.thenerdcj.bazaar.BazaarOrder>> getActiveBazaarOrders() {
        return CompletableFuture.supplyAsync(() -> {
            List<com.thenerdcj.bazaar.BazaarOrder> orders = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM bazaar_orders WHERE filled = 0")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        orders.add(new com.thenerdcj.bazaar.BazaarOrder(
                                rs.getString("id"),
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getString("material"),
                                rs.getInt("amount"),
                                rs.getDouble("price_per_unit"),
                                rs.getBoolean("is_buy_order"),
                                rs.getLong("created_at"),
                                rs.getBoolean("filled")
                        ));
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return orders;
        }, executor);
    }

    public CompletableFuture<Boolean> markBazaarOrderFilled(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE bazaar_orders SET filled = 1 WHERE id = ?")) {
                ps.setString(1, orderId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> storePendingItem(UUID playerUuid, org.bukkit.inventory.ItemStack item) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO pending_items (player_uuid, item_material, item_amount) VALUES (?, ?, ?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, item.getType().name());
                ps.setInt(3, item.getAmount());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }
}