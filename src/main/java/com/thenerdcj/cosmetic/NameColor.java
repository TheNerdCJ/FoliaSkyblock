package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Chat / Name Color cosmetics.
 * These control the color of the player name portion in chat (and display names).
 * Default is white (§f). Rank colors are always controlled by ranks.yml config.
 * Full support for all standard Minecraft chat colors.
 */
public enum NameColor {

    NONE("None", "§f", "§7Default (white) name color", PetRarity.COMMON, 0, 0),

    BLACK("Black", "§0", "§0Black name", PetRarity.COMMON, 0, 5),
    DARK_BLUE("Dark Blue", "§1", "§1Dark blue name", PetRarity.COMMON, 0, 5),
    DARK_GREEN("Dark Green", "§2", "§2Dark green name", PetRarity.COMMON, 0, 5),
    DARK_AQUA("Dark Aqua", "§3", "§3Dark aqua name", PetRarity.COMMON, 0, 5),
    DARK_RED("Dark Red", "§4", "§4Dark red name", PetRarity.COMMON, 0, 5),
    DARK_PURPLE("Dark Purple", "§5", "§5Dark purple name", PetRarity.COMMON, 0, 5),
    GOLD("Gold", "§6", "§6Gold name", PetRarity.COMMON, 0, 5),
    GRAY("Gray", "§7", "§7Gray name", PetRarity.COMMON, 0, 5),

    DARK_GRAY("Dark Gray", "§8", "§8Dark gray name", PetRarity.UNCOMMON, 1, 15),
    BLUE("Blue", "§9", "§9Blue name", PetRarity.UNCOMMON, 1, 15),
    GREEN("Green", "§a", "§aGreen name", PetRarity.UNCOMMON, 1, 15),
    AQUA("Aqua", "§b", "§bAqua name", PetRarity.UNCOMMON, 1, 15),
    RED("Red", "§c", "§cRed name", PetRarity.UNCOMMON, 1, 15),
    LIGHT_PURPLE("Light Purple", "§d", "§dLight purple name", PetRarity.UNCOMMON, 1, 15),
    YELLOW("Yellow", "§e", "§eYellow name", PetRarity.UNCOMMON, 1, 15),
    WHITE("White", "§f", "§fWhite name (default)", PetRarity.COMMON, 0, 0);

    private final String displayName;
    private final String colorCode;   // The § code to prefix before player name
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;

    NameColor(String displayName, String colorCode, String description, PetRarity rarity, int minPrestige, int tokenCost) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
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
