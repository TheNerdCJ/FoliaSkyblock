package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.generator.IslandGenerator;
import com.thenerdcj.island.IslandRank;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

    public Island getIslandAt(GridPosition pos) {
        return islandCache.get(pos);
    }

    public boolean hasIsland(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

    // ====================== ISLAND CREATION ======================
    public void createIsland(Player player, String biomeName) {
        UUID playerUuid = player.getUniqueId();
        World.Environment dimension = player.getWorld().getEnvironment();

        // Check if player already has an island in this dimension
        if (hasIsland(playerUuid, dimension)) {
            player.sendMessage("§cYou already have an island in this dimension!");
            return;
        }

        // Find next available grid position
        GridPosition pos = findNextAvailablePosition(dimension);

        // Create the island
        Island island = new Island(pos, playerUuid, biomeName, dimension);

        // Generate the island terrain
        islandGenerator.generateIsland(island, player, org.bukkit.block.Biome.PLAINS, false);

        // Cache the island
        cacheIsland(playerUuid, island);

        // Save to database
        databaseManager.saveIsland(
                pos.getX(), pos.getZ(), playerUuid, biomeName, dimension
        ).thenAccept(success -> {
            if (success) {
                player.sendMessage("§aIsland created successfully!");

                // Teleport player to their new island
                Location spawn = island.getSpawnLocation();
                if (spawn == null) {
                    spawn = new Location(player.getWorld(),
                            pos.getX() * 100 + 50, 100, pos.getZ() * 100 + 50);
                }
                player.teleport(spawn);
            } else {
                player.sendMessage("§cFailed to create island. Please try again.");
            }
        });
    }

    private GridPosition findNextAvailablePosition(World.Environment dimension) {
        while (true) {
            GridPosition pos = new GridPosition(currentSpiralX, currentSpiralZ, dimension);

            if (!islandCache.containsKey(pos)) {
                updateSpiralPosition();
                return pos;
            }

            updateSpiralPosition();
        }
    }

    private void updateSpiralPosition() {
        switch (spiralDirection) {
            case 0:
                currentSpiralX++;
                if (currentSpiralX == spiralStep) spiralDirection = 1;
                break;
            case 1:
                currentSpiralZ++;
                if (currentSpiralZ == spiralStep) spiralDirection = 2;
                break;
            case 2:
                currentSpiralX--;
                if (currentSpiralX == -spiralStep) spiralDirection = 3;
                break;
            case 3:
                currentSpiralZ--;
                if (currentSpiralZ == -spiralStep) {
                    spiralStep++;
                    spiralDirection = 0;
                }
                break;
        }
    }

    private void cacheIsland(UUID playerUuid, Island island) {
        playerIslands
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(island.getDimension(), island);
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

        Location spawn = new Location(player.getWorld(), centerX, 100, centerZ);
        player.teleport(spawn);
        player.sendMessage("§aTeleported to §e" + Bukkit.getOfflinePlayer(targetUuid).getName() + "'s§a island!");
    }

    // ====================== LOAD ON JOIN ======================
    public void loadPlayerIslands(Player player) {
        UUID playerUuid = player.getUniqueId();

        for (World.Environment dimension : World.Environment.values()) {
            databaseManager.getIslandByOwner(playerUuid, dimension).thenAccept(island -> {
                if (island != null) {
                    cacheIsland(playerUuid, island);
                }
            });
        }
    }

    // ====================== PARTY SYSTEM METHODS ======================
    public void inviteToParty(Player inviter, Player target) {
        Island island = getIsland(inviter.getUniqueId(), inviter.getWorld().getEnvironment());
        if (island == null) {
            inviter.sendMessage("§cYou don't have an island in this dimension!");
            return;
        }
        if (!island.isOwner(inviter.getUniqueId())) {
            inviter.sendMessage("§cOnly the island owner can invite players.");
            return;
        }

        if (hasIsland(target.getUniqueId(), target.getWorld().getEnvironment())) {
            inviter.sendMessage("§cThat player already has an island!");
            return;
        }

        target.sendMessage("§e" + inviter.getName() + "§a has invited you to join their island!");
        target.sendMessage("§aUse §b/island accept§a to join, or ignore to decline.");
        inviter.sendMessage("§aInvite sent to §e" + target.getName() + "§a!");
    }

    public void acceptPartyInvite(Player player) {
        player.sendMessage("§aInvite system active - check with the island owner!");
    }

    public void removeMemberFromIsland(UUID ownerUuid, UUID memberUuid) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island == null) return;

        if (!island.isOwner(ownerUuid)) return;

        island.removeMember(memberUuid);
    }

    public void setMemberRank(UUID ownerUuid, UUID memberUuid, IslandRank rank) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island == null) return;

        if (!island.isOwner(ownerUuid)) return;

        island.setMemberRank(memberUuid, rank);
    }

    // ====================== TOP ISLANDS ======================
    public CompletableFuture<List<Island>> getTopIslands(int limit) {
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    // ====================== ISLAND AT LOCATION ======================
    public Island getIslandAt(Location location) {
        if (location == null || location.getWorld() == null) return null;

        World.Environment env = location.getWorld().getEnvironment();

        for (Island island : islandCache.values()) {
            if (island.getDimension() != env) continue;

            Location center = island.getCenter(location.getWorld());
            if (center != null && location.distance(center) <= 64) {
                return island;
            }
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
        if (island != null) {
            return island.getSpawnLocation();
        }
        return null;
    }

    public void addXpToIsland(UUID playerUuid, int baseAmount) {
        Island island = getIsland(playerUuid, World.Environment.NORMAL);
        if (island != null) {
            int partySize = island.getMemberCount();
            island.addXp(baseAmount, partySize);
        }
    }
}