package com.thenerdcj.boss;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.List;

/**
 * Slayer Tiers - Hypixel-style slayer progression system
 * Each tier has increasing difficulty, XP requirements, and better rewards
 */
public enum SlayerTier {

    // ============================================
    // ZOMBIE SLAYER (Overworld - Beginner)
    // ============================================

    ZOMBIE_I("Zombie Slayer I", EntityType.ZOMBIE, 1, 10, 100, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.ROTTEN_FLESH, 32, 0.8),
                    new SlayerReward(Material.BONE, 16, 0.5),
                    new SlayerReward(Material.IRON_INGOT, 4, 0.2)
            ),
            "§7Kill zombies to progress. Reward: Basic combat items"),

    ZOMBIE_II("Zombie Slayer II", EntityType.ZOMBIE, 2, 25, 250, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.ROTTEN_FLESH, 64, 0.9),
                    new SlayerReward(Material.BONE, 32, 0.6),
                    new SlayerReward(Material.IRON_INGOT, 8, 0.3),
                    new SlayerReward(Material.GOLD_INGOT, 4, 0.15)
            ),
            "§7Increased zombie spawns. Better rewards!"),

    ZOMBIE_III("Zombie Slayer III", EntityType.ZOMBIE, 3, 50, 500, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.ROTTEN_FLESH, 128, 1.0),
                    new SlayerReward(Material.BONE, 64, 0.7),
                    new SlayerReward(Material.IRON_INGOT, 16, 0.4),
                    new SlayerReward(Material.GOLD_INGOT, 8, 0.25),
                    new SlayerReward(Material.DIAMOND, 2, 0.1)
            ),
            "§7Zombie horde event! Rare drops available!"),

    ZOMBIE_IV("Zombie Slayer IV", EntityType.ZOMBIE, 4, 100, 1000, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.ROTTEN_FLESH, 256, 1.0),
                    new SlayerReward(Material.BONE, 128, 0.8),
                    new SlayerReward(Material.IRON_INGOT, 32, 0.5),
                    new SlayerReward(Material.GOLD_INGOT, 16, 0.35),
                    new SlayerReward(Material.DIAMOND, 4, 0.2),
                    new SlayerReward(Material.EMERALD, 2, 0.1)
            ),
            "§7§lEPIC: Zombie boss event! Guaranteed rare drops!"),

    ZOMBIE_V("Zombie Slayer V", EntityType.ZOMBIE, 5, 200, 2500, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.ROTTEN_FLESH, 512, 1.0),
                    new SlayerReward(Material.BONE, 256, 0.9),
                    new SlayerReward(Material.IRON_INGOT, 64, 0.6),
                    new SlayerReward(Material.GOLD_INGOT, 32, 0.45),
                    new SlayerReward(Material.DIAMOND, 8, 0.3),
                    new SlayerReward(Material.EMERALD, 4, 0.2),
                    new SlayerReward(Material.NETHERITE_SCRAP, 1, 0.05)
            ),
            "§6§lLEGENDARY: Zombie apocalypse! Ultimate rewards!"),

    // ============================================
    // SPIDER SLAYER (Overworld - Intermediate)
    // ============================================

    SPIDER_I("Spider Slayer I", EntityType.SPIDER, 1, 15, 150, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.STRING, 32, 0.8),
                    new SlayerReward(Material.SPIDER_EYE, 16, 0.6),
                    new SlayerReward(Material.IRON_INGOT, 4, 0.2)
            ),
            "§7Kill spiders to progress. Reward: String & eyes"),

    SPIDER_II("Spider Slayer II", EntityType.SPIDER, 2, 30, 300, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.STRING, 64, 0.9),
                    new SlayerReward(Material.SPIDER_EYE, 32, 0.7),
                    new SlayerReward(Material.IRON_INGOT, 8, 0.3),
                    new SlayerReward(Material.GOLD_INGOT, 4, 0.15)
            ),
            "§7Cave spider infestation! Better rewards!"),

    SPIDER_III("Spider Slayer III", EntityType.SPIDER, 3, 60, 600, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.STRING, 128, 1.0),
                    new SlayerReward(Material.SPIDER_EYE, 64, 0.8),
                    new SlayerReward(Material.IRON_INGOT, 16, 0.4),
                    new SlayerReward(Material.GOLD_INGOT, 8, 0.25),
                    new SlayerReward(Material.DIAMOND, 2, 0.1)
            ),
            "§7§lEPIC: Spider queen event! Rare drops!"),

    SPIDER_IV("Spider Slayer IV", EntityType.SPIDER, 4, 120, 1200, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.STRING, 256, 1.0),
                    new SlayerReward(Material.SPIDER_EYE, 128, 0.9),
                    new SlayerReward(Material.IRON_INGOT, 32, 0.5),
                    new SlayerReward(Material.GOLD_INGOT, 16, 0.35),
                    new SlayerReward(Material.DIAMOND, 4, 0.2),
                    new SlayerReward(Material.EMERALD, 2, 0.1)
            ),
            "§6§lLEGENDARY: Arachnid apocalypse! Ultimate rewards!"),

    // ============================================
    // ENDERMAN SLAYER (End - Advanced)
    // ============================================

    ENDERMAN_I("Enderman Slayer I", EntityType.ENDERMAN, 1, 50, 500, "End",
            Arrays.asList(
                    new SlayerReward(Material.ENDER_PEARL, 16, 0.8),
                    new SlayerReward(Material.ENDER_EYE, 8, 0.5),
                    new SlayerReward(Material.DIAMOND, 4, 0.3)
            ),
            "§7Kill Endermen to progress. Reward: Ender pearls"),

    ENDERMAN_II("Enderman Slayer II", EntityType.ENDERMAN, 2, 100, 1000, "End",
            Arrays.asList(
                    new SlayerReward(Material.ENDER_PEARL, 32, 0.9),
                    new SlayerReward(Material.ENDER_EYE, 16, 0.6),
                    new SlayerReward(Material.DIAMOND, 8, 0.4),
                    new SlayerReward(Material.EMERALD, 4, 0.2)
            ),
            "§7§lEPIC: Enderman swarm! Rare drops!"),

    ENDERMAN_III("Enderman Slayer III", EntityType.ENDERMAN, 3, 200, 2500, "End",
            Arrays.asList(
                    new SlayerReward(Material.ENDER_PEARL, 64, 1.0),
                    new SlayerReward(Material.ENDER_EYE, 32, 0.8),
                    new SlayerReward(Material.DIAMOND, 16, 0.5),
                    new SlayerReward(Material.EMERALD, 8, 0.3),
                    new SlayerReward(Material.NETHERITE_SCRAP, 2, 0.1)
            ),
            "§6§lLEGENDARY: Enderman apocalypse! Ultimate rewards!"),

    // ============================================
    // BLAZE SLAYER (Nether - Intermediate)
    // ============================================

    BLAZE_I("Blaze Slayer I", EntityType.BLAZE, 1, 30, 300, "Nether",
            Arrays.asList(
                    new SlayerReward(Material.BLAZE_ROD, 8, 0.8),
                    new SlayerReward(Material.GLOWSTONE_DUST, 16, 0.6),
                    new SlayerReward(Material.GOLD_INGOT, 8, 0.3)
            ),
            "§7Kill Blazes to progress. Reward: Blaze rods"),

    BLAZE_II("Blaze Slayer II", EntityType.BLAZE, 2, 60, 600, "Nether",
            Arrays.asList(
                    new SlayerReward(Material.BLAZE_ROD, 16, 0.9),
                    new SlayerReward(Material.GLOWSTONE_DUST, 32, 0.7),
                    new SlayerReward(Material.GOLD_INGOT, 16, 0.4),
                    new SlayerReward(Material.DIAMOND, 4, 0.2)
            ),
            "§7§lEPIC: Blaze fortress event! Rare drops!"),

    BLAZE_III("Blaze Slayer III", EntityType.BLAZE, 3, 120, 1500, "Nether",
            Arrays.asList(
                    new SlayerReward(Material.BLAZE_ROD, 32, 1.0),
                    new SlayerReward(Material.GLOWSTONE_DUST, 64, 0.8),
                    new SlayerReward(Material.GOLD_INGOT, 32, 0.5),
                    new SlayerReward(Material.DIAMOND, 8, 0.3),
                    new SlayerReward(Material.NETHERITE_SCRAP, 2, 0.15)
            ),
            "§6§lLEGENDARY: Blaze inferno! Ultimate rewards!"),

    // ============================================
    // NEW EXPANDED TIERS
    // ============================================

    SKELETON_I("Skeleton Slayer I", EntityType.SKELETON, 1, 20, 200, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.BONE, 32, 0.9),
                    new SlayerReward(Material.ARROW, 64, 0.8),
                    new SlayerReward(Material.IRON_INGOT, 6, 0.25)
            ),
            "§7Classic skeleton hunting. Good for early gear."),

    CREEPER_I("Creeper Slayer I", EntityType.CREEPER, 1, 35, 400, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.GUNPOWDER, 16, 0.85),
                    new SlayerReward(Material.TNT, 2, 0.15),
                    new SlayerReward(Material.DIAMOND, 1, 0.08)
            ),
            "§7Careful with the explosions!"),

    // High-tier "Boss Slayer" quests that tie into custom bosses
    ZOMBIE_BOSS_SLAYER("Zombie Boss Slayer", EntityType.ZOMBIE, 6, 150, 4000, "Overworld",
            Arrays.asList(
                    new SlayerReward(Material.NETHERITE_INGOT, 1, 0.15),
                    new SlayerReward("CRATE_EPIC", 1, 0.25),           // New: Crate key
                    new SlayerReward("BOOSTER_MINION_60", 1, 0.20)     // New: Booster token
            ),
            "§c§lSummons a powerful Zombie Lord boss!"),

    // New high-end content
    WITHER_SKELETON_SLAYER("Wither Skeleton Slayer", EntityType.WITHER_SKELETON, 4, 180, 3500, "Nether",
            Arrays.asList(
                    new SlayerReward(Material.COAL, 128, 0.9),
                    new SlayerReward(Material.NETHER_STAR, 1, 0.08),
                    new SlayerReward("CRATE_LEGENDARY", 1, 0.12)
            ),
            "§8§lExtremely dangerous. High value rewards!"),

    DRAGON_SLAYER("Ender Dragon Slayer", EntityType.ENDER_DRAGON, 5, 250, 8000, "End",
            Arrays.asList(
                    new SlayerReward(Material.DRAGON_EGG, 1, 0.05),
                    new SlayerReward("PRESTIGE_100", 1, 0.30),
                    new SlayerReward("BOOSTER_ISLAND_XP_120", 1, 0.25)
            ),
            "§5§lThe ultimate slayer challenge!"),

    // Task 4 expanded (Hypixel YT slayer progression: T5+ , inferno/blaze synergy, more drops like pet luck, rare mats)
    INFERNO_SLAYER("Inferno Slayer V", EntityType.BLAZE, 5, 180, 4500, "Nether",
            Arrays.asList(
                    new SlayerReward(Material.MAGMA_CREAM, 64, 1.0),
                    new SlayerReward(Material.NETHERITE_INGOT, 2, 0.2),
                    new SlayerReward("MINION_INFERNO", 1, 0.15),  // special minion unlock
                    new SlayerReward("PET_INFERNO", 1, 0.08)
            ),
            "§6§lInferno Demon Lord - matches top server depth!");

    private final String displayName;
    private final EntityType targetEntity;
    private final int tier;
    private final int minLevel;
    private final int xpRequired;
    private final String dimension;
    private final List<SlayerReward> rewards;
    private final String description;

    SlayerTier(String displayName, EntityType targetEntity, int tier, int minLevel,
               int xpRequired, String dimension, List<SlayerReward> rewards, String description) {
        this.displayName = displayName;
        this.targetEntity = targetEntity;
        this.tier = tier;
        this.minLevel = minLevel;
        this.xpRequired = xpRequired;
        this.dimension = dimension;
        this.rewards = rewards;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public EntityType getTargetEntity() { return targetEntity; }
    public int getTier() { return tier; }
    public int getMinLevel() { return minLevel; }
    public int getXpRequired() { return xpRequired; }
    public String getDimension() { return dimension; }
    public List<SlayerReward> getRewards() { return rewards; }
    public String getDescription() { return description; }

    public static SlayerTier[] getTiersForEntity(EntityType entityType) {
        return Arrays.stream(values())
                .filter(t -> t.targetEntity == entityType)
                .toArray(SlayerTier[]::new);
    }

    public SlayerTier getNextTier() {
        SlayerTier[] tiers = getTiersForEntity(targetEntity);
        for (int i = 0; i < tiers.length - 1; i++) {
            if (tiers[i] == this) {
                return tiers[i + 1];
            }
        }
        return null;
    }

    public boolean canStart(int playerLevel, int currentTierProgress) {
        return playerLevel >= minLevel && currentTierProgress >= (tier - 1) * 100;
    }
}