package com.thenerdcj.boss;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-Powered Slayer Reward Balancer
 */
public class SlayerRewardBalancer {

    private final FoliaSkyblock plugin;
    private final Map<Material, DropStatistics> dropStats = new ConcurrentHashMap<>();
    private double inflationFactor = 1.0;
    private double scarcityFactor = 1.0;
    private final Map<String, Double> weights = new HashMap<>();

    public SlayerRewardBalancer(FoliaSkyblock plugin) {
        this.plugin = plugin;
        initializeWeights();
        loadHistoricalData();
    }

    private void initializeWeights() {
        weights.put("level_weight", 0.3);
        weights.put("inflation_weight", 0.25);
        weights.put("scarcity_weight", 0.2);
        weights.put("time_weight", 0.15);
        weights.put("population_weight", 0.1);
    }

    private void loadHistoricalData() {
        for (Material mat : Material.values()) {
            if (mat.isItem()) {
                dropStats.put(mat, new DropStatistics(mat));
            }
        }
    }

    public double calculateDynamicDropChance(SlayerReward reward, int playerLevel, int serverPopulation) {
        Material material = reward.getMaterial();
        DropStatistics stats = dropStats.get(material);

        if (stats == null) {
            stats = new DropStatistics(material);
            dropStats.put(material, stats);
        }

        double levelScore = normalize(playerLevel, 1, 100) * weights.get("level_weight");
        double inflationScore = normalize(inflationFactor, 0.5, 2.0) * weights.get("inflation_weight");
        double scarcityScore = normalize(scarcityFactor, 0.5, 2.0) * weights.get("scarcity_weight");
        double timeScore = normalize(stats.getAverageTimeBetweenDrops(), 60000, 3600000) * weights.get("time_weight");
        double populationScore = normalize(serverPopulation, 1, 100) * weights.get("population_weight");

        double rawScore = levelScore + inflationScore + scarcityScore + timeScore + populationScore;
        double adjustedChance = 1.0 / (1.0 + Math.exp(-rawScore));
        double finalChance = (adjustedChance * 0.7) + (reward.getDropChance() * 0.3);

        stats.recordDropAttempt(finalChance);
        return Math.min(0.95, Math.max(0.01, finalChance));
    }

    public void recordActualDrop(Material material, boolean dropped, double predictedChance) {
        DropStatistics stats = dropStats.get(material);
        if (stats == null) return;

        stats.recordActualDrop(dropped, predictedChance);
        double error = (dropped ? 1.0 : 0.0) - predictedChance;

        if (Math.abs(error) > 0.1) {
            adjustWeights(error, material);
        }

        updateEconomicFactors(material, dropped);
    }

    private void adjustWeights(double error, Material material) {
        double learningRate = 0.01;
        if (isRareMaterial(material)) learningRate *= 0.5;

        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            double currentWeight = entry.getValue();
            double adjustment = error * learningRate * (0.5 + Math.random() * 0.5);
            weights.put(entry.getKey(), Math.max(0.05, Math.min(0.5, currentWeight + adjustment)));
        }
    }

    private void updateEconomicFactors(Material material, boolean dropped) {
        DropStatistics stats = dropStats.get(material);
        if (stats == null) return;

        double dropRate = stats.getActualDropRate();

        if (isHighValueMaterial(material) && dropRate > 0.3) {
            scarcityFactor = Math.min(2.0, scarcityFactor * 1.05);
        }

        if (!isHighValueMaterial(material) && dropRate < 0.1) {
            scarcityFactor = Math.max(0.5, scarcityFactor * 0.95);
        }

        if (dropped && isHighValueMaterial(material)) {
            inflationFactor = Math.min(2.0, inflationFactor * 1.02);
        }
    }

    private boolean isRareMaterial(Material material) {
        return material == Material.NETHERITE_SCRAP ||
                material == Material.NETHERITE_INGOT ||
                material == Material.DIAMOND ||
                material == Material.EMERALD;
    }

    private boolean isHighValueMaterial(Material material) {
        return material == Material.NETHERITE_SCRAP ||
                material == Material.DIAMOND ||
                material == Material.EMERALD ||
                material == Material.GOLD_INGOT;
    }

    private double normalize(double value, double min, double max) {
        return Math.max(0, Math.min(1, (value - min) / (max - min)));
    }

    public Map<String, Double> getModelWeights() { return new HashMap<>(weights); }
    public double getInflationFactor() { return inflationFactor; }
    public double getScarcityFactor() { return scarcityFactor; }

    private static class DropStatistics {
        private final Material material;
        private int totalAttempts = 0;
        private int successfulDrops = 0;
        private double totalPredictedChance = 0;
        private long lastDropTime = 0;
        private List<Long> dropIntervals = new ArrayList<>();

        public DropStatistics(Material material) { this.material = material; }

        public void recordDropAttempt(double predictedChance) {
            totalAttempts++;
            totalPredictedChance += predictedChance;
        }

        public void recordActualDrop(boolean dropped, double predictedChance) {
            if (dropped) {
                successfulDrops++;
                long now = System.currentTimeMillis();
                if (lastDropTime > 0) {
                    dropIntervals.add(now - lastDropTime);
                    if (dropIntervals.size() > 100) dropIntervals.remove(0);
                }
                lastDropTime = now;
            }
        }

        public double getActualDropRate() {
            return totalAttempts > 0 ? (double) successfulDrops / totalAttempts : 0;
        }

        public double getAveragePredictedChance() {
            return totalAttempts > 0 ? totalPredictedChance / totalAttempts : 0;
        }

        public double getAverageTimeBetweenDrops() {
            if (dropIntervals.isEmpty()) return 3600000;
            long sum = 0;
            for (long interval : dropIntervals) sum += interval;
            return sum / (double) dropIntervals.size();
        }

        public double getPredictionAccuracy() {
            double actual = getActualDropRate();
            double predicted = getAveragePredictedChance();
            return 1.0 - Math.abs(actual - predicted);
        }
    }
}