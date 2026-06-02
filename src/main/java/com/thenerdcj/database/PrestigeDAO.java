package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/**
 * Data Access Object for island prestige data.
 * Extracted as part of DatabaseManager modularization effort.
 */
public class PrestigeDAO extends BaseDAO {

    public PrestigeDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // Table creation is handled centrally in DatabaseManager for now.
        // In a future phase we can move schema ownership here.
    }

    public void saveIslandPrestige(String islandKey, int prestigeLevel) {
        // Fire-and-forget write (common for prestige updates on level up)
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_prestige (island_key, prestige_level, last_prestiged) VALUES (?, ?, ?)")) {

                        ps.setString(1, islandKey);
                        ps.setInt(2, prestigeLevel);
                        ps.setLong(3, System.currentTimeMillis());
                        ps.executeUpdate();

                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[PrestigeDAO] saveIslandPrestige failed for " + islandKey + ": " + e.getMessage());
            }
        });
    }

    /**
     * Async save returning future (for callers that want to await, e.g. tests or prestige-up flows).
     */
    public CompletableFuture<Boolean> saveIslandPrestigeAsync(String islandKey, int prestigeLevel) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_prestige (island_key, prestige_level, last_prestiged) VALUES (?, ?, ?)")) {
                        ps.setString(1, islandKey);
                        ps.setInt(2, prestigeLevel);
                        ps.setLong(3, System.currentTimeMillis());
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[PrestigeDAO] saveIslandPrestigeAsync failed for " + islandKey + ": " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Integer> loadIslandPrestige(String islandKey) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT prestige_level FROM island_prestige WHERE island_key = ?")) {

                        ps.setString(1, islandKey);
                        ResultSet rs = ps.executeQuery();

                        if (rs.next()) {
                            return rs.getInt("prestige_level");
                        }
                        return 0;

                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[PrestigeDAO] loadIslandPrestige failed for " + islandKey + ": " + e.getMessage());
                return 0;
            }
        });
    }
}