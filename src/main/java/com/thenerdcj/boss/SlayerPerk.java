package com.thenerdcj.boss;

import org.bukkit.entity.EntityType;

/**
 * Slayer Perks - Passive bonuses unlocked through slayer progression.
 * These provide meaningful power that makes grinding slayers rewarding.
 */
public enum SlayerPerk {

    ZOMBIE_RESISTANCE("Zombie Resistance", "Take less damage from Zombies", EntityType.ZOMBIE, 3),
    SPIDER_AGILITY("Spider Agility", "Increased movement speed when fighting spiders", EntityType.SPIDER, 3),
    ENDERMAN_TELEPORT("Enderman Insight", "Chance to not be teleported by Endermen", EntityType.ENDERMAN, 2),
    BLAZE_FIRE_RESIST("Blaze Fire Resist", "Reduced fire damage from Blazes", EntityType.BLAZE, 3),
    SKELETON_ARCHERY("Skeleton Precision", "Increased arrow damage against Skeletons", EntityType.SKELETON, 2),
    CREEPER_EXPLOSION("Creeper Safety", "Reduced explosion damage from Creepers", EntityType.CREEPER, 2);

    private final String displayName;
    private final String description;
    private final EntityType targetEntity;
    private final int requiredTier; // Tier level required to unlock

    SlayerPerk(String displayName, String description, EntityType targetEntity, int requiredTier) {
        this.displayName = displayName;
        this.description = description;
        this.targetEntity = targetEntity;
        this.requiredTier = requiredTier;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public EntityType getTargetEntity() { return targetEntity; }
    public int getRequiredTier() { return requiredTier; }

    public boolean isUnlocked(int currentTier) {
        return currentTier >= requiredTier;
    }
}