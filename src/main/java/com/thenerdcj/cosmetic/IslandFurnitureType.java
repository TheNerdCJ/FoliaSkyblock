package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Cosmetic furniture and housing decorations for islands.
 *
 * Play-to-Win only. Unlocked via prestige, slayer tokens, and collection milestones.
 * Placed furniture is a per-island cosmetic that visitors can see.
 *
 * Inspired by Hypixel Housing + Skyblock decorative items.
 * Uses CustomModelData for resource-pack supported models.
 * Future: categories for filtering, rotation, scale, variants.
 */
public enum IslandFurnitureType {

    NONE("None", "§7No furniture selected", PetRarity.COMMON, 0, 0, 0, Category.DECOR, null),

    // Common / Starter
    WOODEN_CHAIR("Wooden Chair", "§7Simple seating", PetRarity.COMMON, 1, 20, 5001, Category.SEATING, "chair"),
    POTTED_PLANT("Potted Plant", "§aSmall decorative plant", PetRarity.COMMON, 1, 15, 5002, Category.NATURE, "plant"),
    LANTERN("Hanging Lantern", "§eWarm lighting", PetRarity.COMMON, 1, 25, 5003, Category.LIGHTING, "light"),

    // Uncommon
    STONE_BENCH("Stone Bench", "§7Durable outdoor seating", PetRarity.UNCOMMON, 2, 40, 5004, Category.SEATING, "bench"),
    BOOKSHELF("Enchanted Bookshelf", "§bKnowledge display", PetRarity.UNCOMMON, 2, 45, 5005, Category.DECOR, "shelf"),
    CRYSTAL_LAMP("Crystal Lamp", "§dSoft magical glow", PetRarity.UNCOMMON, 2, 50, 5006, Category.LIGHTING, "lamp"),

    // Rare
    FOUNTAIN("Mini Fountain", "§bGentle water feature", PetRarity.RARE, 3, 80, 5007, Category.WATER, "fountain"),
    STATUE_WOLF("Wolf Guardian Statue", "§fProtective figure", PetRarity.RARE, 3, 75, 5008, Category.STATUE, "statue"),
    HANGING_GARDEN("Hanging Garden", "§aVertical greenery", PetRarity.RARE, 3, 85, 5009, Category.NATURE, "garden"),

    // Epic
    CELESTIAL_ORB("Celestial Orb Stand", "§bFloating star display", PetRarity.EPIC, 4, 140, 5010, Category.DECOR, "celestial"),
    SLAYER_TROPHY("Slayer Trophy Display", "§cTrophy of the hunt", PetRarity.EPIC, 4, 160, 5011, Category.STATUE, "trophy"),
    DRAGON_FOUNTAIN("Dragon Fountain", "§4Dramatic water feature", PetRarity.EPIC, 5, 180, 5012, Category.WATER, "dragon"),

    // Legendary / High prestige
    GRAND_CHANDELIER("Grand Chandelier", "§6§lLuxury lighting centerpiece", PetRarity.LEGENDARY, 6, 250, 5013, Category.LIGHTING, "chandelier"),
    ANCIENT_STATUE("Ancient Relic Statue", "§6§lPrestige centerpiece", PetRarity.LEGENDARY, 6, 0, 5014, Category.STATUE, "relic"), // prestige only

    // Expansion for deeper housing
    CELESTIAL_BENCH("Celestial Bench", "§bGlowing starlit seating", PetRarity.EPIC, 4, 130, 5015, Category.SEATING, "celestial_bench", "Celestial"),
    SLAYER_TROPHY_RACK("Slayer Trophy Rack", "§cDisplay your hunting trophies", PetRarity.EPIC, 5, 150, 5016, Category.DECOR, "trophy_rack", "Slayer"),
    MYSTIC_FOUNTAIN("Mystic Fountain", "§5Enchanted water feature", PetRarity.LEGENDARY, 6, 200, 5017, Category.WATER, "mystic_fountain", "Mystic"),

    // Set completion bonus furniture (auto-unlocked when themed set is fully owned)
    CELESTIAL_THRONE("Celestial Throne", "§b§lSet bonus: Majestic seating", PetRarity.LEGENDARY, 4, 0, 5018, Category.SEATING, "celestial_throne", "Celestial"),
    SLAYER_ALTAR("Slayer Altar", "§c§lSet bonus: Trophy display altar", PetRarity.LEGENDARY, 5, 0, 5019, Category.DECOR, "slayer_altar", "Slayer"),
    MYSTIC_ORB("Mystic Orb", "§5§lSet bonus: Enchanted floating orb", PetRarity.LEGENDARY, 6, 0, 5020, Category.DECOR, "mystic_orb", "Mystic"),

    // More housing variety (autonomous polish)
    SLAYER_BANNER("Slayer Banner", "§cTrophy banner decor", PetRarity.EPIC, 4, 135, 5021, Category.DECOR, "slayer_banner", "Slayer"),
    MYSTIC_ALTAR("Mystic Altar Table", "§5Small ritual table", PetRarity.LEGENDARY, 6, 210, 5022, Category.DECOR, "mystic_altar", "Mystic"),

    // Additional advanced housing variety for set bonus polish round
    ETHEREAL_LANTERN("Ethereal Lantern", "§5Glowing void light", PetRarity.EPIC, 4, 125, 5026, Category.LIGHTING, "ethereal_lantern"),
    ANCIENT_RUNE_PILLAR("Ancient Rune Pillar", "§6Mystic standing stone", PetRarity.LEGENDARY, 6, 240, 5027, Category.STATUE, "rune_pillar"),

    // Continued housing variety (autonomous)
    CELESTIAL_LAMP("Celestial Lamp", "§bStarlit lamp for Celestial sets", PetRarity.EPIC, 4, 120, 5028, Category.LIGHTING, "celestial_lamp", "Celestial"),
    DECORATIVE_GLOBE("Decorative Globe", "§eWorldly accent piece", PetRarity.RARE, 3, 70, 5029, Category.DECOR, "globe");

    public enum Category {
        SEATING("Seating"),
        LIGHTING("Lighting"),
        NATURE("Nature & Plants"),
        STATUE("Statues & Figures"),
        WATER("Water Features"),
        DECOR("General Decor");

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
    private final String modelHint; // for resource pack / future variants
    private final String furnitureSet; // for themed sets / deeper housing (e.g. "Celestial", "Slayer")

    IslandFurnitureType(String displayName, String description, PetRarity rarity,
                        int minPrestige, int tokenCost, int customModelData,
                        Category category, String modelHint, String furnitureSet) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.customModelData = customModelData;
        this.category = category;
        this.modelHint = modelHint;
        this.furnitureSet = furnitureSet != null ? furnitureSet : "Basic";
    }

    // Convenience constructor for older entries without set
    IslandFurnitureType(String displayName, String description, PetRarity rarity,
                        int minPrestige, int tokenCost, int customModelData,
                        Category category, String modelHint) {
        this(displayName, description, rarity, minPrestige, tokenCost, customModelData, category, modelHint, "Basic");
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public PetRarity getRarity() { return rarity; }
    public int getMinPrestige() { return minPrestige; }
    public int getTokenCost() { return tokenCost; }
    public int getCustomModelData() { return customModelData; }
    public Category getCategory() { return category; }
    public String getModelHint() { return modelHint; }

    public String getFurnitureSet() { return furnitureSet; }

    public boolean isNone() { return this == NONE; }
}
