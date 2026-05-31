package com.thenerdcj.island;

/**
 * Enum of all available island upgrades.
 * Each upgrade has a base cost, description, and is purchasable using island balance.
 *
 * Level tracking is handled per-island (in database / Island object),
 * not inside this enum.
 */
public enum IslandUpgrade {

    ISLAND_SIZE(1, 5000, "Increase Island Size", "Expands your island border by 10 blocks"),
    HOPPER_LIMIT(2, 3000, "Hopper Limit", "Allows 5 more hoppers on your island"),
    SPAWNER_RATE(3, 8000, "Spawner Rate", "Increases spawner activation speed by 15%"),
    VAULT_SLOTS(1, 2500, "Vault Slots", "Adds 9 more slots to your /is vault"),
    AUTO_SELLER(4, 12000, "Auto-Seller", "Automatically sells crops every 30 minutes"),
    MOB_CAP(2, 4500, "Mob Cap", "Increases hostile mob spawn cap by 20%"),
    CROP_GROWTH(3, 6000, "Crop Growth", "Crops grow 25% faster on your island"),
    ORE_GENERATOR(3, 15000, "Ore Generator", "Upgrades all cobblestone generators on the island to produce better ores more often"),
    MINION_SLOTS(4, 10000, "Minion Slots", "Increases the maximum number of deployable minions by +1 per level"),
    MEMBER_LIMIT(3, 6500, "Member Limit", "Increases the maximum number of island members/party size by +1 per level"),
    WARDROBE_SLOTS(2, 7500, "Wardrobe Slots", "Increases the number of available wardrobe loadout slots");

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

    public int getLevelReq() {
        return levelReq;
    }

    public int getBaseCost() {
        return baseCost;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Calculates the cost for a specific level of this upgrade.
     */
    public int getCostForLevel(int currentLevel) {
        return (int) (baseCost * Math.pow(1.5, currentLevel));
    }

    /**
     * Returns the base cost (used by IslandUpgradeManager).
     */
    public double getCost() {
        return baseCost;
    }

    /**
     * Default max level for all upgrades (can be overridden per upgrade later if needed).
     */
    public int getMaxLevel() {
        return 10; // You can customize this per upgrade if desired
    }

    @Override
    public String toString() {
        return displayName;
    }
}