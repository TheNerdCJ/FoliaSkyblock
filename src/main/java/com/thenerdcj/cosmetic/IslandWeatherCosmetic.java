package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Cosmetic Island Weather Effects.
 * Purely visual particle "weather" overlays active on the player's island (visible to visitors).
 * Play-to-Win, no actual Bukkit weather changes (no gameplay impact on farming etc.).
 * Triggered on island enter like music/structures/furniture.
 * Folia-safe particle spawning.
 */
public enum IslandWeatherCosmetic {

    NONE("None", "§7No cosmetic weather", PetRarity.COMMON, 0, 0, "none", "Clear skies"),

    // Basic / early unlocks
    GENTLE_RAIN("Gentle Rain", "§bLight water droplets and mist", PetRarity.COMMON, 1, 20, "rain", "Refreshing shower"),
    SNOW_FLURRY("Snow Flurry", "§fSoft falling snowflakes", PetRarity.COMMON, 1, 25, "snow", "Winter calm"),

    // Uncommon
    MEADOW_POLLEN("Meadow Pollen", "§aFloating green particles and light", PetRarity.UNCOMMON, 2, 45, "pollen", "Spring breeze"),
    EMBER_SHOWER("Ember Shower", "§6Soft glowing embers", PetRarity.UNCOMMON, 2, 50, "ember", "Warm twilight"),

    // Rare atmospheric
    MYSTIC_FOG("Mystic Fog", "§5Swirling purple mist and sparkles", PetRarity.RARE, 3, 75, "fog", "Enchanted veil"),
    STARFALL("Starfall", "§eFalling stars and glitter", PetRarity.RARE, 3, 80, "starfall", "Night magic"),

    // Epic
    AURORA("Aurora Borealis", "§d§lShifting green/purple sky ribbons", PetRarity.EPIC, 4, 130, "aurora", "Polar lights"),
    SLAYER_TEMPEST("Slayer Tempest", "§4§lRed winds and trophy sparks", PetRarity.EPIC, 4, 145, "tempest", "Hunt in the storm"),

    // High / prestige
    CELESTIAL_GLOW("Celestial Glow", "§b§lSoft heavenly light + stars", PetRarity.LEGENDARY, 5, 200, "celestial", "Divine"),
    VOID_MIST("Void Mist", "§8§lDeep space particles and whispers", PetRarity.LEGENDARY, 6, 0, "void", "Cosmic prestige"), // prestige gated
    RAINBOW_MIST("Rainbow Mist", "§c§lColor cycling fog and sparkles", PetRarity.LEGENDARY, 6, 220, "rainbow", "Prismatic"),
    BLOOM_SHOWER("Bloom Shower", "§d§lPetal and flower particles", PetRarity.EPIC, 4, 135, "bloom", "Floral spring"),
    LAVA_GLOW("Lava Glow", "§c§lWarm lava sparkles and heat haze", PetRarity.EPIC, 4, 125, "lava", "Nether warmth"),
    CRYSTAL_RAIN("Crystal Rain", "§b§lShimmering crystal droplets", PetRarity.RARE, 3, 85, "crystal", "Mystic downpour"),

    // Overall refinements - more weather variety
    SANDSTORM("Sandstorm", "§6Whirling sand and dust devils", PetRarity.RARE, 3, 90, "sand", "Desert fury"),
    FIREFLY_GLOW("Firefly Glow", "§aBlinking lights in the dusk", PetRarity.UNCOMMON, 2, 55, "firefly", "Twilight magic");

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final String weatherKey; // internal theme for manager particles
    private final String flavor;

    IslandWeatherCosmetic(String displayName, String description, PetRarity rarity,
                          int minPrestige, int tokenCost, String weatherKey, String flavor) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.weatherKey = weatherKey;
        this.flavor = flavor;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public PetRarity getRarity() { return rarity; }
    public int getMinPrestige() { return minPrestige; }
    public int getTokenCost() { return tokenCost; }
    public String getWeatherKey() { return weatherKey; }
    public String getFlavor() { return flavor; }

    public boolean isNone() { return this == NONE; }
}
