package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;
import org.bukkit.Material;

/**
 * Cosmetic Backpack Skins for FoliaSkyblock.
 * 
 * Purely visual overrides for backpacks/storage items.
 * Play-to-Win only.
 * 
 * Inspired by Hypixel Skyblock Fire Sale Backpack Skins.
 * 
 * Note: This project does not yet have a dedicated backpack system.
 * This enum is created as part of exploration for the next cosmetic.
 * Implementation would require either:
 *   - Adding a basic custom backpack item/system, or
 *   - Applying skins to existing storage items (e.g., shulker boxes, chests in inventory).
 */
public enum BackpackSkin {

    NONE("None", "§7No skin applied", PetRarity.COMMON, 0, 0, 0),

    // Basic / Early
    GARDEN_BUNNY("Garden Bunny", "§aCute bunny theme", PetRarity.UNCOMMON, 1, 25, 2001),
    COZY_CABIN("Cozy Cabin", "§6Warm wooden cabin", PetRarity.UNCOMMON, 2, 30, 2002),

    // Popular / Mid
    OCEAN_TREASURE("Ocean Treasure", "§bUnderwater chest vibe", PetRarity.RARE, 3, 60, 2003),
    MYSTIC_FOREST("Mystic Forest", "§2Enchanted woods", PetRarity.RARE, 3, 65, 2004),

    // Premium
    DRAGON_HOARD("Dragon Hoard", "§cGolden dragon treasure", PetRarity.EPIC, 4, 110, 2005),
    VOID_STORAGE("Void Storage", "§8Ender-themed rift", PetRarity.EPIC, 5, 130, 2006),

    // High-end / Prestige
    CELESTIAL_VAULT("Celestial Vault", "§dStarry premium vault", PetRarity.LEGENDARY, 6, 0, 2007), // prestige reward
    SLAYER_TROPHY("Slayer Trophy", "§c⚔ Hunter's pride", PetRarity.LEGENDARY, 6, 180, 2008),

    // Additional variety
    MYSTIC_MUSHROOM("Mystic Mushroom", "§5Enchanted fungi", PetRarity.RARE, 3, 55, 2009),
    ABYSSAL_DEPTHS("Abyssal Depths", "§8Deep sea theme", PetRarity.EPIC, 5, 125, 2010);

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final int customModelData;

    BackpackSkin(String displayName, String description, PetRarity rarity,
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