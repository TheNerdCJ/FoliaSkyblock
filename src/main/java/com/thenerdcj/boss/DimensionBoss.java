package com.thenerdcj.boss;

import org.bukkit.entity.EntityType;

/**
 * Natural Minecraft bosses with level requirements for dimension progression.
 * Custom bosses can be added by extending this enum.
 * 
 * UPDATED for endgame: Added more dimension-specific high-level bosses and
 * endgame content references for The End dimension (level 51+).
 * Resources like chorus, shulker boxes, ender pearls can be tied to these bosses.
 */
public enum DimensionBoss {

    // ============================================
    // OVERWORLD BOSSES (Level 1-25)
    // ============================================

    /** Elder Guardian - Found in Ocean Monuments */
    ELDER_GUARDIAN("Elder Guardian", EntityType.ELDER_GUARDIAN, 1, 25, "Overworld",
            "Spawns naturally in Ocean Monuments"),

    /** Warden - Found in Deep Dark */
    WARDEN("Warden", EntityType.WARDEN, 10, 25, "Overworld",
            "Spawns in Deep Dark when Sculk Shrieker is triggered"),

    /** Evoker - Found in Woodland Mansions */
    EVOKER("Evoker", EntityType.EVOKER, 5, 25, "Overworld",
            "Spawns in Woodland Mansions"),

    /** Ravager - Found in Raids */
    RAVAGER("Ravager", EntityType.RAVAGER, 15, 25, "Overworld",
            "Spawns during Raids"),

    /** Breeze - Found in Trial Chambers */
    BREEZE("Breeze", EntityType.BREEZE, 20, 25, "Overworld",
            "Spawns in Trial Chambers (1.21+)"),

    // ============================================
    // NETHER BOSSES (Level 26-50)
    // ============================================

    /** Wither - Crafted boss */
    WITHER("Wither", EntityType.WITHER, 26, 50, "Nether",
            "Craft with 4 Soul Sand + 3 Wither Skeleton Skulls"),

    /** Blaze - Natural Nether boss */
    BLAZE("Blaze", EntityType.BLAZE, 26, 40, "Nether",
            "Spawns naturally in Nether Fortresses"),

    /** Ghast - Natural Nether boss */
    GHAST("Ghast", EntityType.GHAST, 30, 45, "Nether",
            "Spawns naturally in Nether"),

    /** Piglin Brute - Natural Nether boss */
    PIGLIN_BRUTE("Piglin Brute", EntityType.PIGLIN_BRUTE, 35, 50, "Nether",
            "Spawns in Bastion Remnants"),

    // ============================================
    // END BOSSES (Level 51-100) - ENDGAME CONTENT
    // ============================================

    /** Ender Dragon - Final boss of Minecraft - Core endgame */
    ENDER_DRAGON("Ender Dragon", EntityType.ENDER_DRAGON, 51, 100, "End",
            "Natural End boss - Defeat to win the game! Drops Dragon Egg + XP. Endgame milestone."),

    /** Shulker - End City boss - Endgame resource farm */
    SHULKER("Shulker", EntityType.SHULKER, 55, 80, "End",
            "Spawns in End Cities. Drops Shulker Shells for shulker boxes. Key endgame storage upgrade."),

    /** Enderman - Natural End mob (can be boss-like) */
    ENDERMAN("Enderman", EntityType.ENDERMAN, 60, 90, "End",
            "Spawns naturally in The End. Drops Ender Pearls - essential for endgame travel and crafting."),

    /** Phantom - Can spawn in End */
    PHANTOM("Phantom", EntityType.PHANTOM, 70, 100, "End",
            "Spawns at night in The End. Drops Phantom Membrane for endgame potions."),

    // NEW: Additional endgame dimension-specific bosses for high-level islands
    /** Enderite Golem - Custom endgame boss idea (use custom entity or wither-like) */
    ENDERITE_GOLEM("Enderite Golem", EntityType.IRON_GOLEM, 75, 100, "End",
            "ENDGAME: A powerful golem infused with End energy. Drops rare 'Enderite' fragments for custom gear."),

    /** Corrupted Warden - End dimension variant for ultimate challenge */
    CORRUPTED_WARDEN("Corrupted Warden", EntityType.WARDEN, 85, 100, "End",
            "ULTIMATE ENDGAME: A warden twisted by the End's void. Massive health, drops unique void resources and trophies."),

    /** Shulker King - Raid-like endgame boss */
    SHULKER_KING("Shulker King", EntityType.SHULKER, 90, 100, "End",
            "ENDGAME EVENT: Leads shulker swarms in End Cities. High tier loot including enchanted shells and end crystals."),

    // ============================================
    // SLAYER TIERS (Hypixel-inspired repeatable bosses - tiers 1-5+)
    // Unique drops feed the island trading system & bazaar (Play to Win grind)
    // ============================================

    /** Tier 1-5 Zombie Slayer (Revenant) - Low level repeatable */
    REVENANT_HORROR_T1("Revenant Horror T1", EntityType.ZOMBIE, 5, 20, "Overworld",
            "Slayer Tier 1: Basic zombie boss. Drops: Revenant Flesh (trade for coins or craft zombie gear)"),
    REVENANT_HORROR_T5("Revenant Horror T5", EntityType.ZOMBIE, 30, 60, "Overworld",
            "Slayer Tier 5: Hardcore zombie. Drops: Scythe Blade, Shard, Beheaded (high value trade items for island balance)"),

    /** Spider Slayer */
    TARANTULA_BROODMOTHER_T1("Tarantula Broodmother T1", EntityType.SPIDER, 8, 25, "Overworld",
            "Slayer Tier 1: Spider boss. Drops: Tarantula Web (craft bow upgrades, sell in bazaar)"),
    TARANTULA_BROODMOTHER_T4("Tarantula Broodmother T4", EntityType.SPIDER, 25, 55, "Overworld",
            "Slayer Tier 4: Tough spider. Drops: Toxic Arrow Poison, Spider Catalyst (valuable trade goods)"),

