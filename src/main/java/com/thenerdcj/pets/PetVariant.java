package com.thenerdcj.pets;

/**
 * Simple variants for cosmetic pets (colors, patterns, special editions).
 * Examples: different parrot colors, cat breeds, "shadow" or "golden" editions.
 * Purely cosmetic differentiation.
 */
public enum PetVariant {
    NONE("Standard", "§7"),
    RED("Red", "§c"),
    BLUE("Blue", "§9"),
    GOLD("Golden", "§6"),
    SHADOW("Shadow", "§8"),
    RAINBOW("Rainbow", "§d"),
    CRYSTAL("Crystal", "§b");

    private final String display;
    private final String color;

    PetVariant(String display, String color) {
        this.display = display;
        this.color = color;
    }

    public String getDisplayName() { return display; }
    public String getColorCode() { return color; }
    public String getColoredName() { return color + display; }
}
