package com.thenerdcj.boss;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

/**
 * Slayer Achievements System
 *
 * Achievements unlock automatically when conditions are met
 * Each achievement has rewards (XP, items, titles)
 */
public enum SlayerAchievement {

    // ============================================
    // BEGINNER ACHIEVEMENTS
    // ============================================

    FIRST_BLOOD("First Blood", "Complete your first slayer quest",
            AchievementCategory.BEGINNER, 1, 100,
            Arrays.asList(new AchievementReward(Material.IRON_SWORD, 1)),
            "§7Complete any slayer quest to unlock"),

    SLAYER_APPRENTICE("Slayer Apprentice", "Complete 5 slayer quests",
            AchievementCategory.BEGINNER, 5, 250,
            Arrays.asList(new AchievementReward(Material.GOLD_INGOT, 10)),
            "§7Complete 5 total slayer quests"),

    KILL_STREAK_10("Kill Streak I", "Get 10 kills in a single quest",
            AchievementCategory.BEGINNER, 10, 150,
            Arrays.asList(new AchievementReward(Material.EXPERIENCE_BOTTLE, 5)),
            "§7Get 10 kills without completing the quest"),

    // ============================================
    // INTERMEDIATE ACHIEVEMENTS
    // ============================================

    ZOMBIE_SLAYER("Zombie Slayer", "Complete all Zombie Slayer tiers (I-V)",
            AchievementCategory.INTERMEDIATE, 5, 500,
            Arrays.asList(new AchievementReward(Material.DIAMOND_SWORD, 1)),
            "§7Complete Zombie Slayer V"),

    SPIDER_SLAYER("Spider Slayer", "Complete all Spider Slayer tiers (I-IV)",
            AchievementCategory.INTERMEDIATE, 4, 400,
            Arrays.asList(new AchievementReward(Material.BOW, 1)),
            "§7Complete Spider Slayer IV"),

    BLAZE_SLAYER("Blaze Slayer", "Complete all Blaze Slayer tiers (I-III)",
            AchievementCategory.INTERMEDIATE, 3, 350,
            Arrays.asList(new AchievementReward(Material.BLAZE_ROD, 16)),
            "§7Complete Blaze Slayer III"),

    ENDERMAN_SLAYER("Enderman Slayer", "Complete all Enderman Slayer tiers (I-III)",
            AchievementCategory.INTERMEDIATE, 3, 450,
            Arrays.asList(new AchievementReward(Material.ENDER_PEARL, 32)),
            "§7Complete Enderman Slayer III"),

    SLAYER_VETERAN("Slayer Veteran", "Complete 25 total slayer quests",
            AchievementCategory.INTERMEDIATE, 25, 750,
            Arrays.asList(new AchievementReward(Material.EMERALD, 10)),
            "§7Complete 25 total slayer quests"),

    // ============================================
    // ADVANCED ACHIEVEMENTS
    // ============================================

    SLAYER_MASTER("Slayer Master", "Complete ALL slayer tiers across all entities",
            AchievementCategory.ADVANCED, 15, 1500,
            Arrays.asList(new AchievementReward(Material.NETHERITE_SWORD, 1)),
            "§7Complete every single slayer tier"),

    KILL_STREAK_100("Kill Streak II", "Get 100 kills in a single quest",
            AchievementCategory.ADVANCED, 100, 800,
            Arrays.asList(new AchievementReward(Material.TOTEM_OF_UNDYING, 1)),
            "§7Get 100 kills without completing the quest"),

    LEGENDARY_DROPS("Legendary Hunter", "Obtain 10 legendary drops from slayer quests",
            AchievementCategory.ADVANCED, 10, 1000,
            Arrays.asList(new AchievementReward(Material.NETHERITE_INGOT, 1)),
            "§7Get 10 legendary (5% chance) drops"),

    SPEED_DEMON("Speed Demon", "Complete a tier V quest in under 5 minutes",
            AchievementCategory.ADVANCED, 1, 600,
            Arrays.asList(new AchievementReward(Material.CLOCK, 1)),
            "§7Complete Zombie Slayer V in < 5 minutes"),

    // ============================================
    // LEGENDARY ACHIEVEMENTS
    // ============================================

    SLAYER_LEGEND("Slayer Legend", "Reach 1000 total slayer kills",
            AchievementCategory.LEGENDARY, 1000, 2500,
            Arrays.asList(new AchievementReward(Material.DRAGON_EGG, 1)),
            "§7Accumulate 1000 total slayer kills"),

    ULTIMATE_SLAYER("Ultimate Slayer", "Complete every achievement",
            AchievementCategory.LEGENDARY, 12, 5000,
            Arrays.asList(new AchievementReward(Material.BEACON, 1)),
            "§7Unlock all other slayer achievements"),

    PERFECT_WEEK("Perfect Week", "Complete at least 1 quest every day for 7 days",
            AchievementCategory.LEGENDARY, 7, 2000,
            Arrays.asList(new AchievementReward(Material.NETHER_STAR, 1)),
            "§7Complete quests on 7 consecutive days");

    private final String displayName;
    private final String description;
    private final AchievementCategory category;
    private final int requirement;
    private final int xpReward;
    private final List<AchievementReward> itemRewards;
    private final String unlockCondition;

    SlayerAchievement(String displayName, String description, AchievementCategory category,
                      int requirement, int xpReward, List<AchievementReward> itemRewards,
                      String unlockCondition) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.requirement = requirement;
        this.xpReward = xpReward;
        this.itemRewards = itemRewards;
        this.unlockCondition = unlockCondition;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public AchievementCategory getCategory() { return category; }
    public int getRequirement() { return requirement; }
    public int getXpReward() { return xpReward; }
    public List<AchievementReward> getItemRewards() { return itemRewards; }
    public String getUnlockCondition() { return unlockCondition; }

    /**
     * Check if this achievement is unlocked based on progress
     */
    public boolean isUnlocked(int progress) {
        return progress >= requirement;
    }

    /**
     * Get progress percentage (0.0 to 1.0)
     */
    public double getProgress(int currentProgress) {
        return Math.min(1.0, (double) currentProgress / requirement);
    }
}

/**
 * Achievement categories for organization
 */
