package com.thenerdcj.island;

/**
 * Enum of all available island upgrades.
 * Each upgrade has a base cost and description.
 */
public enum IslandUpgrade {

    ISLAND_SIZE(1, 5000, "§bIncrease Island Size", "§7Expands your island border by 10 blocks"),
    HOPPER_LIMIT(2, 3000, "§eHopper Limit", "§7Allows 5 more hoppers on your island"),
    SPAWNER_RATE(3, 8000, "§cSpawner Rate", "§7Increases spawner activation speed by 15%"),
    VAULT_SLOTS(1, 2500, "§6Vault Slots", "§7Adds 9 more slots to your /is vault"),
    AUTO_SELLER(4, 12000, "§aAuto-Seller", "§7Automatically sells crops every 30 minutes"),
    MOB_CAP(2, 4500, "§5Mob Cap", "§7Increases hostile mob spawn cap by 20%"),
    CROP_GROWTH(3, 6000, "§2Crop Growth", "§7Crops grow 25% faster on your island");

    private final int levelReq;
    private final int baseCost;
    private final String displayName;
    private final String description;

    IslandUpgrade(int levelReq, int baseCost, String displayName, String description) {
        this.levelReq = levelReq;
        this.baseCost = baseCost;
        this.displayName = displayName;
        this.description = description;
    }

    public int getLevelReq() { return levelReq; }
    public int getBaseCost() { return baseCost; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    /**
     * Calculates the cost for the next level of this upgrade.
     */
    public int getCostForLevel(int currentLevel) {
        return (int) (baseCost * Math.pow(1.5, currentLevel));
    }
}