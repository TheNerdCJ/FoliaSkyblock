package com.thenerdcj.boss;

import org.bukkit.entity.EntityType;

/**
 * Natural Minecraft bosses with level requirements for dimension progression
 * Custom bosses can be added by extending this enum
 */
public enum DimensionBoss {

    // ============================================
    // OVERWORLD BOSSES (Level 1-25)
    // ============================================

    /** Elder Guardian - Found in Ocean Monuments */
    ELDER_GUARDIAN("Elder Guardian", EntityType.ELDER_GUARDIAN, 1, 25, "Overworld",
            "§7Spawns naturally in Ocean Monuments"),

    /** Warden - Found in Deep Dark */
    WARDEN("Warden", EntityType.WARDEN, 10, 25, "Overworld",
            "§7Spawns in Deep Dark when Sculk Shrieker is triggered"),

    /** Evoker - Found in Woodland Mansions */
    EVOKER("Evoker", EntityType.EVOKER, 5, 25, "Overworld",
            "§7Spawns in Woodland Mansions"),

    /** Ravager - Found in Raids */
    RAVAGER("Ravager", EntityType.RAVAGER, 15, 25, "Overworld",
            "§7Spawns during Raids"),

    /** Breeze - Found in Trial Chambers */
    BREEZE("Breeze", EntityType.BREEZE, 20, 25, "Overworld",
            "§7Spawns in Trial Chambers (1.21+)"),

    // ============================================
    // NETHER BOSSES (Level 26-50)
    // ============================================

    /** Wither - Crafted boss */
    WITHER("Wither", EntityType.WITHER, 26, 50, "Nether",
            "§7Craft with 4 Soul Sand + 3 Wither Skeleton Skulls"),

    /** Blaze - Natural Nether boss */
    BLAZE("Blaze", EntityType.BLAZE, 26, 40, "Nether",
            "§7Spawns naturally in Nether Fortresses"),

    /** Ghast - Natural Nether boss */
    GHAST("Ghast", EntityType.GHAST, 30, 45, "Nether",
            "§7Spawns naturally in Nether"),

    /** Piglin Brute - Natural Nether boss */
    PIGLIN_BRUTE("Piglin Brute", EntityType.PIGLIN_BRUTE, 35, 50, "Nether",
            "§7Spawns in Bastion Remnants"),

    // ============================================
    // END BOSSES (Level 51-100)
    // ============================================

    /** Ender Dragon - Final boss of Minecraft */
    ENDER_DRAGON("Ender Dragon", EntityType.ENDER_DRAGON, 51, 100, "End",
            "§7Natural End boss - Defeat to win the game!"),

    /** Shulker - End City boss */
    SHULKER("Shulker", EntityType.SHULKER, 55, 80, "End",
            "§7Spawns in End Cities"),

    /** Enderman - Natural End mob (can be boss-like) */
    ENDERMAN("Enderman", EntityType.ENDERMAN, 60, 90, "End",
            "§7Spawns naturally in The End"),

    /** Phantom - Can spawn in End */
    PHANTOM("Phantom", EntityType.PHANTOM, 70, 100, "End",
            "§7Spawns at night in The End");

    private final String displayName;
    private final EntityType entityType;
    private final int minLevel;
    private final int maxLevel;
    private final String dimension;
    private final String spawnInfo;

    DimensionBoss(String displayName, EntityType entityType, int minLevel, int maxLevel, String dimension, String spawnInfo) {
        this.displayName = displayName;
        this.entityType = entityType;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.dimension = dimension;
        this.spawnInfo = spawnInfo;
    }

    public String getDisplayName() { return displayName; }
    public EntityType getEntityType() { return entityType; }
    public int getMinLevel() { return minLevel; }
    public int getMaxLevel() { return maxLevel; }
    public String getDimension() { return dimension; }
    public String getSpawnInfo() { return spawnInfo; }

    public boolean isAvailableAtLevel(int level) {
        return level >= minLevel && level <= maxLevel;
    }

    public static DimensionBoss[] getBossesForDimension(String dimension) {
        return java.util.Arrays.stream(values())
                .filter(b -> b.dimension.equalsIgnoreCase(dimension))
                .toArray(DimensionBoss[]::new);
    }

    /**
     * Add custom bosses here in the future
     * Example:
     * CUSTOM_BOSS_1("Custom Boss 1", EntityType.ZOMBIE, 30, 50, "Overworld", "§7Custom spawn egg"),
     */
}