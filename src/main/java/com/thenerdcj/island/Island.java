package com.thenerdcj.island;

import com.thenerdcj.database.GridPosition;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island class - Fully updated with Party System support
 */
public class Island {

    private final GridPosition gridPosition;
    private UUID ownerUuid;
    private String biomeName;
    private final World.Environment dimension;

    // Party members: UUID -> Rank
    private final Map<UUID, IslandRank> members = new ConcurrentHashMap<>();

    // Leveling system
    private int level = 1;
    private double xp = 0.0;

    public Island(GridPosition gridPosition, UUID ownerUuid, String biomeName, World.Environment dimension) {
        this.gridPosition = gridPosition;
        this.ownerUuid = ownerUuid;
        this.biomeName = biomeName;
        this.dimension = dimension;

        // Owner is automatically OWNER rank
        members.put(ownerUuid, IslandRank.OWNER);
    }

    // ====================== BASIC GETTERS ======================
    public GridPosition getGridPosition() {
        return gridPosition;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getBiomeName() {
        return biomeName;
    }

    public World.Environment getDimension() {
        return dimension;
    }

    public int getLevel() {
        return level;
    }

    public double getXp() {
        return xp;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void addXp(double amount) {
        this.xp += amount;
        // You can add level-up logic here
    }

    // ====================== PARTY SYSTEM ======================
    public Map<UUID, IslandRank> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public boolean isOwner(UUID uuid) {
        return ownerUuid.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public IslandRank getRank(UUID uuid) {
        return members.getOrDefault(uuid, IslandRank.GUEST);
    }

    public void addMember(UUID uuid, IslandRank rank) {
        if (!isOwner(uuid)) {
            members.put(uuid, rank);
        }
    }

    public void removeMember(UUID uuid) {
        if (!isOwner(uuid)) {
            members.remove(uuid);
        }
    }

    public void setMemberRank(UUID uuid, IslandRank rank) {
        if (!isOwner(uuid) && members.containsKey(uuid)) {
            members.put(uuid, rank);
        }
    }

    public void transferOwnership(UUID newOwnerUuid) {
        if (members.containsKey(newOwnerUuid)) {
            members.put(ownerUuid, IslandRank.GUEST); // Demote old owner
            members.put(newOwnerUuid, IslandRank.OWNER);
            this.ownerUuid = newOwnerUuid;
        }
    }

    // ====================== LOCATION HELPERS ======================
    public Location getCenter(World world) {
        if (world.getEnvironment() != this.dimension) return null;
        int centerX = gridPosition.x() * 128 + 64;
        int centerZ = gridPosition.z() * 128 + 64;
        return new Location(world, centerX, 100, centerZ);
    }

    // ====================== PERMISSION CHECK ======================
    public boolean hasPermission(UUID playerUuid, IslandPermission permission) {
        IslandRank rank = getRank(playerUuid);
        return rank != null && rank.hasPermission(permission);
    }
}