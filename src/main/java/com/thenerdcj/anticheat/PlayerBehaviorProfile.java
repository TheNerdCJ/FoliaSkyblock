package com.thenerdcj.anticheat;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;

/**
 * Player Behavior Profile - AI Memory for Anti-Cheat
 * 
 * Updated for better integration with FoliaSkyblock's custom island systems,
 * ore generators, and Play-to-Win progression.
 * 
 * Communicates with:
 * - NeuralCheatDetector (feature extraction for ML detection)
 * - AntiCheatManager (records movement, combat, mining, dupe, XP events)
 * - IslandManager / GridManager (via manager for location context in flags)
 * - IslandXPManager / leveling (XP exploit reporting)
 * 
 * Tracks metrics to prevent exploits that bypass economy, trading, leveling,
 * and custom generators (e.g. high ore from upgraded cobble gens is legit).
 */
public class PlayerBehaviorProfile {

    private final UUID playerId;
    private long lastActivity;
    private Location lastLocation;
    private double lastHealth;

    private final List<Double> speedSamples = new ArrayList<>();
    private static final int MAX_SPEED_SAMPLES = 50;
    private double averageSpeed = 4.0;
    private double speedStdDev = 1.0;

    private long lastAttackTime = 0;
    private int recentAttackCount = 0;
    private final List<Long> attackTimestamps = new ArrayList<>();

    // Mining metrics - enhanced for xray + custom ore gen awareness
    private int oreMinedCount = 0;
    private long oreMiningStartTime = 0;
    private double oreMiningRate = 0.0;
    private int stoneMinedCount = 0; // for xray heuristic (low stone = suspicious ore focus)
    private int blocksBrokenTotal = 0;
    private long lastBlockBreakTime = 0;

    private boolean hasLegitimateSpeed = false;
    private boolean hasLegitimateReach = false;
    private boolean hasHighEnchantments = false;
    private boolean hasHighPotions = false;

    private int flagCount = 0;
    private final Map<String, Integer> flagReasons = new HashMap<>();

    public PlayerBehaviorProfile(UUID playerId) {
        this.playerId = playerId;
        this.lastActivity = System.currentTimeMillis();
        this.oreMiningStartTime = System.currentTimeMillis();
    }

    public void addMovementSample(double speed) {
        speedSamples.add(speed);
        if (speedSamples.size() > MAX_SPEED_SAMPLES) {
            speedSamples.remove(0);
        }
        recalculateSpeedStats();
        lastActivity = System.currentTimeMillis();
    }

    private void recalculateSpeedStats() {
        if (speedSamples.isEmpty()) return;

        averageSpeed = speedSamples.stream().mapToDouble(Double::doubleValue).average().orElse(4.0);

        double variance = speedSamples.stream()
                .mapToDouble(s -> Math.pow(s - averageSpeed, 2))
                .average().orElse(1.0);
        speedStdDev = Math.sqrt(variance);
    }

    public void recordAttack() {
        long now = System.currentTimeMillis();
        attackTimestamps.add(now);

        if (attackTimestamps.size() > 20) {
            attackTimestamps.remove(0);
        }

        if (attackTimestamps.size() >= 2) {
            long timeSpan = attackTimestamps.get(attackTimestamps.size() - 1) - attackTimestamps.get(0);
            if (timeSpan > 0) {
                recentAttackCount = attackTimestamps.size();
            }
        }

        lastAttackTime = now;
        lastActivity = now;
    }

    public void recordOreMined() {
        oreMinedCount++;
        blocksBrokenTotal++;
        long elapsed = System.currentTimeMillis() - oreMiningStartTime;

        if (elapsed > 60000) {
            oreMiningRate = (oreMinedCount * 60000.0) / elapsed;
            oreMinedCount = 0;
            oreMiningStartTime = System.currentTimeMillis();
        }

        lastActivity = System.currentTimeMillis();
    }

    /**
     * Record block break for fastbreak + xray detection.
     * Adjusts for custom island ore generators (high ore on upgraded islands is expected).
     */
    public void recordBlockBreak(Material blockType) {
        long now = System.currentTimeMillis();
        long timeSinceLast = now - lastBlockBreakTime;
        lastBlockBreakTime = now;
        blocksBrokenTotal++;

        if (blockType == Material.STONE || blockType == Material.COBBLESTONE || 
            blockType == Material.DEEPSLATE || blockType.name().endsWith("_STONE")) {
            stoneMinedCount++;
        } else if (blockType.name().endsWith("_ORE") || blockType == Material.ANCIENT_DEBRIS) {
            oreMinedCount++; // also count here for combined rate
            // Note: AntiCheatManager can cross-check with IslandOreGenerator level if needed
        }

        lastActivity = now;
    }

    public long getLastBlockBreakTime() { return lastBlockBreakTime; }
    public int getBlocksBrokenTotal() { return blocksBrokenTotal; }
    public int getStoneMinedCount() { return stoneMinedCount; }

    public void recordStoneMined() {
        stoneMinedCount++;
        blocksBrokenTotal++;
        lastActivity = System.currentTimeMillis();
    }

    public void incrementFlagCount() {
        flagCount++;
        lastActivity = System.currentTimeMillis();
    }

    public void addFlagReason(String reason) {
        flagReasons.merge(reason, 1, Integer::sum);
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public long getLastActivity() { return lastActivity; }
    public Location getLastLocation() { return lastLocation; }
    public double getLastHealth() { return lastHealth; }
    public double getAverageSpeed() { return averageSpeed; }
    public double getSpeedStandardDeviation() { return speedStdDev; }
    public long getLastAttackTime() { return lastAttackTime; }
    public int getRecentAttackCount() { return recentAttackCount; }
    public double getOreMiningRate() { return oreMiningRate; }
    public int getFlagCount() { return flagCount; }
    public boolean hasLegitimateSpeed() { return hasLegitimateSpeed; }
    public boolean hasLegitimateReach() { return hasLegitimateReach; }
    public boolean hasHighEnchantments() { return hasHighEnchantments; }
    public boolean hasHighPotions() { return hasHighPotions; }

    // Setters
    public void setLastLocation(Location loc) { this.lastLocation = loc; }
    public void setLastHealth(double health) { this.lastHealth = health; }
    public void setHasLegitimateSpeed(boolean value) { this.hasLegitimateSpeed = value; }
    public void setHasLegitimateReach(boolean value) { this.hasLegitimateReach = value; }
    public void setHasHighEnchantments(boolean value) { this.hasHighEnchantments = value; }
    public void setHasHighPotions(boolean value) { this.hasHighPotions = value; }
}
