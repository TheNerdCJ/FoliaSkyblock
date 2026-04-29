package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.IslandRank;
import org.bukkit.World;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {

    private final FoliaSkyblock plugin;
    private Connection connection;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private PreparedStatement getPlayerBalanceStmt;
    private PreparedStatement setPlayerBalanceStmt;

    public DatabaseManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        initDatabase();
        createTables();
        prepareStatements();
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/skyblock.db");
            connection.setAutoCommit(true);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
                stmt.execute("PRAGMA cache_size = 10000");
                stmt.execute("PRAGMA temp_store = MEMORY");
            }

            plugin.getLogger().info("§aSQLite connected with performance optimizations!");
        } catch (Exception e) {
            plugin.getLogger().severe("§cDatabase connection failed!");
            e.printStackTrace();
        }
    }

    private void createTables() {
        String[] tables = {
                "CREATE TABLE IF NOT EXISTS islands (grid_x INTEGER, grid_z INTEGER, dimension TEXT, owner_uuid TEXT, biome_name TEXT, level INTEGER DEFAULT 1, xp REAL DEFAULT 0.0, PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS island_members (grid_x INTEGER, grid_z INTEGER, dimension TEXT, member_uuid TEXT, rank TEXT, PRIMARY KEY (grid_x, grid_z, dimension, member_uuid))",
                "CREATE TABLE IF NOT EXISTS island_balances (grid_x INTEGER, grid_z INTEGER, dimension TEXT, balance REAL DEFAULT 0.0, PRIMARY KEY (grid_x, grid_z, dimension))",
                "CREATE TABLE IF NOT EXISTS player_balances (uuid TEXT PRIMARY KEY, balance REAL DEFAULT 0.0)",
                "CREATE TABLE IF NOT EXISTS player_ranks (uuid TEXT PRIMARY KEY, rank_id TEXT DEFAULT 'member', upvotes INTEGER DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS muted_players (uuid TEXT PRIMARY KEY, muted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMP, muted_by TEXT, reason TEXT)"
        };

        try (Statement stmt = connection.createStatement()) {
            for (String sql : tables) stmt.executeUpdate(sql);

            // ==================== INDEXES ====================
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_balances_uuid ON player_balances(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_island_balances_grid ON island_balances(grid_x, grid_z, dimension)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_islands_owner ON islands(owner_uuid, dimension)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_ranks_uuid ON player_ranks(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_muted_players_expires ON muted_players(expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_balances_balance ON player_balances(balance DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_island_balances_balance ON island_balances(balance DESC)");

            plugin.getLogger().info("§aTables and indexes created.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void prepareStatements() {
        try {
            getPlayerBalanceStmt = connection.prepareStatement("SELECT balance FROM player_balances WHERE uuid = ?");
            setPlayerBalanceStmt = connection.prepareStatement("INSERT OR REPLACE INTO player_balances (uuid, balance) VALUES (?, ?)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private CompletableFuture<Boolean> supplyAsync(java.util.function.Supplier<Boolean> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    // ====================== ISLAND MANAGER ======================
    public CompletableFuture<Boolean> saveIsland(int x, int z, UUID owner, String biome, World.Environment dimension) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO islands (grid_x, grid_z, owner_uuid, biome_name, dimension) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, x); ps.setInt(2, z); ps.setString(3, owner.toString());
                ps.setString(4, biome); ps.setString(5, dimension.name());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        });
    }

    public CompletableFuture<Boolean> saveMember(int x, int z, UUID playerUuid, IslandRank rank, World.Environment dimension) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO island_members (grid_x, grid_z, dimension, member_uuid, rank) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, x); ps.setInt(2, z); ps.setString(3, dimension.name());
                ps.setString(4, playerUuid.toString()); ps.setString(5, rank.name());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        });
    }

    public CompletableFuture<Boolean> saveIslandLevel(int x, int z, int level, double xp, World.Environment dimension) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO island_levels (grid_x, grid_z, dimension, level, xp) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, x); ps.setInt(2, z); ps.setString(3, dimension.name());
                ps.setInt(4, level); ps.setDouble(5, xp);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        });
    }

    public CompletableFuture<Boolean> deleteIsland(UUID ownerUuid, World.Environment dimension) {
        return supplyAsync(() -> {
            try {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                    ps.setString(1, ownerUuid.toString()); ps.setString(2, dimension.name()); ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM island_members WHERE grid_x IN (SELECT grid_x FROM islands WHERE owner_uuid = ? AND dimension = ?)")) {
                    ps.setString(1, ownerUuid.toString()); ps.setString(2, dimension.name()); ps.executeUpdate();
                }
                return true;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        });
    }

    // ====================== PLAYER BALANCE ======================
    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                getPlayerBalanceStmt.setString(1, uuid.toString());
                try (ResultSet rs = getPlayerBalanceStmt.executeQuery()) {
                    return rs.next() ? rs.getDouble("balance") : 0.0;
                }
            } catch (SQLException e) { e.printStackTrace(); return 0.0; }
        }, executor);
    }

    public CompletableFuture<Boolean> setPlayerBalance(UUID uuid, double balance) {
        return supplyAsync(() -> {
            try {
                setPlayerBalanceStmt.setString(1, uuid.toString());
                setPlayerBalanceStmt.setDouble(2, balance);
                return setPlayerBalanceStmt.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        });
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
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT balance FROM island_balances WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                ps.setInt(1, pos.x()); ps.setInt(2, pos.z()); ps.setString(3, "NORMAL");
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getDouble("balance") : 0.0; }
            } catch (SQLException e) { e.printStackTrace(); return 0.0; }
        }, executor);
    }

    public CompletableFuture<Boolean> setIslandBalance(GridPosition pos, double balance) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, pos.x()); ps.setInt(2, pos.z()); ps.setString(3, "NORMAL"); ps.setDouble(4, balance);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        });
    }

    public CompletableFuture<Boolean> addIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, current + amount));
    }

    public CompletableFuture<Boolean> removeIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, Math.max(0, current - amount)));
    }

    // ====================== RANK SYSTEM ======================
    public CompletableFuture<String> getCurrentRankId(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT rank_id FROM player_ranks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString("rank_id") : "member"; }
            } catch (SQLException e) { e.printStackTrace(); return "member"; }
        }, executor);
    }

    public CompletableFuture<Integer> getUpvoteCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT upvotes FROM player_ranks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt("upvotes") : 0; }
            } catch (SQLException e) { e.printStackTrace(); return 0; }
        }, executor);
    }

    public CompletableFuture<Boolean> setRank(UUID uuid, String rankId) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_ranks (uuid, rank_id) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString()); ps.setString(2, rankId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        });
    }

    public CompletableFuture<Boolean> voteForPlayer(UUID voter, UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR IGNORE INTO player_votes (voter_uuid, target_uuid) VALUES (?, ?)")) {
                    ps.setString(1, voter.toString()); ps.setString(2, target.toString()); ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE player_ranks SET upvotes = upvotes + 1 WHERE uuid = ?")) {
                    ps.setString(1, target.toString()); ps.executeUpdate();
                }
                return true;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }, executor);
    }

    // ====================== MUTE SYSTEM ======================
    public CompletableFuture<Set<UUID>> loadMutedPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> muted = new HashSet<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid FROM muted_players WHERE expires_at IS NULL OR expires_at > ?")) {
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
            try {
                if (muted) {
                    long expiresAt = durationSeconds > 0 ? System.currentTimeMillis() + (durationSeconds * 1000L) : 0;
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT OR REPLACE INTO muted_players (uuid, muted_at, expires_at, muted_by, reason) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setLong(2, expiresAt);
                        ps.setString(3, mutedBy != null ? mutedBy.toString() : "console");
                        ps.setString(4, reason != null ? reason : "");
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = connection.prepareStatement("DELETE FROM muted_players WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    }
                }
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }, executor);
    }

    // ====================== TOP QUERIES ======================
    public CompletableFuture<List<TopIslandEntry>> getTopIslands(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<TopIslandEntry> top = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                SELECT i.grid_x, i.grid_z, i.owner_uuid, i.biome_name, b.balance 
                FROM islands i JOIN island_balances b 
                ON i.grid_x = b.grid_x AND i.grid_z = b.grid_z AND i.dimension = b.dimension
                ORDER BY b.balance DESC LIMIT ?""")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        top.add(new TopIslandEntry(
                                new GridPosition(rs.getInt("grid_x"), rs.getInt("grid_z")),
                                UUID.fromString(rs.getString("owner_uuid")),
                                rs.getString("biome_name"),
                                rs.getDouble("balance")
                        ));
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return top;
        }, executor);
    }

    public CompletableFuture<List<TopBalanceEntry>> getTopBalances(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<TopBalanceEntry> top = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, balance FROM player_balances ORDER BY balance DESC LIMIT ?")) {
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

    public record TopIslandEntry(GridPosition pos, UUID ownerUuid, String biome, double balance) {}
    public record TopBalanceEntry(UUID uuid, double balance) {}

    public void close() {
        executor.shutdown();
        try {
            if (getPlayerBalanceStmt != null) getPlayerBalanceStmt.close();
            if (setPlayerBalanceStmt != null) setPlayerBalanceStmt.close();
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}