    /** Enderman Slayer (End dimension focused) */
    VOIDGLOOM_SERAPH_T1("Voidgloom Seraph T1", EntityType.ENDERMAN, 20, 40, "End",
            "Slayer Tier 1: Enderman boss. Drops: Null Sphere (trade for end resources)"),
    VOIDGLOOM_SERAPH_T4("Voidgloom Seraph T4", EntityType.ENDERMAN, 45, 80, "End", "Slayer Tier 4: Powerful void boss. Drops: Null Edge, Ender Catalyst"),


    /** Blaze/Inferno Demonlord Slayer (Nether focused, Hypixel-inspired repeatable boss)
     *  Fits the Play to Win slayer grind for island trading / bazaar economy.
     */
    DEMONLORD_T2("Inferno Demonlord T2", EntityType.BLAZE, 25, 55, "Nether",
            "Slayer Tier 2: Mid-level blaze boss. Drops: Demon Horn, Inferno Catalyst (trade for nether resources or island balance)"),
    DEMONLORD_T5("Inferno Demonlord T5", EntityType.BLAZE, 55, 95, "Nether",
            "Slayer Tier 5: Elite blaze slayer. Drops: Fiery Blade, Demon Core (high value for island upgrades and trading)");
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
    public String getName() { return displayName; } // Alias for compatibility
    public EntityType getEntityType() { return entityType; }
    public int getMinLevel() { return minLevel; }
    public int getMaxLevel() { return maxLevel; }
    public String getDimension() { return dimension; }
    public String getSpawnInfo() { return spawnInfo; }

    /**
     * Get default health for this boss
     * Can be customized per boss type. Endgame bosses have higher health.
     */
    public double getHealth() {
        switch (this) {
            case ENDER_DRAGON: return 200.0;
            case WITHER: return 150.0;
            case WARDEN: return 500.0;
            case ELDER_GUARDIAN: return 80.0;
            case EVOKER: return 24.0;
            case RAVAGER: return 100.0;
            case BREEZE: return 30.0;
            case BLAZE: return 20.0;
            case GHAST: return 10.0;
            case PIGLIN_BRUTE: return 50.0;
            case SHULKER: return 30.0;
            case ENDERMAN: return 40.0;
            case PHANTOM: return 20.0;
            case ENDERITE_GOLEM: return 350.0; // Strong endgame
            case CORRUPTED_WARDEN: return 800.0; // Ultimate
            case SHULKER_KING: return 450.0;
            case REVENANT_HORROR_T1: return 120.0;
            case REVENANT_HORROR_T5: return 800.0;
            case TARANTULA_BROODMOTHER_T1: return 150.0;
            case TARANTULA_BROODMOTHER_T4: return 600.0;
            case VOIDGLOOM_SERAPH_T1: return 200.0;
            case VOIDGLOOM_SERAPH_T4: return 700.0;
            case DEMONLORD_T2: return 180.0;
            case DEMONLORD_T5: return 650.0;
            default: return 50.0;
        }
    }

    public boolean isAvailableAtLevel(int level) {
        return level >= minLevel && level <= maxLevel;
    }

    public static DimensionBoss[] getBossesForDimension(String dimension) {
        return java.util.Arrays.stream(values())
                .filter(b -> b.dimension.equalsIgnoreCase(dimension))
                .toArray(DimensionBoss[]::new);
    }

    /**
     * Get endgame-specific bosses (high level End content)
     */
    public static DimensionBoss[] getEndgameBosses() {
        return java.util.Arrays.stream(values())
                .filter(b -> "End".equalsIgnoreCase(b.dimension) && b.minLevel >= 70)
                .toArray(DimensionBoss[]::new);
    }

    /**
     * Unique drops for this boss/slayer tier.
     * These items feed the island trading system (bazaar sell for player balance, or /trade for island balance).
     * Play to Win: Drops are grindable via slayer quests, also obtainable via trading from others.
     * Referenced from Hypixel Slayer drops (Revenant, Tarantula, Voidgloom, Inferno).
     */
    public java.util.List<org.bukkit.inventory.ItemStack> getUniqueDrops() {
        java.util.List<org.bukkit.inventory.ItemStack> drops = new java.util.ArrayList<>();
        switch (this) {
            case REVENANT_HORROR_T1:
            case REVENANT_HORROR_T5:
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ROTTEN_FLESH, 32));
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 4)); // High tier bonus
                break;
            case TARANTULA_BROODMOTHER_T1:
            case TARANTULA_BROODMOTHER_T4:
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.STRING, 64));
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.SPIDER_EYE, 16));
                break;
            case VOIDGLOOM_SERAPH_T1:
            case VOIDGLOOM_SERAPH_T4:
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENDER_PEARL, 16));
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.OBSIDIAN, 8));
                break;
            case DEMONLORD_T2:
            case DEMONLORD_T5:
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BLAZE_ROD, 8));
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.MAGMA_CREAM, 12));
                break;
            case ENDER_DRAGON:
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DRAGON_EGG, 1));
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ELYTRA, 1)); // Rare endgame
                break;
            default:
                drops.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.EXPERIENCE_BOTTLE, 8));
        }
        return drops;
    }

    /**
     * Whether this is a slayer-type boss (repeatable, tiered, for slayer quests)
     */
    public boolean isSlayerBoss() {
        return name().contains("REVENANT") || name().contains("TARANTULA") || name().contains("VOIDGLOOM") || name().contains("DEMONLORD");
    }
}
