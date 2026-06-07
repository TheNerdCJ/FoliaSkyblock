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
import java.util.concurrent.ThreadLocalRandom;

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

    // Bounded for large scale (caches, hoppers cap for perf)
    private static final int MAX_CACHE_SIZE = 2000; // for position caches
    private static final int MAX_HOPPERS_PER_ISLAND = 100; // cap example for hoppers (large scale sink/perf)

    // Task 1: Dimension level gates (enforced before grid alloc to prevent waste) + party XP config (PtW fair)
    private int netherLevelReq = 15;
    private int endLevelReq = 30;
    private final Map<Integer, Double> partyXpMultipliers = new HashMap<>();

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.gridManager = plugin.getGridManager();
        loadDimensionAndPartyConfig(); // Task 1: load gates + party multipliers for configurable PtW balancing + Folia-safe enforcement
        // Periodic cleanup for caches/CHM compression + hopper cap enforcement note
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCaches, 20L * 60 * 5, 20L * 60 * 5);
    }

    private void loadDimensionAndPartyConfig() {
        var c = plugin.getConfig();
        netherLevelReq = c.getInt("island.dimension_requirements.nether", 15);
        endLevelReq = c.getInt("island.dimension_requirements.end", 30);
        partyXpMultipliers.clear();
        if (c.isConfigurationSection("island.party.xp-multipliers")) {
            for (String key : c.getConfigurationSection("island.party.xp-multipliers").getKeys(false)) {
                try {
                    int ps = Integer.parseInt(key);
                    double mult = c.getDouble("island.party.xp-multipliers." + key, 1.0);
                    partyXpMultipliers.put(ps, mult);
                } catch (Exception ignored) {}
            }
        }
        if (partyXpMultipliers.isEmpty()) {
            partyXpMultipliers.put(1, 1.0); partyXpMultipliers.put(2, 0.85);
            partyXpMultipliers.put(3, 0.75); partyXpMultipliers.put(4, 0.65);
            partyXpMultipliers.put(5, 0.60);
        }
        // Push to Island for calculateXpMultiplier (and skill) - static for data class, server-side only (PtW + security: no player input affects mult)
        Island.setPartyXpMultipliers(partyXpMultipliers);
    }

    public double getPartyXpMultiplier(int partySize) {
        if (partySize <= 0) partySize = 1;
        return partyXpMultipliers.getOrDefault(partySize, Math.max(0.55, 1.0 - (partySize - 1) * 0.12));
    }

    public int getDimensionLevelRequirement(World.Environment dim) {
        if (dim == World.Environment.NETHER) return netherLevelReq;
        if (dim == World.Environment.THE_END) return endLevelReq;
        return 0;
    }

    // ==================== ISLAND CREATION ====================
    public CompletableFuture<Boolean> createIsland(Player player, String biomeName, World.Environment dimension) {
        return createIsland(player, biomeName, dimension, false, false);
    }

    /**
     * Creates (or recreates on reset) an island.
     * If donorRerollPersonality is true, a fresh random generation seed is assigned so the island
     * receives a new "personality" (different archetype, terrain shape, tree styles, landmarks).
     * Only honored for actual donors. Purely cosmetic.
     */
    public CompletableFuture<Boolean> createIsland(Player player, String biomeName, World.Environment dimension, boolean donorRerollPersonality) {
        return createIsland(player, biomeName, dimension, donorRerollPersonality, false);
    }

    /**
     * Internal creation used by prestige rebirth (and potentially other admin/force paths).
     * When bypassDimensionGates=true, the Nether/End level requirement checks against main island are skipped.
     * This is required when rebirthing all of a player's dimensions together during a prestige action
     * (the "main" is also being reset to level 1 at the same moment).
     */
    CompletableFuture<Boolean> createIsland(Player player, String biomeName, World.Environment dimension,
                                            boolean donorRerollPersonality, boolean bypassDimensionGates) {
        if (plugin.getSeasonManager() != null && plugin.getSeasonManager().isResetInProgress()) {
            plugin.getThreadSafety().sendMessageSafely(player, "§cSeasonal reset in progress. Please wait a moment and try again.");
            return CompletableFuture.completedFuture(false);
        }
        // === TASK 1: ENFORCE DIM LEVEL GATES (before allocating grid position - prevents waste/leaks) ===
        // Uses main island XP level (from skills/play, not worth). Config driven. Play-to-Win: cannot buy/skip, must grind main.
        // Cross-refs: Island.hasUnlocked* (soft), BiomeSelectionGUI (display), IslandCommand (pre-check), Island (level/xp).
        // The bypass is only used for prestige rebirth flows where we are resetting the main + aux dims together.
        if (dimension != World.Environment.NORMAL && !bypassDimensionGates) {
            int mainLvl = getMainIslandLevel(player.getUniqueId());
            int req = getDimensionLevelRequirement(dimension);
            if (mainLvl < req) {
                MessageUtil.sendMessage(player, "§cYou need main island level §e" + req + "+ §cto create a " + dimension.name() + " island (your main: " + mainLvl + ").");
                MessageUtil.sendMessage(player, "§7Progress via skills, quests, combat, building on your Overworld island.");
                return CompletableFuture.completedFuture(false);
            }
        }

        return gridManager.createPlayerIsland(player.getUniqueId(), dimension)
                .thenCompose(pos -> {
                    if (pos == null) {
                        MessageUtil.sendMessage(player, "§cNo available plots in this dimension!");
                        return CompletableFuture.completedFuture(false);
                    }

                    String finalBiome = (biomeName != null && !biomeName.isEmpty()) ? biomeName : "PLAINS";
                    Island island = new Island(pos, player.getUniqueId(), finalBiome, dimension);

                    // Determine generation seed for personality
                    long genSeed = 0L;
                    boolean isDonor = player.hasPermission("foliasb.donor") || player.hasPermission("foliasb.donor.biome");
                    if (donorRerollPersonality && isDonor) {
                        genSeed = ThreadLocalRandom.current().nextLong();
                        // Ensure non-zero so it is treated as intentional
                        if (genSeed == 0) genSeed = 0x123456789ABCDEFL;
                        island.setGenerationSeed(genSeed);
                    }

                    final long finalGenSeed = genSeed;

                    return databaseManager.saveIsland(pos.x(), pos.z(), player.getUniqueId(), dimension.name(), finalBiome, finalGenSeed)
                            .thenApply(saved -> {
                                if (!saved) {
                                    MessageUtil.sendMessage(player, "§cFailed to save island data.");
                                    return false;
                                }

                                cacheIsland(player.getUniqueId(), island);

                                // Init persisted member_count snapshot (0 for new owner-only island)
                                if (databaseManager.getIslandDAO() != null) {
                                    databaseManager.getIslandDAO().saveIslandMemberCount(pos, 0);
                                }

                                // Task 1: mark unlocked for this dim now that gate passed (for hasUnlocked* queries + persistence roundtrips)
                                if (dimension != World.Environment.NORMAL) {
                                    island.unlockDimension(dimension.name());
                                }

                                Biome biome;
                                try {
                                    biome = Registry.BIOME.get(NamespacedKey.minecraft(finalBiome.toLowerCase(Locale.ROOT)));
                                    if (biome == null) biome = Biome.PLAINS;
                                } catch (Exception e) {
                                    biome = Biome.PLAINS;
                                }

                                // Early game / onboarding: seed first-island quests immediately (in-mem, lightweight)
                                if (plugin.getQuestManager() != null) {
                                    plugin.getQuestManager().generateOnboardingQuests(island.getId());
                                    plugin.getQuestManager().generateDailyQuests(island.getId());
                                    plugin.getQuestManager().generateWeeklyQuests(island.getId());
                                }

                                runGenerationOnRegionScheduler(player, island, biome, isDonor);

                                final Island created = island;
                                final Player onlinePlayer = player;
                                // Ensure any pre-existing prestige (from previous run) is loaded for the stable key
                                if (plugin.getPrestigeManager() != null) {
                                    final String pKey = created.getId();
                                    plugin.getDatabaseManager().loadIslandPrestige(pKey).thenAccept(lvl -> {
                                        plugin.getPrestigeManager().loadPrestigeForIsland(pKey, lvl);
                                    });
                                }
                                plugin.getThreadSafety().runOnMainThread(() -> {
                                    if (!onlinePlayer.isOnline()) return;
                                    MessageUtil.sendMessage(onlinePlayer, "§a§lIsland created in " + dimension.name() + "!");
                                    MessageUtil.sendMessage(onlinePlayer, "§7Complete your §b/quests §7to earn rewards and unlock a free starter cosmetic trail!");
                                    teleportToIslandCenterImmediate(onlinePlayer, created);
                                });
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

                    // Museum persist load for full feature (task continuation)
                    if (plugin.getMuseumManager() != null) {
                        plugin.getMuseumManager().loadForIsland(islandKey);
                    }

                    // Island Furniture / Housing decor - ensure visuals
                    if (plugin.getIslandFurnitureManager() != null) {
                        plugin.getIslandFurnitureManager().respawnAllFurnitureForIsland(islandKey, island.getCenter(null) != null ? island.getCenter(null).getWorld() : null);
                    }

                    // Island Music & Ambience
                    if (plugin.getIslandMusicManager() != null) {
                        plugin.getIslandMusicManager().loadActiveForIsland(islandKey);
                        plugin.getIslandMusicManager().onPlayerEnterIsland(player, island);
                    }

                    // Island Structures (use respawn for consistency with furniture + full DB load inside)
                    if (plugin.getIslandStructureManager() != null) {
                        plugin.getIslandStructureManager().respawnAllStructuresForIsland(islandKey, island.getCenter(null) != null ? island.getCenter(null).getWorld() : null);
                    }

                    // Island Weather Cosmetics
                    if (plugin.getIslandWeatherCosmeticManager() != null) {
                        plugin.getIslandWeatherCosmeticManager().loadActiveForIsland(islandKey);
                        plugin.getIslandWeatherCosmeticManager().onPlayerEnterIsland(player, island);
                    }

                    // Core Collections (per-island unique discovery tracking)
                    if (plugin.getCollectionManager() != null) {
                        plugin.getCollectionManager().loadForIsland(islandKey);
                    }

                    // Prestige (stable owner+dim key) — load into PrestigeManager cache so getPrestigeLevel + getEffectiveLevel work immediately
                    if (plugin.getPrestigeManager() != null) {
                        final String pKey = island.getId();
                        plugin.getDatabaseManager().loadIslandPrestige(pKey).thenAccept(lvl -> {
                            plugin.getPrestigeManager().loadPrestigeForIsland(pKey, lvl);
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
        positionToIslandCache.put(island.getGridPosition(), island);
    }

    // ==================== GETTERS ====================

    /**
     * Returns the player's island for a dimension (memory first, then database).
     */
    public Island getIsland(UUID owner, World.Environment dimension) {
        if (owner == null || dimension == null) return null;

        Map<World.Environment, Island> map = playerIslands.get(owner);
        if (map != null) {
            Island cached = map.get(dimension);
            if (cached != null) {
                return cached;
            }
        }

        Island fromDb = databaseManager.getIslandByOwner(owner, dimension);
        if (fromDb != null) {
            cacheIsland(owner, fromDb);
        }
        return fromDb;
    }

    /** Resolves which island a command should target (current dimension, then overworld fallback). */
    public Island getIslandForPlayer(Player player) {
        if (player == null) return null;
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            island = getIsland(player.getUniqueId(), World.Environment.NORMAL);
        }
        return island;
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

    // Task batch: DB-paginated tops exposed for PAPI expansion and large server leaderboards (replaces pure cache).
    // Calls IslandDAO (via db). Folia async. Used by PAPI for %top_level_N% etc.
    public java.util.concurrent.CompletableFuture<java.util.List<com.thenerdcj.database.TopIslandEntry>> getTopIslandsByLevel(int limit, int offset) {
        return databaseManager.getIslandDAO().getTopIslandsByLevel(limit, offset);
    }

    public java.util.concurrent.CompletableFuture<java.util.List<com.thenerdcj.database.TopIslandEntry>> getTopIslandsByWorth(int limit, int offset) {
        return databaseManager.getIslandDAO().getTopIslandsByWorth(limit, offset);
    }

    public java.util.concurrent.CompletableFuture<java.util.List<com.thenerdcj.database.TopIslandEntry>> getTopIslandsByMemberCount(int limit, int offset) {
        return databaseManager.getIslandDAO().getTopIslandsByMemberCount(limit, offset);
    }

    // Efficient my-rank queries (persistence-backed COUNT on worth/levels tables, no full tops materialization).
    // For PAPI my_xxx_rank and /is rank. Global ranks (matches tops leaderboards).
    public java.util.concurrent.CompletableFuture<Integer> getMyWorthRank(GridPosition pos) {
        return databaseManager.getIslandDAO().getMyWorthRank(pos);
    }
    public java.util.concurrent.CompletableFuture<Integer> getMyLevelRank(GridPosition pos) {
        return databaseManager.getIslandDAO().getMyLevelRank(pos);
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

    /**
     * Resolves the location players should warp to (custom /is sethome or island grid center).
     */
    public Location getIslandHomeLocation(Island island) {
        if (island == null) return null;
        Location custom = island.getSpawnLocation();
        if (custom != null && custom.getWorld() != null) {
            return custom.clone();
        }
        World world = plugin.getSkyblockWorld(island.getDimension());
        if (world == null) return null;
        Location center = island.getCenter(world);
        return center != null ? center.clone() : null;
    }

    /**
     * Returns a best-effort home location without reading blocks (Folia-safe from any thread).
     * For accurate terrain-aware placement use {@link #teleportToIslandHome}.
     */
    public Location getSafeIslandHomeLocation(Island island) {
        Location base = getIslandHomeLocation(island);
        return base != null ? toStandingLocation(base) : null;
    }

    /** Must run on the region thread that owns {@code anchor}. */
    public Location resolveSafeStandingOnRegionThread(Location anchor) {
        if (anchor == null || anchor.getWorld() == null) return null;
        Location found = IslandSpawnFinder.findNearCenter(anchor);
        if (found == null) {
            return toStandingLocation(anchor);
        }
        found.setPitch(anchor.getPitch());
        found.setYaw(anchor.getYaw());
        return found;
    }

    private Location resolveSpawnOnRegionThread(Island island, World world) {
        Location custom = island.getSpawnLocation();
        if (custom != null && custom.getWorld() == world) {
            return resolveSafeStandingOnRegionThread(custom);
        }
        Location center = island.getCenter(world);
        return center != null ? resolveSafeStandingOnRegionThread(center) : null;
    }

    private static Location toStandingLocation(Location anchor) {
        Location loc = anchor.clone();
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);
        if (loc.getY() == Math.floor(loc.getY())) {
            loc.setY(loc.getY() + 1.0);
        }
        return loc;
    }

    /** Warp to grid center before terrain exists; refined after generation on the island region thread. */
    public void teleportToIslandCenterImmediate(Player player, Island island) {
        if (player == null || island == null || !player.isOnline()) return;
        World world = plugin.getSkyblockWorld(island.getDimension());
        if (world == null) {
            MessageUtil.sendMessage(player, "§cCould not find your island world. Ask staff if this persists.");
            return;
        }
        Location center = island.getCenter(world);
        if (center == null) {
            MessageUtil.sendMessage(player, "§cCould not resolve your island location.");
            return;
        }
        teleportPlayerToIslandSpawn(player, island, toStandingLocation(center), true);
    }

    public void teleportToIsland(Player player, UUID owner) {
        teleportToIsland(player, owner, player.getWorld().getEnvironment());
    }

    public void teleportToIsland(Player player, UUID owner, World.Environment dimension) {
        Island island = getIsland(owner, dimension);
        if (island == null && owner.equals(player.getUniqueId())) {
            island = getIsland(owner, World.Environment.NORMAL);
        }
        if (island == null) {
            MessageUtil.sendMessage(player, "§cNo island found in this dimension. Use §b/is create §cif you have not claimed one yet.");
            return;
        }
        teleportToIslandHome(player, island);
    }

    public void teleportToIslandHome(Player player, Island island) {
        if (player == null || island == null) return;
        if (!player.isOnline()) return;

        World world = plugin.getSkyblockWorld(island.getDimension());
        if (world == null) {
            MessageUtil.sendMessage(player, "§cCould not find your island world. Ask staff if this persists.");
            plugin.getLogger().warning("[IslandManager] Teleport failed for " + player.getName()
                    + " — world missing for dimension " + island.getDimension());
            return;
        }

        Location anchor = island.getSpawnLocation();
        if (anchor == null || anchor.getWorld() == null || !anchor.getWorld().equals(world)) {
            anchor = island.getCenter(world);
        }
        if (anchor == null) {
            MessageUtil.sendMessage(player, "§cCould not resolve your island spawn. Try again in a moment.");
            return;
        }

        final Location regionAnchor = anchor.clone();
        plugin.getThreadSafety().runAtLocation(regionAnchor, () -> {
            Location dest = resolveSpawnOnRegionThread(island, world);
            if (dest == null || dest.getWorld() == null) {
                plugin.getThreadSafety().runForEntity(player, () ->
                        MessageUtil.sendMessage(player, "§cCould not resolve your island spawn. Try again in a moment."));
                return;
            }
            teleportPlayerToIslandSpawn(player, island, dest, true);
        });
    }

    /**
     * Folia-safe cross-region teleport (player entity thread; {@link Player#teleportAsync} loads destination chunks).
     */
    public void teleportPlayerToIslandSpawn(Player player, Island island, Location dest, boolean announce) {
        if (player == null || dest == null || dest.getWorld() == null) {
            return;
        }

        // If teleporting to global spawn (island=null), clear any island border immediately to prevent red hue at destination
        if (island == null && plugin.getBorderVisualManager() != null && player.isOnline()) {
            plugin.getBorderVisualManager().clearPlayerWorldBorder(player);
        }

        final Location target = dest.clone();
        target.setX(target.getBlockX() + 0.5);
        target.setZ(target.getBlockZ() + 0.5);
        if (Float.isNaN(target.getPitch())) {
            target.setPitch(player.getLocation().getPitch());
        }
        if (Float.isNaN(target.getYaw())) {
            target.setYaw(player.getLocation().getYaw());
        }

        plugin.getThreadSafety().runForEntity(player, () -> {
            if (!player.isOnline()) return;
            try {
                player.teleportAsync(target).whenComplete((success, err) -> {
                    plugin.getThreadSafety().runForEntity(player, () -> finishIslandTeleport(player, island, target, success, announce));
                });
            } catch (NoSuchMethodError | UnsupportedOperationException ex) {
                finishIslandTeleport(player, island, target, player.teleport(target), announce);
            }
        });
    }

    public void teleportPlayerToIslandSpawn(Player player, Island island, Location dest) {
        teleportPlayerToIslandSpawn(player, island, dest, true);
    }

    private void finishIslandTeleport(Player player, Island island, Location target, Boolean asyncSuccess, boolean announce) {
        if (!player.isOnline()) return;
        if (!Boolean.TRUE.equals(asyncSuccess)) {
            player.teleport(target);
        }
        if (announce) {
            if (island != null) {
                MessageUtil.sendMessage(player, "§aTeleported to your island!");
            } else {
                MessageUtil.sendMessage(player, "§aTeleported to spawn.");
            }
        }
        plugin.getThreadSafety().runOnMainThreadLater(() -> {
            if (player.isOnline() && plugin.getBorderVisualManager() != null) {
                plugin.getBorderVisualManager().updatePlayerWorldBorder(player, island);
            }
        }, 15L);
    }

    public Location getIslandHome(Player player) {
        Island island = getIslandForPlayer(player);
        Location home = getSafeIslandHomeLocation(island);
        return home != null ? home : player.getWorld().getSpawnLocation();
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
        // Persist member + update count snapshot for O(1) aggregates (member_count)
        GridPosition pos = island.getGridPosition();
        plugin.getDatabaseManager().addIslandMember(
            pos.x(), pos.z(), island.getDimension().name(), 
            player.getUniqueId(), IslandRank.GUEST.name()
        );
        plugin.getDatabaseManager().getIslandDAO().saveIslandMemberCount(pos, island.getMemberCount());
        if (plugin.getIslandWorthManager() != null) plugin.getIslandWorthManager().markMembersTopsDirty();
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
        // Persist remove + update member_count snapshot
        GridPosition pos = island.getGridPosition();
        plugin.getDatabaseManager().removeIslandMember(pos.x(), pos.z(), island.getDimension().name(), target);
        plugin.getDatabaseManager().getIslandDAO().saveIslandMemberCount(pos, island.getMemberCount());
        if (plugin.getIslandWorthManager() != null) plugin.getIslandWorthManager().markMembersTopsDirty();
        MessageUtil.sendMessage(kicker, "§aPlayer kicked from the party.");
    }

    public void leaveParty(Player player) {
        Island island = getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || island.isOwner(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "§cOwners cannot leave. Use /is disband instead.");
            return;
        }
        UUID leaving = player.getUniqueId();
        island.removeMember(leaving);
        // Persist remove + snapshot
        GridPosition pos = island.getGridPosition();
        plugin.getDatabaseManager().removeIslandMember(pos.x(), pos.z(), island.getDimension().name(), leaving);
        plugin.getDatabaseManager().getIslandDAO().saveIslandMemberCount(pos, island.getMemberCount());
        if (plugin.getIslandWorthManager() != null) plugin.getIslandWorthManager().markMembersTopsDirty();
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
                // Persist individual removes (for accuracy)
                GridPosition pos = island.getGridPosition();
                plugin.getDatabaseManager().removeIslandMember(pos.x(), pos.z(), island.getDimension().name(), member);
            }
        }
        // Final snapshot (owner only now)
        GridPosition pos = island.getGridPosition();
        plugin.getDatabaseManager().getIslandDAO().saveIslandMemberCount(pos, 1);
        if (plugin.getIslandWorthManager() != null) plugin.getIslandWorthManager().markMembersTopsDirty();
        MessageUtil.sendMessage(player, "§aIsland party disbanded.");
    }

    // ==================== PER-DIMENSION RESET WITH SAFETY CHECKS ====================

    /**
     * Resets only the specified dimension with strong safety checks.
     * This is the main method called from BiomeSelectionGUI after confirmation.
     */
    public void resetIslandWithBiome(Player player, String biomeName, World.Environment dimension) {
        resetIslandWithBiome(player, biomeName, dimension, false);
    }

    /**
     * Resets a dimension island. If rerollPersonality is true (donor only), a fresh random
     * generation seed is generated so the new island gets a completely different "personality"
     * (archetype, terrain shape, tree style, landmarks) while keeping the chosen biome.
     * This is a pure cosmetic donor perk — no mechanical or resource advantage (Play-to-Win safe).
     */
    public void resetIslandWithBiome(Player player, String biomeName, World.Environment dimension, boolean rerollPersonality) {
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

        final boolean finalReroll = rerollPersonality && player.hasPermission("foliasb.donor");

        // Delete old dimension data
        databaseManager.deleteIsland(player.getUniqueId(), dimension).thenAccept(deleted -> {
            if (deleted) {
                gridManager.deletePlayerIsland(player.getUniqueId(), dimension);

                // Create new island, optionally with a fresh personality seed for donors
                createIsland(player, biomeName, dimension, finalReroll).thenAccept(success -> {
                    if (success) {
                        databaseManager.recordIslandReset(player.getUniqueId(), dimension);

                        String msg = finalReroll
                                ? "§a§lYour " + dimension.name() + " island has been reset with a new personality!"
                                : "§a§lYour " + dimension.name() + " island has been successfully reset!";
                        plugin.getThreadSafety().sendMessageSafely(player, msg);
                    }
                });
            } else {
                plugin.getThreadSafety().sendMessageSafely(player,
                        "§cFailed to reset island. Please try again or contact staff.");
            }
        });
    }

    /**
     * Force-rebirth an island as part of a prestige action.
     * - Bypasses normal reset cooldowns, combat tags, and active boss checks.
     * - For non-overworld dimensions, bypasses the main-island level gate (we are rebirthing the main at the same time).
     * - Allocates a fresh plot, creates a small starter island of the requested biome, and ensures the
     *   new prestige level (already persisted under the stable owner_dim key) is attached.
     * - After creation, the normal create post-steps (quest seeding for the new run, etc.) run.
     */
    public void performPrestigeIslandRebirth(Player player, String biomeName, World.Environment dimension, boolean donorRerollPersonality) {
        if (player == null || dimension == null) return;
        UUID owner = player.getUniqueId();

        // Remove any stale in-memory island for this dim so the new one is used after create
        Map<World.Environment, Island> map = playerIslands.get(owner);
        if (map != null) {
            map.remove(dimension);
        }

        // Delete old data (cleans upgrades, levels, collections, active data etc. for the old grid)
        databaseManager.deleteIsland(owner, dimension).thenAccept(deleted -> {
            gridManager.deletePlayerIsland(owner, dimension);

            // Create using the bypass so dim gates don't block during a multi-dim prestige rebirth
            createIsland(player, biomeName, dimension, donorRerollPersonality, true /* bypassDimensionGates */)
                    .thenAccept(success -> {
                        if (!success) {
                            plugin.getThreadSafety().sendMessageSafely(player,
                                    "§cPrestige rebirth failed for " + dimension.name() + ". Contact staff if this persists.");
                            return;
                        }

                        // The new island now exists with a fresh grid position.
                        Island fresh = getIsland(owner, dimension);
                        if (fresh != null) {
                            // Make sure the (already saved) prestige under the stable key is in the PrestigeManager cache
                            if (plugin.getPrestigeManager() != null) {
                                String ik = fresh.getId();
                                plugin.getDatabaseManager().loadIslandPrestige(ik).thenAccept(lvl -> {
                                    plugin.getPrestigeManager().loadPrestigeForIsland(ik, lvl);
                                    // Snapshot the prestige onto the *new* grid position for tops/aggregates
                                    if (fresh.getGridPosition() != null && plugin.getDatabaseManager().getIslandDAO() != null) {
                                        plugin.getDatabaseManager().getIslandDAO().saveIslandPrestigeLevel(fresh.getGridPosition(), lvl);
                                    }
                                });
                            }

                            // Clear any stale in-memory active quests for this stable id so the create-seeded
                            // onboarding/story quests for the new prestige run are the ones shown.
                            if (plugin.getQuestManager() != null) {
                                plugin.getQuestManager().clearActiveForIsland(fresh.getId());
                            }
                        }

                        plugin.getThreadSafety().sendMessageSafely(player,
                                "§a" + dimension.name() + " island reborn for your new Prestige run.");

                        // For the player's main (overworld) after prestige, teleport them to the fresh starter
                        if (dimension == World.Environment.NORMAL && player.isOnline()) {
                            plugin.getThreadSafety().runOnMainThreadLater(() -> {
                                Island newMain = getIsland(owner, dimension);
                                if (newMain != null && player.isOnline()) {
                                    teleportToIslandHome(player, newMain);
                                }
                            }, 30L);
                        }
                    });
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
     * Called by SeasonManager after full seasonal wipe (Option B).
     * Clears in-memory island caches so next joins/creates see clean state.
     */
    public void clearCachesForNewSeason() {
        positionToIslandCache.clear();
        playerIslands.clear();
        lastCombatTime.clear();
        plugin.getLogger().info("[IslandManager] Cleared island caches for new season.");
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
        // IslandGenerator schedules work on the island's block region (not grid indices).
        plugin.getIslandGenerator().generateIsland(island, player, biome, isDonor);
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

    /**
     * Task 1 helper: main (overworld) island level for dim gates.
     * Uses XP level (play/skills) not worth. Inter-class: delegates to Island.getLevel().
     */
    public int getMainIslandLevel(UUID uuid) {
        Island main = getIsland(uuid, World.Environment.NORMAL);
        if (main != null) return main.getLevel();
        for (World.Environment dim : new World.Environment[]{World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END}) {
            Island island = getIsland(uuid, dim);
            if (island != null) return island.getLevel();
        }
        return 0;
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

    /**
     * Bounded eviction for caches and hopper counts (large scale compression + caps for hoppers/auctions per suggestions).
     * Use LRU-ish for caches; cap hoppers.
     */
    private void cleanupCaches() {
        if (positionToIslandCache.size() > MAX_CACHE_SIZE) {
            java.util.Iterator<GridPosition> it = positionToIslandCache.keySet().iterator();
            int toRemove = positionToIslandCache.size() - (MAX_CACHE_SIZE - 200);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
        if (shortLivedTickCache.size() > MAX_CACHE_SIZE) {
            java.util.Iterator<GridPosition> it = shortLivedTickCache.keySet().iterator();
            int toRemove = shortLivedTickCache.size() - (MAX_CACHE_SIZE - 200);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
        // Enforce hopper cap (example for large scale hoppers cap)
        for (Map.Entry<String, Integer> e : new HashMap<>(islandHopperCounts).entrySet()) {
            if (e.getValue() > MAX_HOPPERS_PER_ISLAND) {
                islandHopperCounts.put(e.getKey(), MAX_HOPPERS_PER_ISLAND);
            }
        }
        if (islandHopperCounts.size() > MAX_CACHE_SIZE) {
            java.util.Iterator<String> it = islandHopperCounts.keySet().iterator();
            int toRemove = islandHopperCounts.size() - (MAX_CACHE_SIZE - 200);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
    }
}