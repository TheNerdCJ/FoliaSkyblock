package com.thenerdcj.boss;

import java.util.UUID;

/**
 * Tracks a player's slayer quest progress
 */
public class SlayerQuest {

    private final UUID playerId;
    private final SlayerTier tier;
    private int kills;
    private final long startTime;
    private boolean completed;

    public SlayerQuest(UUID playerId, SlayerTier tier) {
        this.playerId = playerId;
        this.tier = tier;
        this.kills = 0;
        this.startTime = System.currentTimeMillis();
        this.completed = false;
    }

    public UUID getPlayerId() { return playerId; }
    public SlayerTier getTier() { return tier; }
    public int getKills() { return kills; }
    public long getStartTime() { return startTime; }
    public boolean isCompleted() { return completed; }

    public void addKill() {
        kills++;
        if (kills >= tier.getXpRequired() / 10) { // 10% of XP requirement = kill count
            completed = true;
        }
    }

    public int getKillsRequired() {
        return tier.getXpRequired() / 10;
    }

    public double getProgress() {
        return Math.min(1.0, (double) kills / getKillsRequired());
    }

    public long getTimeElapsed() {
        return System.currentTimeMillis() - startTime;
    }
}