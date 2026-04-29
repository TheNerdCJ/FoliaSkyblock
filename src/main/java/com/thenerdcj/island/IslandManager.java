package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full IslandManager with Party System - Optimized for Folia
 */
public class IslandManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;

    // Caches
    private final Map<UUID, Map<World.Environment, Island>> playerIslands = new ConcurrentHashMap<>();
    private final Map<GridPosition, Island> islandCache = new ConcurrentHashMap<>();

    // Party invites cache (temporary)
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();

    // Spiral generator
    private int currentSpiralX = 0;
    private int currentSpiralZ = 0;
    private int spiralStep = 1;
    private int spiralDirection = 0;

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    // ====================== CORE METHODS ======================
    public Island getIsland(UUID playerUuid, World.Environment dimension) {
        return playerIslands
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .get(dimension);
    }

    public boolean hasIsland(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

    // ====================== CREATE / RESET / DELETE ======================
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
        pendingInvites.remove(playerUuid);
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

    // ====================== PARTY SYSTEM ======================

    /** Invite a player to your island */
    public CompletableFuture<Boolean> invitePlayerToIsland(Player inviter, Player target) {
        Island island = getIsland(inviter.getUniqueId(), inviter.getWorld().getEnvironment());
        if (island == null || !island.isOwner(inviter.getUniqueId())) {
            return CompletableFuture.completedFuture(false);
        }

        pendingInvites.put(target.getUniqueId(), inviter.getUniqueId());
        target.sendMessage("§e" + inviter.getName() + " invited you to their island! Type /island accept to join.");
        inviter.sendMessage("§aInvite sent to " + target.getName() + ".");
        return CompletableFuture.completedFuture(true);
    }

    /** Accept pending island invite */
    public CompletableFuture<Boolean> acceptInvite(Player player) {
        UUID inviterUuid = pendingInvites.remove(player.getUniqueId());
        if (inviterUuid == null) {
            player.sendMessage("§cYou have no pending island invites.");
            return CompletableFuture.completedFuture(false);
        }

        Island island = getIsland(inviterUuid, player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cThe island no longer exists.");
            return CompletableFuture.completedFuture(false);
        }

        island.addMember(player.getUniqueId(), IslandRank.GUEST);
        player.sendMessage("§aYou have joined " + Bukkit.getOfflinePlayer(inviterUuid).getName() + "'s island!");
        return CompletableFuture.completedFuture(true);
    }

    /** Kick a player from your island */
    public CompletableFuture<Boolean> kickPlayerFromIsland(Player kicker, UUID targetUuid) {
        Island island = getIsland(kicker.getUniqueId(), kicker.getWorld().getEnvironment());
        if (island == null || !island.isOwner(kicker.getUniqueId())) {
            return CompletableFuture.completedFuture(false);
        }

        if (island.isOwner(targetUuid)) {
            kicker.sendMessage("§cYou cannot kick the owner.");
            return CompletableFuture.completedFuture(false);
        }

        island.removeMember(targetUuid);
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.teleport(Bukkit.getWorld("world").getSpawnLocation());
            target.sendMessage("§cYou have been kicked from the island.");
        }
        return CompletableFuture.completedFuture(true);
    }

    /** Change a member's rank */
    public CompletableFuture<Boolean> setPlayerRank(Player owner, UUID targetUuid, IslandRank newRank) {
        Island island = getIsland(owner.getUniqueId(), owner.getWorld().getEnvironment());
        if (island == null || !island.isOwner(owner.getUniqueId())) {
            return CompletableFuture.completedFuture(false);
        }

        if (island.isOwner(targetUuid)) {
            owner.sendMessage("§cYou cannot change the owner's rank.");
            return CompletableFuture.completedFuture(false);
        }

        island.setMemberRank(targetUuid, newRank);
        return CompletableFuture.completedFuture(true);
    }

    /** Get all members of an island */
    public List<UUID> getIslandMembers(UUID ownerUuid, World.Environment dimension) {
        Island island = getIsland(ownerUuid, dimension);
        return island != null ? new ArrayList<>(island.getMembers().keySet()) : Collections.emptyList();
    }

    /** Player leaves the island */
    public CompletableFuture<Boolean> leaveIsland(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || island.isOwner(player.getUniqueId())) {
            return CompletableFuture.completedFuture(false);
        }

        island.removeMember(player.getUniqueId());
        player.teleport(Bukkit.getWorld("world").getSpawnLocation());
        player.sendMessage("§eYou have left the island.");
        return CompletableFuture.completedFuture(true);
    }

    /** Transfer ownership */
    public CompletableFuture<Boolean> transferOwnership(Player currentOwner, UUID newOwnerUuid) {
        Island island = getIsland(currentOwner.getUniqueId(), currentOwner.getWorld().getEnvironment());
        if (island == null || !island.isOwner(currentOwner.getUniqueId())) {
            return CompletableFuture.completedFuture(false);
        }

        island.transferOwnership(newOwnerUuid);
        return CompletableFuture.completedFuture(true);
    }
}