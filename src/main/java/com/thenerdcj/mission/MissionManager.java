package com.thenerdcj.mission;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fully expanded Island Mission System.
 *
 * Replaces the older QuestManager + ChallengeManager with a unified, persistent,
 * per-island mission system with rich objectives and rewards.
 */
public class MissionManager {

    private final FoliaSkyblock plugin;

    // islandKey -> active missions
    private final Map<String, List<Mission>> missionsByIsland = new ConcurrentHashMap<>();

    public MissionManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Future: load from DB on startup
    }

    public CompletableFuture<List<Mission>> getMissionsForIsland(String islandKey) {
        return CompletableFuture.supplyAsync(() -> {
            List<Mission> missions = missionsByIsland.getOrDefault(islandKey, Collections.emptyList());
            return new ArrayList<>(missions);
        });
    }

    public void generateDailyMissions(String islandKey, int islandLevel) {
        List<Mission> current = missionsByIsland.computeIfAbsent(islandKey, k -> new ArrayList<>());

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
        List<Mission> current = missionsByIsland.computeIfAbsent(islandKey, k -> new ArrayList<>());

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

        Mission.ObjectiveType[] objectives = Mission.ObjectiveType.values();
        Mission.ObjectiveType objective = objectives[random.nextInt(objectives.length)];

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
        Island island = plugin.getIslandManager().getIsland(playerUuid, null);
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
            // Could notify player here if desired
        }
    }

    public boolean claimMission(String islandKey, String missionId, Player player) {
        List<Mission> missions = missionsByIsland.get(islandKey);
        if (missions == null) return false;

        for (Mission m : missions) {
            if (m.getId().equals(missionId) && m.isCompleted() && !m.isClaimed()) {
                m.setClaimed(true);

                // === Give Rewards ===
                if (m.getRewardMoney() > 0 && plugin.getEconomyManager() != null) {
                    // TODO: Use correct EconomyManager method (e.g. depositIsland or addBalance)
                    player.sendMessage("§a+" + m.getRewardMoney() + " Island Money (placeholder)");
                }

                if (m.getRewardIslandXp() > 0 && plugin.getIslandManager() != null) {
                    plugin.getIslandManager().addIslandXp(player, m.getRewardIslandXp());
                }

                Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                if (m.getRewardWorth() > 0 && plugin.getIslandWorthManager() != null && island != null) {
                    plugin.getIslandWorthManager().invalidateCache(island);
                    plugin.getIslandWorthManager().recalculateAndUpdate(island);
                    player.sendMessage("§a+" + m.getRewardWorth() + " Island Worth from mission!");
                }

                // Booster reward from mission (the key finishing item for booster system)
                if (m.getRewardBoosterType() != null && m.getRewardBoosterDurationMinutes() > 0
                        && plugin.getBoosterManager() != null && island != null) {
                    double mult = plugin.getConfig().getDouble("boosters.multipliers." + m.getRewardBoosterType().name(), 2.0);
                    long durMillis = (long) m.getRewardBoosterDurationMinutes() * 60 * 1000;
                    plugin.getBoosterManager().activateBooster(island, m.getRewardBoosterType(), mult, durMillis);
                    player.sendMessage("§aBooster rewarded: §e" + m.getRewardBoosterType().getDisplayName()
                            + " §7x" + String.format("%.1f", mult) + " for " + m.getRewardBoosterDurationMinutes() + "m");
                }

                // Deserialize and give item reward if present
                if (m.getRewardItemBase64() != null && !m.getRewardItemBase64().isEmpty()) {
                    try {
                        byte[] data = java.util.Base64.getDecoder().decode(m.getRewardItemBase64());
                        java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(data);
                        org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
                        ItemStack rewardItem = (ItemStack) dataInput.readObject();
                        dataInput.close();
                        player.getInventory().addItem(rewardItem);
                        player.sendMessage("§aYou received a mission reward item!");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to deserialize mission item reward: " + e.getMessage());
                    }
                }

                // Save updated mission
                saveMission(m);

                return true;
            }
        }
        return false;
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
        missionsByIsland.put(islandKey, new ArrayList<>(loaded));
        // Also ensure fresh generation if needed
    }

    public CompletableFuture<Boolean> saveMission(Mission mission) {
        return plugin.getDatabaseManager().saveMission(mission);
    }
}