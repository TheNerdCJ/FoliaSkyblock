package com.thenerdcj.boss;

import java.util.UUID;

/**
 * Represents a leaderboard entry for slayer rankings
 */
public class SlayerLeaderboard {

    private final UUID playerUuid;
    private final String playerName;
    private final String entityType;
    private final int tier;
    private final int totalKills;
    private final long lastKillTime;

    public SlayerLeaderboard(UUID playerUuid, String playerName, String entityType,
                             int tier, int totalKills, long lastKillTime) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.entityType = entityType;
        this.tier = tier;
        this.totalKills = totalKills;
        this.lastKillTime = lastKillTime;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getEntityType() { return entityType; }
    public int getTier() { return tier; }
    public int getTotalKills() { return totalKills; }
    public long getLastKillTime() { return lastKillTime; }

    public int getPoints() {
        return totalKills * tier * 10;
    }
}