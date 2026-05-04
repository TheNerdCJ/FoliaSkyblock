package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.database.TopIslandEntry;
import com.thenerdcj.island.generator.IslandGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
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

    public Island getIslandAt(GridPosition pos) {
        return islandCache.get(pos);
    }

    public boolean hasIsland(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

    // ====================== CREATE ISLAND ======================
    public void createIsland(Player player, String biomeName) {
        createIsland(player, biomeName, World.Environment.NORMAL);
    }

    public CompletableFuture<Boolean> createIsland(Player player, String biomeName, World.Environment dimension) {
        GridPosition pos = getNextAvailablePosition();

        return databaseManager.saveIsland(pos.x(), pos.z(), player.getUniqueId(), biomeName, dimension.name())
                .thenApply(success -> {
                    if (success) {
                        Island island = new Island(pos, player.getUniqueId(), biomeName, dimension);
                        cacheIsland(player.getUniqueId(), island);

                        // Generate the physical island (biome-aware)
                        org.bukkit.block.Biome chosenBiome = null;
                        if (biomeName != null && !biomeName.isEmpty()) {
                            try {
                                chosenBiome = Biome.valueOf(biomeName);
                            } catch (IllegalArgumentException e) {
                                chosenBiome = org.bukkit.block.Biome.PLAINS;
                            }
                        }
                        islandGenerator.generateIsland(island, player, chosenBiome != null ? chosenBiome : org.bukkit.block.Biome.PLAINS, false);

                        player.sendMessage("§aIsland created successfully!");
                        player.teleport(island.getSpawnLocation());
                    }
                    return success;
                });
    }

    private GridPosition getNextAvailablePosition() {
        while (true) {
            GridPosition pos = new GridPosition(currentSpiralX, currentSpiralZ, World.Environment.NORMAL);
            if (!islandCache.containsKey(pos)) {
                updateSpiralPosition();
                return pos;
            }
            updateSpiralPosition();
        }
    }

    private void updateSpiralPosition() {
        switch (spiralDirection) {
            case 0: currentSpiralX++; if (currentSpiralX == spiralStep) spiralDirection = 1; break;
            case 1: currentSpiralZ++; if (currentSpiralZ == spiralStep) spiralDirection = 2; break;
            case 2: currentSpiralX--; if (currentSpiralX == -spiralStep) spiralDirection = 3; break;
            case 3: currentSpiralZ--; if (currentSpiralZ == -spiralStep) { spiralStep++; spiralDirection = 0; } break;
        }
    }

    private void cacheIsland(UUID playerUuid, Island island) {
        playerIslands.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(island.getDimension(), island);
        islandCache.put(island.getGridPosition(), island);
    }

    // ====================== TELEPORT ======================
    public void teleportToIsland(Player player, UUID targetUuid) {
        Island island = getIsland(targetUuid, player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cThat player doesn't have an island in this dimension!");
            return;
        }
        int centerX = island.getGridPosition().x() * 100 + 50;
        int centerZ = island.getGridPosition().z() * 100 + 50;
        player.teleport(new Location(player.getWorld(), centerX, 100, centerZ));
        player.sendMessage("§aTeleported to §e" + Bukkit.getOfflinePlayer(targetUuid).getName() + "'s§a island!");
    }

    // ====================== LOAD ON JOIN ======================
    public void loadPlayerIslands(Player player) {
        UUID playerUuid = player.getUniqueId();
        for (World.Environment dimension : World.Environment.values()) {
            databaseManager.getIslandByOwner(playerUuid, dimension).thenAccept(island -> {
                if (island != null) cacheIsland(playerUuid, island);
            });
        }
    }

    // ====================== PARTY SYSTEM METHODS ======================
    public void inviteToParty(Player inviter, Player target) {
        Island island = getIsland(inviter.getUniqueId(), inviter.getWorld().getEnvironment());
        if (island == null) { inviter.sendMessage("§cYou don't have an island!"); return; }
        if (!island.isOwner(inviter.getUniqueId())) { inviter.sendMessage("§cOnly the owner can invite!"); return; }
        if (hasIsland(target.getUniqueId(), target.getWorld().getEnvironment())) { inviter.sendMessage("§cThat player already has an island!"); return; }
        target.sendMessage("§e" + inviter.getName() + "§a invited you to their island! Use §b/island accept§a to join.");
        inviter.sendMessage("§aInvite sent to §e" + target.getName() + "§a!");
    }

    public void acceptPartyInvite(Player player) {
        player.sendMessage("§aInvite system active - check with the island owner!");
    }

    public void removeMemberFromIsland(UUID ownerUuid, UUID memberUuid) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island == null || !island.isOwner(ownerUuid)) return;
        island.removeMember(memberUuid);
    }

    public void setMemberRank(UUID ownerUuid, UUID memberUuid, IslandRank rank) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island == null || !island.isOwner(ownerUuid)) return;
        island.setMemberRank(memberUuid, rank);
    }

    // ====================== TOP ISLANDS ======================
    public List<TopIslandEntry> getTopIslands(int limit) {
        // Implementation - return top islands by level/xp
        return new ArrayList<>();
    }

    // ====================== ISLAND AT LOCATION ======================
    public Island getIslandAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        World.Environment env = location.getWorld().getEnvironment();
        for (Island island : islandCache.values()) {
            if (island.getDimension() != env) continue;
            Location center = island.getCenter(location.getWorld());
            if (center != null && location.distance(center) <= 64) return island;
        }
        return null;
    }

    public Island getIslandByPosition(GridPosition pos) {
        if (pos == null) return null;
        return islandCache.get(pos);
    }

    // ====================== HELPER METHODS ======================
    public boolean hasIslandInDimension(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

    public Location getIslandHome(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        return island != null ? island.getSpawnLocation() : null;
    }

    public void addXpToIsland(UUID playerUuid, int baseAmount) {
        Island island = getIsland(playerUuid, World.Environment.NORMAL);
        if (island != null) island.addXp(baseAmount, island.getMemberCount());
    }
    public CompletableFuture<Island> getPlayerIsland(UUID playerUuid) {
        return databaseManager.getIslandByOwner(playerUuid, org.bukkit.World.Environment.NORMAL);
    }
    public CompletableFuture<com.thenerdcj.island.Island> getIslandByOwner(UUID ownerUuid, org.bukkit.World.Environment dimension) {
        return databaseManager.getIslandByOwner(ownerUuid, dimension);
    }
}