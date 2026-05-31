package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.IslandSettings;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandSettingsManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final Map<GridPosition, IslandSettings> settingsCache = new ConcurrentHashMap<>();

    public IslandSettingsManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        // Table creation is now centralized in DatabaseManager.createTables()
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCache, 36000L, 36000L);
    }

    /**
     * Non-blocking cached lookup. Returns cached value or a safe default (never joins).
     * Use this from event handlers, particle tasks, and other hot paths.
     * Schedules an async load/refresh in the background if not cached.
     */
    public IslandSettings getCachedSettings(GridPosition pos) {
        IslandSettings cached = settingsCache.get(pos);
        if (cached != null) {
            return cached;
        }
        // Schedule async load (fire and forget) so next call will hit cache
        getSettings(pos); // this populates the cache asynchronously
        return new IslandSettings(pos); // safe default (all false / blue / size 0 etc.)
    }

    public CompletableFuture<IslandSettings> getSettings(GridPosition pos) {
        IslandSettings cached = settingsCache.get(pos);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM island_settings WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    IslandSettings settings = new IslandSettings(pos);
                    settings.setPvpEnabled(rs.getBoolean("pvp_enabled"));
                    settings.setVisitorsAllowed(rs.getBoolean("visitors_allowed"));
                    settings.setExplosionsEnabled(rs.getBoolean("explosions_enabled"));
                    settings.setFireSpreadEnabled(rs.getBoolean("fire_spread_enabled"));
                    settings.setMobSpawningEnabled(rs.getBoolean("mob_spawning_enabled"));
                    settings.setCropTramplingEnabled(rs.getBoolean("crop_trampling_enabled"));
                    settings.setAnimalSpawningEnabled(rs.getBoolean("animal_spawning_enabled"));
                    settings.setLeafDecayEnabled(rs.getBoolean("leaf_decay_enabled"));
                    settings.setBorderColor(rs.getString("border_color"));
                    settings.setBorderSize(rs.getInt("border_size"));
                    settings.setBorderMarkersEnabled(rs.getBoolean("border_markers_enabled"));
                    settings.setWarpEnabled(rs.getBoolean("warp_enabled"));
                    settings.setWarpDescription(rs.getString("warp_description"));
                    settingsCache.put(pos, settings);
                    return settings;
                } else {
                    IslandSettings newSettings = new IslandSettings(pos);
                    settingsCache.put(pos, newSettings);
                    saveSettings(newSettings);
                    return newSettings;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load island settings: " + e.getMessage());
                return new IslandSettings(pos);
            }
        });
    }

    public CompletableFuture<Void> saveSettings(IslandSettings settings) {
        GridPosition pos = settings.getGridPosition();
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("""
                     INSERT OR REPLACE INTO island_settings 
                     (grid_x, grid_z, dimension, pvp_enabled, visitors_allowed, explosions_enabled, 
                      fire_spread_enabled, mob_spawning_enabled, crop_trampling_enabled, animal_spawning_enabled,
                      leaf_decay_enabled, border_color, border_size, border_markers_enabled, warp_enabled, warp_description)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());
                stmt.setBoolean(4, settings.isPvpEnabled());
                stmt.setBoolean(5, settings.isVisitorsAllowed());
                stmt.setBoolean(6, settings.isExplosionsEnabled());
                stmt.setBoolean(7, settings.isFireSpreadEnabled());
                stmt.setBoolean(8, settings.isMobSpawningEnabled());
                stmt.setBoolean(9, settings.isCropTramplingEnabled());
                stmt.setBoolean(10, settings.isAnimalSpawningEnabled());
                stmt.setBoolean(11, settings.isLeafDecayEnabled());
                stmt.setString(12, settings.getBorderColor());
                stmt.setInt(13, settings.getBorderSize());
                stmt.setBoolean(14, settings.isBorderMarkersEnabled());
                stmt.setBoolean(15, settings.isWarpEnabled());
                stmt.setString(16, settings.getWarpDescription());
                stmt.executeUpdate();
                settingsCache.put(pos, settings);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save island settings: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Boolean> toggleSetting(GridPosition pos, String settingName) {
        return getSettings(pos).thenCompose(settings -> {
            boolean newValue = settings.toggleSetting(settingName);
            return saveSettings(settings).thenApply(v -> newValue);
        });
    }

    public boolean isSettingEnabled(GridPosition pos, String settingName) {
        IslandSettings settings = settingsCache.get(pos);
        if (settings != null) return settings.getSetting(settingName);
        return switch (settingName.toUpperCase()) {
            case "PVP", "EXPLOSIONS", "FIRE", "WARP" -> false;
            default -> true;
        };
    }

    private void cleanupCache() {
        if (settingsCache.size() > 1000) settingsCache.clear();
    }
}