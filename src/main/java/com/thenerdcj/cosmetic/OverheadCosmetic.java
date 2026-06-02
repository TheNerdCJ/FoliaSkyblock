package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Advanced Overhead Cosmetics using TextDisplay / ItemDisplay entities.
 * Purely visual effects floating above the player's head (titles, halos, icons, particles).
 * Play-to-Win only.
 */
public enum OverheadCosmetic {

    NONE("None", "§7No overhead effect", PetRarity.COMMON, 0, 0, null),

    // Basic
    STAR_HALO("Star Halo", "§eGentle sparkling halo", PetRarity.UNCOMMON, 2, 40, "halo"),
    FLOATING_HEART("Floating Heart", "§cSmall heart bobbing above head", PetRarity.UNCOMMON, 2, 45, "heart"),

    // Mid
    PLAYER_TITLE("Custom Title", "§bSmall text title above head", PetRarity.RARE, 3, 80, "title"),
    CHAMPION_TITLE("Champion Title", "§6Bold champion title for the worthy", PetRarity.EPIC, 4, 150, "title"),
    MYSTIC_TITLE("Mystic Title", "§5Arcane floating mystic title", PetRarity.LEGENDARY, 6, 0, "title"),
    ENCHANT_GLOW("Enchant Glow", "§5Enchantment table particles + glow", PetRarity.RARE, 3, 85, "glow"),

    // High
    CELESTIAL_AURA("Celestial Aura", "§dStarry particles and soft light", PetRarity.EPIC, 4, 140, "aura"),
    SLAYER_CROWN("Slayer Crown", "§cTrophy crown effect", PetRarity.EPIC, 5, 160, "crown"),

    // Prestige
    LEGENDARY_AURA("Legendary Aura", "§6§lEpic particle aura + title", PetRarity.LEGENDARY, 6, 0, "legendary"), // prestige only

    // More variety
    RAINBOW_HALO("Rainbow Halo", "§c§lColor shifting halo", PetRarity.LEGENDARY, 6, 0, "rainbow"),
    DRAGON_WINGS("Dragon Wings", "§4§lEpic dragon wing particles", PetRarity.LEGENDARY, 6, 0, "dragon"),

    // Titles expansion / polish variety
    FLOATING_BOOK("Floating Book", "§aMystical book orbiting above", PetRarity.LEGENDARY, 6, 0, "book"),
    GLOWING_EYES("Glowing Eyes", "§cEerie glowing eyes effect", PetRarity.EPIC, 5, 135, "eyes"),
    ICE_AURA("Ice Aura", "§bChilling frost particles and glow", PetRarity.EPIC, 4, 125, "ice"),

    // Deeper titles (autonomous continuation)
    RUNIC_TITLE("Runic Title", "§6Ancient glowing runes", PetRarity.EPIC, 5, 150, "title"),
    VOID_CROWN("Void Crown", "§5§lShifting dark crown", PetRarity.LEGENDARY, 6, 0, "title"),

    // Continued deeper titles polish
    CELESTIAL_TITLE("Celestial Title", "§bStarry divine title", PetRarity.EPIC, 5, 145, "title"),
    SLAYER_SIGIL("Slayer Sigil", "§cBlood-red slayer emblem title", PetRarity.LEGENDARY, 6, 0, "title"),
    ETHEREAL_CROWN("Ethereal Crown", "§dTranslucent spirit crown", PetRarity.EPIC, 4, 155, "title");

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final String effectType; // internal hint for manager

    OverheadCosmetic(String displayName, String description, PetRarity rarity,
                     int minPrestige, int tokenCost, String effectType) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.effectType = effectType;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public PetRarity getRarity() { return rarity; }
    public int getMinPrestige() { return minPrestige; }
    public int getTokenCost() { return tokenCost; }
    public String getEffectType() { return effectType; }

    public boolean isNone() { return this == NONE; }
}
