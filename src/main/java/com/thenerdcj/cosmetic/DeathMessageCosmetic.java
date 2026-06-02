package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Cosmetic Kill/Death Messages for FoliaSkyblock.
 * 
 * Purely cosmetic text messages broadcast on player death or kills.
 * Play-to-Win only.
 * 
 * Can be combined with DeathEffect particles.
 * Inspired by Hypixel final kill messages and chat cosmetics.
 */
public enum DeathMessageCosmetic {

    NONE("None", "§7No custom message", PetRarity.COMMON, 0, 0),

    // Basic
    CLASSIC("Classic Slayer", "§c%victim% was slain by %killer%", PetRarity.UNCOMMON, 1, 20),
    BLOODY("Bloody End", "§4%victim% met a bloody end at the hands of %killer%", PetRarity.UNCOMMON, 2, 35),

    // Mid
    HONORABLE("Honorable Defeat", "§e%victim% fell honorably to %killer%", PetRarity.RARE, 3, 50),
    SHADOW("Shadow Strike", "§8%killer% struck %victim% from the shadows", PetRarity.RARE, 3, 60),

    // Advanced
    EPIC("Epic Fail", "§6%victim% suffered an epic defeat by %killer%", PetRarity.EPIC, 4, 90),
    COSMIC("Cosmic Annihilation", "§5%victim% was cosmically annihilated by %killer%", PetRarity.EPIC, 5, 120),

    // High-end
    LEGENDARY("Legendary Kill", "§b%killer% achieved a legendary kill on %victim%", PetRarity.LEGENDARY, 6, 0), // prestige
    VOID("Void Embrace", "§7%victim% was embraced by the void thanks to %killer%", PetRarity.LEGENDARY, 6, 200),

    // Variety
    FUNNY("Funny Bone", "§d%victim% got their funny bone broken by %killer%", PetRarity.RARE, 3, 55),
    MYSTIC("Mystic Curse", "§5%victim% succumbed to %killer%'s mystic curse", PetRarity.EPIC, 5, 110);

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;

    DeathMessageCosmetic(String displayName, String description, PetRarity rarity,
                         int minPrestige, int tokenCost) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
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

    public boolean isNone() {
        return this == NONE;
    }
}