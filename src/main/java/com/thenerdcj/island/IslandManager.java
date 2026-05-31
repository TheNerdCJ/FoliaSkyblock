package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.manager.GridManager;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final GridManager gridManager;

    private final Map<UUID, Map<World.Environment, Island>> playerIslands = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>(); // inviter -> target

    /**
     * Smart reverse cache: GridPosition → Island for O(1) lookups in hot paths.
     * This is the primary optimization for protection, ore gen, crop growth, etc.
     * Kept in sync on load/create/reset.
     */
    private final Map<GridPosition, Island> positionToIslandCache = new ConcurrentHashMap<>();

    // Short-lived per-tick cache for extremely hot repeated lookups in the same tick (protection/ore gen spam)
    private final Map<GridPosition, Island> shortLivedTickCache = new ConcurrentHashMap<>();
    private long lastTickCacheClear = 0;

    // Lightweight in-memory hopper counter for HOPPER_LIMIT upgrade enforcement (Tier A)
    private final Map<String, Integer> islandHopperCounts = new ConcurrentHashMap<>();

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.gridManager = plugin.getGridManager();
    }

    public CompletableFuture<Boolean> createIsland(Player player, String biomeName, World.Environment dimension) {
        return gridManager.createPlayerIsland(player.getUniqueId(), dimension)
                .thenCompose(pos -> {
                    if (pos == null) {
                        player.sendMessage("§cNo available plots in this dimension!");
                        return CompletableFuture.completedFuture(false);
                    }

                    String finalBiome = (biomeName != null && !biomeName.isEmpty()) ? biomeName : "PLAINS";
                    Island island = new Island(pos, player.getUniqueId(), finalBiome, dimension);

                    return databaseManager.saveIsland(pos.x(), pos.z(), player.getUniqueId(), dimension.name(), finalBiome)
                            .thenApply(saved -> {
                                if (!saved) {
                                    player.sendMessage("§cFailed to save island data.");
                                    return false;
                                }

                                cacheIsland(player.getUniqueId(), island);

                                boolean isDonor = player.hasPermission("foliasb.donor");

                                // Modern way (no deprecation warning)
                                Biome biome;
                                try {
                                    biome = Registry.BIOME.get(NamespacedKey.minecraft(finalBiome.toLowerCase(Locale.ROOT)));
                                    if (biome == null) biome = Biome.PLAINS;
                                } catch (Exception e) {
                                    biome = Biome.PLAINS;
                                }

                                plugin.getIslandGenerator().generateIsland(island, player, biome, isDonor);

                                player.sendMessage("§a§lIsland created in " + dimension.name() + "!");
                                return true;
                            });
                });
    }

    // ==================== LOADING & CACHING ====================
    public void loadPlayerIslands(Player player) {
        for (World.Environment dim : World.Environment.values()) {
            try {
                // Now returns full Island object (we fixed getIslandByOwner earlier)
                Island island = databaseManager.getIslandByOwner(player.getUniqueId(), dim);

                if (island != null) {
                    // Load upgrades from database
                    String islandKey = island.getId();
                    plugin.getDatabaseManager().loadIslandUpgrades(islandKey).thenAccept(loadedUpgrades -> {
                        island.loadUpgrades(loadedUpgrades);
                    });
                    cacheIsland(player.getUniqueId(), island);

                    // Load minion data and respawn entities (nice-to-have)
                    plugin.getMinionManager().loadMinionDataForIsland(islandKey);
                    plugin.getThreadSafety().runOnMainThread(() -> {
                        plugin.getMinionManager().respawnMinionsForIsland(island);
                    });

                    // Trigger worth recalculation on island load (background)
                    if (plugin.getIslandWorthManager() != null) {
                        plugin.getIslandWorthManager().recalculateAndUpdate(island);
                    }

                    // Load missions for this island (new expanded system)
                    if (plugin.getMissionManager() != null) {
                        plugin.getDatabaseManager().loadMissionsForIsland(islandKey).thenAccept(loadedMissions -> {
                            plugin.getMissionManager().loadMissionsForIsland(islandKey, loadedMissions);
                            // Ensure we have a full set (generate missing dailies/weeklies if needed)
                            plugin.getMissionManager().refreshMissionsForIsland(islandKey, island.getLevel());
                        });
                    }

                    // Load active boosters for the island
                    if (plugin.getBoosterManager() != null) {
                        plugin.getThreadSafety().runOnMainThread(() -> {
                            plugin.getBoosterManager().loadBoostersForIsland(island);
                        });
                    }

                    // Load one-time shop purchases for the island
                    if (plugin.getIslandShopManager() != null) {
                        plugin.getDatabaseManager().loadShopPurchasesForIsland(islandKey).thenAccept(purchased -> {
                            plugin.getIslandShopManager().loadOneTimePurchasesForIsland(islandKey, purchased);
                        });
                    }

                    // Load prestige level for the island
                    if (plugin.getPrestigeManager() != null) {
                        plugin.getDatabaseManager().loadIslandPrestige(islandKey).thenAccept(prestigeLevel -> {
                            plugin.getPrestigeManager().loadPrestigeForIsland(islandKey, prestigeLevel);
                        });
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load island for " + player.getName() + " in " + dim);
            }
        }
    }

    private void cacheIsland(UUID owner, Island island) {
        playerIslands.computeIfAbsent(owner, k -> new EnumMap<>(World.Environment.class))
                .put(island.getDimension(), island);

        // Populate the smart position cache for fast lookups
        positionToIslandCache.put(island.getGridPosition(), island);
    }

    // ==================== GETTERS ====================
    public Island getIsland(UUID owner, World.Environment dimension) {
        Map<World.Environment, Island> map = playerIslands.get(owner);
        return (map != null) ? map.get(dimension) : null;
    }

    /**
     * Checks if a location is within an island's effective (upgraded) build/protection radius.
     * Used for size upgrade effects.
     */
    public boolean isWithinUpgradedIslandArea(Location location) {
        Island island = getIslandAt(location);
        if (island == null) return false;

        Location center = island.getCenter(location.getWorld());
        if (center == null) return false;

        int radius = island.getEffectiveIslandRadius();
        double distance = center.distance(location);
        return distance <= radius;
    }

    /**
     * Returns a snapshot of all currently loaded islands.
     * Used by IslandUpgradeManager for fast non-blocking upgrade lookups.
     */
    public Map<UUID, Island> getAllLoadedIslands() {
        Map<UUID, Island> snapshot = new java.util.HashMap<>();
        playerIslands.forEach((owner, dimMap) -> {
            dimMap.values().forEach(island -> snapshot.put(owner, island));
        });
        return snapshot;
    }

    public Island getIslandAt(Location location) {
        GridPosition pos = gridManager.getGridPosition(location);
        return getIslandByPosition(pos);
    }

    public Island getIslandByPosition(GridPosition position) {
        if (position == null) return null;

        long currentTick = plugin.getServer().getCurrentTick();

        // Clear short-lived cache once per tick
        if (currentTick != lastTickCacheClear) {
            shortLivedTickCache.clear();
            lastTickCacheClear = currentTick;
        }

        // Check short-lived tick cache first (very cheap for repeated lookups in same tick)
        Island cached = shortLivedTickCache.get(position);
        if (cached != null) return cached;

        // Fall back to main smart cache
        Island island = positionToIslandCache.get(position);
        if (island != null) {
            shortLivedTickCache.put(position, island); // populate short cache
        }
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

    // ==================== TELEPORT & HOME ====================
    public void teleportToIsland(Player player, UUID owner) {
        Island island = getIsland(owner, player.getWorld().getEnvironment());
        if (island != null && island.getSpawnLocation() != null) {
            player.teleport(island.getSpawnLocation());
        } else {
            player.sendMessage("§cNo island found in this dimension.");
        }
    }

    public Location getIslandHome(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        return (island != null) ? island.getSpawnLocation() : player.getWorld().getSpawnLocation();
    }

    // ==================== PARTY SYSTEM (Fully Implemented) ====================
    public void inviteToParty(Player inviter, Player target) {
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            inviter.sendMessage("§cYou can't invite yourself.");
            return;
        }

        Island island = getIsland(inviter.getUniqueId(), inviter.getWorld().getEnvironment());
        if (island != null) {
            int maxMembers = 3 + plugin.getIslandUpgradeManager().getUpgradeLevel(island.getId(), com.thenerdcj.island.IslandUpgrade.MEMBER_LIMIT);
            if (island.getMemberCount() >= maxMembers) {
                inviter.sendMessage("§cYou have reached your member limit (" + maxMembers + "). Purchase Member Limit upgrades to invite more.");
                return;
            }
        }

        pendingInvites.put(target.getUniqueId(), inviter.getUniqueId());
        target.sendMessage("§a" + inviter.getName() + " invited you to their island party!");
        target.sendMessage("§eType §b/is accept §eto join or §b/is deny §eto decline.");
    }

    public void acceptPartyInvite(Player player) {
        UUID inviterUuid = pendingInvites.remove(player.getUniqueId());
        if (inviterUuid == null) {
            player.sendMessage("§cYou have no pending invite.");
            return;
        }
        Player inviter = Bukkit.getPlayer(inviterUuid);
        if (inviter == null || !inviter.isOnline()) {
            player.sendMessage("§cInviter is no longer online.");
            return;
        }

        Island island = getIsland(inviterUuid, player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cInviter no longer has an island.");
            return;
        }

        int maxMembers = 3 + plugin.getIslandUpgradeManager().getUpgradeLevel(island.getId(), com.thenerdcj.island.IslandUpgrade.MEMBER_LIMIT);
        if (island.getMemberCount() >= maxMembers) {
            player.sendMessage("§cThis island has reached its member limit.");
            return;
        }

        island.addMember(player.getUniqueId(), IslandRank.GUEST);
        player.sendMessage("§aYou joined " + inviter.getName() + "'s island party!");
        inviter.sendMessage("§a" + player.getName() + " joined your island!");
    }

    public void denyPartyInvite(Player player) {
        pendingInvites.remove(player.getUniqueId());
        player.sendMessage("§7Invite declined.");
    }

    public void kickMemberFromParty(Player kicker, UUID target) {
        Island island = getIsland(kicker.getUniqueId(), kicker.getWorld().getEnvironment());
        if (island == null || !island.isOwner(kicker.getUniqueId())) {
            kicker.sendMessage("§cOnly the owner can kick members.");
            return;
        }
        if (target.equals(kicker.getUniqueId())) {
            kicker.sendMessage("§cYou can't kick yourself.");
            return;
        }
        island.removeMember(target);
        kicker.sendMessage("§aPlayer kicked from the party.");
    }

    public void leaveParty(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return;
        if (island.isOwner(player.getUniqueId())) {
            player.sendMessage("§cOwners cannot leave. Use /is disband instead.");
            return;
        }
        island.removeMember(player.getUniqueId());
        player.sendMessage("§aYou left the island party.");
    }

    public void disbandParty(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) {
            player.sendMessage("§cOnly the owner can disband the party.");
            return;
        }
        // Remove all members except owner
        for (UUID member : new ArrayList<>(island.getMembers().keySet())) {
            if (!member.equals(player.getUniqueId())) {
                island.removeMember(member);
            }
        }
        player.sendMessage("§aIsland party disbanded.");
    }

    // ==================== RESET & COOLDOWN ====================
    public void resetIslandWithBiome(Player player, String biomeName, World.Environment dimension) {
        Island oldIsland = getIsland(player.getUniqueId(), dimension);
        if (oldIsland != null) {
            positionToIslandCache.remove(oldIsland.getGridPosition());
        }

        databaseManager.deleteIsland(player.getUniqueId(), dimension).thenAccept(deleted -> {
            if (deleted) {
                gridManager.deletePlayerIsland(player.getUniqueId(), dimension);
                createIsland(player, biomeName, dimension);
            }
        });
    }

    // Check if player can reset (must wait 12 hours)
    public boolean canReset(Player player) {
        long lastReset = getLastResetTime(player.getUniqueId());
        long cooldownMillis = 12 * 60 * 60 * 1000L; // 12 hours

        return (System.currentTimeMillis() - lastReset) >= cooldownMillis;
    }

    // Returns how many hours are left until player can reset again
    public int getResetCooldownRemainingHours(Player player) {
        long lastReset = getLastResetTime(player.getUniqueId());
        long cooldownMillis = 12 * 60 * 60 * 1000L; // 12 hours

        long timePassed = System.currentTimeMillis() - lastReset;
        long timeLeft = cooldownMillis - timePassed;

        if (timeLeft <= 0) return 0;

        return (int) Math.ceil(timeLeft / (60.0 * 60 * 1000));
    }

    // Internal helper to get last reset time from DB
    private long getLastResetTime(UUID playerUuid) {
        // For now we use a simple in-memory map + DB fallback
        // You can expand this later with a dedicated table
        return databaseManager.getLastResetTime(playerUuid);
    }
    // ==================== EXTRA UTILITY ====================
    public Island getIslandByOwner(UUID uuid, World.Environment dimension) {
        return getIsland(uuid, dimension);
    }
    // Helper: Load real GridPosition from database
    private GridPosition loadGridPositionFromDatabase(UUID owner, World.Environment dimension) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT grid_x, grid_z FROM islands WHERE owner_uuid = ? AND dimension = ?")) {

            ps.setString(1, owner.toString());
            ps.setString(2, dimension.name());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int x = rs.getInt("grid_x");
                int z = rs.getInt("grid_z");
                return new GridPosition(x, z, dimension);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading grid position: " + e.getMessage());
        }
        return null;
    }

    // Helper: Load biome from database
    private String loadBiomeFromDatabase(UUID owner, World.Environment dimension) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT biome FROM islands WHERE owner_uuid = ? AND dimension = ?")) {

            ps.setString(1, owner.toString());
            ps.setString(2, dimension.name());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("biome");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading biome: " + e.getMessage());
        }
        return "PLAINS";
    }

    public GridPosition getGridPosition(UUID ownerUuid, World.Environment dimension) {
        Island island = getIsland(ownerUuid, dimension);
        if (island == null) {
            return null;
        }
        return island.getGridPosition();
    }

    /**
     * Overloaded version that accepts dimension as String (for convenience).
     */
    public GridPosition getGridPosition(UUID ownerUuid, String dimensionName) {
        try {
            World.Environment env = World.Environment.valueOf(dimensionName.toUpperCase());
            return getGridPosition(ownerUuid, env);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Test helper - creates a minimal island for unit testing without full generation.
     * Only for use in tests.
     */
    public Island createIslandForTesting(org.bukkit.entity.Player player, World.Environment dimension, String biome) {
        GridPosition pos = new GridPosition(0, 0, dimension);
        Island island = new Island(pos, player.getUniqueId(), biome, dimension);
        cacheIsland(player.getUniqueId(), island);
        return island;
    }

    // ==================== HOPPER LIMIT HELPERS (Tier A) ====================

    public String getIslandIdForHopperCount(Island island) {
        if (island == null) return null;
        return island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
    }

    public int getCurrentHopperCount(String islandId) {
        return islandHopperCounts.getOrDefault(islandId, 0);
    }

    public void incrementHopperCount(String islandId) {
        if (islandId == null) return;
        islandHopperCounts.merge(islandId, 1, Integer::sum);
    }

    public void decrementHopperCount(String islandId) {
        if (islandId == null) return;
        islandHopperCounts.computeIfPresent(islandId, (k, v) -> Math.max(0, v - 1));
    }

    /** Call when an island is deleted/reset to clean up counters */
    public void clearHopperCount(String islandId) {
        if (islandId != null) {
            islandHopperCounts.remove(islandId);
        }
    }
}