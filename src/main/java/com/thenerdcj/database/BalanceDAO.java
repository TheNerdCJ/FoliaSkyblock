package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Data Access Object for player and island balances (dual economy).
 * Extracted as part of continued DatabaseManager modularization.
 * 
 * Player balances: Chest shops / personal economy.
 * Island balances: Upgrades, banks (some overlap with island_banks table).
 * 
 * Caching and dirty flushing remain in DatabaseManager for hot paths.
 * Follows BaseDAO + DBOperations bridge pattern.
 * Schema creation stays centralized.
 */
public class BalanceDAO extends BaseDAO {

    public BalanceDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // Tables (player_balances, island_balances) created centrally in DatabaseManager.
    }

    // ==================== PLAYER BALANCES ====================

    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT balance FROM player_balances WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? rs.getDouble("balance") : 0.0;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BalanceDAO] getPlayerBalance failed: " + e.getMessage());
                return 0.0;
            }
        });
    }

    public CompletableFuture<Boolean> setPlayerBalance(UUID uuid, double balance) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO player_balances (uuid, balance) VALUES (?, ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setDouble(2, Math.max(0, balance));
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BalanceDAO] setPlayerBalance failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return getPlayerBalance(uuid).thenCompose(current ->
                setPlayerBalance(uuid, current + amount));
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return addPlayerBalance(uuid, -amount);
    }

    // ==================== ISLAND BALANCES ====================

    public CompletableFuture<Double> getIslandBalance(GridPosition pos) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT balance FROM island_balances WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? rs.getDouble("balance") : 0.0;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BalanceDAO] getIslandBalance failed: " + e.getMessage());
                return 0.0;
            }
        });
    }

    public CompletableFuture<Boolean> setIslandBalance(GridPosition pos, double balance) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        ps.setDouble(4, balance);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BalanceDAO] setIslandBalance failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> addIslandBalance(GridPosition pos, double amount) {
        return getIslandBalance(pos).thenCompose(current -> setIslandBalance(pos, current + amount));
    }

    public CompletableFuture<Boolean> removeIslandBalance(GridPosition pos, double amount) {
        return addIslandBalance(pos, -amount);
    }

    /**
     * Flush a specific dirty island balance (for use from DM flushCaches).
     */
    public boolean flushIslandBalance(GridPosition pos, double balance) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR REPLACE INTO island_balances (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, pos.x());
                    ps.setInt(2, pos.z());
                    ps.setString(3, pos.getDimension().name());
                    ps.setDouble(4, balance);
                    ps.executeUpdate();
                    return true;
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[BalanceDAO] flushIslandBalance failed: " + e.getMessage());
            return false;
        }
    }
}