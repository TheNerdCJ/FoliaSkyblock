package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/**
 * DAO for island XP/level (separate from worth level).
 * Extracted for task 3 (was inline in DatabaseManager for island_levels table).
 * 
 * island_key = owner_dim usually.
 * Used by IslandManager, IslandXPManager for persistence of progression.
 * Folia async.
 */
public class IslandLevelDAO extends BaseDAO {

    public IslandLevelDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // Table in central createTables + migration.
    }

    public CompletableFuture<Object[]> loadIslandLevel(GridPosition pos) {
        return supplyAsync(() -> {
            String key = pos.x() + "_" + pos.z() + "_" + (pos.getDimension() != null ? pos.getDimension().name() : "NORMAL");
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT xp, level FROM island_levels WHERE island_key = ?")) {
                        ps.setString(1, key);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            return new Object[]{rs.getDouble("xp"), rs.getInt("level")};
                        }
                        return new Object[]{0.0, 1};
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandLevelDAO] loadIslandLevel failed: " + e.getMessage());
                return new Object[]{0.0, 1};
            }
        });
    }

    public CompletableFuture<Boolean> saveIslandLevel(GridPosition pos, double xp, int level) {
        return supplyAsync(() -> {
            String key = pos.x() + "_" + pos.z() + "_" + (pos.getDimension() != null ? pos.getDimension().name() : "NORMAL");
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_levels (island_key, xp, level) VALUES (?, ?, ?)")) {
                        ps.setString(1, key);
                        ps.setDouble(2, xp);
                        ps.setInt(3, level);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandLevelDAO] saveIslandLevel failed: " + e.getMessage());
                return false;
            }
        });
    }
}