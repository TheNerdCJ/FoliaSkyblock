package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Light Accessory Cosmetics - small floating visual items around the player.
 * Purely cosmetic (no stats/power). Uses ItemDisplay/TextDisplay for visuals + particles.
 * Play-to-Win gated.
 * Similar to Overhead but accessory themed (orbiting, floating items).
 */
public enum AccessoryCosmetic {

    NONE("None", "§7No accessory", PetRarity.COMMON, 0, 0, "none"),

    // Basic
    FLOATING_STAR("Floating Star", "§eSmall orbiting star", PetRarity.UNCOMMON, 2, 40, "star"),
    ORBITING_ORB("Orbiting Orb", "§bGlowing orb circling player", PetRarity.UNCOMMON, 2, 45, "orb"),

    // Mid
    FLOATING_SWORD("Floating Sword", "§7Small decorative sword", PetRarity.RARE, 3, 80, "sword"),
    BALLOON("Cosmetic Balloon", "§dBobbing balloon on string", PetRarity.RARE, 3, 85, "balloon"),

    // High
    MYSTIC_COMPANION("Mystic Companion", "§5Small spirit-like float", PetRarity.EPIC, 4, 140, "companion"),
    CROWN_ACCESSORY("Crown Accessory", "§6Floating crown piece", PetRarity.EPIC, 4, 150, "crown"),

    // Prestige
    DRAGON_COMPANION("Dragon Companion", "§4§lMini dragon wings/float", PetRarity.LEGENDARY, 6, 0, "dragon"),
    RAINBOW_ORBIT("Rainbow Orbit", "§c§lColor shifting orbiting particles", PetRarity.LEGENDARY, 6, 200, "rainbow"),

    // Polish variety
    FLOATING_LANTERN("Floating Lantern", "§eGlowing lantern bob", PetRarity.EPIC, 4, 110, "lantern"),
    ORBITING_SHIELD("Orbiting Shield", "§7Protective shield float", PetRarity.RARE, 3, 75, "shield"),

    // More accessory variety (autonomous polish)
    FLOATING_COMPASS("Floating Compass", "§eNavigational spinning charm", PetRarity.UNCOMMON, 2, 55, "compass"),
    ORBITING_CRYSTAL("Orbiting Crystal", "§dPrismatic glowing crystal", PetRarity.EPIC, 4, 145, "crystal"),
    FEATHERED_WING("Feathered Winglet", "§fLight angelic wing accessory", PetRarity.RARE, 3, 70, "feather"),

    // Overall refinements - more accessory
    FLOATING_KEY("Floating Key", "§6Mysterious golden key", PetRarity.RARE, 3, 78, "key"),
    ORBITING_GEM("Orbiting Gem", "§dSparkling jewel", PetRarity.EPIC, 4, 155, "gem"),

    // Continue autonomous variety polish
    MYSTIC_ORB("Mystic Orb", "§5Enchanted floating orb", PetRarity.EPIC, 4, 160, "mystic_orb"),
    SLAYER_TROPHY("Slayer Trophy Float", "§cTrophy of the hunt accessory", PetRarity.LEGENDARY, 5, 220, "slayer_trophy"),

    // Accessory variety continuation (autonomous)
    FLOATING_BOOK("Floating Tome", "§bGlowing spellbook companion", PetRarity.EPIC, 4, 135, "book"),
    ORBITING_RUNE("Orbiting Rune", "§6Mystic spinning rune", PetRarity.RARE, 3, 90, "rune"),

    // Accessory expansions (autonomous next)
    ORBITING_SWORD("Orbiting Sword", "§7Spinning blade charm", PetRarity.EPIC, 4, 140, "sword"),
    GLOWING_LANTERN("Glowing Lantern Charm", "§eWarm bobbing light", PetRarity.RARE, 3, 85, "lantern");

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final String accessoryType;

    AccessoryCosmetic(String displayName, String description, PetRarity rarity,
                      int minPrestige, int tokenCost, String accessoryType) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.accessoryType = accessoryType;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public PetRarity getRarity() { return rarity; }
    public int getMinPrestige() { return minPrestige; }
    public int getTokenCost() { return tokenCost; }
    public String getAccessoryType() { return accessoryType; }

    public boolean isNone() { return this == NONE; }
}
