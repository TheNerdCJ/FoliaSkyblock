package com.thenerdcj.island;

import com.thenerdcj.database.GridPosition;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core Island object with full leveling system.
 * XP scales based on party size (diminishing returns for larger parties).
 */
public class Island {

    private final GridPosition gridPosition;
    private UUID ownerUuid;
    private String biomeName;
    private final World.Environment dimension;

    private final Map<UUID, IslandRank> members = new ConcurrentHashMap<>();
    private int level = 1;
    private double xp = 0.0;

    // XP required for next level (quadratic scaling like Hypixel Skyblock)
    private static final double BASE_XP_PER_LEVEL = 100.0;

    public Island(GridPosition gridPosition, UUID ownerUuid, String biomeName, World.Environment dimension) {
        this.gridPosition = gridPosition;
        this.ownerUuid = ownerUuid;
        this.biomeName = biomeName;
        this.dimension = dimension;
        members.put(ownerUuid, IslandRank.OWNER);
    }

    // ====================== GETTERS ======================
    public GridPosition getGridPosition() { return gridPosition; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getBiomeName() { return biomeName; }

    public void setBiome(String biomeName) {
        this.biomeName = biomeName;
    }
    public World.Environment getDimension() { return dimension; }
    public int getLevel() { return level; }
    public double getXp() { return xp; }
    public double getXpForNextLevel() { return getRequiredXpForLevel(level + 1); }

    public Map<UUID, IslandRank> getMembers() { return Collections.unmodifiableMap(members); }
    public int getMemberCount() { return members.size(); }

    public IslandRank getRank(UUID playerUuid) {
        return members.getOrDefault(playerUuid, IslandRank.GUEST);
    }

    public boolean isOwner(UUID playerUuid) {
        return ownerUuid.equals(playerUuid);
    }

    public boolean isMember(UUID playerUuid) {
        return members.containsKey(playerUuid);
    }

    // ====================== XP & LEVELING ======================
    public void addXp(double amount, int partySize) {
        // Diminishing returns for larger parties (like Hypixel Skyblock)
        double multiplier = Math.max(0.5, 1.0 - (partySize - 1) * 0.1);
        double adjustedXp = amount * multiplier;

        this.xp += adjustedXp;

        // Check for level up
        while (xp >= getRequiredXpForLevel(level + 1)) {
            xp -= getRequiredXpForLevel(level + 1);
            level++;
        }
    }

    public void addXp(double amount) {
        addXp(amount, 1);
    }

    private double getRequiredXpForLevel(int targetLevel) {
        return BASE_XP_PER_LEVEL * targetLevel * targetLevel;
    }

    // ====================== MEMBER MANAGEMENT ======================
    public void addMember(UUID playerUuid) {
        if (!members.containsKey(playerUuid)) {
            members.put(playerUuid, IslandRank.GUEST);
        }
    }

    public void removeMember(UUID playerUuid) {
        if (!playerUuid.equals(ownerUuid)) {
            members.remove(playerUuid);
        }
    }

    public void setMemberRank(UUID playerUuid, IslandRank rank) {
        if (members.containsKey(playerUuid) && !playerUuid.equals(ownerUuid)) {
            members.put(playerUuid, rank);
        }
    }

    public void transferOwnership(UUID newOwnerUuid) {
        if (members.containsKey(newOwnerUuid)) {
            members.put(ownerUuid, IslandRank.GUEST);
            members.put(newOwnerUuid, IslandRank.OWNER);
            this.ownerUuid = newOwnerUuid;
        }
    }

    // ====================== CENTER LOCATION ======================
    public Location getCenter(World world) {
        if (world.getEnvironment() != this.dimension) return null;
        int centerX = gridPosition.x() * 128 + 64;
        int centerZ = gridPosition.z() * 128 + 64;
        return new Location(world, centerX, 100, centerZ);
    }

    // ====================== SPAWN LOCATION ======================
    private Location spawnLocation;

    public Location getSpawnLocation() {
        if (spawnLocation != null) return spawnLocation;
        // Default to center of island
        return getCenter(null);
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
    }

    // ====================== PERMISSIONS ======================
    public boolean hasPermission(UUID playerUuid, IslandPermission permission) {
        IslandRank rank = getRank(playerUuid);
        return rank != null && rank.hasPermission(permission);
    }

    // ====================== SERIALIZATION ======================
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("grid_x", gridPosition.x());
        data.put("grid_z", gridPosition.z());
        data.put("dimension", dimension.name());
        data.put("owner_uuid", ownerUuid.toString());
        data.put("biome_name", biomeName);
        data.put("level", level);
        data.put("xp", xp);
        return data;
    }

    public static Island deserialize(Map<String, Object> data) {
        GridPosition pos = new GridPosition(
                (int) data.get("grid_x"),
                (int) data.get("grid_z"),
                World.Environment.valueOf((String) data.get("dimension"))
        );

        UUID owner = UUID.fromString((String) data.get("owner_uuid"));
        String biome = (String) data.get("biome_name");

        Island island = new Island(pos, owner, biome, pos.getDimension());
        island.level = (int) data.get("level");
        island.xp = (double) data.get("xp");

        return island;
    }
}