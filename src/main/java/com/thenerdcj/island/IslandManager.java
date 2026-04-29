package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optimized IslandManager with multi-layer caching for maximum performance
 */
public class IslandManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;

    // Layer 1: Player → Dimension → Island (primary cache)
    private final Map<UUID, Map<World.Environment, Island>> playerIslands = new ConcurrentHashMap<>();

    // Layer 2: GridPosition → Island (reverse lookup for fast position-based access)
    private final Map<GridPosition, Island> islandCache = new ConcurrentHashMap<>();

    // Layer 3: UUID → Island (fast owner lookup - NEW OPTIMIZATION)
    private final Map<UUID, Island> ownerCache = new ConcurrentHashMap<>();

    // Spiral generator
    private int currentSpiralX = 0;
    private int currentSpiralZ = 0;
    private int spiralStep = 1;
    private int spiralDirection = 0;

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    // ====================== OPTIMIZED LOOKUP ======================

    public Island getIsland(UUID playerUuid, World.Environment dimension) {
        Map<World.Environment, Island> dimMap = playerIslands.get(playerUuid);
        return dimMap != null ? dimMap.get(dimension) : null;
    }

    public boolean hasIsland(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

    public boolean hasIslandInDimension(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

    // NEW: Fast owner lookup (O(1))
    public Island getIslandByOwner(UUID ownerUuid) {
        return ownerCache.get(ownerUuid);
    }

    // ====================== CREATE ISLAND ======================

    public CompletableFuture<Boolean> createIsland(Player player, String biome, World.Environment dimension) {
        GridPosition pos = getNextAvailablePosition();

        return databaseManager.saveIsland(pos.x(), pos.z(), player.getUniqueId(), biome, dimension)
                .thenApply(success -> {
                    if (success) {
                        Island island = new Island(pos, player.getUniqueId(), biome, dimension);
                        cacheIsland(player.getUniqueId(), island);
                        return true;
                    }
                    return false;
                });
    }

    private GridPosition getNextAvailablePosition() {
        GridPosition pos = new GridPosition(currentSpiralX, currentSpiralZ);

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

    // ====================== MULTI-LAYER CACHING ======================

    private void cacheIsland(UUID playerUuid, Island island) {
        // Layer 1: Player → Dimension → Island
        playerIslands
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(island.getDimension(), island);

        // Layer 2: GridPosition → Island
        islandCache.put(island.getGridPosition(), island);

        // Layer 3: Owner → Island (NEW)
        ownerCache.put(playerUuid, island);
    }

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
        Map<World.Environment, Island> removed = playerIslands.remove(playerUuid);
        if (removed != null) {
            for (Island island : removed.values()) {
                islandCache.remove(island.getGridPosition());
                ownerCache.remove(playerUuid);
            }
        }
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

    // ====================== RESET / DELETE ======================

    public CompletableFuture<Boolean> resetIsland(Player player, World.Environment dimension) {
        Island island = getIsland(player.getUniqueId(), dimension);
        if (island == null) return CompletableFuture.completedFuture(false);

        return databaseManager.deleteIsland(player.getUniqueId(), dimension)
                .thenCompose(deleted -> {
                    if (deleted) {
                        playerIslands.getOrDefault(player.getUniqueId(), Collections.emptyMap()).remove(dimension);
                        islandCache.remove(island.getGridPosition());
                        ownerCache.remove(player.getUniqueId());
                        return createIsland(player, "PLAINS", dimension);
                    }
                    return CompletableFuture.completedFuture(false);
                });
    }

    public CompletableFuture<Boolean> deleteIsland(Player player, World.Environment dimension) {
        Island island = getIsland(player.getUniqueId(), dimension);
        if (island == null) return CompletableFuture.completedFuture(false);

        return databaseManager.deleteIsland(player.getUniqueId(), dimension)
                .thenApply(deleted -> {
                    if (deleted) {
                        playerIslands.getOrDefault(player.getUniqueId(), Collections.emptyMap()).remove(dimension);
                        islandCache.remove(island.getGridPosition());
                        ownerCache.remove(player.getUniqueId());
                    }
                    return deleted;
                });
    }

    // ====================== PARTY / RANK / TRADE ======================

    public CompletableFuture<Boolean> inviteToParty(Player inviter, Player target) {
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> acceptPartyInvite(Player player) {
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> removeMemberFromIsland(UUID ownerUuid, UUID targetUuid) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island != null && island.isOwner(ownerUuid)) {
            island.removeMember(targetUuid);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> setMemberRank(UUID ownerUuid, UUID targetUuid, IslandRank rank) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island != null && island.isOwner(ownerUuid)) {
            island.setMemberRank(targetUuid, rank);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> performTrade(Player player, int tradeId) {
        return CompletableFuture.completedFuture(true);
    }
}