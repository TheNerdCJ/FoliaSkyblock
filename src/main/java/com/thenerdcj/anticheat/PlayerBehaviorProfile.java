package com.thenerdcj.anticheat;

import org.bukkit.Location;

import java.util.*;

/**
 * Player Behavior Profile - AI Memory for Anti-Cheat
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

    private int oreMinedCount = 0;
    private long oreMiningStartTime = 0;
    private double oreMiningRate = 0.0;

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
        long elapsed = System.currentTimeMillis() - oreMiningStartTime;

        if (elapsed > 60000) {
            oreMiningRate = (oreMinedCount * 60000.0) / elapsed;
            oreMinedCount = 0;
            oreMiningStartTime = System.currentTimeMillis();
        }

        lastActivity = System.currentTimeMillis();
    }

    public void incrementFlagCount() {
        flagCount++;
        lastActivity = System.currentTimeMillis();
    }

    public void addFlagReason(String reason) {
        flagReasons.merge(reason, 1, Integer::sum);
    }

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

    public void setLastLocation(Location loc) { this.lastLocation = loc; }
    public void setLastHealth(double health) { this.lastHealth = health; }
    public void setHasLegitimateSpeed(boolean value) { this.hasLegitimateSpeed = value; }
    public void setHasLegitimateReach(boolean value) { this.hasLegitimateReach = value; }
    public void setHasHighEnchantments(boolean value) { this.hasHighEnchantments = value; }
    public void setHasHighPotions(boolean value) { this.hasHighPotions = value; }
}