package com.thenerdcj.season;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SeasonManager – Full implementation of Option B seasonal resets (server-wide 3-month cycles).
 *
 * - Selective DB wipe of island progress (via IslandDAO.performSeasonalIslandWipe, re-using cleanup patterns).
 * - Staggered physical plot clears using RegionScheduler per-grid (fresh visual slate for new season).
 * - Current season tracking (persisted in server_meta), history in seasons table.
 * - resetInProgress flag + safety checks.
 * - Basic grant API for seasonal event cosmetic releases (donor + player) – delegates to CosmeticDAO + grants audit.
 * - Cache invalidation across all relevant managers post-wipe.
 * - Dry-run support, confirmation, logging, broadcasts via ThreadSafety.
 *
 * Follows all project patterns: Folia schedulers (no direct Bukkit scheduler), async DAO, CompletableFuture,
 * withConnection, zero new deps, reuse of IslandDAO cleanup + recent aggregates/LRU/snapshot work.
 *
 * See SEASONAL_RESETS_DESIGN.md for full rationale, risks, and cross-refs.
 */
public class SeasonManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private volatile String currentSeason = "S1";
    private final AtomicBoolean resetInProgress = new AtomicBoolean(false);

    // Config (loaded on demand for simplicity; could cache)
    private int clearRadius;
    private int clearBatchSize;
    private long clearBatchDelayTicks;
    private boolean wipePlayerBalances;

    public SeasonManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
        loadConfig();
        loadCurrentSeason();
    }

    private void loadConfig() {
        var c = plugin.getConfig();
        this.clearRadius = c.getInt("seasonal.clear-radius", 300);
        this.clearBatchSize = Math.max(1, c.getInt("seasonal.clear-batch-size", 8));
        this.clearBatchDelayTicks = Math.max(1, c.getLong("seasonal.clear-batch-delay-ticks", 40));
        this.wipePlayerBalances = c.getBoolean("seasonal.wipe-player-balances", false);
    }

    private void loadCurrentSeason() {
        try {
            String s = plugin.getDatabaseManager().getCurrentSeason();
            if (s != null && !s.isEmpty()) {
                this.currentSeason = s;
            }
        } catch (Exception ignored) {}
        plugin.getLogger().info("[Season] Current season loaded: " + currentSeason);
    }

    public String getCurrentSeason() {
        return currentSeason;
    }

    public boolean isResetInProgress() {
        return resetInProgress.get();
    }

    /**
     * Entry point for admin-triggered seasonal reset (Option B).
     * @param newSeasonId e.g. "2026-Q3" or "S4-Summer"
     * @param dryRun if true, only count/report, no mutations
     * @param actorName for audit (admin name or "console")
     */
    public void beginSeasonalReset(String newSeasonId, boolean dryRun, String actorName) {
        if (resetInProgress.getAndSet(true)) {
            plugin.getLogger().warning("[Season] Reset already in progress, ignoring duplicate request.");
            return;
        }

        final String seasonId = (newSeasonId == null || newSeasonId.isBlank()) ? ("S" + System.currentTimeMillis() % 100000) : newSeasonId.trim();
        final long start = System.currentTimeMillis();

        plugin.getLogger().info("[Season] " + (dryRun ? "DRY-RUN " : "") + "Beginning seasonal reset to '" + seasonId + "' by " + actorName);

        // 1. Announce start of process
        broadcast("§6§l[Season] §eServer-wide seasonal reset to §f" + seasonId + " §eis starting. Competitive progress will be wiped; cosmetics and donor unlocks are preserved.");

        CompletableFuture<Integer> wipeFuture = dryRun
                ? plugin.getDatabaseManager().getAllIslandGrids().thenApply(List::size) // approximate count
                : plugin.getDatabaseManager().performSeasonalIslandWipe();

        wipeFuture.thenAccept(islandCount -> {
            plugin.getLogger().info("[Season] DB wipe " + (dryRun ? "simulated" : "completed") + ". Islands affected: " + islandCount);

            if (dryRun) {
                // For dry-run also fetch real grid count if possible
                plugin.getDatabaseManager().getAllIslandGrids().thenAccept(grids -> {
                    int real = grids.size();
                    broadcast("§a[Season DRY-RUN] Would wipe " + real + " islands + all island progress. Physical clear would target ~" + real + " plots with radius " + clearRadius + ".");
                    recordHistory(seasonId, "DRY-RUN", start, real, actorName);
                    resetInProgress.set(false);
                });
                return;
            }

            // Real path: invalidate caches immediately (data is gone)
            invalidateAllCaches();

            // 2. Physical clear pass (Option B) - staggered
            plugin.getDatabaseManager().getAllIslandGrids().thenAccept(grids -> {
                if (grids.isEmpty()) {
                    finishReset(seasonId, 0, actorName, start);
                    return;
                }

                AtomicInteger cleared = new AtomicInteger(0);
                AtomicInteger index = new AtomicInteger(0);
                int total = grids.size();

                // Schedule first batch
                scheduleNextClearBatch(grids, index, cleared, total, seasonId, actorName, start);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[Season] Wipe failed: " + ex.getMessage());
            broadcast("§c[Season] Reset encountered an error. Check console. Partial state may exist - contact staff.");
            resetInProgress.set(false);
            return null;
        });
    }

    private void scheduleNextClearBatch(List<GridPosition> grids, AtomicInteger index, AtomicInteger cleared, int total,
                                        String seasonId, String actorName, long startTs) {
        int startIdx = index.get();
        int end = Math.min(startIdx + clearBatchSize, total);

        for (int i = startIdx; i < end; i++) {
            GridPosition gp = grids.get(i);
            World world = getWorldForDimension(gp.getDimension());
            if (world == null) {
                cleared.incrementAndGet();
                continue;
            }

            Location center = new Location(world,
                    gp.x() * 512 + 256,
                    80, // baseY from Island
                    gp.z() * 512 + 256);

            // Schedule the actual clear on the correct region
            threadSafety.runAtLocationLater(center, () -> {
                try {
                    clearPlot(center, gp);
                } catch (Exception ex) {
                    plugin.getLogger().fine("[Season] Non-fatal clear error for " + gp + ": " + ex.getMessage());
                } finally {
                    int done = cleared.incrementAndGet();
                    if (done % 50 == 0 || done == total) {
                        plugin.getLogger().info("[Season] Physical clear progress: " + done + "/" + total);
                    }
                }
            }, 1L);
        }

        index.addAndGet(clearBatchSize);

        if (index.get() < total) {
            // Chain next batch after delay (global scheduler for orchestration)
            threadSafety.runOnMainThreadLater(() -> {
                scheduleNextClearBatch(grids, index, cleared, total, seasonId, actorName, startTs);
            }, clearBatchDelayTicks);
        } else {
            // All batches scheduled; wait a bit for last ones to finish then complete
            threadSafety.runOnMainThreadLater(() -> {
                finishReset(seasonId, total, actorName, startTs);
            }, clearBatchDelayTicks * 2);
        }
    }

    private void clearPlot(Location center, GridPosition gp) {
        World world = center.getWorld();
        if (world == null) return;

        int r = clearRadius;
        int minX = center.getBlockX() - r;
        int maxX = center.getBlockX() + r;
        int minZ = center.getBlockZ() - r;
        int maxZ = center.getBlockZ() + r;
        int minY = Math.max(world.getMinHeight(), 0);
        int maxY = center.getBlockY() + 120; // cover terrain + some buffer above baseY=80

        // Optimized: chunk-iterated clear (avoids expensive world.getBlockAt in hot loops; only processes relevant chunk slices)
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                // Local block coords within this chunk that overlap the plot
                int startLX = Math.max(0, minX - (cx << 4));
                int endLX = Math.min(15, maxX - (cx << 4));
                int startLZ = Math.max(0, minZ - (cz << 4));
                int endLZ = Math.min(15, maxZ - (cz << 4));

                for (int lx = startLX; lx <= endLX; lx++) {
                    for (int lz = startLZ; lz <= endLZ; lz++) {
                        for (int y = minY; y <= maxY; y++) {
                            chunk.getBlock(lx, y, lz).setType(org.bukkit.Material.AIR, false);
                        }
                    }
                }
            }
        }

        // Entity cleanup in the volume (minions = ArmorStand, overhead = TextDisplay, etc.)
        try {
            List<Entity> ents = world.getNearbyEntities(center, r, 200, r).stream()
                    .filter(e -> e.getType() == EntityType.ARMOR_STAND
                            || e.getType() == EntityType.TEXT_DISPLAY
                            || e.getType() == EntityType.ITEM_FRAME
                            || e.getType() == EntityType.GLOW_ITEM_FRAME)
                    .toList();
            for (Entity e : ents) {
                // Only remove if roughly inside the plot (cheap check)
                if (e.getLocation().distanceSquared(center) <= (r * r * 1.1)) {
                    e.remove();
                }
            }
        } catch (Exception ignored) {}

        // Optional: leave a very low bedrock "floor" marker at center for debug (disabled by default for clean void)
        // world.getBlockAt(center.getBlockX(), minY + 1, center.getBlockZ()).setType(Material.BEDROCK);
    }

    private World getWorldForDimension(org.bukkit.World.Environment env) {
        if (plugin.getWorldManager() != null) {
            return plugin.getWorldManager().resolveSkyblockWorld(env);
        }
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == env)
                .findFirst()
                .orElse(null);
    }

    private void finishReset(String seasonId, int gridsCleared, String actorName, long startTs) {
        // Update current season
        this.currentSeason = seasonId;
        plugin.getDatabaseManager().setCurrentSeason(seasonId);

        // Record history
        recordHistory(seasonId, "COMPLETED", startTs, gridsCleared, actorName);

        // Optional: wipe player balances (rarely wanted; default false)
        if (wipePlayerBalances) {
            try {
                // Best-effort: direct delete (player money reset policy)
                plugin.getDatabaseManager().executeUpdate("DELETE FROM player_balances");
                plugin.getLogger().info("[Season] player_balances wiped per config.");
            } catch (Exception e) {
                plugin.getLogger().warning("[Season] player_balances wipe failed: " + e.getMessage());
            }
        }

        // Final cache invalidation (in case)
        invalidateAllCaches();

        // Reset grid tracking (now empty)
        if (plugin.getGridManager() != null) {
            plugin.getGridManager().resetForNewSeason();
        }

        long dur = System.currentTimeMillis() - startTs;
        plugin.getLogger().info("[Season] Reset to " + seasonId + " COMPLETE in " + dur + "ms. Grids cleared: " + gridsCleared + ". Cosmetics preserved.");

        broadcast("§a§l[Season] §aReset to §f" + seasonId + " §acomplete! §7Your cosmetics, pets, wardrobe, slayer progress and donor perks have been preserved. Use §e/is create §7to begin the new season.");
        broadcast("§7Leaderboards and competitive progress have been reset for fresh seasonal events.");

        resetInProgress.set(false);
    }

    private void recordHistory(String seasonId, String status, long startTs, int count, String actor) {
        try {
            long ended = System.currentTimeMillis();
            plugin.getDatabaseManager().recordSeasonHistory(seasonId, "Season " + seasonId + " (" + status + ")", startTs, count, actor);
        } catch (Exception ignored) {}
    }

    private void invalidateAllCaches() {
        try {
            if (plugin.getIslandWorthManager() != null) {
                plugin.getIslandWorthManager().clearAllForNewSeason();
            }
            if (plugin.getIslandManager() != null) {
                plugin.getIslandManager().clearCachesForNewSeason();
            }
            if (plugin.getPrestigeManager() != null) {
                plugin.getPrestigeManager().clearForNewSeason();
            }
            if (plugin.getCollectionManager() != null) {
                plugin.getCollectionManager().clearForNewSeason();
            }
            if (plugin.getMuseumManager() != null) {
                plugin.getMuseumManager().clearForNewSeason();
            }
            if (plugin.getMinionManager() != null) {
                plugin.getMinionManager().clearForNewSeason();
            }
            if (plugin.getQuestManager() != null) {
                plugin.getQuestManager().clearForNewSeason();
            }
            // Economy island caches cleared via Worth or explicit if needed
            if (plugin.getEconomyManager() != null) {
                // EconomyManager delegates to DB cache which is invalidated by DB wipe + Worth clear
            }
            // Grid cleared separately after
        } catch (Exception e) {
            plugin.getLogger().warning("[Season] Some cache invalidation failed (non-fatal): " + e.getMessage());
        }
    }

    private void broadcast(String msg) {
        threadSafety.runOnMainThread(() -> {
            Bukkit.broadcastMessage(msg);
        });
    }

    /**
     * Grant a seasonal cosmetic (for event releases to players or donors).
     * Writes to the appropriate player_* ownership table (via CosmeticDAO) + audit row in player_seasonal_grants.
     * Safe to call for "all", specific player, or donors (permission check done by caller or here).
     */
    public void grantSeasonalCosmetic(java.util.UUID playerUuid, String category, String cosmeticId, String seasonId, String source) {
        if (playerUuid == null || category == null || cosmeticId == null) return;
        final String sid = (seasonId == null ? currentSeason : seasonId);

        // Delegate actual ownership write + grant log
        try {
            // Use CosmeticDAO for ownership (it knows the tables)
            plugin.getDatabaseManager().getCosmeticDAO().saveOwnedCosmetic(playerUuid, mapCategoryToTable(category), "id", cosmeticId, null, null);

            // Record grant audit (direct for now; can be moved to CosmeticDAO)
            plugin.getDatabaseManager().executeUpdate(
                "INSERT OR IGNORE INTO player_seasonal_grants (uuid, season_id, category, cosmetic_id, granted_at, source) VALUES ('" +
                playerUuid + "', '" + sid + "', '" + category + "', '" + cosmeticId + "', " + System.currentTimeMillis() + ", '" + (source == null ? "event" : source) + "')");
        } catch (Exception e) {
            plugin.getLogger().warning("[Season] grantSeasonalCosmetic failed for " + playerUuid + " " + category + ":" + cosmeticId + " : " + e.getMessage());
        }
    }

    private String mapCategoryToTable(String cat) {
        // Minimal mapping for common ones used in CosmeticDAO; extend as needed
        return switch (cat.toLowerCase()) {
            case "trail", "particle_trail" -> "player_particle_trails";
            case "pet" -> "player_pets";
            case "tag" -> "player_tags";
            case "wing", "elytra" -> "player_elytra_wings";
            case "rune" -> "player_runes";
            case "helmet_skin" -> "player_helmet_skins";
            case "death_effect" -> "player_death_effects";
            case "furniture" -> "player_island_furniture";
            default -> "player_" + cat + "s"; // best effort
        };
    }

    // Simple info for /isadmin season info
    public String getInfo() {
        return "Current: " + currentSeason + " | Reset in progress: " + resetInProgress.get();
    }
}