package com.thenerdcj.quest;

import java.util.UUID;

public class Quest {

    public enum QuestCategory {
        MINING,
        FARMING,
        COMBAT,
        BUILDING,
        EXPLORATION,
        TRADING,
        CHALLENGE
    }

    public enum QuestType {
        DAILY,
        WEEKLY
    }

    private final String id;
    private final String title;
    private final String description;
    private final QuestCategory category;
    private final QuestType type;

    private int progress;
    private final int target;

    private final int rewardXp;
    private final int rewardMoney;

    private boolean completed;
    private boolean claimed;
    private final long expiryTime;

    // ==================== CONSTRUCTOR ====================

    public Quest(String id, String title, String description,
                 QuestCategory category, QuestType type,
                 int progress, int target,
                 int rewardXp, int rewardMoney,
                 boolean completed, long expiryTime) {

        this.id = (id != null && !id.isEmpty()) ? id : UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.category = category;
        this.type = type;
        this.progress = progress;
        this.target = target;
        this.rewardXp = rewardXp;
        this.rewardMoney = rewardMoney;
        this.completed = completed;
        this.claimed = false;
        this.expiryTime = expiryTime;
    }

    // ==================== GETTERS ====================

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public QuestCategory getCategory() {
        return category;
    }

    public QuestType getType() {
        return type;
    }

    public int getProgress() {
        return progress;
    }

    public int getTarget() {
        return target;
    }

    public int getRewardXp() {
        return rewardXp;
    }

    public int getRewardMoney() {
        return rewardMoney;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public boolean isCompleted() {
        return completed || progress >= target;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }

    // ==================== SETTERS / MODIFIERS ====================

    public void setProgress(int progress) {
        this.progress = Math.max(0, progress);
        checkCompletion();
    }

    public void addProgress(int amount) {
        this.progress += amount;
        checkCompletion();
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public void checkCompletion() {
        if (progress >= target) {
            this.completed = true;
        }
    }
}