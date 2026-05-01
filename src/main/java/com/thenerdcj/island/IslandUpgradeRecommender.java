package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-Powered Island Upgrade Recommender
 */
public class IslandUpgradeRecommender {

    private final FoliaSkyblock plugin;
    private final Map<UUID, Playstyle> playerPlaystyles = new ConcurrentHashMap<>();
    private final PlaystyleClassifier classifier = new PlaystyleClassifier();

    public IslandUpgradeRecommender(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    public List<UpgradeRecommendation> getRecommendations(Player player, Island island) {
        UUID uuid = player.getUniqueId();
        Playstyle playstyle = classifyPlaystyle(player, island);
        playerPlaystyles.put(uuid, playstyle);

        List<UpgradeRecommendation> recommendations = new ArrayList<>();
        Map<IslandUpgrade, Integer> currentLevels = getCurrentUpgradeLevels(island);

        for (IslandUpgrade upgrade : IslandUpgrade.values()) {
            int currentLevel = currentLevels.getOrDefault(upgrade, 0);
            int maxLevel = 5;

            if (currentLevel >= maxLevel) continue;

            double score = calculateUpgradeScore(upgrade, currentLevel, playstyle, island);

            if (score > 0.3) {
                recommendations.add(new UpgradeRecommendation(
                        upgrade, currentLevel, currentLevel + 1, score,
                        getRecommendationReason(upgrade, playstyle)
                ));
            }
        }

        recommendations.sort((a, b) -> Double.compare(b.score, a.score));
        return recommendations.subList(0, Math.min(5, recommendations.size()));
    }

    private Playstyle classifyPlaystyle(Player player, Island island) {
        double[] features = extractPlaystyleFeatures(player, island);
        int classIndex = classifier.predict(features);
        return Playstyle.values()[classIndex];
    }

    private double[] extractPlaystyleFeatures(Player player, Island island) {
        double[] features = new double[8];
        double totalXP = island.getXp();

        features[0] = totalXP > 0 ? getCategoryXP(player, "MINING") / totalXP : 0.25;
        features[1] = totalXP > 0 ? getCategoryXP(player, "COMBAT") / totalXP : 0.25;
        features[2] = totalXP > 0 ? (getCategoryXP(player, "BUILDING") + getCategoryXP(player, "FARMING")) / totalXP : 0.25;
        features[3] = Math.min(1.0, island.getLevel() / 100.0);
        features[4] = Math.min(1.0, island.getMemberCount() / 5.0);
        features[5] = Math.min(1.0, getCurrentUpgradeLevels(island).size() / 10.0);
        features[6] = 0.5;
        features[7] = Math.min(1.0, totalXP / 100000.0);

        return features;
    }

    private double getCategoryXP(Player player, String category) {
        return 1000.0;
    }

    private Map<IslandUpgrade, Integer> getCurrentUpgradeLevels(Island island) {
        Map<IslandUpgrade, Integer> levels = new HashMap<>();
        for (IslandUpgrade upgrade : IslandUpgrade.values()) {
            int level = plugin.getIslandUpgradeManager().getUpgradeLevel(island.getGridPosition(), upgrade);
            if (level > 0) levels.put(upgrade, level);
        }
        return levels;
    }

    private double calculateUpgradeScore(IslandUpgrade upgrade, int currentLevel, Playstyle playstyle, Island island) {
        double score = getPlaystyleMatchScore(upgrade, playstyle);

        if (upgrade == IslandUpgrade.ISLAND_SIZE && island.getLevel() >= 10) score += 0.3;
        if (upgrade == IslandUpgrade.SPAWNER_RATE && island.getLevel() >= 15) score += 0.3;

        int cost = upgrade.getBaseCost();
        if (island.getLevel() < 20 && cost > 10000) score -= 0.2;
        if (currentLevel < 3) score += 0.2;

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

    private static class PlaystyleClassifier {
        private final double[][] weights = {
                {0.8, 0.2, 0.3, 0.1, 0.4, 0.5, 0.3, 0.6},
                {0.2, 0.3, 0.8, 0.7, 0.5, 0.6, 0.4, 0.5},
                {0.3, 0.9, 0.2, 0.5, 0.3, 0.4, 0.7, 0.6},
                {0.4, 0.2, 0.7, 0.6, 0.5, 0.5, 0.4, 0.7},
                {0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5}
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
            int maxIndex = 0;
            for (int i = 1; i < scores.length; i++) {
                if (scores[i] > scores[maxIndex]) maxIndex = i;
            }
            return maxIndex;
        }
    }
}