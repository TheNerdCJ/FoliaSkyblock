package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Cosmetic Power Orb Skins for FoliaSkyblock.
 * 
 * Purely visual overrides for Power Orbs (combat utility items).
 * Play-to-Win only.
 * 
 * Inspired by Hypixel Skyblock Fire Sale Power Orb Skins (e.g. Disco Ball, Gem, etc.).
 */
public enum PowerOrbSkin {

    NONE("None", "§7No skin applied", PetRarity.COMMON, 0, 0, 0),

    // Basic
    DISCO_BALL("Disco Ball", "§d§lSparkly party orb", PetRarity.UNCOMMON, 2, 40, 3001),
    GEM("Gem Power Orb", "§bShiny gem theme", PetRarity.UNCOMMON, 2, 45, 3002),

    // Mid-tier
    ABYSSAL("Abyssal Orb", "§8Deep void aesthetic", PetRarity.RARE, 3, 70, 3003),
    FLORAL("Floral Bloom", "§aNature-inspired", PetRarity.RARE, 3, 65, 3004),

    // Premium
    CELESTIAL("Celestial Orb", "§dStarry cosmic", PetRarity.EPIC, 4, 120, 3005),
    DRACONIC("Draconic", "§cDragon scale theme", PetRarity.EPIC, 5, 140, 3006),

    // High-end
    SUPREME("Supreme Orb", "§6§lUltimate prestige skin", PetRarity.LEGENDARY, 6, 0, 3007), // prestige reward
    SLAYER_PRIDE("Slayer Pride", "§c⚔ Trophy orb", PetRarity.LEGENDARY, 6, 200, 3008);

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final int customModelData;

    PowerOrbSkin(String displayName, String description, PetRarity rarity,
                 int minPrestige, int tokenCost, int customModelData) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.customModelData = customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public PetRarity getRarity() {
        return rarity;
    }

    public int getMinPrestige() {
        return minPrestige;
    }

    public int getTokenCost() {
        return tokenCost;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public boolean isNone() {
        return this == NONE;
    }
}