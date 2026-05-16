package com.thenerdcj.island;

import com.thenerdcj.database.GridPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete Island class for FoliaSkyblock.
 *
 * Features:
 * - Core island data, members, permissions, spawn.
 * - Leveling & XP system with party balancing.
 * - Dimension unlock tracking.
 * - NEW: Deep progression via Skills (Mining, Farming, etc.) and Milestones.
 *   Skills level independently and contribute to island XP.
 *   Milestones provide meaningful unlocks for upgrades and dimensions.
 *
 * Fixes applied:
 * - Aligned center location calculation with GridManager (using 512 size).
 * - Proper initialization of skill maps.
 * - Complete original methods + new progression methods.
 * - Better persistence helpers.
 * - Consistent party size handling.
 */
public class Island {

    // Grid and core data
    private final GridPosition gridPosition;
    private UUID ownerUuid;
    private String biomeName;
    private final World.Environment dimension;

    // Members
    private final Map<UUID, IslandRank> members = new ConcurrentHashMap<>();

    // Leveling
    private int level = 1;
    private double xp = 0.0;
    private static final double BASE_XP_PER_LEVEL = 100.0;

    // Dimension unlocks
    private boolean netherUnlocked = false;
    private boolean endUnlocked = false;

    // Spawn
    private Location spawnLocation;

    // ==================== DEEP PROGRESSION: SKILLS ====================
    public enum Skill {
        MINING("Mining", "Break blocks and mine ores"),
        FARMING("Farming", "Harvest crops and tend animals"),
        COMBAT("Combat", "Defeat mobs and bosses"),
        BUILDING("Building", "Place blocks and construct"),
        FISHING("Fishing", "Catch fish and treasures"),
        ENCHANTING("Enchanting", "Enchant items and use anvils");

        private final String displayName;
        private final String description;

