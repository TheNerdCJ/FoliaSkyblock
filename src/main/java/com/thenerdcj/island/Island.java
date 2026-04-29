package com.thenerdcj.island;

import com.thenerdcj.database.GridPosition;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island class with Permission Caching for maximum performance
 */
public class Island {
    private final GridPosition gridPosition;
    private UUID ownerUuid;
    private String biomeName;
    private final World.Environment dimension;
    private final Map<UUID, IslandRank> members = new ConcurrentHashMap<>();
    private int level = 1;
    private double xp = 0.0;

    // ====================== PERMISSION CACHE ======================
    private final ConcurrentHashMap<String, Boolean> permissionCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60000; // 1 minute cache TTL
    private long lastCacheClear = System.currentTimeMillis();

    public Island(GridPosition gridPosition, UUID ownerUuid, String biomeName, World.Environment dimension) {
        this.gridPosition = gridPosition;
        this.ownerUuid = ownerUuid;
        this.biomeName = biomeName;
        this.dimension = dimension;
        members.put(ownerUuid, IslandRank.OWNER);
    }

    public GridPosition getGridPosition() { return gridPosition; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getBiomeName() { return biomeName; }
    public World.Environment getDimension() { return dimension; }
    public int getLevel() { return level; }
    public double getXp() { return xp; }
    public void setLevel(int level) { this.level = level; clearPermissionCache(); }
    public void addXp(double amount) { this.xp += amount; }

    public Map<UUID, IslandRank> getMembers() { return Collections.unmodifiableMap(members); }
    public boolean isOwner(UUID uuid) { return ownerUuid.equals(uuid); }
    public boolean isMember(UUID uuid) { return members.containsKey(uuid); }
    public IslandRank getRank(UUID uuid) { return members.getOrDefault(uuid, IslandRank.GUEST); }
    public void addMember(UUID uuid, IslandRank rank) { if (!isOwner(uuid)) { members.put(uuid, rank); clearPermissionCache(); } }
    public void removeMember(UUID uuid) { if (!isOwner(uuid)) { members.remove(uuid); clearPermissionCache(); } }
    public void setMemberRank(UUID uuid, IslandRank rank) { if (!isOwner(uuid) && members.containsKey(uuid)) { members.put(uuid, rank); clearPermissionCache(); } }
    public void transferOwnership(UUID newOwnerUuid) {
        if (members.containsKey(newOwnerUuid)) {
            members.put(ownerUuid, IslandRank.GUEST);
            members.put(newOwnerUuid, IslandRank.OWNER);
            this.ownerUuid = newOwnerUuid;
            clearPermissionCache();
        }
    }
    public Location getCenter(World world) { if (world.getEnvironment() != this.dimension) return null; int centerX = gridPosition.x() * 128 + 64; int centerZ = gridPosition.z() * 128 + 64; return new Location(world, centerX, 100, centerZ); }

    // ====================== PERMISSION CACHE METHODS ======================

    private void clearPermissionCache() {
        permissionCache.clear();
        lastCacheClear = System.currentTimeMillis();
    }

    private boolean isPermissionCacheValid() {
        return (System.currentTimeMillis() - lastCacheClear) < CACHE_TTL_MS;
    }

    public boolean hasPermission(UUID playerUuid, IslandPermission permission) {
        String cacheKey = playerUuid.toString() + ":" + permission.name();

        // Check cache first
        if (isPermissionCacheValid() && permissionCache.containsKey(cacheKey)) {
            return permissionCache.get(cacheKey);
        }

        // Calculate permission
        IslandRank rank = getRank(playerUuid);
        boolean result = rank != null && rank.hasPermission(permission);

        // Cache the result
        permissionCache.put(cacheKey, result);

        return result;
    }
}