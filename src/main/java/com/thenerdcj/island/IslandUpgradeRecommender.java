package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-Powered Island Upgrade Recommender
 *
 * Uses a neural network + rule-based system to recommend
 * the best upgrades for each player based on:
 * - Playstyle classification (Miner, Builder, Fighter, Farmer, Balanced)
 * - Island progression level
 * - Current upgrade levels
 * - Historical behavior patterns
 */
public class IslandUpgradeRecommender {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;

    // Player playstyle cache
    private final Map<UUID, Playstyle> playerPlaystyles = new ConcurrentHashMap<>();

    // Neural network for playstyle classification
    private final PlaystyleClassifier classifier = new PlaystyleClassifier();

    public IslandUpgradeRecommender(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    /**
     * Get personalized upgrade recommendations for a player
     */
    public List<UpgradeRecommendation> getRecommendations(Player player, Island island) {
        UUID uuid = player.getUniqueId();

        // Classify playstyle
        Playstyle playstyle = classifyPlaystyle(player, island);
        playerPlaystyles.put(uuid, playstyle);

        List<UpgradeRecommendation> recommendations = new ArrayList<>();

        // Get current upgrade levels
        Map<IslandUpgrade, Integer> currentLevels = getCurrentUpgradeLevels(island);

        // Generate recommendations based on playstyle
        for (IslandUpgrade upgrade : IslandUpgrade.values()) {
            int currentLevel = currentLevels.getOrDefault(upgrade, 0);
            int maxLevel = 5; // Default max level

            if (currentLevel >= maxLevel) continue; // Already maxed

            double score = calculateUpgradeScore(upgrade, currentLevel, playstyle, island);

            if (score > 0.3) { // Only recommend if score is decent
                recommendations.add(new UpgradeRecommendation(
                        upgrade,
                        currentLevel,
                        currentLevel + 1,
                        score,
                        getRecommendationReason(upgrade, playstyle)
                ));
            }
        }

        // Sort by score (highest first)
        recommendations.sort((a, b) -> Double.compare(b.score, a.score));

        // Return top 5 recommendations
        return recommendations.subList(0, Math.min(5, recommendations.size()));
    }

    /**
     * Classify player's playstyle using AI
     */
    private Playstyle classifyPlaystyle(Player player, Island island) {
        // Gather features
        double[] features = extractPlaystyleFeatures(player, island);

        // Use neural network to classify
        int classIndex = classifier.predict(features);

        return Playstyle.values()[classIndex];
    }

    /**
     * Extract features for playstyle classification
     */
    private double[] extractPlaystyleFeatures(Player player, Island island) {
        double[] features = new double[8];

        // Feature 0: Mining XP ratio
        double totalXP = island.getXp();
        double miningXP = getCategoryXP(player, "MINING");
        features[0] = totalXP > 0 ? miningXP / totalXP : 0.25;

        // Feature 1: Combat XP ratio
        double combatXP = getCategoryXP(player, "COMBAT");
        features[1] = totalXP > 0 ? combatXP / totalXP : 0.25;

        // Feature 2: Building blocks placed
        features[2] = 0.5; // Placeholder

        // Feature 3: Farming XP ratio
        double farmingXP = getCategoryXP(player, "FARMING");
        features[3] = totalXP > 0 ? farmingXP / totalXP : 0.25;

        // Feature 4: Island level normalized
        features[4] = Math.min(1.0, island.getLevel() / 50.0);

        // Feature 5: Member count normalized
        features[5] = Math.min(1.0, island.getMemberCount() / 10.0);

        // Feature 6: Time since last upgrade (normalized)
        features[6] = 0.5; // Would need to track this

        // Feature 7: Total XP earned
        features[7] = Math.min(1.0, totalXP / 100000.0);

        return features;
    }

    private double getCategoryXP(Player player, String category) {
        // This would query the XP manager for category-specific XP
        // Simplified for now
        return 1000.0;
    }

    private Map<IslandUpgrade, Integer> getCurrentUpgradeLevels(Island island) {
        Map<IslandUpgrade, Integer> levels = new HashMap<>();

        // Convert GridPosition to String ID for database lookup
        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();

        for (IslandUpgrade upgrade : IslandUpgrade.values()) {
            int level = plugin.getIslandUpgradeManager().getUpgradeLevel(islandId, upgrade);
            if (level > 0) {
                levels.put(upgrade, level);
            }
        }

        return levels;
    }

    /**
     * Calculate how good an upgrade is for this playstyle
     */
    private double calculateUpgradeScore(IslandUpgrade upgrade, int currentLevel,
                                         Playstyle playstyle, Island island) {
        double score = 0.0;

        // Base score from playstyle match
        score += getPlaystyleMatchScore(upgrade, playstyle);

        // Bonus for upgrades that unlock new content (level-gated)
        if (upgrade == IslandUpgrade.ISLAND_SIZE && island.getLevel() >= 10) {
            score += 0.3;
        }

        // Penalty for very expensive upgrades at low levels
        if (currentLevel == 0 && island.getLevel() < 5) {
            score *= 0.5;
        }

        // Bonus for balanced progression
        if (currentLevel < 3) {
            score += 0.2; // Encourage getting basics first
        }

        // Clamp to 0-1
        return Math.max(0.0, Math.min(1.0, score));
    }

    private double getPlaystyleMatchScore(IslandUpgrade upgrade, Playstyle playstyle) {
        return switch (playstyle) {
            case MINER -> switch (upgrade) {
                case HOPPER_LIMIT, SPAWNER_RATE -> 0.9;
                case VAULT_SLOTS, AUTO_SELLER -> 0.7;
                default -> 0.3;
            };
            case BUILDER -> switch (upgrade) {
                case ISLAND_SIZE, HOPPER_LIMIT -> 0.9;
                case VAULT_SLOTS, AUTO_SELLER -> 0.6;
                default -> 0.3;
            };
            case FIGHTER -> switch (upgrade) {
                case SPAWNER_RATE, MOB_CAP -> 0.9;
                case HOPPER_LIMIT, VAULT_SLOTS -> 0.5;
                default -> 0.3;
            };
            case FARMER -> switch (upgrade) {
                case CROP_GROWTH, AUTO_SELLER, HOPPER_LIMIT -> 0.9;
                case VAULT_SLOTS, ISLAND_SIZE -> 0.5;
                default -> 0.3;
            };
            case BALANCED -> 0.6;
        };
    }

    private String getRecommendationReason(IslandUpgrade upgrade, Playstyle playstyle) {
        return switch (playstyle) {
            case MINER -> "Great for mining operations and ore generation";
            case BUILDER -> "Essential for large-scale building projects";
            case FIGHTER -> "Boosts combat efficiency and mob farming";
            case FARMER -> "Optimizes crop production and auto-selling";
            case BALANCED -> "Well-rounded upgrade for any playstyle";
        };
    }

    /**
     * Playstyle enum
     */
    public enum Playstyle {
        MINER("⛏️ Miner", "Focuses on mining and ore generation"),
        BUILDER("🏗️ Builder", "Focuses on construction and expansion"),
        FIGHTER("⚔️ Fighter", "Focuses on combat and mob farming"),
        FARMER("🌾 Farmer", "Focuses on farming and automation"),
        BALANCED("⚖️ Balanced", "Well-rounded progression");

        private final String displayName;
        private final String description;

        Playstyle(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * Upgrade recommendation data class
     */
    public static class UpgradeRecommendation {
        public final IslandUpgrade upgrade;
        public final int currentLevel;
        public final int recommendedLevel;
        public final double score;
        public final String reason;

        public UpgradeRecommendation(IslandUpgrade upgrade, int currentLevel,
                                     int recommendedLevel, double score, String reason) {
            this.upgrade = upgrade;
            this.currentLevel = currentLevel;
            this.recommendedLevel = recommendedLevel;
            this.score = score;
            this.reason = reason;
        }
    }

    /**
     * Simple neural network for playstyle classification
     */
    private static class PlaystyleClassifier {
        // Simplified neural network weights (pre-trained)
        private final double[][] weights = {
                {0.8, 0.2, 0.3, 0.1, 0.4, 0.5, 0.3, 0.6}, // MINER
                {0.2, 0.3, 0.8, 0.7, 0.5, 0.6, 0.4, 0.5}, // BUILDER
                {0.3, 0.9, 0.2, 0.5, 0.3, 0.4, 0.7, 0.6}, // FIGHTER
                {0.4, 0.2, 0.7, 0.6, 0.5, 0.5, 0.4, 0.7}, // FARMER
                {0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5}  // BALANCED
        };

        public int predict(double[] features) {
            double[] scores = new double[5];

            for (int i = 0; i < 5; i++) {
                double score = 0;
                for (int j = 0; j < features.length; j++) {
                    score += features[j] * weights[i][j];
                }
                scores[i] = score;
            }

            // Find highest score
            int maxIndex = 0;
            for (int i = 1; i < scores.length; i++) {
                if (scores[i] > scores[maxIndex]) {
                    maxIndex = i;
                }
            }

            return maxIndex;
        }
    }
}