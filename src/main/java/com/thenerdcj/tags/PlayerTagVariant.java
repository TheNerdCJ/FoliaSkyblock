package com.thenerdcj.tags;

/**
 * Variants / styles for cosmetic player tags.
 * Similar to PetVariant for pets.
 *
 * Allows the same tag to have different visual flavors (color tints, special editions).
 * Purely cosmetic.
 */
public enum PlayerTagVariant {

    NONE("Standard", "§7", ""),
    RED("Crimson", "§c", "§c"),
    BLUE("Azure", "§9", "§9"),
    GOLD("Golden", "§6", "§6"),
    SHADOW("Shadow", "§8", "§8"),
    RAINBOW("Prismatic", "§d", "§d"),   // special - can cycle or use special formatting
    CRYSTAL("Crystal", "§b", "§b"),
    BLOOD("Blood", "§4", "§4");

    private final String displayName;
    private final String colorCode;
    private final String tint; // used to recolor the base tag text

    PlayerTagVariant(String displayName, String colorCode, String tint) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.tint = tint;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getTint() {
        return tint;
    }

    public String getColoredName() {
        return colorCode + displayName;
    }
}
