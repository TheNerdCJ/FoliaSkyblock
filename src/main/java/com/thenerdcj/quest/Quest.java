package com.thenerdcj.quest;

import java.util.UUID;

/**
 * Quest - Structured mission with progress tracking
 */
public class Quest {

    private final String id;
    private final String islandId;
    private final QuestType type;
    private final QuestCategory category;
    private final String title;
    private final String description;
    private final int target;
    private int progress;
    private final int rewardXp;
    private final int rewardMoney;
    private boolean completed;
    private final long createdAt;
    private final long expiresAt;

    public enum QuestType {
        DAILY, WEEKLY, SPECIAL
    }

    public enum QuestCategory {
        MINING, FARMING, COMBAT, BUILDING, EXPLORATION, TRADING, CHALLENGE
    }

    public Quest(String id, String islandId, QuestType type, QuestCategory category,
                 String title, String description, int target, int rewardXp, int rewardMoney) {
        this.id = id;
        this.islandId = islandId;
        this.type = type;
        this.category = category;
        this.title = title;
        this.description = description;
        this.target = target;
        this.progress = 0;
        this.rewardXp = rewardXp;
        this.rewardMoney = rewardMoney;
        this.completed = false;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = type == QuestType.DAILY ?
                createdAt + (24 * 60 * 60 * 1000L) :
                createdAt + (7 * 24 * 60 * 60 * 1000L);
    }

    // Getters
    public String getId() { return id; }
    public String getIslandId() { return islandId; }
    public QuestType getType() { return type; }
    public QuestCategory getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getTarget() { return target; }
    public int getProgress() { return progress; }
    public int getRewardXp() { return rewardXp; }
    public int getRewardMoney() { return rewardMoney; }
    public boolean isCompleted() { return completed; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }

    // Setters
    public void setProgress(int progress) { this.progress = Math.min(progress, target); }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public double getProgressPercent() {
        return target > 0 ? (double) progress / target * 100 : 0;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public void addProgress(int amount) {
        if (!completed && !isExpired()) {
            this.progress = Math.min(this.progress + amount, target);
            if (this.progress >= target) {
                this.completed = true;
            }
        }
    }
}
