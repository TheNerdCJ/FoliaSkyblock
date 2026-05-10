package com.thenerdcj.island;

import com.thenerdcj.database.GridPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core Island object with full leveling system.
 * XP scales based on party size (diminishing returns for larger parties).
 * Added dimension unlock and online members for IslandXPListener and design compliance.
 */
public class Island {

    private final GridPosition gridPosition;
    private UUID ownerUuid;
    private String biomeName;
    private final World.Environment dimension;

    private final Map<UUID, IslandRank> members = new ConcurrentHashMap<>();
    private int level = 1;
    private double xp = 0.0;

    // Dimension unlock flags (for progression to Nether/End)
    private boolean netherUnlocked = false;
    private boolean endUnlocked = false;

    // XP required for next level (quadratic scaling)
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

    public boolean isOwner(UUID uuid) { return ownerUuid.equals(uuid); }
    public boolean isMember(UUID uuid) { return members.containsKey(uuid); }
    public IslandRank getRank(UUID uuid) { return members.getOrDefault(uuid, IslandRank.GUEST); }

    // ====================== MEMBER MANAGEMENT ======================
    public void addMember(UUID uuid, IslandRank rank) {
        if (!isOwner(uuid)) members.put(uuid, rank);
    }

    public void removeMember(UUID uuid) {
        if (!isOwner(uuid)) members.remove(uuid);
    }

    public void setMemberRank(UUID uuid, IslandRank rank) {
        if (!isOwner(uuid) && members.containsKey(uuid)) members.put(uuid, rank);
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

    // ====================== LEVELING SYSTEM (balanced for party) ======================
    public void addXp(double baseAmount, int partySize) {
        if (baseAmount <= 0) return;
        if (partySize <= 0) partySize = 1;

        double multiplier = calculateXpMultiplier(partySize);
        double effectiveXp = baseAmount * multiplier;

        this.xp += effectiveXp;
        checkLevelUp();
    }

    public void addXp(double amount) {
        addXp(amount, 1);
    }

    private double calculateXpMultiplier(int partySize) {
        if (partySize == 1) return 1.0;
        if (partySize == 2) return 0.85;
        if (partySize == 3) return 0.75;
        return Math.max(0.55, 1.0 - (partySize - 1) * 0.12);
    }

    private double getRequiredXpForLevel(int targetLevel) {
        return BASE_XP_PER_LEVEL * targetLevel * targetLevel;
    }

    private void checkLevelUp() {
        while (xp >= getRequiredXpForLevel(level + 1)) {
            int oldLevel = level;
            xp -= getRequiredXpForLevel(level + 1);
            level++;

            IslandLevelUpEvent event = new IslandLevelUpEvent(this, oldLevel, level, ownerUuid);
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    public double getProgressToNextLevel() {
        double required = getRequiredXpForLevel(level + 1);
        return Math.min(1.0, xp / required);
    }

    public void setLevel(int newLevel) {
        this.level = Math.max(1, newLevel);
        this.xp = 0;
    }

    public void setXp(double newXp) {
        this.xp = Math.max(0, newXp);
    }

    // ====================== DIMENSION UNLOCK (for IslandXPListener and progression design) ======================
    public boolean hasUnlockedNether() {
        return netherUnlocked || level >= 10; // Example threshold; tune based on config/boss defeats
    }

    public boolean hasUnlockedEnd() {
        return endUnlocked || level >= 25;
    }

    public void unlockDimension(String dimensionName) {
        if ("nether".equalsIgnoreCase(dimensionName)) {
            this.netherUnlocked = true;
        } else if ("end".equalsIgnoreCase(dimensionName) || "the_end".equalsIgnoreCase(dimensionName)) {
            this.endUnlocked = true;
        }
        // Could persist to DB via IslandManager or listener
    }

    // ====================== ONLINE MEMBERS (for party XP calc and anti-cheat) ======================
    public List<Player> getOnlineMembers() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : members.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                online.add(p);
            }
        }
        return online;
    }

    // Optional: get online member count for XP multiplier
    public int getOnlineMemberCount() {
        return getOnlineMembers().size();
    }
}
