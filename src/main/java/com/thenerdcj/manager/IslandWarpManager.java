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
        // Table creation is now centralized in DatabaseManager.createTables()
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCache, 36000L, 36000L);
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

                if (rs.next()) {
                    IslandWarp warp = new IslandWarp(pos);
                    org.bukkit.World world = Bukkit.getWorld(rs.getString("world"));
                    if (world != null) {
                        Location loc = new Location(
                                world,
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                (float) rs.getDouble("yaw"),
                                (float) rs.getDouble("pitch")
                        );
                        warp.setWarpLocation(loc);
                        warp.setEnabled(rs.getBoolean("enabled"));
                    }
                    warpCache.put(pos, warp);
                    return warp;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load island warp: " + e.getMessage());
            }
            return new IslandWarp(pos);
        });
    }

    public CompletableFuture<Void> saveWarp(IslandWarp warp) {
        GridPosition pos = warp.getGridPosition();
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_warps (grid_x, grid_z, dimension, world, x, y, z, yaw, pitch, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());
                stmt.setString(4, warp.getWarpLocation() != null ? warp.getWarpLocation().getWorld().getName() : "world");
                stmt.setDouble(5, warp.getWarpLocation() != null ? warp.getWarpLocation().getX() : 0);
                stmt.setDouble(6, warp.getWarpLocation() != null ? warp.getWarpLocation().getY() : 64);
                stmt.setDouble(7, warp.getWarpLocation() != null ? warp.getWarpLocation().getZ() : 0);
                stmt.setDouble(8, warp.getWarpLocation() != null ? warp.getWarpLocation().getYaw() : 0);
                stmt.setDouble(9, warp.getWarpLocation() != null ? warp.getWarpLocation().getPitch() : 0);
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

    /**
     * Get all public warps for the browse GUI
     */
    public CompletableFuture<Map<GridPosition, IslandWarp>> getAllPublicWarps() {
        return CompletableFuture.supplyAsync(() -> {
            Map<GridPosition, IslandWarp> publicWarps = new java.util.concurrent.ConcurrentHashMap<>();
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM island_warps WHERE enabled = 1")) {
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    GridPosition pos = new GridPosition(
                            rs.getInt("grid_x"),
                            rs.getInt("grid_z"),
                            org.bukkit.World.Environment.valueOf(rs.getString("dimension"))
                    );

                    IslandWarp warp = new IslandWarp(pos);
                    org.bukkit.World world = Bukkit.getWorld(rs.getString("world"));
                    if (world != null) {
                        org.bukkit.Location loc = new org.bukkit.Location(
                                world,
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                (float) rs.getDouble("yaw"),
                                (float) rs.getDouble("pitch")
                        );
                        warp.setWarpLocation(loc);
                        warp.setEnabled(rs.getBoolean("enabled"));
                        publicWarps.put(pos, warp);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load all public warps: " + e.getMessage());
            }
            return publicWarps;
        });
    }

    private void cleanupCache() {
        if (warpCache.size() > 500) warpCache.clear();
    }
}