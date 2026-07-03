package com.thenerdcj.mission;

import java.util.UUID;

/**
 * Unified Mission model for FoliaSkyblock.
 * Replaces/enhances the old Quest and Challenge systems.
 *
 * Supports:
 * - Daily / Weekly / Special missions
 * - Rich objective types
 * - Multiple reward types (money, XP, items, prestige)
 * - Proper expiration and claiming
 */
public class Mission {

    public enum MissionType {
        DAILY, WEEKLY, SPECIAL, EVENT
    }

    public enum ObjectiveType {
        BREAK_BLOCKS,
        PLACE_BLOCKS,
        KILL_MOBS,
        KILL_PLAYERS,
        HARVEST_CROPS,
        SELL_ITEMS,
        BUY_ITEMS,
        COMPLETE_SLAYERS,
        LEVEL_UPGRADE,
        EARN_MONEY,
        SPEND_MONEY,
        FISH_ITEMS,
        ENCHANT_ITEMS,
        BUILD_STRUCTURE,   // future
        VISIT_DIMENSION
    }

    private final String id;
    private final UUID islandOwner;   // Tied to island owner for persistence
    private final String islandKey;   // gridX:gridZ:dimension for fast lookup

    private final MissionType type;
    private final ObjectiveType objective;
    private final String targetMaterial; // e.g. "DIAMOND_ORE", "WHEAT", or "ANY"
    private final int target;

    private int progress;

    private final int rewardMoney;
    private final int rewardIslandXp;
    private final double rewardWorth; // direct worth boost
    private final String rewardItemBase64; // optional serialized item reward

    // Booster reward support
    private final com.thenerdcj.booster.BoosterType rewardBoosterType;
    private final int rewardBoosterDurationMinutes;

    private boolean completed;
    private boolean claimed;
    private final long createdAt;
    private final long expiresAt;

    private final String title;
    private final String description;

    // ==================== CONSTRUCTORS ====================

    public Mission(String islandKey, UUID islandOwner, MissionType type,
                   ObjectiveType objective, String targetMaterial, int target,
                   int rewardMoney, int rewardIslandXp, String rewardItemBase64,
                   com.thenerdcj.booster.BoosterType rewardBoosterType, int rewardBoosterDurationMinutes,
                   String title, String description, long durationMillis) {

        this.id = UUID.randomUUID().toString();
        this.islandKey = islandKey;
        this.islandOwner = islandOwner;
        this.type = type;
        this.objective = objective;
        this.targetMaterial = targetMaterial;
        this.target = target;
        this.progress = 0;
        this.rewardMoney = rewardMoney;
        this.rewardIslandXp = rewardIslandXp;
        this.rewardWorth = 0;
        this.rewardItemBase64 = rewardItemBase64;
        this.rewardBoosterType = rewardBoosterType;
        this.rewardBoosterDurationMinutes = rewardBoosterDurationMinutes;
        this.title = title;
        this.description = description;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + durationMillis;
        this.completed = false;
        this.claimed = false;
    }

    // Full constructor for loading from DB
    public Mission(String id, String islandKey, UUID islandOwner, MissionType type,
                   ObjectiveType objective, String targetMaterial, int target, int progress,
                   int rewardMoney, int rewardIslandXp, String rewardItemBase64,
                   com.thenerdcj.booster.BoosterType rewardBoosterType, int rewardBoosterDurationMinutes,
                   boolean completed, boolean claimed, long createdAt, long expiresAt,
                   String title, String description) {
        this.id = id;
        this.islandKey = islandKey;
        this.islandOwner = islandOwner;
        this.type = type;
        this.objective = objective;
        this.targetMaterial = targetMaterial;
        this.target = target;
        this.progress = progress;
        this.rewardMoney = rewardMoney;
        this.rewardIslandXp = rewardIslandXp;
        this.rewardWorth = 0;
        this.rewardItemBase64 = rewardItemBase64;
        this.rewardBoosterType = rewardBoosterType;
        this.rewardBoosterDurationMinutes = rewardBoosterDurationMinutes;
        this.completed = completed;
        this.claimed = claimed;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.title = title;
        this.description = description;
    }

    // ==================== BUSINESS LOGIC ====================

    public void addProgress(int amount) {
        if (isCompleted() || isClaimed() || isExpired()) return;

        this.progress = Math.min(this.target, this.progress + amount);

        if (this.progress >= this.target) {
            this.completed = true;
        }
    }

    public boolean isCompleted() {
        return completed || progress >= target;
    }

    public synchronized boolean isClaimed() {
        return claimed;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public synchronized void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    /**
     * Atomically reserves this mission for claiming: succeeds for exactly one caller when the
     * mission is completed and not yet claimed. Prevents two island members (or double-clicks on
     * different region threads) from both paying out the same reward. On a failed reward delivery
     * the caller should roll this back via {@link #setClaimed(boolean) setClaimed(false)}.
     */
    public synchronized boolean tryClaim() {
        if (claimed || !isCompletedInternal()) return false;
        claimed = true;
        return true;
    }

    private boolean isCompletedInternal() {
        return completed || progress >= target;
    }

    public double getProgressPercent() {
        return Math.min(100.0, (progress * 100.0) / Math.max(1, target));
    }

    // ==================== GETTERS ====================

    public String getId() { return id; }
    public String getIslandKey() { return islandKey; }
    public UUID getIslandOwner() { return islandOwner; }
    public MissionType getType() { return type; }
    public ObjectiveType getObjective() { return objective; }
    public String getTargetMaterial() { return targetMaterial; }
    public int getTarget() { return target; }
    public int getProgress() { return progress; }
    public int getRewardMoney() { return rewardMoney; }
    public int getRewardIslandXp() { return rewardIslandXp; }
    public double getRewardWorth() { return rewardWorth; }
    public String getRewardItemBase64() { return rewardItemBase64; }
    public com.thenerdcj.booster.BoosterType getRewardBoosterType() { return rewardBoosterType; }
    public int getRewardBoosterDurationMinutes() { return rewardBoosterDurationMinutes; }
    public long getExpiresAt() { return expiresAt; }
    public long getCreatedAt() { return createdAt; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
}