package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.IslandRank;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.bukkit.World;

public class DatabaseManager {

    private final FoliaSkyblock plugin;
    private Connection connection;

    public DatabaseManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        initDatabase();
    }

    private void initDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + 
                    new File(dataFolder, "skyblock.db").getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
            }

            createTables();
            createIndexes();

            plugin.getLogger().info("§aDatabase initialized successfully!");

        } catch (Exception e) {
            plugin.getLogger().severe("§cDatabase initialization failed!");
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS islands (
                    grid_x INTEGER NOT NULL,
                    grid_z INTEGER NOT NULL,
                    owner_uuid TEXT NOT NULL,
                    biome_name TEXT NOT NULL,
                    dimension TEXT NOT NULL DEFAULT 'NORMAL',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (grid_x, grid_z, dimension)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS island_members (
                    grid_x INTEGER NOT NULL,
                    grid_z INTEGER NOT NULL,
                    dimension TEXT NOT NULL DEFAULT 'NORMAL',
                    player_uuid TEXT NOT NULL,
                    island_rank TEXT NOT NULL DEFAULT 'GUEST',
                    PRIMARY KEY (grid_x, grid_z, dimension, player_uuid)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_ranks (
                    uuid TEXT PRIMARY KEY,
                    rank_id TEXT NOT NULL DEFAULT 'member',
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_votes (
                    voter_uuid TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    voted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (voter_uuid, target_uuid)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS island_levels (
                    grid_x INTEGER NOT NULL,
                    grid_z INTEGER NOT NULL,
                    dimension TEXT NOT NULL DEFAULT 'NORMAL',
                    level INTEGER NOT NULL DEFAULT 1,
                    xp REAL NOT NULL DEFAULT 0.0,
                    PRIMARY KEY (grid_x, grid_z, dimension)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS island_balances (
                    grid_x INTEGER NOT NULL,
                    grid_z INTEGER NOT NULL,
                    dimension TEXT NOT NULL DEFAULT 'NORMAL',
                    balance REAL NOT NULL DEFAULT 1500.0,
                    PRIMARY KEY (grid_x, grid_z, dimension)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_balances (
                    uuid TEXT PRIMARY KEY,
                    balance REAL NOT NULL DEFAULT 0.0
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS muted_players (
                    uuid TEXT PRIMARY KEY,
                    muted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP,
                    muted_by TEXT,
                    reason TEXT
                )
            """);
        }
    }

    private void createIndexes() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_islands_owner ON islands(owner_uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_votes_target ON player_votes(target_uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_island_levels ON island_levels(grid_x, grid_z, dimension);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_muted_players ON muted_players(uuid);");
        }
    }

    // ====================== MUTE SYSTEM (for ChatManager) ======================
    public CompletableFuture<Boolean> setMuted(UUID uuid, boolean muted, UUID mutedBy, String reason, long durationSeconds) {
        return supplyAsync(() -> {
            try {
                if (muted) {
                    long expiresAt = durationSeconds > 0 ? System.currentTimeMillis() + (durationSeconds * 1000L) : 0;
                    try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT OR REPLACE INTO muted_players (uuid, muted_at, expires_at, muted_by, reason)
                        VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)""")) {
                        ps.setString(1, uuid.toString());
                        ps.setLong(2, expiresAt);
                        ps.setString(3, mutedBy != null ? mutedBy.toString() : "console");
                        ps.setString(4, reason != null ? reason : "");
                        return ps.executeUpdate() > 0;
                    }
                } else {
                    try (PreparedStatement ps = connection.prepareStatement("DELETE FROM muted_players WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        return ps.executeUpdate() > 0;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Set<UUID>> loadMutedPlayers() {
        return supplyAsync(() -> {
            Set<UUID> muted = new HashSet<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid FROM muted_players WHERE expires_at = 0 OR expires_at > ?")) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        muted.add(UUID.fromString(rs.getString("uuid")));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return muted;
        });
    }

    // ====================== ISLAND CREATION ======================
    public CompletableFuture<Boolean> createIsland(UUID owner, int x, int z, World.Environment dimension) {
        return saveIsland(x, z, owner, "PLAINS", dimension);
    }

    public CompletableFuture<Boolean> saveIsland(int x, int z, UUID owner, String biome, World.Environment dimension) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO islands (grid_x, grid_z, owner_uuid, biome_name, dimension) 
                VALUES (?, ?, ?, ?, ?)""")) {
                ps.setInt(1, x);
                ps.setInt(2, z);
                ps.setString(3, owner.toString());
                ps.setString(4, biome);
                ps.setString(5, dimension.name());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> saveMember(int x, int z, UUID playerUuid, IslandRank rank, World.Environment dimension) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO island_members (grid_x, grid_z, dimension, player_uuid, island_rank) 
                VALUES (?, ?, ?, ?, ?)""")) {
                ps.setInt(1, x);
                ps.setInt(2, z);
                ps.setString(3, dimension.name());
                ps.setString(4, playerUuid.toString());
                ps.setString(5, rank.name());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    // ====================== ISLAND LEVELING ======================
    public CompletableFuture<Boolean> saveIslandLevel(int x, int z, int level, double xp, World.Environment dimension) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO island_levels (grid_x, grid_z, dimension, level, xp)
                VALUES (?, ?, ?, ?, ?)""")) {
                ps.setInt(1, x);
                ps.setInt(2, z);
                ps.setString(3, dimension.name());
                ps.setInt(4, level);
                ps.setDouble(5, xp);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    // ====================== ISLAND DELETION ======================
    public CompletableFuture<Boolean> deleteIsland(UUID ownerUuid, World.Environment dimension) {
        return supplyAsync(() -> {
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                    ps.setString(1, ownerUuid.toString());
                    ps.setString(2, dimension.name());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM island_members WHERE grid_x IN (SELECT grid_x FROM islands WHERE owner_uuid = ? AND dimension = ?) AND dimension = ?")) {
                    ps.setString(1, ownerUuid.toString());
                    ps.setString(2, dimension.name());
                    ps.setString(3, dimension.name());
                    ps.executeUpdate();
                }
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    // ====================== ISLAND QUERIES ======================
    public CompletableFuture<Boolean> hasIslandInDimension(UUID uuid, World.Environment dimension) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM islands WHERE owner_uuid = ? AND dimension = ? LIMIT 1")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, dimension.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<GridPosition> getIslandPosition(UUID uuid, World.Environment dimension) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT grid_x, grid_z FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, dimension.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new GridPosition(rs.getInt("grid_x"), rs.getInt("grid_z"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public CompletableFuture<Set<GridPosition>> getAllOccupiedPositionsInDimension(World.Environment dimension) {
        return supplyAsync(() -> {
            Set<GridPosition> occupied = new HashSet<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT grid_x, grid_z FROM islands WHERE dimension = ?")) {
                ps.setString(1, dimension.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        occupied.add(new GridPosition(rs.getInt("grid_x"), rs.getInt("grid_z")));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return occupied;
        });
    }

    // ====================== ECONOMY - PLAYER ======================
    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT balance FROM player_balances WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble("balance") : 0.0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return 0.0;
            }
        });
    }

    public CompletableFuture<Boolean> setPlayerBalance(UUID uuid, double amount) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_balances (uuid, balance) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return getPlayerBalance(uuid).thenCompose(current -> setPlayerBalance(uuid, current + amount));
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return getPlayerBalance(uuid).thenCompose(current -> setPlayerBalance(uuid, current - amount));
    }

    // ====================== ECONOMY - ISLAND ======================
    public CompletableFuture<Double> getIslandBalance(GridPosition pos) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT balance FROM island_balances WHERE grid_x = ? AND grid_z = ?")) {
                ps.setInt(1, pos.x());
                ps.setInt(2, pos.z());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble("balance") : 1500.0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return 1500.0;
            }
        });
    }

    public CompletableFuture<Boolean> setIslandBalance(GridPosition pos, double amount) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO island_balances (grid_x, grid_z, balance) VALUES (?, ?, ?)")) {
                ps.setInt(1, pos.x());
                ps.setInt(2, pos.z());
                ps.setDouble(3, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> addIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, current + amount));
    }

    public CompletableFuture<Boolean> removeIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, current - amount));
    }

    // ====================== RANK & VOTING ======================
    public CompletableFuture<String> getCurrentRankId(UUID uuid) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT rank_id FROM player_ranks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("rank_id").toLowerCase();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return "member";
        });
    }

    public CompletableFuture<Boolean> setRank(UUID uuid, String rankId) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_ranks (uuid, rank_id) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankId.toLowerCase());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> voteForPlayer(UUID voterUuid, UUID targetUuid) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_votes (voter_uuid, target_uuid, voted_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                ps.setString(1, voterUuid.toString());
                ps.setString(2, targetUuid.toString());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Integer> getUpvoteCount(UUID targetUuid) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM player_votes WHERE target_uuid = ? AND voted_at > datetime('now', '-90 days')")) {
                ps.setString(1, targetUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return 0;
            }
        });
    }

    // ====================== TOP LISTS ======================
    public CompletableFuture<List<TopBalanceEntry>> getTopBalances(int limit) {
        return supplyAsync(() -> {
            List<TopBalanceEntry> top = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, balance FROM player_balances ORDER BY balance DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        top.add(new TopBalanceEntry(UUID.fromString(rs.getString("uuid")), rs.getDouble("balance")));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return top;
        });
    }

    public CompletableFuture<List<TopIslandEntry>> getTopIslands(int limit) {
        return supplyAsync(() -> {
            List<TopIslandEntry> top = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                SELECT i.grid_x, i.grid_z, i.owner_uuid, i.biome_name, b.balance
                FROM islands i
                JOIN island_balances b ON i.grid_x = b.grid_x AND i.grid_z = b.grid_z
                ORDER BY b.balance DESC LIMIT ?
                """)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        GridPosition pos = new GridPosition(rs.getInt("grid_x"), rs.getInt("grid_z"));
                        top.add(new TopIslandEntry(pos, UUID.fromString(rs.getString("owner_uuid")), 
                                rs.getString("biome_name"), rs.getDouble("balance")));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return top;
        });
    }

    public record TopBalanceEntry(UUID uuid, double balance) {}
    public record TopIslandEntry(GridPosition pos, UUID ownerUuid, String biome, double balance) {}

    // ====================== HELPER ======================
    private <T> CompletableFuture<T> supplyAsync(SupplierWithException<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}