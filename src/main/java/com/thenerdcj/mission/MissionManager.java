package com.thenerdcj.mission;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fully expanded Island Mission System.
 *
 * Replaces the older QuestManager + ChallengeManager with a unified, persistent,
 * per-island mission system with rich objectives and rewards.
 */
public class MissionManager {

    /**
     * Objective types generation is allowed to pick. Restricted to those that actually receive
     * progress feeds (see MissionProgressListener + BossManager) so no impossible-to-complete
     * "dead-end" missions are ever generated. Add a type here only once a real feed exists for it.
     */
    private static final Mission.ObjectiveType[] SUPPORTED_OBJECTIVES = {
            Mission.ObjectiveType.BREAK_BLOCKS,
            Mission.ObjectiveType.PLACE_BLOCKS,
            Mission.ObjectiveType.KILL_MOBS,
            Mission.ObjectiveType.HARVEST_CROPS,
            Mission.ObjectiveType.FISH_ITEMS,
            Mission.ObjectiveType.COMPLETE_SLAYERS,
    };

    private final FoliaSkyblock plugin;

    // islandKey -> active missions. Values are CopyOnWriteArrayList so region threads can
    // feed progress (addProgress) while the main thread generates/prunes without a CME.
    private final Map<String, List<Mission>> missionsByIsland = new ConcurrentHashMap<>();

    // Islands whose persisted missions have already been pulled into memory this server run
    // (mirrors QuestManager.ensureActiveQuestsLoaded — load once, never clobber live progress).
    private final Set<String> loadedFromDb = ConcurrentHashMap.newKeySet();

    // Islands with in-progress mission changes not yet persisted. Flushed periodically so a
    // restart doesn't lose progress (completed/claimed missions are already saved immediately).
    private final Set<String> dirtyIslands = ConcurrentHashMap.newKeySet();

