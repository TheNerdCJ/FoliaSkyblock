package com.thenerdcj.database;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class TopIslandEntry {
    private final UUID ownerUuid;
    private final int level;
    private final String dimension;
    private double worth = 0.0; // for worth tops (task batch)
    private int memberCount = 0; // populated for leaderboard compression / GUI display (per-cat tops)
    private int prestigeLevel = 0; // persisted aggregate snapshot for O(1) display in tops/holograms (no subq/join cost in hot paths)
    private String ownerName; // cached for holograms

    public TopIslandEntry(UUID ownerUuid, int level, String dimension) {
        this.ownerUuid = ownerUuid;
        this.level = level;
        this.dimension = dimension;
    }

    public TopIslandEntry(UUID ownerUuid, int level, String dimension, double worth) {
        this(ownerUuid, level, dimension);
        this.worth = worth;
    }

    /**
     * Rich ctor for per-category paginated tops (level or member count) with memberCount for display in IslandTopGUI.
     * Supports true server-side ORDER BY + LIMIT OFFSET without client-side hacks for large scale.
     * prestigeLevel added for persisted aggregate (O(1) in tops/holograms per "Persist more aggregates").
     */
    public TopIslandEntry(UUID ownerUuid, int level, String dimension, double worth, int memberCount) {
        this(ownerUuid, level, dimension, worth, memberCount, 0);
    }

    public TopIslandEntry(UUID ownerUuid, int level, String dimension, double worth, int memberCount, int prestigeLevel) {
        this(ownerUuid, level, dimension, worth);
        this.memberCount = memberCount;
        this.prestigeLevel = prestigeLevel;
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public int getLevel() { return level; }
    public String getDimension() { return dimension; }
    public double getWorth() { return worth; }

    public int getMemberCount() { return memberCount; }

    public int getPrestigeLevel() { return prestigeLevel; }

    public String getOwnerName() {
        if (ownerName != null) return ownerName;
        if (ownerUuid == null) return "Unknown";
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(ownerUuid);
            ownerName = (op.getName() != null) ? op.getName() : ownerUuid.toString().substring(0, 8);
        } catch (Exception e) {
            ownerName = ownerUuid.toString().substring(0, 8);
        }
        return ownerName;
    }
}
