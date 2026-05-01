package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.challenge.Challenge;
import com.thenerdcj.challenge.PlayerChallengeProfile;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChallengeManager {

    private final FoliaSkyblock plugin;

    private final Map<UUID, List<Challenge>> activeChallenges = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerChallengeProfile> playerProfiles = new ConcurrentHashMap<>();

    private String currentTheme = "MIXED";
    private long themeEndTime = 0;

    public ChallengeManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        updateThemedWeek();
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateThemedWeek, 0L, 12096000L);
    }

    public List<Challenge> generateChallenges(Player player, boolean isWeekly) {
        UUID owner = player.getUniqueId();
        Island island = plugin.getIslandManager().getIsland(owner, player.getWorld().getEnvironment());
        int level = island != null ? island.getLevel() : 1;

        PlayerChallengeProfile profile = playerProfiles.computeIfAbsent(owner, k -> new PlayerChallengeProfile());

        List<Challenge> challenges = loadExistingChallenges(island);

        if (challenges.size() >= (isWeekly ? 5 : 3)) {
            activeChallenges.put(owner, challenges);
            return challenges;
        }

        Random random = new Random();
        Map<String, Double> skillGaps = analyzeSkillGaps(profile, level);
        int numNeeded = (isWeekly ? 7 : 5) - challenges.size();

        for (int i = 0; i < numNeeded; i++) {
            Challenge challenge = createSmartChallenge(player, level, skillGaps, isWeekly, random, profile);

            if (island != null) {
                String islandId = island.getGridPosition().toString();
                plugin.getDatabaseManager().saveChallenge(
                        challenge.id(), islandId, challenge.getType().name(), challenge.getCategory(),
                        challenge.getDescription(), challenge.getTarget(), 0, challenge.getRewardXP(), false
                );
            }

            challenges.add(challenge);
        }

        activeChallenges.put(owner, challenges);
        return challenges;
    }

    private List<Challenge> loadExistingChallenges(Island island) {
        List<Challenge> loaded = new ArrayList<>();
        if (island == null) return loaded;

        try {
            String islandId = island.getGridPosition().toString();
            List<Map<String, Object>> data = plugin.getDatabaseManager().loadChallengesForIsland(islandId).get();

            for (Map<String, Object> row : data) {
                if (!(boolean) row.get("completed")) {
                    Challenge c = new Challenge(
                            island.getOwner(),
                            Challenge.Type.valueOf((String) row.get("type")),
                            (String) row.get("category"),
                            (String) row.get("description"),
                            (int) row.get("target"),
                            (int) row.get("reward_xp")
                    );
                    c.addProgress((int) row.get("progress"));
                    loaded.add(c);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load challenges from database");
        }
        return loaded;
    }

    private Map<String, Double> analyzeSkillGaps(PlayerChallengeProfile profile, int level) {
        Map<String, Double> gaps = new HashMap<>();
        gaps.put("MINING", 1.0);
        gaps.put("FARMING", 1.0);
        gaps.put("COMBAT", 1.0);
        gaps.put("BUILDING", 1.0);
        gaps.put("EXPLORATION", 1.0);

        if (profile.getCompletionRate("MINING") < 0.6) gaps.put("MINING", 1.8);
        if (profile.getCompletionRate("FARMING") < 0.6) gaps.put("FARMING", 1.7);
        if (profile.getCompletionRate("COMBAT") < 0.5) gaps.put("COMBAT", 2.0);
        if (profile.getCompletionRate("BUILDING") < 0.7) gaps.put("BUILDING", 1.5);

        if (level < 15) gaps.put("MINING", gaps.get("MINING") + 0.5);
        if (level >= 20 && level < 40) gaps.put("COMBAT", gaps.get("COMBAT") + 0.6);
        if (level >= 40) gaps.put("BUILDING", gaps.get("BUILDING") + 0.8);

        return gaps;
    }

    private Challenge createSmartChallenge(Player player, int level, Map<String, Double> skillGaps,
                                           boolean isWeekly, Random random, PlayerChallengeProfile profile) {
        String category = pickCategoryByWeight(skillGaps, random);
        int baseTarget = calculateBaseTarget(category, level, isWeekly);
        double difficultyMultiplier = 1.0 + (profile.getAverageDifficulty() * 0.3);
        if (profile.getCurrentStreak() > 3) difficultyMultiplier *= 1.2;

        int finalTarget = (int) (baseTarget * difficultyMultiplier * (0.85 + random.nextDouble() * 0.3));
        String description = generateDescription(category, finalTarget, level);
        int reward = calculateReward(finalTarget, level, isWeekly, profile.getCurrentStreak());
        Challenge.Type type = isWeekly ? Challenge.Type.WEEKLY : Challenge.Type.DAILY;

        return new Challenge(player.getUniqueId(), type, category, description, finalTarget, reward);
    }

    private String pickCategoryByWeight(Map<String, Double> weights, Random random) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double r = random.nextDouble() * total;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            r -= entry.getValue();
            if (r <= 0) return entry.getKey();
        }
        return "MINING";
    }

    private int calculateBaseTarget(String category, int level, boolean isWeekly) {
        int base = switch (category) {
            case "MINING" -> 40 + (level * 4);
            case "FARMING" -> 35 + (level * 3);
            case "COMBAT" -> 15 + (level * 2);
            case "BUILDING" -> 25 + (level * 5);
            case "EXPLORATION" -> 8 + (level / 2);
            default -> 30;
        };
        return isWeekly ? base * 6 : base;
    }

    private String generateDescription(String category, int target, int level) {
        return switch (category) {
            case "MINING" -> "Mine " + target + " blocks (any ore)";
            case "FARMING" -> "Harvest " + target + " crops";
            case "COMBAT" -> "Defeat " + target + " hostile mobs";
            case "BUILDING" -> "Place " + target + " blocks on your island";
            case "EXPLORATION" -> "Travel " + target + " blocks from spawn";
            default -> "Complete " + target + " actions";
        };
    }

    private int calculateReward(int target, int level, boolean isWeekly, int streak) {
        int base = target / 2;
        if (isWeekly) base *= 3;
        if (streak >= 5) base = (int)(base * 1.5);
        return Math.max(50, base);
    }

    private void updateThemedWeek() {
        String[] themes = {"MINING", "COMBAT", "FARMING", "BUILDING", "MIXED"};
        currentTheme = themes[new Random().nextInt(themes.length)];
        themeEndTime = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000);
        Bukkit.broadcastMessage("§6§l[Challenge] New themed week: §e" + currentTheme + " Week!");
    }

    public List<Challenge> getActiveChallenges(UUID owner) {
        return activeChallenges.getOrDefault(owner, new ArrayList<>());
    }

    public void updateProgress(UUID owner, String category, int amount) {
        List<Challenge> challenges = activeChallenges.get(owner);
        if (challenges == null) return;

        for (Challenge c : challenges) {
            if (c.getCategory().equals(category) && !c.isCompleted()) {
                c.addProgress(amount);

                if (c.isCompleted()) {
                    Player player = Bukkit.getPlayer(owner);
                    if (player != null) {
                        player.sendMessage("§a§lChallenge Complete! §e+" + c.getRewardXP() + " XP");

                        Island island = plugin.getIslandManager().getIsland(owner, player.getWorld().getEnvironment());
                        if (island != null) {
                            island.addXp(c.getRewardXP());

                            String islandId = island.getGridPosition().toString();
                            plugin.getDatabaseManager().saveChallenge(
                                    c.id(), islandId, c.getType().name(), c.getCategory(),
                                    c.getDescription(), c.getTarget(), c.getProgress(), c.getRewardXP(), true
                            );
                        }
                    }
                }
            }
        }
    }

    public void recordChallengeCompletion(UUID owner, String category, boolean success) {
        PlayerChallengeProfile profile = playerProfiles.get(owner);
        if (profile != null) profile.recordCompletion(category, success);
    }

    public int getCurrentStreak(UUID owner) {
        PlayerChallengeProfile profile = playerProfiles.get(owner);
        return profile != null ? profile.getCurrentStreak() : 0;
    }
}