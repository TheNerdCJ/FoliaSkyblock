package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/**
 * DAO for island fuel persistence (minions).
 * Full extraction from inline code in DatabaseManager (task batch).
 * 
 * Table: island_fuel (island_key, fuel_amount)
 * Used by MinionManager via DatabaseManager delegation for compat.
 * Folia async via DBOperations.
 * Play-to-Win: fuel earned/spent from play (minion production, feed from inventory drops/trade).
 */
public class IslandFuelDAO extends BaseDAO {

    public IslandFuelDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // Table created centrally in DatabaseManager.createTables()
    }

    public CompletableFuture<Boolean> saveIslandFuel(String islandKey, int fuelAmount) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_fuel (island_key, fuel_amount) VALUES (?, ?)")) {
                        ps.setString(1, islandKey);
                        ps.setInt(2, fuelAmount);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[IslandFuelDAO] saveIslandFuel failed for " + islandKey + ": " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Integer> loadIslandFuel(String islandKey) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT fuel_amount FROM island_fuel WHERE island_key = ?")) {
                        ps.setString(1, islandKey);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            return rs.getInt("fuel_amount");
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return 1000; // default
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[IslandFuelDAO] loadIslandFuel failed for " + islandKey + ": " + e.getMessage());
                return 1000;
            }
        });
    }
}