    public MissionManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Auto-save in-progress missions every 2 minutes (2400 ticks).
        plugin.getThreadSafety().runRepeatingOnMainThread(this::flushDirtyMissions, 2400L, 2400L);
    }

    /** Persists in-progress missions for islands that advanced since the last flush. */
    public void flushDirtyMissions() {
        if (dirtyIslands.isEmpty()) return;
        for (String key : new ArrayList<>(dirtyIslands)) {
            dirtyIslands.remove(key);
            List<Mission> missions = missionsByIsland.get(key);
            if (missions == null) continue;
            for (Mission m : missions) {
                if (!m.isClaimed()) saveMission(m);
            }
        }
    }

    /** Per-island mission list, created thread-safe on first touch. */
    private List<Mission> islandList(String islandKey) {
        return missionsByIsland.computeIfAbsent(islandKey, k -> new CopyOnWriteArrayList<>());
    }

    public CompletableFuture<List<Mission>> getMissionsForIsland(String islandKey) {
        return CompletableFuture.supplyAsync(() -> {
            List<Mission> missions = missionsByIsland.getOrDefault(islandKey, Collections.emptyList());
            return new ArrayList<>(missions);
        });
    }

    /**
     * Entry point used before opening the mission GUI: loads persisted missions once, then
     * tops up daily/weekly generation in memory. Returns a future that completes when the
     * island's missions are ready to display.
     */
    public CompletableFuture<Void> ensureMissionsReady(String islandKey, int islandLevel) {
        if (loadedFromDb.contains(islandKey)) {
            refreshMissionsForIsland(islandKey, islandLevel);
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getDatabaseManager().loadMissionsForIsland(islandKey).thenAccept(loaded -> {
            List<Mission> current = islandList(islandKey);
            for (Mission m : loaded) {
                if (current.stream().noneMatch(existing -> existing.getId().equals(m.getId()))) {
                    current.add(m);
                }
            }
            loadedFromDb.add(islandKey);
            refreshMissionsForIsland(islandKey, islandLevel);
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[MissionManager] Failed to load missions for " + islandKey + ": " + ex.getMessage());
            // Still allow fresh generation so the GUI isn't permanently empty on a DB hiccup.
            loadedFromDb.add(islandKey);
            refreshMissionsForIsland(islandKey, islandLevel);
            return null;
        });
    }

    public void generateDailyMissions(String islandKey, int islandLevel) {
        List<Mission> current = islandList(islandKey);

        // Remove expired or claimed dailies
        current.removeIf(m -> m.getType() == Mission.MissionType.DAILY && (m.isExpired() || m.isClaimed()));

        long dailyCount = current.stream()
                .filter(m -> m.getType() == Mission.MissionType.DAILY)
                .count();

        for (long i = dailyCount; i < 5; i++) { // 5 daily missions
            Mission mission = generateRandomMission(islandKey, islandLevel, Mission.MissionType.DAILY);
            current.add(mission);
        }
    }

    public void generateWeeklyMissions(String islandKey, int islandLevel) {
        List<Mission> current = islandList(islandKey);

        current.removeIf(m -> m.getType() == Mission.MissionType.WEEKLY && (m.isExpired() || m.isClaimed()));

        long weeklyCount = current.stream()
                .filter(m -> m.getType() == Mission.MissionType.WEEKLY)
                .count();

        for (long i = weeklyCount; i < 3; i++) { // 3 weekly missions
            Mission mission = generateRandomMission(islandKey, islandLevel, Mission.MissionType.WEEKLY);
            current.add(mission);
        }
    }

    private Mission generateRandomMission(String islandKey, int islandLevel, Mission.MissionType type) {
        Random random = ThreadLocalRandom.current();

        // Only pick objectives that have a real progress feed — never generate a dead-end mission.
        Mission.ObjectiveType objective = SUPPORTED_OBJECTIVES[random.nextInt(SUPPORTED_OBJECTIVES.length)];

        int target = switch (objective) {
            case BREAK_BLOCKS, PLACE_BLOCKS -> 150 + (islandLevel * 20);
            case KILL_MOBS -> 40 + (islandLevel * 8);
            case HARVEST_CROPS -> 80 + (islandLevel * 15);
            case SELL_ITEMS, BUY_ITEMS -> 25 + (islandLevel * 5);
            case COMPLETE_SLAYERS -> Math.max(1, islandLevel / 5);
            case EARN_MONEY -> 5000 + (islandLevel * 1000);
            default -> 20 + (islandLevel * 5);
        };

        int rewardMoney = 500 + (islandLevel * 150);
        int rewardXp = 25 + (islandLevel * 8);

        String material = "ANY";
        if (objective == Mission.ObjectiveType.BREAK_BLOCKS || objective == Mission.ObjectiveType.PLACE_BLOCKS) {
            material = random.nextBoolean() ? "STONE" : "DIRT";
        }

        long duration = (type == Mission.MissionType.WEEKLY) ? 7L * 24 * 60 * 60 * 1000 : 24L * 60 * 60 * 1000;

        String title = objective.name().replace("_", " ") + " Mission";
        String desc = "Progress: " + objective.name().toLowerCase().replace("_", " ");

        // Occasionally reward a booster instead of (or in addition to) money
        com.thenerdcj.booster.BoosterType boosterReward = null;
        int boosterDuration = 0;
        if (random.nextDouble() < 0.25) { // 25% chance for booster reward
            boosterReward = com.thenerdcj.booster.BoosterType.values()[random.nextInt(com.thenerdcj.booster.BoosterType.values().length)];
            boosterDuration = (type == Mission.MissionType.WEEKLY) ? 120 : 45; // longer for weekly
        }

        return new Mission(islandKey, null, type, objective, material, target,
                rewardMoney, rewardXp, null, boosterReward, boosterDuration, title, desc, duration);
    }

    public void addProgress(UUID playerUuid, Mission.ObjectiveType objective, String material, int amount) {
        // Missions are anchored to the player's overworld (NORMAL) island so progress from any
        // dimension accrues to one canonical place — the same key generation and the GUI use.
        // (The old code passed a null dimension, which IslandManager.getIsland always rejects,
        // so every progress event was silently dropped.)
        Island island = plugin.getIslandManager().getIsland(playerUuid, World.Environment.NORMAL);
        if (island == null) return;

        String key = island.getId();
        List<Mission> missions = missionsByIsland.get(key);
        if (missions == null) return;

        boolean anyUpdated = false;
        for (Mission m : missions) {
            if (m.getObjective() == objective &&
                (m.getTargetMaterial().equals("ANY") || m.getTargetMaterial().equalsIgnoreCase(material))) {
                m.addProgress(amount);
                anyUpdated = true;

                if (m.isCompleted() && !m.isClaimed()) {
                    // Auto-save on completion
                    saveMission(m);
                }
            }
        }

        if (anyUpdated) {
            // Mark for the periodic flush so in-progress advances survive a restart.
            dirtyIslands.add(key);
        }
    }

    /**
     * Claims a completed mission. Money is deposited to the island bank FIRST; the claim is only
     * finalized (persisted claimed=true, other rewards granted) once the deposit succeeds. If the
     * deposit fails the reservation is rolled back so the reward is never silently lost and the
     * player can retry — satisfying the atomic-economy / durable-reward invariants.
     *
     * @return a future resolving true if the mission was claimed and rewards delivered.
     */
    public CompletableFuture<Boolean> claimMission(String islandKey, String missionId, Player player) {
        List<Mission> missions = missionsByIsland.get(islandKey);
        if (missions == null) return CompletableFuture.completedFuture(false);

        Mission found = null;
        for (Mission m : missions) {
            if (m.getId().equals(missionId)) { found = m; break; }
        }
        if (found == null) return CompletableFuture.completedFuture(false);
        final Mission mission = found;

        // Atomically reserve the claim: exactly one caller wins even under concurrent clicks or
        // two island members claiming at once on different region threads.
        if (!mission.tryClaim()) return CompletableFuture.completedFuture(false);

        // Anchor reward delivery to the canonical (NORMAL) island the missions live on.
        final Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), World.Environment.NORMAL);

        // Deposit money first; only finalize if it actually lands.
        final CompletableFuture<Boolean> moneyFuture;
        if (mission.getRewardMoney() > 0) {
            if (island != null && plugin.getIslandBankManager() != null) {
                moneyFuture = plugin.getIslandBankManager().deposit(island.getGridPosition(), mission.getRewardMoney());
            } else {
                moneyFuture = CompletableFuture.completedFuture(false); // no bank -> not delivered
            }
        } else {
            moneyFuture = CompletableFuture.completedFuture(true); // nothing owed
        }

        return moneyFuture.exceptionally(ex -> false).thenApply(delivered -> {
            if (mission.getRewardMoney() > 0 && !Boolean.TRUE.equals(delivered)) {
                // Roll back the reservation — nothing was paid, so let the player try again later.
                mission.setClaimed(false);
                plugin.getThreadSafety().sendMessageSafely(player,
                        "§cMission claim failed (island bank unavailable). Please try again.");
                return false;
            }
            if (mission.getRewardMoney() > 0) {
                plugin.getThreadSafety().sendMessageSafely(player,
                        "§a+" + mission.getRewardMoney() + " added to your island bank from a mission!");
            }
            grantNonMoneyRewards(mission, player, island);
            saveMission(mission); // persist claimed=true only after money is delivered
            return true;
        });
    }

    /** Grants XP / worth / booster / item rewards on the main thread (durable item delivery). */
    private void grantNonMoneyRewards(Mission mission, Player player, Island island) {
        plugin.getThreadSafety().runOnMainThread(() -> {
            if (mission.getRewardIslandXp() > 0 && plugin.getIslandManager() != null) {
                plugin.getIslandManager().addIslandXp(player, mission.getRewardIslandXp());
            }

            if (mission.getRewardWorth() > 0 && plugin.getIslandWorthManager() != null && island != null) {
                plugin.getIslandWorthManager().invalidateCache(island);
                plugin.getIslandWorthManager().recalculateAndUpdate(island);
                player.sendMessage("§a+" + mission.getRewardWorth() + " Island Worth from mission!");
            }

            if (mission.getRewardBoosterType() != null && mission.getRewardBoosterDurationMinutes() > 0
                    && plugin.getBoosterManager() != null && island != null) {
                double mult = plugin.getConfig().getDouble("boosters.multipliers." + mission.getRewardBoosterType().name(), 2.0);
                long durMillis = (long) mission.getRewardBoosterDurationMinutes() * 60 * 1000;
                plugin.getBoosterManager().activateBooster(island, mission.getRewardBoosterType(), mult, durMillis);
                player.sendMessage("§aBooster rewarded: §e" + mission.getRewardBoosterType().getDisplayName()
                        + " §7x" + String.format("%.1f", mult) + " for " + mission.getRewardBoosterDurationMinutes() + "m");
            }

            if (mission.getRewardItemBase64() != null && !mission.getRewardItemBase64().isEmpty()) {
                try {
                    byte[] data = java.util.Base64.getDecoder().decode(mission.getRewardItemBase64());
                    java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(data);
                    org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
                    ItemStack rewardItem = (ItemStack) dataInput.readObject();
                    dataInput.close();
                    // Durable: giveItemSafely stashes a pending item if the player is offline/full.
                    plugin.getThreadSafety().giveItemSafely(player.getUniqueId(), rewardItem,
                            "§aYou received a mission reward item!");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to deserialize mission item reward: " + e.getMessage());
                }
            }
        });
    }

    // Called periodically or on player login
    public void refreshMissionsForIsland(String islandKey, int islandLevel) {
        generateDailyMissions(islandKey, islandLevel);
        generateWeeklyMissions(islandKey, islandLevel);
    }

    /**
     * Load missions from DB into memory (called on island load).
     */
    public void loadMissionsForIsland(String islandKey, List<Mission> loaded) {
        missionsByIsland.put(islandKey, new CopyOnWriteArrayList<>(loaded));
        loadedFromDb.add(islandKey);
    }

    public CompletableFuture<Boolean> saveMission(Mission mission) {
        return plugin.getDatabaseManager().saveMission(mission);
    }
}