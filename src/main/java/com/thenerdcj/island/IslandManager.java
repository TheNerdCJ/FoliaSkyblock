package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.party.PartyManager;
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
    private final PartyManager partyManager;

    // Cache: player -> dimension -> Island
    private final Map<UUID, Map<World.Environment, Island>> playerIslands = new ConcurrentHashMap<>();

    // Cooldown for island resets (donor biome reset feature)
    private final Map<UUID, Long> lastResetTime = new ConcurrentHashMap<>();

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.gridManager = plugin.getGridManager();
        // Initialize PartyManager directly (works even if not yet exposed in main plugin)
        this.partyManager = new PartyManager(plugin);
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

    /**
     * Convenience method for compatibility with older code (e.g. BalanceCommand).
     * Delegates to getIsland since cache is keyed by owner UUID.
     */
    public Island getIslandByOwner(UUID owner, World.Environment dimension) {
        return getIsland(owner, dimension);
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

    // ====================== XP SYSTEM (UPDATED FOR PARTY BALANCING) ======================

    /**
     * Add XP to the player's current island, automatically applying party-size multiplier
     * for balanced progression (solo gets full XP, larger parties get diminishing returns).
     */
    public void addIslandXp(Player player, double amount) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island != null) {
            island.addXp(amount, Math.max(1, island.getMemberCount()));
        }
    }

    public void addIslandXp(UUID playerUuid, World.Environment dimension, double amount) {
        Island island = getIsland(playerUuid, dimension);
        if (island != null) {
            island.addXp(amount, Math.max(1, island.getMemberCount()));
        }
    }

    // ====================== PARTY SYSTEM (FULLY IMPLEMENTED & FIXED) ======================

    /**
     * Invite a player to the island party.
     * Uses PartyManager for pending invites, config-driven max size, and expiration.
     */
    public void inviteToParty(Player inviter, Player target) {
        Island island = getIsland(inviter.getUniqueId(), inviter.getWorld().getEnvironment());
        if (island == null) {
            inviter.sendMessage("§cYou don't have an island in this dimension.");
            return;
        }

        if (!island.isOwner(inviter.getUniqueId())) {
            inviter.sendMessage("§cOnly the island owner can invite players to the party.");
            return;
        }

        if (island.getMemberCount() >= partyManager.getMaxPartySize()) {
            inviter.sendMessage("§cYour party is full (max " + partyManager.getMaxPartySize() + " members).");
            return;
        }

        if (island.isMember(target.getUniqueId())) {
            inviter.sendMessage("§e" + target.getName() + " §cis already in your party.");
            return;
        }

        // Add pending invite via PartyManager (with auto-expire)
        partyManager.addPendingInvite(inviter.getUniqueId(), target.getUniqueId(), island);

        target.sendMessage("§a" + inviter.getName() + " §7has invited you to join their island party!");
        target.sendMessage("§7Use §b/is accept §7or §b/is deny§7 to respond. (Expires in " + (partyManager.getInviteTimeoutSeconds() / 60) + " minutes)");

        inviter.sendMessage("§aInvite sent to §e" + target.getName() + "§a. They have " + (partyManager.getInviteTimeoutSeconds() / 60) + " minutes to accept.");
    }

    /**
     * Accept a pending party invite (fully functional, with DB persistence).
     */
    public void acceptPartyInvite(Player player) {
        PartyManager.PendingInvite invite = partyManager.getPendingInvite(player.getUniqueId());
        if (invite == null) {
            player.sendMessage("§cYou have no pending island invites.");
            return;
        }

        Island island = getIsland(invite.getInviter(), invite.getDimension());
        if (island == null) {
            player.sendMessage("§cThe island no longer exists or the owner left.");
            partyManager.removePendingInvite(player.getUniqueId());
            return;
        }

        if (island.getMemberCount() >= partyManager.getMaxPartySize()) {
            player.sendMessage("§cThat party is now full.");
            partyManager.removePendingInvite(player.getUniqueId());
            return;
        }

        // Add member with default rank from config
        IslandRank defaultRank = partyManager.getDefaultInviteRank();
        island.addMember(player.getUniqueId(), defaultRank);

        // Persist to database (fixed grid access + dimension as String)
        databaseManager.addIslandMember(
                island.getGridPosition().x(),
                island.getGridPosition().z(),
                invite.getDimension().name(),
                player.getUniqueId(),
                defaultRank.name()
        );

        partyManager.removePendingInvite(player.getUniqueId());

        player.sendMessage("§aYou have joined §e" + Bukkit.getOfflinePlayer(invite.getInviter()).getName() + "'s §aisland party!");
        player.sendMessage("§7Welcome to the team. Use §b/is home §7to teleport to the island.");

        // Notify owner
        Player owner = Bukkit.getPlayer(invite.getInviter());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage("§a" + player.getName() + " §2has accepted your party invite and joined the island!");
        }
    }

    /**
     * Deny a pending invite.
     */
    public void denyPartyInvite(Player player) {
        if (!partyManager.hasPendingInvite(player.getUniqueId())) {
            player.sendMessage("§cYou have no pending invites.");
            return;
        }
        partyManager.removePendingInvite(player.getUniqueId());
        player.sendMessage("§7You declined the island party invite.");
    }

    /**
     * Kick a member from the party (supports all dimensions).
     */
    public void kickMemberFromParty(Player kicker, UUID targetUuid) {
        Island island = getIsland(kicker.getUniqueId(), kicker.getWorld().getEnvironment());
        if (island == null || !island.isOwner(kicker.getUniqueId())) {
            kicker.sendMessage("§cOnly the island owner can kick members.");
            return;
        }

        if (targetUuid.equals(kicker.getUniqueId())) {
            kicker.sendMessage("§cYou cannot kick yourself. Use §b/is leave §cinstead.");
            return;
        }

        if (!island.isMember(targetUuid)) {
            kicker.sendMessage("§cThat player is not in your party.");
            return;
        }

        island.removeMember(targetUuid);
        databaseManager.removeIslandMember(
                island.getGridPosition().x(),
                island.getGridPosition().z(),
                island.getDimension().name(),
                targetUuid
        );

        kicker.sendMessage("§cYou kicked §e" + Bukkit.getOfflinePlayer(targetUuid).getName() + " §cfrom the party.");

        Player kicked = Bukkit.getPlayer(targetUuid);
        if (kicked != null) {
            kicked.sendMessage("§cYou have been kicked from the island party.");
        }
    }

    /**
     * Player leaves the party voluntarily.
     */
    public void leaveParty(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || island.isOwner(player.getUniqueId())) {
            player.sendMessage("§cOwners cannot leave their own island. Use §b/is disband §cinstead.");
            return;
        }

        island.removeMember(player.getUniqueId());
        databaseManager.removeIslandMember(
                island.getGridPosition().x(),
                island.getGridPosition().z(),
                island.getDimension().name(),
                player.getUniqueId()
        );

        player.sendMessage("§7You have left the island party.");
    }

    /**
     * Disband the entire party (owner only). Keeps the owner.
     */
    public void disbandParty(Player owner) {
        Island island = getIsland(owner.getUniqueId(), owner.getWorld().getEnvironment());
        if (island == null || !island.isOwner(owner.getUniqueId())) {
            owner.sendMessage("§cOnly the owner can disband the party.");
            return;
        }

        // Remove all members except owner (use keySet to avoid Map iteration issues)
        for (UUID member : new ArrayList<>(island.getMembers().keySet())) {
            if (!member.equals(owner.getUniqueId())) {
                island.removeMember(member);
                databaseManager.removeIslandMember(
                        island.getGridPosition().x(),
                        island.getGridPosition().z(),
                        island.getDimension().name(),
                        member
                );
            }
        }

        owner.sendMessage("§cYou have disbanded the island party. All other members have been removed.");
    }

    /**
     * Get formatted party info string (for /is party command etc).
     */
    public String getPartyInfo(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return "§cYou don't have an island here.";

        StringBuilder sb = new StringBuilder();
        sb.append("§6=== Island Party ===\n");
        sb.append("§7Owner: §e").append(Bukkit.getOfflinePlayer(island.getOwnerUuid()).getName()).append("\n");
        sb.append("§7Members (").append(island.getMemberCount()).append("/").append(partyManager.getMaxPartySize()).append("):\n");

        for (UUID uuid : island.getMembers().keySet()) {
            IslandRank rank = island.getRank(uuid);
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            sb.append(" §8- §f").append(name).append(" §7[").append(rank.name()).append("]\n");
        }
        return sb.toString();
    }

    // Optional: expose PartyManager if needed by commands
    public PartyManager getPartyManager() {
        return partyManager;
    }
}