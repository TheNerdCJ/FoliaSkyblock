package com.thenerdcj.challenge;

import java.util.UUID;

/**
 * Represents a single challenge (Daily, Weekly, or Special)
 *
 * Used by the AI-powered ChallengeManager for personalized challenges
 */
public class Challenge {

    public enum Type {
        DAILY, WEEKLY, SPECIAL
    }

    private final String id;           // Unique ID for database storage
    private final UUID owner;
    private final Type type;
    private final String category;
    private final String description;
    private final int target;
    private int progress;
    private final int rewardXP;
    private final long createdAt;
    private final long expiresAt;
    private boolean completed;

    public Challenge(UUID owner, Type type, String category, String description, int target, int rewardXP) {
        this.id = UUID.randomUUID().toString();
        this.owner = owner;
        this.type = type;
        this.category = category;
        this.description = description;
        this.target = target;
        this.progress = 0;
        this.rewardXP = rewardXP;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + (type == Type.WEEKLY ? 7L * 24 * 60 * 60 * 1000 : 24L * 60 * 60 * 1000);
        this.completed = false;
    }

    /**
     * Alternative constructor for database-loaded challenges
     */
    public Challenge(String id, Type type, String category, String description, int target, int progress, int rewardXP, boolean completed) {
        this.id = id;
        this.owner = null; // Loaded from database, owner not needed
        this.type = type;
        this.category = category;
        this.description = description;
        this.target = target;
        this.progress = progress;
        this.rewardXP = rewardXP;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + (type == Type.WEEKLY ? 7L * 24 * 60 * 60 * 1000 : 24L * 60 * 60 * 1000);
        this.completed = completed;
    }

    public void addProgress(int amount) {
        this.progress += amount;
        if (this.progress >= this.target) {
            this.progress = this.target;
            this.completed = true;
        }
    }

    public double getProgressPercent() {
        return Math.min(100.0, (progress * 100.0) / Math.max(1, target));
    }

    // Getters (for record-style access in ChallengeManager)
    public String id() { return id; }
    public UUID getOwner() { return owner; }
    public Type getType() { return type; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public int getTarget() { return target; }
    public int getProgress() { return progress; }
    public int getRewardXP() { return rewardXP; }
    public long getExpiresAt() { return expiresAt; }
    public boolean isCompleted() { return completed; }
}