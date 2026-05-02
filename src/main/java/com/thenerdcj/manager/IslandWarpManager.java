package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.IslandWarp;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island Warp Manager - Folia-optimized warp system
 */
public class IslandWarpManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final Map<GridPosition, IslandWarp> warpCache = new ConcurrentHashMap<>();

    public IslandWarpManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        createWarpTable();
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupCache, 36000L, 36000L);
    }

    private void createWarpTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS island_warps (
                grid_x INTEGER,
                grid_z INTEGER,
                dimension TEXT,
                world TEXT,
                x REAL,
                y REAL,
                z REAL,
                yaw REAL,
                pitch REAL,
                enabled BOOLEAN DEFAULT 0,
                PRIMARY KEY (grid_x, grid_z, dimension)
            )
            """;
        databaseManager.executeUpdate(sql);
    }

    public CompletableFuture<IslandWarp> getWarp(GridPosition pos) {
        IslandWarp cached = warpCache.get(pos);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM island_warps WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());
                ResultSet rs = stmt.executeQuery();

                IslandWarp warp = new IslandWarp(pos);
                if (rs.next()) {
                    String worldName = rs.getString("world");
                    if (worldName != null && Bukkit.getWorld(worldName) != null) {
                        Location loc = new Location(
                                Bukkit.getWorld(worldName),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getFloat("yaw"),
                                rs.getFloat("pitch")
                        );
                        warp.setWarpLocation(loc);
                        warp.setEnabled(rs.getBoolean("enabled"));
                    }
                }

                warpCache.put(pos, warp);
                return warp;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load island warp: " + e.getMessage());
                return new IslandWarp(pos);
            }
        });
    }

    public CompletableFuture<Void> saveWarp(IslandWarp warp) {
        GridPosition pos = warp.getGridPosition();
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("""
                     INSERT OR REPLACE INTO island_warps 
                     (grid_x, grid_z, dimension, world, x, y, z, yaw, pitch, enabled)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());

                if (warp.getWarpLocation() != null) {
                    Location loc = warp.getWarpLocation();
                    stmt.setString(4, loc.getWorld().getName());
                    stmt.setDouble(5, loc.getX());
                    stmt.setDouble(6, loc.getY());
                    stmt.setDouble(7, loc.getZ());
                    stmt.setFloat(8, loc.getYaw());
                    stmt.setFloat(9, loc.getPitch());
                } else {
                    stmt.setNull(4, java.sql.Types.VARCHAR);
                    stmt.setNull(5, java.sql.Types.DOUBLE);
                    stmt.setNull(6, java.sql.Types.DOUBLE);
                    stmt.setNull(7, java.sql.Types.DOUBLE);
                    stmt.setNull(8, java.sql.Types.FLOAT);
                    stmt.setNull(9, java.sql.Types.FLOAT);
                }
                stmt.setBoolean(10, warp.isEnabled());
                stmt.executeUpdate();
                warpCache.put(pos, warp);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save island warp: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Boolean> setWarp(GridPosition pos, Location location) {
        return getWarp(pos).thenCompose(warp -> {
            warp.setWarpLocation(location);
            warp.setEnabled(true);
            return saveWarp(warp).thenApply(v -> true);
        });
    }

    public CompletableFuture<Boolean> removeWarp(GridPosition pos) {
        return getWarp(pos).thenCompose(warp -> {
            warp.setEnabled(false);
            return saveWarp(warp).thenApply(v -> true);
        });
    }

    public boolean hasWarp(GridPosition pos) {
        IslandWarp warp = warpCache.get(pos);
        return warp != null && warp.isEnabled();
    }

    private void cleanupCache() {
        if (warpCache.size() > 500) warpCache.clear();
    }
}
