package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.generator.IslandGenerator;
import org.bukkit.*;
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
    private int spiralDirection = 0;

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
                        Biome chosenBiome = getBiomeFromName(biomeName);

                        boolean isDonor = player.hasPermission("foliasb.donor");
                        islandGenerator.generateIsland(island, player, chosenBiome, isDonor);

                        return true;
                    }
                    return false;
                });
    }

    /**
     * Modern way to get Biome from string (fixes deprecation warning)
     */
    private Biome getBiomeFromName(String name) {
        if (name == null || name.isEmpty()) {
            return Biome.PLAINS;
        }

        try {
            NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase().replace(" ", "_"));
            Biome biome = Registry.BIOME.get(key);
            if (biome != null) {
                return biome;
            }
        } catch (Exception ignored) {}

        // Fallback for common legacy names
        return switch (name.toUpperCase()) {
            case "PLAINS", "GRASSLAND" -> Biome.PLAINS;
            case "FOREST" -> Biome.FOREST;
            case "DESERT" -> Biome.DESERT;
            case "TAIGA" -> Biome.TAIGA;
            case "JUNGLE" -> Biome.JUNGLE;
            case "SNOWY_PLAINS", "SNOWY" -> Biome.SNOWY_PLAINS;
            case "MUSHROOM_ISLAND", "MUSHROOM_FIELDS" -> Biome.MUSHROOM_FIELDS;
            default -> Biome.PLAINS;
        };
    }

    private GridPosition getNextAvailablePosition() {
        GridPosition pos = new GridPosition(currentSpiralX, currentSpiralZ);

        switch (spiralDirection) {
            case 0 -> currentSpiralX += spiralStep;
            case 1 -> currentSpiralZ += spiralStep;
            case 2 -> currentSpiralX -= spiralStep;
            case 3 -> currentSpiralZ -= spiralStep;
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

    // ====================== HOME ======================
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
                        playerIslands.getOrDefault(player.getUniqueId(), new HashMap<>()).remove(dimension);
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
            island.addXp(baseAmount, island.getMemberCount());
        }
    }

    public void addIslandXp(UUID playerUuid, World.Environment dimension, double baseAmount) {
        Island island = getIsland(playerUuid, dimension);
        if (island != null) {
            island.addXp(baseAmount, island.getMemberCount());
        }
    }

    // ====================== HELPER METHODS ======================
    public boolean hasIslandInDimension(UUID playerUuid, World.Environment dimension) {
        return getIsland(playerUuid, dimension) != null;
    }

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

    // ====================== PARTY SYSTEM (Basic) ======================
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
        target.sendMessage("§a" + inviter.getName() + " has invited you to join their island!");
        target.sendMessage("§7Use §b/island accept§7 to join.");
    }

    public void acceptPartyInvite(Player player) {
        player.sendMessage("§aYou have joined the island! (Party system coming soon)");
    }

    public void removeMemberFromIsland(UUID ownerUuid, UUID targetUuid) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island == null || !island.isOwner(ownerUuid)) return;
        island.removeMember(targetUuid);
    }

    public void setMemberRank(UUID ownerUuid, UUID targetUuid, IslandRank rank) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island == null || !island.isOwner(ownerUuid)) return;
        island.setMemberRank(targetUuid, rank);
    }
}