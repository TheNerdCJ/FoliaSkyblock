package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Cosmetic Island Music & Ambient sounds for FoliaSkyblock.
 *
 * Play-to-Win only. Unlocked via prestige, slayer tokens, collection.
 * When set active on an island, plays ambient/music to visitors/members (cosmetic only).
 *
 * Uses vanilla sounds for compatibility. Custom resource pack can override.
 * Looping handled in IslandMusicManager with Folia-safe schedulers.
 */
public enum IslandMusicType {

    NONE("None", "§7No music or ambient playing", PetRarity.COMMON, 0, 0, "none", "Default silence"),

    // Calm / Relaxing
    CALM_OCEAN("Calm Ocean", "§bGentle waves and sea breeze", PetRarity.COMMON, 1, 25, "ambient.underwater.loop", "Ocean waves"),
    FOREST_WHISPERS("Forest Whispers", "§aBirds and rustling leaves", PetRarity.COMMON, 1, 30, "ambient.cave", "Peaceful woods"),

    // Mid
    MEADOW_BREEZE("Meadow Breeze", "§eSoft wind and distant chimes", PetRarity.UNCOMMON, 2, 50, "music.game", "Open fields"),
    DESERT_WINDS("Desert Winds", "§6Dunes and wind chimes", PetRarity.UNCOMMON, 2, 55, "block.sand.fall", "Arid calm"),

    // Atmospheric
    CAVE_AMBIENCE("Deep Cave", "§8Drips, echoes and mystery", PetRarity.RARE, 3, 80, "ambient.cave", "Underground feel"),
    NETHER_GLOW("Nether Glow", "§cLava bubbles and distant roars", PetRarity.RARE, 3, 85, "ambient.nether.loop", "Fiery atmosphere"),

    // Epic / Themed
    CELESTIAL_CHIMES("Celestial Chimes", "§dStars and ethereal tones", PetRarity.EPIC, 4, 140, "music.end", "Heavenly"),
    SLAYER_ECHOES("Slayer Echoes", "§4Battle drums and trophies", PetRarity.EPIC, 4, 160, "entity.ender_dragon.growl", "Triumphant hunt"),

    // High-end
    GRAND_SYMPHONY("Grand Symphony", "§6§lEpic orchestral swells", PetRarity.LEGENDARY, 5, 220, "music.creative", "Majestic"),
    VOID_WHISPERS("Void Whispers", "§5§lEndless night and stars", PetRarity.LEGENDARY, 6, 0, "music.end", "Cosmic prestige"), // prestige reward

    // Island ambience extensions (more variety/polish)
    JUNGLE_RHYTHM("Jungle Rhythm", "§aDense foliage and wildlife calls", PetRarity.UNCOMMON, 2, 52, "ambient.cave", "Tropical beats"),
    MOUNTAIN_WIND("Mountain Wind", "§7High altitude gusts and echoes", PetRarity.RARE, 3, 82, "ambient.cave", "Alpine calm"),
    TUNDRA_CHILL("Tundra Chill", "§bIcy winds and frozen silence", PetRarity.RARE, 3, 88, "ambient.cave", "Frigid expanse"),
    ANCIENT_RUINS("Ancient Ruins", "§6Mysterious echoes of lost civilizations", PetRarity.EPIC, 4, 145, "music.end", "Archeological wonder"),
    ETHEREAL_MIST("Ethereal Mist", "§d§lDreamy floating tones", PetRarity.LEGENDARY, 6, 0, "music.creative", "Mystical fog"); // prestige reward

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final String soundKey; // Bukkit sound or resource key for playSound
    private final String flavor;

    IslandMusicType(String displayName, String description, PetRarity rarity,
                    int minPrestige, int tokenCost, String soundKey, String flavor) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.soundKey = soundKey;
        this.flavor = flavor;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public PetRarity getRarity() { return rarity; }
    public int getMinPrestige() { return minPrestige; }
    public int getTokenCost() { return tokenCost; }
    public String getSoundKey() { return soundKey; }
    public String getFlavor() { return flavor; }

    public boolean isNone() { return this == NONE; }
}
