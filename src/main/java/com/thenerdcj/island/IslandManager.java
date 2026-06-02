package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.manager.GridManager;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final GridManager gridManager;

    private final Map<UUID, Map<World.Environment, Island>> playerIslands = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();

    private final Map<GridPosition, Island> positionToIslandCache = new ConcurrentHashMap<>();
    private final Map<GridPosition, Island> shortLivedTickCache = new ConcurrentHashMap<>();
    private long lastTickCacheClear = 0;

    private final Map<String, Integer> islandHopperCounts = new ConcurrentHashMap<>();

    // Combat tracking for reset safety
    private final Map<UUID, Long> lastCombatTime = new ConcurrentHashMap<>();

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.gridManager = plugin.getGridManager();
    }

    // ==================== ISLAND CREATION ====================
    public CompletableFuture<Boolean> createIsland(Player player, String biomeName, World.Environment dimension) {
        return gridManager.createPlayerIsland(player.getUniqueId(), dimension)
                .thenCompose(pos -> {
                    if (pos == null) {
                        MessageUtil.sendMessage(player, "§cNo available plots in this dimension!");
                        return CompletableFuture.completedFuture(false);
                    }

                    String finalBiome = (biomeName != null && !biomeName.isEmpty()) ? biomeName : "PLAINS";
                    Island island = new Island(pos, player.getUniqueId(), finalBiome, dimension);

                    return databaseManager.saveIsland(pos.x(), pos.z(), player.getUniqueId(), dimension.name(), finalBiome)
                            .thenApply(saved -> {
                                if (!saved) {
                                    MessageUtil.sendMessage(player, "§cFailed to save island data.");
                                    return false;
                                }

                                cacheIsland(player.getUniqueId(), island);

                                boolean isDonor = player.hasPermission("foliasb.donor");

                                Biome biome;
                                try {
                                    biome = Registry.BIOME.get(NamespacedKey.minecraft(finalBiome.toLowerCase(Locale.ROOT)));
                                    if (biome == null) biome = Biome.PLAINS;
                                } catch (Exception e) {
                                    biome = Biome.PLAINS;
                                }

                                // Use RegionScheduler for generation when possible
                                runGenerationOnRegionScheduler(player, island, biome, isDonor);

                                MessageUtil.sendMessage(player, "§a§lIsland created in " + dimension.name() + "!");
                                return true;
                            });
                });
    }

    // ==================== LOADING & CACHING ====================
    public void loadPlayerIslands(Player player) {
        for (World.Environment dim : World.Environment.values()) {
            try {
                Island island = databaseManager.getIslandByOwner(player.getUniqueId(), dim);
                if (island != null) {
                    String islandKey = island.getId();
                    plugin.getDatabaseManager().loadIslandUpgrades(islandKey).thenAccept(island::loadUpgrades);
                    cacheIsland(player.getUniqueId(), island);

                    plugin.getMinionManager().loadMinionDataForIsland(islandKey);
                    plugin.getThreadSafety().runOnMainThread(() ->
                            plugin.getMinionManager().respawnMinionsForIsland(island));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load island for " + player.getName() + " in " + dim);
            }
        }
    }

    private void cacheIsland(UUID owner, Island island) {
        playerIslands.computeIfAbsent(owner, k -> new EnumMap<>(World.Environment.class))
                .put(island.getDimension(), island);
        positionToIslandCache.put(island.getGridPosition(), island);
    }

    // ==================== GETTERS ====================
    public Island getIsland(UUID owner, World.Environment dimension) {
        Map<World.Environment, Island> map = playerIslands.get(owner);
        return (map != null) ? map.get(dimension) : null;
    }

    public boolean isWithinUpgradedIslandArea(Location location) {
        Island island = getIslandAt(location);
        if (island == null) return false;

        Location center = island.getCenter(location.getWorld());
        return center != null && center.distance(location) <= island.getEffectiveIslandRadius();
    }

    public Map<UUID, Island> getAllLoadedIslands() {
        Map<UUID, Island> snapshot = new HashMap<>();
        playerIslands.forEach((owner, dimMap) -> dimMap.values().forEach(island -> snapshot.put(owner, island)));
        return snapshot;
    }

    public Island getIslandAt(Location location) {
        GridPosition pos = gridManager.getGridPosition(location);
        return getIslandByPosition(pos);
    }

    public Island getIslandByPosition(GridPosition position) {
        if (position == null) return null;

        long currentTick = plugin.getServer().getCurrentTick();
        if (currentTick != lastTickCacheClear) {
            shortLivedTickCache.clear();
            lastTickCacheClear = currentTick;
        }

        Island cached = shortLivedTickCache.get(position);
        if (cached != null) return cached;

        Island island = positionToIslandCache.get(position);
        if (island != null) shortLivedTickCache.put(position, island);

        return island;
    }

    public boolean hasIsland(UUID uuid, World.Environment dimension) {
        return getIsland(uuid, dimension) != null;
    }

    // ==================== XP SYSTEM ====================
    public void addIslandXp(Player player, int xp) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island != null) {
            island.addXp(xp, island.getOnlineMemberCount());
        }
    }

    // ==================== TELEPORT ====================
    public void teleportToIsland(Player player, UUID owner) {
        Island island = getIsland(owner, player.getWorld().getEnvironment());
        if (island != null && island.getSpawnLocation() != null) {
            player.teleport(island.getSpawnLocation());
        } else {
            MessageUtil.sendMessage(player, "§cNo island found in this dimension.");
        }
    }

    public Location getIslandHome(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        return (island != null) ? island.getSpawnLocation() : player.getWorld().getSpawnLocation();
    }

    // ==================== PARTY SYSTEM ====================
    public void inviteToParty(Player inviter, Player target) {
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            MessageUtil.sendMessage(inviter, "§cYou can't invite yourself.");
            return;
        }

        Island island = getIsland(inviter.getUniqueId(), inviter.getWorld().getEnvironment());
        if (island != null) {
            int maxMembers = 3 + plugin.getIslandUpgradeManager().getUpgradeLevel(island.getId(), IslandUpgrade.MEMBER_LIMIT);
            if (island.getMemberCount() >= maxMembers) {
                MessageUtil.sendMessage(inviter, "§cYou have reached your member limit (" + maxMembers + ").");
                return;
            }
        }

        pendingInvites.put(target.getUniqueId(), inviter.getUniqueId());
        MessageUtil.sendMessage(target, "§a" + inviter.getName() + " invited you to their island party!");
        MessageUtil.sendMessage(target, "§eType §b/is accept §eto join.");
    }

    public void acceptPartyInvite(Player player) {
        UUID inviterUuid = pendingInvites.remove(player.getUniqueId());
        if (inviterUuid == null) {
            MessageUtil.sendMessage(player, "§cYou have no pending invite.");
            return;
        }

        Player inviter = Bukkit.getPlayer(inviterUuid);
        if (inviter == null || !inviter.isOnline()) {
            MessageUtil.sendMessage(player, "§cInviter is no longer online.");
            return;
        }

        Island island = getIsland(inviterUuid, player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cInviter no longer has an island.");
            return;
        }

        int maxMembers = 3 + plugin.getIslandUpgradeManager().getUpgradeLevel(island.getId(), IslandUpgrade.MEMBER_LIMIT);
        if (island.getMemberCount() >= maxMembers) {
            MessageUtil.sendMessage(player, "§cThis island has reached its member limit.");
            return;
        }

        island.addMember(player.getUniqueId(), IslandRank.GUEST);
        MessageUtil.sendMessage(player, "§aYou joined " + inviter.getName() + "'s island party!");
        MessageUtil.sendMessage(inviter, "§a" + player.getName() + " joined your island!");
    }

    public void denyPartyInvite(Player player) {
        pendingInvites.remove(player.getUniqueId());
        MessageUtil.sendMessage(player, "§7Invite declined.");
    }

    public void kickMemberFromParty(Player kicker, UUID target) {
        Island island = getIsland(kicker.getUniqueId(), kicker.getWorld().getEnvironment());
        if (island == null || !island.isOwner(kicker.getUniqueId())) {
            MessageUtil.sendMessage(kicker, "§cOnly the owner can kick members.");
            return;
        }
        if (target.equals(kicker.getUniqueId())) {
            MessageUtil.sendMessage(kicker, "§cYou can't kick yourself.");
            return;
        }
        island.removeMember(target);
        MessageUtil.sendMessage(kicker, "§aPlayer kicked from the party.");
    }

    public void leaveParty(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || island.isOwner(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "§cOwners cannot leave. Use /is disband instead.");
            return;
        }
        island.removeMember(player.getUniqueId());
        MessageUtil.sendMessage(player, "§aYou left the island party.");
    }

    public void disbandParty(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "§cOnly the owner can disband the party.");
            return;
        }
        for (UUID member : new ArrayList<>(island.getMembers().keySet())) {
            if (!member.equals(player.getUniqueId())) {
                island.removeMember(member);
            }
        }
        MessageUtil.sendMessage(player, "§aIsland party disbanded.");
    }

    // ==================== PER-DIMENSION RESET WITH SAFETY CHECKS ====================

    /**
     * Resets only the specified dimension with strong safety checks.
     * This is the main method called from BiomeSelectionGUI after confirmation.
     */
    public void resetIslandWithBiome(Player player, String biomeName, World.Environment dimension) {
        // === SAFETY CHECKS ===

        if (!canReset(player)) {
            int hours = getResetCooldownRemainingHours(player);
            MessageUtil.sendMessage(player, "§cYou must wait §e" + hours + " more hours§c before resetting again.");
            return;
        }

        Island island = getIsland(player.getUniqueId(), dimension);
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension to reset.");
            return;
        }

        // Combat protection
        if (isPlayerInCombat(player)) {
            MessageUtil.sendMessage(player, "§cYou cannot reset while in combat! Please wait a few seconds.");
            return;
        }

        // Boss protection
        if (hasActiveBossOnDimension(player.getUniqueId(), dimension)) {
            MessageUtil.sendMessage(player, "§c§lYou cannot reset this dimension while a boss is active!");
            MessageUtil.sendMessage(player, "§7Please defeat the boss or wait for it to despawn.");
            return;
        }

        // Party warning
        if (island.getMemberCount() > 1) {
            MessageUtil.sendMessage(player, "§c§lWARNING: §e" + (island.getMemberCount() - 1) +
                    " party member(s) will lose access to this dimension!");
            MessageUtil.sendMessage(player, "§7They will need to be re-invited after the reset.");
        }

        // === EXECUTE RESET ===

        // Clean up caches
        if (island.getGridPosition() != null) {
            positionToIslandCache.remove(island.getGridPosition());
        }
        playerIslands.getOrDefault(player.getUniqueId(), Collections.emptyMap()).remove(dimension);

        // Delete old dimension data
        databaseManager.deleteIsland(player.getUniqueId(), dimension).thenAccept(deleted -> {
            if (deleted) {
                gridManager.deletePlayerIsland(player.getUniqueId(), dimension);

                // Create new island (generation uses RegionScheduler)
                createIsland(player, biomeName, dimension).thenAccept(success -> {
                    if (success) {
                        databaseManager.recordIslandReset(player.getUniqueId(), dimension);

                        plugin.getThreadSafety().sendMessageSafely(player,
                                "§a§lYour " + dimension.name() + " island has been successfully reset!");
                    }
                });
            } else {
                plugin.getThreadSafety().sendMessageSafely(player,
                        "§cFailed to reset island. Please try again or contact staff.");
            }
        });
    }

    // ==================== SAFETY HELPER METHODS ====================

    public boolean isPlayerInCombat(Player player) {
        Long lastHit = lastCombatTime.get(player.getUniqueId());
        if (lastHit == null) return false;
        return (System.currentTimeMillis() - lastHit) < 15000; // 15-second combat tag
    }

    /** Call this from your CombatListener when a player takes or deals damage */
    public void markPlayerInCombat(UUID uuid) {
        lastCombatTime.put(uuid, System.currentTimeMillis());
    }

    /**
     * Returns whether the player currently has an active (undefeated) boss event
     * in the specified dimension. Used as a safety gate before allowing dimension reset.
     */
    public boolean hasActiveBossOnDimension(UUID owner, World.Environment dimension) {
        if (plugin.getBossManager() == null) return false;

        Island island = getIsland(owner, dimension);
        if (island == null) return false;

        return plugin.getBossManager().hasActiveBossOnIsland(island.getId());
    }
    /**
     * Returns remaining cooldown time in minutes.
     * More precise for short cooldowns.
     */
    public int getResetCooldownRemainingMinutes(Player player) {
        long lastReset = getLastResetTime(player.getUniqueId());
        long cooldownMillis = 12 * 60 * 60 * 1000L; // 12 hours
        long timeLeft = cooldownMillis - (System.currentTimeMillis() - lastReset);

        if (timeLeft <= 0) return 0;

        return (int) Math.ceil(timeLeft / (60.0 * 1000));
    }

    // ==================== RESET COOLDOWN ====================
    public boolean canReset(Player player) {
        long lastReset = getLastResetTime(player.getUniqueId());
        long cooldownMillis = 12 * 60 * 60 * 1000L;
        return (System.currentTimeMillis() - lastReset) >= cooldownMillis;
    }

    // Per-dimension version for safety in reset flow
    public boolean canReset(Player player, World.Environment dimension) {
        long lastReset = databaseManager.getLastDimensionReset(player.getUniqueId(), dimension);
        if (lastReset == 0) {
            lastReset = getLastResetTime(player.getUniqueId());
        }
        long cooldownMillis = 12 * 60 * 60 * 1000L;
        return (System.currentTimeMillis() - lastReset) >= cooldownMillis;
    }

    public int getResetCooldownRemainingHours(Player player) {
        long lastReset = getLastResetTime(player.getUniqueId());
        long cooldownMillis = 12 * 60 * 60 * 1000L;
        long timeLeft = cooldownMillis - (System.currentTimeMillis() - lastReset);
        return timeLeft <= 0 ? 0 : (int) Math.ceil(timeLeft / (60.0 * 60 * 1000));
    }

    // Per-dimension version for reset GUI and safety
    public int getResetCooldownRemainingHours(Player player, World.Environment dimension) {
        long lastReset = databaseManager.getLastDimensionReset(player.getUniqueId(), dimension);
        if (lastReset == 0) {
            lastReset = getLastResetTime(player.getUniqueId()); // fallback to global
        }
        long cooldownMillis = 12 * 60 * 60 * 1000L;
        long timeLeft = cooldownMillis - (System.currentTimeMillis() - lastReset);
        return timeLeft <= 0 ? 0 : (int) Math.ceil(timeLeft / (60.0 * 60 * 1000));
    }

    /**
     * Returns the remaining cooldown (in hours) for resetting the specific dimension.
     * Uses the dedicated per-dimension tracking table when available.
     */
    public int getDimensionResetCooldownRemainingHours(Player player, World.Environment dimension) {
        long lastReset = databaseManager.getLastDimensionReset(player.getUniqueId(), dimension);
        if (lastReset == 0) {
            lastReset = getLastResetTime(player.getUniqueId()); // fallback
        }
        long cooldownMillis = 12 * 60 * 60 * 1000L;
        long timeLeft = cooldownMillis - (System.currentTimeMillis() - lastReset);
        return timeLeft <= 0 ? 0 : (int) Math.ceil(timeLeft / (60.0 * 60 * 1000));
    }

    private long getLastResetTime(UUID playerUuid) {
        return databaseManager.getLastResetTime(playerUuid);
    }

    // ==================== GENERATION SCHEDULER ====================
    private void runGenerationOnRegionScheduler(Player player, Island island, Biome biome, boolean isDonor) {
        World world = plugin.getSkyblockWorld(island.getDimension());
        if (world == null) {
            plugin.getIslandGenerator().generateIsland(island, player, biome, isDonor);
            return;
        }

        plugin.getServer().getRegionScheduler().execute(plugin, world,
                island.getGridPosition().getX(), island.getGridPosition().getZ(), () -> {
                    plugin.getIslandGenerator().generateIsland(island, player, biome, isDonor);
                });
    }

    // ==================== UTILITY ====================
    public Island getIslandByOwner(UUID uuid, World.Environment dimension) {
        return getIsland(uuid, dimension);
    }

    public GridPosition getGridPosition(UUID ownerUuid, World.Environment dimension) {
        Island island = getIsland(ownerUuid, dimension);
        return island != null ? island.getGridPosition() : null;
    }

    public GridPosition getGridPosition(UUID ownerUuid, String dimensionName) {
        try {
            return getGridPosition(ownerUuid, World.Environment.valueOf(dimensionName.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Island createIslandForTesting(Player player, World.Environment dimension, String biome) {
        GridPosition pos = new GridPosition(0, 0, dimension);
        Island island = new Island(pos, player.getUniqueId(), biome, dimension);
        cacheIsland(player.getUniqueId(), island);
        return island;
    }

    // ==================== HOPPER LIMIT HELPERS ====================
    public String getIslandIdForHopperCount(Island island) {
        if (island == null) return null;
        return island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
    }

    public int getCurrentHopperCount(String islandId) {
        return islandHopperCounts.getOrDefault(islandId, 0);
    }

    public void incrementHopperCount(String islandId) {
        if (islandId != null) islandHopperCounts.merge(islandId, 1, Integer::sum);
    }

    public void decrementHopperCount(String islandId) {
        if (islandId != null) islandHopperCounts.computeIfPresent(islandId, (k, v) -> Math.max(0, v - 1));
    }

    public void clearHopperCount(String islandId) {
        if (islandId != null) islandHopperCounts.remove(islandId);
    }
}