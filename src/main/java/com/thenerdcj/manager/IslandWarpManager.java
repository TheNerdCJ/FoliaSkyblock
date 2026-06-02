package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.database.IslandDAO;
import com.thenerdcj.island.IslandWarp;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island Warp Manager - Folia-optimized warp system
 */
public class IslandWarpManager {

    private final FoliaSkyblock plugin;
    // Real LRU for large scale (access-order LinkedHashMap like worthCache for compression)
    private final Map<GridPosition, IslandWarp> warpCache = Collections.synchronizedMap(new LinkedHashMap<GridPosition, IslandWarp>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GridPosition, IslandWarp> eldest) {
            return size() > 500;
        }
    });

    public IslandWarpManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Table creation centralized; persistence now delegated to IslandDAO (withConnection, grid PK)
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCache, 36000L, 36000L);
    }

    public CompletableFuture<IslandWarp> getWarp(GridPosition pos) {
        IslandWarp cached = warpCache.get(pos);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        // Delegate to IslandDAO (withConnection promoted)
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        return dao.loadIslandWarp(pos).thenApply(w -> {
            warpCache.put(pos, w);
            return w;
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load island warp via DAO: " + ex.getMessage());
            return new IslandWarp(pos);
        });
    }

    public CompletableFuture<Void> saveWarp(IslandWarp warp) {
        GridPosition pos = warp.getGridPosition();
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        dao.saveIslandWarp(warp);
        warpCache.put(pos, warp);
        return CompletableFuture.completedFuture(null);
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
     * Get all public warps for the browse GUI (no limit).
     * Delegates to IslandDAO (now supports limit for large scale compression).
     */
    public CompletableFuture<Map<GridPosition, IslandWarp>> getAllPublicWarps() {
        return getAllPublicWarps(0);
    }

    /**
     * Get public warps with optional limit for DB/memory compression on large servers (100s-1000+ public warps possible).
     * limit <=0 means all (compat). See DAO + IMPROVEMENTS compression suggestions (paged data, bounded globals).
     */
    public CompletableFuture<Map<GridPosition, IslandWarp>> getAllPublicWarps(int limit) {
        long start = System.nanoTime();
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        return dao.loadAllPublicWarps(limit).thenApply(map -> {
            warpCache.putAll(map);
            if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) {
                long ns = System.nanoTime() - start;
                if (ns > 1_000_000L) plugin.getLogger().info("[IslandWarpManager] PROFILE: getAllPublicWarps took " + (ns / 1_000_000.0) + " ms (large scale global query, limit=" + limit + ")");
            }
            return map;
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load all public warps via DAO: " + ex.getMessage());
            return new java.util.concurrent.ConcurrentHashMap<>();
        });
    }

    private void cleanupCache() {
        // Real LRU now via LinkedHashMap access-order removeEldest (auto evicts when >500); no manual needed.
    }
}