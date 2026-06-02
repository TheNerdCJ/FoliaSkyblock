package com.thenerdcj.pets;

import org.bukkit.Material;

/**
 * Rarity tiers for cosmetic pets.
 * Used for collection XP rewards (modeled after wardrobe equipment collection)
 * and future prestige/slayer gating + display.
 *
 * Play-to-Win: higher rarity pets are harder to unlock but remain purely cosmetic.
 */
public enum PetRarity {

    COMMON("Common", "§7", 10, Material.GRAY_DYE),
    UNCOMMON("Uncommon", "§a", 25, Material.LIME_DYE),
    RARE("Rare", "§b", 50, Material.CYAN_DYE),
    EPIC("Epic", "§5", 100, Material.PURPLE_DYE),
    LEGENDARY("Legendary", "§6", 200, Material.ORANGE_DYE),
    MYTHIC("Mythic", "§d", 350, Material.MAGENTA_DYE);

    private final String displayName;
    private final String colorCode;
    private final int xpReward;
    private final Material icon;

    PetRarity(String displayName, String colorCode, int xpReward, Material icon) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.xpReward = xpReward;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public int getXpReward() {
        return xpReward;
    }

    public Material getIcon() {
        return icon;
    }

    public String getColoredName() {
        return colorCode + displayName;
    }
}
