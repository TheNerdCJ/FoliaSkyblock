package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island Rating Manager - Folia-optimized rating system
 */
public class IslandRatingManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final Map<GridPosition, Map<UUID, Integer>> ratingCache = new ConcurrentHashMap<>();

    public IslandRatingManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        createRatingTable();
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupCache, 36000L, 36000L);
    }

    private void createRatingTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS island_ratings (
                grid_x INTEGER,
                grid_z INTEGER,
                dimension TEXT,
                player_uuid TEXT,
                rating INTEGER,
                timestamp INTEGER,
                PRIMARY KEY (grid_x, grid_z, dimension, player_uuid)
            )
            """;
        databaseManager.executeUpdate(sql);
    }

    public CompletableFuture<Void> rateIsland(GridPosition pos, UUID playerUuid, int rating) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_ratings (grid_x, grid_z, dimension, player_uuid, rating, timestamp) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());
                stmt.setString(4, playerUuid.toString());
                stmt.setInt(5, Math.max(1, Math.min(5, rating)));
                stmt.setLong(6, System.currentTimeMillis());
                stmt.executeUpdate();

                // Update cache
                ratingCache.computeIfAbsent(pos, k -> new ConcurrentHashMap<>())
                        .put(playerUuid, rating);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save island rating: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Double> getAverageRating(GridPosition pos) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT AVG(rating) as avg_rating FROM island_ratings WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getDouble("avg_rating");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load island rating: " + e.getMessage());
            }
            return 0.0;
        });
    }

    public CompletableFuture<Integer> getRatingCount(GridPosition pos) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) as count FROM island_ratings WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getInt("count");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to count island ratings: " + e.getMessage());
            }
            return 0;
        });
    }

    public CompletableFuture<Map<GridPosition, Double>> getTopRatedIslands(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<GridPosition, Double> topRated = new LinkedHashMap<>();
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT grid_x, grid_z, dimension, AVG(rating) as avg_rating, COUNT(*) as count " +
                                 "FROM island_ratings " +
                                 "GROUP BY grid_x, grid_z, dimension " +
                                 "HAVING count >= 1 " +
                                 "ORDER BY avg_rating DESC, count DESC " +
                                 "LIMIT ?")) {
                stmt.setInt(1, limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    GridPosition pos = new GridPosition(
                            rs.getInt("grid_x"),
                            rs.getInt("grid_z"),
                            org.bukkit.World.Environment.valueOf(rs.getString("dimension"))
                    );
                    topRated.put(pos, rs.getDouble("avg_rating"));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load top rated islands: " + e.getMessage());
            }
            return topRated;
        });
    }

    public CompletableFuture<Integer> getPlayerRating(GridPosition pos, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Integer> posRatings = ratingCache.get(pos);
            if (posRatings != null && posRatings.containsKey(playerUuid)) {
                return posRatings.get(playerUuid);
            }

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT rating FROM island_ratings WHERE grid_x = ? AND grid_z = ? AND dimension = ? AND player_uuid = ?")) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());
                stmt.setString(4, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int rating = rs.getInt("rating");
                    ratingCache.computeIfAbsent(pos, k -> new ConcurrentHashMap<>()).put(playerUuid, rating);
                    return rating;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load player rating: " + e.getMessage());
            }
            return 0;
        });
    }

    private void cleanupCache() {
        if (ratingCache.size() > 1000) ratingCache.clear();
    }
}
