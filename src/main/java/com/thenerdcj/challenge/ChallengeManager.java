package com.thenerdcj.challenge;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ADVANCED AI-Powered Challenge Generator with Validation Layer
 *
 * Features:
 * - Personalized challenges based on player history (AI simulation)
 * - Dynamic difficulty scaling
 * - Streak bonuses & adaptive targets
 * - Themed weeks
 * - Smart category rotation & exploration/exploitation balance
 * - Full validation layer to prevent impossible challenges
 */
public class ChallengeManager {

    private final FoliaSkyblock plugin;
    private final ChallengeValidator validator;

    // Active challenges
    private final Map<UUID, List<Challenge>> activeChallenges = new ConcurrentHashMap<>();

    // Player history (simulated "AI memory")
    private final Map<UUID, PlayerChallengeProfile> playerProfiles = new ConcurrentHashMap<>();

    // Current themed week
    private String currentTheme = "MIXED";
    private long themeEndTime = 0;

    public ChallengeManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.validator = new ChallengeValidator();
        updateThemedWeek();

        // Update theme every 7 days (use ThreadSafety for Folia compatibility)
        // 12096000 ticks = 7 days; initial 1 tick (Folia rejects 0 initial delay)
        plugin.getThreadSafety().runRepeatingOnMainThread(this::updateThemedWeek, 1L, 12096000L);
    }

    /**
     * Main entry point - generates daily or weekly challenges with full AI + validation
     */
    public List<Challenge> generateChallenges(Player player, boolean isWeekly) {
        UUID owner = player.getUniqueId();
        Island island = plugin.getIslandManager().getIsland(owner, player.getWorld().getEnvironment());
        int level = island != null ? island.getLevel() : 1;

        PlayerChallengeProfile profile = playerProfiles.computeIfAbsent(owner, k -> new PlayerChallengeProfile(owner));

        List<Challenge> challenges = new ArrayList<>();
        Random random = ThreadLocalRandom.current();

        // 1. AI Analysis
        Map<String, Double> skillGaps = analyzeSkillGaps(profile, level);

        // 2. Number of challenges
        int numChallenges = isWeekly ? 7 : 5;

        // 3. Generate validated smart challenges
        for (int i = 0; i < numChallenges; i++) {
            Challenge challenge = createSmartChallenge(player, level, skillGaps, isWeekly, random, profile);
            challenges.add(challenge);
        }

        // 4. Themed challenges (if active)
        if (!currentTheme.equals("MIXED") && random.nextDouble() < 0.6) {
            Challenge themed = createThemedChallenge(owner, level, currentTheme, isWeekly, random);
            if (validator.isValidChallenge(themed, player, level)) {
                challenges.add(themed);
            }
        }

        activeChallenges.put(owner, challenges);
        return challenges;
    }

    /**
     * Creates a single smart challenge with validation.
     * If the generated challenge fails validation, falls back to a safe one.
     */
    public Challenge createSmartChallenge(Player player, int level, Map<String, Double> skillGaps,
                                          boolean isWeekly, Random random, PlayerChallengeProfile profile) {

        // Try to generate a good challenge up to 4 times
        for (int attempt = 0; attempt < 4; attempt++) {
            Challenge candidate = createSmartChallengeInternal(player, level, skillGaps, isWeekly, random, profile);

            if (validator.isValidChallenge(candidate, player, level)) {
                return candidate;
            }
        }

        // All attempts failed validation → return safe fallback
        return createSafeFallbackChallenge(level, isWeekly, random);
    }

    /**
     * Core AI challenge generation logic (internal)
     */
    Challenge createSmartChallengeInternal(Player player, int level, Map<String, Double> skillGaps,
                                           boolean isWeekly, Random random, PlayerChallengeProfile profile) {

        // Weighted category selection (AI)
        String category = pickCategoryByWeight(skillGaps, random);

        // 15% chance to force exploration of underused categories
        if (random.nextDouble() < 0.15) {
            List<String> unexplored = getUnexploredCategories(profile);
            if (!unexplored.isEmpty()) {
                category = unexplored.get(random.nextInt(unexplored.size()));
            }
        }

        int baseTarget = calculateBaseTarget(category, level, isWeekly);

        // Dynamic difficulty from player history
        double difficultyMultiplier = 1.0 + (profile.getAverageDifficulty() * 0.3);
        if (profile.getCurrentStreak() > 3) difficultyMultiplier *= 1.2;
        if (profile.getRecentFailureRate(category) > 0.5) difficultyMultiplier *= 0.75;

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
            case "MINING" -> {
                if (level >= 30) yield "Mine " + target + " valuable ores (Iron+)";
                if (level >= 15) yield "Mine " + target + " Iron or better ores";
                yield "Mine " + target + " ore blocks";
            }
            case "FARMING" -> "Harvest " + target + " crops on your island";
            case "COMBAT" -> {
                if (level >= 35) yield "Defeat " + target + " dangerous mobs (Nether/End)";
                yield "Defeat " + target + " hostile mobs";
            }
            case "BUILDING" -> "Place " + target + " blocks to expand your island";
            case "EXPLORATION" -> "Travel far and explore (" + target + " blocks)";
            default -> "Complete " + target + " actions";
        };
    }

    private int calculateReward(int target, int level, boolean isWeekly, int streak) {
        int base = target / 2;
        if (isWeekly) base *= 3;
        if (streak >= 5) base = (int) (base * 1.5);
        return Math.max(50, base);
    }

    private Challenge createSafeFallbackChallenge(int level, boolean isWeekly, Random random) {
        String[] safeCategories = {"FARMING", "BUILDING", "MINING"};
        String category = safeCategories[random.nextInt(safeCategories.length)];

        int base = switch (category) {
            case "FARMING" -> 30 + level * 2;
            case "BUILDING" -> 20 + level * 3;
            case "MINING" -> 25 + level * 2;
            default -> 30;
        };

        int target = isWeekly ? base * 5 : base;
        int reward = target / 3;
        String description = generateDescription(category, target, level);

        Challenge.Type type = isWeekly ? Challenge.Type.WEEKLY : Challenge.Type.DAILY;
        return new Challenge(null, type, category, description, target, reward);
    }

    // ==================== AI HELPER METHODS ====================

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

        // Performance trend analysis
        if (profile.getRecentCompletionRate("MINING") > profile.getCompletionRate("MINING") + 0.1) {
            gaps.put("MINING", gaps.get("MINING") * 0.7);
        }
        if (profile.getRecentCompletionRate("COMBAT") > profile.getCompletionRate("COMBAT") + 0.1) {
            gaps.put("COMBAT", gaps.get("COMBAT") * 0.7);
        }

        // Force improvement on avoided categories
        if (profile.getCategoryAvoidanceRate("BUILDING") > 0.4) {
            gaps.put("BUILDING", gaps.get("BUILDING") * 1.5);
        }
        if (profile.getCategoryAvoidanceRate("EXPLORATION") > 0.5) {
            gaps.put("EXPLORATION", gaps.get("EXPLORATION") * 1.6);
        }

        // Adaptive difficulty
        double recentAvg = profile.getRecentAverageCompletionRate();
        if (recentAvg > 0.85) {
            gaps.replaceAll((k, v) -> v * 1.3);
        } else if (recentAvg < 0.4) {
            gaps.replaceAll((k, v) -> v * 0.6);
        }

        // Level-based adjustments
        if (level < 15) gaps.put("MINING", gaps.get("MINING") + 0.5);
        if (level >= 20 && level < 40) gaps.put("COMBAT", gaps.get("COMBAT") + 0.6);
        if (level >= 40) gaps.put("BUILDING", gaps.get("BUILDING") + 0.8);

        return gaps;
    }

    private List<String> getUnexploredCategories(PlayerChallengeProfile profile) {
        List<String> unexplored = new ArrayList<>();
        for (String cat : new String[]{"MINING", "FARMING", "COMBAT", "BUILDING", "EXPLORATION"}) {
            if (profile.getCompletionRate(cat) < 0.3) {
                unexplored.add(cat);
            }
        }
        return unexplored;
    }

    // ==================== THEMED WEEK SYSTEM ====================

    private void updateThemedWeek() {
        long now = System.currentTimeMillis();
        if (now > themeEndTime) {
            String[] themes = {"MINING", "FARMING", "COMBAT", "BUILDING", "EXPLORATION", "MIXED"};
            currentTheme = themes[ThreadLocalRandom.current().nextInt(themes.length)];
            themeEndTime = now + (7L * 24 * 60 * 60 * 1000);
        }
    }

    private Challenge createThemedChallenge(UUID owner, int level, String theme, boolean isWeekly, Random random) {
        int target = calculateBaseTarget(theme, level, isWeekly);
        String description = generateDescription(theme, target, level);
        int reward = calculateReward(target, level, isWeekly, 0);

        Challenge.Type type = isWeekly ? Challenge.Type.WEEKLY : Challenge.Type.DAILY;
        return new Challenge(owner, type, theme, description, target, reward);
    }

    // ==================== GETTERS ====================

    public List<Challenge> getActiveChallenges(UUID playerUuid) {
        return activeChallenges.getOrDefault(playerUuid, Collections.emptyList());
    }

    public void clearActiveChallenges(UUID playerUuid) {
        activeChallenges.remove(playerUuid);
    }

    public PlayerChallengeProfile getPlayerProfile(UUID uuid) {
        return playerProfiles.computeIfAbsent(uuid, k -> new PlayerChallengeProfile(uuid));
    }
    /**
     * Updates progress on active challenges for a player.
     * Called from listeners when the player performs relevant actions (mining, farming, killing mobs, etc.).
     */
    public void updateProgress(UUID playerUuid, String category, int amount) {
        List<Challenge> challenges = activeChallenges.get(playerUuid);
        if (challenges == null || challenges.isEmpty()) return;

        for (Challenge challenge : challenges) {
            if (challenge.getCategory().equalsIgnoreCase(category) && !challenge.isCompleted()) {
                challenge.addProgress(amount);
                // Note: We do NOT call setCompleted() here.
                // The addProgress() method inside Challenge.java already sets completed = true when target is reached.
            }
        }
    }
}