        Skill(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    private final Map<Skill, Double> skillXp = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> skillLevels = new EnumMap<>(Skill.class);
    private static final double SKILL_BASE_XP = 50.0;

    // Milestones
    private final Set<String> completedMilestones = ConcurrentHashMap.newKeySet();

    public Island(GridPosition gridPosition, UUID ownerUuid, String biomeName, World.Environment dimension) {
        this.gridPosition = gridPosition;
        this.ownerUuid = ownerUuid;
        this.biomeName = biomeName;
        this.dimension = dimension;
        members.put(ownerUuid, IslandRank.OWNER);

        // Initialize skills
        for (Skill s : Skill.values()) {
            skillXp.put(s, 0.0);
            skillLevels.put(s, 1);
        }
    }

    // ====================== CORE GETTERS & SETTERS ======================
    public GridPosition getGridPosition() { return gridPosition; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getBiomeName() { return biomeName; }
    public World.Environment getDimension() { return dimension; }
    public int getLevel() { return level; }
    public double getXp() { return xp; }

    public void setBiome(String biomeName) { this.biomeName = biomeName; }

    public String getId() {
        return ownerUuid.toString() + "_" + dimension.name().toLowerCase();
    }

    // ====================== MEMBER MANAGEMENT ======================
    public Map<UUID, IslandRank> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public int getMemberCount() { return members.size(); }
    public boolean isOwner(UUID uuid) { return ownerUuid.equals(uuid); }
    public boolean isMember(UUID uuid) { return members.containsKey(uuid); }
    public IslandRank getRank(UUID uuid) { return members.getOrDefault(uuid, IslandRank.GUEST); }

    public void addMember(UUID uuid, IslandRank rank) {
        if (!isOwner(uuid)) members.put(uuid, rank);
    }

    public void removeMember(UUID uuid) {
        if (!isOwner(uuid)) members.remove(uuid);
    }

    public void setMemberRank(UUID uuid, IslandRank rank) {
        if (!isOwner(uuid) && members.containsKey(uuid)) {
            members.put(uuid, rank);
        }
    }

    public void transferOwnership(UUID newOwnerUuid) {
        if (members.containsKey(newOwnerUuid)) {
            members.put(ownerUuid, IslandRank.GUEST);
            members.put(newOwnerUuid, IslandRank.OWNER);
            this.ownerUuid = newOwnerUuid;
        }
    }

    // ====================== LOCATION HELPERS (FIXED) ======================
    public Location getCenter(World world) {
        if (world == null || world.getEnvironment() != this.dimension) return null;
        // Fixed: Now uses 512 to match GridManager
        int centerX = gridPosition.x() * 512 + 256;
        int centerZ = gridPosition.z() * 512 + 256;
        int baseY = 80;
        return new Location(world, centerX, baseY, centerZ);
    }

    public Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation : getCenter(null);
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
    }

    // ====================== PERMISSIONS ======================
    public boolean hasPermission(UUID playerUuid, IslandPermission permission) {
        IslandRank rank = getRank(playerUuid);
        return rank != null && rank.hasPermission(permission);
    }

    // ====================== LEVELING SYSTEM (Party Balanced) ======================
    public void addXp(double baseAmount, int partySize) {
        if (baseAmount <= 0) return;
        if (partySize <= 0) partySize = 1;
        double multiplier = calculateXpMultiplier(partySize);
        this.xp += baseAmount * multiplier;
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

            // Fire the custom event
            IslandLevelUpEvent event = new IslandLevelUpEvent(this, oldLevel, level, ownerUuid);
            Bukkit.getPluginManager().callEvent(event);

            checkMilestoneUnlocks();
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

    public double getXpForNextLevel() {
        return getRequiredXpForLevel(level + 1);
    }

    // ====================== NEW: SKILL SYSTEM ======================
    public void addSkillXp(Skill skill, double amount, int partySize) {
        if (amount <= 0 || skill == null) return;
        double currentXp = skillXp.getOrDefault(skill, 0.0);
        double multiplier = calculateXpMultiplier(Math.max(1, partySize));
        double newXp = currentXp + (amount * multiplier);
        skillXp.put(skill, newXp);

        int currentLevel = skillLevels.getOrDefault(skill, 1);
        while (newXp >= getRequiredSkillXp(currentLevel + 1)) {
            newXp -= getRequiredSkillXp(currentLevel + 1);
            currentLevel++;
            skillLevels.put(skill, currentLevel);
            skillXp.put(skill, newXp);
            addXp(25.0 * currentLevel, partySize); // Bonus to island XP
        }
        addXp(amount * 0.3, partySize); // 30% contribution to island XP
    }

    public void addSkillXp(Skill skill, double amount) {
        addSkillXp(skill, amount, getOnlineMemberCount());
    }

    private double getRequiredSkillXp(int targetLevel) {
        return SKILL_BASE_XP * targetLevel * 1.5;
    }

    public int getSkillLevel(Skill skill) {
        return skillLevels.getOrDefault(skill, 1);
    }

    public double getSkillXp(Skill skill) {
        return skillXp.getOrDefault(skill, 0.0);
    }

    public double getSkillProgress(Skill skill) {
        int lvl = getSkillLevel(skill);
        double req = getRequiredSkillXp(lvl + 1);
        return Math.min(1.0, getSkillXp(skill) / req);
    }

    // ====================== NEW: MILESTONE SYSTEM ======================
    public boolean hasCompletedMilestone(String milestoneId) {
        return completedMilestones.contains(milestoneId);
    }

    public void completeMilestone(String milestoneId, double bonusXp) {
        if (completedMilestones.add(milestoneId)) {
            addXp(bonusXp, getOnlineMemberCount());
            checkMilestoneUnlocks();

            // Fire custom event
            IslandMilestoneCompleteEvent event = new IslandMilestoneCompleteEvent(this, milestoneId, bonusXp);
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    private void checkMilestoneUnlocks() {
        if (level >= 8 && hasCompletedMilestone("nether_access_milestone") && !netherUnlocked) {
            netherUnlocked = true;
        }
        if (level >= 20 && hasCompletedMilestone("end_access_milestone") && !endUnlocked) {
            endUnlocked = true;
        }
    }

    public boolean hasUnlockedNether() {
        return netherUnlocked || level >= 10 || hasCompletedMilestone("nether_access_milestone");
    }

    public boolean hasUnlockedEnd() {
        return endUnlocked || level >= 25 || hasCompletedMilestone("end_access_milestone");
    }

    public void unlockDimension(String dimensionName) {
        if ("nether".equalsIgnoreCase(dimensionName)) {
            this.netherUnlocked = true;
        } else if ("end".equalsIgnoreCase(dimensionName) || "the_end".equalsIgnoreCase(dimensionName)) {
            this.endUnlocked = true;
        }
    }

    // ====================== ONLINE MEMBERS ======================
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

    public int getOnlineMemberCount() {
        return getOnlineMembers().size();
    }

    // ====================== PERSISTENCE HELPERS ======================
    public Map<Skill, Double> getSkillXpMap() {
        return new EnumMap<>(skillXp);
    }

    public Map<Skill, Integer> getSkillLevelsMap() {
        return new EnumMap<>(skillLevels);
    }

    public Set<String> getCompletedMilestones() {
        return new HashSet<>(completedMilestones);
    }

    public void loadProgressionData(Map<Skill, Double> xpData, Map<Skill, Integer> levelData, Set<String> milestones) {
        if (xpData != null) this.skillXp.putAll(xpData);
        if (levelData != null) this.skillLevels.putAll(levelData);
        if (milestones != null) this.completedMilestones.addAll(milestones);
    }
}