package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandUpgrade;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island Worth / Level System (Classic Skyblock style)
 *
 * Features:
 * - Configurable per-block worth values
 * - Async worth calculation (Folia-safe)
 * - Smart caching with invalidation
 * - Upgrade multipliers (from IslandUpgradeManager)
 * - Island "Worth Level" derived from total worth
 */
public class IslandWorthManager {

    private final FoliaSkyblock plugin;

    // Config values
    private final Map<Material, Double> blockWorth = new ConcurrentHashMap<>();
    private double levelBase = 100.0;
    private double levelMultiplier = 1.5;
    private boolean upgradeMultipliersEnabled = true;

    // Smart caching - worth is expensive to calculate. LRU bounded for large server perf (from optimization suggestions).
    private final Map<String, Double> worthCache = Collections.synchronizedMap(new LinkedHashMap<String, Double>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
            return size() > 2000;
        }
    });
    private final Map<String, Integer> worthLevelCache = Collections.synchronizedMap(new LinkedHashMap<String, Integer>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > 2000;
        }
    });

    // === NEW: Live delta tracking for incremental worth (Phase 1 optimization) ===
    // This lets us avoid full rescans on every block change.
    private final Map<String, Double> baseBlockWorth = new ConcurrentHashMap<>(); // islandKey -> sum of raw block values (no multipliers)

    // Perf / economy follow-up: configurable worth recalc (for massive scale; event-driven preferred)
    private long worthRecalcIntervalMinutes = 15;
    private boolean periodicRecalcEnabled = true;
    // Perf: chunk budget for worth scans (configurable; prevents lag on massive islands per optimization suggestions)
    private int maxBlocksToScan = 250_000;
    // Debug hot-path profiling (nanoTime around chunk scan combine; gated, from optimization suggestions)
    private boolean profileHotPaths = false;
    // Perf cap for periodic recalc loop (from suggestions: max-islands-per-recalc-tick to avoid lag on massive servers)
    private int maxIslandsPerRecalcTick = 50;

    public IslandWorthManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        blockWorth.clear();

        ConfigurationSection worthSection = plugin.getConfig().getConfigurationSection("island.worth");
        if (worthSection == null) return;

        // Block worth values
        ConfigurationSection blockSection = worthSection.getConfigurationSection("block-worth");
        if (blockSection != null) {
            for (String key : blockSection.getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    double value = blockSection.getDouble(key, 1.0);
                    blockWorth.put(mat, value);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Level formula - more configurable
        ConfigurationSection formula = worthSection.getConfigurationSection("level-formula");
        if (formula != null) {
            levelBase = formula.getDouble("base", 100.0);
            levelMultiplier = formula.getDouble("multiplier", 1.5);
        }

        // Upgrade multipliers - fully configurable per upgrade type
        ConfigurationSection upgradeMulti = worthSection.getConfigurationSection("upgrade-multipliers");
        if (upgradeMulti != null) {
            upgradeMultipliersEnabled = upgradeMulti.getBoolean("enabled", true);
            // Future: load per-upgrade multipliers from config map
        } else {
            upgradeMultipliersEnabled = true;
        }

        // Perf config for economy/perf next steps (configurable interval or event-driven only)
        ConfigurationSection perf = worthSection.getConfigurationSection("recalc");
        if (perf == null) {
            // fallback to flat key for simplicity (as added in config.yml)
            worthRecalcIntervalMinutes = worthSection.getLong("recalc-interval-minutes", 15);
        } else {
            worthRecalcIntervalMinutes = perf.getLong("interval-minutes", 15);
        }
        periodicRecalcEnabled = worthRecalcIntervalMinutes > 0;

        // Chunk budget for calc (optimization: make MAX_BLOCKS_TO_SCAN configurable per island size / server profile)
        maxBlocksToScan = worthSection.getInt("max-blocks-to-scan", 250000);
        profileHotPaths = worthSection.getBoolean("profile-hot-paths", false);
        maxIslandsPerRecalcTick = worthSection.getInt("max-islands-per-recalc-tick", 50);
    }

    public long getWorthRecalcIntervalMinutes() { return worthRecalcIntervalMinutes; }
    public boolean isPeriodicRecalcEnabled() { return periodicRecalcEnabled; }
    public int getMaxBlocksToScan() { return maxBlocksToScan; }
    public boolean isProfileHotPaths() { return profileHotPaths; }
    public int getMaxIslandsPerRecalcTick() { return maxIslandsPerRecalcTick; }

    /**
     * Returns the worth value of a single block type.
     */
    public double getBlockWorth(Material material) {
        return blockWorth.getOrDefault(material, 0.0);
    }

    /**
     * Asynchronously calculates the total worth of an island using real block scanning.
     * Uses Folia async chunk loading for safety and performance.
     * Includes hard limits to prevent lag on huge islands.
     */
    public CompletableFuture<Double> calculateIslandWorthAsync(Island island) {
        String key = getIslandKey(island);

        Double cached = worthCache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Load persisted worth for drift correction (implemented this pass for optimization suggestions)
        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().getIslandDAO() != null) {
            try {
                Object[] persisted = plugin.getDatabaseManager().getIslandDAO().loadIslandWorth(island.getGridPosition()).join();
                if (persisted != null) {
                    double pWorth = (double) persisted[0];
                    if (pWorth > 0) {
                        baseBlockWorth.put(key, pWorth); // seed for incremental
                        // apply multipliers etc would be in full load, but for now use as base
                        worthCache.put(key, pWorth);
                        worthLevelCache.put(key, (int) persisted[1]);
                        return CompletableFuture.completedFuture(pWorth);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Folia-safe async worth calculation using proper chunk async loading + ChunkSnapshot (thread-safe reads)
        String worldName = "skyblock";
        if (island.getDimension() == World.Environment.NETHER) worldName = "skyblock_nether";
        else if (island.getDimension() == World.Environment.THE_END) worldName = "skyblock_end";

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return CompletableFuture.completedFuture(0.0);
        }

        Location center = island.getCenter(world);
        if (center == null) {
            return CompletableFuture.completedFuture(0.0);
        }

        int radius = island.getEffectiveIslandRadius(); // Use authoritative value from Island (includes ISLAND_SIZE upgrades)
        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;

        final int MAX_BLOCKS_TO_SCAN = maxBlocksToScan;

        // Build list of per-chunk futures using getChunkAtAsync + ChunkSnapshot for Folia safety
        java.util.List<CompletableFuture<Double>> chunkFutures = new java.util.ArrayList<>();

        for (int cx = (minX >> 4); cx <= (maxX >> 4); cx++) {
            for (int cz = (minZ >> 4); cz <= (maxZ >> 4); cz++) {
                final int chunkX = cx;
                final int chunkZ = cz;

                CompletableFuture<Double> chunkFuture = world.getChunkAtAsync(cx, cz)
                    .thenApply(chunk -> {
                        org.bukkit.ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
                        double chunkWorth = 0.0;
                        int localScanned = 0;

                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                int worldX = (chunkX << 4) + x;
                                int worldZ = (chunkZ << 4) + z;
                                if (worldX < minX || worldX > maxX || worldZ < minZ || worldZ > maxZ) continue;

                                for (int y = world.getMinHeight(); y < Math.min(256, world.getMaxHeight()); y++) {
                                    if (localScanned >= MAX_BLOCKS_TO_SCAN) break;

                                    Material type = snapshot.getBlockType(x, y, z);
                                    if (type != Material.AIR && type != Material.VOID_AIR) {
                                        chunkWorth += getBlockWorth(type);
                                        localScanned++;
                                    }
                                }
                                if (localScanned >= MAX_BLOCKS_TO_SCAN) break;
                            }
                            if (localScanned >= MAX_BLOCKS_TO_SCAN) break;
                        }
                        return chunkWorth;
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("[IslandWorth] Async chunk scan failed for " + chunkX + "," + chunkZ + ": " + ex.getMessage());
                        return 0.0;
                    });

                chunkFutures.add(chunkFuture);
            }
        }

        // Combine all chunk contributions, then apply multipliers + cache (on completion)
        final long profileStart = profileHotPaths ? System.nanoTime() : 0L;
        return java.util.concurrent.CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                if (profileHotPaths) {
                    long ns = System.nanoTime() - profileStart;
                    plugin.getLogger().info("[IslandWorth] PROFILE: chunk combine took " + (ns / 1_000_000) + " ms for " + chunkFutures.size() + " chunks (island key=" + key + ")");
                }
                double totalWorth = 0.0;
                for (CompletableFuture<Double> f : chunkFutures) {
                    try {
                        totalWorth += f.join(); // safe here: all futures already completed
                    } catch (Exception ignored) {}
                }

                // If we have live delta base worth (from block events), prefer it as the raw block value
                // (avoids full rescan cost for most changes)
                Double liveBase = baseBlockWorth.get(key);
                if (liveBase != null && liveBase > 0) {
                    totalWorth = liveBase;  // Use the incrementally maintained value
                } else {
                    // First time or after full invalidate → seed the base from this full scan
                    baseBlockWorth.put(key, totalWorth);
                }

                // Apply upgrade multipliers on top of (base or freshly scanned) raw worth
                if (upgradeMultipliersEnabled) {
                    double multiplier = getWorthMultiplierFromUpgrades(island);
                    totalWorth *= multiplier;
                }

                // Apply Prestige multipliers (endgame power)
                if (plugin.getPrestigeManager() != null) {
                    double prestigeMult = plugin.getPrestigeManager().getPrestigeMultiplier(island, com.thenerdcj.manager.PrestigeManager.PrestigeMultiplierType.WORTH);
                    totalWorth *= prestigeMult;
                }

                // Cache result (LRU bounded via LinkedHashMap override for massive server safety - see optimization suggestions)
                worthCache.put(key, totalWorth);
                int level = calculateWorthLevel(totalWorth);
                worthLevelCache.put(key, level);

                // Persist for drift correction on load (this pass)
                if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().getIslandDAO() != null) {
                    plugin.getDatabaseManager().getIslandDAO().saveIslandWorth(island.getGridPosition(), totalWorth, level, System.currentTimeMillis());
                }

                // Task 1: explicit tie - sync worthLevel to Island for getProgressionLevel() / display. Worth = economy value (tops, bank interest potential); XP level = play unlocks.
                // Folia: called from RegionScheduler in FoliaSkyblock worth task (per-island).
                island.syncWorthLevel(level);

                // Persistence prepared (saveIslandWorth in DatabaseManager)

                return totalWorth;
            });
    }

    /**
     * Calculates the "Worth Level" from total worth.
     */
    public int calculateWorthLevel(double totalWorth) {
        if (totalWorth <= 0) return 1;
        return (int) Math.floor(Math.sqrt(totalWorth / levelBase) * levelMultiplier) + 1;
    }

    private double getWorthMultiplierFromUpgrades(Island island) {
        if (plugin.getIslandUpgradeManager() == null) return 1.0;

        // Example multipliers - can be expanded
        int cropLevel = plugin.getIslandUpgradeManager().getUpgradeLevel(island.getOwnerUuid().toString(), IslandUpgrade.CROP_GROWTH);
        int spawnerLevel = plugin.getIslandUpgradeManager().getUpgradeLevel(island.getOwnerUuid().toString(), IslandUpgrade.SPAWNER_RATE);

        double multiplier = 1.0;
        multiplier += (cropLevel * 0.02);      // +2% per level
        multiplier += (spawnerLevel * 0.03);   // +3% per level

        return Math.max(1.0, multiplier);
    }

    /**
     * Gets cached worth (or 0 if not calculated yet).
     * Use calculateIslandWorthAsync for accurate value.
     */
    public double getCachedWorth(Island island) {
        return worthCache.getOrDefault(getIslandKey(island), 0.0);
    }

    public int getCachedWorthLevel(Island island) {
        return worthLevelCache.getOrDefault(getIslandKey(island), 1);
    }

    /**
     * Convenience for holograms and other systems: get worth for a specific island by owner + dimension.
     */
    public double getWorthForPlayerIsland(UUID owner, World.Environment dimension) {
        if (plugin.getIslandManager() == null) return 0;
        Island island = plugin.getIslandManager().getIsland(owner, dimension);
        if (island == null) return 0;
        return getCachedWorth(island);
    }

    /**
     * Updates a player's tab list name with their island worth (if they have one).
     * Safe to call from any thread.
     */
    public void updatePlayerTabList(org.bukkit.entity.Player player) {
        if (player == null || !player.isOnline()) return;

        plugin.getThreadSafety().runOnMainThread(() -> {
            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island == null) {
                player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
                return;
            }

            double worth = getCachedWorth(island);
            int level = getCachedWorthLevel(island);
            int prestige = (plugin.getPrestigeManager() != null) 
                    ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

            Component name = Component.text(player.getName(), NamedTextColor.WHITE);

            Component prestigeComp = prestige > 0 
                    ? Component.text("[" + prestige + "] ", NamedTextColor.GOLD)
                    : Component.empty();

            Component worthComp = Component.text(" [W:", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%,.0f", worth), NamedTextColor.GOLD))
                    .append(Component.text(" L:", NamedTextColor.GRAY))
                    .append(Component.text(level, NamedTextColor.AQUA))
                    .append(Component.text("]", NamedTextColor.GRAY));

            player.playerListName(prestigeComp.append(name).append(worthComp));
        });
    }

    /**
     * Invalidates cache when blocks are placed/broken or upgrades change.
     * Call this from listeners when relevant changes happen.
     */
    public void invalidateCache(Island island) {
        String key = getIslandKey(island);
        worthCache.remove(key);
        worthLevelCache.remove(key);
    }

    public void invalidateCache(GridPosition pos, World.Environment dimension) {
        String key = pos.x() + ":" + pos.z() + ":" + dimension.name();
        worthCache.remove(key);
        worthLevelCache.remove(key);
        baseBlockWorth.remove(key); // also drop delta base when we do full invalidation
    }

    /**
     * Delta adjustment for incremental worth tracking (Folia optimization).
     * Called from BlockPlaceEvent / BlockBreakEvent so we rarely need full rescans.
     */
    public void adjustBlockWorth(Island island, Material type, int count) {
        if (island == null || type == null || count == 0) return;
        double value = blockWorth.getOrDefault(type, 0.0);
        if (value == 0.0) return;

        String key = getIslandKey(island);
        baseBlockWorth.merge(key, value * count, Double::sum);

        // Invalidate the final cached total so next read will recombine base + multipliers
        worthCache.remove(key);
        worthLevelCache.remove(key);
    }

    private String getIslandKey(Island island) {
        // Use GridPosition.toString() (comma "x,z,DIM") for cache + persist key consistency with IslandDAO / GridPosition
        GridPosition pos = island.getGridPosition();
        return pos.toString();
    }

    /**
     * Recalculates and updates an island's worth/level (async + Folia safe).
     */
    public void recalculateAndUpdate(Island island) {
        calculateIslandWorthAsync(island).thenAccept(worth -> {
            int level = calculateWorthLevel(worth);

            // Per-island RegionScheduler example for Folia perf (from optimization suggestions): schedule the final update at island center region to avoid global contention.
            Location center = island.getCenter(Bukkit.getWorld(island.getDimension() == World.Environment.NORMAL ? "skyblock" : (island.getDimension() == World.Environment.NETHER ? "skyblock_nether" : "skyblock_end")));
            if (center != null) {
                plugin.getThreadSafety().runAtLocation(center, () -> {
                    // Future: persist + fire events + update caches if needed
                });
            } else {
                plugin.getThreadSafety().runOnMainThread(() -> {
                    // fallback
                });
            }
        });
    }

    // ==================== LEADERBOARD SUPPORT ====================

    /**
     * Returns top islands by worth from currently loaded islands + cache (fast local).
     */
    public CompletableFuture<java.util.List<IslandTopEntry>> getTopIslandsByWorthLocal(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            java.util.List<IslandTopEntry> entries = new java.util.ArrayList<>();

            if (plugin.getIslandManager() != null) {
                for (Island island : plugin.getIslandManager().getAllLoadedIslands().values()) {
                    double worth = getCachedWorth(island);
                    if (worth <= 0) continue;

                    int wLevel = getCachedWorthLevel(island);
                    int members = island.getMemberCount();
                    String ownerName = plugin.getNameCache().getName(island.getOwnerUuid());

                    entries.add(new IslandTopEntry(
                        island.getOwnerUuid(),
                        worth,
                        wLevel,
                        members,
                        ownerName != null ? ownerName : island.getOwnerUuid().toString().substring(0, 8)
                    ));
                }
            }

            entries.sort((a, b) -> Double.compare(b.worth, a.worth));
            if (entries.size() > limit) entries = entries.subList(0, limit);
            return entries;
        });
    }

    /**
     * Proper global DB-backed leaderboard (recommended for /is top).
     * Falls back to local cache if DB query fails.
     */
    public CompletableFuture<java.util.List<IslandTopEntry>> getTopIslandsByWorth(int limit) {
        return plugin.getDatabaseManager().getTopIslandsByWorth(limit).thenApply(dbResults -> {
            java.util.List<IslandTopEntry> entries = new java.util.ArrayList<>();

            for (var db : dbResults) {
                String ownerName = plugin.getNameCache().getName(db.ownerUuid);
                entries.add(new IslandTopEntry(
                    db.ownerUuid,
                    db.worth,
                    db.worthLevel,
                    db.memberCount,
                    ownerName != null ? ownerName : db.ownerUuid.toString().substring(0, 8)
                ));
            }

            // Already sorted by DB query (ORDER BY worth DESC)
            return entries;
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[IslandWorth] DB leaderboard failed, falling back to local: " + ex.getMessage());
            // Fallback
            try {
                return getTopIslandsByWorthLocal(limit).get();
            } catch (Exception e) {
                return java.util.Collections.emptyList();
            }
        });
    }

    /**
     * Overload with offset for DB-paginated worth tops (large scale 1000+).
     * Delegates to DM for server-side pagination.
     */
    public CompletableFuture<java.util.List<IslandTopEntry>> getTopIslandsByWorth(int limit, int offset) {
        return plugin.getDatabaseManager().getTopIslandsByWorth(limit, offset).thenApply(dbResults -> {
            java.util.List<IslandTopEntry> entries = new java.util.ArrayList<>();

            for (var db : dbResults) {
                String ownerName = plugin.getNameCache().getName(db.ownerUuid);
                entries.add(new IslandTopEntry(
                    db.ownerUuid,
                    db.worth,
                    db.worthLevel,
                    db.memberCount,
                    ownerName != null ? ownerName : db.ownerUuid.toString().substring(0, 8)
                ));
            }

            return entries;
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[IslandWorth] DB leaderboard (offset) failed, falling back to local: " + ex.getMessage());
            try {
                return getTopIslandsByWorthLocal(limit).get();
            } catch (Exception e) {
                return java.util.Collections.emptyList();
            }
        });
    }

    public static class IslandTopEntry {
        public final UUID owner;
        public final double worth;
        public final int level;
        public final int memberCount;
        public final String displayName;

        public IslandTopEntry(UUID owner, double worth, int level, int memberCount, String displayName) {
            this.owner = owner;
            this.worth = worth;
            this.level = level;
            this.memberCount = memberCount;
            this.displayName = displayName;
        }
    }
}
