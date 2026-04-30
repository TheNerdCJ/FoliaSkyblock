package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.generator.IslandGenerator;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full-featured, highly optimized IslandManager for Folia 1.21+
 */
public class IslandManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final IslandGenerator islandGenerator;

    // Caches
    private final Map<UUID, Map<World.Environment, Island>> playerIslands = new ConcurrentHashMap<>();
    private final Map<GridPosition, Island> islandCache = new ConcurrentHashMap<>();

    // Simple spiral generator for new islands
    private int currentSpiralX = 0;
    private int currentSpiralZ = 0;
    private int spiralStep = 1;
    private int spiralDirection = 0; // 0=right, 1=up, 2=left, 3=down

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.islandGenerator = new IslandGenerator(plugin);
    }

    // ====================== CORE LOOKUP ======================
    public Island getIsland(UUID playerUuid, World.Environment dimension) {
        return playerIslands
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .get(dimension);
    }

    public boolean hasIsland(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

    // ====================== CREATE ISLAND ======================
    public CompletableFuture<Boolean> createIsland(Player player, String biomeName, World.Environment dimension) {
        GridPosition pos = getNextAvailablePosition();

        return databaseManager.saveIsland(pos.x(), pos.z(), player.getUniqueId(), biomeName, dimension)
                .thenApply(success -> {
                    if (success) {
                        Island island = new Island(pos, player.getUniqueId(), biomeName, dimension);
                        cacheIsland(player.getUniqueId(), island);

                        // Generate the physical island (biome-aware)
                        Biome chosenBiome = null;
                        if (biomeName != null && !biomeName.isEmpty()) {
                            try {
                                // Modern way (1.21.3+ compatible) - avoid deprecated Biome.valueOf()
                                NamespacedKey key = NamespacedKey.minecraft(biomeName.toLowerCase().replace(" ", "_"));
                                chosenBiome = Registry.BIOME.get(key);
                            } catch (Exception ignored) {}
                        }

                        // Normal players get random biome, donors can choose later via GUI
                        boolean isDonor = player.hasPermission("foliasb.donor");
                        islandGenerator.generateIsland(island, player, chosenBiome, isDonor);

                        return true;
                    }
                    return false;
                });
    }

    private GridPosition getNextAvailablePosition() {
        GridPosition pos = new GridPosition(currentSpiralX, currentSpiralZ);

        // Move to next position in spiral
        switch (spiralDirection) {
            case 0: currentSpiralX += spiralStep; break;
            case 1: currentSpiralZ += spiralStep; break;
            case 2: currentSpiralX -= spiralStep; break;
            case 3: currentSpiralZ -= spiralStep; break;
        }

        spiralDirection = (spiralDirection + 1) % 4;
        if (spiralDirection % 2 == 0) spiralStep++;

        return pos;
    }

    private void cacheIsland(UUID playerUuid, Island island) {
        playerIslands
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(island.getDimension(), island);
        islandCache.put(island.getGridPosition(), island);
    }

    // ====================== LOAD ON JOIN ======================
    public void loadPlayerIslands(Player player) {
        for (World.Environment dim : World.Environment.values()) {
            databaseManager.getIslandByOwner(player.getUniqueId(), dim)
                    .thenAccept(island -> {
                        if (island != null) {
                            cacheIsland(player.getUniqueId(), island);
                        }
                    });
        }
    }

    public void removePlayerFromCache(UUID playerUuid) {
        playerIslands.remove(playerUuid);
    }

    // ====================== HOME & TELEPORT ======================
    public Location getIslandHome(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island != null) {
            Location center = island.getCenter(player.getWorld());
            return center != null ? center : player.getWorld().getSpawnLocation();
        }
        return player.getWorld().getSpawnLocation();
    }

    // ====================== RESET ISLAND ======================
    public CompletableFuture<Boolean> resetIsland(Player player, World.Environment dimension) {
        Island island = getIsland(player.getUniqueId(), dimension);
        if (island == null) return CompletableFuture.completedFuture(false);

        return databaseManager.deleteIsland(player.getUniqueId(), dimension)
                .thenCompose(deleted -> {
                    if (deleted) {
                        // Remove from cache
                        playerIslands.getOrDefault(player.getUniqueId(), new HashMap<>()).remove(dimension);
                        islandCache.remove(island.getGridPosition());

                        // Create new island
                        return createIsland(player, "PLAINS", dimension);
                    }
                    return CompletableFuture.completedFuture(false);
                });
    }

    // ====================== DELETE ISLAND ======================
    public CompletableFuture<Boolean> deleteIsland(Player player, World.Environment dimension) {
        Island island = getIsland(player.getUniqueId(), dimension);
        if (island == null) return CompletableFuture.completedFuture(false);

        return databaseManager.deleteIsland(player.getUniqueId(), dimension)
                .thenApply(deleted -> {
                    if (deleted) {
                        playerIslands.getOrDefault(player.getUniqueId(), new HashMap<>()).remove(dimension);
                        islandCache.remove(island.getGridPosition());
                    }
                    return deleted;
                });
    }

    // ====================== XP SYSTEM ======================
    public void addIslandXp(Player player, double baseAmount) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island != null) {
            int partySize = island.getMemberCount();
            island.addXp(baseAmount, partySize);
        }
    }

    public void addIslandXp(UUID playerUuid, World.Environment dimension, double baseAmount) {
        Island island = getIsland(playerUuid, dimension);
        if (island != null) {
            int partySize = island.getMemberCount();
            island.addXp(baseAmount, partySize);
        }
    }

    // ====================== HELPER METHODS ======================
    public boolean hasIslandInDimension(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

    /**
     * Get the island at a specific location (used by protection listener)
     */
    public Island getIslandAt(Location location) {
        if (location == null || location.getWorld() == null) return null;

        World.Environment env = location.getWorld().getEnvironment();

        // Check all cached islands in this dimension
        for (Island island : islandCache.values()) {
            if (island.getDimension() != env) continue;

            Location center = island.getCenter(location.getWorld());
            if (center != null && location.distance(center) <= 64) {
                return island;
            }
        }
        return null;
    }
}