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

public class IslandManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;

    private final Map<UUID, Map<World.Environment, Island>> playerIslands = new ConcurrentHashMap<>();
    private final Map<GridPosition, Island> islandCache = new ConcurrentHashMap<>();

    private int currentSpiralX = 0;
    private int currentSpiralZ = 0;
    private int spiralStep = 1;
    private int spiralDirection = 0;

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public Island getIsland(UUID playerUuid, World.Environment dimension) {
        return playerIslands
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .get(dimension);
    }

    public boolean hasIsland(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

    public boolean hasIslandInDimension(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

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

    private void cacheIsland(UUID playerUuid, Island island) {
        playerIslands.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(island.getDimension(), island);
        islandCache.put(island.getGridPosition(), island);
    }

    public void loadPlayerIslands(Player player) {
        for (World.Environment dim : World.Environment.values()) {
            databaseManager.getIslandByOwner(player.getUniqueId(), dim)
                    .thenAccept(island -> {
                        if (island != null) cacheIsland(player.getUniqueId(), island);
                    });
        }
    }

    public void removePlayerFromCache(UUID playerUuid) {
        playerIslands.remove(playerUuid);
    }

    public Location getIslandHome(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island != null && island.getCenter(player.getWorld()) != null) {
            return island.getCenter(player.getWorld());
        }
        return player.getWorld().getSpawnLocation();
    }

    public CompletableFuture<Boolean> resetIsland(Player player, World.Environment dimension) {
        Island island = getIsland(player.getUniqueId(), dimension);
        if (island == null) return CompletableFuture.completedFuture(false);

        return databaseManager.deleteIsland(player.getUniqueId(), dimension)
                .thenCompose(deleted -> {
                    if (deleted) {
                        playerIslands.getOrDefault(player.getUniqueId(), Collections.emptyMap()).remove(dimension);
                        islandCache.remove(island.getGridPosition());
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
                    }
                    return deleted;
                });
    }

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