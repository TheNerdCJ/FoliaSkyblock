package com.thenerdcj.island;

import com.thenerdcj.database.GridPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public class Island {

    private final GridPosition gridPosition;
    private final UUID ownerUuid;
    private final IslandParty party;
    private final World.Environment dimension;

    private int level = 1;
    private double xp = 0.0;

    /**
     * Main constructor used by IslandManager
     */
    public Island(GridPosition gridPosition, UUID ownerUuid, IslandParty party, World.Environment dimension) {
        this.gridPosition = gridPosition;
        this.ownerUuid = ownerUuid;
        this.party = party;
        this.dimension = dimension;
    }

    // ====================== LEVELING SYSTEM ======================
    public int getLevel() {
        return level;
    }

    public double getXp() {
        return xp;
    }

    public double getXpToNextLevel() {
        return calculateXpRequiredForLevel(level + 1) - calculateXpRequiredForLevel(level);
    }

    public double getProgress() {
        return (xp - calculateXpRequiredForLevel(level)) / getXpToNextLevel();
    }

    public void addXp(double amount) {
        if (amount <= 0) return;
        xp += amount;

        boolean leveledUp = false;
        while (xp >= calculateXpRequiredForLevel(level + 1)) {
            level++;
            leveledUp = true;
        }

        if (leveledUp) {
            onLevelUp();
        }
    }

    private long calculateXpRequiredForLevel(int targetLevel) {
        return (long) (350.0 * targetLevel * targetLevel);
    }

    private void onLevelUp() {
        party.getMembers().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("§6§lISLAND LEVEL UP §e§l" + (level - 1) + " §f→ §e§l" + level);
                p.sendMessage("§aYour island has reached level §e" + level + "§a!");
            }
        });
    }

    // ====================== LOCATION ======================
    /**
     * Returns the center location of this island in the given world
     * (used by IslandProtectionListener and commands)
     */
    public Location getCenter(World world) {
        return new Location(world,
                gridPosition.x() * 512 + 0.5,
                100.0,
                gridPosition.z() * 512 + 0.5);
    }

    // ====================== BASIC GETTERS ======================
    public GridPosition getGridPosition() {
        return gridPosition;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public IslandParty getParty() {
        return party;
    }

    public World.Environment getDimension() {
        return dimension;
    }

    // ====================== PERMISSIONS ======================
    public boolean hasPermission(UUID playerUuid, IslandPermission permission) {
        if (ownerUuid.equals(playerUuid)) return true;
        IslandRank rank = party.getRank(playerUuid);
        return rank != null && rank.hasPermission(permission);
    }

    public boolean isOwner(UUID playerUuid) {
        return ownerUuid.equals(playerUuid);
    }

    public String getInfo() {
        return "§6Island §7| §eLevel " + level + " §7| §eXP: " + String.format("%.0f", xp);
    }
}