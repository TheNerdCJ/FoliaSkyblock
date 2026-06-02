package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Cosmetic Chat Bubbles - floating text + particle effects when sending chat messages.
 * Purely visual, Play-to-Win gated by prestige and slayer tokens.
 * Styles control color, particle theme, and bubble presentation (TextDisplay + flair).
 */
public enum ChatBubbleCosmetic {

    NONE("None", "§7No chat bubble", PetRarity.COMMON, 0, 0, "none"),

    // Common / early
    HEART_BUBBLE("Heart Bubble", "§cHearts and pink bubble on chat", PetRarity.COMMON, 1, 15, "heart"),
    STAR_BUBBLE("Star Bubble", "§eSparkling stars around message", PetRarity.UNCOMMON, 2, 30, "star"),

    // Mid
    NOTE_BUBBLE("Note Bubble", "§aMusical notes with green text", PetRarity.UNCOMMON, 2, 35, "note"),
    FLAME_BUBBLE("Flame Bubble", "§cFiery particles and red text", PetRarity.RARE, 3, 60, "flame"),
    MAGIC_BUBBLE("Magic Bubble", "§5Enchant particles + purple text", PetRarity.RARE, 3, 65, "magic"),

    // Higher
    ENDER_BUBBLE("Ender Bubble", "§5Teleport particles + dark text", PetRarity.EPIC, 4, 95, "ender"),
    SNOW_BUBBLE("Snow Bubble", "§bSnowflakes and cool blue text", PetRarity.EPIC, 4, 100, "snow"),
    SLIME_BUBBLE("Slime Bubble", "§aBouncy green slime particles", PetRarity.EPIC, 4, 105, "slime"),

    // Prestige / high value
    RAINBOW_BUBBLE("Rainbow Bubble", "§d§lColor-shifting rainbow text + sparkles", PetRarity.LEGENDARY, 6, 0, "rainbow"),
    CROWN_BUBBLE("Crown Bubble", "§6§lRoyal crown + gold text flair", PetRarity.LEGENDARY, 6, 180, "crown"),
    VOID_BUBBLE("Void Bubble", "§8§lDark void particles + shadow text", PetRarity.LEGENDARY, 6, 200, "void"),
    FIREWORK_BUBBLE("Firework Bubble", "§6§lExplosive firework flair on chat", PetRarity.LEGENDARY, 6, 220, "firework"),
    AQUA_BUBBLE("Aqua Bubble", "§3§lOcean waves and cyan text", PetRarity.EPIC, 5, 140, "aqua"),
    BUBBLE_BURST("Bubble Burst", "§b§lPopping bubbles with blue text", PetRarity.RARE, 3, 70, "burst"),
    GLITTER_SHOWER("Glitter Shower", "§d§lSparkly glitter rain on messages", PetRarity.EPIC, 4, 110, "glitter"),

    // Chat cosmetics depth (more variety/polish)
    SPARK_BUBBLE("Spark Bubble", "§eElectric sparks with yellow text", PetRarity.RARE, 3, 75, "spark"),
    LEAF_BUBBLE("Leaf Bubble", "§2Nature leaves and green text", PetRarity.EPIC, 4, 115, "leaf"),
    SKULL_BUBBLE("Skull Bubble", "§8Spooky skull particles + dark flair", PetRarity.LEGENDARY, 6, 230, "skull");

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final String bubbleStyle;

    ChatBubbleCosmetic(String displayName, String description, PetRarity rarity,
                       int minPrestige, int tokenCost, String bubbleStyle) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.bubbleStyle = bubbleStyle;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public PetRarity getRarity() { return rarity; }
    public int getMinPrestige() { return minPrestige; }
    public int getTokenCost() { return tokenCost; }
    public String getBubbleStyle() { return bubbleStyle; }

    public boolean isNone() { return this == NONE; }
}
