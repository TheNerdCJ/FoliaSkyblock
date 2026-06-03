package com.thenerdcj.database;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class TopIslandEntry {
    private final UUID ownerUuid;
    private final int level;
    private final String dimension;
    private double worth = 0.0; // for worth tops (task batch)
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

    public UUID getOwnerUuid() { return ownerUuid; }
    public int getLevel() { return level; }
    public String getDimension() { return dimension; }
    public double getWorth() { return worth; }

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
