package com.thenerdcj.database;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class TopIslandEntry {
    private final UUID ownerUuid;
    private final int level;
    private final String dimension;
    private String ownerName; // cached for holograms

    public TopIslandEntry(UUID ownerUuid, int level, String dimension) {
        this.ownerUuid = ownerUuid;
        this.level = level;
        this.dimension = dimension;
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public int getLevel() { return level; }
    public String getDimension() { return dimension; }

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
