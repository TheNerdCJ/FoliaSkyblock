package com.thenerdcj.database;

/**
 * Data holder for top island leaderboard entries.
 * Adjust fields/methods to match your actual implementation in DatabaseManager.
 */
public class TopIslandEntry {

    private String ownerName;
    private int level;
    private Integer xp; // nullable

    public TopIslandEntry(String ownerName, int level, Integer xp) {
        this.ownerName = ownerName;
        this.level = level;
        this.xp = xp;
    }

    public String getOwnerName() { return ownerName; }
    public int getLevel() { return level; }
    public Integer getXp() { return xp; }

    // Add more fields if your DB query returns them (e.g. island id, members, etc.)
}
