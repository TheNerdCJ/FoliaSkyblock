package com.thenerdcj.booster;

/**
 * Types of temporary island boosters.
 * These provide time-limited multipliers on top of upgrades.
 */
public enum BoosterType {
    CROP_GROWTH("Crop Growth", "Increases crop growth speed"),
    SPAWNER_RATE("Spawner Rate", "Increases mob spawner speed"),
    ISLAND_XP("Island XP", "Increases XP gained on the island"),
    MONEY_EARN("Money Earn", "Increases money earned from activities"),
    MINION_SPEED("Minion Speed", "Increases minion work speed");

    private final String displayName;
    private final String description;

    BoosterType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}