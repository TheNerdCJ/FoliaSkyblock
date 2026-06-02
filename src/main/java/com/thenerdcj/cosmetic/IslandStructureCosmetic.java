package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Cosmetic structures for island decoration (larger than single furniture).
 * Placed as clusters of ItemDisplays.
 * Play-to-Win.
 */
public enum IslandStructureCosmetic {

    NONE("None", "§7No structure", PetRarity.COMMON, 0, 0, 0, Category.DECOR, null, "Basic"),

    // Small structures
    STONE_PILLAR("Stone Pillar", "§7Simple decorative pillar", PetRarity.UNCOMMON, 2, 50, 6001, Category.DECOR, "pillar", "Basic"),
    WOODEN_ARCH("Wooden Arch", "§6Entrance arch", PetRarity.UNCOMMON, 2, 55, 6002, Category.DECOR, "arch", "Basic"),

    // Mid
    CRYSTAL_CLUSTER("Crystal Cluster", "§bGlowing crystals", PetRarity.RARE, 3, 90, 6003, Category.DECOR, "crystals", "Mystic"),
    SMALL_TREE("Small Decorative Tree", "§aCosmetic foliage", PetRarity.RARE, 3, 85, 6004, Category.NATURE, "tree", "Basic"),

    // Larger
    FOUNTAIN_BASE("Fountain Base", "§bMulti-part fountain", PetRarity.EPIC, 4, 150, 6005, Category.WATER, "fountain", "Mystic"),
    MYSTIC_CIRCLE("Mystic Circle", "§5Runed platform", PetRarity.EPIC, 4, 160, 6006, Category.DECOR, "circle", "Mystic"),

    // Prestige
    DRAGON_NEST("Dragon Nest", "§4§lLarge prestige structure", PetRarity.LEGENDARY, 6, 0, 6007, Category.STATUE, "nest", "Slayer"),
    GRAND_ARCH("Grand Archway", "§6§lEpic entrance", PetRarity.LEGENDARY, 6, 250, 6008, Category.DECOR, "grandarch", "Basic");

    public enum Category {
        DECOR("Decor"),
        NATURE("Nature"),
        WATER("Water"),
        STATUE("Statue");

        private final String display;
        Category(String display) { this.display = display; }
        public String getDisplayName() { return display; }
    }

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final int customModelData;
    private final Category category;
    private final String modelHint;
    private final String structureSet;

    IslandStructureCosmetic(String displayName, String description, PetRarity rarity,
                            int minPrestige, int tokenCost, int customModelData,
                            Category category, String modelHint, String structureSet) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.customModelData = customModelData;
        this.category = category;
        this.modelHint = modelHint;
        this.structureSet = structureSet != null ? structureSet : "Basic";
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public PetRarity getRarity() { return rarity; }
    public int getMinPrestige() { return minPrestige; }
    public int getTokenCost() { return tokenCost; }
    public int getCustomModelData() { return customModelData; }
    public Category getCategory() { return category; }
    public String getModelHint() { return modelHint; }
    public String getStructureSet() { return structureSet; }

    public boolean isNone() { return this == NONE; }
}
