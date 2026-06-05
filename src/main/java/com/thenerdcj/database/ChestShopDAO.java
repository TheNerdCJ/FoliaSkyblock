package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence for player chest shops (extracted from {@link com.thenerdcj.shop.ChestShopManager}).
 */
public class ChestShopDAO extends BaseDAO {

    public record ChestShopRow(
            String world,
            int x, int y, int z,
            UUID ownerUuid,
            String ownerName,
            String itemType,
            double buyPrice,
            double sellPrice,
            int stock) {}

    public ChestShopDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        ensureTable();
    }

    public void ensureTable() {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("""
                            CREATE TABLE IF NOT EXISTS chest_shops (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                world TEXT NOT NULL,
                                x INTEGER NOT NULL,
                                y INTEGER NOT NULL,
                                z INTEGER NOT NULL,
                                owner_uuid TEXT NOT NULL,
                                owner_name TEXT NOT NULL,
                                item_type TEXT NOT NULL,
                                buy_price REAL NOT NULL,
                                sell_price REAL NOT NULL,
                                stock INTEGER DEFAULT 0,
                                created_at INTEGER DEFAULT 0,
                                UNIQUE(world, x, y, z)
                            )
                            """)) {
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[ChestShopDAO] ensureTable failed: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<ChestShopRow>> loadAll() {
        return supplyAsync(() -> {
            List<ChestShopRow> rows = new ArrayList<>();
            withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM chest_shops");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new ChestShopRow(
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z"),
                                UUID.fromString(rs.getString("owner_uuid")),
                                rs.getString("owner_name"),
                                rs.getString("item_type"),
                                rs.getDouble("buy_price"),
                                rs.getDouble("sell_price"),
                                rs.getInt("stock")));
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            return rows;
        });
    }

    public CompletableFuture<Boolean> save(ChestShopRow row) {
        return supplyAsync(() -> {
            withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT OR REPLACE INTO chest_shops
                        (world, x, y, z, owner_uuid, owner_name, item_type, buy_price, sell_price, stock, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    ps.setString(1, row.world());
                    ps.setInt(2, row.x());
                    ps.setInt(3, row.y());
                    ps.setInt(4, row.z());
                    ps.setString(5, row.ownerUuid().toString());
                    ps.setString(6, row.ownerName());
                    ps.setString(7, row.itemType());
                    ps.setDouble(8, row.buyPrice());
                    ps.setDouble(9, row.sellPrice());
                    ps.setInt(10, row.stock());
                    ps.setLong(11, System.currentTimeMillis());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            return true;
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[ChestShopDAO] save failed: " + ex.getMessage());
            return false;
        });
    }

    public CompletableFuture<Boolean> delete(String world, int x, int y, int z) {
        return supplyAsync(() -> {
            withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM chest_shops WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                    ps.setString(1, world);
                    ps.setInt(2, x);
                    ps.setInt(3, y);
                    ps.setInt(4, z);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            return true;
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[ChestShopDAO] delete failed: " + ex.getMessage());
            return false;
        });
    }

    /**
     * Loads all shops in a chunk (block coords {@code chunkX/chunkZ} are chunk indices, not block coords).
     */
    public CompletableFuture<List<ChestShopRow>> loadByChunk(String world, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;
        return supplyAsync(() -> {
            List<ChestShopRow> rows = new ArrayList<>();
            withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM chest_shops WHERE world = ? AND x >= ? AND x <= ? AND z >= ? AND z <= ?")) {
                    ps.setString(1, world);
                    ps.setInt(2, minX);
                    ps.setInt(3, maxX);
                    ps.setInt(4, minZ);
                    ps.setInt(5, maxZ);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new ChestShopRow(
                                    rs.getString("world"),
                                    rs.getInt("x"),
                                    rs.getInt("y"),
                                    rs.getInt("z"),
                                    UUID.fromString(rs.getString("owner_uuid")),
                                    rs.getString("owner_name"),
                                    rs.getString("item_type"),
                                    rs.getDouble("buy_price"),
                                    rs.getDouble("sell_price"),
                                    rs.getInt("stock")));
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            return rows;
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[ChestShopDAO] loadByChunk failed: " + ex.getMessage());
            return List.of();
        });
    }

    public CompletableFuture<Optional<ChestShopRow>> loadAt(String world, int x, int y, int z) {
        return supplyAsync(() -> {
            Optional<ChestShopRow> row = withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM chest_shops WHERE world = ? AND x = ? AND y = ? AND z = ? LIMIT 1")) {
                    ps.setString(1, world);
                    ps.setInt(2, x);
                    ps.setInt(3, y);
                    ps.setInt(4, z);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.<ChestShopRow>empty();
                        }
                        return Optional.of(new ChestShopRow(
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z"),
                                UUID.fromString(rs.getString("owner_uuid")),
                                rs.getString("owner_name"),
                                rs.getString("item_type"),
                                rs.getDouble("buy_price"),
                                rs.getDouble("sell_price"),
                                rs.getInt("stock")));
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            return row != null ? row : Optional.<ChestShopRow>empty();
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[ChestShopDAO] loadAt failed: " + ex.getMessage());
            return Optional.<ChestShopRow>empty();
        });
    }

    /**
     * Single-transaction batch upsert for coalesced shop saves.
     */
    public CompletableFuture<Boolean> flushBatch(Collection<ChestShopRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        return supplyAsync(() -> {
            withConnection(conn -> {
                try {
                    boolean prevAutoCommit = conn.getAutoCommit();
                    conn.setAutoCommit(false);
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT OR REPLACE INTO chest_shops
                            (world, x, y, z, owner_uuid, owner_name, item_type, buy_price, sell_price, stock, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)) {
                        for (ChestShopRow row : rows) {
                            ps.setString(1, row.world());
                            ps.setInt(2, row.x());
                            ps.setInt(3, row.y());
                            ps.setInt(4, row.z());
                            ps.setString(5, row.ownerUuid().toString());
                            ps.setString(6, row.ownerName());
                            ps.setString(7, row.itemType());
                            ps.setDouble(8, row.buyPrice());
                            ps.setDouble(9, row.sellPrice());
                            ps.setInt(10, row.stock());
                            ps.setLong(11, System.currentTimeMillis());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                        conn.commit();
                    } catch (SQLException e) {
                        try {
                            conn.rollback();
                        } catch (SQLException rollbackEx) {
                            e.addSuppressed(rollbackEx);
                        }
                        throw e;
                    } finally {
                        conn.setAutoCommit(prevAutoCommit);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            return true;
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[ChestShopDAO] flushBatch failed: " + ex.getMessage());
            return false;
        });
    }

    public CompletableFuture<Integer> countAll() {
        return supplyAsync(() -> withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM chest_shops");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        })).exceptionally(ex -> 0);
    }
}