package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.manager.GridManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final GridManager gridManager;

    // Cache: player -> dimension -> Island
    private final Map<UUID, Map<World.Environment, Island>> playerIslands = new ConcurrentHashMap<>();

    // Cooldown for island resets (donor biome reset feature)
    private final Map<UUID, Long> lastResetTime = new ConcurrentHashMap<>();

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.gridManager = plugin.getGridManager();
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

    // ====================== DONOR BIOME RESET SYSTEM ======================

    public boolean canReset(Player player) {
        long cooldownHours = plugin.getConfig().getLong("island.reset.cooldown-hours", 24);
        if (cooldownHours <= 0) return true;

        Long lastReset = lastResetTime.get(player.getUniqueId());
        if (lastReset == null) return true;

        long hoursSinceReset = (System.currentTimeMillis() - lastReset) / (1000 * 60 * 60);
        return hoursSinceReset >= cooldownHours;
    }

    public long getResetCooldownRemainingHours(Player player) {
        long cooldownHours = plugin.getConfig().getLong("island.reset.cooldown-hours", 24);
        Long lastReset = lastResetTime.get(player.getUniqueId());
        if (lastReset == null) return 0;

        long hoursSinceReset = (System.currentTimeMillis() - lastReset) / (1000 * 60 * 60);
        return Math.max(0, cooldownHours - hoursSinceReset);
    }

    private void recordReset(Player player) {
        lastResetTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void resetIslandWithBiome(Player player, String biomeName, World.Environment dimension) {
        Island island = getIsland(player.getUniqueId(), dimension);
        if (island == null) {
            player.sendMessage("§cYou don't have an island in this dimension to reset.");
            return;
        }

        if (!canReset(player)) {
            long remaining = getResetCooldownRemainingHours(player);
            player.sendMessage("§cYou must wait §e" + remaining + " more hours§c before resetting again.");
            return;
        }

        // === ANTI-DUPLICATION PROTECTION ===
        Location safeSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        player.teleport(safeSpawn);

        clearIslandContainers(island, dimension);

        databaseManager.deleteIsland(player.getUniqueId(), dimension).thenAccept(deleted -> {
            if (deleted) {
                Map<World.Environment, Island> playerMap = playerIslands.get(player.getUniqueId());
                if (playerMap != null) {
                    playerMap.remove(dimension);
                }

                gridManager.deletePlayerIsland(player.getUniqueId(), dimension).thenRun(() -> {
                    createIsland(player, biomeName, dimension);
                    recordReset(player);
                    player.sendMessage("§aYour island has been reset to the §e" + biomeName + "§a biome!");
                });
            } else {
                player.sendMessage("§cFailed to reset your island. Please try again.");
            }
        });
    }

    private void clearIslandContainers(Island island, World.Environment dimension) {
        String worldName = switch (dimension) {
            case NETHER -> plugin.getConfig().getString("worlds.nether", "world_nether");
            case THE_END -> plugin.getConfig().getString("worlds.end", "world_the_end");
            default -> plugin.getConfig().getString("worlds.overworld", "world");
        };

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location center = gridManager.getCenterLocation(island.getGridPosition(), world);
        if (center == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            int radius = 10;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = -6; y <= 12; y++) {
                        Block block = world.getBlockAt(
                                center.getBlockX() + x,
                                center.getBlockY() + y,
                                center.getBlockZ() + z
                        );

                        if (block.getState() instanceof Container container) {
                            container.getInventory().clear();
                        }

                        if ((block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST)
                                && Math.abs(x) <= 6 && Math.abs(z) <= 6) {
                            block.setType(Material.AIR);
                        }
                    }
                }
            }
        });
    }

    // ====================== ORIGINAL RESET METHOD ======================

    public CompletableFuture<Boolean> resetIsland(Player player, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            resetIslandWithBiome(player, "PLAINS", dimension);
            return true;
        });
    }

    // ====================== HELPER ======================

    private org.bukkit.block.Biome getBiomeFromName(String name) {
        if (name == null || name.isEmpty()) return org.bukkit.block.Biome.PLAINS;
        try {
            return org.bukkit.Registry.BIOME.get(org.bukkit.NamespacedKey.minecraft(name.toLowerCase()));
        } catch (Exception e) {
            return org.bukkit.block.Biome.PLAINS;
        }
    }

    // ====================== PLACEHOLDER FOR OTHER ORIGINAL METHODS ======================
    // Keep all your other original methods here (teleportToIsland, addIslandXp, party methods, etc.)
    // ====================== TELEPORT & HOME ======================

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

// ====================== XP SYSTEM ======================

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
}