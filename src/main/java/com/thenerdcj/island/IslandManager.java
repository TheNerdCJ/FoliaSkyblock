package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.manager.GridManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IslandManager - Handles island data, caching, teleportation, and high-level operations.
 *
 * Delegates grid position management to GridManager.
 */
public class IslandManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final GridManager gridManager;

    // Cache: player -> dimension -> Island
    private final Map<UUID, Map<World.Environment, Island>> playerIslands = new ConcurrentHashMap<>();

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.gridManager = plugin.getGridManager();
    }

    // ====================== GETTERS ======================

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
        UUID uuid = player.getUniqueId();

        return gridManager.createPlayerIsland(uuid, dimension)
                .thenCompose(pos -> {
                    if (pos == null) {
                        player.sendMessage("§cCould not find a free island location. Please try again later.");
                        return CompletableFuture.completedFuture(false);
                    }

                    return databaseManager.saveIsland(
                            pos.x(), pos.z(), uuid, biomeName, dimension.name()
                    ).thenApply(success -> {
                        if (success) {
                            Island island = new Island(pos, uuid, biomeName, dimension);
                            cacheIsland(uuid, island);

                            // Generate physical island
                            boolean isDonor = player.hasPermission("foliasb.donor");
                            plugin.getIslandGenerator().generateIsland(island, player,
                                    getBiomeFromName(biomeName), isDonor);

                            player.sendMessage("§aYour island has been created!");
                            return true;
                        }
                        return false;
                    });
                });
    }

    private void cacheIsland(UUID playerUuid, Island island) {
        playerIslands
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(island.getDimension(), island);
    }

    // ====================== TELEPORT / HOME ======================

    public void teleportToIsland(Player player, UUID targetUuid) {
        Island island = getIsland(targetUuid, player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cThat player does not have an island in this dimension.");
            return;
        }

        Location center = gridManager.getCenterLocation(island.getGridPosition(), player.getWorld());
        if (center != null) {
            player.teleport(center);
            player.sendMessage("§aTeleported to §e" + Bukkit.getOfflinePlayer(targetUuid).getName() + "'s§a island!");
        }
    }

    public Location getIslandHome(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island != null) {
            return gridManager.getCenterLocation(island.getGridPosition(), player.getWorld());
        }
        return player.getWorld().getSpawnLocation();
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

    // ====================== RESET / DELETE ======================

    public CompletableFuture<Boolean> resetIsland(Player player, World.Environment dimension) {
        Island island = getIsland(player.getUniqueId(), dimension);
        if (island == null) return CompletableFuture.completedFuture(false);

        return databaseManager.deleteIsland(player.getUniqueId(), dimension)
                .thenCompose(deleted -> {
                    if (deleted) {
                        playerIslands.getOrDefault(player.getUniqueId(), new HashMap<>()).remove(dimension);
                        return gridManager.deletePlayerIsland(player.getUniqueId(), dimension)
                                .thenCompose(v -> createIsland(player, "PLAINS", dimension));
                    }
                    return CompletableFuture.completedFuture(false);
                });
    }

    public CompletableFuture<Boolean> deleteIsland(Player player, World.Environment dimension) {
        Island island = getIsland(player.getUniqueId(), dimension);
        if (island == null) return CompletableFuture.completedFuture(false);

        return databaseManager.deleteIsland(player.getUniqueId(), dimension)
                .thenCompose(deleted -> {
                    if (deleted) {
                        playerIslands.getOrDefault(player.getUniqueId(), new HashMap<>()).remove(dimension);
                        return gridManager.deletePlayerIsland(player.getUniqueId(), dimension);
                    }
                    return CompletableFuture.completedFuture(false);
                });
    }

    // ====================== LOCATION HELPERS ======================

    public Island getIslandAt(Location location) {
        if (location == null || location.getWorld() == null) return null;

        if (!gridManager.isIslandLocation(location)) return null;

        GridPosition pos = gridManager.getGridPosition(location);
        return getIslandByPosition(pos);
    }

    public Island getIslandByPosition(GridPosition pos) {
        if (pos == null) return null;

        for (Map<World.Environment, Island> islands : playerIslands.values()) {
            for (Island island : islands.values()) {
                if (island.getGridPosition().equals(pos)) {
                    return island;
                }
            }
        }
        return null;
    }

    // ====================== XP ======================

    public void addIslandXp(Player player, double amount) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island != null) {
            island.addXp(amount);
        }
    }

    public void addIslandXp(UUID playerUuid, World.Environment dimension, double amount) {
        Island island = getIsland(playerUuid, dimension);
        if (island != null) {
            island.addXp(amount);
        }
    }

    // ====================== PARTY SYSTEM ======================

    public void inviteToParty(Player inviter, Player target) {
        Island island = getIsland(inviter.getUniqueId(), inviter.getWorld().getEnvironment());
        if (island == null || !island.isOwner(inviter.getUniqueId())) {
            inviter.sendMessage("§cOnly the island owner can invite players.");
            return;
        }
        target.sendMessage("§a" + inviter.getName() + " invited you to their island! Use §b/is accept§a to join.");
    }

    public void acceptPartyInvite(Player player) {
        player.sendMessage("§aYou joined the island! (Full party system coming soon)");
    }

    public void removeMemberFromIsland(UUID ownerUuid, UUID targetUuid) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island != null && island.isOwner(ownerUuid)) {
            island.removeMember(targetUuid);
        }
    }

    public void setMemberRank(UUID ownerUuid, UUID targetUuid, IslandRank rank) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island != null && island.isOwner(ownerUuid)) {
            island.setMemberRank(targetUuid, rank);
        }
    }

    // ====================== HELPER ======================

    private org.bukkit.block.Biome getBiomeFromName(String name) {
        // Reuse the same logic from your previous IslandManager or GridManager
        if (name == null || name.isEmpty()) return org.bukkit.block.Biome.PLAINS;

        try {
            return org.bukkit.Registry.BIOME.get(org.bukkit.NamespacedKey.minecraft(name.toLowerCase()));
        } catch (Exception e) {
            return org.bukkit.block.Biome.PLAINS;
        }
    }
    public Island getIslandByOwner(UUID ownerUuid, World.Environment dimension) {
        return getIsland(ownerUuid, dimension);
    }
}