package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.database.IslandDAO;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island Rating Manager - Folia-optimized rating system
 * Event sink for ratings (topsDirty on rate) supports full event-driven compression of periodic global top work for large servers.
 */
public class IslandRatingManager {

    private final FoliaSkyblock plugin;
    // Real LRU for large scale (access-order LinkedHashMap like worthCache for compression; nested for per-island ratings)
    private final Map<GridPosition, Map<UUID, Integer>> ratingCache = Collections.synchronizedMap(new LinkedHashMap<GridPosition, Map<UUID, Integer>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GridPosition, Map<UUID, Integer>> eldest) {
            return size() > 1000;
        }
    });
    // Event sink flag for ratings changes (enables event-driven invalidation of global tops/leaderboards instead of periodic only)
    // Per "event sinks for ratings", "full event-driven to compress periodic work" optimization suggestions for large scale.
    private volatile boolean topsDirty = true;

    public IslandRatingManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Persistence delegated to IslandDAO (withConnection + grid PK). Cache kept for hot reads.
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCache, 36000L, 36000L);
    }

    public CompletableFuture<Void> rateIsland(GridPosition pos, UUID playerUuid, int rating) {
        long start = System.nanoTime();
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        dao.rateIsland(pos, playerUuid, rating);
        // update cache
        ratingCache.computeIfAbsent(pos, k -> new ConcurrentHashMap<>())
                .put(playerUuid, rating);
        topsDirty = true; // event sink: ratings mutation marks global tops dirty for event-driven refresh (compress periodic work)
        long ns = System.nanoTime() - start;
        if (ns > 500_000L) { // simple hot path timing (more profiling per suggestions)
            plugin.getLogger().info("[IslandRating] PROFILE: rateIsland took " + (ns / 1_000_000.0) + " ms");
        }
        return CompletableFuture.completedFuture(null);
    }

    /** Returns whether global tops should be considered dirty (event sink for ratings). Callers (leaderboards) can reset after refresh. */
    public boolean isTopsDirty() { return topsDirty; }
    public void clearTopsDirty() { topsDirty = false; }

    public CompletableFuture<Double> getAverageRating(GridPosition pos) {
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        return dao.getAverageRating(pos);
    }

    public CompletableFuture<Integer> getRatingCount(GridPosition pos) {
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        return dao.getRatingCount(pos);
    }

    public CompletableFuture<Map<GridPosition, Double>> getTopRatedIslands(int limit) {
        return getTopRatedIslands(limit, 0);
    }

    /**
     * DB-paginated top rated (offset support via DAO). Enables fetching only needed page for
     * large scale leaderboards (1000+ islands) without full in-mem load. Event sink (topsDirty)
     * + Region stagger notes apply. Gated profiling.
     */
    public CompletableFuture<Map<GridPosition, Double>> getTopRatedIslands(int limit, int offset) {
        final boolean doProfile = (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths());
        final long start = doProfile ? System.nanoTime() : 0L;
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        return dao.getTopRatedIslands(limit, offset).thenApply(top -> {
            if (start != 0) {
                long ns = System.nanoTime() - start;
                if (ns > 1_000_000L) plugin.getLogger().info("[IslandRatingManager] PROFILE: getTopRatedIslands took " + (ns / 1_000_000.0) + " ms (large scale global, limit=" + limit + ", offset=" + offset + ")");
            }
            return top;
        });
    }

    public CompletableFuture<Integer> getPlayerRating(GridPosition pos, UUID playerUuid) {
        Map<UUID, Integer> posRatings = ratingCache.get(pos);
        if (posRatings != null && posRatings.containsKey(playerUuid)) {
            return CompletableFuture.completedFuture(posRatings.get(playerUuid));
        }
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        return dao.getPlayerRating(pos, playerUuid).thenApply(r -> {
            if (r > 0) {
                ratingCache.computeIfAbsent(pos, k -> new ConcurrentHashMap<>()).put(playerUuid, r);
            }
            return r;
        });
    }

    private void cleanupCache() {
        // Real LRU now via LinkedHashMap access-order removeEldest (auto evicts eldest on access when >1000); nested inners for per-island.
        // For very large, inners could be bounded too, but outer suffices for scale.
        if (ratingCache.size() > 1000) {
            // trim any excess if not auto
            java.util.Iterator<GridPosition> it = ratingCache.keySet().iterator();
            int toRemove = ratingCache.size() - 800;
            while (it.hasNext() && toRemove > 0) {
                it.next();
                it.remove();
                toRemove--;
            }
        }
    }

    // Note for large scale: getTopRatedIslands is global DB query; for 1000+ islands, consider per-island RegionScheduler staggering for any in-mem refresh or invalidation (see suggestions for "per-island RegionScheduler for globals/leaderboards", "staggered RegionScheduler for more (e.g. global tops, leaderboards)").
}
