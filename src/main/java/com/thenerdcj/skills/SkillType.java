package com.thenerdcj.skills;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Player Skill Types inspired by MCMMO + project island skills.
 * Per-player progression, XP from actions, levels unlock abilities.
 * Folia-safe design.
 */
public enum SkillType {
    MINING("Mining", "Mine ores and stone", 1.0),
    WOODCUTTING("Woodcutting", "Chop trees and logs", 1.0),
    FARMING("Farming", "Harvest crops and farm", 1.0),
    FISHING("Fishing", "Catch fish and treasures", 1.0),
    COMBAT("Combat", "Fight mobs and players", 1.0),
    EXCAVATION("Excavation", "Dig dirt, gravel, sand", 1.0),
    ACROBATICS("Acrobatics", "Fall, dodge, roll", 1.0),
    REPAIR("Repair", "Repair tools and armor", 1.0);

    private final String displayName;
    private final String description;
    private final double baseXpMultiplier;

    SkillType(String displayName, String description, double baseXpMultiplier) {
        this.displayName = displayName;
        this.description = description;
        this.baseXpMultiplier = baseXpMultiplier;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public double getBaseXpMultiplier() { return baseXpMultiplier; }

    // Helper to map actions to skill (simplified for complete system)
    public static SkillType fromBlockBreak(Material material) {
        if (material.name().contains("ORE") || material == Material.STONE || material == Material.COBBLESTONE || material.name().contains("DEEPSLATE")) {
            return MINING;
        }
        if (material.name().contains("LOG") || material.name().contains("WOOD")) {
            return WOODCUTTING;
        }
        if (material.name().contains("DIRT") || material == Material.GRAVEL || material == Material.SAND || material == Material.CLAY) {
            return EXCAVATION;
        }
        if (isFarmMaterial(material)) {
            return FARMING;
        }
        return null;
    }

    public static SkillType fromMobKill(EntityType type) {
        if (type != null) return COMBAT;
        return null;
    }

    public static SkillType fromFish() {
        return FISHING;
    }

    private static boolean isFarmMaterial(Material m) {
        return m == Material.WHEAT || m == Material.CARROTS || m == Material.POTATOES ||
               m == Material.BEETROOTS || m == Material.PUMPKIN || m == Material.MELON ||
               m == Material.SUGAR_CANE || m == Material.CACTUS || m == Material.NETHER_WART ||
               m == Material.COCOA;
    }
}