package com.thenerdcj.pets;

import org.bukkit.Material;

/**
 * Cosmetic skins that can be applied to pets to change their appearance.
 * 
 * Follows the same design as PetVariant, PlayerTagVariant, etc.
 * 
 * Skins are Play-to-Win only (prestige, slayer, collection rewards).
 * They primarily change the head texture and can add unique particle effects.
 */
public enum PetSkin {

    NONE("None", "", "§7No skin applied", PetRarity.COMMON, 0, 0, null),

    // Universal / Color skins (can apply to most pets)
    NEON("Neon", "§b✦", "§7Bright glowing style", PetRarity.UNCOMMON, 2, 30, "MHF_Chicken"),
    SHADOW("Shadow", "§8☁", "§7Dark and mysterious", PetRarity.UNCOMMON, 2, 35, "MHF_Enderman"),
    GOLDEN("Golden", "§6★", "§7Luxurious golden finish", PetRarity.RARE, 4, 80, "MHF_Villager"),
    CRYSTAL("Crystal", "§b❄", "§7Refractive crystal look", PetRarity.RARE, 4, 90, "MHF_Guardian"),
    RAINBOW("Rainbow", "§d✺", "§7Color shifting magic", PetRarity.EPIC, 5, 150, "MHF_Parrot"),

    // Pet-specific premium skins
    BABY_DRAGON_FIRE("Inferno Dragon", "§4🔥", "§7Flaming dragon variant", PetRarity.EPIC, 5, 200, "MHF_EnderDragon"),
    PHOENIX_ASCENDED("Ascended Phoenix", "§6☀", "§7Evolved fiery form", PetRarity.LEGENDARY, 6, 0, "MHF_Blaze"), // prestige reward
    ALLAY_CELESTIAL("Celestial Allay", "§b✧", "§7Heavenly light bearer", PetRarity.EPIC, 5, 180, "MHF_Allay"),
    AXOLOTL_GLOW("Glowing Axolotl", "§a☢", "§7Bioluminescent edition", PetRarity.RARE, 3, 70, "MHF_Axolotl"),

    // Collection / Slayer rewards
    SLAYER_BEAST("Slayer Beast", "§c⚔", "§7Trophy of the hunt", PetRarity.EPIC, 4, 220, "MHF_Wolf"),
    COLLECTOR_PRIDE("Collector's Pride", "§6◆", "§7Show off your collection", PetRarity.LEGENDARY, 7, 0, "MHF_Villager"); // high prestige

    private final String displayName;
    private final String iconSuffix;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final String overrideHeadOwner; // null = use the pet's normal head

    PetSkin(String displayName, String iconSuffix, String description, 
            PetRarity rarity, int minPrestige, int tokenCost, String overrideHeadOwner) {
        this.displayName = displayName;
        this.iconSuffix = iconSuffix;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.overrideHeadOwner = overrideHeadOwner;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconSuffix() {
        return iconSuffix;
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

    public String getOverrideHeadOwner() {
        return overrideHeadOwner;
    }

    public boolean isNone() {
        return this == NONE;
    }

    /**
     * Returns the head owner that should be used when this skin is applied.
     * Falls back to the pet's default if no override.
     */
    public String getEffectiveHeadOwner(PetType baseType) {
        if (overrideHeadOwner != null) {
            return overrideHeadOwner;
        }
        return baseType.getHeadTextureOwner();
    }
}
